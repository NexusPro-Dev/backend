# SPEC — `RF-AC-009` Consultar cursos

| Campo | Valor |
|---|---|
| Requerimiento | `RF-AC-009` |
| Módulo | `AC` — Academia |
| Estado | **En revisión** |
| Autor | Responsable técnico |
| Aprobada por | — |
| Fecha de aprobación | — |

---

## 1. Objetivo

Ver y encontrar los cursos, **incluidos los que no se ofrecen**, en el orden en que se enseñan y con la señal de si hoy se pueden ofrecer.

## 2. Contexto

Es `RF-AC-002` para cursos, y hereda del listado de categorías el orden por omisión —el declarado, con la más antigua primero— y del listado de paquetes (`RF-PM-018`) la forma: filtros de lista cerrada, retirados fuera salvo que se pidan, y `offerable` **como columna y no como filtro**, porque quien administra quiere ver precisamente los que no se ofrecen.

**Cada fila trae el instructor resuelto y sus categorías**, porque son las dos cosas por las que se busca un curso en una lista; y **cuántos módulos y lecciones vivos tiene**, porque es lo que dice si el curso está armado. Las categorías y las cuentas llegan **vacías y en cero** hasta que existan sus tablas (`RF-AC-016`, `RF-AC-022`, `RF-AC-028`), y los tres requerimientos enmiendan esta lectura (Art. I.7).

## 3. Actores

| Actor | Papel |
|---|---|
| Administrador | Consulta el catálogo de cursos |

## 4. Alcance

### 4.1 Incluye

- Devolver los cursos **paginados**, con filtros por **título** (`q`), **categoría**, **instructor**, **dificultad** y **estado**, e `includeDeleted`.
- Cada fila con: identificador, título, instructor resuelto, dificultad, descripción corta, orden, estado, `coverImageUrl`, categorías vivas, `offerable`, `moduleCount`, `lessonCount`, fecha de alta y `deletedAt` si aplica.
- Orden **por `displayOrder` ascendente** por omisión, con el identificador de desempate; o por título o fecha de alta, de una lista cerrada.

### 4.2 No incluye

- **Los módulos, las membresías ni las recomendaciones** de cada curso. Es el detalle (`RF-AC-010`).
- **`offerableReason`.** Uno a uno es una consulta; el detalle lo dice.
- **Filtrar por `offerable`** ni por membresía: se publica por fila y no se filtra (`RF-PM-018` §14.1).
- **La descripción larga y el video.** Son de la página del curso, no de la lista.
- **La vista del alumno.** `RF-AC-033`.

## 5. Reglas de negocio aplicables

| ID | Regla | Origen |
|---|---|---|
| `RN-AC-002` | El orden global decide en qué lugar se enseña; desempate por identificador | `requirements/ac.md` §5.1 |
| `RN-AC-004` | `coverImageUrl` presente y nula | `requirements/ac.md` §5.1 |
| `RN-AC-010` | Las categorías de la fila son las **vivas** en que está clasificado | `requirements/ac.md` §5.1 |
| `RN-AC-015` | `offerable` por fila se calcula | `requirements/ac.md` §5.1 |
| `RN-AC-018` | Los retirados existen y se listan si se piden | `requirements/ac.md` §5.1 |

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción | Restricción |
|---|---|---|---|
| `q` | No | Búsqueda por título | Sin distinguir mayúsculas ni acentos, por contenido |
| `categoryId` | No | Solo los clasificados en esa categoría | UUID. **Hasta `RF-AC-016` ningún curso está en ninguna**, y el filtro devuelve vacío |
| `instructorId` | No | Solo los de ese instructor | UUID |
| `difficulty` | No | `PRINCIPIANTE`, `INTERMEDIO` o `AVANZADO` | Fuera del dominio, `400` |
| `status` | No | `ACTIVO` o `INACTIVO` | Fuera del dominio, `400` |
| `includeDeleted` | No | Incluir retirados | Por omisión `false` |
| `sort`, `page`, `size` | No | `displayOrder` (omisión), `title`, `createdAt`; paginación del sistema | Lista cerrada |

### 6.2 Salida

La envoltura de página del sistema, y en `content` cada curso con: `id`, `title`, `instructor { id, username, fullName }`, `difficulty`, `shortDescription`, `displayOrder`, `status`, `coverImageUrl`, `categories [{ id, name, color, icon }]`, `offerable`, `moduleCount`, `lessonCount`, `createdAt`, `deletedAt` (`NON_NULL`).

**El instructor se resuelve en la misma sentencia** que la página, por lectura, y su nombre es el actual. **Las categorías de toda la página se resuelven en una segunda sentencia fija** desde `RF-AC-016`, y se agrupan por curso: ni ellas ni las cuentas cuestan una consulta por fila.

## 7. Precondiciones y postcondiciones

**Precondiciones:** actor con `courses:read`. **Postcondiciones:** ninguna.

## 8. Flujo principal

1. Llega la petición con sus parámetros.
2. El sistema valida paginación, orden y filtros de dominio, **juntos** (§11).
3. El sistema resuelve la página con el instructor y las cuentas, el total, y las categorías de la página.
4. Devuelve `200`.

## 9. Flujos alternativos

### FA-001 — `includeDeleted=true`

**Comportamiento:** los retirados entran en la página con `deletedAt`, en el mismo orden. Su `offerable` es falso.

### FA-002 — El instructor fue retirado de `SP` después

**Comportamiento:** la fila lo sigue nombrando, con el nombre que tenga hoy (`RN-AC-006`). No es un filtro ni una marca.

## 10. Excepciones

### EX-001 — Parámetros inválidos

**Respuesta del sistema:** `400` con los errores **juntos**.

## 11. Validaciones

| ID | Validación | Mensaje esperado |
|---|---|---|
| `VAL-001` | Paginación dentro de rango | Los de `shared/pagination` |
| `VAL-002` | `sort` en la lista cerrada | El campo de ordenamiento no es admitido. |
| `VAL-003` | `difficulty` en el dominio | La dificultad debe ser PRINCIPIANTE, INTERMEDIO o AVANZADO. |
| `VAL-004` | `status` en el dominio | El estado debe ser ACTIVO o INACTIVO. |
| `VAL-005` | `categoryId` e `instructorId` con formato válido | El identificador indicado no tiene un formato válido. |

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-AC-044` | Cada fila trae el instructor resuelto, `coverImageUrl`, `categories`, `offerable`, `moduleCount` y `lessonCount`, y **cuadran** con el detalle de cada curso |
| `CA-AC-045` | El orden por omisión es **`displayOrder` ascendente** con el identificador de desempate; `title` y `createdAt` se admiten; otro campo es `400` |
| `CA-AC-046` | El sistema **excluye** los retirados salvo `includeDeleted=true`, y entonces los trae con `deletedAt` y `offerable: false` |
| `CA-AC-047` | Los filtros por título, instructor, dificultad y estado acotan y se combinan; `categoryId` acota — **vacío hasta `RF-AC-016`** |
| `CA-AC-048` | El número de sentencias **no crece** con el tamaño de la página: dos —página y total— hoy, tres desde `RF-AC-016` con las categorías |
| `CA-AC-049` | Los parámetros inválidos se devuelven **juntos** con `400`, y sin `courses:read` responde `403` aunque el actor porte `course-categories:read` |
| `CA-AC-050` | Un curso cuyo instructor fue **retirado** después sigue listado y nombrándolo |

## 13. Casos límite

| Caso | Decisión |
|---|---|
| Curso sin categoría | `categories` vacío; se lista igual |
| Dos cursos con el mismo orden | Desempata el identificador: el más antiguo primero |
| `q` con acentos | Se busca sin acentos |
| Curso `ACTIVO` sin módulos | Se lista `ACTIVO` con `offerable: false`: el estado es lo que alguien decidió, y lo que lo detiene se enseña (`ac.md` §5.1, `RN-AC-009`) |

## 14. Preguntas abiertas

| # | Pregunta | Resolución |
|---|---|---|
| 1 | ¿Se filtra por membresía? | **No hoy.** «Qué cursos abre `ORO`» es una pregunta del aula al revés, y nadie la ha pedido desde administración; el detalle dice qué membresías abren cada curso |
| 2 | ¿`moduleCount` cuenta los ofrecibles? | **No, los vivos**, por lo mismo que `courseCount` en la categoría (`RF-AC-002` §14.2): `offerable` ya resume si el curso se puede ofrecer |

## 15. Control de cambios

| Versión | Fecha | Cambio | Responsable |
|---|---|---|---|
| 0.1.0 | 18-09-2026 | Redacción inicial. Hereda `RF-AC-002` —orden por `displayOrder`— y `RF-PM-018` —`offerable` como columna, retirados si se piden—. El instructor se resuelve en la misma sentencia; las categorías, en una segunda fija por página desde `RF-AC-016`; las cuentas de módulos y lecciones, de los vivos. **Declara tres enmiendas futuras** (Art. I.7) para categorías, módulos y lecciones. | Responsable técnico |
| 0.2.0 | 18-09-2026 | **Construida** (`CourseListIT` (7)). Dos sentencias fijas hoy; `categoryId` deja la lista vacía por un `1 = 0` con la nota de `RF-AC-016`; dificultad y estado fuera del dominio son `VAL-003` **junto** con paginación y orden. | Responsable técnico |
