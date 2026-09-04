package io.github.vmarins2005.outbox.faturamento;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RepositorioDeFaturas extends JpaRepository<FaturaEntidade, String> {

    Optional<FaturaEntidade> findByPedidoId(String pedidoId);

    long countByPedidoId(String pedidoId);
}
