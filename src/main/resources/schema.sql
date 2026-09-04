CREATE TABLE IF NOT EXISTS pedido (
    id         VARCHAR(64)    NOT NULL PRIMARY KEY,
    cliente_id VARCHAR(64)    NOT NULL,
    valor      DECIMAL(12, 2) NOT NULL,
    status     VARCHAR(30)    NOT NULL,
    pago_em    TIMESTAMP
);

-- A tabela de saida. Escrita na mesma transacao do agregado; lida pelo publicador.
CREATE TABLE IF NOT EXISTS mensagem_de_saida (
    id                VARCHAR(64)   NOT NULL PRIMARY KEY,
    tipo              VARCHAR(100)  NOT NULL,
    chave_de_particao VARCHAR(100)  NOT NULL,
    payload           VARCHAR(4000) NOT NULL,
    criada_em         TIMESTAMP     NOT NULL,
    publicada_em      TIMESTAMP,
    tentativas        INT           NOT NULL,
    ultimo_erro       VARCHAR(500)
);

-- Indice parcial seria o ideal (WHERE publicada_em IS NULL): a consulta do publicador so
-- olha pendentes, e num sistema saudavel eles sao uma fracao minima da tabela. H2 nao
-- suporta indice parcial; em Postgres, este indice deveria ter o WHERE.
CREATE INDEX IF NOT EXISTS idx_saida_pendentes ON mensagem_de_saida (publicada_em, criada_em);

CREATE TABLE IF NOT EXISTS fatura (
    numero     VARCHAR(32)    NOT NULL PRIMARY KEY,
    pedido_id  VARCHAR(64)    NOT NULL UNIQUE,
    valor      DECIMAL(12, 2) NOT NULL,
    emitida_em TIMESTAMP      NOT NULL
);

-- Deduplicacao do consumidor, gravada na mesma transacao do efeito.
CREATE TABLE IF NOT EXISTS mensagem_processada (
    mensagem_id   VARCHAR(100) NOT NULL PRIMARY KEY,
    processada_em TIMESTAMP    NOT NULL
);
