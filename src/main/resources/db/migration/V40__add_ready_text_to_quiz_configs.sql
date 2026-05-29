-- Campos de texto editáveis para a tela "Tudo pronto!" do quiz
-- Quando null, o frontend usa valores padrão hardcoded
ALTER TABLE quiz_configs ADD COLUMN ready_title   VARCHAR(120);
ALTER TABLE quiz_configs ADD COLUMN ready_message VARCHAR(255);
