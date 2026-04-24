-- Corrige FK de form_templates.client_id: apontava para users(id) por engano (V4),
-- deve referenciar clients(id).
--
-- Estratégia sem perda de dados:
--  1. Dropa a FK antiga.
--  2. Remapeia client_id (que hoje guarda, em registros antigos, um users.id) para
--     o clients.id correspondente via clients.user_id. Só remapeia linhas cujo
--     client_id atual NÃO é um clients.id válido, para não mexer nos registros
--     que já estavam corretos.
--  3. Remove órfãos residuais (sem correspondência em clients nem via user_id) —
--     são registros impossíveis de recuperar.
--  4. Recria a FK apontando para clients(id).

ALTER TABLE form_templates DROP FOREIGN KEY fk_form_template_client;

UPDATE form_templates ft
  JOIN clients c ON c.user_id = ft.client_id
   SET ft.client_id = c.id
 WHERE ft.client_id NOT IN (SELECT id FROM clients);

DELETE FROM form_templates
 WHERE client_id NOT IN (SELECT id FROM clients);

ALTER TABLE form_templates
    ADD CONSTRAINT fk_form_template_client
        FOREIGN KEY (client_id) REFERENCES clients(id)
        ON DELETE CASCADE;
