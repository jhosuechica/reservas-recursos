# ADR-0001 · La regla de no solapamiento vive en el esquema

- **Estado:** Aceptada
- **Fecha:** 2026-09-07
- **Aplica a:** `V1__esquema.sql`, `ReservaService`, `TraductorDeRestricciones`

## Contexto

La regla que define este sistema es una sola: **dos reservas confirmadas del
mismo recurso no pueden solaparse en el tiempo.** Todo lo demás es un CRUD.

La forma habitual de implementarla es consultar antes de escribir:

```java
if (reservas.existeSolapamiento(recursoId, inicio, fin)) {
    throw new ConflictoDeReserva(...);
}
reservas.save(nueva);
```

Ese código parece correcto y funciona en las pruebas manuales. Tiene dos
problemas que solo aparecen cuando el sistema se usa de verdad.

**Una carrera entre la lectura y la escritura.** Dos peticiones simultáneas
para la misma sala consultan a la vez, las dos ven el hueco libre, y las dos
insertan. La ventana es de milisegundos, así que no se reproduce probando a
mano, pero existe siempre. Bajo el nivel de aislamiento por defecto de
PostgreSQL, `READ COMMITTED`, ninguna de las dos transacciones ve la fila que
la otra todavía no ha confirmado.

**Una via de escritura que se olvida la comprobación.** El día que se añada una
importación masiva, un proceso de migración o un endpoint nuevo, la regla hay
que volver a escribirla. Si alguien no lo hace, nada avisa: los datos quedan
mal y se descubre semanas después.

## Decisión

Expresar la regla como una **restricción de exclusión** en la tabla:

```sql
CREATE EXTENSION IF NOT EXISTS btree_gist;

ALTER TABLE reservas
    ADD CONSTRAINT reservas_sin_solape
    EXCLUDE USING gist (
        recurso_id                   WITH =,
        tstzrange(inicio, fin, '[)') WITH &&
    )
    WHERE (estado = 'CONFIRMADA');
```

Se lee: no pueden coexistir dos filas con el mismo `recurso_id` cuyos
intervalos se solapen.

El servicio **no comprueba nada antes de insertar**. Intenta la escritura y
traduce el rechazo:

```java
try {
    return reservas.saveAndFlush(reserva);
} catch (DataIntegrityViolationException excepcion) {
    throw TraductorDeRestricciones.traducir(excepcion);
}
```

`saveAndFlush` y no `save`: hay que enviar el INSERT dentro del `try`. Con
`save`, la violación salta al confirmar la transacción, ya fuera del bloque, y
no hay forma de convertirla en una respuesta útil.

La extensión `btree_gist` es imprescindible: permite combinar un operador de
igualdad sobre una columna escalar con el operador de solapamiento sobre un
rango dentro del mismo índice GiST.

## Alternativas consideradas

| Alternativa | Por qué se descartó |
|---|---|
| **Comprobar antes de insertar** | Es lo que se hace en la mayoría de proyectos y funciona casi siempre. Deja la carrera descrita arriba y obliga a repetir la regla en cada via de escritura nueva |
| Comprobar y además bloquear la fila del recurso con `SELECT ... FOR UPDATE` | Cierra la carrera de verdad, pero serializa todas las reservas del mismo recurso y sigue sin proteger de una via de escritura que olvide el bloqueo. Más complejidad para menos garantía |
| Subir el aislamiento a `SERIALIZABLE` | Resuelve la carrera, pero obliga a reintentar ante errores de serialización en toda la aplicación, y penaliza operaciones que no lo necesitan |
| Un `UNIQUE` sobre `(recurso_id, inicio)` | Solo impide dos reservas que empiecen en el mismo instante. No detecta que una de 9 a 11 choca con otra de 10 a 12, que es el caso normal |
| Un disparador `BEFORE INSERT` | Vive en la base de datos, pero es código imperativo que hay que leer para saber qué hace y que puede tener sus propias carreras. La restricción es declarativa y el planificador la respalda con un índice |

## Consecuencias

**Positivas**

- La regla no se puede eludir. Da igual si la escritura viene del servicio, de
  una migración o de alguien conectado con `psql`.
- Sin carreras, sin bloqueos explícitos y sin reintentos.
- El índice GiST que crea la restricción también sirve para consultar
  solapamientos.
- La regla se lee en un sitio, y ese sitio es donde están los datos.

**Negativas**

- **Ata el proyecto a PostgreSQL.** Las restricciones de exclusión no son
  estándar; migrar a otro motor obligaría a rehacer esto.
- **El error llega como una excepción de integridad genérica**, y hay que
  traducirlo a partir del nombre de la restricción. Ese nombre es ahora parte
  del contrato: renombrarlo en una migración rompe la traducción.
- **Hibernate no siempre extrae el nombre** en las violaciones de exclusión, así
  que el traductor tiene un camino alternativo que busca el nombre en el texto
  del error. Es frágil frente a un cambio de formato de mensajes de PostgreSQL.
- Comprobar la regla exige un motor real, así que las pruebas necesitan
  Testcontainers y no pueden usar una base en memoria.

**A vigilar**

- Si aparecieran reglas de disponibilidad más ricas (horarios de apertura,
  festivos, mantenimiento), una restricción de exclusión sola dejaría de
  bastar. La señal sería empezar a añadir columnas de estado solo para poder
  excluirlas en el `WHERE`.
- Si el volumen creciera hasta que el índice GiST pese, habría que revisar si
  conviene particionar por fecha.
