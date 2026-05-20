-- Adiciona suporte a acompanhantes por convidado na lista de presença.
-- companions_count representa quantos acompanhantes o convidado trouxe ao evento.
ALTER TABLE attendance_records
    ADD COLUMN companions_count INT NOT NULL DEFAULT 0;
