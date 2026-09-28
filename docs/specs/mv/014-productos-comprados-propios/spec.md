# SPEC — `RF-MV-014` Consultar los productos comprados propios

| Campo | Valor |
|---|---|
| Requerimiento | `RF-MV-014` |
| Módulo | `MV` — Movimientos |
| Versión | 0.6.0 |
| Estado | **Aprobada** |
| Autor | Responsable técnico |
| Aprobada por | Responsable del proyecto |
| Fecha de aprobación | 17-09-2026 |
| Enmendada | 21-09-2026 — exige **`movements:read-own-products`** (`RF-SP-062`, `RN-SEG-015`: autenticarse no autoriza nada); lo siembra `V31`; `CA-MV-109` deja de decir «sin permiso» |
| Enmendada | 22-09-2026 — **la línea entregada trae `couponUrl`** (`RN-MV-032`, `RN-PM-050`): el cupón del bot, resuelto, **solo si la entrega está hecha**. Es **el único sitio del sistema, fuera de administración, donde ese enlace se ve**. Ver §15 |
| Enmendada | 28-09-2026 — **el producto viaja como en la oferta, y el cupón pasa a ser uno más de sus enlaces**: `couponUrl` desaparece y el `CUPON_BOT` llega **dentro de los enlaces del producto**, con su tipo, con la misma condición de siempre —**solo si la línea está entregada**—. Ver el recuadro y §15 |

!!! warning "Enmendado el 23-09-2026 — responde por lo que se TIENE, y nace `CANCELADO`"

    `RN-MV-036` y `RN-SP-056` ([`requirements/mv.md`](../../../requirements/mv.md) v0.40.0). Hasta hoy esta consulta **reconstruía** la lista recorriendo las líneas de venta y **recalculaba** el «hasta cuándo» en cada petición; desde que la entrega escribe la posesión, esa fecha **está escrita** y es la que se escribió al entregar — editar el catálogo después ya no le mueve el vencimiento a nadie.

    **Los estados anteriores a la entrega no se mueven**, y conviene no leer esto como un cambio de fuente: `PENDIENTE_PAGO`, `RECHAZADO`, `ANULADO`, `RETENIDO` y `PENDIENTE_ACTIVACION` siguen siendo de la **línea**, porque lo que aún no se entregó no se tiene y no tiene fila que consultar.

    **Nace un octavo estado, `CANCELADO`**: lo que se tuvo y se dejó de tener **antes** de su fecha. Hoy lo produce un solo caso —una membresía **sustituida** por otra que se compró encima, cuya fila se cierra el día de la compra nueva—, y hasta ahora esa línea aparecía como `ACTIVO` hasta que pasara una fecha que ya no significaba nada. **Cancelar algo entregado sigue sin ser una operación del sistema**: `RF-MV-008` solo anula ventas `PENDIENTE`, que no entregaron nada. El estado queda definido para cuando esa operación exista; no se inventa aquí.

!!! warning "Enmendado el 28-09-2026 — los enlaces del producto, todos, y el cupón entre ellos"

    Por decisión del responsable del proyecto: «en consultar mis productos comprados quiero poder traer los links del producto; si es bot, traer el tipo `CUPON_BOT`» y, ya aclarado el orden de la activación, «que los enlaces solo vengan en los productos, consultar todos los links que tenga el producto». Son dos cambios, y el primero ya estaba construido sin estar escrito aquí.

    **(1) El producto viaja en la forma de la oferta** de `RF-PM-007`, leído **del catálogo de hoy**: tipo, descripción, portada, enlaces, destino, precio, moneda, conversión, vigencia, alcance, implementación y valoración. El nombre **con el que se compró** (`RN-MV-002`) sigue viajando, aparte. Se construyó el 28-09-2026 (PR #128) y es un cambio incompatible: hasta entonces el producto eran tres datos.

    **(2) El cupón del bot deja de ser un dato suelto de la fila y pasa a ser un enlace más del producto**, con su tipo `CUPON_BOT` junto al `VIDEO_PRESENTACION`. **Una sola lista de enlaces** y no una lista más un campo aparte: quien pinta lo comprado recorre los enlaces del producto y encuentra el cupón por su tipo, como encuentra el video. El campo aparte desaparece — es **otro cambio incompatible**, declarado.

    **Lo que no cambia es cuándo se ve** (`RN-MV-032`), y el responsable lo confirmó al preguntar qué pasa con el cupón que se añade después de vender: el cupón **se usa después de activar**, no para activar. Por eso la lista de enlaces **depende de la línea y no solo del producto**: la línea `ACTIVO` o `VENCIDO` trae **todos** los enlaces del producto; las de los otros estados traen **los mismos que la oferta**, sin el cupón aunque el producto lo declare. Dos compras del mismo bot, una entregada y otra pendiente, traen listas distintas — y es correcto.

    **Y se sigue leyendo de hoy**: si administración añade, corrige o quita el cupón después de vender, la siguiente consulta de una línea entregada trae el vigente, o ninguno.

    **(3) El mismo día nace un tercer tipo, `DESCARGA`** ([`requirements/pm.md`](../../../requirements/pm.md) v0.45.0, `RN-PM-048`, `RN-PM-050`), también a petición del responsable: dónde descarga quien compró lo que compró. **Es entrega y no material de venta**, como el cupón —enseñarlo antes de cobrar regalaría lo que se vende—, de modo que aquí sigue **exactamente la regla del cupón**: viaja entre los enlaces de la línea entregada y no en las otras. Por eso esta spec deja de hablar del cupón en singular y habla de **los enlaces de entrega**: lo que decide si un enlace se ve antes de la entrega es su tipo, y el tipo lo declara `PM`.

!!! info "Qué va en este documento"

    **Qué debe pasar, y por qué.** Nada más.

    **Prueba de pertenencia:** si un cambio de tecnología lo invalidaría, no pertenece aquí — va a `plan.md`. No se nombran tablas, clases, endpoints ni librerías.

---

!!! note "Enmienda de Art. I.7 — 21-09-2026, `RF-SP-062`"

    Esta operación exige **`movements:read-own-products`** desde el 21-09-2026, por `RF-SP-062` —**autenticarse no autoriza nada**, `RN-SEG-015` ([`security.md` §4.3](../../../security.md#43-reglas-de-negocio))—, por decisión del responsable del proyecto: «cada endpoint debe tener su propio permiso, ya que uso esto para saber qué vista o consulta mostrar en el front; no basta con solo tener el token». Hasta entonces se atendía con solo el token, y las líneas que abajo dicen «sin permiso» o «autenticado a secas» hablan de esa decisión original y se conservan como historia: el alcance sobre uno mismo sigue siendo exactamente el mismo, lo que cambia es que ahora tiene nombre. `V31` siembra el permiso y lo da a todo rol por su tipo.



## 1. Objetivo

Que cualquier persona autenticada vea **los productos que compró**, uno por uno, con **en qué estado está cada uno y hasta cuándo lo tiene**, sin abrir venta por venta.

---

## 2. Contexto

**Lo pide el responsable del proyecto el 17-09-2026** —«quiero tener un registro de yo como usuario los productos que he comprado»— el mismo día que se especifica confirmar el pago, y no es casualidad: hasta que existe `RF-MV-003`, «lo que compré» y «lo que tengo» son la misma lista vacía. Desde que confirmar entrega, dejan de serlo, y la pregunta que una persona se hace no es «¿qué movimientos tengo?» sino **«¿ya tengo el bot que pagué?»**.

**`RF-MV-008` no la responde, y no hay que estirarlo para que lo haga.** Aquel lista **movimientos** —una fila por venta, sin líneas— y su detalle abre **una** venta. Para saber qué productos tiene, la persona tendría que abrir cada venta y mirar cada línea; y si un mismo producto lo compró dos veces, sumarlo ella. Este requerimiento responde por **productos**, que es otra pregunta con otra fila.

**Se deriva del libro y no se guarda.** Una línea de venta ya dice qué se compró, cuándo, por cuántos días y —desde `RN-MV-030`— si se entregó y desde cuándo. Una tabla de «productos de la persona» sería una copia que hay que mantener en cada confirmación, cada autorización y cada vencimiento, y que se desincroniza sin que nada falle. Es el mismo criterio con el que `requirements/mv.md` §4.2 decidió que el saldo de puntos **se deriva y no se guarda**.

### 2.1 Un estado por producto, calculado de lo que ya está escrito

| Estado | Cuándo |
|---|---|
| `PENDIENTE_PAGO` | La venta está pendiente |
| `RECHAZADO` / `ANULADO` | La venta terminó así. **Aparecen**, porque «lo compré y no se pagó» es parte de la respuesta |
| `PENDIENTE_ACTIVACION` | La venta está confirmada y la línea es manual y **quien la compró no la ha activado** (`RN-MV-021`, `RN-MV-048`). Se activa por `RF-MV-010`. **Hasta el 28-09-2026 se llamaba `PENDIENTE_AUTORIZACION`** |
| `ACTIVO` | La línea está entregada y su vigencia no ha pasado —o no caduca (`RN-PM-015`)— |
| `VENCIDO` | La línea está entregada y su vigencia pasó |
| `RETENIDO` | La venta se confirmó y la línea no se entregará (`RN-MV-029`) |

**Lista todo lo comprado, diga lo que diga la venta** (decisión del responsable, 17-09-2026, sobre la alternativa de listar solo lo pagado). Una sola lista responde «qué tengo y qué me falta»; con solo lo pagado, quien acaba de comprar vería la lista vacía y no sabría si la compra existió.

**La vigencia corre desde la entrega, no desde la compra.** Es la decisión 1 de `requirements/mv.md` §5.4: quien pagó treinta días recibe treinta días de uso. Un producto comprado el sábado y confirmado el lunes está activo hasta treinta días **después del lunes**.

---

## 3. Actores

| Actor | Rol en esta funcionalidad |
|---|---|
| Cualquiera autenticado con `movements:read-own-products` | Ve los productos de las ventas **a su nombre**. Hasta el 21-09-2026 sin permiso, «como `RF-MV-008` y por lo mismo», y los dos cambiaron el mismo día |

**«Propio» aquí es un solo papel: el sujeto.** Al revés que `RF-MV-008`, no se incluye lo que la persona **vendió**: un vendedor no «tiene» los bots que colocó. Lo que vendió se sigue viendo allí, con papel `SELLER`.

---

## 4. Alcance

### 4.1 Incluye

- El **listado paginado** de los productos de las ventas a nombre del actor, del más reciente al más antiguo.
- En cada fila: el producto, de qué venta viene, cuántos, cuándo se compró, la implementación, **el estado**, **desde cuándo** se tiene y **hasta cuándo**.
- Un **filtro por estado**.

### 4.2 No incluye

- **Los productos de otras personas.** Ni con permiso: es una consulta sobre uno mismo.
- **Lo vendido** → `RF-MV-008`.
- **Descargar, activar o usar** el producto. El sistema dice que se tiene; qué se hace con ello no es de este módulo.
- **Agrupar** dos compras del mismo producto en una fila. Cada compra es una fila con su propia vigencia; agruparlas obligaría a decidir cuál vigencia manda, y esa decisión no existe.

---

## 5. Reglas de negocio aplicables

| Regla | Cómo aplica |
|---|---|
| `RN-MV-001` | Solo lee |
| `RN-MV-004` | Lo pendiente de pago **no se tiene**: aparece con ese estado y sin vigencia |
| `RN-MV-021`, `RN-MV-029`, `RN-MV-030` | Son de donde salen `PENDIENTE_ACTIVACION`, `RETENIDO` y el instante de la entrega |
| `RN-PM-015` | Sin vigencia, **no caduca**: `ACTIVO` sin «hasta» |
| `RN-MV-032` | **Los enlaces de entrega —el cupón del bot y, desde el 28-09-2026, la descarga— viajan solo con la línea entregada** (22-09-2026). En los seis estados de §2.1 eso son **`ACTIVO` y `VENCIDO`**, los dos que se apoyan en `delivery_status = ENTREGADA`; en `PENDIENTE_PAGO`, `PENDIENTE_ACTIVACION`, `RETENIDO`, `RECHAZADO` y `ANULADO` **no están entre los enlaces del producto** |
| `RN-PM-048`, `RN-PM-050` | De dónde salen los enlaces de entrega —los de tipo `CUPON_BOT` y `DESCARGA` del producto— y por qué **ninguna otra lectura fuera de administración los publica** |

**Una regla nueva, y no la decide este requerimiento**: `RN-MV-032` nace en `requirements/mv.md` §5.1 el 22-09-2026 porque `PM` estrenó un enlace que **solo tiene sentido después de la entrega**, y la entrega es de este módulo. Lo demás sigue igual: este requerimiento **lee lo que `RF-MV-003` y `RF-MV-010` deciden**.

---

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción |
|---|---|---|
| Página, tamaño | No | Como todo listado |
| Estado | No | Uno de los seis de §2.1. Uno que no exista es un error |

**Sobre quién NO es un dato de entrada**, como en `RF-MV-008`.

### 6.2 Salida

| Dato | Descripción |
|---|---|
| La línea | **Su identificador**: es lo que `RF-MV-010` activa. Desde el 28-09-2026 |
| Producto | **Como en la oferta de `RF-PM-007`, y como está hoy en el catálogo** (desde el 28-09-2026): identificador, código, tipo, nombre de hoy, descripción, portada, **sus enlaces**, destino, precio, moneda, conversión, vigencia, alcance, implementación y valoración. **Sin precio de compra** (`RN-PM-024`). Hasta el 28-09-2026 eran el identificador, el código y el nombre de la compra |
| Nombre de la compra | **El nombre tal como se compró** (la copia de la línea, `RN-MV-002`); el del producto es el de hoy |
| **Enlaces del producto** | **Resueltos** (`RN-PM-049`), cada uno con su tipo. **En la línea entregada** —`ACTIVO` o `VENCIDO`— son **todos** los que el producto declara, **los de entrega incluidos** (`CUPON_BOT`, `DESCARGA`); **en los otros estados**, solo los que son material de venta, **como en la oferta**, aunque el producto declare los de entrega (`RN-MV-032`, `RN-PM-050`). Vacíos si no declara ninguno. Hasta el 28-09-2026 el cupón viajaba aparte, en un dato propio de la fila |
| La venta | Identificador y código, para abrirla en `RF-MV-008` |
| Cantidad | |
| Cuándo se compró | La fecha de la venta |
| Implementación | Automática o manual |
| **Estado** | Uno de los seis |
| Desde cuándo | El instante de la entrega; ausente si no se ha entregado |
| Hasta cuándo | La entrega más la vigencia comprada; ausente si no se ha entregado **o si no caduca** |
| Motivo | Solo en `RETENIDO` |

**Un `VENCIDO` conserva sus enlaces de entrega, y conviene decir por qué.** La vigencia que pasó es la de **lo que se compró**, no la del enlace: el bot lo aloja un tercero, y esconder la dirección no le quita a nadie el acceso que ya tiene — solo haría que el registro de lo comprado **mintiera sobre lo que se entregó**. Es la misma línea que `RETENIDO` traza por el otro lado: allí los enlaces de entrega no viajan porque **la entrega no ocurrió**.

**Y los enlaces no se copiaron en la línea** (`RN-MV-032`, única excepción declarada a `RN-MV-002`): se leen **del catálogo, hoy**. Si administración corrige la dirección del bot o de la descarga, quien compró ve **la nueva**, que es lo que necesita para que su enlace funcione — al revés que el precio y el nombre, que se congelan porque cambiarlos reescribiría lo vendido.

**«Hasta cuándo» viaja calculado**, aunque la persona pudiera sumarlo: sumar días a un instante lo haría cada cliente de la API a su manera, y con distinta zona horaria.

---

## 7. Precondiciones y postcondiciones

| Tipo | Condición |
|---|---|
| Precondición | El actor está autenticado y porta `movements:read-own-products` — **hasta el 21-09-2026 sin permiso** (`RF-SP-062`) |
| Postcondición | Ninguna. No se escribe ni se audita |

---

## 8. Flujo principal

1. El actor pide sus productos.
2. El sistema resuelve quién es por su credencial.
3. Toma las líneas de las ventas **a su nombre**, calcula el estado de cada una con la venta, la entrega y la vigencia, y si se indicó un estado se queda con esas.
4. Ordena de la compra más reciente a la más antigua y devuelve la página.

---

## 9. Flujos alternativos

### FA-001 — No compró nada

Página vacía, no un error.

### FA-002 — Compró dos veces el mismo producto

Dos filas, cada una con su venta, su estado y su vigencia.

### FA-003 — La venta es de un paquete

Una fila por producto del paquete, como cualquier otra. El paquete no es un producto que se tenga.

---

## 10. Excepciones

Ninguna propia.

---

## 11. Validaciones

| ID | Validación |
|---|---|
| `VAL-001` | La paginación es válida |
| `VAL-002` | El estado, si viene, es uno de los seis |

---

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-MV-140` | Una línea **entregada** de un producto con `CUPON_BOT` trae el cupón **entre los enlaces del producto**, con su tipo y **resuelto** —con el identificador externo pegado al final—, junto a los demás enlaces que declare; y lo trae **igual en `ACTIVO` y en `VENCIDO`**. La fila no trae ningún otro dato con el cupón (enmendado el 28-09-2026: antes viajaba en un dato propio de la fila) |
| `CA-MV-141` | La **misma** línea en `PENDIENTE_PAGO`, `PENDIENTE_ACTIVACION` y `RETENIDO` **no trae el cupón** entre sus enlaces, comprobado sobre el cuerpo entero: el producto declara el cupón y la respuesta no lo lleva. **Es el criterio que impide entregar por una consulta lo que quien compró no ha activado (`RN-MV-021`)** |
| `CA-MV-142` | Una línea entregada de un producto **sin** enlaces de entrega trae **los mismos enlaces que la oferta**; y **veinte líneas entregadas no disparan veinte consultas al catálogo**: los enlaces de la página se piden **en una sola llamada en lote** |
| `CA-MV-285` | Un producto con `DESCARGA` la trae entre sus enlaces **solo en la línea entregada**, como el cupón; y **dos compras del mismo producto, una entregada y otra pendiente, traen listas distintas**: la entregada con los enlaces de entrega y la pendiente sin ellos (28-09-2026) |
| `CA-MV-099` | Aparecen **solo** los productos de las ventas a nombre del actor; lo que vendió a otros **no** |
| `CA-MV-100` | Una línea de una venta **pendiente** aparece como `PENDIENTE_PAGO`, sin «desde» ni «hasta» |
| `CA-MV-101` | Una línea **entregada** con vigencia aparece `ACTIVO` con «hasta» = entrega + días, y pasado ese instante aparece `VENCIDO` |
| `CA-MV-102` | Una línea entregada **sin vigencia** aparece `ACTIVO` sin «hasta» |
| `CA-MV-103` | Una línea **manual** de una venta confirmada aparece `PENDIENTE_ACTIVACION` |
| `CA-MV-104` | Una línea **retenida** aparece `RETENIDO` con su motivo |
| `CA-MV-105` | Una línea de una venta **rechazada** o **anulada** aparece con ese estado |
| `CA-MV-106` | El filtro por estado devuelve **solo** ese estado; uno inexistente es `400` |
| `CA-MV-107` | Va **paginado**, del más reciente al más antiguo, y **una fila por línea** aunque el producto se repita |
| `CA-MV-108` | Cada fila trae el nombre **copiado en la línea**, no el del catálogo |
| `CA-MV-109` | Responde a cualquier autenticado con `movements:read-own-products` y sin él `403` (hasta el 21-09-2026, «sin permiso»); sin autenticar, `401` |
| `CA-MV-284` | Cada fila trae **el identificador de su línea**, y es el que acepta la activación de `RF-MV-010` |

---

## 13. Casos límite

| Caso | Comportamiento |
|---|---|
| Vigencia que vence **exactamente** en el instante consultado | Ya está `VENCIDO`: el borde es el mismo que `SP` fija para la membresía vigente —una fecha igual al instante ya no está vigente— |
| El producto fue **retirado del catálogo** después | Aparece igual, con el nombre copiado. `RN-PM-010` garantiza que la fila del producto sigue existiendo para el código |
| Cantidad mayor que uno | Una fila, con su cantidad. No se multiplica |
| El producto **se retira del catálogo** y tenía cupón o descarga | Los enlaces de entrega **siguen viajando** en las líneas entregadas. `RN-PM-010` garantiza que la fila del producto sobrevive, y sus enlaces con ella; retirar del catálogo es dejar de vender, no retirar lo entregado (22-09-2026) |
| Administración **cambia la dirección del cupón** después de la compra | Quien compró ve **la nueva**, porque el cupón se lee del catálogo y no se copió en la línea (`RN-MV-032`). Es lo buscado: si el bot cambia de dirección, un enlace congelado dejaría de funcionar |
| Administración **quita** el enlace `CUPON_BOT` del producto | El campo **desaparece** de las líneas entregadas. Es la contrapartida del caso anterior y se acepta con él: lo que se publica es el enlace vigente, y si no hay, no hay |

---

## 14. Preguntas abiertas

**Si «activo» debería mirar algo más que la vigencia.** Hoy un producto entregado está activo hasta que vence; el día que exista retirar un nivel o revocar un producto, este estado tendrá que leerlo. No es de este requerimiento.

---

## 15. Control de cambios

| Versión | Fecha | Cambio | Autor |
|---|---|---|---|
| 0.1.0 | 17-09-2026 | Primera versión, a petición del responsable del proyecto. **Responde por productos y no por movimientos**, que es lo que `RF-MV-008` no puede hacer sin estirarse; **se deriva del libro y no se guarda**, por el mismo criterio que el saldo de puntos; y **lista todo lo comprado con su estado** (decisión del responsable, sobre listar solo lo pagado), con seis estados calculados de lo que la venta, la entrega y la vigencia ya dicen. La vigencia corre desde la entrega. | Responsable del proyecto |
| 0.4.0 | 22-09-2026 | **La línea entregada trae el cupón del bot** (`RN-MV-032`, [`requirements/mv.md`](../../../requirements/mv.md) v0.35.0; `RN-PM-048` a `RN-PM-050`, [`requirements/pm.md`](../../../requirements/pm.md) v0.43.0 §5.2.14). `PM` estrenó un enlace que **solo tiene sentido después de la entrega** —dónde registra su cuenta quien ya compró un bot— y la entrega es de este módulo, de modo que **esta lectura es el único sitio del sistema, fuera de administración, donde ese enlace se ve**. Lo que fija: **(1) solo entregada.** `couponUrl` viaja en `ACTIVO` y `VENCIDO` —los dos estados que se apoyan en `delivery_status = ENTREGADA`— y **no viaja** en `PENDIENTE_PAGO`, `PENDIENTE_ACTIVACION`, `RETENIDO`, `RECHAZADO` ni `ANULADO`. Publicarlo en una pendiente sería **entregar por una consulta** lo que `RN-MV-021` no ha autorizado, y en una retenida, después de haber escrito que no se entregaría; por eso el estado va **en el predicado** y `CA-MV-141` comprueba **el cuerpo entero**. **Un `VENCIDO` lo conserva**: la vigencia que pasó es la de lo comprado, no la del enlace, y esconderlo no le quita el acceso a nadie — solo haría que el registro mintiera sobre lo que se entregó. **(2) Lo resuelve `PM`, en lote.** Se descartó el `JOIN` contra `product_links`, que habría costado una línea porque esta consulta ya cruza `products`: la composición del enlace (`RN-PM-049`) es una regla de `PM` y un `JOIN` obligaría a reescribirla aquí. Es la distinción de D-25 que `modelo-datos.md` declara —**las claves foráneas cruzan; los repositorios no**—, y lo que se cruza por FK es el código, un dato sin reglas. Y **en lote**, una vez por página, porque veinte líneas preguntando veinte veces son la `N+1` que no se ve (`CA-MV-142`). **(3) No se copia en la línea**, única excepción declarada a `RN-MV-002`: se lee **del catálogo, hoy**, de modo que corregir la dirección del bot **repara** el enlace de quien compró en lugar de reescribir lo vendido — y quitarlo lo hace desaparecer, que es la contrapartida y se acepta con él. **Sin migración, sin permiso y sin ruta nueva**; `GET /api/v1/movements/mine/products` sigue con `movements:read-own-products`. Nacen **`CA-MV-140`** a **`CA-MV-142`** y tres casos límite. Enmienda de Art. I.7. | Responsable del proyecto |
| 0.5.0 | 28-09-2026 | **`PENDIENTE_AUTORIZACION` pasa a llamarse `PENDIENTE_ACTIVACION`**, por decisión del responsable del proyecto: desde ese día lo manual lo activa quien lo compró (`RF-MV-010`, `RN-MV-048`, [`requirements/mv.md`](../../../requirements/mv.md) v0.48.0) y nadie autoriza nada. **Es un cambio incompatible del contrato**, declarado. **Y cada fila trae el identificador de su línea** —compatible—, que es lo que se activa: `CA-MV-284`. | Responsable del proyecto |
| 0.6.0 | 28-09-2026 | **El producto viaja como en la oferta, y los enlaces de entrega van entre sus enlaces**, por decisión del responsable del proyecto —«en consultar mis productos comprados quiero poder traer los links del producto; si es bot, traer el tipo `CUPON_BOT`», y después «que los enlaces solo vengan en los productos, consultar todos los links que tenga el producto»—. **(1)** §6.2 recoge por escrito lo que el PR #128 construyó ese mismo día sin enmendar esta spec: el producto en la forma de `RF-PM-007`, leído del catálogo de hoy, y el nombre de la compra aparte. **(2) El cupón deja de viajar en un dato propio de la fila** y pasa a ser uno más de los enlaces del producto, con su tipo: una sola lista. **Cambio incompatible**, declarado. **(3) Cuándo se ve no cambia** (`RN-MV-032`): el responsable confirmó que el cupón se usa **después** de activar, no para activar, de modo que la línea entregada trae **todos** los enlaces y las demás, los de la oferta. **(4) Nace `DESCARGA`** ([`requirements/pm.md`](../../../requirements/pm.md) v0.45.0), que es entrega como el cupón y sigue su regla. Se enmiendan `CA-MV-140` a `CA-MV-142` y nace `CA-MV-285` | Responsable del proyecto |
