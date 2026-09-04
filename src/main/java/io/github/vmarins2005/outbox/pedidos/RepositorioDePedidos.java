package io.github.vmarins2005.outbox.pedidos;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RepositorioDePedidos extends JpaRepository<PedidoEntidade, String> {
}
