package io.github.vmarins2005.outbox.saida;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RepositorioDeMensagens extends JpaRepository<MensagemDeSaida, String> {

    /**
     * Lote limitado e ordenado por criacao. Ordenar por {@code criada_em} preserva a ordem
     * em que os fatos aconteceram; publicar tudo de uma vez, sem limite, transformaria um
     * acumulo de fila em uma transacao gigante.
     */
    List<MensagemDeSaida> findTop50ByPublicadaEmIsNullOrderByCriadaEmAsc();

    List<MensagemDeSaida> findByChaveDeParticaoOrderByCriadaEmAsc(String chaveDeParticao);

    long countByPublicadaEmIsNull();

    long deleteByPublicadaEmIsNotNullAndPublicadaEmBefore(Instant limite);
}
