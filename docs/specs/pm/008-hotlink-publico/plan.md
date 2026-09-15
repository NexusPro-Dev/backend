# PLAN — `RF-PM-008` Consultar un hotlink

| Campo | Valor |
|---|---|
| Requerimiento | `RF-PM-008` |
| Especificación | [`spec.md`](spec.md), aprobada el 07-09-2026 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 07-09-2026 |
| Enmendado el | 14-09-2026 — **`videoUrl` en el producto, sin token** (`RN-PM-032`), §4; 14-09-2026 — **`coverImageUrl` en el producto, sin token** (`RN-PM-033`), §4; 15-09-2026 — **`HOTLINK` o `AMBOS`** en el predicado (`RN-PM-021`) |

---

## 1. Enfoque

**Una lectura que compone tres cosas de dos módulos, y que no puede vivir en `SP`.**

`modules.md` §7 exige que el grafo de dependencias sea acíclico: `PM` consume `SP` y `SP` no consume nada. Este endpoint devuelve un producto, un vendedor y una tasa; ponerlo en `SP` obligaría a que **la raíz del grafo leyera `products`**, y eso cierra el ciclo. Vive en `PM`, y los dos datos de `SP` entran por **interfaces de aplicación de solo lectura** (**D-25**).

## 2. Cambios de esquema

**Ninguno propio.** `products` ya tiene `scope` (`V59`) y `exchange_rates` la crea `RF-SP-047` (`V65`).

!!! warning "Dependencia dura: este requerimiento no se puede construir antes que `RF-SP-047`"

    La conversión sale de `exchange_rates`, que **todavía no existe**: sus tripletas están escritas y su código no. Construir esto antes obligaría a devolver la conversión siempre vacía, y a volver después — con una prueba que pasaría por el motivo equivocado.

La búsqueda del producto por código **ya está cubierta** por `uq_products_code`, que es único y total. No hace falta índice nuevo.

## 3. Componentes afectados

| Capa | Elemento | Módulo |
|---|---|---|
| `interfaces` | `HotlinkController` | `PM` |
| `application` | `HotlinkResponse`, con `SellerRef` y `ExchangeRef` | `PM` |
| `domain/service` | `GetHotlinkService` | `PM` |
| `domain/repository` | `ProductQueryRepository.findPublishedByCode(String)` | `PM` |
| **`application`** | **`PublicSellerLookup`** — nombre y apellido por nombre de usuario | **`SP`** |
| **`application`** | **`ExchangeRateLookup`** — la tasa vigente entre dos monedas | **`SP`** |
| `shared/security` | La ruta entra en `RUTAS_PUBLICAS` y en la política de límite de tasa | `shared` |

!!! important "Las dos lecturas nuevas de `SP` llevan la regla dentro, y eso es lo que las hace correctas"

    **`PublicSellerLookup` devuelve vacío cuando la persona no es fuerza comercial**, y no un objeto que `PM` tenga que filtrar. La regla de quién es publicable depende de los **roles**, que son de `SP`: si el puerto devolviera a cualquiera y `PM` decidiera, la definición de «fuerza comercial» viviría en dos módulos y el segundo se quedaría atrás.

    Es exactamente lo que `CurrentMembershipLookup` hizo con «vigente»: devuelve la membresía **ya evaluada** en lugar de su fecha de fin, porque reimplementar esa evaluación en `PM` es el defecto que **no falla** — resultados plausibles durante meses.

    **`ExchangeRateLookup` devuelve la tasa ya elegida**, no la lista de las del par. Que sea una sola lo garantiza `RN-SP-032`; que sea la de hoy lo decide `SP`, que es de quien es la vigencia.

## 4. Contrato de API

`GET /api/v1/hotlinks/{username}/{code}` — **público**.

```json
{
  "seller": { "firstName": "Ana", "lastName": "Ruiz" },
  "product": {
    "code": "UPGRADE_ORO",
    "type": "UPGRADE_MEMBRESIA",
    "name": "Ascenso a Oro",
    "icon": "crown",
    "videoUrl": "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
    "validityDays": 30,
    "membership": { "code": "ORO", "name": "Oro", "color": "D4AF37" },
    "price": 60.00,
    "currency": { "code": "USD", "decimalPlaces": 2 },
    "exchange": {
      "currency": { "code": "COP", "decimalPlaces": 2 },
      "rate": "4150.00000000",
      "amount": 207458.50
    }
  }
}
```

- **`membership` es una proyección PROPIA y recortada**, no el `MembershipRef` que comparten los otros cuatro endpoints: tres campos en vez de cinco. Se declara aparte **a propósito**, y no por descuido — en una ruta pública, reutilizar la forma completa publicaría el identificador y el nivel sin que nadie lo hubiera decidido. Llega **presente y nula** en los bots.
- **`exchange` llega presente y nulo** cuando no hay conversión (`FA-001`, `FA-002`). Un campo que desaparece es indistinguible de uno que el cliente no conoce.
- **`rate` viaja como cadena y no como número.** Es el único campo del sistema que lo hace, y por un motivo: tiene **ocho decimales**, y un número JSON pasa por coma flotante de doble precisión en cualquier cliente JavaScript. Como cadena, la tasa que se muestra es la que se declaró.
- **`amount` sí es número**, redondeado a los decimales de la **moneda de destino** con `ProductPrice`, que es el componente que ya hace eso para las respuestas del módulo.
- **Ningún parámetro de consulta**, y ninguna cabecera que cambie la respuesta.
- **`coverImageUrl` viaja, y sin token** (14-09-2026, `RN-PM-033`, enmienda de `RF-PM-014`): `findPublishedByCode` selecciona `p.cover_image_id` y `ProductRef` gana el campo convertido con `ProductImageUrls.de(...)`, **presente y nulo** cuando no hay. **La dirección señala una imagen y no un producto** —`/api/v1/product-images/{imageId}`—, de modo que una dirección de imagen no dice de qué producto es ni sirve para llamar a nada más. Y `RF-PM-016` la sirve sin token, que es lo que hace que la pantalla del hotlink pueda pintarla.
- **`videoUrl` viaja, y sin token** (14-09-2026, `RN-PM-032`): `findPublishedByCode` selecciona `p.video_url` y `ProductRef` gana el campo, **presente y nulo** cuando no hay. Es la única columna opcional de `products` que esta consulta trae y `purchase_price` no, y la línea que las separa es la de `pm.md` §5.2.8: el costo enseñaría el margen, el video existe para que lo vean. **Se publica tal cual se escribió**, sin seguirlo ni reescribirlo.
- **`price` viaja solo** (12-09-2026, `RN-PM-024` reescrita por tercera vez): es el que se cobra. **El precio de compra no viaja ni se selecciona**: `ProductRef` no tiene el campo y `findPublishedByCode` no trae la columna, de modo que `ProductRow.purchasePrice` llega nulo a propósito desde esta lectura y **no hay nada que publicar**. Entre el 08-09-2026 y el 12-09-2026 viajó también `publicPrice`, cuando ese importe era lo que se anunciaba; convertido en el costo de NEXUS, publicarlo sin token enseñaría el margen a cualquiera (`requirements/pm.md` §5.2.6).
- **Y `amount` se calcula sobre `price`**, que es el único importe que se enseña. Desaparece «el importe que se muestra» y con él `ProductExchangeResolver.importeMostrado`.

!!! warning "Este apartado se ha invertido dos veces, y la segunda deshace la primera"

    **El 12-09-2026 volvió a la forma original, con el otro importe.** La consulta **no selecciona** `purchase_price`, por el mismo motivo por el que el 08-09-2026 por la mañana no seleccionaba `public_price`: traer el costo hasta el servicio lo dejaría a un campo de distancia de publicarse **sin token**. Lo que sigue es la historia de la inversión intermedia.


    Decía que la consulta debía resolver `COALESCE(public_price, price)` y **no seleccionar el otro**, porque traer los dos importes hasta el servicio dejaba el del sistema «a un campo de distancia de publicarse» en una ruta sin token.

    **Eso es exactamente lo que ahora se pide**, por decisión del responsable del proyecto (`requirements/pm.md` §5.2.5). `findPublishedByCode` selecciona `p.price` y `p.public_price` **por separado**, y los dos viajan.

    Lo que la decisión acepta —que la diferencia entre lo anunciado y lo cobrado quede visible sin token— está escrito en `spec.md` §6.2 y medido por `CA-PM-169`.

## 5. El `404` uniforme, y dónde se implementa

**Un solo punto de salida.** El servicio no lanza tres excepciones distintas que un manejador homogeneíce después: lanza **una**, con el mismo código y el mismo mensaje, desde los tres sitios donde puede fallar.

!!! danger "El error más fácil de este requerimiento es distinguir los mensajes"

    Es tentador escribir «vendedor no encontrado» y «producto no encontrado» porque **ayuda a depurar**. Y es exactamente lo que convierte el endpoint en un oráculo: con dos mensajes distintos, fijar un código bueno y variar el usuario dice quién existe.

    `CA-PM-134` compara **el cuerpo entero** de las seis respuestas, no solo el estado. Sin esa comparación, el día que alguien mejore un mensaje nadie se enteraría.

## 6. Autorización

**Ninguna.** La ruta entra en `RUTAS_PUBLICAS` de `SecurityConfig`, con su motivo escrito al lado como las cinco que ya hay. Y entra en `EndpointPermissionsIT` en la lista de rutas **sin permiso a propósito**, que es lo que impide que una ruta pública se cuele por descuido.

## 7. Límite de tasa

La ruta se acota **por origen** en `RateLimitFilter`, y no por identidad: no hay identidad. Es la única mitigación del recorrido a ciegas, y `spec.md` §10 deja escrito que **acotar no es impedir**.

## 8. Auditoría

**No audita.** Es una lectura pública y anónima; auditarla llenaría el registro de cambios de filas sin actor. La huella queda en `request_log`, que ya recoge toda llamada HTTP.

## 9. Transaccionalidad

`@Transactional(readOnly = true)`. **TRES consultas, y cuatro solo si hay algo que convertir**: el vendedor, el producto, la **moneda de casa** y —únicamente cuando el producto está en otra moneda— la **tasa**. Todas menos la primera se pagan solo si la anterior salió, de modo que **quien recorre nombres al azar cuesta UNA**.

!!! note "El plan dijo tres, luego cuatro, y la prueba de `T-13` dejó el número en su sitio (08-09-2026)"

    El plan contó la conversión como **un** paso —«la tasa»— cuando son **dos**: cuál es la moneda de casa y a cuánto se cambia. Y la segunda **no siempre se paga**: con el producto ya en la moneda de casa no hay nada que convertir y la consulta de tasas ni se lanza, de modo que el coste real es **tres**, o cuatro cuando la conversión procede.

    El número se corrigió **dos veces el mismo día**, y las dos las destapó la prueba: primero al escribirla y después al optimizar el resolutor. Es exactamente para lo que existe una prueba que cuenta sentencias — la respuesta habría sido idéntica con seis.

## 10. Alternativas consideradas

| Alternativa | Por qué se descartó |
|---|---|
| **Dos endpoints públicos independientes** | Dos superficies públicas que vigilar en vez de una, y dos sitios donde alguien podría distinguir los mensajes de error |
| **Ponerlo en `SP`** | Cierra el ciclo del grafo: `SP` tendría que leer `products` |
| **Que el puerto devuelva a cualquiera y `PM` filtre** | La definición de «fuerza comercial» viviría en dos módulos, y el segundo se quedaría atrás sin que nada fallara |
| **Distinguir los mensajes de `404`** | Convierte el endpoint en un oráculo de existencia de personas |
| **`404` cuando no hay tasa vigente** | Escondería un producto vendible porque nadie declaró una tasa |
| **Elegir la moneda por parámetro** | Superficie pública añadida, y abre la pregunta sin dueño de qué hacer cuando no hay tasa para ese par |
| **Cachear la respuesta en el servidor** | La tasa y el estado del producto cambian, y una caché mal invalidada publicaría un producto retirado. Si hace falta, se resuelve con cabeceras y no con estado |

## 11. Riesgos

| # | Riesgo | Mitigación |
|---|---|---|
| 1 | **Alguien distingue los mensajes de `404`** para depurar | `CA-PM-134` compara el **cuerpo entero** de las seis respuestas |
| 2 | **La ruta se cuela sin declararse pública** y responde `401` | `EndpointPermissionsIT` la exige en la lista de rutas sin permiso a propósito |
| 3 | **`rate` se serializa como número** y el cliente lo redondea | Viaja como **cadena**, y `CA-PM-128` comprueba los ocho decimales |
| 4 | El puerto de `SP` devuelve a **cualquier** persona | La regla vive en `SP`; `CA-PM-133` prueba el cliente y `CA-PM-135` que no viaja nada más que el nombre |
| 5 | **Se construye antes que `RF-SP-047`** y la conversión queda siempre vacía | Declarado como bloqueo en `tasks.md` §4 |
| 6 | **Se publica el precio de compra sin token** — el margen de NEXUS, a la vista de quien reciba un enlace por mensajería. **Vuelve a ser un riesgo el 12-09-2026**: entre el 08-09-2026 y esa fecha el segundo importe se publicaba a propósito, cuando era lo que se anunciaba | La única defensa es que `ProductRef` **no tenga** el campo y que la consulta **no lo seleccione**. `CA-PM-163` prueba la ausencia con un producto que **sí** tiene costo declarado; `CA-PM-169`, que afirmaba la presencia, se invierte de vuelta |
| 7 | **Se convierte un importe y se publica el otro** | `CA-PM-162` comprueba la cuenta al revés: el importe convertido **dividido por la tasa** devuelve el publicado. Con `rate` en la respuesta, cualquier descuadre es deducible desde fuera |

## 12. Estrategia de prueba

- **Integración de API**: los once criterios de `spec.md` §12.
- **La prueba del oráculo**: los **seis** casos que no proceden, comparando el cuerpo entero entre ellos. Es la prueba que define el requerimiento.
- **De la conversión**: con tasa, sin tasa, y con el producto ya en la moneda de casa.
- **Del precio que se publica**: `price` con precio de compra declarado y sin él (`CA-PM-161`), que la conversión sale de `price` (`CA-PM-162`), y que el precio de compra **no aparece en el cuerpo** bajo ningún nombre (`CA-PM-163`).
- **De número de consultas**: tres, y **dos** cuando el vendedor no procede — la tasa no se pide si no hay a quién enseñársela.
- **Del límite de tasa**: el exceso desde un origen recibe `429`.
