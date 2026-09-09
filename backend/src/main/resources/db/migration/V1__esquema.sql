-- =============================================================================
-- Esquema de reservas de recursos.
--
-- La decision central del proyecto esta al final de este archivo: la regla de
-- "dos reservas confirmadas no pueden solaparse sobre el mismo recurso" se
-- expresa como una restriccion de exclusion, no como una comprobacion en el
-- servicio. Una comprobacion en codigo se puede saltar por una via nueva o
-- perder una carrera entre dos peticiones simultaneas. Una restriccion no.
-- =============================================================================

-- btree_gist permite combinar un operador de igualdad sobre una columna
-- escalar (recurso_id) con un operador de solapamiento sobre un rango dentro
-- del mismo indice GiST. Sin esta extension la restriccion de mas abajo no se
-- puede crear.
CREATE EXTENSION IF NOT EXISTS btree_gist;


-- ---------------------------------------------------------------------------
-- Recursos reservables: salas y equipos
-- ---------------------------------------------------------------------------
CREATE TABLE recursos (
    id        BIGSERIAL    PRIMARY KEY,
    codigo    VARCHAR(20)  NOT NULL,
    nombre    VARCHAR(120) NOT NULL,
    tipo      VARCHAR(10)  NOT NULL,
    capacidad INTEGER,
    ubicacion VARCHAR(120) NOT NULL,
    activo    BOOLEAN      NOT NULL DEFAULT TRUE,

    CONSTRAINT recursos_codigo_unico UNIQUE (codigo),

    CONSTRAINT recursos_tipo_valido
        CHECK (tipo IN ('SALA', 'EQUIPO')),

    -- La capacidad describe cuanta gente cabe, asi que solo tiene sentido en
    -- una sala. Exigirla en las salas y prohibirla en los equipos evita la
    -- columna a medio rellenar que nadie sabe como interpretar.
    CONSTRAINT recursos_capacidad_solo_en_salas
        CHECK (
            (tipo = 'SALA'   AND capacidad IS NOT NULL AND capacidad > 0)
         OR (tipo = 'EQUIPO' AND capacidad IS NULL)
        )
);

COMMENT ON TABLE recursos IS 'Salas y equipos que se pueden reservar';


-- ---------------------------------------------------------------------------
-- Reservas
-- ---------------------------------------------------------------------------
CREATE TABLE reservas (
    id          BIGSERIAL    PRIMARY KEY,
    recurso_id  BIGINT       NOT NULL,
    solicitante VARCHAR(120) NOT NULL,
    motivo      VARCHAR(200) NOT NULL,
    inicio      TIMESTAMPTZ  NOT NULL,
    fin         TIMESTAMPTZ  NOT NULL,
    estado      VARCHAR(12)  NOT NULL DEFAULT 'CONFIRMADA',
    creada_en   TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT reservas_recurso_fk
        FOREIGN KEY (recurso_id) REFERENCES recursos (id),

    CONSTRAINT reservas_estado_valido
        CHECK (estado IN ('CONFIRMADA', 'CANCELADA')),

    -- Un intervalo que termina antes de empezar no es un caso raro: es un dato
    -- imposible, y la base de datos es el sitio donde se rechaza.
    CONSTRAINT reservas_intervalo_valido
        CHECK (fin > inicio),

    -- Tope de duracion. Es una regla de negocio discutible, y por eso esta
    -- aqui: cambiarla es una migracion, que deja rastro, en vez de un valor
    -- suelto perdido en el codigo.
    CONSTRAINT reservas_duracion_maxima
        CHECK (fin - inicio <= INTERVAL '8 hours')
);

COMMENT ON TABLE reservas IS 'Reservas de un recurso durante un intervalo de tiempo';


-- ---------------------------------------------------------------------------
-- La regla que sostiene el proyecto
-- ---------------------------------------------------------------------------
-- Se lee: no pueden coexistir dos filas con el MISMO recurso_id (WITH =) cuyos
-- intervalos SE SOLAPEN (WITH &&).
--
-- El rango se construye como '[)': cerrado por la izquierda y abierto por la
-- derecha. Una reserva que termina a las 10:00 y otra que empieza a las 10:00
-- NO se solapan, que es como cuenta el tiempo la gente al reservar una sala.
-- Con '[]' el sistema rechazaria reservas consecutivas legitimas.
--
-- El WHERE la vuelve una restriccion parcial: solo se aplica a las reservas
-- confirmadas. Cancelar libera el hueco sin borrar la fila, asi que el
-- historico se conserva.
ALTER TABLE reservas
    ADD CONSTRAINT reservas_sin_solape
    EXCLUDE USING gist (
        recurso_id                       WITH =,
        tstzrange(inicio, fin, '[)')     WITH &&
    )
    WHERE (estado = 'CONFIRMADA');


-- La restriccion anterior ya crea un indice GiST, pero esta pensado para
-- resolver solapamientos. Las consultas de la aplicacion piden la agenda de un
-- recurso entre dos fechas, que es un recorrido ordenado: eso lo sirve mejor
-- un btree.
CREATE INDEX ix_reservas_recurso_inicio ON reservas (recurso_id, inicio);
