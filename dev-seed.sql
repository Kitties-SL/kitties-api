-- Datos de prueba para desarrollo local
-- Uso: psql -U <usuario> -d <base_de_datos> -f dev-seed.sql
--
-- Credenciales (todos los usuarios):
--   password: Password1!
--   hash bcrypt: $2a$10$kV0FosI9wx8frVmUeGfp.O4PJbqCE5b.nYq/cgaUu0BtO5eTkYOr6
--
-- Usuarios creados:
--   adoptante@kitti.es  — rol User        (adopter)
--   refugio@kitti.es    — rol Organization (admin de Protectora El Refugio)
--   felinos@kitti.es    — rol Organization (admin de Asociación Felinos del Norte)
--
-- NOTA: En dev, Hibernate gestiona el id internamente (sin DEFAULT en la columna).
-- Este script inserta con IDs fijos y avanza las sequences para evitar colisiones.
-- Requiere que las tablas estén vacías o que los IDs insertados no existan.

BEGIN;

-- ─── Usuarios ──────────────────────────────────────────────────────────────

INSERT INTO users.users (
    id, email, password_hash, name, surname,
    status, role, created_at, updated_at
) VALUES
    (1, 'adoptante@kitti.es', '$2a$10$kV0FosI9wx8frVmUeGfp.O4PJbqCE5b.nYq/cgaUu0BtO5eTkYOr6', 'Carlos',  'García',  'Active', 'User',         NOW(), NOW()),
    (2, 'refugio@kitti.es',   '$2a$10$kV0FosI9wx8frVmUeGfp.O4PJbqCE5b.nYq/cgaUu0BtO5eTkYOr6', 'Laura',   'Martínez','Active', 'Organization', NOW(), NOW()),
    (3, 'felinos@kitti.es',   '$2a$10$kV0FosI9wx8frVmUeGfp.O4PJbqCE5b.nYq/cgaUu0BtO5eTkYOr6', 'Marcos',  'López',   'Active', 'Organization', NOW(), NOW());

-- ─── Organizaciones ────────────────────────────────────────────────────────

INSERT INTO organization.organizations (
    id, name, description, address, city, region, country,
    phone, email, logo_url,
    status, plan, max_members,
    created_at, updated_at
) VALUES
    (
        1,
        'Protectora El Refugio',
        'Protectora de animales de la zona sur de Madrid. Llevamos más de 10 años rescatando y dando en adopción gatos y perros.',
        'Calle Mayor 1', 'Madrid', 'Comunidad de Madrid', 'España',
        '+34 910 000 001', 'info@elrefugio.org', NULL,
        'Active', 'Pro', -1,
        NOW(), NOW()
    ),
    (
        2,
        'Asociación Felinos del Norte',
        'Asociación sin ánimo de lucro especializada en la acogida y adopción responsable de gatos en el área metropolitana de Barcelona.',
        'Avinguda Diagonal 200', 'Barcelona', 'Cataluña', 'España',
        '+34 930 000 002', 'hola@felinosdelnorte.org', NULL,
        'Active', 'Pro', -1,
        NOW(), NOW()
    );

-- ─── Miembros de organización ──────────────────────────────────────────────

INSERT INTO organization.organization_members (
    id, organization_id, user_id, role, status, joined_at
) VALUES
    (1, 1, 2, 'Admin', 'Active', NOW()),
    (2, 2, 3, 'Admin', 'Active', NOW());

-- ─── Gatos — Protectora El Refugio (org_id=1, Madrid) ─────────────────────

INSERT INTO cats.cats (
    id, name, age, sex, description, neutered,
    status, profile_image_url,
    organization_id, city, region, country,
    latitude, longitude,
    created_at, updated_at
) VALUES
    ( 1, 'Luna',    2, 'Female', 'Gata tranquila y cariñosa, ideal para apartamento. Se lleva bien con otros gatos.',                     TRUE,  'Available', NULL, 1, 'Madrid', 'Comunidad de Madrid', 'España', 40.4168, -3.7038, NOW(), NOW()),
    ( 2, 'Mochi',   1, 'Male',   'Gatito juguetón y muy sociable, busca familia activa con niños.',                                        FALSE, 'Available', NULL, 1, 'Madrid', 'Comunidad de Madrid', 'España', 40.4168, -3.7038, NOW(), NOW()),
    ( 3, 'Nube',    3, 'Female', 'Gata adulta independiente, busca hogar tranquilo sin niños pequeños.',                                   TRUE,  'Available', NULL, 1, 'Madrid', 'Comunidad de Madrid', 'España', 40.4168, -3.7038, NOW(), NOW()),
    ( 4, 'Simba',   4, 'Male',   'Gato grande y tranquilo. Necesita ser el único animal en casa.',                                         TRUE,  'Available', NULL, 1, 'Madrid', 'Comunidad de Madrid', 'España', 40.4168, -3.7038, NOW(), NOW()),
    ( 5, 'Cleo',    2, 'Female', 'Muy cariñosa, sigue a las personas por toda la casa. Le encanta el sol.',                               TRUE,  'Available', NULL, 1, 'Madrid', 'Comunidad de Madrid', 'España', 40.4168, -3.7038, NOW(), NOW()),
    ( 6, 'Kiko',    1, 'Male',   'Gatito activo y curioso. Aprende trucos fácilmente. Necesita estimulación.',                             FALSE, 'Available', NULL, 1, 'Madrid', 'Comunidad de Madrid', 'España', 40.4168, -3.7038, NOW(), NOW()),
    ( 7, 'Miña',    5, 'Female', 'Gata mayor, muy serena. Perfecta para personas mayores o hogares tranquilos.',                          TRUE,  'Available', NULL, 1, 'Madrid', 'Comunidad de Madrid', 'España', 40.4168, -3.7038, NOW(), NOW()),
    ( 8, 'Tigre',   3, 'Male',   'Atigrado clásico con mucha personalidad. Tarda en coger confianza pero luego es muy fiel.',             TRUE,  'Available', NULL, 1, 'Madrid', 'Comunidad de Madrid', 'España', 40.4168, -3.7038, NOW(), NOW()),
    ( 9, 'Perla',   2, 'Female', 'Gata blanca de pelo largo. Necesita cepillado regular. Muy dócil.',                                     TRUE,  'Available', NULL, 1, 'Madrid', 'Comunidad de Madrid', 'España', 40.4168, -3.7038, NOW(), NOW()),
    (10, 'Gizmo',   1, 'Male',   'Curioso e intrépido, explora todo. Perfecto para casas con jardín.',                                    FALSE, 'Available', NULL, 1, 'Madrid', 'Comunidad de Madrid', 'España', 40.4168, -3.7038, NOW(), NOW()),
    (11, 'Sombra',  4, 'Male',   'Gato tímido con personas nuevas, pero muy cariñoso con su familia. Necesita paciencia inicial.',        TRUE,  'Available', NULL, 1, 'Madrid', 'Comunidad de Madrid', 'España', 40.4168, -3.7038, NOW(), NOW()),
    (12, 'Naranja', 2, 'Male',   'Anaranjado intenso, muy social y ruidoso. Maúlla para pedir atención constantemente.',                  FALSE, 'Available', NULL, 1, 'Madrid', 'Comunidad de Madrid', 'España', 40.4168, -3.7038, NOW(), NOW()),
    (13, 'Bianca',  3, 'Female', 'Gata elegante de pelaje bicolor. Se lleva bien con perros tranquilos.',                                 TRUE,  'Available', NULL, 1, 'Madrid', 'Comunidad de Madrid', 'España', 40.4168, -3.7038, NOW(), NOW()),
    (14, 'Zeus',    2, 'Male',   'Activo y juguetón, necesita juguetes e interacción diaria. No se recomienda dejarlo solo muchas horas.', FALSE, 'Available', NULL, 1, 'Madrid', 'Comunidad de Madrid', 'España', 40.4168, -3.7038, NOW(), NOW()),
    (15, 'Minerva', 1, 'Female', 'Cachorra pequeña y delicada. Busca hogar con experiencia en gatos.',                                    FALSE, 'Available', NULL, 1, 'Madrid', 'Comunidad de Madrid', 'España', 40.4168, -3.7038, NOW(), NOW());

-- ─── Gatos — Asociación Felinos del Norte (org_id=2, Barcelona) ───────────

INSERT INTO cats.cats (
    id, name, age, sex, description, neutered,
    status, profile_image_url,
    organization_id, city, region, country,
    latitude, longitude,
    created_at, updated_at
) VALUES
    (16, 'Aria',    3, 'Female', 'Gata elegante y vocal. Le encanta mirar por la ventana y que le cuenten cosas.',                        TRUE,  'Available', NULL, 2, 'Barcelona', 'Cataluña', 'España', 41.3851, 2.1734, NOW(), NOW()),
    (17, 'Bruno',   2, 'Male',   'Gato robusto y tranquilo. Se adapta bien a pisos pequeños.',                                            TRUE,  'Available', NULL, 2, 'Barcelona', 'Cataluña', 'España', 41.3851, 2.1734, NOW(), NOW()),
    (18, 'Carmen',  4, 'Female', 'Gata de exterior recuperada. Le gusta la libertad pero también el calor del hogar.',                    TRUE,  'Available', NULL, 2, 'Barcelona', 'Cataluña', 'España', 41.3851, 2.1734, NOW(), NOW()),
    (19, 'Dante',   1, 'Male',   'Gatito negro y vivaracho. Muy divertido, siempre está en medio de todo.',                               FALSE, 'Available', NULL, 2, 'Barcelona', 'Cataluña', 'España', 41.3851, 2.1734, NOW(), NOW()),
    (20, 'Estrella',2, 'Female', 'Dulce y apacible. Se lleva de maravilla con gatos y perros.',                                           TRUE,  'Available', NULL, 2, 'Barcelona', 'Cataluña', 'España', 41.3851, 2.1734, NOW(), NOW()),
    (21, 'Faust',   3, 'Male',   'Gato de carácter fuerte, fiel a su persona de referencia. No apto para hogares con muchos animales.',   TRUE,  'Available', NULL, 2, 'Barcelona', 'Cataluña', 'España', 41.3851, 2.1734, NOW(), NOW()),
    (22, 'Gala',    2, 'Female', 'Tranquila y ordenada. Perfecta para personas que trabajan desde casa.',                                  TRUE,  'Available', NULL, 2, 'Barcelona', 'Cataluña', 'España', 41.3851, 2.1734, NOW(), NOW()),
    (23, 'Héroe',   5, 'Male',   'Gato mayor con historia. Necesita una familia comprensiva que respete su ritmo.',                       TRUE,  'Available', NULL, 2, 'Barcelona', 'Cataluña', 'España', 41.3851, 2.1734, NOW(), NOW()),
    (24, 'Iris',    1, 'Female', 'Gatita con los ojos más azules de la protectora. Juguetona e incansable.',                              FALSE, 'Available', NULL, 2, 'Barcelona', 'Cataluña', 'España', 41.3851, 2.1734, NOW(), NOW()),
    (25, 'Jasper',  4, 'Male',   'Pelo largo atigrado. Le gusta estar en alto y observar. Muy observador e inteligente.',                 TRUE,  'Available', NULL, 2, 'Barcelona', 'Cataluña', 'España', 41.3851, 2.1734, NOW(), NOW()),
    (26, 'Kira',    2, 'Female', 'Sociable desde el primer día. Busca familia con niños para jugar sin parar.',                           TRUE,  'Available', NULL, 2, 'Barcelona', 'Cataluña', 'España', 41.3851, 2.1734, NOW(), NOW()),
    (27, 'Leo',     3, 'Male',   'Dorado y majestuoso. Muy cariñoso al despertar. Adora las mantas y los sofás.',                         TRUE,  'Available', NULL, 2, 'Barcelona', 'Cataluña', 'España', 41.3851, 2.1734, NOW(), NOW()),
    (28, 'Maya',    1, 'Female', 'Muy pequeña todavía pero con mucho carácter. Necesita adoptante con experiencia.',                      FALSE, 'Available', NULL, 2, 'Barcelona', 'Cataluña', 'España', 41.3851, 2.1734, NOW(), NOW()),
    (29, 'Nico',    2, 'Male',   'Energético y acrobático. Necesita espacio, rascadores y juguetes de actividad.',                        FALSE, 'Available', NULL, 2, 'Barcelona', 'Cataluña', 'España', 41.3851, 2.1734, NOW(), NOW()),
    (30, 'Oliva',   3, 'Female', 'Gata verde oliva de ojos. Serena, no invasiva. Ideal para pisos y personas independientes.',            TRUE,  'Available', NULL, 2, 'Barcelona', 'Cataluña', 'España', 41.3851, 2.1734, NOW(), NOW());

-- ─── Avanzar sequences de Hibernate ───────────────────────────────────────
-- setval(seq, n, false) → el próximo nextval() devolverá n

SELECT setval('users.users_seq',                          100, false);
SELECT setval('organization.organizations_seq',           100, false);
SELECT setval('organization.organization_members_seq',    100, false);
SELECT setval('cats.cats_seq',                            100, false);

COMMIT;