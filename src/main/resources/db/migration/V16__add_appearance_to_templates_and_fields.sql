-- Add visual customization columns to form_templates
SET @s1 = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'form_templates' AND COLUMN_NAME = 'background_color') = 0,
    'ALTER TABLE form_templates ADD COLUMN background_color VARCHAR(50)', 'SELECT 1');
PREPARE st FROM @s1; EXECUTE st; DEALLOCATE PREPARE st;

SET @s2 = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'form_templates' AND COLUMN_NAME = 'background_gradient') = 0,
    'ALTER TABLE form_templates ADD COLUMN background_gradient VARCHAR(255)', 'SELECT 1');
PREPARE st FROM @s2; EXECUTE st; DEALLOCATE PREPARE st;

SET @s3 = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'form_templates' AND COLUMN_NAME = 'background_image_url') = 0,
    'ALTER TABLE form_templates ADD COLUMN background_image_url VARCHAR(1000)', 'SELECT 1');
PREPARE st FROM @s3; EXECUTE st; DEALLOCATE PREPARE st;

SET @s4 = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'form_templates' AND COLUMN_NAME = 'header_image_url') = 0,
    'ALTER TABLE form_templates ADD COLUMN header_image_url VARCHAR(1000)', 'SELECT 1');
PREPARE st FROM @s4; EXECUTE st; DEALLOCATE PREPARE st;

SET @s5 = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'form_templates' AND COLUMN_NAME = 'footer_image_url') = 0,
    'ALTER TABLE form_templates ADD COLUMN footer_image_url VARCHAR(1000)', 'SELECT 1');
PREPARE st FROM @s5; EXECUTE st; DEALLOCATE PREPARE st;

SET @s6 = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'form_templates' AND COLUMN_NAME = 'primary_color') = 0,
    'ALTER TABLE form_templates ADD COLUMN primary_color VARCHAR(50)', 'SELECT 1');
PREPARE st FROM @s6; EXECUTE st; DEALLOCATE PREPARE st;

SET @s7 = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'form_templates' AND COLUMN_NAME = 'form_text_color') = 0,
    'ALTER TABLE form_templates ADD COLUMN form_text_color VARCHAR(50)', 'SELECT 1');
PREPARE st FROM @s7; EXECUTE st; DEALLOCATE PREPARE st;

SET @s8 = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'form_templates' AND COLUMN_NAME = 'field_background_color') = 0,
    'ALTER TABLE form_templates ADD COLUMN field_background_color VARCHAR(50)', 'SELECT 1');
PREPARE st FROM @s8; EXECUTE st; DEALLOCATE PREPARE st;

SET @s9 = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'form_templates' AND COLUMN_NAME = 'field_text_color') = 0,
    'ALTER TABLE form_templates ADD COLUMN field_text_color VARCHAR(50)', 'SELECT 1');
PREPARE st FROM @s9; EXECUTE st; DEALLOCATE PREPARE st;

-- Add per-field custom color to form_fields
SET @s10 = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'form_fields' AND COLUMN_NAME = 'field_color') = 0,
    'ALTER TABLE form_fields ADD COLUMN field_color VARCHAR(50)', 'SELECT 1');
PREPARE st FROM @s10; EXECUTE st; DEALLOCATE PREPARE st;
