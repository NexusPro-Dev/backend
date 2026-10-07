# PLAN — `RF-CM-013` Devengar las comisiones de una línea de venta

| Campo | Valor |
|---|---|
| Requerimiento | `RF-CM-013` |
| Especificación | [`spec.md`](spec.md) v0.1.0 |
| `spec.md` aprobada el | 28-09-2026 |
| Versión | 0.7.0 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 28-09-2026 |
| Enmendado el | 29-09-2026 — las líneas FTD fuera del devengo y `commission_kind` en la inserción (§12) |
| Enmendado el | 29-09-2026 — la directa en el nivel `0`, `LastLinkRoles` en `SP` y `directCommissionOf` en `PM` (§13) |
| Enmendado el | 30-09-2026 — la línea revertida se devenga otra vez, sin código nuevo (§14) |
| Enmendado el | 05-10-2026 — la comisión y el lote en centésimas, redondeo al construir la fila (§16) |
| Enmendado el | 07-10-2026 — la cadena vieja se borra; el devengo no cambia (§17) |

!!! info "Qué va en este documento"

    **Cómo se construye.** Esquema, componentes, contrato, autorización y pruebas.

    **Prueba de pertenencia:** si un cambio de negocio lo invalidaría, pertenece a `spec.md`.

La mecánica común de `CM` —puertos en `domain/repository`, adaptadores JPA con SQL nativo, traducción de restricciones por nombre y de exclusiones por estado SQL— la fijó el plan de [`RF-CM-001`](../001-registrar-tasa-comision-rol/plan.md) y **se hereda sin repetirla**. La resolución de la tasa es la de [`RF-CM-005`](../005-consultar-comision-efectiva/plan.md), **consumida y no reimplementada**.

---

## 1. Enfoque

**Un evento de Spring publicado por `MV` dentro de su transacción y escuchado por `CM` después del commit, y una transacción nueva por línea.**

- **`MV` publica** con `ApplicationEventPublisher` un `CommissionableLinesEvent` —un `record` de su paquete `application`, con la venta y los identificadores de las líneas— desde los dos únicos sitios donde una línea queda comisionable: `ConfirmSaleService` y `AssignSellersService`. Publicar **dentro** de la transacción es lo que permite escuchar **después** de ella.
- **`CM` escucha** con `@TransactionalEventListener(phase = AFTER_COMMIT)`. Si la transacción de `MV` se revierte, el evento **no se entrega** —no hay aviso de una venta que no existe—; si se confirma, se entrega en el mismo hilo, después del commit. **Es el primer evento entre módulos del sistema**, y por eso este plan fija la forma para los que vengan (§8).
- **Una transacción por línea** (`TransactionTemplate` con `REQUIRES_NEW`), dentro de un bucle que **captura** la excepción de cada una: es lo que hace verdad `CA-CM-166` —la venta no se entera, y una línea que falla no arrastra a sus vecinas—.

**Se relee, no se confía en el evento.** El evento dice **dónde mirar**; lo que se comprueba es lo que `MV` publica al releer (`spec.md` §11). Así el mismo servicio sirve al aviso, al barrido y al reintento, y un evento repetido o tardío no hace daño.

**La exclusión entre dos devengos de la misma línea es un bloqueo consultivo**, `pg_advisory_xact_lock(ns, hashtext(detail_id))`, el mismo patrón que `ProductCommissionCapGuard` y `JpaUserCommissionRateRepository`. Tomado el bloqueo, se mira si la línea ya tiene desenlace: si lo tiene, se sale (`FA-001`). `pk_commission_accruals` y `uq_commissions_detail_user` quedan **detrás**, como red.

---

## 2. Cambios de esquema

**`V51__cm_devengo_de_comisiones.sql`** —la siguiente libre a 28-09-2026— crea **las cuatro tablas de la liquidación**, como [`requirements/cm.md`](../../../requirements/cm.md) §7.5 a §7.8 las declara, con todas sus restricciones de §7.4:

| Tabla | Lo que conviene mirar al escribirla |
|---|---|
| `commission_closings` | Va **primero**: `commission_batches.closing_id` la señala |
| `commission_batches` | `ex_commission_batches_solape` con `tstzrange(period_start, period_end, '[)')` —el fin nulo es «sin techo»—; `ck_commission_batches_periodo`; `ck_commission_batches_pagado` con `movement_id` |
| `commissions` | `uq_commissions_detail_user`; `fk_commissions_detail` **`RESTRICT`**; `accrued_at`; `fk_commissions_batch` `ON DELETE CASCADE` |
| `commission_accruals` | `movement_detail_id` como clave primaria; `fk_commission_accruals_detail` **`RESTRICT`**; `ck_commission_accruals_reason` |

Y un índice de apoyo: `ix_commission_batches_abierto` sobre `(user_id, currency_id) WHERE status = 'ABIERTO'`, para encontrar el lote abierto sin recorrer el historial.

**Siembra los ocho permisos de la liquidación** ([`security.md`](../../../security.md) v0.81.0 §4.4), aunque solo `RF-CM-009` a `RF-CM-012` y `RF-CM-014` los usen: **las tablas y los permisos del submódulo nacen juntos**, y repartirlos entre cinco migraciones obligaría a cinco recuentos del catálogo. Serie de `CM`, **el siguiente sufijo libre tras el mayor de `V28`**. **`SUPERADMIN` y `ADMIN` reciben los ocho**, explícitos; **los roles de tipo `VENDEDOR` reciben `list-own` y `read-own`**, por tipo de rol y no por nombre. **Catálogo 145 → 153.**

---

## 3. Componentes afectados

| Módulo | Capa | Componente | Cambio | Nota |
|---|---|---|---|---|
| `shared` | `time` | `BusinessCalendar` | **Nuevo** | La zona del negocio por configuración (`nexus.business.zone`, `America/Bogota` por defecto; [`architecture.md` §15.1.1](../../../architecture.md)): `ZoneId zona()`, `LocalDate hoy()`, `LocalDate diaDe(OffsetDateTime)`. **No sustituye a `MovementCode.ZONA`** en esta tripleta: moverla es un cambio de `MV` sin motivo propio |
| `MV` | `application` | `CommissionableLinesEvent` | **Nuevo — publicado** | `record(UUID movementId, List<UUID> detailIds)` |
| `MV` | `application` | `CommissionableLines` | **Nueva interfaz publicada** | `List<CommissionableLine> of(Collection<UUID> detailIds)` —solo las que **cumplen** `RN-CM-022` hoy, las demás se omiten— y `List<UUID> idsAfter(UUID cursor, int limit)` para el barrido, por clave y no por página. `CommissionableLine(detailId, movementId, productId, sellerId, unitPrice, quantity, currencyId, occurredAt)` |
| `MV` | `domain/service` | `ConfirmSaleService`, `AssignSellersService` | Modificados | Publican el evento con las líneas que **quedaron** comisionables: al confirmar, las que tienen vendedor; al asignar en una venta `CONFIRMADA`, las que acaban de recibirlo |
| `SP` | `application` | `SupervisorChain` | **Nueva interfaz publicada** | `List<UUID> chainAt(UUID sellerId, OffsetDateTime at)`: el vendedor primero y sus superiores **vigentes en ese instante** (`started_at <= at AND (ended_at IS NULL OR ended_at > at)`), por un `WITH RECURSIVE` con tope de profundidad y guarda de ciclo. **La condición que `requirements/cm.md` §3 impuso a `SP` el 24-09-2026**, en la dirección contraria a `CommercialReach` |
| `CM` | `domain/service` | `ResolveCommissionService` | Modificado | «Hoy» deja de ser `Clock.systemUTC()` y pasa a `BusinessCalendar.hoy()`. Gana una operación interna que devuelve **la tasa resuelta con su identidad** para el devengo, sobre el mismo puerto: no hay segunda sentencia de precedencia |
| `CM` | `domain/models` | `AccrualOutcome`, `BatchStatus` | Nuevos | Enums: `DEVENGADA`/`SIN_COMISION`/`RECHAZADA` y `ABIERTO`/`PENDIENTE`/`PAGADO` |
| `CM` | `domain/service` | `ChainCommissionCalculator` | Nuevo | **Puro**, sin base: de la cadena y sus tasas, lo de cada nivel y el veredicto de `RN-CM-026`. Es donde viven la base bruta, el fijo por unidad y el tope |
| `CM` | `domain/service` | `CommissionAccrualService` | Nuevo | `AccrualSummary accrue(Collection<UUID> detailIds)` y `retryRejected()`; una transacción por línea (§1) |
| `CM` | `domain/repository` | `CommissionAccrualRepository`, `CommissionBatchRepository` y sus adaptadores | Nuevos | El bloqueo de la línea, el desenlace, las comisiones, **el lote abierto** (§4) |
| `CM` | `interfaces` | `CommissionableLinesListener` | Nuevo | `@TransactionalEventListener(AFTER_COMMIT)` → `accrue(event.detailIds())` |

---

## 4. El lote abierto, y la suma que no se pierde

```
1. SELECT … FROM commission_batches
    WHERE user_id = :u AND currency_id = :c AND status = 'ABIERTO' FOR UPDATE
2. si no hay: INSERT … (status 'ABIERTO', period_start = :ahora) ON CONFLICT DO NOTHING
             y se repite 1   — otro devengo lo abrió a la vez, y ex_commission_batches_solape lo impidió
3. INSERT INTO commissions (…, batch_id, accrued_at = :ahora)
4. UPDATE commission_batches SET total_amount = total_amount + :importe, updated_at = :ahora
    WHERE id = :lote
```

**`ON CONFLICT DO NOTHING` sin columnas** es la única forma que Postgres admite contra una restricción de exclusión, y es exactamente la que hace falta: el que llega segundo **no inserta** y encuentra el del primero. **El `UPDATE` suma sobre la fila** (`CA-CM-165`) y el `FOR UPDATE` del paso 1 es lo que serializa con el cierre de `RF-CM-009`: el cierre toma los mismos lotes con `FOR UPDATE`, y el devengo que llegue después **ya no los encuentra abiertos** y abre uno nuevo.

---

## 5. Contrato de API

**Ninguno.** No hay ruta. Los contratos son las tres interfaces publicadas de §3, documentadas en su Javadoc como `MembershipGrant` y `CommissionPayout`.

---

## 6. Autorización

**Ninguna propia**: no hay operación de la API (`security.md` v0.81.0, `RN-SEG-015`). Quien confirma la venta o asigna el vendedor pasó por los permisos de `MV`.

---

## 7. Auditoría

**Las comisiones no se auditan fila a fila**: son un hecho derivado, no una decisión de nadie, y copiarlas en `audit_change_log` duplicaría el libro. Se audita **el desenlace**: un `ChangeEvent` por línea sobre `commission_accruals` —`INSERT` o, en el reintento, `UPDATE`—, con la venta, el desenlace, el número de comisiones y el motivo si lo hay. **Un fallo inesperado** (`EX-001`) se registra con `log.error` e identificador de la línea, y no escribe nada más: la línea sin desenlace **es** la constancia.

---

## 8. Impacto sobre otros módulos

| Módulo | Qué cambia | Enmienda que aplica este plan |
|---|---|---|
| `MV` | Publica `CommissionableLinesEvent` y `CommissionableLines` (`RN-MV-049`) | Ya registrada en [`requirements/mv.md`](../../../requirements/mv.md) v0.49.0. **Las tripletas de `RF-MV-003` y `RF-MV-016` no cambian de comportamiento visible**: el evento no altera su respuesta. Se anota en el `tasks.md` de cada una como desviación |
| `SP` | Publica `SupervisorChain` | **[`requirements/sp.md`](../../../requirements/sp.md) v1.88.0** registra la condición que `CM` le impuso el 24-09-2026 y que no llegó a escribirse allí |
| `shared` | `BusinessCalendar` | [`architecture.md`](../../../architecture.md) §15.1.1 lo nombra como **el** sitio de la zona del negocio |
| Pruebas de `MV` | **Confirmar una venta ahora deja comisiones**, y `fk_commissions_detail` y `fk_commission_accruals_detail` son `RESTRICT`: las **21 suites** que limpian con `DELETE FROM movements` fallarán | Se limpian `commission_accruals`, `commissions` y `commission_batches` **antes**, desde un ayudante común de prueba y no copiando el `DELETE` veintiuna veces. Es el mismo accidente que `product_links` provocó el 22-09-2026 con una clave sin `ON DELETE` |

**La forma de un evento entre módulos, para los que vengan:** el `record` vive en el `application` de quien publica; se publica **dentro** de su transacción; se escucha con `AFTER_COMMIT` en el `interfaces` de quien escucha; y quien escucha abre su propia transacción. **ArchUnit** ya permite que `CM` lea el `application` de `MV`; no hace falta regla nueva.

---

## 9. Alternativas consideradas

| Alternativa | Por qué no |
|---|---|
| Llamar a `CM` desde `ConfirmSaleService` en la misma transacción | `MV` dependería de `CM` y un error de comisiones impediría cobrar (`requirements/cm.md` §5.7) |
| Una bandeja de salida en `MV` | El barrido responde lo mismo con los datos que ya existen (§5.7) |
| `@Async` en el escuchador | La respuesta de confirmar sería más rápida, a cambio de pruebas no deterministas y de un hilo más que vigilar. El cálculo es de milisegundos; si deja de serlo, es un cambio de una anotación |
| Una transacción para todo el evento | Una línea que falla revertiría a sus vecinas (`CA-CM-166`) |
| Unicidad del lote abierto con un índice único parcial | `ex_commission_batches_solape` ya la da, con el fin nulo como «sin techo»; un segundo índice diría lo mismo con otras palabras |

---

## 10. Riesgos

| Riesgo | Mitigación |
|---|---|
| El evento se pierde si la aplicación cae entre el commit y el escuchador | El barrido del cierre (`RF-CM-009`, `RN-CM-034`); `commission_closings.lines_swept` lo delata |
| Una prueba con `@Transactional` nunca dispara el evento —la transacción se revierte— y parece que el devengo no funciona | Las suites de este requerimiento **no** son transaccionales y limpian al terminar; se dice en su Javadoc |
| Las 21 suites de `MV` | §8, con el ayudante común; y el orden alfabético fijo de la suite lo hace reproducible |
| El rol de cada nivel es el de hoy | Aceptado en `spec.md` §14 |

---

## 11. Estrategia de prueba

- **Unitarias** de `ChainCommissionCalculator`: base bruta, fijo por unidad, cero, tope, gratuito con fijo (`CA-CM-160`, `CA-CM-161`, casos límite).
- **Unitarias** de `BusinessCalendar` con el reloj fijado a las `01:00Z` —las 20:00 del día anterior en Bogotá—.
- **`SupervisorChainIT`** (en `SP`): cadena vigente, cadena a una fecha pasada, historial cerrado, sin superior.
- **`CommissionAccrualIT`**: `CA-CM-154` a `CA-CM-169`, **confirmando y asignando por la API de `MV`** —no invocando el servicio—, para que el evento, el `AFTER_COMMIT` y la transacción nueva entren en la prueba. `CA-CM-164` y `CA-CM-165` con dos hilos. `CA-CM-166` con un doble que falla en la segunda línea.
- **Los seis recuentos del catálogo** (`PermissionsSeedIT`, `JpaPermissionQueryRepositoryIT`, `ListPermissionsServiceIT`, `TeamsPermissionsSeedIT`, `PermissionIT` con `153L`, y la de `CM`): 145 → 153.

## 12. Las líneas FTD, fuera — enmienda del 29-09-2026

`RN-CM-022` gana una quinta condición y `RN-CM-044` una columna.

- **`CommissionAccrualService` descarta las líneas cuyo producto esté en `ProductCatalog.ftdProductIds()`**, pedido **una vez por tanda** —no por línea— tras `CommissionableLines.of`. **Se descarta sin escribir desenlace**, y es deliberado: `commission_accruals` dice qué pasó con una línea **en este camino**, y una línea FTD no es de este camino. **`CommissionableLines` no cambia**: la definición de FTD vive en `PM` (`RF-CM-015` `plan.md` §1), y copiarla en la sentencia de `MV` la tendría en dos sitios.
- **El precio de no escribir desenlace**: el barrido de cada cierre vuelve a encontrar las líneas FTD —no tienen fila— y las vuelve a descartar. Son pocas —las altas `BECA → BECA`— y la cuenta es en memoria contra un conjunto de un elemento; si pesara, `idsAfter` puede recibir los productos a excluir sin que `MV` sepa qué es un FTD.
- **La inserción de `commissions` declara `commission_kind = 'POR_VENTA'`**, porque `V54` retira el valor por omisión (`RF-CM-015` `T-02`).

`CommissionAccrualIT` gana `CA-CM-253` —criterio de `RF-CM-020`—: una línea `BECA → BECA` confirmada y con vendedor, sobre un producto con una tasa **sembrada directamente** en la base —el alta ya la rechaza (`RN-CM-037`)—, no deja comisión ni desenlace, ni al confirmar ni tras un cierre.

## 13. La comisión por venta directa — enmienda del 29-09-2026

`RN-CM-045` ([`requirements/cm.md`](../../../requirements/cm.md) v0.24.0 §5.9). **Solo cambia el nivel `0`, y solo cuando lo que ganó es la tasa de rol o nada.**

- **`SP` publica `LastLinkRoles.ids()`**: los roles `VENDEDOR` vivos de los que **no cuelga ningún otro rol `VENDEDOR` vivo** —`NOT EXISTS` sobre `roles.parent_role_id`—. Hoy devuelve uno, `AGENTE`. **Una interfaz y no un método más de `SellerRoleCatalog`**, por la norma de §15.2 de [`architecture.md`](../../../architecture.md): una interfaz por lectura. La implementa `PublishedUserCatalog`, que ya lee `roles` para `sellerRoleOf`. **Se define por la forma y no por el código**, como `CommercialStructure.esCuspide`: un rango nuevo por debajo de `AGENTE` no exige tocar nada.
- **`PM` publica `ProductCatalog.directCommissionOf(UUID)`**: la directa del producto —tipo y valor, con un tipo propio de `PM`—, o vacío en un FTD.
- **`CommissionAccrualService` pide `LastLinkRoles.ids()` una vez por tanda**, como `ftdProductIds()` (§12). En el nivel `0`, **después** de `rateFor` —que no cambia—: si lo resuelto **no es `PERSONALIZADA`** y el rol vendedor del vendedor (`SellerRoleCatalog.sellerRoleOf`) **existe y no está en el conjunto**, se sustituye por la directa: `ResolvedRate(DIRECTA, productId, tipo, valor, null, null)`. **Sin rol vendedor no hay rango**, y se resuelve como hoy.
- **`RateSource` gana `DIRECTA`**, al final. El Javadoc del enumerado dice que su orden es la precedencia de la sentencia de `RF-CM-005`; `DIRECTA` **no sale de esa sentencia** y se dice. **El contrato de `RF-CM-005` pasa a listar `DIRECTA` en el enumerado de `source` sin devolverlo nunca**: se acepta y se escribe en su `@Operation`, porque un segundo enumerado para una sola columna costaría una traducción en cada lectura de lotes.
- **`ChainCommissionCalculator` no cambia**: recibe un `ResolvedRate` más y la suma de la línea lo cuenta, de modo que `RN-CM-026` rechaza una directa que, con los overrides, pase del 100 %.

**Alternativa descartada: resolverlo en la sentencia de `RF-CM-005`** con una tercera rama. Haría que la consulta de la comisión efectiva dependiera de quién pregunta y para qué, y mezclaría en SQL una regla que depende del nivel de la cadena, que la sentencia no conoce.

**Riesgo: el rango es el de hoy** (`requirements/cm.md` §5.9, supuesto 1). Un ascenso entre la venta y la asignación del vendedor cambia lo que se paga. Se acepta con la regla; la prueba lo fija para que un cambio futuro sea deliberado.

`CommissionAccrualIT` gana `CA-CM-264` a `CA-CM-270`, confirmando por la API de `MV` como el resto de la suite. `LastLinkRolesIT` en `SP`: la jerarquía sembrada devuelve `AGENTE`, y un rol vendedor hijo de `AGENTE` creado en la prueba lo saca del conjunto.

## 14. La línea revertida — enmienda del 30-09-2026

`RN-CM-047`. **Ningún componente cambia**, y conviene decir por qué basta:

- **`RF-CM-024` borra el desenlace de la línea**, y «sin desenlace» es exactamente lo que el paso 2 de `spec.md` §8 exige para devengar. El aviso de `MV` llega después del commit de la corrección con la línea dentro (`RF-MV-016` `plan.md` §12), y si se pierde, el barrido la encuentra por la misma razón.
- **El bloqueo consultivo de la línea es el mismo** que toma la reversión, y la reversión lo mantiene hasta el commit de `MV`: el devengo de la cadena nueva no puede empezar antes de que la vieja esté revertida.
- **`uq_commissions_detail_user` es parcial desde `V59`** y conserva su nombre, de modo que la traducción por nombre de restricción sigue valiendo y la persona que está en las dos cadenas inserta su comisión nueva sin chocar con la revertida.
- **La auditoría del desenlace es un `CREATE`** otra vez: la fila se borró y vuelve a nacer.

`CommissionAccrualIT` gana `CA-CM-304` —corrigiendo por la ruta de `RF-MV-016`— y `CA-CM-305`, preparando por SQL una línea con su cadena revertida y sin desenlace —lo que deja una corrección cuyo aviso se perdió— y cerrando: el barrido la devenga. **Sin dobles del escuchador**: un `@MockitoSpyBean` crea otro contexto de Spring y ya agotó las conexiones de la suite (`tasks.md` §3.1).

## 15. La directa de la tasa de rol — enmienda del 05-10-2026

`RN-CM-050`. `CommissionAccrualService.ventaPropia` deja de leer `ProductCatalog.directCommissionOf` —que se retira— y lee la **tasa de rol viva del vendedor sobre el producto** por su rol vendedor (`commission_rates`, `product_id` y `role_id`): si declara directa, la devuelve como `ResolvedRate` con `source = DIRECTA` y `rateId` **la tasa**; si no, devuelve lo que resolvió `RF-CM-005`. Una lectura nueva en el repositorio de tasas, `directOf(productId, roleId)`, dentro del mismo módulo. **Cambia `CA-CM-267`**: el `DIRECTOR` sin tasa de rol sobre el producto ya no tiene directa, y `RF-CM-005` tampoco le da tasa, así que su nivel queda **sin tasa** como cualquier otro. `CommissionAccrualIT` reescribe la siembra de la directa —de `products` a `commission_rates`— y gana `CA-CM-328` a `CA-CM-330`.

## 16. La comisión y el lote en centésimas — enmienda del 05-10-2026

[`ADR-006`](../../../architecture/ADR-006-importes-en-unidades-minimas.md) y [`requirements/cm.md`](../../../requirements/cm.md) v0.31.0 §7.2 y §7.3.

**Esquema — `V65` (`RF-MV-001` `T-41`).** `commissions.fixed_amount` y `commission_amount` (`numeric(14,4)`), `commissions.unit_price` (`numeric(14,2)`) y `commission_batches.total_amount` (`numeric(14,4)`) pasan a `bigint` con `round(col * 100)`. **El total del lote se recalcula en la misma migración** como suma de las comisiones ya convertidas de cada lote, en lugar de convertirlo por su cuenta. Redondear cada comisión y redondear la suma no dan el mismo número, y el invariante «el total es la suma de sus comisiones» tiene que salir intacto de la migración. `commissions.percentage` no cambia.

**El redondeo es explícito y vive en el dominio.** `ChainCommissionCalculator` sigue calculando con `ESCALA` cuatro, y **la fila de comisión se construye con el importe ya redondeado a dos con `HALF_UP`**. El convertidor también redondearía, pero el ADR lo deja como red y no como regla, y aquí hay una razón más: **el total del lote se suma en memoria** antes de escribirse. Si se sumaran importes de cuatro decimales y el convertidor redondeara después, el total no sería la suma de las filas guardadas, y `CA-CM-337` fallaría por un céntimo. La regla de rechazo de `RN-CM-019` (la cadena no pasa del importe de la línea) **se compara antes de redondear**, como hoy: redondear primero podría colar una cadena que se pasa por menos de medio céntimo.

**SQL nativo.** `JpaCommissionAccrualRepository`, `JpaCommissionAccrualQueryRepository`, `JpaCommissionBatchRepository`, `JpaCommissionBatchQueryRepository`, `JpaCommissionClosingRepository` y `JpaCommissionResolutionRepository` se revisan uno a uno. La suma del lote (`total_amount = total_amount + :importe`) **vincula el importe en centésimas**, y las lecturas convierten al mapear. Que la suma se haga en SQL sobre enteros es exacto, y por eso se queda en SQL.

**`CA-CM-166` pierde su provocación** (`tasks.md` §3.1: una línea de cien mil millones al 10 % cuya comisión no cabía en `numeric(14,4)`). `tasks.md` §10 lo declara y propone cómo rehacerla.

`CommissionAccrualIT` gana `CA-CM-336` y `CA-CM-337`. Este último recorre devengo, cierre y pago por la API, y compara el abono del libro con el total del lote **en centésimas**.

## 17. La cadena vieja se borra — enmienda del 07-10-2026

`RN-CM-047` enmendada. **Ningún componente del devengo cambia**: lo que §14 explicaba sigue valiendo, con una salvedad — `uq_commissions_detail_user` **ya no es parcial** (`V80`, [`RF-CM-024`](../024-revertir-comisiones-de-linea/plan.md) §12), y no necesita serlo: la comisión vieja de quien está en las dos cadenas ya no existe cuando se inserta la nueva. `CA-CM-346` se prueba en `ReleaseCommissionedLineIT`, y `CA-CM-305` prepara su línea **borrando** la cadena por SQL.
