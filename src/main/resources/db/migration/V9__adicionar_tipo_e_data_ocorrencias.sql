ALTER TABLE pedido_ocorrencias
    ADD COLUMN tipo VARCHAR(30) NOT NULL DEFAULT 'ATRASO';

ALTER TABLE pedido_ocorrencias
    ADD COLUMN data_ocorrencia DATE NOT NULL DEFAULT CURRENT_DATE;

ALTER TABLE pedido_ocorrencias
    ADD CONSTRAINT ck_pedido_ocorrencias_tipo
    CHECK (tipo IN ('ATRASO', 'AVARIA', 'DESTINATARIO_AUSENTE'));