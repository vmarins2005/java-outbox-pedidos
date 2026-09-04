# ADR 0002 — Payload do evento: completo ou só o identificador

Status: aceito · 2026-09-04 · supera: —

## Contexto

`PedidoPago` carrega `pedidoId`, `clienteId`, `valor` e `pagoEm`. A alternativa comum é
publicar só `{"pedidoId": "..."}` e deixar o consumidor buscar o resto.

A escolha define o contrato entre serviços por anos, e mudá-la depois exige coordenar
todos os assinantes.

## Alternativas

**1. Só o identificador (*thin event* / notificação).**

Payload mínimo, contrato mínimo. O consumidor chama o produtor para saber os detalhes.

O problema é que **isso recria o acoplamento que o evento deveria remover**: todo
assinante passa a depender de a API do produtor estar de pé no momento em que processa.
Um evento processado com atraso de horas consulta um estado que já mudou, e o consumidor
age sobre dados diferentes dos que existiam quando o fato aconteceu. Para "pedido pago",
isso é grave: o valor pode ter sido alterado por um estorno.

Some-se a isso a explosão de chamadas — dez assinantes viram dez consultas por evento.

**2. O agregado inteiro serializado.**

O consumidor tem tudo. E o contrato do evento passa a ser o modelo interno do produtor:
qualquer refatoração de campo quebra assinantes que nem sabiam que existiam. É a forma
mais rápida de transformar mensageria em acoplamento distribuído.

**3. O que o negócio precisa para reagir. — escolhida**

## Decisão

O evento carrega **os dados do fato**, e não o estado do agregado: quem, quanto, quando.

O critério prático: *se um assinante razoável precisar consultar o produtor para agir,
falta dado no evento; se um campo só existe porque está no modelo, sobra.*

Para `PedidoPago`, faturamento precisa de valor e cliente para emitir a nota. Não precisa
dos itens — se um dia precisar, o campo entra com essa justificativa registrada.

Duas decisões que acompanham:

- **O evento carrega o `id` da linha do outbox**, que viaja até o consumidor como chave de
  deduplicação. Sem ele, o consumidor não tem como distinguir "a mesma mensagem de novo"
  de "outra mensagem igual". É o que torna a idempotência implementável.
- **A `chaveDeParticao` é o id do agregado**, para que dois eventos do mesmo pedido cheguem
  na ordem em que aconteceram quando isto virar Kafka.

## Consequências

- \+ O assinante age sem consultar o produtor: nada de acoplamento temporal.
- \+ O evento é um registro histórico fiel — descreve o que era verdade quando aconteceu,
  e não o que é verdade agora.
- \+ Reprocessar a fila meses depois produz o mesmo resultado.
- − O payload duplica dados que existem no produtor. É duplicação deliberada, pelo mesmo
  motivo do preço copiado no item de pedido: **não é o mesmo dado**, é o dado naquele
  instante.
- − Adicionar campo é fácil; remover exige coordenar assinantes. Versionamento de schema e
  compatibilidade são assunto do projeto de evolução de schema da série.
- − Payload maior. Irrelevante neste volume; para eventos com muitos itens, a conversa
  muda e vira "claim check" — evento com ponteiro para um armazenamento externo.
