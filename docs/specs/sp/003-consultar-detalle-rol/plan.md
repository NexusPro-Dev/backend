# PLAN — `RF-SP-003` Consultar detalle de un rol

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-003` |
| Especificación | [`spec.md`](spec.md) |
| `spec.md` aprobada el | 20-08-2026 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable técnico |
| Fecha de aprobación | 21-08-2026 |

!!! info "Qué va en este documento"

    **Cómo se construye.** Las decisiones técnicas que la especificación deliberadamente no toma.

    **Prueba de pertenencia:** si al negocio no le importa ni lo entendería, va aquí.

El comportamiento —flujos, excepciones, validaciones y criterios de aceptación— es el de [`spec.md`](spec.md) y no se repite aquí. Este documento decide dos cosas: cuántas sentencias cuesta el detalle y de qué forma, y por qué un identificador malformado es `400` y no `404`.

!!! warning "Revisado el 21-08-2026"

    Este plan se aprobó por la mañana con un diseño muy distinto: el número de usuarios asignados se obtenía de `USR` a través del puerto `RoleAssignmentCounter`, que devolvía un tipo sellado `Known` / `Unavailable`, con adaptador nulo, `@ConditionalOnMissingBean` y una propiedad que hacía fallar el arranque en producción.

    Toda esa maquinaria existía por una sola razón: `architecture.md` §5.3 prohíbe que un módulo lea las tablas de otro, y los usuarios vivían en `USR`. Al retirarse ese módulo y absorber `SP` los usuarios (`modules.md` v0.9.0, `requirements/sp.md` v1.3.0), **la frontera desapareció y con ella la razón de la inversión de dependencias**. El conteo pasa a ser una subconsulta correlacionada sobre una tabla propia, como la de roles hijos.

    Se retiran, por tanto: el puerto `RoleAssignmentCounter`, el tipo `AssignedUserCount`, el adaptador nulo, la propiedad `nexus.usr.assignment-counter.required`, la envoltura transaccional `RoleDetailReader` y el objeto `assignedUsers` con su campo `available`.

---

## 1. Enfoque

El detalle se resuelve con **dos sentencias de lectura sobre proyecciones**, en la línea de `RF-SP-002`: la primera trae el rol, su padre por `LEFT JOIN`, el número de hijos y el número de usuarios asignados por sendas subconsultas correlacionadas; la segunda trae los permisos declarados en una sola pasada sobre `role_permissions ⋈ permissions`. No hay entidades JPA en el camino, y por tanto no hay colección perezosa que pueda dispararse ni navegación al padre que produzca una consulta por fila.

El número de usuarios asignados dejó de gobernar el diseño. Con los usuarios dentro de `SP`, `user_roles` es una tabla del propio módulo y contarla no cruza ninguna frontera: es una subconsulta más en la misma sentencia. Lo único que impone es una **dependencia de esquema**: `users` y `user_roles` las crean `RF-SP-024` y `RF-SP-030`, que por eso se adelantaron en el orden de implementación de `requirements/sp.md` §6.1 (§2).

`domain` no participa. La única regla aplicable, `RN-SEG-004`, es negativa: se cumple porque el plan de ejecución no contiene ningún recorrido de ancestros, no porque haya código que la verifique. Eso condiciona cómo se prueba (§11): se prueba contando sentencias, no ejercitando un método.

## 2. Cambios de esquema

**No hay migración. Este requerimiento no cambia el esquema.**

Todo lo que necesita ya existe y se da por aplicado:

| Objeto | De dónde viene | Para qué lo usa este requerimiento |
|---|---|---|
| `roles` | `V5__create_roles.sql` (`RF-SP-001`) | Fila del rol y del padre |
| `role_permissions` | `V6__create_role_permissions.sql` (`RF-SP-001`) | Permisos declarados. Su clave primaria compuesta `(role_id, permission_id)` sirve directamente la consulta, que filtra por el prefijo `role_id` |
| `permissions` | `V2__create_permissions.sql` (`RF-SP-010`) | Código y nombre de cada permiso |
| `ix_roles_parent_role_id` | `V5__create_roles.sql` | Conteo de hijos directos sin recorrerlos |
| `roles_pkey` | `V5__create_roles.sql` | Acceso por identificador |

Tampoco hay migración de permisos: `roles:read` lo crea `V3__seed_permissions.sql` (`RF-SP-010`) y ya lo usa `RF-SP-002` (§5).

`ix_roles_busqueda` y la función `f_unaccent` que crea `V8` (`RF-SP-002`) **no intervienen**: aquí no hay búsqueda por texto.

**Dependencia de esquema con los usuarios.** El conteo de usuarios asignados lee `user_roles`, que crea `RF-SP-030`; esa tabla referencia a `users`, que crea `RF-SP-024`. Ninguna de las dos pertenece a este requerimiento y ninguna se declara aquí: diseñar su esquema sin sus especificaciones sería exactamente lo que el Art. I.6 impide. Lo que sí impone este plan es el **orden**: ambas tripletas se adelantan por delante de esta en `requirements/sp.md` §6.1, fijado el 21-08-2026.

Dos cosas que esas migraciones deben traer, y que se declaran aquí porque es este requerimiento quien las necesita:

| Objeto | De quién | Para qué |
|---|---|---|
| `ix_user_roles_role_id` sobre `user_roles(role_id)` | `RF-SP-030` | Es la columna por la que se cuenta. Sin índice, cada detalle de rol recorre la tabla entera de asignaciones |
| Borrado lógico en `users` | `RF-SP-024` | El conteo excluye a los usuarios eliminados (§4). Si `users` no tuviera `deleted_at`, la semántica del conteo tendría que cambiar |

Los recordatorios de la plantilla no aplican: no se crea ninguna tabla, así que no hay clave primaria UUID v7, ni `created_at`/`updated_at`, ni columnas de actor que omitir, ni integridad declarativa que añadir.

## 3. Componentes afectados

Paquete raíz del módulo: `com.factech.nexus.modules.system`. Reglas de dependencia de `architecture.md` §5.2.

| Capa | Componente | Nuevo / Modificado | Responsabilidad |
|---|---|---|---|
| `domain` | — | — | Sin participación. `RN-SEG-004` se cumple por ausencia de recorrido, no por código (§11) |
| `application` | `GetRoleDetailService` | Nuevo | Caso de uso. `@Transactional(readOnly = true)` (§7) |
| `application` | `RoleDetailQueryRepository` | Nuevo | Puerto de consulta: `findById(UUID)` devuelve `Optional<RoleDetail>`; `findDeclaredPermissions(UUID)` devuelve la lista de permisos |
| `application` | `RoleDetail` | Nuevo | Modelo de lectura del rol: campos escalares, padre embebido, `childRoleCount` y `assignedUserCount` |
| `application` | `PermissionItem` | Nuevo | Modelo de lectura de un permiso declarado: `id`, `code`, `name`, y desde `RF-SP-010` también `resource`, `action` y `description` |
| `infrastructure` | `JpaRoleDetailQueryRepository` | Nuevo | Adaptador. Construye las dos proyecciones con la API de criterios |
| `infrastructure` | `RoleEntity`, `RolePermissionEntity`, `UserRoleEntity` | Sin cambios / Nuevo | Solo como metamodelo para nombrar columnas; no se instancian. `UserRoleEntity` lo declara `RF-SP-030` |
| `infrastructure` | `RoleJpaMapper` | Sin cambios | No interviene: no se carga el agregado |
| `api` | `RoleController` | Modificado | Añade `GET /api/v1/roles/{id}`. Declara el permiso y delega |
| `api` | `RoleDetailResponse` | Nuevo | DTO de salida del detalle. Reutiliza `RoleSummaryResponse` y `PermissionResponse` |
| `api` | `RoleSummaryResponse` | Sin cambios | Definido en `RF-SP-001`. Se reutiliza tal cual |
| `api` | `PermissionResponse` | Modificado | Definido en `RF-SP-001` y ampliado el 21-08-2026 por `RF-SP-010` con `resource`, `action` y `description`, que el detalle del rol devuelve también. La `spec.md` de este requerimiento pide en §6.2 «lista explícita de los permisos que declara», sin enumerar campos |
| `shared/api` | `CanonicalUuidConverter` | Nuevo | `Converter<String, UUID>` que exige la forma canónica de 36 caracteres. Es lo que hace que un identificador malformado sea `400` y no `404` (§4) |
| `shared/error` | `GlobalExceptionHandler` | Modificado | Traduce `ResourceNotFoundException` a `404` y el fallo de conversión del identificador a `400` con `VAL-001` |

Tres decisiones de reparto que conviene justificar:

**El puerto de consulta vive en `application`, no en `domain`.** `domain` solo puede depender de sí mismo y del JDK, y `RoleDetail` no es el agregado `Role`: le faltan cosas, le sobra la forma del padre y existe únicamente para dar forma a una respuesta. Es el mismo criterio con el que `RF-SP-001` situó `AuthenticatedActor` y `RoleChangeAuditor` en `application`, y `RF-SP-002` situó allí `RoleQueryRepository`.

**`RoleDetailResponse` no es `RoleResponse`.** Se estudió ampliar el DTO de `RF-SP-001` con los dos conteos. Se descarta: `RoleResponse` lo devuelve también el alta y las cinco operaciones de edición, y ninguna de ellas necesita saber cuántos usuarios tiene el rol —en el alta el número es cero por construcción—. Añadir dos subconsultas a seis endpoints para que uno las use es coste sin destinatario.

**`UserRoleEntity` se usa como metamodelo y no se declara aquí.** La tabla es de `RF-SP-030` y su mapeo también. Este requerimiento la lee, y leer una tabla del propio módulo desde otra funcionalidad es exactamente lo que `architecture.md` §5.3 permite: la prohibición era entre módulos, y ya no hay dos.

**No se reutiliza `RoleListItem` de `RF-SP-002`.** Le faltan los permisos y el conteo de hijos, y le sobra la forma pensada para una fila de listado. Compartir el modelo obligaría a que el listado arrastrara campos que `CA-SP-148` le prohíbe llenar.

## 4. Contrato de API

| Método | Ruta | Descripción |
|---|---|---|
| `GET` | `/api/v1/roles/{id}` | Detalle de un rol con sus permisos declarados, su padre, el número de hijos directos y el de usuarios asignados |

**Petición**

```
GET /api/v1/roles/018f3a2b-7c41-7000-9a3d-1f2e5b8c9d10
```

Sin cuerpo y sin parámetros de consulta. No hay `?include=…` ni forma de pedir un subconjunto: la especificación define un único detalle y ofrecer variantes multiplicaría los contratos que hay que probar.

**Respuesta `200`**

```json
{
  "id": "018f3a2b-7c41-7000-9a3d-1f2e5b8c9d10",
  "code": "CONTABILIDAD",
  "name": "Contabilidad",
  "description": "Rol del área contable.",
  "roleType": "FUNCIONARIO",
  "status": "ACTIVO",
  "isSystem": false,
  "parentRole": {
    "id": "018f3a2b-7c41-7000-9a3d-1f2e5b8c9d01",
    "code": "ADMIN",
    "name": "Administrador"
  },
  "permissions": [
    { "id": "018f3a2b-7c41-7000-9a3d-1f2e5b8c9d02", "code": "audit:read-changes", "name": "Consultar auditoría de cambios" },
    { "id": "018f3a2b-7c41-7000-9a3d-1f2e5b8c9d03", "code": "roles:read", "name": "Consultar roles" }
  ],
  "childRoleCount": 3,
  "assignedUserCount": 12,
  "createdAt": "2026-08-20T14:32:11Z",
  "updatedAt": "2026-08-20T14:32:11Z"
}
```

Decisiones del contrato:

- **`childRoleCount` es un número, nunca una lista** (`spec.md` §14, pregunta 2). No existe `childRoles` ni una página embebida: el listado se obtiene con `GET /api/v1/roles?parentRoleId={id}`, que ya existe y ya está paginado. Es lo que hace verificable a `CA-SP-150`.
- **El conteo de hijos excluye los eliminados lógicamente** e incluye los inactivos. Un rol eliminado es inexistente en todo este requerimiento —es el criterio que la especificación fija para el propio rol consultado— y un rol inactivo sigue existiendo y sigue colgando de su padre. El número coincide así con el `totalElements` de `GET /api/v1/roles?parentRoleId={id}` con el `includeDeleted=false` que trae por defecto; con `includeDeleted=true` no coincidirá, y eso es correcto, no un defecto.
- **`permissions` va completa y sin paginar**, ordenada por `code` para que la respuesta sea estable entre llamadas. `architecture.md` §7.4 exige paginar «las colecciones», y aquí se aparta de forma consciente: los permisos declarados de un rol son decenas (`spec.md` §13), no constituyen un recurso navegable por sí mismos y paginarlos obligaría a un segundo endpoint para responder «qué puede hacer este rol», que es la única pregunta del requerimiento. El orden por `code` no es decorativo: sin `ORDER BY` explícito PostgreSQL no garantiza orden alguno, y una lista que cambia de orden entre dos llamadas hace inútil cualquier comparación entre roles.
- **`assignedUserCount` es un número simple**, y se desarrolla abajo.
- **`isSystem`, `createdAt` y `updatedAt`** los declara `spec.md` §6.2 como «marca de rol de sistema» y «fechas de creación y modificación». Se incluyen además porque `RoleResponse` de `RF-SP-001` ya los devuelve y porque el detalle es la pantalla desde la que se edita, se cambia de estado y se elimina un rol: sin `isSystem`, el cliente no puede saber qué acciones le están vedadas. El **actor** de esas fechas no se devuelve: reside en la auditoría (Art. V.7).
- **No existe `deletedAt`.** Un rol eliminado devuelve `404`, de modo que el campo sería siempre nulo.
- **No existe `createdBy`** ni equivalente: el actor no vive en la tabla de negocio (Art. V.7). Quién creó o modificó el rol se responde con `RF-SP-011`.

### El número de usuarios asignados

Es una **subconsulta correlacionada más** en la primera sentencia, junto a la de roles hijos:

```sql
(SELECT count(DISTINCT ur.user_id)
   FROM user_roles ur
   JOIN users u ON u.id = ur.user_id AND u.deleted_at IS NULL
  WHERE ur.role_id = r.id)
```

Cuatro puntos de semántica, que este requerimiento fija y `RF-SP-009` reutiliza:

1. **Cuenta usuarios distintos, no asignaciones.** Si un usuario llegara a tener el rol por dos caminos, cuenta una vez. Es lo que hace correcto el `DISTINCT` aunque la clave primaria de `user_roles` ya deba impedirlo: el conteo no depende de que esa restricción exista.
2. **Cuenta usuarios en cualquier estado** —`ACTIVO`, `INACTIVO`, `BLOQUEADO`, `PENDIENTE`— y **excluye los eliminados lógicamente**. La distinción importa porque de ella depende `RN-SEG-008`: un usuario bloqueado sigue teniendo el rol y su existencia debe impedir el borrado; uno eliminado no existe. Es el mismo criterio con el que `RF-SP-006` trata a los roles hijos inactivos.
3. **No devuelve identidades.** Un número, no una lista: `spec.md` §4.2 excluye el listado de usuarios, que es `RF-SP-025`.
4. **No exige un permiso adicional.** Se consideró pedir también `users:read`: se descarta porque el dato es un agregado sin identidad y porque haría que la forma de la respuesta dependiera de quién pregunta, obligando al cliente a tratar dos formas distintas del mismo recurso.

**`assignedUserCount` es un número, no un objeto.** El borrador de este plan lo devolvía como `{ "count": …, "available": … }` para poder expresar «no se pudo saber», que era un estado real cuando el dato venía de otro módulo. Con `user_roles` en el propio esquema ese estado no existe: la subconsulta devuelve un número o la petición entera falla. Un envoltorio para representar una indisponibilidad imposible sería ceremonia sin contenido, y obligaría a todo cliente a comprobar un booleano que siempre vale lo mismo.

**Cero es un dato, no una ausencia.** Un rol que nadie tiene devuelve `0`, y eso es exactamente lo que el cliente necesita saber antes de eliminarlo.

**Errores**

| Código | Cuándo | `error_code` | Campo en `errors` |
|---|---|---|---|
| `400` | El identificador no es un UUID en forma canónica | `VAL-001` | `id` |
| `401` | Token ausente o inválido | `AUTH-001` | — |
| `403` | Autenticado sin `roles:read` | `AUTH-002` | — |
| `404` | No existe rol con ese identificador, o está eliminado lógicamente | `EX-001` | — |
| `500` | Fallo no controlado | `ERR-500` | — |

- **`404` y no `422`.** Es la diferencia con `RF-SP-001`, donde un rol padre inexistente devuelve `422`: allí el recurso de la petición era la colección `/api/v1/roles`, que existe, y el identificador inválido venía en el cuerpo; aquí el recurso de la petición **es el rol**, y su ausencia es exactamente lo que `404` significa en `architecture.md` §7.2. La excepción es `ResourceNotFoundException`, que `development-guide.md` §7.1 ya contempla: a diferencia del `422` de `RF-SP-001`, no hay que ampliar la jerarquía.
- **`VAL-002` no produce un código propio.** Enuncia como validación lo mismo que `EX-001`, igual que `VAL-005` y `VAL-006` respecto de `EX-001` en `RF-SP-001`. Un solo hecho, un solo código.
- **Un rol eliminado devuelve el mismo `404` y el mismo mensaje que uno inexistente**, sin ninguna pista de que existió. Reconstruir qué era corresponde a `RF-SP-012` sobre `audit_deletion_log` (Art. V.13).
- El `type` `…/errors/no-encontrado` es **el primero de este módulo**; los demás los estrenó `RF-SP-001`. El formato es el de `architecture.md` §7.3, con `correlationId` siempre presente.
- **La degradación del conteo de usuarios no aparece en esta tabla**: no produce error de API.

**Por qué un identificador malformado es `400` y no `404`.** `spec.md` §13 lo exige y el mecanismo no es gratuito. Dos formas naturales de escribir el endpoint lo incumplen:

- Declarar la ruta con restricción de patrón —`@GetMapping("/{id:[0-9a-fA-F-]{36}}")`— hace que un identificador que no case **no encuentre manejador**, y Spring responde `404`: precisamente el error que la especificación prohíbe.
- Declarar el parámetro como `UUID` y confiar en la conversión por defecto tampoco basta. `UUID.fromString` del JDK acepta formas no canónicas: `1-1-1-1-1` se convierte sin error en `00000001-0001-0001-0001-000000000001`. Ese identificador es sintácticamente basura pero se convierte, llega a la consulta, no encuentra fila y devuelve `404`.

Por eso el parámetro se declara `UUID` y la conversión la hace `CanonicalUuidConverter`, en `shared/api`, que exige los 36 caracteres canónicos antes de delegar en `UUID.fromString`; el fallo de conversión lo traduce `GlobalExceptionHandler` a `400` con `VAL-001` y campo `id`. **No se valida la versión del UUID**: un v4 bien formado es un identificador válido que simplemente no existe, y rechazarlo por su versión sería inventar una regla que ningún documento aprueba.

**Cuántas consultas cuesta el detalle.** Dos sentencias contra PostgreSQL, y ninguna llamada fuera del proceso:

```sql
-- 1: rol, padre, número de hijos y número de usuarios asignados
SELECT r.id, r.code, r.name, r.description, r.role_type, r.status, r.is_system,
       r.created_at, r.updated_at,
       p.id, p.code, p.name,
       (SELECT count(*) FROM roles c
         WHERE c.parent_role_id = r.id AND c.deleted_at IS NULL)
       (SELECT count(DISTINCT ur.user_id) FROM user_roles ur
          JOIN users u ON u.id = ur.user_id AND u.deleted_at IS NULL
         WHERE ur.role_id = r.id),
  FROM roles r
  LEFT JOIN roles p ON p.id = r.parent_role_id AND p.deleted_at IS NULL
 WHERE r.id = :id AND r.deleted_at IS NULL;

-- 2: permisos declarados
SELECT pe.id, pe.code, pe.name
  FROM role_permissions rp
  JOIN permissions pe ON pe.id = rp.permission_id
 WHERE rp.role_id = :id
 ORDER BY pe.code;
```

- **El conteo de hijos va en la primera sentencia, como subconsulta correlacionada.** Se evalúa una vez —la sentencia devuelve una fila— y se resuelve por `ix_roles_parent_role_id` sin leer las filas hijas. Un tercer viaje a la base de datos no compraría nada. Si la construcción con la API de criterios resultara incómoda de expresar dentro de la proyección, la alternativa es una tercera sentencia de conteo: cambia el coste, no el diseño ni el contrato.
- **Los permisos van en sentencia aparte, y esto no es negociable.** Unir permisos e hijos en una sola sentencia produciría el producto cartesiano de dos colecciones —diez permisos y tres hijos dan treinta filas— y obligaría a deduplicar en memoria un resultado que ya se leyó multiplicado. Es el motivo por el que dos colecciones nunca se traen en la misma consulta.
- **No hay `N+1` posible**, por el mismo argumento de `RF-SP-002`: no se carga `RoleEntity`, así que no hay asociación perezosa que alguien pueda recorrer desde un mapeador, un `toString` o la serialización. El padre lo resuelve el `LEFT JOIN`; los permisos, la segunda sentencia; los hijos, la subconsulta. Ninguna de las tres puede degenerar en una consulta por elemento porque ninguna atraviesa una entidad.
- **El `LEFT JOIN` al padre lleva `deleted_at IS NULL`.** Un padre eliminado no debería existir —`RF-SP-009` rechaza eliminar un rol con hijos por `RN-SEG-008`—, pero si por un defecto ocurriera, el detalle mostraría como padre vigente un rol que el sistema considera inexistente. La condición cuesta nada y cierra la incoherencia. **Consecuencia que hay que aceptar:** en ese caso el rol aparecería con `parentRole` nulo sin ser la raíz.
- **El orden importa.** Primero el rol: si no existe, se devuelve `404` sin ejecutar la segunda sentencia. Los dos conteos van dentro de la primera, de modo que un rol inexistente no cuesta ni una subconsulta de más.

## 5. Autorización

| Endpoint | Permiso requerido |
|---|---|
| `GET /api/v1/roles/{id}` | `roles:read` |

- El permiso **ya existe**: lo crea `V3__seed_permissions.sql` (`RF-SP-010`). No hace falta migración.
- Se declara sobre el método del controlador (`security.md` §6). Un endpoint sin declaración queda inaccesible, no público (Art. IV.1).
- **Es el mismo permiso que `RF-SP-002`.** Detalle y listado responden la misma pregunta con distinto grano; exigir un permiso propio obligaría a concederlos siempre juntos.
- **El conteo de usuarios no exige `users:read`.** Se consideró pedirlo además: se descarta porque el dato es un agregado sin identidad —no se devuelve ninguna persona— y porque haría que la forma de la respuesta dependiera de quién pregunta, obligando al cliente a tratar dos formas del mismo recurso. Quien quiera saber **quiénes** son, usa `RF-SP-025` con su propio permiso.
- **No hay filtrado por alcance de datos**, igual que en `RF-SP-002`: quien tiene el permiso consulta cualquier rol. Si alguna vez se resuelve **D-22** (`requirements/sp.md` §10.2), este endpoint hay que revisarlo, porque hoy no tiene punto donde insertar la restricción.
- La resolución del permiso puede usar la caché de `security.md` §4.5: aquí solo se decide acceso, no un techo de privilegios.
- El `403` lo produce la capa de seguridad antes de entrar al caso de uso, y es ella quien emite el evento de seguridad (§6). `CA-SP-022` se satisface ahí.

## 6. Auditoría

| Operación | Registro | Contenido relevante |
|---|---|---|
| Consulta exitosa | — | **No se audita** |
| Rechazo `400` por `VAL-001` | — | **No se audita**: es validación de formato (`architecture.md` §6.6.4) |
| Rechazo `404` | — | **No se audita.** Ver abajo |
| Denegación `403` | `audit_security_log` | `event_type` de denegación de autorización, `severity = 'MEDIA'`, `outcome = 'FAILURE'`. Lo emite la capa de seguridad |
| Fallo no controlado `5xx` | `audit_error_log` | `error_type = 'UNHANDLED'`, `severity = 'ALTA'` |
| — | `audit_change_log` | No aplica: la consulta no altera el estado (`spec.md` §7) |
| — | `audit_deletion_log` | No aplica: no elimina nada |

Dos decisiones, porque la ausencia de auditoría es tan decisión como su presencia:

- **Una consulta exitosa no produce evento.** El catálogo de `security.md` §8.1 es cerrado y no incluye la lectura de roles. Auditar cada detalle añadiría una fila por pulsación de un administrador. La trazabilidad de quién consultó qué la aporta `request_log`. Es la misma conclusión de `RF-SP-002` §6.
- **El `404` tampoco se audita.** No hay regla de negocio incumplida ni cambio de estado: es un identificador que no encuentra fila, y en un endpoint de consulta eso es navegación, no incidente. Registrarlo como `BUSINESS_RULE` llenaría el registro de errores con enlaces caducados. Difiere de `RF-SP-001`, que sí audita sus rechazos, porque allí cada uno corresponde a una regla identificada.

## 7. Transaccionalidad

| Elemento | Transacción |
|---|---|
| Las dos sentencias de lectura | **Una sola**, `@Transactional(readOnly = true)` sobre `GetRoleDetailService` |
| `audit_error_log` de un fallo no controlado | **Independiente**, `REQUIRES_NEW` (Art. V.14) |
| `audit_security_log` de la denegación `403` | **Independiente**, `REQUIRES_NEW`. La emite la capa de seguridad |
| `request_log` | Ninguna: posterior a la respuesta, *best effort* |

`readOnly = true` no es decorativo: evita que Hibernate registre entidades para comprobación de cambios y marca la transacción como de solo lectura en PostgreSQL, de modo que un defecto no pueda escribir desde un camino de consulta.

**Las tres obtenciones caben en una sola transacción de lectura.** El borrador separaba el conteo de usuarios fuera de ella, mediante una envoltura `RoleDetailReader`, porque una excepción del adaptador de `USR` habría dejado la transacción marcada como *rollback-only* y la petición habría terminado en `500` pese a haber degradado bien. Con el conteo dentro de la misma sentencia esa preocupación desaparece: no hay llamada externa que pueda fallar por su cuenta, y la envoltura se retira.

Como contrapartida menor: bajo `READ COMMITTED`, cada sentencia toma su propia instantánea, de modo que la lista de permisos puede corresponder a un instante distinto al del rol y sus dos conteos. Los dos conteos sí son coherentes entre sí, por ir en la misma sentencia. Elevar el aislamiento a `REPEATABLE READ` encarecería toda consulta por un desfase que en un catálogo de roles es teórico. Se acepta, con el mismo razonamiento de `RF-SP-002` §7.

## 8. Impacto sobre otros módulos

| Módulo | Impacto |
|---|---|
| `RF-SP-024` y `RF-SP-030` | Se adelantan en el orden de implementación de `requirements/sp.md` §6.1, porque crean `users` y `user_roles`. `RF-SP-030` debe además declarar `ix_user_roles_role_id`: es la columna por la que se cuenta, y sin índice cada detalle de rol recorre la tabla entera de asignaciones (§2) |
| `SP` (resto del módulo) | `RF-SP-009` reutiliza la misma semántica de conteo para `RN-SEG-008` (§4). `RF-SP-004` a `RF-SP-008` devuelven `RoleResponse` y no este detalle, de modo que ninguna escritura paga las dos subconsultas |
| `shared/api` | `CanonicalUuidConverter` se registra de forma global: **cambia el comportamiento de todo endpoint con un UUID en la ruta**, presente y futuro. Un identificador no canónico pasa de `404` a `400` en todo el sistema. Es lo correcto y es uniforme, pero es un cambio de mayor alcance que este requerimiento |
| `shared/error` | Estrena la traducción de `ResourceNotFoundException` a `404` y del fallo de conversión del identificador a `VAL-001`. La jerarquía de `development-guide.md` §7.1 ya la contempla: a diferencia del `422` de `RF-SP-001`, no hay que enmendar ese documento |
| `shared/audit` | Estrena `error_type = 'INTEGRATION'`, previsto en el `CHECK` de `V4` pero sin uso hasta ahora |

## 9. Alternativas consideradas

| Alternativa | Por qué se descartó |
|---|---|
| Mantener el puerto `RoleAssignmentCounter` con su tipo sellado | Existía para cruzar una frontera de módulo que ya no existe. Conservarlo obligaría a mantener un adaptador, un tipo sellado y una propiedad de arranque para expresar una indisponibilidad que no puede ocurrir: `user_roles` está en el mismo esquema y en la misma transacción |
| Denormalizar el conteo en una columna `roles.assigned_users_count` | Duplica un dato que puede desincronizarse sin que nada lo detecte. Para `RN-SEG-008` sería peor que no tenerlo: un contador desfasado en cero autoriza borrar un rol con usuarios. Y la subconsulta indexada no cuesta lo suficiente para justificarlo |
| Que el puerto devuelva `long` | No hay ningún valor de `long` que signifique «no se pudo saber». Devolver `0` o `-1` obliga a un acuerdo tácito que el compilador no verifica y que `RF-SP-009` incumpliría con un `if` mal escrito |
| Que el puerto devuelva `null` cuando no puede contar | Misma objeción, con un `NullPointerException` de premio |
| Distinguir en el JSON «módulo no construido» de «módulo no responde» | El consumidor no puede actuar distinto en cada caso, y publicarlo ataría el contrato de la API al calendario de implementación de otro módulo |
| Responder `206 Partial Content` en el caso degradado | `206` está definido para respuestas por rango de bytes. Reutilizarlo para «faltó un campo» confunde a intermediarios y clientes genéricos |
| Devolver la lista de roles hijos en lugar del conteo | `spec.md` §14 lo resolvió: el tamaño de la respuesta dejaría de estar acotado y `CA-SP-150` sería infalseable. El listado ya existe, paginado, en `RF-SP-002` |
| Devolver el conteo de hijos y además una primera página de hijos embebida | Duplicaría la lógica de paginación dentro de un recurso que no es una colección, y obligaría a decidir aquí un tamaño de página propio |
| Paginar `permissions` | Convertiría la única pregunta del requerimiento —«¿qué puede hacer alguien con este rol?»— en dos peticiones, para una lista de decenas de elementos |
| Traer rol, padre, hijos y permisos en una sola sentencia | Producto cartesiano entre dos colecciones: se leen y se transfieren filas multiplicadas para deduplicarlas después en memoria |
| Cargar el agregado `Role` y mapearlo con `RoleJpaMapper` | Reutiliza código de `RF-SP-001` a cambio de exponer la colección perezosa de permisos y de dejar el `N+1` del padre a un `JOIN FETCH` que hay que acordarse de escribir. La proyección hace imposible por construcción lo que el mapeo deja a la disciplina |
| Restringir el identificador en la propia ruta con una expresión regular | Un identificador que no case no encontraría manejador y Spring respondería `404`, que es justo lo que `spec.md` §13 prohíbe |
| Confiar en la conversión por defecto de `String` a `UUID` | `UUID.fromString` acepta formas no canónicas como `1-1-1-1-1` y las expande a un UUID válido, que produciría `404` en lugar de `400` |
| Cachear el conteo de usuarios en `SP` con un tiempo de vida corto | Añade una fuente obsoleta de un dato con el que `RF-SP-009` decide un borrado. El ahorro tampoco existe: es una consulta indexada por petición |
| Devolver también el detalle de roles eliminados con un parámetro `includeDeleted` | `spec.md` §14, pregunta 1: añadiría una rama permanente al endpoint y una segunda fuente del mismo dato. `RF-SP-012` ya conserva el estado del rol al borrarse |

## 10. Riesgos

| Riesgo | Impacto | Mitigación |
|---|---|---|
| El conteo de usuarios se convierte en un recorrido completo de `user_roles` | Medio | Depende de `ix_user_roles_role_id`, que declara `RF-SP-030` (§2). La prueba de `EXPLAIN` de §11 lo verifica; sin ella, el detalle de un rol degradaría a medida que crezca el número de asignaciones, sin que ninguna prueba funcional lo note |
| `RF-SP-003` se implementa antes que `users` y `user_roles` y no compila | Medio | Resuelto por el orden de `requirements/sp.md` §6.1, fijado el 21-08-2026. Es una dependencia de esquema declarada, no una suposición |
| ~~`INT-001` y el uso de `error_type = 'INTEGRATION'` no están respaldados~~ | — | **Sin objeto desde el 21-08-2026:** al desaparecer la frontera con `USR`, este requerimiento ya no degrada ni emite evento de integración alguno. `architecture.md` §7.3 conserva la serie `INT-nnn` declarada, sin consumidor, para el primer sistema externo que la necesite |
| `CanonicalUuidConverter` cambia de `404` a `400` el comportamiento de todo endpoint con UUID en la ruta | Bajo | Es el comportamiento correcto y no hay endpoints publicados que dependan del anterior. Se registra porque el alcance excede a este requerimiento |
| ~~`isSystem`, `createdAt` y `updatedAt` exceden los campos de `spec.md` §6.2~~ | — | **Resuelto el 21-08-2026:** confirmados al aprobar este plan. `spec.md` §6.2 los declara como «marca de rol de sistema» y «fechas de creación y modificación», esta última con la aclaración de que el actor no se devuelve (Art. V.7) |
| El conteo de hijos excluye los eliminados y no coincidirá con `RF-SP-002?includeDeleted=true` | Bajo | Documentado en §4 y probado en §11. La correspondencia declarada es con el listado por defecto |
| Un padre eliminado lógicamente haría aparecer el rol con `parentRole` nulo, indistinguible del rol raíz | Bajo | No debería ocurrir: `RF-SP-009` impide eliminar un rol con hijos (`RN-SEG-008`). Se acepta como estado imposible; si llegara a darse, es un defecto de `RF-SP-009`, no de esta consulta |
| `description` es `text` sin longitud declarada | Bajo | Heredado de `RF-SP-001` §10 y sin resolver. Aquí el impacto es menor que en el listado: una sola fila por respuesta |
| La subconsulta correlacionada del conteo de hijos puede resultar incómoda de expresar en la proyección con la API de criterios | Bajo | Si lo fuera, se separa en una tercera sentencia. Cambia el coste, no el contrato ni ninguna prueba de comportamiento |

## 11. Estrategia de prueba

Niveles: **Integración** (Testcontainers sobre PostgreSQL real, con el esquema de usuarios aplicado) y **API** (extremo a extremo por HTTP, con autenticación). No hay nivel unitario: este requerimiento no toca `domain` y su lógica es una consulta.

| Criterio | Nivel | Qué verifica |
|---|---|---|
| `CA-SP-016` | Integración + API | Un rol con permisos declarados devuelve `200` con la lista completa, ordenada por `code`, sin paginar y sin envoltura de página; el número de elementos coincide con las filas de `role_permissions` de ese rol |
| `CA-SP-017` | Integración + API | La respuesta trae `parentRole` con `id`, `code` y `name`, y `childRoleCount` con el número de hijos directos; con nietos en la tabla, el conteo no los incluye |
| `CA-SP-018` | Integración + API | `FA-001`: un rol sin filas en `role_permissions` devuelve `200` con `permissions` como lista vacía, nunca `null` ni campo ausente |
| `CA-SP-019` | Integración + API | `FA-002`: el rol raíz devuelve `parentRole` nulo. Es la prueba que detecta un `JOIN` interno donde debe haber un `LEFT JOIN` |
| `CA-SP-020` | Integración + API | Un rol con `deleted_at` no nulo devuelve `404` con `EX-001`, con el mismo cuerpo que un identificador inexistente y sin dato alguno del rol |
| `CA-SP-021` | Integración | La traza de sentencias de la petición contiene **exactamente dos** consultas, ninguna recursiva y ninguna sobre los permisos del rol padre. Sobre un rol con abuelo cuyos permisos difieren, la respuesta trae solo los propios. `RN-SEG-004` no tiene prueba unitaria porque no hay código que la implemente: se verifica por lo que la consulta no hace |
| `CA-SP-022` | API | Un actor autenticado sin `roles:read` recibe `403`, no obtiene dato alguno del rol y queda el evento de denegación en `audit_security_log` |
| `CA-SP-149` | Integración + API | Con tres usuarios vigentes que tienen el rol, la respuesta trae `assignedUserCount: 3`. Con uno de ellos eliminado lógicamente, trae `2` |
| `CA-SP-150` | Integración + API | El mismo rol con cero, con tres y con doscientos hijos devuelve el mismo conjunto de campos y el mismo número de sentencias; el cuerpo solo difiere en el valor de `childRoleCount` |

Casos límite de `spec.md` §13 y decisiones de este plan que exigen prueba propia (Art. VII.3):

| Caso | Nivel | Qué verifica |
|---|---|---|
| Rol sin usuarios | API | `assignedUserCount` vale `0`, con el campo presente y no omitido. Cero es un dato |
| Usuario eliminado lógicamente | Integración | No se cuenta. Es la distinción de la que depende `RN-SEG-008` en `RF-SP-009` |
| Usuario bloqueado o inactivo | Integración | **Sí** se cuenta: sigue teniendo el rol, y su existencia debe impedir el borrado |
| Uso efectivo del índice de asignaciones | Integración | El `EXPLAIN` de la primera sentencia muestra el recorrido de `ix_user_roles_role_id` en la subconsulta de usuarios |
| Identificador no canónico | API | `1-1-1-1-1` devuelve `400` con `VAL-001` y campo `id`, **no** `404`. Sin esta prueba, la conversión permisiva del JDK pasa inadvertida |
| Identificador que no es UUID | API | `abc` devuelve `400` con `VAL-001`, no un `404` de manejador ausente |
| Identificador bien formado e inexistente | API | `404` con `EX-001`, sin ejecutar la segunda sentencia |
| Rol con muchos permisos | Integración | Doscientos permisos declarados se devuelven completos en una sola sentencia, sin paginar y en orden estable entre dos llamadas |
| Hijos eliminados lógicamente | Integración | Con dos hijos vigentes y uno eliminado, `childRoleCount` vale `2`, y coincide con el `totalElements` de `GET /api/v1/roles?parentRoleId={id}` |
| Hijos inactivos | Integración | Un hijo `INACTIVO` sí cuenta: sigue existiendo y sigue colgando del padre |
| Número de sentencias por petición | Integración | Dos, con independencia del número de permisos y de hijos. Es la única forma de que el `N+1` no vuelva en una refactorización posterior |

Las reglas de ArchUnit introducidas en `RF-SP-001` —`domain` no importa Spring ni JPA, `api` no accede a `infrastructure`— cubren también este requerimiento. **No se añade la regla que este plan proponía por la mañana**, que prohibía a `modules.system` importar nada de `modules.users`: esos usuarios ya no son otro módulo, y la regla habría prohibido exactamente lo que ahora es correcto. La prohibición de `architecture.md` §5.3 sigue vigente entre módulos distintos; aquí no hay dos.
