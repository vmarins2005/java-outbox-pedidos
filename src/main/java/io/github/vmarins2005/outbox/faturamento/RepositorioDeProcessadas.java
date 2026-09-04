package io.github.vmarins2005.outbox.faturamento;

import org.springframework.data.jpa.repository.JpaRepository;

interface RepositorioDeProcessadas extends JpaRepository<MensagemProcessada, String> {
}
