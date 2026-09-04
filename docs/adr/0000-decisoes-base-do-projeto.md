# ADR 0000 — Decisões base do projeto

Status: aceito · 2026-09-04 · supera: —

## Contexto

O projeto existe para resolver um problema específico, e o problema precisa ser sentido
antes: **não existe transação entre o banco e o broker.** Salvar e publicar são duas
escritas em dois sistemas, e nenhuma ordem entre elas resolve.

- Publicar **depois** do commit: se o broker estiver fora ou o processo cair, o pedido
  fica pago e o evento nunca sai. Pior: não há registro de que faltou publicar, então não
  existe nem o que reprocessar.
- Publicar **antes** do commit: o broker recebe e a transação pode dar rollback. O evento
  passa a existir para um pedido que nunca foi pago, e o consumidor age sobre um fato que
  não aconteceu.

`PublicacaoIngenuaTest` demonstra o primeiro caso, e ele **passa quando a inconsistência
acontece** — é um teste que prova o problema, não a solução.

A saída do outbox: fazer as duas escritas no **mesmo** sistema transacional (o banco) e
entregar depois.

## Decisões

### 1. Sem broker real

`MensageriaEmMemoria` no lugar de Kafka. Ela tem dois superpoderes que broker de verdade
não tem — pode ser derrubada por comando e pode ser lida — e os dois existem para os
testes provarem o que interessa: queda não perde evento, e reentrega não duplica efeito.

Partição, offset, rebalance e DLQ são assunto do projeto de mensageria da série. Trocar
por Kafka afeta uma classe.

### 2. O outbox garante at-least-once, e isso não é ajustável

Se o processo cair entre publicar no broker e marcar a linha como publicada, a mensagem
sai de novo. Não existe configuração que elimine essa janela — ela é a consequência de
haver dois sistemas.

Portanto **o consumidor idempotente não é opcional**: é parte do padrão. `Faturador` tem
duas linhas de defesa, a tabela `mensagem_processada` e o `UNIQUE` em `fatura.pedido_id`.
A segunda existe porque a primeira não é atômica entre réplicas — a verificação em
aplicação perde a corrida, o banco não.

### 3. Uma transação por mensagem no publicador

Não uma para o lote. Com transação única, a falha na quinta mensagem desfaria a marcação
das quatro já entregues, e elas seriam publicadas de novo na rodada seguinte.

### 4. A falha ao publicar não propaga

`ProcessadorDeMensagem` captura a exceção, incrementa `tentativas` e grava `ultimo_erro`.
A mensagem continua pendente e é tentada de novo.

Registrar tentativa e erro é o que permite descobrir uma mensagem envenenada — payload que
nunca vai ser aceito — antes que ela trave a fila por dias. Sem esse contador, o sintoma
seria só "a fila não anda".

### 5. Perfil `test` desliga o agendamento

Publicador rodando em paralelo com o teste transformaria "a mensagem ainda está pendente"
numa corrida contra o relógio: passa na máquina rápida, falha no CI carregado. Nos testes
o publicador é chamado quando o cenário pede.

## Limitações conhecidas

- **Uma instância do publicador.** Com várias, duas leriam o mesmo lote e publicariam em
  duplicidade. Em Postgres a solução é `SELECT ... FOR UPDATE SKIP LOCKED`; o H2 não o
  suporta bem, e implementar isso aqui adicionaria complexidade sem tornar a demonstração
  mais clara. Está nos exercícios do README.
- **Ordem garantida apenas por chave de partição**, e só se o broker respeitar. A ordenação
  por `criada_em` no lote é condição necessária, não suficiente.
- **H2.** Banco real com Testcontainers é assunto de outro projeto da série.
