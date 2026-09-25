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

Poner en el sistema un **curso** —título, instructor, dificultad y orden, y **las categorías en que se encuentra** si se piden— para empezar a armarlo: los módulos, las recomendaciones, las membresías, los servicios y la portada entran después, cada uno por su operación.

## 2. Contexto

Es el alta del paquete (`RF-PM-017`) con otra entidad: crea la tabla `courses`, **siembra los seis permisos `courses:`** —los cuatro de administración, `courses:teach` y `courses:learn`— con la obligación de asociarlos a `SUPERADMIN` y `ADMIN` en la misma migración, y devuelve el curso en la forma del detalle. Las decisiones que dan forma al curso están en [`requirements/ac.md` §5.2](../../../requirements/ac.md) y no se repiten.

**El curso nace `INACTIVO` y vacío.** Inactivo porque publicar es otra operación con sus condiciones (`RF-AC-012`, `RN-AC-009`); vacío porque cada relación tiene sus reglas —una categoría retirada no admite, un curso no se recomienda a sí mismo, una membresía tiene que existir en `SP`— y un alta que las juntara fallaría por siete motivos a la vez. Y **sin código**: no se teclea en ninguna venta ni se imprime en ningún comprobante (`ac.md` §5.2.6).

**Salvo las categorías, desde el 25-09-2026** (`ac.md` §5.2.9), por decisión del responsable del proyecto: el alta admite **`categoryIds`**, una lista de categorías en las que el curso nace clasificado, con las mismas reglas que `RF-AC-016` y **todo o nada** —una categoría que no sirve rechaza el alta entera y no se crea nada—. Es la relación que menos reglas tiene (`RN-AC-010`: viva y sin repetir), y la que la pantalla de alta pide junto al título. Las demás siguen fuera por lo que dice §14.3.

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
- **Clasificarlo en las categorías de `categoryIds`**, si vienen, en la misma transacción, con una fila de auditoría por pareja como `RF-AC-016`.
- Devolver el curso en la **forma del detalle** (`RF-AC-010`): instructor resuelto, sus categorías, las demás listas vacías, `offerable: false` con su motivo.

### 4.2 No incluye

- **Activarlo.** Nace `INACTIVO` (`RN-AC-008`) y se publica con `RF-AC-012`, cuando tenga descripciones y un módulo activo.
- **Recomendarle un previo, darle membresías o servicios.** `RF-AC-018`, `RF-AC-020`, `RF-AC-037`. Clasificarlo **después** del alta sigue siendo `RF-AC-016`.
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
| Categorías (`categoryIds`) | No | En qué cajones nace | Lista de identificadores, **sin repetir y sin nulos**, cada uno de una categoría **viva**; ausente o vacía es «sin categorías». Desde el 25-09-2026 |
| Servicios (`productIds`) | No | Qué servicios lo abren (`RN-AC-020`) | Lista **sin repetir y sin nulos**, cada uno un producto **`BOT` no retirado**; ausente o vacía es «ninguno». Desde el 25-09-2026 |
| Membresías (`membershipIds`) | No | Qué niveles lo abren (`RN-AC-012`) | Lista **sin repetir y sin nulos**, cada una una membresía **existente**; ausente o vacía es «ninguna». Desde el 25-09-2026 |

**Ni estado, ni módulos, ni recomendaciones, ni portada.** Cualquiera de ellos en el cuerpo es un campo desconocido y se rechaza como tal; **`categories`, `products` y `memberships`** —los nombres de las listas en la respuesta— también: se envían `categoryIds`, `productIds` y `membershipIds`.

### 6.2 Salida

`201` con el curso en la **misma forma del detalle** (`RF-AC-010`): identificador, título, **instructor resuelto** —identificador, nombre de usuario y nombre completo—, dificultad, descripciones y video presentes y nulos si no vinieron, orden, estado `INACTIVO`, `coverImageUrl` **presente y nula**, `categories` **con las pedidas en su orden** —vacía si no se pidió ninguna—, `recommendedCourses`, `memberships`, `products` y `modules` **vacíos**, `totalDurationSeconds` y `lessonCount` en **cero**, `offerable: false` con `offerableReason` diciendo que está inactivo, y las dos fechas de auditoría iguales.

## 7. Precondiciones y postcondiciones

**Precondiciones:** actor autenticado con `courses:create`; título libre entre los vivos; instructor válido según `RN-AC-006`.

**Postcondiciones:** existe la fila en `courses` con `status = INACTIVO`, `instructor_id` puesto y `cover_image_id` nulo; `audit_change_log` tiene una fila `CREATE` de `courses` y, **por cada categoría pedida**, una fila en `course_category_items` y una `CREATE` de esa tabla con el curso como entidad. Ningún evento de seguridad.

## 8. Flujo principal

1. Llega la petición con título, instructor, dificultad, orden y —si vienen— las dos descripciones y el video.
2. El sistema valida la forma de los siete, **juntos** (§11).
3. El sistema comprueba que el **título** no lo usa otro curso vivo (`EX-001`).
4. El sistema comprueba el **instructor** por las interfaces que `SP` publica: que existe y no está retirado (`EX-002`), y que porta `courses:teach` (`EX-003`).
5. El sistema resuelve **todas** las categorías de `categoryIds`, vivas, en una lectura (`EX-004`); **todos** los servicios de `productIds` por la interfaz de `PM` (`EX-005`); y **todas** las membresías de `membershipIds` por la de `SP` (`EX-006`).
6. El sistema inserta el curso en `INACTIVO`, lo clasifica, le da sus servicios y sus membresías, y registra cada fila en la auditoría, en la misma transacción.
7. Devuelve `201` con el curso y sus tres listas.

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

### EX-004 — Alguna categoría no existe o está retirada

**Respuesta del sistema:** `422` — *«Estas categorías no existen o están retiradas: {identificadores}.»* **Nombra todas las que fallan**, no la primera, y **no se crea nada**: ni el curso ni ninguna clasificación. Desde el 25-09-2026.

### EX-005 — Algún servicio no existe, está retirado o no es un `BOT`

**Respuesta del sistema:** `422` — *«Estos productos no son servicios vivos: {códigos o identificadores}.»* Nombra todos; un upgrade se nombra por su código. **No se crea nada.**

### EX-006 — Alguna membresía no existe

**Respuesta del sistema:** `422` — *«Estas membresías no existen: {identificadores}.»* Nombra todas. **No se crea nada.**

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
| `VAL-008` | `categoryIds`, `productIds` y `membershipIds`, si vienen, sin identificadores repetidos ni nulos | La lista de categorías no puede traer identificadores repetidos ni vacíos. (y el mismo mensaje con «servicios» y «membresías») |

`VAL-001` a `VAL-006` y `VAL-008` se devuelven **juntas**.

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-AC-034` | El sistema registra el curso con `201` en la forma del detalle: `INACTIVO`, instructor resuelto con nombre de usuario y nombre completo, las cuatro listas vacías, `coverImageUrl` presente y nula, cero segundos y cero lecciones, y `offerable: false` con motivo «inactivo» |
| `CA-AC-035` | El sistema rechaza con `409` un título que ya usa un curso vivo, sin distinguir mayúsculas ni acentos, y **admite** el de uno retirado |
| `CA-AC-036` | El sistema rechaza con `422` `EX-002` un instructor inexistente y uno retirado, y con `422` `EX-003` una persona viva **sin `courses:teach`** — también una que lo tenga por un rol **inactivo** |
| `CA-AC-037` | El sistema rechaza con `400` el título ausente o largo, el instructor ausente, la dificultad ausente o fuera de dominio, el orden ausente o negativo, las descripciones largas y el video mal formado, **juntos** |
| `CA-AC-038` | El sistema rechaza con `400` un cuerpo que traiga `status`, `categories`, `memberships`, `modules`, `coverImageUrl` o `code` |
| `CA-AC-039` | Las descripciones de solo espacios se guardan **nulas**; el video se guarda tal como llegó, sin seguirlo |
| `CA-AC-040` | El sistema registra una fila `CREATE` en `audit_change_log` con el actor, en la misma transacción, con `instructor_id` en la instantánea |
| `CA-AC-041` | Los seis `courses:` están sembrados con identificador estable de la serie de `AC` y asociados a `SUPERADMIN` y `ADMIN`, y **no** a `CLIENTE`; sin `courses:create` el alta responde `403` aunque el actor porte los cuatro `course-categories:` |
| `CA-AC-042` | Dos altas simultáneas con el mismo título dejan **una** fila y un `409` |
| `CA-AC-043` | Revocar después el rol que daba `courses:teach` al instructor **no cambia el curso**: el detalle lo sigue nombrando |
| `CA-AC-227` | **Desde el 25-09-2026**: el alta con `categoryIds` responde `201` con esas categorías en `categories`, en su orden, y deja una fila en `course_category_items` y una `CREATE` de esa tabla por cada una; sin `categoryIds`, o con la lista vacía, el curso nace sin categorías |
| `CA-AC-228` | El alta con una categoría inexistente o retirada responde `422` `EX-004` **nombrando todas las que fallan**, y **no deja nada**: ni el curso, ni clasificaciones, ni auditoría |
| `CA-AC-230` | **Desde el 25-09-2026, con `productIds` y `membershipIds`**: el alta deja al curso con esos servicios en `products` y esas membresías en `memberships`, con una fila y un `CREATE` por cada uno, como `RF-AC-037` y `RF-AC-020` |
| `CA-AC-231` | Un servicio inexistente, retirado o upgrade responde `422` `EX-005` y una membresía inexistente `422` `EX-006`, **nombrando todos los que fallan**, y **no queda nada** |
| `CA-AC-232` | Un curso `ACTIVO` no se crea —nace `INACTIVO`—, de modo que el alta con llaves **no lo ofrece**: el detalle dice «inactivo» |
| `CA-AC-229` | El alta con `categoryIds` repetidos o con un nulo responde `400` `VAL-008`, **junto** con los demás errores de forma |

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
| 3 | ¿El alta admite categorías y membresías dentro? | **Las categorías, los servicios y las membresías, sí, desde el 25-09-2026** —las dos últimas por una segunda decisión del mismo día (`ac.md` §5.2.9)—; **las recomendaciones, no**. Lo que sigue es la resolución de la primera decisión: **las categorías sí**, por decisión del responsable del proyecto (`ac.md` §5.2.9): es la relación de una sola regla —viva y sin repetir—, y **todo o nada** evita el rollback parcial que esta respuesta temía. **Las membresías, los servicios y las recomendaciones, no**, por lo que decía la resolución original: cada una tiene reglas propias —en `SP`, en `PM`, contra sí mismo— y un `422` de cinco orígenes no lo entiende nadie |

## 15. Control de cambios

| Versión | Fecha | Cambio | Responsable |
|---|---|---|---|
| 0.1.0 | 18-09-2026 | Redacción inicial. **El curso nace inactivo, vacío y sin código**; lo único que el alta comprueba fuera del módulo es el instructor, y para eso **pide a `SP` la primera interfaz que responde sobre un permiso** («¿porta `courses:teach`?»). Instructor inexistente o retirado y sin permiso son dos `422` distintos porque se arreglan en sitios distintos. Crea `courses` y siembra los seis `courses:`. | Responsable técnico |
| 0.2.0 | 18-09-2026 | **Enmienda de Art. I.7 (18-09-2026)**: `RN-AC-015` gana dos motivos por decisión del responsable del proyecto —«sin descripción» en el curso y «sin contenido» en la lección—. `CourseOfferability` (`plan.md` §3, `tasks.md` `T-06`) nace con **cinco** motivos en su orden: retirado → inactivo → sin descripción → sin membresías → sin módulo activo con lección activa con contenido. La respuesta del alta no cambia: un curso recién creado sigue diciendo «inactivo». | Responsable técnico |
| 0.3.0 | 18-09-2026 | **Construida** (`V21`, `V22`, `CoursesIT` (9), `CourseConcurrencyIT`, `CoursesPermissionsSeedIT`, `PermissionHolderLookupIT` en `SP`, `CourseTest`, `CourseOfferabilityTest`). Sin enmiendas al construir: la spec se cumplió tal como se escribió, con los cinco motivos de la 0.2.0. El puerto de `SP` y sus dos enmiendas documentales fueron en commit propio (`b15853a`). | Responsable técnico |
| 0.4.0 | 25-09-2026 | **Enmienda por decisión del responsable del proyecto** (`ac.md` §5.2.9): **el alta admite `categoryIds`** y el curso nace clasificado en ellas, todo o nada. Nacen `EX-004` —`422` que nombra todas las categorías que fallan— y `VAL-008` —repetidas o nulas—, y `CA-AC-227` a `CA-AC-229`. §14.3 cambia de respuesta para las categorías y la conserva para las demás relaciones. `categories` sigue siendo un campo no admitido en la entrada: la lista se envía como `categoryIds`. | Responsable técnico |
| 0.5.0 | 25-09-2026 | **La enmienda de la 0.4.0 está construida** (`CourseRegistrationCategoriesIT`, `CourseClassifier`). **Una precisión a `CA-AC-229`**: las **nulas** salen con los demás errores de forma —es una restricción del elemento de la lista— y las **repetidas**, en el caso de uso antes de cualquier consulta, como `RF-MV-001` con sus líneas; no pueden salir juntas sin un validador propio que el proyecto no tiene. La escritura de cada clasificación es la de `RF-AC-016`, extraída a `CourseClassifier`. | Responsable técnico |
| 0.6.0 | 25-09-2026 | **Segunda enmienda del día, por decisión del responsable del proyecto** (`ac.md` §5.2.9): **el alta admite también `productIds` y `membershipIds`**, todo o nada, con las mismas escrituras que `RF-AC-037` y `RF-AC-020`. Nacen `EX-005` y `EX-006` —cada uno nombra todos los que fallan— y `CA-AC-230` a `CA-AC-232`; `VAL-008` vale para las tres listas. **La portada sigue fuera**: va por `RF-AC-014`. | Responsable técnico |
| 0.7.0 | 25-09-2026 | **La segunda enmienda está construida** (`CourseRegistrationCategoriesIT`, `CourseAccessWriter`). Los servicios se resuelven uno a uno por `ProductCatalog.findKind` y las membresías por `MembershipCatalog.find`: son pocos por curso y ninguno de los dos puertos tiene lectura por lote. | Responsable técnico |
| 0.8.0 | 25-09-2026 | **Enmienda por decisión del responsable del proyecto** (`ac.md` §5.2.10, `RN-AC-017`): **la duración de la lección se guarda en segundos**, y las sumas del módulo y del curso también: `durationSeconds` y `totalDurationSeconds` sustituyen a `durationMinutes` y `totalDurationMinutes` en el cuerpo de esta spec. Las filas anteriores de esta tabla conservan el nombre que tenía el campo en su fecha. | Responsable técnico |
