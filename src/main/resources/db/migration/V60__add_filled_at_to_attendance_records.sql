-- Data/hora em que a linha foi efetivamente preenchida (editada na tabela ou cadastrada pelo público).
-- NULL para linhas apenas importadas: a coluna "Preenchido em" fica em branco até alguém preencher.
ALTER TABLE attendance_records
    ADD COLUMN filled_at DATETIME NULL;
