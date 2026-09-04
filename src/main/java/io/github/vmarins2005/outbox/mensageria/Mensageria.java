package io.github.vmarins2005.outbox.mensageria;

/**
 * Porta para o broker. A implementação real seria Kafka ou RabbitMQ; aqui e em memoria,
 * porque o assunto deste projeto e a garantia de entrega, e nao o broker.
 */
public interface Mensageria {

    void publicar(MensagemPublicada mensagem);
}
