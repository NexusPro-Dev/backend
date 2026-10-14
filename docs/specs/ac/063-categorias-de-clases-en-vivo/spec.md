# SPEC — `RF-AC-063` a `RF-AC-066` Las categorías de las clases en vivo

| Campo | Valor |
|---|---|
| Requerimientos | `RF-AC-063`, `RF-AC-064`, `RF-AC-065`, `RF-AC-066`; enmienda `RF-AC-042` a `RF-AC-045`, `RF-AC-048` a `RF-AC-050` y `RF-AC-053` |
| Módulo | `AC` — Academia |
| Versión | 1.0.0 |
| Estado | **Aprobada** |
| Autor | Responsable técnico |
| Aprobada por | Responsable del proyecto |
| Fecha de aprobación | 14-10-2026 |

---

## 1. Objetivo

Que cada clase en vivo caiga en **una categoría** —«Trading», «Clases», «Señales» u otra que administración cree—, y que esas categorías se consulten, registren, corrijan y retiren.

## 2. Contexto

El responsable del proyecto lo pidió el 14-10-2026 ([`requirements/ac.md`](../../../requirements/ac.md) §5.2.20): «a sesiones en vivo quiero poder crearle categorías donde las principales son "Trading", "Clases" y "Señales", con la opción de poder crear, eliminar y actualizar».

Nace **`RN-AC-034`**: un catálogo propio de las clases —no el de cursos—, con nombre y orden, sembrado con las tres; toda clase declara **exactamente una**. Son cuatro rutas nuevas y un campo más en las que ya programan, corrigen y leen clases.

## 3. Actores

| Actor | Permisos |
|---|---|
| Administrador | `live-session-categories:list`, `:create`, `:update`, `:delete`; `live-sessions:create`, `:update`, `:list`, `:read` |
| Instructor | `live-session-categories:list`; los `live-sessions:*-own` |
| Alumno | `live-session-categories:list`; `live-sessions:learn` |

## 4. Alcance

### 4.1 Incluye

- **Consultar** (`RF-AC-063`): `GET /live-session-categories`, las vivas, sin paginar.
- **Registrar** (`RF-AC-064`): `POST /live-session-categories`.
- **Corregir** (`RF-AC-065`): `PATCH /live-session-categories/{id}`.
- **Retirar** (`RF-AC-066`): `POST /live-session-categories/{id}/deletion`, con motivo.
- **Las clases**: `categoryId` obligatorio al programar y cambiable al corregir; `category` en el detalle, los listados y la vitrina; el filtro `categoryId` en los dos listados y en la vitrina.
- `V107`: la tabla, las tres sembradas, `live_sessions.category_id` rellenada con «Clases», y los cuatro permisos.

### 4.2 No incluye

- Color, icono o portada de la categoría.
- Más de una categoría por clase.
- Reactivar una retirada, o ver las retiradas en el listado.

## 5. Reglas de negocio aplicables

| Regla | Cómo aplica |
|---|---|
| `RN-AC-002` | El orden es una posición, no única; desempate por nombre e identificador |
| `RN-AC-018` | Retirar es baja lógica con motivo y no arrastra: las clases conservan su categoría |
| `RN-AC-034` | Catálogo propio; nombre único entre las vivas; una por clase, obligatoria, viva al asignarse |

## 6. Datos

### 6.1 Entrada

| Campo | Dónde | Regla |
|---|---|---|
| `name` | Registrar | Obligatorio, de 1 a 100 caracteres tras recortar |
| `displayOrder` | Registrar | Obligatorio, entero mayor o igual que cero |
| `name`, `displayOrder` | Corregir | Opcionales, al menos uno; **ninguno se vacía** |
| `reason` | Retirar | Obligatorio, como todo retiro (`DeletionReason`) |
| `categoryId` | Programar una clase | **Obligatorio**; una categoría viva |
| `categoryId` | Corregir una clase | Opcional; si viene, una categoría viva |
| `categoryId` | Filtro de los listados y la vitrina | Opcional |

### 6.2 Salida

- **La categoría**: `{ id, name, displayOrder, createdAt, updatedAt }`; el listado, una lista de ellas.
- **En las clases**: `category: { id, name }` en `LiveSessionDetailResponse`, `LiveSessionItem` y `AvailableLiveSessionItem`, siempre presente.

## 7. Precondiciones y postcondiciones

| | |
|---|---|
| Precondición | El permiso de cada ruta |
| Postcondición | Registrar audita `CREATE` con la instantánea; corregir audita `UPDATE` con lo que cambió; retirar audita la eliminación con el motivo; programar y corregir una clase llevan `categoryId` en su auditoría |

## 8. Flujo principal (retirar una categoría)

1. Se valida el motivo.
2. Se busca la categoría, bloqueada; si no existe, `404`; si ya está retirada, `409`.
3. Se marca `deleted_at` y se audita la eliminación con la instantánea y el motivo.
4. Las clases que la tienen **no cambian**.

## 9. Flujos alternativos

### FA-001 — Corregir con los mismos valores

`200` con la categoría, sin auditoría.

### FA-002 — Registrar con el nombre de una retirada

Se admite: la retirada libera el nombre.

### FA-003 — Corregir una clase que tiene una categoría ya retirada, sin `categoryId`

Se admite y la conserva: la categoría solo se comprueba **al asignarse**.

## 10. Excepciones

| Código | Dónde | Cuándo | Respuesta |
|---|---|---|---|
| `EX-001` | Registrar | Nombre de otra viva | `409` |
| `EX-001` | Corregir, retirar | No existe —o, al corregir, está retirada— | `404` |
| `EX-002` | Corregir | Nombre de otra viva | `409` |
| `EX-002` | Retirar | Ya retirada | `409` |
| `EX-002` | Programar o corregir una clase | `categoryId` no existe o está retirada | `422`, campo `categoryId` |

## 11. Validaciones

| Código | Campo | Regla |
|---|---|---|
| `VAL-001` | `name` | Vacío, nulo o de más de 100 caracteres |
| `VAL-002` | `displayOrder` | Nulo o negativo |
| `VAL-003` | — | Corregir sin ningún campo |
| `VAL-002`, `VAL-003` | `reason` | Las del motivo de todo retiro |
| `VAL-005` | `categoryId` | Programar sin categoría |

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-AC-330` | **`V107`** siembra «Trading», «Clases» y «Señales» con orden 0, 1 y 2; `live_sessions.category_id` no admite nulo y apunta a `live_session_categories`; el nombre es único entre las vivas |
| `CA-AC-331` | **Consultar** devuelve solo las vivas, por orden, nombre e identificador; sin `live-session-categories:list`, `403` |
| `CA-AC-332` | **Registrar** responde `201` con la categoría y audita `CREATE`; nombre vacío o largo, `400` `VAL-001`; orden nulo o negativo, `400` `VAL-002`; nombre de otra viva —sin distinguir acentos ni mayúsculas—, `409` `EX-001`; el de una retirada se admite |
| `CA-AC-333` | **Corregir** cambia nombre y orden y audita solo lo que cambió; sin campos, `400` `VAL-003`; nombre nulo, `400` `VAL-001`; inexistente o retirada, `404` `EX-001`; nombre de otra viva, `409` `EX-002`; las clases que la tienen muestran el nombre nuevo |
| `CA-AC-334` | **Retirar** con motivo responde `204` y audita la eliminación; sin motivo, `400`; inexistente, `404`; ya retirada, `409` `EX-002`; la clase que la tenía la conserva y la muestra; deja de salir en el listado y de poder asignarse |
| `CA-AC-335` | **Programar** —administración y `/mine`— sin `categoryId`, `400` `VAL-005`; con una inexistente o retirada, `422` `EX-002` y **sin llamar a Zoom**; con una viva, la clase responde `category` con identificador y nombre |
| `CA-AC-336` | **Corregir una clase** con `categoryId` la cambia y audita `categoryId`; con una retirada, `422`; sin él, la conserva |
| `CA-AC-337` | **Las lecturas** —detalle, listado, listado propio y vitrina— traen `category`, y el filtro `categoryId` deja solo las de esa categoría en los dos listados y en la vitrina |
| `CA-AC-338` | **Los permisos**: los cuatro existen; las escrituras, solo `SUPERADMIN` y `ADMIN`; el listado también donde está `courses:teach` y `live-sessions:learn`; catálogo de **244** |

## 13. Casos límite

| Caso | Decisión |
|---|---|
| Retirar las tres sembradas | Se admite; programar exigirá crear otra antes |
| Dos categorías con el mismo orden | Se ordenan por nombre e identificador |
| `categoryId` mal formado en el filtro | `400`, como cualquier parámetro ilegible |

## 14. Preguntas abiertas

Ninguna. Las decisiones tomadas sin preguntar están en `ac.md` §5.2.20, abiertas a revisión.

## 15. Control de cambios

| Versión | Fecha | Cambio | Responsable |
|---|---|---|---|
| 1.0.0 | 14-10-2026 | Nace con las categorías de las clases en vivo (`ac.md` v0.29.0 §5.2.20, `RN-AC-034`). | Responsable técnico |
