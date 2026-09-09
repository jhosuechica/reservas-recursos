# ADR-0002 · Dos columnas de instante en lugar de una columna de rango

- **Estado:** Aceptada
- **Fecha:** 2026-09-07
- **Aplica a:** `V1__esquema.sql`, `Reserva`

## Contexto

PostgreSQL tiene un tipo nativo para intervalos de tiempo, `tstzrange`. Guardar
la reserva en una sola columna de ese tipo sería lo más directo, y la
restricción de exclusión del [ADR-0001](0001-la-regla-vive-en-el-esquema.md)
podría usarla tal cual, sin construir nada.

El problema aparece en la capa de persistencia. Hibernate no mapea `tstzrange`
a ningún tipo de Java estándar. Usarlo obliga a escribir un tipo personalizado,
o a añadir una biblioteca de terceros que los aporte.

## Decisión

Guardar **dos columnas `TIMESTAMPTZ`**, `inicio` y `fin`, y construir el rango
dentro de la restricción:

```sql
EXCLUDE USING gist (
    recurso_id                   WITH =,
    tstzrange(inicio, fin, '[)') WITH &&
)
```

La expresión `tstzrange(inicio, fin, '[)')` es inmutable, así que puede formar
parte de un índice. La base de datos obtiene el rango que necesita sin que la
tabla tenga que almacenarlo.

En Java las dos columnas son `OffsetDateTime`, que Hibernate mapea de serie.

## Alternativas consideradas

| Alternativa | Por qué se descartó |
|---|---|
| **Una columna `tstzrange`** | Es la representación más fiel y la que usaría un sistema solo de base de datos. Exige un tipo personalizado de Hibernate o una dependencia externa, para un beneficio que aquí es de forma, no de función |
| Una dependencia que aporte tipos de rango | Resuelve el mapeo, pero añade una biblioteca al proyecto entero para una sola columna |
| Guardar `inicio` y `duracion` | El solapamiento habría que calcularlo en cada consulta y la restricción se complica sin ganar nada |
| Guardar los instantes sin zona (`TIMESTAMP`) | Dos reservas correctas pueden parecer solapadas al cambiar la hora o al reservar desde otra zona horaria |

## Consecuencias

**Positivas**

- El mapeo con JPA es el de siempre, sin tipos personalizados ni dependencias.
- Las consultas de agenda comparan columnas normales y usan un índice btree.
- Cualquiera que abra la tabla ve dos marcas de tiempo legibles.

**Negativas**

- **El rango se repite en cada sitio que lo necesita.** La expresión
  `tstzrange(inicio, fin, '[)')` aparece en la restricción, y si hiciera falta
  en más consultas habría que escribirla otra vez, con el riesgo de usar otros
  límites por descuido.
- **Nada impide a nivel de tipo que `fin` sea anterior a `inicio`.** Con un
  rango nativo eso sería imposible de construir; aquí lo cubre un CHECK
  explícito, que hay que acordarse de tener.

**A vigilar**

- Si el sistema pasara a manipular intervalos de verdad (unirlos, restarlos,
  calcular huecos libres), el tipo nativo empezaría a compensar y esta decisión
  habría que revisarla.
