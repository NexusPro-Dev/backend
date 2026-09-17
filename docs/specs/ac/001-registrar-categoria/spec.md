# SPEC — `RF-AC-001` Registrar categoría

| Campo | Valor |
|---|---|
| Requerimiento | `RF-AC-001` |
| Módulo | `AC` — Academia |
| Estado | **En revisión** |
| Autor | Responsable técnico |
| Aprobada por | — |
| Fecha de aprobación | — |

---

## 1. Objetivo

Poner en el sistema un **cajón del catálogo de cursos** —nombre, color, icono y orden— para que los cursos tengan dónde clasificarse y el alumno tenga por dónde recorrerlos.

## 2. Contexto

Es el **primer requerimiento del módulo**: crea la primera tabla de `AC` y **siembra los cuatro permisos `course-categories:`**, con la obligación de asociarlos a `SUPERADMIN` y `ADMIN` en la misma migración. Las decisiones que dan forma a la categoría están en [`requirements/ac.md` §5.2](../../../requirements/ac.md) y no se repiten aquí; lo que esta spec fija es el alta.

**La categoría nace viva, sin portada y sin cursos.** Viva porque **no tiene estado** (`RN-AC-008`, §5.2.6 del módulo): está o está retirada, y un cajón vacío no molesta. Sin portada porque la imagen es un archivo y llega por otra petición (`RF-AC-006`), como la del producto. Sin cursos porque clasificar es una operación del curso (`RF-AC-016`), no de la categoría.

**Lo que la categoría declara para pintarse lo declara entero en el alta.** El color y el icono son obligatorios (`RN-AC-003`): a diferencia del upgrade de `PM`, que exige el icono *porque* no hay portada, la categoría se pinta **siempre** con color e icono y la portada es un adorno opcional. No hay regla cruzada que comprobar al quitarla.

## 3. Actores

| Actor | Papel |
|---|---|
| Administrador | Registra la categoría |

## 4. Alcance

### 4.1 Incluye

- Registrar una categoría con **nombre, color, icono y orden**, obligatorios, y **descripción** opcional.
- Crear `course_categories` y sembrar `course-categories:read`, `course-categories:create`, `course-categories:update` y `course-categories:delete`.
- Devolver la categoría recién creada **en la forma del detalle** (`RF-AC-003`): sin cursos, sin portada.

### 4.2 No incluye

- **La portada.** Se sube después con `RF-AC-006`; la respuesta trae `coverImageUrl` presente y nulo.
- **Clasificar cursos.** `RF-AC-016`, desde el curso.
- **Un estado.** La categoría no lo tiene (`RN-AC-008`).
- **Reordenar a las demás.** El orden es un número que se declara; corregir el de otras es `RF-AC-004` sobre cada una (`RN-AC-002`).

## 5. Reglas de negocio aplicables

| ID | Regla | Origen |
|---|---|---|
| `RN-AC-001` | El nombre es único entre las vivas, sin distinguir mayúsculas ni acentos | `requirements/ac.md` §5.1 |
| `RN-AC-002` | El orden es una posición: entero ≥ 0, no único, desempate por identificador | `requirements/ac.md` §5.1 |
| `RN-AC-003` | La categoría declara color —formato de `RN-SP-024`, **no único**— e icono, obligatorios | `requirements/ac.md` §5.1 |
| `RN-AC-004` | La portada es un archivo, opcional sin condición; toda lectura trae `coverImageUrl` | `requirements/ac.md` §5.1 |
| `RN-AC-008` | La categoría no tiene estado | `requirements/ac.md` §5.1 |
| `RN-SEG-003` | Los permisos se conceden por rol; ningún rol concede lo que su padre no tiene | `security.md` §4 |

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción | Restricción |
|---|---|---|---|
| Nombre (`name`) | Sí | Cómo se llama el cajón | Hasta 150 tras recortar; **único entre las vivas** sin distinguir mayúsculas ni acentos |
| Descripción (`description`) | No | Qué se encuentra en él | Texto libre; recortada; **de solo espacios se guarda nula** |
| Color (`color`) | Sí | Con qué color lo pinta el frontend | **Seis dígitos hexadecimales, sin `#`**; se normaliza a mayúsculas antes de validar y de guardar (`RN-SP-024` por extensión). **No es único** |
| Icono (`icon`) | Sí | **El nombre** del icono con el que se pinta, no una imagen | Minúsculas, dígitos y guion medio, empezando por letra, hasta 50 — la misma forma que el icono del producto (`RN-PM-016`) |
| Orden (`displayOrder`) | Sí | En qué lugar se enseña | Entero **mayor o igual que cero**; **no único** |

**Ni portada, ni estado, ni cursos.** Un `status` o un `courses` en el cuerpo es un campo desconocido y se rechaza como tal.

### 6.2 Salida

`201` con la categoría en la **misma forma del detalle** (`RF-AC-003`): identificador, nombre, descripción —presente y nula si no vino—, color en mayúsculas, icono, orden, `coverImageUrl` **presente y nula**, `courseCount` en **cero**, `courses` vacío, y las dos fechas de auditoría iguales.

**Se devuelve la forma completa aunque esté vacía**, para que el frontend trate «acabo de crearla» y «la abrí» igual, como hace toda alta del sistema.

## 7. Precondiciones y postcondiciones

**Precondiciones:** actor autenticado con `course-categories:create`; nombre libre entre las vivas.

**Postcondiciones:** existe la fila en `course_categories` con `deleted_at` nulo, `cover_image_id` nulo y el color en mayúsculas; `audit_change_log` tiene una fila `CREATE` de `course_categories`. Ningún evento de seguridad: una categoría no concede nada.

## 8. Flujo principal

1. Llega la petición con nombre, color, icono, orden y —si viene— descripción.
2. El sistema valida la forma de los cinco, **juntos** (§11).
3. El sistema normaliza el color a mayúsculas y recorta nombre y descripción.
4. El sistema comprueba que el **nombre** no lo usa otra categoría viva (`EX-001`).
5. El sistema inserta la categoría y registra la creación en la auditoría, en la misma transacción.
6. Devuelve `201` con la categoría vacía.

El paso 4 tiene su red en el esquema —`uq_course_categories_name`, parcial—: la carrera entre dos altas simultáneas la muerde el índice y el repositorio la traduce al mismo `409` que la comprobación previa, como en `RF-PM-001`.

## 9. Flujos alternativos

### FA-001 — Sin descripción

**Comportamiento:** se registra igual. La categoría no exige nada para enseñarse: no tiene estado que activar.

### FA-002 — El color llega en minúsculas

**Comportamiento:** se normaliza a mayúsculas antes de validar y de guardar, como el de la membresía. `1e88e5` y `1E88E5` son el mismo color.

### FA-003 — El color ya lo usa otra categoría

**Comportamiento:** se registra igual. **No es una excepción**: el color no es único (`RN-AC-003`), y dos cajones del mismo tono no confunden a nadie.

### FA-004 — El orden ya lo usa otra categoría

**Comportamiento:** se registra igual. Las dos se enseñan en ese lugar y se desempatan por identificador, que es cronológico: la más antigua primero (`RN-AC-002`).

## 10. Excepciones

### EX-001 — El nombre ya lo usa una categoría viva

**Condición:** hay una categoría **no retirada** cuyo nombre coincide, sin distinguir mayúsculas ni acentos.
**Respuesta del sistema:** `409` — *«Ya existe una categoría con ese nombre.»* Una retirada **sí** libera el nombre: no hay código que conservar.

## 11. Validaciones

| ID | Validación | Mensaje esperado |
|---|---|---|
| `VAL-001` | Nombre presente y de hasta 150 caracteres tras recortar | El nombre es obligatorio y no puede superar los 150 caracteres. |
| `VAL-002` | Color presente y con la forma admitida, tras normalizar | El color es obligatorio y debe ser seis dígitos hexadecimales sin el símbolo #. |
| `VAL-003` | Icono presente y con la forma admitida | El icono es obligatorio, solo admite minúsculas, dígitos y guion medio, debe empezar por letra y no puede exceder 50 caracteres. |
| `VAL-004` | Orden presente y mayor o igual que cero | El orden es obligatorio y debe ser un entero mayor o igual que cero. |
| `VAL-005` | Ningún campo desconocido — en particular, ni `status`, ni `courses`, ni `coverImageUrl` | El cuerpo de la petición contiene campos no admitidos. |

Las cuatro primeras se devuelven **juntas**: quien se equivocó en dos corrige una vez. Ninguna compara dos campos, de modo que no hay una tanda aparte.

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-AC-001` | El sistema registra la categoría con `201`, en la forma del detalle: `courseCount` en **cero**, `courses` vacío, `coverImageUrl` **presente y nula**, y el color **en mayúsculas** aunque llegara en minúsculas |
| `CA-AC-002` | El sistema rechaza con `409` un nombre que ya usa una categoría viva, sin distinguir mayúsculas ni acentos, y **admite** el de una retirada |
| `CA-AC-003` | El sistema **admite** un color y un orden que ya usa otra categoría |
| `CA-AC-004` | El sistema rechaza con `400` el nombre ausente o largo, el color mal formado, el icono mal formado y el orden ausente o negativo, **juntos** |
| `CA-AC-005` | El sistema rechaza con `400` un cuerpo que traiga `status`, `courses` o `coverImageUrl` |
| `CA-AC-006` | Una descripción de solo espacios se guarda **nula** y la respuesta la trae presente y nula |
| `CA-AC-007` | El sistema registra una fila `CREATE` en `audit_change_log` con el actor, en la misma transacción |
| `CA-AC-008` | Los cuatro permisos `course-categories:` están sembrados con identificador estable y asociados a `SUPERADMIN` y `ADMIN`, y **no** a `CLIENTE`; sin `course-categories:create` el alta responde `403` aunque el actor porte los cuatro `courses:` |
| `CA-AC-009` | Dos altas simultáneas con el mismo nombre dejan **una** fila y un `409`, no un `500` |

## 13. Casos límite

| Caso | Decisión |
|---|---|
| Dos altas simultáneas con el mismo nombre | Una queda y la otra recibe `409` por el índice parcial, no `500` (`CA-AC-009`) |
| El nombre coincide con el **título de un curso** | Se admite: son entidades distintas y la unicidad es por tabla |
| El nombre coincide con el de una categoría **retirada** | Se admite: la retirada liberó el nombre. Dos cajones «Trading» en la historia, uno vivo y uno retirado, es exactamente lo que pasó |
| Orden `0` | Se admite: es «primero», y varios ceros se desempatan por identificador |
| Color `000000` o `FFFFFF` | Se admiten: la forma es lo único que se comprueba; si contrasta o no es del diseño |
| Un icono que el frontend no conoce | Se admite: el backend guarda el nombre y no sabe pintarlo (`RN-PM-016` por extensión). Qué iconos existen es del frontend |

## 14. Preguntas abiertas

| # | Pregunta | Resolución |
|---|---|---|
| 1 | ¿La categoría tiene estado activo/inactivo? | **No**, por decisión del responsable del proyecto (17-09-2026): viva o retirada. Un cajón vacío sale vacío, y un cajón «inactivo» con cursos activos dentro abriría la pregunta de si esos cursos se ofrecen, que nadie quiere responder |
| 2 | ¿El color es único, como el de la membresía? | **No**, por decisión del responsable del proyecto (17-09-2026): las categorías no son una cadena que haya que distinguir por el color |
| 3 | ¿Se reutilizan los `courses:`? | **No**: recurso propio. Una categoría existe sin cursos y la administra quien organiza el catálogo, que puede no ser quien arma un curso (`requirements/ac.md` §7) |

## 15. Control de cambios

| Versión | Fecha | Cambio | Responsable |
|---|---|---|---|
| 0.1.0 | 17-09-2026 | Redacción inicial. **La categoría nace viva, sin portada y sin cursos**: no tiene estado, la imagen llega por otra petición y clasificar es del curso. **Color e icono obligatorios y sin regla cruzada**: la categoría se pinta siempre con los dos, y la portada es un adorno. El color se normaliza como el de la membresía y **no es único**; el orden no es único y se desempata por identificador. Crea la primera tabla del módulo y siembra los cuatro `course-categories:` con la guarda de siempre. | Responsable técnico |
