package io.github.vmarins2005.outbox.mensageria;

/**
 * O que chega ao broker.
 *
 * <p>{@code id} e o identificador da linha do outbox e viaja com a mensagem: e a chave de
 * deduplicacao do consumidor. Sem ele, o consumidor nao tem como distinguir "a mesma
 * mensagem de novo" de "outra mensagem igual".
 *
 * <p>{@code chaveDeParticao} existe para o dia em que isto virar Kafka: mensagens do mesmo
 * agregado precisam ir para a mesma particao, senao a ordem entre elas se perde.
 */
public record MensagemPublicada(String id, String tipo, String chaveDeParticao, String payload) {
}
