# ADR-0005 · La infraestructura se hereda de una plantilla

- **Estado:** Aceptada
- **Fecha:** 2026-09-07
- **Aplica a:** `docker-compose.yml`, `backend/Dockerfile`, `frontend/Dockerfile`, `frontend/nginx.conf`, `.github/workflows/ci.yml`

## Contexto

Contenerizar una aplicación de Spring Boot con Angular y PostgreSQL exige
decidir bastantes cosas que no tienen nada que ver con reservar salas: cómo
construir las imágenes, en qué orden arrancar los servicios, cómo servir la
SPA, cómo evitar el CORS, qué comprueba la integración continua.

Ese trabajo ya estaba resuelto y probado en un repositorio aparte.

## Decisión

Copiar la infraestructura de
[plantilla-spring-angular-docker](https://github.com/jhosuechica/plantilla-spring-angular-docker)
y adaptar solo lo propio de este sistema.

Lo que se hereda sin cambios:

- Construcciones multietapa en las dos imágenes, con usuario sin privilegios.
- Encadenado de arranque por estado de salud, no por orden de declaración.
- Nginx sirviendo la SPA y haciendo de proxy de `/api`, con el nombre del
  backend resuelto en cada petición.
- El flujo de integración continua con sus cinco trabajos.

Lo que se adaptó aquí:

| Cambio | Motivo |
|---|---|
| Se eliminó `JWT_SECRET` | Este sistema no autentica a nadie, y una variable sin uso confunde |
| El perfil de pruebas ejecuta Flyway en vez de dejar que Hibernate genere el esquema | Las reglas de este proyecto viven en el esquema, así que unas pruebas sobre un esquema generado por el ORM no comprobarían nada |
| El perfil de pruebas se detiene en la migración V1 | Deja el esquema sin datos de ejemplo, para que cada prueba prepare los suyos |
| Node 20 pasó a Node 24 en el `.env`, en el Compose y en la integración continua | Angular 20 necesita una versión reciente, y las tres tienen que coincidir: si la del contenedor y la de la CI divergen, un fallo aparece en un sitio y no en el otro |

Las decisiones de infraestructura en sí están documentadas en los ADR de la
plantilla, y no se repiten aquí.

## Alternativas consideradas

| Alternativa | Por qué se descartó |
|---|---|
| **Escribir la infraestructura desde cero** | Se habrían vuelto a tomar las mismas decisiones y a cometer los mismos errores, sin aprender nada nuevo |
| Consumir la plantilla como dependencia o submódulo | La infraestructura de un proyecto se acaba tocando siempre. Un submódulo la vuelve remota y complica un cambio local que debería ser trivial |
| Un generador que produzca el proyecto | Más potente, pero mantenerlo cuesta más que la propia plantilla, y aquí solo hay un consumidor |

## Consecuencias

**Positivas**

- El proyecto arrancó con infraestructura ya probada.
- Este repositorio demuestra que la plantilla sirve: es lo que ejecuta sus
  cinco trabajos de integración continua de principio a fin.
- Las adaptaciones quedan documentadas, así que se ve qué se cambió y por qué.

**Negativas**

- **Una copia no recibe correcciones.** Si la plantilla arregla algo mañana,
  aquí hay que traerlo a mano, y nada avisa de que exista esa corrección.
- **La divergencia crece con el tiempo.** Cuantos más cambios locales, más
  difícil es distinguir lo heredado de lo propio.

**A vigilar**

- Si aparecieran varios proyectos que copian la plantilla, este modelo dejaría
  de escalar y tocaría convertirla en algo versionado de verdad.
