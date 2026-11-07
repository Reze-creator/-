-- Only for a new environment. Current computer has already been initialized.
CREATE DATABASE IF NOT EXISTS parking CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin;
CREATE DATABASE IF NOT EXISTS parking_test CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin;
-- Create a dedicated application account separately, grant access only to these two schemas,
-- and put its credentials in the ignored .env. Flyway creates business tables on first startup.
