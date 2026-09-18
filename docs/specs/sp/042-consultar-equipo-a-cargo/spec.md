# SPEC — `RF-SP-042` Consultar el equipo a cargo de un usuario

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-042` |
| Módulo | `SP` — Sistema Principal |
| Estado | **Aprobada** |
| Autor | Responsable técnico |
| Aprobada por | Responsable del proyecto |
| Fecha de aprobación | 22-08-2026 |
| Enmendada | 10-09-2026 — el equipo se **filtra por códigos de rol** y cada persona lleva **la lista completa de sus roles** en lugar de un `roleCode` único. `CA-SP-455` queda **invertido**; entran `CA-SP-624` a `CA-SP-628`; se rehace la resolución 3 de §14 |
| Enmendada | 18-09-2026 — **la cartera sale del equipo**: el cliente deja de colgar de `user_supervisors` (`RN-SP-028` revertida) y el equipo vuelve a ser solo fuerza comercial. `CA-SP-625` queda **invertido** en `CA-SP-696`; `roles` y su filtro se conservan; «los clientes de un vendedor» pasa a `RF-SP-061` (renumerado el 21-09-2026; nació como `RF-SP-060`). Lo construye `RF-SP-059` |

---

!!! note "Enmienda de Art. I.7 — 19-09-2026, `RF-SP-060`"

    Esta operación exige **`users:read-team`** y no `users:read` desde el 19-09-2026, por `RF-SP-060` —**un permiso por operación**, `RN-SEG-014` ([`security.md` §4.4](../../../security.md#44-catalogo-de-permisos))—: `users:read` gobernaba varias operaciones y se queda con una; esta recibe código propio, sembrado por `V28` y dado a todo rol que portara `users:read`. Las menciones de `users:read` que siguen abajo hablan de su siembra original y se conservan como historia.

## 1. Objetivo

Ver la posición de una persona dentro de la fuerza comercial: de quién depende y a quién tiene a cargo.

## 2. Contexto

`RF-SP-041` registra quién está a cargo de quién. Sin una forma de leerlo, ese dato solo sería observable indirectamente —por el rechazo que produce al intentar retirarle el rol a alguien con equipo (`RN-SP-022`)—, y quien tiene que reorganizar la estructura estaría trabajando a ciegas.

Es además la consulta que **hace ejecutable** ese rechazo. Cuando `RF-SP-028`, `RF-SP-029` o `RF-SP-031` responden «esta persona tiene tres personas a cargo», deliberadamente **no dicen quiénes son**: esa respuesta pertenece a una consulta con su propio permiso. Esta.

**Qué queda fuera, y por qué es lo mismo en los dos casos.** No recorre el árbol completo —un manager no obtiene aquí a los agentes de sus directores— y no existe una variante «mi equipo» que se resuelva contra el actor en lugar de contra un identificador. La segunda es **alcance por persona**, que `security.md` §6 reserva hasta resolver **D-22**. La primera no lo es, pero carece de sentido sin ella: quien necesita ver su red descendente completa es el propio manager, no un administrador revisando fichas. Ambas se especificarán juntas cuando D-22 esté cerrada.

Mientras tanto esta consulta cubre lo que hoy hace falta de verdad: administrar la estructura, y saber a quién hay que reasignar antes de dar de baja a alguien.

## 3. Actores

| Actor | Rol en esta funcionalidad |
|---|---|
| Super Administrador | Consulta la estructura de cualquier persona |
| Administrador | Consulta la estructura de cualquier persona |

## 4. Alcance

### 4.1 Incluye

- El **superior inmediato** de la persona consultada, cuando lo tiene.
- Su **equipo directo**: las personas de las que es superior hoy.
- **La lista completa de roles** de cada persona de la respuesta —la consultada, su superior y cada miembro del equipo—, con identificador, código y nombre. Es lo que hace legible la estructura, y desde el 10-09-2026 sustituye al rol comercial único.
- **Un filtro por códigos de rol** sobre el equipo directo, con varios códigos a la vez.

### 4.2 No incluye

- **El árbol descendente completo.** Solo un nivel hacia abajo. Reservado, junto con la variante siguiente, a cuando se cierre D-22.
- **El conteo de la rama indirecta**, ni siquiera como número sin nombres. Obligaría a recorrer el árbol, que es exactamente lo que D-22 debe gobernar, y un total también informa: revela el tamaño de la red de cada mando.
- **Una variante «mi equipo»** resuelta contra el actor: es alcance por persona (`security.md` §6).
- **El historial de superiores anteriores.** Solo se devuelve lo vigente. El dato se conserva (`RN-SP-021`) y hará falta, pero quien lo consultará es una auditoría de reparto de comisiones, con su propio permiso y sus propios filtros por fecha; no esta pantalla.
- **Filtrar el equipo por cualquier cosa que no sea el rol** —por estado de cuenta, por nombre, por país—. El filtro por rol entró el 10-09-2026 porque responde una pregunta que el listado general **no sabe responder**: «de la gente que cuelga de este agente, enséñame solo los clientes». Los demás filtros siguen fuera por el argumento original: `RF-SP-025` ya los tiene sobre el listado general, y replicarlos aquí obligaría a mantener dos semánticas sincronizadas.
- **Filtrar al superior o a la persona consultada.** El filtro acota **el equipo** y nada más. El superior es uno solo y su **ausencia ya significa otra cosa** —«no depende de nadie»—; omitirlo por no casar con el filtro haría indistinguible la cúspide de un superior descartado, que es justo la distinción que `CA-SP-445` obliga a preservar.
- **La cadena ascendente completa** —el superior del superior, y así hasta la cúspide—. Se obtiene encadenando consultas, y devolverla entera invitaría a usarla como sustituto del modelo de alcance que falta.
- Cambiar la estructura → `RF-SP-041`.
- Las personas que no pertenecen a la fuerza comercial: no tienen estructura que consultar.

## 5. Reglas de negocio aplicables

Ninguna regla gobierna esta consulta. El alcance de los datos es **global**: cualquier actor con `users:read-team` ve la estructura de cualquiera.

Conviene dejarlo escrito de forma explícita, por el mismo motivo que lo hace `RF-SP-025` §5: **el día que exista alcance comercial dejará de ser cierto**, y esta consulta será de las primeras afectadas. Que un director pueda ver hoy el equipo de otro director es una consecuencia consciente de que D-22 no está resuelta, no un descuido.

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción | Restricción de negocio |
|---|---|---|---|
| Identificador | Sí | Persona cuya estructura se consulta | Debe existir y no estar eliminada |
| Página | No | Página del equipo directo | Por defecto la primera |
| Tamaño | No | Elementos por página | Por defecto 20, máximo 100 (`architecture.md` §7.4) |
| Roles | No | Códigos de rol por los que se acota **el equipo directo** | Varios admitidos. Entra quien porte **alguno** (semántica O). Un código inexistente **no es un error**: devuelve el equipo vacío, mismo criterio que `RF-SP-025` con su filtro por rol |

### 6.2 Salida

| Dato | Descripción |
|---|---|
| Persona consultada | Nombre de usuario, nombre y **la lista completa de sus roles**, cada uno con identificador, código y nombre |
| Superior inmediato | Quién la tiene a cargo hoy, con **sus roles**, y desde cuándo. Ausente si es la cúspide. **No se ve afectado por el filtro** |
| Equipo directo | Personas de las que es superior hoy, cada una con su nombre, **sus roles** y su estado de cuenta. **Acotado por el filtro de roles, si viene** |
| Total del equipo | Cuántas personas tiene a cargo, incluso cuando la página devuelta no las contenga todas. **Cuenta lo filtrado**: con un filtro por roles, cuenta a quienes lo cumplen |
| Paginación | Total de elementos, total de páginas y página actual |

## 7. Precondiciones y postcondiciones

**Precondiciones**

- El actor está autenticado y posee `users:read`, el mismo permiso que exigen `RF-SP-025` y `RF-SP-026`. No se crea un permiso propio para la estructura: quien puede ver la ficha de una persona puede ver de quién depende.
- La persona consultada existe y no está eliminada.

**Postcondiciones**

- Ninguna: la consulta no altera el estado del sistema.

## 8. Flujo principal

1. El actor solicita la estructura comercial de una persona.
2. El sistema verifica que la persona exista y no esté eliminada.
3. El sistema recupera su superior vigente, si lo tiene.
4. El sistema recupera las asignaciones vigentes que la declaran superior, paginadas.
5. El sistema devuelve la estructura con su información de paginación.

## 9. Flujos alternativos

### FA-001 — La persona no pertenece a la fuerza comercial

**Cuándo ocurre:** no porta ningún rol de clasificación `VENDEDOR`.

1. El sistema devuelve la estructura vacía: sin superior y con el equipo en cero.
2. **No es un error.** La consulta responde «esta persona no tiene estructura comercial», que es una respuesta legítima y distinta de «esta persona no existe».

### FA-002 — La persona es la cúspide de la fuerza comercial

**Cuándo ocurre:** su rol comercial no tiene por encima ningún rol comercial.

1. El sistema devuelve el equipo directo y **omite el superior**.
2. Es el estado que `RN-SP-019` declara admisible, y la consulta debe distinguirlo de «tiene superior y no lo encontramos».

### FA-003 — Persona sin equipo

**Cuándo ocurre:** nadie la tiene como superior.

1. El sistema devuelve el equipo vacío con la paginación en cero, y el superior si lo tiene.
2. Es la respuesta que confirma que puede dársele de baja sin reasignar a nadie (`RN-SP-022`).

## 10. Excepciones

### EX-001 — Persona inexistente o eliminada

**Condición:** el identificador no corresponde a ningún usuario vigente.
**Respuesta del sistema:** informa que la persona no existe, sin distinguir entre nunca haber existido y haber sido eliminada.

## 11. Validaciones

| ID | Validación | Mensaje esperado |
|---|---|---|
| `VAL-001` | Identificador con formato válido | El identificador del usuario no es válido. |
| `VAL-002` | Persona existente y no eliminada | El usuario solicitado no existe. |
| `VAL-003` | Parámetros de paginación dentro de los límites | El tamaño de página solicitado no es válido. |

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-SP-442` | El sistema devuelve el superior inmediato y el equipo directo de una persona de la fuerza comercial |
| `CA-SP-443` | El equipo devuelto contiene **solo** asignaciones vigentes: quien dejó de estar a su cargo no aparece |
| `CA-SP-444` | El sistema devuelve la estructura vacía, sin error, para quien no porta ningún rol comercial |
| `CA-SP-445` | El sistema **omite** el superior cuando la persona es la cúspide, y lo distingue de no haberlo encontrado |
| `CA-SP-446` | El sistema devuelve el equipo vacío con la paginación en cero cuando nadie depende de la persona |
| `CA-SP-447` | El total del equipo coincide con el número que `RF-SP-028`, `RF-SP-029` y `RF-SP-031` informan al rechazar por `RN-SP-022` |
| `CA-SP-448` | El equipo se pagina con el tamaño por defecto y respeta el máximo configurado |
| `CA-SP-449` | La respuesta **no** contiene el árbol descendente completo: solo un nivel |
| `CA-SP-450` | La consulta **no** admite resolverse contra el actor en lugar de contra un identificador |
| `CA-SP-451` | El sistema informa que la persona no existe cuando está eliminada lógicamente |
| `CA-SP-452` | El sistema rechaza la consulta a un actor sin `users:read-team` |
| `CA-SP-453` | La respuesta **no** contiene superiores anteriores ni tramos cerrados: solo la asignación vigente |
| `CA-SP-454` | La respuesta **no** contiene ningún conteo de la rama indirecta, ni siquiera como número agregado |
| `CA-SP-455` | **Invertido el 10-09-2026.** La consulta **sí** admite un filtro por códigos de rol sobre el equipo directo, y **sigue sin admitir** ningún otro: un `search` o un `status` no cambian el resultado |
| `CA-SP-624` | Cada persona de la respuesta —la consultada, su superior y cada miembro del equipo— lleva **la lista completa de sus roles**, con identificador, código y nombre, ordenada por código y **presente aunque vaya vacía**. La respuesta **ya no publica un rol único** |
| `CA-SP-625` | **Invertido el 18-09-2026** en `CA-SP-696`. Decía: «un cliente de la cartera llega con su rol a la vista y no en nulo, que es lo que permite distinguirlo de un vendedor sin rol» — y desde `V20` no hay cliente que llegue |
| `CA-SP-696` | Consultar el equipo de un vendedor que registró clientes devuelve **solo** a sus vendedores directos: los clientes **no aparecen**, y `roles=CLIENTE` devuelve el equipo vacío con `200` |
| `CA-SP-626` | El filtro por un solo código devuelve **solo** a quienes lo portan, y el total del equipo **cuenta lo filtrado** |
| `CA-SP-627` | Varios códigos se combinan con **O**: entra quien porte alguno, y **quien porte dos no aparece dos veces** |
| `CA-SP-628` | Un código de rol inexistente devuelve el equipo vacío con `200`, **no un error**; y ni el superior ni la persona consultada se ven afectados por el filtro |

## 13. Casos límite

- **Subordinado con la cuenta inactiva o bloqueada:** sigue apareciendo en el equipo, con su estado a la vista. Retirarle el acceso a alguien no lo saca de la estructura —`RF-SP-028` ni siquiera lo permite si él mismo tiene equipo—, y ocultarlo haría que el total no cuadrara con el que impide dar de baja a su superior.
- **Subordinado eliminado:** no aparece. `RF-SP-029` cierra su asignación al eliminarlo, y esta consulta solo devuelve las vigentes.
- **Persona con equipo grande:** se pagina. Un manager con decenas de directores no puede devolverse en una sola respuesta, y el total va aparte precisamente para que la primera página baste cuando lo único que se necesita es el número.
- **Consulta durante una reasignación:** la operación de `RF-SP-041` cierra y abre en la misma transacción, de modo que esta consulta ve el estado anterior o el posterior, nunca a la persona sin superior ni con dos.
- **Persona que porta rol comercial y además otro de otra clasificación:** se devuelve su estructura con normalidad. Los roles no comerciales **no intervienen** en la estructura —quién depende de quién sigue saliendo de `user_supervisors`—, pero desde el 10-09-2026 **sí se publican**: `roles` los lleva todos, y filtrar por uno de ellos acota el equipo igual que filtrar por uno comercial.
- **Identificador con formato incorrecto:** se rechaza por validación, no se trata como persona inexistente. Mismo criterio que `RF-SP-026`.

## 14. Preguntas abiertas

Ninguna. Las cuatro se resolvieron el 22-08-2026, antes de aprobar la especificación, y las cuatro respuestas fueron restrictivas: **esta consulta se mantiene deliberadamente pequeña** para no convertirse en el sustituto informal del modelo de alcance que falta.

**La tercera se rehízo el 10-09-2026**, y conviene ver por qué eso no rompe el criterio: no se relajó «porque los datos ya estaban ahí» —que es exactamente lo que el enfoque original temía—, sino porque **cambió el contenido de la estructura**. Cuando se respondió que no, esta tabla relacionaba vendedores entre sí; desde `RF-SP-045` contiene también la cartera de clientes, y con ella apareció una pregunta que el listado general no sabe responder. Las otras tres siguen resueltas como estaban.

| # | Pregunta | Resolución |
|---|---|---|
| 1 | ¿Devuelve el historial de superiores anteriores? | **No, solo lo vigente.** Esta consulta sirve para dos cosas —administrar la estructura y saber a quién reasignar antes de dar de baja a alguien (`RN-SP-022`)—, y el historial no ayuda a ninguna. Quien lo necesitará es una auditoría del reparto de comisiones, con permiso propio y filtros por fecha, que hoy no existe. El dato queda conservado por `RN-SP-021` y **sin ninguna vía de lectura** hasta entonces: es un hueco aceptado, no un olvido |
| 2 | ¿Devuelve el conteo del equipo indirecto? | **No, ni siquiera como número.** «¿De cuánta gente respondo?» es la pregunta de un mando comercial, y el actor de esta consulta es un administrador revisando una ficha. Responderla exige recorrer el árbol, que es justo lo que **D-22** debe gobernar, y adelantarlo aquí crearía el precedente que la reserva de `security.md` §6 quiere evitar. Además un total tampoco es inocuo: revela el tamaño de la red de cada mando |
| 3 | ¿El equipo directo admite filtros? | **Sí desde el 10-09-2026, y solo por rol** (decisión del responsable del proyecto). La resolución original decía que no, con el argumento de que `RF-SP-025` ya filtra el listado general por estado y por rol. **El argumento dejó de valer cuando la cartera de clientes entró en esta estructura** (`RF-SP-045`, 01-09-2026): el listado general sabe responder «enséñame a los clientes» y **no** sabe responder «de la gente que cuelga de este agente, enséñame solo los clientes», que es la pregunta de esta pantalla. Lo que la resolución protegía —no mantener dos semánticas de filtrado sincronizadas— se conserva acotando el filtro **al rol y a nada más**: ni `search`, ni `status`, ni país. Y no toca al superior, porque su ausencia ya significa otra cosa |
| 4 | ¿Se admite consultar la estructura de una persona eliminada? | **No: se trata como inexistente**, mismo criterio que `RF-SP-026` y `RF-SP-003`. `RF-SP-029` cierra su asignación al eliminarla, de modo que no queda estructura **vigente** que devolver, y esta consulta solo devuelve lo vigente por la resolución 1. Reconstruir de quién dependía alguien que ya no está es una pregunta de auditoría, y llegará por la misma vía que el historial |
