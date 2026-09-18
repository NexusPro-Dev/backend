# SPEC — `RF-AC-022` Registrar módulo

| Campo | Valor |
|---|---|
| Requerimiento | `RF-AC-022` |
| Módulo | `AC` — Academia |
| Estado | **En revisión** |
| Autor | Responsable técnico |
| Aprobada por | — |
| Fecha de aprobación | — |

---

## 1. Objetivo

Darle al curso una **parte** donde poner lecciones: un módulo con título, orden y —si lo tiene— sus descripciones y su video de presentación.

## 2. Contexto

El módulo es la pieza intermedia del árbol `curso → módulos → lecciones` (`ac.md` §5.2.1): agrupa lecciones y se ordena dentro de su curso. **Nace dentro del curso y no se mueve** (`RN-AC-019`): la ruta lo dice —`POST /courses/{courseId}/modules`—, y `course_id` no tiene mutador. Hereda del curso la forma —título único **dentro del curso**, descripciones que se recortan, video como enlace, nace `INACTIVO`, portada que llega después— y lo que no hereda es el instructor y la dificultad, que son del curso.

Es el requerimiento que **crea `course_modules`** y el primero del bloque 3, que es el bloque que **llena lo que el bloque 2 dejó vacío**: la cuenta de módulos del listado (`RF-AC-009`), el árbol del detalle (`RF-AC-010`), la condición de activar del curso (`RF-AC-012`) y el arrastre del retiro (`RF-AC-013`). Cada una de esas cuatro enmiendas (Art. I.7) está declarada en su spec y este requerimiento las construye.

## 3. Actores

| Actor | Papel |
|---|---|
| Administrador | Registra el módulo |

## 4. Alcance

### 4.1 Incluye

- Registrar un módulo dentro de un curso vivo con **título y orden**, obligatorios, y **descripción corta, descripción larga y video de presentación**, opcionales.
- Crear `course_modules`.
- **Enmendar** `RF-AC-009` (`moduleCount` real), `RF-AC-010` (los módulos en el árbol), `RF-AC-012` (la cuenta de módulos activos) y `RF-AC-013` (el arrastre de módulos).
- Devolver el módulo en la **forma de su detalle**: lo suyo, `coverImageUrl` nula, `lessons` vacío, `offerable: false` con motivo.

### 4.2 No incluye

- **Lecciones en el alta.** `RF-AC-028`.
- **Activarlo.** Nace `INACTIVO` y se publica con `RF-AC-024`, cuando tenga una lección activa.
- **La portada.** `RF-AC-026`.
- **Un listado de módulos.** No existen fuera del curso; el detalle del curso los trae (`ac.md` §2).

## 5. Reglas de negocio aplicables

| ID | Regla | Origen |
|---|---|---|
| `RN-AC-001` | El título es único **dentro del curso**, entre los vivos, sin mayúsculas ni acentos | `requirements/ac.md` §5.1 |
| `RN-AC-002` | El orden es un entero ≥ 0 **dentro de su curso**, no único | `requirements/ac.md` §5.1 |
| `RN-AC-004` | La portada llega después; toda lectura trae `coverImageUrl` | `requirements/ac.md` §5.1 |
| `RN-AC-005` | El video de presentación es un enlace con la forma de `RN-PM-032` | `requirements/ac.md` §5.1 |
| `RN-AC-008` | El módulo nace `INACTIVO` | `requirements/ac.md` §5.1 |
| `RN-AC-015` | Un módulo se ofrece si está `ACTIVO`, no retirado y con una lección activa; se calcula | `requirements/ac.md` §5.1 |
| `RN-AC-018` | Un curso retirado no admite módulos | `requirements/ac.md` §5.1 |
| `RN-AC-019` | El módulo no cambia de curso | `requirements/ac.md` §5.1 |

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción | Restricción |
|---|---|---|---|
| Curso | Sí | En cuál nace | Ruta. Curso **vivo**, en cualquier estado |
| Título (`title`) | Sí | Cómo se llama la parte | Hasta 150 tras recortar; **único entre los módulos vivos de ese curso** |
| Descripción corta (`shortDescription`) | No | La de la tarjeta | Hasta 300; de solo espacios queda nula |
| Descripción larga (`longDescription`) | No | La de la página | Hasta 10 000; de solo espacios queda nula |
| Video de presentación (`presentationVideoUrl`) | No | La dirección de un video que presenta el módulo — el «contenido url» de la lista original (`ac.md` §5.2.1) | Forma de `RN-AC-005`; nulo es «no tiene» |
| Orden (`displayOrder`) | Sí | En qué lugar del curso | Entero ≥ 0; no único |

**Ni estado, ni lecciones, ni portada, ni curso en el cuerpo**: el curso va en la ruta, y lo demás se rechaza como campo desconocido.

### 6.2 Salida

`201` con el módulo en la **forma de su detalle**, que es la misma que el detalle del curso enseña por módulo, más sus dos descripciones y su video: identificador, `courseId`, título, descripciones y video presentes y nulos, orden, estado `INACTIVO`, `coverImageUrl` presente y nula, `durationMinutes` en cero, `lessons` vacío, `offerable: false` con `offerableReason` diciendo que está inactivo, y las dos fechas.

**Las escrituras sobre un módulo devuelven el módulo, no el curso entero.** Es la decisión de §14.1: el curso entero con su árbol es lo que el frontend ya tiene abierto, y lo que cambia al registrar un módulo es una fila de ese árbol.

## 7. Precondiciones y postcondiciones

**Precondiciones:** actor con `courses:update`; curso vivo; título libre entre los módulos vivos del curso.

**Postcondiciones:** existe la fila en `course_modules` con `course_id`, `status = INACTIVO`, `cover_image_id` nulo; `audit_change_log` tiene una fila `CREATE` de `course_modules`; el detalle del curso lo enseña en su orden y `moduleCount` del listado sube en uno.

## 8. Flujo principal

1. Llega la petición con el curso en la ruta, título, orden y —si vienen— descripciones y video.
2. El sistema valida la forma de los cinco, **juntos** (§11).
3. El sistema resuelve el curso **vivo**, bloqueándolo: si no existe o está retirado, `EX-001`.
4. El sistema comprueba que el **título** no lo usa otro módulo vivo del mismo curso (`EX-002`).
5. El sistema inserta el módulo en `INACTIVO` y registra la creación, en la misma transacción.
6. Devuelve `201` con el módulo vacío.

**El curso se bloquea al registrarle un módulo**, y no es exceso: es lo que ordena esta alta frente a un retiro simultáneo del curso (`RF-AC-013` §13), y lo que hace que «dentro del curso» sea una afirmación y no una carrera.

## 9. Flujos alternativos

### FA-001 — El curso está `ACTIVO`

**Comportamiento:** se registra igual. El módulo nace inactivo y no cambia lo que el aula enseña hasta que se active.

### FA-002 — Mismo título en otro curso

**Comportamiento:** se admite. La unicidad es dentro del curso: dos «Introducción» en dos cursos son lo normal.

## 10. Excepciones

### EX-001 — El curso no existe o está retirado

**Respuesta del sistema:** `404` — *«No existe un curso vivo con ese identificador.»*

### EX-002 — El título ya lo usa un módulo vivo del curso

**Respuesta del sistema:** `409` — *«Ya existe un módulo con ese título en este curso.»* Un retirado libera el título.

## 11. Validaciones

| ID | Validación | Mensaje esperado |
|---|---|---|
| `VAL-001` | Identificador del curso con formato válido | El identificador indicado no tiene un formato válido. |
| `VAL-002` | Título presente y de hasta 150 tras recortar | El título es obligatorio y no puede superar los 150 caracteres. |
| `VAL-003` | Orden presente y ≥ 0 | El orden es obligatorio y debe ser un entero mayor o igual que cero. |
| `VAL-004` | Descripciones dentro de su tope | Los de `RF-AC-008` `VAL-005` |
| `VAL-005` | Video, si viene, con la forma de `RN-AC-005` | El de `RF-AC-008` `VAL-006` |
| `VAL-006` | Ningún campo desconocido — `status`, `lessons`, `coverImageUrl`, `courseId` | El cuerpo de la petición contiene campos no admitidos. |

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-AC-076` | El sistema registra el módulo con `201` en la forma de su detalle: `INACTIVO`, `courseId` el de la ruta, `coverImageUrl` presente y nula, `lessons` vacío, cero minutos y `offerable: false` «inactivo» |
| `CA-AC-077` | El sistema rechaza con `409` un título que ya usa un módulo vivo **del mismo curso**, sin mayúsculas ni acentos, y **admite** el de un retirado y el mismo título **en otro curso** |
| `CA-AC-078` | El sistema responde `404` a un curso inexistente y a uno **retirado**; un curso `ACTIVO` admite el módulo igual |
| `CA-AC-079` | El sistema rechaza con `400` el título ausente o largo, el orden ausente o negativo, las descripciones largas y el video mal formado, **juntos**; y con `400` un cuerpo con `status`, `lessons`, `coverImageUrl` o `courseId` |
| `CA-AC-080` | El sistema registra una fila `CREATE` en `audit_change_log` de `course_modules` con el actor, en la misma transacción |
| `CA-AC-081` | **Enmienda de `RF-AC-009`**: `moduleCount` cuenta los módulos **vivos** del curso —uno inactivo cuenta, uno retirado no— en la misma sentencia de la página |
| `CA-AC-082` | **Enmienda de `RF-AC-010`**: el detalle del curso trae sus módulos **en orden con desempate por identificador**, vivos y retirados marcados, cada uno con `offerable`, y la lectura cuesta **una sentencia más** cuando hay módulos |
| `CA-AC-083` | **Enmienda de `RF-AC-013`**: retirar el curso **retira sus módulos vivos** con el mismo instante y una fila de auditoría cada uno; los ya retirados no se tocan |
| `CA-AC-084` | Dos altas simultáneas con el mismo título en el mismo curso dejan **una** fila y un `409` |

## 13. Casos límite

| Caso | Decisión |
|---|---|
| Orden `0` en dos módulos del mismo curso | Desempata el identificador: el más antiguo primero |
| Registrar un módulo mientras otro retira el curso | El bloqueo del curso los ordena; si el retiro gana, `404` |
| El curso está `ACTIVO` y se ofrece, y este es su primer módulo | Nace inactivo: el aula no cambia. Que el curso estuviera activo sin módulos no puede ocurrir por `RF-AC-012`, salvo que se le retiraran después — y entonces estaba `offerable: false` y sigue igual hasta que este módulo se active |

## 14. Preguntas abiertas

| # | Pregunta | Resolución |
|---|---|---|
| 1 | ¿Las escrituras sobre un módulo devuelven el módulo o el curso entero? | **El módulo.** El curso entero con su árbol pesa —cada lección con sus campos— y el frontend lo tiene abierto; lo que cambia es una fila. `PM` devuelve el paquete entero al asociar porque lo que cambia es **el precio del paquete**; aquí no hay cuenta del curso que rehacer salvo la duración, que el frontend puede volver a pedir. La forma del módulo es la misma que el detalle del curso enseña por módulo, más sus descripciones y su video, para que las dos pantallas coincidan |
| 2 | ¿Un módulo puede cambiar de curso? | **No** (`RN-AC-019`): orden, unicidad y ofrecibilidad están definidos dentro del curso. Mover es retirar y registrar |

## 15. Control de cambios

| Versión | Fecha | Cambio | Responsable |
|---|---|---|---|
| 0.1.0 | 18-09-2026 | Redacción inicial. El módulo nace dentro del curso —ruta anidada, curso bloqueado— e **inactivo y vacío**; título único dentro del curso; **las escrituras devuelven el módulo y no el curso** (§14.1). Crea `course_modules` y **construye cuatro enmiendas declaradas** por el bloque 2: `moduleCount`, el árbol del detalle, la cuenta de activar y el arrastre. | Responsable técnico |
