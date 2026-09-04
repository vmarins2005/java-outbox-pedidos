package io.github.vmarins2005.outbox.mensageria;

public class FalhaNaMensageria extends RuntimeException {

    public FalhaNaMensageria(String mensagem) {
        super(mensagem);
    }
}
