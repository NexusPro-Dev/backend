# PLAN — `RF-SP-030` Asignar roles a un usuario

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-030` |
| Especificación | [`spec.md`](spec.md) |
| `spec.md` aprobada el | 21-08-2026 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 22-08-2026 |

---

## 1. Enfoque

Es donde el acceso deja de ser una definición y empieza a recaer sobre personas. Todo lo que `SP` construyó antes —el catálogo, los roles, su contención— no afecta a nadie hasta esta operación.

La forma es la de `RF-SP-005`: **aditiva e idempotente**. Agrega los roles que faltan e ignora los que ya estaban, y esa forma no es una comodidad sino lo que la mantiene fuera del alcance de las reglas de retiro. Como nunca quita nada, no puede dejar a nadie sin superadministrador (`RN-SP-001`), ni sin membresía habiendo rol de consumidor (`RN-SP-018`), ni sin superior habiendo rol vendedor (`RN-SP-019`) — salvo por un camino, y es justo el que este requerimiento tiene que cerrar.

Ese camino es el que le da su dificultad propia. Conceder el **primer** rol `CONSUMIDOR` o el **primer** rol `VENDEDOR` produciría, por sí solo, un estado que dos reglas críticas prohíben. La salida no es rechazar —eso dejaría un bloqueo mutuo, el mismo que `RF-SP-033` documentó— sino **exigir el dato que falta en la misma petición y escribirlo en la misma transacción**. De ahí que el cuerpo tenga dos campos condicionales que no son adornos: sin ellos, la operación no puede terminar sin dejar la cuenta incoherente.

Tres verificaciones actúan sobre planos distintos y ninguna sustituye a otra:

- **`RN-SEG-010`** acota al actor: nadie concede lo que no posee. Se evalúa **comparando permisos**, no roles (`spec.md` §14, pregunta 1).
- **`RN-SP-018`** exige membresía en cuanto aparece el primer rol `CONSUMIDOR`.
- **`RN-SP-019`** y **`RN-SP-020`** exigen superior en cuanto aparece el primer rol `VENDEDOR` **o cambia cuál es el de mayor rango**, y fijan quién puede serlo.

La operación se aplica **entera o no se aplica**. Un rechazo parcial dejaría a la persona con un subconjunto de roles que nadie pidió, y con la posibilidad de que ese subconjunto sea precisamente el que incumple una de las tres.

Y una promesa que esta operación **no** hace: el efecto no es inmediato. El token de acceso transporta los códigos de rol (`security.md` §4.5), de modo que el rol nuevo se aplica cuando ese token expira, como mucho a los quince minutos. No se revocan sesiones, y esa es la asimetría deliberada con `RF-SP-031` (§9).

## 2. Cambios de esquema

**Migración:** `V25__create_user_roles_role_index.sql`

Ninguna tabla nueva, ninguna columna nueva y ninguna restricción nueva. `user_roles` la crea `V19__create_user_roles.sql` (`RF-SP-024`), porque el alta de usuario ya escribe asignaciones. Lo único que falta es un acceso:

| Tabla | Cambio | Detalle |
|---|---|---|
| `user_roles` | Altera (índice) | `ix_user_roles_role_id` sobre `(role_id)` |

La clave primaria compuesta `(user_id, role_id)` solo sirve consultas que empiezan por el usuario. La pregunta inversa —«cuántas personas portan este rol»— la necesitan `RF-SP-003` y `RF-SP-009` para sus conteos, y este requerimiento es quien crea el índice porque es quien puebla la tabla en volumen (`requirements/sp.md` §10.11).

**La clave primaria compuesta no absorbe el empate concurrente por sí sola**, y conviene no repetir aquí el error que `RF-SP-005` tuvo que corregir el 22-08-2026. Con dos transacciones abiertas a la vez asignando el mismo rol a la misma persona, la segunda inserción **espera** al desenlace de la primera y recibe `23505` al confirmar esta, que sin tratamiento sale como `500`. El caso límite de `spec.md` §13 exige lo contrario.

La inserción declara el conflicto como esperado:

```sql
INSERT INTO user_roles (user_id, role_id)
VALUES (?, ?)
ON CONFLICT (user_id, role_id) DO NOTHING
```

Es la misma solución, por la misma razón y con la misma consecuencia: esa escritura baja a sentencia nativa, porque el `persist` de JPA no sabe expresar `ON CONFLICT`.

**`user_memberships` y `user_supervisors` no se alteran.** Las crean `V20` y `V21` (`RF-SP-024`) con todo lo que esta operación necesita: la clave primaria de `user_memberships` es `user_id`, que es como `RN-SP-014` está declarada en el esquema, y `uq_user_supervisors_vigente` —parcial sobre `user_id WHERE ended_at IS NULL`— es la que impide dos superiores vigentes. Esta operación escribe en ambas, pero no cambia su forma.

## 3. Componentes afectados

| Capa | Componente | Nuevo / Modificado | Responsabilidad |
|---|---|---|---|
| `domain` | `User` | Modificado | `assignRoles(...)`: agrega los roles que faltan y devuelve **cuáles se agregaron realmente**. Decide si la operación produce el primer rol `CONSUMIDOR` y si cambia el rol vendedor de mayor rango |
| `domain` | `RoleGrantPolicy` | **Nuevo, compartido** | `RN-SEG-010` en un solo sitio. Recibe los permisos que declara cada rol y los permisos efectivos del actor, y devuelve qué roles exceden. Lo consumen `RF-SP-024`, `RF-SP-030` y —por la vía de los permisos— `RF-SP-005` |
| `domain` | `CommercialRank` | **Nuevo** | Resuelve cuál es el rol vendedor de **mayor rango** de un conjunto de roles y cuál es su rol padre inmediato. Es el cálculo del que dependen `RN-SP-019` y `RN-SP-020`, y el que distingue un ascenso de una asignación lateral |
| `domain` | `UserRepository` | Modificado | Puerto de `RF-SP-024`. Añade la carga del usuario con sus roles, su membresía y su superior vigente en una sola lectura |
| `domain` | `RoleRepository` | Sin cambios | Puerto de `RF-SP-001` |
| `application` | `AssignUserRolesService` | Nuevo | Caso de uso. `@Transactional`, resuelve las cotas, escribe los tres hechos y emite la auditoría |
| `application` | `AuthenticatedActor` | Sin cambios | Puerto de `RF-SP-001`, ampliado por `RF-SP-004`. Aporta los permisos efectivos del actor |
| `application` | `MembershipCatalog` | Sin cambios | Puerto de `RF-SP-016`. Verifica que la membresía indicada exista |
| `infrastructure` | `JpaUserRepository` | Modificado | Inserta en `user_roles` con **`INSERT … ON CONFLICT DO NOTHING`** en sentencia nativa (§2); escribe `user_memberships` y abre la fila de `user_supervisors` cuando procede |
| `infrastructure` | `SecurityContextActorAdapter` | Sin cambios | Resuelve los permisos efectivos del actor desde la base de datos (`RF-SP-005` §5) |
| `api` | `UserController` | Modificado | Añade `POST /api/v1/users/{id}/roles` |
| `api` | `AssignRolesRequest` | Nuevo | DTO de entrada con Bean Validation (`VAL-001`, `VAL-002`, `VAL-005`) |
| `api` | `UserResponse` | Sin cambios | Definido en `RF-SP-024` |

`RoleGrantPolicy` vive en `domain` y no en `application` porque es una regla de negocio y debe poder probarse sin Spring ni base de datos (Art. VI.3). Que sea **un solo componente** es lo que resolvió la pregunta 1 de `spec.md` §14: tres comprobaciones idénticas escritas en tres sitios divergen, y la que se quede atrás no falla — concede.

## 4. Contrato de API

| Método | Ruta | Descripción |
|---|---|---|
| `POST` | `/api/v1/users/{id}/roles` | Agrega roles al usuario |

`POST` sobre una subruta y no `PUT` sobre la lista, por el mismo motivo que en `RF-SP-005`: la operación **no** representa el estado final del conjunto. `PUT` invitaría a leerla como un reemplazo, y un reemplazo haría retiros implícitos que se saltarían `RN-SP-001`, `RN-SP-015` y `RN-SP-022`.

**Petición**

```json
{
  "roleIds": [
    "018f3a2b-7c41-7000-9a3d-1f2e5b8c9d01"
  ],
  "membershipId": "018f3a2b-7c41-7000-9a3d-1f2e5b8c9d40",
  "membershipEndsAt": "2027-08-22T00:00:00Z",
  "supervisorId": "018f3a2b-7c41-7000-9a3d-1f2e5b8c9d90"
}
```

Los roles van por identificador y no por código, igual que los permisos en `RF-SP-001` y `RF-SP-005`, para no mezclar dos espacios de identificación en el mismo cuerpo.

`membershipId`, `membershipEndsAt` y `supervisorId` son **condicionales**: obligatorios exactamente en los casos que `RN-SP-018` y `RN-SP-019` describen, y **no admitidos** en cualquier otro. `membershipEndsAt` solo se admite acompañando a `membershipId`, y sus reglas son las de `RF-SP-032` §4 — no se duplican aquí.

**Respuesta `200`** — `UserResponse`, definido en `RF-SP-024`: la persona con su lista de roles actualizada, su membresía y su superior vigente. No se devuelve `201`: no se ha creado ningún recurso direccionable, se ha ampliado uno existente.

**Errores**

| Código | Cuándo | `error_code` |
|---|---|---|
| `400` | Lista vacía, identificador malformado o más de 100 elementos | `VAL-001`, `VAL-002`, `VAL-005` |
| `401` | Token ausente o inválido | `AUTH-001` |
| `403` | El actor no posee `users:assign-roles` | `AUTH-002` |
| `404` | El usuario no existe o está eliminado (`EX-004`) | `VAL-006` |
| `409` | Algún rol declara permisos que el actor no posee (`EX-001`) | `RN-SEG-010` |
| `422` | Algún rol no existe o está eliminado (`EX-002`) | `EX-002` |
| `422` | Algún rol está inactivo (`EX-003`) | `EX-003` |
| `422` | Primer rol `CONSUMIDOR` sin membresía (`EX-005`) | `RN-SP-018` |
| `422` | Membresía indicada sin que corresponda (`EX-006`) | `EX-006` |
| `422` | Primer rol `VENDEDOR` o ascenso sin superior (`EX-007`) | `RN-SP-019` |
| `422` | Superior indicado sin que corresponda, o que no puede serlo (`EX-008`) | `VAL-007`, `RN-SP-020` |
| `500` | Fallo no controlado | `ERR-500` |

Los cuerpos de `409` y de los `422` de rol **deben enumerar los roles que incumplen**. Sin ese detalle el actor no puede corregir su petición, y `EX-001`, `EX-002` y `EX-003` lo exigen de forma explícita.

!!! warning "Enmienda a `spec.md` §11 — los cuatro casos condicionales son `422`, no `400` (Art. I.7)"

    La especificación redacta `EX-006` y la primera mitad de `EX-008` como «rechaza la petición por **campo no admitido**», que en la serie `VAL-` de `architecture.md` §7.3 corresponde a un `400`; y deja `EX-005` y `EX-007` sin código de validación asignado. Este plan los unifica en `422` y declara la enmienda aquí.

    El criterio es el que separa las dos series en todo el módulo: **`400` es lo que se decide mirando solo el cuerpo** —formato, obligatoriedad incondicional, el límite de 100—, y lo rechaza Bean Validation antes de entrar al caso de uso (`development-guide.md` §8, nivel «formato»). **Ninguno de estos cuatro casos se puede decidir así.** Saber si `membershipId` sobra exige leer qué roles porta ya la persona y si tiene membresía; saber si `supervisorId` falta exige calcular cuál será su rol vendedor de mayor rango **al terminar la operación**. Es exactamente la definición de `422` que `RF-SP-001` fijó al introducir `UnprocessableEntityException`: la ruta existe, el cuerpo es sintácticamente válido, y la petición es semánticamente irrealizable.

    Devolverlos como `400` obligaría además a que un `400` pudiera salir del caso de uso y no solo del validador, que es la frontera que hace legible el manejador global.

    `VAL-007` y `VAL-008` de `spec.md` §11 se mantienen como identificadores de la regla —son los que viajan en `error_code`—, pero su nivel es de negocio, no de formato.

**Orden de verificación**

1. Formato, obligatoriedad y límite de 100, todas juntas.
2. Usuario existente y no eliminado.
3. Todos los roles existen y no están eliminados.
4. Ninguno de los roles está inactivo.
5. Contención en el actor (`RN-SEG-010`).
6. Coherencia de la membresía: obligatoria si aparece el primer rol `CONSUMIDOR`, no admitida en otro caso.
7. Coherencia del superior: obligatorio si aparece el primer rol `VENDEDOR` o cambia el de mayor rango y no es la cúspide; no admitido en otro caso.
8. El superior indicado existe, está `ACTIVO` y porta el rol padre inmediato del rol de mayor rango resultante (`RN-SP-020`).

Los pasos 6 a 8 **no son evaluables** sin haber resuelto antes los roles: hasta que no se sabe qué roles son y de qué clasificación, no se puede saber si la persona termina siendo consumidor o vendedor. El orden es dependencia, no preferencia. El paso 5 va antes que ellos porque un rol fuera del alcance del actor debe rechazarse aunque además falte la membresía: el intento de escalada es lo que más importa registrar.

## 5. Autorización

| Endpoint | Permiso requerido |
|---|---|
| `POST /api/v1/users/{id}/roles` | `users:assign-roles` |

**`RN-SEG-010` se evalúa leyendo la base de datos, no la caché de resolución.** Es la decisión de `RF-SP-001` §5 y `RF-SP-005` §5, y aquí pesa más que en ninguna de las dos: lo que una entrada obsoleta produciría no es una lectura desactualizada sino **una concesión indebida sobre una persona**, que además sobrevive a la corrección de la caché.

**No hay regla equivalente a `RN-SEG-011` aquí**, y `spec.md` §13 lo dice explícitamente: aquella protege a los roles de que su portador se los amplíe, y esta operación no toca la definición de ningún rol. El actor puede asignarse roles a sí mismo, y `RN-SEG-010` lo acota a lo que ya posee, de modo que no gana nada.

## 6. Auditoría

| Operación | Registro | Contenido |
|---|---|---|
| Roles agregados | `audit_change_log` | `action = UPDATE` sobre la entidad `users`, con `changes` conteniendo **solo los roles realmente agregados**, por código |
| Membresía establecida en la misma operación | `audit_change_log` | Evento propio sobre la entidad `users`, con la membresía y su fecha de fin. **Mismo `correlation_id`** que el anterior (`RN-SP-018`) |
| Superior establecido en la misma operación | `audit_change_log` | Evento propio, con el superior y el rol que porta. **Mismo `correlation_id`** |
| Roles agregados | `audit_security_log` | `event_type = 'USER_ROLES_ASSIGNED'`, `severity = 'ALTA'`, `outcome = 'SUCCESS'`, `target_user_id` de la persona afectada |
| Ninguno agregado | — | **Ningún evento**: si todos los roles ya estaban, nada cambió (`CA-SP-257`) |
| Rechazo por `EX-001` (`409`) | `audit_error_log` | `resource = 'users'`, `error_type = 'BUSINESS_RULE'`, `severity = **ALTA**`. Es un intento de escalada de privilegios y debe encontrarse buscando por severidad |
| Rechazo por `EX-002`, `EX-003`, `EX-005` a `EX-008` (`422`) | `audit_error_log` | Ídem, `severity = 'MEDIA'` |
| Rechazo `404` por `EX-004` | — | **No se audita** en la auditoría de error: `ck_audit_error_log_status` rechaza el `404` (`architecture.md` §6.6.4) |
| Rechazo `400` de formato | — | **No se audita** (`architecture.md` §6.6.4) |
| Denegación `403` por `AUTH-002` | `audit_security_log` | `event_type = 'AUTHORIZATION_DENIED'`, `severity = 'MEDIA'`, `outcome = 'FAILURE'`. Lo emite la capa de seguridad compartida |
| Fallo no controlado `5xx` | `audit_error_log` | `error_type = 'UNHANDLED'`, `severity = 'ALTA'` |

**Tres eventos de cambio y no uno**, cuando la operación establece membresía o superior. Es deliberado y es lo contrario de lo que hace `RF-SP-005`, que emite uno solo: allí los tres permisos son la misma decisión de negocio, y aquí son tres hechos distintos sobre tres tablas distintas que `RF-SP-011` debe poder consultar por separado. Lo que los ata es el `correlation_id`, que es lo que `CA-SP-259` y `CA-SP-401` verifican y lo que permite recuperar la operación entera con una sola consulta.

**Un solo evento de seguridad**, en cambio, aunque los roles sean varios: `security.md` §8.1 declara `USER_ROLES_ASSIGNED` como el evento de la operación, no de cada fila.

## 7. Transaccionalidad

| Elemento | Transacción |
|---|---|
| Inserción en `user_roles`, escritura de `user_memberships` y de `user_supervisors`, y sus tres eventos de `audit_change_log` | **La misma** (Art. V.14) |
| Evento `USER_ROLES_ASSIGNED` en `audit_security_log` | **Independiente**, `REQUIRES_NEW`, enganchada al commit |
| Auditoría de los rechazos | **Independiente**, `REQUIRES_NEW`, sin esperar a un commit que no llega |
| Revocación de sesiones | **No aplica**: esta operación no revoca ninguna (§9) |

Que los tres hechos de negocio compartan transacción es la exigencia literal de `RN-SP-018` y `RN-SP-019`: «nadie porta un rol comercial sin sitio en la estructura, ni siquiera durante un instante». Si la escritura del superior fallara después de la del rol y ambas no se revirtieran juntas, el sistema quedaría en el estado que la regla declara inexistente.

El evento de seguridad espera al commit por el motivo de `RF-SP-001` §7: emitido antes, una reversión dejaría un `SUCCESS` de una asignación que no ocurrió, y ese evento no se puede retirar porque su transacción ya cerró.

## 8. Impacto sobre otros módulos

- **`RF-SP-003` y `RF-SP-009`** dependen de `ix_user_roles_role_id` (§2) para contar cuántas personas portan un rol sin recorrer la tabla. Sus planes ya lo dan por existente; esta migración es quien lo crea.
- **`RF-SP-024`** comparte con este requerimiento `RoleGrantPolicy` y `CommercialRank`. El alta de usuario resuelve exactamente el mismo problema —primer rol de consumidor, primer rol de vendedor— sobre una persona que aún no existe, y debe hacerlo con los mismos componentes. Si se implementa antes, los crea él y este requerimiento los consume.
- **`RF-SP-031`** es la operación inversa y **no** es su simétrica. Comparte `CommercialRank` para decidir si el retiro deja a la persona sin rol vendedor.
- **`RF-SP-032`** no cambia. Esta operación establece la membresía **solo** en el caso del primer rol `CONSUMIDOR`; cualquier otro cambio de nivel es suyo, y `EX-006` existe para que esta vía no se convierta en una segunda puerta con reglas distintas.
- **`RF-SP-041`** ídem con el superior comercial.
- **`security.md` §8.1** ya enumera `USER_ROLES_ASSIGNED` y `ck_audit_security_log_event_type` ya lo admite (`V4__create_audit_logs.sql`). No hay enmienda que tramitar.
- **`spec.md` §11** se enmienda por §4: los cuatro casos condicionales pasan de `400` a `422`.

## 9. Alternativas consideradas

| Alternativa | Por qué se descartó |
|---|---|
| Reemplazar la lista completa con `PUT` | Haría retiros implícitos, y retirar tiene reglas propias —`RN-SP-001`, `RN-SP-015`, `RN-SP-022`—. O se reimplementan aquí, con dos copias de cada una, o se saltan en silencio y la operación deja al sistema sin superadministrador sin que nada falle |
| Asignar los roles válidos e ignorar los que fallan | Dejaría a la persona en un estado que nadie pidió. `EX-001` a `EX-003` exigen rechazo completo, y el subconjunto que sobrevive podría ser justo el que incumple una regla condicional |
| Evaluar `RN-SEG-010` comparando **roles** en vez de permisos | Era más barato —bastaba recorrer la cadena de rol padre— pero rechaza asignaciones legítimas: un rol de otra rama del árbol cuyos permisos el actor sí posee. Y acopla la asignación de roles a la jerarquía de contención, que existe para acotar qué declara un rol, no quién puede repartirlo (`spec.md` §14, pregunta 1) |
| Escribir `RN-SEG-010` en este caso de uso, sin componente compartido | Tres comprobaciones idénticas en `RF-SP-005`, `RF-SP-024` y aquí divergen con el tiempo, y la que se quede atrás no falla: concede. Es la resolución literal de la pregunta 1 |
| Rechazar el primer rol `CONSUMIDOR` sin membresía en lugar de exigirla en la petición | Es el bloqueo mutuo que `RF-SP-033` §14 documentó: con `RN-SP-013` exigiendo rol para tener membresía y `RN-SP-018` exigiendo membresía para tener rol, nadie podría entrar al estado de consumidor nunca |
| Establecer la membresía en una llamada posterior a `RF-SP-032` | Deja a la persona en el estado que `RN-SP-018` declara inexistente durante el intervalo entre las dos llamadas, y nadie garantiza que la segunda llegue. Lo mismo vale para el superior |
| Deducir el superior del organigrama en vez de exigirlo | El sistema no puede saber a quién pasa a reportar quien asciende: puede haber varios portadores del rol padre. Quien ejecuta el ascenso sí lo sabe (`spec.md` §13) |
| Evaluar `RN-SP-019` sobre «el primer rol vendedor» y no sobre el de mayor rango | Un ascenso cambia con quién debe cumplirse `RN-SP-020`: quien pasa de `AGENTE` a `DIRECTOR` deja de poder estar a cargo de un director. Mirar el primero dejaría pasar el ascenso sin pedir superior nuevo |
| Revocar las sesiones de la persona para que el rol aplique de inmediato | Exigiría revocar su refresh token: **expulsar a alguien de su sesión por haberle concedido algo**. Desproporcionado para una operación que solo amplía. En `RF-SP-031` la respuesta es la contraria y la asimetría es deliberada (`spec.md` §14, pregunta 3) |
| Fijar un tope de roles por persona | Nadie sabría cuál es el número correcto, y un tope elegido a ojo rechazaría asignaciones legítimas. Queda como riesgo con su condición de disparo (§10) |
| Confiar el empate concurrente a la clave primaria compuesta, sin más | Es el error que `RF-SP-005` tuvo que corregir: la segunda inserción no «encuentra la fila», recibe `23505` al confirmar la primera y sale como `500` |

## 10. Riesgos

| Riesgo | Impacto | Mitigación |
|---|---|---|
| Los tres hechos no comparten transacción y la persona queda como consumidor sin membresía o vendedor sin superior | **Alto** | `@Transactional` único en el caso de uso; `CA-SP-259` y `CA-SP-401` verifican además el `correlation_id` compartido |
| `RN-SEG-010` se implementa contra la caché de permisos por eficiencia | **Alto** | Anotado aquí, en `RF-SP-001` y en `RF-SP-005`. Es una concesión indebida sobre una persona, no una lectura desactualizada |
| `RoleGrantPolicy` se duplica en `RF-SP-024` en lugar de compartirse | **Alto** | §3 y §8 lo declaran compartido; `T-02` de `tasks.md` lo exige como componente propio con sus pruebas |
| El ascenso no se detecta y se acepta sin superior nuevo | **Alto** | `CommercialRank` calcula el rol de mayor rango **resultante**, no el actual. `CA-SP-403` y `CA-SP-404` cubren las dos direcciones |
| El token crece hasta superar el tamaño razonable de una cabecera HTTP | Medio | **Condición de disparo declarada** (`spec.md` §14, pregunta 4). La corrección no sería un tope de roles sino dejar de transportar los códigos de rol en el token, que es un cambio de `security.md` §4.5 y no de este requerimiento |
| El superior asciende y su subordinado no, dejando `RN-SP-020` incumplida | Medio | Hueco conocido y declarado en `spec.md` §13: se valida al escribir, no de forma continua. La corrección es `RF-SP-041` sobre cada subordinado. Si aparece con frecuencia, la salida es una comprobación periódica de consistencia, no una cascada automática |
| Se espera que la asignación tenga efecto inmediato | Medio | Declarado en `spec.md` §2 y §13. La vía inmediata es ampliar un rol que la persona **ya porta** con `RF-SP-005` |

## 11. Estrategia de prueba

| Criterio | Nivel | Qué verifica |
|---|---|---|
| `CA-SP-251` | Integración | Las filas quedan en `user_roles` |
| `CA-SP-252` | Integración | Los roles previos siguen presentes tras la operación |
| `CA-SP-253` | Unitaria + API | `RoleGrantPolicy` rechaza y enumera; la API devuelve `409` con los roles citados |
| `CA-SP-254` | API | Un rol inactivo devuelve `422` indicando cuál |
| `CA-SP-255` | API | Rol inexistente y rol eliminado devuelven el **mismo** cuerpo |
| `CA-SP-256` | Integración | Repetir la petición no produce error ni filas duplicadas |
| `CA-SP-257` | Integración | Ninguna fila de auditoría cuando ningún rol era nuevo |
| `CA-SP-258` | Integración | Renovado el token, los permisos efectivos incluyen los del rol nuevo |
| `CA-SP-259` | Integración | Rol `CONSUMIDOR` y membresía en la misma transacción y bajo el mismo `correlation_id` |
| `CA-SP-369` | API | Primer rol `CONSUMIDOR` sin membresía devuelve `422` con `RN-SP-018` |
| `CA-SP-370` | API | Membresía indicada sin corresponder devuelve `422` con `EX-006` |
| `CA-SP-399` | API | Primer rol `VENDEDOR` sin superior devuelve `422` con `RN-SP-019` |
| `CA-SP-403` | Unitaria + Integración | El **ascenso** sin superior nuevo se rechaza; con él se acepta y cierra la asignación anterior con su fecha de fin |
| `CA-SP-404` | Unitaria | Un rol vendedor de rango **inferior** al que ya porta no exige superior |
| `CA-SP-400` | API | Superior no admitido, o que no porta el rol padre inmediato, devuelve `422` |
| `CA-SP-401` | Integración | Rol `VENDEDOR` y superior en la misma transacción y bajo el mismo `correlation_id` |
| `CA-SP-402` | API | La cúspide de la fuerza comercial no exige superior y lo rechaza si se indica |
| `CA-SP-260` | Integración | Una fila en `audit_change_log` y otra en `audit_security_log`, con severidad Alta y `target_user_id` de la persona |
| `CA-SP-261` | API | Un actor sin `users:assign-roles` recibe `403` |

Casos límite de `spec.md` §13 con prueba propia (Art. VII.3):

| Caso | Nivel | Qué verifica |
|---|---|---|
| Asignación concurrente del mismo rol al mismo usuario | **Integración concurrente** | Dos transacciones asignan a la vez el mismo rol: **ambas terminan con `200`**, queda **una** fila y **ninguna** produce `500`. Sin `ON CONFLICT DO NOTHING` esta prueba falla con `23505`, que es justo lo que la hace valer |
| Asignación concurrente con la eliminación del rol | **Integración concurrente** | Se serializan sobre la fila del rol según el contrato de `RF-SP-009`. Es la garantía de `CA-SP-165` |
| Roles duplicados en la petición | Unitaria | Se colapsan a una ocurrencia sin error |
| Usuario inactivo o bloqueado | Integración | Recibe roles sin error: `RN-SEG-002` afecta al estado del rol, no al de la persona |
| El actor se asigna roles a sí mismo | API | Se admite, y `RN-SEG-010` lo acota a lo que ya posee |

`CA-SP-403` y `CA-SP-404` merecen prueba unitaria propia sobre `CommercialRank`, y no solo de API: son las dos direcciones del mismo cálculo —el rango sube, el rango no sube— y confundirlas es el defecto más probable de todo el requerimiento.
