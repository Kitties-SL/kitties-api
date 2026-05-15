-- Datos de prueba para desarrollo local
-- Uso: psql -U <usuario> -d <base_de_datos> -f dev-seed.sql
-- Crea 1 organización y 3 gatos disponibles en un único bloque atómico.

BEGIN;

WITH nueva_org AS (
    INSERT INTO organization.organizations (
        name, description, address, city, region, country,
        phone, email, logo_url,
        status, plan, max_members,
        created_at, updated_at
    ) VALUES (
        'Protectora El Refugio',
        'Protectora de animales de la zona sur de Madrid',
        'Calle Mayor 1', 'Madrid', 'Comunidad de Madrid', 'España',
        '+34 910 000 001', 'info@elrefugio.org', NULL,
        'Active', 'Pro', -1,
        NOW(), NOW()
    )
    RETURNING id
)
INSERT INTO cats.cats (
    name, age, sex, description, neutered,
    status, profile_image_url,
    organization_id, city, region, country,
    latitude, longitude,
    created_at, updated_at
)
SELECT
    v.name, v.age, v.sex, v.description, v.neutered,
    'Available', NULL,
    nueva_org.id, 'Madrid', 'Comunidad de Madrid', 'España',
    40.4168, -3.7038,
    NOW(), NOW()
FROM nueva_org
CROSS JOIN (VALUES
    ('Luna',  2, 'Female', 'Gata tranquila y cariñosa, ideal para apartamento',               TRUE),
    ('Mochi', 1, 'Male',   'Gatito juguetón y muy sociable con personas',                      FALSE),
    ('Nube',  3, 'Female', 'Gata adulta, busca hogar tranquilo sin niños pequeños',            TRUE)
) AS v(name, age, sex, description, neutered);

COMMIT;