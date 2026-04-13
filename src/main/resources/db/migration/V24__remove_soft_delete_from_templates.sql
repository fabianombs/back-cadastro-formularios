SET FOREIGN_KEY_CHECKS = 0;
DELETE FROM form_templates WHERE deleted = true;
ALTER TABLE form_templates DROP COLUMN deleted;
SET FOREIGN_KEY_CHECKS = 1;
