# PLAN — `RF-SP-066` Editar equipo

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-066` |
| Especificación | [`spec.md`](spec.md), aprobada el 22-09-2026 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 22-09-2026 |

---

## 1. Enfoque

**La edición parcial que el sistema ya sabe hacer, con una vuelta de tuerca conocida: distinguir «no vino» de «vino nulo».** `UpdateTeamRequest` usa el mismo recurso que `UpdateUserRequest` y `UpdateCourseCategoryRequest` —`JsonNullable` o el envoltorio equivalente que el proyecto ya emplea— para que `description: null` borre y la ausencia conserve. Es la única sutileza del requerimiento; el resto es cargar el agregado, aplicar, auditar el diff y releer el detalle con `TeamDetailReader`, que ya existe desde `RF-SP-063` y se completó en `RF-SP-065`.

**El agregado decide, el servicio orquesta.** `Team.rename(nombre, ahora)` y `Team.describe(descripcion, ahora)` normalizan y avanzan `updated_at`; la unicidad no la puede decidir el agregado porque depende de las demás filas, y por eso vive en el servicio con su red en el índice.

**Sin migración y sin componentes nuevos de lectura**: la ruta se cuelga del `TeamController` que ya existe.

## 2. Cambios de esquema

**Ninguno.** `teams` y `uq_teams_name` existen desde `V33`; `teams:update`, desde `V34`.

## 3. Componentes afectados

| Capa | Componente | Nuevo / Modificado | Responsabilidad |
|---|---|---|---|
| `domain/models` | `Team` | Modificado | `rename` y `describe`: recortan, rechazan el nombre vacío y avanzan `updatedAt`. Probables sin Spring |
| `domain/repository` | `TeamRepository` | Modificado | `existsAliveNameExcluding(name, id)` — la unicidad **excluyendo al propio equipo**, que es lo que hace posible `FA-001` |
| `domain/service` | `UpdateTeamService` | Nuevo | Resuelve el equipo no eliminado (`404`), comprueba el nombre (`409`), aplica, audita el **diff** y relee el detalle. `@Transactional` |
| `application` | `UpdateTeamRequest` | Nuevo | `name` y `description` opcionales, con la distinción ausente / nulo; `VAL-003` si no viene ninguno; rechaza campos desconocidos |
| `interfaces` | `TeamController` | Modificado | `PATCH /api/v1/teams/{id}` con `teams:update` |
| Pruebas | `TeamUpdateIT` | Nuevo | §11 |
| Pruebas | `TeamConcurrencyIT` | Modificado | Dos renombrados simultáneos al mismo nombre |
| Pruebas | `EndpointPermissionsIT`, `OpenApiContractIT` | Modificado | La ruta y su extensión |

## 4. Contrato de API

| Método | Ruta | Descripción |
|---|---|---|
| `PATCH` | `/api/v1/teams/{id}` | Corrige el nombre o la descripción |

**Petición** — los dos campos son opcionales y al menos uno debe venir:

```json
{
  "name": "Equipo Norte y Centro",
  "description": null
}
```

**Respuesta `200`**: la forma del detalle de `RF-SP-065`, con los valores nuevos y `updatedAt` avanzado.

**Errores**

| Código | Cuándo | `error_code` |
|---|---|---|
| `400` | Nombre vacío o largo; descripción larga; cuerpo sin campos; campo desconocido | `VAL-001` a `VAL-004` |
| `403` | Sin `teams:update` | — |
| `404` | El equipo no existe **o está eliminado** | `EX-001` |
| `409` | El nombre ya lo usa otro equipo no eliminado | `EX-002` |

## 5. Autorización

| Endpoint | Permiso |
|---|---|
| `PATCH /api/v1/teams/{id}` | `teams:update` |

Uno y solo uno (`RN-SEG-014`). `CA-SP-762` prueba el `403` con `teams:read` y `teams:create` puestos: quien puede crear no puede corregir, y esa asimetría es deliberada — son dos decisiones de negocio distintas y el frontend enseña dos botones distintos.

**`teams:update` no cambia el estado**, y eso importa más de lo que parece: `RF-SP-067` tiene permiso propio precisamente para que un rol pueda corregir nombres sin poder suspender equipos.

## 6. Auditoría

| Operación | Registro | Contenido |
|---|---|---|
| Edición | `audit_change_log` | Acción `UPDATE`, entidad `teams`, **diff de los campos modificados** — solo los que cambian de valor |
| `409` por nombre repetido | — | Ninguno: es un `409` de negocio |

**El diff se calcula sobre valores, no sobre presencia**: enviar el mismo nombre que ya tenía no produce una entrada de diff para `name`. Es lo que hace que la auditoría se lea como una historia de cambios y no como un registro de peticiones.

## 7. Transaccionalidad

| Elemento | Transacción |
|---|---|
| `UPDATE` de `teams` y su fila de auditoría | **La misma** (Art. V.14) |
| Relectura del detalle | La misma |

El equipo se resuelve **con bloqueo** dentro de la transacción, como hace `RF-AC-005` al retirar: dos ediciones simultáneas del mismo equipo se ordenan, y la segunda ve lo que dejó la primera en lugar de pisarlo con una copia vieja.

## 8. Impacto sobre otros módulos

Ninguno. Nadie fuera de `SP` lee `teams`, y el nombre no viaja a ningún sitio: quien referencia a un equipo lo hace por identificador.

## 9. Alternativas consideradas

| Alternativa | Por qué se descarta |
|---|---|
| **`PUT` con el recurso completo** | Obligaría a enviar el estado y abriría cambiarlo por esta vía, que es justo lo que `RF-SP-067` separa. Ninguna edición del sistema es `PUT` |
| **Permitir editar un equipo eliminado** | Movería una unicidad de la que ya no forma parte, sin efecto útil (spec §14.2) |
| **Hacer inmutable el nombre**, como el código de un rol | No hay código detrás: una errata en el alta sería permanente y la salida sería eliminar y recrear, perdiendo el historial de pertenencias |
| **Rechazar con `409` el renombrado al mismo nombre** | La unicidad no se viola contra uno mismo, y obligaría al frontend a comparar antes de enviar. `existsAliveNameExcluding` lo resuelve en la consulta |
| **Tratar `description: ""` como borrado** | La cadena vacía y el nulo serían sinónimos y habría dos formas de decir lo mismo. La cadena de solo espacios se guarda nula porque es una errata; el borrado explícito es `null` |
| **Aceptar un `PATCH` vacío con `200`** | Diría «cambié algo» sin cambiar nada, y dejaría una fila de auditoría sin diff |
| **Meter el cambio de estado aquí con un campo opcional** | Un cuerpo podría fallar por cuatro motivos distintos y el permiso dejaría de significar una operación (`RN-SEG-014`) |

## 10. Riesgos

| Riesgo | Impacto | Mitigación |
|---|---|---|
| Que la distinción ausente / nulo se pierda al deserializar | Medio — la descripción se borraría sola o no se podría borrar | El mismo envoltorio que ya usan las ediciones del sistema, y `CA-SP-757` prueba los tres casos: con valor, `null` y ausente |
| Que el diff registre campos que no cambiaron | Bajo — auditoría ruidosa | Se calcula por valor (§6) y `CA-SP-762` lo comprueba |
| Que una edición concurrente pise a otra | Medio | Bloqueo en la lectura dentro de la transacción (§7) |

## 11. Estrategia de prueba

| Criterio | Nivel | Qué verifica |
|---|---|---|
| `CA-SP-756`, `CA-SP-757` | API (`TeamUpdateIT`) | El renombrado; la descripción con valor, `null` y ausente |
| `CA-SP-758` | API (`TeamUpdateIT`) | El `409` por caja y por acentos; el nombre de un eliminado; **el propio nombre**, que debe pasar |
| `CA-SP-759` | API (`TeamUpdateIT`) | Las cuatro validaciones, incluida la del cuerpo vacío |
| `CA-SP-760` | API (`TeamUpdateIT`) | `404` al inexistente y al eliminado |
| `CA-SP-761` | Integración (`TeamUpdateIT`) | Que el estado, `deleted_at` y las pertenencias no cambian: se edita un `INACTIVO` con dos miembros y se comprueban las tres cosas después |
| `CA-SP-762` | Integración + `EndpointPermissionsIT` | La fila `UPDATE` con su diff; el `403` con `teams:read` y `teams:create` |
| `CA-SP-758` (carrera) | Integración (`TeamConcurrencyIT`) | Dos renombrados simultáneos al mismo nombre: uno `200`, otro `409`, ningún `500` |

**Unitarias sin Spring** (`TeamTest`): `rename` y `describe` —recorte, nombre vacío rechazado, `updatedAt` que avanza— que es lo único que el agregado decide solo.
