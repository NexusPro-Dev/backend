# PLAN — `RF-MV-014` Consultar los productos comprados propios

| Campo | Valor |
|---|---|
| Requerimiento | `RF-MV-014` |
| Especificación | [`spec.md`](spec.md) v0.1.0 |
| `spec.md` aprobada el | 17-09-2026 |
| Versión | 0.1.0 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 17-09-2026 |
| Enmendado | 21-09-2026 — exige **`movements:read-own-products`** (`RF-SP-062`, `RN-SEG-015`: autenticarse no autoriza nada); lo siembra `V31` |
| Enmendado | 22-09-2026 — **`couponUrl` en la línea entregada** (`RN-MV-032`), pedido a `PM` **en lote** por su interfaz publicada; §3, §4 y §8 |
| Enmendado | 28-09-2026 — **`couponUrl` desaparece: los enlaces de entrega van dentro de `product.links`**, y nace `DESCARGA`. Ver el recuadro |

!!! warning "Enmendado el 23-09-2026 — el «hasta» se lee, no se calcula"

    `spec.md`, enmienda del 23-09-2026. **La sentencia de §4 sigue partiendo de `movement_details`** —el alcance, la paginación y los cinco estados anteriores a la entrega no cambian— y **le une la posesión** con un `LEFT JOIN` sobre `user_products` por `movement_detail_id`, que es único.

    **Lo que se cae es la primera `CROSS JOIN LATERAL`**, la que calculaba `hasta` como `delivered_at + make_interval(days => validity_days)`. Ese valor pasa a ser `up.ends_at`. La decisión de §4 —**el estado y el «hasta» en la misma expresión, para que no puedan discrepar**— se conserva tal cual, y es lo que hace que el cambio sea una sustitución y no una reescritura: el `CASE` gana dos ramas y sigue siendo uno solo.

    **Las dos ramas nuevas, en este orden**: `CANCELADO` cuando `up.closed_at` no es nulo —se comprueba **antes** que el vencimiento, porque quien dejó de tenerlo el día doce no «venció» el treinta—, y el `VENCIDO`/`ACTIVO` de siempre, ahora contra `up.ends_at`.

    **Y queda un borde declarado**: una línea entregada **sin** fila en `user_products` solo puede ser anterior a `V38`. Se resuelve como `ACTIVO` sin vencimiento, que es lo que el `LEFT JOIN` produce por sí solo, y no se inventa una fila para ella.

!!! warning "Enmendado el 28-09-2026 — una lista de enlaces por línea, no por producto"

    `spec.md` v0.6.0. **`product` es un `OfferItem`** (PR #128, que no enmendó este plan): `ProductCatalog.offerItemsOf` lo trae por lote, con los enlaces **publicables** (`RN-PM-050`), y el nombre de la compra viaja en `purchasedName`. Lo que cambia hoy:

    **`couponUrl` desaparece y `couponLinksOf` con él.** `ProductCatalog` publica en su lugar **`deliveredLinksOf`**: los enlaces de un lote de productos, **todos los tipos y resueltos** (`RN-PM-049`), en una sentencia. Es la lectura de «lo que ve quien ya lo tiene», y existe como método aparte —y no como un parámetro de `offerItemsOf`— para que **la oferta no tenga forma de pedir los de entrega**: el filtro de la oferta sigue en el predicado.

    **El servicio arma la lista por LÍNEA y no por producto.** El mismo producto puede estar en una línea entregada y en otra pendiente de la misma página, y las dos listas son distintas (`CA-MV-285`). Para la línea `ACTIVO` o `VENCIDO`, `product` es el `OfferItem` **con los enlaces de `deliveredLinksOf`** (`OfferItem.conEnlaces`, una copia: el del mapa se comparte entre líneas); para las demás, el `OfferItem` tal cual. Los de entrega se piden **solo para los productos de líneas entregadas**, como hasta hoy los cupones: al de una línea pendiente ni se pregunta.

    **Sigue siendo `MV` quien decide por el estado**, y `PM` quien decide por el tipo y compone: `deliveredLinksOf` no sabe de líneas y `ListMyProductsService` no sabe qué tipo es entrega. §8 se conserva como historia; su argumento —lote, composición en `PM`, filtro de estado en `MV`— vale igual para la lista.

    **Por qué la lista entera y no el `OfferItem` más los de entrega añadidos**: el orden. Los enlaces salen **ordenados por tipo**, como en todas las lecturas del producto, y pegar los de entrega al final los dejaría detrás del video en esta y delante en las de administración. Una sentencia con todos los tipos los deja en su sitio.

!!! info "Qué va en este documento"

    **Cómo se construye.** Esquema, componentes, contrato, autorización y pruebas.

    **Prueba de pertenencia:** si un cambio de negocio lo invalidaría, pertenece a `spec.md`.

---

!!! note "Enmienda de Art. I.7 — 21-09-2026, `RF-SP-062`"

    Esta operación exige **`movements:read-own-products`** desde el 21-09-2026, por `RF-SP-062` —**autenticarse no autoriza nada**, `RN-SEG-015` ([`security.md` §4.3](../../../security.md#43-reglas-de-negocio))—, por decisión del responsable del proyecto: «cada endpoint debe tener su propio permiso, ya que uso esto para saber qué vista o consulta mostrar en el front; no basta con solo tener el token». Hasta entonces se atendía con solo el token, y las líneas que abajo dicen «sin permiso» o «autenticado a secas» hablan de esa decisión original y se conservan como historia: el alcance sobre uno mismo sigue siendo exactamente el mismo, lo que cambia es que ahora tiene nombre. `V31` siembra el permiso y lo da a todo rol por su tipo.



## 1. Enfoque

**Una consulta de lectura sobre las líneas, con el estado calculado en la sentencia.** Hereda de `RF-MV-008` todo lo que importa —el actor sale de la credencial, el alcance va **dentro** de la sentencia, sin permiso, orden fijo, total exacto— y cambia una cosa: **la fila es la línea, no la venta**, y el alcance es solo `movements.user_id = :actor` porque «propio» aquí es un papel (`spec.md` §3).

**El estado se calcula en SQL y no en Java**, por lo mismo que el papel en `RF-MV-008`: el filtro por estado tiene que aplicarse en la sentencia para que el total cuente lo que devuelve, y calcularlo en Java obligaría a traer todo y descartar. Es un `CASE` sobre cinco columnas —`movements.status`, `delivery_status`, `implementation`, `delivered_at`, `validity_days`— y el reloj, y **el «hasta» se calcula en la misma expresión** para que `VENCIDO` y el «hasta» que se devuelve no puedan discrepar.

**El reloj entra como parámetro**, no como `now()` de la base: es lo que permite probar el vencimiento sin esperar y lo que deja el borde fijado —una vigencia que vence exactamente ahora ya venció, como la membresía en `SP`—.

---

## 2. Cambios de esquema

**Ninguno, y el 22-09-2026 sigue siendo ninguno** aunque la respuesta gane `couponUrl` (`RN-MV-032`): el cupón **no se copia en la línea** —única excepción declarada a `RN-MV-002`, porque es el medio de la entrega y no un término de la venta— y **no se lee de `product_links`** desde aquí. Todo sale de `movements`, `movement_details` (con las columnas de `V16`, `RF-MV-003` · `T-01`) y `products` para el código. `ix_movements_user` (`V12`) responde al alcance; las líneas se cruzan por `idx_movement_details_movement` (`V7`).

---

## 3. Componentes afectados

| Capa | Componente | Cambio | Nota |
|---|---|---|---|
| `application` | `MyProductsRequest` | Nuevo | Página, tamaño y estado. Sin identificador de persona |
| `application` | `MyProductResponse` | Nuevo | La fila de §4.1 |
| `application` | `PurchasedProductState` | Nuevo | Los seis estados, cerrados |
| `domain/repository` | `MovementRepository` | Gana `findMyProducts` y `countMyProducts` | Una sentencia con el `CASE`, escrita una vez para la página y el conteo |
| `domain/service` | `ListMyProductsService` | Nuevo | Resuelve el actor, valida el estado, pagina y mapea |
| `interfaces` | `MovementController` | `GET /mine/products` | Antes de cualquier `/mine/{id}`, como `/mine` antes de `/{id}` |

**`/mine/products` va declarado antes que `/mine/{id}`, y una prueba lo fija.** Hoy `/mine/{id}` existe y `products` no es un identificador válido: Spring resolvería por especificidad igual, pero el síntoma de romperlo sería un `400 VAL-001` en la ruta que se acaba de estrenar.

---

## 4. Contrato de API

| Verbo | Ruta | Permiso |
|---|---|---|
| `GET` | `/api/v1/movements/mine/products` | Autenticado |

**Bajo `/movements/mine` y no bajo `/products`**: el dato es del libro —qué compré y si me lo entregaron—, no del catálogo. `PM` no sabe quién tiene qué.

### 4.1 La fila

| Campo | Tipo | Nota |
|---|---|---|
| `movementId`, `movementCode`, `movementStatus` | | De qué venta viene; para abrirla en `/mine/{id}` |
| `product` | objeto | `id`, `code`, `name` — el nombre es **el copiado en la línea** |
| `quantity` | entero | |
| `implementation` | `AUTOMATICA` \| `MANUAL` | |
| `state` | uno de los seis | Calculado |
| `purchasedAt` | instante | `occurred_at` de la venta |
| `deliveredAt` | instante **o nulo** | `types = {"string","null"}` |
| `validUntil` | instante **o nulo** | Nulo si no se entregó o si no caduca |
| `deliveryNote` | texto **o nulo** | Solo en `RETENIDO` |

### 4.2 Parámetros

| Parámetro | Nota |
|---|---|
| `page`, `size` | `Pagination` |
| `state` | Opcional; se valida contra `PurchasedProductState`, no contra una lista escrita a mano |

**Sin ordenamiento**: de la compra más reciente a la más antigua, con desempate por venta y por código de producto para que sea estable.

**Total exacto**, como en `RF-MV-008`: es el conjunto de una persona.

---

## 5. Autorización

Ninguna anotación; la ruta entra en la lista blanca de `EndpointPermissionsIT`, como `/mine`. El alcance lo pone la sentencia, y `CA-MV-099` lo prueba con el actor que **vendió** líneas a otros: no aparecen.

---

## 6. Auditoría

Ninguna (`spec.md` §7).

---

## 7. Transaccionalidad

`@Transactional(readOnly = true)`; página y conteo en la misma transacción.

---

## 8. Impacto sobre otros módulos

Ninguno hasta el 22-09-2026. Se cruza `products` solo por el código, que es inmutable (`RN-PM-013`).

**Desde el 22-09-2026, `PM` publica una lectura más y este módulo la consume** (`RN-MV-032`): los cupones de un **lote** de productos, **ya resueltos**, en `ProductCatalog`. Tres cosas la justifican, y conviene que estén escritas porque la alternativa era más corta.

**Por qué no un `JOIN` contra `product_links`.** La consulta de este requerimiento ya cruza `products`, de modo que añadir la tabla habría costado una línea. Se descarta porque **la composición del enlace (`RN-PM-049`) es una regla de `PM`**, y un `JOIN` obligaría a reescribirla aquí —concatenar la barra, no duplicarla, respetar el identificador nulo— en un segundo sitio. Es la distinción de D-25 que `modelo-datos.md` ya declara: **las claves foráneas cruzan; los repositorios no**. Lo que se cruza por FK es el código del producto, un dato sin reglas; el enlace las tiene.

**Por qué en lote y no por línea.** Una página de veinte líneas que preguntara veinte veces cruzaría la frontera veinte veces para lo mismo — la `N+1` que no se ve, porque cada llamada es un método Java (`ProductCatalog`, Javadoc de `findForSale`). Se piden **una vez por página**, con los identificadores ya resueltos, y `CA-MV-142` lo fija.

**Por qué la resuelve `PM` y no este módulo.** Porque **también decide quién puede verlo** no es cosa de `PM`: el filtro por estado de entrega es de aquí (`RN-MV-032`), y la composición es de allí (`RN-PM-049`). Cada módulo pone lo que sabe, y ninguno de los dos tiene que conocer la mitad del otro.

---

## 9. Alternativas consideradas

| Alternativa | Por qué no |
|---|---|
| Una tabla de «productos de la persona», escrita al confirmar y al autorizar | Copia que se desincroniza sin fallar; `spec.md` §2 |
| Calcular el estado en Java | El filtro por estado dejaría el total contando lo que no devuelve |
| `now()` de la base en el `CASE` | El vencimiento no se podría probar sin esperar, y el borde quedaría sin fijar |
| Agrupar por producto | Obliga a elegir qué vigencia manda cuando hay dos compras |
| Ponerlo bajo `/products` | El dato es del libro, no del catálogo |
| Reutilizar `/mine/{id}` y que el cliente recorra ventas | Es exactamente lo que este requerimiento existe para evitar |

---

## 10. Riesgos

| Riesgo | Mitigación |
|---|---|
| Que el `CASE` y el «hasta» discrepen | Una sola expresión para los dos; `CA-MV-101` comprueba los dos lados del borde |
| Que `/mine/products` lo capture `/mine/{id}` | Orden en el controlador y prueba propia |
| Que aparezca lo vendido a otros | Alcance en la sentencia; `CA-MV-099` con un actor vendedor |

---

## 11. Estrategia de prueba

| Qué | Nivel | Por qué ahí |
|---|---|---|
| Los seis estados, con el reloj fijado a los dos lados del vencimiento | Integración, sembrando líneas por SQL con `delivered_at` | Depende de la sentencia |
| Solo lo propio como sujeto; lo vendido no | Integración | |
| Filtro por estado, estado inválido `400` | Integración | |
| Orden, paginación, una fila por línea | Integración | |
| `/mine/products` no es un `{id}` | Integración | |
| `401` sin token; `200` sin permiso | Integración | |
| El cupón en la línea entregada | API | `ACTIVO` y `VENCIDO` lo traen **resuelto** entre los enlaces de `product` (28-09-2026: antes en `couponUrl`); `PENDIENTE_PAGO`, `PENDIENTE_ACTIVACION` y `RETENIDO` **no lo traen**, comprobando el cuerpo entero (`CA-MV-140`, `CA-MV-141`) |
| El cupón no es un `N+1` | Integración | Veinte líneas entregadas: **una sola llamada** a `ProductCatalog`, y el recuento **no crece** con la página (`CA-MV-142`) |
| El cupón se lee de hoy, no de la venta | API | Se corrige la dirección en el catálogo y la misma línea entregada devuelve **la nueva**; se quita el enlace y **deja de estar entre los enlaces** |
| La descarga y la lista por línea | API | `DESCARGA` solo en la línea entregada; el mismo producto entregado y pendiente en la misma página trae dos listas (`CA-MV-285`) |
