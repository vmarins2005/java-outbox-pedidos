package io.github.vmarins2005.outbox.faturamento;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.vmarins2005.outbox.mensageria.AssinanteDeMensagens;
import io.github.vmarins2005.outbox.mensageria.MensagemPublicada;
import io.github.vmarins2005.outbox.pedidos.PedidoPago;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consumidor idempotente.
 *
 * <p>O outbox garante <b>at-least-once</b>, e nao exactly-once: se o processo cair depois
 * de publicar no broker e antes de marcar a linha como publicada, a mesma mensagem sai de
 * novo. Nao existe configuracao que elimine isso - a unica saida e o consumidor aguentar
 * receber duas vezes.
 *
 * <p>Duas linhas de defesa, de proposito:
 *
 * <ol>
 *   <li>a tabela {@code mensagem_processada}, verificada antes de agir;</li>
 *   <li>{@code UNIQUE} em {@code fatura.pedido_id}, que segura o caso em que duas replicas
 *       do consumidor passam pela verificacao ao mesmo tempo - a checagem em aplicacao nao
 *       e atomica, o banco e.</li>
 * </ol>
 */
@Component
public class Faturador implements AssinanteDeMensagens {

    private static final Logger log = LoggerFactory.getLogger(Faturador.class);

    private final RepositorioDeFaturas faturas;
    private final RepositorioDeProcessadas processadas;
    private final ObjectMapper json;
    private final Clock relogio;

    Faturador(RepositorioDeFaturas faturas, RepositorioDeProcessadas processadas,
              ObjectMapper json, Clock relogio) {
        this.faturas = faturas;
        this.processadas = processadas;
        this.json = json;
        this.relogio = relogio;
    }

    @Override
    public boolean aceita(String tipo) {
        return PedidoPago.TIPO.equals(tipo);
    }

    @Override
    @Transactional
    public void receber(MensagemPublicada mensagem) {
        if (processadas.existsById(mensagem.id())) {
            log.info("mensagem {} ja processada, ignorando reentrega", mensagem.id());
            return;
        }

        PedidoPago evento = desserializar(mensagem);
        faturas.save(new FaturaEntidade(evento.pedidoId(), evento.valor(), relogio.instant()));
        processadas.save(new MensagemProcessada(mensagem.id(), relogio.instant()));

        log.info("fatura emitida para o pedido {}", evento.pedidoId());
    }

    private PedidoPago desserializar(MensagemPublicada mensagem) {
        try {
            return json.readValue(mensagem.payload(), PedidoPago.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("payload invalido na mensagem " + mensagem.id(), e);
        }
    }
}
