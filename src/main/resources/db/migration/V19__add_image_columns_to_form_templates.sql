ALTER TABLE form_templates


ADD COLUMN background_image LONGBLOB,
ADD COLUMN background_image_type VARCHAR(100),

ADD COLUMN header_image LONGBLOB,
ADD COLUMN header_image_type VARCHAR(100),

ADD COLUMN footer_image LONGBLOB,
ADD COLUMN footer_image_type VARCHAR(100);