package io.github.vmarins2005.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.vmarins2005.outbox.mensageria.FalhaNaMensageria;
import io.github.vmarins2005.outbox.mensageria.MensageriaEmMemoria;
import io.github.vmarins2005.outbox.pedidos.PedidoPago;
import io.github.vmarins2005.outbox.pedidos.RepositorioDePedidos;
import io.github.vmarins2005.outbox.pedidos.ServicoDePedidos;
import io.github.vmarins2005.outbox.pedidos.ServicoIngenuoDePedidos;
import io.github.vmarins2005.outbox.pedidos.StatusPedido;
import io.github.vmarins2005.outbox.saida.RepositorioDeMensagens;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Este teste existe para <b>provar o problema</b>, e nao para verificar uma solucao.
 *
 * <p>Ele passa quando a inconsistencia acontece. E o ponto de partida do projeto: sem
 * sentir isto, o outbox parece cerimonia desnecessaria.
 */
@SpringBootTest
@ActiveProfiles("test")
class PublicacaoIngenuaTest {

    @Autowired
    private ServicoDePedidos pedidos;

    @Autowired
    private ServicoIngenuoDePedidos ingenuo;

    @Autowired
    private MensageriaEmMemoria mensageria;

    @Autowired
    private RepositorioDePedidos repositorioDePedidos;

    @Autowired
    private RepositorioDeMensagens repositorioDeMensagens;

    @BeforeEach
    void limpar() {
        mensageria.limpar();
        repositorioDeMensagens.deleteAll();
    }

    @Test
    @DisplayName("broker fora do ar: o pedido fica pago e o evento nunca sai")
    void eventoPerdidoQuandoOBrokerCai() {
        String pedidoId = pedidos.criar("CLI-1", new BigDecimal("250.00"));
        mensageria.derrubar();

        assertThatThrownBy(() -> ingenuo.pagarSemOutbox(pedidoId))
                .isInstanceOf(FalhaNaMensageria.class);

        // O banco diz que o cliente pagou...
        assertThat(repositorioDePedidos.findById(pedidoId).orElseThrow().status())
                .isEqualTo(StatusPedido.PAGO);

        // ...e o evento nao existe em lugar nenhum.
        assertThat(mensageria.quantidadeRecebida(PedidoPago.TIPO)).isZero();
        assertThat(repositorioDeMensagens.count()).isZero();
    }

    @Test
    @DisplayName("nao ha o que reprocessar: o sistema nao registrou que faltou publicar")
    void naoHaRastroParaReprocessar() {
        String pedidoId = pedidos.criar("CLI-2", new BigDecimal("99.90"));
        mensageria.derrubar();

        assertThatThrownBy(() -> ingenuo.pagarSemOutbox(pedidoId));

        // Esta e a parte que dói. Nao e so que o evento se perdeu: nao existe nenhum
        // registro de que ele deveria ter saido. Passada a excecao, ninguem tem como
        // descobrir quais pedidos ficaram sem evento - nem em uma hora, nem em um mes.
        assertThat(repositorioDeMensagens.count()).isZero();

        mensageria.restabelecer();
        assertThat(mensageria.recebidas()).isEmpty();
    }
}
