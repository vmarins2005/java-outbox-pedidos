package io.github.vmarins2005.outbox.faturamento;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Registro de deduplicacao do consumidor.
 *
 * <p>Gravado na <b>mesma transacao</b> do efeito - aqui, a emissao da fatura. Se fosse
 * gravado em transacao separada, existiria a janela "fatura emitida, dedupe nao gravado",
 * e a reentrega emitiria a segunda nota: exatamente o problema que ele deveria evitar.
 */
@Entity
@Table(name = "mensagem_processada")
class MensagemProcessada {

    @Id
    @Column(name = "mensagem_id", length = 100)
    private String mensagemId;

    @Column(name = "processada_em", nullable = false)
    private Instant processadaEm;

    protected MensagemProcessada() {
        // exigido pelo JPA
    }

    MensagemProcessada(String mensagemId, Instant processadaEm) {
        this.mensagemId = mensagemId;
        this.processadaEm = processadaEm;
    }
}
