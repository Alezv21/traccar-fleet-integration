package org.example.fleet.delivery.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.fleet.delivery.model.CrearPedidoRequest;
import org.example.fleet.delivery.model.PedidoCreadoResponse;
import org.example.fleet.delivery.model.TrackingResponse;
import org.example.fleet.delivery.model.ProcesamientoPosicionResultado;
import org.example.fleet.delivery.util.Haversine;
import org.example.fleet.model.VehiclePosition;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;

import javax.sql.DataSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import java.util.Map;
import java.util.Optional;

public class PedidoRepository {

    private final DataSource dataSource;
    private final ObjectMapper objectMapper;
    private static final double RADIO_ENTREGA_M = 30.0;

    public PedidoRepository(DataSource dataSource) {

        this.dataSource = dataSource;
        this.objectMapper = new ObjectMapper();
    }

    public boolean existeRepartidor(
        String deviceId
    ) throws SQLException {

        String sql = """
            SELECT 1
            FROM repartidores
            WHERE device_id = ?
            """;

        try (
            Connection connection =
                dataSource.getConnection();

            PreparedStatement statement =
                connection.prepareStatement(sql)
        ) {

            statement.setString(
                1,
                deviceId
            );

            try (
                ResultSet resultSet =
                    statement.executeQuery()
            ) {

                return resultSet.next();
            }
        }
    }

    public PedidoCreadoResponse crearPedido(
        CrearPedidoRequest request,
        int radioLlegada
    ) throws Exception {

        String insertPedido = """
            INSERT INTO pedidos (
                id,
                cliente_nombre,
                cliente_msisdn,
                cliente_fcm_id,
                direccion_texto,
                lat_destino,
                lon_destino,
                radio_llegada_m,
                repartidor_device_id,
                estado
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'RECIBIDO')
            """;

        String insertEvento = """
            INSERT INTO pedido_eventos (
                pedido_id,
                hito,
                detalle,
                notificado
            )
            VALUES (
                ?,
                'RECIBIDO',
                CAST(? AS jsonb),
                false
            )
            """;

        try (
            Connection connection =
                dataSource.getConnection()
        ) {

            connection.setAutoCommit(false);

            try {

                try (
                    PreparedStatement statement =
                        connection.prepareStatement(
                            insertPedido
                        )
                ) {

                    statement.setString(
                        1,
                        request.id()
                    );

                    statement.setString(
                        2,
                        request.clienteNombre()
                    );

                    statement.setString(
                        3,
                        request.clienteMsisdn()
                    );

                    statement.setString(
                        4,
                        request.clienteFcmId()
                    );

                    statement.setString(
                        5,
                        request.direccionTexto()
                    );

                    statement.setDouble(
                        6,
                        request.latDestino()
                    );

                    statement.setDouble(
                        7,
                        request.lonDestino()
                    );

                    statement.setInt(
                        8,
                        radioLlegada
                    );

                    statement.setString(
                        9,
                        request.repartidorDeviceId()
                    );

                    statement.executeUpdate();
                }

                String detalle =
                    objectMapper.writeValueAsString(
                        Map.of(
                            "estado",
                            "RECIBIDO",
                            "repartidorDeviceId",
                            request.repartidorDeviceId()
                        )
                    );

                try (
                    PreparedStatement statement =
                        connection.prepareStatement(
                            insertEvento
                        )
                ) {

                    statement.setString(
                        1,
                        request.id()
                    );

                    statement.setString(
                        2,
                        detalle
                    );

                    statement.executeUpdate();
                }

                connection.commit();

                return new PedidoCreadoResponse(
                    request.id(),
                    "RECIBIDO",
                    request.repartidorDeviceId(),
                    "Pedido recibido correctamente"
                );

            } catch (Exception e) {

                connection.rollback();

                if (
                    e instanceof SQLException sqlException
                    && "23505".equals(
                        sqlException.getSQLState()
                    )
                ) {

                    throw new IllegalArgumentException(
                        "Ya existe el pedido o el repartidor ya posee un pedido activo"
                    );
                }

                throw e;

            } finally {

                connection.setAutoCommit(true);
            }
        }
    }

    public Optional<TrackingResponse> obtenerTracking(
        String pedidoId
    ) throws SQLException {

        String sql = """
            SELECT
                p.id,
                p.estado,
                p.repartidor_device_id,
                p.cliente_nombre,
                p.direccion_texto,
                p.lat_destino,
                p.lon_destino,
                p.radio_llegada_m,

                u.lat,
                u.lon,
                u.velocidad_kmh,
                u.distancia_destino_m,
                u.timestamp

            FROM pedidos p

            LEFT JOIN pedido_ultima_posicion u
                ON u.pedido_id = p.id

            WHERE p.id = ?
            """;

        try (
            Connection connection =
                dataSource.getConnection();

            PreparedStatement statement =
                connection.prepareStatement(sql)
        ) {

            statement.setString(
                1,
                pedidoId
            );

            try (
                ResultSet resultSet =
                    statement.executeQuery()
            ) {

                if (!resultSet.next()) {
                    return Optional.empty();
                }

                Double lat =
                    getNullableDouble(
                        resultSet,
                        "lat"
                    );

                Double lon =
                    getNullableDouble(
                        resultSet,
                        "lon"
                    );

                Double velocidad =
                    getNullableDouble(
                        resultSet,
                        "velocidad_kmh"
                    );

                Double distancia =
                    getNullableDouble(
                        resultSet,
                        "distancia_destino_m"
                    );

                String timestamp = null;

                if (
                    resultSet.getTimestamp(
                        "timestamp"
                    ) != null
                ) {

                    timestamp =
                        resultSet.getTimestamp(
                            "timestamp"
                        )
                        .toInstant()
                        .toString();
                }

                TrackingResponse tracking =
                    new TrackingResponse(

                        resultSet.getString("id"),

                        resultSet.getString(
                            "estado"
                        ),

                        resultSet.getString(
                            "repartidor_device_id"
                        ),

                        resultSet.getString(
                            "cliente_nombre"
                        ),

                        resultSet.getString(
                            "direccion_texto"
                        ),

                        resultSet.getDouble(
                            "lat_destino"
                        ),

                        resultSet.getDouble(
                            "lon_destino"
                        ),

                        resultSet.getInt(
                            "radio_llegada_m"
                        ),

                        lat,
                        lon,
                        velocidad,
                        distancia,
                        timestamp
                    );

                return Optional.of(
                    tracking
                );
            }
        }
    }

    private Double getNullableDouble(
        ResultSet resultSet,
        String column
    ) throws SQLException {

        double value =
            resultSet.getDouble(column);

        if (resultSet.wasNull()) {
            return null;
        }

        return value;
    }
    public ProcesamientoPosicionResultado procesarPosicion(
    VehiclePosition position
) throws Exception {

    String insertMensaje = """
        INSERT INTO mensajes_procesados (
            message_id,
            device_id
        )
        VALUES (?, ?)
        ON CONFLICT (message_id) DO NOTHING
        """;

    String buscarPedido = """
        SELECT
            id,
            estado,
            lat_destino,
            lon_destino,
            radio_llegada_m
        FROM pedidos
        WHERE repartidor_device_id = ?
          AND estado IN (
              'RECIBIDO',
              'EN_CAMINO',
              'CERCA'
          )
        ORDER BY fecha_creacion
        LIMIT 1
        FOR UPDATE
        """;

    String upsertPosicion = """
        INSERT INTO pedido_ultima_posicion (
            pedido_id,
            device_id,
            lat,
            lon,
            velocidad_kmh,
            distancia_destino_m,
            timestamp
        )
        VALUES (?, ?, ?, ?, ?, ?, ?)

        ON CONFLICT (pedido_id)
        DO UPDATE SET
            device_id = EXCLUDED.device_id,
            lat = EXCLUDED.lat,
            lon = EXCLUDED.lon,
            velocidad_kmh = EXCLUDED.velocidad_kmh,
            distancia_destino_m = EXCLUDED.distancia_destino_m,
            timestamp = EXCLUDED.timestamp
        """;

    String actualizarEstado = """
        UPDATE pedidos
        SET
            estado = ?,
            fecha_actualizacion = NOW()
        WHERE id = ?
        """;

    String insertarEvento = """
        INSERT INTO pedido_eventos (
            pedido_id,
            hito,
            detalle,
            notificado
        )
        VALUES (
            ?,
            ?,
            CAST(? AS jsonb),
            false
        )
        ON CONFLICT (pedido_id, hito)
        DO NOTHING
        """;

    try (
        Connection connection =
            dataSource.getConnection()
    ) {

        connection.setAutoCommit(false);

        try {

            /*
             * Idempotent Receiver
             */
            try (
                PreparedStatement statement =
                    connection.prepareStatement(
                        insertMensaje
                    )
            ) {

                statement.setString(
                    1,
                    position.messageId()
                );

                statement.setString(
                    2,
                    position.deviceId()
                );

                int inserted =
                    statement.executeUpdate();

                if (inserted == 0) {

                    connection.commit();

                    return new ProcesamientoPosicionResultado(
                        "DUPLICADO",
                        null,
                        null,
                        null,
                        null
                    );
                }
            }

            String pedidoId;
            String estadoAnterior;
            double latDestino;
            double lonDestino;
            int radioLlegada;

            try (
                PreparedStatement statement =
                    connection.prepareStatement(
                        buscarPedido
                    )
            ) {

                statement.setString(
                    1,
                    position.deviceId()
                );

                try (
                    ResultSet resultSet =
                        statement.executeQuery()
                ) {

                    if (!resultSet.next()) {

                        connection.commit();

                        return new ProcesamientoPosicionResultado(
                            "SIN_PEDIDO",
                            null,
                            null,
                            null,
                            null
                        );
                    }

                    pedidoId =
                        resultSet.getString(
                            "id"
                        );

                    estadoAnterior =
                        resultSet.getString(
                            "estado"
                        );

                    latDestino =
                        resultSet.getDouble(
                            "lat_destino"
                        );

                    lonDestino =
                        resultSet.getDouble(
                            "lon_destino"
                        );
                    radioLlegada =
                        resultSet.getInt(
                            "radio_llegada_m"
                        );
                }
            }

            /*
             * Haversine:
             * GPS actual -> destino del pedido
             */
            double distancia =
                Haversine.distanceMeters(
                    position.latitude(),
                    position.longitude(),
                    latDestino,
                    lonDestino
                );

            /*
             * Guardamos siempre la ultima posicion.
             */
            try (
                PreparedStatement statement =
                    connection.prepareStatement(
                        upsertPosicion
                    )
            ) {

                statement.setString(
                    1,
                    pedidoId
                );

                statement.setString(
                    2,
                    position.deviceId()
                );

                statement.setDouble(
                    3,
                    position.latitude()
                );

                statement.setDouble(
                    4,
                    position.longitude()
                );

                statement.setDouble(
                    5,
                    position.speedKmh()
                );

                statement.setDouble(
                    6,
                    distancia
                );

                statement.setTimestamp(
                    7,
                    convertirTimestamp(
                        position.timestamp()
                    )
                );

                statement.executeUpdate();
            }

            String estadoActual =
                estadoAnterior;


            /*
            * Máquina de estados del pedido.
            *
            * Puede avanzar más de un hito en una misma
            * coordenada si el repartidor ya se encuentra
            * dentro de los radios correspondientes.
            */


            /*
            * RECIBIDO -> EN_CAMINO
            */
            if (
                "RECIBIDO".equals(
                    estadoActual
                )
            ) {

                estadoActual =
                    "EN_CAMINO";

                registrarCambioEstado(
                    connection,
                    actualizarEstado,
                    insertarEvento,
                    pedidoId,
                    estadoActual,
                    position,
                    distancia
                );
            }


            /*
            * EN_CAMINO -> CERCA
            *
            * Se utiliza el radio configurado en el pedido.
            * Por defecto: 150 metros.
            */
            if (
                "EN_CAMINO".equals(
                    estadoActual
                )
                && distancia <= radioLlegada
            ) {

                estadoActual =
                    "CERCA";

                registrarCambioEstado(
                    connection,
                    actualizarEstado,
                    insertarEvento,
                    pedidoId,
                    estadoActual,
                    position,
                    distancia
                );
            }


            /*
            * CERCA -> ENTREGADO
            *
            * Para la demo consideramos entrega efectiva
            * cuando el repartidor está a <= 30 metros.
            */
            if (
                "CERCA".equals(
                    estadoActual
                )
                && distancia <= RADIO_ENTREGA_M
            ) {

                estadoActual =
                    "ENTREGADO";

                registrarCambioEstado(
                    connection,
                    actualizarEstado,
                    insertarEvento,
                    pedidoId,
                    estadoActual,
                    position,
                    distancia
                );
            }
            connection.commit();

            return new ProcesamientoPosicionResultado(
                "PROCESADO",
                pedidoId,
                estadoAnterior,
                estadoActual,
                distancia
            );

        } catch (Exception e) {

            connection.rollback();

            throw e;

        } finally {

            connection.setAutoCommit(true);
        }
    }
}

private Timestamp convertirTimestamp(
    String timestamp
) {

    if (
        timestamp == null
        || timestamp.isBlank()
    ) {

        return Timestamp.from(
            Instant.now()
        );
    }

    try {

        return Timestamp.from(
            OffsetDateTime
                .parse(timestamp)
                .toInstant()
        );

    } catch (Exception ignored) {

        try {

            return Timestamp.from(
                Instant.parse(timestamp)
            );

        } catch (Exception ignoredAgain) {

            return Timestamp.from(
                Instant.now()
            );
        }
    }
}
private void registrarCambioEstado(
    Connection connection,
    String actualizarEstado,
    String insertarEvento,
    String pedidoId,
    String nuevoEstado,
    VehiclePosition position,
    double distancia
) throws Exception {

    /*
     * Actualizamos el estado actual del pedido.
     */
    try (
        PreparedStatement statement =
            connection.prepareStatement(
                actualizarEstado
            )
    ) {

        statement.setString(
            1,
            nuevoEstado
        );

        statement.setString(
            2,
            pedidoId
        );

        statement.executeUpdate();
    }


    /*
     * Creamos el hito.
     *
     * UNIQUE(pedido_id, hito) garantiza que
     * un mismo milestone no pueda duplicarse.
     */
    String detalle =
        objectMapper.writeValueAsString(
            Map.of(
                "deviceId",
                position.deviceId(),

                "latitud",
                position.latitude(),

                "longitud",
                position.longitude(),

                "distanciaDestinoM",
                distancia,

                "estado",
                nuevoEstado
            )
        );


    try (
        PreparedStatement statement =
            connection.prepareStatement(
                insertarEvento
            )
    ) {

        statement.setString(
            1,
            pedidoId
        );

        statement.setString(
            2,
            nuevoEstado
        );

        statement.setString(
            3,
            detalle
        );

        statement.executeUpdate();
    }
}
public boolean existePedido(
    String pedidoId
) throws Exception {

    String sql = """
        SELECT 1
        FROM pedidos
        WHERE id = ?
        """;

    try (
        Connection connection =
            dataSource.getConnection();

        PreparedStatement statement =
            connection.prepareStatement(sql)
    ) {

        statement.setString(
            1,
            pedidoId
        );

        try (
            ResultSet resultSet =
                statement.executeQuery()
        ) {

            return resultSet.next();
        }
    }
}
}