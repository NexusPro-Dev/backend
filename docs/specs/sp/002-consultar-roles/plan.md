# PLAN — `RF-SP-002` Consultar roles

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-002` |
| Especificación | [`spec.md`](spec.md) |
| `spec.md` aprobada el | 20-08-2026 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable técnico |
| Fecha de aprobación | 21-08-2026 |

!!! info "Qué va en este documento"

    **Cómo se construye.** Las decisiones técnicas que la especificación deliberadamente no toma.

    **Prueba de pertenencia:** si al negocio no le importa ni lo entendería, va aquí.

El comportamiento —flujos, excepciones, validaciones y criterios de aceptación— es el de [`spec.md`](spec.md) y no se repite aquí. Este documento decide cómo se consulta: el índice que sostiene la búsqueda, la proyección que se lee, la forma de la paginación y el modo de acotar el ordenamiento.

---

## 1. Enfoque

La consulta se resuelve con **una sola sentencia de lectura sobre una proyección**, no cargando el agregado `Role`. La capa `infrastructure` construye la consulta con la API de criterios de JPA a partir de los filtros presentes —solo los presentes— y la materializa directamente en un registro plano de lectura, con un `LEFT JOIN` al rol padre en la misma sentencia. No hay entidades JPA en el camino de esta funcionalidad y, por tanto, no hay colección perezosa que pueda dispararse ni `role_permissions` que se lea sin que nadie lo haya pedido.

La búsqueda insensible a mayúsculas y acentos se apoya en la función `IMMUTABLE` que envuelve a `unaccent` —creada en `V1` por `RF-SP-010`, que la necesita antes— y en un índice GIN de trigramas sobre esa expresión, que sí es de este requerimiento (`V8`). Sin ese envoltorio no hay índice posible, y sin trigramas no hay índice que sirva a una coincidencia por contención; ambos puntos se justifican en §2.

`domain` no participa: `spec.md` §5 declara que ninguna regla de negocio gobierna esta consulta y que el alcance de los datos es global. El caso de uso vive íntegro en `application` como servicio de solo lectura, y todo lo que la especificación llama validación (`VAL-001` a `VAL-004`) es formato de parámetros y se resuelve en `api` antes de construir consulta alguna. Este requerimiento también estrena la mecánica de paginación del sistema: la envoltura de respuesta, el rechazo del tamaño excesivo y la lista blanca de ordenamiento nacen aquí y los heredan todos los listados posteriores.

## 2. Cambios de esquema

**Migración:** `V8__create_role_search_index.sql`

La tabla `roles` ya existe: la crea `V5__create_roles.sql` (`RF-SP-001`), junto con `ix_roles_parent_role_id`, que este requerimiento aprovecha para el filtro por rol padre. `V2` a `V7` pertenecen a `RF-SP-010` y `RF-SP-001` y se dan por aplicadas. **No hay cambios de columnas ni de restricciones**: la consulta no modifica la forma de los datos, solo añade la estructura de acceso que la búsqueda exige.

| Tabla | Cambio | Detalle |
|---|---|---|
| `roles` | Altera (índice) | `ix_roles_busqueda`, GIN de trigramas sobre `f_unaccent(lower(code))` y `f_unaccent(lower(name))` |

```sql
CREATE INDEX ix_roles_busqueda ON roles USING gin (
    f_unaccent(lower(code)) gin_trgm_ops,
    f_unaccent(lower(name)) gin_trgm_ops
);
```

**Las extensiones y la función no se crean aquí.** El borrador de este plan las creaba en esta misma migración. Se movieron el 21-08-2026 a `V1__create_shared_functions.sql`, al redactar el plan de `RF-SP-010`: ese requerimiento se implementa **antes** que este —es el primero del orden aprobado en `requirements/sp.md` §6.1— y su búsqueda del catálogo de permisos también es insensible a acentos, de modo que necesita `f_unaccent` desde el primer día. Dejarla aquí habría dejado el catálogo de permisos con la búsqueda rota hasta implementar `RF-SP-002`. `V1` es también donde vive la justificación de por qué `unaccent` no es indexable directamente y por qué hay que envolverla; no se repite aquí.

Tres decisiones sostienen las cuatro líneas que sí quedan.

**Por qué GIN de trigramas y no un B-tree.** Un `LIKE '%termino%'` no puede usar un B-tree en ningún caso: el B-tree ordena por prefijo y una coincidencia que puede empezar en cualquier posición no acota el rango a recorrer. Un B-tree funcional con `text_pattern_ops` serviría solo a la búsqueda por prefijo (`'termino%'`), y el prefijo no basta aquí: códigos como `LIDER_ACADEMICO` obligan a que teclear «academico» encuentre el rol, y ningún prefijo lo consigue. El índice de trigramas descompone cada valor en secuencias de tres caracteres y responde a la contención, que es la semántica que este listado necesita. Ese es también el motivo de la extensión adicional: `unaccent` resuelve los acentos, pero no aporta operador de índice alguno; el índice lo aporta `pg_trgm`.

**Por qué un índice multicolumna y no dos.** El predicado es `código O nombre`, y un GIN multicolumna admite ser usado por cualquiera de sus columnas de forma independiente; el planificador combina ambas ramas del `OR` con un `BitmapOr` sobre el mismo índice. Mantiene además el nombre único `ix_roles_busqueda` que exige `requirements/sp.md` §10.7, en lugar de inventar dos que ese documento no declara.

El índice **no es parcial**: no lleva `WHERE deleted_at IS NULL`. Excluir los eliminados lo haría marginalmente más pequeño, pero dejaría sin cobertura la consulta que sí los incluye (`CA-SP-011`), y a este volumen —decenas de roles— el ahorro no existe. La distinción se deja al predicado, no al índice.

**Recordatorios de la plantilla que no aplican a esta migración:** no crea tablas, así que no hay clave primaria UUID v7, ni `created_at`/`updated_at`, ni columnas de actor que omitir, ni integridad declarativa que añadir. La convención de nombre de `architecture.md` §6.2 se respeta en el índice; el nombre `ix_roles_busqueda` viene fijado en español por `requirements/sp.md` §10.7 y se conserva tal cual. La discrepancia con el inglés que `development-guide.md` §4.1 exige a los objetos de base de datos se cerró el 21-08-2026: ese documento pasa a declarar como excepción los nombres que un requerimiento ya fijó, y adopta el prefijo `f_` para funciones de base de datos, que hasta entonces no tenía convención.

## 3. Componentes afectados

Paquete raíz del módulo: `com.factech.nexus.modules.system`. Reglas de dependencia de `architecture.md` §5.2.

| Capa | Componente | Nuevo / Modificado | Responsabilidad |
|---|---|---|---|
| `domain` | — | — | Sin participación: `spec.md` §5 no declara ninguna regla `RN-…` para esta consulta |
| `application` | `ListRolesService` | Nuevo | Caso de uso. `@Transactional(readOnly = true)`. Traduce la consulta al puerto y arma el resultado paginado |
| `application` | `ListRolesQuery` | Nuevo | Criterios ya validados y normalizados: filtros, término de búsqueda, orden y página. Sin tipos de HTTP |
| `application` | `RoleSortField` | Nuevo | Enum cerrado de campos ordenables. Es la lista blanca de §4 |
| `application` | `RoleListItem` | Nuevo | Modelo de lectura: exactamente los campos que el listado devuelve, con el padre embebido |
| `application` | `RoleQueryRepository` | Nuevo | Puerto de consulta: recibe `ListRolesQuery`, devuelve la página de `RoleListItem` y el total |
| `infrastructure` | `JpaRoleQueryRepository` | Nuevo | Adaptador. Construye predicado, proyección y conteo con la API de criterios |
| `infrastructure` | `RoleEntity` | Sin cambios | Se usa solo como metamodelo para nombrar columnas en los criterios; no se instancia |
| `infrastructure` | `RoleJpaMapper` | Sin cambios | No interviene: la proyección se construye en la propia consulta, sin pasar por el agregado |
| `api` | `RoleController` | Modificado | Añade `GET /api/v1/roles`. Declara el permiso y delega |
| `api` | `ListRolesRequest` | Nuevo | Parámetros de consulta con Bean Validation (`VAL-001` a `VAL-004`) |
| `api` | `RoleListItemResponse` | Nuevo | DTO de salida de cada fila. Reutiliza `RoleSummaryResponse` para el rol padre |
| `api` | `RoleSummaryResponse` | Sin cambios | Definido en `RF-SP-001` (`id`, `code`, `name`). Se reutiliza tal cual |
| `shared/api` | `PageResponse<T>` | Nuevo | Envoltura de colección paginada, uniforme para todo el sistema (`architecture.md` §7.4). Ampliada el 21-08-2026 por `RF-SP-011` con `totalIsExact`, que aquí vale siempre `true` |
| `shared/api` | `PageRequestFactory` | Nuevo | Valida `page` y `size` contra el máximo configurado y produce el `Pageable`. Único lugar donde vive el techo |
| `shared/error` | `GlobalExceptionHandler` | Sin cambios | Ya traduce `ValidationException` a `400`; esta funcionalidad no estrena ningún tipo de error |

Sobre por qué el puerto de consulta vive en `application` y no en `domain`: `RoleRepository` está en `domain` porque devuelve el agregado y lo protege. `RoleQueryRepository` devuelve un modelo de lectura que no es el agregado —le faltan los permisos y le sobra la forma del padre—, y ponerlo en `domain` obligaría al dominio a declarar un tipo que existe únicamente para dar forma a una respuesta HTTP. Es el mismo criterio con el que `RF-SP-001` situó `AuthenticatedActor` y `RoleChangeAuditor` en `application`.

`RoleResponse` no se reutiliza: incluye `permissions`, y `spec.md` §4.2 excluye los permisos del listado. Devolverlo con la lista vacía sería peor que no devolverlo, porque un rol con permisos aparecería como si no los tuviera.

## 4. Contrato de API

| Método | Ruta | Descripción |
|---|---|---|
| `GET` | `/api/v1/roles` | Listado paginado de roles con filtros, búsqueda y ordenamiento |

**Petición**

```
GET /api/v1/roles?page=0&size=20&sort=name,asc
                 &status=ACTIVO
                 &roleType=FUNCIONARIO
                 &parentRoleId=018f3a2b-7c41-7000-9a3d-1f2e5b8c9d01
                 &search=administracion
                 &includeDeleted=false
```

| Parámetro | Tipo | Por defecto | Notas |
|---|---|---|---|
| `page` | entero | `0` | Base cero. Negativo → `VAL-001` |
| `size` | entero | `20` | Entre 1 y 100. Fuera de rango → `VAL-002`; **no se recorta** |
| `sort` | `campo,sentido` | `code,asc` | Solo los campos de la lista blanca. Otro → `VAL-003` |
| `status` | enum | — | `ACTIVO` o `INACTIVO`. Otro → `VAL-004` |
| `roleType` | enum | — | `FUNCIONARIO`, `VENDEDOR`, `CONSUMIDOR`. Otro → `VAL-004` |
| `parentRoleId` | UUID | — | Padre **directo**. No se valida que exista |
| `search` | texto | — | Sobre código y nombre. Recortado; en blanco equivale a ausente |
| `includeDeleted` | booleano | `false` | `true` incorpora los roles con `deleted_at` no nulo |

**Respuesta `200`**

```json
{
  "content": [
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
      "deletedAt": null
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 7,
  "totalPages": 1
}
```

Decisiones del contrato:

- **La envoltura de paginación se fija aquí para todo el sistema.** `architecture.md` §7.4 exige total de elementos, total de páginas y página actual, pero no nombra los campos, y este es el primer listado que se implementa. Se declara `PageResponse<T>` en `shared/api` con esos nombres más `size`, y **no se serializa el `Page` de Spring Data**: su forma JSON no es contrato estable —Spring Boot 3.3 en adelante lo advierte de forma explícita— y publicarla ataría el contrato de la API a la versión del framework. El 21-08-2026, al aprobar el plan de `RF-SP-011`, la envoltura ganó un campo más, `totalIsExact`: los cuatro listados de auditoría cuentan hasta un techo y necesitan poder decir que el total no es exacto. Este endpoint lo devuelve **siempre `true`**, porque su conteo sí lo es.
- **No existe `permissions`** (`spec.md` §4.2) **ni ningún campo con el número de usuarios asignados** (`CA-SP-148`). Este último no es una omisión de redacción: no hay `JOIN` a la tabla de asignación ni subconsulta correlacionada en la sentencia, que es lo único que hace verificable el criterio. La pregunta se responde en `RF-SP-003`.
- **`parentRole` es nulo en el rol raíz**, y por eso la sentencia usa `LEFT JOIN`. Con `JOIN` interno el rol raíz desaparecería del listado sin error visible, y el catálogo de un sistema recién instalado se vería incompleto (`spec.md` §13).
- **`deletedAt` e `isSystem` van en la respuesta**, y `spec.md` §6.2 los declara como «marca de eliminación» y «marca de rol de sistema». Sin el primero, `includeDeleted=true` devuelve una mezcla en la que el cliente no puede distinguir qué está eliminado, y `CA-SP-011` quedaría satisfecho con una respuesta inútil. Sin el segundo, el listado —que es la entrada natural a editar, cambiar de estado y eliminar— no permite saber qué filas admiten esas acciones.
- **`sort` admite un solo criterio.** El ordenamiento múltiple no lo pide la especificación y multiplica la superficie de validación. Se añadirá cuando algún requerimiento lo justifique.
- **A todo ordenamiento se le añade `id` como desempate**, sin declararlo el cliente. Ordenar por `status` en una tabla donde muchos roles comparten valor deja el orden de las filas empatadas a criterio del plan de ejecución, y ese orden puede cambiar entre la página 1 y la página 2: filas repetidas en una y ausentes en la otra. El desempate por clave primaria es lo que hace determinista el recorrido de páginas.

**Errores**

| Código | Cuándo | `error_code` | Campo en `errors` |
|---|---|---|---|
| `400` | `page` negativa | `VAL-001` | `page` |
| `400` | `size` fuera de `[1, 100]` | `VAL-002` | `size` |
| `400` | Campo de ordenamiento fuera de la lista blanca | `VAL-003` | `sort` |
| `400` | `status` o `roleType` fuera de su dominio | `VAL-004` | `status` o `roleType` |
| `400` | `parentRoleId` no es un UUID | `VAL-004` | `parentRoleId` |
| `401` | Token ausente o inválido | `AUTH-001` | — |
| `403` | Autenticado sin `roles:read` | `AUTH-002` | — |
| `500` | Fallo no controlado | `ERR-500` | — |

- **No hay `404` ni `422`.** Un filtro que no encuentra nada devuelve `200` con la colección vacía (`FA-001`, `CA-SP-013`), y una página más allá de la última hace lo mismo. Tratarlo como error obligaría al cliente a distinguir «no hay» de «falló», que son la misma respuesta útil.
- **Los cuatro `400` se evalúan juntos y se devuelven juntos** en `errors`. Son independientes entre sí y devolverlos de a uno obliga a corregir la URL parámetro por parámetro.
- **El `403` lo produce la capa de seguridad compartida antes de entrar al caso de uso**, y es ella quien emite el evento de `audit_security_log` (§6). `CA-SP-015` se satisface ahí.
- Los `type` que este endpoint usa —`…/errors/validacion`, `…/errors/no-autenticado`, `…/errors/sin-permiso`, `…/errors/interno`— ya los estrenó `RF-SP-001`. El formato es el de `architecture.md` §7.3, con `correlationId` siempre presente.

**Cómo se acota el ordenamiento.** El campo recibido se resuelve contra el enum `RoleSortField`, que asocia cada nombre público a un atributo del metamodelo de JPA; un valor no reconocido produce `VAL-003` **antes** de construir la consulta. La cadena del cliente nunca llega a la sentencia. Es el punto que importa: con una consulta `@Query` y un `Sort` de Spring Data, la propiedad recibida se concatena al `ORDER BY` sin comprobarse contra la entidad, y ahí un parámetro de ordenamiento es una vía de inyección. Con criterios y enum, el peor caso posible es un `400`. La lista blanca es `code`, `name`, `roleType`, `status`, `createdAt` y `updatedAt`: los campos escalares del propio rol, tal como exige `spec.md` §6.1. Quedan fuera `description` —ordenar por un `text` libre no responde ninguna pregunta y no está indexado—, `parentRoleId` —ordenar por un UUID opaco tampoco— y todo lo que no pertenezca a la tabla.

**Cómo se aplica la búsqueda.** El término se recorta; si queda vacío, no se añade predicado (`spec.md` §13). Si no, se escapan `\`, `%` y `_`, y se envía **como parámetro enlazado**, envuelto en comodines de contención, con `ESCAPE` explícito:

```sql
WHERE (f_unaccent(lower(code)) LIKE f_unaccent(lower(:termino)) ESCAPE '\'
    OR f_unaccent(lower(name)) LIKE f_unaccent(lower(:termino)) ESCAPE '\')
```

Dos detalles no obvios. El primero: la normalización del término la hace **la base de datos con la misma función que alimenta el índice**, no Java. Normalizar en Java con `java.text.Normalizer` produciría un resultado parecido pero no idéntico al del diccionario `unaccent`, y cualquier divergencia se traduce en un rol que existe, está indexado y no aparece. El segundo: el escape es lo que cumple el caso límite de `spec.md` §13 sobre caracteres especiales. Un `%` sin escapar no es inyección —el valor va enlazado—, pero convierte «100%» en un comodín que devuelve el catálogo entero.

**Cómo se construye el resto del predicado.** Cada filtro presente añade su condición; los ausentes no añaden nada. Se descarta explícitamente el patrón `(:status IS NULL OR r.status = :status)`, que resulta más corto de escribir pero produce una única sentencia para todas las combinaciones de filtros: el planificador debe elegir un plan válido para todos los casos y deja de aprovechar los índices que sí sirven a la combinación concreta. El predicado de los datos y el del conteo se generan **con la misma función**, de modo que no puedan divergir; un conteo que aplique un filtro distinto al de los datos produce un `totalElements` que no corresponde a lo devuelto, y es un fallo que ninguna prueba de la página detecta.

**Proyección y N+1.** La sentencia selecciona once columnas —las nueve del rol que la respuesta usa, más `code` y `name` del padre— y las materializa con `cb.construct` en `RoleListItem`. No se leen entidades. Esto resuelve las dos cosas a la vez:

- No hay `N+1` sobre el rol padre, porque el padre se resuelve con el `LEFT JOIN` de la propia sentencia. Cargar `RoleEntity` y navegar a su padre produciría una consulta por fila —veinte consultas para una página de veinte— y ese es exactamente el patrón que `development-guide.md` §11 prohíbe.
- No se toca `role_permissions` en ningún momento. Con entidades, la colección de permisos es `LAZY` y basta con que un `toString`, un mapeador o una serialización la recorra para que aparezca otra consulta por fila. Con proyección, esa consulta no puede existir: la asociación no está en la sentencia.

**Coste del conteo.** `spec.md` §6.2 exige total de elementos y total de páginas, y eso obliga a una segunda sentencia `COUNT(*)`. Tres decisiones al respecto:

1. **El conteo se omite cuando el resultado lo hace innecesario**: si la primera página no se llena, o si la página devuelve menos filas que el tamaño pedido, el total se deduce de lo ya leído. Es lo que hace `PageableExecutionUtils.getPage`, y ahorra la sentencia en el caso más frecuente, que es el catálogo pequeño sin filtros.
2. **El conteo no incluye el `LEFT JOIN` al padre.** No cambia el número de filas —la clave foránea apunta a lo sumo a un rol—, pero sí añade trabajo que no aporta nada.
3. **A este volumen el conteo es irrelevante**: un catálogo de roles se mide en decenas y `COUNT(*)` es un recorrido secuencial de milisegundos. Se documenta porque el patrón no escala igual en todas partes: en `RF-SP-011` a `RF-SP-014`, sobre registros de auditoría que crecen sin límite, un conteo exacto por página es un recorrido completo de la tabla en cada petición, y la respuesta correcta allí es paginación por cursor o un total aproximado. **Esa decisión pertenece a esos requerimientos y no se toma aquí**, pero se deja anotada para que no se herede este plan sin revisarlo.

## 5. Autorización

| Endpoint | Permiso requerido |
|---|---|
| `GET /api/v1/roles` | `roles:read` |

- El permiso **ya existe** en el catálogo: lo crea `V3__seed_permissions.sql` (`RF-SP-010`). No hace falta migración de permisos.
- Se declara sobre el método del controlador (`security.md` §6). Un endpoint sin declaración queda inaccesible, no público (Art. IV.1).
- **No hay filtrado por alcance de datos.** `spec.md` §5 lo dice de forma explícita: quien tiene el permiso ve todos los roles. La consulta no recibe el actor ni lo usa en el predicado. Si alguna vez se resuelve **D-22** (`requirements/sp.md` §10.2), este endpoint es de los primeros que habrá que revisar, porque hoy no tiene ningún punto donde insertar esa restricción.
- La resolución del permiso en tiempo de autorización sí puede usar la caché de `security.md` §4.5: aquí solo se decide acceso, no un techo de privilegios. Es la situación inversa a la de `RF-SP-001` §5, y por eso la conclusión es la contraria.

## 6. Auditoría

| Operación | Registro | Contenido relevante |
|---|---|---|
| Consulta exitosa | — | **No se audita.** Ver abajo |
| Rechazo `400` por `VAL-001` a `VAL-004` | — | **No se audita**: son validaciones de formato (`architecture.md` §6.6.4) |
| Denegación `403` | `audit_security_log` | `event_type` de denegación de autorización, `severity = 'MEDIA'`, `outcome = 'FAILURE'`. Lo emite la capa de seguridad compartida |
| Fallo no controlado `5xx` | `audit_error_log` | `resource = 'roles'`, `operation = 'GET /api/v1/roles'`, `error_code = 'ERR-500'`, `error_type = 'UNHANDLED'`, `severity = 'ALTA'` |
| — | `audit_change_log` | No aplica: la consulta no altera el estado (`spec.md` §7) |
| — | `audit_deletion_log` | No aplica: no elimina nada |

Dos puntos que conviene dejar escritos, porque la ausencia de auditoría es tan decisión como su presencia:

- **Una consulta exitosa no produce evento de seguridad.** El catálogo de `security.md` §8.1 es cerrado y no incluye la lectura de roles; los eventos que registra son los que cambian privilegios o los que fallan contra el control de acceso. Auditar cada listado añadiría una fila por pulsación de un administrador y sepultaría bajo ruido informativo la búsqueda de eventos reales. La trazabilidad de quién consultó qué la aporta `request_log`, que registra toda petición con su actor, su `correlation_id` y su IP.
- **El `403` no va a `audit_error_log`.** Es lo que fija `security.md` §8.1: un `403` es el sistema funcionando, no un fallo, y contarlo como error contamina la búsqueda de fallos. `CA-SP-015` se verifica sobre `audit_security_log`.

## 7. Transaccionalidad

| Elemento | Transacción |
|---|---|
| Consulta de datos y conteo | **Una sola**, `@Transactional(readOnly = true)` sobre `ListRolesService` (`development-guide.md` §10) |
| `audit_error_log` de un fallo no controlado | **Independiente**, `REQUIRES_NEW` (Art. V.14) |
| `audit_security_log` de la denegación `403` | **Independiente**, `REQUIRES_NEW`. La emite la capa de seguridad |
| `request_log` | Ninguna: posterior a la respuesta, *best effort* |

`readOnly = true` no es decorativo: le indica a Hibernate que no registre las entidades para comprobación de cambios ni fuerce vaciados antes de cada consulta, y marca la transacción como de solo lectura en PostgreSQL, lo que impide que un fallo de programación escriba desde un camino de consulta.

Un matiz que suele darse por sentado y es falso: **envolver el conteo y la lectura de datos en la misma transacción no los hace consistentes entre sí**. Bajo `READ COMMITTED`, que es el nivel por defecto, cada sentencia toma su propia instantánea, de modo que un alta concurrente entre ambas puede dejar un `totalElements` que no corresponde exactamente a la página devuelta. Corregirlo exigiría `REPEATABLE READ` en toda consulta paginada. **No se hace**: sobre un catálogo de roles, cuyas altas se cuentan por semanas, el desfase es teórico, y elevar el nivel de aislamiento en cada listado del sistema es un coste permanente para un problema que aquí no se manifiesta. Queda registrado para que la ausencia de aislamiento se lea como decisión y no como descuido.

## 8. Impacto sobre otros módulos

| Módulo | Impacto |
|---|---|
| `shared/api` | Se crea con este requerimiento: `PageResponse<T>` y `PageRequestFactory`. **Todo listado posterior del sistema los usa**, y cambiar después la forma de la envoltura sería un cambio de contrato en todos los endpoints a la vez. Es la decisión de este plan con mayor alcance fuera de él |
| `shared/config` | Declara `nexus.pagination.default-size: 20` y `nexus.pagination.max-size: 100`, un solo lugar para todo el sistema (`architecture.md` §7.4). **No se usa `spring.data.web.pageable.max-page-size`**: ese ajuste **recorta en silencio** el tamaño excedido, que es justo lo que §7.4 prohíbe y lo que `CA-SP-014` verifica que no ocurre |
| `shared/persistence` | La función `f_unaccent` es un recurso compartido y la crea `V1__create_shared_functions.sql` (`RF-SP-010`), no esta migración. La reutilizan la búsqueda del catálogo de permisos y `ix_countries_busqueda` (`RF-SP-021`). Ningún requerimiento posterior debe volver a crearla, y quien la modifique debe reindexar todo lo que dependa de ella |
| `SP` (resto del módulo) | `RF-SP-003` construye el detalle sobre este listado y es quien aporta permisos y número de usuarios. `RF-SP-011` a `RF-SP-014` heredan la envoltura y la lista blanca de ordenamiento, pero **no deben heredar el conteo exacto sin revisarlo** (§4) |
| `USR` | Consume el catálogo de roles por la interfaz publicada de `SP`, nunca por sus tablas (`architecture.md` §5.3). Este endpoint es el que alimenta la selección de rol al asignarlo a un usuario |

## 9. Alternativas consideradas

| Alternativa | Por qué se descartó |
|---|---|
| Colación ICU no determinista sobre `code` y `name`, que ignora mayúsculas y acentos sin funciones ni índices adicionales | PostgreSQL **prohíbe `LIKE` y los operadores de patrón sobre columnas con colación no determinista**, de modo que la búsqueda por contención dejaría de ser posible. Además cambiaría la semántica de `uq_roles_code` y `uq_roles_name`, que `RF-SP-001` ya declaró y que pasarían a considerar `Contabilidad` y `contabilidad` el mismo nombre: una decisión de negocio, no de consulta |
| Tipo `citext` en lugar de `unaccent` | Resuelve las mayúsculas y **no resuelve los acentos**, que es la mitad del requisito y la que motivó la pregunta 1 de `spec.md` §14 |
| Columna generada y persistida `search_text` con el texto ya normalizado, indexada con un índice corriente | Necesita igualmente una función `IMMUTABLE`, así que no evita el envoltorio; añade una columna denormalizada a una tabla de negocio para acomodar una necesidad de consulta, y hay que mantenerla para dos columnas y para cada tabla que busque |
| Búsqueda de texto completo con `tsvector` y `to_tsquery` | Está pensada para palabras, no para subcadenas: lematiza, descarta vacías y no encuentra «conta» dentro de «Contabilidad» salvo con búsqueda por prefijo, que ya se descartó. Un código como `LIDER_ACADEMICO` se tokeniza de forma poco predecible. Los trigramas son la herramienta para contención |
| Índice B-tree funcional con `text_pattern_ops` y búsqueda por prefijo | Es más barato y suficiente para «CONTABILIDAD», pero no encuentra «academico» dentro de `LIDER_ACADEMICO`. Reduciría el requisito de búsqueda a un autocompletado por comienzo, que no es lo que aprobó `spec.md` §4.1 |
| Normalizar el término en Java y compararlo con la columna normalizada en SQL | La normalización de Java y la del diccionario `unaccent` no coinciden carácter por carácter. Cualquier divergencia se manifiesta como un rol indexado que no aparece en su propia búsqueda, y es un fallo que ninguna prueba unitaria detecta porque ambas partes son «correctas» por separado |
| Traer los roles y filtrar o buscar en memoria | Rompe la paginación —hay que leer todo para paginar— y el conteo, y convierte una consulta acotada por índice en una lectura completa de la tabla. Contradice `development-guide.md` §11 |
| Devolver el agregado `Role` y mapearlo con `RoleJpaMapper` | Reutiliza código de `RF-SP-001` a cambio de leer columnas que la respuesta no usa, de exponer la colección `LAZY` de permisos a que alguien la recorra y de dejar el `N+1` del padre a un `JOIN FETCH` que hay que acordarse de escribir. La proyección hace imposible por construcción lo que el mapeo deja a la disciplina |
| Consulta `@Query` con `Sort` de Spring Data | Spring Data concatena al `ORDER BY` la propiedad recibida sin verificarla contra la entidad cuando la consulta es explícita. Es un vector de inyección por un parámetro que el usuario controla, y evitarlo exige de todas formas la lista blanca; con criterios y enum, el nombre inválido no tiene camino hasta el SQL |
| Predicado único con guardas `(:filtro IS NULL OR …)` | Una sola sentencia para todas las combinaciones de filtros obliga al planificador a un plan que sirva a todas y desaprovecha los índices de la combinación concreta. Se escribe en menos líneas y se paga en cada consulta |
| Paginación por cursor en lugar de por número de página | Elimina el conteo y no degrada en páginas altas, pero no puede dar `totalElements` ni `totalPages` ni salto a una página arbitraria, que es exactamente lo que `spec.md` §6.2 exige. Es la alternativa correcta para los listados de auditoría, no para un catálogo de decenas de filas |
| Filtro por rol padre que devuelva todo el subárbol, con un `WITH RECURSIVE` | Ni `spec.md` §6.1 ni §13 piden descendientes: piden filtrar por rol padre. Añadir el recorrido recursivo cambiaría el significado del filtro sin que nadie lo haya pedido |
| Validar que `parentRoleId` existe y devolver error si no | `spec.md` §13 es explícito: un padre inexistente devuelve colección vacía, no error. Validarlo añadiría una consulta por petición para producir un fallo que la especificación no quiere |

## 10. Riesgos

| Riesgo | Impacto | Mitigación |
|---|---|---|
| ~~`CREATE EXTENSION` requiere privilegios que el usuario de la aplicación puede no tener~~ | — | **Trasladado el 21-08-2026 a `RF-SP-010`**, que es quien crea las extensiones en `V1`. El riesgo sigue existiendo y se gestiona allí; aquí solo se hereda la dependencia: si `V1` no llegó a aplicarse, `V8` falla al crear el índice, que es el momento correcto para enterarse |
| `f_unaccent` se declara `IMMUTABLE` sin serlo del todo: si el diccionario `unaccent` cambia, `ix_roles_busqueda` conserva valores obsoletos y devuelve resultados incorrectos **sin error** | Medio | El diccionario se referencia cualificado y no se personaliza (`V1`, `RF-SP-010`). Queda registrado que cualquier cambio en él obliga a `REINDEX` de todos los índices que usen la función, en `roles` y en `countries` |
| Un término de búsqueda de menos de tres caracteres no puede usar el índice de trigramas y degrada a recorrido secuencial | Bajo | Irrelevante en `roles`, cuyo volumen es de decenas. **Debe reconsiderarse en `RF-SP-021`**, donde `countries` tiene cientos de filas y el mismo patrón, y donde puede convenir exigir una longitud mínima al término |
| La búsqueda ignora los acentos pero el ordenamiento no: `Álvarez` y `Alvarez` se encuentran igual y se ordenan según la colación de la base de datos | Bajo | Se acepta. Ordenar por la forma sin acentos exigiría un segundo índice funcional y haría que el orden mostrado no coincidiera con el texto mostrado. Si el negocio lo pide, es un cambio localizado en `RoleSortField` |
| ~~`deletedAt` e `isSystem` exceden los campos de `spec.md` §6.2~~ | — | **Resuelto el 21-08-2026:** confirmados al aprobar este plan. `spec.md` §6.2 los declara como «marca de eliminación» y «marca de rol de sistema» |
| ~~Tensión entre `spec.md` §6.1 y §13 sobre el rol padre inexistente~~ | — | **Resuelto el 21-08-2026:** gana §13. Un `parentRoleId` inexistente devuelve colección vacía y **no se valida**; §6.1 se lee como descripción del dato, no como validación. Evita una consulta por petición cuyo único fin sería producir un error que nadie pidió |
| `totalElements` puede no corresponder exactamente a la página bajo escrituras concurrentes (§7) | Bajo | Se acepta de forma consciente. Elevar el aislamiento a `REPEATABLE READ` en todo listado cuesta más que el desfase que evita |
| ~~El listado devuelve `description` sin límite declarado, para hasta cien filas~~ | — | **Resuelto el 21-08-2026:** `description` queda acotada a 500 caracteres en `requirements/sp.md` §10.2 y en el esquema (`ck_roles_description_length`, `V5`). Una página de cien roles tiene ahora un tamaño máximo predecible, sin tocar este endpoint |
| Un listado posterior copia esta estrategia de conteo sobre una tabla que crece sin límite | Medio | Anotado en §4 y en §8. La revisión de `RF-SP-011` a `RF-SP-014` debe decidir su propia paginación, no heredar esta |

## 11. Estrategia de prueba

Niveles: **Unitaria** (sin Spring ni base de datos), **Integración** (Testcontainers sobre PostgreSQL real, con `V8` aplicada) y **API** (extremo a extremo por HTTP, con autenticación).

| Criterio | Nivel | Qué verifica |
|---|---|---|
| `CA-SP-009` | Integración + API | Con más roles que el tamaño de página, la respuesta trae exactamente `size` elementos, el `totalElements` real, el `totalPages` calculado y la `page` pedida; la segunda página no repite ni omite ninguna fila |
| `CA-SP-010` | Integración + API | Con un rol de `deleted_at` no nulo, la consulta sin parámetros no lo devuelve y no lo cuenta en `totalElements` |
| `CA-SP-011` | Integración + API | Con `includeDeleted=true`, ese mismo rol aparece, con `deletedAt` no nulo, y el total crece en consecuencia |
| `CA-SP-012` | Integración + API | Cada filtro por separado y los tres combinados devuelven solo las filas que cumplen; el filtro por rol padre devuelve los hijos directos y no los nietos |
| `CA-SP-013` | API | Un filtro sin coincidencias devuelve `200` con `content` vacío, `totalElements` y `totalPages` en cero. Nunca `404` ni `204` |
| `CA-SP-014` | Unitaria + API | `PageRequestFactory` rechaza `size = 101` contra el máximo configurado; el endpoint devuelve `400` con `VAL-002` y **no** una página de cien elementos, que es la forma en que el recorte silencioso se manifestaría |
| `CA-SP-015` | API | Un actor autenticado sin `roles:read` recibe `403`, no obtiene dato alguno del catálogo y queda el evento de denegación en `audit_security_log` |
| `CA-SP-147` | Integración + API | Con `Administración` y `Contabilidad` en la tabla, buscar `administracion`, `ADMINISTRACION` y `contabilidad` encuentra cada uno. Exige PostgreSQL real: `unaccent` no es simulable, y una prueba con base embebida daría un falso positivo o un falso fallo |
| `CA-SP-148` | API + Integración | El cuerpo de cada fila no contiene ningún campo con el número de usuarios, y la traza de sentencias de la petición no incluye ninguna consulta a la tabla de asignación de roles |

Casos límite de `spec.md` §13 y decisiones de este plan que exigen prueba propia (Art. VII.3):

| Caso | Nivel | Qué verifica |
|---|---|---|
| Página más allá de la última | API | `200` con `content` vacío y `totalElements` intacto, no `404` |
| Búsqueda con `%`, `_` y `\` | Integración | Se tratan como texto literal: `100%` no devuelve el catálogo entero, y un término con `_` no coincide con cualquier carácter |
| Búsqueda vacía o solo espacios | API | Equivale a no filtrar: mismo resultado que la consulta sin el parámetro |
| Rol padre inexistente | API | Colección vacía y `200`; no se consulta la existencia del padre |
| Rol raíz | Integración | El rol sin padre aparece en el listado con `parentRole` nulo. Es la prueba que detecta un `JOIN` interno donde debe haber un `LEFT JOIN` |
| Campo de ordenamiento arbitrario | API | `sort=deleted_at,asc`, `sort=(select 1),asc` y `sort=permissions.code,asc` devuelven `400` con `VAL-003`; ninguno llega a la base de datos |
| Ordenamiento por un campo con valores repetidos | Integración | Recorrer todas las páginas ordenando por `status` devuelve cada rol exactamente una vez: verifica el desempate por `id` |
| Número de sentencias por petición | Integración | Una página de veinte roles con padre ejecuta **dos** sentencias como máximo —datos y conteo—, nunca veintiuna. Es la única forma de que el `N+1` no vuelva en una refactorización posterior |
| Uso efectivo del índice | Integración | El `EXPLAIN` de la consulta con término de búsqueda muestra el recorrido de `ix_roles_busqueda`. Sin esta comprobación, el índice podría dejar de usarse por un cambio en el predicado sin que ninguna prueba funcional lo note |

Las pruebas de ArchUnit introducidas en `RF-SP-001` cubren también este requerimiento: `api` no accede a `infrastructure` y `domain` no importa Spring ni JPA. No se añaden reglas nuevas, porque este requerimiento no toca `domain`.
