# SPEC — `RF-SP-055` Consultar las cuentas de broker de una persona

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-055` |
| Módulo | `SP` — Sistema Principal |
| Estado | **Aprobada** |
| Autor | Responsable técnico |
| Aprobada por | Responsable del proyecto |
| Fecha de aprobación | 10-09-2026 |
| Enmendada | 21-09-2026 — exige **`broker-accounts:read-team-member`** (`RF-SP-062`, `RN-SEG-015`: autenticarse no autoriza nada); lo siembra `V31`, que abre la ruta; `RN-SP-046` sigue decidiendo el alcance |

---

!!! note "Enmienda de Art. I.7 — 21-09-2026, `RF-SP-062`"

    Esta operación exige **`broker-accounts:read-team-member`** desde el 21-09-2026, por `RF-SP-062` —**autenticarse no autoriza nada**, `RN-SEG-015` ([`security.md` §4.3](../../../security.md#43-reglas-de-negocio))—, por decisión del responsable del proyecto: «cada endpoint debe tener su propio permiso, ya que uso esto para saber qué vista o consulta mostrar en el front; no basta con solo tener el token». Hasta entonces se atendía con solo el token, y las líneas que abajo dicen «sin permiso» o «autenticado a secas» hablan de esa decisión original y se conservan como historia: el alcance sobre uno mismo sigue siendo exactamente el mismo, lo que cambia es que ahora tiene nombre. `V31` siembra el permiso y lo da a todo rol por su tipo, a `CONSUMIDOR` no. **Abre la ruta y no decide el alcance**: `RN-SP-046` sigue diciendo quién es visible —el subordinado directo, el cliente propio, o cualquiera para quien además porte `broker-accounts:read`— y el `404` sigue saliendo del servicio.



## 1. Objetivo

Saber qué cuentas de broker declaró una persona, en cuál las tiene y **en qué punto está cada una**.

## 2. Contexto

**`user_brokers` lleva un día escribiéndose y nadie la ha leído nunca.** La escribe el registro por enlace (`RF-SP-045 · T-19`) desde el 09-09-2026, y hasta hoy no había ninguna consulta: quien declaraba su cuenta la mandaba al sistema y no volvía a verla.

**El requerimiento nace junto a una columna nueva, y conviene decir por qué van juntas.** `RN-SP-045` añade `user_brokers.status` —`REGISTER` o `FIRST_DEPOSIT`—, y sin ella esta consulta devolvería el número de cuenta y poco más. La pregunta que hay que poder hacer no es «¿qué cuenta tiene?» sino **«¿ya depositó?»**, y esa pregunta necesita las dos cosas: el dato y quien lo lee.

**Hoy la respuesta será siempre `REGISTER`, y no es un defecto de esta consulta.** Quien mueve la cuenta a `FIRST_DEPOSIT` es el webhook del broker (`RF-SP-054`), que no está construido; mientras no lo esté, **ninguna cuenta tiene depósito confirmado** y `REGISTER` es la verdad, no un relleno. Queda escrito aquí para que nadie lo lea como un fallo de la lectura.

## 3. Actores

| Actor | Papel |
|---|---|
| **El superior comercial vigente** de la persona | Consulta las cuentas de quien depende de él |
| **Administrador** con `broker-accounts:read` | Consulta las de cualquiera |

## 4. Alcance

### 4.1 Incluye

- Devolver **todas las cuentas** de una persona: broker, identificador, nombre de usuario en el broker y **estado**.
- Autorizar por **estructura comercial vigente** o por **permiso** (`RN-SP-046`).
- Responder **`404`** —y no `403`— a quien no es ninguna de las dos cosas.

### 4.2 No incluye

- **Escribir.** Ni declarar una cuenta —la declara su titular al registrarse (`RN-SP-042`)—, ni cambiar su estado (`RF-SP-054`), ni desvincularla, que sigue sin decidirse.
- **El titular sobre sí mismo.** Decidido el 10-09-2026: esta lectura se definió sobre el equipo. El día que el cliente deba ver sus cuentas, la vía es `RF-SP-039` y su `GET /users/me` — no relajar esta.
- **Más de un nivel.** El superior del superior no ve por esta vía las cuentas del nieto (`RN-SP-046`).
- **Paginar.** Una persona tiene unas pocas cuentas, como los catálogos pequeños. El listado que sí pagina es `RF-SP-056`.
- **Filtrar.** Con dos o tres filas, filtrar es trabajo del cliente. El filtro por estado y por broker vive en `RF-SP-056`, donde las filas son muchas.

## 5. Reglas de negocio aplicables

| ID | Regla | Origen |
|---|---|---|
| `RN-SP-040` | El nombre de usuario en el broker llega DESPUÉS | `requirements/sp.md` §5.1 |
| `RN-SP-045` | Toda cuenta de broker declara en qué punto está | `requirements/sp.md` §5.1 |
| `RN-SP-046` | Las cuentas las ve el superior vigente, o quien traiga el permiso | `requirements/sp.md` §5.1 |

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción | Restricción |
|---|---|---|---|
| `id` | Sí | La persona cuyas cuentas se consultan | `uuid` en la ruta |

### 6.2 Salida

| Dato | Descripción |
|---|---|
| Cuentas | Identificador, **broker** —identificador y nombre—, **identificador de cuenta**, **nombre de usuario en el broker** —puede ser nulo— y **estado** |

**El orden lo fija el servidor**: por nombre de broker y, dentro de él, por identificador de cuenta. Es determinista a propósito — una persona con dos cuentas en el mismo broker las vería bailar entre llamadas si el orden dependiera del motor.

**`brokerUsername` viaja en nulo y el nulo significa algo** (`RN-SP-040`): «el broker todavía no lo ha confirmado». No se omite el campo ni se sustituye por una cadena vacía: las dos cosas borrarían la distinción entre «no confirmado» y «confirmado sin nombre».

## 7. Precondiciones y postcondiciones

**Precondiciones:** actor autenticado con `broker-accounts:read-team-member` — **hasta el 21-09-2026 sin permiso** (`RF-SP-062`). Y **una de las dos**: ser el superior comercial vigente de la persona, o traer `broker-accounts:read`.

**Postcondiciones:** ninguna. Es una lectura y **no audita**.

## 8. Flujo principal

1. El actor pide las cuentas de una persona.
2. El sistema comprueba que la persona existe y no está eliminada.
3. El sistema comprueba la autorización: **permiso**, o **superior vigente** de esa persona.
4. El sistema devuelve las cuentas, ordenadas.

## 9. Flujos alternativos

| ID | Caso | Comportamiento |
|---|---|---|
| `FA-001` | La persona no tiene ninguna cuenta declarada | `200` con la colección vacía. Es lo normal: solo el enlace `BECA → BECA` obliga a declararlas (`RN-SP-042`) |
| `FA-002` | La persona no existe, o está eliminada | `404` |
| `FA-003` | El actor no es el superior vigente ni trae el permiso | **`404`, el mismo de `FA-002`** — ver §10 |
| `FA-004` | El actor **fue** su superior y ya no lo es | `404`. La fila cerrada de `user_supervisors` conserva el historial y **no concede lectura**: quien deja de llevar a una persona deja de ver sus datos el mismo día |
| `FA-005` | El actor pide **sus propias** cuentas sin traer el permiso | `404`. Es la consecuencia de §4.2 y se prueba, para que relajarlo sea una decisión y no un descuido |

## 10. Seguridad

**Es la primera lectura del sistema que autoriza por estructura comercial**, y eso merece decirse entero. Hasta hoy `user_supervisors` decía a quién se atribuye cada resultado y **no concedía alcance de datos** —lo declara `V21` y lo deja abierto la **D-22**—. Aquí sí lo concede, **y solo aquí**: `RN-SP-046` lo acota a esta lectura, a un solo nivel y a este dato, y `security.md` §5 lo registra como excepción declarada en lugar de disimularla. **D-22 sigue abierta.**

**`404` donde el modelo pide `403`.** `security.md` §5 admite la excepción «cuando la especificación lo exija de forma expresa y justificada»; esta es esa justificación. El actor de esta ruta **es un vendedor cualquiera**, no un administrador: con un `403` podría recorrer identificadores y saber cuáles corresponden a personas reales. Con `404` no puede distinguir «no existe» de «no es tuya», que es el mismo razonamiento del hotlink de `RF-PM-008`.

**Quien trae el permiso sí distingue**, y no es una fuga: para él las dos respuestas significan lo mismo, y su `404` solo aparece cuando la persona de verdad no existe.

## 11. Validaciones

| Campo | Regla | Código |
|---|---|---|
| `id` | `uuid` bien formado | `VAL-001` |
| `id` | Designa una persona no eliminada | `VAL-002` |

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-SP-629` | El superior comercial **vigente** obtiene las cuentas de quien depende de él, **sin traer ningún permiso** |
| `CA-SP-630` | Cada cuenta llega con **broker, identificador de cuenta, nombre de usuario en el broker y estado** |
| `CA-SP-631` | El **estado** de una cuenta recién declarada es **`REGISTER`** (`RN-SP-045`) |
| `CA-SP-632` | `brokerUsername` llega **en nulo** mientras el broker no lo haya confirmado, y el campo **está presente** (`RN-SP-040`) |
| `CA-SP-633` | Quien trae `broker-accounts:read` obtiene las cuentas de **cualquiera**, sin ser su superior |
| `CA-SP-634` | Quien **no** es el superior vigente ni trae el permiso recibe **`404`**, indistinguible del de una persona inexistente |
| `CA-SP-635` | Quien **fue** superior y ya no lo es recibe **`404`** |
| `CA-SP-636` | Una persona **sin cuentas** devuelve `200` con la colección vacía, no `404` |

## 13. Casos límite

| Caso | Decisión |
|---|---|
| Dos cuentas de la misma persona en el mismo broker | Salen **las dos**. Es lo normal en el ramo y `RN-SP-038` lo admite expresamente |
| La persona tiene cuenta en un broker **apagado** | Sale igual. Apagar un broker deja de ofrecerlo, no borra lo declarado (`RF-SP-052` §13) |
| El actor pide sus propias cuentas **siendo** superior de otros | `404` igual: ser superior de terceros no le hace superior de sí mismo |
| El actor es el superior y la persona está **eliminada** | `404`, por `FA-002`. La eliminación lógica retira a la persona de toda lectura |

## 14. Preguntas abiertas resueltas

| # | Pregunta | Resolución |
|---|---|---|
| 1 | ¿`403` o `404` para quien no es el superior? | **`404`** (10-09-2026). Un `403` convertiría la ruta en un oráculo de identificadores para cualquier vendedor |
| 2 | ¿El titular ve sus propias cuentas? | **No** (10-09-2026, responsable del proyecto). La lectura se definió sobre el equipo; abrirla al titular es otra decisión y tiene otra vía |
| 3 | ¿Se pagina? | **No.** Una persona tiene unas pocas cuentas. Lo que se pagina es el listado del equipo (`RF-SP-056`) |
| 4 | ¿Hace falta un permiso propio, o basta `users:read`? | **Propio: `broker-accounts:read`** (10-09-2026). `users:read` gobierna quién puede leer personas, y una cuenta de broker es un dato de otra naturaleza |

## 15. Control de cambios

| Versión | Fecha | Cambio | Responsable |
|---|---|---|---|
| 0.1.0 | 10-09-2026 | Redacción inicial. Nace con `RN-SP-045` —el estado de la cuenta— y `RN-SP-046` —quién la ve—, y **lo que carga la especificación no es el campo sino la autorización**: es la primera lectura del sistema que se resuelve contra `user_supervisors`, el caso que la nota de **D-22** llevaba desde el 22-08-2026 señalando como riesgo. Se declara como excepción acotada y **D-22 no se cierra**. Segunda decisión que carga: **`404` y no `403`** para quien no es el superior, con la justificación expresa que `security.md` §5 exige. | Responsable del proyecto |
