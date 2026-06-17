-- Tamanho base da fonte da lista de presença (preset escolhido pelo cliente: SMALL/MEDIUM/LARGE/XLARGE).
-- Padrão 'MEDIUM': preserva eventos existentes sem mudança brusca.
ALTER TABLE form_templates
    ADD COLUMN attendance_font_scale VARCHAR(16) NOT NULL DEFAULT 'MEDIUM';
