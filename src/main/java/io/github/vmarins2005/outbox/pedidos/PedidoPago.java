package io.github.vmarins2005.outbox.pedidos;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Payload do evento. Carrega o que o assinante precisa para agir sem voltar a perguntar -
 * a escolha entre isto e "so o identificador" esta no ADR 0002.
 */
public record PedidoPago(String pedidoId, String clienteId, BigDecimal valor, Instant pagoEm) {

    public static final String TIPO = "pedido.pago";
}
