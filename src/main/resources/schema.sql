-- Explicitly drop all tables on every startup so Hibernate recreates them fresh.
-- This runs BEFORE Hibernate (ddl-auto: create) due to defer-datasource-initialization=true.
DROP TABLE IF EXISTS companies;
DROP TABLE IF EXISTS users;
