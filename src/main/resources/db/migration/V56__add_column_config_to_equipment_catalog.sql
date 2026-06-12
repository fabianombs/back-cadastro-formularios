-- A coluna de select na lista de presenca e definida pelo proprio catalogo:
--   column_key  -> chave estavel gravada no rowData de cada registro
--   visible     -> mostra/oculta a coluna no controle do admin
ALTER TABLE equipment_catalogs
    ADD COLUMN column_key VARCHAR(255) NULL AFTER name,
    ADD COLUMN visible    BOOLEAN      NOT NULL DEFAULT TRUE AFTER stock_control;
