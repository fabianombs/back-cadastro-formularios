-- Cor de fundo dos cards/tabelas/filtros (independente do fundo da página)
SET @s1 = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'form_templates' AND COLUMN_NAME = 'card_background_color') = 0,
    'ALTER TABLE form_templates ADD COLUMN card_background_color VARCHAR(50)',
    'SELECT 1'
);
PREPARE st FROM @s1; EXECUTE st; DEALLOCATE PREPARE st;

-- Cor da borda dos cards/tabelas
SET @s2 = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'form_templates' AND COLUMN_NAME = 'card_border_color') = 0,
    'ALTER TABLE form_templates ADD COLUMN card_border_color VARCHAR(50)',
    'SELECT 1'
);
PREPARE st FROM @s2; EXECUTE st; DEALLOCATE PREPARE st;
