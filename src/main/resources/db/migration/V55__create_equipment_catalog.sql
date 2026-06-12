-- Catálogo de equipamentos importado da 2ª planilha (ex: celulares).
-- Cada template pode ter N catálogos; cada catálogo tem N opções (modelos).
CREATE TABLE equipment_catalogs (
    id                  BIGINT       NOT NULL AUTO_INCREMENT,
    form_template_id    BIGINT       NOT NULL,
    name                VARCHAR(255) NOT NULL,
    -- Coluna da planilha usada para extrair os valores (apenas informativo)
    source_column       VARCHAR(255) NULL,
    -- Liga/desliga o controle de disponibilidade (estoque)
    stock_control       BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    CONSTRAINT fk_equipment_catalog_template FOREIGN KEY (form_template_id)
        REFERENCES form_templates(id) ON DELETE CASCADE
);

-- Opções do catálogo (valores distintos da coluna escolhida).
-- total_qty = quantidade disponível (usado só quando stock_control = TRUE).
-- used_count = quantos já foram atribuídos; disponível = total_qty - used_count.
CREATE TABLE equipment_options (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    catalog_id  BIGINT       NOT NULL,
    label       VARCHAR(255) NOT NULL,
    total_qty   INT          NOT NULL DEFAULT 0,
    used_count  INT          NOT NULL DEFAULT 0,

    PRIMARY KEY (id),
    CONSTRAINT fk_equipment_option_catalog FOREIGN KEY (catalog_id)
        REFERENCES equipment_catalogs(id) ON DELETE CASCADE
);

-- Índice para busca rápida por trecho do nome dentro de um catálogo (autocomplete).
CREATE INDEX idx_equipment_options_catalog_label ON equipment_options (catalog_id, label);
