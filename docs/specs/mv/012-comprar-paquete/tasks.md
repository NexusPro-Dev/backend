# TASKS — `RF-MV-012` Comprar un paquete para uno mismo

| Campo | Valor |
|---|---|
| Requerimiento | `RF-MV-012` |
| Plan | [`plan.md`](plan.md), aprobado el 16-09-2026 |
| Versión | 0.5.0 |
| Estado | **Aprobadas** |
| Autor | Responsable técnico |
| Aprobadas por | Responsable del proyecto |
| Fecha de aprobación | 17-09-2026 |
| Enmendadas | 01-10-2026 — `T-10` por **la tarjeta por Stripe** (§6) · 03-10-2026 — `T-11` por **el upgrade escalonado** (§7) |
| Enmendadas | 09-10-2026 — `T-12` y `T-13` por **la oficina de cada línea** (§1.1) |
| Issue | Pendiente de crear |
| Rama | `feature/venta-de-productos` |

!!! info "Qué va en este documento"

    **En qué pasos se construye** lo que `plan.md` decidió, con su dependencia y su verificación. Ninguna tarea se da por `Hecha` sin que su verificación pase.

!!! abstract "Nueve tareas, y ninguna toca el esquema"

    `V14` dejó puestas las columnas el mismo día en que se decidió la compra. Lo que queda es **la lectura que `PM` publica**, **el caso de uso que arma las líneas** y **la puerta por la que entra**.

---

## 1. Tareas

**Estados:** `Pendiente` · `En curso` · `Hecha` · `Bloqueada`.

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-01` | **`PackageCatalog`** en `products/application`: el puerto con la lectura de compra —ofrecibilidad con su motivo, y los productos con su descuento declarado— | — | La interfaz compila sin arrastrar nada de `domain`; la suite de `PM` sigue en verde **sin cambios** | **Hecha** |
| `T-02` | **`PublishedPackageCatalog`**: lo implementa reutilizando la sentencia y el objeto de ofrecibilidad que ya usa `RF-PM-019` | `T-01` | Un paquete vencido, uno inactivo, uno con un producto retirado y uno correcto devuelven lo mismo que el detalle del paquete publica | **Hecha** |
| `T-03` | `BuyPackageRequest`: **solo el método de pago**, condicional como en `RF-MV-001` | — | Un `packageId`, una cantidad o una fecha en el cuerpo **no tienen dónde caer**: no existen en la representación | **Hecha** |
| `T-04` | `BuyPackageService`: el orden de `spec.md` §8 — actor, paquete, productos, oferta, nivel, moneda, copia y congelado, importes, método, alta | `T-01`, `T-03` | Un paquete que no se ofrece **no llega a resolver productos**; un producto fuera de la oferta rechaza **antes** de escribir nada | **Hecha** |
| `T-05` | Las líneas: una por producto, con nombre y descripción **copiados**, el vendedor del comprador y **la rebaja congelada** con `LineDiscount` | `T-04` | `CA-MV-052`: cada línea trae lo copiado y su rebaja explicada; el dinero por unidad coincide con la fórmula de `RN-PM-036` | **Hecha** |
| `T-06` | El alta: `Movement.registrar` **con el paquete en la cabecera**, y el mismo repositorio que `RF-MV-001` | `T-05` | `CA-MV-059`: la venta es indistinguible de cualquier otra salvo por su paquete. `CA-MV-060`: la auditoría lleva paquete, líneas y rebajas | **Hecha** |
| `T-07` | `PackagePurchaseController`: `POST /api/v1/packages/{code}/purchases`, **sin `@PreAuthorize`**, con `Location` al movimiento propio | `T-04` | Documentado, y la ruta **entra en la lista blanca** de `EndpointPermissionsIT` | **Hecha** |
| `T-08` | `BuyPackageIT`: los doce criterios, `CA-MV-049` a `CA-MV-060` | `T-07` | Incluida la que importa — `CA-MV-050`, **comparando con el `price` que el catálogo publica** | **Hecha** |
| `T-09` | Contrato OpenAPI y matriz: `RF-MV-012` pasa de `Pendiente` a `En desarrollo` | `T-08` | `docs/api/openapi.*` salen modificados y la prosa dice **qué no admite** el cuerpo: ni productos, ni cantidad, ni precio, ni descuento | **Hecha** |

**`T-02` reutiliza el objeto de ofrecibilidad y no lo reescribe**, y es la tarea donde se decide si este requerimiento envejece bien: si copia el predicado, el día que `PM` añada una condición —otra fecha, otro alcance— la venta seguirá con el viejo y **nada fallará**.

**`T-05` no vuelve a probar el redondeo.** Lo cubren las unitarias de `LineDiscount`, escritas con `V14`; lo que aquí se verifica es que **la rebaja que se congela es la que el paquete declara**, que es otra cosa.

**Construido el 17-09-2026, y tres cosas que el plan no decía y el código decidió**:

1. **`EX-004` es más estrecho de lo que `spec.md` §10 enumera, y `EX-002` más ancho.** Un producto **inactivo o retirado** lo rechaza `PM` al decidir la ofrecibilidad —`PackageOfferability` lo nombra por su código, y `T-02` exige reutilizar ese objeto y no reescribirlo—, de modo que llega como `EX-002` con el mensaje del catálogo. `EX-004` queda para el producto que existe, está activo y **no está en la oferta de esa persona** (un bot de alcance `HOTLINK` dentro de un paquete de tienda). Lo que `spec.md` §4.1 exige —rechazar la compra entera nombrando el producto— se cumple en los dos casos, y `CA-MV-054` lo prueba con los tres.
2. **Un fijo mayor que el precio de hoy se congela como el precio, y no se rechaza.** Es el hueco temporal de `RN-PM-037` —el precio bajó después de asociar— y `PM` lo cierra publicando el producto a cero; `LineDiscount` hacía lo contrario —lanzaba— y `CA-MV-050` obligó a alinearlo: lo pactado se guarda tal cual y lo cobrado es lo que vale. Una prueba de `MovementTest` cambió de sentido por esto.
3. **Las reglas de vender que no dependen de qué se vende salieron de `RegisterSaleService` a `SaleRules`** —la cuenta que opera, el upgrade que no baja, el método que cuadra con el importe, el tipo— y los dos casos de uso las llaman. No es una relajación de `plan.md` §3.3: el caso de uso sigue siendo propio; lo que se comparte son las comprobaciones que eran literalmente las mismas, para no duplicar la clase de defecto que no falla.

Y una cuarta, de `PM`: **`RN-PM-044` se movió de `GetOwnOfferService` a `PackageOfferability.correspondeA`**, porque desde hoy la responden dos lecturas —la oferta y la venta— y tienen que decir lo mismo. La suite de `PM` sigue en verde sin tocar una prueba.

**Enmienda del mismo día (v0.3.0): el paquete entra por su código.** `spec.md` v0.2.0 y `plan.md` v0.2.0 lo fijan; `T-01`, `T-02`, `T-07` y `T-08` se rehicieron en consecuencia —`PackageCatalog.storeSaleViewOf(code, buyerId)`, `ProductPackageQueryRepository.findDetailByCode` sin distinguir mayúsculas, la ruta `{code}` y `BuyPackageIT` comprando por código, también en minúsculas— y las nueve siguen en `Hecha`.

### 1.1 La oficina de cada línea — 09-10-2026

Enmienda de hecho (Art. I.7), `spec.md` 0.7.0 y `plan.md` 0.5.0 **antes** del código (`RN-MV-078`). El puerto, la columna y `MovementLine.teamId` son de `RF-MV-001` (`T-49` a `T-51`); aquí va el caso de uso propio.

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-12` | `BuyPackageService.registrar`: `teamAt(vendedor, ahora)` tras la atribución y antes de copiar; `copiar` pasa la oficina a `MovementLine.copiarDe` con rebajas; dependencia de `SellerTeamLookup` | `RF-MV-001` · `T-49`, `T-51` | La compra por enlace (`RF-MV-013`) la hereda sin código | **Hecha** — 10-10-2026 |
| `T-13` | `BuyPackageIT`: la oficina en todas las líneas, en lo guardado y en la instantánea; con varios vendedores, sin oficina; el cuerpo no trae `team` | `T-12` | `CA-MV-714` | **Hecha** — 10-10-2026 |

---

## 2. Orden de ejecución

`T-01` → `T-02` abren la frontera; `T-03` y `T-04` construyen el caso de uso; `T-05` y `T-06` producen la venta; `T-07` la publica; `T-08` y `T-09` la verifican y la documentan.

**`T-01` y `T-03` pueden ir a la vez**: una es de `PM` y la otra de `MV`, y no se tocan.

---

## 3. Cobertura de los criterios de aceptación

| Criterio | Tareas |
|---|---|
| `CA-MV-049` | `T-04`, `T-07`, `T-08` |
| `CA-MV-050`, `CA-MV-051` | `T-05`, `T-06`, `T-08` |
| `CA-MV-052`, `CA-MV-053` | `T-05`, `T-08` |
| `CA-MV-054` | `T-04`, `T-08` |
| `CA-MV-055`, `CA-MV-056` | `T-02`, `T-04`, `T-08` |
| `CA-MV-057` | `T-04`, `T-08` |
| `CA-MV-058` | `T-03`, `T-08` |
| `CA-MV-059`, `CA-MV-060` | `T-06`, `T-08` |
| `CA-MV-714` | `T-12`, `T-13` — 09-10-2026 |

---

## 4. Bloqueos

**`RF-SP-045` — el registro de clientes por enlace. Sin código.** Es el mismo bloqueo que arrastran `RF-MV-002` y `RF-MV-011`, y aquí pesa igual: **esta operación solo la pueden usar clientes**, y sin personas colgadas de un vendedor no hay camino feliz que probar con un cliente de verdad.

**Lo que NO bloquea, y conviene decirlo**: el registro de la venta, el congelado del descuento y el esquema **ya existen** (`V14`, 16-09-2026). Cuando `RF-SP-045` llegue, lo que falta aquí es el caso de uso y su puerta, no la forma de lo que escriben.

**`RF-MV-002` tampoco bloquea**: esta operación no reutiliza su servicio (`plan.md` §3.3) y solo hereda sus **decisiones**, que ya están escritas.

---

## 5. Definición de terminado

- [x] Las nueve tareas en `Hecha`, con su verificación pasando — `BuyPackageIT`, 16 pruebas.
- [x] **`CA-MV-050` pasando**, que es la que garantiza que el cliente paga lo que vio: compara `payableAmount` con el `price` que `GET /api/v1/products/available` publica para tres paquetes, uno de ellos donde el redondeo se ve (33 % de 49.99).
- [x] **`CA-MV-054` pasando**, que es la que garantiza que un paquete roto no se vende a medias: ni cabecera, ni línea, ni fila de auditoría.
- [x] La suite de `PM` en verde **sin cambios** en sus pruebas: lo que se le añade es `PackageCatalog` y su adaptador; `RN-PM-044` cambió de sitio, no de contenido.
- [x] El contrato OpenAPI coincide con el comportamiento real, **también en la prosa**: la `@Operation` dice qué no admite el cuerpo, y `BuyPackageRequest` solo declara `paymentMethodId`.
- [x] La matriz de trazabilidad al día: `RF-MV-012` en `En desarrollo`.

**Lo que sigue sin cumplirse para `Implementado`** es lo mismo que en todo el módulo: integrar a `main` con la definición de terminado de la constitución (§16), y el asunto de que `RF-MV-003` confirme lo vendido.

---

## 6. La tarjeta por Stripe — enmienda del 01-10-2026

Rama: `feature/stripe-tarjeta`. Después de las tareas de `RF-MV-040` que cada fila cita.

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-10` | La llamada a `CardPayment` y `cardCharge` en la respuesta; la prosa | `RF-MV-040` · `T-05` | | **Hecha** — 01-10-2026 |

Criterios: `CA-MV-472`.

**Construido el 01-10-2026** (issue [#161](https://github.com/NexusPro-Dev/backend/issues/161)). Los criterios se prueban en `CardPaymentIT` —o en la suite de la entrada, para las compras— y no en la suite que cada fila nombra.

## 7. El upgrade sube un escalón — enmienda del 03-10-2026

Por `plan.md` §13. **Sin tarea de código**: la comparación es la de `SaleRules.verificarQueSube`, que construye [`RF-MV-001`](../001-registrar-venta/tasks.md) en su enmienda del 03-10-2026.

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-11` | `BuyPackageIT`: un paquete cuyo upgrade **salta** —sembrado por SQL— se rechaza con `409` y **sin venta, pago ni auditoría**; uno de **un escalón** se registra | `RF-MV-001` · la tarea que enmienda `SaleRules.verificarQueSube` | `CA-MV-540`; `CA-MV-057` sigue en verde sin tocarla | **Hecha el 03-10-2026** |

Criterios: `CA-MV-540`.
