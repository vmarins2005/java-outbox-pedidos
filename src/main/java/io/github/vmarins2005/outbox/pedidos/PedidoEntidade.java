package io.github.vmarins2005.outbox.pedidos;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "pedido")
public class PedidoEntidade {

    @Id
    private String id;

    @Column(name = "cliente_id", nullable = false)
    private String clienteId;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal valor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private StatusPedido status;

    @Column(name = "pago_em")
    private Instant pagoEm;

    protected PedidoEntidade() {
        // exigido pelo JPA
    }

    PedidoEntidade(String clienteId, BigDecimal valor) {
        this.id = UUID.randomUUID().toString();
        this.clienteId = clienteId;
        this.valor = valor;
        this.status = StatusPedido.AGUARDANDO_PAGAMENTO;
    }

    void pagar(Instant quando) {
        if (status != StatusPedido.AGUARDANDO_PAGAMENTO) {
            throw new IllegalStateException("pedido em %s nao pode ser pago".formatted(status));
        }
        this.status = StatusPedido.PAGO;
        this.pagoEm = quando;
    }

    public String id() {
        return id;
    }

    public String clienteId() {
        return clienteId;
    }

    public BigDecimal valor() {
        return valor;
    }

    public StatusPedido status() {
        return status;
    }

    public Instant pagoEm() {
        return pagoEm;
    }
}
