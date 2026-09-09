#!/usr/bin/env bash
#
# Prueba de humo del sistema completo.
#
# No comprueba solo que los servicios respondan: verifica una a una las
# afirmaciones que sostiene el README, y en particular que la regla de no
# solapamiento la impone la base de datos y no el codigo.
#
# Es reejecutable. Cada pasada da de alta su propio recurso, asi que las
# reservas de una ejecucion no chocan con las de la siguiente.
#
# Uso:  bash scripts/prueba-humo.sh
#
set -euo pipefail

# Git Bash convierte las rutas que empiezan por barra. Sin esto, un /api/...
# acaba convertido en C:/Program Files/Git/api/...
export MSYS_NO_PATHCONV=1
export MSYS2_ARG_CONV_EXCL='*'

API="${API:-http://localhost:8080/api}"
SALUD="${SALUD:-http://localhost:8080/actuator/health}"
WEB="${WEB:-http://localhost:4200}"

verde() { printf '\033[32m  OK  \033[0m %s\n' "$1"; }
rojo()  { printf '\033[31m FALLO\033[0m %s\n' "$1"; }
gris()  { printf '\033[90m       %s\033[0m\n' "$1"; }

TOTAL=0
BIEN=0

CODIGO=""
CUERPO=""

# Ejecuta curl separando cuerpo y codigo, y reintenta mientras el stack todavia
# esta levantando. Sin el reintento, la primera comprobacion falla en un
# arranque en frio aunque el sistema sea correcto.
peticion() {
    local salida intento=1
    while :; do
        salida="$(curl -s -m 30 -w $'\n%{http_code}' "$@" || true)"
        CODIGO="${salida##*$'\n'}"
        CUERPO="${salida%$'\n'*}"
        case "$CODIGO" in
            502|503|504|000)
                if [ "$intento" -ge 25 ]; then return 0; fi
                gris "el servicio aun no responde (codigo $CODIGO), reintento $intento"
                intento=$((intento + 1))
                sleep 4
                ;;
            *) return 0 ;;
        esac
    done
}

# Extrae el valor de una clave de un JSON plano. Termina en 'grep', que
# devuelve 1 si no encuentra nada: sin el '|| true' eso mataria el script
# entero bajo 'set -e', y sin mensaje.
extraer() {
    grep -o "\"$1\":[[:space:]]*\"\?[^,\"}]*\"\?" | head -1 | sed 's/.*: *"\?//; s/"$//' || true
}

comprobar() {
    local descripcion="$1" condicion="$2"
    TOTAL=$((TOTAL + 1))
    if [ "$condicion" = "si" ]; then
        BIEN=$((BIEN + 1))
        verde "$descripcion"
    else
        rojo "$descripcion"
    fi
}

# Manana, para no pisar los datos de ejemplo, que son de hoy.
FECHA="$(date -u -d 'tomorrow' +%Y-%m-%d 2>/dev/null || date -u -v+1d +%Y-%m-%d)"
H09="${FECHA}T09:00:00Z"
H10="${FECHA}T10:00:00Z"
H11="${FECHA}T11:00:00Z"
H12="${FECHA}T12:00:00Z"
H21="${FECHA}T21:00:00Z"

echo
echo "Prueba de humo · reserva de salas y equipos"
echo "==========================================="
echo

# --- 1. El backend esta sano ------------------------------------------------
peticion "$SALUD"
estado="$(printf '%s' "$CUERPO" | extraer status)"
comprobar "El backend responde UP en /actuator/health" \
    "$([ "$estado" = "UP" ] && echo si || echo no)"
[ "$estado" = "UP" ] || gris "respuesta: $CUERPO"

# --- 2. El frontend se sirve ------------------------------------------------
peticion "$WEB/"
comprobar "El frontend responde en $WEB" \
    "$([ "$CODIGO" = "200" ] && echo si || echo no)"

# --- 3. El proxy /api del frontend enruta al backend ------------------------
peticion "$WEB/api/recursos"
comprobar "Nginx enruta /api al backend sin CORS" \
    "$([ "$CODIGO" = "200" ] && echo si || echo no)"

# --- 4. Catalogo de recursos ------------------------------------------------
# Se comprueba que esten los seis codigos sembrados, no un total exacto: cada
# pasada anade su propio recurso de prueba.
peticion "$API/recursos?soloActivos=false"
sembrados=0
for codigo in SALA-A SALA-B SALA-C EQ-PROY-1 EQ-CAM-1 EQ-PORT-2; do
    if printf '%s' "$CUERPO" | grep -q "\"$codigo\""; then
        sembrados=$((sembrados + 1))
    fi
done
comprobar "El catalogo trae los 6 recursos de ejemplo" \
    "$([ "$sembrados" -eq 6 ] && echo si || echo no)"

INACTIVO_ID="$(printf '%s' "$CUERPO" | tr '}' '\n' | grep '"activo":false' | extraer id)"

# --- 5. El filtro de activos funciona ---------------------------------------
peticion "$API/recursos?soloActivos=true"
comprobar "El recurso dado de baja queda fuera del listado de activos" \
    "$(printf '%s' "$CUERPO" | grep -q '"EQ-PORT-2"' && echo no || echo si)"

# --- 6. Alta de un recurso propio de esta pasada ----------------------------
SUFIJO="$(date -u +%y%m%d%H%M%S)"
peticion -X POST "$API/recursos" \
    -H 'Content-Type: application/json' \
    -d "{\"codigo\":\"HUMO-$SUFIJO\",\"nombre\":\"Sala de prueba de humo\",\"tipo\":\"SALA\",\"capacidad\":4,\"ubicacion\":\"Prueba\"}"
SALA_ID="$(printf '%s' "$CUERPO" | extraer id)"
comprobar "Se puede dar de alta un recurso (201)" \
    "$([ "$CODIGO" = "201" ] && echo si || echo no)"
gris "recurso de esta pasada: HUMO-$SUFIJO (id $SALA_ID)"

# --- 7. Un recurso con capacidad incoherente se rechaza ---------------------
peticion -X POST "$API/recursos" \
    -H 'Content-Type: application/json' \
    -d "{\"codigo\":\"HUMO-EQ-$SUFIJO\",\"nombre\":\"Equipo con aforo\",\"tipo\":\"EQUIPO\",\"capacidad\":5,\"ubicacion\":\"Prueba\"}"
restriccion="$(printf '%s' "$CUERPO" | extraer restriccion)"
comprobar "Un equipo con capacidad se rechaza por el CHECK del esquema" \
    "$([ "$CODIGO" = "409" ] && [ "$restriccion" = "recursos_capacidad_solo_en_salas" ] && echo si || echo no)"

# --- 8. La agenda del dia ---------------------------------------------------
peticion "$API/reservas"
comprobar "La agenda de hoy responde con las reservas sembradas" \
    "$([ "$CODIGO" = "200" ] && printf '%s' "$CUERPO" | grep -q '"solicitante"' && echo si || echo no)"

# --- 9. Crear una reserva en un hueco libre ---------------------------------
crear() {
    peticion -X POST "$API/reservas" \
        -H 'Content-Type: application/json' \
        -d "{\"recursoId\":$SALA_ID,\"solicitante\":\"Prueba de humo\",\"motivo\":\"$1\",\"inicio\":\"$2\",\"fin\":\"$3\"}"
}

crear "Reserva base" "$H09" "$H11"
RESERVA_ID="$(printf '%s' "$CUERPO" | extraer id)"
comprobar "Una reserva en un hueco libre se crea (201)" \
    "$([ "$CODIGO" = "201" ] && echo si || echo no)"
[ "$CODIGO" = "201" ] || gris "respuesta: $CUERPO"

# --- 10. El solapamiento se rechaza -----------------------------------------
crear "Reserva solapada" "$H10" "$H11"
restriccion="$(printf '%s' "$CUERPO" | extraer restriccion)"
comprobar "Una reserva solapada se rechaza con 409" \
    "$([ "$CODIGO" = "409" ] && echo si || echo no)"
comprobar "El 409 identifica la restriccion reservas_sin_solape" \
    "$([ "$restriccion" = "reservas_sin_solape" ] && echo si || echo no)"

# --- 11. Reservas consecutivas si se permiten -------------------------------
crear "Reserva consecutiva" "$H11" "$H12"
comprobar "Reservar justo cuando termina la anterior se permite" \
    "$([ "$CODIGO" = "201" ] && echo si || echo no)"

# --- 12. Cancelar libera el hueco -------------------------------------------
peticion -X DELETE "$API/reservas/$RESERVA_ID"
estado_tras_cancelar="$(printf '%s' "$CUERPO" | extraer estado)"
comprobar "Cancelar devuelve la reserva en estado CANCELADA" \
    "$([ "$estado_tras_cancelar" = "CANCELADA" ] && echo si || echo no)"

crear "Ocupa el hueco liberado" "$H09" "$H11"
comprobar "El hueco de la reserva cancelada vuelve a estar libre" \
    "$([ "$CODIGO" = "201" ] && echo si || echo no)"

# --- 13. El tope de duracion ------------------------------------------------
# Nueve horas: una mas que el tope. Con ocho justas el CHECK las admite, porque
# el limite es inclusivo.
crear "Reserva demasiado larga" "$H12" "$H21"
restriccion="$(printf '%s' "$CUERPO" | extraer restriccion)"
comprobar "Una reserva de mas de 8 horas se rechaza por el CHECK del esquema" \
    "$([ "$CODIGO" = "409" ] && [ "$restriccion" = "reservas_duracion_maxima" ] && echo si || echo no)"

# --- 14. El recurso inactivo no admite reservas -----------------------------
if [ -n "$INACTIVO_ID" ]; then
    peticion -X POST "$API/reservas" \
        -H 'Content-Type: application/json' \
        -d "{\"recursoId\":$INACTIVO_ID,\"solicitante\":\"Prueba de humo\",\"motivo\":\"Sobre inactivo\",\"inicio\":\"$H09\",\"fin\":\"$H10\"}"
    comprobar "Un recurso dado de baja no admite reservas (409)" \
        "$([ "$CODIGO" = "409" ] && echo si || echo no)"
else
    comprobar "Un recurso dado de baja no admite reservas (409)" "no"
    gris "no se pudo localizar el recurso inactivo"
fi

# --- 15. La peticion mal formada da 400, no 500 -----------------------------
peticion -X POST "$API/reservas" \
    -H 'Content-Type: application/json' \
    -d '{"solicitante":"","motivo":"","inicio":null,"fin":null}'
comprobar "Una peticion invalida responde 400 con el detalle de los campos" \
    "$([ "$CODIGO" = "400" ] && printf '%s' "$CUERPO" | grep -q 'errores' && echo si || echo no)"

echo
echo "==========================================="
printf 'Resultado: %s de %s comprobaciones\n' "$BIEN" "$TOTAL"
echo

[ "$BIEN" -eq "$TOTAL" ]
