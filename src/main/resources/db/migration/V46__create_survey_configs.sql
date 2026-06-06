CREATE TABLE survey_configs (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    name              VARCHAR(255) NOT NULL,
    slug              VARCHAR(255) NOT NULL UNIQUE,
    company_name      VARCHAR(255),
    company_logo_url  TEXT,
    welcome_title     VARCHAR(500) NOT NULL DEFAULT 'Como foi sua experiência?',
    question_text     VARCHAR(500) NOT NULL DEFAULT 'Quão satisfeito você está com nosso serviço hoje?',
    show_comment      BOOLEAN NOT NULL DEFAULT FALSE,
    thank_you_msg     VARCHAR(500) NOT NULL DEFAULT 'Muito obrigado pela sua avaliação!',
    active            BOOLEAN NOT NULL DEFAULT TRUE,
    created_at        TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
