-- Datos de prueba para desarrollo local
-- Uso: psql -U <usuario> -d <base_de_datos> -f dev-seed.sql
--
-- NOTA: En dev, Hibernate gestiona el id internamente (sin DEFAULT en la columna).
-- Este script inserta con IDs fijos y avanza las sequences para evitar colisiones.
-- Requiere que las tablas estén vacías o que los IDs 1 y 1/2/3 no existan.

BEGIN;

-- Organización de prueba con id=1
INSERT INTO organization.organizations (
    id, name, description, address, city, region, country,
    phone, email, logo_url,
    status, plan, max_members,
    created_at, updated_at
) VALUES (
    1,
    'Protectora El Refugio',
    'Protectora de animales de la zona sur de Madrid',
    'Calle Mayor 1', 'Madrid', 'Comunidad de Madrid', 'España',
    '+34 910 000 001', 'info@elrefugio.org', NULL,
    'Active', 'Pro', -1,
    NOW(), NOW()
);

-- Tres gatos con ids 1, 2, 3 referenciando la org anterior
INSERT INTO cats.cats (
    id, name, age, sex, description, neutered,
    status, profile_image_url,
    organization_id, city, region, country,
    latitude, longitude,
    created_at, updated_at
) VALUES
    (1, 'Luna',  2, 'Female', 'Gata tranquila y cariñosa, ideal para apartamento',            TRUE,  'Available', NULL, 1, 'Madrid', 'Comunidad de Madrid', 'España', 40.4168, -3.7038, NOW(), NOW()),
    (2, 'Mochi', 1, 'Male',   'Gatito juguetón y muy sociable con personas',                  FALSE, 'Available', NULL, 1, 'Madrid', 'Comunidad de Madrid', 'España', 40.4168, -3.7038, NOW(), NOW()),
    (3, 'Nube',  3, 'Female', 'Gata adulta, busca hogar tranquilo sin niños pequeños',        TRUE,  'Available', NULL, 1, 'Madrid', 'Comunidad de Madrid', 'España', 40.4168, -3.7038, NOW(), NOW());

-- Avanzar las sequences de Hibernate para que el siguiente id generado no colisione.
-- Sustituye los nombres si difieren (consulta: SELECT schemaname, sequencename FROM pg_sequences WHERE schemaname IN ('organization','cats'))
SELECT setval('organization.organizations_seq', 100, false);
SELECT setval('cats.cats_seq', 100, false);

COMMIT;
