# PLAN — `RF-MV-058` Rellenar la oficina de las líneas de venta que no la tienen

| Campo | Valor |
|---|---|
| Requerimiento | `RF-MV-058` |
| Especificación | [`spec.md`](spec.md) v0.1.0 |
| `spec.md` aprobada el | 09-10-2026 |
| Versión | 0.1.0 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 09-10-2026 |

---

## 1. Enfoque

**Una transacción, cuatro sentencias, y a Java solo suben los vendedores.** `FillLineTeamsService.fill()`, `@Transactional`:

1. **Bloquea las ventas afectadas, en orden de identificador**: `SELECT m.id FROM movements m WHERE m.movement_type_id = (SELECT t.id FROM movement_types t WHERE t.code = 'VENTA') AND EXISTS (SELECT 1 FROM movement_details d WHERE d.movement_id = m.id AND d.seller_id IS NOT NULL AND d.team_id IS NULL) ORDER BY m.id FOR UPDATE OF m`, contado en una subconsulta para no traer los identificadores. Es el orden de bloqueos del módulo —**la venta antes que sus líneas**—, el mismo de `RF-MV-016` (`lockForAssignment`): una asignación concurrente espera o hace esperar (`FA-003`), y dos órdenes de relleno a la vez no se cruzan. Si no hay ninguna, responde cero sin escribir.
2. **Lee los vendedores distintos** de esas líneas: `SELECT DISTINCT d.seller_id` con la misma condición.
3. **Pregunta la oficina vigente** a `teams`: `SellerTeamLookup.currentTeamsOf(vendedores)` ([`RF-MV-001`](../001-registrar-venta/plan.md) §2.8), una sentencia para todos, con la cadena y la pertenencia **de ahora**. El vendedor sin entrada —un manager, o sin director con equipo— se queda fuera (`CA-MV-732`).
4. **Escribe por equipo**: agrupa los vendedores por su equipo y, para cada uno, `UPDATE movement_details d SET team_id = :equipo WHERE d.seller_id IN (:vendedores) AND d.team_id IS NULL AND d.movement_id IN (SELECT m.id FROM movements m WHERE m.movement_type_id = (… 'VENTA')) RETURNING d.movement_id, d.product_id`, con `RETURNING` como `JpaCommissionBatchRepository`. **`team_id IS NULL` dentro de la propia sentencia** es la garantía de `CA-MV-730`, no una comprobación previa. Las ventas van por subconsulta y no por lista, para no topar con el límite de parámetros del controlador. `filled` es la suma de las filas devueltas.

**No se filtra por estado de la venta** (`CA-MV-735`): la regla habla de líneas sin oficina, y una anulada también se vendió en una oficina. **Solo `seller_id` y `team_id` deciden**, y solo se escribe `team_id` (`CA-MV-736`): ni el vendedor, ni el estado, ni nada de `CM`, que no mira la oficina.

---

## 2. Cambios de esquema

**Ninguno propio.** `V97` ([`RF-MV-001`](../001-registrar-venta/plan.md) §2.8) trae la columna y el permiso `movements:fill-line-teams` a `SUPERADMIN` y `ADMIN`. **No es sensible** (`requires_recent_mfa` en falso): rellenar oficinas no mueve dinero, y la lista de sensibles la confirmó el responsable el 06-10-2026.

---

## 3. Componentes afectados

| Capa | Componente | Cambio |
|---|---|---|
| `domain/service` | `FillLineTeamsService` | Nuevo: bloqueo, vendedores, oficinas, escritura por equipo, auditoría |
| `domain/repository` | `MovementRepository`, `JpaMovementRepository` | `lockSalesWithLinesWithoutTeam()`, `findSellersOfLinesWithoutTeam()`, `fillLineTeam(UUID teamId, Collection<UUID> sellerIds)` → las líneas rellenadas (venta y producto) |
| `application` | `LineTeamFillResponse` | Nuevo: `{ "filled": n }`, con `@Schema(name = "LineTeamFillResponse")` propio |
| `interfaces` | `MovementController` | `POST /sales/lines/team-fill` |
| `system/teams/application` | `SellerTeamLookup.currentTeamsOf` | De `RF-MV-001` §2.8, sin cambio aquí |

---

## 4. Contrato de API

| Verbo | Ruta | Permiso | Cuerpo |
|---|---|---|---|
| `POST` | `/api/v1/movements/sales/lines/team-fill` | `movements:fill-line-teams` | Ninguno |

| Código | Cuándo |
|---|---|
| `200` | Siempre que se ejecuta, también con `filled` en cero |
| `401` | Sin token |
| `403` | Sin `movements:fill-line-teams` |

**Bajo `/sales/lines`**, junto a la consulta de líneas de `RF-MV-017`: es una acción sobre ese conjunto y no sobre una venta. **`POST`** porque es una orden y no la representación de un estado. **`200` y no `204`**: la respuesta dice cuántas, y es lo que administración necesita para saber si ya terminó. La prosa de la `@Operation` dice que **solo toca líneas con vendedor y sin oficina**, que usa la oficina **de hoy** y que es **repetible**.

---

## 5. Autorización

`@PreAuthorize("hasAuthority('movements:fill-line-teams')")`. La ruta entra en `PERMISO_DE_CADA_OPERACION` y en `EndpointPermissionsIT`.

---

## 6. Auditoría

**Un `ChangeEvent` `UPDATE` por venta tocada**, sobre `movements` y con su identificador, como la asignación de vendedores (`RF-MV-016`): `before` con cada línea rellenada —`product_id` y `team_id` nulo y presente— y `after` con la oficina escrita. Se arma con las filas que devuelve `RETURNING`, agrupadas por venta.

**Por qué por venta y no una entrada con el total.** `audit_change_log.entity_id` es `NOT NULL` (`V2`): una entrada única no tiene a qué entidad apuntar sin inventar un identificador. Y el patrón del borrado de lotes vacíos de `CM` (`RF-CM-027`, `EmptyBatchRemoval`) es exactamente este: **un registro por cosa afectada**, nunca uno por la orden. Lo que se gana es que la pregunta «¿cuándo ganó esta venta su oficina, y cuál?» se responde mirando la historia de la venta, como cualquier otro cambio suyo.

---

## 7. Transaccionalidad

Una transacción por orden, **todo o nada**. Con el bloqueo del paso 1, la asignación (`RF-MV-016`) y el relleno se serializan sobre la venta: si la asignación gana, la línea ya tiene vendedor y oficina —o vendedor nuevo sin oficina, y entonces el relleno la ve vacía y la rellena con la de hoy de ese vendedor—; si gana el relleno, la asignación escribe después su propia oficina. **Dos rellenos a la vez** bloquean las mismas ventas en el mismo orden: el segundo espera y, al seguir, no encuentra nada vacío.

---

## 8. Alternativas consideradas

| Alternativa | Por qué no |
|---|---|
| Rellenar en `V97` | A esa hora ningún director está en un equipo: no hay oficina que poner (`requirements/mv.md` §4.13) |
| Rellenar con la oficina **a la fecha de la venta** | Para todo lo anterior a la regla da vacío, que es el problema de partida |
| Rellenar solo al asignar un director a un equipo | Un proceso invisible que mueve ventas al tocar `SP`; el responsable decidió una orden |
| Un `UPDATE … FROM` con la `WITH RECURSIVE` dentro | Leería `team_members` y `user_supervisors` desde `MV`; D-25 lo prohíbe. La cadena la calcula `teams` por su puerto |
| Recorrer línea a línea | Una `N+1` sobre todo el libro |
| Una entrada de auditoría con el total | `entity_id` es `NOT NULL`; ver §6 |

---

## 9. Estrategia de prueba

`FillLineTeamsIT`, con la estructura sembrada —director con equipo y sus agentes, un manager, un vendedor sin director con equipo— y ventas insertadas **con `team_id` nulo**, como las anteriores a la regla: `CA-MV-729` a `CA-MV-739`. `CA-MV-733` con una venta fechada antes del inicio de la pertenencia del director. `CA-MV-734` ejecutando dos veces y, antes de una tercera, asignando tarde a un director. `CA-MV-735` con una venta en cada estado. `CA-MV-736` comparando las filas enteras antes y después salvo `team_id`. `CA-MV-740` en la prueba de la siembra de permisos, junto a las suites que cuentan el catálogo (234, `ADMIN` 232, `RF-MV-001` · `T-50`).
