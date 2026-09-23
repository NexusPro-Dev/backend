# SPEC — `RF-SP-064` Consultar equipos

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-064` |
| Módulo | `SP` — Sistema Principal |
| Estado | **Aprobada** |
| Autor | Responsable técnico |
| Aprobada por | Responsable del proyecto |
| Fecha de aprobación | 22-09-2026 |

---

## 1. Objetivo

Ver qué equipos existen, en qué estado están y **cuántos managers tiene cada uno hoy**, para administrarlos y para saber de un vistazo cuáles están en uso y cuáles quedaron vacíos.

## 2. Contexto

Es el catálogo de administración del submódulo, con la forma que `RF-SP-002` fijó para los roles y `RF-AC-002` repitió para las categorías: paginado, con búsqueda por nombre insensible a mayúsculas y acentos, con los eliminados fuera salvo que se pidan, y con el desempate por identificador —UUID v7, cronológico— que hace determinista cualquier orden.

**Cada fila dice cuántos miembros vigentes tiene**, y es lo que convierte esta lista en algo más que un índice de nombres. Un equipo vacío y uno con cinco managers se ven igual sin ese número, y tres operaciones del bloque dependen de él: eliminar exige que no haya vigentes (`RN-SP-054`), desactivar conserva los que hay (`RN-SP-053`), y reubicar a un manager (`RF-SP-069`) empieza por mirar dónde está cada cual. Se cuenta **en la misma sentencia que la página**, por `ix_team_members_team_vigente`, de modo que el número no cuesta una consulta por fila.

**Hasta que `RF-SP-069` exista, todas las filas traen cero**, y no es un defecto de esta consulta: es el mismo estado en que nació `courseCount` en `RF-AC-002` el día que no había clasificación. El contrato ya es el definitivo.

**No devuelve quiénes son los miembros.** La fila dice cuántos; el detalle (`RF-SP-065`) dice cuáles. Es la misma separación que `RF-AC-002` hace con los cursos de una categoría, y existe para que el listado tenga un coste fijo: publicar los miembros por fila obligaría a una consulta por equipo o a una respuesta que crece con el producto de dos cardinalidades.

## 3. Actores

| Actor | Papel |
|---|---|
| Administrador con `teams:list` | Consulta el catálogo de equipos |

## 4. Alcance

### 4.1 Incluye

- Devolver los equipos **paginados**, con búsqueda por **nombre**, filtro por **estado** e `includeDeleted`.
- Cada fila con identificador, nombre, estado, **cuántos miembros vigentes**, fecha de alta y `deletedAt` si aplica.
- Orden por **nombre ascendente** por omisión, con el identificador de desempate; o por fecha de alta, de una lista cerrada.

### 4.2 No incluye

- **La descripción y los miembros de cada equipo.** Es el detalle (`RF-SP-065`).
- **Filtrar por «vacío»** ni por número de miembros. Se publica el recuento por fila y el frontend separa en pantalla; un filtro es una enmienda cuando alguien lo pida, con el criterio de `RF-PM-018` §14.1.
- **Buscar por el nombre de un manager.** «¿En qué equipo está esta persona?» es la lectura inversa y no está en este bloque (§14.4).
- **Los equipos de un actor concreto.** No hay variante `/me/teams`: un administrador no pertenece a ningún equipo, y la vista del manager sobre el suyo, si llega a pedirse, será un requerimiento propio con su permiso de alcance propio (`RN-SEG-015`).

## 5. Reglas de negocio aplicables

| ID | Regla | Origen |
|---|---|---|
| `RN-SP-052` | Un miembro vigente por manager; el historial se conserva — aquí fija **qué se cuenta**: las filas sin fecha de fin | `requirements/sp.md` §5.1 |
| `RN-SP-053` | Un equipo `INACTIVO` conserva sus miembros — por eso el recuento **no** depende del estado | `requirements/sp.md` §5.1 |
| `RN-SP-054` | No se elimina un equipo con miembros vigentes — el recuento es lo que permite verlo antes de intentarlo | `requirements/sp.md` §5.1 |
| `RN-SEG-014` | Un permiso gobierna una operación o ninguna: listar y ver el detalle son dos | `security.md` §4.3 |

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción | Restricción |
|---|---|---|---|
| `q` | No | Búsqueda por nombre | Sin distinguir mayúsculas ni acentos, **por contenido** |
| `status` | No | Acota a `ACTIVO` o a `INACTIVO` | Uno de los dos; otro valor es `400` (`VAL-002`) |
| `includeDeleted` | No | Incluir los eliminados | Booleano; por omisión `false` |
| `sort`, `page`, `size` | No | `name` (omisión) o `createdAt`; la paginación del sistema | Lista cerrada; los límites de todo listado |

### 6.2 Salida

La envoltura de página del sistema —`content`, `totalElements`, `totalPages`, `page`, `size`, `totalIsExact`—, y en cada fila:

| Dato | Descripción |
|---|---|
| `id` | El identificador del equipo, con el que se abre su detalle |
| `name` | Cómo se llama |
| `status` | `ACTIVO` o `INACTIVO` |
| `memberCount` | **Cuántos miembros vigentes** tiene hoy: filas de pertenencia sin fecha de fin (`RN-SP-052`) |
| `createdAt` | Cuándo se creó |
| `deletedAt` | Presente **solo si está eliminado** (`NON_NULL`) |

**El orden por omisión es alfabético por nombre**, y no por fecha de alta como en el listado de productos: un equipo se busca por su nombre porque es lo único que lo identifica (`RN-SP-050`), y una lista de equipos es corta y se recorre con la vista. `createdAt` queda en la lista cerrada para quien quiera ver los últimos creados.

**`memberCount` cuenta vigentes y no históricos**, y no depende del estado del equipo: un equipo `INACTIVO` con tres managers dice tres, porque desactivarlo no los saca (`RN-SP-053`). Un equipo **eliminado** —que solo sale con `includeDeleted=true`— dice **cero**, y no por una regla aparte: `RN-SP-054` impide eliminarlo con vigentes, de modo que cualquier equipo eliminado los tiene todos cerrados.

## 7. Precondiciones y postcondiciones

**Precondiciones:** actor autenticado con `teams:list`.

**Postcondiciones:** ninguna. Es una lectura y **no audita**.

## 8. Flujo principal

1. Llega la petición con sus parámetros.
2. El sistema valida la paginación, el orden, el estado y `includeDeleted`, **juntos** (§11).
3. El sistema resuelve el total y la página, con el recuento de miembros por fila en la misma sentencia.
4. Devuelve `200` con la página.

## 9. Flujos alternativos

| ID | Caso | Comportamiento |
|---|---|---|
| `FA-001` | **No hay equipos** | `200` con la página vacía y `totalElements` en cero. No es un error: el sistema funciona sin un solo equipo |
| `FA-002` | `includeDeleted=true` | Los eliminados entran, con su `deletedAt`, en el mismo orden que los demás y con `memberCount` en cero |
| `FA-003` | `status=INACTIVO` | Solo los inactivos **no eliminados**; los dos filtros se combinan, y `includeDeleted` decide aparte |
| `FA-004` | `q` no coincide con nada | Página vacía, no `404` |
| `FA-005` | Un equipo tiene miembros **cerrados** solamente | `memberCount` en cero: lo pasado es historial y no es el equipo de hoy |

## 10. Excepciones

### EX-001 — Parámetros inválidos

**Condición:** paginación fuera de rango, `sort` fuera de la lista cerrada, `status` con un valor que no existe o `includeDeleted` que no es booleano.
**Respuesta del sistema:** `400` con los errores **juntos**, en el formato de `architecture.md` §7.3.

## 11. Validaciones

| ID | Validación | Mensaje esperado |
|---|---|---|
| `VAL-001` | Paginación dentro de los límites del sistema | Los de `shared/pagination` |
| `VAL-002` | `status` es `ACTIVO` o `INACTIVO`, sin distinguir mayúsculas | El estado indicado no es válido. |
| `VAL-003` | `sort` en la lista cerrada (`name`, `createdAt`) | El campo de ordenamiento no es admitido. |
| `VAL-004` | `includeDeleted` booleano | El parámetro includeDeleted debe ser true o false. |

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-SP-740` | El sistema devuelve la página con `id`, `name`, `status`, `memberCount`, `createdAt` y sin `deletedAt` en los vivos, y `memberCount` **cuadra** con los miembros que devuelve el detalle de cada equipo |
| `CA-SP-741` | El orden por omisión es por **nombre ascendente**, sin distinguir acentos, con el identificador de desempate; `createdAt` se admite y cualquier otro campo es `400` (`VAL-003`) |
| `CA-SP-742` | El sistema **excluye** los eliminados salvo `includeDeleted=true`, y entonces los trae con `deletedAt` y `memberCount` en cero |
| `CA-SP-743` | `status=ACTIVO` y `status=INACTIVO` acotan la página y `totalElements`; un valor que no existe es `400` (`VAL-002`) |
| `CA-SP-744` | La búsqueda por `q` acota **por contenido** y sin distinguir mayúsculas ni acentos: «norte» encuentra «Equipo Norte» y «Región NORTE» |
| `CA-SP-745` | `memberCount` cuenta **solo los vigentes**: un equipo con dos pertenencias cerradas y una abierta dice uno, y un equipo `INACTIVO` con miembros los sigue contando |
| `CA-SP-746` | El número de sentencias **no crece** con el tamaño de la página: una página de uno y una de veinte cuestan lo mismo, **dos** —página y total— |
| `CA-SP-747` | Sin `teams:list` la consulta responde `403` **aunque el actor porte `teams:read`**, y `EndpointPermissionsIT` recibe `GET /teams` con su código |

## 13. Casos límite

| Caso | Decisión |
|---|---|
| Dos equipos cuyos nombres solo difieren en acentos | No pueden coexistir vivos (`RN-SP-050`); sí un vivo y uno eliminado, y entonces salen juntos con `includeDeleted=true` y se distinguen por `deletedAt` |
| `q` con acentos | Se busca sin ellos, como en el nombre de un usuario o de un país |
| `q` de un solo carácter | Se admite: es una búsqueda por contenido y el índice de trigramas la sostiene; devolver media lista no es un error |
| `page` más allá de la última | `200` con `content` vacío y los totales de siempre |
| `totalIsExact` | Siempre `true`: los equipos de una empresa se cuentan en decenas, y el techo de `RF-MV-006` existe para tablas que crecen sin cota |
| Un equipo eliminado **con** miembros vigentes | No puede existir (`RN-SP-054`). Si apareciera, sería un defecto de la eliminación y no de esta lectura |

## 14. Preguntas abiertas resueltas

| # | Pregunta | Resolución |
|---|---|---|
| 1 | ¿El orden por omisión es alfabético o por fecha? | **Alfabético por nombre.** El equipo se identifica por su nombre y la lista es corta; `RF-AC-002` ordenó por `displayOrder` porque la categoría tiene orden propio, y el equipo no lo tiene |
| 2 | ¿La fila trae la descripción? | **No.** Es texto de hasta 500 caracteres multiplicado por el tamaño de la página, y la lista se recorre por nombre. El detalle la trae |
| 3 | ¿Se filtra por «equipos vacíos»? | **No.** Se publica `memberCount` y el frontend separa; un filtro nuevo es una enmienda cuando alguien lo necesite, con el criterio que `PM` aplicó a `offerable` |
| 4 | ¿Se puede buscar el equipo de una persona? | **No aquí.** «¿En qué equipo está este manager?» es la lectura inversa, y su sitio natural es la ficha de la persona (`RF-SP-026`) o `GET /users/{id}/team`; queda **declarada como pendiente** en `requirements/sp.md` §6.2 y se decidirá cuando el frontend diga dónde la necesita — no se cuela como filtro de este listado, porque un filtro que devuelve un solo elemento es una lectura distinta disfrazada |
| 5 | ¿`memberCount` cuenta el historial? | **No, los vigentes.** «Cuántos hay» es lo que decide si se puede eliminar (`RN-SP-054`) y si el equipo está en uso; cuántos pasaron por él es una pregunta de las comisiones, y se responde sobre el historial completo, no en un listado |

## 15. Control de cambios

| Versión | Fecha | Cambio | Responsable |
|---|---|---|---|
| 0.1.0 | 22-09-2026 | Redacción inicial. Hereda la forma de `RF-SP-002` y `RF-AC-002` —paginado, búsqueda sin acentos, eliminados fuera salvo que se pidan, desempate por identificador— y **cambia el orden por omisión a alfabético**, porque el equipo no tiene orden propio y el nombre es lo único que lo identifica. `memberCount` de los **vigentes**, en la misma sentencia; dos sentencias fijas. La lectura inversa —el equipo de una persona— queda fuera y declarada pendiente. Ocho criterios, `CA-SP-740` a `CA-SP-747`. | Responsable del proyecto |
