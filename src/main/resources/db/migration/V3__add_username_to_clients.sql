-- V3__add_username_to_clients.sql

ALTER TABLE clients
    ADD COLUMN username VARCHAR(100) UNIQUE AFTER company;