# SPEC — `RF-SP-062` Autenticarse no autoriza nada

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-062` |
| Módulo | `SP` — Sistema Principal |
| Estado | **Aprobada** |
| Autor | Responsable técnico |
| Aprobada por | Responsable del proyecto |
| Fecha de aprobación | 21-09-2026 |

---

## 1. Objetivo

Que **toda operación que exija token exija además un permiso** —también las que actúan sobre el propio actor—, de modo que el frontend decida qué vista o consulta mostrar mirando solo los permisos de quien entró, y «tener el token» no signifique nada por sí solo.

## 2. Contexto

**Lo pidió el responsable del proyecto el 21-09-2026, dos veces y con la razón.** La primera, al pedir `RF-SP-061`: «cada endpoint debe tener un permiso único, con el fin de que el frontend se pueda separar y saber qué vistas mostrar». Se entendió como `RN-SEG-014` —un permiso, una operación, **entre las operaciones que tenían permiso**— y `RF-SP-061` nació con dos rutas, una con permiso y otra «autenticada a secas», como `RF-SP-059` y `RF-SP-039` antes. La segunda vez lo dijo sin margen: «no estás entendiendo: ocupo que cada endpoint tenga su propio permiso, ya que uso esto para saber qué vista o consulta mostrar en el front; hazla como regla general, es decir, **no basta con solo tener el token**».

**Once operaciones se atendían con solo el token**, cada una con el mismo argumento escrito en su tripleta —«alcance sobre uno mismo: no hay nada que autorizar más allá de estar autenticado»—: el propio perfil (`RF-SP-039`) y su corrección (`RF-SP-044`), la propia contraseña (`RF-SP-037`), mis vendedores (`RF-SP-059`), mis clientes (`RF-SP-061`), las cuentas de broker de mi equipo (`RF-SP-056`) y las de una persona a cargo (`RF-SP-055`), mis movimientos y su detalle (`RF-MV-008`), mis productos comprados (`RF-MV-014`) y la compra propia de un paquete (`RF-MV-012`). `requirements/mv.md` §6 lo defendía además con otro: «exigirlo obligaría a concedérselo a todos los clientes, que es la forma de que un permiso deje de significar nada».

**Los dos argumentos eran correctos para la seguridad y equivocados para el frontend.** Un permiso que todos los clientes portan sigue significando «a este se le enseña la tienda», y un rol al que se le retire deja de verla sin tocar una línea. Lo que el frontend necesita no es que el permiso sea escaso sino que **exista**: la pantalla se arma leyendo los permisos del actor, y con «tiene token» no puede decidir si ofrece «mis clientes» a un cliente o «mi cartera» a un vendedor.

**No es un cambio de seguridad y conviene decirlo**: ninguna de las once operaciones deja de estar acotada a lo propio, y el `403` que ahora puede salir no protege ningún dato que antes se fugara. Es un cambio de **contrato entre el backend y el frontend**: el catálogo de permisos pasa a describir **todo** lo que una persona puede hacer, sin un resto implícito que viajaba con el token.

## 3. Actores

| Actor | Papel |
|---|---|
| **El sistema, al migrar** | `V31` siembra los once y los reparte por tipo de rol |
| **Cualquier persona autenticada** | Deja de poder hacer nada que su rol no le conceda, también sobre sí misma |
| **Administrador de roles** | Concede los de alcance propio a un rol nuevo por `RF-SP-005`; sin ellos, sus personas no ven ni su perfil |
| **El frontend** | Decide cada vista por el permiso, sin un caso especial para «lo propio» |

## 4. Alcance

### 4.1 Incluye

- **`RN-SEG-015`** en `security.md` §4.3: toda operación con token exige permiso; las públicas son el límite de la regla y su lista es cerrada.
- **Once permisos** nuevos, con la convención `own` para el alcance sobre uno mismo, en `security.md` §4.4 (**ciento veinticuatro**).
- **`V31`**: los siembra y los da a **todo rol por su tipo** —`FUNCIONARIO` y `VENDEDOR` los once; `CONSUMIDOR` ocho—, con la guarda de que ningún rol quede con un permiso que su padre no porte.
- **Once `@PreAuthorize`** en los controladores de `SP` y `MV`, con su prosa de `403`.
- **`EndpointPermissionsIT`** deja de admitir «autenticada a propósito»: la lista de excepciones pasa a llamarse `PUBLICAS` y solo admite operaciones que `SecurityConfig` sirve sin token.
- Las **enmiendas de Art. I.7** a las diez tripletas afectadas.

### 4.2 No incluye

- **Las públicas.** Entrar, renovar, salir, registrarse, recuperar la contraseña, los catálogos del formulario de registro (`countries`, `document-types`, `brokers`, `payment-methods`), el hotlink de producto y de paquete, las reseñas de un producto y sus portadas. Se atienden **sin token** y no tienen a quién pedirle uno.
- **Los pendientes** `RF-MV-002`, `RF-MV-011` y `RF-MV-013`: nacerán con `products:buy`, `products:buy-by-hotlink` y `packages:buy-by-hotlink`, y sus tripletas lo dirán cuando existan.
- **Cambiar el alcance de nada.** `RN-SP-046` sigue decidiendo quién es visible en `GET /users/{id}/broker-accounts`; el permiso nuevo solo abre la ruta.
- **`AC`.** Sigue con `courses:update` compartido hasta el tramo 3 de `RF-SP-060`; sus operaciones ya exigen permiso, y esta regla no las toca.

## 5. Reglas de negocio aplicables

| ID | Regla | Origen |
|---|---|---|
| `RN-SEG-015` | **Nace aquí.** Autenticarse no autoriza nada: toda operación con token exige permiso | `security.md` §4.3 |
| `RN-SEG-014` | Un permiso, una operación: los once son nuevos y ninguno gobierna dos | `security.md` §4.3 |
| `RN-SEG-003` | Contención: cada rol recibe los suyos y su padre los recibe también | `security.md` §4.3 |
| `RN-SP-046` | No cambia: el alcance de las cuentas de broker sigue siendo la estructura | `requirements/sp.md` §5.1 |

## 6. Datos

### 6.1 Los once permisos

| Código | Operación | Requerimiento | `CONSUMIDOR` |
|---|---|---|---|
| `users:read-own-profile` | `GET /users/me` | `RF-SP-039` | Sí |
| `users:update-own-profile` | `PATCH /users/me` | `RF-SP-044` | Sí |
| `users:change-own-password` | `POST /auth/password` | `RF-SP-037` | Sí |
| `users:read-own-sellers` | `GET /users/me/sellers` | `RF-SP-059` | Sí |
| `users:read-own-clients` | `GET /users/me/clients` | `RF-SP-061` | **No** |
| `broker-accounts:read-own-team` | `GET /users/me/team/broker-accounts` | `RF-SP-056` | **No** |
| `broker-accounts:read-team-member` | `GET /users/{id}/broker-accounts` | `RF-SP-055` | **No** |
| `movements:list-own` | `GET /movements/mine` | `RF-MV-008` | Sí |
| `movements:read-own` | `GET /movements/mine/{id}` | `RF-MV-008` | Sí |
| `movements:read-own-products` | `GET /movements/mine/products` | `RF-MV-014` | Sí |
| `packages:buy` | `POST /packages/{code}/purchases` | `RF-MV-012` | Sí |

**`own` es la convención para el alcance sobre uno mismo**, y ya existía: `products:read-own-comments` (`RF-PM-013`). `movements:list-own` y `movements:read-own` son dos porque `RN-SEG-014` es estricto también con listado y detalle. `packages:buy` no lleva `own` porque comprar es siempre para uno mismo: la venta a otro es `movements:create`, y el día que exista la compra por hotlink será `buy-by-hotlink`.

**`broker-accounts:read-team-member` abre la ruta y no decide el alcance.** `RN-SP-046` sigue diciendo quién es visible —el subordinado directo, el cliente propio, o cualquiera para quien además porte `broker-accounts:read`— y el `404` que protege de un oráculo de identificadores sigue saliendo del servicio. Es la única de las once cuya operación ya tenía una autorización, y se conserva entera debajo del permiso.

### 6.2 El reparto

`V31` da cada permiso a **todo rol que exista** —de sistema o creado a mano— **según su tipo**: `FUNCIONARIO` y `VENDEDOR` reciben los once; `CONSUMIDOR` recibe los ocho marcados «Sí», no los tres de vendedor. Es la lógica de `V28` —cada hijo a todo rol que portara el padre— con «estar autenticado» como padre: nadie pierde nada de lo que podía hacer ayer, y un cliente no recibe vistas de vendedor que el frontend no debe ofrecerle.

## 7. Precondiciones y postcondiciones

**Precondiciones:** `V30` aplicada (catálogo en 113).

**Postcondiciones:** catálogo en **124**; `SUPERADMIN` porta 124 y `ADMIN` 118; los roles `MANAGER`, `DIRECTOR` y `AGENTE` portan los once; `CLIENTE` porta ocho; ningún rol porta un permiso que su padre no porte; ninguna operación autenticada carece de `@PreAuthorize`.

## 8. Flujo principal

1. `V31` siembra los once permisos con identificadores literales y los asocia por tipo de rol, y aborta si algún recuento no cuadra.
2. Cada una de las once operaciones declara su permiso en `@PreAuthorize`.
3. `EndpointPermissionsIT` recorre todas las operaciones: cada una declara permiso o está en la lista cerrada de públicas, y la función operación → permiso sigue siendo inyectiva.
4. El contrato publica la `x-required-permission` de las once, como la de todas.

## 9. Flujos alternativos

| ID | Caso | Comportamiento |
|---|---|---|
| `FA-001` | Una persona cuyo rol **no porta** `users:read-own-profile` pide `GET /users/me` | `403` (`AUTH-002`). Ocurre con un rol creado a mano después del 21-09-2026 al que aún no se le concedieron los de alcance propio; es el precio de la regla y queda escrito |
| `FA-002` | Un cliente pide `GET /users/me/clients` | `403`: `CLIENTE` no porta `users:read-own-clients`. Hasta hoy recibía `200` con la página vacía; el frontend ya no le ofrece la vista |
| `FA-003` | Una persona con `must_change_password` y un rol sin `users:change-own-password` | Queda sin salida hasta que un administrador conceda el permiso o restablezca la contraseña (`RF-SP-038`). Los roles existentes lo reciben por `V31`; solo un rol nuevo mal configurado llega aquí |
| `FA-004` | Una ruta pública se declara con `@PreAuthorize` | `EndpointPermissionsIT` falla: una pública no puede exigir permiso, porque no hay actor |
| `FA-005` | Una ruta autenticada nueva sin `@PreAuthorize` | `EndpointPermissionsIT` falla diciendo cuál: la única salida es declararla pública en `SecurityConfig` **y** en la lista, con el motivo |

## 10. Seguridad

**No se abre ni se cierra ningún dato.** Las once operaciones siguen acotadas a lo propio por construcción —el actor sale del token—, y `RN-SP-046` sigue decidiendo el alcance de la única que tenía uno. Lo que cambia es que la **ausencia** de permiso deja de ser un estado válido para una operación con token: el catálogo describe todo lo que una persona puede hacer, y el frontend puede fiarse de él sin un caso especial.

**El reparto por tipo de rol respeta `RN-SEG-003`.** `CLIENTE` cuelga de `SUPERADMIN`, que porta todo; los tres de la fuerza comercial cuelgan de `ADMIN`, que recibe los once. Un rol creado a mano recibe lo de su tipo y su padre, del mismo tipo o superior, también; `V31` lo comprueba y aborta si encuentra un rol con un permiso que su padre no porta.

**Un rol nuevo nace sin permisos, como siempre, y desde hoy eso es más visible.** `V8` decidió que los roles se siembran sin permisos y `RF-SP-005` los concede; esta regla no lo cambia, pero hace que la omisión se note en el primer `GET /users/me`. Es deliberado: mejor un `403` claro que un permiso implícito.

## 11. Validaciones

Ninguna nueva: la validación es la de `V31` (recuentos) y la de `EndpointPermissionsIT` (cobertura e inyectividad).

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-SP-723` | **Ninguna operación con token carece de permiso**: `EndpointPermissionsIT` recorre cada `@RequestMapping` de `/api/v1` y afirma que declara `@PreAuthorize` o está en la lista cerrada de públicas, y que cada pública coincide con lo que `SecurityConfig` sirve sin token |
| `CA-SP-724` | Las once operaciones exigen su código exacto y **`403` sin él aunque el actor esté autenticado**: `GET /users/me` sin `users:read-own-profile` es `403`, y así las otras diez |
| `CA-SP-725` | `V31` siembra los once con identificador literal; el catálogo cuenta **ciento veinticuatro**; `SUPERADMIN` 124, `ADMIN` 118; `MANAGER`, `DIRECTOR` y `AGENTE` portan los once; `CLIENTE` porta ocho y **no** `users:read-own-clients`, `broker-accounts:read-own-team` ni `broker-accounts:read-team-member` |
| `CA-SP-726` | Un rol creado a mano **antes** de `V31`, de tipo `VENDEDOR` bajo `AGENTE`, recibe los once; uno de tipo `CONSUMIDOR` bajo `SUPERADMIN` recibe los ocho; tras `V31` ningún rol porta un permiso que su padre no porte |
| `CA-SP-727` | La inyectividad de `RN-SEG-014` se conserva: ninguno de los once gobierna otra operación |
| `CA-SP-728` | `GET /users/{id}/broker-accounts` con `broker-accounts:read-team-member` sigue devolviendo `404` a quien no es superior ni principal de la persona y no porta `broker-accounts:read` (`RN-SP-046` intacta) |
| `CA-SP-729` | El contrato publica la `x-required-permission` de las once, y ninguna forma cambia |
| `CA-SP-730` | La semilla de desarrollo sigue arrancando y sus veinte personas ven su perfil: cada una porta `users:read-own-profile` por su rol |

## 13. Casos límite

| Caso | Decisión |
|---|---|
| Una persona con **dos roles**, uno de vendedor y otro de cliente | Porta la unión: ve su cartera y sus vendedores. Es `RN-SEG-009`, sin cambios |
| `POST /auth/password` con `must_change_password` | Exige `users:change-own-password` como cualquier otra; el filtro que acota a esa ruta a quien debe cambiarla sigue delante. Todos los roles existentes lo reciben |
| `POST /auth/logout` | Sigue pública: cerrar sesión no puede exigir un permiso a quien quizá ya no tiene token vigente (`RF-SP-036`) |
| Un rol de tipo `CONSUMIDOR` al que se le concede `users:read-own-clients` por `RF-SP-005` | Se admite: `SUPERADMIN` lo porta y la contención se cumple. La regla dice qué reparte `V31`, no qué puede concederse después |
| `GET /users/me` de una persona **eliminada** entre dos peticiones | `401`, como hasta hoy: el corte de acceso va antes que el permiso |

## 14. Preguntas abiertas resueltas

| # | Pregunta | Resolución |
|---|---|---|
| 1 | ¿Las públicas también? | **No.** No hay actor a quien pedirle permiso; son el límite de la regla, no una excepción, y la lista es cerrada y vigilada |
| 2 | ¿A quién se dan los once? | **A todo rol por su tipo** (21-09-2026): nadie pierde nada, y los de vendedor no van a `CONSUMIDOR` porque el frontend no debe ofrecérselos a un cliente |
| 3 | ¿`movements:read-own` y `movements:list-own` o uno solo? | **Dos.** `RN-SEG-014` es estricto también con listado y detalle, por decisión del 19-09-2026 |
| 4 | ¿`broker-accounts:read-team-member` sustituye a la autorización por estructura de `RF-SP-055`? | **No: la abre.** El permiso dice que la operación existe para ese rol; `RN-SP-046` dice a quién puede mirar. Sustituirla habría ampliado D-22, que se decidió no ampliar |
| 5 | ¿Se renombra algo? | **No.** Ningún código existente cambia; los once son nuevos |
| 6 | ¿Es un RF o una enmienda? | **RF con tripleta**, como `RF-SP-060`: toca once tripletas, una regla transversal y una migración de datos, y merece su propio control de cambios |

## 15. Control de cambios

| Versión | Fecha | Cambio | Responsable |
|---|---|---|---|
| 0.1.0 | 21-09-2026 | Redacción inicial, el mismo día de la decisión: «cada endpoint debe tener su propio permiso, ya que uso esto para saber qué vista o consulta mostrar en el front; hazla como regla general, es decir, no basta con solo tener el token». Nace `RN-SEG-015`; once permisos con `own`; reparto por tipo de rol; las catorce públicas como límite. Ocho criterios, `CA-SP-723` a `730`. | Responsable del proyecto |
