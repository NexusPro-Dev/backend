# SPEC — `RF-SP-060` Un permiso por operación

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-060` |
| Módulo | `SP` — Sistema Principal |
| Estado | **Aprobada** |
| Autor | Responsable técnico |
| Aprobada por | Responsable del proyecto |
| Fecha de aprobación | 19-09-2026 |

---

## 1. Objetivo

Que **cada operación de la API exija un permiso que no exija ninguna otra**, de modo que conceder un permiso a un rol conceda **exactamente una cosa**.

## 2. Contexto

**Lo pidió el responsable del proyecto el 19-09-2026, con el problema ya formulado**: «hay algunos permisos que se le podrán asignar a diferentes roles, pero al compartir permiso con otras rutas puede que se llegue a asignar rutas que no debería».

**El problema es real y está en el catálogo, no en el código de seguridad.** Hoy hay **sesenta** permisos y **noventa y siete** operaciones que exigen uno, y **veintiún** códigos gobiernan más de una. Los casos que mejor lo enseñan:

| Permiso | Operaciones | Lo que arrastra sin querer |
|---|---|---|
| `courses:update` | 10 | Corregir el título de un curso ⇒ crear, corregir, desactivar y **retirar** módulos y lecciones |
| `packages:update` | 7 | Corregir un paquete ⇒ cambiar su estado, su portada, y **qué productos lleva y con qué descuento** |
| `roles:update` | 5 | Editar el nombre de un rol ⇒ cambiar su estado, reubicarlo, y **asignarle y revocarle permisos** |
| `products:comment` | 4 | Escribir una reseña ⇒ leer, corregir y retirar la propia |
| `commissions:read` | 4 | Tres recursos de `CM` y la resolución de la comisión efectiva bajo un solo código |
| `users:read` | 3 | Ver el listado ⇒ ver el detalle **y el equipo a cargo** |

`roles:update` es el que más pesa: quien administre roles concede «editar un rol» y ha concedido «repartir permisos», que es la operación que `RN-SEG-003`, `RN-SEG-010` y `RN-SEG-011` rodean de reglas.

**La causa es una decisión de diseño que se tomó bien y que ya no alcanza.** `security.md` §4.4 fija el patrón `<recurso>:<acción>` con `read`, `create`, `update` y `delete` como acciones base, y cada módulo agrupó bajo `update` todo lo que «modifica» — con razonamiento escrito: `ac.md` §7 («un módulo y una lección no existen sin su curso, y quien puede corregir el curso tiene que poder armarlo»), `security.md` §4.4 sobre `packages:update` («la asociación es parte de armar el paquete»). Ese razonamiento responde a **qué operaciones van juntas para un administrador completo**, y el catálogo de permisos no existe para ese administrador —`SUPERADMIN` y `ADMIN` los tienen todos— sino para **los roles que se crearán con una parte**. Para esos, un permiso que agrupa es un permiso que **no se puede conceder a medias**.

**Lo que este requerimiento no hace es cambiar el modelo.** `RN-SEG-001` a `RN-SEG-013` siguen; el token sigue llevando roles; la resolución sigue por unión. Lo que cambia es **la granularidad del catálogo** y una regla nueva que impide que vuelva a agruparse.

## 3. Actores

| Actor | Papel |
|---|---|
| **Administrador de roles** con `roles:assign-permissions` | Es a quien sirve: concede permisos a roles y necesita que cada uno signifique una sola operación |
| **Cualquier persona autenticada** | Cada operación que ejerza le exige, desde hoy, el permiso propio de esa operación |
| **El sistema**, al migrar | Siembra los permisos nuevos y **los da a todo rol que portaba el que se divide**, para que nadie pierda nada |

## 4. Alcance

### 4.1 Incluye

- **La regla**: un permiso gobierna **una operación** —método y ruta— o ninguna; **nunca dos** (`RN-SEG-014`).
- **El reparto** de los veintiún códigos compartidos entre las **noventa y siete** operaciones que hoy exigen permiso, más las **seis** relaciones de curso del bloque 4 de `AC` (`RF-AC-016` a `RF-AC-021`), que nacerán bajo `courses:update` por sus tripletas aprobadas y se reparten aquí: **ciento tres** operaciones, **ciento tres** permisos.
- **Ningún código se renombra ni se retira.** Cada código existente **se queda con una** de sus operaciones y estrecha su descripción; las demás reciben código nuevo. Son **cincuenta y un** permisos nuevos y el catálogo pasa de sesenta a **ciento once** (§6.2).
- **Nadie pierde acceso al migrar**: todo rol que porte un código dividido recibe **todos** sus hijos en la misma migración. Estrechar después es trabajo de quien administre roles, por `RF-SP-006`.
- **La prueba que impide que vuelva a pasar**: la que recorre los endpoints (`EndpointPermissionsIT`) comprueba desde hoy que **ningún permiso aparece en dos operaciones**.
- **Las convenciones de nombre** para lo que se separa (§6.3), para que el siguiente módulo no tenga que inventarlas.

### 4.2 No incluye

- **Cambiar quién tiene qué.** Al terminar, `SUPERADMIN` y `ADMIN` y cualquier rol creado a mano pueden **exactamente lo mismo que antes**. Recortar es una decisión posterior y ajena a este requerimiento.
- **Retirar códigos.** Un código que hoy existe sigue existiendo, con una operación. Retirar permisos del catálogo es otra cosa —tienen filas en `role_permissions` y aparecen en auditoría— y nadie lo ha pedido.
- **Las operaciones que hoy no exigen permiso**: las públicas, las de solo token (`RF-SP-037`, `RF-SP-039`, `RF-SP-044`, `RF-SP-056`) y `RF-SP-055`, que resuelve por el par actor–consultado. No cambian: no comparten nada.
- **Los permisos que no gobiernan ninguna ruta** —`courses:teach`, `courses:learn`, `countries:read`, `brokers:read`, `document-types:read`, `exchange-rates:read`, `exchange-rates:update`, `exchange-rates:delete`—. Siguen igual: la regla admite cero rutas, lo que prohíbe son dos.
- **Los bloques 5 y 6 de `AC`** (portadas y aula), que no están construidos. Sus tripletas se enmiendan para nacer con un permiso por operación, y sus migraciones siembran los suyos: la regla vale desde hoy para todo lo que se construya.
- **El frontend.** Consume estos códigos para decidir qué enseña; el reparto lo afecta y se le avisa (`tasks.md` §4), pero no se construye aquí.

## 5. Reglas de negocio aplicables

| ID | Regla | Origen |
|---|---|---|
| `RN-SEG-003` | Contención de privilegios: un rol no declara lo que su padre no tiene | `security.md` §4.3 |
| `RN-SEG-007` | `SUPERADMIN` está acotado por el catálogo completo | `security.md` §4.3 |
| `RN-SEG-009` | Los permisos efectivos son la unión de los de los roles activos | `security.md` §4.3 |
| `RN-SEG-014` | **Un permiso, una operación** — nace con este requerimiento | `security.md` §4.3 |

**`RN-SEG-003` es la que obliga a repartir a todos los roles a la vez**: si un hijo nuevo se diera a un rol y no a su padre, el hijo declararía lo que el padre no tiene. Darlo a **todo rol que portaba el padre** conserva la contención sin comprobarla, porque la contención ya se cumplía para el código que se divide.

## 6. Datos

### 6.1 Entrada

No hay entrada: es un cambio del catálogo que se aplica por migración (`security.md` §4.4: «el catálogo de permisos es datos, no código»).

### 6.2 El reparto

**Se conserva** quiere decir que el código existente se queda con esa operación. **Nuevo** es un permiso que nace. Las descripciones de los conservados se reescriben para decir solo lo que gobiernan.

#### `SP`

| Operación | Requerimiento | Hoy | Desde hoy |
|---|---|---|---|
| `GET /roles` | `RF-SP-002` | `roles:read` | `roles:list` **nuevo** |
| `GET /roles/{id}` | `RF-SP-003` | `roles:read` | `roles:read` se conserva |
| `PATCH /roles/{id}` | `RF-SP-004` | `roles:update` | `roles:update` se conserva |
| `POST /roles/{id}/permissions` | `RF-SP-005` | `roles:update` | `roles:assign-permissions` **nuevo** |
| `POST /roles/{id}/permissions/revocations` | `RF-SP-006` | `roles:update` | `roles:revoke-permissions` **nuevo** |
| `PATCH /roles/{id}/status` | `RF-SP-007` | `roles:update` | `roles:change-status` **nuevo** |
| `PATCH /roles/{id}/parent` | `RF-SP-008` | `roles:update` | `roles:assign-parent` **nuevo** |
| `GET /permissions` | `RF-SP-010` | `permissions:read` | `permissions:list` **nuevo** |
| `GET /permissions/{id}` | `RF-SP-015` | `permissions:read` | `permissions:read` se conserva |
| `GET /memberships` | `RF-SP-017` | `memberships:read` | `memberships:list` **nuevo** |
| `GET /memberships/{id}` | `RF-SP-018` | `memberships:read` | `memberships:read` se conserva |
| `GET /users` | `RF-SP-025` | `users:read` | `users:list` **nuevo** |
| `GET /users/{id}` | `RF-SP-026` | `users:read` | `users:read` se conserva |
| `PATCH /users/{id}` | `RF-SP-027` | `users:update` | `users:update` se conserva |
| `PATCH /users/{id}/status` | `RF-SP-028` | `users:update` | `users:change-status` **nuevo** |
| `POST /users/{id}/roles` | `RF-SP-030` | `users:assign-roles` | `users:assign-roles` se conserva |
| `POST /users/{id}/roles/revocations` | `RF-SP-031` | `users:assign-roles` | `users:revoke-roles` **nuevo** |
| ~~`PUT /users/{id}/membership`~~ | ~~`RF-SP-032`~~ | ~~`users:assign-membership`~~ | ~~se conserva~~ — **la operación se retira el 23-09-2026** con su requerimiento (`RN-SP-056`): el permiso sale del catálogo |
| ~~`DELETE /users/{id}/membership`~~ | ~~`RF-SP-033`~~ | ~~`users:assign-membership`~~ | ~~`users:revoke-membership` **nuevo**~~ — **retirado el 23-09-2026**, cuatro días después de nacer, con la operación que lo motivó |
| `GET /users/{id}/team` | `RF-SP-042` | `users:read` | `users:read-team` **nuevo** |
| `GET /broker-accounts` | `RF-SP-057` | `broker-accounts:read` | `broker-accounts:read` se conserva |
| `GET /broker-accounts/indicators` | `RF-SP-058` | `broker-accounts:read` | `broker-accounts:read-indicators` **nuevo** |

**Trece nuevos.** No cambian `roles:create`, `roles:delete`, `users:create`, `users:delete`, `users:reset-password`, `users:assign-supervisor`, `memberships:create`, `countries:create`, `countries:update`, `currencies:read`, `currencies:update`, `exchange-rates:create` ni los cuatro `audit:`: ya gobiernan una sola operación. **`RF-SP-059`**, pendiente, declaraba `users:read` para `GET /users/{id}/sellers`: nacerá con `users:read-sellers`, y su ficha se corrige aquí.

#### `PM`

| Operación | Requerimiento | Hoy | Desde hoy |
|---|---|---|---|
| `GET /products` | `RF-PM-002` | `products:read` | `products:list` **nuevo** |
| `GET /products/{id}` | `RF-PM-003` | `products:read` | `products:read` se conserva |
| `PATCH /products/{id}` | `RF-PM-004` | `products:update` | `products:update` se conserva |
| `PATCH /products/{id}/status` | `RF-PM-005` | `products:update` | `products:change-status` **nuevo** |
| `PUT /products/{id}/cover` | `RF-PM-014` | `products:update` | `products:set-cover` **nuevo** |
| `DELETE /products/{id}/cover` | `RF-PM-015` | `products:update` | `products:remove-cover` **nuevo** |
| `POST /products/{id}/comments` | `RF-PM-009` | `products:comment` | `products:comment` se conserva |
| `GET /products/{id}/comments/mine` | `RF-PM-013` | `products:comment` | `products:read-own-comments` **nuevo** |
| `PATCH /products/{id}/comments/{commentId}` | `RF-PM-010` | `products:comment` | `products:update-comment` **nuevo** |
| `DELETE /products/{id}/comments/{commentId}` | `RF-PM-011` | `products:comment` | `products:delete-comment` **nuevo** |
| `GET /packages` | `RF-PM-018` | `packages:read` | `packages:list` **nuevo** |
| `GET /packages/{id}` | `RF-PM-019` | `packages:read` | `packages:read` se conserva |
| `PATCH /packages/{id}` | `RF-PM-020` | `packages:update` | `packages:update` se conserva |
| `PATCH /packages/{id}/status` | `RF-PM-021` | `packages:update` | `packages:change-status` **nuevo** |
| `PUT /packages/{id}/cover` | `RF-PM-028` | `packages:update` | `packages:set-cover` **nuevo** |
| `DELETE /packages/{id}/cover` | `RF-PM-029` | `packages:update` | `packages:remove-cover` **nuevo** |
| `POST /packages/{id}/products` | `RF-PM-023` | `packages:update` | `packages:add-product` **nuevo** |
| `PATCH /packages/{id}/products/{productId}` | `RF-PM-024` | `packages:update` | `packages:update-product` **nuevo** |
| `DELETE /packages/{id}/products/{productId}` | `RF-PM-025` | `packages:update` | `packages:remove-product` **nuevo** |

**Catorce nuevos.** **Las reseñas se quedan bajo el recurso `products`** y no estrenan `product-comments:`, porque `products:comment` se conserva —no se renombra nada— y tres códigos de un recurso y uno de otro sería peor que cuatro del mismo. **«Habilita, no autoriza» sigue** (`RN-PM-027`): `products:update-comment` permite corregir **la propia**, y una ajena responde `403` como hoy.

#### `CM`

| Operación | Requerimiento | Hoy | Desde hoy |
|---|---|---|---|
| `POST /commission-rates` | `RF-CM-001` | `commissions:create` | `commissions:create` se conserva |
| `GET /commission-rates` | `RF-CM-002` | `commissions:read` | `commissions:read` se conserva |
| `PATCH /commission-rates/{id}` | `RF-CM-003` | `commissions:update` | `commissions:update` se conserva |
| `POST /commission-rates/{id}/deletion` | `RF-CM-004` | `commissions:delete` | `commissions:delete` se conserva |
| `GET /commissions/effective` | `RF-CM-005` | `commissions:read` | `commissions:read-effective` **nuevo** |
| `POST /user-commission-rates` | `RF-CM-006` | `commissions:create` | `user-commission-rates:create` **nuevo** |
| `GET /user-commission-rates` | `RF-CM-006` | `commissions:read` | `user-commission-rates:read` **nuevo** |
| `PATCH /user-commission-rates/{id}` | `RF-CM-006` | `commissions:update` | `user-commission-rates:update` **nuevo** |
| `POST /user-commission-rates/{id}/deletion` | `RF-CM-006` | `commissions:delete` | `user-commission-rates:delete` **nuevo** |
| `GET /product-commission-rates` | `RF-CM-002` | `commissions:read` | `product-commission-rates:read` **nuevo** |

**Seis nuevos.** `commissions:` se queda con **las tasas de rol** (`/commission-rates`), que son el recurso principal del módulo; las personalizadas son un recurso con identidad y tabla propias (`user_commission_rates`) y ganan los cuatro suyos. **Cuatro operaciones bajo un mismo requerimiento no contradicen la regla**: la regla cuenta operaciones, no requerimientos, y `RF-CM-006` publica cuatro. `GET /product-commission-rates` —«qué comisiona un producto, y a qué rol»— es una vista por producto de las tasas de rol y se atribuye a `RF-CM-002`, cuyo listado la acompaña desde `V94`.

#### `MV`

Sin cambios: `movements:read`, `movements:create`, `movements:confirm` y `movements:void` gobiernan una operación cada uno desde que nacieron. **La reserva del superadministrador sobre los cuatro no se toca.**

#### `AC`

| Operación | Requerimiento | Hoy | Desde hoy |
|---|---|---|---|
| `GET /course-categories` | `RF-AC-002` | `course-categories:read` | `course-categories:list` **nuevo** |
| `GET /course-categories/{id}` | `RF-AC-003` | `course-categories:read` | `course-categories:read` se conserva |
| `GET /courses` | `RF-AC-009` | `courses:read` | `courses:list` **nuevo** |
| `GET /courses/{id}` | `RF-AC-010` | `courses:read` | `courses:read` se conserva |
| `PATCH /courses/{id}` | `RF-AC-011` | `courses:update` | `courses:update` se conserva |
| `PATCH /courses/{id}/status` | `RF-AC-012` | `courses:update` | `courses:change-status` **nuevo** |
| `POST /courses/{courseId}/modules` | `RF-AC-022` | `courses:update` | `course-modules:create` **nuevo** |
| `PATCH …/modules/{moduleId}` | `RF-AC-023` | `courses:update` | `course-modules:update` **nuevo** |
| `PATCH …/modules/{moduleId}/status` | `RF-AC-024` | `courses:update` | `course-modules:change-status` **nuevo** |
| `POST …/modules/{moduleId}/deletion` | `RF-AC-025` | `courses:update` | `course-modules:delete` **nuevo** |
| `POST …/lessons` | `RF-AC-028` | `courses:update` | `lessons:create` **nuevo** |
| `GET …/lessons/{lessonId}` | `RF-AC-036` | `courses:read` | `lessons:read` **nuevo** |
| `PATCH …/lessons/{lessonId}` | `RF-AC-029` | `courses:update` | `lessons:update` **nuevo** |
| `PATCH …/lessons/{lessonId}/status` | `RF-AC-030` | `courses:update` | `lessons:change-status` **nuevo** |
| `POST …/lessons/{lessonId}/deletion` | `RF-AC-031` | `courses:update` | `lessons:delete` **nuevo** |
| `POST /courses/{courseId}/categories` | `RF-AC-016` | `courses:update` | `courses:assign-category` **nuevo** |
| `DELETE /courses/{courseId}/categories/{categoryId}` | `RF-AC-017` | `courses:update` | `courses:revoke-category` **nuevo** |
| `POST /courses/{courseId}/recommendations` | `RF-AC-018` | `courses:update` | `courses:assign-recommendation` **nuevo** |
| `DELETE /courses/{courseId}/recommendations/{recommendedCourseId}` | `RF-AC-019` | `courses:update` | `courses:revoke-recommendation` **nuevo** |
| `POST /courses/{courseId}/memberships` | `RF-AC-020` | `courses:update` | `courses:assign-membership` **nuevo** |
| `DELETE /courses/{courseId}/memberships/{membershipId}` | `RF-AC-021` | `courses:update` | `courses:revoke-membership` **nuevo** |

**Dieciocho nuevos**, seis de ellos sobre las relaciones del bloque 4, que **se construye antes que esto y con `courses:update`**, como sus tripletas aprobadas dicen, y se reparte aquí junto con los bloques 1 a 3: repartir `AC` es un trabajo entero y no uno por bloque, y el reverso del razonamiento de `ac.md` §7 —«módulos y lecciones bajo `courses:update`»— merece una sola fila de control y no un cambio de paso. Lo acordó el responsable técnico de `AC` el 19-09-2026. **Módulos y lecciones ganan recurso propio** porque tienen identidad, tabla y rutas propias; las relaciones no, y por eso son `courses:assign-…`.

### 6.3 Las convenciones, para lo que venga

| Caso | Forma | Ejemplos |
|---|---|---|
| Leer la colección / leer una | `<recurso>:list` / `<recurso>:read` | `users:list`, `users:read` |
| Leer algo derivado de una | `<recurso>:read-<qué>` | `users:read-team`, `broker-accounts:read-indicators`, `commissions:read-effective` |
| Cambiar el estado | `<recurso>:change-status` | `roles:change-status` |
| Portada | `<recurso>:set-cover` / `<recurso>:remove-cover` | `products:set-cover` |
| Relación sin identidad propia, dar y quitar | `<recurso>:assign-<hijo>` / `<recurso>:revoke-<hijo>` | `users:assign-roles`, `courses:revoke-category` |
| Elemento de una colección propia del recurso | `<recurso>:add-<hijo>` / `update-<hijo>` / `remove-<hijo>` | `packages:add-product` |
| Hijo con identidad, tabla y rutas propias | Recurso propio | `course-modules:create`, `lessons:read` |
| Lo propio del actor sobre un recurso | `<recurso>:<verbo>-own-<qué>` | `products:read-own-comments` |

**`list` y `read` y no `read` y `read-detail`**: el detalle es la lectura por antonomasia y conserva el código; la colección es la que gana verbo. **`assign`/`revoke` y no `add`/`remove` para las relaciones** porque es el par que `users:assign-roles` estableció el 21-08-2026 y `RF-SP-031` se llama «retirar roles»; `add`/`remove` queda para los elementos de una colección con datos propios —el producto de un paquete lleva descuento—.

## 7. Precondiciones y postcondiciones

**Precondiciones:** ninguna. Se aplica al arrancar con `V28`.

**Postcondiciones:** el catálogo tiene ciento once permisos; cada operación exige uno y ninguno se repite; todo rol que portaba un código dividido porta todos sus hijos; las descripciones de los conservados dicen solo lo que gobiernan.

## 8. Flujo principal

1. La migración siembra los cincuenta y un permisos nuevos, con identificador literal (Art. V.11).
2. Por cada código dividido, **inserta en `role_permissions` cada hijo para todo rol que porte el padre**.
3. Reescribe nombre y descripción de los veintiún códigos que se estrechan.
4. Comprueba, y aborta si no: ciento once permisos; `SUPERADMIN` con ciento once; `ADMIN` con ciento cinco —los seis de la reserva siguen fuera—; y **ningún rol que porte un padre sin alguno de sus hijos**.
5. Cada controlador declara en cada operación el permiso de §6.2, y el contrato lo publica (`x-required-permission`).

## 9. Flujos alternativos

| ID | Caso | Comportamiento |
|---|---|---|
| `FA-001` | Un rol creado a mano porta `roles:update` | Recibe `roles:change-status`, `roles:assign-parent`, `roles:assign-permissions` y `roles:revoke-permissions`. Puede lo mismo que ayer |
| `FA-002` | Un rol creado a mano porta un código que **no** se divide | No recibe nada. No hay nada que repartir |
| `FA-003` | Alguien con `roles:update` intenta asignar permisos tras la migración | Solo puede si su rol recibió `roles:assign-permissions` — y lo recibió si portaba `roles:update`. **`403` únicamente si alguien se lo quitó después** |
| `FA-004` | Un `ADMIN` crea un rol con `users:list` y sin `users:read` | Válido: ese rol ve el listado y recibe `403` en el detalle. **Es exactamente lo que se pidió poder hacer** |
| `FA-005` | Un permiso nuevo intenta gobernar dos operaciones en un módulo futuro | `EndpointPermissionsIT` falla y dice cuáles |

## 10. Seguridad

**Nadie gana nada que no tuviera.** La migración da hijos a quien portaba el padre y a nadie más; `RN-SEG-003` se conserva por construcción (§5). **Nadie pierde nada** hasta que un administrador de roles decida recortar, y ese recorte es una operación auditada de `RF-SP-006`.

**La reserva del superadministrador no cambia**: `audit:read-security`, `currencies:update` y los cuatro `movements:` gobiernan una operación cada uno y no se dividen, de modo que `ADMIN` sigue sin ellos y sin ningún hijo suyo, porque no tienen.

**Lo que sí cambia es que `roles:update` deja de ser la llave del reparto.** Hoy quien edita el nombre de un rol asigna permisos; desde hoy asignar permisos exige `roles:assign-permissions`, que es el único código nuevo que este requerimiento considera **de riesgo**: conviene que quien administre roles lo conceda con la misma cautela con que hoy concede `roles:update`.

## 11. Validaciones

No aplica: no hay entrada. Las guardas de la migración (§8, paso 4) hacen de validación del reparto.

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-SP-688` | Tras `V28` el catálogo tiene **ciento once** permisos, sesenta con el identificador que ya tenían y cincuenta y uno nuevos con identificador literal, sin repetidos |
| `CA-SP-689` | **Ningún permiso gobierna dos operaciones**: recorridos todos los endpoints con `@PreAuthorize`, la función operación → permiso es inyectiva (`RN-SEG-014`) |
| `CA-SP-690` | Cada operación de §6.2 exige **exactamente** el permiso que la tabla le asigna, y con el permiso de ayer —el padre— responde `403` cuando el de hoy es otro |
| `CA-SP-691` | `SUPERADMIN` porta los ciento once y `ADMIN` ciento cinco; los seis que le faltan son los de la reserva |
| `CA-SP-692` | Un rol creado **antes** de la migración con `roles:update` porta, después, sus cuatro hijos; y uno con `users:read`, `users:list` y `users:read-team` |
| `CA-SP-693` | Un rol creado antes con un código que no se divide **no recibe** ningún permiso |
| `CA-SP-694` | Un rol con `users:list` y sin `users:read` lista usuarios y recibe `403` en el detalle |
| `CA-SP-695` | Los veintiún códigos que se estrechan tienen nombre y descripción que **no mencionan** las operaciones que perdieron |
| `CA-SP-696` | El contrato OpenAPI declara en cada operación su `x-required-permission` de §6.2, y su descripción dice el mismo nombre |
| `CA-SP-697` | `products:update-comment` y `products:delete-comment` **habilitan y no autorizan**: sobre una reseña ajena responden `403` aunque los porte un administrador (`RN-PM-027`, como hoy) |

**`CA-SP-689` es el criterio que sostiene el requerimiento.** Los demás comprueban el reparto de hoy; ese es el único que impide el de mañana.

## 13. Casos límite

| Caso | Decisión |
|---|---|
| Un rol creado a mano porta **varios** códigos divididos | Recibe los hijos de todos. La inserción es por pareja (rol, padre) y no interfiere entre padres |
| Un rol porta el padre **y** ya tiene algún hijo (imposible hoy: los hijos no existen) | La inserción ignora la pareja existente. Vale para poder reejecutar el reparto si un módulo futuro lo necesita |
| `ADMIN` porta un padre que **no** está en la reserva pero un hijo suyo debiera estarlo | No ocurre: ninguna reserva se divide. Si un día se decidiera reservar un hijo, es una decisión declarada en `security.md` §4.4 y no una omisión de la migración |
| La caché de `rol → permisos` (§4.5) | Se llena al arrancar, después de migrar. No hay instancia en marcha con la caché vieja porque la migración corre al arrancar la misma instancia |
| Un token emitido antes de migrar | Sigue valiendo: lleva roles, no permisos, y los roles resuelven contra el catálogo nuevo |

## 14. Preguntas abiertas resueltas

| # | Pregunta | Resolución |
|---|---|---|
| 1 | ¿Se separan también listado y detalle del mismo recurso? | **Sí, estricto** (19-09-2026, responsable del proyecto). Una excepción reabriría la puerta que se cierra, y diez permisos más cuestan menos que decidir cada vez si una lectura «es la misma cosa» |
| 2 | ¿Se renombra o se retira algún código? | **No.** Cada uno conserva una operación. Renombrar obliga al frontend y a las pruebas a cambiar lo que ya funcionaba sin ganar nada; retirar es otra clase de operación |
| 3 | ¿Quién recibe los permisos nuevos? | **Todo rol que porte el padre**, sin excepción, para que el reparto no sea también un recorte. Recortar es decisión de quien administre roles |
| 4 | ¿Se reparte `AC` por bloques o entero? | **Entero, en `V28`, después del bloque 4** (19-09-2026, responsable técnico de `AC`): las tripletas de `RF-AC-016` a `021` están aprobadas con `courses:update` y cambiarlas a medio construir dejaría `AC` en dos estados |
| 5 | ¿Cómo se registra el cambio? | **Como requerimiento con tripleta**, por decisión del responsable del proyecto (19-09-2026), y no como enmienda directa: toca sesenta y ocho requerimientos de cuatro módulos —cuarenta y ocho cambian de permiso— y merece trazabilidad propia |

## 15. Control de cambios

| Versión | Fecha | Cambio | Responsable |
|---|---|---|---|
| 0.1.0 | 19-09-2026 | Redacción inicial, a partir de la petición del responsable del proyecto —«que sea solo un permiso por ruta»— y de dos decisiones suyas del mismo día: **estricto**, también listado y detalle; y **como requerimiento**, no como enmienda. Lo que la especificación añade es el reparto completo (§6.2: veintiún códigos divididos, cincuenta y un permisos nuevos, ciento once en total), las convenciones de nombre para que el siguiente módulo no las invente (§6.3), la regla `RN-SEG-014` que impide que vuelva a agruparse, y la decisión de que **nadie pierda nada al migrar** —cada hijo a todo rol que portaba el padre—, que es lo que convierte un reparto en un reparto y no en un recorte. | Responsable del proyecto |
