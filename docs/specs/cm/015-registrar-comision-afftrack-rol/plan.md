# PLAN — `RF-CM-015` Registrar una comisión afftrack de rol

| Campo | Valor |
|---|---|
| Requerimiento | `RF-CM-015` |
| Especificación | [`spec.md`](spec.md) v0.1.0 |
| `spec.md` aprobada el | 29-09-2026 |
| Versión | 0.1.0 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 29-09-2026 |

!!! info "Qué va en este documento"

    **Cómo se construye.** Esquema, componentes, contrato, autorización y pruebas.

    **Prueba de pertenencia:** si un cambio de negocio lo invalidaría, pertenece a `spec.md`.

La mecánica es la de [`RF-CM-001`](../001-registrar-tasa-comision-rol/plan.md): el mismo orden de comprobaciones, los mismos códigos para rol y producto, el importe contra la moneda con `ProductCurrencyScale` y la unicidad en un índice parcial que el adaptador traduce. **Aquí solo se escribe lo que cambia.**

---

## 1. Enfoque

**Este requerimiento trae la migración del submódulo entero**, como `RF-CM-013` trajo la de la liquidación: las cuatro tablas afftrack, el cambio de `commissions` y los nueve permisos. Repartirla en tres migraciones —una por requerimiento— obligaría a que las pruebas de `RF-CM-015` corrieran contra un esquema a medias, y `commissions.commission_kind` la necesitan a la vez `RF-CM-020` y las lecturas de lotes.

**«Es FTD» es una pregunta a `PM`**, y la contesta `PM`: el producto, su tipo, su origen y su destino son suyos. `ProductCatalog` gana `Set<UUID> ftdProductIds()`: **los productos FTD**, retirados incluidos, los que tienen `type = UPGRADE_MEMBRESIA` y origen y destino iguales a **la membresía de código `BECA`** —el mismo literal y el mismo criterio que `MembershipCatalog.floor()` y `SaleRules` (`RN-SP-018`)—. **Es un conjunto y no un predicado por producto** porque tiene tres consumidores —esta alta, el devengo que excluye las líneas FTD (`RF-CM-013`) y el cierre que las cuenta (`RF-CM-020`)— y los dos últimos preguntan por muchas líneas a la vez; el conjunto es pequeño —hoy, un producto— y **la definición de FTD vive así en una sola sentencia**. **No se añade a `ProductView`**, que usan media docena de consumidores a los que el dato no les dice nada.

---

## 2. Cambios de esquema

**`V54__cm_comision_afftrack.sql`** — la siguiente libre a 29-09-2026 (`V53` la tomó `PM` el 28-09-2026). Crea, con las restricciones de [`requirements/cm.md` §7.4](../../../requirements/cm.md):

| Objeto | Qué |
|---|---|
| `afftrack_rates` | §7.9, con `uq_afftrack_rates_product_role_threshold` parcial sobre las vivas |
| `user_afftrack_rates` | §7.10, con `ex_user_afftrack_rates_vigente` (`btree_gist` ya declarada) |
| `afftrack_settlements` | §7.11, con `ck_afftrack_settlements_counts` y `ck_afftrack_settlements_threshold` |
| `afftrack_ftds` | §7.12, con `fk_afftrack_ftds_detail` en `RESTRICT` |
| `commissions` | Gana `commission_kind varchar(20) NOT NULL DEFAULT 'POR_VENTA'` —el valor por omisión rellena las filas que existen y **se retira en la misma migración**, para que ninguna inserción futura se ahorre declararlo— y `afftrack_settlement_id uuid NULL`; `movement_detail_id`, `chain_level` y `unit_price` pasan a `NULL`; entran `ck_commissions_kind_values`, `ck_commissions_kind`, `fk_commissions_settlement` y `uq_commissions_settlement` |
| `permissions` | Los nueve de `cm.md` §6, a `SUPERADMIN` y `ADMIN`. **Catálogo 153 → 162** |
| Índices de apoyo | `ix_afftrack_rates_product_role` y `ix_user_afftrack_rates_user_product` para la resolución de la escala; `ix_afftrack_settlements_user_product_created` para leer el remanente más reciente |

**Ningún permiso afftrack va por tipo de rol**: son configuración y lectura de administración, y lo propio no entra (`cm.md` §6).

---

## 3. Componentes afectados

| Módulo | Capa | Componente | Cambio | Nota |
|---|---|---|---|---|
| `PM` | `application` | `ProductCatalog` | Gana `Set<UUID> ftdProductIds()` | Retirados incluidos: el alta los rechaza antes por `EX-004`, y el cierre tiene que seguir contando los FTD de un producto que se retiró |
| `PM` | `domain/repository` | Adaptador de `ProductCatalog` | Implementa `ftdProductIds` | Una sentencia: `type = 'UPGRADE_MEMBRESIA'` y origen y destino iguales a la membresía `BECA` |
| `CM` | `domain/models` | `AfftrackRate` | Nuevo | `create`, `instantanea` para la auditoría |
| `CM` | `domain/repository` | `AfftrackRateRepository` y adaptador | Nuevos | `existsAlive(productId, roleId, threshold)`; traduce `uq_afftrack_rates_product_role_threshold` a `409` |
| `CM` | `domain/service` | `RegisterAfftrackRateService` | Nuevo | El orden de §8 de la spec |
| `CM` | `domain/service` | `ProductCurrencyScale` | Gana una sobrecarga para un importe suelto | Hoy recibe un `CommissionValue`; el escalón no lo es (`cm.md` §7.9) |
| `CM` | `application` | `RegisterAfftrackRateRequest`, `AfftrackRateResponse` | Nuevos | `@Schema(name)` explícito — springdoc funde dos records con el mismo nombre simple |
| `CM` | `interfaces` | `AfftrackRateController` | Nuevo | `POST /api/v1/afftrack-rates` |
| `shared` | seguridad | `PERMISO_DE_CADA_OPERACION` | Gana la ruta | `EndpointPermissionsIT` |

---

## 4. Contrato de API

| Verbo | Ruta | Permiso |
|---|---|---|
| `POST` | `/api/v1/afftrack-rates` | `afftrack-rates:create` |

**Cuerpo:** `roleId`, `productId`, `threshold` (entero), `amountPerFtd` (decimal).

**Respuesta `201`:** `id`, `role {id, code, name}`, `product {id, code, name, currencyCode, currencyDecimalPlaces}`, `threshold`, `amountPerFtd`, `amountAtThreshold`, `createdAt`.

**Códigos:** `201`; `400` (`VAL-001`–`VAL-005`, `EX-001`); `401`; `403`; `409` (`EX-006`); `422` (`EX-002`–`EX-005`).

---

## 5. Autorización

`@PreAuthorize("hasAuthority('afftrack-rates:create')")`; la ruta en `PERMISO_DE_CADA_OPERACION`.

---

## 6. Auditoría

`ChangeAction.CREATE` sobre `afftrack_rates`, con el estado completo.

---

## 7. Transaccionalidad

`@Transactional`. **No hay bloqueo consultivo**: sin tope que sumar (`RN-CM-038`), la única carrera posible es el duplicado, y la cierra el índice.

---

## 8. Impacto sobre otros módulos

**`PM`** gana `ftdProductIds` en `ProductCatalog`. Sin enmienda documental en `requirements/pm.md`: es una lectura nueva de datos que ya existen.

**Las suites que limpian `movements`** tendrán que limpiar antes `afftrack_ftds` por `fk_afftrack_ftds_detail` (el mismo coste que ya tiene `fk_commissions_detail`). El ayudante de limpieza que `RF-CM-013` introdujo gana esa tabla.

---

## 9. Alternativas consideradas

| Alternativa | Por qué no |
|---|---|
| La definición de FTD en `CM`, con un `JOIN` a `products` y `memberships` | Es una propiedad del producto, y la decide la forma que `PM` le da; en `CM` se leerían columnas de `PM` para responder una pregunta de `PM` |
| Una columna `is_ftd` en `products` | Un dato deducible que puede mentir: un producto con la marca y con otra pareja de membresías |
| Una migración por requerimiento | Ver §1 |

---

## 10. Riesgos

| Riesgo | Mitigación |
|---|---|
| Retirar el `DEFAULT` de `commission_kind` rompe una inserción de `RF-CM-013` que no lo declare | La enmienda de `RF-CM-013` lo declara (`T-02` de este requerimiento, abajo en `tasks.md`), y la suite de devengo lo detecta si falta |
| Relajar `movement_detail_id` a `NULL` deja entrar filas de venta sin línea | `ck_commissions_kind` las rechaza; lo prueba la suite de `RF-CM-020` |

---

## 11. Estrategia de prueba

`RegisterAfftrackRateIT`: `CA-CM-209` a `CA-CM-216`, con un producto `BECA → BECA` sembrado por fixture y los tres «no FTD» —otra pareja, un bot, uno retirado—. `CA-CM-211` con dos hilos. **Los seis recuentos del catálogo** que `RF-CM-013` enumeró (`PermissionsSeedIT`, `JpaPermissionQueryRepositoryIT`, `ListPermissionsServiceIT`, `TeamsPermissionsSeedIT`, `PermissionIT` y la de `CM`) pasan de 153 a **162**.
