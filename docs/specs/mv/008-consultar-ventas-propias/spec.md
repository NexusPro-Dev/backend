# SPEC — `RF-MV-008` Consultar los movimientos propios

| Campo | Valor |
|---|---|
| Requerimiento | `RF-MV-008` |
| Módulo | `MV` — Movimientos |
| Versión | 0.7.0 |
| Estado | **Aprobada** |
| Enmendada el | 16-09-2026 — el vendedor es de cada línea (`RN-MV-003`) y la cabecera lleva un sujeto (`RN-MV-026`): «lo que vendí» se responde por las líneas. Ver §15 |
| Enmendada el | 21-09-2026 — el listado se filtra también **por tipo** y cada fila **dice su tipo** (§6.1, §6.2, §11, §12). Ver §15 |
| Enmendada el | 21-09-2026 (segunda del día) — el listado se filtra también por **método de pago**, **comprobante** y **periodo**, los mismos tres de `RF-MV-006` (§6.1, §11, §12). Ver §15 |
| Enmendada el | 22-09-2026 — **el listado trae solo lo COMPRADO**: la mitad de vendedor se va a `RF-MV-015`, y el papel desaparece de la fila. El **detalle no se acota** (§2.1, §4, §6.2, §12). Ver §15 |
| Enmendada el | 22-09-2026 (segunda del día) — **el listado se llama «mis compras» y vive en su propia ruta**; el detalle y los productos comprados no se mueven (§4.1, §12). Ver §15 |
| Enmendada el | 26-09-2026 — **«mis compras» son solo ventas**: se retira el filtro por tipo; el método es el del **último pago**, y **el detalle publica los pagos** (`RN-MV-047`; §6.1, §6.3, §12). Ver §15 |
| Autor | Responsable técnico |
| Aprobada por | Responsable del proyecto |
| Fecha de aprobación | 05-09-2026 |
| Enmendada | 21-09-2026 — exige **`movements:list-own` (el listado) y `movements:read-own` (el detalle)** (`RF-SP-062`, `RN-SEG-015`: autenticarse no autoriza nada); lo siembra `V31` |

!!! warning "Enmendado el 24-09-2026 — el filtro `code` busca por FRAGMENTO"

    `RN-MV-037` ([`requirements/mv.md`](../../../requirements/mv.md) v0.41.0), a petición del responsable del proyecto: «por si solo me sé una parte». El filtro `code` de lo comprado propio **deja de exigir el comprobante entero** y pasa a devolver todo el que lo **contenga**, sin distinguir mayúsculas.

    **Es una ampliación y no un cambio de contrato**: el código completo sigue encontrando lo que encontraba, porque un comprobante se contiene a sí mismo. Lo que cambia para quien lo pinta es que la respuesta puede traer **más de una fila** donde antes traía como mucho una.

    **Tres cosas que NO cambian, y conviene que no se den por hechas.** `type` y `typeStatus` **siguen siendo exactos**: se eligen de un conjunto cerrado, no se teclean, y un `LIKE` ahí haría que pedir `VENTA` arrastrara cualquier tipo que la contenga. Los comodines `%` y `_` que escriba el usuario se **escapan** —son texto y no patrón—, que es la misma defensa que `RF-SP-025` ya tenía escrita. Y **el alcance no se ensancha**: va en la misma sentencia y **antes** que este predicado, de modo que quien solo ve lo suyo sigue viendo lo suyo.

    **Se indexa con trigramas** (`ix_movements_codigo_busqueda`, `V39`), como `ix_users_busqueda`: `uq_movements_code` no puede responder por un fragmento del medio —un B-tree solo responde por el principio— y sin el índice nuevo la consulta recorrería la tabla entera.

!!! info "Qué va en este documento"

    **Qué debe pasar, y por qué.** Nada más.

    **Prueba de pertenencia:** si un cambio de tecnología lo invalidaría, no pertenece aquí — va a `plan.md`. No se nombran tablas, clases, endpoints ni librerías.

---

!!! note "Enmienda de Art. I.7 — 21-09-2026, `RF-SP-062`"

    Esta operación exige **`movements:list-own` (el listado) y `movements:read-own` (el detalle)** desde el 21-09-2026, por `RF-SP-062` —**autenticarse no autoriza nada**, `RN-SEG-015` ([`security.md` §4.3](../../../security.md#43-reglas-de-negocio))—, por decisión del responsable del proyecto: «cada endpoint debe tener su propio permiso, ya que uso esto para saber qué vista o consulta mostrar en el front; no basta con solo tener el token». Hasta entonces se atendía con solo el token, y las líneas que abajo dicen «sin permiso» o «autenticado a secas» hablan de esa decisión original y se conservan como historia: el alcance sobre uno mismo sigue siendo exactamente el mismo, lo que cambia es que ahora tiene nombre. `V31` siembra el permiso y lo da a todo rol por su tipo.



## 1. Objetivo

Que **cualquier persona autenticada vea los movimientos en los que participó**, sin depender de ningún permiso y sin ver los de nadie más.

---

## 2. Contexto

**Hoy una venta se registra y desaparece de la vista de quien la hizo.** `RF-MV-001` la crea y la devuelve, y ahí termina: no hay forma de volver a mirarla. Para el vendedor eso significa que no puede responder «¿cuánto vendí este mes?»; para quien compró, que no puede comprobar qué pidió ni en qué estado está el pago que hizo.

**Consultar ventas ya existe, y no sirve para esto.** `RF-MV-006` y `RF-MV-007` exigen `movements:read`, que hoy solo tiene el superadministrador (`requirements/mv.md` §6.1) y que —cuando se conceda— será un permiso de **administración**: quien lo tenga verá las ventas de todo el mundo. Dárselo a un vendedor para que mire las suyas le daría de paso las de sus compañeros, y dárselo a un cliente sería absurdo.

**Es el mismo hueco que `RF-SP-039` cerró con el perfil propio y `RF-PM-007` con la oferta propia**, y se cierra igual: una operación **sobre uno mismo** no lleva permiso, porque exigirlo obligaría a concedérselo a todo el mundo — que es la forma de que un permiso deje de significar nada.

**Y no toca la decisión que sigue abierta.** «Quién ve las ventas de quién» —si un director ve las de su equipo— es alcance de datos, depende de **D-22** y `requirements/mv.md` §5.3 lo aplazó a propósito. Este requerimiento **no lo decide ni lo prejuzga**: responde solo por la persona que pregunta, que es la única respuesta que no cambia cuando D-22 se cierre.

!!! danger "Desde el 22-09-2026 el listado trae SOLO lo comprado — §2.1 se revierte"

    **La decisión de fondo de este requerimiento, la que §2.1 explica abajo, ya no está vigente para el listado.** Por decisión del responsable del proyecto del 22-09-2026 —«que mis compras solo traiga lo del usuario en sesión»—, `GET /movements/mine` devuelve **únicamente los movimientos a nombre de quien pregunta** (`RN-MV-026`): lo que compró. Lo que vendió se consulta por **`RF-MV-015`**, que nació el 21-09-2026 y responde «lo que vendí yo y lo que vendió mi red».

    **Lo que cambió no es el criterio sino el mapa.** §2.1 eligió fundir los dos papeles porque en septiembre no existía ninguna otra vía: descartó «solo lo comprado» con el argumento de que dejaría «al vendedor sin la pregunta que más va a hacer», y descartó «dos operaciones separadas» porque habría que partir el requerimiento en dos. Hoy esa segunda operación **existe y es mejor que la mitad que se retira** —llega a toda la red, no solo a lo propio—, de modo que el argumento se queda sin sujeto: el vendedor no pierde su pregunta, la hace en otra ruta y con más alcance.

    **Y con la mitad de vendedor se va el papel.** `role` —`BUYER`, `SELLER`, `BOTH`— valdría siempre `BUYER`, y un campo que siempre vale lo mismo **miente por omisión**: es el argumento con el que `RF-MV-006` §6.2 rechazó llevarlo, aplicado aquí. Se **retira del contrato** (§6.2), y es un cambio rompedor declarado.

    **El detalle NO se acota, y la asimetría es deliberada** (§4.1): `GET /movements/mine/{id}` sigue abriendo un movimiento en el que el actor participa **de cualquiera de las dos formas**. Acotarlo dejaría a un vendedor sin **ninguna** vía para ver el detalle de lo que vendió —`RF-MV-007` no está escrito y `RF-MV-015` es solo listado—, es decir, cerraría una puerta sin abrir otra. El día que exista el detalle de administración habrá que volver aquí.

    Lo que sigue de §2.1 **se conserva como historia**, porque explica por qué la forma actual es la que es.

### 2.1 «Propio» son DOS papeles, y esa es la decisión de fondo

Un movimiento lleva **dos personas**: quien **recibe** lo comprado y quien lo **vendió**. La expresión «mis ventas» significa cosas distintas según quién la diga:

- un **vendedor** llama «mis ventas» a lo que colocó;
- un **cliente** llama «mis compras» a lo que pidió;
- y desde que comprar dejó de ser cosa solo de los clientes (`RF-MV-002`), **la misma persona puede estar en los dos papeles**, incluso en el mismo movimiento.

**Se devuelven los dos papeles en un solo listado**, y cada movimiento dice en cuál aparece quien pregunta. Se eligió sobre las alternativas por lo que cuesta cada una:

| Alternativa | Por qué no |
|---|---|
| Solo lo vendido | Un cliente **nunca** es vendedor: el listado le saldría siempre vacío y sus compras quedarían sin ninguna forma de consultarse |
| Solo lo comprado | Deja al vendedor sin la pregunta que más va a hacer |
| Un parámetro que elija el papel | Resuelve lo mismo con una decisión más que especificar, validar y probar. Que el movimiento **diga** su papel deja al cliente de la API partir la lista sin que el sistema tenga que ofrecer dos formas de pedirla |
| Dos operaciones separadas | Obligaría a partir este requerimiento en dos, y a que una interfaz que quiera mostrar «todo lo mío» pida dos veces y ordene el resultado por su cuenta |

---

## 3. Actores

| Actor | Rol en esta funcionalidad |
|---|---|
| Cualquiera autenticado con `movements:list-own` (listado) y `movements:read-own` (detalle) | Consulta los movimientos en los que participó, como comprador, como vendedor o como ambos. Hasta el 21-09-2026, **no hacía falta permiso**: «es el mismo criterio de `RF-SP-039`, `RF-PM-007` y `RF-MV-009`», y los tres cambiaron el mismo día |

**No hay un segundo actor, y eso es lo que define el requerimiento.** Nadie puede pedir los movimientos de otra persona por esta vía — ni indicándolo, ni por omisión, ni teniendo permisos. Quien pregunta es siempre el actor autenticado, y la operación **no admite decir sobre quién**.

---

## 4. Alcance

### 4.1 Incluye

- El **listado paginado** de los movimientos **a nombre del actor** —lo que compró—, del más reciente al más antiguo (22-09-2026; antes, aquellos en los que participaba de cualquiera de las dos formas). **Vive en una ruta propia, «mis compras»**, y ya no en la raíz de lo propio: el nombre dice lo que devuelve, que es lo que la enmienda anterior hizo cierto.
- Las **dos partes** de cada movimiento —quién compró y quién vendió—, para que la interfaz pinte la contraparte sin una segunda consulta.
- El **detalle** de un movimiento en el que participa **de cualquiera de las dos formas**, también de lo que vendió: el listado se acota y el detalle no (22-09-2026, ver el aviso de §2).
- Un **filtro por estado**, para responder «¿qué tengo pendiente de pago?» sin traerse todo.
- El **detalle** de un movimiento propio, con sus líneas y sus importes.

### 4.2 No incluye

- **Los movimientos de otras personas**, por ninguna vía. Para eso están `RF-MV-006` y `RF-MV-007`, con su permiso.
- **Lo que el actor VENDIÓ** (22-09-2026): es `RF-MV-015`, que además llega a toda su red. En el listado no aparece; en el detalle sí, por lo que dice el aviso de §2.
- **El papel** (`role`) de quien pregunta: retirado del contrato el 22-09-2026, porque valdría siempre lo mismo.
- **El equipo a cargo.** Un director no ve aquí las ventas de sus agentes: eso es `RF-MV-015` desde el 21-09-2026, y ya no D-22.
- **El comprobante de pago.** Lo declara `RF-MV-007` y no existe todavía; cuando exista habrá que decidir aparte si el propio comprador puede descargarlo.
- **Filtrar por fechas, por producto o por método de pago.** Se deja fuera: el volumen de los movimientos de una sola persona no lo exige, y añadir filtros que nadie pidió es especificar de más.
- **Exportar.** No es una operación de este módulo.

---

## 5. Reglas de negocio aplicables

| Regla | Cómo aplica |
|---|---|
| `RN-MV-001` | La venta no se toca. Esta operación **solo lee**, y es la primera del módulo de la que eso se puede decir sin matices |
| `RN-MV-004` | Registrar no concede nada: una venta pendiente aparece en el listado **y no ha entregado nada todavía**. El estado viaja para que eso se pueda ver |

**Ninguna regla nueva.** Este requerimiento no decide nada sobre las ventas: las muestra.

---

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción |
|---|---|---|
| Página | No | Cuál de las páginas se pide. Por omisión, la primera |
| Tamaño | No | Cuántos movimientos por página, dentro del límite del sistema |
| Estado | No | Devuelve solo los movimientos en ese estado. Ausente, todos |
| Tipo (21-09-2026, **retirado**) | — | **Retirado el 26-09-2026** (`RN-MV-047`): el listado fija las ventas en la consulta, de modo que el filtro solo podía tomar un valor útil. Es el argumento de `RN-MV-038`. Un parámetro `type` que llegue **se ignora**, como cualquier parámetro desconocido |
| Método de pago (21-09-2026) | No | Solo los movimientos pagados con ese método —**desde el 26-09-2026, el de su último pago**: el confirmado, si lo hay (`RN-MV-039`)—. Uno que no exista da una **página vacía**, no un error: es un dato, como en `RF-MV-006` §6.1 |
| Código (21-09-2026) | No | El comprobante **exacto**, sin distinguir mayúsculas. Como mucho devuelve uno — y solo si es propio |
| Desde, hasta (21-09-2026) | No | Instantes con zona horaria sobre **cuándo ocurrió** el movimiento, rango **semiabierto** —incluye «desde», excluye «hasta»—. «Desde» posterior a «hasta» es un **error**. Es la misma fecha que `RF-MV-006` acota y la que la fila publica: la de registro, salvo cuando quien registró la indicó hacia atrás |

**Los tres filtros del 21-09-2026 son los de `RF-MV-006`, con el mismo significado y el mismo trato**, por decisión del responsable del proyecto: «que los movimientos se puedan filtrar por fecha, método de pago y código», **en todos los listados**. Que el listado propio los gane no cambia su alcance —siguen siendo solo los movimientos en los que participa quien pregunta— y responde preguntas que una persona se hace sobre lo suyo: «¿qué pagué con tarjeta?», «¿dónde está este comprobante?», «¿qué compré en septiembre?».

**Sobre quién se pregunta NO es un dato de entrada**, y esa ausencia es el requerimiento: quien pregunta sale de la credencial, y no hay forma de indicar a nadie más.

### 6.2 Salida — el listado

Cada movimiento devuelve:

| Dato | Descripción |
|---|---|
| Identificador y código | El código es el que la persona ve y cita |
| Estado | Pendiente, confirmada, rechazada o anulada |
| **Tipo** (21-09-2026) | Qué clase de hecho es. Hoy, siempre una venta. Hasta hoy no viajaba, y desde que se puede filtrar por él **tiene que viajar**: filtrar por lo que la fila no dice sería una respuesta que quien la lee no puede comprobar. Es el mismo dato que `RF-MV-006` publica desde el 17-09-2026 |
| **Papel** | Si quien pregunta es el **comprador**, el **vendedor**, o **ambos** |
| Sujeto | A nombre de quién es el movimiento: en una venta, quien compra (`RN-MV-026`) |
| Vendedores | A quién se atribuye **cada línea**, sin repetir (`RN-MV-003`). Hoy es uno; la lista va **vacía y presente** en los tipos de movimiento que no venden nada |
| Moneda y método de pago | Con qué se paga |
| Importes | Total, descuento y lo que se paga de verdad |
| Cuándo ocurrió | La fecha del movimiento |

**El papel viaja aunque se pueda deducir.** Quien consume la respuesta ya tiene su propio identificador y podría compararlo con las dos partes; hacerlo bien —incluido el caso de ser las dos— es lógica que **acabaría escrita en cada cliente de la API**, y escrita distinto en cada uno.

**Las líneas del movimiento NO van en el listado.** Una venta puede llevar varias, y meterlas multiplicaría la respuesta para un dato que solo se mira al abrir uno. Van en el detalle.

### 6.3 Salida — el detalle

**Lo mismo que devuelve registrar una venta**, con sus líneas: qué productos, cuántos, a qué precio y con qué vigencia. No se inventa una forma nueva — quien registró una venta y quien la consulta después tienen que ver lo mismo.

**Y desde el 26-09-2026, sus pagos** (`RN-MV-047`): cada intento de pagarla, del más antiguo al más reciente, con su método, su estado, su importe, la referencia de quien cobra si la hay, cuándo se intentó y cuándo se resolvió, y el motivo si se rechazó. **No la clave de idempotencia**, que es del cliente que la mandó.

---

## 7. Precondiciones y postcondiciones

| Tipo | Condición |
|---|---|
| Precondición | El actor está autenticado y porta `movements:list-own` o `movements:read-own`, según la ruta — **hasta el 21-09-2026 sin permiso** (`RF-SP-062`) |
| Postcondición | **Ninguna.** No se escribe nada, no se audita nada |

**No se audita, y es deliberado.** Consultar lo propio no es un acceso a datos ajenos: registrar cada vez que alguien mira su propia lista llenaría la auditoría de ruido y haría más difícil encontrar en ella lo que sí importa.

---

## 8. Flujo principal

1. El actor pide sus movimientos.
2. El sistema resuelve **quién es** a partir de su credencial.
3. Selecciona los movimientos en los que esa persona es el sujeto **o** el vendedor **de alguna de sus líneas**.
4. Si se indicó un estado, se queda solo con los de ese estado.
5. Ordena del más reciente al más antiguo y devuelve la página pedida, cada movimiento con su papel.

---

## 9. Flujos alternativos

### FA-001 — La persona no participó en ningún movimiento

Devuelve una página **vacía**, no un error. Es el caso de toda cuenta recién creada, y tratarlo como excepción obligaría a la interfaz a distinguir dos respuestas que significan lo mismo.

### FA-002 — La persona es comprador y vendedor del mismo movimiento

**Desde el 22-09-2026 aparece una vez y como lo que es: una compra.** Ocurre cuando alguien de la fuerza comercial compra para sí mismo y la venta se le atribuye —desde el 16-09-2026, **toda** compra de quien no cuelga de nadie, porque esa persona es su propio vendedor (`RN-MV-003`)—. Con el listado acotado al sujeto, el caso deja de necesitar tratamiento: una sola fila, sin papel que resolver. **Hasta esa fecha aparecía «una sola vez, con papel ambos»**, y ese era el caso que un `UNION` habría duplicado; el predicado de hoy no puede duplicar nada porque mira una sola columna.

### FA-003 — Un movimiento sin vendedor

La lista de vendedores viaja **vacía y presente**. **Desde el 16-09-2026 no es el caso de ninguna venta** —quien no cuelga de nadie se vende a sí mismo, `RN-MV-003`—; queda declarado para los tipos de movimiento que no venden nada, que son los de las etapas siguientes de `requirements/mv.md` §4.2. Entre el 04-09-2026 y el 16-09-2026 era el caso normal de quien no colgaba de nadie.

---

## 10. Excepciones

| ID | Situación | Resultado |
|---|---|---|
| `EX-001` | El movimiento pedido en el detalle **no existe** | No encontrado |
| `EX-002` | El movimiento pedido en el detalle **existe y no es suyo** | **No encontrado, igual que si no existiera** |

**`EX-002` responde lo mismo que `EX-001`, y no es un descuido.** Decir «existe pero no es tuyo» **confirma que existe**, y con un identificador que alguien esté probando eso ya es información. El sistema no distingue las dos situaciones hacia fuera. Es el mismo criterio del rechazo genérico de `RF-SP-034`.

---

## 11. Validaciones

| ID | Validación |
|---|---|
| `VAL-001` | El identificador del detalle es un identificador válido |
| `VAL-002` | La página no es negativa y el tamaño está dentro del límite del sistema |
| `VAL-003` | El estado indicado, si viene, es uno de los que existen |
| `VAL-004` | El tipo indicado, si viene, es uno del catálogo de tipos de movimiento (21-09-2026) |
| `VAL-005` | «Desde» y «hasta», si vienen, son instantes bien formados, y «desde» no es posterior a «hasta» (21-09-2026) |
| `VAL-006` | El identificador del método de pago, si viene, está bien formado (21-09-2026) |

---

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-MV-035` | Un **vendedor** ve los movimientos que vendió, cada uno con papel **vendedor** |
| `CA-MV-036` | Un **comprador** ve los movimientos que compró, cada uno con papel **comprador** |
| `CA-MV-037` | Quien es **las dos cosas en el mismo movimiento** lo ve **una sola vez**, con papel **ambos** |
| `CA-MV-038` | **No aparece ningún movimiento ajeno**, ni siquiera para quien tiene `movements:read` |
| `CA-MV-039` | Quien no participó en ninguno recibe una **página vacía**, no un error |
| `CA-MV-040` | El listado va **paginado y envuelto**, y el total cuenta **movimientos** y no participaciones |
| `CA-MV-041` | El orden es **del más reciente al más antiguo**, y es estable entre páginas |
| `CA-MV-042` | El filtro por estado devuelve **solo** los movimientos en ese estado |
| `CA-MV-043` | Cada movimiento trae **el sujeto y sus vendedores sin repetir**; la lista va **vacía y presente** cuando el movimiento no tiene ninguno |
| `CA-MV-044` | El detalle de un movimiento propio devuelve **sus líneas**, con producto, cantidad, precio y vigencia |
| `CA-MV-045` | El detalle de un movimiento **ajeno** responde **no encontrado**, igual que uno inexistente |
| `CA-MV-046` | Responde a cualquier actor autenticado que porte `movements:list-own` —o `movements:read-own` en el detalle— **y ningún otro permiso**; sin él, `403` (hasta el 21-09-2026 decía «sin exigir ningún permiso») |
| `CA-MV-047` | **Sin autenticar responde `401`** |
| `CA-MV-120` | El filtro por **tipo** devuelve solo los movimientos propios de ese tipo, escrito en mayúsculas o en minúsculas, y se combina con el estado; un tipo que no existe es un **error** y no una página vacía (21-09-2026) |
| `CA-MV-121` | Cada movimiento del listado trae **el tipo** (21-09-2026) |
| `CA-MV-133` | El filtro por **método de pago** devuelve solo los movimientos propios pagados con él; uno que no existe da una página vacía (21-09-2026) |
| `CA-MV-134` | El filtro por **código** devuelve ese comprobante escrito en mayúsculas o en minúsculas, y **nada** si el comprobante es ajeno (21-09-2026) |
| `CA-MV-135` | El **periodo** incluye «desde», excluye «hasta», se **combina** con los demás filtros, y «desde» posterior a «hasta» es un error (21-09-2026) |
| `CA-MV-137` | El listado trae **solo lo comprado**: un vendedor **no ve** en él lo que vendió a otra persona, y sí lo que compró —incluida la compra que se atribuyó a sí mismo, **una sola vez**— (22-09-2026) |
| `CA-MV-138` | El **detalle sí abre lo vendido**: el mismo vendedor que no ve esa venta en su listado la abre por su identificador (22-09-2026) |
| `CA-MV-139` | Ninguna fila del listado lleva **`role`** (22-09-2026) |
| `CA-MV-140` | El listado responde en **su ruta propia de «mis compras»**, y **la ruta anterior ya no existe**: pedirla devuelve `404` en lugar de un listado (22-09-2026). El **detalle** y los **productos comprados** siguen respondiendo donde estaban |
| `CA-MV-221` | El listado trae **solo ventas**: un retiro, un abono o un bono a nombre de quien pregunta **no aparece** (26-09-2026) |
| `CA-MV-222` | El filtro por método busca **sobre el último pago**: una venta pagada al segundo intento con otro método aparece bajo el segundo (26-09-2026) |
| `CA-MV-223` | El detalle propio trae **los pagos** de la venta, en orden, y ninguno lleva la clave de idempotencia (26-09-2026) |

**`CA-MV-035`, `CA-MV-036` y `CA-MV-037` quedan retirados el 22-09-2026** —los tres papeles del listado— y sus números **no se reutilizan**: describían la decisión que el aviso de §2 revierte. `CA-MV-036` sobrevive dentro de `CA-MV-137`, que es lo mismo visto desde el único papel que queda.

**`CA-MV-038` es el criterio que sostiene el requerimiento**, y por eso se ejercita **con el permiso puesto**: si algún día alguien decide que quien administra vea aquí también las ajenas, esta prueba lo delata en lugar de dejar que ocurra por omisión.

---

## 13. Casos límite

| Caso | Comportamiento |
|---|---|
| Una venta **anulada** | Aparece, con su estado. Anular no la borra, y ocultarla haría que la persona no pudiera comprobar qué pasó con algo que sí registró |
| Una venta cuyo **comprador fue eliminado** | **Desde el 22-09-2026 no aparece en ningún listado propio**: el único que la vería era el vendedor, y el listado ya no trae lo vendido. Sale en `RF-MV-015` y en `RF-MV-006`, con los datos de la persona tal como están (`RF-SP-029` es un borrado lógico) |
| Una venta con **líneas de vendedores distintos** | **Para el comprador**, una fila con la lista de vendedores completa. Hasta el 22-09-2026 aparecía además una vez para cada vendedor; eso es ahora `RF-MV-015`. Hoy ninguna entrada la produce (`RN-MV-003`) |
| **Muchos movimientos** de una sola persona | Se pagina. El total es **exacto**: es el conjunto de una persona y no una tabla que crezca sin límite, de modo que no hace falta el conteo acotado de los listados de auditoría |
| Dos movimientos **en el mismo instante** | El orden entre ellos es estable, y no depende de la página que se pida |
| El catálogo con **un solo tipo** (21-09-2026) | Filtrar por `VENTA` devuelve lo mismo que no filtrar. El filtro existe para el día del segundo tipo, y lo que se comprueba es que **discrimina**, con un segundo tipo que solo existe en la prueba (`RF-MV-006` §13) |
| El código de un comprobante **ajeno** (21-09-2026) | Página vacía, la misma que si no existiera: el alcance va antes que el filtro, y conocer un código no abre lo que no es propio (`EX-002`) |

---

## 14. Preguntas abiertas

**El comprobante de pago.** `RF-MV-007` lo declara y no existe. Cuando exista habrá que decidir si el propio comprador puede descargarlo, y la respuesta no es obvia: es el documento con el que él mismo demostró que pagó.

**Si un director debería ver aquí a su equipo.** Es **D-22** y sigue abierta. Este requerimiento se especifica de modo que **la respuesta no lo cambie**: si se decide que sí, será otra operación o un parámetro, y no una reinterpretación de «propio».

---

## 15. Control de cambios

| Versión | Fecha | Cambio | Autor |
|---|---|---|---|
| 0.1.0 | 05-09-2026 | Primera versión. **El requerimiento estaba declarado desde el 02-09-2026** en `requirements/mv.md` §4.1 y sin especificar; lo pide el responsable del proyecto. La decisión que carga la spec es que **«propio» son DOS papeles y no uno** (§2.1): comprador y vendedor van en el mismo listado y cada movimiento dice en cuál aparece quien pregunta, porque una misma persona puede estar en los dos —incluso en el mismo movimiento— desde que comprar dejó de ser cosa solo de los clientes. El alcance incluye el **detalle** además del listado: sin él, quien ve que compró algo no podría abrirlo, porque `RF-MV-007` exige `movements:read`. Y `EX-002` fija que un movimiento ajeno responde **lo mismo que uno inexistente**, para no confirmar la existencia de un identificador ajeno. | Responsable del proyecto |
| 0.2.0 | 16-09-2026 | **«Lo que vendí» pasa a responderse por las líneas** (`requirements/mv.md` v0.16.0: `RN-MV-003` enmendada, `RN-MV-026` nueva; Art. I.7 sobre un requerimiento construido), por decisión del responsable del proyecto. La cabecera de un movimiento lleva **un sujeto** y el vendedor **vive en cada línea**, de modo que ser vendedor de un movimiento es serlo **de alguna de sus líneas**. §6.2 cambia «comprador» por «sujeto» y «vendedor» por **«vendedores, sin repetir»**; `FA-003` deja de describir una venta —ya no existe la venta sin vendedor— y pasa a describir los tipos de movimiento que no venden nada; `CA-MV-043` lo sigue. `FA-002` gana el caso que desde hoy lo produce siempre: quien no cuelga de nadie compra y **es su propio vendedor**. §13 gana la venta con líneas de vendedores distintos, que hoy nadie produce y el modelo admite. **Los tres papeles no cambian**, ni el alcance, ni la paginación. | Responsable del proyecto |
| 0.3.0 | 21-09-2026 | **El listado se filtra también por tipo, y cada fila dice su tipo** (`requirements/mv.md` v0.31.0; Art. I.7 sobre un requerimiento construido), a petición del responsable del proyecto —«que los movimientos se puedan filtrar por tipos de movimiento»—, el mismo día y con el mismo filtro que `RF-MV-006`. §6.1 gana la entrada, con el argumento de aquel —el catálogo es cerrado por `RN-MV-017`, y un tipo inexistente es un **error**—; §6.2 gana **el tipo en la fila**, que hasta hoy no viajaba y que desde que se puede filtrar por él tiene que viajar; `VAL-004`, `CA-MV-120`, `CA-MV-121` y el caso límite del catálogo con un solo tipo. **Ni el alcance, ni los papeles, ni el detalle cambian.** La enmienda de `RF-SP-062` del mismo día —los permisos `movements:list-own` y `movements:read-own`— vive como nota tras la cabecera, sin versión propia. | Responsable del proyecto |
| 0.4.0 | 21-09-2026 | **El listado se filtra también por método de pago, comprobante y periodo** (Art. I.7; `requirements/mv.md` v0.33.0), por decisión del responsable del proyecto del mismo día —«que los movimientos se puedan filtrar por fecha de creación (rango), método de pago y código de movimiento», en todos los listados; la fecha es **cuándo ocurrió**, la misma de `RF-MV-006` y de la fila—. §6.1 gana las tres entradas con el trato de aquel: el método inexistente es página vacía, el código es exacto sin distinguir mayúsculas, el rango es semiabierto y el invertido un error. `VAL-005`, `VAL-006`, `CA-MV-133` a `CA-MV-135`, y el caso límite del comprobante ajeno: el alcance va antes que el filtro. **Ni el alcance, ni los papeles, ni el detalle cambian.** | Responsable del proyecto |
| 0.6.0 | 22-09-2026 | **El listado se llama «mis compras» y se muda a su propia ruta** (Art. I.7; `requirements/mv.md` v0.36.0), por decisión del responsable del proyecto: «`movements/mine/shopping` para consultar todo lo que el usuario en sesión ha comprado». Es la consecuencia de nombre de la enmienda de esta misma mañana: desde que el listado trae solo compras, seguir llamándolo «lo propio» prometía más de lo que devuelve. **No queda alias**: dos rutas con el mismo permiso romperían la inyectividad operación → permiso que `RN-SEG-014` exige y que `EndpointPermissionsIT` comprueba, de modo que la ruta anterior **deja de existir** — segundo cambio incompatible del día, declarado. **El detalle y los productos comprados no se mueven**: el primero abre también lo vendido y no es «compras»; el segundo ya tenía su ruta. `CA-MV-140`. Ni el permiso, ni los filtros, ni la fila cambian. | Responsable del proyecto |
| 0.5.0 | 22-09-2026 | **El listado trae SOLO lo comprado, y la fila pierde el papel** (Art. I.7; `requirements/mv.md` v0.34.0), por decisión del responsable del proyecto: «que mis compras solo traiga lo del usuario en sesión». **Se revierte la decisión de fondo de §2.1** —«propio son dos papeles»— y se explica por qué el argumento ya no aplica: aquella descartó «solo lo comprado» porque dejaba al vendedor sin su pregunta, y desde el 21-09-2026 esa pregunta la responde `RF-MV-015` con **más** alcance —él y toda su red—. `role` se **retira del contrato**, cambio rompedor declarado, porque valdría siempre `BUYER` (el argumento de `RF-MV-006` §6.2). **El detalle NO se acota** y la asimetría se declara: acotarlo dejaría a un vendedor sin ninguna vía para abrir lo que vendió, porque `RF-MV-007` no existe. `CA-MV-035` a `CA-MV-037` retirados sin reutilizar número; nacen `CA-MV-137` a `CA-MV-139`; `FA-002` y dos casos límite reescritos. Ni el permiso, ni los filtros, ni la paginación cambian. | Responsable del proyecto |
| 0.7.0 | 26-09-2026 | **«Mis compras» son solo ventas, y su detalle enseña los pagos** (`requirements/mv.md` v0.45.0, `RN-MV-047`; Art. I.7 sobre un requerimiento construido), por decisión del responsable del proyecto. **Se retira el filtro `type`** —`CA-MV-120` queda retirado y su número no se reutiliza— con el argumento de `RN-MV-038`; `CA-MV-121` sigue, porque la fila sigue diciendo su tipo. El método es el del último pago (`RN-MV-039`). `CA-MV-221` a `CA-MV-223`; lo construye `RF-MV-018` · `tasks.md` `T-06` y `T-07`. | Responsable del proyecto |
