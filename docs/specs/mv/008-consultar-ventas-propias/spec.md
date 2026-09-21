# SPEC — `RF-MV-008` Consultar los movimientos propios

| Campo | Valor |
|---|---|
| Requerimiento | `RF-MV-008` |
| Módulo | `MV` — Movimientos |
| Versión | 0.2.0 |
| Estado | **Aprobada** |
| Enmendada el | 16-09-2026 — el vendedor es de cada línea (`RN-MV-003`) y la cabecera lleva un sujeto (`RN-MV-026`): «lo que vendí» se responde por las líneas. Ver §15 |
| Autor | Responsable técnico |
| Aprobada por | Responsable del proyecto |
| Fecha de aprobación | 05-09-2026 |
| Enmendada | 21-09-2026 — exige **`movements:list-own` (el listado) y `movements:read-own` (el detalle)** (`RF-SP-062`, `RN-SEG-015`: autenticarse no autoriza nada); lo siembra `V31` |

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

- El **listado paginado** de los movimientos en los que el actor participó, del más reciente al más antiguo.
- El **papel** en el que aparece en cada uno: comprador, vendedor, o los dos.
- Las **dos partes** de cada movimiento —quién compró y quién vendió—, para que la interfaz pinte la contraparte sin una segunda consulta.
- Un **filtro por estado**, para responder «¿qué tengo pendiente de pago?» sin traerse todo.
- El **detalle** de un movimiento propio, con sus líneas y sus importes.

### 4.2 No incluye

- **Los movimientos de otras personas**, por ninguna vía. Para eso están `RF-MV-006` y `RF-MV-007`, con su permiso.
- **El equipo a cargo.** Un director no ve aquí las ventas de sus agentes: eso es D-22 y sigue abierta.
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

**Sobre quién se pregunta NO es un dato de entrada**, y esa ausencia es el requerimiento: quien pregunta sale de la credencial, y no hay forma de indicar a nadie más.

### 6.2 Salida — el listado

Cada movimiento devuelve:

| Dato | Descripción |
|---|---|
| Identificador y código | El código es el que la persona ve y cita |
| Estado | Pendiente, confirmada, rechazada o anulada |
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

El movimiento aparece **una sola vez**, con papel **ambos**. Ocurre cuando alguien de la fuerza comercial compra para sí mismo y la venta se le atribuye — y desde el 16-09-2026 es **el caso de toda compra de quien no cuelga de nadie**, porque esa persona es su propio vendedor (`RN-MV-003`). Duplicar la fila sería contar dos veces un solo hecho, y el total de la página dejaría de significar «cuántos movimientos tengo».

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

**`CA-MV-038` es el criterio que sostiene el requerimiento**, y por eso se ejercita **con el permiso puesto**: si algún día alguien decide que quien administra vea aquí también las ajenas, esta prueba lo delata en lugar de dejar que ocurra por omisión.

---

## 13. Casos límite

| Caso | Comportamiento |
|---|---|
| Una venta **anulada** | Aparece, con su estado. Anular no la borra, y ocultarla haría que la persona no pudiera comprobar qué pasó con algo que sí registró |
| Una venta cuyo **comprador fue eliminado** | Aparece para el vendedor, con los datos de la persona tal como están. `RF-SP-029` es un borrado lógico y la fila sigue ahí |
| Una venta con **líneas de vendedores distintos** | Aparece **una vez** para cada uno de ellos, con papel vendedor, y su lista de vendedores los trae a todos. Hoy ninguna entrada la produce (`RN-MV-003`); el modelo la admite y esta consulta no tiene que cambiar el día que exista |
| **Muchos movimientos** de una sola persona | Se pagina. El total es **exacto**: es el conjunto de una persona y no una tabla que crezca sin límite, de modo que no hace falta el conteo acotado de los listados de auditoría |
| Dos movimientos **en el mismo instante** | El orden entre ellos es estable, y no depende de la página que se pida |

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
