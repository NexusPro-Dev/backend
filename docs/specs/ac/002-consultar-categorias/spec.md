# SPEC — `RF-AC-002` Consultar categorías

| Campo | Valor |
|---|---|
| Requerimiento | `RF-AC-002` |
| Módulo | `AC` — Academia |
| Estado | **En revisión** — **construida el 17-09-2026** |
| Autor | Responsable técnico |
| Aprobada por | — |
| Fecha de aprobación | — |

---

## 1. Objetivo

Ver y encontrar las categorías **en el orden en que se enseñan**, con cuántos cursos vivos tiene cada una, para administrarlas y reordenarlas.

## 2. Contexto

Es `RF-PM-002` para categorías: el catálogo de administración, paginado, con búsqueda por nombre y con los retirados fuera salvo que se pidan. Hereda de aquel el desempate por identificador —UUID v7, cronológico— y la envoltura de página del sistema. **Lo que cambia es el orden por omisión**: el producto se lista por fecha de alta porque no tiene orden propio; la categoría **sí lo tiene** (`RN-AC-002`), y la lista lo respeta, porque es la vista con la que administración decide qué va antes y comprueba que quedó como quería.

**Cada fila trae cuántos cursos vivos tiene**, calculado en la misma lectura, porque es lo primero que se pregunta al mirar un cajón —¿está vacío?— y porque sin él retirar una categoría (`RF-AC-005`) se haría a ciegas. Hasta que exista la clasificación (`RF-AC-016`) la cuenta es cero en todas.

## 3. Actores

| Actor | Papel |
|---|---|
| Administrador | Consulta el catálogo de categorías |

## 4. Alcance

### 4.1 Incluye

- Devolver las categorías **paginadas**, con búsqueda por **nombre** e `includeDeleted`.
- Cada fila con: identificador, nombre, color, icono, orden, `coverImageUrl`, **cuántos cursos vivos**, fecha de alta y `deletedAt` si aplica.
- Orden **por `displayOrder` ascendente** por omisión, con el identificador de desempate; o por nombre o por fecha de alta, de una lista cerrada.

### 4.2 No incluye

- **La descripción ni los cursos de cada categoría.** Es el detalle (`RF-AC-003`). La fila trae cuántos son, no cuáles.
- **El motivo del retiro** de las retiradas: vive en la auditoría y lo trae el detalle.
- **Filtrar por «vacía»**: se publica por fila y no se filtra, por lo mismo que `PM` no filtra por `offerable` (`RF-PM-018` §14.1).
- **La vista del alumno.** `RF-AC-033` trae las categorías vivas con la oferta; esta lista es de administración.

## 5. Reglas de negocio aplicables

| ID | Regla | Origen |
|---|---|---|
| `RN-AC-002` | El orden es la posición en que se enseñan; desempate por identificador | `requirements/ac.md` §5.1 |
| `RN-AC-004` | Toda lectura trae `coverImageUrl`, presente y nula | `requirements/ac.md` §5.1 |
| `RN-AC-010` | Los cursos que cuentan son los **vivos** clasificados en ella | `requirements/ac.md` §5.1 |
| `RN-AC-018` | Las retiradas existen y se listan si se piden | `requirements/ac.md` §5.1 |

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción | Restricción |
|---|---|---|---|
| `q` | No | Búsqueda por nombre | Sin distinguir mayúsculas ni acentos, por contenido |
| `includeDeleted` | No | Incluir retiradas | Por omisión `false` |
| `sort`, `page`, `size` | No | `displayOrder` (omisión), `name`, `createdAt`; paginación del sistema | Lista cerrada |

### 6.2 Salida

La envoltura de página del sistema, y en `content` cada categoría con: `id`, `name`, `color`, `icon`, `displayOrder`, `coverImageUrl`, **`courseCount`**, `createdAt`, `deletedAt` (`NON_NULL`).

**`coverImageUrl` es la dirección de la portada** (`RN-AC-004`): `/api/v1/academy-images/{imageId}`, construida sobre `cover_image_id` **sin ninguna consulta más** —los bytes no se seleccionan nunca en un listado— y **presente y nula** cuando no hay. **`courseCount` cuenta los cursos vivos** clasificados en la categoría, **en la misma sentencia** que la página: ni la cuenta ni la portada cuestan una consulta por fila.

## 7. Precondiciones y postcondiciones

**Precondiciones:** actor con `course-categories:read`. **Postcondiciones:** ninguna.

## 8. Flujo principal

1. Llega la petición con sus parámetros.
2. El sistema valida la paginación, la búsqueda y el orden, **juntos** (§11).
3. El sistema resuelve la página y el total, con la cuenta de cursos por fila.
4. Devuelve `200` con la página.

## 9. Flujos alternativos

### FA-001 — Sin categorías

**Comportamiento:** página vacía con total cero. No es un error.

### FA-002 — `includeDeleted=true`

**Comportamiento:** las retiradas entran en la página, con `deletedAt`, en el mismo orden que las demás. Su `courseCount` cuenta igual: los cursos que tenía siguen clasificados en ella aunque la clasificación ya no se enseñe (`RN-AC-018`).

## 10. Excepciones

### EX-001 — Parámetros inválidos

**Respuesta del sistema:** `400` con los errores **juntos**.

## 11. Validaciones

| ID | Validación | Mensaje esperado |
|---|---|---|
| `VAL-001` | Paginación dentro de rango | Los de `shared/pagination` |
| `VAL-002` | `sort` en la lista cerrada | El campo de ordenamiento no es admitido. |
| `VAL-003` | `includeDeleted` booleano | El parámetro includeDeleted debe ser true o false. |

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-AC-010` | El sistema devuelve la página con `coverImageUrl` y `courseCount` por fila, y `courseCount` **cuadra** con el detalle de cada categoría |
| `CA-AC-011` | El orden por omisión es por **`displayOrder` ascendente** con el identificador de desempate: dos con el mismo orden salen la más antigua primero; `name` y `createdAt` se admiten; otro campo es `400` |
| `CA-AC-012` | El sistema **excluye** las retiradas salvo `includeDeleted=true`, y entonces las trae con `deletedAt` |
| `CA-AC-013` | La búsqueda por `q` acota sin distinguir mayúsculas ni acentos y por contenido |
| `CA-AC-014` | El número de sentencias **no crece** con el tamaño de la página: página de uno y de veinte cuestan lo mismo, **dos** —página y total— |
| `CA-AC-015` | Los parámetros inválidos se devuelven **juntos** con `400`, y sin `course-categories:read` responde `403` aunque el actor porte `courses:read` |

## 13. Casos límite

| Caso | Decisión |
|---|---|
| Categoría sin cursos | `courseCount` cero; no hay marca aparte de «vacía» |
| Categoría con cursos **retirados** solamente | `courseCount` cero: cuentan los vivos |
| Categoría con cursos **inactivos** | Cuentan: un curso inactivo es un curso vivo. La cuenta dice cuántos hay, no cuántos se ofrecen |
| `q` con acentos | Se busca sin acentos, como el nombre del producto |
| Todas con orden `0` | Salen por antigüedad, y es exactamente el orden de alta |

## 14. Preguntas abiertas

| # | Pregunta | Resolución |
|---|---|---|
| 1 | ¿Se lista por fecha de alta como el producto? | **No.** La categoría tiene orden propio y esta es la vista para comprobarlo. `createdAt` queda como opción de la lista cerrada |
| 2 | ¿`courseCount` cuenta los que se ofrecen? | **No, los vivos.** «Cuántos se ofrecen» exige la ofrecibilidad de cada curso (`RN-AC-015`), que es una cuenta sobre cuatro tablas, y multiplicarla por página convertiría este listado en otra cosa. El detalle (`RF-AC-003`) sí trae `offerable` por curso |

## 15. Control de cambios

| Versión | Fecha | Cambio | Responsable |
|---|---|---|---|
| 0.1.0 | 17-09-2026 | Redacción inicial. Hereda `RF-PM-002` y **cambia el orden por omisión a `displayOrder`**, porque la categoría tiene orden propio y esta es la vista con la que se comprueba. `courseCount` por fila, de los **vivos** y en la misma sentencia; `coverImageUrl` sin consulta más. Dos sentencias fijas. | Responsable técnico |
| 0.2.0 | 17-09-2026 | **Construida** (`CourseCategoryListIT`). Sin enmiendas de comportamiento. `CA-AC-014` cuenta **dos** sentencias en la página de uno y en la de veinte; `CA-AC-010` es trivial como la spec declaraba y queda para `RF-AC-016`. | Responsable técnico |
| 0.3.0 | 25-09-2026 | **Enmienda de `RF-AC-016`, construida** (Art. I.7): `courseCount` es la subconsulta real sobre `course_category_items` —vivos, no ofrecidos—; `CA-AC-010` con cursos se prueba en `CourseClassificationIT` (`CA-AC-127`). La cuenta de sentencias no cambia: la subconsulta viaja en la fila. | Responsable técnico |
