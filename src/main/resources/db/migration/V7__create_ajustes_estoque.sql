-- Adiciona a coluna de justificativa para os ajustes de inventario
ALTER TABLE movimentacoes_estoque ADD COLUMN justificativa VARCHAR(500);

-- Atualiza os tipos permitidos de movimentacao de estoque
ALTER TABLE movimentacoes_estoque DROP CONSTRAINT ck_movimentacoes_tipo;
ALTER TABLE movimentacoes_estoque ADD CONSTRAINT ck_movimentacoes_tipo CHECK (tipo IN ('ENTRADA', 'SAIDA', 'AJUSTE_ENTRADA', 'AJUSTE_SAIDA'));

-- Garante que ajustes de estoque nao fiquem vinculados a pedidos
ALTER TABLE movimentacoes_estoque DROP CONSTRAINT ck_movimentacoes_vinculo_pedido;
ALTER TABLE movimentacoes_estoque ADD CONSTRAINT ck_movimentacoes_vinculo_pedido CHECK (
    (tipo = 'ENTRADA' AND pedido_id IS NULL) OR 
    (tipo = 'SAIDA' AND pedido_id IS NOT NULL) OR
    (tipo IN ('AJUSTE_ENTRADA', 'AJUSTE_SAIDA') AND pedido_id IS NULL)
);