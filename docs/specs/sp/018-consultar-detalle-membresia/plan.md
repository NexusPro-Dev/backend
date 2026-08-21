# PLAN — `RF-SP-018` Consultar detalle de una membresía

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-018` |
| Especificación | [`spec.md`](spec.md) |
| `spec.md` aprobada el | 21-08-2026 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable técnico |
| Fecha de aprobación | 21-08-2026 |

!!! info "Qué va en este documento"

    **Cómo se construye.** Las decisiones técnicas que la especificación deliberadamente no toma.

    **Prueba de pertenencia:** si al negocio no le importa ni lo entendería, va aquí.

El comportamiento es el de [`spec.md`](spec.md) y no se repite aquí. Este documento decide cómo se traen los dos vecinos en una sola sentencia y por qué eso es posible, que es lo único que distingue a esta consulta de un detalle corriente por clave primaria.

---

## 1. Enfoque

Una sentencia de lectura por clave primaria con dos vecinos resueltos en la misma consulta, sin `domain`, sin conteos y sin colecciones. Estructuralmente es `RF-SP-003` sin lo que allí lo hacía costoso: no hay una segunda sentencia para una colección, no hay subconsultas de conteo y no hay borrado lógico que filtrar, porque `memberships` no lo tiene (`RN-SP-008`).

Lo propio es cómo se obtiene cada vecino, y son dos mecanismos distintos:

- **La superior** se resuelve con un `LEFT JOIN` por `parent_membership_id`, que es una clave foránea: a lo sumo una fila por definición.
- **La hija** se resuelve buscando quién apunta a esta membresía. Eso normalmente devolvería una colección, y aquí devuelve un objeto **porque `uq_memberships_parent` lo garantiza** (`RF-SP-016` §2). Es la misma dependencia que `RF-SP-017` §4 declara: una restricción de integridad es lo que hace legal la forma de la consulta y del contrato.

Ambas relaciones se traen en la **misma** sentencia, y eso no contradice la regla de `RF-SP-003` §4 —«dos colecciones nunca se traen en la misma consulta»—: aquí no hay ninguna colección, hay dos referencias a lo sumo unitarias, de modo que no existe producto cartesiano posible.

## 2. Cambios de esquema

**Ninguno.** La tabla `memberships` y sus restricciones las crea `V13__create_memberships.sql` (`RF-SP-016`), y el permiso `memberships:read` sale de `V3__seed_permissions.sql` (`RF-SP-010`).

Tampoco se añade índice: el acceso principal es por clave primaria, y la búsqueda de la hija por `parent_membership_id` la resuelve `uq_memberships_parent`, que es un índice único B-tree sobre esa columna. Ese es un efecto secundario útil de la restricción: la relación inversa queda indexada sin declarar nada más.

## 3. Componentes afectados

Paquete raíz: `com.factech.nexus.modules.system`. Reglas de dependencia de `architecture.md` §5.2.

| Capa | Componente | Nuevo / Modificado | Responsabilidad |
|---|---|---|---|
| `domain` | — | — | Sin participación: `spec.md` §5 solo referencia `RN-SP-006`, que este requerimiento **lee** y no verifica |
| `application` | `GetMembershipService` | Nuevo | Caso de uso. `@Transactional(readOnly = true)`. Recupera el detalle o lanza la excepción de no encontrado |
| `application` | `MembershipDetail` | Nuevo | Modelo de lectura: la membresía más sus dos vecinos ya resueltos |
| `application` | `MembershipItem` | Sin cambios | Modelo de lectura de `RF-SP-017`. **Es el tipo de cada vecino** (§4) |
| `application` | `MembershipQueryRepository` | **Modificado** | Puerto de `RF-SP-017`. Gana `findDetailById(UUID): Optional<MembershipDetail>` |
| `infrastructure` | `JpaMembershipQueryRepository` | Modificado | Implementa el método nuevo con la sentencia de §4 |
| `api` | `MembershipController` | Modificado | Añade `GET /api/v1/memberships/{id}` con el mismo permiso |
| `api` | `MembershipDetailResponse` | Nuevo | DTO de salida: la membresía con sus campos propios y sus dos vecinos como `MembershipSummaryResponse` |
| `api` | `MembershipSummaryResponse` | Nuevo | DTO reducido de un vecino: `id`, `code`, `name` y `level` (§4) |
| `api` | `MembershipResponse` | Sin cambios | DTO de `RF-SP-016`. **No se reutiliza aquí** (§4) |
| `shared/api` | `CanonicalUuidConverter` | Sin cambios | Creado en `RF-SP-003`. Convierte el identificador de la ruta o falla con `400` |

Dos decisiones de reparto:

**`MembershipDetail` existe y `MembershipItem` no basta.** El listado devuelve identificadores de vecinos; el detalle los devuelve expandidos. Ampliar `MembershipItem` con dos objetos anidados haría que `RF-SP-017` los devolviera también, y eso sí sería un cambio en su contrato: la cadena entera pasaría a traer cada membresía repetida hasta tres veces —como elemento, como superior de la siguiente y como hija de la anterior—, triplicando la respuesta sin añadir un solo dato. Es la diferencia con `PermissionResponse`, que `RF-SP-010` §3 sí amplió: allí eran tres campos escalares, aquí es anidamiento redundante.

**Los vecinos se devuelven como `MembershipSummaryResponse`, un tipo reducido, y no como el `MembershipResponse` de `RF-SP-016`.** El borrador de este plan decía lo contrario y se corrigió el 21-08-2026 al aprobarlo, porque prometía una reutilización que el contrato de §4 no hacía: `MembershipResponse` lleva nueve campos —entre ellos `parentMembershipId`, `childMembershipId`, `createdAt` y `updatedAt`—, de modo que cada vecino habría traído a su vez sus propios vecinos y unas marcas temporales que este detalle decide no devolver ni siquiera en su objeto principal. Es exactamente el criterio con el que `RF-SP-001` §4 creó `RoleSummaryResponse`: lo que se expande como vecino se recorta a lo que identifica y sitúa.

El tipo reducido lleva `id`, `code`, `name` y `level`. `description` queda fuera porque el vecino se muestra para situarse, no para leerlo; quien lo necesite consulta su detalle o lo tiene ya del listado de `RF-SP-017`, que sí la devuelve.

## 4. Contrato de API

| Método | Ruta | Descripción |
|---|---|---|
| `GET` | `/api/v1/memberships/{id}` | Detalle de una membresía con sus dos vecinos inmediatos |

**Petición**

```
GET /api/v1/memberships/018f3a2b-7c41-7000-9a3d-1f2e5b8c9d30
```

Sin cuerpo y sin parámetros de consulta. No hay `?include=…` ni forma de pedir un subconjunto: la especificación define un único detalle, y ofrecer variantes multiplicaría los contratos que hay que probar (`RF-SP-003` §4).

**Respuesta `200`**

```json
{
  "id": "018f3a2b-7c41-7000-9a3d-1f2e5b8c9d30",
  "code": "PLATA",
  "name": "Plata",
  "description": "Acceso a los cursos de nivel intermedio.",
  "level": 2,
  "parentMembership": {
    "id": "018f3a2b-7c41-7000-9a3d-1f2e5b8c9d10",
    "code": "ORO",
    "name": "Oro",
    "level": 1
  },
  "childMembership": {
    "id": "018f3a2b-7c41-7000-9a3d-1f2e5b8c9d20",
    "code": "BRONCE",
    "name": "Bronce",
    "level": 3
  }
}
```

- **Los vecinos llegan **solo** hasta el primer grado.** `spec.md` §14, pregunta 1, lo resolvió: la cadena entera la trae `RF-SP-017` en una sola llamada, y este detalle responde la otra pregunta —entre qué dos niveles va a quedar la nueva—. Cada vecino trae sus datos, **no** sus propios vecinos: anidarlos convertiría la respuesta en la cadena completa por un camino distinto, y su profundidad dependería de dónde estuviera la membresía consultada.
- **`parentMembership` es `null` en la superior de la cadena y `childMembership` es `null` en la inferior** (`FA-001`, `FA-002`, `CA-SP-126`, `CA-SP-127`). Se devuelven como `null` sin omitirse: un campo ausente es indistinguible de uno que el cliente no conoce. En la única membresía del sistema ambos son nulos a la vez, y es válido (`spec.md` §13).
- **`level` está en la membresía y en cada vecino**, y es lo que permite leer la posición sin comparar identificadores. Significa distancia hasta la cima: `1` es la superior (`RF-SP-016` §2). En una cadena bien formada, `parentMembership.level` es siempre `level - 1` y `childMembership.level` siempre `level + 1`; devolverlos igualmente es lo que hace la incoherencia visible en lugar de invisible.
- **No se devuelven `createdAt` ni `updatedAt`.** `spec.md` §6.2 no los pide, y `updatedAt` diría algo confuso: cambia cuando **otra** membresía se insertó por encima, no cuando esta cambió. Es la misma decisión que en `RF-SP-015` §4 y `RF-SP-017` §4, y por un motivo emparentado: en una entidad inmutable, las marcas temporales cuentan efectos secundarios, no acciones.
- **No se devuelven las personas que tienen la membresía** (`spec.md` §4.2). No hay `JOIN` a `user_memberships` ni subconsulta correlacionada en la sentencia, que es lo único que lo hace verificable (§11). Se responde con `RF-SP-025`, filtrando por membresía.
- **`description` puede venir vacía** y se devuelve como `null`, nunca omitida.

**Errores**

| Código | Cuándo | `error_code` | Campo en `errors` |
|---|---|---|---|
| `400` | El identificador no es un UUID en forma canónica (`VAL-001`) | `VAL-001` | `id` |
| `401` | Token ausente o inválido | `AUTH-001` | — |
| `403` | Autenticado sin `memberships:read` | `AUTH-002` | — |
| `404` | No existe membresía con ese identificador (`EX-001`) | `EX-001` | — |
| `500` | Fallo no controlado | `ERR-500` | — |

- **`404` y no `422`**, por el criterio de `development-guide.md` §7.1: el recurso **de la ruta** es la membresía. El `422` se reserva para una referencia inexistente en el cuerpo, que es el caso de `RF-SP-016` con su hija indicada.
- **`VAL-002` no produce un código propio.** Enuncia como validación lo mismo que `EX-001`; un solo hecho, un solo código.
- **Un identificador malformado es `400`, no `404`** (`spec.md` §13), y el mecanismo ya está resuelto en `RF-SP-003` §4: `CanonicalUuidConverter` exige los 36 caracteres canónicos antes de delegar en `UUID.fromString`, porque el JDK convierte sin error formas abreviadas como `1-1-1-1-1`. La ruta **no** se declara con restricción de patrón, que produciría `404` por falta de manejador.
- Todos los `type` que usa ya los estrenaron `RF-SP-001` y `RF-SP-003`.

**Cuántas consultas cuesta.** Una:

```sql
SELECT m.id, m.code, m.name, m.description, m.level,
       p.id, p.code, p.name, p.level,
       h.id, h.code, h.name, h.level
  FROM memberships m
  LEFT JOIN memberships p ON p.id = m.parent_membership_id
  LEFT JOIN memberships h ON h.parent_membership_id = m.id
 WHERE m.id = :id;
```

- **Los dos `LEFT JOIN` no multiplican filas.** El primero va contra la clave primaria; el segundo, contra `uq_memberships_parent`, que garantiza a lo sumo una fila. Sin esa restricción, esta sentencia devolvería tantas filas como hijas hubiera y el adaptador tendría que deduplicar en memoria un resultado ya leído multiplicado —el problema que `RF-SP-003` §4 evita separando las colecciones—. Aquí no hace falta separarlas porque no hay colecciones, y esa es toda la diferencia.
- **No se filtra por `deleted_at`.** Esta tabla no lo tiene, y añadirlo sugeriría que sí. Es la asimetría con `RF-SP-003`, donde el `LEFT JOIN` al padre lleva `deleted_at IS NULL` precisamente porque un rol sí se elimina lógicamente.
- **No hay `N+1` posible**: no se carga `MembershipEntity`, se materializa `MembershipDetail` con `cb.construct`, de modo que no hay asociación perezosa que un mapeador, un `toString` o la serialización pudieran recorrer.
- **Si la membresía no existe, la sentencia devuelve cero filas y el caso de uso lanza `ResourceNotFoundException`.** No se ejecuta ninguna consulta previa de existencia: una sentencia que no encuentra nada ya es la respuesta.

## 5. Autorización

| Endpoint | Permiso requerido |
|---|---|
| `GET /api/v1/memberships/{id}` | `memberships:read` |

- El permiso **ya existe**: lo crea `V3__seed_permissions.sql` (`RF-SP-010`).
- **Es el mismo permiso que `RF-SP-017`**, por el criterio de `RF-SP-003` §5: detalle y listado responden la misma pregunta con distinto grano, y exigir un permiso propio obligaría a concederlos siempre juntos.
- Se declara sobre el método del controlador (`security.md` §6). Un endpoint sin declaración queda inaccesible, no público (Art. IV.1).
- **No hay filtrado por alcance de datos.** La cadena es única y global (`spec.md` §14, pregunta 4, de `RF-SP-016`).
- La resolución del permiso puede usar la caché de `security.md` §4.5: aquí solo se decide acceso.
- El `403` lo produce la capa de seguridad antes de entrar al caso de uso, y es ella quien emite el evento de seguridad (§6). `CA-SP-129` se satisface ahí.

## 6. Auditoría

| Operación | Registro | Contenido relevante |
|---|---|---|
| Consulta exitosa | — | **No se audita** |
| Rechazo `400` o `404` | — | **No se audita**: `architecture.md` §6.6.4 deja fuera la validación de formato y el `404`, y `ck_audit_error_log_status` (`RF-SP-013`) lo impide en el esquema |
| Denegación `403` | `audit_security_log` | `event_type = 'AUTHORIZATION_DENIED'`, `severity = 'MEDIA'`, `outcome = 'FAILURE'`. Lo emite la capa de seguridad |
| Fallo no controlado `5xx` | `audit_error_log` | `resource = 'memberships'`, `operation = 'GET /api/v1/memberships/{id}'`, `error_code = 'ERR-500'`, `error_type = 'UNHANDLED'`, `severity = 'ALTA'` |
| — | `audit_change_log` | No aplica: la consulta no altera el estado (`spec.md` §7) |
| — | `audit_deletion_log` | No aplica |

Una consulta exitosa no produce evento de seguridad: el catálogo de `security.md` §8.1 es cerrado y no incluye la lectura de membresías. Misma conclusión de `RF-SP-017` §6.

## 7. Transaccionalidad

| Elemento | Transacción |
|---|---|
| La consulta | **Una sola**, `@Transactional(readOnly = true)` sobre `GetMembershipService` (`development-guide.md` §10) |
| `audit_error_log` de un fallo no controlado | **Independiente**, `REQUIRES_NEW` (Art. V.14) |
| `audit_security_log` de la denegación `403` | **Independiente**, `REQUIRES_NEW`. La emite la capa de seguridad |
| `request_log` | Ninguna: posterior a la respuesta, *best effort* |

`readOnly = true` marca la transacción como de solo lectura en PostgreSQL. Una sola sentencia toma una sola instantánea, de modo que la membresía y sus dos vecinos se leen del mismo estado: no puede ocurrir que el detalle muestre una superior que ya dejó de serlo mientras la hija corresponda al estado nuevo. Es lo que hace cumplible el segundo caso límite de `spec.md` §13 —una membresía recién insertada refleja ya el reordenamiento— incluso bajo un alta concurrente.

## 8. Impacto sobre otros módulos

| Módulo | Impacto |
|---|---|
| `RF-SP-016` | Comparte `MembershipController` y `MembershipResponse`. **Depende de `uq_memberships_parent`**: es lo que garantiza que la hija sea un objeto y no una lista, tanto en la sentencia como en el contrato. Ningún cambio en su contrato |
| `RF-SP-017` | Comparte `MembershipQueryRepository` y `MembershipItem`, que **no se amplían** con los vecinos expandidos (§3): hacerlo triplicaría la respuesta del listado sin añadir un dato. Los identificadores que aquel devuelve son los que este expande |
| `RF-SP-003` | Reutiliza `CanonicalUuidConverter` sin modificarlo. Es su tercer consumidor, junto con `RF-SP-015` |
| `RF-SP-025` | Es donde se responde qué personas tienen una membresía, filtrando por ella. Este detalle no lo hace ni lo hará |
| `shared/api`, `shared/error` | Ninguno. No se añade ningún tipo de excepción: `ResourceNotFoundException` ya existe desde `RF-SP-003` |

## 9. Alternativas consideradas

| Alternativa | Por qué se descartó |
|---|---|
| Devolver la cadena completa desde el detalle | `spec.md` §14, pregunta 1: al resolverse que `RF-SP-017` devuelve la cadena entera sin paginar, pintarla ya no cuesta varias llamadas. Este detalle responde la otra pregunta, y para eso bastan los dos vecinos |
| Anidar los vecinos de los vecinos | Convertiría la respuesta en la cadena completa por un camino distinto, y su profundidad dependería de dónde estuviera la membresía consultada: el mismo endpoint devolvería estructuras de tamaño distinto según el argumento |
| Ampliar `MembershipItem` con los vecinos expandidos, en lugar de crear `MembershipDetail` | `RF-SP-017` los devolvería también, y la cadena entera traería cada membresía repetida hasta tres veces. Es la diferencia con `PermissionResponse`, que `RF-SP-010` §3 sí amplió porque eran tres campos escalares |
| Un `MembershipSummaryResponse` reducido para los vecinos | Obligaría al cliente a distinguir dos formas de lo mismo según dónde aparezcan, para ahorrar un campo de texto. `RF-SP-001` §4 sí lo hizo con los roles, donde el padre arrastra permisos y jerarquía propia |
| Dos sentencias, una para la membresía y otra para los vecinos | Abre una ventana en la que un alta concurrente puede reordenar la cadena entre ambas, y el detalle mostraría una superior del estado anterior con una hija del posterior |
| Traer la hija en sentencia aparte «por si acaso devuelve varias» | Sería asumir que `uq_memberships_parent` puede no existir. Si se retirara, este endpoint dejaría de funcionar, y por eso la dependencia se declara en §8 en lugar de disimularse con un `LIMIT 1` que ocultaría la bifurcación |
| Admitir también el acceso por código | `spec.md` §14, pregunta 2: dos formas de direccionar el mismo recurso obligan a distinguir en cada petición cuál es cuál. El código es la vía para *encontrar* la membresía, mediante la búsqueda de `RF-SP-017` |
| Declarar la ruta con restricción de patrón sobre el identificador | Un identificador malformado no encontraría manejador y Spring respondería `404`, que es el error que `spec.md` §13 prohíbe. Documentado en `RF-SP-003` §4 |
| Devolver cuántas personas tienen la membresía | `spec.md` §4.2 lo excluye, y el motivo es el de `RF-SP-017` §14, pregunta 2: una membresía ni se elimina ni se desactiva, de modo que el conteo no condiciona ninguna decisión tomable desde aquí |
| Devolver `createdAt` y `updatedAt` | `spec.md` §6.2 no los pide, y `updatedAt` cambia cuando otra membresía se insertó por encima, no cuando esta cambió |

## 10. Riesgos

| Riesgo | Impacto | Mitigación |
|---|---|---|
| La subconsulta de la hija devuelve varias filas y la respuesta sale malformada | Bajo | Imposible mientras exista `uq_memberships_parent`, y la dependencia queda declarada en §8. La guarda es la aserción de esquema de §11, que falla si esa restricción desaparece o pierde `NULLS NOT DISTINCT`: es una alarma permanente y no una prueba que rompa la integridad para observar el fallo |
| El detalle muestra un `level` de vecino que no es consecutivo | Bajo | Es un síntoma de incoherencia entre `level` y la cadena, no un defecto de este endpoint. Se devuelve tal cual **a propósito** (§4): mostrarlo es lo que hace visible el problema, y su detección vive en la prueba de coherencia de `RF-SP-016` §11 |
| Un alta concurrente hace que el detalle muestre una posición ya obsoleta | Bajo | Inevitable en cualquier lectura, y acotado: la instantánea única garantiza que lo devuelto es un estado que existió, no una mezcla de dos |
| El cliente supone que `parentMembership` nulo significa error y no «es la superior» | Bajo | El campo se devuelve presente con valor `null`, nunca omitido, y `CA-SP-126` y `CA-SP-127` lo verifican por separado. Es información, no ausencia de información |

## 11. Estrategia de prueba

Niveles: **Integración** (Testcontainers sobre PostgreSQL real, con `V13` aplicada) y **API** (extremo a extremo por HTTP, con autenticación). No hay nivel unitario: este requerimiento no tiene `domain`.

| Criterio | Nivel | Qué verifica |
|---|---|---|
| `CA-SP-125` | Integración + API | Sobre una cadena de tres, el detalle de la del medio devuelve su `level` y sus dos vecinos expandidos con `id`, `code`, `name` y `level` |
| `CA-SP-126` | API | El detalle de la superior devuelve `parentMembership: null`, con el campo presente y no omitido |
| `CA-SP-127` | API | El detalle de la inferior devuelve `childMembership: null`, con el campo presente y no omitido |
| `CA-SP-128` | API | Un UUID canónico que no corresponde a ninguna membresía devuelve `404` con `EX-001` |
| `CA-SP-129` | API | Un actor autenticado sin `memberships:read` recibe `403`, no obtiene dato alguno y queda el evento de denegación en `audit_security_log` |

Casos límite de `spec.md` §13 y decisiones de este plan que exigen prueba propia (Art. VII.3):

| Caso | Nivel | Qué verifica |
|---|---|---|
| Única membresía del sistema | API | Superior e hija **nulas a la vez**, ambos campos presentes, y `level = 1` |
| Membresía recién insertada | Integración + API | Tras insertar una intermedia con `RF-SP-016`, el detalle de la nueva, el de su superior y el de su hija reflejan ya el reordenamiento, y los tres son coherentes entre sí |
| Identificador con formato incorrecto | API | `abc`, `1-1-1-1-1` y un UUID de 35 caracteres devuelven los tres `400` con `VAL-001` y campo `id`, **nunca `404`**. La segunda forma es la que el JDK convertiría sin error |
| Vecinos sin sus propios vecinos | API | El objeto de `parentMembership` **no** contiene a su vez `parentMembership` ni `childMembership`: la expansión llega a un solo grado |
| Dependencia de la restricción | Integración | Una consulta sobre `pg_constraint` comprueba que `uq_memberships_parent` **existe y conserva `NULLS NOT DISTINCT`**, y falla si desaparece o se relaja. Es lo que sostiene que el contrato prometa un objeto y no una lista, y se verifica como guarda permanente —igual que la ausencia de cascadas de `RF-SP-012` §11— y no deshabilitando la restricción para observar el sistema roto |
| Coherencia con el listado | Integración | Los vecinos que expande este detalle coinciden con los identificadores que `RF-SP-017` devuelve para esa misma membresía |
| Ausencia de marcas temporales y de conteos | API | El cuerpo no contiene `createdAt`, `updatedAt` ni ningún conteo de personas |
| Número de sentencias por petición | Integración | **Una**, y **ninguna sobre `user_memberships`**. Es lo que hace verificable que el detalle no cuenta personas |
| Ausencia de edición y eliminación | API | `PUT`, `PATCH` y `DELETE` sobre `/api/v1/memberships/{id}` devuelven `405`. Junto con la prueba equivalente de `RF-SP-016`, es la única forma de verificar `RN-SP-008` |

Las reglas de ArchUnit introducidas en `RF-SP-001` y `RF-SP-003` cubren también este requerimiento. No se añade ninguna nueva: no toca `domain` y no introduce dependencias entre módulos.
