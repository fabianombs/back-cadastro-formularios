-- Cor da borda dos campos do formulario (antes nao era persistida).
-- NULL = usar padrao: nenhum template existente muda.

ALTER TABLE form_templates
    ADD COLUMN field_border_color VARCHAR(50) NULL;
