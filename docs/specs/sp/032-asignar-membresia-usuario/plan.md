# PLAN — `RF-SP-032` Asignar membresía a un usuario

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-032` |
| Especificación | [`spec.md`](spec.md) |
| `spec.md` aprobada el | 21-08-2026 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 22-08-2026 |

---

## 1. Enfoque

Es la operación más pequeña del bloque y la que más fácilmente se implementa de más, porque su forma no se parece a las otras tres: **asignar membresía reemplaza**, no agrega. `RF-SP-030` y `RF-SP-031` operan sobre conjuntos; esta opera sobre un atributo con un solo valor posible, y `RN-SP-014` lo tiene declarado en el esquema — la clave primaria de `user_memberships` es `user_id` (`requirements/sp.md` §10.12).

Esa forma decide casi todo lo demás. El verbo es `PUT`, porque el cuerpo **sí** representa el estado final. La escritura es una sola sentencia que sirve para los dos casos —primera membresía y sustitución— y que además absorbe el empate concurrente. Y la auditoría es **un solo evento de cambio** con el nivel anterior y el nuevo, no una eliminación más un alta.

Dos reglas la gobiernan, y las dos vienen de que la membresía solo tiene sentido para clientes:

- **`RN-SP-013`** exige que la persona porte al menos un rol de clasificación `CONSUMIDOR`.
- **`RN-SP-014`** limita a una la membresía asignada y admite **fecha de fin opcional**.

Y una decisión que este plan no toma pero de la que depende su forma: **la vigencia se evalúa al consultarla, no la retira ningún proceso** (`spec.md` §2). No hay tarea programada, no hay columna de estado y no hay marca de caducada. La fila permanece tras vencer, ocupando la única plaza de la persona, y quien la lee decide si concede nivel comparando `ends_at` con el momento de la consulta.

## 2. Cambios de esquema

**Ninguno.**

`user_memberships` la crea `V20__create_user_memberships.sql` (`RF-SP-024`) con `user_id` como clave primaria, `membership_id`, `started_at` y `ends_at` nulable. Todo lo que este requerimiento necesita ya está.

**La sustitución se escribe como una sola sentencia**, y no como «leer, decidir si existe, insertar o actualizar»:

```sql
INSERT INTO user_memberships (user_id, membership_id, started_at, ends_at)
VALUES (?, ?, now(), ?)
ON CONFLICT (user_id)
DO UPDATE SET membership_id = EXCLUDED.membership_id,
              started_at    = EXCLUDED.started_at,
              ends_at       = EXCLUDED.ends_at,
              updated_at    = now()
```

Tres razones, y la tercera es la que importa:

1. Cubre `FA-001` —primera membresía— y el reemplazo con el mismo código.
2. Evita la lectura previa que solo serviría para elegir entre dos sentencias.
3. **Absorbe el empate concurrente** que `spec.md` §13 declara: dos asignaciones simultáneas al mismo usuario no pueden dejar dos filas, porque la clave primaria lo impide, pero sin `ON CONFLICT` la segunda recibiría `23505` y saldría como `500`. Es la misma corrección que `RF-SP-005` tuvo que aplicar el 22-08-2026, y conviene no repetir el error en el tercer sitio.

**Consecuencia sobre el adaptador:** esa escritura baja a sentencia nativa. El `merge` de JPA no sabe expresar `ON CONFLICT` y, sobre una entidad con clave primaria asignada, haría exactamente la lectura previa que se quiere evitar.

## 3. Componentes afectados

| Capa | Componente | Nuevo / Modificado | Responsabilidad |
|---|---|---|---|
| `domain` | `UserMembership` | **Nuevo** | Agregado de la asignación: `RN-SP-014` y el cálculo de vigencia contra un instante dado. `isCurrentAt(OffsetDateTime)` es la única definición de «vigente» del sistema |
| `domain` | `User` | Modificado | `hasConsumerRole()`: `RN-SP-013` sobre los roles ya cargados |
| `domain` | `UserRepository` | Modificado | Puerto de `RF-SP-024`. Añade el guardado de la asignación de membresía |
| `application` | `AssignUserMembershipService` | Nuevo | Caso de uso. `@Transactional`, resuelve el orden de `plan.md` §4, decide si hay cambio y emite la auditoría |
| `application` | `MembershipCatalog` | Sin cambios | Puerto de `RF-SP-016`. Verifica que la membresía indicada exista |
| `infrastructure` | `JpaUserRepository` | Modificado | Escribe `user_memberships` con **`INSERT … ON CONFLICT (user_id) DO UPDATE`** en sentencia nativa (§2) |
| `api` | `UserController` | Modificado | Añade `PUT /api/v1/users/{id}/membership` |
| `api` | `AssignMembershipRequest` | Nuevo | DTO de entrada con Bean Validation (`VAL-001`, `VAL-005`) |
| `api` | `UserMembershipResponse` | Nuevo | Membresía vigente, su nivel y su fecha de fin cuando la tiene |

`UserMembership.isCurrentAt(...)` es dominio y no una consulta SQL, y es deliberado: **la definición de «vigente» debe estar en un solo sitio y poder probarse sin base de datos** (Art. VI.3). `RF-SP-026` la lee, `RF-SP-031` la usa para decidir si `RN-SP-015` protege algo, y los tres deben coincidir. Escrita como un `WHERE ends_at IS NULL OR ends_at > now()` repetido en tres consultas, un día dejarán de coincidir en el borde.

## 4. Contrato de API

| Método | Ruta | Descripción |
|---|---|---|
| `PUT` | `/api/v1/users/{id}/membership` | Fija la membresía del usuario |

`PUT` y no `POST`, al revés que en `RF-SP-030`, y la diferencia no es de gusto: aquí el cuerpo **sí** representa el estado final del recurso. La persona tiene una membresía o ninguna, de modo que enviar una la deja como la única — que es exactamente la semántica de `PUT`. Y de ahí sale gratis la idempotencia que `FA-002` exige.

**Petición**

```json
{
  "membershipId": "018f3a2b-7c41-7000-9a3d-1f2e5b8c9d40",
  "endsAt": "2027-08-22T00:00:00Z"
}
```

`endsAt` es opcional. Ausente, la membresía es **indefinida**; presente, deja de estar vigente al pasar. Enviarlo ausente sobre una membresía que tenía fecha la convierte en indefinida, y es un caso normal de `FA-003` — no un olvido que haya que interpretar.

**Respuesta `200`** — `UserMembershipResponse`: la membresía, su nivel y su `endsAt` cuando lo tiene. Se devuelve `200` y no `201` incluso en `FA-001`: `PUT` sobre una ruta fija no crea un recurso direccionable nuevo.

**Errores**

| Código | Cuándo | `error_code` |
|---|---|---|
| `400` | Membresía ausente o identificador malformado | `VAL-001`, `VAL-002` |
| `400` | Fecha de fin igual o anterior al momento de la asignación (`EX-004`) | `VAL-005` |
| `401` | Token ausente o inválido | `AUTH-001` |
| `403` | El actor no posee `users:assign-membership` | `AUTH-002` |
| `404` | El usuario no existe o está eliminado (`EX-003`) | `VAL-004` |
| `409` | La persona no porta ningún rol `CONSUMIDOR` (`EX-001`) | `RN-SP-013` |
| `422` | La membresía indicada no existe en la cadena (`EX-002`) | `VAL-002` |
| `500` | Fallo no controlado | `ERR-500` |

Los tres códigos de rechazo caen en tres categorías distintas y conviene ver por qué, porque es la misma frontera que `RF-SP-030` §4 tuvo que enmendar:

- **`EX-004` es `400`** porque se decide **mirando solo el cuerpo** y el reloj. No hace falta leer nada de la persona para saber que una fecha pasada no sirve.
- **`EX-002` es `422`** porque es una **referencia del cuerpo que no resuelve**, que es la definición literal con la que `RF-SP-001` introdujo el estado.
- **`EX-001` es `409`** porque es una **regla de negocio violada sobre datos que existen**: la persona existe, la membresía existe, y lo que falla es la relación entre ambas.

El cuerpo del `409` debe indicar que primero corresponde asignar un rol de consumidor con `RF-SP-030`, que es lo que `spec.md` `EX-001` exige de forma explícita. Sin esa indicación, quien recibe el error no tiene forma de saber que la operación que le falta es otra.

**Orden de verificación**

1. Formato y obligatoriedad, incluida la fecha de fin.
2. Usuario existente y no eliminado.
3. La membresía indicada existe en la cadena.
4. La persona porta al menos un rol de clasificación `CONSUMIDOR`.

`spec.md` §8 comprueba la membresía antes que el rol de consumidor, y este plan mantiene ese orden: es el que da el error más accionable cuando fallan los dos, porque una membresía inexistente es un dato equivocado en la petición y la falta de rol es una operación previa pendiente.

## 5. Autorización

| Endpoint | Permiso requerido |
|---|---|
| `PUT /api/v1/users/{id}/membership` | `users:assign-membership` |

El permiso es **propio y distinto de `users:update`**, y así está sembrado en `V3__seed_permissions.sql`. Es lo que resuelve la pregunta 4 de `spec.md` §14: en la práctica quien asigna membresías es comercial o soporte, no quien administra la seguridad del sistema.

**Qué roles lo reciben no se decide aquí.** Se decide al sembrarlos, y atarlo a esta especificación la obligaría a cambiar cada vez que cambie el catálogo de roles sin que el requerimiento haya cambiado.

No hay comprobación de `RN-SEG-010` ni equivalente: la membresía **no concede permisos del sistema** y no interviene en la resolución de `security.md` §4.5. Determina alcance sobre contenido, que es otro eje.

## 6. Auditoría

| Operación | Registro | Contenido |
|---|---|---|
| Membresía establecida o sustituida | `audit_change_log` | `action = UPDATE` sobre la entidad `users`, con `changes` conteniendo `before` y `after` de la membresía **y de su fecha de fin**. En `FA-001`, `before` en nulo |
| Renovación (`FA-003`) | `audit_change_log` | Ídem: el nivel no cambió, pero `ends_at` sí, y eso **es** el cambio |
| Sin cambio (`FA-002`) | — | **Ningún evento**: misma membresía y misma vigencia (`CA-SP-276`) |
| Rechazo por `EX-001` (`409`) y `EX-002` (`422`) | `audit_error_log` | `resource = 'users'`, `error_type = 'BUSINESS_RULE'`, `severity = 'MEDIA'` |
| Rechazo `404` por `EX-003` y `400` por `EX-004` | — | **No se audita**: `ck_audit_error_log_status` rechaza el `404` y el `400` (`architecture.md` §6.6.4) |
| Denegación `403` por `AUTH-002` | `audit_security_log` | `event_type = 'AUTHORIZATION_DENIED'`, `severity = 'MEDIA'`. Lo emite la capa de seguridad compartida |
| Fallo no controlado `5xx` | `audit_error_log` | `error_type = 'UNHANDLED'`, `severity = 'ALTA'` |

**Un solo evento de cambio, y no una eliminación más un alta.** Es la resolución de la pregunta 1 de `spec.md` §14: la pregunta que se hace en la práctica es «¿de qué nivel a qué nivel pasó, y quién lo hizo?», y responderla no debería exigir cruzar dos registros. La alternativa era más fiel al modelo de datos —donde una fila cambia de valores— pero esa fidelidad no la necesita nadie.

**Ningún evento de seguridad**, y es la diferencia con `RF-SP-030` y `RF-SP-031`. El catálogo de `security.md` §8.1 es cerrado y enumera la asignación de **roles**, no la de membresía. Añadir uno exigiría alterar `ck_audit_security_log_event_type`, y no hay nada que justifique la migración: una membresía no concede permisos del sistema. Mismo criterio que `RF-SP-016` §6, `RF-SP-022` y `RF-SP-023`.

## 7. Transaccionalidad

| Elemento | Transacción |
|---|---|
| Escritura de `user_memberships` y su evento en `audit_change_log` | **La misma** (Art. V.14) |
| Auditoría de los rechazos | **Independiente**, `REQUIRES_NEW` |
| Revocación de sesiones | **No aplica**: la membresía no viaja en el token ni afecta a los permisos efectivos |

No hay caché que invalidar ni sesión que revocar, y ambas ausencias son consecuencia de lo mismo: la membresía no participa en la resolución de permisos de `security.md` §4.5. `CA-SP-279` lo verifica desde el otro lado — asignar membresía no altera los roles ni los permisos efectivos de la persona.

## 8. Impacto sobre otros módulos

- **`RF-SP-026`** lee `UserMembership.isCurrentAt(...)` para devolver la membresía **y su fecha**, que es lo que permite distinguir una vigente de una vencida (`CA-SP-366`).
- **`RF-SP-031`** usa la misma definición de vigencia para decidir si `RN-SP-015` protege algo: sobre una membresía **vencida** la protección no aplica, de modo que el retiro del último rol consumidor procede igual (`RF-SP-032` §13).
- **`RF-SP-030`** establece la membresía **solo** al conceder el primer rol `CONSUMIDOR`, y su `EX-006` existe para que aquella vía no se convierta en una segunda puerta con reglas distintas. Cualquier otro cambio de nivel es de este requerimiento.
- **`RF-SP-024`** concede la membresía **indefinida**: el alta no admite fecha de fin, que se pone después aquí (`requirements/sp.md` §10.12).
- **Academia y productos** consumirán el nivel para decidir qué contenido alcanza cada persona. Este requerimiento no define esa correspondencia, y `spec.md` §4.2 la deja fuera de forma expresa.
- **Ninguna enmienda a documento transversal.** Es el único requerimiento del bloque A que no la necesita.

## 9. Alternativas consideradas

| Alternativa | Por qué se descartó |
|---|---|
| `POST` sobre una subruta, como `RF-SP-030` | Aquí el cuerpo **sí** representa el estado final: la persona tiene una membresía o ninguna. `POST` sugeriría que se acumulan, que es justo lo que `RN-SP-014` prohíbe |
| Un endpoint único que asigna o retira según venga o no el dato | Oculta dos operaciones con reglas **opuestas** bajo una sola: esta exige rol de consumidor y `RF-SP-033` exige lo contrario. Fundirlas haría imposible declarar `EX-001` (`RF-SP-033` §14, pregunta 2) |
| Auditar la sustitución como eliminación más alta | Más fiel al modelo de datos y menos útil: obliga a cruzar dos registros para responder de qué nivel a qué nivel pasó alguien |
| Emitir además evento de seguridad | El catálogo de `security.md` §8.1 es cerrado y exigiría alterar el `CHECK` del esquema. Una membresía no concede permisos del sistema |
| Un proceso programado que venza las membresías | Sería un requerimiento nuevo —con horario, registro de ejecución y comportamiento ante fallos— que hoy nada cubre. La vigencia se evalúa al consultarla, y el coste está declarado en `spec.md` §2 y §13 |
| Una columna de estado o una marca de caducada en `user_memberships` | Habría que mantenerla, y mantenerla es el proceso programado que se acaba de descartar. Un dato derivable de `ends_at` que alguien tiene que escribir es un dato que un día estará mal |
| Leer, decidir y luego insertar o actualizar | Dos sentencias donde basta una, y deja abierta la ventana concurrente que `spec.md` §13 declara: la segunda asignación recibiría `23505` y saldría como `500` |
| Rechazar la asignación sobre una membresía vencida, exigiendo retirarla antes | Renovar es el caso normal de `FA-003` y debe funcionar sin pasos previos. Exigirlos convertiría una renovación en dos llamadas, y la segunda podría no llegar |
| Prohibir bajar de nivel | Bajar es tan legítimo como subir, y es lo que ocurre cuando alguien deja de pagar el nivel alto (`spec.md` §13) |

## 10. Riesgos

| Riesgo | Impacto | Mitigación |
|---|---|---|
| La definición de «vigente» se reimplementa como `WHERE` en varias consultas y deja de coincidir en el borde | **Alto** | `UserMembership.isCurrentAt(...)` es la única, probada de forma unitaria sobre los tres casos: sin fecha, fecha futura, fecha pasada |
| La escritura se implementa con lectura previa y dos asignaciones concurrentes producen `500` | **Alto** | `ON CONFLICT (user_id) DO UPDATE` en sentencia nativa, con prueba de integración concurrente |
| Nadie renueva y la persona pierde el nivel en silencio | Medio | Contrapartida declarada de no tener proceso de vencimiento (`spec.md` §13). La interfaz debería avisar antes de que ocurra; el sistema no lo hace y no lo promete |
| Se espera que la membresía vencida libere la plaza | Medio | Declarado en `spec.md` §2: la fila permanece y ocupa la única plaza hasta que se renueve o se retire. `RF-SP-026` la devuelve con su fecha para que se distinga |
| Se asume que la membresía concede permisos | Medio | `CA-SP-279` verifica que no altera roles ni permisos efectivos. §5 y §7 lo declaran |
| «¿Qué nivel tenía esta persona en marzo?» pasa a ser consulta habitual | Bajo | Hoy se reconstruye desde la auditoría. `RF-SP-033` §14 pregunta 3 lo tiene anotado: el día que se facture por periodos, la salida es una tabla de histórico con su política de retención |

## 11. Estrategia de prueba

| Criterio | Nivel | Qué verifica |
|---|---|---|
| `CA-SP-272` | Integración | La fila queda en `user_memberships` |
| `CA-SP-273` | API | Persona sin rol consumidor devuelve `409` e indica que primero corresponde `RF-SP-030` |
| `CA-SP-274` | Integración | Tras sustituir queda **una** sola fila, con la membresía nueva |
| `CA-SP-275` | Integración | El evento de `audit_change_log` conserva `before` y `after` del nivel y de la fecha |
| `CA-SP-276` | Integración | Misma membresía y misma vigencia: ninguna fila de auditoría |
| `CA-SP-277` | API | Membresía inexistente devuelve `422` |
| `CA-SP-278` | Integración | Persona con rol funcionario **y** consumidor recibe membresía: basta uno |
| `CA-SP-279` | Integración | Los roles y los permisos efectivos no cambian |
| `CA-SP-364` | Unitaria + Integración | Sin `endsAt` la membresía no vence nunca |
| `CA-SP-365` | Unitaria + Integración | Con `endsAt` pasado deja de conceder nivel **sin que ningún proceso intervenga** |
| `CA-SP-366` | Integración | Una vencida se distingue de no tener ninguna: se devuelve con su fecha y no concede nivel |
| `CA-SP-367` | Integración | Renovar —misma membresía, fecha distinta— actualiza la vigencia y **sí** registra evento |
| `CA-SP-368` | API | Fecha de fin anterior o igual al momento de la asignación devuelve `400` |
| `CA-SP-280` | API | Un actor sin `users:assign-membership` recibe `403` |

Casos límite de `spec.md` §13 con prueba propia (Art. VII.3):

| Caso | Nivel | Qué verifica |
|---|---|---|
| Asignación concurrente de dos membresías al mismo usuario | **Integración concurrente** | Ambas terminan sin `500`, queda **una** fila y el resultado es una de las dos, nunca una mezcla. Sin `ON CONFLICT DO UPDATE` la prueba falla con `23505` |
| Renovar una membresía ya vencida | Integración | Se admite y le devuelve vigencia, sin retirarla antes |
| Convertir indefinida en fechada, y al revés | Integración | Ambas direcciones son `FA-003` y ambas registran evento |
| Sustituir por un nivel inferior | Integración | Se admite sin condiciones |
| Cadena de membresías vacía | API | Toda asignación devuelve `422`. Es el estado inicial del sistema y es correcto |
| Persona inactiva o bloqueada | Integración | Recibe membresía sin error |

`CA-SP-364` a `CA-SP-366` se prueban **primero de forma unitaria** sobre `UserMembership.isCurrentAt(...)`, con los tres casos de borde —sin fecha, fecha futura, fecha exactamente igual al instante consultado— y solo después de integración. Es la única lógica del requerimiento que no es orquestación, y es la que otros dos requerimientos van a reutilizar.
