# SPEC — `RF-SP-059` Consultar los vendedores de un cliente

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-059` |
| Módulo | `SP` — Sistema Principal |
| Estado | **Aprobada** |
| Autor | Responsable técnico |
| Aprobada por | Responsable del proyecto |
| Fecha de aprobación | 18-09-2026 |
| Enmendada | 24-09-2026 — cada vendedor llega con **identificador y correo** (§10). La premisa de la decisión contraria se rompió: `RF-MV-016` estrenó una ruta que consume `sellerId` |
| Enmendada | 21-09-2026 — `GET /users/{id}/sellers` exige **`users:read-sellers`** y no `users:read` (`RF-SP-060`, `RN-SEG-014`); lo siembra `V29`. `CA-SP-705` y la resolución 6 nombran el permiso nuevo |
| Enmendada | 21-09-2026 — exige **`users:read-own-sellers`** (`RF-SP-062`, `RN-SEG-015`: autenticarse no autoriza nada); lo siembra `V31` (la ruta `/me`) |

---

!!! note "Enmienda de Art. I.7 — 21-09-2026, `RF-SP-062`"

    Esta operación exige **`users:read-own-sellers`** desde el 21-09-2026, por `RF-SP-062` —**autenticarse no autoriza nada**, `RN-SEG-015` ([`security.md` §4.3](../../../security.md#43-reglas-de-negocio))—, por decisión del responsable del proyecto: «cada endpoint debe tener su propio permiso, ya que uso esto para saber qué vista o consulta mostrar en el front; no basta con solo tener el token». Hasta entonces se atendía con solo el token, y las líneas que abajo dicen «sin permiso» o «autenticado a secas» hablan de esa decisión original y se conservan como historia: el alcance sobre uno mismo sigue siendo exactamente el mismo, lo que cambia es que ahora tiene nombre. `V31` siembra el permiso y lo da a todo rol por su tipo.



!!! note "Enmienda de Art. I.7 — 21-09-2026, `RF-SP-060`"

    `GET /users/{id}/sellers` exige **`users:read-sellers`** y no `users:read` desde el 21-09-2026, por `RF-SP-060` —**un permiso por operación**, `RN-SEG-014` ([`security.md` §4.4](../../../security.md#44-catalogo-de-permisos))—. Esta tripleta se redactó el 18-09-2026 en `feature/vendedores-de-un-cliente` con `users:read`, el día antes de que `RF-SP-060` naciera en `feature/academia`; al integrarla, `users:read` ya gobierna solo el detalle de una persona y esta operación recibe código propio. `RF-SP-060` lo dejó dicho en su spec §6.2 («`RF-SP-059`, pendiente, nacerá con `users:read-sellers`») pero no lo sembró en `V28`, porque la ruta no existía en su rama: lo siembra **`V29`**, con el mismo criterio —a `SUPERADMIN` y a `ADMIN` explícitamente, a `CLIENTE` no— y el catálogo pasa a **ciento doce**. El argumento original de §9 —«los vendedores son un dato de la persona y por eso no tienen permiso propio»— es exactamente el que `RN-SEG-014` deja de aceptar, y se conserva abajo como historia.

## 1. Objetivo

Que un cliente sepa **quiénes son sus vendedores** —quién lo registró, que es su principal, y quiénes le vendieron por su enlace— y que administración pueda verlo de cualquiera.

## 2. Contexto

**La pregunta la hizo el responsable del proyecto con sus palabras: «quiero saber a qué agentes estoy asignado».** Y la respuesta que el sistema podía dar hasta hoy era a medias: `GET /users/me` (`RF-SP-039`) devolvía un `supervisor`, que para un cliente era el agente que lo registró, y nada más — ni el origen de esa relación, ni los demás vendedores que `RN-SP-049` admite desde el 16-09-2026.

**El requerimiento nació el 16-09-2026 con `RN-SP-049` y se rediseñó el 18-09-2026, y el rediseño es lo que carga esta especificación.** Hasta hoy el cliente colgaba de su vendedor en `user_supervisors` (`RN-SP-028`, 01-09-2026), la misma tabla donde un agente cuelga de su director, y `client_sellers` iba a ser un complemento —los vinculados por hotlink— que crearía `RF-MV-011`. **La decisión del 18-09-2026 invierte eso**: el cliente **sale** de `user_supervisors`, que vuelve a significar solo mando dentro de la fuerza comercial, y `client_sellers` pasa a ser **la única** relación entre un cliente y sus vendedores. El principal es la fila con origen `REGISTRO` —quien lo registró— y **no se cambia**: no hay operación que lo reasigne ni historial que cerrar, porque quién trajo a una persona no deja de ser cierto.

**Por eso este requerimiento trae una migración, y no es lo habitual en una lectura.** Alguien tiene que crear `client_sellers` y mover lo que ya existe, y el primero que la lee es el lugar natural: `RF-MV-011`, que iba a crearla, no tiene ni tripleta, y sin la tabla ninguna de las cinco lecturas que resuelven «el vendedor de un cliente» puede cambiar de sitio. La migración `V20` crea la tabla, copia cada fila vigente de cliente como `REGISTRO` y **borra** de `user_supervisors` todas las de clientes, vigentes y cerradas.

**Hoy la lista tendrá un solo elemento, y no es un defecto de esta consulta.** Las filas `HOTLINK` las escribirán `RF-MV-011` y `RF-MV-013`, que no existen; mientras no existan, el único vendedor de un cliente es quien lo registró, y **el contrato ya es el definitivo**: el día que lleguen los vinculados, no cambia un campo.

**Lo que cuesta el rediseño está escrito en `RN-SP-028`** y no se repite aquí: subir de un cliente a su manager deja de ser un recorrido de una tabla para ser un salto —`client_sellers`— y luego el recorrido. Lo que se gana también: `RF-SP-042` deja de mezclar equipo y cartera, y `RN-SP-022` deja de hacer irretirable a quien haya registrado a alguien.

## 3. Actores

| Actor | Papel |
|---|---|
| **El propio cliente** con `users:read-own-sellers` | Consulta sus vendedores por `GET /users/me/sellers` — **hasta el 21-09-2026 sin permiso** (`RF-SP-062`) |
| **Administrador** con `users:read-sellers` | Consulta los de cualquiera por `GET /users/{id}/sellers` |

## 4. Alcance

### 4.1 Incluye

- Devolver **todos los vendedores** de un cliente, con el origen del vínculo, su fecha y **cuál es el principal**, principal primero.
- La migración `V20`: crear `client_sellers` con las restricciones de `requirements/sp.md` §10.19 —incluido el índice único parcial que garantiza **un** `REGISTRO` por cliente—, **mover** las filas de clientes desde `user_supervisors` y borrarlas allí.
- Que `RF-SP-045` **escriba `client_sellers`** en lugar de `user_supervisors` al registrar por enlace, con la venta del enlace como `first_movement_id`.
- Que las lecturas que resuelven «el vendedor de un cliente» lo hagan en la tabla nueva: la autorización de `RF-SP-055` y el equipo de `RF-SP-056` (`RN-SP-046`), la red en profundidad de `RF-SP-057` (`RN-SP-047`), los indicadores de `RF-SP-058` (`RN-SP-048`) y la atribución de la venta de `RF-MV-001` y `RF-MV-002` (`RN-MV-003`).
- Que la semilla de desarrollo cuelgue a `cliente1`, `cliente2` y `cliente3` en `client_sellers` y no en `user_supervisors`.

### 4.2 No incluye

- **Las filas `HOTLINK`.** Las escriben `RF-MV-011` y `RF-MV-013`, por la interfaz que `SP` publique entonces. Esta lectura las devuelve el día que existan sin cambiar.
- **Cambiar el principal.** No es que se posponga: **no existe** (`RN-SP-049`). Un cliente registrado con el enlace equivocado es un dato a corregir a mano, y quedó declarado así el 18-09-2026.
- **Quitar un vínculo.** Un vínculo es un hecho.
- **La lectura inversa** —los clientes de un vendedor—, que es `RF-SP-061`, pendiente (nació como `RF-SP-060` y se renumeró el 21-09-2026 al integrar sobre `feature/academia`, donde `RF-SP-060` es «un permiso por operación»).
- **Paginar ni filtrar.** Un cliente tiene un puñado de vendedores, como una persona tiene un puñado de cuentas de broker (`RF-SP-055` §4.2).
- **D-22.** Ninguna de las dos rutas se autoriza por estructura: una es alcance sobre uno mismo y la otra es un permiso.

## 5. Reglas de negocio aplicables

| ID | Regla | Origen |
|---|---|---|
| `RN-SP-027` | Ningún cliente se registra sin vendedor | `requirements/sp.md` §5.1 |
| `RN-SP-028` | **Revertida el 18-09-2026.** El cliente no cuelga de la estructura comercial: sus vendedores viven en `client_sellers` | `requirements/sp.md` §5.1 |
| `RN-SP-049` | **Enmendada el 18-09-2026.** Un principal —el que lo registró, para siempre— y tantos vinculados como le hayan vendido por hotlink | `requirements/sp.md` §5.1 |
| `RN-SP-046`, `RN-SP-048` | **Acotadas.** El principal del cliente se resuelve en `client_sellers` | `requirements/sp.md` §5.1 |
| `RN-MV-003` | **Acotada.** La compra en tienda se atribuye al principal, leído en `client_sellers` | `requirements/mv.md` §5 |

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción | Restricción |
|---|---|---|---|
| `id` | Solo en `/users/{id}/sellers` | El cliente cuyos vendedores se consultan | `uuid` en la ruta |

`/users/me/sellers` no recibe nada: el cliente es el actor.

### 6.2 Salida

| Dato | Descripción |
|---|---|
| Vendedores | Por cada uno: **nombre de usuario, nombre y apellido**, **teléfono de empresa**, **estado** de la cuenta, **origen** del vínculo (`REGISTRO` o `HOTLINK`), si es el **principal** y **desde cuándo** está vinculado |

**El orden lo fija el servidor**: el principal primero y después por fecha de vínculo, la más antigua antes. Es determinista y significa algo — el primero de la lista es quien lo trajo.

**Enmendado el 22-09-2026 (Art. I.7): la fila gana `companyPhone` y `status`.** A petición del responsable del proyecto, que pidió el teléfono de empresa y el estado «en la consulta de mis agentes», señalando `GET /users/{id}/sellers`.

Esto **revierte en parte la decisión con la que este requerimiento nació el 18-09-2026**, y el párrafo original se conserva aquí como historia en lugar de borrarse, porque el argumento sigue siendo bueno para lo que cubría:

> *«No publica nada que el cliente no sepa ya. Cada vendedor de la lista es alguien a quien le compró o quien lo registró, y de él se publica lo mismo que su hotlink (`RN-PM-022`): nombre y apellido, más el nombre de usuario, que el cliente ya conoce porque forma parte del enlace que usó. Ni identificador, ni correo, ni estado, ni roles.»*

**Qué cambia y qué no.** Entran **dos** campos y **no** los otros dos: `companyPhone` y `status` se publican; **el identificador y el correo siguen fuera**. El criterio deja de ser «lo que el cliente ya sabe» y pasa a ser **lo que sirve para contactar a quien te vende y saber si sigue activo**, que es la pregunta real de la pantalla. El correo queda fuera porque el canal de contacto que la empresa publica es el teléfono de empresa —no el personal, que tampoco entra— y el identificador porque nada de la pantalla lo necesita: el vendedor se referencia por su nombre de usuario, igual que en su hotlink.

**Los dos campos salen en las DOS rutas, y es una decisión, no una consecuencia.** `/users/{id}/sellers` es de administración y ahí no hay discusión: quien porta `users:read-sellers` puede abrir la ficha completa de esa persona con `GET /users/{id}`, de modo que no se le enseña nada nuevo. En `/users/me/sellers` **sí** es información nueva para el cliente, y se publica igualmente por decisión del responsable del proyecto del 22-09-2026: un cliente que quiere hablar con su agente necesita su teléfono, y obligarle a pedirlo por otra vía para «proteger» un dato que la empresa publica en su propio material de venta no protege nada. **Se descartó separar las dos formas** —una rica para administración y otra mínima para el cliente— porque duplicaría el esquema y el servicio para esconder un teléfono corporativo, y porque la asimetría habría que explicarla en el contrato cada vez que alguien la mirase.

**`status` es el de la cuenta del vendedor, tal cual, y puede ser el de una cuenta eliminada.** La consulta une `users` por `seller_id` **sin filtrar `deleted_at`** —a propósito desde el 18-09-2026: el historial del vínculo no se pierde porque el vendedor se vaya—, de modo que un vendedor eliminado sigue saliendo y ahora **lo dice**. Es la mejora que el campo trae de propina: hasta hoy esa fila era indistinguible de la de un vendedor activo.

**`principal` es un booleano derivado del origen** —`origin = 'REGISTRO'`— y se publica igualmente: es la pregunta que se hace («¿quién es mi agente?») y obligar a cada consumidor del contrato a derivarla es repartir una regla de negocio.

## 7. Precondiciones y postcondiciones

**Precondiciones:** actor autenticado con `users:read-own-sellers` para `/users/me/sellers` — **hasta el 21-09-2026 sin permiso** (`RF-SP-062`); para `/users/{id}/sellers`, `users:read-sellers`.

**Postcondiciones:** ninguna. Es una lectura y **no audita**.

## 8. Flujo principal

1. El actor pide los vendedores de un cliente — el propio, o uno por identificador.
2. El sistema comprueba que la persona existe y no está eliminada (solo por identificador: el actor existe por definición).
3. El sistema lee `client_sellers` del cliente, con los datos de cada vendedor.
4. El sistema devuelve la lista, principal primero.

## 9. Flujos alternativos

| ID | Caso | Comportamiento |
|---|---|---|
| `FA-001` | El cliente **no tiene ningún vendedor** | `200` con la colección vacía. Ocurre: un cliente dado de alta por un funcionario (`RF-SP-024`) no pasa por un enlace y `RN-SP-027` no lo alcanza |
| `FA-002` | El actor de `/me/sellers` **no es un cliente** —es vendedor o funcionario— | `200` con la colección vacía. Nadie lo registró por enlace y nadie le vendió por hotlink; no es un error, es una lista sin filas |
| `FA-003` | `/users/{id}/sellers` con una persona **inexistente o eliminada** | `404` |
| `FA-004` | `/users/{id}/sellers` **sin `users:read-sellers`** | `403`. Es el modelo general de `security.md` §5: aquí no hay oráculo que proteger, porque quien no trae el permiso no llega a la consulta |
| `FA-005` | El vendedor de un vínculo está **desactivado, bloqueado o eliminado** | **Sale igual**, con sus datos. El vínculo es un hecho y no depende del estado de nadie; ocultarlo dejaría al cliente sin saber quién lo trajo |

## 10. Seguridad

**Dos rutas y dos modelos, y ninguno es la estructura comercial.** `/users/me/sellers` es alcance sobre uno mismo, como `GET /users/me`: no hay nada que autorizar más allá de estar autenticado, y por eso entra en `EndpointPermissionsIT` como autenticada sin permiso, con el motivo escrito. `/users/{id}/sellers` es `users:read-sellers` desde el 21-09-2026 (`RN-SEG-014`: un permiso, una operación). Hasta entonces esta spec decía `users:read`, «el mismo permiso que gobierna leer personas: los vendedores de un cliente son un dato de la persona, no de otra naturaleza — a diferencia de las cuentas de broker, que tienen permiso propio (`RF-SP-055` §14)»; el argumento describe bien qué es el dato y ya no decide qué permiso lo gobierna, porque `RF-SP-060` resolvió que ningún código gobierne dos operaciones, tampoco cuando el dato sea de la misma naturaleza.

**`403` y no `404` para quien no trae el permiso.** `RF-SP-055` usó `404` porque su actor era un vendedor cualquiera autorizado por estructura, y un `403` le habría servido de oráculo de identificadores. Aquí el actor sin permiso **no llega a mirar la base**: el `403` sale de la anotación antes de consultar nada, y no revela si el identificador existe.

**El `404` del identificador inexistente sí distingue**, y no es una fuga: quien trae `users:read-sellers` porta normalmente `users:list` —`V29` lo da a los mismos roles que ya lo tenían— y puede listar a todas las personas por `RF-SP-025`.

**Lo que se publica de cada vendedor está acotado a propósito** (§6.2). ~~El identificador no viaja: un cliente no tiene ninguna ruta donde usarlo, y un administrador que lo necesite tiene el nombre de usuario y `RF-SP-025`.~~

**Enmendado el 24-09-2026: el identificador y el correo SÍ viajan**, a petición del responsable del proyecto.

**La decisión anterior no era un capricho y no se cae por gusto: se le rompió la premisa.** Decía que «un cliente no tiene ninguna ruta donde usar un identificador ajeno», y eso dejó de ser cierto el 23-09-2026, cuando `RF-MV-016` publicó `POST /movements/{id}/seller-assignments`, que **asigna los vendedores de una venta eligiéndolos entre los del cliente** y los recibe por `sellerId`. Sin el identificador aquí, quien asigna tiene que traducir un nombre de usuario a un identificador en otra consulta — y esta lista es justamente el conjunto entre el que se elige.

**El correo entra por decisión expresa**, preguntada y confirmada el 24-09-2026: se publica en las **dos** rutas, también en `/users/me/sellers`, de modo que el cliente ve el correo de sus vendedores igual que ya ve su teléfono de empresa. Es un canal de contacto comercial más.

**Lo que sigue fuera son los roles**, y es lo único que queda de aquella acotación: qué papeles porta alguien es de administración de accesos y no tiene nada que ver con «quién me vende».

## 11. Validaciones

| Campo | Regla | Código |
|---|---|---|
| `id` | `uuid` bien formado | `VAL-001` |
| `id` | Designa una persona no eliminada | `VAL-002` |

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-SP-700` | Un cliente registrado por enlace obtiene por `GET /users/me/sellers`, **con `users:read-own-sellers` y ningún otro permiso** (hasta el 21-09-2026, «sin traer ningún permiso»), la lista con **quien lo registró como principal y en primer lugar** |
| `CA-SP-701` | Cada vendedor llega con **identificador, nombre de usuario, correo, nombre, apellido, teléfono de empresa, estado, origen, `principal` y fecha de vínculo**, y **sin roles**. **Invertido dos veces**: el 22-09-2026 el estado pasó a viajar, y el 24-09-2026 el identificador y el correo — este último porque `RF-MV-016` estrenó una ruta que consume `sellerId` y esta lista es el conjunto entre el que se elige |
| `CA-SP-798` | **El teléfono de empresa y el estado viajan en las DOS rutas** y son los de la cuenta del vendedor: un vendedor sin teléfono declarado llega con `companyPhone` **presente y nulo** —no ausente—, y un vendedor **eliminado** sigue saliendo y publica su estado, que es lo que lo distingue de uno activo |
| `CA-SP-702` | Cada cliente tiene **exactamente un** `REGISTRO`: la base rechaza un segundo con el índice único parcial `uq_client_sellers_principal` |
| `CA-SP-703` | Un cliente **sin vendedor** —dado de alta por un funcionario— obtiene `200` con la colección vacía, no `404` |
| `CA-SP-704` | Un **vendedor** que pide `GET /users/me/sellers` obtiene `200` con la colección vacía |
| `CA-SP-705` | Quien trae `users:read-sellers` (`users:read` hasta el 21-09-2026) obtiene los vendedores de **cualquier** cliente por `GET /users/{id}/sellers`; sin el permiso recibe `403`, y con una persona inexistente o eliminada, `404` |
| `CA-SP-706` | **La migración mueve y no copia**: tras `V20`, cada cliente que colgaba de un vendedor tiene su fila `REGISTRO` con `first_movement_id` nulo, y `user_supervisors` **no contiene ninguna fila** —vigente ni cerrada— cuyo subordinado sea un consumidor |
| `CA-SP-707` | El **principal** de un cliente ve sus cuentas de broker por `RF-SP-055` sin traer permiso; un vendedor con vínculo `HOTLINK` sobre el mismo cliente recibe `404` (`RN-SP-046`) |
| `CA-SP-708` | El `own` de un agente en `RF-SP-058` cuenta las cuentas de los consumidores cuyo `REGISTRO` es él, y un vínculo `HOTLINK` **no suma** (`RN-SP-048` (5)) |
| `CA-SP-709` | Una venta registrada por `RF-MV-001` a un cliente queda atribuida en cada línea a su vendedor **`REGISTRO`**, y `user_supervisors` no interviene (`RN-MV-003`) |

Las enmiendas de hecho a `RF-SP-042` y `RF-SP-045` se prueban en sus propias especificaciones: `CA-SP-710` —el equipo no contiene clientes— y `CA-SP-711` a `CA-SP-713` —el registro escribe `client_sellers`, un vendedor con clientes se puede retirar, y el equipo no los devuelve—.

## 13. Casos límite

| Caso | Decisión |
|---|---|
| Un cliente que tenía **dos tramos** en `user_supervisors` —lo reasignaron por `RF-SP-041` antes del 18-09-2026— | `V20` toma **la vigente** como `REGISTRO`: es quien el sistema consideraba su principal el día del cambio. El tramo cerrado **se borra** con los demás; el registrador original, si fue otro, no se recupera, y queda escrito |
| Una persona que fue **cliente y luego vendedor** (`RF-SP-030` le dio `AGENTE`) | Tiene fila `REGISTRO` en `client_sellers` y superior en `user_supervisors`, y las dos son ciertas. Para comprar, manda el `REGISTRO` —le vendieron antes de que vendiera— (`RN-MV-003`); para mandar, `user_supervisors` |
| El vendedor `REGISTRO` **se elimina** | La fila queda: `client_sellers` no tiene `ON DELETE` y la eliminación es lógica. El cliente sigue viendo quién lo trajo; lo que `CM` haga con una venta atribuida a alguien que ya no está es de `CM` (`RN-SP-022`) |
| `first_movement_id` nulo | Solo en las filas que `V20` trae desde `user_supervisors`. Las que escribe `RF-SP-045` desde hoy llevan **siempre** la venta del enlace, gratuita o de pago |
| El mismo vendedor **registra y además vende por hotlink** al mismo cliente | Una sola fila —la pareja es la clave— y es `REGISTRO`: el segundo hecho no añade nada que la primera fila no diga |

## 14. Preguntas abiertas resueltas

| # | Pregunta | Resolución |
|---|---|---|
| 1 | ¿El cliente sigue en `user_supervisors` con `client_sellers` como complemento, o sale? | **Sale** (18-09-2026, responsable del proyecto). El complemento obligaba a cada consulta del árbol a distinguir mando de cartera, y `RN-SP-022` hacía irretirable a quien hubiera registrado a alguien |
| 2 | ¿El principal se puede cambiar? | **No** (18-09-2026, responsable del proyecto). Es un hecho: quien registró a alguien lo registró. Reasignarlo exigía una marca con vigencia en una tabla de hechos, que es justo lo que §10.19 quiso evitar. `RF-SP-041` deja de alcanzar a clientes |
| 3 | ¿Qué se hace con las filas de clientes que ya están en `user_supervisors`? | **Moverlas** (18-09-2026): copiar la vigente como `REGISTRO` y **borrar** todas, vigentes y cerradas. Cerrarlas conservaría filas de clientes en una tabla que ya no los admite, y `RF-SP-042` y las bajas tendrían que seguir filtrándolas |
| 4 | ¿Quién crea la tabla: `RF-MV-011` o este? | **Este.** Es el primero que la lee, `RF-MV-011` no tiene tripleta, y la migración además mueve datos que este requerimiento necesita movidos |
| 5 | ¿Se publica el identificador del vendedor? | **No.** El cliente no tiene ruta donde usarlo y el hotlink tampoco lo da (`RN-PM-022`). El nombre de usuario sí, porque forma parte del enlace que el cliente ya usó |
| 6 | ¿`403` o `404` para quien no trae `users:read-sellers` (`users:read` hasta el 21-09-2026)? | **`403`**, el modelo general. El `404` de `RF-SP-055` se justificó por un actor autorizado por estructura, que aquí no existe |
| 7 | ¿`GET /users/me` sigue devolviendo `supervisor` a un cliente? | **No, y no es una enmienda**: `CA-SP-441` decía desde el principio «nada cuando no pertenece a la fuerza comercial». Que lo devolviera fue consecuencia de tener al cliente en `user_supervisors`; la vía del cliente es esta |

## 15. Control de cambios

| Versión | Fecha | Cambio | Responsable |
|---|---|---|---|
| 0.1.0 | 18-09-2026 | Redacción inicial. Nace dos días después que el requerimiento y con él rediseñado: **el cliente sale de `user_supervisors`** (`RN-SP-028` revertida) y `client_sellers` pasa a ser su única relación con los vendedores, con el principal —la fila `REGISTRO`— **inmutable**. Lo que carga la especificación no es la lectura, que es pequeña, sino **la migración `V20`** que mueve a los clientes de tabla y **las cinco lecturas construidas** que tienen que resolver al principal en el sitio nuevo. Tres decisiones del responsable del proyecto quedan escritas en §14: sale, no se cambia, se mueve. | Responsable del proyecto |
| 0.2.0 | 21-09-2026 | **`GET /users/{id}/sellers` exige `users:read-sellers` y no `users:read`** (`RF-SP-060`, `RN-SEG-014`; Art. I.7 al integrar la rama sobre `feature/academia`, donde `RF-SP-060` nació el 19-09-2026). §3, §7, `FA-004`, `CA-SP-705` y la resolución 6 nombran el permiso nuevo; §9 conserva el argumento original como historia y explica por qué dejó de decidir. `V29` siembra el permiso. | Responsable técnico |
| 0.4.0 | 22-09-2026 | **La fila de vendedor gana `companyPhone` y `status`** (Art. I.7), a petición del responsable del proyecto («el teléfono de empresa y el estado en la consulta de mis agentes», señalando `GET /users/{id}/sellers`). **Revierte en parte §6.2**, cuyo párrafo original —«no publica nada que el cliente no sepa ya… ni identificador, ni correo, ni estado, ni roles»— se **conserva citado** en lugar de borrarse: el argumento sigue valiendo para el identificador y el correo, que **siguen fuera**. El criterio pasa de «lo que el cliente ya sabe» a «lo que sirve para contactar a quien te vende y saber si sigue activo». **Los dos campos salen en las DOS rutas**: en `/users/{id}/sellers` no enseñan nada que `GET /users/{id}` no enseñe ya, y en `/users/me/sellers` sí son nuevos para el cliente y se publican igualmente por decisión del responsable; se descartó separar las formas para no duplicar esquema y servicio por esconder un teléfono corporativo. `status` puede ser el de una cuenta **eliminada**, porque la consulta no filtra `deleted_at` —y hasta hoy esa fila era indistinguible de la de un vendedor activo—. Nace `CA-SP-798`. Sin migración, sin permiso y sin ruta nueva. | Responsable del proyecto |
| 0.3.0 | 21-09-2026 | **Los catorce criterios se renumeran: `CA-SP-686` a `699` pasan a `CA-SP-700` a `713`** (686→700 … 699→713, también los cuatro invertidos en `RF-SP-042` y `RF-SP-045`). Se redactaron el 18-09-2026 en la rama y el mismo día `RF-SP-045` (19-09, `CA-SP-686`/`687`) y `RF-SP-060` (19-09, `CA-SP-688` a `697`) tomaron los mismos números en `feature/academia`; al integrar, el que llega después renumera. Las pruebas y los documentos que los citan cambian con ellos. | Responsable técnico |
