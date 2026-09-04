package io.github.vmarins2005.outbox.saida;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import org.springframework.stereotype.Component;

/**
 * Grava a mensagem na tabela de saida.
 *
 * <p><b>Este componente nao abre transacao propria, e isso e a decisao inteira.</b> Ele
 * roda dentro da transacao de quem o chamou, junto com a alteracao do agregado. Colocar
 * {@code @Transactional(REQUIRES_NEW)} aqui destruiria o padrao: a mensagem comitaria
 * separado do pedido, e voltariam a existir os dois estados inconsistentes que o outbox
 * elimina - pedido sem evento, e evento sem pedido.
 *
 * <p>Se alguem "otimizar" isso um dia, o teste {@code AtomicidadeDoOutboxTest} reprova.
 */
@Component
public class RegistradorDeSaida {

    private final RepositorioDeMensagens repositorio;
    private final ObjectMapper json;
    private final Clock relogio;

    RegistradorDeSaida(RepositorioDeMensagens repositorio, ObjectMapper json, Clock relogio) {
        this.repositorio = repositorio;
        this.json = json;
        this.relogio = relogio;
    }

    public void registrar(String tipo, String chaveDeParticao, Object payload) {
        repositorio.save(new MensagemDeSaida(tipo, chaveDeParticao, serializar(payload), relogio.instant()));
    }

    private String serializar(Object payload) {
        try {
            return json.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            // Falhar aqui derruba a transacao inteira, e e o comportamento certo:
            // melhor nao gravar o pedido do que grava-lo sabendo que o evento nao sai.
            throw new IllegalStateException("nao foi possivel serializar o evento: " + payload, e);
        }
    }
}
