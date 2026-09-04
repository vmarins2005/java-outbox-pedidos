# ADR 0001 — Outbox com polling em vez de CDC com Debezium

Status: aceito · 2026-09-04 · supera: —

## Contexto

A tabela de saída resolve a atomicidade. Falta decidir **como** as linhas chegam ao
broker. As duas famílias de resposta são polling pela aplicação e captura de mudança
(CDC) lendo o log de transações do banco.

A escolha quase nunca é técnica: é sobre quem opera a coisa às três da manhã.

## Alternativas

**1. Polling pela própria aplicação. — escolhida**

Um `@Scheduled` lê pendentes, publica e marca. Tudo no mesmo artefato, na mesma linguagem,
no mesmo deploy.

**2. CDC com Debezium lendo o WAL do Postgres.**

O conector lê o log de transações, detecta a inserção na tabela de saída e publica no
Kafka sem a aplicação participar.

Vantagens reais, e não pequenas:
- **latência menor** — não há intervalo de polling;
- **não gera carga de leitura** no banco a cada meio segundo;
- **não perde nada em queda da aplicação**, porque quem lê é o conector;
- escala melhor com volume alto de eventos.

Custos, também reais:
- **Kafka Connect em produção**: mais um cluster para operar, monitorar e atualizar;
- **configuração de replicação lógica** no Postgres — `wal_level=logical`, slot de
  replicação, permissões. Slot de replicação parado enche o disco do banco e derruba o
  cluster inteiro. É um modo de falha novo, e dos ruins;
- **acoplamento ao banco**: o conector depende do schema físico da tabela;
- **depuração distribuída**: "o evento não chegou" passa a ter três lugares para
  investigar em vez de um.

**3. Um serviço publicador separado, lendo a mesma tabela.**

Isola a publicação da aplicação de negócio, ao custo de mais um deploy e de dois sistemas
escrevendo na mesma tabela.

## Decisão

Polling pela aplicação, com intervalo configurável (`outbox.intervalo-de-publicacao`,
500 ms por padrão).

O critério: **este sistema não tem o volume nem a exigência de latência que justifiquem
operar Debezium.** Meio segundo de latência é irrelevante para emitir nota fiscal, e uma
consulta indexada a cada 500 ms é carga desprezível.

O sinal objetivo de que a decisão precisa ser revista:

- a consulta de pendentes aparecer no *top* de queries do banco;
- a latência de meio segundo passar a incomodar alguém de negócio;
- o volume crescer a ponto de o lote de 50 não vazer a fila;
- já existir Kafka Connect operado pelo time — aí o custo marginal do Debezium cai muito,
  e o cálculo se inverte.

Nenhum desses é o caso hoje. **A migração é possível sem mudar a aplicação**: a tabela de
saída continua a mesma, e o publicador some.

## Consequências

- \+ Um artefato, uma linguagem, um lugar para depurar.
- \+ O intervalo é um parâmetro, e a latência é uma decisão explícita.
- \+ Funciona em qualquer banco, sem exigir replicação lógica.
- − Carga de leitura constante, mesmo com a fila vazia. Mitigada pelo índice, e pelo fato
  de que a consulta sai cedo quando não há pendentes.
- − Latência mínima igual ao intervalo.
- − O publicador dentro da aplicação é mais um motivo para a aplicação estar de pé. Com
  ela fora do ar, nada é publicado — a diferença crucial em relação à publicação ingênua é
  que **nada se perde**: a fila espera.
- − Não escala para várias instâncias sem `SKIP LOCKED`. Ver limitações no ADR 0000.
