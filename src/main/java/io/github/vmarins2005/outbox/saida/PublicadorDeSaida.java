package io.github.vmarins2005.outbox.saida;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Le a tabela de saida por polling e entrega ao broker.
 *
 * <p>Polling, e nao CDC - a comparacao com Debezium esta no ADR 0001. O intervalo define a
 * latencia minima do evento: 500 ms aqui significa que, no pior caso, o consumidor recebe
 * meio segundo depois do commit.
 */
@Component
public class PublicadorDeSaida {

    private static final Logger log = LoggerFactory.getLogger(PublicadorDeSaida.class);

    private final RepositorioDeMensagens repositorio;
    private final ProcessadorDeMensagem processador;

    PublicadorDeSaida(RepositorioDeMensagens repositorio, ProcessadorDeMensagem processador) {
        this.repositorio = repositorio;
        this.processador = processador;
    }

    @Scheduled(fixedDelayString = "${outbox.intervalo-de-publicacao:500}")
    public void publicarPendentes() {
        List<String> pendentes = idsPendentes();
        if (pendentes.isEmpty()) {
            return;
        }

        int publicadas = 0;
        for (String id : pendentes) {
            if (processador.publicar(id)) {
                publicadas++;
            }
        }
        log.info("outbox: {} de {} mensagens publicadas", publicadas, pendentes.size());
    }

    /**
     * Leitura em transacao propria e curta: segurar a transacao durante as chamadas ao
     * broker prenderia uma conexao do pool pelo tempo da rede.
     */
    @Transactional(readOnly = true)
    List<String> idsPendentes() {
        return repositorio.findTop50ByPublicadaEmIsNullOrderByCriadaEmAsc().stream()
                .map(MensagemDeSaida::id)
                .toList();
    }

    @Transactional(readOnly = true)
    public long pendentes() {
        return repositorio.countByPublicadaEmIsNull();
    }
}
