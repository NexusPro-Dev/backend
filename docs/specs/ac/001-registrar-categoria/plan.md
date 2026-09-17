# PLAN — `RF-AC-001` Registrar categoría

| Campo | Valor |
|---|---|
| Requerimiento | `RF-AC-001` |
| Especificación | [`spec.md`](spec.md), en revisión desde el 17-09-2026 |
| Estado | **Aprobado** — construido el 17-09-2026 |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 17-09-2026 |

---

## 1. Enfoque

**El alta del paquete (`RF-PM-017`), sin código, sin moneda, sin estado y con un módulo nuevo por delante.**

Se hereda la forma —validación conjunta, unicidad parcial del nombre con traducción de la restricción, auditoría `CREATE` en la misma transacción, relectura del detalle— y se quita todo lo que la categoría no tiene. Lo que este plan añade es **el esqueleto del módulo**: `modules/academy` con sus cuatro capas, la regla de ArchUnit que lo aísla como a los demás, y la primera migración de `AC`. Es más andamio que negocio, y por eso va primero.

## 2. Cambios de esquema

**Dos migraciones**, por el reparto de siempre —las tablas en una, los permisos en otra—. **Los números se asignan al construir**: `V17` la tomó `RF-MV-005` el 17-09-2026, de modo que serán `V18` y `V19` salvo que otra tanda se adelante — «una migración reservada no está reservada».

### `V18__ac_categorias.sql`

```sql
CREATE TABLE course_categories (
    id             uuid         PRIMARY KEY,
    name           varchar(150) NOT NULL,
    description    text         NULL,
    color          varchar(6)   NOT NULL,
    icon           varchar(50)  NOT NULL,
    display_order  integer      NOT NULL,
    cover_image_id uuid         NULL,
    created_at     timestamptz  NOT NULL DEFAULT now(),
    updated_at     timestamptz  NOT NULL DEFAULT now(),
    deleted_at     timestamptz  NULL,
    CONSTRAINT ck_course_categories_color_format  CHECK (color ~ '^[0-9A-F]{6}$'),
    CONSTRAINT ck_course_categories_icon_format   CHECK (icon ~ '^[a-z][a-z0-9-]*$'),
    CONSTRAINT ck_course_categories_display_order CHECK (display_order >= 0)
);
CREATE UNIQUE INDEX uq_course_categories_name
    ON course_categories (f_unaccent(lower(name))) WHERE deleted_at IS NULL;
```

- **`cover_image_id` nace ya en la tabla, nulable y sin clave foránea todavía**: la tabla `academy_images` la crea `RF-AC-006`, y su migración añadirá `fk_course_categories_cover_image` y `uq_course_categories_cover_image`. Se declara ahora para que el `SELECT` del detalle y el `coverImageUrl` de la respuesta existan desde el primer día con su forma definitiva, y `RF-AC-006` no tenga que tocar el modelo de lectura. **Es el mismo orden que siguió `products`**: la columna llegó con `V90` porque la portada llegó después; aquí se sabe de antemano y se ahorra un `ALTER`.
- **`ck_course_categories_color_format` es la expresión de `ck_memberships_color_format`**, sin el `uq_`: la restricción rechaza lo que llegue por cualquier vía en minúsculas, y la normalización la hace el dominio **antes** de escribir.
- **`ck_course_categories_icon_format`** es la del icono del producto, que en `products` no tiene `CHECK` porque el icono puede ser nulo y la forma solo se comprobaba en `VAL-012`; aquí es obligatorio y cabe en el esquema.
- **Sin `status`** (`RN-AC-008`) y **sin `code`** (`requirements/ac.md` §5.2.6).
- **`uq_course_categories_name` es un índice parcial** y no admite `DEFERRABLE`: muerde en el `INSERT`, y el repositorio lo traduce al `409` de `EX-001`.

### `V19__ac_semilla_permisos_categorias.sql`

Cuatro filas con identificador literal —**serie propia de `AC`**, distinta de las de `SP`, `PM`, `CM` y `MV`, `…000001` a `…000004`; los seis `courses:` seguirán del `…000005` al `…000010` en `RF-AC-008`—, asociadas a `SUPERADMIN` y `ADMIN` en la misma migración y **con la guarda** que cuenta ocho asociaciones. **A `CLIENTE` no**, por lo mismo de siempre. Las cuatro suites del catálogo de permisos pasan de **50 a 54**.

## 3. Componentes afectados

| Capa | Elemento | Módulo |
|---|---|---|
| `modules/academy` | **El paquete nuevo**, con `domain/{models,repository,service}`, `application` e `interfaces`, y su fila en la prueba de ArchUnit que impide que un módulo importe `domain` o `infrastructure` de otro | `AC` |
| `domain/models` | `CourseCategory` (entidad): el constructor recorta nombre y descripción, deja la descripción **nula** si queda vacía, **normaliza el color a mayúsculas** y valida la forma de color, icono y orden; `CategoryColor` (objeto de valor, reutilizable por lo que en el futuro lleve color) | `AC` |
| `domain/repository` | `CourseCategoryRepository` + `JpaCourseCategoryRepository`: `save` con **traducción** de `uq_course_categories_name` al `409`, `existsAliveName`, `findAliveByIdForUpdate`, `findByIdForUpdate` | `AC` |
| `domain/repository` | `CourseCategoryQueryRepository` + `Jpa…`: `findDetail(id)` — la categoría con su `courseCount` en **una sentencia**; **hasta `RF-AC-016` la cuenta es literal `0`** y la lista de cursos, vacía | `AC` |
| `domain/service` | `RegisterCourseCategoryService` | `AC` |
| `application` | `RegisterCourseCategoryRequest`, `CourseCategoryDetailResponse` (con `courseCount`, `courses` y `coverImageUrl`), `AcademyImageUrls` —la dirección pública de una imagen, `/api/v1/academy-images/{id}`, con el nulo cuando no hay— | `AC` |
| `interfaces` | `CourseCategoryController` — `POST /api/v1/course-categories` | `AC` |
| `shared/security` | La ruta en `EndpointPermissionsIT` con su permiso; `CourseCategoriesPermissionsSeedIT` nuevo; el recuento de las cuatro suites del catálogo | `shared` |

**`AcademyImageUrls` nace aquí sin nada que señalar**, igual que `ProductImageUrls` nació con la portada: es una función pura —identificador o nulo → dirección o nulo— y conviene que la respuesta del detalle la use desde el primer día para que `RF-AC-006` no tenga que abrir ningún DTO.

## 4. Contrato de API

`POST /api/v1/course-categories` — `course-categories:create`.

```json
{ "name": "Trading", "description": "…", "color": "1e88e5", "icon": "chart-line", "displayOrder": 0 }
```

`201` con `CourseCategoryDetailResponse` y cabecera `Location`:

```json
{
  "id": "…", "name": "Trading", "description": "…",
  "color": "1E88E5", "icon": "chart-line", "displayOrder": 0,
  "coverImageUrl": null,
  "courseCount": 0, "courses": [],
  "createdAt": "…", "updatedAt": "…"
}
```

- **`color` sale siempre en mayúsculas y sin `#`**, como el de la membresía: el `#` es notación de CSS y quien no sea una hoja de estilos tendría que quitárselo.
- **`coverImageUrl` presente y nulo**; **`courses` presente y vacío**; sin `deletedAt` ni `deletionReason`, que solo trae el detalle de una retirada (`RF-AC-003`).

| Código | Cuándo |
|---|---|
| `400` | `VAL-001` a `VAL-005` |
| `401` / `403` | Sin sesión / sin `course-categories:create` |
| `409` | `EX-001` |

## 5. Autorización

`@PreAuthorize("hasAuthority('course-categories:create')")`. Los `courses:` **no** habilitan: `CA-AC-008` lo prueba con un actor que porta los seis — sembrados en `RF-AC-008`; hasta entonces la prueba usa los cuatro `products:` como permiso ajeno, y `RF-AC-008` la enmienda.

## 6. Auditoría

`ChangeEvent` `CREATE` de `course_categories` con la instantánea de la categoría. Sin evento de seguridad.

## 7. Transaccionalidad

`@Transactional`. **Tres sentencias**: nombre, `INSERT`, auditoría; y **una más** para releer el detalle con su forma completa.

## 8. Impacto sobre otros módulos

**Ninguno en código.** `AC` no consume nada de `SP` en este requerimiento. **En pruebas**, las cuatro suites que cuentan el catálogo de permisos cambian de número — es la fricción deliberada de `security.md` §4.4.

## 9. Alternativas consideradas

| Alternativa | Por qué se descartó |
|---|---|
| **Añadir `cover_image_id` en `RF-AC-006`, cuando exista la tabla** | Un `ALTER` y un cambio en el modelo de lectura y en el DTO para algo que ya se sabe; la columna nulable sin clave foránea no permite nada que no deba permitirse, porque nadie la escribe hasta que exista la tabla |
| **Un estado en la categoría** | Decisión del responsable del proyecto (`spec.md` §14.1) |
| **Color único** | Decisión del responsable del proyecto (`spec.md` §14.2) |
| **Reutilizar `courses:create`** | Recurso propio: una categoría existe sin cursos (`requirements/ac.md` §7) |
| **`CHECK` del icono ausente, como en `products`** | Allí el icono es nulable y la forma vive en la validación; aquí es obligatorio y el esquema puede decirlo entero |
| **Una sola migración con tablas y permisos** | El reparto de `V39`/`V40`, `V91`/`V93`: los permisos son datos y las tablas son esquema, y una siembra que falla no debe deshacer una tabla |

## 10. Riesgos

| # | Riesgo | Mitigación |
|---|---|---|
| 1 | **Dos altas simultáneas** con el mismo nombre | Índice parcial; el repositorio traduce; `CA-AC-009` |
| 2 | **Se olvida asociar los permisos a `ADMIN`** | La guarda de `V19` y `CourseCategoriesPermissionsSeedIT` |
| 3 | **El módulo nuevo importa algo de `PM`** «porque la forma es la misma» | La regla de ArchUnit que aísla los módulos gana la fila de `academy`; `T-03` la verifica antes de escribir una clase de negocio |
| 4 | **El color se guarda como llegó** y `1e88e5` y `1E88E5` conviven | La normalización en el constructor **y** el `CHECK` en mayúsculas, que rechaza la minúscula por cualquier vía |

## 11. Estrategia de prueba

- **Unitarias**: `CourseCategory` (color en minúsculas sale en mayúsculas, descripción de espacios sale nula, nombre recortado, color e icono mal formados y orden negativo rechazados); `AcademyImageUrls` (identificador → dirección, nulo → nulo).
- **Integración de API** (`CourseCategoriesIT`): los nueve criterios de `spec.md` §12, la traducción de la unicidad, el `403` con permisos ajenos, y la carrera de dos altas en `CourseCategoryConcurrencyIT`.
- **Siembra** (`CourseCategoriesPermissionsSeedIT`): cuatro permisos, identificadores estables, dos asociaciones cada uno, ninguna a `CLIENTE`.
- **Arquitectura**: la prueba de ArchUnit incluye `academy` y sigue en verde.
- **Contrato**: el esquema declara `CourseCategoryDetailResponse` con `coverImageUrl`, `courseCount` y `courses`; la prosa dice que nace sin portada, sin cursos y sin estado, y que el color va sin `#`.
