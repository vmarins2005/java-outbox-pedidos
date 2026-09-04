package io.github.vmarins2005.outbox.pedidos;

import java.time.Clock;
import java.time.Instant;
import java.util.NoSuchElementException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Bean separado de proposito.
 *
 * <p>{@code @Transactional} em metodo chamado de dentro da mesma classe nao passa pelo
 * proxy do Spring e simplesmente <b>nao abre transacao</b> - a anotacao fica ali,
 * decorativa, e ninguem percebe. Se este metodo morasse dentro de
 * {@link ServicoIngenuoDePedidos}, a demonstracao falharia pelo motivo errado.
 *
 * <p>E uma das armadilhas mais comuns de Spring, e vale conhecer pelo nome:
 * auto-invocacao.
 */
@Component
class PagamentoDoPedido {

    private final RepositorioDePedidos pedidos;
    private final Clock relogio;

    PagamentoDoPedido(RepositorioDePedidos pedidos, Clock relogio) {
        this.pedidos = pedidos;
        this.relogio = relogio;
    }

    @Transactional
    PedidoPago marcarComoPago(String pedidoId) {
        Instant agora = relogio.instant();
        PedidoEntidade pedido = pedidos.findById(pedidoId)
                .orElseThrow(() -> new NoSuchElementException("pedido nao encontrado: " + pedidoId));
        pedido.pagar(agora);
        return new PedidoPago(pedido.id(), pedido.clienteId(), pedido.valor(), agora);
    }
}
