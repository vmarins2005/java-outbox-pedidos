package io.github.vmarins2005.outbox.faturamento;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "fatura")
public class FaturaEntidade {

    @Id
    private String numero;

    @Column(name = "pedido_id", nullable = false, unique = true)
    private String pedidoId;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal valor;

    @Column(name = "emitida_em", nullable = false)
    private Instant emitidaEm;

    protected FaturaEntidade() {
        // exigido pelo JPA
    }

    FaturaEntidade(String pedidoId, BigDecimal valor, Instant emitidaEm) {
        this.numero = "NF-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        this.pedidoId = pedidoId;
        this.valor = valor;
        this.emitidaEm = emitidaEm;
    }

    public String numero() {
        return numero;
    }

    public String pedidoId() {
        return pedidoId;
    }

    public BigDecimal valor() {
        return valor;
    }

    public Instant emitidaEm() {
        return emitidaEm;
    }
}
