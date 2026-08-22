# PLAN — `RF-SP-033` Retirar la membresía de un usuario

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-033` |
| Especificación | [`spec.md`](spec.md) |
| `spec.md` aprobada el | 21-08-2026 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 22-08-2026 |

---

## 1. Enfoque

Es el requerimiento más pequeño del módulo y el que más fácil es implementar mal, porque su nombre promete una operación corriente y lo que hace es lo contrario: **retira la membresía exactamente cuando la persona no es consumidora**, y la rechaza cuando lo es.

`RN-SP-013` no admite membresía sin rol de consumidor. `RN-SP-018` no admite rol de consumidor sin membresía. Juntas hacen que el nivel no sea un atributo opcional de un cliente sino parte de lo que significa serlo: se conceden juntos —`RF-SP-024`, `RF-SP-030`— y se sueltan juntos —`RF-SP-031`—. Este requerimiento no participa de ninguna de las dos cosas.

Existe para lo único que queda: **corregir la incoherencia**. Una persona con membresía y sin ningún rol `CONSUMIDOR` no debería poder existir, pero puede aparecer —una migración, una corrección manual sobre la base de datos, un defecto que se arregla después— y sin esta operación la única salida sería otra intervención manual, que es justo lo que produjo el problema.

Su uso es **excepcional por diseño**, y de ahí sale casi todo lo demás: no hay cuerpo, no hay motivo, no hay elección de qué membresía retirar —solo puede haber una— y la respuesta no lleva datos. Lo único que tiene sustancia es `EX-001`, que es la excepción que define el requerimiento: sin ella, esta operación sería una vía para producir el estado que `RN-SP-018` prohíbe.

## 2. Cambios de esquema

**Ninguno.**

`user_memberships` la crea `V20__create_user_memberships.sql` (`RF-SP-024`) y este requerimiento solo borra filas de ella.

**El retiro es un `DELETE` de la fila**, no un `UPDATE`. La clave primaria de `user_memberships` es `user_id` (`requirements/sp.md` §10.12): no existe un estado «sin membresía» que escribir, existe la ausencia de fila. Es la misma decisión que toma `RF-SP-031` al aplicar la cascada de `RN-SP-015`, y por el mismo motivo — de hecho **es la misma escritura**, y §3 la comparte.

## 3. Componentes afectados

| Capa | Componente | Nuevo / Modificado | Responsabilidad |
|---|---|---|---|
| `domain` | `User.hasConsumerRole()` | Sin cambios | Componente compartido de `RF-SP-032`. Aquí se usa **negado**: `EX-001` rechaza si devuelve verdadero |
| `domain` | `UserMembership` | Sin cambios | Agregado de `RF-SP-032`. Aporta el estado que viaja en el `snapshot` de la auditoría |
| `domain` | `UserRepository` | Sin cambios | Puerto de `RF-SP-024`, ampliado por `RF-SP-031` con el borrado de la asignación de membresía. **Este requerimiento reutiliza esa misma operación** |
| `application` | `RevokeUserMembershipService` | Nuevo | Caso de uso. `@Transactional`, verifica `EX-001` y emite la auditoría |
| `infrastructure` | `JpaUserRepository` | Sin cambios | El `DELETE` sobre `user_memberships` lo aporta `RF-SP-031` |
| `api` | `UserController` | Modificado | Añade `DELETE /api/v1/users/{id}/membership` |

**No hay DTO de entrada**, y su ausencia es la implementación de `spec.md` §6.1: no se indica cuál membresía se retira porque solo puede haber una, y no se declara motivo porque es la eliminación de una asociación (Art. V.13). Un cuerpo vacío que nadie lee sería una invitación a que alguien empiece a mandar algo por él.

Este requerimiento **no aporta un solo componente de dominio propio**. Es la señal de que está bien situado: si necesitara lógica que ningún otro tiene, sería que está haciendo algo más que corregir un estado.

## 4. Contrato de API

| Método | Ruta | Descripción |
|---|---|---|
| `DELETE` | `/api/v1/users/{id}/membership` | Retira la membresía del usuario |

**`DELETE` se conserva aquí, y no le alcanza la enmienda de `RF-SP-031` §4.** Aquella cambió `DELETE` por `POST …/revocations` porque su lista de roles viaja en el cuerpo y RFC 9110 no le define semántica; esta operación **no lleva cuerpo**, de modo que el problema no existe. El recurso `/membership` es único y direccionable, y borrarlo es exactamente lo que `DELETE` significa.

**Sin petición.**

**Respuesta `204 No Content`** — `spec.md` §6.2 declara la salida como «confirmación, sin cuerpo de datos», y `204` es la forma de decirlo. No se devuelve `UserResponse`: la persona no cambió, y devolverla invitaría a leer de aquí un estado que se consulta con `RF-SP-026`.

**Errores**

| Código | Cuándo | `error_code` |
|---|---|---|
| `400` | Identificador malformado | `VAL-001` |
| `401` | Token ausente o inválido | `AUTH-001` |
| `403` | El actor no posee `users:assign-membership` | `AUTH-002` |
| `404` | El usuario no existe o está eliminado (`EX-002`) | `VAL-002` |
| `409` | La persona porta al menos un rol `CONSUMIDOR` (`EX-001`) | `RN-SP-018` |
| `500` | Fallo no controlado | `ERR-500` |

**El cuerpo del `409` debe explicar las dos salidas reales**: bajar de nivel con `RF-SP-032`, o dejar de ser consumidor con `RF-SP-031`, que retira la membresía por su cuenta. `spec.md` `EX-001` lo exige de forma explícita, y es lo que impide que quien recibe el error concluya que el sistema tiene un defecto.

`FA-001` —la persona no tenía membresía— **no es un error**: devuelve `204` sin escribir nada ni auditar. La operación es idempotente y su resultado, persona sin membresía, ya se cumplía.

**Orden de verificación**

1. Formato del identificador.
2. Usuario existente y no eliminado.
3. La persona **no** porta ningún rol de clasificación `CONSUMIDOR`.

## 5. Autorización

| Endpoint | Permiso requerido |
|---|---|
| `DELETE /api/v1/users/{id}/membership` | `users:assign-membership` |

Es el **mismo permiso** que la asignación, como declara `requirements/sp.md` §9. Separarlos no tendría sentido: quien puede fijar el nivel de alguien puede corregir una incoherencia de nivel, y esta operación no puede hacer nada que la otra no pueda deshacer.

## 6. Auditoría

| Operación | Registro | Contenido |
|---|---|---|
| Membresía retirada | `audit_deletion_log` | `deletion_type = 'ASSOCIATION'`, entidad `users`, con `snapshot` de **cuál era la membresía y hasta cuándo estaba vigente**. **Sin motivo** (Art. V.13) |
| La persona no tenía membresía (`FA-001`) | — | **Ningún evento**: nada cambió (`CA-SP-284`) |
| Rechazo por `EX-001` (`409`) | `audit_error_log` | `resource = 'users'`, `error_type = 'BUSINESS_RULE'`, `severity = 'MEDIA'` |
| Rechazo `404` por `EX-002` y `400` de formato | — | **No se audita**: `ck_audit_error_log_status` rechaza ambos |
| Denegación `403` por `AUTH-002` | `audit_security_log` | `event_type = 'AUTHORIZATION_DENIED'`, `severity = 'MEDIA'`. Lo emite la capa de seguridad compartida |
| Fallo no controlado `5xx` | `audit_error_log` | `error_type = 'UNHANDLED'`, `severity = 'ALTA'` |

**El `snapshot` es lo único que hace útil esta fila.** Sin él, el evento diría que a alguien se le retiró «una membresía» y nadie recordaría cuál ni hasta cuándo la tenía — y como esta operación solo actúa sobre estados que no deberían existir, la fila de auditoría es a menudo la **única** constancia de que ese estado existió. `CA-SP-285` lo verifica.

**Ningún evento de seguridad**, por el mismo motivo que en `RF-SP-032` §6: el catálogo de `security.md` §8.1 es cerrado y la membresía no concede permisos del sistema.

## 7. Transaccionalidad

| Elemento | Transacción |
|---|---|
| `DELETE` de `user_memberships` y su evento en `audit_deletion_log` | **La misma** (Art. V.14) |
| Auditoría del rechazo | **Independiente**, `REQUIRES_NEW` |
| Revocación de sesiones | **No aplica**: la membresía no viaja en el token ni afecta a los permisos efectivos |

Nada más. Es el requerimiento con la transaccionalidad más simple del módulo, y conviene que siga siéndolo: cualquier cosa que se le añada aquí es señal de que se le está dando un uso que no le corresponde.

## 8. Impacto sobre otros módulos

- **`RF-SP-031`** aporta el `DELETE` sobre `user_memberships` y `RF-SP-032` aporta `UserMembership` y `User.hasConsumerRole()`. Este requerimiento no crea nada propio (§3).
- **`RF-SP-031` es la puerta de salida real** del estado de consumidor, no esta operación. Su `FA-003` hace en cascada lo que aquí se rechaza, y las dos cosas son coherentes: allí el rol desaparece y aquí permanece.
- **`RF-SP-029`** ya retira la membresía al eliminar a una persona, de modo que un usuario eliminado nunca llega aquí (`spec.md` §13).
- **`RF-SP-026`** es donde se consulta el estado resultante. Esta operación devuelve `204` precisamente para no convertirse en una segunda vía de lectura.
- **Ninguna enmienda a documento transversal.** La ruta de `requirements/sp.md` §9 se conserva tal cual (§4).

## 9. Alternativas consideradas

| Alternativa | Por qué se descartó |
|---|---|
| Fundirlo con `RF-SP-032`, admitiendo una membresía vacía | Oculta dos operaciones con reglas **opuestas** bajo un endpoint: aquella exige rol de consumidor y esta exige lo contrario. Fundirlas haría imposible declarar `EX-001` (`spec.md` §14, pregunta 2) |
| `POST …/revocations`, por simetría con `RF-SP-031` | Aquella cambió de verbo porque su lista viaja en el cuerpo. Esta no lleva cuerpo, de modo que el motivo no aplica y `DELETE` es el verbo correcto (§4) |
| Devolver `UserResponse` en lugar de `204` | Invita a leer de aquí un estado que se consulta con `RF-SP-026`, y convierte una operación correctiva en una vía de lectura |
| Admitir el retiro también a un consumidor | Sería una vía para producir el estado que `RN-SP-018` prohíbe. Es literalmente lo contrario de para lo que existe el requerimiento |
| Tratar `FA-001` como `404` | El resultado buscado —persona sin membresía— ya se cumple. Un error diría que la operación no se pudo hacer cuando no hacía falta hacerla |
| Exigir motivo | Es la eliminación de una asociación, y el «por qué» ya está en el evento: qué membresía, de quién, quién y cuándo (Art. V.13, `RN-SP-005`) |
| Suprimir el requerimiento por ser excepcional | Sin él, la única salida de un estado inconsistente sería otra intervención manual sobre los datos — exactamente lo que produjo el problema (`spec.md` §2) |
| Conservar un histórico de membresías retiradas | Se reconstruye desde la auditoría de cambios y de eliminación, y con la fecha de fin se ve hasta cuándo estuvo vigente. Queda como riesgo (§10) |

## 10. Riesgos

| Riesgo | Impacto | Mitigación |
|---|---|---|
| `EX-001` se implementa al revés —rechazar a quien no es consumidor— por parecer lo natural | **Alto** | Es el defecto más probable del requerimiento. `CA-SP-281` y `CA-SP-374` prueban las **dos** direcciones, y `T-02` las cubre antes que nada |
| El `snapshot` se escribe sin la membresía o sin su vigencia | **Alto** | Es a menudo la única constancia de que el estado incoherente existió. `CA-SP-285` lo verifica |
| El endpoint se usa como forma corriente de quitar el nivel a un cliente | Medio | `EX-001` lo impide por construcción, y su cuerpo explica las dos salidas reales |
| Se añade lógica propia y el requerimiento deja de ser correctivo | Medio | §3 declara que no aporta ningún componente de dominio. Necesitar uno sería la señal de que está haciendo algo más |
| «¿Qué nivel tenía esta persona en marzo?» pasa a ser consulta habitual | Bajo | Anotado en `spec.md` §14 pregunta 3: el día que se facture por periodos, la salida es una tabla de histórico con su política de retención, no reconstruirlo desde la auditoría cada vez |

## 11. Estrategia de prueba

| Criterio | Nivel | Qué verifica |
|---|---|---|
| `CA-SP-281` | Integración | La fila desaparece de `user_memberships` cuando la persona no porta ningún rol de consumidor |
| `CA-SP-374` | API | Quien porta un rol de consumidor recibe `409`, y el cuerpo cita **las dos** salidas reales |
| `CA-SP-282` | Integración | Los roles y los permisos efectivos no cambian |
| `CA-SP-284` | Integración | Sin membresía previa devuelve `204`, sin error y **sin evento** |
| `CA-SP-285` | Integración | El `snapshot` de `audit_deletion_log` conserva la membresía y su vigencia |
| `CA-SP-286` | API | El endpoint no admite motivo |
| `CA-SP-287` | Integración | La membresía sigue en la cadena y puede volver a asignarse |
| `CA-SP-288` | API | Un actor sin `users:assign-membership` recibe `403` |

`CA-SP-283` está **retirado** por `spec.md` §12 y su número queda consumido. No se reutiliza.

Casos límite de `spec.md` §13 con prueba propia (Art. VII.3):

| Caso | Nivel | Qué verifica |
|---|---|---|
| Persona con membresía **vencida** y sin rol de consumidor | Integración | Se retira igual: vencida o vigente, la asignación sobra |
| Retiro concurrente con la asignación de un rol de consumidor | **Integración concurrente** | Se serializan sobre el usuario. Si el rol va primero, el retiro devuelve `409`; si el retiro va primero, la asignación exige indicar membresía. **Ningún orden deja una cuenta incoherente**, que es lo que la prueba tiene que demostrar |
| Persona inactiva o bloqueada | Integración | Admite el retiro |
| Retirar la membresía superior de la cadena | Integración | Sin particularidad: el nivel no interviene |

La prueba concurrente es la que más valor tiene de las cuatro, y es la única del requerimiento que no es trivial: verifica que las dos operaciones que forman el par —esta y `RF-SP-030`— no pueden entrelazarse hasta producir el estado que `RN-SP-018` declara inexistente. Ejecutarla en un solo orden no prueba nada; hay que ejecutar los dos.
