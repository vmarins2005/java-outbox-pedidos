package io.github.vmarins2005.outbox.pedidos;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.vmarins2005.outbox.mensageria.MensagemPublicada;
import io.github.vmarins2005.outbox.mensageria.Mensageria;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * A versao ingenua, escrita de proposito como quase todo mundo escreve na primeira vez:
 * salva, comita, publica.
 *
 * <p>Parece correto e tem duas janelas de inconsistencia, ambas demonstradas em
 * {@code PublicacaoIngenuaTest}:
 *
 * <ol>
 *   <li><b>Broker fora do ar.</b> O commit ja aconteceu. O pedido esta pago no banco e o
 *       evento nunca sai. Ninguem fica sabendo - nao existe registro de que faltou
 *       publicar, entao nao ha nem o que reprocessar depois.</li>
 *   <li><b>Processo morre entre o commit e o publish.</b> Mesmo resultado, e sem excecao
 *       nenhuma para investigar.</li>
 * </ol>
 *
 * <p>A "solucao" comum de mover o publish para dentro da transacao troca o problema de
 * lado e nao resolve: o broker pode receber a mensagem e a transacao dar rollback logo
 * depois. Ai o evento existe para um pedido que nunca foi pago - pior que o anterior,
 * porque o consumidor age sobre um fato que nao aconteceu.
 *
 * <p>Nao existe ordem entre "gravar no banco" e "publicar no broker" que resolva isso.
 * Sao dois sistemas, e nao ha transacao entre eles. E dai que nasce o outbox: fazer as
 * duas escritas no <b>mesmo</b> sistema transacional, e entregar depois.
 */
@Service
public class ServicoIngenuoDePedidos {

    private final PagamentoDoPedido pagamento;
    private final Mensageria mensageria;
    private final ObjectMapper json;

    ServicoIngenuoDePedidos(PagamentoDoPedido pagamento, Mensageria mensageria, ObjectMapper json) {
        this.pagamento = pagamento;
        this.mensageria = mensageria;
        this.json = json;
    }

    public void pagarSemOutbox(String pedidoId) {
        PedidoPago evento = pagamento.marcarComoPago(pedidoId);

        // <<< A transacao ja comitou. Daqui para a frente, qualquer falha perde o evento
        //     e deixa o banco afirmando que o pedido esta pago.
        publicar(evento);
    }

    private void publicar(PedidoPago evento) {
        try {
            mensageria.publicar(new MensagemPublicada(
                    UUID.randomUUID().toString(), PedidoPago.TIPO, evento.pedidoId(),
                    json.writeValueAsString(evento)));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("nao foi possivel serializar o evento", e);
        }
    }
}
