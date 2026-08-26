# SPEC — `RF-PM-007` Consultar la oferta disponible para uno mismo

| Campo | Valor |
|---|---|
| Requerimiento | `RF-PM-007` |
| Módulo | `PM` — Productos y Mercadeo |
| Estado | **Aprobada** |
| Autor | Responsable técnico |
| Aprobada por | Responsable del proyecto |
| Fecha de aprobación | 26-08-2026 |

---

## 1. Objetivo

Que cada persona vea qué puede comprar, sin que sea el navegador quien decida la regla.

## 2. Contexto

El catálogo de `RF-PM-002` lo lee quien administra y contiene todo. Lo que un cliente puede comprar es **un subconjunto que depende de él**: los upgrades solo tienen sentido hacia niveles por encima del suyo, y ofrecerle uno hacia el nivel que ya tiene —o hacia uno inferior— es ofrecerle pagar por nada.

**Esa regla vive en el servidor o no vive.** Si la interfaz filtrara el catálogo por su cuenta, cada pantalla que muestre productos tendría que repetir el mismo cálculo, y la que se quedara atrás **no fallaría: ofrecería de más**. Es la misma decisión que `RF-SP-039` tomó al publicar los permisos efectivos del actor en lugar de dejar que el navegador los dedujera.

**No admite parámetro de persona.** Responde sobre quien llama y sobre nadie más. Un parámetro convertiría esta consulta en «qué puede comprar fulano», que es una pregunta sobre un tercero y que hoy nadie ha decidido quién puede hacer.

## 3. Actores

| Actor | Rol en esta funcionalidad |
|---|---|
| Cualquier persona autenticada | Consulta lo que ella misma puede comprar. **No exige permiso**: exigir el de lectura de productos daría a cada cliente el catálogo entero para que pudiera ver tres líneas |

## 4. Alcance

### 4.1 Incluye

- Devolver los productos **activos** que el actor puede comprar hoy.
- De los upgrades, **todos los que llevan a un nivel superior** al que el actor tiene, y no solo el inmediato: quien está en el nivel más bajo ve todos los de arriba y elige cuánto saltar.
- **Todos los servicios activos, para cualquiera**: no dependen del nivel de quien mira, ni siquiera de que tenga uno.
- Devolverlos **agrupados por tipo**: los upgrades por nivel destino, los servicios por fecha de alta. Fijado el 26-08-2026 al aprobar `RF-PM-002`, que dejó dicho que las dos consultas tienen órdenes distintos porque responden a actores distintos.

### 4.2 No incluye

- **Comprar.** Esta consulta no inicia ninguna compra ni reserva nada.
- **La oferta de un tercero.** Ni con parámetro, ni con permiso: no existe.
- **Los productos inactivos o retirados**, ni el motivo por el que se retiraron.
- **El catálogo completo**, que es `RF-PM-002` y exige permiso.
- **Cualquier ajuste del precio por nivel.** Un precio distinto según quién mira es un descuento, y los descuentos son promociones, que §1.3 de `requirements/pm.md` deja fuera a propósito. Resuelto el 26-08-2026.
- **Servicios acotados por nivel.** Hoy ningún servicio declara membresía —`RN-PM-002` se lo prohíbe—, y acotarlos exigiría una relación nueva entre producto y membresía, no un filtro más.

## 5. Reglas de negocio aplicables

| ID | Regla | Origen |
|---|---|---|
| `RN-PM-009` | Solo se ofrece lo activo | `requirements/pm.md` §5.1 |
| `RN-PM-011` | Un upgrade se ofrece solo hacia arriba | `requirements/pm.md` §5.1 |
| `RN-SP-018` | Todo consumidor tiene membresía | `requirements/sp.md` §5.1 |

## 6. Datos

### 6.1 Entrada

**Ninguna.** La consulta no admite parámetros: ni de persona, ni de filtro, ni de paginación. El actor sale del token de la sesión.

### 6.2 Salida

| Dato | Descripción |
|---|---|
| Productos ofrecibles | Identificador, código, tipo, nombre, descripción y precio con su moneda. **El precio es el del producto**, sin ajuste por nivel |
| Orden | **Agrupados por tipo**: primero los upgrades ordenados por **nivel destino**, después los servicios por fecha de alta |
| Membresía destino | En los upgrades: código, nombre y **nivel**, para que quien mira entienda a dónde sube |
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
4. De los upgrades, conserva solo aquellos cuyo destino está en un nivel **superior** al del actor.
5. El sistema agrupa el resultado por tipo: los upgrades por nivel destino, los servicios por fecha de alta.
6. El sistema devuelve los productos resultantes junto con el nivel actual del actor.

## 9. Flujos alternativos

### FA-001 — El actor no tiene membresía

**Cuándo ocurre:** quien consulta no es consumidor —un funcionario, un vendedor— y por tanto no tiene nivel (`RN-SP-018`).

1. **No se le ofrece ningún upgrade**: no hay nivel desde el que subir, y ofrecerle el primero sería venderle una membresía, que no es lo que un upgrade hace.
2. Los servicios activos **sí** se le ofrecen: un vendedor o un funcionario también puede querer comprar un servicio de la plataforma, y nada en el producto lo impide.

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
| `CA-PM-059` | El sistema ofrece a un actor de nivel intermedio **solo los upgrades hacia niveles superiores** al suyo |
| `CA-PM-060` | El sistema **no ofrece** el upgrade hacia el nivel que el actor ya tiene |
| `CA-PM-061` | El sistema **no ofrece** upgrades hacia niveles inferiores al del actor |
| `CA-PM-062` | El sistema devuelve la lista de upgrades vacía a quien está en el nivel más alto de la cadena |
| `CA-PM-063` | El sistema no ofrece ningún upgrade a quien no tiene membresía vigente, incluida la vencida |
| `CA-PM-064` | El sistema devuelve el nivel actual del actor junto con su oferta |
| `CA-PM-065` | El sistema responde **sin exigir ningún permiso**, a cualquier persona autenticada |
| `CA-PM-066` | El sistema **no admite ningún parámetro**: enviarlos no cambia la respuesta ni permite consultar la oferta de otra persona |
| `CA-PM-067` | El sistema no devuelve el motivo de retiro de ningún producto, ni la membresía de terceros |
| `CA-PM-078` | El sistema devuelve la oferta **agrupada por tipo**, con los upgrades ordenados por nivel destino y los servicios por fecha de alta |
| `CA-PM-079` | El sistema ordena los upgrades por el **nivel** de su destino y no por su precio ni por su nombre: es el único orden en el que «subir» significa algo |
| `CA-PM-088` | El sistema ofrece **los servicios activos a quien no tiene membresía**, y a esa misma persona **ningún upgrade** |
| `CA-PM-089` | El sistema ofrece a quien está en el nivel más bajo **todos los upgrades superiores**, y no solo el del nivel inmediato |
| `CA-PM-090` | El sistema devuelve el **precio del producto sin ajuste alguno**: dos personas de niveles distintos ven el mismo importe para el mismo producto |
| `CA-PM-091` | El sistema devuelve las dos colecciones **envueltas en un objeto** y no como arreglos desnudos, de modo que añadir paginación después no rompa a ningún cliente |

## 13. Casos límite

- **La cadena se reordena entre dos consultas:** insertar una membresía intermedia (`RN-SP-007`) cambia los niveles de las demás. La oferta se calcula con los niveles **del momento de la consulta**; que la lista cambie de una consulta a otra sin que nadie tocara los productos es correcto y debe estar escrito.
- **Actor con membresía vigente cuyo nivel es el único de la cadena:** no hay ni arriba ni abajo; la lista de upgrades llega vacía.
- **Dos upgrades activos hacia niveles distintos, ambos superiores:** se ofrecen los dos. `RN-PM-004` acota un upgrade por **destino**, no uno en total.
- **Un upgrade se desactiva mientras el actor mira la pantalla:** la consulta siguiente ya no lo trae. No hay reserva ni bloqueo: esta consulta no promete que lo que devuelve seguirá disponible.
- **Actor sin ningún rol de consumidor pero con membresía:** `RN-SP-018` lo hace imposible. Se enumera para que quede escrito que no se defiende ese caso.

## 14. Preguntas abiertas

Ninguna. Las cinco se resolvieron el 26-08-2026, antes de aprobar la especificación.

| # | Pregunta | Resolución |
|---|---|---|
| 1 | ¿Qué ve quien no es consumidor? | **Los servicios sí, los upgrades no.** No hay nivel desde el que subir, y ofrecerle el primer upgrade sería venderle una membresía, que no es lo que un upgrade hace — quien no tiene nivel no lo obtiene comprando un salto, sino recibiendo un rol de consumidor (`RN-SP-018`). Los servicios en cambio no dependen de nada suyo, y un vendedor o un funcionario también puede querer comprar uno. Se descartó devolver la oferta vacía, que habría cerrado esa venta sin motivo, y devolverlo todo, que rompería `RN-PM-011` |
| 2 | ¿Los servicios dependen también del nivel? | **No: un servicio activo se ofrece a todos por igual.** Es lo que el modelo ya dice —`RN-PM-002` prohíbe al servicio declarar membresía—, y mantenerlo así deja la oferta explicable con una sola frase. **Lo que costaría cambiarlo queda escrito**: un «servicio solo para oro» no es un filtro más, sino una **relación nueva entre producto y membresía** —nivel mínimo, o una lista de niveles—, con su tabla, su regla y una enmienda de `RN-PM-002`. El día que se pida, se pide entero |
| 3 | ¿El precio se ajusta por nivel? | **No: el precio es el del producto, igual para todos.** Un precio distinto según quién mira es un descuento, y los descuentos son **promociones**, que `requirements/pm.md` §1.3 deja fuera del alcance a propósito. Admitirlo aquí las colaría por la puerta de atrás: sin tabla donde vivir, sin vigencia que las acote y sin decidir qué precio recuerda una compra |
| 4 | ¿Se ofrecen todos los upgrades superiores o solo el siguiente? | **Todos los superiores.** Quien está en el nivel más bajo ve todos los de arriba y elige cuánto saltar; el precio de cada upgrade ya expresa el salto que da. Ofrecer solo el inmediato obligaría a comprar tres veces para recorrer una cadena de cuatro niveles, que es una fuga de ventas disfrazada de simplicidad |
| 5 | ¿Esta consulta se pagina? | **No, y la respuesta se escribe para que paginarla después no rompa nada.** Hoy la oferta es corta: los upgrades están acotados por la longitud de la cadena, y los servicios activos son pocos. Lo que crece sin techo con el tiempo son los servicios, de modo que el día que haya que paginarlos **la forma de la respuesta ya lo admite**: las dos colecciones viajan **envueltas en un objeto** y no como arreglos desnudos, que es la misma decisión que `RF-SP-017` tomó con la cadena de membresías. Resuelta por el responsable técnico, al no quedar ninguna decisión de negocio dentro |

---

## 15. Control de cambios

| Versión | Fecha | Cambio | Responsable |
|---|---|---|---|
| 0.2.0 | 26-08-2026 | **Aprobada**, y con ella las siete del módulo. Quien no tiene nivel **ve los servicios y ningún upgrade**; los servicios **no dependen del nivel** y su acotación por nivel queda declarada como lo que costaría —una relación nueva entre producto y membresía, no un filtro—; el **precio no se ajusta** por quién mira, porque eso sería una promoción y §1.3 las deja fuera; y se ofrecen **todos los upgrades superiores**, no solo el siguiente. La paginación se resuelve sin decidirla: no se pagina hoy, y las dos colecciones viajan **envueltas** para que añadirla después no rompa a ningún cliente. Cuatro criterios nuevos, `CA-PM-088` a `CA-PM-091`. | Responsable del proyecto |
| 0.1.0 | 26-08-2026 | Redacción inicial, con cinco preguntas abiertas. | Responsable técnico |
