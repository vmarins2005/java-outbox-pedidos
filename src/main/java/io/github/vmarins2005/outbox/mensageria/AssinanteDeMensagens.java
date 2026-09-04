package io.github.vmarins2005.outbox.mensageria;

public interface AssinanteDeMensagens {

    boolean aceita(String tipo);

    void receber(MensagemPublicada mensagem);
}
