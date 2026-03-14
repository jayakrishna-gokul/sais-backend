ALTER TABLE users ADD COLUMN IF NOT EXISTS must_change_password BOOLEAN NOT NULL DEFAULT FALSE;

-- Force first admin to change password
UPDATE users SET must_change_password = TRUE WHERE role = 'ADMIN';