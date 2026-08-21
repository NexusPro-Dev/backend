# PLAN — `RF-SP-010` Consultar catálogo de permisos

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-010` |
| Especificación | [`spec.md`](spec.md) |
| `spec.md` aprobada el | 21-08-2026 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable técnico |
| Fecha de aprobación | 21-08-2026 |

!!! info "Qué va en este documento"

    **Cómo se construye.** Las decisiones técnicas que la especificación deliberadamente no toma.

    **Prueba de pertenencia:** si al negocio no le importa ni lo entendería, va aquí.

El comportamiento —flujos, filtros y criterios de aceptación— es el de [`spec.md`](spec.md) y no se repite aquí. Este documento decide el esquema del catálogo, qué permisos se siembran y con qué identificadores, y cómo se consulta una colección que deliberadamente no se pagina.

---

## 1. Enfoque

La consulta es la más simple del módulo: una sentencia de lectura sobre una proyección, sin paginar, sin `JOIN` y sin reglas de negocio. **El peso de este requerimiento no está en el endpoint, está en las migraciones.** `RF-SP-010` es el primero del orden de implementación aprobado (`requirements/sp.md` §6.1) y crea tres cosas de las que depende todo lo demás:

1. `V1__create_shared_functions.sql` — las extensiones `unaccent` y `pg_trgm` y la función `f_unaccent`, que sostienen toda búsqueda insensible a acentos del sistema.
2. `V2__create_permissions.sql` — la tabla `permissions`, referenciada por `role_permissions` (`RF-SP-001`) y leída por `RF-SP-003` y `RF-SP-015`.
3. `V3__seed_permissions.sql` — el catálogo, sin el cual ningún endpoint del sistema es accesible: un permiso que no existe en la tabla no puede concederse a ningún rol, y un endpoint cuyo permiso nadie tiene queda cerrado a todo el mundo (Art. IV.1).

De ahí que el catálogo sea **datos y no código** (`RN-SP-004`): añadir un permiso es una migración, no un despliegue de lógica nueva. Este plan se limita a hacer verificable esa afirmación —no publicando ninguna operación de escritura— y a dejar el catálogo poblado y consultable.

`domain` no participa. `spec.md` §5 declara una sola regla, `RN-SP-004`, y es negativa: se cumple porque no existe endpoint de escritura, no porque haya código que la verifique. Eso condiciona cómo se prueba (§11): se prueba por lo que la API **no** expone, no ejercitando un método.

## 2. Cambios de esquema

Tres migraciones, y las tres son las primeras del sistema.

### `V1__create_shared_functions.sql`

| Objeto | Cambio | Detalle |
|---|---|---|
| — | Extensiones | `CREATE EXTENSION IF NOT EXISTS unaccent` y `CREATE EXTENSION IF NOT EXISTS pg_trgm` |
| — | Función | `f_unaccent(text)`, envoltorio `IMMUTABLE` de `unaccent` |

```sql
CREATE EXTENSION IF NOT EXISTS unaccent;
CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE FUNCTION f_unaccent(text) RETURNS text
    LANGUAGE sql IMMUTABLE STRICT PARALLEL SAFE
    RETURN public.unaccent('public.unaccent'::regdictionary, $1);
```

**Por qué esto vive aquí y no en el requerimiento que lo estrenó sobre papel.** El plan de `RF-SP-002` creaba las extensiones y la función junto con `ix_roles_busqueda`. Al redactar este documento apareció el problema: `RF-SP-010` se implementa **antes** que `RF-SP-002`, y su búsqueda sobre la descripción de los permisos también ignora acentos (`spec.md` §6.1), de modo que necesita `f_unaccent` desde el primer día. Con la función en la última migración de `RF-SP-002`, el catálogo de permisos habría quedado con la búsqueda rota entre un requerimiento y otro: no un fallo de despliegue visible al arrancar, sino un `42883` en tiempo de ejecución la primera vez que alguien escribiera algo en el buscador. Se movió aquí el 21-08-2026, y la numeración de las migraciones se corrió en consecuencia (§8).

**Por qué `unaccent` no se puede indexar directamente.** La función `unaccent(text)` que instala la extensión está declarada `STABLE`, no `IMMUTABLE`, porque resuelve el diccionario a través del `search_path` de la sesión. PostgreSQL rechaza cualquier índice de expresión que invoque una función no inmutable, de modo que `CREATE INDEX … ON roles (unaccent(lower(name)))` falla. La forma admitida es la variante de dos argumentos, que recibe el diccionario explícito y es determinista; envolverla y declararla `IMMUTABLE` es lo que la vuelve indexable. El diccionario se escribe cualificado (`'public.unaccent'::regdictionary`) para que la función no dependa del `search_path` de quien la llame.

**Esa declaración `IMMUTABLE` es una promesa que la base de datos no verifica.** Si alguien redefine el diccionario `unaccent`, los índices que la usan conservan los valores calculados con el diccionario anterior y devuelven resultados incorrectos sin error. Se acepta, se anota en §10, y la consecuencia operativa es que **tocar el diccionario obliga a `REINDEX`** de todo lo que dependa de ella.

**Esta migración no crea ningún índice.** El de `roles` pertenece a `RF-SP-002` (`V8`) y el de `countries` a `RF-SP-021`. Aquí solo se deja disponible la herramienta; cada requerimiento decide si la indexa.

### `V2__create_permissions.sql`

Campos tomados de `requirements/sp.md` §10.1.

| Tabla | Cambio | Detalle |
|---|---|---|
| `permissions` | Crea | `id uuid PRIMARY KEY`, `code varchar(100) NOT NULL`, `resource varchar(50) NOT NULL`, `action varchar(50) NOT NULL`, `name varchar(100) NOT NULL`, `description text NULL`, `created_at timestamptz NOT NULL DEFAULT now()`, `updated_at timestamptz NOT NULL DEFAULT now()` |

Restricciones:

| Nombre | Definición | Por qué |
|---|---|---|
| `uq_permissions_code` | `UNIQUE (code)` | Exigido por `requirements/sp.md` §10.7. Restricción **total**, no parcial: el catálogo no tiene borrado lógico, así que no hay estado en el que un código deba poder repetirse |
| `ck_permissions_code_format` | `CHECK (code ~ '^[a-z][a-z0-9-]*:[a-z][a-z0-9-]*$')` | Formato `<recurso>:<acción>` en minúsculas (`security.md` §4.4). Admite el guion medio porque el catálogo aprobado lo usa: `audit:read-changes`, `users:reset-password` |
| `ck_permissions_code_matches` | `CHECK (code = resource \|\| ':' \|\| action)` | `code` es la concatenación de las otras dos columnas (`requirements/sp.md` §10.1). Sin esta restricción, una migración futura puede dejar `code = 'roles:read'` con `resource = 'role'` y el filtro por recurso devolvería un catálogo incoherente **sin que nada falle**. Es la restricción más valiosa de la tabla y no cuesta nada |
| `ck_permissions_description_length` | `CHECK (description IS NULL OR length(description) <= 500)` | Mismo límite y mismo motivo que en `roles` (`requirements/sp.md` §10.2): la columna es `text` y el catálogo se devuelve entero, sin paginar, de modo que sin cota el tamaño de la respuesta es impredecible |

**No se declara un `CHECK` sobre el dominio de `action`.** Sería tentador cerrarlo a `read`, `create`, `update` y `delete`, pero `security.md` §4.4 admite de forma explícita «acciones específicas del dominio cuando la especificación lo justifique», y el propio catálogo aprobado ya las tiene. Un dominio cerrado obligaría a alterar la restricción cada vez que un módulo estrena una acción, que es exactamente la fricción que `RN-SP-004` quiere evitar. Lo mismo vale para `resource`: cerrarlo convertiría cada módulo nuevo en una migración de esquema.

**No hay `deleted_at`, y la ausencia es deliberada.** `requirements/sp.md` §10.1 no lo declara. Un permiso no se elimina: si dejara de usarse, retirarlo exigiría además retirar sus filas de `role_permissions`, y la clave foránea con `ON DELETE RESTRICT` de `RF-SP-001` está puesta precisamente para que quien lo intente se encuentre con las asociaciones vigentes. `created_at` y `updated_at` sí están, por Art. V.7, aunque en la práctica `updated_at` solo cambie si una migración corrige un nombre o una descripción.

**No se crea índice de búsqueda sobre `permissions`.** El catálogo son decenas de filas y se devuelve entero: un recorrido secuencial sobre veinte filas es más rápido que consultar cualquier índice. Añadir un GIN de trigramas aquí sería mantener una estructura para acelerar lo que ya es inmediato. Si el catálogo llegara a crecer en un orden de magnitud, el índice es una migración de una línea y la consulta no cambia (§10).

### `V3__seed_permissions.sql`

Puebla los **veintitrés permisos del módulo**, tomados de la tabla de API de `requirements/sp.md` §9:

| Recurso | Acciones | De dónde sale |
|---|---|---|
| `roles` | `read`, `create`, `update`, `delete` | `RF-SP-001` a `RF-SP-009` |
| `permissions` | `read` | `RF-SP-010` y `RF-SP-015` |
| `audit` | `read-changes`, `read-deletions`, `read-errors`, `read-security` | `RF-SP-011` a `RF-SP-014` |
| `memberships` | `read`, `create` | `RF-SP-016` a `RF-SP-018` |
| `countries` | `read`, `create`, `update` | `RF-SP-020`, `RF-SP-021`, `RF-SP-022` |
| `currencies` | `read`, `update` | `RF-SP-019`, `RF-SP-023` |
| `users` | `read`, `create`, `update`, `delete`, `assign-roles`, `assign-membership`, `reset-password` | `RF-SP-024` a `RF-SP-038` |

Cada fila lleva su `name` y su `description` legibles, que es lo que hace útil el catálogo en una pantalla de composición de roles (`security.md` §4.4).

Cuatro decisiones sobre esta migración:

- **El catálogo completo se siembra aquí, incluidos los siete permisos de `users:`.** El borrador de este plan los dejaba fuera, porque pertenecían a `USR` y ese módulo los sembraría en su propia migración. Al retirarse `USR` y absorber `SP` los usuarios (`modules.md` v0.9.0), no queda otro módulo que pudiera hacerlo: la tabla y su contenido pertenecen al mismo sitio. `V3` siembra por tanto veintitrés permisos, y `V7__seed_system_roles.sql` los asocia a `SUPERADMIN` y a `ADMIN` sin que haga falta ninguna migración de datos posterior.
- **Se siembran también los permisos de requerimientos aún sin especificación**, `countries:update` y `currencies:update` (`RF-SP-022` y `RF-SP-023`). El coste de sembrarlos hoy es cero —un permiso que ningún endpoint declara no concede nada— y el de no hacerlo es una migración de datos por cada requerimiento que llegue. Lo que **no** se hace es inventar permisos que ningún documento aprobado declare.
- **Identificadores UUID v7 literales**, escritos en el propio SQL y generados una sola vez al redactar la migración. Ni `gen_random_uuid()` ni ninguna generación en base de datos: el Art. V.11 lo prohíbe, y además el identificador de cada permiso debe ser el mismo en todos los entornos para que las pruebas de `RF-SP-001` y `RF-SP-005` puedan referenciarlos por constante y para que `V7__seed_system_roles.sql` pueda asociarlos. Es el mismo criterio con el que `RF-SP-001` siembra sus roles de sistema.
- **La siembra del catálogo no emite eventos de auditoría**, y aquí está la diferencia con `V7__seed_system_roles.sql`, que sí los emite. No es solo que `audit_change_log` todavía no exista en `V3` —se crea en `V4`—: es que **no debería existir un evento de cambio para una entidad que nunca cambia por la API**. Un rol de sistema sí se consulta en `RF-SP-011` preguntando quién lo creó, y su línea de tiempo quedaría coja sin la fila del poblado. Un permiso no tiene línea de tiempo: `RN-SP-004` lo hace inmutable por API, de modo que su único historial posible es el de las migraciones, y ese lo lleva `flyway_schema_history`. Auditar la siembra produciría veintitrés filas que nunca tendrían una vigesimocuarta.

## 3. Componentes afectados

Paquete raíz del módulo: `com.factech.nexus.modules.system`. Reglas de dependencia de `architecture.md` §5.2.

| Capa | Componente | Nuevo / Modificado | Responsabilidad |
|---|---|---|---|
| `domain` | — | — | Sin participación: `RN-SP-004` se cumple por ausencia de endpoint, no por código (§11) |
| `application` | `ListPermissionsService` | Nuevo | Caso de uso. `@Transactional(readOnly = true)`. Traduce la consulta al puerto y devuelve la colección |
| `application` | `ListPermissionsQuery` | Nuevo | Criterios ya validados y normalizados: recurso, acción y término de búsqueda. Sin tipos de HTTP |
| `application` | `PermissionItem` | Sin cambios | Modelo de lectura definido en `RF-SP-003`. Se **amplía** con `resource`, `action` y `description`, que el detalle del rol no usaba (§4) |
| `application` | `PermissionQueryRepository` | Nuevo | Puerto de consulta: recibe `ListPermissionsQuery`, devuelve la lista completa de `PermissionItem` |
| `application` | `PermissionCatalog` | Sin cambios | Puerto de `RF-SP-001`, para resolver identificadores a permisos. **No se reutiliza aquí** (§3, abajo) |
| `infrastructure` | `JpaPermissionQueryRepository` | Nuevo | Adaptador. Construye predicado y proyección con la API de criterios |
| `infrastructure` | `PermissionEntity` | Nuevo | Mapeo JPA de `permissions`. Se usa como metamodelo para nombrar columnas; la consulta no lo instancia |
| `api` | `PermissionController` | Nuevo | `GET /api/v1/permissions`. Declara el permiso y delega |
| `api` | `ListPermissionsRequest` | Nuevo | Parámetros de consulta. Sin Bean Validation: `spec.md` §11 no declara ninguna validación |
| `api` | `PermissionResponse` | Modificado | DTO definido en `RF-SP-001`. Se amplía con `resource`, `action` y `description` (§4) |
| `shared/api` | `PageResponse<T>` | Sin cambios | **No se usa**: este catálogo no se pagina (§4) |

Tres decisiones de reparto:

**`PermissionController` es un controlador nuevo, no un método más de `RoleController`.** El recurso es `/api/v1/permissions`, no un subrecurso de un rol, y `RF-SP-015` añadirá aquí el detalle. Colgarlo de `RoleController` mezclaría dos recursos en una clase por el solo hecho de que ambos pertenecen al mismo módulo.

**No se reutiliza `PermissionCatalog`.** Ese puerto existe para que `RF-SP-001` y `RF-SP-005` resuelvan un conjunto de identificadores a permisos y verifiquen que existen; su firma es de resolución, no de listado, y ampliarlo con un método de consulta filtrada mezclaría dos responsabilidades en un puerto que el dominio usa para decidir. Es el mismo criterio con el que `RF-SP-002` separó `RoleQueryRepository` de `RoleRepository`: lo que devuelve un modelo de lectura no comparte puerto con lo que devuelve el agregado.

**`PermissionItem` y `PermissionResponse` se amplían en lugar de duplicarse.** `RF-SP-001` los definió con `id`, `code` y `name`, que es lo que necesita el detalle de un rol. El catálogo necesita además `resource`, `action` y `description`. Añadir tres campos a los tipos existentes hace que el detalle de un rol los devuelva también, lo cual es información correcta y no contradice ninguna especificación —`spec.md` de `RF-SP-003` §6.2 dice «lista explícita de los permisos que declara», sin enumerar campos—. La alternativa, un segundo par de tipos casi idéntico, obliga a mantener dos representaciones del mismo concepto y a decidir en cada endpoint futuro cuál usar. Confirmado el 21-08-2026 al aprobar este plan; el impacto sobre `RF-SP-001` y `RF-SP-003` queda declarado en §8.

## 4. Contrato de API

| Método | Ruta | Descripción |
|---|---|---|
| `GET` | `/api/v1/permissions` | Catálogo completo de permisos, con filtro por recurso, por acción y búsqueda |

**Petición**

```
GET /api/v1/permissions?resource=roles&action=read&search=auditoria
```

| Parámetro | Tipo | Por defecto | Notas |
|---|---|---|---|
| `resource` | texto | — | Coincidencia **exacta**. Un recurso inexistente devuelve colección vacía, no error |
| `action` | texto | — | Coincidencia exacta. Ídem |
| `search` | texto | — | Sobre código y descripción. Recortado; en blanco equivale a ausente |

- **No hay `page`, `size` ni `sort`.** `spec.md` §6.1 lo decide de forma explícita, y no aceptarlos siquiera es lo que lo hace verificable: con `FAIL_ON_UNKNOWN_PROPERTIES` no basta, porque los parámetros de consulta desconocidos se ignoran en silencio por defecto en Spring. La respuesta es que el DTO de entrada declara exactamente tres campos y **no** se envuelve en `PageResponse`; un cliente que envíe `?page=2` recibe el catálogo entero, que es la respuesta correcta a una petición que pide algo que este recurso no ofrece.
- **`resource` y `action` filtran por igualdad, no por contención.** Son valores de un dominio conocido que el cliente obtiene del propio catálogo; buscar por fragmento es lo que hace `search`. Un filtro por contención sobre `resource` haría que `role` devolviera también los de `roles`, y el usuario no tendría forma de pedir solo uno de los dos.
- **Los permisos se devuelven planos y no agrupados** (`spec.md` §6.2, pregunta 3 de §14). Agrupar es presentación.

**Respuesta `200`**

```json
{
  "content": [
    {
      "id": "018f3a2b-7c41-7000-9a3d-1f2e5b8c9d01",
      "code": "audit:read-changes",
      "resource": "audit",
      "action": "read-changes",
      "name": "Consultar auditoría de cambios",
      "description": "Permite consultar el registro de modificaciones sobre entidades del sistema."
    }
  ]
}
```

- **La colección va envuelta en un objeto con `content`, no devuelta como arreglo desnudo.** Un `[...]` en la raíz cierra la puerta a añadir después cualquier metadato —un total, una marca de versión del catálogo— sin romper a todos los clientes, y obliga a que cada consumidor trate este endpoint distinto de los paginados. El nombre `content` es a propósito el mismo que usa `PageResponse<T>` de `RF-SP-002`: la forma de leer la lista es idéntica, y lo único ausente son los campos de paginación, que es exactamente el mensaje que se quiere dar. **No se reutiliza `PageResponse` con valores inventados**: rellenar `totalPages: 1` diría que hay paginación, y `CA-SP-073` exige que no la haya.
- **`description` puede venir vacía** y se devuelve como `null`, nunca omitida (`spec.md` §13).
- **No se devuelve `createdAt` ni `updatedAt`.** El catálogo se modifica por migración; sus marcas temporales cuentan cuándo se desplegó una migración, que no es información de negocio para quien compone un rol. `RF-SP-015` decidirá si el detalle las expone.
- **No se devuelve cuántos roles declaran cada permiso** (`spec.md` §14, pregunta 2). No hay `JOIN` a `role_permissions` ni subconsulta correlacionada en la sentencia, que es lo único que lo hace verificable.

**Errores**

| Código | Cuándo | `error_code` |
|---|---|---|
| `401` | Token ausente o inválido | `AUTH-001` |
| `403` | Autenticado sin `permissions:read` | `AUTH-002` |
| `500` | Fallo no controlado | `ERR-500` |

- **No hay `400`.** `spec.md` §11 no declara ninguna validación: los tres filtros son opcionales y cualquier valor es admisible, incluido uno que no corresponda a ningún permiso. Es la diferencia con `RF-SP-002`, donde `status` y `roleType` sí tienen dominio cerrado y un valor fuera de él es un error del cliente. Aquí `resource` y `action` son texto libre porque su dominio **es** el contenido de la tabla, y consultarlo es justamente lo que hace este endpoint.
- **No hay `404` ni `422`.** Un filtro sin coincidencias devuelve `200` con la colección vacía (`FA-001`, `CA-SP-075`).
- Los `type` que este endpoint usa ya los estrenó `RF-SP-001`. El formato es el de `architecture.md` §7.3, con `correlationId` siempre presente.

**Cómo se aplica la búsqueda.** El término se recorta; si queda vacío, no se añade predicado. Si no, se escapan `\`, `%` y `_`, y se envía como parámetro enlazado, envuelto en comodines de contención, con `ESCAPE` explícito:

```sql
WHERE (f_unaccent(lower(code)) LIKE f_unaccent(lower(:termino)) ESCAPE '\'
    OR f_unaccent(lower(coalesce(description, ''))) LIKE f_unaccent(lower(:termino)) ESCAPE '\')
```

Tres detalles heredados de `RF-SP-002` §4 y uno propio. Los heredados: la normalización la hace **la base de datos con la misma función** que usará cualquier índice futuro, no Java, porque `java.text.Normalizer` produce un resultado parecido y no idéntico al del diccionario `unaccent`; el escape es lo que impide que un `%` en el término convierta la búsqueda en «devuélvemelo todo»; y el valor viaja enlazado, nunca concatenado. El propio: **`description` es nulable**, de modo que va envuelta en `coalesce`. Sin él, un permiso sin descripción no aparecería jamás en una búsqueda, ni siquiera buscando su propio código, porque `NULL LIKE …` es `NULL` y la rama del `OR` no se evalúa a verdadero.

**Cuántas consultas cuesta.** Una. No hay conteo —no se pagina—, no hay `JOIN` y no hay colección perezosa: la sentencia selecciona seis columnas de una tabla y las materializa con `cb.construct` en `PermissionItem`. El orden es `ORDER BY resource, action`, siempre y sin que el cliente pueda cambiarlo: sin `ORDER BY` explícito PostgreSQL no garantiza orden alguno, y un catálogo que cambia de orden entre dos llamadas hace inútil compararlo. Agrupar por recurso al pintarlo, que es lo que hará la interfaz, es trivial si las filas del mismo recurso llegan juntas.

## 5. Autorización

| Endpoint | Permiso requerido |
|---|---|
| `GET /api/v1/permissions` | `permissions:read` |

- El permiso **lo crea este mismo requerimiento**, en `V3__seed_permissions.sql`. Es el único caso del módulo en que el endpoint y su permiso nacen en la misma migración; en todos los demás el permiso ya existía porque `RF-SP-010` es prerrequisito.
- Se declara sobre el método del controlador (`security.md` §6). Un endpoint sin declaración queda inaccesible, no público (Art. IV.1).
- **No hay filtrado por alcance de datos.** Quien tiene el permiso ve el catálogo entero; no hay nada que acotar, porque un permiso no pertenece a nadie.
- La resolución del permiso puede usar la caché de `security.md` §4.5: aquí solo se decide acceso, no un techo de privilegios. Es la misma conclusión de `RF-SP-002` §5 y la contraria a la de `RF-SP-001` §5.
- **`permissions:read` no está en el catálogo de `security.md` §4.4**, que lista doce permisos y omite este, los de membresías, los de países y los de monedas. `requirements/sp.md` §9 sí lo exige, para este requerimiento y para `RF-SP-015`. Se resuelve enmendando `security.md` §4.4, que es la lista incompleta (§8).

## 6. Auditoría

| Operación | Registro | Contenido relevante |
|---|---|---|
| Consulta exitosa | — | **No se audita** |
| Denegación `403` | `audit_security_log` | `event_type` de denegación de autorización, `severity = 'MEDIA'`, `outcome = 'FAILURE'`. Lo emite la capa de seguridad compartida |
| Fallo no controlado `5xx` | `audit_error_log` | `resource = 'permissions'`, `operation = 'GET /api/v1/permissions'`, `error_code = 'ERR-500'`, `error_type = 'UNHANDLED'`, `severity = 'ALTA'` |
| — | `audit_change_log` | No aplica: la consulta no altera el estado, y el catálogo no se altera nunca por API |
| — | `audit_deletion_log` | No aplica |

- **Una consulta exitosa no produce evento de seguridad.** El catálogo de `security.md` §8.1 es cerrado y no incluye la lectura del catálogo de permisos. La trazabilidad de quién consultó qué la aporta `request_log`. Es la misma conclusión de `RF-SP-002` §6 y `RF-SP-003` §6.
- **La siembra del catálogo tampoco se audita**, por la razón de §2: un permiso no tiene línea de tiempo que reconstruir, porque `RN-SP-004` lo hace inmutable por API.
- **Hay una asimetría con `RF-SP-001` que conviene declarar:** allí los rechazos de regla de negocio van a `audit_error_log`. Aquí no hay ninguno que auditar, porque este requerimiento no declara excepciones (`spec.md` §10) ni validaciones (§11). La tabla queda corta porque el requerimiento lo es, no por omisión.

## 7. Transaccionalidad

| Elemento | Transacción |
|---|---|
| La consulta | **Una sola**, `@Transactional(readOnly = true)` sobre `ListPermissionsService` (`development-guide.md` §10) |
| `audit_error_log` de un fallo no controlado | **Independiente**, `REQUIRES_NEW` (Art. V.14) |
| `audit_security_log` de la denegación `403` | **Independiente**, `REQUIRES_NEW`. La emite la capa de seguridad |
| `request_log` | Ninguna: posterior a la respuesta, *best effort* |

`readOnly = true` no es decorativo: le indica a Hibernate que no registre las entidades para comprobación de cambios y marca la transacción como de solo lectura en PostgreSQL, de modo que un defecto no pueda escribir desde un camino de consulta. En este requerimiento tiene además un valor de diseño: es la garantía de que `RN-SP-004` no se incumple por accidente desde el único camino que toca esta tabla.

No hay aquí el matiz de consistencia entre sentencias que registraron `RF-SP-002` §7 y `RF-SP-003` §7: una sola sentencia toma una sola instantánea.

## 8. Impacto sobre otros módulos

| Módulo | Impacto |
|---|---|
| **Todo el sistema** | `V1__create_shared_functions.sql` y `V3__seed_permissions.sql` son prerrequisito de cualquier endpoint: sin catálogo poblado, ningún rol puede declarar permisos y ningún endpoint es accesible. Es el motivo por el que `requirements/sp.md` §6.1 pone este requerimiento primero |
| `SP` (numeración de migraciones) | Traer la función compartida a `V1` corrió toda la numeración: `V2` permisos, `V3` siembra, `V4` auditoría, `V5` roles, `V6` `role_permissions`, `V7` roles de sistema, `V8` índice de búsqueda de roles. Los planes de `RF-SP-001` a `RF-SP-009` se actualizaron el 21-08-2026. **Nada estaba desplegado**, de modo que renumerar costó una edición documental; hacerlo después habría exigido migrar un historial de Flyway ya aplicado |
| `RF-SP-002` | Su `V8` deja de crear las extensiones y la función y se limita a `ix_roles_busqueda`. El riesgo de privilegios de `CREATE EXTENSION` se traslada aquí (§10) |
| `RF-SP-001` y `RF-SP-003` | `PermissionResponse` gana `resource`, `action` y `description`, de modo que el alta y el detalle de un rol los devuelven también. Es información correcta y ninguna especificación lo contradice, pero **cambia una respuesta ya aprobada** y debe confirmarse (§10) |
| `RF-SP-015` | Consultar el detalle de un permiso cuelga de `PermissionController` y del mismo permiso `permissions:read`. Debe decidir por su cuenta si expone `createdAt` y `updatedAt`, que este endpoint omite |
| Módulos futuros | Todo módulo que se incorpore sembrará sus permisos en su propia migración, y **esa migración deberá además asociarlos a `SUPERADMIN` y a `ADMIN`**, o incumplirá `security.md` §4.1 desde el momento en que se aplique. `V7__seed_system_roles.sql` no puede hacerlo por ellos: asocia el catálogo existente en su momento. Queda declarado en `security.md` §4.4 como obligación permanente |
| `security.md` | Su §4.4 lista doce permisos y omite `permissions:read`, `memberships:*`, `countries:*` y `currencies:*`. Debe enmendarse para recoger el catálogo completo y para decir qué módulo siembra cada bloque, que es la decisión que este plan toma |

## 9. Alternativas consideradas

| Alternativa | Por qué se descartó |
|---|---|
| Dejar las extensiones y `f_unaccent` en la migración de `RF-SP-002` | El catálogo de permisos, que se implementa antes, quedaría con la búsqueda rota hasta que llegara ese requerimiento. No sería un fallo visible al arrancar, sino un error de función inexistente la primera vez que alguien escribiera en el buscador |
| Crearlas en `V2__create_permissions.sql` sin renumerar | Evita tocar los planes ya aprobados, a cambio de una migración cuyo nombre no dice lo que hace. Con nada desplegado, renumerar cuesta una edición documental y deja un historial que se lee bien para siempre |
| Dejar `users:*` fuera y que los siembre el módulo de usuarios | Era la decisión del borrador, correcta mientras `USR` existía. Sin ese módulo no hay quién los siembre, y dejarlos fuera obligaría a una migración de datos por cada permiso de usuario que llegara, más otra para dárselos a `ADMIN` |
| Sembrar solo los permisos de requerimientos ya especificados | Dejaría fuera `countries:update` y `currencies:update` y obligaría a una migración de datos —y a otra para dárselos a `ADMIN`— por cada requerimiento que llegue. Sembrarlos hoy no concede nada: un permiso que ningún endpoint declara es inerte |
| `CHECK` cerrado sobre `action` | `security.md` §4.4 admite acciones específicas del dominio, y el propio catálogo ya las usa. Cerrar el dominio obligaría a alterar la restricción cada vez que un módulo estrena una, que es la fricción que `RN-SP-004` quiere evitar |
| Generar `code` como columna generada a partir de `resource` y `action` | Garantizaría la coherencia sin `CHECK`, pero `requirements/sp.md` §10.1 declara `code` como columna propia «para poder consultarlo y referenciarlo directamente», y una columna generada no admite ser escrita por la migración de siembra. El `CHECK` da la misma garantía sin cambiar el modelo aprobado |
| Índice de trigramas sobre `permissions` como el de `roles` | Mantener una estructura para acelerar el recorrido de veinte filas. La búsqueda sobre un catálogo de este tamaño es más rápida sin índice que con él |
| Paginar el catálogo | `spec.md` §6.1 y §14 lo resolvieron: el catálogo alimenta la composición de un rol y esa tarea necesita verlo entero. Paginarlo convierte «qué permisos hay» en un recorrido de páginas |
| Devolver la colección como arreglo desnudo en la raíz | Impide añadir después cualquier metadato sin romper a todos los clientes, y obliga a cada consumidor a leer este endpoint distinto de los paginados |
| Reutilizar `PageResponse<T>` con `totalPages: 1` | Diría que hay paginación donde no la hay, y `CA-SP-073` exige lo contrario. Los campos de paginación no se rellenan con valores de adorno |
| Agrupar los permisos por recurso en la respuesta | `spec.md` §14, pregunta 3: agrupar es presentación. El `ORDER BY resource, action` deja las filas del mismo recurso juntas, que es cuanto la interfaz necesita para agruparlas ella |
| Devolver cuántos roles declaran cada permiso | `spec.md` §14, pregunta 2: es el recorrido inverso del catálogo y encarece una consulta hoy trivial. Mismo criterio con el que `RF-SP-003` dejó fuera el listado de roles hijos |
| Filtrar `resource` y `action` por contención | `role` devolvería también los permisos de `roles`, y el cliente no tendría forma de pedir solo uno. La contención ya la ofrece `search` |
| Ampliar `PermissionCatalog` con un método de listado | Mezcla resolución y consulta en un puerto que el dominio usa para decidir. Mismo criterio con el que `RF-SP-002` separó `RoleQueryRepository` de `RoleRepository` |
| Un segundo par de tipos para el catálogo, distinto del de `RF-SP-001` | Dos representaciones del mismo concepto que hay que mantener en paralelo, y una decisión que tomar en cada endpoint futuro sobre cuál usar |
| Auditar la siembra del catálogo como hace `V7` con los roles | Un permiso no tiene línea de tiempo que reconstruir: `RN-SP-004` lo hace inmutable por API, así que serían veintitrés filas que nunca tendrían una vigesimocuarta. El historial de una tabla que solo cambia por migración lo lleva `flyway_schema_history` |

## 10. Riesgos

| Riesgo | Impacto | Mitigación |
|---|---|---|
| `CREATE EXTENSION` requiere privilegios que el usuario de la aplicación puede no tener en un PostgreSQL administrado, y ahora es la **primera** migración del sistema | Alto | Se comprueba en cada entorno antes de desplegar `V1`; si el usuario de migración no puede crearlas, las instala el administrador de base de datos como paso previo y la migración las encuentra por el `IF NOT EXISTS`. Que sea la primera migración es una mejora respecto de tenerla en `V8`: el fallo aparece al levantar el entorno por primera vez, no a mitad del historial |
| `f_unaccent` se declara `IMMUTABLE` sin serlo del todo: si el diccionario `unaccent` cambia, los índices que la usan conservan valores obsoletos y devuelven resultados incorrectos **sin error** | Medio | El diccionario se referencia cualificado y no se personaliza. Queda registrado que cualquier cambio en él obliga a `REINDEX` de todo lo que dependa de la función: hoy `ix_roles_busqueda`, mañana `ix_countries_busqueda` |
| Una migración futura siembra permisos y olvida asociarlos a `SUPERADMIN` y `ADMIN` | Alto | Declarado en `security.md` §4.4 como obligación de toda migración que siembre permisos. El síntoma no es evidente: `ADMIN` quedaría incapaz de crear un rol que declare ese permiso, y `RN-SEG-003` rechazaría la operación sin decir que lo que falta es una siembra |
| ~~Ampliar `PermissionResponse` cambia una respuesta ya aprobada en `RF-SP-001` y `RF-SP-003`~~ | — | **Resuelto el 21-08-2026:** se amplía el tipo existente. Añadir campos no rompe a ningún cliente, y mantener dos representaciones del mismo concepto habría obligado a decidir cuál usar en cada endpoint futuro. Los planes de `RF-SP-001` y `RF-SP-003` quedan anotados |
| El catálogo crece y devolverlo entero deja de ser razonable (`spec.md` §13) | Bajo | Con veintitrés permisos la respuesta ronda los pocos kilobytes. El `CHECK` de 500 caracteres sobre `description` acota el peor caso. Si llegara a crecer en un orden de magnitud, la decisión de no paginar habría que revisarla en la especificación, no aquí |
| Una migración futura deja `code` incoherente con `resource` y `action` | Bajo | `ck_permissions_code_matches` lo hace imposible en base de datos. Es la restricción que convierte un error silencioso —filtro por recurso que no encuentra lo que el código dice— en un fallo de migración |
| Un permiso sin descripción desaparece de la búsqueda | Medio | `coalesce(description, '')` en el predicado (§4), con prueba propia en §11. Es el defecto más fácil de introducir y el más difícil de notar: el permiso existe, se lista sin filtros y solo falta cuando alguien busca |

## 11. Estrategia de prueba

Niveles: **Integración** (Testcontainers sobre PostgreSQL real, con `V1` a `V3` aplicadas) y **API** (extremo a extremo por HTTP, con autenticación). No hay nivel unitario: este requerimiento no tiene `domain`.

| Criterio | Nivel | Qué verifica |
|---|---|---|
| `CA-SP-073` | Integración + API | La respuesta trae los veintitrés permisos sembrados, con `code`, `resource`, `action`, `name` y `description`; el cuerpo **no** contiene `page`, `size`, `totalElements` ni `totalPages`, y las filas no vienen anidadas por recurso |
| `CA-SP-074` | Integración + API | `resource=audit` devuelve los cuatro de auditoría y ninguno más; `action=read` devuelve los de lectura de todos los recursos; combinados, la intersección |
| `CA-SP-075` | API | `resource=inexistente` devuelve `200` con `content` vacío. Nunca `404` ni `204` |
| `CA-SP-076` | API | No existe manejador para `POST`, `PUT`, `PATCH` ni `DELETE` sobre `/api/v1/permissions` ni sobre `/api/v1/permissions/{id}`: las cuatro devuelven `405`. Es la única forma de verificar `RN-SP-004`, que no tiene código que la implemente |
| `CA-SP-077` | API | Un actor autenticado sin `permissions:read` recibe `403`, no obtiene dato alguno del catálogo y queda el evento de denegación en `audit_security_log` |

Casos límite de `spec.md` §13 y decisiones de este plan que exigen prueba propia (Art. VII.3):

| Caso | Nivel | Qué verifica |
|---|---|---|
| Permiso sin descripción | Integración | Se devuelve con `description: null`, sin omitir el campo, **y sigue apareciendo al buscar por su código**. Es la prueba que detecta la ausencia del `coalesce` |
| Búsqueda insensible a acentos y mayúsculas | Integración | Buscar `auditoria`, `AUDITORÍA` y `Auditoria` encuentra los permisos cuya descripción dice «auditoría». Exige PostgreSQL real: `unaccent` no es simulable |
| Búsqueda con `%`, `_` y `\` | Integración | Se tratan como texto literal: un término con `%` no devuelve el catálogo entero |
| Búsqueda vacía o solo espacios | API | Equivale a no filtrar: mismo resultado que la consulta sin el parámetro |
| Orden estable | Integración | Dos llamadas consecutivas devuelven las filas en el mismo orden, agrupadas por recurso y ordenadas por acción dentro de cada uno |
| Parámetros de paginación ignorados | API | `?page=2&size=5` devuelve el catálogo completo, no cinco elementos ni un error |
| Número de sentencias por petición | Integración | **Una**, con y sin filtros, y ninguna sobre `role_permissions`. Es lo que hace verificable que el catálogo no cuenta roles |
| Coherencia de `code` | Integración | Un `INSERT` con `code = 'roles:read'`, `resource = 'role'` y `action = 'read'` es rechazado por `ck_permissions_code_matches` |
| Formato del código | Integración | `ck_permissions_code_format` rechaza `Roles:Read`, `roles read` y `roles:`; acepta `audit:read-changes` |
| Catálogo completo tras la siembra | Integración | Tras `V3`, la tabla tiene exactamente veintitrés filas, incluidas las siete de recurso `users` |

Las reglas de ArchUnit introducidas en `RF-SP-001` y `RF-SP-003` cubren también este requerimiento. No se añade ninguna nueva: no toca `domain` y no introduce dependencias entre módulos.
