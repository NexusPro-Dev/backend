# PLAN — `RF-SP-063` Registrar equipo

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-063` |
| Especificación | [`spec.md`](spec.md), aprobada el 22-09-2026 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 22-09-2026 |

---

## 1. Enfoque

**Un alta corriente que paga el estreno del submódulo.** El caso de uso es el de `RF-AC-001` —validar, comprobar el nombre contra los vivos, insertar, auditar y releer el detalle—, y lo que este plan tiene de particular son las **dos migraciones**: `V33` crea `teams` y `team_members` con todas sus restricciones, y `V34` siembra los ocho permisos `teams:`. Ningún requerimiento posterior del submódulo toca el esquema: `RF-SP-069` y `RF-SP-070` escriben en una tabla que ya existe, con los índices que ya existen.

**Paquete propio, `modules/system/teams`**, hermano de `roles`, `users` y `brokers`. No entra en `users` aunque `team_members` apunte a `users`: la tabla que manda es `teams`, el recurso de la API es `/teams` y el permiso es `teams:`. La frontera se cruza en una sola dirección y por un puerto publicado, como exige [`architecture.md` §5.3](../../../architecture.md), y el cruce lo paga `RF-SP-069` —que necesita saber si alguien porta el rol vendedor de mayor rango— no esta alta, que no consulta nada de `users`.

**`team_members` nace con `V33` aunque nadie la escriba hasta `RF-SP-069`.** Es deliberado y sigue el precedente de `course_categories.cover_image_id` (`V18`): crear el esquema del submódulo entero de una vez evita que el detalle de `RF-SP-065` cambie de forma a mitad del bloque, y **`memberCount` puede contarse desde el primer día** —da cero— en lugar de ser un campo que aparece después.

## 2. Cambios de esquema

**Dos migraciones. `V33__sp_equipos.sql`** crea las dos tablas tal como las declara [`requirements/sp.md` §10.20, §10.21 y §10.8](../../../requirements/sp.md):

| Tabla | Cambio | Detalle |
|---|---|---|
| `teams` | Crea | `id uuid PK`, `name varchar(100) NOT NULL`, `description text NULL`, `status varchar(20) NOT NULL DEFAULT 'ACTIVO'`, `created_at`, `updated_at`, `deleted_at NULL`. `ck_teams_name_not_blank`, `ck_teams_description_length` (≤ 500), `ck_teams_status` en (`ACTIVO`, `INACTIVO`) |
| `teams` | Índices | `uq_teams_name` **único, funcional y parcial**: `(f_unaccent(lower(name))) WHERE deleted_at IS NULL`; `ix_teams_busqueda` **gin de trigramas** sobre `f_unaccent(lower(name))` |
| `team_members` | Crea | `id uuid PK`, `team_id uuid NOT NULL`, `user_id uuid NOT NULL`, `started_at timestamptz NOT NULL DEFAULT now()`, `ended_at timestamptz NULL`, `created_at`, `updated_at`. `fk_team_members_team` → `teams(id)` sin `ON DELETE`, `fk_team_members_user` → `users(id)` `ON DELETE RESTRICT`, `ck_team_members_periodo` |
| `team_members` | Índices | `uq_team_members_vigente` **único parcial**: `(user_id) WHERE ended_at IS NULL`; `ix_team_members_team_vigente` **parcial**: `(team_id) WHERE ended_at IS NULL` |

- **`uq_teams_name` es funcional porque el nombre es lo único que identifica** —el criterio de `uq_brokers_name`— y **parcial porque la baja es lógica y libera el nombre** —el criterio de `uq_roles_name`—. Las dos cosas a la vez, y es la primera restricción del sistema que las junta; por parcial no admite `DEFERRABLE`, de modo que muerde en el `INSERT` y el repositorio la traduce al mismo `409` de `EX-001`, como en `uq_course_categories_name`.
- **`ix_teams_busqueda` lleva la expresión del predicado** —`f_unaccent(lower(name))`, no otra—, que es la lección que `ix_users_busqueda` dejó escrita: si divergen, el índice existe, el planificador no lo usa nunca y el defecto sale como lentitud que nadie relaciona con su migración. Lo usa `RF-SP-064`; se crea aquí porque aquí nace la tabla.
- **`uq_team_members_vigente` es `RN-SP-052` declarada en el motor**, con la construcción exacta de `uq_user_supervisors_vigente`. Se prueba ya (`CA-SP-737`) aunque nadie inserte hasta `RF-SP-069`: una restricción que nadie ejercita es una restricción que nadie sabe si funciona.
- `COMMENT ON TABLE` y sobre las columnas que lo merecen —`status`, `ended_at`—, como `V18`, diciendo **qué significa una fila** y que la tabla no concede alcance.

**`V34__sp_semilla_permisos_equipos.sql`** siembra el catálogo, con la forma de `V19` y `V22` —varios permisos de un recurso nuevo— y no la de `V28`, que repartía hijos de un padre:

- Ocho `INSERT` en `permissions` con identificador literal, continuando la serie de `SP` (`…9c4f-5e7ad000002c` a `…5e7ad0000033`) y la secuencia que `V32` dejó en `700c`: **`01a0c143-2c00-700d-…`** a **`01a0c143-2c00-7014-…`**, para `teams:list`, `teams:read`, `teams:create`, `teams:update`, `teams:change-status`, `teams:delete`, `teams:assign-members` y `teams:remove-members`.
- `role_permissions` para **`SUPERADMIN` y `ADMIN`, explícitas**: `V8` asocia por exclusión solo hasta su fecha, y todo lo sembrado después va explícito.
- **A ningún otro rol.** Ni a `MANAGER`: administrar cómo se organiza la cúspide es tarea de administración, y ningún manager organiza su propio equipo (spec §14.3).
- **Ningún reparto de padre.** Estas ocho operaciones no existían y no hay código que dividir; es la diferencia con `V28`, y por eso no hay `INSERT … SELECT` por pareja rol–padre.
- Guardas, como todas las de catálogo: el conteo en **133**, `SUPERADMIN` en **133**, `ADMIN` en **127** —la reserva de seis de `MV` sigue sin llegarle— y la comprobación de contención de `RN-SEG-003`. Sin auditoría, como `V8`, `V22`, `V28` a `V32`.

## 3. Componentes afectados

| Capa | Componente | Nuevo / Modificado | Responsabilidad |
|---|---|---|---|
| `domain/models` | `Team` | Nuevo | El agregado: `create(id, name, description, ahora)` normaliza —recorta el nombre, recorta la descripción y devuelve nula la de solo espacios— y nace `ACTIVO`. Sin Spring y sin base de datos (Art. VI.3) |
| `domain/models` | `TeamStatus` | Nuevo | `ACTIVO`, `INACTIVO`. Dominio cerrado, el del `CHECK` |
| `domain/models` | `TeamMember` | Nuevo | La pertenencia: `open(id, teamId, userId, ahora)` y `close(ahora)`. **Nace aquí y la usa `RF-SP-069`**; se declara con la tabla para que el agregado no cambie de forma a mitad del bloque |
| `domain/repository` | `TeamRepository`, `JpaTeamRepository` | Nuevo | `save`, `existsAliveName(name)` —sin acentos y sin caja, con la misma expresión del índice—, `findAlive(id)`. La violación de `uq_teams_name` se traduce **por nombre de restricción** y no por el texto del driver, como exige la lección de `ck_currencies_default_active` |
| `domain/service` | `RegisterTeamService` | Nuevo | El caso de uso: validación de negocio, `existsAliveName`, `save`, `AuditWriter`, relectura del detalle. `@Transactional` |
| `domain/service` | `TeamDetailReader` | Nuevo | Lee la ficha y sus miembros vigentes. **Lo estrena esta alta** devolviendo la forma vacía, y lo reutilizan `RF-SP-065`, `RF-SP-066` y `RF-SP-067` |
| `application` | `RegisterTeamRequest` | Nuevo | `name`, `description`; `@JsonIgnoreProperties(ignoreUnknown = false)` como el resto, que es lo que hace observable `VAL-003` |
| `application` | `TeamDetailResponse`, `TeamMemberItem` | Nuevo | La forma del detalle, compartida con `RF-SP-065` |
| `interfaces` | `TeamController` | Nuevo | `POST /api/v1/teams` con `teams:create`. El controlador único del recurso, como `UserController` |
| `db/migration` | `V33`, `V34` | Nuevo | Esquema y catálogo |
| Pruebas | `TeamsIT`, `TeamConcurrencyIT`, `TeamsPermissionsSeedIT` | Nuevo | §11 |
| Pruebas | `PermissionsSeedIT`, `PermissionIT`, `JpaPermissionQueryRepositoryIT`, `ListPermissionsServiceIT`, `EndpointPermissionsIT`, `OpenApiContractIT` | Modificado | Los recuentos y la ruta nueva |

## 4. Contrato de API

| Método | Ruta | Descripción |
|---|---|---|
| `POST` | `/api/v1/teams` | Registra un equipo vacío y activo |

**Petición**

```json
{
  "name": "Equipo Norte",
  "description": "Managers de la región norte"
}
```

**Respuesta `201`**

```json
{
  "id": "01a0c1f0-7a00-7001-9c4f-5e7ad6000001",
  "name": "Equipo Norte",
  "description": "Managers de la región norte",
  "status": "ACTIVO",
  "memberCount": 0,
  "members": [],
  "createdAt": "2026-09-22T14:05:11Z",
  "updatedAt": "2026-09-22T14:05:11Z"
}
```

**Errores**

| Código | Cuándo | `error_code` |
|---|---|---|
| `400` | Nombre ausente o de más de 100; descripción de más de 500 | `VAL-001`, `VAL-002` |
| `400` | Campo desconocido en el cuerpo | `VAL-003` |
| `403` | Sin `teams:create` | — |
| `409` | El nombre ya lo usa un equipo no eliminado | `EX-001` |

El formato de error es el de [`architecture.md` §7.3](../../../architecture.md). **`deletedAt` no se publica en el alta**: es `NON_NULL` en el detalle y un equipo recién creado no lo tiene.

## 5. Autorización

| Endpoint | Permiso |
|---|---|
| `POST /api/v1/teams` | `teams:create` |

`@PreAuthorize("hasAuthority('teams:create')")` en el controlador, un permiso y uno solo (`RN-SEG-014`). **Nada de `hasAnyAuthority` con `teams:update` «porque quien edita puede crear»**: es la forma silenciosa de romper la regla, y `CA-SP-739` la cierra probando el `403` con los **otros siete** `teams:` puestos. La ruta entra en `PERMISO_DE_CADA_OPERACION` de `EndpointPermissionsIT`, que falla si falta.

## 6. Auditoría

| Operación | Registro | Contenido |
|---|---|---|
| Alta del equipo | `audit_change_log` | Acción `CREATE`, entidad `teams`, el identificador nuevo y el estado resultante |
| Nombre repetido (`EX-001`) | — | **Ninguno.** Un `409` de negocio no es un evento de error ni de seguridad, como en `RF-AC-001` |

**Ningún evento de seguridad**, y conviene decir por qué se mira: crear un rol tampoco lo emite, y un equipo concede todavía menos —nada—. Si algún día pertenecer a un equipo decidiera qué datos se ven (D-22), esta decisión se revisa.

## 7. Transaccionalidad

| Elemento | Transacción |
|---|---|
| `INSERT` en `teams` y su fila `CREATE` de auditoría | **La misma** (Art. V.14) |
| Relectura del detalle para la respuesta | La misma, dentro de `@Transactional` |

## 8. Impacto sobre otros módulos

| Módulo | Impacto |
|---|---|
| `PM`, `CM`, `MV`, `AC` | **Ninguno.** Nadie lee `teams` |
| `SP` · usuarios | Ninguno **en esta alta**. `RF-SP-069` consultará el rol de una persona y `RF-SP-070` traerá la enmienda de `RN-SP-055` a `RF-SP-029` y `RF-SP-031` |
| `CM` (futuro) | Es quien consumirá el historial de `team_members` al liquidar. No existe todavía y no se le publica puerto alguno por adelantado |

## 9. Alternativas consideradas

| Alternativa | Por qué se descarta |
|---|---|
| **Crear `team_members` en la migración de `RF-SP-069`**, cuando haga falta | El detalle de `RF-SP-065` publicaría `memberCount` contra una tabla inexistente, o cambiaría de forma a mitad del bloque. El precedente contrario ya está en `V18`, que declaró `cover_image_id` sin la tabla de imágenes |
| **Meter los equipos dentro del paquete `users`** | La tabla que manda es `teams` y el recurso es `/teams`. Un submódulo se reconoce por su agregado, no por a quién apunta su tabla puente; con ese criterio `user_supervisors` habría metido media red en `users` |
| **Un `code` único, como roles y membresías** | Nada lo referenciaría (spec §14.1). Una columna única que nadie lee es coste de mantenimiento y una segunda forma de decir lo mismo que el nombre |
| **`uq_teams_name` total en vez de parcial** | Bloquearía para siempre el nombre de un equipo eliminado, que es el error que `uq_roles_name` documenta desde el primer día |
| **`uq_teams_name` literal en vez de funcional** | «Equipo Norte» y «equipo norte» convivirían. Es literal en `roles` porque allí el código identifica y el nombre acompaña; aquí el nombre es lo único que hay |
| **Sembrar los ocho permisos en la misma migración que las tablas** | Se puede, y se separa a propósito: el esquema y el catálogo fallan por motivos distintos y se revierten a distinto coste. Es la forma de `V18`/`V19` y `V21`/`V22` |
| **Dar `teams:list` y `teams:read` también a `MANAGER`** | Nadie lo ha pedido, y conceder lecturas «porque parecen inofensivas» es cómo se llega a un catálogo que nadie sabe justificar. El día que un manager deba ver su equipo, la vía es `RF-SP-005` o un requerimiento de alcance propio con su permiso `own` (`RN-SEG-015`) |
| **Devolver `204` con la cabecera `Location`** en vez del cuerpo completo | Toda alta del sistema devuelve la forma del detalle para que el frontend no encadene dos peticiones |

## 10. Riesgos

| Riesgo | Impacto | Mitigación |
|---|---|---|
| Que `V33` o `V34` choquen con otra sesión sobre el mismo árbol | Alto — Flyway falla al arrancar | Números acordados con `backend-62` el 21-09-2026: `V32` es suyo, `V33` y `V34` son de equipos. Queda en la memoria del proyecto y en el PR |
| Que el recuento de permisos quede desfasado en las cuatro suites del catálogo | Medio — la suite entera en rojo por un número | `T-08` las toca **en el mismo commit** que `V34`, y las guardas de la migración fallan primero y con un mensaje que dice el número |
| Que `f_unaccent` no exista en el esquema de prueba | Bajo | La crea `V1__funciones_compartidas.sql`, que ya sostiene `uq_countries_name` y `ix_users_busqueda` |
| Que alguien use `team_members` como alcance de datos | Medio — repartiría D-22 | El `COMMENT ON TABLE` lo dice, `requirements/sp.md` §10.21 lo advierte y `security.md` §6 lo repite |

## 11. Estrategia de prueba

| Criterio | Nivel | Qué verifica |
|---|---|---|
| `CA-SP-731`, `CA-SP-733`, `CA-SP-734` | API (`TeamsIT`) | El `201` con la forma del detalle vacía; el `400` con las dos validaciones juntas; el cuerpo con campos desconocidos |
| `CA-SP-732` | API (`TeamsIT`) | El `409` por mayúsculas y por acentos, y el alta que **sí** admite el nombre de un equipo eliminado |
| `CA-SP-735` | Integración (`TeamsIT`) | La fila `CREATE` en `audit_change_log` con el actor, y `audit_security_log` sin filas nuevas |
| `CA-SP-736` | Integración (`TeamConcurrencyIT`) | Dos altas simultáneas: una fila, un `409`, ningún `500`. La forma de `CourseCategoryConcurrencyIT` |
| `CA-SP-737` | Integración (`TeamsSchemaIT`) | Las restricciones de las dos tablas, ejercitadas con `INSERT` directos —incluida la segunda pertenencia vigente de la misma persona, que debe reventar |
| `CA-SP-738` | Integración (`TeamsPermissionsSeedIT` y las cuatro del catálogo) | Los ocho literales, los dos roles, 133 / 133 / 127 |
| `CA-SP-739` | API (`TeamsIT`) + `EndpointPermissionsIT` | El `403` con los otros siete permisos puestos, y la ruta en `PERMISO_DE_CADA_OPERACION` |

**Unitarias sin Spring** (`TeamTest`): la normalización del nombre y de la descripción y el estado inicial, que es lo único que el agregado decide por su cuenta.

**Lo que no se prueba aquí:** asignar y retirar miembros (`RF-SP-069`, `RF-SP-070`), y el listado y el detalle con miembros dentro (`RF-SP-064`, `RF-SP-065`) — aquí el detalle solo se ejercita vacío.
