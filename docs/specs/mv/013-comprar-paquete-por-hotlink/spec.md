# SPEC — `RF-MV-013` Comprar un paquete por el hotlink de un vendedor

| Campo | Valor |
|---|---|
| Requerimiento | `RF-MV-013` |
| Módulo | `MV` — Movimientos |
| Versión | 0.1.0 |
| Estado | **Aprobada** |
| Autor | Responsable técnico |
| Aprobada por | Responsable del proyecto |
| Fecha de aprobación | 16-09-2026 |

!!! info "Qué va en este documento"

    **Qué debe pasar, y por qué.** Nada más.

    **Prueba de pertenencia:** si un cambio de tecnología lo invalidaría, no pertenece aquí — va a `plan.md`. No se nombran tablas, clases, endpoints ni librerías.

!!! abstract "Esta especificación es a `RF-MV-012` lo que `RF-MV-011` es a `RF-MV-002`"

    La venta es **exactamente la misma** que la de [`RF-MV-012`](../012-comprar-paquete/spec.md): el paquete se compra entero, uno, solo y tal como está hoy (`RN-MV-028`), con una línea por producto y su rebaja congelada. Sus doce criterios, sus ocho excepciones y sus casos límite **valen tal cual**.

    Lo que cambia son **dos cosas, y solo dos** — las mismas que `RF-MV-011` introduce sobre la compra propia de un producto (`requirements/mv.md` §4.1; su tripleta está pendiente):

    1. **El paquete llega por el enlace**, no por su identificador.
    2. **El vendedor de cada línea es el dueño del enlace**, no el superior de quien compra.

    Y deja **una huella** que la compra propia no deja: el vínculo entre el cliente y ese vendedor.

---

## 1. Objetivo

Que un cliente **con cuenta** compre el paquete que le llegó por el enlace de un vendedor, y que **ese** vendedor cobre la venta aunque no sea su agente.

## 2. Contexto

**`RF-PM-026` publica el hotlink de un paquete desde el 15-09-2026, y hasta hoy no llevaba a ninguna parte.** Quien lo abría veía el paquete, su precio, su ahorro y sus productos, y no tenía forma de comprarlo: la única compra por enlace que existía era la de un producto suelto (`RF-MV-011`).

**Es el mismo círculo que se cerró con los productos, cerrado ahora con los paquetes.** `RF-PM-008` publica el enlace del producto, `RF-SP-045` registra por él a quien no tiene cuenta y `RF-MV-011` lo compra cuando ya la tiene. Para paquetes existían las dos primeras piezas —el enlace y el registro— y faltaba la tercera.

## 3. Actores

| Actor | Rol en esta funcionalidad |
|---|---|
| Cliente | Compra para sí mismo, como en `RF-MV-012`. **Puede ser cliente de otro agente** y aun así comprar por este enlace |
| Vendedor del enlace | **No interviene**: no pide nada y no se entera en el momento. Cobra la venta, y queda **vinculado** al cliente |

## 4. Las dos diferencias

### 4.1 El paquete llega por el enlace

**Se identifica por nombre de usuario del vendedor y código del paquete**, resueltos con **las mismas reglas que `RF-PM-026`**: solo responde un paquete **activo**, de alcance hotlink, **ofrecible hoy** y **dentro de su vigencia**.

**Y todo lo que no procede responde lo mismo: no encontrado.** El enlace que no existe, el vendedor que no lo es, el paquete que no se publica por hotlink y el que hoy no se puede ofrecer son **indistinguibles desde fuera**. Es la uniformidad que `RF-PM-026` ya eligió, y por el mismo motivo: un enlace público que distinga «no existe» de «existe pero no se ofrece» **enumera el catálogo** a quien lo pruebe.

!!! warning "Esto se aparta de `RF-MV-012`, donde el rechazo SÍ dice qué falla"

    Ahí el cliente ve el paquete en **su** oferta y el detalle del rechazo le ayuda; aquí llega de fuera, con un enlace que alguien le pasó, y el mismo detalle sería **información sobre el catálogo de otro**.

    La diferencia no es de rigor sino de **quién pregunta**, y es la misma que `RF-MV-011` ya aceptó.

### 4.2 El vendedor de cada línea es el dueño del enlace

**Siempre** (`RN-MV-025`). También cuando quien compra es cliente de otro agente, y también cuando el dueño del enlace **es** su agente —en ese caso el resultado coincide con `RF-MV-012` y no hay nada que distinguir—.

**El enlace es la prueba de quién trajo esa venta**, y por eso manda sobre la estructura comercial. Y se congela como cualquier otro vendedor (`RN-MV-003`): reasignar mañana no cambia quién ganó por lo vendido hoy.

**Lo que NO ocurre nunca es que esta compra cambie el agente principal del cliente.** Lo único que cambia de manos es **esta venta**.

### 4.3 Y deja una huella: el vínculo

En la misma transacción nace —si no existía— el **vínculo** entre el cliente y ese vendedor (`RN-SP-049`), con **esta venta** como el movimiento que lo originó y origen `HOTLINK`.

**Es lo que permite que un cliente tenga varios vendedores sin tener varios superiores**: el vínculo dice «le vendió», no manda nada y no se cierra. El vendedor del enlace ve la venta en sus movimientos propios (`RF-MV-008`); **al cliente no lo ve en su equipo**.

## 5. Reglas de negocio aplicables

Las de [`RF-MV-012`](../012-comprar-paquete/spec.md) §5, **más**:

| ID | Regla | Origen |
|---|---|---|
| `RN-MV-025` | La compra por hotlink se atribuye al dueño del enlace, y lo vincula al cliente | `requirements/mv.md` §5.1 |
| `RN-PM-021` | Qué publica el hotlink | `requirements/pm.md` §5.1 |
| `RN-PM-022` | Qué responde cuando no procede | `requirements/pm.md` §5.1 |
| `RN-PM-043` | El hotlink publica el paquete con su cuenta hecha | `requirements/pm.md` §5.1 |
| `RN-SP-049` | Un cliente tiene un agente principal y varios vendedores vinculados | `requirements/sp.md` §5.1 |

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción | Restricción de negocio |
|---|---|---|---|
| Vendedor del enlace | Sí | **Por nombre de usuario** | Debe existir, portar el rol vendedor y tener ese paquete publicado por hotlink |
| Paquete | Sí | **Por su código** | Las condiciones de `RF-PM-026`, comprobadas otra vez al registrar |
| Método de pago | **Condicional** | Con qué se paga | Igual que en `RF-MV-012` (`RN-MV-022`) |

**El identificador del paquete no se admite**, aunque quien compra podría conocerlo: la puerta es el enlace, y aceptar las dos formas daría **dos caminos con dos modelos de atribución** para la misma venta.

### 6.2 Salida

La de `RF-MV-012`, **sin cambio**: la venta con su paquete, sus líneas y sus descuentos explicados, y **sin el vendedor** —que aquí, igual que allí, el cliente no elige y no le corresponde ver—.

**El vínculo no viaja en la respuesta.** Es un efecto de la compra y no parte de ella; quien quiera saber con qué vendedores está vinculado lo pregunta a `SP`.

## 7. Precondiciones y postcondiciones

**Precondiciones**

- Las de `RF-MV-012`, y además: el enlace resuelve a un vendedor y a un paquete suyo publicado por hotlink.
- **Quien compra no es el dueño del enlace** (§10 · `EX-009`).

**Postcondiciones**

- Las de `RF-MV-012`, con **el vendedor del enlace congelado en cada línea**.
- **Existe el vínculo** cliente-vendedor (`RN-SP-049`); si ya existía, no se duplica y no se toca.
- **El agente principal del cliente no ha cambiado.**

## 8. Flujo principal

Es el de `RF-MV-012` con **dos pasos distintos al principio y uno nuevo al final**:

1. El actor abre la compra del enlace, indicando con qué paga.
2. El sistema resuelve **quién es** por su credencial y comprueba que puede comprar.
3. **El sistema resuelve el enlace**: el vendedor por su nombre de usuario y el paquete por su código, con las reglas de `RF-PM-026`. Lo que no procede es **no encontrado**.
4. El sistema comprueba que **quien compra no es el dueño del enlace**.
5. …*idéntico a `RF-MV-012` §8, pasos 4 a 10*, con **el vendedor del enlace** en cada línea.
6. **El sistema crea el vínculo** cliente-vendedor si no existía, con esta venta como origen.
7. El sistema devuelve la venta.

**El vínculo se crea después de registrar la venta y en la misma transacción**, porque necesita el identificador de la venta para decir cuál lo originó. Si la venta se cae, el vínculo no existe: no hubo compra que vincular.

## 9. Flujos alternativos

### FA-001 — El dueño del enlace ES el agente del cliente

1. La venta se registra igual, atribuida a él.
2. **El vínculo ya existía** —lo creó su registro— y no se toca.
3. El resultado es indistinguible de una compra por `RF-MV-012`, y eso es correcto: es la misma persona cobrando lo mismo.

### FA-002 — El cliente ya compró antes por este mismo enlace

1. La venta se registra con normalidad.
2. **El vínculo no se duplica** y sigue apuntando a la **primera** venta que lo originó. El vínculo dice desde cuándo, no cuántas veces.

### FA-003 — El paquete deja de ofrecerse mientras el enlace circula

1. La compra responde **no encontrado**, como todo lo que no procede en este camino (§4.1).
2. El enlace sigue existiendo y volverá a funcionar si el paquete vuelve a ofrecerse. **Un enlace no se invalida**: lo que se comprueba es el paquete, cada vez.

## 10. Excepciones

Las ocho de `RF-MV-012`, **con dos salvedades y una añadida**:

- **`EX-001`, `EX-002` y `EX-003` se funden en una sola respuesta: no encontrado** (§4.1). El paquete que no existe, el que no se ofrece y el que no le corresponde a quien compra son indistinguibles desde fuera.
- **`EX-004` a `EX-008` valen tal cual** y siguen diciendo qué falla: un producto caído, un upgrade que baja, una cuenta que no opera o un método de pago que no cuadra **no son información sobre el catálogo de otro**, sino sobre la operación que el actor acaba de pedir.

### EX-009 — Comprarse a uno mismo por el propio hotlink

**Condición:** quien compra es el dueño del enlace.
**Respuesta del sistema:** rechaza. **Un vendedor no es su propio cliente**, y admitirlo dejaría que cualquiera se atribuyera sus propias compras — que es justo lo que `RN-MV-003` evita al deducir el vendedor en lugar de recibirlo.

## 11. Validaciones

| ID | Regla | Mensaje |
|---|---|---|
| — | Ninguna propia | — |

**Ninguna, y es coherente con §4.1**: lo que en `RF-MV-012` sería una validación de forma —falta el paquete— aquí es parte de la ruta, y lo que no resuelve responde **no encontrado** en lugar de un error de petición.

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-MV-061` | Un cliente compra por el enlace de **otro** vendedor y la venta queda atribuida **al dueño del enlace** en todas sus líneas |
| `CA-MV-062` | **Su agente principal no cambia**, y sigue siendo el de antes |
| `CA-MV-063` | Nace el **vínculo** cliente-vendedor con esta venta como origen y origen `HOTLINK`; **comprar dos veces no lo duplica** |
| `CA-MV-064` | El enlace inexistente, el paquete que no se ofrece y el que no le corresponde responden **el mismo no encontrado** |
| `CA-MV-065` | Comprarse a uno mismo por el propio hotlink **se rechaza** |
| `CA-MV-066` | La venta es **indistinguible** de la de `RF-MV-012` salvo por a quién se atribuye |
| `CA-MV-067` | Un producto caído del paquete **sigue diciendo cuál es**: la uniformidad del `404` no alcanza a lo que no es del catálogo ajeno |

## 13. Casos límite

- **El vendedor del enlace se desactiva entre que el cliente lo abre y compra:** responde **no encontrado**, porque el hotlink deja de resolver. Es lo mismo que hace `RF-PM-026`.
- **El cliente está en `FTD_PENDIENTE`:** se rechaza diciendo que le falta el depósito (`EX-006`), y **no** con el no encontrado: es información sobre su propia cuenta.
- **Dos compras simultáneas por el mismo enlace del mismo cliente:** las dos ventas se registran y **el vínculo se crea una sola vez**. Que no se duplique no puede depender de que nadie pulse dos veces.
- **El vendedor del enlace es el agente principal y además ya estaba vinculado:** nada nuevo ocurre; ver `FA-001`.

## 14. Preguntas abiertas

| # | Pregunta | Responsable | Estado |
|---|---|---|---|
| — | Ninguna | — | — |

**Queda declarado lo mismo que en `RF-MV-012`**: sobre qué importe comisiona una línea rebajada lo decide `CM`, y esta venta guarda las dos cifras.

## 15. Control de cambios

| Versión | Fecha | Cambio | Responsable |
|---|---|---|---|
| 0.1.0 | 16-09-2026 | Redacción inicial, sin preguntas abiertas. **Se escribe por diferencias con `RF-MV-012`**, y las diferencias son las dos que `RF-MV-011` ya introdujo sobre la compra propia de un producto: el paquete llega **por el enlace** y el vendedor de cada línea es **su dueño** (`RN-MV-025`), más la huella que la compra propia no deja — el **vínculo** de `RN-SP-049`. **Lo que se hereda sin repetir** es todo lo que el paquete añade: entero, uno, solo y tal como está hoy (`RN-MV-028`). **La decisión que este documento fija y aquel no tenía** es la del `404` uniforme (§4.1): aquí el rechazo del catálogo **no dice qué falla**, porque quien pregunta llega de fuera y el detalle sería información sobre el catálogo de otro — pero **la uniformidad no alcanza** a lo que es de la operación del propio actor, y `CA-MV-067` lo fija. Siete criterios nuevos, `CA-MV-061` a `CA-MV-067`. | Responsable del proyecto |
