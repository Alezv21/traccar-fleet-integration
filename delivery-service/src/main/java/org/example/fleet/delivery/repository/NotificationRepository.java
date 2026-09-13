package org.example.fleet.delivery.repository;

import org.example.fleet.delivery.model.PendingNotification;

import javax.sql.DataSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import java.util.ArrayList;
import java.util.List;

public class NotificationRepository {

    private final DataSource dataSource;

    public NotificationRepository(
        DataSource dataSource
    ) {

        this.dataSource = dataSource;
    }


    /**
     * Reclama eventos pendientes.
     *
     * IMPORTANTE:
     *
     * El evento se marca notificado=true antes de
     * realizar la llamada HTTP.
     *
     * Con esto implementamos semantica AT-MOST-ONCE:
     * un mismo hito nunca se intenta enviar dos veces.
     */
    public List<PendingNotification> claimPending(
        int limit
    ) throws Exception {

        String selectSql = """
            SELECT
                e.id,
                e.pedido_id,
                e.hito,
                p.cliente_nombre,
                p.cliente_msisdn,
                p.cliente_fcm_id

            FROM pedido_eventos e

            JOIN pedidos p
                ON p.id = e.pedido_id

            WHERE e.notificado = false
                AND e.hito IN (
                    'EN_CAMINO',
                    'CERCA',
                    'ENTREGADO'
                )

            ORDER BY e.id

            LIMIT ?

            FOR UPDATE OF e SKIP LOCKED
            """;

        String updateSql = """
            UPDATE pedido_eventos
            SET notificado = true
            WHERE id = ?
              AND notificado = false
            """;

        List<PendingNotification> result =
            new ArrayList<>();


        try (
            Connection connection =
                dataSource.getConnection()
        ) {

            connection.setAutoCommit(false);

            try {

                try (
                    PreparedStatement select =
                        connection.prepareStatement(
                            selectSql
                        )
                ) {

                    select.setInt(
                        1,
                        limit
                    );

                    try (
                        ResultSet rs =
                            select.executeQuery()
                    ) {

                        while (rs.next()) {

                            long eventId =
                                rs.getLong("id");

                            try (
                                PreparedStatement update =
                                    connection.prepareStatement(
                                        updateSql
                                    )
                            ) {

                                update.setLong(
                                    1,
                                    eventId
                                );

                                int updated =
                                    update.executeUpdate();

                                if (updated == 1) {

                                    result.add(
                                        new PendingNotification(

                                            eventId,

                                            rs.getString(
                                                "pedido_id"
                                            ),

                                            rs.getString(
                                                "hito"
                                            ),

                                            rs.getString(
                                                "cliente_nombre"
                                            ),

                                            rs.getString(
                                                "cliente_msisdn"
                                            ),

                                            rs.getString(
                                                "cliente_fcm_id"
                                            )
                                        )
                                    );
                                }
                            }
                        }
                    }
                }

                connection.commit();

            } catch (Exception e) {

                connection.rollback();

                throw e;

            } finally {

                connection.setAutoCommit(
                    true
                );
            }
        }

        return result;
    }
}