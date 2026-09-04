# ADR 0003 — Retenção e limpeza da tabela de saída

Status: aceito · 2026-09-04 · supera: —

## Contexto

A tabela de saída é das mais quentes do sistema: **toda** escrita de negócio grava nela, e
o publicador a varre a cada meio segundo. Sem política de retenção, ela cresce para sempre.

O problema não aparece na primeira semana. Aparece meses depois, como "o sistema ficou
lento", com a consulta de pendentes varrendo dezenas de milhões de linhas já publicadas
para encontrar as poucas que importam. É uma das dívidas mais previsíveis do padrão, e uma
das mais esquecidas.

## Alternativas

**1. Não apagar nunca.**
A tabela vira registro histórico de tudo que o sistema publicou — o que soa útil até
alguém calcular o custo de armazenamento e o tamanho do índice. Auditoria é um requisito
legítimo, mas resolvido por um destino próprio (data warehouse, tópico de log), e não
mantendo a tabela operacional inchada.

**2. Apagar assim que publica.**
Tabela sempre pequena e consulta sempre rápida. Perde-se a capacidade de investigar
"este evento foi publicado?" logo depois de um incidente — que é exatamente quando a
pergunta aparece. Perde-se também a possibilidade de republicar manualmente algo entregue
há minutos.

**3. Reter por um período e apagar em lote. — escolhida**

## Decisão

Publicadas são apagadas depois de `outbox.retencao` (padrão: 7 dias), por um job diário às
3h.

Sete dias porque é o horizonte em que alguém ainda investiga um incidente: "o cliente diz
que não recebeu a nota da semana passada" é uma pergunta que se responde consultando a
tabela; "de três meses atrás" já é pergunta para o histórico contábil.

**Pendentes nunca são apagadas, em nenhuma idade.** Uma mensagem pendente antiga é sintoma
— quase sempre payload envenenado que o broker recusa a cada tentativa. Apagar esconderia
o problema em vez de resolvê-lo, e o efeito de negócio correspondente ficaria perdido para
sempre. O teste `limpezaPreservaPendentes` fixa esse comportamento.

## Consequências

- \+ A tabela estabiliza num tamanho previsível: volume diário × 7.
- \+ Sete dias de janela para investigar incidente sem sair da tabela operacional.
- \+ Pendente antiga fica visível justamente por não ser apagada — é o alerta que se quer.
- − Apagar em lote gera carga: em volume alto, `DELETE` de milhões de linhas trava
  replicação e infla o log de transações. A partir de certo volume, o caminho é apagar em
  blocos com pausa, ou particionar a tabela por dia e descartar partições inteiras —
  operação muito mais barata. Não está implementado aqui, e é um dos exercícios.
- − O job é mais uma coisa que pode parar sem ninguém perceber. Deveria haver alerta sobre
  o tamanho da tabela, e não confiança de que o cron rodou. Não implementado — observabilidade
  é assunto de outro projeto da série.
- − Sete dias é um chute educado, não um número medido. O sinal para revisá-lo é o tamanho
  da tabela ou uma auditoria pedindo janela maior.
