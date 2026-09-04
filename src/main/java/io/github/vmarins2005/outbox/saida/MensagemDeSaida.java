package io.github.vmarins2005.outbox.saida;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Uma linha na tabela de saida.
 *
 * <p>O ponto inteiro do padrao esta em onde esta linha e gravada: <b>na mesma transacao</b>
 * que altera o agregado. Ou os dois vao para o banco, ou nenhum vai. Nao existe o estado
 * "pedido pago mas evento nao registrado", que e a inconsistencia que
 * {@code PublicacaoIngenuaTest} demonstra.
 */
@Entity
@Table(name = "mensagem_de_saida")
public class MensagemDeSaida {

    @Id
    private String id;

    @Column(nullable = false, length = 100)
    private String tipo;

    /**
     * Identidade do agregado que gerou a mensagem. Vira chave de particao no broker: e o
     * que garante que dois eventos do mesmo pedido cheguem na ordem em que aconteceram.
     */
    @Column(name = "chave_de_particao", nullable = false, length = 100)
    private String chaveDeParticao;

    @Column(nullable = false, length = 4000)
    private String payload;

    @Column(name = "criada_em", nullable = false)
    private Instant criadaEm;

    @Column(name = "publicada_em")
    private Instant publicadaEm;

    @Column(nullable = false)
    private int tentativas;

    @Column(name = "ultimo_erro", length = 500)
    private String ultimoErro;

    protected MensagemDeSaida() {
        // exigido pelo JPA
    }

    MensagemDeSaida(String tipo, String chaveDeParticao, String payload, Instant criadaEm) {
        this.id = UUID.randomUUID().toString();
        this.tipo = tipo;
        this.chaveDeParticao = chaveDeParticao;
        this.payload = payload;
        this.criadaEm = criadaEm;
        this.tentativas = 0;
    }

    void marcarPublicada(Instant quando) {
        this.publicadaEm = quando;
        this.ultimoErro = null;
    }

    void registrarFalha(String erro) {
        this.tentativas++;
        this.ultimoErro = erro != null && erro.length() > 500 ? erro.substring(0, 500) : erro;
    }

    public boolean estaPendente() {
        return publicadaEm == null;
    }

    public String id() {
        return id;
    }

    public String tipo() {
        return tipo;
    }

    public String chaveDeParticao() {
        return chaveDeParticao;
    }

    public String payload() {
        return payload;
    }

    public Instant criadaEm() {
        return criadaEm;
    }

    public Instant publicadaEm() {
        return publicadaEm;
    }

    public int tentativas() {
        return tentativas;
    }

    public String ultimoErro() {
        return ultimoErro;
    }
}
