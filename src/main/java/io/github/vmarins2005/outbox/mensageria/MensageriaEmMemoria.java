package io.github.vmarins2005.outbox.mensageria;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.stereotype.Component;

/**
 * Broker de mentira, com dois superpoderes que um broker de verdade nao tem: da para
 * derruba-lo por comando e da para ler tudo que ele recebeu.
 *
 * <p>Os dois existem para os testes conseguirem provar o que interessa - que uma queda do
 * broker nao perde evento, e que o consumidor aguenta receber a mesma mensagem duas vezes.
 */
@Component
public class MensageriaEmMemoria implements Mensageria {

    private final List<MensagemPublicada> recebidas = new CopyOnWriteArrayList<>();
    private final List<AssinanteDeMensagens> assinantes;

    private volatile boolean disponivel = true;

    MensageriaEmMemoria(List<AssinanteDeMensagens> assinantes) {
        this.assinantes = assinantes;
    }

    @Override
    public void publicar(MensagemPublicada mensagem) {
        if (!disponivel) {
            throw new FalhaNaMensageria("broker indisponivel");
        }
        recebidas.add(mensagem);
        assinantes.stream()
                .filter(assinante -> assinante.aceita(mensagem.tipo()))
                .forEach(assinante -> assinante.receber(mensagem));
    }

    public void derrubar() {
        disponivel = false;
    }

    public void restabelecer() {
        disponivel = true;
    }

    public List<MensagemPublicada> recebidas() {
        return List.copyOf(recebidas);
    }

    public long quantidadeRecebida(String tipo) {
        return recebidas.stream().filter(mensagem -> mensagem.tipo().equals(tipo)).count();
    }

    public void limpar() {
        recebidas.clear();
        disponivel = true;
    }
}
