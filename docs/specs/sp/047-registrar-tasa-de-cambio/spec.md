# SPEC — `RF-SP-047` Registrar una tasa de cambio

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-047` |
| Módulo | `SP` — Sistema Principal |
| Estado | **Aprobada** |
| Autor | Responsable técnico |
| Aprobada por | Responsable del proyecto |
| Fecha de aprobación | 07-09-2026 |

---

## 1. Objetivo

Poner en el sistema **a cuánto se cambia una moneda por otra, y desde cuándo**.

Hoy el sistema tiene monedas y no tiene forma de relacionarlas. `movements` exige que una venta vaya en **una sola moneda** (`RN-MV-012`) y el motivo escrito de esa regla es literalmente que «este sistema no tiene ninguna tasa de cambio». Este requerimiento pone ese objeto.

## 2. Contexto

**Una tasa es un hecho con vigencia, no un atributo de la moneda.** Por eso no es una columna de `currencies`: el cambio de hoy no es el de la semana pasada, y la pregunta que se hace a diario —*¿a cuánto está?*— lleva una fecha implícita que una columna no puede responder.

**Y por eso se administra por API**, al revés que el catálogo de monedas. `RN-SP-010` deja las monedas fuera del alcance de la API porque son un catálogo estable que nadie edita; una tasa es lo contrario — cambia, y cambia seguido. Meterla en una migración obligaría a desplegar para corregir un número.

## 3. Actores

| Actor | Papel |
|---|---|
| Administrador | Declara las tasas y su vigencia |

**No lo hace el superadministrador en exclusiva**, al revés que `currencies:update`. Administrar a cuánto se cambia una moneda es administración ordinaria: la reserva de la raíz existe para lo que condiciona todo cálculo financiero de forma irreversible, y una tasa mal puesta se corrige (`RF-SP-049`) o se retira (`RF-SP-050`).

## 4. Alcance

### 4.1 Incluye

- Registrar una tasa declarando **origen, destino, precio y desde cuándo rige**.
- Declarar la fecha de fin, **opcional**: sin ella la tasa es **vitalicia**.
- Declarar si nace **activa**, que es lo normal.
- Rechazar el **solapamiento** con otra tasa vigente del mismo par (`RN-SP-032`).
- Sembrar los **cuatro permisos** del submódulo y asociarlos a `SUPERADMIN` y `ADMIN`.

### 4.2 No incluye

- **Convertir nada.** Ninguna operación del sistema consume esta tasa todavía, y `RN-MV-012` sigue exigiendo una sola moneda por venta. Ver §13.
- **Tasas inversas automáticas.** Declarar `USD → COP` **no** crea `COP → USD`. Ver §14, resolución 3.
- **Histórico de quién cambió qué**: vive en la auditoría de cambios, como en todo el sistema.
- **Importar tasas de un proveedor externo.** El sistema no llama a nadie: la tasa la teclea una persona.

## 5. Reglas de negocio aplicables

| ID | Regla | Origen |
|---|---|---|
| `RN-SP-029` | Origen y destino existen, están activas y **no son la misma** | `requirements/sp.md` §5.1 |
| `RN-SP-030` | El precio es mayor que cero | `requirements/sp.md` §5.1 |
| `RN-SP-031` | La vigencia empieza siempre y puede no terminar | `requirements/sp.md` §5.1 |
| `RN-SP-032` | **Dos tasas vigentes del mismo par no se solapan** | `requirements/sp.md` §5.2 |

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción | Restricción de negocio |
|---|---|---|---|
| Moneda de origen | Sí | De qué moneda se parte | Debe existir y estar **activa**; distinta del destino (`RN-SP-029`) |
| Moneda de destino | Sí | A qué moneda se llega | Mismas condiciones |
| Precio | Sí | Cuántas unidades de destino da **una** de origen | Mayor que cero, con hasta **ocho decimales** (`RN-SP-030`) |
| Fecha de inicio | **Sí** | Desde qué día rige | Una fecha, sin hora |
| Fecha de fin | No | Hasta qué día rige | **Sin ella la tasa es vitalicia.** Si se declara, no puede ser anterior al inicio (`RN-SP-031`) |
| Estado | No | Si la tasa rige o está suspendida | Por omisión **activa**, que es el caso normal |

**El precio se lee en una sola dirección, y hay que decirlo**: es **cuántas unidades de la moneda de destino da una unidad de la de origen**. `USD → COP` a `4150` significa que un dólar da cuatro mil ciento cincuenta pesos. La dirección contraria **no se deduce**: es otra tasa (§14, resolución 3).

### 6.2 Salida

| Dato | Descripción |
|---|---|
| Tasa registrada | Identificador, precio, vigencia y estado |
| Monedas resueltas | Origen y destino con su **código, nombre y decimales**, y no solo sus identificadores: una tasa que obliga a una segunda llamada para leerse no sirve |

## 7. Precondiciones y postcondiciones

**Precondiciones**

- El actor está autenticado y porta `exchange-rates:create`.
- Las dos monedas existen y están activas.

**Postcondiciones**

- Existe una fila en `exchange_rates` con lo declarado.
- Se registró un evento de **creación** en la auditoría de cambios, con el estado inicial completo.
- **Ninguna otra fila cambió.** Registrar una tasa no cierra la anterior ni la desactiva: si la nueva se solapa con una vigente, la operación **se rechaza** en lugar de resolver el conflicto por su cuenta. Ver §13.

## 8. Flujo principal

1. El actor envía origen, destino, precio y vigencia.
2. El sistema valida la forma de lo recibido.
3. El sistema comprueba que **las dos monedas existen y están activas**, y que no son la misma.
4. El sistema comprueba que el precio **cabe en ocho decimales** y es mayor que cero.
5. El sistema comprueba que la vigencia es coherente: el fin, si viene, no es anterior al inicio.
6. El sistema **inserta**. Si la fila se solapa con otra vigente del mismo par, el motor la rechaza y el sistema traduce ese rechazo a `EX-002`.
7. El sistema registra el evento de creación y devuelve la tasa con sus dos monedas resueltas.

## 9. Flujos alternativos

### FA-001 — Tasa vitalicia

**Condición:** no se declara fecha de fin.
**Comportamiento:** la tasa rige **indefinidamente**. Es un estado normal y no un dato que falte: la respuesta devuelve la fecha de fin **vacía y presente**, nunca ausente.

### FA-002 — Tasa que empieza en el futuro

**Condición:** la fecha de inicio es posterior a hoy.
**Comportamiento:** **se admite**, y es el caso que hace innecesario un tercer estado. Una tasa programada es una tasa activa cuya vigencia todavía no llegó; quien pregunte «a cuánto está hoy» no la verá, y el día que llegue empezará a regir sin que nadie toque nada.

### FA-003 — La primera tasa del par

**Condición:** no existe ninguna tasa para ese origen y ese destino.
**Comportamiento:** el camino normal. `RN-SP-032` no tiene con qué chocar.

## 10. Excepciones

### EX-001 — Moneda inexistente o inactiva

**Condición:** el origen o el destino no existen, o existen y están **desactivadas**.
**Respuesta del sistema:** rechaza la operación diciendo **cuál de las dos** falla y **si es que no existe o es que no está activa**. Son dos diagnósticos distintos: uno es un dato equivocado y el otro una decisión del sistema que el actor no puede saltarse.

### EX-002 — Se solapa con una tasa vigente

**Condición:** ya existe una tasa **activa y viva** del mismo par cuya vigencia toca a la que se declara.
**Respuesta del sistema:** rechaza la operación como **conflicto** y **no registra nada**, nombrando el periodo que choca. No desactiva la anterior ni la recorta: resolver el conflicto es una decisión del actor, no del sistema (§13).

## 11. Validaciones

| ID | Validación | Mensaje esperado |
|---|---|---|
| `VAL-001` | Moneda de origen obligatoria | La moneda de origen es obligatoria. |
| `VAL-002` | Moneda de destino obligatoria | La moneda de destino es obligatoria. |
| `VAL-003` | Origen y destino distintos | La moneda de origen y la de destino no pueden ser la misma. |
| `VAL-004` | Precio obligatorio y mayor que cero | El precio de la tasa debe ser mayor que cero. |
| `VAL-005` | Decimales del precio | El precio admite como mucho ocho decimales. |
| `VAL-006` | Fecha de inicio obligatoria | La fecha de inicio de la vigencia es obligatoria. |
| `VAL-007` | Fin no anterior al inicio | La fecha de fin no puede ser anterior a la de inicio. |

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-SP-530` | El sistema registra una tasa con sus dos monedas **resueltas** —código, nombre y decimales— y no solo sus identificadores |
| `CA-SP-531` | El sistema **admite una tasa sin fecha de fin**, y esa tasa rige indefinidamente: el campo llega **vacío y presente** |
| `CA-SP-532` | El sistema rechaza una tasa **sin fecha de inicio** |
| `CA-SP-533` | El sistema rechaza una fecha de fin **anterior** a la de inicio, y **admite** que sean el mismo día |
| `CA-SP-534` | El sistema rechaza un precio de **cero o negativo** |
| `CA-SP-535` | El sistema **conserva los ocho decimales** del precio: una tasa de `0,00024096` no se guarda redondeada |
| `CA-SP-536` | El sistema rechaza una tasa cuyo origen **es** el destino |
| `CA-SP-537` | El sistema rechaza una moneda **inexistente**, y lo distingue de una **desactivada** |
| `CA-SP-538` | El sistema rechaza una tasa que **se solapa** con otra vigente del mismo par, **y no registra nada** |
| `CA-SP-539` | El sistema **admite** dos tasas del mismo par cuyas vigencias **se tocan sin solaparse**: una termina el día antes de que la otra empiece |
| `CA-SP-540` | El sistema **admite** `USD → COP` y `USD → EUR` vigentes a la vez: lo que la regla acota es el **par**, no el origen |
| `CA-SP-541` | El sistema registra en la auditoría de cambios un evento de creación con el estado inicial completo |
| `CA-SP-542` | El sistema rechaza el alta a un actor sin `exchange-rates:create`, y no registra nada |

## 13. Casos límite

| Caso | Decisión |
|---|---|
| Se declara una tasa que se solapa con la vigente | **Se rechaza**, y el sistema **no** cierra la anterior por su cuenta. Cerrarla sería tomar una decisión de negocio en nombre de quien no la pidió, y dejaría una tasa modificada sin que nadie lo hubiera ordenado. Quien quiera reemplazarla, corrige el fin de la vigente (`RF-SP-049`) y registra la nueva |
| Dos altas simultáneas del mismo par y periodo | Las dos pasan la comprobación previa y **el `EXCLUDE` decide**: una queda y la otra recibe `409`. La verificación previa existe para el mensaje; la garantía es la restricción |
| Una tasa que empieza y termina el mismo día | **Se admite.** Rige un solo día, que es un periodo legítimo |
| Se desactiva una moneda que ya tiene tasas | Las tasas **siguen existiendo y rigiendo**. La validación es del momento del registro, no permanente — mismo criterio que `RN-PM-008` con los productos |
| Nadie consume la tasa todavía | **Correcto y declarado.** Este requerimiento crea el dato; convertir con él es una decisión que no se ha tomado (`requirements/mv.md` v0.11.0) |

## 14. Preguntas abiertas

| # | Pregunta | Resolución |
|---|---|---|
| 1 | ¿El estado se recibe en el alta, como no lo hace `RF-PM-001`? | **Sí, opcional y por omisión activa.** Aquel no lo recibe porque `RN-PM-012` obliga a que todo producto nazca inactivo, y eso hace que `RN-PM-004` viva en un solo sitio. Aquí no hay una regla equivalente, y una tasa que naciera suspendida obligaría a una segunda operación para servir de algo. **El coste se acepta**: el alta puede violar `RN-SP-032` y tiene que traducirlo, exactamente igual que la corrección |
| 2 | ¿Hace falta un estado «programada»? | **No.** Lo expresan las fechas: una tasa cuya vigencia empieza mañana ya es una tasa programada (`FA-002`). Un tercer estado sería un segundo sitio donde decir lo mismo, y los dos podrían discrepar |
| 3 | ¿Declarar `USD → COP` crea la inversa `COP → USD`? | **No.** El sistema no la deduce, y no por pereza: la inversa aritmética casi nunca es la tasa real —hay diferencial entre compra y venta—, y calcularla produciría un número plausible y falso. Quien necesite las dos direcciones declara **dos tasas** |
| 4 | ¿La fecha de inicio puede ser pasada? | **Sí.** Registrar hoy la tasa que rigió la semana pasada es corriente al poner el sistema al día, y prohibirlo obligaría a tocar la base a mano. Lo que sigue sin poder pasar es que **se solape** con otra |

## 15. Control de cambios

| Versión | Fecha | Cambio | Responsable |
|---|---|---|---|
| 0.1.0 | 07-09-2026 | Redacción inicial, con las cuatro preguntas resueltas en el mismo pase. Es la primera tripleta del submódulo de tasas de cambio y la que **crea la tabla y siembra los permisos**. Las dos decisiones que carga: **el estado SÍ se recibe en el alta** —al revés que `RF-PM-001`, y con el coste declarado de que el alta pueda violar `RN-SP-032`— y **la inversa no se deduce**, porque la inversa aritmética de una tasa casi nunca es la tasa real y calcularla produciría un número plausible y falso. `EX-002` deja escrito además lo que el sistema **no** hace al detectar un solapamiento: no cierra la tasa anterior, porque sería tomar una decisión de negocio que nadie pidió. | Responsable del proyecto |
