-- Insert roles (without specifying IDs - let Hibernate generate them)
INSERT INTO roles (name, description) VALUES ('USER', 'Normal user role');
INSERT INTO roles (name, description) VALUES ('ADMIN', 'Administrator role');

-- Insert admin user (password: admin123)
-- Using MERGE to avoid duplicate key issues
MERGE INTO users (username, email, password, full_name, is_active, created_at, updated_at)
    KEY(username)
    VALUES ('admin', 'admin@example.com', '$2a$10$rQKoZkZqQkQkQkQkQkQkQkQkQkQkQkQkQkQk', 'System Admin', true, NOW(), NOW());

-- Insert test user (password: user123)
MERGE INTO users (username, email, password, full_name, is_active, created_at, updated_at)
    KEY(username)
    VALUES ('testuser', 'test@example.com', '$2a$10$sQKoZkZqQkQkQkQkQkQkQkQkQkQkQkQkQkQk', 'Test User', true, NOW(), NOW());

-- Assign admin role to admin user
MERGE INTO user_roles (user_id, role_id)
    KEY(user_id, role_id)
SELECT u.id, r.id FROM users u, roles r
WHERE u.username = 'admin' AND r.name = 'ADMIN';

-- Assign user role to test user
MERGE INTO user_roles (user_id, role_id)
    KEY(user_id, role_id)
SELECT u.id, r.id FROM users u, roles r
WHERE u.username = 'testuser' AND r.name = 'USER';