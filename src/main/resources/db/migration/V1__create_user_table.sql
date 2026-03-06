CREATE TABLE users (

                       id BIGINT PRIMARY KEY AUTO_INCREMENT,

                       name VARCHAR(150) NOT NULL,
                       email VARCHAR(150) UNIQUE NOT NULL,
                       username VARCHAR(100) UNIQUE NOT NULL,
                       password VARCHAR(255) NOT NULL,

                       role VARCHAR(50) NOT NULL,

                       active BOOLEAN DEFAULT TRUE,

                       created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);