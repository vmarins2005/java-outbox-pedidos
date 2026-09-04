package io.github.vmarins2005.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import io.github.vmarins2005.outbox.pedidos.RepositorioDePedidos;
import io.github.vmarins2005.outbox.pedidos.ServicoDePedidos;
import io.github.vmarins2005.outbox.pedidos.StatusPedido;
import io.github.vmarins2005.outbox.saida.MensagemDeSaida;
import io.github.vmarins2005.outbox.saida.RepositorioDeMensagens;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * A garantia central do padrao, verificada por injecao de falha.
 *
 * <p>Se a gravacao da mensagem falhar, o pedido <b>nao pode</b> ficar pago. Nao existe o
 * estado "pago sem evento" - e essa e a unica coisa que o outbox promete.
 *
 * <p>Este teste tambem e a rede de seguranca contra uma "otimizacao" plausivel: colocar
 * {@code @Transactional(REQUIRES_NEW)} em {@code RegistradorDeSaida}. Com transacao
 * separada, a mensagem comitaria sozinha, o pedido continuaria pago, e o teste abaixo
 * reprovaria - que e exatamente o que se quer que aconteca.
 */
@SpringBootTest
@ActiveProfiles("test")
class AtomicidadeDoOutboxTest {

    @MockitoBean
    private RepositorioDeMensagens mensagens;

    @Autowired
    private ServicoDePedidos pedidos;

    @Autowired
    private RepositorioDePedidos repositorioDePedidos;

    @Test
    @DisplayName("falha ao gravar a mensagem desfaz o pagamento: nao existe pago sem evento")
    void falhaNaMensagemDesfazOPagamento() {
        given(mensagens.save(any(MensagemDeSaida.class)))
                .willThrow(new RuntimeException("banco fora do ar ao gravar a saida"));

        String pedidoId = pedidos.criar("CLI-ATOMICO", new BigDecimal("199.00"));

        assertThatThrownBy(() -> pedidos.pagar(pedidoId))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("banco fora do ar");

        assertThat(repositorioDePedidos.findById(pedidoId).orElseThrow().status())
                .isEqualTo(StatusPedido.AGUARDANDO_PAGAMENTO);
    }
}
