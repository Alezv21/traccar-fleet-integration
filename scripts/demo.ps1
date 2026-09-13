[Console]::OutputEncoding =
    [System.Text.Encoding]::UTF8

$ErrorActionPreference = "Stop"

$script:Fallos = 0


# ============================================================
# FUNCIONES AUXILIARES
# ============================================================

function Titulo($texto) {

    Write-Host ""
    Write-Host "===================================================="
    Write-Host $texto
    Write-Host "===================================================="
}


function OK($texto) {

    Write-Host "[OK] $texto" -ForegroundColor Green
}


function FAIL($texto) {

    Write-Host "[ERROR] $texto" -ForegroundColor Red

    $script:Fallos++
}


function Assert-Igual(
    $actual,
    $esperado,
    $descripcion
) {

    if ("$actual" -eq "$esperado") {

        OK "$descripcion -> $actual"

    } else {

        FAIL "$descripcion. Esperado=$esperado Actual=$actual"
    }
}


function SQL(
    $query
) {

    $resultado =
        docker exec postgres-delivery `
            psql `
            -U delivery `
            -d delivery `
            -t `
            -A `
            -c $query

    if ($LASTEXITCODE -ne 0) {

        throw "Error ejecutando SQL"
    }

    return (
        (
            $resultado |
            Out-String
        ).Trim()
    )
}


function PushCount {

    $match = @{
        method = "POST"
        url    = "/push"
    } | ConvertTo-Json


    $resultado =
        Invoke-RestMethod `
            -Method POST `
            -Uri "http://localhost:8089/__admin/requests/count" `
            -ContentType "application/json" `
            -Body $match


    return [int]$resultado.count
}


# ============================================================
# ESPERA ASINCRONICA DE PUSH
# ============================================================

function Esperar-PushCount(
    [int]$Esperado,
    [int]$TimeoutSegundos = 15
) {

    $inicio =
        Get-Date

    while ($true) {

        $actual =
            PushCount


        if ($actual -ge $Esperado) {

            return $actual
        }


        $transcurrido =
            (
                (Get-Date) - $inicio
            ).TotalSeconds


        if (
            $transcurrido -ge $TimeoutSegundos
        ) {

            return $actual
        }


        Start-Sleep -Milliseconds 500
    }
}


# ============================================================
# ESPERA ASINCRONICA DE SQL
#
# Evita asumir que Traccar -> Broker -> Artemis ->
# Delivery Service -> PostgreSQL terminó en N segundos.
# ============================================================

function Esperar-ValorSQL(
    [string]$Query,
    [string]$Esperado,
    [int]$TimeoutSegundos = 15
) {

    $inicio =
        Get-Date

    $actual =
        ""


    while ($true) {

        $actual =
            SQL $Query


        if ("$actual" -eq "$Esperado") {

            return $actual
        }


        $transcurrido =
            (
                (Get-Date) - $inicio
            ).TotalSeconds


        if (
            $transcurrido -ge $TimeoutSegundos
        ) {

            return $actual
        }


        Start-Sleep -Milliseconds 500
    }
}


# ============================================================
# PREPARACION
# ============================================================

Titulo "PREPARACION DE LA DEMO"

Write-Host "Limpiando datos anteriores..."


SQL @"
TRUNCATE TABLE
    mensajes_procesados,
    pedido_eventos,
    pedido_ultima_posicion,
    pedidos
RESTART IDENTITY
CASCADE;
"@ | Out-Null


OK "Base de datos preparada"


# ------------------------------------------------------------
# Limpiar historial de WireMock
# ------------------------------------------------------------

try {

    Invoke-RestMethod `
        -Method DELETE `
        -Uri "http://localhost:8089/__admin/requests" |
        Out-Null

    OK "Request Journal de WireMock reseteado"

} catch {

    Write-Host `
        "No se pudo resetear WireMock. Se usara el contador actual como baseline." `
        -ForegroundColor Yellow
}


$pushBase =
    PushCount


Write-Host "PUSH existentes al iniciar: $pushBase"


# ============================================================
# CASO 1
#
# Crear pedido -> RECIBIDO
# ============================================================

Titulo "CASO 1 - Crear pedido en estado RECIBIDO"


$body = @{

    id =
        "PED-DEMO-001"

    clienteNombre =
        "Cliente Demo"

    clienteMsisdn =
        "+595981555555"

    clienteFcmId =
        "fcm-demo-001"

    direccionTexto =
        "San Lorenzo - Pedido Demo"

    latDestino =
        -25.3000

    lonDestino =
        -57.6300

    radioLlegadaM =
        150

    repartidorDeviceId =
        "repartidor-01"

} | ConvertTo-Json


$respuesta =
    Invoke-RestMethod `
        -Method POST `
        -Uri "http://localhost:8081/api/pedidos" `
        -ContentType "application/json" `
        -Body $body


Assert-Igual `
    $respuesta.estado `
    "RECIBIDO" `
    "Pedido creado"


$estado =
    SQL "SELECT estado FROM pedidos WHERE id='PED-DEMO-001';"


Assert-Igual `
    $estado `
    "RECIBIDO" `
    "Estado persistido"


# ============================================================
# CASO 2
#
# GPS REAL
#
# Traccar -> Broker -> Artemis -> Delivery Service
# ============================================================

Titulo "CASO 2 - GPS Traccar y posicion canonica"


curl.exe `
    -s `
    "http://localhost:5055/?id=repartidor-01&lat=-25.2967&lon=-57.6359&speed=12&bearing=45&altitude=80&ignition=true" |
    Out-Null


$cantidadPosiciones =
    Esperar-ValorSQL `
        -Query @"
SELECT COUNT(*)
FROM pedido_ultima_posicion
WHERE pedido_id='PED-DEMO-001';
"@ `
        -Esperado "1" `
        -TimeoutSegundos 15


Assert-Igual `
    $cantidadPosiciones `
    "1" `
    "GPS almacenado en pedido_ultima_posicion"


$deviceGps =
    SQL @"
SELECT device_id
FROM pedido_ultima_posicion
WHERE pedido_id='PED-DEMO-001';
"@


Assert-Igual `
    $deviceGps `
    "repartidor-01" `
    "Posicion correlacionada con el repartidor"


# ============================================================
# CASO 3
#
# RECIBIDO -> EN_CAMINO
# ============================================================

Titulo "CASO 3 - EN_CAMINO y PUSH"


$estado =
    Esperar-ValorSQL `
        -Query "SELECT estado FROM pedidos WHERE id='PED-DEMO-001';" `
        -Esperado "EN_CAMINO" `
        -TimeoutSegundos 15


Assert-Igual `
    $estado `
    "EN_CAMINO" `
    "Transicion RECIBIDO -> EN_CAMINO"


$esperadoPush =
    $pushBase + 1


$pushes =
    Esperar-PushCount `
        -Esperado $esperadoPush `
        -TimeoutSegundos 15


Assert-Igual `
    $pushes `
    $esperadoPush `
    "Un PUSH para EN_CAMINO"


# ============================================================
# CASO 6
#
# IDEMPOTENT RECEIVER
#
# Enviamos dos veces el MISMO messageId.
# ============================================================

Titulo "CASO 6 - Reprocesamiento sin duplicados"


jbang `
    .\scripts\PositionTool.java `
    DEMO-DUP-001 `
    repartidor-01 `
    -25.2967 `
    -57.6359 `
    22.224 |
    Out-Null


if ($LASTEXITCODE -ne 0) {

    FAIL "No se pudo enviar la primera posicion JBang"
}


Start-Sleep -Seconds 1


jbang `
    .\scripts\PositionTool.java `
    DEMO-DUP-001 `
    repartidor-01 `
    -25.2967 `
    -57.6359 `
    22.224 |
    Out-Null


if ($LASTEXITCODE -ne 0) {

    FAIL "No se pudo reenviar la posicion duplicada"
}


$cantidadMensaje =
    Esperar-ValorSQL `
        -Query @"
SELECT COUNT(*)
FROM mensajes_procesados
WHERE message_id='DEMO-DUP-001';
"@ `
        -Esperado "1" `
        -TimeoutSegundos 15


Assert-Igual `
    $cantidadMensaje `
    "1" `
    "MessageId procesado una sola vez"


Start-Sleep -Seconds 2


$cantidadEventos =
    SQL @"
SELECT COUNT(*)
FROM pedido_eventos
WHERE pedido_id='PED-DEMO-001';
"@


Assert-Igual `
    $cantidadEventos `
    "2" `
    "No se genero un nuevo hito por reprocesamiento"


$pushes =
    PushCount


$esperadoPush =
    $pushBase + 1


Assert-Igual `
    $pushes `
    $esperadoPush `
    "No se duplico el PUSH"


# ============================================================
# CASO 4
#
# EN_CAMINO -> CERCA
#
# Aproximadamente 111 metros del destino.
# ============================================================

Titulo "CASO 4 - CERCA <= 150 metros"


curl.exe `
    -s `
    "http://localhost:5055/?id=repartidor-01&lat=-25.2990&lon=-57.6300&speed=8&bearing=45&altitude=80&ignition=true" |
    Out-Null


# ------------------------------------------------------------
# ESPERAMOS REALMENTE A QUE EL EVENTO SEA PROCESADO
# ------------------------------------------------------------

$estado =
    Esperar-ValorSQL `
        -Query "SELECT estado FROM pedidos WHERE id='PED-DEMO-001';" `
        -Esperado "CERCA" `
        -TimeoutSegundos 15


Assert-Igual `
    $estado `
    "CERCA" `
    "Transicion EN_CAMINO -> CERCA"


# Si estado=CERCA, esta posicion ya fue persistida.
$distancia =
    SQL @"
SELECT
    ROUND(distancia_destino_m::numeric,2)
FROM pedido_ultima_posicion
WHERE pedido_id='PED-DEMO-001';
"@


Write-Host "Distancia calculada: $distancia metros"


if (
    [double]$distancia -le 150
) {

    OK "Distancia dentro del radio CERCA <= 150m"

} else {

    FAIL "La distancia CERCA es mayor a 150m"
}


$esperadoPush =
    $pushBase + 2


$pushes =
    Esperar-PushCount `
        -Esperado $esperadoPush `
        -TimeoutSegundos 15


Assert-Igual `
    $pushes `
    $esperadoPush `
    "PUSH unico para CERCA"


# ============================================================
# CASO 5
#
# CERCA -> ENTREGADO
#
# Aproximadamente 22 metros del destino.
# ============================================================

Titulo "CASO 5 - ENTREGADO <= 30 metros"


curl.exe `
    -s `
    "http://localhost:5055/?id=repartidor-01&lat=-25.2998&lon=-57.6300&speed=2&bearing=45&altitude=80&ignition=true" |
    Out-Null


# ------------------------------------------------------------
# ESPERAMOS REALMENTE A QUE EL EVENTO SEA PROCESADO
# ------------------------------------------------------------

$estado =
    Esperar-ValorSQL `
        -Query "SELECT estado FROM pedidos WHERE id='PED-DEMO-001';" `
        -Esperado "ENTREGADO" `
        -TimeoutSegundos 15


Assert-Igual `
    $estado `
    "ENTREGADO" `
    "Transicion CERCA -> ENTREGADO"


# Si estado=ENTREGADO, la posicion <= 30m ya fue procesada.
$distanciaEntrega =
    SQL @"
SELECT
    ROUND(distancia_destino_m::numeric,2)
FROM pedido_ultima_posicion
WHERE pedido_id='PED-DEMO-001';
"@


Write-Host "Distancia final: $distanciaEntrega metros"


if (
    [double]$distanciaEntrega -le 30
) {

    OK "Distancia dentro del radio ENTREGADO <= 30m"

} else {

    FAIL "Distancia final mayor a 30m"
}


$esperadoPush =
    $pushBase + 3


$pushes =
    Esperar-PushCount `
        -Esperado $esperadoPush `
        -TimeoutSegundos 15


Assert-Igual `
    $pushes `
    $esperadoPush `
    "PUSH unico para ENTREGADO"


# ============================================================
# CASO 7
#
# GPS SIN PEDIDO ACTIVO
# ============================================================

Titulo "CASO 7 - Posicion sin pedido activo"


$eventosAntes =
    SQL @"
SELECT COUNT(*)
FROM pedido_eventos
WHERE pedido_id='PED-DEMO-001';
"@


jbang `
    .\scripts\PositionTool.java `
    SIN-PEDIDO-ACTIVO-001 `
    repartidor-01 `
    -25.3000 `
    -57.6300 `
    5 |
    Out-Null


if ($LASTEXITCODE -ne 0) {

    FAIL "No se pudo enviar posicion sin pedido activo"
}


# Esperamos a que el Idempotent Receiver registre
# que el mensaje fue consumido.
$mensajeSinPedido =
    Esperar-ValorSQL `
        -Query @"
SELECT COUNT(*)
FROM mensajes_procesados
WHERE message_id='SIN-PEDIDO-ACTIVO-001';
"@ `
        -Esperado "1" `
        -TimeoutSegundos 15


Assert-Igual `
    $mensajeSinPedido `
    "1" `
    "GPS sin pedido activo fue consumido"


$eventosDespues =
    SQL @"
SELECT COUNT(*)
FROM pedido_eventos
WHERE pedido_id='PED-DEMO-001';
"@


Assert-Igual `
    $eventosDespues `
    $eventosAntes `
    "GPS sin pedido activo no genero hitos"


$estado =
    SQL "SELECT estado FROM pedidos WHERE id='PED-DEMO-001';"


Assert-Igual `
    $estado `
    "ENTREGADO" `
    "Pedido entregado no fue modificado"


# ============================================================
# CASO 8
#
# REPARTIDOR DESCONOCIDO
# ============================================================

Titulo "CASO 8 - Repartidor desconocido"


$bodyError = @{

    id =
        "PED-ERROR-001"

    clienteNombre =
        "Cliente Error"

    clienteMsisdn =
        "+595981000000"

    clienteFcmId =
        "fcm-error"

    direccionTexto =
        "Destino Error"

    latDestino =
        -25.3000

    lonDestino =
        -57.6300

    radioLlegadaM =
        150

    repartidorDeviceId =
        "repartidor-inexistente"

} | ConvertTo-Json


$statusCode = 0


try {

    $respuestaError =
        Invoke-WebRequest `
            -Method POST `
            -Uri "http://localhost:8081/api/pedidos" `
            -ContentType "application/json" `
            -Body $bodyError `
            -UseBasicParsing

    $statusCode =
        [int]$respuestaError.StatusCode

} catch {

    try {

        $statusCode =
            [int]$_.Exception.Response.StatusCode.value__

    } catch {

        try {

            $statusCode =
                [int]$_.Exception.Response.StatusCode

        } catch {

            $statusCode = -1
        }
    }
}


Assert-Igual `
    $statusCode `
    "400" `
    "Repartidor desconocido rechazado"


$cantidadPedidoError =
    SQL @"
SELECT COUNT(*)
FROM pedidos
WHERE id='PED-ERROR-001';
"@


Assert-Igual `
    $cantidadPedidoError `
    "0" `
    "Pedido invalido no fue persistido"


# ============================================================
# CASO 9
#
# PEDIDO INEXISTENTE -> DLQ
# ============================================================

Titulo "CASO 9 - Pedido inexistente enviado a DLQ"


$sendOutput =
    jbang `
        .\scripts\OrderEventTool.java `
        send |
        Out-String


Write-Host $sendOutput


if (
    $sendOutput -match "\[OK\] Evento enviado"
) {

    OK "Evento desconocido enviado a delivery.order.events"

} else {

    FAIL "No se pudo publicar el evento desconocido"
}


$eventIdMatch =
    [regex]::Match(
        $sendOutput,
        "EventId:\s*(\S+)"
    )


$eventIdEnviado =
    $null


if (
    $eventIdMatch.Success
) {

    $eventIdEnviado =
        $eventIdMatch.Groups[1].Value

    Write-Host "EventId esperado en DLQ: $eventIdEnviado"

} else {

    FAIL "No se pudo obtener EventId generado por JBang"
}


# Dejamos que Camel haga:
#
# delivery.order.events
#       ->
# Content Based Router
#       ->
# delivery.errors

Start-Sleep -Seconds 3


$receiveOutput =
    jbang `
        .\scripts\OrderEventTool.java `
        receive |
        Out-String


Write-Host $receiveOutput


if (
    $receiveOutput -match "PEDIDO_INEXISTENTE"
) {

    OK "Evento fue derivado a delivery.errors"

} else {

    FAIL "No se encontro PEDIDO_INEXISTENTE en la DLQ"
}


if (
    $null -ne $eventIdEnviado `
    -and
    $receiveOutput -match [regex]::Escape(
        $eventIdEnviado
    )
) {

    OK "La DLQ contiene exactamente el evento enviado"

} else {

    FAIL "El EventId recibido en DLQ no coincide con el enviado"
}


# ============================================================
# CASO 10
#
# TRACKING FINAL
# ============================================================

Titulo "CASO 10 - Tracking final coherente"


$tracking =
    Invoke-RestMethod `
        -Method GET `
        -Uri "http://localhost:8081/api/pedidos/PED-DEMO-001/tracking"


Assert-Igual `
    $tracking.estado `
    "ENTREGADO" `
    "Tracking devuelve ENTREGADO"


Assert-Igual `
    $tracking.repartidorDeviceId `
    "repartidor-01" `
    "Tracking devuelve repartidor correcto"


if (
    $null -ne $tracking.ultimaLatitud `
    -and
    $null -ne $tracking.ultimaLongitud `
    -and
    $null -ne $tracking.distanciaDestinoM
) {

    OK "Tracking contiene ultima posicion GPS"

} else {

    FAIL "Tracking no contiene posicion GPS"
}


if (
    $tracking.distanciaDestinoM -le 30
) {

    OK "Distancia final <= 30 metros"

} else {

    FAIL "Distancia final mayor a 30 metros"
}


# ============================================================
# HISTORIAL DE HITOS
# ============================================================

Titulo "VERIFICACION - HISTORIAL DE HITOS"


$hitos =
    SQL @"
SELECT hito
FROM pedido_eventos
WHERE pedido_id='PED-DEMO-001'
ORDER BY id;
"@


Write-Host ""
Write-Host "Hitos registrados:"
Write-Host $hitos
Write-Host ""


$cantidadHitos =
    SQL @"
SELECT COUNT(*)
FROM pedido_eventos
WHERE pedido_id='PED-DEMO-001';
"@


Assert-Igual `
    $cantidadHitos `
    "4" `
    "Se registraron exactamente cuatro hitos"


$cantidadNotificados =
    SQL @"
SELECT COUNT(*)
FROM pedido_eventos
WHERE pedido_id='PED-DEMO-001'
  AND hito IN (
      'EN_CAMINO',
      'CERCA',
      'ENTREGADO'
  )
  AND notificado=true;
"@


Assert-Igual `
    $cantidadNotificados `
    "3" `
    "Tres hitos fueron notificados"


$recibidoNotificado =
    SQL @"
SELECT notificado
FROM pedido_eventos
WHERE pedido_id='PED-DEMO-001'
  AND hito='RECIBIDO';
"@


Assert-Igual `
    $recibidoNotificado `
    "f" `
    "RECIBIDO no genera PUSH"


# ============================================================
# PUSH AT-MOST-ONCE
# ============================================================

Titulo "VERIFICACION FINAL - PUSH AT-MOST-ONCE"


$pushAntes =
    PushCount


Write-Host "PUSH antes de esperar: $pushAntes"


Start-Sleep -Seconds 6


$pushDespues =
    PushCount


Write-Host "PUSH despues de esperar: $pushDespues"


Assert-Igual `
    $pushDespues `
    $pushAntes `
    "El timer no duplica notificaciones"


$pushGenerados =
    $pushDespues - $pushBase


Assert-Igual `
    $pushGenerados `
    "3" `
    "La ejecucion genero exactamente tres PUSH"


# ============================================================
# ESTADO FINAL
# ============================================================

Titulo "ESTADO FINAL"


Write-Host ""
Write-Host "PEDIDO:"
Write-Host ""


docker exec postgres-delivery `
    psql `
    -U delivery `
    -d delivery `
    -c "SELECT id, estado, repartidor_device_id FROM pedidos ORDER BY id;"


Write-Host ""
Write-Host "ULTIMA POSICION:"
Write-Host ""


docker exec postgres-delivery `
    psql `
    -U delivery `
    -d delivery `
    -c "SELECT pedido_id, device_id, lat, lon, ROUND(distancia_destino_m::numeric,2) AS distancia_m FROM pedido_ultima_posicion;"


Write-Host ""
Write-Host "EVENTOS:"
Write-Host ""


docker exec postgres-delivery `
    psql `
    -U delivery `
    -d delivery `
    -c "SELECT pedido_id, hito, notificado FROM pedido_eventos ORDER BY id;"


# ============================================================
# RESULTADO FINAL
# ============================================================

Titulo "RESULTADO FINAL"


if (
    $script:Fallos -eq 0
) {

    Write-Host ""

    Write-Host `
        "TODAS LAS PRUEBAS FINALIZARON CORRECTAMENTE" `
        -ForegroundColor Green

    Write-Host ""

    Write-Host `
        "Resultado: 10/10 casos OK" `
        -ForegroundColor Green

    Write-Host ""

    exit 0

} else {

    Write-Host ""

    Write-Host `
        "Pruebas con errores: $script:Fallos" `
        -ForegroundColor Red

    Write-Host ""

    exit 1
}