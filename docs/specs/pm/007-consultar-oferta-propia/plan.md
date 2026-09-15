# PLAN — `RF-PM-007` Consultar la oferta disponible para uno mismo

| Campo | Valor |
|---|---|
| Requerimiento | `RF-PM-007` |
| Especificación | [`spec.md`](spec.md) v0.5.0 |
| `spec.md` aprobada el | 26-08-2026 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Enmendado el | 27-08-2026 — `RN-PM-015`; 02-09-2026 — **la oferta deja de comparar niveles**; 02-09-2026 — `products:sale`; 07-09-2026 — el **alcance** y la **implementación** en la respuesta, **sin filtro** (`RN-PM-019`, `RN-PM-020`), y **construida la coincidencia por ORIGEN** (`T-20`) con la **renovación** dentro; 08-09-2026 — **un solo importe, el que se muestra** (`RN-PM-023`, `RN-PM-024`), §4 y §5; 12-09-2026 — **el segundo precio es el de COMPRA y no se selecciona** (`RN-PM-024`), §4 y §5; 14-09-2026 — **`videoUrl` sí se selecciona y viaja** (`RN-PM-032`), §4 y §5; 14-09-2026 — **`coverImageUrl` viaja** (`RN-PM-033`), §5; 15-09-2026 — **la oferta filtra por alcance**: `scope IN ('TIENDA','AMBOS')` (`RN-PM-019`) |
| Fecha de aprobación | 26-08-2026 |

---

## 1. Enfoque

Una consulta sin parámetros que responde **sobre quien llama**. Su dificultad no está en el SQL: está en que **la regla que decide qué ofrecer vive en el servidor o no vive**, y en que el dato que la alimenta —la membresía del actor— pertenece a otro módulo.

Desde el 02-09-2026 esa regla es **más pequeña de lo que era**: un upgrade declara de qué membresía sale, de modo que la oferta pasa de un cálculo —«todos los que llevan por encima de mi nivel»— a una **coincidencia exacta**. **La comparación de niveles no desaparece, se muda**: se comprueba una vez, al registrar el producto (`RN-PM-017`), en lugar de en cada consulta. Lo que aquí quedaba como el riesgo número uno —escribirla al revés— deja de existir porque ya no hay ninguna comparación que escribir.

Es la tercera y última lectura de D-25, y la única que este requerimiento estrena.

## 2. Cambios de esquema

**Ninguno sobre `products`.** El índice de listado de `V41` sirve. Desde el 02-09-2026 (§15 de `spec.md`) hay una migración nueva, `V48__seed_products_sale_permission.sql`, que siembra `products:sale` y lo asocia a `SUPERADMIN` y `ADMIN` — no toca la tabla del módulo.

## 3. Componentes afectados

| Capa | Componente | Responsabilidad |
|---|---|---|
| `modules/system/users/application` | `CurrentMembershipLookup` + adaptador | **En `SP`**: la membresía **vigente** de una persona, o vacío |
| `application` | `OfferResponse` | Dos colecciones **envueltas**, más el nivel actual del actor |
| `domain/repository` | `ProductQueryRepository.findOffer(UUID membresia)` | Una sentencia. **El parámetro es el identificador de la membresía, no su nivel**: la coincidencia es por origen |
| `domain/service` | `GetOwnOfferService` | `@Transactional(readOnly = true)` |
| `interfaces` | `ProductController` | `GET /api/v1/products/available` |

!!! important "«Vigente» lo calcula `SP`, y no se copia aquí"

    El puerto devuelve la membresía **ya evaluada**: si la asignación tiene fecha de fin y pasó, devuelve vacío. Esa definición vive en `SP` en un solo sitio desde el 24-08-2026, con su borde fijado por prueba —una fecha **igual** al instante consultado ya **no** está vigente—.

    Reimplementar esa comparación en `PM` es el defecto que no falla: devolvería resultados plausibles durante meses y solo se notaría en el borde. `FA-003` de la spec depende enteramente de que esto se respete.

## 4. Contrato de API

`GET /api/v1/products/available`, **sin ningún parámetro**. Enviarlos no cambia la respuesta ni permite preguntar por otra persona (`CA-PM-066`).

```json
{
  "currentMembership": { "id": "…", "code": "PLATA", "name": "Plata", "level": 2 },
  "upgrades": { "content": [ … ] },
  "services": { "content": [ … ] }
}
```

- **Las dos colecciones van envueltas en un objeto**, no como arreglos desnudos (`CA-PM-091`). Es la decisión que `RF-SP-017` tomó con la cadena de membresías, y aquí resuelve la quinta pregunta de la spec: hoy no se pagina, y el día que los bots crezcan, añadir paginación **no rompe a ningún cliente**.
- **`currentMembership` es `null` presente** en quien no tiene nivel, no ausente.
- **Los upgrades ordenados por nivel destino; los bots por fecha de alta** (`CA-PM-078`). El orden **sí** sigue mirando el `level` del destino, y no contradice lo anterior: ordenar no es filtrar. Presenta primero el salto más alto, que es la información que quien compra quiere ver arriba.
- **La membresía de origen no viaja en cada producto**: es siempre la del actor, que ya va en `currentMembership`. Repetirla en cada fila sería decir tres veces lo mismo, y la tercera acabaría desincronizada.
- **`price` viaja solo, y con él `exchange`** (12-09-2026, `RN-PM-024` reescrita por tercera vez): `price` es el que se cobra y `exchange` su conversión. **El precio de compra no viaja**: `OfferItem` **no tiene el campo** y la consulta **no lo selecciona**. Entre el 08-09-2026 y el 12-09-2026 `OfferItem` tuvo `publicPrice`, cuando ese importe era lo que se anunciaba; convertido en el costo de NEXUS, publicarlo enseñaría el margen (`requirements/pm.md` §5.2.6).
- **Y la conversión de la página se resuelve en DOS consultas, no en una por fila**: la moneda de casa una vez y las tasas de todas las monedas presentes en una sola sentencia. El diseño está escrito una sola vez, en [`002-consultar-productos/plan.md` §4.1](../002-consultar-productos/plan.md), porque es el mismo aquí y allí y duplicarlo dejaría dos versiones que divergen. Lo mide `CA-PM-168` **contando sentencias**, no leyendo el cuerpo.

!!! important "Por qué el campo NO se renombra a `displayPrice`"

    Sería más explícito y rompería a todo cliente de la tienda por un cambio que **no cambia lo que el campo significa para quien lo lee**: quien consume la oferta siempre ha leído «el precio que se le enseña a esta persona».

    Lo que sí cambia —y por eso se escribe aquí y en `spec.md` §13— es que **ese número puede no ser el que la venta cobre**. Quien construya la pantalla de compra tiene que saberlo: el importe que confirme `RF-MV-002` sale de `products.price`, no de este campo.

- **Y la exclusión va en el `SELECT` y no en el modelo de lectura**, aunque `ProductRow` lleve el precio de compra para el catálogo administrativo. El motivo es cuál de las dos formas puede equivocarse en silencio: si la consulta lo trajera y `OfferItem` lo callara, el costo estaría dentro del objeto que se serializa, **a un campo de distancia** — y el día que alguien añada el campo al registro «por simetría», el margen sale publicado sin que ninguna prueba lo note. Con la columna fuera del `SELECT`, `ProductRow.purchasePrice` llega **nulo** desde esta lectura y **no hay nada que publicar**. Es el mismo argumento que sostuvo el `COALESCE` el 08-09-2026 por la mañana, con el otro importe.

!!! warning "`/products/available` compite con `/products/{id}`"

    Spring resuelve primero el patrón **más específico** —el segmento literal gana a la variable de ruta—, de modo que `available` no se interpreta como identificador. Es correcto, y **por eso necesita prueba**: si alguien reordena o renombra, el síntoma sería un `400` por identificador inválido en la única ruta que un cliente usa a diario.

## 5. La consulta

Una sola sentencia, con la membresía del actor como parámetro:

- **Productos activos y no retirados**, siempre.
- **Upgrades**: solo aquellos cuyo `source_membership_id` **es** la membresía vigente del actor. Coincidencia exacta, sin comparar niveles y sin recorrer la cadena. Quien declaró el producto ya dijo a quién va dirigido.
- **Sin membresía** —el actor no tiene ninguna vigente—: **cero upgrades** y todos los bots (`FA-001`), y **sale del propio filtro**: el nulo no coincide con ningún origen. Antes había que escribirlo aparte.
- **Bots**: todos los activos, sin filtro (`spec.md` §14, resolución 2).
- **Y `p.cover_image_id` también se selecciona** (14-09-2026, `RN-PM-033`, enmienda de `RF-PM-014`): `OfferItem` gana `coverImageUrl`, convertido con `ProductImageUrls.de(...)`, **presente y nulo** cuando no hay. **Nada de `product_images`** en la sentencia: la oferta devuelve la dirección y el navegador va a buscar la imagen a `RF-PM-016`, que es público — la oferta exige `products:sale` y la imagen no exige nada, y eso es lo que hace que un `<img>` funcione sin cabecera.
- **Y `p.video_url` SÍ se selecciona** (14-09-2026, `RN-PM-032`), en la misma sentencia y sin condición nueva. Que esta consulta excluya una columna opcional y traiga otra es la línea entera de `pm.md` §5.2.8: una se esconde porque enseñaría el margen; la otra se publica porque existe para que la vean. `OfferItem` gana `videoUrl`, presente y nulo cuando no hay.
- **Solo se selecciona `p.price`** (12-09-2026). Entre el 08-09-2026 y esa fecha la consulta seleccionaba también `p.public_price`; con el segundo importe convertido en `purchase_price` —el costo— **sale del `SELECT`**, y `ProductRow.purchasePrice` llega nulo a propósito desde esta lectura.

!!! success "Escrito el 02-09-2026, construido el 07-09-2026 — y lo que lo desatascó fue la renovación"

    Esta sección describía la coincidencia por origen desde el 02-09-2026, y `findOffer` **siguió comparando niveles cinco días**: `T-20` quedó en `Pendiente` y `RN-PM-011` ni siquiera se reescribió, de modo que el documento del módulo se contradecía consigo mismo.

    Lo desatascó la **renovación** (`requirements/pm.md` §5.2.3), porque **un `X → X` no se puede ofrecer comparando niveles**: abrir la comparación a «inferior o igual» le daría a quien está en `ORO` un `PLATINO → ORO`, que no es suyo. La coincidencia exacta era la única forma, y ya estaba decidida.

    **Con ella la renovación no cuesta ni una condición**: `BECA → BECA` coincide con quien está en `BECA` igual que coincidiría `BECA → ORO`. El filtro no sabe que una es una renovación, y no necesita saberlo.

## 6. Autorización

**`@PreAuthorize("hasAuthority('products:sale')")` desde el 02-09-2026** (`CA-PM-065`, `CA-PM-101`). Hasta entonces bastaba estar autenticado; el cambio no reabre el argumento contra `products:read` —seguiría dando a cada cliente el catálogo entero para ver tres líneas, la misma decisión que `RF-SP-039` tomó con el perfil propio—, abre uno **nuevo**: un permiso propio de esta vista, que se concede por rol como cualquier otro (`RF-SP-006`) y no viene sembrado en `CLIENTE`.

El actor sale del token, nunca de un parámetro. Eso no cambia: el permiso decide **si** se responde, no **a quién**.

## 7. Auditoría

Ninguna.

## 8. Transaccionalidad

`@Transactional(readOnly = true)`. Dos lecturas: el puerto de `SP` y la consulta del catálogo.

## 9. Alternativas consideradas

| Alternativa | Por qué se descartó |
|---|---|
| Filtrar el catálogo completo en el navegador | Cada pantalla repetiría el cálculo, y la que se quedara atrás **no fallaría: ofrecería de más** |
| Un parámetro opcional de persona | Convertiría esto en «qué puede comprar fulano», que es una pregunta sobre un tercero que nadie ha decidido quién puede hacer |
| Reutilizar `RF-PM-002` con un filtro | Ese exige `products:read` y devuelve lo inactivo y lo retirado. Son dos preguntas y dos actores |
| Devolver los arreglos desnudos | Cerraría la puerta a paginar sin romper a todos los clientes |
| Calcular la vigencia de la membresía aquí | Duplicaría una regla de `SP` cuyo borde ya está fijado por prueba |
| Sembrar `products:sale` en `CLIENTE` (02-09-2026) | `V30` siembra ese rol **sin permisos a propósito**. Concedérselo de oficio en la migración repetiría exactamente lo que esa decisión evitó |

## 10. Riesgos

| # | Riesgo | Mitigación |
|---|---|---|
| 1 | **Un nivel se queda sin oferta y nadie se entera**: si nadie declara un upgrade desde `VIP`, quien esté en `VIP` no ve ninguna subida — sin error y sin aviso, y el catálogo se ve bien desde administración | Es el coste aceptado de la enmienda del 02-09-2026, escrito en la cabecera de `spec.md`. La cobertura de la cadena **deja de ser automática** y pasa a ser una responsabilidad de quien mantiene el catálogo |
| 2 | La cadena se reordena entre dos consultas y la oferta cambia sin que nadie tocara productos | Es correcto y está en `spec.md` §13. La prueba lo fija para que nadie lo «arregle» |
| 3 | Los bots crecen y la respuesta se vuelve grande | La envoltura permite paginar después sin romper el contrato; el disparador es que los bots activos pasen de unas decenas |
| 4 | **El precio de compra acaba publicado**, por un campo añadido a `OfferItem` «por simetría» con el catálogo administrativo, y con él el margen de NEXUS | La única defensa es que el registro **no tenga** ese campo y que la consulta **no lo seleccione**. `CA-PM-160` prueba la ausencia con un producto que **sí** lo tiene declarado; sin esa prueba, el defecto entra en cualquier ampliación rutinaria y **no falla nada**. Entre el 08-09-2026 y el 12-09-2026 este riesgo no existía porque el segundo importe se publicaba a propósito |
| 5 | **Se enseña un importe y se cobra otro**, y quien construye la pantalla no lo sabe | Es consecuencia aceptada (`requirements/pm.md` §5.2.4) y **no se corrige aquí**. Queda escrito en §4 y en `spec.md` §13, que es lo único que este plan puede hacer: el importe que confirma `RF-MV-002` sale de `products.price` |

## 11. Estrategia de prueba

| Qué se prueba | Nivel | Cómo |
|---|---|---|
| Los doce criterios de `spec.md` §12 | API | |
| **La coincidencia de origen** | API | Un upgrade desde la membresía del actor **se ofrece**; uno declarado desde otra, **no** (`CA-PM-106`) |
| **Un upgrade hacia el nivel que ya se tiene** | API | No se ofrece (`CA-PM-108`), y sale solo de la coincidencia: su origen es otro |
| El paso corto y el salto conviven | API | Dos upgrades desde su membresía, con destinos distintos: **se ofrecen los dos** (`CA-PM-107`) |
| Todos los declarados desde ahí, no solo el inmediato | API | Actor en el nivel más bajo de una cadena de cuatro (`CA-PM-089`) |
| Quien no tiene membresía | API | Cero upgrades, todos los bots |
| **Membresía vencida** | API | Se comporta como quien no tiene nivel (`FA-003`) |
| Quien está en la cima | API | Lista de upgrades vacía, sin error |
| Con `products:sale` | API | Responde a un actor autenticado que lo porta (`CA-PM-065`) |
| Sin `products:sale` | API | `403`, aunque el actor tenga otros permisos de `products:` (`CA-PM-101`) |
| Parámetros ignorados | API | Enviar `userId` no cambia la respuesta |
| La ruta literal no se confunde con `{id}` | API | `available` responde `200`, no `400` |
| Colecciones envueltas | API | La respuesta tiene `upgrades.content`, no un arreglo en la raíz |
| **El precio que se publica** | API | `price`, tenga o no el producto precio de compra declarado (`CA-PM-158`) |
| **El enlace del video** | API | Un producto con enlace y otro sin él en la misma oferta: tal cual y **presente y nulo**, y en la misma respuesta **sigue sin haber `purchasePrice`** (`CA-PM-228`) |
| **Que no se publique el otro** | API | El cuerpo trae **un** campo de importe por producto: se comprueba la **ausencia** de `purchasePrice` con un producto que sí lo declara (`CA-PM-160`), que es lo único que sostiene `RN-PM-024` |
