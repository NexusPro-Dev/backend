# PLAN — `RF-MV-016` Asignar los vendedores de una venta

| Campo | Valor |
|---|---|
| Requerimiento | `RF-MV-016` |
| Especificación | [`spec.md`](spec.md) v0.1.0 |
| `spec.md` aprobada el | 23-09-2026 |
| Versión | 0.1.0 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 23-09-2026 |

!!! info "Qué va en este documento"

    **Cómo se construye.** Esquema, componentes, contrato, autorización y pruebas.

    **Prueba de pertenencia:** si un cambio de negocio lo invalidaría, pertenece a `spec.md`.

---

## 1. Enfoque

**Tres piezas, y solo la tercera es un endpoint nuevo.**

1. **Un catálogo y una columna.** `movement_type_statuses` declara los estados de cada tipo y `movements.type_status_id` guarda el de cada movimiento, atado a su tipo por una clave foránea **compuesta**.
2. **La atribución al registrar deja de ser una lectura y pasa a ser una decisión.** Hoy `RegisterSaleService` y `BuyPackageService` preguntan `ClientCatalog.sellerOf` y copian lo que vuelve. Con esta tripleta preguntan **todos los vendedores** del comprador (`ClientCatalog.sellersOf`, nuevo) y una sola pieza, `SaleAttribution`, decide vendedor y estado con la regla de `RN-MV-034`. **Es una sola pieza a propósito**: dos servicios que cuentan vendedores por su cuenta acaban contando distinto.
3. **`POST /{id}/seller-assignments`**, con la forma de `RF-MV-003` y `RF-MV-005`: una acción sobre la venta, con la venta como queda en la respuesta.

**La asignación bloquea la fila de la venta** (`SELECT … FOR UPDATE`) y no usa una transición condicionada como confirmar y anular. La diferencia es de forma: aquellas cambian **una** columna con **una** condición, y la cuenta de filas decide; esta comprueba varias líneas contra el estado del pago y contra los vínculos del cliente, y escribe varias filas. El bloqueo la serializa con confirmar —cuyo `UPDATE … WHERE status = 'PENDIENTE'` espera a la fila bloqueada— y con otra asignación, y hace verdadero el FA-004 de la spec sin reintentos.

---

## 2. Cambios de esquema

**`V36__mv_estados_por_tipo.sql`**:

| Objeto | Definición | Por qué |
|---|---|---|
| `movement_type_statuses` | `id`, `movement_type_id` → `movement_types`, `code` con el formato de los catálogos, `name`, `created_at` | `RN-MV-033`. Sin `updated_at` ni `deleted_at`, como `movement_types` (`RN-MV-017`) |
| `uq_movement_type_statuses_code` | `UNIQUE (movement_type_id, code)` | Un código por tipo; dos tipos pueden repetir código |
| `uq_movement_type_statuses_tipo` | `UNIQUE (id, movement_type_id)` | Es lo que la clave compuesta necesita referenciar |
| Siembra | `VALIDAR_COMISIONES` y `VALIDADO` para `VENTA`, con identificadores literales (Art. V.11) | |
| `movements.type_status_id` | `uuid`, se añade nulo, se rellena con `VALIDADO` y pasa a `NOT NULL` | Lo ya vendido tiene vendedor en cada línea |
| `fk_movements_type_status` | `FOREIGN KEY (type_status_id, movement_type_id) REFERENCES movement_type_statuses (id, movement_type_id)` | **El esquema impide** que un movimiento lleve el estado de otro tipo |
| `ix_movements_type_status` | Sobre `type_status_id` | El filtro de los listados, y la pregunta diaria «qué falta por validar» |
| `movements:assign-sellers` | Permiso 134, `SUPERADMIN` y `ADMIN` **explícitos**; guardas 134 / 134 / 128 y contención (`RN-SEG-003`) | La forma de `V34` |

**Sin `DEFAULT`**, y es deliberado. Un valor por omisión haría que una escritura que olvida el estado produjera una venta **validada**, que es exactamente el estado que no se puede dar por supuesto. Los fixtures de pruebas que insertan ventas a mano ganan la columna.

**No hay `CHECK` que ate «validada» a «todas las líneas tienen vendedor»**: cruza dos tablas. Lo sostiene el caso de uso, y lo comprueban `CA-MV-144`, `CA-MV-149` y `CA-MV-150`.

---

## 3. Componentes afectados

| Capa | Componente | Cambio | Nota |
|---|---|---|---|
| `system/users/application` | `ClientCatalog` | Gana `sellersOf(clientId)` | Todas las filas de `client_sellers`, sin distinguir origen |
| `system/users/domain/repository` | `PublishedUserCatalog` | La implementa con `ClientSellerRepository.findSellersOf` | Ya existe para `RF-SP-059` |
| `domain/models` | `SaleTypeStatus` | Nuevo: `VALIDAR_COMISIONES`, `VALIDADO` | Los códigos que el caso de uso decide; la tabla los declara |
| `domain/models` | `MovementLine` | Admite vendedor nulo | La obligatoriedad pasa a `SaleAttribution` |
| `domain/models` | `Movement` | Lleva el estado del tipo | Lo recibe al registrarse |
| `domain/service` | `SaleAttribution` | Nuevo | `RN-MV-034`: uno, varios, ninguno, y el enlace |
| `domain/service` | `RegisterSaleService`, `BuyPackageService` | Usan `SaleAttribution` | `BuyPackageService.registrar` recibe la atribución y no un vendedor, para que el hotlink la pase hecha |
| `domain/service` | `AssignSellersService` | Nuevo | Validación, bloqueo, comprobaciones, escritura, transición, auditoría, respuesta |
| `domain/repository` | `MovementRepository` | `findTypeStatus`, `lockForAssignment`, `findLinesForAssignment`, `assignSeller`, `changeTypeStatus`, `existsTypeStatusCode`; las lecturas proyectan `type_status`; los dos filtros ganan `typeStatus` | |
| `application` | `AssignSellersRequest` | Nuevo | `lines: [{productId, sellerId}]` |
| `application` | `SaleResponse`, `PurchaseResponse`, `MovementResponse` | Ganan `typeStatus` | `MyMovementResponse` no |
| `application` | `ListMovementsRequest`, `ListSalesRequest` | Ganan `typeStatus` | |
| `interfaces` | `MovementController` | `POST /{id}/seller-assignments`; `typeStatus` en los dos listados | |

---

## 4. Contrato de API

**`POST /api/v1/movements/{id}/seller-assignments`** — `movements:assign-sellers`.

```json
{ "lines": [ { "productId": "…", "sellerId": "…" } ] }
```

| Respuesta | Cuándo |
|---|---|
| `200` | La venta como queda (`SaleResponse`), con `typeStatus` |
| `400` | `VAL-001` a `VAL-004` |
| `401` / `403` | Sin sesión / sin `movements:assign-sellers` |
| `404` | `EX-001` |
| `409` | `EX-002` (rechazada o anulada), `EX-003` (corregir una línea de una venta confirmada) |
| `422` | `EX-004` (línea ajena), `EX-005` (vendedor que no es del cliente) |

**`typeStatus`** —`VALIDAR_COMISIONES` o `VALIDADO`— se añade a `SaleResponse` (registro, detalle, confirmar, anular y esta ruta), a `PurchaseResponse` y a `MovementResponse` (`RF-MV-006` y `RF-MV-015`). Y `seller` de la línea **deja de prometer «en una venta nunca es nulo»**: la prosa se corrige.

**`GET /api/v1/movements?typeStatus=`** (`VAL-006`) y **`GET /api/v1/movements/sales?typeStatus=`** (`VAL-005`). Un código que no esté en el catálogo es error, con el argumento con que `RF-MV-006` trató el tipo.

---

## 5. Autorización

`movements:assign-sellers`, sembrado por `V36` a `SUPERADMIN` y `ADMIN`. **No reutiliza `movements:confirm`**: confirmar responde «¿entró el dinero?» y esto «¿a quién se le paga?», que no tienen por qué ser la misma persona. **No es una lectura por estructura**: quien lo porta asigna en cualquier venta; lo que acota la operación es la regla —solo entre los vendedores del cliente—, no quién mira. Entra en `PERMISO_DE_CADA_OPERACION` de `EndpointPermissionsIT`.

---

## 6. Auditoría

Un `ChangeEvent` `UPDATE` sobre `movements`, con `before` y `after`: el `seller_id` de cada línea asignada —por `product_id`— y el `type_status`. **La venta se registra con su estado del tipo** en la instantánea del alta, y `seller_id` viaja nulo y presente cuando no lo hay.

---

## 7. Transaccionalidad

Una transacción por petición. El orden es el de la spec §8, y **todas las comprobaciones van antes de la primera escritura**, de modo que cualquier rechazo deja la venta intacta sin depender de revertir nada. El bloqueo se toma **después** de validar la forma de la petición: una petición mal formada no espera a nadie.

---

## 8. Impacto sobre otros módulos

| Módulo | Impacto |
|---|---|
| `SP` | `ClientCatalog` gana un método; ningún cambio de esquema ni de regla |
| `CM` | Ninguno hoy. La etapa 5 leerá `typeStatus` |
| Frontend | Un campo nuevo en tres respuestas, un filtro en dos listados y una ruta. **`seller` de la línea puede venir nulo en una venta**: es un cambio de lo que el contrato prometía, y se declara |

---

## 9. Alternativas consideradas

| Alternativa | Por qué no |
|---|---|
| **Añadir los estados a `status`** | Mezcla dos preguntas: una venta puede estar pagada y sin validar. Obligaría además a rehacer confirmar, anular, «mis productos» y la caja. Descartada por el responsable |
| **Una columna `commission_status` con `CHECK`** | Solo sirve a la venta, y §7.2 de `requirements/mv.md` ya pagó dos veces por ampliar un `CHECK` sobre una tabla en uso. El responsable pidió estados **por tipo** |
| **Tomar el principal cuando hay varios, y dejar corregirlo** | Es lo que había, con un botón más: la venta nacería atribuida a alguien que nadie eligió, y «corregir» dependería de que alguien se acordase |
| **Una ruta por línea** (`PUT /{id}/lines/{productId}/seller`) | Asignar una venta de paquete serían varias peticiones, y el paso a validada ocurriría en la última sin que ninguna lo pidiera. Una petición con varias líneas es la unidad que el front tiene delante |
| **Transición condicionada en vez de bloqueo** | Una sentencia no puede comprobar vínculos del cliente y varias líneas a la vez |

---

## 10. Riesgos

| Riesgo | Mitigación |
|---|---|
| Diez suites insertan ventas a mano y se rompen con `NOT NULL` | Se actualizan en la misma tanda; las dos que siembran un segundo tipo le siembran también su estado |
| Un listado que une líneas con `users` por `JOIN` deja de ver las líneas sin vendedor | Revisar cada unión con `seller_id`: solo pueden ser `LEFT JOIN` o subconsultas `EXISTS` |
| El contrato se regenera sin las rutas de Equipos (PR #97) | No se publica desde esta rama: se regenera **una vez**, al integrar, sobre la rama que tenga todo |

---

## 11. Estrategia de prueba

| Prueba | Qué cubre |
|---|---|
| `SaleAttributionTest` (unitaria) | Uno, varios, ninguno con superior, ninguno sin superior, y el enlace |
| `MovementLineTest` / `MovementTest` | La línea sin vendedor, y la instantánea con el nulo presente |
| `SellerAssignmentIT` | `CA-MV-143`, `CA-MV-144`, `CA-MV-149` a `CA-MV-160` sobre la API |
| `BuyPackageIT` (ampliada) | `CA-MV-145` |
| `SelfRegistrationIT` o la de alta por enlace (ampliada) | `CA-MV-147` |
| `MovementsIT` y `SalesIT` (ampliadas) | `CA-MV-161`; `MyMovementsIT`, `CA-MV-162` |
| `EndpointPermissionsIT` | La ruta nueva y su permiso |
| Las cuatro suites que cuentan el catálogo | 134 |
