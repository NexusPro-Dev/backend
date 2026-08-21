# PLAN — `RF-SP-017` Consultar membresías

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-017` |
| Especificación | [`spec.md`](spec.md) |
| `spec.md` aprobada el | 21-08-2026 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable técnico |
| Fecha de aprobación | 21-08-2026 |

!!! info "Qué va en este documento"

    **Cómo se construye.** Las decisiones técnicas que la especificación deliberadamente no toma.

    **Prueba de pertenencia:** si al negocio no le importa ni lo entendería, va aquí.

El comportamiento es el de [`spec.md`](spec.md) y no se repite aquí. Este documento decide lo único que la especificación deja abierto y que no es evidente: **cómo se devuelve un orden sin recorrer la cadena**, y por qué esa elección es la que hace que una incoherencia de datos no rompa el listado.

---

## 1. Enfoque

Una sentencia de lectura sobre una proyección, sin `JOIN`, sin paginar y sin reglas de negocio. Estructuralmente es `RF-SP-010`: catálogo pequeño, colección completa envuelta en `content`, búsqueda opcional insensible a mayúsculas y acentos, y ninguna participación de `domain`.

La única decisión de fondo es cómo se produce el orden que `spec.md` §2 declara «la información» de este listado:

> **Se ordena por `level`, no se recorre `parent_membership_id`.**

Suena obvio y no lo es: la cadena está representada dos veces —el enlace y el número— y elegir cuál manda tiene consecuencias distintas. Recorrer el enlace con una consulta recursiva sería el orden «verdadero» y produciría un listado que **se detiene en el primer eslabón roto**; ordenar por el número materializado devuelve siempre la colección completa, que es exactamente lo que pide el segundo caso límite de `spec.md` §13: «una membresía huérfana no debe romper el listado».

Que ese número sea fiable lo garantizan `uq_memberships_level` y `uq_memberships_parent`, declaradas en `V13` por el plan de [`RF-SP-016`](../016-registrar-membresia/plan.md) §2, más la prueba de coherencia entre ambas representaciones que allí queda establecida. Este requerimiento **lee** esa garantía; no la construye.

## 2. Cambios de esquema

**Ninguno.** La tabla `memberships` y sus seis restricciones las crea `V13__create_memberships.sql` (`RF-SP-016`), y el permiso `memberships:read` sale de `V3__seed_permissions.sql` (`RF-SP-010`).

**No se crea índice de búsqueda**, y la ausencia es deliberada. `requirements/sp.md` §10.7 declara índices de trigramas para `roles` y para `countries`, y **no para `memberships`**; este plan confirma esa omisión en lugar de corregirla. El motivo es el de `RF-SP-010` §2: la cadena tiene unos pocos elementos (`spec.md` §6.1) y se devuelve entera, de modo que un recorrido secuencial sobre cinco filas es más rápido que consultar cualquier índice. Es también la diferencia con `countries`, que crece por API sin techo declarado y cuya búsqueda sí recibe índice en `RF-SP-021`: aquí la cadena crece solo cuando el negocio define un nivel nuevo, que es un acontecimiento, no una operación.

**No se crea índice de ordenamiento sobre `level`.** `uq_memberships_level` ya es un índice único B-tree sobre esa columna, y sirve al `ORDER BY` sin coste adicional. Declarar otro sería mantener dos estructuras para lo mismo.

## 3. Componentes afectados

Paquete raíz: `com.factech.nexus.modules.system`. Reglas de dependencia de `architecture.md` §5.2.

| Capa | Componente | Nuevo / Modificado | Responsabilidad |
|---|---|---|---|
| `domain` | — | — | Sin participación: `spec.md` §5 solo referencia `RN-SP-006`, que este requerimiento **lee** y no verifica |
| `application` | `ListMembershipsService` | Nuevo | Caso de uso. `@Transactional(readOnly = true)`. Traduce la consulta al puerto y devuelve la colección |
| `application` | `ListMembershipsQuery` | Nuevo | Un solo criterio ya normalizado: el término de búsqueda recortado |
| `application` | `MembershipItem` | Nuevo | Modelo de lectura. Lo reutiliza `RF-SP-018` para los vecinos |
| `application` | `MembershipQueryRepository` | Nuevo | Puerto de consulta. `RF-SP-018` le añadirá su método |
| `infrastructure` | `JpaMembershipQueryRepository` | Nuevo | Adaptador. Predicado de búsqueda y proyección con la API de criterios |
| `infrastructure` | `MembershipEntity` | Sin cambios | Mapeo JPA de `RF-SP-016`. Se usa como metamodelo; la consulta no lo instancia |
| `api` | `MembershipController` | Modificado | Añade `GET /api/v1/memberships` |
| `api` | `ListMembershipsRequest` | Nuevo | Un parámetro de consulta. Sin Bean Validation: `spec.md` §11 no declara ninguna validación |
| `api` | `MembershipResponse` | Sin cambios | DTO definido en `RF-SP-016`. Se reutiliza tal cual (§4) |
| `shared/api` | `PageResponse<T>` | Sin cambios | **No se usa**: esta colección no se pagina (§4) |

Dos decisiones de reparto:

**`MembershipQueryRepository` es un puerto distinto de `MembershipRepository`.** El segundo lo creó `RF-SP-016` para cargar y guardar el agregado, con `loadChainForUpdate` y `save`; este devuelve modelos de lectura. Es el criterio con el que `RF-SP-002` separó `RoleQueryRepository` de `RoleRepository` y `RF-SP-010` dejó `PermissionCatalog` aparte: lo que devuelve un modelo de lectura no comparte puerto con lo que devuelve el agregado.

**`MembershipItem` es un tipo nuevo aunque se parezca al agregado.** No se proyecta sobre `Membership`, que vive en `domain` y no debe cruzar hacia `api`; y no se reutiliza `MembershipEntity`, porque cargar entidades traería la relación al padre y con ella la posibilidad de un `N+1` que este listado no puede permitirse.

## 4. Contrato de API

| Método | Ruta | Descripción |
|---|---|---|
| `GET` | `/api/v1/memberships` | Cadena completa de membresías, del nivel superior al inferior |

**Petición**

```
GET /api/v1/memberships?search=plata
```

| Parámetro | Tipo | Por defecto | Notas |
|---|---|---|---|
| `search` | texto | — | Sobre código y nombre. Recortado; en blanco equivale a ausente |

- **No hay `page`, `size` ni `sort`.** `spec.md` §6.1 y §14 lo deciden de forma explícita, y no aceptarlos siquiera es lo que lo hace verificable: los parámetros de consulta desconocidos se ignoran en silencio por defecto en Spring, de modo que la garantía no puede venir de `FAIL_ON_UNKNOWN_PROPERTIES`. El DTO de entrada declara **un** campo y la respuesta **no** se envuelve en `PageResponse`; un cliente que envíe `?page=2` recibe la cadena entera, que es la respuesta correcta a una petición que pide algo que este recurso no ofrece. Es el mismo mecanismo de `RF-SP-010` §4.
- **No se admite ordenamiento arbitrario**, y aquí el motivo es más fuerte que en el catálogo de permisos: el orden **es** la información (`spec.md` §2). Ofrecer `sort=name,asc` produciría una lista alfabética de niveles, que es un artefacto sin significado.

**Respuesta `200`**

```json
{
  "content": [
    {
      "id": "018f3a2b-7c41-7000-9a3d-1f2e5b8c9d10",
      "code": "ORO",
      "name": "Oro",
      "description": "Acceso a todo el catálogo de cursos.",
      "level": 1,
      "parentMembershipId": null,
      "childMembershipId": "018f3a2b-7c41-7000-9a3d-1f2e5b8c9d30"
    },
    {
      "id": "018f3a2b-7c41-7000-9a3d-1f2e5b8c9d30",
      "code": "PLATA",
      "name": "Plata",
      "description": "Acceso a los cursos de nivel intermedio.",
      "level": 2,
      "parentMembershipId": "018f3a2b-7c41-7000-9a3d-1f2e5b8c9d10",
      "childMembershipId": "018f3a2b-7c41-7000-9a3d-1f2e5b8c9d20"
    }
  ]
}
```

- **La colección va envuelta en `content`, no como arreglo desnudo**, por lo dicho en `RF-SP-010` §4: un `[...]` en la raíz cierra la puerta a añadir después cualquier metadato sin romper a todos los clientes, y el nombre `content` hace que la forma de leer la lista sea idéntica a la de los endpoints paginados, faltando solo lo que aquí no existe.
- **No se reutiliza `PageResponse<T>` con valores de adorno.** Rellenar `totalPages: 1` diría que hay paginación, y `CA-SP-120` exige que no la haya.
- **Se devuelven los dos vecinos, no solo la superior.** `childMembershipId` es redundante con la cadena —se deduce mirando quién apunta a quién— pero el cliente no debería tener que reconstruirlo: viene gratis, se resuelve con la misma sentencia (abajo) y evita que cada consumidor implemente ese cruce a su manera. `RF-SP-018` devuelve lo mismo expandido.
- **`level` se devuelve tal como está almacenado** y significa distancia hasta la cima: `1` es la superior (`RF-SP-016` §2). `CA-SP-121` lo exige, y el orden de la colección lo refleja.
- **`description` puede venir vacía** y se devuelve como `null`, nunca omitida.
- **No se devuelve cuántas personas tienen cada membresía** (`spec.md` §14, pregunta 2). No hay `JOIN` a `user_memberships` ni subconsulta correlacionada en la sentencia, que es lo único que lo hace verificable (§11). Es la asimetría deliberada con `RF-SP-003`, donde el conteo de usuarios **sí** se aceptó porque decidía si el rol podía eliminarse; una membresía ni se elimina ni se desactiva (`RN-SP-008`), de modo que el número no condiciona ninguna decisión tomable desde aquí.
- **No se devuelven `createdAt` ni `updatedAt`.** `spec.md` §6.2 no los pide y son ruido en un listado cuyo eje es la posición. `updatedAt` diría además algo confuso: cambia cuando **otra** membresía se insertó por encima, no cuando esta cambió.

**Errores**

| Código | Cuándo | `error_code` |
|---|---|---|
| `401` | Token ausente o inválido | `AUTH-001` |
| `403` | Autenticado sin `memberships:read` | `AUTH-002` |
| `500` | Fallo no controlado | `ERR-500` |

**No hay `400`, `404` ni `422`.** `spec.md` §10 y §11 no declaran ninguna excepción ni validación: el único parámetro es opcional y cualquier texto es admisible. Una búsqueda sin coincidencias devuelve `200` con `content` vacío, y una cadena todavía sin membresías también (`FA-001`, `CA-SP-122`). Los `type` que este endpoint usa ya los estrenó `RF-SP-001`.

**Cuántas consultas cuesta.** Una:

```sql
SELECT m.id, m.code, m.name, m.description, m.level,
       m.parent_membership_id,
       (SELECT h.id FROM memberships h WHERE h.parent_membership_id = m.id)
  FROM memberships m
 -- El bloque siguiente solo se añade si hay término; sin él, la sentencia no lleva WHERE
 WHERE (f_unaccent(lower(m.code)) LIKE f_unaccent(lower(:termino)) ESCAPE '\'
     OR f_unaccent(lower(m.name)) LIKE f_unaccent(lower(:termino)) ESCAPE '\')
 ORDER BY m.level;
```

**El predicado de búsqueda se añade o no se añade; no se neutraliza con una guarda.** Escribirlo como `WHERE :termino IS NULL OR …` produciría una sola sentencia para los dos casos y obligaría al planificador a un plan que sirva a ambos, que es exactamente lo que `RF-SP-002` §9 descartó. Sobre cinco filas la diferencia de coste es nula, pero el criterio es el mismo en todo el módulo y la ilustración no debe sugerir lo contrario.

- **La hija se resuelve con una subconsulta correlacionada**, no con un segundo `JOIN` a la misma tabla. Devuelve a lo sumo una fila **porque `uq_memberships_parent` lo garantiza** (`RF-SP-016` §2); sin esa restricción, la subconsulta podría devolver varias y la sentencia fallaría en tiempo de ejecución. Es un caso poco habitual en que una restricción de integridad es lo que hace legal una consulta.
- **`ORDER BY m.level` y nunca `ORDER BY m.name`.** Sin `ORDER BY` explícito PostgreSQL no garantiza orden alguno, y aquí el orden es el contenido.
- **No hay `N+1` posible**: no se carga `MembershipEntity`, se materializa `MembershipItem` con `cb.construct`, de modo que no hay asociación perezosa que un mapeador, un `toString` o la serialización pudieran recorrer.
- **No hay conteo**, porque no hay paginación.

**Cómo se aplica la búsqueda.** El término se recorta; si queda vacío, no se añade predicado. Si no, se escapan `\`, `%` y `_`, y se envía como parámetro enlazado, envuelto en comodines de contención, con `ESCAPE` explícito. La normalización la hace **la base de datos con `f_unaccent`** —creada en `V1__create_shared_functions.sql` por `RF-SP-010`—, nunca `java.text.Normalizer`, cuyo resultado es parecido y no idéntico al del diccionario `unaccent`. Todo eso es heredado de `RF-SP-002` §4 y `RF-SP-010` §4 y no se vuelve a argumentar.

**Lo propio: aquí no hace falta `coalesce`.** `RF-SP-010` §4 tuvo que envolver `description` porque es nulable y `NULL LIKE …` es `NULL`, de modo que un permiso sin descripción desaparecía de toda búsqueda. Aquí se busca sobre `code` y `name`, ambos `NOT NULL`: el envoltorio sería inerte y sugeriría un problema que no existe. **La descripción no es campo de búsqueda** —`spec.md` §6.1 dice «sobre código y nombre»—, y por eso no hay nulos en juego.

**Qué devuelve una búsqueda sobre una cadena.** Membresías sueltas, no un tramo contiguo (`spec.md` §13). No se rellenan los huecos ni se devuelve marca alguna de discontinuidad: el `level` de cada elemento ya dice qué posición ocupa, y saltos en esa secuencia son la señal de que el resultado está filtrado. Añadir un indicador explícito sería resolver en el contrato lo que el propio dato ya dice.

## 5. Autorización

| Endpoint | Permiso requerido |
|---|---|
| `GET /api/v1/memberships` | `memberships:read` |

- El permiso **ya existe**: lo crea `V3__seed_permissions.sql` (`RF-SP-010`).
- Se declara sobre el método del controlador (`security.md` §6). Un endpoint sin declaración queda inaccesible, no público (Art. IV.1).
- **Es el mismo permiso que `RF-SP-018`** y distinto de `memberships:create`, por lo dicho en `RF-SP-016` §5: consultar la cadena es apoyo, insertarla es irreversible.
- **No hay filtrado por alcance de datos.** La cadena es única y global (`spec.md` §14, pregunta 4): no hay nada que acotar por actor.
- La resolución del permiso puede usar la caché de `security.md` §4.5: aquí solo se decide acceso.
- El `403` lo produce la capa de seguridad antes de entrar al caso de uso, y es ella quien emite el evento de seguridad (§6). `CA-SP-124` se satisface ahí.

## 6. Auditoría

| Operación | Registro | Contenido relevante |
|---|---|---|
| Consulta exitosa | — | **No se audita** |
| Denegación `403` | `audit_security_log` | `event_type = 'AUTHORIZATION_DENIED'`, `severity = 'MEDIA'`, `outcome = 'FAILURE'`. Lo emite la capa de seguridad |
| Fallo no controlado `5xx` | `audit_error_log` | `resource = 'memberships'`, `operation = 'GET /api/v1/memberships'`, `error_code = 'ERR-500'`, `error_type = 'UNHANDLED'`, `severity = 'ALTA'` |
| — | `audit_change_log` | No aplica: la consulta no altera el estado (`spec.md` §7) |
| — | `audit_deletion_log` | No aplica |

Una consulta exitosa no produce evento de seguridad: el catálogo de `security.md` §8.1 es cerrado y no incluye la lectura de membresías, y el rastro de quién consultó qué lo aporta `request_log`. Misma conclusión de `RF-SP-002` §6, `RF-SP-003` §6 y `RF-SP-010` §6, y la contraria a la de `RF-SP-014`.

## 7. Transaccionalidad

| Elemento | Transacción |
|---|---|
| La consulta | **Una sola**, `@Transactional(readOnly = true)` sobre `ListMembershipsService` (`development-guide.md` §10) |
| `audit_error_log` de un fallo no controlado | **Independiente**, `REQUIRES_NEW` (Art. V.14) |
| `audit_security_log` de la denegación `403` | **Independiente**, `REQUIRES_NEW`. La emite la capa de seguridad |
| `request_log` | Ninguna: posterior a la respuesta, *best effort* |

`readOnly = true` marca la transacción como de solo lectura en PostgreSQL, de modo que ningún defecto pueda escribir desde un camino de consulta.

**Una sola sentencia toma una sola instantánea**, y aquí eso importa más de lo habitual: un listado que leyera la cadena en dos sentencias podría atrapar un reordenamiento a medias y devolver dos membresías con el mismo nivel, o un hueco. Con una sola, la cadena que se devuelve es siempre una que existió: la de antes o la de después del alta concurrente, nunca una intermedia.

## 8. Impacto sobre otros módulos

| Módulo | Impacto |
|---|---|
| `RF-SP-016` | Comparte `MembershipController` y `MembershipResponse`. **Depende de sus restricciones**: `uq_memberships_parent` es lo que hace legal la subconsulta de la hija, y `uq_memberships_level` lo que hace fiable el orden. Ningún cambio en su contrato |
| `RF-SP-018` | Reutiliza `MembershipQueryRepository`, `MembershipItem` y `MembershipResponse`, y expande los vecinos que este listado devuelve como identificadores. `spec.md` §14, pregunta 1, de aquel requerimiento se apoya en que este devuelve la cadena entera en una sola llamada |
| `RF-SP-032` | Asignar una membresía a un usuario necesita elegirla de esta lista. El identificador es lo que se guarda; el `level` no (`RF-SP-016` §8) |
| `architecture.md` | §7.4 exige paginar «las colecciones», y este endpoint se aparta de forma consciente, como ya hicieron `RF-SP-003` §4 con los permisos de un rol y `RF-SP-010` §4 con el catálogo. La excepción está resuelta en `spec.md` §14, pregunta 1, y no se propone enmendar el documento: la regla general sigue siendo correcta |
| Academia y productos | Esta es la consulta con la que un módulo de contenidos resuelve qué niveles existen. La obligación de referenciar por `id` y no por número está declarada en `RF-SP-016` §8 |
| `shared/api` | Ninguno. `PageResponse<T>` no se usa |

## 9. Alternativas consideradas

| Alternativa | Por qué se descartó |
|---|---|
| Recorrer la cadena con `WITH RECURSIVE` desde la superior | Sería el orden «verdadero» y **se detiene en el primer eslabón roto**, que es exactamente lo que el segundo caso límite de `spec.md` §13 prohíbe. Además cuesta más y depende de que exista una superior, que en una tabla vacía no existe |
| Paginar la cadena | `spec.md` §6.1 y §14 lo resolvieron: aquí la información *es* el orden, y partirlo entre páginas destruye lo que se viene a consultar. Son unos pocos elementos |
| Devolver la colección como arreglo desnudo en la raíz | Impide añadir después cualquier metadato sin romper a todos los clientes, y obliga a leer este endpoint distinto de los paginados. Mismo criterio de `RF-SP-010` §4 |
| Reutilizar `PageResponse<T>` con `totalPages: 1` | Diría que hay paginación donde no la hay, y `CA-SP-120` exige lo contrario |
| Ofrecer `sort` con lista blanca, como `RF-SP-002` | El orden es el contenido de este recurso. Una lista alfabética de niveles es un artefacto sin significado |
| Devolver la cadena anidada, cada membresía conteniendo a su hija | Convierte una lista en un árbol de profundidad *n* para representar algo que es lineal, y obliga a todo cliente a desanidar antes de pintar. La colección plana ordenada dice lo mismo y se recorre una vez |
| Devolver solo `parentMembershipId` y que el cliente deduzca la hija | Cada consumidor implementaría ese cruce a su manera. La subconsulta viene gratis y `uq_memberships_parent` garantiza que devuelve a lo sumo una fila |
| Devolver cuántas personas tienen cada membresía | `spec.md` §14, pregunta 2: una membresía ni se elimina ni se desactiva, de modo que el conteo no condiciona ninguna decisión tomable desde aquí. Es la asimetría deliberada con `RF-SP-003` |
| Buscar también sobre la descripción | `spec.md` §6.1 dice «sobre código y nombre». Añadirla obligaría además al `coalesce` que `RF-SP-010` §4 necesitó, para acelerar una búsqueda sobre cinco filas |
| Índice de trigramas sobre `memberships`, como en `roles` y `countries` | Mantener una estructura para acelerar el recorrido de unas pocas filas. `requirements/sp.md` §10.7 no lo declara, y esta es la confirmación de esa omisión |
| Marcar en la respuesta que un resultado filtrado no es contiguo | El `level` de cada elemento ya lo dice: un salto en la secuencia es la señal. Un indicador explícito resolvería en el contrato lo que el dato ya resuelve |
| Devolver `createdAt` y `updatedAt` | `spec.md` §6.2 no los pide, y `updatedAt` diría algo confuso: cambia cuando **otra** membresía se insertó por encima, no cuando esta cambió |

## 10. Riesgos

| Riesgo | Impacto | Mitigación |
|---|---|---|
| Los datos quedan incoherentes —`level` contradice la cadena— y el listado lo muestra como si fuera correcto | Medio | Es el precio de ordenar por el número materializado, y se acepta a cambio de que una incoherencia no rompa el listado (`spec.md` §13). La detección no vive aquí: `uq_memberships_level` y `uq_memberships_parent` impiden los dos síntomas más graves, y la prueba de coherencia de `RF-SP-016` §11 recorre ambas representaciones y las compara. **Este endpoint no la detecta ni debe intentarlo**: hacerlo obligaría a recorrer la cadena en cada consulta, que es lo que se descartó |
| Una membresía huérfana desaparece del listado | Bajo | No puede ocurrir con esta implementación: el predicado no toca `parent_membership_id`. Es el motivo de la decisión de §1, y tiene prueba propia en §11 |
| La subconsulta de la hija devuelve varias filas y la sentencia falla en ejecución | Bajo | Imposible mientras exista `uq_memberships_parent`. Si alguna vez se retirara esa restricción, este endpoint dejaría de funcionar, y por eso la dependencia se declara en §8 en lugar de defenderse con un `LIMIT 1` que ocultaría el problema |
| La cadena crece hasta un tamaño en que devolverla entera deja de ser razonable | Bajo | Los niveles de membresía son unos pocos por naturaleza del negocio, y el `CHECK` de 500 caracteres sobre `description` acota el peor caso. Si creciera en un orden de magnitud, la decisión de no paginar habría que revisarla en la especificación, no aquí |
| Un alta concurrente produce un listado con la cadena a medias | Bajo | Una sola sentencia, una sola instantánea (§7). Lo que se devuelve es siempre un estado que existió |

## 11. Estrategia de prueba

Niveles: **Integración** (Testcontainers sobre PostgreSQL real, con `V13` aplicada) y **API** (extremo a extremo por HTTP, con autenticación). No hay nivel unitario: este requerimiento no tiene `domain`.

| Criterio | Nivel | Qué verifica |
|---|---|---|
| `CA-SP-120` | Integración + API | Con una cadena de cuatro, la respuesta trae los cuatro elementos en orden de `level` ascendente, y el cuerpo **no** contiene `page`, `size`, `totalElements` ni `totalPages` |
| `CA-SP-121` | Integración + API | Cada elemento trae su `level`, y el primero de la colección es el que tiene `parentMembershipId` nulo |
| `CA-SP-122` | API | Sobre una tabla vacía devuelve `200` con `content` vacío. Nunca `404` ni `204` |
| `CA-SP-123` | Integración + API | Se lista, se inserta una membresía intermedia con `RF-SP-016` y se vuelve a listar: el orden y los niveles reflejan el reordenamiento, y la nueva aparece entre las dos correctas |
| `CA-SP-124` | API | Un actor autenticado sin `memberships:read` recibe `403`, no obtiene dato alguno y queda el evento de denegación en `audit_security_log` |

Casos límite de `spec.md` §13 y decisiones de este plan que exigen prueba propia (Art. VII.3):

| Caso | Nivel | Qué verifica |
|---|---|---|
| Una sola membresía | API | Se devuelve con `parentMembershipId` y `childMembershipId` **ambos nulos**, con los campos presentes y no omitidos |
| Cadena rota por un fallo de datos | Integración | Con una membresía cuyo `parent_membership_id` apunta a otra que no la reconoce —forzado por `UPDATE` directo—, el listado **sigue devolviendo todas las filas** en orden de `level`. Es la prueba que verifica la decisión de §1 |
| Búsqueda sobre una cadena | API | Filtrar por un término que coincide con la primera y la tercera devuelve dos elementos con `level` `1` y `3`: el resultado no es contiguo y no se rellena |
| Búsqueda insensible a acentos y mayúsculas | Integración | Buscar `platino`, `PLATINO` y `Platíno` encuentra la misma membresía. Exige PostgreSQL real: `unaccent` no es simulable |
| Búsqueda con `%`, `_` y `\` | Integración | Se tratan como texto literal: un término con `%` no devuelve la cadena entera |
| Búsqueda vacía o solo de espacios | API | Equivale a no filtrar: mismo resultado que la consulta sin el parámetro |
| Parámetros de paginación ignorados | API | `?page=2&size=1` devuelve la cadena completa, no un elemento ni un error |
| Orden estable | Integración | Dos llamadas consecutivas devuelven los mismos elementos en el mismo orden |
| Número de sentencias por petición | Integración | **Una**, con y sin búsqueda, y **ninguna sobre `user_memberships`**. Es lo que hace verificable que el listado no cuenta personas |
| Coherencia con el detalle | Integración | Los vecinos que devuelve cada elemento coinciden con los que `RF-SP-018` expande para esa misma membresía |
| Ausencia de escritura | API | `POST` sobre `/api/v1/memberships` es el alta de `RF-SP-016` y exige otro permiso; `PUT`, `PATCH` y `DELETE` sobre la colección devuelven `405` |

Las reglas de ArchUnit introducidas en `RF-SP-001` y `RF-SP-003` cubren también este requerimiento. No se añade ninguna nueva: no toca `domain` y no introduce dependencias entre módulos.
