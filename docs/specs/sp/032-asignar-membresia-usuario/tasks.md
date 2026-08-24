# TASKS — `RF-SP-032` Asignar membresía a un usuario

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-032` |
| Especificación | [`spec.md`](spec.md) |
| Plan | [`plan.md`](plan.md) |
| `plan.md` aprobado el | 22-08-2026 |
| Estado | **Aprobadas** — 24-08-2026 |
| Issue | Pendiente de crear |
| Rama | `feature/membresia-de-usuario` |
| Aprobadas por | Responsable técnico, 24-08-2026 |

---

## 1. Tareas

Sin migración: `user_memberships` la crea `V20__create_user_memberships.sql` (`RF-SP-024`) con todo lo que hace falta (`plan.md` §2). El requerimiento es corto y casi todo él es orquestación, salvo una pieza que no lo es y que otros dos requerimientos van a reutilizar: **la definición de «vigente»**. Se escribe primero y se prueba sola.

| # | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-01` | `domain/UserMembership`: agregado de la asignación con `isCurrentAt(OffsetDateTime)`, **única definición de «vigente» del sistema** | — | Pruebas unitarias sin Spring sobre los tres bordes: sin fecha es siempre vigente; fecha futura es vigente; fecha **exactamente igual** al instante consultado ya no lo es | **Hecha** |
| `T-02` | `domain/User.hasConsumerRole()`: `RN-SP-013` sobre los roles ya cargados | — | Prueba unitaria: basta un rol de clasificación `CONSUMIDOR` entre varios de otras clasificaciones | **Hecha** |
| `T-03` | `application/AssignUserMembershipService` con `@Transactional` y el orden de verificación de `plan.md` §4 | `T-01`, `T-02` | Pruebas con dobles: la membresía se comprueba **antes** que el rol de consumidor; cada excepción en el orden declarado | **Hecha** |
| `T-04` | Escritura en `user_memberships` desde `JpaUserRepository` con **`INSERT … ON CONFLICT (user_id) DO UPDATE`** en sentencia nativa (`plan.md` §2) | `T-03` | Prueba de integración concurrente: dos asignaciones simultáneas terminan sin `500`, dejan **una** fila y el resultado es una de las dos, nunca una mezcla | **Hecha** |
| `T-05` | Detección de «sin cambio» (`FA-002`) frente a renovación (`FA-003`): misma membresía y misma vigencia no escribe ni audita; misma membresía con fecha distinta sí | `T-01`, `T-03` | Prueba de integración: repetir la petición idéntica no deja fila de auditoría; cambiar solo `endsAt` deja una | **Hecha** |
| `T-06` | Auditoría de éxito: un evento en `audit_change_log` con `before` y `after` del nivel **y** de la fecha, con `before` nulo en `FA-001`. **Ningún evento de seguridad** | `T-04`, `T-05` | Prueba de integración: el evento conserva ambos niveles; `audit_security_log` queda **vacío** tras la operación | **Hecha** |
| `T-07` | Auditoría de los rechazos (`plan.md` §6): `EX-001` y `EX-002` en `audit_error_log` con severidad Media; `EX-003` (`404`) y `EX-004` (`400`) sin auditar | `T-03` | Prueba de integración: los dos primeros dejan su fila con su `error_code`; los dos últimos no dejan ninguna | **En curso** |
| `T-08` | `api/AssignMembershipRequest` con Bean Validation (`VAL-001`, `VAL-002`) y la comprobación de `VAL-005` —fecha posterior al momento de la asignación— contra un `Clock` inyectado | `T-03` | Prueba de API: fecha pasada e igual al instante devuelven `400`; el `Clock` fijado hace la prueba determinista | **Hecha** |
| `T-09` | `api/UserMembershipResponse` y `api/UserController`: `PUT /api/v1/users/{id}/membership` con el permiso `users:assign-membership` | `T-06`, `T-08` | Prueba de API: `200` con la membresía, su nivel y su fecha; el `409` indica que primero corresponde `RF-SP-030` | **Hecha** |
| `T-10` | Pruebas de API e integración de los criterios de aceptación de `spec.md` §12 | `T-09` | La suite cubre `CA-SP-272` a `CA-SP-280` y `CA-SP-364` a `CA-SP-368` | **En curso** |
| `T-11` | Pruebas de los casos límite de `spec.md` §13: renovar una vencida, convertir indefinida en fechada y al revés, bajar de nivel, cadena vacía, persona inactiva y la asignación concurrente | `T-09` | Ninguno produce `500` ni deja dos filas | **En curso** |
| `T-12` | Documentación OpenAPI del endpoint: cuerpo con `endsAt` opcional y su significado, respuesta `200` y los estados `400`, `401`, `403`, `404`, `409`, `422` y `500` | `T-10` | El contrato publicado coincide con el comportamiento real (Art. VIII.6), y la descripción de `endsAt` dice que ausente significa indefinida | **Hecha** |
| `T-13` | Actualizar la matriz de trazabilidad de `docs/requirements.md` | `T-10` | La fila de `RF-SP-032` refleja el estado y enlaza esta tripleta | **Hecha** |

**Estados:** `Pendiente` · `En curso` · `Hecha` · `Bloqueada`.

## 2. Orden de ejecución

```mermaid
graph LR
    T01[T-01] --> T03[T-03]
    T02[T-02] --> T03
    T01 --> T05[T-05]
    T03 --> T04[T-04] --> T06[T-06]
    T03 --> T05 --> T06
    T03 --> T07[T-07]
    T03 --> T08[T-08]
    T06 --> T09[T-09]
    T08 --> T09
    T09 --> T10[T-10] --> T12[T-12]
    T10 --> T13[T-13]
    T09 --> T11[T-11]
```

`T-01` y `T-02` son dominio puro y no dependen de nada. `T-01` es la que conviene escribir primero de todo el bloque A: `RF-SP-026` y `RF-SP-031` la consumen.

## 3. Cobertura de los criterios de aceptación

| Criterio | Tarea que lo cubre |
|---|---|
| `CA-SP-272` | `T-04`, `T-10` |
| `CA-SP-273` | `T-02`, `T-09`, `T-10` |
| `CA-SP-274` | `T-04`, `T-10` |
| `CA-SP-275` | `T-06`, `T-10` |
| `CA-SP-276` | `T-05`, `T-10` |
| `CA-SP-277` | `T-03`, `T-10` |
| `CA-SP-278` | `T-02`, `T-10` |
| `CA-SP-279` | `T-10` |
| `CA-SP-364` | `T-01`, `T-10` |
| `CA-SP-365` | `T-01`, `T-10` |
| `CA-SP-366` | `T-01`, `T-10` |
| `CA-SP-367` | `T-05`, `T-10` |
| `CA-SP-368` | `T-08`, `T-10` |
| `CA-SP-280` | `T-09`, `T-10` |

## 4. Bloqueos

| # | Bloqueo | Desde | Responsable | Estado |
|---|---|---|---|---|
| 1 | Ninguna tarea es ejecutable hasta que `RF-SP-024` cree `users` (`V18`), `user_roles` (`V19`) y `user_memberships` (`V20`) | 22-08-2026 | Responsable técnico | **Cerrado — `V20` existe desde el 24-08-2026** |
| 2 | `T-03` consume `MembershipCatalog`, el puerto de `RF-SP-016`. Sin la cadena de membresías sembrada, toda asignación se rechaza por `EX-002` — que es correcto, pero deja `T-10` sin poder cubrir el camino feliz | 22-08-2026 | Responsable técnico | **Cerrado — `MembershipCatalog` se creó al implementar `RF-SP-030` y aquí se amplía con el nivel** |
| 3 | `T-01` produce la definición de vigencia que `RF-SP-026` y `RF-SP-031` reutilizan. Quien llegue segundo **no la reimplementa** como `WHERE` en su consulta (`plan.md` §10) | 22-08-2026 | Responsable técnico | **Cerrado — la definición vive en `UserMembership.isCurrentAt`, con sus tres bordes probados; `RF-SP-026` y `RF-SP-031` la consumirán** |


## 4.bis Desviaciones respecto del plan e implementación real

| # | Desviación | Motivo | Consecuencia |
|---|---|---|---|
| 1 | `T-01` no produjo un agregado `UserMembership` nuevo: la definición de «vigente» vive en el registro de proyección del mismo nombre, que ya existía en `domain/repository` | Es el único tipo que lleva la fecha, y crear un segundo con el mismo nombre habría producido justamente las dos definiciones que la tarea existe para evitar | Ninguna: sigue habiendo **una** definición de «vigente», probada en sus tres bordes, y la comparten `RF-SP-026`, `RF-SP-031` y `RF-SP-032` |
| 2 | `T-02` no produjo `User.hasConsumerRole()`: la condición vive en `ConsumerStatus`, en `domain/security` | El agregado guarda identificadores de rol, no su clasificación — esa vive en el catálogo. Ponerlo en `User` habría obligado a cargar el catálogo dentro del agregado | Cuatro casos de uso comparten la condición, y **dos deciden en direcciones opuestas**: `RF-SP-032` exige que la persona SÍ sea consumidor y `RF-SP-033` que NO lo sea. Tenerla en un solo sitio es lo que impide que la quinta copia la invierta |
| 3 | La comprobación de `VAL-005` —fecha posterior al momento de la asignación— vive en el caso de uso y no en una anotación | `@Future` compara contra el reloj del sistema; con un reloj inyectable la prueba deja de depender de la hora a la que se ejecute | Es el único `400` del módulo que **no** sale del validador. El plan lo endosa de forma expresa: se decide mirando solo el cuerpo y el reloj, de modo que la frontera de `architecture.md` §7.3 se respeta aunque el punto de decisión sea otro |
| 4 | Se añade un rechazo que la tabla de errores no enumera: indicar `membershipId` en la asignación de **roles** cuando la persona ya tiene membresía | Cambiar la membresía es esta operación, con su propio permiso. Admitirlo desde `RF-SP-030` sería una edición encubierta que se salta esa autorización | Queda declarado por si conviene reabrirlo |
| 5 | `T-07`, `T-10` y `T-11` quedan **En curso** | De la auditoría de rechazos solo se comprueba lo que el plan exige que **no** deje fila; falta verificar que `EX-001` y `EX-002` sí dejen la suya con severidad Media, y el caso de la persona inactiva | Los rechazos se producen y son correctos; su registro no está fijado por prueba |

### Lo que sí quedó verificado

- El **borde** de la vigencia: una fecha exactamente igual al instante consultado ya **no** está vigente. Con `>=` habría un instante en que la membresía seguiría concediendo lo que ya expiró.
- `FA-002` frente a `FA-003`: repetir la petición idéntica no escribe ni audita; cambiar **solo** la fecha sí. Sin esa distinción, una interfaz que reenvía el formulario dejaría una fila de auditoría por pulsación.
- Enviar `endsAt` ausente sobre una membresía fechada la convierte en **indefinida**, que es un caso normal y no un olvido.
- Dos asignaciones simultáneas dejan **una** fila, ninguna produce `500`, y el resultado es una de las dos y nunca una mezcla.
- Los tres rechazos caen en sus **tres categorías** —`400`, `422` y `409`— y la membresía se comprueba **antes** que el rol de consumidor.
- La operación **no deja evento de seguridad**: la membresía es un dato comercial, no un permiso.

## 5. Definición de terminado

El requerimiento no está terminado hasta cumplir **todas** las condiciones de la constitución §16:

- [ ] Todas las tareas en estado `Hecha`. — tres en curso: `T-07`, `T-10` y `T-11`.
- [ ] Todos los criterios de aceptación con prueba automatizada en verde. — falta el caso de la persona inactiva y el registro de los rechazos.
- [x] `mvn verify` en verde en local. — 99 unitarias y 326 de integración, 24-08-2026.
- [x] Toda escritura emite su evento de auditoría, en la transacción que corresponde. — un evento de cambio con ambos niveles y ambas fechas; **ninguno** de seguridad, y así está probado.
- [x] Los endpoints nuevos declaran su permiso. — `users:assign-membership`.
- [x] El contrato OpenAPI coincide con el comportamiento real. — `OpenApiContractIT` fija el `PUT` y sus estados.
- [x] Documentación afectada actualizada en el mismo Pull Request. — `requirements.md` v0.39.0.
- [x] Matriz de trazabilidad actualizada.
- [ ] Pull Request aprobado por alguien distinto del autor e integrado.
