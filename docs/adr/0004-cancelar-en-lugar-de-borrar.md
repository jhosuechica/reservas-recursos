# ADR-0004 · Cancelar cambia el estado, no borra la fila

- **Estado:** Aceptada
- **Fecha:** 2026-09-07
- **Aplica a:** `V1__esquema.sql`, `ReservaService.cancelar`

## Contexto

Al anular una reserva hay que liberar el hueco para que otra persona pueda
ocuparlo. La forma más simple es borrar la fila: el hueco queda libre porque la
reserva deja de existir.

Pero una reserva anulada es información. Quién la pidió, para qué, cuándo se
anuló y qué recurso ocupaba son datos que sirven para saber si una sala está
sobredemandada o si alguien reserva y no aparece. Borrar la fila los tira.

El problema es que una reserva cancelada que sigue en la tabla seguiría
bloqueando su intervalo frente a la restricción de exclusión, que es
exactamente lo que no se quiere.

## Decisión

Guardar un `estado` y hacer que la restricción de exclusión sea **parcial**:

```sql
EXCLUDE USING gist (...) WHERE (estado = 'CONFIRMADA')
```

El `WHERE` hace que la restricción solo mire las reservas confirmadas. Cancelar
es un `UPDATE` del estado: la fila sigue ahí, sale del índice, y el hueco queda
libre en la misma operación.

Cancelar dos veces no hace nada la segunda vez. El servicio devuelve la reserva
tal cual si ya estaba cancelada, en vez de escribir otra vez o fallar.

## Alternativas consideradas

| Alternativa | Por qué se descartó |
|---|---|
| **Borrar la fila** | Es más simple y no necesita restricción parcial. Pierde el histórico, que es justo lo que da valor a los datos de un sistema de reservas |
| Mover las canceladas a una tabla de histórico | Conserva el dato y mantiene limpia la tabla principal, pero duplica el esquema y obliga a consultar dos sitios para responder «qué pasó con esta reserva» |
| Marcar con una columna `activa` booleana | Equivalente en la práctica, pero no deja sitio para estados futuros como `PENDIENTE` o `RECHAZADA` sin volver a migrar |
| Borrado lógico con `fecha_cancelacion` nula | Funciona igual, pero mezcla dos informaciones en una columna: si está cancelada y cuándo. El estado explícito se lee mejor |

## Consecuencias

**Positivas**

- El histórico completo queda en una sola tabla.
- Liberar el hueco y registrar la cancelación son la misma escritura, así que
  no pueden quedar descompasados.
- Añadir un estado nuevo más adelante no obliga a cambiar la estructura.

**Negativas**

- **La tabla crece indefinidamente.** Las canceladas nunca se van, y con el
  tiempo pesan en los recorridos que no filtran por estado.
- **Toda consulta tiene que acordarse del estado.** Una que olvide filtrar por
  `CONFIRMADA` contará reservas anuladas como si fueran válidas. La restricción
  sí lo filtra, pero las consultas de la aplicación no lo hacen solas.
- **El `WHERE` de la restricción es fácil de romper sin notarlo.** Quitarlo en
  una migración futura haría que las canceladas volvieran a bloquear su hueco.
  La migración de datos de ejemplo dejaría de aplicarse, que es la señal de
  alarma que se dejó a propósito.

**A vigilar**

- Si la tabla llegara a un volumen en que las canceladas estorben, lo indicado
  sería archivarlas por fecha, no empezar a borrarlas.
