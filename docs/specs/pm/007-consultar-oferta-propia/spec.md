# SPEC — `RF-PM-007` Consultar la oferta disponible para uno mismo

| Campo | Valor |
|---|---|
| Requerimiento | `RF-PM-007` |
| Módulo | `PM` — Productos y Mercadeo |
| Estado | **Aprobada** |
| Autor | Responsable técnico |
| Aprobada por | Responsable del proyecto |
| Fecha de aprobación | 26-08-2026 |
| Enmendada el | 02-09-2026 — ver §15 |
| Enmendada el | 07-09-2026 — **la oferta publica el alcance y la implementación, y no filtra por ninguno** (`RN-PM-019`, `RN-PM-020`). Ver §15 |
| Enmendada el | 07-09-2026 — **la oferta coincide por ORIGEN** (`RN-PM-011`), y con ella entra la **renovación**. Ver §15 |
| Enmendada el | 07-09-2026 — **el color de las membresías, incluida la del actor** (`RN-SP-024`). Ver §15 |
| Enmendada el | 08-09-2026 — **la oferta publica UN precio, y es el que se muestra** (`RN-PM-023`, `RN-PM-024`). Ver §15 |
| Enmendada el | 08-09-2026 — **los DOS precios y la conversión** al reescribirse `RN-PM-024`; `CA-PM-160` se retira y nacen `CA-PM-167` y `CA-PM-168`. Ver §15 |

---

## 1. Objetivo

Que cada persona vea qué puede comprar, sin que sea el navegador quien decida la regla.

## 2. Contexto

El catálogo de `RF-PM-002` lo lee quien administra y contiene todo. Lo que un cliente puede comprar es **un subconjunto que depende de él**: los upgrades solo tienen sentido hacia niveles por encima del suyo, y ofrecerle uno hacia el nivel que ya tiene —o hacia uno inferior— es ofrecerle pagar por nada.

!!! danger "Desde el 02-09-2026 esta consulta NO COMPARA NIVELES: compara ORIGEN"

    Un upgrade declara ahora **de qué membresía sale**, no solo a cuál lleva (`pm.md` §5.2.1). La oferta pasa de ser un cálculo —«todos los que llevan por encima de mi nivel»— a ser una **coincidencia exacta**: *los upgrades cuyo origen es mi membresía*.

    **La regla de niveles no desaparece: se muda.** Deja de evaluarse en cada consulta y pasa a comprobarse **una vez, al registrar el producto** (`RN-PM-017`, en `RF-PM-001`). Aquí ya no hay nada que deducir, porque quien declaró el producto ya dijo a quién va dirigido.

    **Y eso conserva `FA-001` sin escribir una línea**: quien no tiene membresía no coincide con ningún origen, de modo que sigue sin ver upgrades. Antes había que decirlo aparte; ahora sale del propio filtro.

    **Lo que se paga**: si nadie declara un upgrade desde `VIP`, quien esté en `VIP` **no ve ninguna subida**. No hay error. La cobertura de la cadena deja de ser automática y pasa a depender de que alguien declare los productos.

**Esa regla vive en el servidor o no vive.** Si la interfaz filtrara el catálogo por su cuenta, cada pantalla que muestre productos tendría que repetir el mismo cálculo, y la que se quedara atrás **no fallaría: ofrecería de más**. Es la misma decisión que `RF-SP-039` tomó al publicar los permisos efectivos del actor en lugar de dejar que el navegador los dedujera.

**No admite parámetro de persona.** Responde sobre quien llama y sobre nadie más. Un parámetro convertiría esta consulta en «qué puede comprar fulano», que es una pregunta sobre un tercero y que hoy nadie ha decidido quién puede hacer.

## 3. Actores

| Actor | Rol en esta funcionalidad |
|---|---|
| Cualquier persona autenticada con `products:sale` | Consulta lo que ella misma puede comprar. **No exige `products:read`**: ese permiso da el catálogo entero para que pudiera ver tres líneas. `products:sale` es propio de esta vista y desde el 02-09-2026 (§15) se concede a los roles de tipo `CONSUMIDOR` por `RF-SP-006`, como cualquier otro permiso |

## 4. Alcance

### 4.1 Incluye

- Devolver los productos **activos** que el actor puede comprar hoy.
- De los upgrades, **todos los que declaran como origen la membresía que el actor tiene**. Puede haber varios con destinos distintos —un paso corto y un salto— y se ofrecen todos: elegir es de quien compra. Y no solo el inmediato: quien está en el nivel más bajo ve todos los de arriba y elige cuánto saltar.
- **Todos los bots activos, para cualquiera**: no dependen del nivel de quien mira, ni siquiera de que tenga uno.
- Devolverlos **agrupados por tipo**: los upgrades por nivel destino, los bots por fecha de alta. Fijado el 26-08-2026 al aprobar `RF-PM-002`, que dejó dicho que las dos consultas tienen órdenes distintos porque responden a actores distintos.

### 4.2 No incluye

- **Comprar.** Esta consulta no inicia ninguna compra ni reserva nada.
- **La oferta de un tercero.** Ni con parámetro, ni con permiso: no existe.
- **Los productos inactivos o retirados**, ni el motivo por el que se retiraron.
- **El catálogo completo**, que es `RF-PM-002` y exige permiso.
- **Cualquier ajuste del precio por nivel.** Un precio distinto según quién mira es un descuento, y los descuentos son promociones, que §1.3 de `requirements/pm.md` deja fuera a propósito. Resuelto el 26-08-2026.
- **Bots acotados por nivel.** Hoy ningún bot declara membresía —`RN-PM-002` se lo prohíbe—, y acotarlos exigiría una relación nueva entre producto y membresía, no un filtro más.

## 5. Reglas de negocio aplicables

| ID | Regla | Origen |
|---|---|---|
| `RN-PM-009` | Solo se ofrece lo activo | `requirements/pm.md` §5.1 |
| `RN-PM-011` | **La oferta coincide por ORIGEN**, no compara niveles | `requirements/pm.md` §5.1 |
| `RN-PM-019` | El alcance es **acumulativo**, y por eso **no filtra** esta consulta | `requirements/pm.md` §5.2.2 |
| `RN-PM-020` | La implementación dice si lo comprado se aplica solo o espera autorización | `requirements/pm.md` §5.1 |
| `RN-PM-023` | **El precio público es opcional y no se cobra** | `requirements/pm.md` §5.1 |
| `RN-PM-024` | **Toda lectura devuelve los dos precios y la conversión** (reescrita el 08-09-2026) | `requirements/pm.md` §5.1 |
| `RN-SP-018` | Todo consumidor tiene membresía | `requirements/sp.md` §5.1 |

## 6. Datos

### 6.1 Entrada

**Ninguna.** La consulta no admite parámetros: ni de persona, ni de filtro, ni de paginación. El actor sale del token de la sesión.

### 6.2 Salida

| Dato | Descripción |
|---|---|
| Productos ofrecibles | Identificador, código, tipo, nombre, descripción, **los dos precios** con su moneda y **vigencia en días**. **El precio no se ajusta por nivel**: dos personas ven el mismo importe para el mismo producto |
| **Los DOS importes, y la conversión** | `price` —el del sistema, el que se cobra— y `publicPrice` —el anunciado, **nulo** si el producto no lo declara— (`RN-PM-024`, reescrita el 08-09-2026). Hasta esa fecha viajaba **uno solo**, resuelto por un `COALESCE` que el cliente no veía. Viaja además `exchange`, la conversión a la moneda por omisión del importe **que se muestra**, **presente y nula** cuando no hay nada que convertir |
| Alcance e implementación | Los dos, en cada producto. **El alcance no filtra esta consulta** —es acumulativo y los dos valores llegan a la tienda—; la implementación viaja para que quien compra sepa **antes de pagar** si lo que se lleva se le entrega en el acto |
| Orden | **Agrupados por tipo**: primero los upgrades ordenados por **nivel destino**, después los bots por fecha de alta |
| Membresía destino | En los upgrades: código, nombre y **nivel**, para que quien mira entienda a dónde sube |
| Membresía de origen | **No viaja.** Es siempre la del actor, que ya va en la respuesta: repetirla en cada producto sería decir tres veces lo mismo |
| Nivel actual del actor | Cuál es su membresía hoy, o que no tiene ninguna |

## 7. Precondiciones y postcondiciones

**Precondiciones**

- El actor está autenticado.

**Postcondiciones**

- Ninguna: la consulta no modifica nada.

## 8. Flujo principal

1. El actor pide su oferta.
2. El sistema resuelve **su membresía vigente** y el nivel de esta.
3. El sistema toma los productos activos.
4. De los upgrades, conserva solo aquellos **cuyo origen es la membresía del actor** — coincidencia exacta, sin comparar niveles.
5. El sistema agrupa el resultado por tipo: los upgrades por nivel destino, los bots por fecha de alta.
6. El sistema devuelve los productos resultantes junto con el nivel actual del actor.

## 9. Flujos alternativos

### FA-001 — El actor no tiene membresía

**Cuándo ocurre:** quien consulta no es consumidor —un funcionario, un vendedor— y por tanto no tiene nivel (`RN-SP-018`).

1. **No se le ofrece ningún upgrade**: no hay nivel desde el que subir, y ofrecerle el primero sería venderle una membresía, que no es lo que un upgrade hace.
2. Los bots activos **sí** se le ofrecen: un vendedor o un funcionario también puede querer comprar un bot de la plataforma, y nada en el producto lo impide.

### FA-002 — El actor está en el nivel más alto

**Cuándo ocurre:** su membresía es la cima de la cadena.

1. Ningún upgrade lleva más arriba, de modo que la lista de upgrades llega vacía.
2. **No es un error ni un mensaje especial**: es una lista vacía, y la interfaz decide qué decir.

### FA-003 — Su membresía está vencida

**Cuándo ocurre:** la membresía del actor tiene fecha de fin y ya pasó.

1. La membresía **no está vigente**, de modo que a efectos de esta consulta el actor no tiene nivel, y se aplica `FA-001`.
2. Vencer no es lo mismo que no tener, pero para decidir «a dónde puede subir» produce el mismo resultado, y conviene que esté escrito en lugar de deducido.

## 10. Excepciones

Ninguna propia. Un actor sin sesión válida se rechaza por la autenticación, que es transversal y no de este requerimiento.

## 11. Validaciones

Ninguna: la consulta no admite entrada.

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-PM-058` | El sistema devuelve solo productos **activos**: ni inactivos, ni retirados |
| `CA-PM-059` | El sistema ofrece a un actor de nivel intermedio **solo los upgrades declarados desde su membresía**, y ninguno declarado desde otra |
| `CA-PM-060` | El sistema **no ofrece** un upgrade hacia el nivel que el actor ya tiene **cuando su origen no es el suyo** — sería el salto de otro que acaba donde él está |
| `CA-PM-126` | El sistema **sí ofrece** el upgrade `X → X` declarado desde la membresía del actor: es su **renovación** |
| `CA-PM-144` | El sistema devuelve el **color** de la membresía destino de cada upgrade ofrecido, y el de la membresía **vigente del actor** |
| `CA-PM-061` | El sistema **no ofrece** upgrades hacia niveles inferiores al del actor |
| `CA-PM-062` | El sistema devuelve la lista de upgrades **vacía a quien no tenga ninguno declarado desde su membresía**, y eso incluye al que está en la cima si nadie declaró su renovación |
| `CA-PM-063` | El sistema no ofrece ningún upgrade a quien no tiene membresía vigente, incluida la vencida |
| `CA-PM-064` | El sistema devuelve el nivel actual del actor junto con su oferta |
| `CA-PM-065` | El sistema responde a cualquier persona autenticada **con `products:sale`**. Hasta el 02-09-2026 no exigía ningún permiso: mismo identificador, contenido revisado (§15) |
| `CA-PM-066` | El sistema **no admite ningún parámetro**: enviarlos no cambia la respuesta ni permite consultar la oferta de otra persona |
| `CA-PM-067` | El sistema no devuelve el motivo de retiro de ningún producto, ni la membresía de terceros |
| `CA-PM-078` | El sistema devuelve la oferta **agrupada por tipo**, con los upgrades ordenados por nivel destino y los bots por fecha de alta |
| `CA-PM-079` | El sistema ordena los upgrades por el **nivel** de su destino y no por su precio ni por su nombre: es el único orden en el que «subir» significa algo |
| `CA-PM-088` | El sistema ofrece **los bots activos a quien no tiene membresía**, y a esa misma persona **ningún upgrade** |
| `CA-PM-089` | El sistema ofrece a quien está en el nivel más bajo **todos los upgrades declarados desde ahí**, y no solo el del nivel inmediato |
| `CA-PM-090` | El sistema devuelve el precio **sin ajuste alguno por quién mira**: dos personas de niveles distintos ven el mismo importe para el mismo producto. **Sigue siendo cierto con dos precios**: cuál de los dos se publica lo decide **el producto**, no el actor |
| `CA-PM-091` | El sistema devuelve las dos colecciones **envueltas en un objeto** y no como arreglos desnudos, de modo que añadir paginación después no rompa a ningún cliente |
| `CA-PM-095` | El sistema devuelve la **vigencia** de cada producto ofrecido, y **vacía** en los que no caducan: es lo que distingue comprar un mes de comprar para siempre |
| `CA-PM-101` | El sistema responde `403` a un actor autenticado **sin** `products:sale`, aunque tenga otros permisos de `products:` |
| `CA-PM-106` | El sistema ofrece **solo los upgrades cuyo origen es la membresía del actor**, y **no** uno declarado desde otra |
| `CA-PM-107` | Con dos upgrades desde su membresía —un paso corto y un **salto**— el sistema **ofrece los dos**: elegir es de quien compra |
| `CA-PM-108` | Un upgrade **hacia** la membresía del actor, declarado desde una inferior, **no se le ofrece**: ya está ahí |
| `CA-PM-123` | El sistema devuelve **el alcance y la implementación** de cada producto ofrecido, en los dos tipos |
| `CA-PM-124` | El sistema ofrece **los productos de los dos alcances**: uno de `TIENDA` y otro de `HOTLINKS` aparecen los dos, porque la escala es acumulativa y esta consulta **no filtra por ella** |
| `CA-PM-158` | El sistema publica **los dos importes** de un producto que declara precio público: `price` con el del sistema y `publicPrice` con el anunciado |
| `CA-PM-159` | El sistema publica `publicPrice` **nulo y presente** en un producto que no declara precio público, y `price` con el del sistema |
| ~~`CA-PM-160`~~ | ~~La respuesta trae **un solo campo de importe** por producto~~ — **retirado el 08-09-2026** al reescribirse `RN-PM-024`: trae los dos |
| `CA-PM-167` | El sistema devuelve la **conversión** de cada producto, calculada sobre el importe que se muestra, y **presente y nula** cuando no hay nada que convertir |
| `CA-PM-168` | El sistema resuelve la conversión de una página **sin una consulta por producto**: la moneda de casa una vez y las tasas en una sola sentencia |

## 13. Casos límite

- **La cadena se reordena entre dos consultas:** insertar una membresía intermedia (`RN-SP-007`) cambia los niveles de las demás. La oferta se calcula con los niveles **del momento de la consulta**; que la lista cambie de una consulta a otra sin que nadie tocara los productos es correcto y debe estar escrito.
- **Actor con membresía vigente cuyo nivel es el único de la cadena:** no hay ni arriba ni abajo; la lista de upgrades llega vacía.
- **Dos upgrades activos hacia niveles distintos, ambos superiores:** se ofrecen los dos. `RN-PM-004` acota un upgrade por **destino**, no uno en total.
- **Un upgrade se desactiva mientras el actor mira la pantalla:** la consulta siguiente ya no lo trae. No hay reserva ni bloqueo: esta consulta no promete que lo que devuelve seguirá disponible.
- **Actor sin ningún rol de consumidor pero con membresía:** `RN-SP-018` lo hace imposible. Se enumera para que quede escrito que no se defiende ese caso.
- **Un producto con precio público 0 y precio del sistema mayor:** se publica **el cero**, porque es lo que el producto declara como anuncio. La oferta no compara los dos importes ni «corrige» el que le parezca raro: quien los declara es quien decide (`requirements/pm.md` §5.2.4).
- **El precio público se corrige mientras el actor mira la pantalla:** la consulta siguiente trae el nuevo. Esta consulta no reserva ni promete nada, igual que con la desactivación de un producto.
- **Lo que esta consulta enseña puede no ser lo que la venta cobre.** Es la consecuencia aceptada del cambio del 08-09-2026, y **no se corrige aquí**: la venta cobra el precio del sistema (`RF-MV-001`, `RF-MV-002`) y el comprobante lo dice. Queda escrito para que nadie lo interprete como un defecto de esta spec.

## 14. Preguntas abiertas

Ninguna. Las cinco se resolvieron el 26-08-2026, antes de aprobar la especificación.

| # | Pregunta | Resolución |
|---|---|---|
| 1 | ¿Qué ve quien no es consumidor? | **Los bots sí, los upgrades no.** No hay nivel desde el que subir, y ofrecerle el primer upgrade sería venderle una membresía, que no es lo que un upgrade hace — quien no tiene nivel no lo obtiene comprando un salto, sino recibiendo un rol de consumidor (`RN-SP-018`). Los bots en cambio no dependen de nada suyo, y un vendedor o un funcionario también puede querer comprar uno. Se descartó devolver la oferta vacía, que habría cerrado esa venta sin motivo, y devolverlo todo, que rompería `RN-PM-011` |
| 2 | ¿Los bots dependen también del nivel? | **No: un bot activo se ofrece a todos por igual.** Es lo que el modelo ya dice —`RN-PM-002` prohíbe al bot declarar membresía—, y mantenerlo así deja la oferta explicable con una sola frase. **Lo que costaría cambiarlo queda escrito**: un «bot solo para oro» no es un filtro más, sino una **relación nueva entre producto y membresía** —nivel mínimo, o una lista de niveles—, con su tabla, su regla y una enmienda de `RN-PM-002`. El día que se pida, se pide entero |
| 3 | ¿El precio se ajusta por nivel? | **No: el precio es el del producto, igual para todos.** Un precio distinto según quién mira es un descuento, y los descuentos son **promociones**, que `requirements/pm.md` §1.3 deja fuera del alcance a propósito. Admitirlo aquí las colaría por la puerta de atrás: sin tabla donde vivir, sin vigencia que las acote y sin decidir qué precio recuerda una compra |
| 4 | ¿Se ofrecen todos los upgrades superiores o solo el siguiente? | **Todos los superiores.** Quien está en el nivel más bajo ve todos los de arriba y elige cuánto saltar; el precio de cada upgrade ya expresa el salto que da. Ofrecer solo el inmediato obligaría a comprar tres veces para recorrer una cadena de cuatro niveles, que es una fuga de ventas disfrazada de simplicidad |
| 5 | ¿Esta consulta se pagina? | **No, y la respuesta se escribe para que paginarla después no rompa nada.** Hoy la oferta es corta: los upgrades están acotados por la longitud de la cadena, y los bots activos son pocos. Lo que crece sin techo con el tiempo son los bots, de modo que el día que haya que paginarlos **la forma de la respuesta ya lo admite**: las dos colecciones viajan **envueltas en un objeto** y no como arreglos desnudos, que es la misma decisión que `RF-SP-017` tomó con la cadena de membresías. Resuelta por el responsable técnico, al no quedar ninguna decisión de negocio dentro |

---

## 15. Control de cambios

| Versión | Fecha | Cambio | Responsable |
|---|---|---|---|
| 0.2.0 | 26-08-2026 | **Aprobada**, y con ella las siete del módulo. Quien no tiene nivel **ve los servicios y ningún upgrade**; los servicios **no dependen del nivel** y su acotación por nivel queda declarada como lo que costaría —una relación nueva entre producto y membresía, no un filtro—; el **precio no se ajusta** por quién mira, porque eso sería una promoción y §1.3 las deja fuera; y se ofrecen **todos los upgrades superiores**, no solo el siguiente. La paginación se resuelve sin decidirla: no se pagina hoy, y las dos colecciones viajan **envueltas** para que añadirla después no rompa a ningún cliente. Cuatro criterios nuevos, `CA-PM-088` a `CA-PM-091`. | Responsable del proyecto |
| 0.1.0 | 26-08-2026 | Redacción inicial, con cinco preguntas abiertas. | Responsable técnico |
| 0.3.0 | 27-08-2026 | La oferta devuelve la **vigencia en días** de cada producto (`RN-PM-015`), y **vacía** en los que no caducan. Es el dato que decide una compra: sin él, dos upgrades al mismo nivel y al mismo precio son indistinguibles aunque uno dure un mes y el otro para siempre. `CA-PM-095`. **Lo que ocurre al vencer no lo decide esta consulta** y ya está escrito en `requirements/pm.md` §1.4: la persona se queda sin nivel vigente, que es exactamente el caso que `FA-003` ya contempla. | Responsable del proyecto |
| 0.4.0 | 02-09-2026 | **Esta consulta deja de comparar niveles.** Un upgrade declara ahora **de qué membresía sale** (`pm.md` §5.2.1), y la oferta pasa de ser un cálculo —«todos los que llevan por encima de mi nivel»— a una **coincidencia exacta**: los upgrades cuyo origen es mi membresía. **La regla de niveles no desaparece, se muda**: deja de evaluarse en cada consulta y se comprueba **una vez, al registrar** (`RN-PM-017`). Aquí ya no hay nada que deducir, porque quien declaró el producto ya dijo a quién va dirigido. **Y `FA-001` se conserva sin escribir una línea**: quien no tiene membresía no coincide con ningún origen, de modo que sigue sin ver upgrades — antes había que decirlo aparte, ahora sale del propio filtro. La membresía de origen **no viaja en la respuesta**: es siempre la del actor, que ya va ahí. Nacen `CA-PM-106` a `CA-PM-108`, y la última es la que más fácil se olvida: **un upgrade hacia el nivel que ya se tiene no se ofrece**, y con la coincidencia exacta eso sale solo — su origen es otro. **Lo que se paga queda escrito en cabecera**: si nadie declara un upgrade desde `VIP`, quien esté en `VIP` no ve ninguna subida, sin error y sin aviso. La cobertura de la cadena deja de ser automática. | Responsable del proyecto |
| 0.5.0 | 02-09-2026 | **Enmienda bajo Art. I.7, con el requerimiento ya implementado.** Por decisión del responsable del proyecto, esta consulta pasa a exigir `products:sale`: hasta hoy respondía a cualquier persona autenticada sin exigir nada. `CA-PM-065` **cambia de contenido y conserva su identificador** —el mismo criterio, el permiso exigido— y nace `CA-PM-101`: el `403` a quien no lo tenga, aunque porte otros permisos de `products:`. §3 se actualiza a juego. Lo que **no** cambia: sigue sin admitir parámetro de persona (`CA-PM-066`), y la oferta de un tercero sigue sin existir ni con parámetro ni con permiso (§4.2). | Responsable del proyecto |
| 0.5.0 | 07-09-2026 | **La oferta publica el alcance y la implementación, y NO filtra por ninguno de los dos** (`RN-PM-019`, `RN-PM-020`). Lo segundo es lo que hay que leer: **el alcance no puede filtrar aquí**, porque es **acumulativo** — `HOTLINKS` incluye la tienda, de modo que los dos valores llegan a esta consulta y un predicado sobre él devolvería siempre lo mismo que no ponerlo. Escribirlo «por simetría» con `RF-PM-002` habría sido peor que no escribirlo: un filtro que no filtra invita a construir sobre él una condición que nunca se cumple. **La implementación sí viaja en la respuesta**, y no por simetría tampoco: quien compra tiene que poder saber **antes de pagar** que lo que se lleva no se le entrega en el acto, y ocultarlo no evita la espera — la convierte en una incidencia de soporte. Entran `CA-PM-123` y `CA-PM-124`, y el segundo es el que **prueba que la escala no filtra**: un producto de `TIENDA` y otro de `HOTLINKS` aparecen **los dos**. | Responsable del proyecto |
| 0.6.0 | 07-09-2026 | **La oferta deja de comparar niveles y pasa a coincidir por ORIGEN**, que es lo que `requirements/pm.md` §5.2.1 declaró decidido el **02-09-2026** y nunca se construyó (`T-20`). El disparador es la **renovación**: `PM` admite ya un upgrade `X → X` (§5.2.3), y **eso no se puede expresar comparando niveles** — abrir la comparación a «inferior o igual» le ofrecería a quien está en `ORO` un `PLATINO → ORO`, que no es suyo. La coincidencia exacta lo resuelve entero: `BECA → BECA` es la renovación de quien está en `BECA`, y `PLATINO → ORO` no le aparece a nadie que no esté en `PLATINO`. **Se enmiendan tres criterios que estaban escritos en términos de nivel** —`CA-PM-059`, `CA-PM-060` y `CA-PM-062`—, y `CA-PM-106` a `CA-PM-108`, escritos el 02-09-2026 y **sin prueba hasta hoy**, pasan a estar cubiertos. Nace `CA-PM-126` para la renovación. **La garantía de que no se ofrecen bajadas NO se pierde al quitar el filtro de niveles**, y conviene que quede escrito: la sostiene `RN-PM-017` comprobada **al registrar**, porque un producto declarado desde mi membresía no puede apuntar por debajo — la regla se mudó de la consulta al alta, que es lo que aquella sección ya decía. | Responsable del proyecto |
| 0.6.0 | 07-09-2026 | **La oferta trae el COLOR de las membresías** (`RN-SP-024`): el del destino de cada upgrade **y el de la membresía vigente de quien mira**, que viaja en `currentMembership`. Es donde más se nota: la pantalla de venta pinta «estás en X, sube a Y» y hasta hoy no tenía con qué colorear ninguno de los dos. Sale de `CurrentMembershipLookup` y del `LEFT JOIN` que la consulta ya hace, **sin ninguna llamada extra**. Entra `CA-PM-144`. | Responsable del proyecto |
| 0.7.0 | 08-09-2026 | **La oferta publica UN precio, y es el que se muestra** (`RN-PM-023`, `RN-PM-024`), por decisión del responsable del proyecto: el **público** si el producto lo declara y el **del sistema** si no. **No viajan los dos, ni un indicador de cuál es**, y esa es toda la regla: publicar el par enseñaría **la diferencia entre lo que se anuncia y lo que se cobra**, que es la decisión comercial que el segundo precio existe para no enseñar. Lo que separa esta respuesta del catálogo administrativo —donde los dos sí se ven— es `products:read`. **El campo no se renombra**, y se descartó hacerlo: quien consume esta respuesta siempre ha leído «el precio que se le enseña a esta persona», y cambiarle el nombre rompería a todo cliente de la tienda por un cambio que no cambia lo que el campo significa **para quien lo lee**. Lo que sí cambia, y queda escrito en §13, es que **ese número puede no ser el que la venta cobre**: es la consecuencia aceptada de `requirements/pm.md` §5.2.4, no se corrige aquí, y el comprobante de `RF-MV-002` sí dice el importe cobrado. **`CA-PM-090` se reescribe sin cambiar de sentido**: el precio sigue sin ajustarse **por quién mira** — cuál de los dos se publica lo decide **el producto**, no el actor, de modo que dos personas de niveles distintos siguen viendo el mismo importe. Entran `CA-PM-158` a `CA-PM-160`, y el tercero prueba una **ausencia**: que la respuesta no tenga un segundo campo de importe es lo único que sostiene la regla. | Responsable del proyecto |
| 0.8.0 | 08-09-2026 | **La oferta publica los DOS precios y la conversión**, por decisión del responsable del proyecto y **sobre la advertencia de lo que cuesta** ([`requirements/pm.md`](../../../requirements/pm.md) v0.22.0 §5.2.5). Es la reversión de la enmienda 0.7.0, tomada el mismo día: `RN-PM-024` pasa de «un solo importe» a «los dos y la conversión». `CA-PM-158` y `CA-PM-159` cambian de sentido —ahora afirman que **los dos** campos llegan, con `publicPrice` **nulo y presente** cuando no se declara— y **`CA-PM-160` muere**, porque exigía justo lo contrario. Nacen `CA-PM-167`, la conversión de cada producto, y **`CA-PM-168`, que es la que tiene filo**: la oferta es una **lista**, de modo que resolver la conversión por fila sería el `N+1` que este módulo lleva seis requerimientos evitando. Se resuelve con **la moneda de casa una vez y las tasas de todas las monedas presentes en una sola sentencia**, y el criterio se mide contando sentencias, no leyendo el cuerpo — porque el cuerpo sería idéntico con veinte consultas. | Responsable del proyecto |
