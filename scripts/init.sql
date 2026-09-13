-- ============================================================
-- TP2 - Seguimiento de Pedidos por Delivery con GPS
-- Base de datos PostgreSQL
-- ============================================================

-- ------------------------------------------------------------
-- REPARTIDORES
-- Datos maestros precargados.
-- device_id coincide con uniqueId de Traccar.
-- ------------------------------------------------------------

CREATE TABLE IF NOT EXISTS repartidores (
    device_id VARCHAR(64) PRIMARY KEY,
    nombre VARCHAR(120) NOT NULL,
    msisdn VARCHAR(20)
);

INSERT INTO repartidores (
    device_id,
    nombre,
    msisdn
)
VALUES
    ('repartidor-01', 'Repartidor 01', '+595981111111'),
    ('repartidor-02', 'Repartidor 02', '+595982222222')
ON CONFLICT (device_id) DO NOTHING;


-- ------------------------------------------------------------
-- PEDIDOS
-- ------------------------------------------------------------

CREATE TABLE IF NOT EXISTS pedidos (
    id VARCHAR(50) PRIMARY KEY,

    cliente_nombre VARCHAR(120) NOT NULL,
    cliente_msisdn VARCHAR(20) NOT NULL,
    cliente_fcm_id VARCHAR(255),

    direccion_texto VARCHAR(255),

    lat_destino DOUBLE PRECISION NOT NULL,
    lon_destino DOUBLE PRECISION NOT NULL,

    radio_llegada_m INTEGER NOT NULL DEFAULT 150,

    repartidor_device_id VARCHAR(64) NOT NULL
        REFERENCES repartidores(device_id),

    estado VARCHAR(20) NOT NULL DEFAULT 'RECIBIDO',

    fecha_creacion TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    fecha_actualizacion TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_pedido_estado
        CHECK (
            estado IN (
                'RECIBIDO',
                'EN_CAMINO',
                'CERCA',
                'ENTREGADO',
                'CANCELADO'
            )
        ),

    CONSTRAINT chk_radio_llegada
        CHECK (radio_llegada_m > 0),

    CONSTRAINT chk_latitud
        CHECK (lat_destino BETWEEN -90 AND 90),

    CONSTRAINT chk_longitud
        CHECK (lon_destino BETWEEN -180 AND 180)
);


-- Permite localizar rápidamente el pedido activo de un repartidor.
CREATE INDEX IF NOT EXISTS idx_pedidos_repartidor_estado
    ON pedidos (
        repartidor_device_id,
        estado
    );


-- Un repartidor solamente puede tener un pedido activo
-- en esta implementación.
CREATE UNIQUE INDEX IF NOT EXISTS uq_pedido_activo_repartidor
    ON pedidos (repartidor_device_id)
    WHERE estado IN (
        'RECIBIDO',
        'EN_CAMINO',
        'CERCA'
    );


-- ------------------------------------------------------------
-- ÚLTIMA POSICIÓN DEL PEDIDO
-- ------------------------------------------------------------

CREATE TABLE IF NOT EXISTS pedido_ultima_posicion (
    pedido_id VARCHAR(50) PRIMARY KEY
        REFERENCES pedidos(id)
        ON DELETE CASCADE,

    device_id VARCHAR(64) NOT NULL,

    lat DOUBLE PRECISION NOT NULL,
    lon DOUBLE PRECISION NOT NULL,

    velocidad_kmh DOUBLE PRECISION,

    distancia_destino_m DOUBLE PRECISION,

    timestamp TIMESTAMPTZ NOT NULL
);


-- ------------------------------------------------------------
-- EVENTOS / HITOS DEL PEDIDO
--
-- UNIQUE(pedido_id, hito) es parte de nuestra estrategia
-- de idempotencia.
--
-- Un pedido solamente puede generar una vez cada hito.
-- ------------------------------------------------------------

CREATE TABLE IF NOT EXISTS pedido_eventos (
    id BIGSERIAL PRIMARY KEY,

    pedido_id VARCHAR(50) NOT NULL
        REFERENCES pedidos(id)
        ON DELETE CASCADE,

    hito VARCHAR(30) NOT NULL,

    detalle JSONB NOT NULL DEFAULT '{}'::jsonb,

    notificado BOOLEAN NOT NULL DEFAULT FALSE,

    timestamp TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_pedido_hito
        UNIQUE (pedido_id, hito)
);


-- ------------------------------------------------------------
-- MENSAJES PROCESADOS
--
-- Se utilizará para implementar Idempotent Receiver.
-- message_id corresponde al messageId de VehiclePosition.
-- ------------------------------------------------------------

CREATE TABLE IF NOT EXISTS mensajes_procesados (
    message_id VARCHAR(100) PRIMARY KEY,

    device_id VARCHAR(64),

    fecha_procesamiento TIMESTAMPTZ NOT NULL DEFAULT NOW()
);


-- ------------------------------------------------------------
-- Índices adicionales
-- ------------------------------------------------------------

CREATE INDEX IF NOT EXISTS idx_eventos_pedido
    ON pedido_eventos (pedido_id);

CREATE INDEX IF NOT EXISTS idx_ultima_posicion_device
    ON pedido_ultima_posicion (device_id);