# SPEC — `RF-PM-027` Consultar el catálogo de hotlinks

| Campo | Valor |
|---|---|
| Requerimiento | `RF-PM-027` |
| Módulo | `PM` — Productos y Mercadeo |
| Estado | **En revisión** |
| Autor | Responsable técnico |
| Aprobada por | — |
| Fecha de aprobación | — |
| Enmendada el | 22-09-2026 — **`links` sustituye a `videoUrl`, resuelto y sin el `CUPON_BOT`** (`RN-PM-048` a `RN-PM-050`), **heredado de `RF-PM-007` con `OfferItem`**. Ver §15 |
| Enmendada el | 15-09-2026 — **lista `HOTLINK` y `AMBOS`** (`RN-PM-021` con el alcance de cuatro valores). Ver §15 |

---

## 1. Objetivo

Que un vendedor vea **qué puede repartir**: los productos que se publican por el canal de hotlinks, para armar sus enlaces.

## 2. Contexto

**Es la pieza que faltaba delante del hotlink.** Desde el 07-09-2026 el sistema resuelve un enlace ya repartido —`RF-PM-008`, por nombre de usuario y código, sin token— pero nada le decía al vendedor **qué enlaces podía repartir**. `products:hotlink` nació ese mismo día como «el segundo permiso de vista del módulo», sembrado y **sin endpoint que lo exigiera** ([`requirements/pm.md` §4](../../../requirements/pm.md)). Este es el suyo.

**Es la otra mitad de la vista de venta.** El responsable del proyecto lo puso así al revisar las dos lecturas: el consumidor ve **lo de su membresía** (`RF-PM-007`), y el vendedor ve **el catálogo de hotlinks**. Las dos devuelven productos en la misma forma; lo que cambia es **qué conjunto** y **desde dónde se mira**: la oferta coincide por la membresía de quien llama, y esta **no mira la membresía de nadie** — el vendedor no compra lo que reparte, de modo que un `BECA → ORO` le interesa aunque él ya esté en `ORO`.

**Y es `RN-PM-021` vista entera.** Lo que aquí se lista es exactamente lo que el hotlink público resuelve enlace a enlace: activo, no retirado, alcance `HOTLINK` o `AMBOS`. Ninguna regla nueva.

## 3. Actores

| Actor | Papel |
|---|---|
| Fuerza comercial, con `products:hotlink` | Consulta qué productos puede repartir |

**Quién lo porta lo decide quien administra roles** (`RF-SP-006`), no una siembra: hoy lo tienen `SUPERADMIN` y `ADMIN` (`V60`), y el destinatario natural es el rol de tipo `VENDEDOR`. Un administrador con el permiso ve la misma lista, y su enlace **no resolvería** (`RN-PM-022`): esta lectura dice qué se publica, no quién puede publicarlo.

## 4. Alcance

### 4.1 Incluye

- Devolver los productos **activos, no retirados y de alcance `HOTLINK` o `AMBOS`**, de los dos tipos, separados en `upgrades` y `services`.
- En la **misma forma que la oferta**: `price` con su moneda y `exchange`, **`links`** —resueltos y sin el `CUPON_BOT` (22-09-2026; era `videoUrl`)—, `coverImageUrl`, `rating`, vigencia, alcance e implementación; **sin `purchasePrice`** (`RN-PM-024`).
- Ordenados como la oferta: upgrades por nivel de destino, bots por fecha de alta.

### 4.2 No incluye

- **Componer el enlace.** La respuesta no trae `/hotlinks/{username}/{code}` armado: el vendedor conoce su nombre de usuario (`RF-SP-039`) y la forma de la ruta pública es un dato del contrato. Armarlo aquí costaría una lectura de la persona por página para ahorrarle al frontend una concatenación (§14.1).
- **Filtrar por membresía**, por tipo ni por nada: es un catálogo de decenas, entero.
- **Paginación.** Como la oferta: el conjunto es el catálogo publicable, no una tabla que crezca sin cota.
- **Los paquetes.** Entrarán a esta respuesta cuando exista `RF-PM-026`, como la oferta ganó `packages`.
- **Lo inactivo, lo retirado y lo de alcance `TIENDA`**: eso es `RF-PM-002`, bajo `products:read`.

## 5. Reglas de negocio aplicables

| ID | Regla | Origen |
|---|---|---|
| `RN-PM-021` | **El hotlink solo publica lo activo y de alcance `HOTLINK` o `AMBOS`** — aquí, el conjunto entero de lo que el hotlink publica | `requirements/pm.md` §5.1 |
| `RN-PM-019` | El alcance dice en qué vistas está el producto: **solo `HOTLINK` y `AMBOS` entran aquí**; `TIENDA` y `NINGUNO` no | `requirements/pm.md` §5.1 |
| `RN-PM-009` | Solo se ofrece lo activo | `requirements/pm.md` §5.1 |
| `RN-PM-024` | El precio de compra no sale de administración: **no se selecciona** | `requirements/pm.md` §5.1 |
| `RN-PM-031`, `RN-PM-032`, `RN-PM-033` | `rating`, los **enlaces** y `coverImageUrl` en toda lectura (22-09-2026: era `videoUrl`) | `requirements/pm.md` §5.1 |
| `RN-PM-048` a `RN-PM-050` | **Los enlaces, resueltos y sin el `CUPON_BOT`** — llegan **con `OfferItem`**, de modo que esta ficha no decide nada: lo que `RF-PM-007` publica es lo que aquí se ve. **Con token, pero no es administración**: un vendedor reparte lo que le dan, no administra el catálogo | `requirements/pm.md` §5.1 |

## 6. Datos

### 6.1 Entrada

Ninguna. Sin parámetros: responde el catálogo publicable entero.

### 6.2 Salida

| Dato | Descripción |
|---|---|
| `upgrades` | Los upgrades publicables, en la forma de `OfferItem`, con `targetMembership` resuelta —como en la oferta, el origen no viaja: es el dato con el que la oferta filtra, y aquí no filtra nada— |
| `services` | Los bots publicables, en la misma forma |

**Sin `currentMembership`**, y ahí se aparta de `OfferResponse`: la membresía de quien llama no interviene y devolverla invitaría al frontend a filtrar con ella.

## 7. Precondiciones y postcondiciones

**Precondiciones:** el actor está autenticado y porta `products:hotlink`.

**Postcondiciones:** ninguna. Es una lectura, y **no reserva nada**: que un producto aparezca aquí no promete que siga publicable cuando el enlace se abra.

## 8. Flujo principal

1. Llega un `GET` sin parámetros.
2. El sistema selecciona los productos activos, no retirados y de alcance `HOTLINK` o `AMBOS`, con sus membresías, su moneda y su `rating`, en **una sentencia**.
3. El sistema resuelve la conversión de todos en **dos sentencias más** como máximo, como en la oferta.
4. Devuelve `200` con las dos listas.

## 9. Flujos alternativos

### FA-001 — No hay nada publicable

**Comportamiento:** `200` con las dos listas vacías. No es un error: es un catálogo que todavía no publica nada por ese canal.

### FA-002 — El actor no es fuerza comercial

**Comportamiento:** con el permiso, **ve la lista igual**. Que sus enlaces no resuelvan es cosa de `RF-PM-008` (`RN-PM-022`), no de esta lectura.

### FA-003 — El actor tiene membresía `ORO` y hay un `BECA → ORO`

**Comportamiento:** **lo ve.** No es su oferta: es lo que puede repartir.

## 10. Excepciones

### EX-001 — Sin permiso

**Respuesta del sistema:** `403`. Sin `products:hotlink` la ruta no se alcanza; sin token, `401`.

## 11. Validaciones

Ninguna: no hay entrada.

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-PM-340` | Con `products:hotlink`, el sistema devuelve **los productos activos de alcance `HOTLINK` o `AMBOS`** en `upgrades` y `services`, en la forma de la oferta, y **excluye** los de alcance `TIENDA` y `NINGUNO`, los inactivos y los retirados |
| `CA-PM-341` | La respuesta **no mira la membresía del actor**: un vendedor en `ORO` ve el `BECA → ORO`, y la respuesta **no trae `currentMembership`** |
| `CA-PM-399` | La respuesta **no trae el `CUPON_BOT`** de un producto que lo declara, y sí su video **resuelto**: quien reparte hotlinks **tiene token y no es administración**, de modo que se le enseña lo mismo que a quien abre el enlace y nada más (22-09-2026) |
| `CA-PM-342` | La respuesta **no trae `purchasePrice`** bajo ningún nombre aunque el producto lo tenga declarado, y sí trae `price`, `exchange`, `links`, `coverImageUrl` y `rating`. **Reescrito el 22-09-2026**: hasta ese día `links` era el campo `videoUrl` |
| `CA-PM-343` | El orden es el de la oferta: upgrades por nivel de destino y bots por fecha de alta |
| `CA-PM-344` | Sin nada publicable responde `200` con las dos listas vacías |
| `CA-PM-345` | Sin `products:hotlink` responde `403` —también con `products:sale` o `products:read`—, y sin token `401` |
| `CA-PM-346` | `/products/hotlinks` **no cae en `/products/{id}`**: responde el catálogo y no un `400` por identificador inválido |
| `CA-PM-347` | La lectura cuesta **una sentencia** más las de la conversión, y **no sube** con el número de productos |
| `CA-PM-353` | Un producto **`HOTLINK`** activo entra en el catálogo aunque no esté en la tienda, y uno **`NINGUNO`** activo no |

## 13. Casos límite

| Caso | Decisión |
|---|---|
| Un producto pasa a `TIENDA` después de que el vendedor copió su enlace | Deja de aparecer aquí y el enlace responde `404` (`RN-PM-021`). Esta lectura no reserva nada |
| El actor es administrador sin rol de vendedor | Ve la lista (`FA-002`) |
| Cien productos publicables | Se devuelven todos: es el catálogo, y la oferta tampoco pagina. El día que sean miles se pagina como `RF-PM-002` |

## 14. Preguntas abiertas

| # | Pregunta | Resolución |
|---|---|---|
| 1 | ¿No debería la respuesta traer el enlace ya armado? | **No, por ahora.** El vendedor conoce su `username` y la ruta pública es contrato; armarlo aquí costaría leer a la persona en cada página. Si el frontend lo pide, es un campo más —`hotlinkPath`— y no otra ruta |
| 2 | ¿Por qué no reutilizar `RF-PM-002` con `scope=AMBOS&status=ACTIVO`? | **Porque exige `products:read`**, que abre el catálogo administrativo entero con el precio de compra dentro. La vista de hotlinks es de venta, y `products:hotlink` existe precisamente para no dar aquel |
| 3 | ¿Entran los paquetes? | **Con `RF-PM-026`**, como segunda lista, igual que la oferta ganó `packages`. Hoy no existen |

## 15. Control de cambios

| Versión | Fecha | Cambio | Responsable |
|---|---|---|---|
| 0.1.0 | 15-09-2026 | Redacción inicial. **La otra mitad de la vista de venta**: el consumidor ve lo de su membresía (`RF-PM-007`) y el vendedor ve el catálogo de hotlinks — que `products:hotlink` esperaba sin endpoint desde el 07-09-2026. Es `RN-PM-021` vista entera y con token: sin regla nueva, sin membresía de por medio, sin precio de compra, sin enlace armado. Ocho criterios, `CA-PM-340` a `CA-PM-347`. | Responsable técnico |
| 0.2.0 | 15-09-2026 | **Lista `HOTLINK` y `AMBOS`** (`RN-PM-021`, con el alcance de cuatro valores de [`requirements/pm.md`](../../../requirements/pm.md) v0.35.0 §5.2.11). Nació el mismo día con `HOTLINKS` y cambia de letra horas después: el predicado pasa a `scope IN ('HOTLINK','AMBOS')`. `CA-PM-353`. | Responsable del proyecto |
| 0.3.0 | 22-09-2026 | **`links` sustituye a `videoUrl`, resuelto y sin el `CUPON_BOT`** (`RN-PM-048` a `RN-PM-050`, [`requirements/pm.md`](../../../requirements/pm.md) v0.43.0 §5.2.14). Llega **con `OfferItem`**, que esta lectura comparte con la oferta: **no cambia una línea de su consulta** y no vuelve a decidir nada, que es exactamente el valor de compartir la proyección. Lo único que se escribe es **`CA-PM-399`**, y se escribe por una razón que conviene dejar dicha: **tener token no convierte esta lectura en administración**. Quien reparte hotlinks no administra el catálogo —ve lo mismo que quien abre el enlace—, y sin ese criterio sería razonable que alguien «mejorara» la vista del vendedor añadiéndole el cupón, que es la prestación que sus clientes compran. Enmienda de Art. I.7. | Responsable del proyecto |
