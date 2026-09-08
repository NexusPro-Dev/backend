# PLAN — `RF-PM-008` Consultar un hotlink

| Campo | Valor |
|---|---|
| Requerimiento | `RF-PM-008` |
| Especificación | [`spec.md`](spec.md), aprobada el 07-09-2026 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 07-09-2026 |

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
    "validityDays": 30,
    "membership": { "code": "ORO", "name": "Oro", "color": "D4AF37" },
    "price": 49.99,
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
- **`price` es el precio A MOSTRAR** (08-09-2026, `RN-PM-024`): `COALESCE(public_price, price)` resuelto **en la consulta**, igual que en `RF-PM-007`. La respuesta **no tiene un segundo campo de importe** del producto, y esa ausencia es lo único que sostiene la regla en la única ruta del módulo **sin token**.
- **Y `amount` se calcula sobre ESE número**, no sobre el otro. Es la mitad de la enmienda y la que tiene filo: `rate` viaja en la misma respuesta, de modo que publicar un importe y convertir el otro dejaría **la diferencia entre lo anunciado y lo cobrado a una división de distancia** — sin token, en un endpoint pensado para repartirse por WhatsApp.

!!! danger "Aquí NO vale resolver el importe en Java"

    En `RF-PM-007` la resolución va en el `SELECT` por prudencia; aquí es una condición del requerimiento. Si la consulta trajera **los dos** importes hasta el servicio, el precio del sistema estaría **dentro del objeto que se serializa**, a un campo de distancia de publicarse — y en una ruta pública ese descuido no se puede retirar después.

    `findPublishedByCode` selecciona `COALESCE(p.public_price, p.price) AS price` y **no selecciona `public_price`**: por esta lectura solo viaja un número.

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

`@Transactional(readOnly = true)`. **Cuatro consultas**: el vendedor, el producto, **la moneda de casa** y la tasa. Las dos últimas solo se pagan si las dos primeras salieron, de modo que **quien recorre nombres al azar cuesta UNA** y no cuatro.

!!! note "Eran tres al aprobar el plan, y la prueba de `T-13` dijo que son cuatro (08-09-2026)"

    La cuarta es `CurrencyCatalog.findDefault()`, que el plan no contó porque pensaba la conversión como un solo paso —«la tasa»— cuando son dos: **cuál es la moneda de casa** y **a cuánto se cambia**. El número se corrige aquí en vez de esconderlo, que es para lo que existe una prueba que cuenta sentencias: la respuesta habría sido idéntica con seis.

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
| 6 | **Se publica el precio del sistema sin token**, por traer los dos importes hasta el servicio o por añadir un campo «por simetría» con el catálogo administrativo | La consulta resuelve `COALESCE` y **no selecciona el otro**; `CA-PM-163` prueba la **ausencia** en el cuerpo. En una ruta pública el descuido no se puede retirar después |
| 7 | **Se convierte un importe y se publica el otro** | `CA-PM-162` comprueba la cuenta al revés: el importe convertido **dividido por la tasa** devuelve el publicado. Con `rate` en la respuesta, cualquier descuadre es deducible desde fuera |

## 12. Estrategia de prueba

- **Integración de API**: los once criterios de `spec.md` §12.
- **La prueba del oráculo**: los **seis** casos que no proceden, comparando el cuerpo entero entre ellos. Es la prueba que define el requerimiento.
- **De la conversión**: con tasa, sin tasa, y con el producto ya en la moneda de casa.
- **Del precio que se publica**: con precio público y sin él (`CA-PM-161`), que la conversión sale del importe publicado (`CA-PM-162`), y que el precio del sistema **no aparece en el cuerpo** (`CA-PM-163`).
- **De número de consultas**: tres, y **dos** cuando el vendedor no procede — la tasa no se pide si no hay a quién enseñársela.
- **Del límite de tasa**: el exceso desde un origen recibe `429`.
