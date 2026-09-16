# TASKS — `RF-MV-012` Comprar un paquete para uno mismo

| Campo | Valor |
|---|---|
| Requerimiento | `RF-MV-012` |
| Plan | [`plan.md`](plan.md), aprobado el 16-09-2026 |
| Versión | 0.1.0 |
| Estado | **En revisión** |
| Autor | Responsable técnico |
| Aprobadas por | Pendiente |
| Fecha de aprobación | Pendiente |
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
| `T-01` | **`PackageCatalog`** en `products/application`: el puerto con la lectura de compra —ofrecibilidad con su motivo, y los productos con su descuento declarado— | — | La interfaz compila sin arrastrar nada de `domain`; la suite de `PM` sigue en verde **sin cambios** | **Pendiente** |
| `T-02` | **`PublishedPackageCatalog`**: lo implementa reutilizando la sentencia y el objeto de ofrecibilidad que ya usa `RF-PM-019` | `T-01` | Un paquete vencido, uno inactivo, uno con un producto retirado y uno correcto devuelven lo mismo que el detalle del paquete publica | **Pendiente** |
| `T-03` | `BuyPackageRequest`: **solo el método de pago**, condicional como en `RF-MV-001` | — | Un `packageId`, una cantidad o una fecha en el cuerpo **no tienen dónde caer**: no existen en la representación | **Pendiente** |
| `T-04` | `BuyPackageService`: el orden de `spec.md` §8 — actor, paquete, productos, oferta, nivel, moneda, copia y congelado, importes, método, alta | `T-01`, `T-03` | Un paquete que no se ofrece **no llega a resolver productos**; un producto fuera de la oferta rechaza **antes** de escribir nada | **Pendiente** |
| `T-05` | Las líneas: una por producto, con nombre y descripción **copiados**, el vendedor del comprador y **la rebaja congelada** con `LineDiscount` | `T-04` | `CA-MV-052`: cada línea trae lo copiado y su rebaja explicada; el dinero por unidad coincide con la fórmula de `RN-PM-036` | **Pendiente** |
| `T-06` | El alta: `Movement.registrar` **con el paquete en la cabecera**, y el mismo repositorio que `RF-MV-001` | `T-05` | `CA-MV-059`: la venta es indistinguible de cualquier otra salvo por su paquete. `CA-MV-060`: la auditoría lleva paquete, líneas y rebajas | **Pendiente** |
| `T-07` | `PackagePurchaseController`: `POST /api/v1/packages/{id}/purchases`, **sin `@PreAuthorize`**, con `Location` al movimiento propio | `T-04` | Documentado, y la ruta **entra en la lista blanca** de `EndpointPermissionsIT` | **Pendiente** |
| `T-08` | `BuyPackageIT`: los doce criterios, `CA-MV-049` a `CA-MV-060` | `T-07` | Incluida la que importa — `CA-MV-050`, **comparando con el `price` que el catálogo publica** | **Pendiente** |
| `T-09` | Contrato OpenAPI y matriz: `RF-MV-012` pasa de `Pendiente` a `En desarrollo` | `T-08` | `docs/api/openapi.*` salen modificados y la prosa dice **qué no admite** el cuerpo: ni productos, ni cantidad, ni precio, ni descuento | **Pendiente** |

**`T-02` reutiliza el objeto de ofrecibilidad y no lo reescribe**, y es la tarea donde se decide si este requerimiento envejece bien: si copia el predicado, el día que `PM` añada una condición —otra fecha, otro alcance— la venta seguirá con el viejo y **nada fallará**.

**`T-05` no vuelve a probar el redondeo.** Lo cubren las unitarias de `LineDiscount`, escritas con `V14`; lo que aquí se verifica es que **la rebaja que se congela es la que el paquete declara**, que es otra cosa.

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

---

## 4. Bloqueos

**`RF-SP-045` — el registro de clientes por enlace. Sin código.** Es el mismo bloqueo que arrastran `RF-MV-002` y `RF-MV-011`, y aquí pesa igual: **esta operación solo la pueden usar clientes**, y sin personas colgadas de un vendedor no hay camino feliz que probar con un cliente de verdad.

**Lo que NO bloquea, y conviene decirlo**: el registro de la venta, el congelado del descuento y el esquema **ya existen** (`V14`, 16-09-2026). Cuando `RF-SP-045` llegue, lo que falta aquí es el caso de uso y su puerta, no la forma de lo que escriben.

**`RF-MV-002` tampoco bloquea**: esta operación no reutiliza su servicio (`plan.md` §3.3) y solo hereda sus **decisiones**, que ya están escritas.

---

## 5. Definición de terminado

- [ ] Las nueve tareas en `Hecha`, con su verificación pasando.
- [ ] **`CA-MV-050` pasando**, que es la que garantiza que el cliente paga lo que vio.
- [ ] **`CA-MV-054` pasando**, que es la que garantiza que un paquete roto no se vende a medias.
- [ ] La suite de `PM` en verde **sin cambios**: lo que se le añade es una interfaz nueva, no una modificación.
- [ ] El contrato OpenAPI coincide con el comportamiento real, **también en la prosa**.
- [ ] La matriz de trazabilidad al día.
