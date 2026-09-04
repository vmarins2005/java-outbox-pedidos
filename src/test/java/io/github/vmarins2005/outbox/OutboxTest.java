package io.github.vmarins2005.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.vmarins2005.outbox.faturamento.RepositorioDeFaturas;
import io.github.vmarins2005.outbox.mensageria.MensagemPublicada;
import io.github.vmarins2005.outbox.mensageria.MensageriaEmMemoria;
import io.github.vmarins2005.outbox.pedidos.PedidoPago;
import io.github.vmarins2005.outbox.pedidos.RepositorioDePedidos;
import io.github.vmarins2005.outbox.pedidos.ServicoDePedidos;
import io.github.vmarins2005.outbox.pedidos.StatusPedido;
import io.github.vmarins2005.outbox.saida.LimpadorDeMensagens;
import io.github.vmarins2005.outbox.saida.MensagemDeSaida;
import io.github.vmarins2005.outbox.saida.PublicadorDeSaida;
import io.github.vmarins2005.outbox.saida.RepositorioDeMensagens;
import java.math.BigDecimal;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * O perfil {@code test} desliga o agendamento: o publicador so roda quando o teste manda.
 * Com o scheduler ligado, a asserção "a mensagem ainda esta pendente" viraria uma corrida
 * contra o relogio.
 */
@SpringBootTest
@ActiveProfiles("test")
class OutboxTest {

    @Autowired
    private ServicoDePedidos pedidos;

    @Autowired
    private PublicadorDeSaida publicador;

    @Autowired
    private LimpadorDeMensagens limpador;

    @Autowired
    private MensageriaEmMemoria mensageria;

    @Autowired
    private RepositorioDeMensagens mensagens;

    @Autowired
    private RepositorioDePedidos repositorioDePedidos;

    @Autowired
    private RepositorioDeFaturas faturas;

    @BeforeEach
    void limpar() {
        mensageria.limpar();
        mensagens.deleteAll();
    }

    @Nested
    @DisplayName("caminho normal")
    class CaminhoNormal {

        @Test
        @DisplayName("pagar grava pedido e mensagem na mesma transacao, sem tocar no broker")
        void pagarGravaMensagem() {
            String pedidoId = pedidos.criar("CLI-1", new BigDecimal("250.00"));

            pedidos.pagar(pedidoId);

            assertThat(repositorioDePedidos.findById(pedidoId).orElseThrow().status())
                    .isEqualTo(StatusPedido.PAGO);
            assertThat(mensagens.countByPublicadaEmIsNull()).isEqualTo(1);
            // O broker ainda nao foi chamado: a transacao de negocio nao depende dele.
            assertThat(mensageria.recebidas()).isEmpty();
        }

        @Test
        @DisplayName("o publicador entrega, marca como publicada e o consumidor emite a fatura")
        void publicadorEntrega() {
            String pedidoId = pedidos.criar("CLI-2", new BigDecimal("100.00"));
            pedidos.pagar(pedidoId);

            publicador.publicarPendentes();

            assertThat(mensageria.quantidadeRecebida(PedidoPago.TIPO)).isEqualTo(1);
            assertThat(publicador.pendentes()).isZero();
            assertThat(faturas.findByPedidoId(pedidoId)).isPresent();

            MensagemPublicada mensagem = mensageria.recebidas().getFirst();
            assertThat(mensagem.tipo()).isEqualTo(PedidoPago.TIPO);
            assertThat(mensagem.chaveDeParticao()).isEqualTo(pedidoId);
            assertThat(mensagem.payload()).contains(pedidoId).contains("100.00");
        }

        @Test
        @DisplayName("mensagem ja publicada nao volta ao broker na rodada seguinte")
        void naoRepublica() {
            String pedidoId = pedidos.criar("CLI-3", new BigDecimal("10.00"));
            pedidos.pagar(pedidoId);
            publicador.publicarPendentes();

            publicador.publicarPendentes();
            publicador.publicarPendentes();

            assertThat(mensageria.quantidadeRecebida(PedidoPago.TIPO)).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("broker indisponivel")
    class BrokerIndisponivel {

        @Test
        @DisplayName("o pagamento acontece normalmente mesmo com o broker fora do ar")
        void pagamentoNaoDependeDoBroker() {
            mensageria.derrubar();
            String pedidoId = pedidos.criar("CLI-4", new BigDecimal("500.00"));

            pedidos.pagar(pedidoId);

            assertThat(repositorioDePedidos.findById(pedidoId).orElseThrow().status())
                    .isEqualTo(StatusPedido.PAGO);
            assertThat(publicador.pendentes()).isEqualTo(1);
        }

        @Test
        @DisplayName("a falha na publicacao fica registrada e a mensagem continua pendente")
        void falhaRegistrada() {
            String pedidoId = pedidos.criar("CLI-5", new BigDecimal("77.00"));
            pedidos.pagar(pedidoId);
            mensageria.derrubar();

            publicador.publicarPendentes();

            MensagemDeSaida mensagem = mensagens.findByChaveDeParticaoOrderByCriadaEmAsc(pedidoId).getFirst();
            assertThat(mensagem.estaPendente()).isTrue();
            assertThat(mensagem.tentativas()).isEqualTo(1);
            assertThat(mensagem.ultimoErro()).contains("indisponivel");
        }

        @Test
        @DisplayName("quando o broker volta, a mensagem antiga e entregue sem nenhuma acao manual")
        void entregaDepoisQueOBrokerVolta() {
            String pedidoId = pedidos.criar("CLI-6", new BigDecimal("42.00"));
            pedidos.pagar(pedidoId);

            mensageria.derrubar();
            publicador.publicarPendentes();
            assertThat(publicador.pendentes()).isEqualTo(1);

            mensageria.restabelecer();
            publicador.publicarPendentes();

            assertThat(publicador.pendentes()).isZero();
            assertThat(faturas.findByPedidoId(pedidoId)).isPresent();
        }
    }

    @Nested
    @DisplayName("consumidor idempotente")
    class ConsumidorIdempotente {

        @Test
        @DisplayName("a mesma mensagem entregue duas vezes gera uma fatura so")
        void reentregaNaoDuplicaFatura() {
            String pedidoId = pedidos.criar("CLI-7", new BigDecimal("300.00"));
            pedidos.pagar(pedidoId);
            publicador.publicarPendentes();

            // Reentrega: e o que acontece se o processo cair depois de publicar no broker
            // e antes de marcar a linha como publicada. O outbox garante at-least-once,
            // entao isto nao e hipotese remota - e o comportamento normal.
            MensagemPublicada jaEntregue = mensageria.recebidas().getFirst();
            mensageria.publicar(jaEntregue);

            assertThat(mensageria.quantidadeRecebida(PedidoPago.TIPO)).isEqualTo(2);
            assertThat(faturas.countByPedidoId(pedidoId)).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("retencao")
    class Retencao {

        @Test
        @DisplayName("a limpeza remove publicadas antigas e nunca toca em pendente")
        void limpezaPreservaPendentes() {
            String publicado = pedidos.criar("CLI-8", new BigDecimal("15.00"));
            pedidos.pagar(publicado);
            publicador.publicarPendentes();

            mensageria.derrubar();
            String pendente = pedidos.criar("CLI-9", new BigDecimal("25.00"));
            pedidos.pagar(pendente);

            long removidas = limpador.remover(Duration.ZERO);

            assertThat(removidas).isEqualTo(1);
            assertThat(mensagens.findByChaveDeParticaoOrderByCriadaEmAsc(publicado)).isEmpty();
            // Pendente antiga e sintoma de mensagem envenenada; apagar esconderia o
            // problema em vez de resolve-lo.
            assertThat(mensagens.findByChaveDeParticaoOrderByCriadaEmAsc(pendente)).hasSize(1);
        }
    }
}
