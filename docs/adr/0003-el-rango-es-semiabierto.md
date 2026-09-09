# ADR-0003 · El intervalo es cerrado por la izquierda y abierto por la derecha

- **Estado:** Aceptada
- **Fecha:** 2026-09-07
- **Aplica a:** `V1__esquema.sql`

## Contexto

Al construir el rango hay que elegir si los extremos entran o no. PostgreSQL lo
expresa con un tercer argumento: `'[]'` incluye los dos, `'()'` ninguno, `'[)'`
incluye el inicio y excluye el fin.

La elección no es cosmética. Determina qué pasa con el caso más frecuente de
todos: una reunión de 9 a 10 y la siguiente de 10 a 11 en la misma sala.

Con `'[]'` las dos reservas comparten el instante de las 10:00, así que se
consideran solapadas y **el sistema rechazaría la segunda**. Sería un rechazo
incomprensible para quien reserva, porque nadie entiende que dos reuniones
consecutivas se estorben.

## Decisión

Usar `'[)'`: el intervalo incluye su inicio y excluye su fin.

```sql
tstzrange(inicio, fin, '[)')
```

Una reserva de 9 a 10 ocupa hasta el instante anterior a las 10:00. Otra que
empieza a las 10:00 no la toca.

Hay una prueba de integración dedicada solo a esto, porque es una decisión
invisible en el código que se rompería sin que nada más avisara.

## Alternativas consideradas

| Alternativa | Por qué se descartó |
|---|---|
| **`'[]'`, con los dos extremos incluidos** | Rechaza reservas consecutivas legítimas, que es el uso normal de una sala |
| `'()'`, sin ningún extremo | Dos reservas idénticas de 9 a 10 no se solaparían, porque el rango abierto no contiene sus extremos. Justo lo contrario de lo que se busca |
| Restar un segundo al fin antes de guardar | Consigue el mismo efecto ensuciando el dato. La reserva dejaría de terminar a las 10:00 y el informe mostraría 9:59:59 |

## Consecuencias

**Positivas**

- Las reservas consecutivas funcionan, que es lo que espera cualquiera.
- El dato guardado es el que el usuario introdujo, sin ajustes.
- Coincide con la convención habitual para intervalos de tiempo, así que a
  quien lea el esquema no le sorprende.

**Negativas**

- **Es una decisión invisible.** Nada en el código Java indica qué convención
  se usa; está solo en la migración. Alguien que escriba una consulta nueva
  puede elegir otros límites sin darse cuenta y obtener resultados que no
  cuadran con la restricción.
- Una reserva de duración cero no existe, porque el CHECK exige `fin > inicio`.
  Es lo correcto aquí, pero conviene saberlo.

**A vigilar**

- Si aparece una segunda consulta que construya el rango a mano, es el momento
  de encapsularlo en una función SQL para no tener la convención escrita en
  dos sitios.
