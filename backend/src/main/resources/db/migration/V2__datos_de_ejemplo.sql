-- =============================================================================
-- Datos de ejemplo.
--
-- Las reservas se colocan relativas al dia en que se aplica la migracion, para
-- que la agenda tenga contenido el primer dia sin depender de una fecha fija
-- que envejece.
--
-- Se anclan al inicio del dia en una zona horaria concreta y no en UTC. El
-- motor corre en UTC, asi que sembrar "las 9" sin mas coloca las reservas a las
-- 09:00 UTC, y un navegador en America las enseña de madrugada. El dato seria
-- correcto y la demostracion ilegible. Es una decision solo de los datos de
-- ejemplo: la aplicacion guarda y devuelve instantes con zona, y cada cliente
-- los muestra en la suya.
-- =============================================================================

INSERT INTO recursos (codigo, nombre, tipo, capacidad, ubicacion, activo) VALUES
    ('SALA-A',    'Sala de juntas Norte',   'SALA',     12, 'Piso 3',       TRUE),
    ('SALA-B',    'Sala de reuniones Sur',  'SALA',      6, 'Piso 2',       TRUE),
    ('SALA-C',    'Auditorio',              'SALA',     80, 'Planta baja',  TRUE),
    ('EQ-PROY-1', 'Proyector portatil',     'EQUIPO', NULL, 'Almacen TI',   TRUE),
    ('EQ-CAM-1',  'Camara de video',        'EQUIPO', NULL, 'Almacen TI',   TRUE),
    ('EQ-PORT-2', 'Portatil de prestamo',   'EQUIPO', NULL, 'Almacen TI',   FALSE);


-- Las dos ultimas filas son deliberadas: una reserva CANCELADA y otra
-- CONFIRMADA sobre el mismo recurso y el mismo horario. Solo pueden convivir
-- porque la restriccion de exclusion es parcial y no mira las canceladas.
-- Si alguien le quitara el WHERE, esta migracion dejaria de aplicarse, y ese
-- es justo el aviso que se busca.
INSERT INTO reservas (recurso_id, solicitante, motivo, inicio, fin, estado)
SELECT r.id,
       v.solicitante,
       v.motivo,
       date_trunc('day', now() AT TIME ZONE zona.nombre) AT TIME ZONE zona.nombre + v.desde,
       date_trunc('day', now() AT TIME ZONE zona.nombre) AT TIME ZONE zona.nombre + v.hasta,
       v.estado
FROM (VALUES
    ('SALA-A',    'Ana Torres',   'Comite de calidad',        INTERVAL  '9 hours', INTERVAL '10 hours 30 minutes', 'CONFIRMADA'),
    ('SALA-A',    'Bruno Mena',   'Entrevista tecnica',       INTERVAL '11 hours', INTERVAL '12 hours',            'CONFIRMADA'),
    ('SALA-B',    'Carla Rojas',  'Planificacion trimestral', INTERVAL  '9 hours', INTERVAL '13 hours',            'CONFIRMADA'),
    ('SALA-C',    'Ana Torres',   'Charla de seguridad',      INTERVAL '15 hours', INTERVAL '17 hours',            'CONFIRMADA'),
    -- Mismo horario que el comite de calidad, pero otro recurso: permitido.
    ('EQ-PROY-1', 'Ana Torres',   'Comite de calidad',        INTERVAL  '9 hours', INTERVAL '10 hours 30 minutes', 'CONFIRMADA'),
    ('EQ-CAM-1',  'Bruno Mena',   'Grabacion de la charla',   INTERVAL '15 hours', INTERVAL '17 hours',            'CONFIRMADA'),
    -- Mismo recurso y mismo horario: conviven porque una esta cancelada.
    ('SALA-B',    'Bruno Mena',   'Reunion anulada',          INTERVAL '14 hours', INTERVAL '15 hours',            'CANCELADA'),
    ('SALA-B',    'Carla Rojas',  'Revision de indicadores',  INTERVAL '14 hours', INTERVAL '15 hours',            'CONFIRMADA')
) AS v (codigo, solicitante, motivo, desde, hasta, estado)
CROSS JOIN (SELECT 'America/Guayaquil' AS nombre) AS zona
JOIN recursos r ON r.codigo = v.codigo;
