# SPEC — `RF-AC-011` Editar curso

| Campo | Valor |
|---|---|
| Requerimiento | `RF-AC-011` |
| Módulo | `AC` — Academia |
| Estado | **En revisión** |
| Autor | Responsable técnico |
| Aprobada por | — |
| Fecha de aprobación | — |

---

## 1. Objetivo

Corregir lo que se declaró de un curso —título, instructor, dificultad, descripciones, video y orden— sin tocar lo que no se pidió, y **reasignar al instructor** con la misma comprobación del alta.

## 2. Contexto

Es `RF-AC-004` para cursos y hereda entera su mecánica: parcial, el nulo explícito como orden donde el vacío es legítimo, unicidad del título frente a los otros vivos, auditoría solo de lo que cambió, respuesta en la forma del detalle, **sin inmutables**. Lo propio son dos cosas: **el instructor se reasigna** y al reasignarlo se repite `RN-AC-006` sobre el nuevo —existe, no retirado, porta `courses:teach`—; y **las descripciones y el video se vacían aunque el curso esté `ACTIVO`**: `RN-AC-009` rige al activar y no después, y un curso activo que se queda sin descripción sigue activo y deja de ofrecerse, que el detalle enseña. Es el mismo trato que `RF-PM-020` da a la descripción del paquete.

## 3. Actores

| Actor | Papel |
|---|---|
| Administrador | Corrige el curso |

## 4. Alcance

### 4.1 Incluye

- Corregir **título, instructor, dificultad, descripción corta, descripción larga, video de introducción y orden**, por separado o juntos.
- **Vaciar** las dos descripciones y el video con nulo explícito, en cualquier estado.
- Devolver el curso corregido en la forma del detalle (`RF-AC-010`).

### 4.2 No incluye

- **El estado.** `RF-AC-012`.
- **La portada.** `RF-AC-014`, `RF-AC-015`.
- **Las relaciones y el árbol.** Cada uno con sus operaciones.
- **Corregir un retirado.** Lo retirado no se corrige (`RN-AC-018`).

## 5. Reglas de negocio aplicables

| ID | Regla | Origen |
|---|---|---|
| `RN-AC-001` | El título nuevo no lo usa otro curso vivo | `requirements/ac.md` §5.1 |
| `RN-AC-002` | El orden es un entero ≥ 0; corregirlo no reordena a los demás | `requirements/ac.md` §5.1 |
| `RN-AC-005` | El video se corrige y se vacía, con la forma de `RN-PM-032` | `requirements/ac.md` §5.1 |
| `RN-AC-006` | El instructor nuevo porta `courses:teach`, comprobado al asignar | `requirements/ac.md` §5.1 |
| `RN-AC-007` | La dificultad es una de tres | `requirements/ac.md` §5.1 |
| `RN-AC-009` | Vaciar una descripción **no desactiva** el curso: la activación se comprueba al activar | `requirements/ac.md` §5.1 |
| `RN-AC-018` | Lo retirado no se corrige | `requirements/ac.md` §5.1 |

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción | Restricción |
|---|---|---|---|
| Identificador | Sí | Cuál | Ruta. Curso **vivo** |
| `title` | No | Título nuevo | Hasta 150; único entre los vivos; **no admite nulo** |
| `instructorId` | No | Instructor nuevo | `RN-AC-006` sobre el nuevo; **no admite nulo** |
| `difficulty` | No | Dificultad nueva | En el dominio; **no admite nulo** |
| `shortDescription` | No | Descripción corta nueva | Hasta 300; **nulo explícito la vacía** |
| `longDescription` | No | Descripción larga nueva | Hasta 10 000; **nulo explícito la vacía** |
| `introVideoUrl` | No | Video nuevo | Forma de `RN-AC-005`; **nulo explícito lo vacía** |
| `displayOrder` | No | Orden nuevo | Entero ≥ 0; **no admite nulo** |

**Al menos uno de los siete** tiene que venir.

### 6.2 Salida

`200` con el curso en la forma del detalle, con `updatedAt` avanzado **solo si algo cambió**.

## 7. Precondiciones y postcondiciones

**Precondiciones:** actor con `courses:update`; curso vivo; si viene título, libre entre los otros vivos; si viene instructor, válido según `RN-AC-006`.

**Postcondiciones:** la fila refleja los campos corregidos y solo esos; `audit_change_log` tiene una fila `UPDATE` con antes y después de cada campo que cambió — `instructor_id` incluido—, o ninguna si nada cambió.

## 8. Flujo principal

1. Llega la petición con uno o más campos.
2. El sistema valida la forma de lo que viene, **juntos**, y que venga al menos uno (§11).
3. El sistema resuelve el curso **vivo**, bloqueándolo: si no existe o está retirado, `EX-002`.
4. Si viene el título, comprueba que no lo usa **otro** vivo (`EX-001`).
5. Si viene el instructor, lo comprueba contra `SP` como el alta (`EX-003`, `EX-004`).
6. El sistema aplica los cambios, y si alguno cambió de valor, escribe y registra el diff.
7. Devuelve `200` con el detalle.

## 9. Flujos alternativos

### FA-001 — Vaciar una descripción de un curso `ACTIVO`

**Comportamiento:** se admite. El curso sigue `ACTIVO` y **deja de ofrecerse**: el detalle lo devuelve `offerable: false` con «sin descripción» —el tercer motivo de `RN-AC-015` desde el 18-09-2026— y el aula no lo enseña hasta que alguien la reponga (§14.1).

### FA-002 — Reasignar al mismo instructor

**Comportamiento:** se comprueba igual y no es un cambio: sin auditoría.

### FA-003 — El cuerpo trae los mismos valores

**Comportamiento:** `200` sin avanzar `updatedAt` ni auditar.

## 10. Excepciones

### EX-001 — El título ya lo usa otro curso vivo

**Respuesta del sistema:** `409` — *«Ya existe un curso con ese título.»* El mismo código que el alta, por lo mismo que en `RF-AC-004`.

### EX-002 — El curso no existe o está retirado

**Respuesta del sistema:** `404` — *«No existe un curso vivo con ese identificador.»*

### EX-003 — El instructor nuevo no existe o está retirado

**Respuesta del sistema:** `422`, con el mensaje de `RF-AC-008` `EX-002`.

### EX-004 — El instructor nuevo no porta `courses:teach`

**Respuesta del sistema:** `422`, con el mensaje de `RF-AC-008` `EX-003`.

## 11. Validaciones

| ID | Validación | Mensaje esperado |
|---|---|---|
| `VAL-001` | Identificador con formato válido | El identificador indicado no tiene un formato válido. |
| `VAL-002` | El título no admite vaciarse y cabe en 150 | El título del curso no puede quedar vacío ni superar los 150 caracteres. |
| `VAL-003` | El instructor no admite vaciarse | El instructor del curso no puede quedar vacío. |
| `VAL-004` | La dificultad no admite vaciarse y está en el dominio | La dificultad del curso no puede quedar vacía y debe ser PRINCIPIANTE, INTERMEDIO o AVANZADO. |
| `VAL-005` | El orden no admite vaciarse y es ≥ 0 | El orden del curso no puede quedar vacío y debe ser un entero mayor o igual que cero. |
| `VAL-006` | Descripciones dentro de su tope y video con la forma admitida, si vienen con valor | Los de `RF-AC-008` `VAL-005` y `VAL-006` |
| `VAL-007` | Al menos un campo corregible | Debe informar al menos uno de los campos corregibles. |
| `VAL-008` | Ningún campo desconocido — `status`, `categories`, `memberships`, `modules`, `coverImageUrl`, `code` | El cuerpo de la petición contiene campos no admitidos. |

Todas se devuelven **juntas**, antes de cualquier consulta.

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-AC-057` | El sistema corrige los siete campos por separado y juntos, y devuelve el detalle con `updatedAt` avanzado |
| `CA-AC-058` | El nulo explícito **vacía** las dos descripciones y el video —**también en un curso `ACTIVO`**, que sigue `ACTIVO`— y **se rechaza** en título, instructor, dificultad y orden, con `400` y los errores juntos |
| `CA-AC-059` | El sistema rechaza con `400` un cuerpo vacío y uno que traiga `status`, `categories`, `memberships`, `modules`, `coverImageUrl` o `code` |
| `CA-AC-060` | El sistema rechaza con `409` un título que ya usa **otro** vivo, admite el de un retirado y cambiar solo la caja del propio; y con `404` un curso retirado o inexistente |
| `CA-AC-061` | Reasignar el instructor comprueba `RN-AC-006`: `422` el inexistente, el retirado y el que no porta `courses:teach`; el que sí, queda con antes y después en la auditoría |
| `CA-AC-062` | Un cuerpo sin cambios de valor —incluido reasignar al mismo instructor— responde `200` sin avanzar `updatedAt` ni auditar; uno con cambios deja la fila `UPDATE` con **solo** los campos que cambiaron |
| `CA-AC-063` | Un video mal formado responde `400` junto a los demás errores, y uno bien formado se guarda sin seguirlo |
| `CA-AC-214` | **Enmienda del 18-09-2026**: vaciar cualquiera de las dos descripciones de un curso `ACTIVO` y ofrecido lo deja `ACTIVO` con `offerable: false` y `offerableReason` «sin descripción», y reponerla lo devuelve a `offerable: true` sin tocar el estado; el aula lo comprueba desde `RF-AC-033` |

## 13. Casos límite

| Caso | Decisión |
|---|---|
| El título nuevo coincide con el de un retirado | Se admite |
| Reasignar a un instructor que porta el permiso por un rol **inactivo** | `422` `EX-004`: un rol inactivo no concede (como `SP` resuelve los permisos efectivos) |
| Se corrige el orden de un curso mientras el listado se lee | El listado siguiente lo enseña en su sitio nuevo; no hay bloqueo de lectura |

## 14. Preguntas abiertas

| # | Pregunta | Resolución |
|---|---|---|
| 1 | ¿Vaciar una descripción de un curso activo lo desactiva? | **No.** El estado es lo que alguien decidió; `RN-AC-009` rige al activar. **Pero lo saca de la oferta** desde el 18-09-2026: el responsable del proyecto decidió que «sin descripción» sea **un motivo más en `RN-AC-015`** —no una desactivación—, y el detalle lo dice como tercer motivo. Hasta ese día esta spec dejaba escrito que el curso seguía ofreciéndose vacío |

## 15. Control de cambios

| Versión | Fecha | Cambio | Responsable |
|---|---|---|---|
| 0.1.0 | 18-09-2026 | Redacción inicial. Hereda `RF-AC-004` entera y añade **la reasignación del instructor con la comprobación del alta** y **el vaciado de descripciones y video en cualquier estado**. Deja escrito en §14.1 que hoy un curso activo sin descripción sigue ofreciéndose, porque `RN-AC-015` no lo lista como motivo. | Responsable técnico |
| 0.2.0 | 18-09-2026 | **Enmienda de Art. I.7 (18-09-2026)**: `RN-AC-015` gana dos motivos por decisión del responsable del proyecto —«sin descripción» en el curso y «sin contenido» en la lección—. Se cierra la pregunta de §14.1 en el sentido de **sí lo saca de la oferta**: `FA-001` reescrito y **`CA-AC-214`** añadido; el estado sigue sin tocarse. | Responsable técnico |
| 0.3.0 | 18-09-2026 | **Construida** (`CourseUpdateIT` (8), incluido `CA-AC-214`). `InstructorVerifier` extraído y compartido con el alta; **reasignar al mismo instructor no repite la comprobación** —nada cambia y nada se audita (`CA-AC-062`)—, y solo el instructor nuevo cruza por los dos puertos. | Responsable técnico |
| 0.4.0 | 25-09-2026 | **Enmienda por decisión del responsable del proyecto** (`ac.md` §5.2.11, `RN-AC-005` reescrita): `VAL-006` rechaza un video de introducción que no sea de YouTube o de Vimeo, con el mensaje de `RF-AC-008`. Los ya guardados de otros dominios se conservan hasta que alguien los corrija. | Responsable técnico |
| 0.5.0 | 25-09-2026 | **La enmienda de §5.2.11 está construida**: el video solo se valida contra `VideoLink` —YouTube o Vimeo—, sin consultar al proveedor. | Responsable técnico |
