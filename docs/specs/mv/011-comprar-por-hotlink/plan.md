# PLAN — `RF-MV-011` Comprar un producto por el hotlink de un vendedor

| Campo | Valor |
|---|---|
| Requerimiento | `RF-MV-011` |
| Especificación | [`spec.md`](spec.md) |
| `spec.md` aprobada el | 24-09-2026 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 24-09-2026 |

!!! info "Qué va en este documento"

    **Cómo se construye.** Las decisiones técnicas que la especificación deliberadamente no toma.

    **Prueba de pertenencia:** si al negocio no le importa ni lo entendería, va aquí.

---

## 1. Enfoque

**Es `RF-MV-002` con el vendedor puesto desde fuera.** El registro de la venta no se reescribe: se reutiliza entero y se le entrega la atribución ya decidida, que es lo que `SaleAttribution.delEnlace` **ya devuelve** desde el 23-09-2026 y que hasta hoy **no llamaba nadie**. Este requerimiento es quien la llama.

**Y trae la segunda escritura publicada del sistema.** `MV` tiene que crear el vínculo en `client_sellers`, que es de `SP`. Es la misma cuestión que **D-26** resolvió para conceder el nivel comprado, y se resuelve igual y por lo mismo: `SP` publica una operación, `MV` la invoca dentro de su transacción. `RF-MV-013` ya da por hecho este puerto y lo cita como «reutilizado de `RF-MV-011`».

## 2. Cambios de esquema

**Ninguna tabla.** `client_sellers` la creó `RF-SP-059` con `V20`, con su `origin` y su `first_movement_id`; las columnas de la venta las dejó `V14`. Este requerimiento solo **inserta**.

**Una migración, y es de catálogo**: `V41` siembra `products:buy-by-hotlink` (`RN-SEG-015`) y lo reparte. El reparto es el de una compra propia: a quien compra. Se asocia **por tipo de rol**, como hizo `V31` con la familia de alcance propio, y no por lista — un rol de consumidor nuevo tiene que poder comprar sin tocar una migración.

## 3. Componentes afectados

| Capa | Componente | Estado | Qué hace |
|---|---|---|---|
| `movements/interfaces` | `HotlinkPurchaseController` | **Nuevo** | `POST /api/v1/hotlinks/{username}/{code}/purchases` |
| `movements/application` | `HotlinkPurchaseRequest` | **Nuevo** | El método de pago, y nada más |
| `movements/domain/service` | `BuyByHotlinkService` | **Nuevo** | Resuelve el enlace, rechaza la compra a sí mismo, registra la venta y pide el vínculo |
| `movements/domain/service` | `SaleAttribution` | **Reutilizado sin tocar** | `delEnlace` ya existe y ya decide; este servicio es su primer cliente |
| `system/users/application` | `ClientSellerBond` | **Nuevo — puerto publicado** | «vincula a este cliente con este vendedor por esta venta» |
| `system/users/domain/service` | `PublishedClientSellerBond` | **Nuevo** | La implementación en `SP`, `MANDATORY`, con su asiento de auditoría |
| `system/users/domain/repository` | `ClientSellerRepository` | **Modificado** | Gana `attachByHotlink`; hoy solo sabe `registerPrincipal` |
| `products/application` | `PublicSellerLookup`, `ProductCatalog` | **Reutilizados** | La resolución del enlace es la de `RF-PM-008`, no una segunda |

## 4. Contrato de API

**`POST /api/v1/hotlinks/{username}/{code}/purchases`**, que es la ruta del enlace más `/purchases`. Cuerpo y respuesta son los de `RF-MV-002`: el método de pago entra, la venta sale.

**Bajo `/hotlinks` y no bajo `/products`**, aunque quien responde sea `MV`: el recurso sobre el que se actúa **es el enlace**, y es lo único que distingue esta compra de la ordinaria. `RF-MV-013` fija la misma forma para el paquete.

**`201` y no `200`**: crea una venta, como `RF-MV-002`.

## 5. Autorización

**`products:buy-by-hotlink`**, nacido con este requerimiento. `RN-SEG-015` no admite «basta con el token», y la ficha del 16-09-2026 —que decía «Autenticado, sin permiso»— es anterior a esa regla; §8 la enmienda.

**Un permiso propio y no `products:buy`**, aunque las dos sean compras del mismo producto. Son dos puertas con **atribuciones distintas**: por una la venta se le acredita a quien ya vendía a ese cliente, y por la otra a quien reparte el enlace. Quien administre roles tiene que poder abrir una sin abrir la otra, y con un solo permiso esa decisión no se puede expresar. Es el mismo criterio que `RN-SEG-014` aplicó al separar `users:revoke-membership` de `users:assign-membership`.

## 6. Auditoría

**Dos asientos y no uno**, cada uno en su módulo: la venta en `MV` como cualquier otra, y el vínculo en `SP` sobre `client_sellers`, con la correlación compartida. Un solo asiento obligaría a uno de los dos módulos a describir lo que hizo el otro.

**El asiento del vínculo se escribe solo cuando el vínculo nace.** Repetir una compra por el mismo enlace no deja rastro de vínculo, porque no hubo ninguno: auditar un `INSERT` que no insertó nada llenaría el registro de hechos que no ocurrieron.

## 7. Transaccionalidad

**Una transacción**, la del caso de uso de `MV`. La escritura publicada se une a ella con `MANDATORY`, igual que `MembershipGrant` y por el mismo motivo: si el vínculo falla, la venta no queda; y si la venta falla, el vínculo tampoco.

**La idempotencia la da el esquema y no una comprobación previa.** La pareja `(client_id, seller_id)` es la clave primaria de `client_sellers`, de modo que el segundo intento no crea nada y `ON CONFLICT DO NOTHING` lo absorbe. Comprobar antes de insertar es una carrera: dos compras simultáneas por el mismo enlace leerían las dos una tabla sin la fila.

**`first_movement_id` se escribe solo al crear.** Si el vínculo ya estaba, conserva la venta que lo creó — es su significado: la primera, no la última.

## 8. Impacto sobre otros módulos

| Documento | Enmienda |
|---|---|
| `requirements/mv.md` | La ficha de `RF-MV-011` pasa de «Autenticado, sin permiso» a **`products:buy-by-hotlink`** (`RN-SEG-015`), y de `Pendiente` a `Tasks en revisión`. `RN-MV-025` gana la nota de que el vínculo lo escribe `SP` |
| `security.md` | Nace `products:buy-by-hotlink` en §4.4 y el catálogo sube en uno |
| `architecture.md` | §15.2.1 gana **la segunda escritura publicada**: `ClientSellerBond`. Las cuatro reglas de §15.2 no se retocan |
| `requirements.md` | La fila de `RF-MV-011` en la matriz |

**`SP` no gana ningún requerimiento por publicar el puerto**, por lo mismo que con `MembershipGrant`: ningún actor pide «publicar una interfaz».

## 9. Alternativas consideradas

| Alternativa | Por qué no |
|---|---|
| **Que el vendedor viaje en el cuerpo** de la compra ordinaria | Convierte la atribución en algo que el cliente **declara** en lugar de algo que el enlace **prueba**. Cualquiera podría acreditarle una venta a cualquiera |
| **Que `MV` escriba `client_sellers`** | Contradice **D-26**. Qué significa vincular —quién es principal, qué no se toca— es regla de `SP`, y una segunda definición en `MV` divergiría sin fallar |
| **Reutilizar `products:buy`** | Deja sin expresar la decisión de abrir una puerta y no la otra (§5) |
| **Comprobar el vínculo antes de insertar** | Es una carrera, y el esquema ya lo resuelve (§7) |
| **Que la compra convierta al dueño del enlace en principal** | `RN-SP-049`: el principal es quien **registró**, y es inmutable. Moverlo reescribiría la historia comercial con cada compra |

## 10. Riesgos

| Riesgo | Cómo se ataja |
|---|---|
| Que la venta quede atribuida al principal —el fallo que hoy se ve— | `CA-MV-189`, que compra con un cliente que **ya tiene** principal y mira la línea |
| Que el vínculo se cree pero la venta no, o al revés | `MANDATORY` y una sola transacción (§7) |
| Que repetir la compra mueva el `first_movement_id` | `CA-MV-193`, que compra dos veces y mira que siga el de la primera |
| Que la compra mueva el principal | `CA-MV-191`, que lo mira **después** de comprar |
| Que alguien se acredite ventas por su propio enlace | `EX-003` y `CA-MV-194` |

## 11. Estrategia de prueba

**El escenario que importa se siembra con un cliente que YA tiene principal**, porque el defecto que este requerimiento corrige solo aparece ahí: con un cliente sin vendedores, atribuir por enlace y atribuir por `client_sellers` dan lo mismo y una prueba mal montada pasaría con el código viejo.

| Qué | Cómo |
|---|---|
| La atribución | Integración: cliente con principal `A`, compra por el enlace de `B`, la línea queda con `B` |
| El vínculo y su primera venta | La misma prueba, mirando `client_sellers` |
| Que el principal no se mueva | La misma prueba, mirando la fila `REGISTRO` antes y después |
| La idempotencia | Dos compras seguidas; un vínculo y el `first_movement_id` de la primera |
| La concurrencia | Dos compras simultáneas por el mismo enlace con el arnés del proyecto: ninguna `500`, un solo vínculo |
| El rechazo a sí mismo | `422`, y **cero** filas nuevas en `movements` y en `client_sellers` |
| El `404` único | Vendedor inexistente y producto inexistente devuelven **el mismo cuerpo** |
