INSERT INTO users.users (id, email, password_hash, name, surname, status, role, created_at, updated_at)
VALUES
  (9001, 'alice@kitti.es', '$2a$10$test_hash_alice', 'Alice', 'Test', 'Active', 'User', NOW(), NOW()),
  (9002, 'bob@kitti.es',   '$2a$10$test_hash_bob',   'Bob',   'Test', 'Active', 'User', NOW(), NOW());
