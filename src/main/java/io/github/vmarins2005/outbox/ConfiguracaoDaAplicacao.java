package io.github.vmarins2005.outbox;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
class ConfiguracaoDaAplicacao {

    @Bean
    Clock relogio() {
        return Clock.systemUTC();
    }

    /**
     * O agendamento fica desligado no perfil de teste.
     *
     * <p>Publicador rodando em paralelo com o teste e a receita para uma suite que passa
     * na maquina rapida e falha no CI: a asserção "a mensagem ainda esta pendente"
     * dependeria de o scheduler nao ter passado por ali. Nos testes, o publicador e
     * chamado de proposito, quando o cenario pede.
     */
    @Configuration
    @EnableScheduling
    @Profile("!test")
    static class Agendamento {
    }
}
