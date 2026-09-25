# PLAN — `RF-AC-008` Registrar curso

| Campo | Valor |
|---|---|
| Requerimiento | `RF-AC-008` |
| Especificación | [`spec.md`](spec.md), en revisión desde el 18-09-2026 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 18-09-2026 |

---

## 1. Enfoque

**El alta de la categoría (`RF-AC-001`) con una entidad más ancha, dos puertos de `SP` y un objeto de ofrecibilidad por delante.**

Se hereda entera la forma del bloque 1 —validación conjunta, unicidad parcial del título traducida por el repositorio, auditoría `CREATE`, relectura del detalle— y se añaden las tres cosas que la categoría no tenía: **el instructor**, que se comprueba contra `SP` por `UserCatalog` (existe) y por **`PermissionHolderLookup`** (porta `courses:teach`), la interfaz que este plan le pide a `SP`; **el estado**, que nace `INACTIVO`; y **`CourseOfferability`**, que nace aquí con su orden completo de motivos y con los dos que ya se pueden decidir —retirado, inactivo—, y que los bloques 3 y 4 alimentarán con módulos y membresías sin reescribir.

## 2. Cambios de esquema

**Dos migraciones**, tablas y permisos por separado. **Los números se asignan al construir**: `V20` la reservó `SP` el 18-09-2026 (`client_sellers`), de modo que serán `V21` y `V22` salvo que otra tanda se adelante.

### `V21__ac_cursos.sql`

```sql
CREATE TABLE courses (
    id                uuid         PRIMARY KEY,
    title             varchar(150) NOT NULL,
    instructor_id     uuid         NOT NULL,
    difficulty        varchar(20)  NOT NULL,
    short_description varchar(300) NULL,
    long_description  text         NULL,
    intro_video_url   varchar(500) NULL,
    display_order     integer      NOT NULL,
    status            varchar(20)  NOT NULL DEFAULT 'INACTIVO',
    cover_image_id    uuid         NULL,
    created_at        timestamptz  NOT NULL DEFAULT now(),
    updated_at        timestamptz  NOT NULL DEFAULT now(),
    deleted_at        timestamptz  NULL,
    CONSTRAINT fk_courses_instructor        FOREIGN KEY (instructor_id) REFERENCES users (id),
    CONSTRAINT ck_courses_difficulty        CHECK (difficulty IN ('PRINCIPIANTE','INTERMEDIO','AVANZADO')),
    CONSTRAINT ck_courses_status            CHECK (status IN ('ACTIVO','INACTIVO')),
    CONSTRAINT ck_courses_display_order     CHECK (display_order >= 0),
    CONSTRAINT ck_courses_intro_video_url   CHECK (intro_video_url IS NULL OR intro_video_url ~ '^https?://[^[:space:]]+$')
);
CREATE UNIQUE INDEX uq_courses_title
    ON courses (f_unaccent(lower(title))) WHERE deleted_at IS NULL;
CREATE INDEX ix_courses_instructor ON courses (instructor_id);
```

- **`fk_courses_instructor` se declara hacia `users`**, sin `ON DELETE`: la persona no se borra físicamente nunca (`RF-SP-029`), y la frontera de `modules.md` §7 es la del código. **Que porte `courses:teach` no cabe en la clave**: vive en el caso de uso.
- **`cover_image_id` nace nulable y sin clave foránea**, como en `course_categories`: `RF-AC-014` no tendrá que abrir el modelo de lectura, y `RF-AC-006` —que crea `academy_images`— añadirá la restricción de las tres tablas que la señalan en la misma migración.
- **`ck_courses_intro_video_url` es la expresión de `RN-PM-032`**, con la rama `IS NULL` delante y explícita.
- **`ix_courses_instructor`** sostiene el filtro por instructor de `RF-AC-009`; el orden por `display_order` no lleva índice por lo mismo que en la categoría.
- **`status` con `DEFAULT 'INACTIVO'`** y no `'ACTIVO'` como `products`: el `DEFAULT` de `products` es una herencia de antes de `RN-PM-012`; aquí el esquema dice lo mismo que la regla.

### `V22__ac_semilla_permisos_cursos.sql`

Seis filas con identificador literal —la serie de `AC` continúa: `…000005` a `…000010`—, asociadas a `SUPERADMIN` y `ADMIN` en la misma migración **explícitamente** (la lección de `V19`: `V8` asocia a `ADMIN` por exclusión solo sobre el catálogo de aquel día) y con la guarda que cuenta doce. **A `CLIENTE` no**. `courses:teach` y `courses:learn` se siembran igual que los cuatro de administración: un administrador que los porte puede ser instructor y recorrer el aula (`ac.md` §4). Las cuatro suites del catálogo pasan de **54 a 60**.

## 3. Componentes afectados

| Capa | Elemento | Módulo |
|---|---|---|
| `domain/models` | `Course` (entidad), `CourseStatus`, `CourseDifficulty`; el constructor recorta título y descripciones, deja las descripciones nulas si quedan vacías y valida la forma del video con **`VideoUrl`**, objeto de valor que reutilizarán el módulo (`RF-AC-022`) y la lección (`RF-AC-028`) | `AC` |
| `domain/models` | **`CourseOfferability`** — `RN-AC-015` en un solo sitio: recibe retiro, estado, **si tiene las dos descripciones**, cuántas membresías y cuántos módulos ofrecibles, y devuelve `(offerable, reason)` con el **primer** motivo en orden fijo: retirado → inactivo → **sin descripción** → sin membresías → sin módulo activo con lección activa con contenido (**cinco motivos** desde el 18-09-2026, `ac.md` §5.2.7). **Hoy los dos últimos reciben cero siempre**, porque las tablas no existen; los bloques 3 y 4 cambian lo que se le pasa, no el objeto | `AC` |
| `domain/repository` | `CourseRepository` + `JpaCourseRepository`: `save` con traducción de `uq_courses_title`, `existsAliveTitle`, `existsAliveTitleForOther`, `findAliveByIdForUpdate`, `findByIdForUpdate`, `flush` | `AC` |
| `domain/repository` | `CourseQueryRepository` + `Jpa…`: `findDetail(id)` — el curso **con su instructor resuelto por `JOIN users`** (`username`, `first_name`, `last_name`; nada más), en una sentencia; y las lecturas de relaciones y módulos, que hoy devuelven vacío y las estrenan sus requerimientos | `AC` |
| `domain/service` | `RegisterCourseService`; **`CourseDetailReader`** (`MODULO = "AC"`, `ENTIDAD = "courses"`): detalle → relaciones → módulos → ofrecibilidad → motivo de retiro, para las nueve operaciones que devuelven el curso | `AC` |
| `application` | `RegisterCourseRequest`, `CourseDetailResponse` (con `InstructorRef`, las cuatro listas, `totalDurationSeconds`, `lessonCount`, `offerable`, `offerableReason`, `coverImageUrl`) | `AC` |
| `interfaces` | `CourseController` — `POST /api/v1/courses` | `AC` |
| **`SP` · `users/application`** | **`PermissionHolderLookup`** — `boolean holds(UUID userId, String permissionCode)`; e implementación `JpaPermissionHolderLookup` en `users/domain/repository`: un `EXISTS` con **el mismo predicado** que `JpaEffectivePermissions` —roles vivos y `ACTIVO`, persona no retirada— acotado al código | `SP` |
| `shared/security` | La ruta en `EndpointPermissionsIT`; `CoursesPermissionsSeedIT` nuevo; el recuento de las cuatro suites del catálogo | `shared` |

**El instructor se resuelve dos veces y de dos maneras, a propósito.** Al **escribir**, por los puertos: `UserCatalog.find` dice si existe y si está retirado, `PermissionHolderLookup.holds` si porta el permiso — son reglas, y las reglas cruzan por `application`. Al **leer**, por `JOIN users` en la sentencia del detalle y del listado: es el precedente de `RF-PM-012` con las reseñas, «la regla de ArchUnit prohíbe importar repositorios y entidades de `SP`, no nombrar sus tablas en una sentencia»; ninguna regla se decide con él, y un puerto «nombre por identificador» llamado por fila sería el `N+1` de siempre. **Se seleccionan exactamente tres columnas de `users`**, y no el correo ni el estado.

## 4. Contrato de API

`POST /api/v1/courses` — `courses:create`.

```json
{ "title": "Introducción al trading", "instructorId": "…", "difficulty": "PRINCIPIANTE",
  "shortDescription": "…", "longDescription": "…", "introVideoUrl": "https://…", "displayOrder": 0 }
```

`201` con `CourseDetailResponse` y cabecera `Location`:

```json
{
  "id": "…", "title": "Introducción al trading",
  "instructor": { "id": "…", "username": "jperez", "fullName": "Juan Pérez" },
  "difficulty": "PRINCIPIANTE",
  "shortDescription": "…", "longDescription": "…", "introVideoUrl": "https://…",
  "displayOrder": 0, "status": "INACTIVO", "coverImageUrl": null,
  "categories": [], "recommendedCourses": [], "memberships": [], "modules": [],
  "totalDurationSeconds": 0, "lessonCount": 0,
  "offerable": false, "offerableReason": "El curso está inactivo.",
  "createdAt": "…", "updatedAt": "…"
}
```

- **`offerable` y `offerableReason` siempre presentes**: `true` con motivo nulo; `false` con el **primer** motivo en el orden de `CourseOfferability`.
- **`instructor.fullName` es el nombre actual** de la persona, no una copia: quien lo muestre debe saberlo (`UserCatalog`).

| Código | Cuándo |
|---|---|
| `400` | `VAL-001` a `VAL-007` |
| `401` / `403` | Sin sesión / sin `courses:create` |
| `409` | `EX-001` |
| `422` | `EX-002`, `EX-003` |

## 5. Autorización

`@PreAuthorize("hasAuthority('courses:create')")`. Los `course-categories:` **no** habilitan (`CA-AC-041`).

## 6. Auditoría

`ChangeEvent` `CREATE` de `courses` con la instantánea del curso, `instructor_id` incluido. Sin evento de seguridad.

## 7. Transaccionalidad

`@Transactional`. **Cinco sentencias**: título, la persona (`UserCatalog`), el permiso (`PermissionHolderLookup`), `INSERT`, auditoría; y **una más** para releer el detalle —que hoy no tiene relaciones que leer—.

## 8. Impacto sobre otros módulos

**`SP` publica una interfaz nueva, `PermissionHolderLookup`**, la primera que responde sobre un permiso y no sobre un dato (`ac.md` §3, `modules.md` §5.5). Es una lectura más bajo la norma de D-25: la declara `SP` en su `application`, la implementa `SP` sobre su propia consulta de permisos efectivos, y `AC` la importa. **No devuelve la lista de permisos**: un booleano sobre un código, para no dar con qué reconstruir fuera de `SP` la autorización que es suya.

**Enmiendas que este plan declara** y que se aplican **al construir `T-04`**, y no en este pase, porque `requirements/sp.md` está tomado por la rama `feature/vendedores-de-un-cliente` (1.59.0) y `architecture.md` por la misma: [`requirements/sp.md` §8](../../../requirements/sp.md) gana el párrafo de la tercera lectura publicada —**«¿esta persona porta este permiso?»**, con quién la pide y por qué no devuelve la lista— y [`architecture.md` §15.2](../../../architecture.md) su fila en la tabla de interfaces, numeradas por encima de lo que esa rama deje. Queda como bloqueo explícito en `tasks.md`.

## 9. Alternativas consideradas

| Alternativa | Por qué se descartó |
|---|---|
| **Leer `user_roles` y `role_permissions` desde `AC`** | Es exactamente lo que `modules.md` §7 prohíbe y la regla de ArchUnit de `RF-AC-001` impide compilar |
| **Que `SP` publique la lista de permisos de una persona** | Da con qué reconstruir fuera de `SP` la autorización que es suya; un booleano sobre un código no |
| **Reutilizar `EffectivePermissions.forUser` desde `AC`** | Es `domain/repository` de `SP`: no cruza. La implementación del puerto sí lo reutiliza por dentro |
| **Un rol «Instructor» sembrado por `SP` y exigido por nombre** | `SP` no sabe de academia y un nombre fijo es una constante en dos módulos (`ac.md` §5.2.5) |
| **Resolver el nombre del instructor por `UserCatalog` en cada lectura** | `N+1` en el listado; el `JOIN` de lectura tiene precedente en `RF-PM-012` |
| **Nacer `CourseOfferability` en el bloque 3, «cuando tenga con qué»** | Ya tiene con qué decidir tres de los cinco motivos, y la respuesta del alta necesita `offerableReason` desde hoy; nacer después obligaría a un literal que luego alguien olvidaría sustituir |
| **Descripción larga sin tope** | `text` sin cota admite un cuerpo de un megabyte; 10 000 caracteres son unas veinte páginas |

## 10. Riesgos

| # | Riesgo | Mitigación |
|---|---|---|
| 1 | **`PermissionHolderLookup` diverge de `JpaEffectivePermissions`** —un rol inactivo concede en uno y no en el otro— | El predicado se escribe una vez como constante en `SP` y las dos consultas lo usan; `CA-AC-036` prueba el rol inactivo |
| 2 | **Dos altas simultáneas** con el mismo título | Índice parcial; el repositorio traduce; `CA-AC-042` |
| 3 | **Se olvida asociar los seis a `ADMIN`** | La guarda de `V22` que cuenta doce, y `CoursesPermissionsSeedIT` |
| 4 | **La enmienda a `sp.md` se queda sin aplicar** por la coordinación de ramas | Bloqueo explícito en `tasks.md` `T-04`, y la definición de terminado la exige |

## 11. Estrategia de prueba

- **Unitarias**: `Course` (recorte, descripciones vacías → nulas, dificultad, orden, video mal formado); `VideoUrl` (los casos de `RN-PM-032`); **`CourseOfferability`** (el orden de los cinco motivos, con cero membresías y cero módulos hoy).
- **Integración de API** (`CoursesIT`): `CA-AC-034` a `CA-AC-041` y `CA-AC-043`; la carrera en `CourseConcurrencyIT` (`CA-AC-042`).
- **Del puerto** (`PermissionHolderLookupIT`, en `SP`): porta por un rol activo → verdadero; por un rol inactivo o retirado → falso; persona retirada → falso; persona inexistente → falso; permiso inexistente → falso.
- **Siembra** (`CoursesPermissionsSeedIT`): seis permisos, identificadores estables, doce asociaciones, ninguna a `CLIENTE`.
- **Contrato**: el esquema declara `CourseDetailResponse` con las cuatro listas y `offerable`; la prosa dice que nace inactivo y vacío, qué exige el instructor y que el nombre es el actual.

## 12. Enmienda del 25-09-2026 — categorías en el alta

Por decisión del responsable del proyecto (`spec.md` 0.4.0, `ac.md` §5.2.9).

| Capa | Cambio |
|---|---|
| `application` | `RegisterCourseRequest` gana `List<UUID> categoryIds`, opcional; `VAL-008` se comprueba en el caso de uso, **junto** con `VAL-001` a `VAL-006` |
| `domain/repository` | `CourseCategoryRepository.findAliveByIds(Collection<UUID>)`: **una** lectura para toda la lista, sin bloquear —el mismo trato que `RF-AC-016` §14.1 da a la categoría— |
| `domain/service` | `RegisterCourseService`: tras el instructor, resuelve las categorías y rechaza con `EX-004` las que falten; tras insertar el curso, **reutiliza la escritura de `RF-AC-016`** —fila y `ChangeEvent` `CREATE` de `course_category_items` por cada una—, extraída a un colaborador para que las dos puertas no puedan divergir en cómo se clasifica |

**Sin migración.** **La transacción es la del alta**: un fallo en cualquier categoría revierte el curso, y por eso las categorías se resuelven **antes** de insertar nada —el `422` no debe costar un `INSERT` revertido—. **Sin bloquear el curso**: acaba de nacer y nadie más lo ve. El orden de `categories` en la respuesta es el de la categoría (`RN-AC-002`), no el de la lista enviada.

**Descartado**: *ignorar las repetidas en silencio* —la lista es de lo que el cliente quiere, y dos veces la misma es un error suyo que conviene decirle—, y *crear el curso con las categorías que sirvan* —el rollback parcial que §14.3 temía—.
