# Outbox — o evento sai se, e somente se, o commit aconteceu

Projeto de estudo do **padrão outbox**, construído na ordem que ensina: primeiro o jeito
que parece natural e perde eventos, depois o que não perde.

Quinto de uma série em que cada repositório isola um conceito.

## O problema, antes da solução

Não existe transação entre o banco e o broker. São duas escritas em dois sistemas, e
**nenhuma ordem entre elas resolve**:

| Ordem | O que quebra |
| --- | --- |
| commit, depois publicar | broker fora do ar ou processo morto → pedido pago, evento nunca sai, e **nenhum registro de que faltou publicar** |
| publicar, depois commit | broker recebe e a transação dá rollback → evento de um pedido que nunca foi pago, e o consumidor age sobre um fato que não aconteceu |

`PublicacaoIngenuaTest` demonstra o primeiro caso. Ele **passa quando a inconsistência
acontece** — é um teste que prova o problema, não a solução. Vale rodar e ler antes de
olhar o resto:

```java
assertThat(pedido.status()).isEqualTo(StatusPedido.PAGO);   // o banco diz que pagou
assertThat(mensageria.quantidadeRecebida(TIPO)).isZero();   // o evento não existe
assertThat(repositorioDeMensagens.count()).isZero();        // e não há o que reprocessar
```

A terceira asserção é a que dói: não é só que o evento se perdeu. É que **ninguém tem como
descobrir quais pedidos ficaram sem evento** — nem em uma hora, nem em um mês.

## A saída

Fazer as duas escritas no **mesmo** sistema transacional, e entregar depois.

```
┌─ transação ────────────────────────────┐
│  UPDATE pedido SET status = 'PAGO'     │
│  INSERT INTO mensagem_de_saida (...)   │   ← mesma transação
└────────────────────────────────────────┘
                    │  commit
                    ▼
          publicador (polling, 500ms)
                    │
                    ▼
                 broker  →  consumidor idempotente
```

Se o processo cair em qualquer ponto, o banco fica consistente: ou as duas escritas
aconteceram, ou nenhuma.

## Como rodar

```bash
./mvnw test
```

11 testes. Sem Maven instalado (wrapper versionado), sem container, sem broker. Só JDK 21.

## O que os testes provam

| Teste | O que garante |
| --- | --- |
| `PublicacaoIngenuaTest` | o problema existe, e não tem rastro |
| `AtomicidadeDoOutboxTest` | falha ao gravar a mensagem **desfaz o pagamento** — não existe "pago sem evento" |
| `pagamentoNaoDependeDoBroker` | o cliente paga normalmente com o broker fora do ar |
| `entregaDepoisQueOBrokerVolta` | broker volta, mensagem antiga é entregue, sem ação manual |
| `falhaRegistrada` | tentativas e último erro ficam gravados — mensagem envenenada fica visível |
| `reentregaNaoDuplicaFatura` | consumidor idempotente: mesma mensagem duas vezes, uma fatura |
| `limpezaPreservaPendentes` | a limpeza apaga publicadas antigas e **nunca** toca em pendente |

`AtomicidadeDoOutboxTest` também é rede de segurança contra uma "otimização" plausível:
colocar `@Transactional(REQUIRES_NEW)` em `RegistradorDeSaida`. Com transação separada a
mensagem comitaria sozinha, o pedido continuaria pago, e o teste reprovaria — que é
exatamente o que se quer.

## At-least-once não é ajustável

Se o processo cair **entre** publicar no broker e marcar a linha como publicada, a mensagem
sai de novo. Não existe configuração que elimine essa janela: ela é consequência de haver
dois sistemas.

Por isso o consumidor idempotente **não é opcional** — é parte do padrão. `Faturador` tem
duas linhas de defesa:

1. a tabela `mensagem_processada`, verificada antes de agir;
2. `UNIQUE` em `fatura.pedido_id`, para o caso de duas réplicas passarem pela verificação
   ao mesmo tempo. A checagem em aplicação não é atômica; o banco é.

## Decisões registradas

| ADR | Assunto |
| --- | --- |
| [0000](docs/adr/0000-decisoes-base-do-projeto.md) | Escopo, at-least-once e limitações conhecidas |
| [0001](docs/adr/0001-outbox-com-polling-em-vez-de-cdc-com-debezium.md) | Polling em vez de CDC com Debezium |
| [0002](docs/adr/0002-payload-do-evento-completo-ou-so-o-identificador.md) | Payload completo ou só o identificador |
| [0003](docs/adr/0003-retencao-e-limpeza-da-tabela-de-saida.md) | Retenção e limpeza da tabela |

O ADR 0001 é o mais útil em entrevista: a comparação com Debezium não é técnica, é sobre
quem opera a coisa às três da manhã — slot de replicação parado enche o disco do banco.

## Limitações assumidas

- **Uma instância do publicador.** Com várias, duas leriam o mesmo lote e publicariam em
  duplicidade. Em Postgres se resolve com `SELECT ... FOR UPDATE SKIP LOCKED`; o H2 não
  suporta bem, e implementar aqui adicionaria complexidade sem tornar a demonstração mais
  clara. É o exercício 1.
- **A limpeza apaga em lote.** Em volume alto, `DELETE` de milhões de linhas trava
  replicação e infla o log de transações. O caminho é apagar em blocos ou particionar por
  dia. É o exercício 3.
- **Sem broker real, sem banco real.** Mensageria e Testcontainers são outros projetos da
  série. Trocar por Kafka afeta uma classe.
- **Sem alerta sobre a fila.** Pendentes acumulando é o sintoma mais importante do sistema
  e ninguém é avisado. Observabilidade é outro projeto.

## Exercícios

1. **Suba duas instâncias do publicador** e veja a duplicação acontecer. Depois resolva com
   `SKIP LOCKED` — em Postgres, porque o H2 não vai ajudar. É a diferença entre o padrão
   funcionar na sua máquina e funcionar em produção com três réplicas.
2. **Crie uma mensagem envenenada** (payload que o consumidor sempre recusa) e observe o
   contador de tentativas subir para sempre. Agora decida: quantas tentativas antes de
   marcar como morta? Escreva o ADR.
3. **Meça a limpeza com um milhão de linhas.** Depois implemente em blocos e compare o
   tempo de bloqueio.
4. **Troque o polling por Debezium.** Você vai descobrir que a aplicação quase não muda — o
   publicador some. É essa a prova de que o ADR 0001 pode ser revertido sem reescrever nada.

## Uma armadilha que vale conhecer pelo nome

`PagamentoDoPedido` é um bean separado só por um motivo: `@Transactional` em método
chamado de dentro da mesma classe **não passa pelo proxy do Spring** e simplesmente não
abre transação. A anotação fica ali, decorativa, e ninguém percebe.

Chama-se auto-invocação, e se o método morasse dentro de `ServicoIngenuoDePedidos` a
demonstração falharia pelo motivo errado.

## Regras de trabalho neste repositório

- `RegistradorDeSaida` **nunca** abre transação própria. É a decisão inteira do padrão.
- Consumidor novo nasce idempotente, com a chave de deduplicação gravada na mesma
  transação do efeito.
- Commits atômicos: cada commit compila e passa nos testes sozinho.

## O que eu faria diferente

_A preencher depois de usar._
