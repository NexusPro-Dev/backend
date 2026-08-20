# PLAN — `RF-SP-003` Consultar detalle de un rol

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-003` |
| Especificación | [`spec.md`](spec.md) |
| `spec.md` aprobada el | 20-08-2026 |
| Estado | **Borrador** |
| Autor | Responsable técnico |
| Aprobado por | — |
| Fecha de aprobación | — |

!!! info "Qué va en este documento"

    **Cómo se construye.** Las decisiones técnicas que la especificación deliberadamente no toma.

    **Prueba de pertenencia:** si al negocio no le importa ni lo entendería, va aquí.

El comportamiento —flujos, excepciones, validaciones y criterios de aceptación— es el de [`spec.md`](spec.md) y no se repite aquí. Este documento decide tres cosas: cuántas sentencias cuesta el detalle y de qué forma, cómo se obtiene de `USR` un dato que `SP` no puede leer, y qué se devuelve mientras ese módulo no exista.

---

## 1. Enfoque

El detalle se resuelve con **dos sentencias de lectura sobre proyecciones**, en la línea de `RF-SP-002`: la primera trae el rol, su padre por `LEFT JOIN` y el número de hijos por subconsulta correlacionada; la segunda trae los permisos declarados en una sola pasada sobre `role_permissions ⋈ permissions`. No hay entidades JPA en el camino, y por tanto no hay colección perezosa que pueda dispararse ni navegación al padre que produzca una consulta por fila.

El número de usuarios asignados es el punto que gobierna el diseño. Ese dato pertenece a `USR`, que hoy no existe, y `architecture.md` §5.3 prohíbe leer sus tablas; `requirements/sp.md` §3 lo refuerza desde el otro lado: `SP` es la raíz del grafo de módulos y no puede depender de nadie sin crear un ciclo. La única forma admisible es **invertir la dependencia**: `SP` declara en su capa `application` el puerto `RoleAssignmentCounter` y `USR` lo implementa cuando se construya. Ese puerto devuelve un tipo que distingue «hay tantos usuarios» de «no se pudo saber», y esa distinción se propaga hasta el JSON: el consumidor nunca ve un cero que en realidad significa ignorancia. Mientras `USR` no exista, un adaptador nulo dentro de `SP` responde siempre «no se pudo saber», de modo que este requerimiento se implementa y se despliega completo sin esperar a otro módulo.

`domain` no participa. La única regla aplicable, `RN-SEG-004`, es negativa: se cumple porque el plan de ejecución no contiene ningún recorrido de ancestros, no porque haya código que la verifique. Eso condiciona cómo se prueba (§11): se prueba contando sentencias, no ejercitando un método.

## 2. Cambios de esquema

**No hay migración. Este requerimiento no cambia el esquema.**

Todo lo que necesita ya existe y se da por aplicado:

| Objeto | De dónde viene | Para qué lo usa este requerimiento |
|---|---|---|
| `roles` | `V4__create_roles.sql` (`RF-SP-001`) | Fila del rol y del padre |
| `role_permissions` | `V5__create_role_permissions.sql` (`RF-SP-001`) | Permisos declarados. Su clave primaria compuesta `(role_id, permission_id)` sirve directamente la consulta, que filtra por el prefijo `role_id` |
| `permissions` | `V1__create_permissions.sql` (`RF-SP-010`) | Código y nombre de cada permiso |
| `ix_roles_parent_role_id` | `V4__create_roles.sql` | Conteo de hijos directos sin recorrerlos |
| `roles_pkey` | `V4__create_roles.sql` | Acceso por identificador |

Tampoco hay migración de permisos: `roles:read` lo crea `V2__seed_permissions.sql` (`RF-SP-010`) y ya lo usa `RF-SP-002` (§5).

`ix_roles_busqueda` y la función `f_unaccent` que crea `V7` (`RF-SP-002`) **no intervienen**: aquí no hay búsqueda por texto.

Un índice que sí hará falta, pero **no en este esquema**: el conteo de usuarios asignados exige que `USR` indexe `user_roles(role_id)`. Es responsabilidad suya y se anota en §8; declararlo desde aquí sería exactamente la intromisión que §5.3 prohíbe.

Los recordatorios de la plantilla no aplican: no se crea ninguna tabla, así que no hay clave primaria UUID v7, ni `created_at`/`updated_at`, ni columnas de actor que omitir, ni integridad declarativa que añadir.

## 3. Componentes afectados

Paquete raíz del módulo: `com.factech.nexus.modules.system`. Reglas de dependencia de `architecture.md` §5.2.

| Capa | Componente | Nuevo / Modificado | Responsabilidad |
|---|---|---|---|
| `domain` | — | — | Sin participación. `RN-SEG-004` se cumple por ausencia de recorrido, no por código (§11) |
| `application` | `GetRoleDetailService` | Nuevo | Caso de uso. **Sin** `@Transactional` (§7). Orquesta lectura, conteo externo y degradación |
| `application` | `RoleDetailReader` | Nuevo | Envoltura transaccional de solo lectura de las dos sentencias de `SP`. Existe por la razón transaccional de §7, no por gusto de añadir una capa |
| `application` | `RoleDetailQueryRepository` | Nuevo | Puerto de consulta: `findById(UUID)` devuelve `Optional<RoleDetail>`; `findDeclaredPermissions(UUID)` devuelve la lista de permisos |
| `application` | `RoleDetail` | Nuevo | Modelo de lectura del rol: campos escalares, padre embebido y `childRoleCount` |
| `application` | `PermissionItem` | Nuevo | Modelo de lectura de un permiso declarado: `id`, `code`, `name` |
| `application` | `RoleAssignmentCounter` | Nuevo | **Puerto hacia `USR`.** Único punto por el que `SP` conoce el número de usuarios de un rol (§4) |
| `application` | `AssignedUserCount` | Nuevo | Tipo sellado de retorno del puerto: `Known(long)` o `Unavailable(Reason)` |
| `infrastructure` | `JpaRoleDetailQueryRepository` | Nuevo | Adaptador. Construye las dos proyecciones con la API de criterios |
| `infrastructure` | `UnavailableRoleAssignmentCounter` | Nuevo | Adaptador nulo del puerto, registrado con `@ConditionalOnMissingBean`. Devuelve siempre `Unavailable(NOT_IMPLEMENTED)` |
| `infrastructure` | `RoleEntity`, `RolePermissionEntity` | Sin cambios | Solo como metamodelo para nombrar columnas; no se instancian |
| `infrastructure` | `RoleJpaMapper` | Sin cambios | No interviene: no se carga el agregado |
| `api` | `RoleController` | Modificado | Añade `GET /api/v1/roles/{id}`. Declara el permiso y delega |
| `api` | `RoleDetailResponse` | Nuevo | DTO de salida del detalle. Reutiliza `RoleSummaryResponse` y `PermissionResponse` |
| `api` | `AssignedUsersResponse` | Nuevo | Objeto anidado con `count` y `available` (§4) |
| `api` | `RoleSummaryResponse`, `PermissionResponse` | Sin cambios | Definidos en `RF-SP-001`. Se reutilizan tal cual |
| `shared/api` | `CanonicalUuidConverter` | Nuevo | `Converter<String, UUID>` que exige la forma canónica de 36 caracteres. Es lo que hace que un identificador malformado sea `400` y no `404` (§4) |
| `shared/error` | `GlobalExceptionHandler` | Modificado | Traduce `ResourceNotFoundException` a `404` y el fallo de conversión del identificador a `400` con `VAL-001` |

Cuatro decisiones de reparto que conviene justificar:

**El puerto de `USR` vive en `application`, no en `domain`.** `domain` solo puede depender de sí mismo y del JDK, y el número de usuarios de un rol no es parte del agregado `Role` ni entra en ninguna regla que `domain` verifique: es un dato de otro módulo que se agrega a una respuesta. Es el mismo criterio con el que `RF-SP-001` situó `AuthenticatedActor` y `RoleChangeAuditor` en `application`, y `RF-SP-002` situó allí `RoleQueryRepository`.

**El adaptador nulo vive en `SP`, no en ninguna parte de `USR`.** `USR` no existe, así que no hay dónde ponerlo; y cuando exista, su adaptador real desplaza al nulo sin tocar `SP`, porque este está condicionado a la ausencia de otro bean del mismo tipo. La alternativa —inyectar `Optional<RoleAssignmentCounter>` o un `ObjectProvider` en el servicio— obliga a que cada consumidor escriba la rama de ausencia, y ya hay dos consumidores previstos.

**`RoleDetailResponse` no es `RoleResponse`.** Se estudió ampliar el DTO de `RF-SP-001` con los dos campos nuevos. Se descarta: `RoleResponse` lo devuelve también el alta, y añadirle `assignedUsers` obligaría a que `POST /api/v1/roles` consultara a `USR` por un conteo que acaba de ser cero por construcción, importando esa dependencia a un camino de escritura que hoy no la tiene.

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
  "assignedUsers": { "count": 12, "available": true },
  "createdAt": "2026-08-20T14:32:11Z",
  "updatedAt": "2026-08-20T14:32:11Z"
}
```

Decisiones del contrato:

- **`childRoleCount` es un número, nunca una lista** (`spec.md` §14, pregunta 2). No existe `childRoles` ni una página embebida: el listado se obtiene con `GET /api/v1/roles?parentRoleId={id}`, que ya existe y ya está paginado. Es lo que hace verificable a `CA-SP-150`.
- **El conteo de hijos excluye los eliminados lógicamente** e incluye los inactivos. Un rol eliminado es inexistente en todo este requerimiento —es el criterio que la especificación fija para el propio rol consultado— y un rol inactivo sigue existiendo y sigue colgando de su padre. El número coincide así con el `totalElements` de `GET /api/v1/roles?parentRoleId={id}` con el `includeDeleted=false` que trae por defecto; con `includeDeleted=true` no coincidirá, y eso es correcto, no un defecto.
- **`permissions` va completa y sin paginar**, ordenada por `code` para que la respuesta sea estable entre llamadas. `architecture.md` §7.4 exige paginar «las colecciones», y aquí se aparta de forma consciente: los permisos declarados de un rol son decenas (`spec.md` §13), no constituyen un recurso navegable por sí mismos y paginarlos obligaría a un segundo endpoint para responder «qué puede hacer este rol», que es la única pregunta del requerimiento. El orden por `code` no es decorativo: sin `ORDER BY` explícito PostgreSQL no garantiza orden alguno, y una lista que cambia de orden entre dos llamadas hace inútil cualquier comparación entre roles.
- **`assignedUsers` es un objeto, no un número.** Es la decisión central de este plan y se desarrolla abajo.
- **`isSystem`, `createdAt` y `updatedAt` no están enumerados en `spec.md` §6.2.** Se incluyen porque `RoleResponse` de `RF-SP-001` ya los devuelve y porque el detalle es la pantalla desde la que se edita, se cambia de estado y se elimina un rol: sin `isSystem`, el cliente no puede saber qué acciones le están vedadas. **Exceden lo aprobado** y se anotan en §10 para confirmación.
- **No existe `deletedAt`.** Un rol eliminado devuelve `404`, de modo que el campo sería siempre nulo.
- **No existe `createdBy`** ni equivalente: el actor no vive en la tabla de negocio (Art. V.7). Quién creó o modificó el rol se responde con `RF-SP-011`.

### El número de usuarios asignados

**El puerto.** Se declara en `com.factech.nexus.modules.system.application`:

```java
public interface RoleAssignmentCounter {
    AssignedUserCount countUsersWithRole(UUID roleId);
}

public sealed interface AssignedUserCount {
    record Known(long value) implements AssignedUserCount {}
    record Unavailable(Reason reason) implements AssignedUserCount {}

    enum Reason { NOT_IMPLEMENTED, UNREACHABLE }
}
```

Cinco puntos del contrato, que `SP` fija aquí y `USR` implementa:

1. **Cuenta usuarios distintos, no asignaciones.** Si un usuario tuviera el rol por dos caminos, cuenta una vez.
2. **Cuenta usuarios en cualquier estado** —`ACTIVO`, `INACTIVO`, `BLOQUEADO`, `PENDIENTE`— y **excluye los eliminados lógicamente**. La semántica la fija `SP` porque de ella depende `RN-SEG-008`: un usuario bloqueado sigue teniendo el rol y su existencia debe impedir el borrado; un usuario eliminado no existe. Si esta decisión la tomara `USR` por su cuenta, `RF-SP-009` podría borrar un rol que alguien conserva.
3. **No devuelve identidades.** El puerto entrega un número. Así el detalle no filtra qué personas tienen el rol —`spec.md` §4.2 lo excluye— y no hace falta que el actor tenga permisos de `USR` para consultar un rol (§5).
4. **No lanza.** El implementador aplica su propio límite de tiempo y traduce cualquier fallo suyo a `Unavailable(UNREACHABLE)`. Aun así, `SP` envuelve la llamada en un `try/catch` de `RuntimeException`: el contrato dice lo que debe pasar, la guarda dice qué pasa cuando no se cumple.
5. **Un solo método, sin variante por lotes.** El único listado que podría querer conteos masivos es `RF-SP-002`, y `CA-SP-148` le prohíbe expresamente devolverlos. Añadir hoy un `countUsersWithRoles(Set<UUID>)` sería declarar una firma sin consumidor posible.

**Los dos consumidores.** El tipo sellado existe para que sean distintos sin duplicar el puerto. `RF-SP-003` degrada: sobre `Unavailable` devuelve `200` con el conteo vacío. `RF-SP-009` **no puede degradar**: `RN-SEG-008` exige constancia de que nadie tiene el rol, y `Unavailable` no es constancia de nada; ahí la respuesta correcta es rechazar la eliminación explicando que no se pudo verificar. El `switch` sobre el tipo sellado es exhaustivo, así que quien añada un tercer consumidor tiene que decidir su rama; con un `long` o un `Optional<Long>` bastaría un `orElse(0L)` para convertir «no se sabe» en «cero» sin que nada lo advierta, y en `RF-SP-009` eso es un rol borrado con usuarios dentro.

**Lo que este puerto no resuelve.** `RN-SEG-011` —que el actor no elimine un rol que él mismo tiene— también necesita datos de `USR`, pero es una pregunta sobre *un* usuario concreto, no un conteo. Su lugar natural es `AuthenticatedActor`, ya declarado en `RF-SP-001`, y la decisión pertenece a `RF-SP-009`. No se amplía `RoleAssignmentCounter` con métodos que este requerimiento no usa.

**Mientras `USR` no exista.** `UnavailableRoleAssignmentCounter` responde `Unavailable(NOT_IMPLEMENTED)` a toda llamada. El detalle se implementa, se prueba y se despliega completo; el único efecto observable es que `assignedUsers` viene vacío. El adaptador emite un `WARN` **al arrancar**, una sola vez, no por petición: la ausencia del módulo es un hecho de despliegue, no un incidente por consulta.

**Cómo se ve la degradación en la respuesta.** El campo nunca se omite y nunca vale cero por defecto:

| Situación | JSON |
|---|---|
| Doce usuarios tienen el rol | `"assignedUsers": { "count": 12, "available": true }` |
| Ningún usuario tiene el rol | `"assignedUsers": { "count": 0, "available": true }` |
| No se pudo saber | `"assignedUsers": { "count": null, "available": false }` |

`count: null` bastaría para un cliente estricto, pero muchos clientes convierten un nulo en cero al pintarlo; el booleano hace que confundir ambos casos exija ignorar un campo, no olvidarse de una comprobación. **Las dos razones de indisponibilidad producen el mismo JSON**: al consumidor no le sirve distinguir «el módulo no está construido» de «el módulo no responde», y publicar esa diferencia exportaría el calendario de implementación al contrato de la API. La distinción vive dentro, y solo decide si se emite evento de auditoría (§6).

**El estado sigue siendo `200`, no `206` ni `503`.** `206` significa respuesta parcial por rango de bytes y desviarlo rompe intermediarios; `503` contradice `spec.md` §13, que prefiere degradar antes que tumbar una consulta de roles por un módulo ajeno.

Queda una tensión con `development-guide.md` §15, que lista como antipatrón capturar una excepción, registrarla y continuar. Aquí no se continúa como si nada: el fallo se representa en el tipo de retorno, viaja hasta el JSON, queda en `audit_error_log` (§6) y tiene prueba propia (§11). Es lo contrario de un fallo silencioso.

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

**Cuántas consultas cuesta el detalle.** Dos sentencias contra PostgreSQL, más como máximo una llamada al puerto de `USR`:

```sql
-- 1: rol, padre y número de hijos
SELECT r.id, r.code, r.name, r.description, r.role_type, r.status, r.is_system,
       r.created_at, r.updated_at,
       p.id, p.code, p.name,
       (SELECT count(*) FROM roles c
         WHERE c.parent_role_id = r.id AND c.deleted_at IS NULL)
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
- **El orden de las tres obtenciones importa.** Primero el rol: si no existe, se devuelve `404` sin ejecutar la segunda sentencia y **sin llamar a `USR`**. No se pregunta a otro módulo por un rol que no existe.

## 5. Autorización

| Endpoint | Permiso requerido |
|---|---|
| `GET /api/v1/roles/{id}` | `roles:read` |

- El permiso **ya existe**: lo crea `V2__seed_permissions.sql` (`RF-SP-010`). No hace falta migración.
- Se declara sobre el método del controlador (`security.md` §6). Un endpoint sin declaración queda inaccesible, no público (Art. IV.1).
- **Es el mismo permiso que `RF-SP-002`.** Detalle y listado responden la misma pregunta con distinto grano; exigir un permiso propio obligaría a concederlos siempre juntos.
- **El conteo de usuarios no exige un permiso de `USR`.** Se consideró pedir además `users:read`: se descarta porque el dato es un agregado sin identidad —el puerto no devuelve personas— y porque haría que la forma de la respuesta dependiera de quién pregunta, obligando al cliente a tratar el campo ausente y el campo indisponible como casos distintos.
- **No hay filtrado por alcance de datos**, igual que en `RF-SP-002`: quien tiene el permiso consulta cualquier rol. Si alguna vez se resuelve **D-22** (`requirements/sp.md` §10.2), este endpoint hay que revisarlo, porque hoy no tiene punto donde insertar la restricción.
- La resolución del permiso puede usar la caché de `security.md` §4.5: aquí solo se decide acceso, no un techo de privilegios.
- El `403` lo produce la capa de seguridad antes de entrar al caso de uso, y es ella quien emite el evento de seguridad (§6). `CA-SP-022` se satisface ahí.

## 6. Auditoría

| Operación | Registro | Contenido relevante |
|---|---|---|
| Consulta exitosa | — | **No se audita** |
| Rechazo `400` por `VAL-001` | — | **No se audita**: es validación de formato (`architecture.md` §6.6.4) |
| Rechazo `404` | — | **No se audita.** Ver abajo |
| Degradación por `Unavailable(UNREACHABLE)` | `audit_error_log` | `resource = 'roles'`, `operation = 'GET /api/v1/roles/{id}'`, `error_code = 'INT-001'`, `error_type = 'INTEGRATION'`, `http_status = 200`, `severity = 'MEDIA'`, `message` saneado |
| Degradación por `Unavailable(NOT_IMPLEMENTED)` | — | **No se audita.** Ver abajo |
| Denegación `403` | `audit_security_log` | `event_type` de denegación de autorización, `severity = 'MEDIA'`, `outcome = 'FAILURE'`. Lo emite la capa de seguridad |
| Fallo no controlado `5xx` | `audit_error_log` | `error_type = 'UNHANDLED'`, `severity = 'ALTA'` |
| — | `audit_change_log` | No aplica: la consulta no altera el estado (`spec.md` §7) |
| — | `audit_deletion_log` | No aplica: no elimina nada |

Cuatro decisiones, porque la ausencia de auditoría es tan decisión como su presencia:

- **Una consulta exitosa no produce evento.** El catálogo de `security.md` §8.1 es cerrado y no incluye la lectura de roles. Auditar cada detalle añadiría una fila por pulsación de un administrador. La trazabilidad de quién consultó qué la aporta `request_log`. Es la misma conclusión de `RF-SP-002` §6.
- **El `404` tampoco se audita.** No hay regla de negocio incumplida ni cambio de estado: es un identificador que no encuentra fila, y en un endpoint de consulta eso es navegación, no incidente. Registrarlo como `BUSINESS_RULE` llenaría el registro de errores con enlaces caducados. Difiere de `RF-SP-001`, que sí audita sus rechazos, porque allí cada uno corresponde a una regla identificada.
- **La degradación sí se audita, y solo cuando `USR` existe y falla.** `error_type = 'INTEGRATION'` es exactamente el caso para el que ese valor está en el `CHECK` de `V3`. Que `USR` no esté construido no es un fallo de integración sino un hecho conocido del despliegue, y auditarlo produciría una fila por consulta durante meses sin informar de nada nuevo. **Detalle incómodo:** la fila queda con `http_status = 200`, porque eso es lo que el cliente recibió. La columna registra lo que se respondió, no la gravedad del fallo interno; la alternativa sería anotar un `503` que nadie envió.
- **`INT-001` no está en ningún catálogo aprobado.** No existe un catálogo de códigos de error de integración en la documentación vigente. Se propone `INT-001` para poder implementar y **debe confirmarse**; se anota en §10.

## 7. Transaccionalidad

| Elemento | Transacción |
|---|---|
| Las dos sentencias de lectura de `SP` | **Una sola**, `@Transactional(readOnly = true)` sobre `RoleDetailReader` |
| Llamada a `RoleAssignmentCounter` | **Fuera** de la transacción anterior. `GetRoleDetailService` no es transaccional |
| `audit_error_log` de la degradación o de un fallo | **Independiente**, `REQUIRES_NEW` (Art. V.14) |
| `audit_security_log` de la denegación `403` | **Independiente**, `REQUIRES_NEW`. La emite la capa de seguridad |
| `request_log` | Ninguna: posterior a la respuesta, *best effort* |

`readOnly = true` no es decorativo: evita que Hibernate registre entidades para comprobación de cambios y marca la transacción como de solo lectura en PostgreSQL, de modo que un defecto no pueda escribir desde un camino de consulta.

**Por qué el conteo de usuarios se pide fuera de la transacción de `SP`.** Es la razón de existir de `RoleDetailReader`, y sin ella el diseño se rompe de una forma que no aparece en las pruebas del camino feliz. Si la llamada al puerto ocurriera dentro de la transacción de lectura y el adaptador de `USR` lanzara una excepción de acceso a datos, la transacción quedaría marcada como *rollback-only*: el `try/catch` de `SP` capturaría la excepción y seguiría adelante, pero la transacción ya no podría confirmarse y la petición terminaría en `500` **a pesar de haber degradado correctamente**. Sacar la llamada fuera hace que la degradación sea de verdad independiente. La misma prevención sirve el día que `USR` sea un servicio remoto: mantener abierta una transacción de base de datos mientras se espera a otro módulo es la forma clásica de agotar el pool de conexiones cuando ese módulo se vuelve lento.

Como contrapartida menor: bajo `READ COMMITTED`, cada sentencia toma su propia instantánea, de modo que la lista de permisos puede corresponder a un instante distinto al del conteo de hijos, y el conteo de usuarios a un tercero. Elevar el aislamiento a `REPEATABLE READ` no arreglaría el tercero —`USR` no participa de la transacción— y encarecería toda consulta por un desfase que en un catálogo de roles es teórico. Se acepta, con el mismo razonamiento de `RF-SP-002` §7.

## 8. Impacto sobre otros módulos

| Módulo | Impacto |
|---|---|
| `USR` | **Debe implementar `RoleAssignmentCounter`** al construirse, con la semántica de §4: usuarios distintos, cualquier estado, sin los eliminados lógicamente, sin identidades, sin lanzar y con límite de tiempo propio. Debe además indexar `user_roles(role_id)`: es la columna por la que se contará, y sin índice el conteo recorre la tabla entera. La dependencia va de `USR` a `SP`, nunca al revés (`modules.md` §5.2) |
| `SP` (resto del módulo) | `RF-SP-009` reutiliza el mismo puerto para `RN-SEG-008` y **no debe degradar**: sin conteo no hay verificación (§4). `RF-SP-004` a `RF-SP-008` pueden devolver `RoleDetailResponse` tras modificar un rol; si lo hacen, heredan la dependencia de `USR` en un camino de escritura y deben decidir explícitamente qué hacen con la indisponibilidad, en lugar de dar por buena esta decisión |
| `shared/api` | `CanonicalUuidConverter` se registra de forma global: **cambia el comportamiento de todo endpoint con un UUID en la ruta**, presente y futuro. Un identificador no canónico pasa de `404` a `400` en todo el sistema. Es lo correcto y es uniforme, pero es un cambio de mayor alcance que este requerimiento |
| `shared/error` | Estrena la traducción de `ResourceNotFoundException` a `404` y del fallo de conversión del identificador a `VAL-001`. La jerarquía de `development-guide.md` §7.1 ya la contempla: a diferencia del `422` de `RF-SP-001`, no hay que enmendar ese documento |
| `shared/audit` | Estrena `error_type = 'INTEGRATION'`, previsto en el `CHECK` de `V3` pero sin uso hasta ahora |

## 9. Alternativas consideradas

| Alternativa | Por qué se descartó |
|---|---|
| Que `SP` consulte `user_roles` con una consulta nativa | Lo prohíbe `architecture.md` §5.3, y además invertiría el grafo de módulos: `SP` es su raíz (`requirements/sp.md` §3) y depender de `USR` cerraría un ciclo con la dependencia que `USR` ya tiene de `SP` |
| Denormalizar el conteo en una columna `roles.assigned_users_count` mantenida por eventos de `USR` | Duplica en una tabla de `SP` un dato cuya verdad vive en `USR` y que puede desincronizarse sin que nada lo detecte. Para `RN-SEG-008` sería peor que no tenerlo: un contador desfasado en cero autoriza borrar un rol con usuarios. Exigiría además un bus de eventos que hoy no existe |
| Que el puerto devuelva `long` | No hay ningún valor de `long` que signifique «no se pudo saber». Devolver `0` o `-1` obliga a un acuerdo tácito que el compilador no verifica y que `RF-SP-009` incumpliría con un `if` mal escrito |
| Que el puerto devuelva `Optional<Long>` | Es honesto sobre la ausencia, pero un `orElse(0L)` la convierte en cero en una línea y ninguna revisión lo señala. El tipo sellado obliga a un `switch` exhaustivo: quien no trate `Unavailable` no compila |
| Que el puerto devuelva `null` cuando no puede contar | Misma objeción, con un `NullPointerException` de premio |
| Que el detalle falle con `503` cuando `USR` no responde | Contradice `spec.md` §13: una consulta de roles no debe caer por un módulo ajeno. El código, el nombre, los permisos y la jerarquía —el grueso del valor de la pantalla— están disponibles y no hay razón para ocultarlos |
| Omitir el campo `assignedUsers` cuando no se puede obtener | Un campo ausente es indistinguible de un campo que el cliente no conoce, y obliga a tratar dos formas de respuesta. Se prefiere una sola forma con un valor explícito |
| Distinguir en el JSON «módulo no construido» de «módulo no responde» | El consumidor no puede actuar distinto en cada caso, y publicarlo ataría el contrato de la API al calendario de implementación de otro módulo |
| Responder `206 Partial Content` en el caso degradado | `206` está definido para respuestas por rango de bytes. Reutilizarlo para «faltó un campo» confunde a intermediarios y clientes genéricos |
| Inyectar el puerto como `Optional<RoleAssignmentCounter>` en lugar de registrar un adaptador nulo | Traslada la rama de ausencia a cada consumidor. Ya hay dos previstos, y el segundo debe resolverla de forma distinta: mejor un tipo que la exprese que un `Optional` que la esconda |
| Bloquear `RF-SP-003` hasta que `USR` exista | Invertiría el orden de implementación de `requirements/sp.md` §6.1 por un campo accesorio de una pantalla cuyo valor principal son los permisos |
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
| El contrato de `RoleAssignmentCounter` lo fija `SP` antes de que `USR` exista, y puede no encajar con su modelo real | Medio | La firma es mínima —un método, un `UUID`, un tipo de retorno propio— y la semántica que se le impone (§4) es la que `RN-SEG-008` necesita, no una comodidad de `SP`. Cualquier ajuste al construir `USR` afecta a un archivo de interfaz y a dos consumidores |
| `assignedUsers` viene vacío durante todo el tiempo que `USR` tarde en existir | Medio | Es la consecuencia aceptada de no bloquear este requerimiento. El cliente debe pintar «no disponible», no un cero. **Debe acordarse con el frontend antes de implementar**: si la pantalla no distingue ambos casos, la degradación se vuelve engañosa justo donde importa |
| `@ConditionalOnMissingBean` enmascara un fallo de configuración: `USR` existe, su adaptador no se registra —paquete fuera del escaneo, perfil equivocado— y el sistema degrada en silencio; `RF-SP-009` rechazaría entonces toda eliminación | Alto | El adaptador nulo emite `WARN` al arrancar indicando qué bean está supliendo. Además se declara la propiedad `nexus.usr.assignment-counter.required`, activa en producción, que hace **fallar el arranque** si no hay implementación real. Un fallo de despliegue es preferible a un dato falso en dos requerimientos |
| Con `USR` caído, cada consulta de detalle escribe una fila en `audit_error_log` | Medio | Aceptable al volumen de esta pantalla. Si se manifestara, la corrección es un cortacircuitos que emita el evento al abrirse y al cerrarse, no una fila por petición. **No se implementa ahora**: sería infraestructura sin un fallo observado que la justifique |
| `INT-001`, y el uso de `error_type = 'INTEGRATION'` con `http_status = 200`, no están respaldados por ningún documento aprobado | Bajo | Se proponen aquí y **deben confirmarse**. Si se define un catálogo de códigos de integración, este es el primer caso que debe recoger |
| `CanonicalUuidConverter` cambia de `404` a `400` el comportamiento de todo endpoint con UUID en la ruta | Bajo | Es el comportamiento correcto y no hay endpoints publicados que dependan del anterior. Se registra porque el alcance excede a este requerimiento |
| `isSystem`, `createdAt` y `updatedAt` exceden los campos que `spec.md` §6.2 enumera | Bajo | **No se decide unilateralmente**: se proponen con la justificación de §4 y deben confirmarse. Quitarlos después es un cambio de contrato |
| El conteo de hijos excluye los eliminados y no coincidirá con `RF-SP-002?includeDeleted=true` | Bajo | Documentado en §4 y probado en §11. La correspondencia declarada es con el listado por defecto |
| Un padre eliminado lógicamente haría aparecer el rol con `parentRole` nulo, indistinguible del rol raíz | Bajo | No debería ocurrir: `RF-SP-009` impide eliminar un rol con hijos (`RN-SEG-008`). Se acepta como estado imposible; si llegara a darse, es un defecto de `RF-SP-009`, no de esta consulta |
| `description` es `text` sin longitud declarada | Bajo | Heredado de `RF-SP-001` §10 y sin resolver. Aquí el impacto es menor que en el listado: una sola fila por respuesta |
| La subconsulta correlacionada del conteo de hijos puede resultar incómoda de expresar en la proyección con la API de criterios | Bajo | Si lo fuera, se separa en una tercera sentencia. Cambia el coste, no el contrato ni ninguna prueba de comportamiento |

## 11. Estrategia de prueba

Niveles: **Unitaria** (sin Spring ni base de datos), **Integración** (Testcontainers sobre PostgreSQL real) y **API** (extremo a extremo por HTTP, con autenticación). En las pruebas de API, `RoleAssignmentCounter` se sustituye por un doble que devuelve a voluntad `Known`, `Unavailable` o una excepción: es lo único que permite probar hoy un requerimiento que depende de un módulo inexistente.

| Criterio | Nivel | Qué verifica |
|---|---|---|
| `CA-SP-016` | Integración + API | Un rol con permisos declarados devuelve `200` con la lista completa, ordenada por `code`, sin paginar y sin envoltura de página; el número de elementos coincide con las filas de `role_permissions` de ese rol |
| `CA-SP-017` | Integración + API | La respuesta trae `parentRole` con `id`, `code` y `name`, y `childRoleCount` con el número de hijos directos; con nietos en la tabla, el conteo no los incluye |
| `CA-SP-018` | Integración + API | `FA-001`: un rol sin filas en `role_permissions` devuelve `200` con `permissions` como lista vacía, nunca `null` ni campo ausente |
| `CA-SP-019` | Integración + API | `FA-002`: el rol raíz devuelve `parentRole` nulo. Es la prueba que detecta un `JOIN` interno donde debe haber un `LEFT JOIN` |
| `CA-SP-020` | Integración + API | Un rol con `deleted_at` no nulo devuelve `404` con `EX-001`, con el mismo cuerpo que un identificador inexistente y sin dato alguno del rol |
| `CA-SP-021` | Integración | La traza de sentencias de la petición contiene **exactamente dos** consultas, ninguna recursiva y ninguna sobre los permisos del rol padre. Sobre un rol con abuelo cuyos permisos difieren, la respuesta trae solo los propios. `RN-SEG-004` no tiene prueba unitaria porque no hay código que la implemente: se verifica por lo que la consulta no hace |
| `CA-SP-022` | API | Un actor autenticado sin `roles:read` recibe `403`, no obtiene dato alguno del rol y queda el evento de denegación en `audit_security_log` |
| `CA-SP-149` | Unitaria + API | El servicio traduce `Known(3)` a `count = 3` con `available = true`; el endpoint lo devuelve con el doble del puerto configurado en `3` |
| `CA-SP-150` | Integración + API | El mismo rol con cero, con tres y con doscientos hijos devuelve el mismo conjunto de campos y el mismo número de sentencias; el cuerpo solo difiere en el valor de `childRoleCount` |

Casos límite de `spec.md` §13 y decisiones de este plan que exigen prueba propia (Art. VII.3):

| Caso | Nivel | Qué verifica |
|---|---|---|
| `USR` no implementado | API | Con el adaptador nulo activo, la respuesta es `200` con `{ "count": null, "available": false }` y **no** hay fila en `audit_error_log` |
| `USR` presente y fallando | API + Integración | Con el doble lanzando una excepción, la respuesta sigue siendo `200`, el campo viene vacío, el resto del detalle está completo y queda una fila en `audit_error_log` con `error_type = 'INTEGRATION'` |
| La excepción de `USR` no envenena la transacción | Integración | Con el doble lanzando una excepción de acceso a datos, la petición devuelve `200` y no `500`. Es la prueba que detecta que alguien movió la llamada dentro de la transacción de lectura (§7) |
| Cero usuarios frente a indisponible | API | `Known(0)` produce `{ "count": 0, "available": true }` y `Unavailable` produce `{ "count": null, "available": false }`: los dos cuerpos son distintos |
| Identificador no canónico | API | `1-1-1-1-1` devuelve `400` con `VAL-001` y campo `id`, **no** `404`. Sin esta prueba, la conversión permisiva del JDK pasa inadvertida |
| Identificador que no es UUID | API | `abc` devuelve `400` con `VAL-001`, no un `404` de manejador ausente |
| Identificador bien formado e inexistente | API | `404` con `EX-001`, y **ninguna llamada al puerto de `USR`**: no se pregunta por un rol que no existe |
| Rol con muchos permisos | Integración | Doscientos permisos declarados se devuelven completos en una sola sentencia, sin paginar y en orden estable entre dos llamadas |
| Hijos eliminados lógicamente | Integración | Con dos hijos vigentes y uno eliminado, `childRoleCount` vale `2`, y coincide con el `totalElements` de `GET /api/v1/roles?parentRoleId={id}` |
| Hijos inactivos | Integración | Un hijo `INACTIVO` sí cuenta: sigue existiendo y sigue colgando del padre |
| Número de sentencias por petición | Integración | Dos, con independencia del número de permisos y de hijos. Es la única forma de que el `N+1` no vuelva en una refactorización posterior |

A las reglas de ArchUnit introducidas en `RF-SP-001` —`domain` no importa Spring ni JPA, `api` no accede a `infrastructure`— se añade una: **ninguna clase de `modules.system` importa nada de `modules.users`**. Sin ella, la prohibición de `architecture.md` §5.3 depende de que alguien la recuerde en cada revisión, y el atajo de consultar `user_roles` directamente es el que una prisa acabaría tomando.
