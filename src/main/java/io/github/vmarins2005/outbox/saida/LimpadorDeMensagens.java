package io.github.vmarins2005.outbox.saida;

import java.time.Clock;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Apaga mensagens ja publicadas depois do periodo de retencao.
 *
 * <p>Sem isto, a tabela de saida cresce para sempre - e ela e das mais quentes do sistema,
 * porque toda escrita de negocio grava nela e o publicador a varre a cada meio segundo.
 * Uma tabela de outbox esquecida com dezenas de milhoes de linhas publicadas transforma a
 * consulta por pendentes num scan caro, e o problema aparece meses depois, como
 * "o sistema ficou lento". Ver ADR 0003.
 *
 * <p>Nunca apaga pendente: pendente antiga e sintoma de mensagem envenenada, e apagar
 * esconderia o problema.
 */
@Component
public class LimpadorDeMensagens {

    private static final Logger log = LoggerFactory.getLogger(LimpadorDeMensagens.class);

    private final RepositorioDeMensagens repositorio;
    private final Clock relogio;
    private final Duration retencao;

    LimpadorDeMensagens(RepositorioDeMensagens repositorio, Clock relogio,
                        @Value("${outbox.retencao:P7D}") Duration retencao) {
        this.repositorio = repositorio;
        this.relogio = relogio;
        this.retencao = retencao;
    }

    @Scheduled(cron = "${outbox.cron-de-limpeza:0 0 3 * * *}")
    public void limparPublicadasAntigas() {
        long removidas = remover(retencao);
        if (removidas > 0) {
            log.info("outbox: {} mensagens publicadas removidas apos {}", removidas, retencao);
        }
    }

    @Transactional
    public long remover(Duration retencao) {
        return repositorio.deleteByPublicadaEmIsNotNullAndPublicadaEmBefore(
                relogio.instant().minus(retencao));
    }
}
