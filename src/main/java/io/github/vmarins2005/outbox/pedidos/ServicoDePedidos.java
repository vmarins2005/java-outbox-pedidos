package io.github.vmarins2005.outbox.pedidos;

import io.github.vmarins2005.outbox.saida.RegistradorDeSaida;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.NoSuchElementException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A versao correta: pedido e mensagem gravados na <b>mesma transacao</b>.
 *
 * <p>Compare com {@link ServicoIngenuoDePedidos}, que faz a mesma coisa do jeito que
 * parece natural e perde eventos.
 */
@Service
public class ServicoDePedidos {

    private final RepositorioDePedidos pedidos;
    private final RegistradorDeSaida saida;
    private final Clock relogio;

    ServicoDePedidos(RepositorioDePedidos pedidos, RegistradorDeSaida saida, Clock relogio) {
        this.pedidos = pedidos;
        this.saida = saida;
        this.relogio = relogio;
    }

    @Transactional
    public String criar(String clienteId, BigDecimal valor) {
        return pedidos.save(new PedidoEntidade(clienteId, valor)).id();
    }

    /**
     * Uma transacao, duas escritas: o status do pedido e a linha da tabela de saida. Nao
     * ha chamada de rede aqui dentro - o broker so entra em cena depois do commit, no
     * publicador.
     *
     * <p>Se o processo cair em qualquer ponto deste metodo, o banco fica consistente: ou
     * as duas escritas aconteceram, ou nenhuma.
     */
    @Transactional
    public void pagar(String pedidoId) {
        Instant agora = relogio.instant();
        PedidoEntidade pedido = carregar(pedidoId);
        pedido.pagar(agora);

        saida.registrar(
                PedidoPago.TIPO,
                pedido.id(),
                new PedidoPago(pedido.id(), pedido.clienteId(), pedido.valor(), agora));
    }

    @Transactional(readOnly = true)
    public PedidoEntidade consultar(String pedidoId) {
        return carregar(pedidoId);
    }

    private PedidoEntidade carregar(String pedidoId) {
        return pedidos.findById(pedidoId)
                .orElseThrow(() -> new NoSuchElementException("pedido nao encontrado: " + pedidoId));
    }
}
