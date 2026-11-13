ALTER TABLE movimentacoes_estoque ADD COLUMN pedido_id BIGINT;
ALTER TABLE movimentacoes_estoque ADD CONSTRAINT fk_movimentacoes_pedido FOREIGN KEY (pedido_id) REFERENCES pedidos (id);
ALTER TABLE movimentacoes_estoque DROP CONSTRAINT ck_movimentacoes_tipo;
ALTER TABLE movimentacoes_estoque ADD CONSTRAINT ck_movimentacoes_tipo CHECK (tipo IN ('ENTRADA', 'SAIDA'));
ALTER TABLE movimentacoes_estoque ADD CONSTRAINT ck_movimentacoes_vinculo_pedido CHECK (
    (tipo = 'ENTRADA' AND pedido_id IS NULL) OR (tipo = 'SAIDA' AND pedido_id IS NOT NULL)
);
ALTER TABLE movimentacoes_estoque ADD CONSTRAINT uk_saida_pedido_produto UNIQUE (pedido_id, produto_id);
