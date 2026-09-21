# SPEC — `RF-SP-061` Consultar los clientes de un vendedor

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-061` |
| Módulo | `SP` — Sistema Principal |
| Estado | **Aprobada** |
| Autor | Responsable técnico |
| Aprobada por | Responsable del proyecto |
| Fecha de aprobación | 21-09-2026 |

---

## 1. Objetivo

Que un vendedor vea **su cartera** —a quiénes registró, que son suyos, y a quiénes les vendió por su enlace, que son vinculados— y que administración pueda ver la de cualquier vendedor.

## 2. Contexto

**Es la lectura inversa de `RF-SP-059`, y nació el mismo día que la cartera salió del equipo.** Hasta el 18-09-2026 «los clientes de un agente» se respondía con `GET /users/{id}/team?roles=CLIENTE` (`RF-SP-042`), porque el cliente colgaba de su vendedor en `user_supervisors`. Con `RN-SP-028` revertida el equipo es solo fuerza comercial y esa pregunta se quedó sin ruta: el frontend lo anotó como `R-45` el 21-09-2026 —`/mis-clientes` y la pestaña «Clientes» de la ficha de un usuario quedaron «pendientes del backend»— y el responsable del proyecto lo pidió el mismo día con sus palabras: «un endpoint para consultar mis clientes o los clientes de un vendedor».

**La tabla ya existe y ya tiene el índice.** `client_sellers` (`V20`, `requirements/sp.md` §10.19) guarda una fila por pareja cliente-vendedor con el origen del vínculo, y `ix_client_sellers_vendedor` se creó sin que nadie lo usara **para esto**: `RF-SP-059` lo dejó dicho en su plan §2.1. Esta especificación no trae migración de esquema; trae **un permiso**.

**El permiso es propio porque `RN-SEG-014` lo exige, y el responsable del proyecto lo volvió a pedir el 21-09-2026** con la razón de fondo escrita: «cada endpoint debe tener un permiso único, con el fin de que el frontend se pueda separar y saber qué vistas mostrar». Un rol que porte `users:read-clients` y no `users:read-sellers` ve la cartera de un vendedor y no los vendedores de un cliente, y el frontend decide qué pestaña enseñar mirando un solo código. Nació como `RF-SP-060` en `feature/vendedores-de-un-cliente` y se renumeró a `061` al integrar sobre `feature/academia`, donde `RF-SP-060` es precisamente «un permiso por operación».

**Hoy la cartera solo tiene filas `REGISTRO`, y no es un defecto de esta consulta.** Las `HOTLINK` las escribirán `RF-MV-011` y `RF-MV-013`, que no existen; el contrato ya es el definitivo y el filtro por origen ya distingue lo que todavía no llega.

## 3. Actores

| Actor | Papel |
|---|---|
| **El propio vendedor** | Consulta su cartera por `GET /users/me/clients`, sin permiso |
| **Administrador** con `users:read-clients` | Consulta la de cualquier vendedor por `GET /users/{id}/clients` |

## 4. Alcance

### 4.1 Incluye

- Devolver, **paginados y los más recientes primero**, los clientes de un vendedor con el origen de cada vínculo, si el vendedor es su **principal** y desde cuándo.
- Un filtro opcional por **origen** (`REGISTRO` o `HOTLINK`), para separar los propios de los vinculados.
- El permiso `users:read-clients`, sembrado por `V30` y dado a `SUPERADMIN` y `ADMIN`.
- Que `EndpointPermissionsIT` reciba las dos rutas: una como autenticada sin permiso con su motivo, la otra con su código.

### 4.2 No incluye

- **Escribir vínculos.** Los escriben `RF-SP-045` (`REGISTRO`) y, cuando existan, `RF-MV-011` y `RF-MV-013` (`HOTLINK`).
- **El equipo.** Un vendedor subordinado en `user_supervisors` **no es un cliente** de su superior y no sale aquí; para eso está `RF-SP-042`.
- **Las cuentas de broker de los clientes.** Es `RF-SP-056` y `RF-SP-057`, sobre `user_brokers`.
- **Filtrar por estado del cliente, por nombre o por fecha.** Se publica el estado y el frontend puede separar en pantalla; un filtro nuevo es una enmienda cuando alguien lo necesite.
- **D-22.** Ninguna de las dos rutas se autoriza por estructura: el director de un agente **no** ve la cartera del agente por ser su director. Ver §10.

## 5. Reglas de negocio aplicables

| ID | Regla | Origen |
|---|---|---|
| `RN-SP-049` | Un principal —el que lo registró, para siempre— y tantos vinculados como le hayan vendido por hotlink; todo en `client_sellers` | `requirements/sp.md` §5.1 |
| `RN-SP-028` | **Revertida.** El cliente no cuelga de la estructura comercial: la cartera no se lee en `user_supervisors` | `requirements/sp.md` §5.1 |
| `RN-SEG-014` | Un permiso gobierna una operación o ninguna, nunca dos | `security.md` §4.3 |
| D-22 | Ninguna lectura de `SP` se autoriza por estructura, salvo `RN-SP-046` | `security.md` §5 |

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción | Restricción |
|---|---|---|---|
| `id` | Solo en `/users/{id}/clients` | El vendedor cuya cartera se consulta | `uuid` en la ruta |
| `origin` | No | Acota a los propios (`REGISTRO`) o a los vinculados (`HOTLINK`) | Uno de los dos valores; otro es `400` |
| `page`, `size` | No | Paginación, con los límites de todo listado | `VAL-003` fuera de límites |

`/users/me/clients` no recibe identificador: el vendedor es el actor.

### 6.2 Salida

Una **página** —`content`, `totalElements`, `totalPages`, `page`, `size`, `totalIsExact`— como `RF-SP-056`, y en cada fila:

| Dato | Descripción |
|---|---|
| `id` | El identificador del cliente |
| `username`, `firstName`, `lastName` | Quién es |
| `status` | Su estado (`ACTIVO`, `FTD_PENDIENTE`, `INACTIVO`, `BLOQUEADO`), tal como lo publica `RF-SP-025`. Para un vendedor es el dato que más importa de la cartera: quién se registró y **todavía no depositó** (`RN-SP-026`) |
| `origin` | `REGISTRO` si el vendedor lo registró, `HOTLINK` si le vendió por su enlace |
| `principal` | `true` si el vendedor es su principal —derivado de `origin`, publicado igual, por lo mismo que en `RF-SP-059` §6.2— |
| `linkedAt` | Desde cuándo está vinculado; en las filas que `V20` trajo, desde cuándo colgaba de él en `user_supervisors` |

**Con `id`, al contrario que `RF-SP-059`, y por la razón inversa.** Aquel omitió el identificador del vendedor porque el cliente no tiene ninguna ruta donde usarlo. Aquí el vendedor **sí** la tiene: desde la cartera se abre la ficha del cliente (`RF-SP-026`) y sin `id` no hay enlace, que es exactamente lo que el frontend pidió el 21-09-2026. Lo que un vendedor ve de un cliente por esta lista es lo mismo que ya ve de él en su detalle.

**Con `status`**, porque una cartera se trabaja y hay que distinguir al cliente activo del desactivado; y porque el vínculo es un hecho (`RN-SP-049`) que no se oculta cuando el cliente cambia de estado — se publica con su estado, no se filtra. **El eliminado es la excepción**, y no por el vínculo sino por la persona: para el sistema no existe (`RF-SP-025`, `RF-SP-026`), y una fila cuyo `id` no abre nada es un enlace roto (`FA-007`).

**Los más recientes primero**, y después por nombre de usuario para que el orden sea determinista: la cartera crece por el final y lo nuevo es lo que se atiende. `RF-SP-059` ordena al revés —principal primero, luego el más antiguo— porque allí la lista contesta «¿quién me trajo?»; aquí contesta «¿a quién tengo?».

## 7. Precondiciones y postcondiciones

**Precondiciones:** actor autenticado. Para `/users/{id}/clients`, además `users:read-clients`.

**Postcondiciones:** ninguna. Es una lectura y **no audita**.

## 8. Flujo principal

1. El actor pide la cartera de un vendedor — la propia, o una por identificador.
2. El sistema valida `origin`, `page` y `size`.
3. El sistema comprueba que la persona existe y no está eliminada (solo por identificador).
4. El sistema cuenta y lee `client_sellers` por `seller_id`, con los datos de cada cliente **no eliminado**.
5. El sistema devuelve la página, los vínculos más recientes primero.

## 9. Flujos alternativos

| ID | Caso | Comportamiento |
|---|---|---|
| `FA-001` | El vendedor **no tiene clientes** | `200` con la página vacía y `totalElements` en cero |
| `FA-002` | El actor de `/me/clients` **no es vendedor** —es cliente o funcionario— | `200` con la página vacía. Nadie se registró con su enlace ni le compró por él; no es un error, es una cartera sin filas |
| `FA-003` | `/users/{id}/clients` con una persona **inexistente o eliminada** | `404` |
| `FA-004` | `/users/{id}/clients` **sin `users:read-clients`** | `403`, aunque el actor porte `users:read`, `users:read-team` o `users:read-sellers`: ninguno de esos gobierna esta operación (`RN-SEG-014`) |
| `FA-005` | `origin` con un valor distinto de `REGISTRO` y `HOTLINK` | `400` (`VAL-001`) |
| `FA-006` | El cliente de un vínculo está **desactivado o bloqueado** | **Sale igual**, con su estado. El vínculo es un hecho; ocultarlo dejaría al vendedor sin saber a quién trajo |
| `FA-007` | El cliente de un vínculo está **eliminado** | **No sale**, y `totalElements` no lo cuenta. Para el sistema una persona eliminada no existe (`RF-SP-025` no la lista, `RF-SP-026` responde `404`): una fila con un `id` que no abre nada sería un enlace roto en la cartera. La fila de `client_sellers` queda —el hecho no se borra— y volvería a verse si la persona se restaurara |
| `FA-008` | El superior comercial de un vendedor pide `/users/{id}/clients` de su subordinado **sin el permiso** | `403`. La estructura de mando no autoriza esta lectura (§10) |

## 10. Seguridad

**Dos rutas y dos modelos, y ninguno es la estructura comercial**, como en `RF-SP-059` §10. `/users/me/clients` es alcance sobre uno mismo: el vendedor sale del token y no hay nada que autorizar más allá de estar autenticado; entra en `EndpointPermissionsIT` como autenticada sin permiso, con el motivo escrito. `/users/{id}/clients` es `users:read-clients`, y solo eso.

**Por qué no se autoriza por estructura, aunque la pregunta lo sugiera.** Un director querrá ver la cartera de sus agentes, y `RN-SP-046` ya abre una lectura de esa forma para las cuentas de broker. Se descarta ampliarla: D-22 declara `RN-SP-046` como **la única** excepción y `security.md` §5 la acota a un nivel y a las cuentas; abrir una segunda —con su `404` en lugar de `403` para no ser oráculo, su recorrido de `user_supervisors` y su prueba de tres niveles— es lo que la decisión quiso evitar. Quien deba ver carteras ajenas porta `users:read-clients`; un rol `DIRECTOR` que lo necesite lo recibe por `RF-SP-005`, y ese día verá **todas**, no solo las de su rama — es el precio de no recorrer la estructura, y queda escrito.

**`403` y no `404` para quien no trae el permiso**, por lo mismo que `RF-SP-059`: el actor sin permiso no llega a mirar la base y el `403` no revela si el identificador existe. **El `404` del inexistente sí distingue**, y no es una fuga: quien porta `users:read-clients` porta normalmente `users:list`.

**`users:read-clients` es un permiso nuevo y no un hijo de nadie.** `V28` repartió los cincuenta y un códigos que nacieron de dividir otros, dando cada hijo a todo rol que portara el padre. Este no divide a nadie: la operación no existía. `V30` lo siembra a `SUPERADMIN` y `ADMIN` explícitamente, como `V22` y `V29`, y a `CLIENTE` no.

**Lo que se publica de cada cliente** (§6.2) es lo que el vendedor ya ve de él en `RF-SP-026`: ni correo, ni roles, ni membresía. La cartera dice quién es y desde cuándo; el detalle dice el resto.

## 11. Validaciones

| Campo | Regla | Código |
|---|---|---|
| `id` | `uuid` bien formado | `VAL-001` |
| `id` | Designa una persona no eliminada | `VAL-002` |
| `origin` | `REGISTRO` o `HOTLINK`, sin distinguir mayúsculas | `VAL-001` |
| `page`, `size` | Los límites de todo listado | `VAL-003` |

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-SP-714` | Un vendedor obtiene por `GET /users/me/clients`, **sin traer ningún permiso**, la página con los clientes que registró y los que le compraron por hotlink, **los más recientes primero**, y cada fila trae `id`, `username`, `firstName`, `lastName`, `status`, `origin`, `principal` y `linkedAt` |
| `CA-SP-715` | `origin=REGISTRO` devuelve solo los propios y `origin=HOTLINK` solo los vinculados, con `totalElements` acorde; otro valor es `400` (`VAL-001`) |
| `CA-SP-716` | La cartera se pagina como todo listado: `page` y `size` fuera de límites son `400` (`VAL-003`), y `totalElements` cuenta la cartera entera aunque la página traiga menos |
| `CA-SP-717` | Un actor **sin cartera** —un cliente, un funcionario, un vendedor sin registros— obtiene `200` con la página vacía, no `404` |
| `CA-SP-718` | Quien trae `users:read-clients` obtiene la cartera de **cualquier** vendedor por `GET /users/{id}/clients`; sin él recibe `403` **aunque porte `users:read`, `users:read-team` o `users:read-sellers`**; con una persona inexistente o eliminada, `404` |
| `CA-SP-719` | Un cliente **desactivado o bloqueado** sigue en la cartera de quien lo registró, con su estado; uno **eliminado** no sale ni se cuenta |
| `CA-SP-720` | El **superior comercial** de un vendedor recibe `403` en `GET /users/{id}/clients` de su subordinado si no porta el permiso: la estructura no autoriza |
| `CA-SP-721` | Un vendedor subordinado en `user_supervisors` **no aparece** en la cartera de su superior: la lista lee solo `client_sellers` |
| `CA-SP-722` | `V30` siembra `users:read-clients` con identificador literal, asociado a `SUPERADMIN` y `ADMIN`; el catálogo cuenta **ciento trece** y `EndpointPermissionsIT` recibe `/users/{id}/clients` con ese código y `/users/me/clients` como autenticada sin permiso |

## 13. Casos límite

| Caso | Decisión |
|---|---|
| El mismo cliente **registrado por A** y **comprador por el hotlink de B** | Sale en la cartera de A como `REGISTRO` y en la de B como `HOTLINK`. Dos filas, dos vendedores, una verdad cada una |
| Un vendedor que **fue cliente** y tiene fila `REGISTRO` como cliente de otro | En `/me/clients` ve su cartera —donde él es `seller_id`—; su propia fila como cliente no es suya. Las dos consultas (`059` y `061`) miran columnas distintas de la misma tabla |
| El vendedor **se elimina** | Su cartera sigue existiendo por `/users/{id}/clients` con el permiso: la eliminación es lógica, las filas quedan (`RF-SP-059` §13) y quien liquide necesita verlas. Por `/me/clients` no, porque a un eliminado se le corta el acceso en el acto (`security.md` §4.5) |
| `page` más allá de la última | `200` con `content` vacío y los totales de siempre, como todo listado |
| `totalIsExact` | Siempre `true`: el conteo es una consulta por índice sobre una cartera que se cuenta en cientos, no el techo de `RF-MV-006` |

## 14. Preguntas abiertas resueltas

| # | Pregunta | Resolución |
|---|---|---|
| 1 | ¿Se publica el `id` del cliente? | **Sí** (21-09-2026, a petición del frontend, R-45). Desde la cartera se abre la ficha y sin `id` no hay enlace. `RF-SP-059` decidió lo contrario para el vendedor por la razón inversa: el cliente no tiene ruta donde usarlo |
| 2 | ¿Se publica el estado? | **Sí.** Una cartera se trabaja; y el vínculo no se filtra por estado porque es un hecho. Filtrar por estado no se incluye: el frontend separa en pantalla y un filtro es una enmienda cuando haga falta. **El eliminado sí se excluye**, porque para el sistema no existe y su `id` no abriría nada |
| 3 | ¿Orden? | **Los más recientes primero**, y por nombre de usuario como desempate. La pregunta es «¿a quién tengo?», y lo nuevo es lo que se atiende. `RF-SP-059` va al revés porque contesta «¿quién me trajo?» |
| 4 | ¿Paginada? | **Sí**, como `RF-SP-042` y `RF-SP-056`: una cartera puede tener cientos de filas y `RF-SP-059` solo se libró de paginar porque un cliente tiene un puñado de vendedores |
| 5 | ¿El superior ve la cartera de su subordinado por estructura? | **No.** D-22 tiene una sola excepción y no se amplía; quien deba verla porta `users:read-clients`, y ese día verá todas |
| 6 | ¿`users:read-clients` es hijo de `users:read` o de `users:read-team`? | **De nadie.** La operación no existía y no hay padre que repartir; `V30` lo siembra a los dos roles de sistema, como `V22` y `V29` |
| 7 | ¿`403` o `404` sin el permiso? | **`403`**, el modelo general, por lo mismo que `RF-SP-059` §14.6 |

## 15. Control de cambios

| Versión | Fecha | Cambio | Responsable |
|---|---|---|---|
| 0.1.0 | 21-09-2026 | Redacción inicial, tres días después de registrarse como `RF-SP-060` en la rama de `RF-SP-059` y el mismo día de renumerarse a `061`. Por petición del responsable del proyecto: «un endpoint para consultar mis clientes o los clientes de un vendedor», con un permiso único por endpoint «para que el frontend se pueda separar y saber qué vistas mostrar» (`RN-SEG-014`). Hereda de `RF-SP-059` los dos modelos de autorización y el `403`; decide al revés que él el `id` (sí), el estado (sí), el orden (recientes primero) y la paginación (sí), y dice por qué cada uno. Sin migración de esquema: solo `V30`, un permiso. | Responsable del proyecto |
