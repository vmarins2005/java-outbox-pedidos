package io.github.vmarins2005.outbox.saida;

import io.github.vmarins2005.outbox.mensageria.MensagemPublicada;
import io.github.vmarins2005.outbox.mensageria.Mensageria;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Publica uma mensagem, em transacao propria.
 *
 * <p>Uma transacao por mensagem, e nao uma para o lote inteiro: com transacao unica, a
 * falha na quinta mensagem desfaria a marcacao das quatro que ja tinham sido entregues, e
 * elas seriam publicadas de novo na proxima rodada.
 */
@Component
class ProcessadorDeMensagem {

    private static final Logger log = LoggerFactory.getLogger(ProcessadorDeMensagem.class);

    private final RepositorioDeMensagens repositorio;
    private final Mensageria mensageria;
    private final Clock relogio;

    ProcessadorDeMensagem(RepositorioDeMensagens repositorio, Mensageria mensageria, Clock relogio) {
        this.repositorio = repositorio;
        this.mensageria = mensageria;
        this.relogio = relogio;
    }

    /**
     * @return {@code true} se a mensagem foi publicada nesta rodada.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    boolean publicar(String mensagemId) {
        MensagemDeSaida mensagem = repositorio.findById(mensagemId).orElse(null);
        if (mensagem == null || !mensagem.estaPendente()) {
            return false;
        }

        try {
            mensageria.publicar(new MensagemPublicada(
                    mensagem.id(), mensagem.tipo(), mensagem.chaveDeParticao(), mensagem.payload()));
        } catch (RuntimeException e) {
            // A falha nao propaga: a mensagem continua pendente e sera tentada de novo.
            // Registrar a tentativa e o erro e o que permite descobrir uma mensagem
            // envenenada antes que ela trave a fila por dias.
            mensagem.registrarFalha(e.getMessage());
            log.warn("falha ao publicar {} (tentativa {}): {}", mensagem.id(), mensagem.tentativas(), e.getMessage());
            return false;
        }

        // Se o processo cair EXATAMENTE aqui - publicado no broker, ainda nao marcado -
        // a mensagem sera publicada de novo na proxima rodada. E por isso que o outbox
        // garante at-least-once, e nao exactly-once, e por isso que o consumidor precisa
        // ser idempotente. Ver ADR 0000.
        mensagem.marcarPublicada(relogio.instant());
        return true;
    }
}
