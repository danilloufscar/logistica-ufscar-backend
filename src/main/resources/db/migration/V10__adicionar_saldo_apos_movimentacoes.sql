-- RF12: saldo do produto logo apos cada movimentacao, exibido no historico
ALTER TABLE movimentacoes_estoque ADD COLUMN saldo_apos INTEGER;

-- Preenche os registros existentes. O saldo nasce zerado (V5) e so muda por movimentacoes,
-- entao o saldo apos cada uma e a soma acumulada ate ela, na ordem em que foram gravadas.
UPDATE movimentacoes_estoque m
SET saldo_apos = (
    SELECT SUM(CASE WHEN x.tipo IN ('ENTRADA', 'AJUSTE_ENTRADA') THEN x.quantidade ELSE -x.quantidade END)
    FROM movimentacoes_estoque x
    WHERE x.produto_id = m.produto_id
      AND (x.registrado_em < m.registrado_em OR (x.registrado_em = m.registrado_em AND x.id <= m.id))
);
