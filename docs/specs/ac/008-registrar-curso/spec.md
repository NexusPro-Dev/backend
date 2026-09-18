# SPEC — `RF-AC-008` Registrar curso

| Campo | Valor |
|---|---|
| Requerimiento | `RF-AC-008` |
| Módulo | `AC` — Academia |
| Estado | **En revisión** |
| Autor | Responsable técnico |
| Aprobada por | — |
| Fecha de aprobación | — |

---

## 1. Objetivo

Poner en el sistema un **curso vacío** —título, instructor, dificultad y orden— para empezar a armarlo: los módulos, las categorías, las recomendaciones y las membresías entran después, cada uno por su operación.

## 2. Contexto

Es el alta del paquete (`RF-PM-017`) con otra entidad: crea la tabla `courses`, **siembra los seis permisos `courses:`** —los cuatro de administración, `courses:teach` y `courses:learn`— con la obligación de asociarlos a `SUPERADMIN` y `ADMIN` en la misma migración, y devuelve el curso en la forma del detalle. Las decisiones que dan forma al curso están en [`requirements/ac.md` §5.2](../../../requirements/ac.md) y no se repiten.

**El curso nace `INACTIVO` y vacío.** Inactivo porque publicar es otra operación con sus condiciones (`RF-AC-012`, `RN-AC-009`); vacío porque cada relación tiene sus reglas —una categoría retirada no admite, un curso no se recomienda a sí mismo, una membresía tiene que existir en `SP`— y un alta que las juntara fallaría por siete motivos a la vez. Y **sin código**: no se teclea en ninguna venta ni se imprime en ningún comprobante (`ac.md` §5.2.6).

**Lo único que el alta comprueba fuera del módulo es el instructor**, y es la primera vez que un módulo condiciona un dato suyo a un **permiso** de `SP` (`RN-AC-006`): la persona existe, no está retirada y **porta `courses:teach`**. Para lo tercero `SP` no publica nada todavía, y **este requerimiento se lo pide** (§14.1, `plan.md` §8).

## 3. Actores

| Actor | Papel |
|---|---|
| Administrador | Registra el curso |
| Instructor | Es nombrado; no interviene |

## 4. Alcance

### 4.1 Incluye

- Registrar un curso con **título, instructor, dificultad y orden**, obligatorios, y **descripción corta, descripción larga y video de introducción**, opcionales.
- Comprobar el instructor contra `SP`: existe, no está retirado, porta `courses:teach`.
- Crear `courses` y sembrar los seis `courses:`.
- Devolver el curso en la **forma del detalle** (`RF-AC-010`): instructor resuelto, listas vacías, `offerable: false` con su motivo.

### 4.2 No incluye

- **Activarlo.** Nace `INACTIVO` (`RN-AC-008`) y se publica con `RF-AC-012`, cuando tenga descripciones y un módulo activo.
- **Clasificarlo, recomendarle un previo, darle membresías.** `RF-AC-016`, `RF-AC-018`, `RF-AC-020`.
- **Módulos y lecciones.** `RF-AC-022` en adelante.
- **La portada.** `RF-AC-014`; la respuesta trae `coverImageUrl` presente y nulo.
- **Un código.** No existe el campo (`ac.md` §5.2.6).

## 5. Reglas de negocio aplicables

| ID | Regla | Origen |
|---|---|---|
| `RN-AC-001` | El título es único entre los cursos vivos, sin mayúsculas ni acentos | `requirements/ac.md` §5.1 |
| `RN-AC-002` | El orden es un entero ≥ 0, global, no único | `requirements/ac.md` §5.1 |
| `RN-AC-005` | El video de introducción es un enlace con la forma de `RN-PM-032`, y el sistema no lo sigue | `requirements/ac.md` §5.1 |
| `RN-AC-006` | **El instructor porta `courses:teach`**, comprobado al asignar | `requirements/ac.md` §5.1 |
| `RN-AC-007` | La dificultad es `PRINCIPIANTE`, `INTERMEDIO` o `AVANZADO` | `requirements/ac.md` §5.1 |
| `RN-AC-008` | El curso nace `INACTIVO` | `requirements/ac.md` §5.1 |
| `RN-AC-015` | La ofrecibilidad se calcula: un curso recién creado no se ofrece, y la respuesta dice por qué | `requirements/ac.md` §5.1 |
| `RN-SEG-003` | Los permisos se conceden por rol; ningún rol concede lo que su padre no tiene | `security.md` §4 |

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción | Restricción |
|---|---|---|---|
| Título (`title`) | Sí | Cómo se llama el curso | Hasta 150 tras recortar; **único entre los vivos** sin distinguir mayúsculas ni acentos |
| Instructor (`instructorId`) | Sí | Quién lo enseña | Una persona de `SP` que **existe, no está retirada y porta `courses:teach`** |
| Dificultad (`difficulty`) | Sí | Para quién es | `PRINCIPIANTE`, `INTERMEDIO` o `AVANZADO`, sin valor por omisión |
| Descripción corta (`shortDescription`) | No | La de la tarjeta | Hasta 300; **obligatoria para activar**, no para registrar; de solo espacios queda nula |
| Descripción larga (`longDescription`) | No | La de la página del curso | Hasta 10 000; **obligatoria para activar**; de solo espacios queda nula |
| Video de introducción (`introVideoUrl`) | No | La dirección de un video que presenta el curso | URL absoluta `http` o `https`, sin espacios, hasta 500 (`RN-AC-005`); nulo es «no tiene» |
| Orden (`displayOrder`) | Sí | En qué lugar se enseña | Entero ≥ 0, global, no único |

**Ni estado, ni categorías, ni membresías, ni módulos, ni portada.** Cualquiera de ellos en el cuerpo es un campo desconocido y se rechaza como tal.

### 6.2 Salida

`201` con el curso en la **misma forma del detalle** (`RF-AC-010`): identificador, título, **instructor resuelto** —identificador, nombre de usuario y nombre completo—, dificultad, descripciones y video presentes y nulos si no vinieron, orden, estado `INACTIVO`, `coverImageUrl` **presente y nula**, `categories`, `recommendedCourses`, `memberships` y `modules` **vacíos**, `totalDurationMinutes` y `lessonCount` en **cero**, `offerable: false` con `offerableReason` diciendo que está inactivo, y las dos fechas de auditoría iguales.

## 7. Precondiciones y postcondiciones

**Precondiciones:** actor autenticado con `courses:create`; título libre entre los vivos; instructor válido según `RN-AC-006`.

**Postcondiciones:** existe la fila en `courses` con `status = INACTIVO`, `instructor_id` puesto y `cover_image_id` nulo; `audit_change_log` tiene una fila `CREATE` de `courses`. Ningún evento de seguridad.

## 8. Flujo principal

1. Llega la petición con título, instructor, dificultad, orden y —si vienen— las dos descripciones y el video.
2. El sistema valida la forma de los siete, **juntos** (§11).
3. El sistema comprueba que el **título** no lo usa otro curso vivo (`EX-001`).
4. El sistema comprueba el **instructor** por las interfaces que `SP` publica: que existe y no está retirado (`EX-002`), y que porta `courses:teach` (`EX-003`).
5. El sistema inserta el curso en `INACTIVO` y registra la creación en la auditoría, en la misma transacción.
6. Devuelve `201` con el curso vacío.

El paso 3 tiene su red en el esquema —`uq_courses_title`, parcial—: la carrera entre dos altas la muerde el índice y el repositorio la traduce al mismo `409`.

## 9. Flujos alternativos

### FA-001 — Sin descripciones ni video

**Comportamiento:** se registra igual. Las descripciones son lo que `RF-AC-012` exigirá para activar; el video es opcional siempre.

### FA-002 — El instructor es el propio actor

**Comportamiento:** se admite si porta `courses:teach`. Nada distingue a quien registra de quien enseña.

### FA-003 — Al instructor se le revoca el permiso después

**Comportamiento:** el curso **no cambia** (`RN-AC-006`): sigue diciendo quién lo enseñó hasta que administración lo reasigne (`RF-AC-011`). La comprobación es al asignar y solo al asignar.

## 10. Excepciones

### EX-001 — El título ya lo usa un curso vivo

**Respuesta del sistema:** `409` — *«Ya existe un curso con ese título.»* Un retirado **sí** libera el título.

### EX-002 — El instructor no existe o está retirado

**Respuesta del sistema:** `422` — *«La persona indicada como instructor no existe o está retirada.»* El mismo código y el mismo trato que `PM` da a la moneda: un dato de otro módulo que no sirve.

### EX-003 — El instructor no porta `courses:teach`

**Respuesta del sistema:** `422` — *«La persona indicada no puede ser instructor: no porta el permiso courses:teach.»* Se distingue de `EX-002` porque lo que hay que arreglar es distinto: allí la persona, aquí sus roles (`RF-SP-030`).

## 11. Validaciones

| ID | Validación | Mensaje esperado |
|---|---|---|
| `VAL-001` | Título presente y de hasta 150 tras recortar | El título es obligatorio y no puede superar los 150 caracteres. |
| `VAL-002` | Instructor presente | El instructor es obligatorio. |
| `VAL-003` | Dificultad presente y en el dominio | La dificultad es obligatoria y debe ser PRINCIPIANTE, INTERMEDIO o AVANZADO. |
| `VAL-004` | Orden presente y ≥ 0 | El orden es obligatorio y debe ser un entero mayor o igual que cero. |
| `VAL-005` | Descripción corta de hasta 300 y larga de hasta 10 000 | La descripción corta no puede exceder 300 caracteres. · La descripción larga no puede exceder 10 000 caracteres. |
| `VAL-006` | Video, si viene, con la forma de `RN-AC-005` | El enlace del video debe ser una URL absoluta http o https, sin espacios y de hasta 500 caracteres. |
| `VAL-007` | Ningún campo desconocido — `status`, `categories`, `memberships`, `modules`, `coverImageUrl`, `code` | El cuerpo de la petición contiene campos no admitidos. |

Las seis primeras se devuelven **juntas**.

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-AC-034` | El sistema registra el curso con `201` en la forma del detalle: `INACTIVO`, instructor resuelto con nombre de usuario y nombre completo, las cuatro listas vacías, `coverImageUrl` presente y nula, cero minutos y cero lecciones, y `offerable: false` con motivo «inactivo» |
| `CA-AC-035` | El sistema rechaza con `409` un título que ya usa un curso vivo, sin distinguir mayúsculas ni acentos, y **admite** el de uno retirado |
| `CA-AC-036` | El sistema rechaza con `422` `EX-002` un instructor inexistente y uno retirado, y con `422` `EX-003` una persona viva **sin `courses:teach`** — también una que lo tenga por un rol **inactivo** |
| `CA-AC-037` | El sistema rechaza con `400` el título ausente o largo, el instructor ausente, la dificultad ausente o fuera de dominio, el orden ausente o negativo, las descripciones largas y el video mal formado, **juntos** |
| `CA-AC-038` | El sistema rechaza con `400` un cuerpo que traiga `status`, `categories`, `memberships`, `modules`, `coverImageUrl` o `code` |
| `CA-AC-039` | Las descripciones de solo espacios se guardan **nulas**; el video se guarda tal como llegó, sin seguirlo |
| `CA-AC-040` | El sistema registra una fila `CREATE` en `audit_change_log` con el actor, en la misma transacción, con `instructor_id` en la instantánea |
| `CA-AC-041` | Los seis `courses:` están sembrados con identificador estable de la serie de `AC` y asociados a `SUPERADMIN` y `ADMIN`, y **no** a `CLIENTE`; sin `courses:create` el alta responde `403` aunque el actor porte los cuatro `course-categories:` |
| `CA-AC-042` | Dos altas simultáneas con el mismo título dejan **una** fila y un `409` |
| `CA-AC-043` | Revocar después el rol que daba `courses:teach` al instructor **no cambia el curso**: el detalle lo sigue nombrando |

## 13. Casos límite

| Caso | Decisión |
|---|---|
| El instructor porta `courses:teach` por dos roles y se le retira uno | Sigue pudiendo ser instructor: basta uno vivo y activo |
| El instructor es `SUPERADMIN` | Se admite: porta todo el catálogo, `courses:teach` incluido |
| El título coincide con el **nombre de una categoría** | Se admite: entidades distintas, unicidad por tabla |
| `introVideoUrl` de un dominio que no existe | Se admite: el sistema comprueba la forma y **no lo sigue** (`RN-AC-005`) |
| El instructor está `INACTIVO` en `SP` pero no retirado | **Se admite**: `RN-AC-006` habla de retiro, no de estado. Un instructor de baja temporal sigue siendo quien enseñó el curso |

## 14. Preguntas abiertas

| # | Pregunta | Resolución |
|---|---|---|
| 1 | ¿Cómo sabe `AC` si la persona porta el permiso sin leer `user_roles`? | **`SP` publica una interfaz de lectura más**: «¿esta persona porta este permiso?», un booleano sobre un código. **No devuelve la lista** de permisos, para no dar con qué reconstruir fuera de `SP` la autorización que es suya. La ampliación pertenece a este requerimiento (`ac.md` §3, D-25), como `UserCatalog` perteneció a `RF-CM-001` |
| 2 | ¿Se comprueba también que la persona esté `ACTIVO`? | **No.** `RN-AC-006` dice existe, no retirada y con permiso. El estado de `SP` gobierna el acceso de esa persona, no si puede figurar como instructor |
| 3 | ¿El alta admite categorías y membresías dentro? | **No**, por lo mismo que el paquete: cada relación tiene sus reglas y un `400` de siete causas con rollback parcial no lo entiende nadie |

## 15. Control de cambios

| Versión | Fecha | Cambio | Responsable |
|---|---|---|---|
| 0.1.0 | 18-09-2026 | Redacción inicial. **El curso nace inactivo, vacío y sin código**; lo único que el alta comprueba fuera del módulo es el instructor, y para eso **pide a `SP` la primera interfaz que responde sobre un permiso** («¿porta `courses:teach`?»). Instructor inexistente o retirado y sin permiso son dos `422` distintos porque se arreglan en sitios distintos. Crea `courses` y siembra los seis `courses:`. | Responsable técnico |
| 0.2.0 | 18-09-2026 | **Enmienda de Art. I.7 (18-09-2026)**: `RN-AC-015` gana dos motivos por decisión del responsable del proyecto —«sin descripción» en el curso y «sin contenido» en la lección—. `CourseOfferability` (`plan.md` §3, `tasks.md` `T-06`) nace con **cinco** motivos en su orden: retirado → inactivo → sin descripción → sin membresías → sin módulo activo con lección activa con contenido. La respuesta del alta no cambia: un curso recién creado sigue diciendo «inactivo». | Responsable técnico |
