# PLAN — `RF-SP-037` Cambiar la propia contraseña

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-037` |
| Especificación | [`spec.md`](spec.md) |
| `spec.md` aprobada el | 21-08-2026 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 24-08-2026 |

---

## 1. Enfoque

Es la única de las tres operaciones sobre la credencial que ejecuta **el propio titular conociendo la vigente**, y esa condición es la que le da su forma. `RF-SP-038` la ejecuta un administrador sobre una cuenta ajena; `RF-SP-040`, el titular sin conocerla. Las tres sustituyen un hash y revocan sesiones, y las tres se equivocarían de reglas si se escribieran como una sola.

Dos garantías la sostienen y ninguna es decorativa.

**Exigir la contraseña vigente.** Sin ella, quien encuentre una sesión abierta —un portátil sin bloquear, un token robado— se apodera de la cuenta cambiando la credencial, y el titular queda fuera de la suya. Poseer la sesión no basta: hay que conocer el secreto.

**Revocar todas las sesiones, incluida la que ejecutó el cambio.** Es lo que hace que cambiar la contraseña sirva de algo cuando ya la robaron. Conservar la sesión actual es más cómodo, pero **el sistema no sabe si quien ejecutó el cambio era el titular**: si fue un atacante desde una sesión tomada, la sesión conservada sería la suya y la operación habría servido para expulsar al legítimo dueño.

Y una tercera pieza que parece menor y decide la seguridad de la operación: **los intentos con la contraseña vigente incorrecta cuentan para el bloqueo de la cuenta**, con el mismo umbral y el mismo contador que `RF-SP-034`. La spec eligió a conciencia protegerse de la apropiación —daño mayor y permanente— frente al bloqueo —temporal y reversible con `RF-SP-028`—.

Este requerimiento cierra además el ciclo que `RF-SP-034` abre: es **el único endpoint accesible** mientras la marca de cambio obligatorio esté puesta, y el único que la limpia.

## 2. Cambios de esquema

**Ninguno.**

`users.password_hash` y `users.must_change_password` los crea `V18` (`RF-SP-024`); `failed_attempts` y `locked_until`, `V26` (`RF-SP-034`); `refresh_tokens`, `V27`. Este requerimiento solo escribe sobre lo existente.

## 3. Componentes afectados

| Capa | Componente | Nuevo / Modificado | Responsabilidad |
|---|---|---|---|
| `domain` | `User` | Modificado | `changePassword(...)`: sustituye el hash, limpia la marca de cambio obligatorio y pone a cero el contador de fallos |
| `domain` | `PasswordPolicy` | Sin cambios | Componente de `RF-SP-024`. La política mínima vive ahí y no se redefine (`security.md` §3.2) |
| `domain` | `LockoutPolicy` | Sin cambios | Componente de `RF-SP-034`. El fallo aquí alimenta el **mismo** contador y el mismo umbral |
| `application` | `ChangeOwnPasswordService` | Nuevo | Caso de uso. `@Transactional`, orden de `plan.md` §4, revocación y auditoría |
| `application` | `PasswordHasher` | Sin cambios | Puerto de `RF-SP-024`, ampliado por `RF-SP-034` con la verificación |
| `application` | `CommonPasswordCatalog` | Sin cambios | Puerto de `RF-SP-024` |
| `application` | `SessionRevoker` | Sin cambios | Puerto de `RF-SP-028`, implementado por `RF-SP-034` |
| `application` | `AccessRevocationPublisher` | Sin cambios | Puerto de `RF-SP-028`: declara el instante desde el que los tokens de acceso de la persona dejan de admitirse |
| `api` | `AuthController` | Modificado | Añade `POST /api/v1/auth/password` |
| `api` | `ChangePasswordRequest` | Nuevo | DTO de entrada |

**Ningún componente de dominio nuevo.** La política la aporta `RF-SP-024`, el bloqueo `RF-SP-034` y la revocación `RF-SP-028`. Lo único propio es el orden en que se combinan, y eso vive en el caso de uso.

## 4. Contrato de API

| Método | Ruta | Descripción |
|---|---|---|
| `POST` | `/api/v1/auth/password` | Sustituye la contraseña de la persona autenticada |

**Petición**

```json
{
  "currentPassword": "...",
  "newPassword": "..."
}
```

**No lleva identificador de usuario, y su ausencia es la implementación de `CA-SP-328`.** El sujeto es siempre quien porta el token; no hay campo por el que dirigir la operación a un tercero, del mismo modo que `RF-SP-036` no puede cerrar la sesión de otro.

**Respuesta `204 No Content`**, sin cuerpo ni credencial alguna.

**Errores**

| Código | Cuándo | `error_code` |
|---|---|---|
| `400` | Falta la vigente o la nueva | `VAL-001`, `VAL-002` |
| `400` | La contraseña nueva no cumple la política (`EX-002`) | `VAL-004` |
| `400` | La contraseña nueva es igual a la vigente presentada (`EX-003`) | `VAL-005` |
| `401` | Token ausente o inválido | `AUTH-001` |
| `422` | La contraseña vigente presentada es incorrecta (`EX-001`) | `VAL-003` |
| `423` | La cuenta quedó bloqueada al alcanzar el umbral de fallos | `VAL-004` de `RF-SP-034` |
| `500` | Fallo no controlado | `ERR-500` |

Tres decisiones de esta tabla merecen su motivo:

**`EX-003` es `400` y `EX-001` es `422`**, aunque las dos hablen de contraseñas. La diferencia es la misma frontera de todo el módulo: que la nueva sea igual a la vigente **se decide comparando los dos campos del cuerpo**, sin leer nada; que la vigente sea correcta exige contrastar contra el hash almacenado.

**`EX-001` no es `401`, y esto importa en el cliente.** Un `401` le dice a cualquier cliente bien escrito que su sesión ya no vale, y reaccionaría descartándola y mandando a la persona a iniciar sesión — cuando lo único que ocurrió es que escribió mal su contraseña actual. La sesión sigue siendo válida; lo que no se puede procesar es la petición.

**Aquí sí se dice qué falló**, a diferencia de `RF-SP-034`. Quien hace esta petición ya está autenticado: no se le revela nada que no supiera.

**Orden de verificación**

1. Formato y obligatoriedad.
2. La contraseña nueva difiere de la vigente presentada.
3. La contraseña nueva cumple la política mínima.
4. La contraseña vigente coincide con la almacenada, de forma resistente a temporización.

El paso 4 va **el último** y es deliberado: es el único que consume el intento del contador de bloqueo. Ponerlo antes haría que una petición con una contraseña nueva mal formada gastara un intento sin necesidad, y bastarían cinco peticiones descuidadas de un cliente propio para bloquear la cuenta de su titular.

## 5. Autorización

| Endpoint | Permiso requerido |
|---|---|
| `POST /api/v1/auth/password` | **Ninguno más allá de estar autenticado** |

`requirements/sp.md` §9 lo declara así. No hay permiso porque no hay privilegio: cada quien cambia lo suyo.

**Es la excepción del `MustChangePasswordFilter`.** `RF-SP-034` §5 niega todo endpoint mientras el claim `mcp` esté en verdadero; **este es el único que debe seguir accesible**, porque es el que limpia la marca. Un filtro que lo negara también dejaría a la persona sin ninguna salida, y la cuenta quedaría inservible hasta que un administrador la restableciera — sobre una credencial que ese administrador acaba de fijar.

## 6. Auditoría

| Operación | Registro | Contenido |
|---|---|---|
| Cambio efectivo | `audit_security_log` | `event_type = 'PASSWORD_CHANGED'`, `severity = 'ALTA'`, `outcome = 'SUCCESS'`, `target_user_id` de la persona —que es también el actor— |
| Contraseña vigente incorrecta (`EX-001`) | `audit_security_log` | `event_type = 'PASSWORD_CHANGED'`, **`outcome = 'FAILURE'`**, `severity = 'MEDIA'` |
| Bloqueo alcanzado por fallos aquí | `audit_security_log` | `event_type = 'ACCOUNT_LOCKED'`, `severity = 'ALTA'`, igual que en `RF-SP-034` |
| Rechazo por política o por contraseña repetida (`400`) | — | **No se audita**: `ck_audit_error_log_status` rechaza el `400`, y no es un evento de seguridad sino un error de forma |
| Fallo no controlado `5xx` | `audit_error_log` | `error_type = 'UNHANDLED'`, `severity = 'ALTA'` |

**El intento fallido se registra como `PASSWORD_CHANGED` con `outcome = 'FAILURE'`, no como `LOGIN_FAILURE`.** El catálogo de `security.md` §8.1 es cerrado y no tiene un literal propio para esto, y la columna `outcome` existe exactamente para distinguir el éxito del fracaso de un mismo tipo de evento. Reutilizar `LOGIN_FAILURE` habría sido lo cómodo y habría **corrompido la señal que `RF-SP-014` lee para reconocer un ataque de fuerza bruta**: un patrón de fallos de inicio de sesión mezclado con fallos de cambio de contraseña ya no dice qué está ocurriendo.

**Ninguna de las dos contraseñas aparece en `detail`**, ni en claro ni transformada (`CA-SP-326`). Tampoco su longitud ni qué regla de la política incumplió: eso viaja en la respuesta, que solo lee quien la pidió.

## 7. Transaccionalidad

| Elemento | Transacción |
|---|---|
| Sustitución del hash, limpieza de la marca, contador a cero y revocación de todas las sesiones | **La misma** |
| Publicación del corte de tokens de acceso | Dentro de la misma, **antes** del commit |
| Evento `PASSWORD_CHANGED` de éxito | **Independiente**, `REQUIRES_NEW`, enganchada al commit |
| Evento de fallo y de bloqueo | **Independiente**, `REQUIRES_NEW`, **sin esperar al commit** |
| Incremento del contador de fallos | Transacción **propia**, que confirma aunque la petición termine en rechazo |

El contador de fallos necesita transacción propia por la misma razón que la auditoría de `RF-SP-034` §7: se incrementa **mientras** la operación se rechaza, y escrito dentro de la transacción principal el `rollback` lo borraría — dejando el bloqueo por intentos sin ningún efecto, que es la defensa entera de `CA-SP-389`.

La revocación va dentro de la transacción y no después: si falla, el cambio debe fallar con ella. Mismo argumento que `RF-SP-031` §7 — una contraseña cambiada con las sesiones antiguas vivas es un gesto vacío.

## 8. Impacto sobre otros módulos

- **`RF-SP-034`** debe exceptuar esta ruta en `MustChangePasswordFilter` (§5). Es la dependencia más fácil de olvidar y la que deja la cuenta sin salida.
- **`RF-SP-035`** recibe los tokens revocados con motivo `ACCESO_RETIRADO` y no debe tratarlos como reutilización (`CA-SP-391`). Es `EX-004` de aquel requerimiento.
- **`RF-SP-038`** y **`RF-SP-040`** dejan la cuenta en el estado que esta operación resuelve, y `RF-SP-040` limpia la marca por su cuenta.
- **`RF-SP-028`** aporta `SessionRevoker` y `AccessRevocationPublisher`.
- **Ninguna enmienda a documento transversal.** `security.md` §3.2 y §5.5 ya declaran la política, la marca de cambio obligatorio y la revocación al cambiar la contraseña.

## 9. Alternativas consideradas

| Alternativa | Por qué se descartó |
|---|---|
| Conservar la sesión que ejecutó el cambio | El sistema no sabe si quien lo ejecutó era el titular. Si fue un atacante desde una sesión tomada, la conservada sería la suya (`spec.md` §14, pregunta 1) |
| Guardar historial de contraseñas | Obliga a conservar credenciales que ya no están en uso, y cada hash almacenado es superficie de exposición. Solo se prohíbe reutilizar la **vigente** (`spec.md` §14, pregunta 2) |
| No contar los fallos para el bloqueo | Protege del bloqueo malicioso y desprotege de la apropiación. Se elige el daño mayor y permanente frente al temporal y reversible (`spec.md` §14, pregunta 3) |
| Verificar la contraseña vigente **antes** que la política | Gastaría un intento del contador por cada petición mal formada, y cinco descuidos de un cliente propio bloquearían la cuenta (§4) |
| Devolver `401` cuando la vigente es incorrecta | Un cliente bien escrito descartaría la sesión y mandaría a la persona a iniciar sesión, cuando lo único que hizo fue equivocarse al escribir |
| Registrar el fallo como `LOGIN_FAILURE` | Corrompe la señal de fuerza bruta que `RF-SP-014` lee. `outcome = 'FAILURE'` sobre `PASSWORD_CHANGED` distingue lo mismo sin mezclar |
| Aceptar que la contraseña nueva sea la vigente | Revocaría todas las sesiones sin haber cambiado nada |
| Admitir un identificador de usuario en el cuerpo | Convertiría esta operación en `RF-SP-038` sin su permiso ni sus reglas. `CA-SP-328` lo prohíbe |
| Incrementar el contador dentro de la transacción principal | El `rollback` lo borraría y el bloqueo por intentos no tendría efecto (§7) |

## 10. Riesgos

| Riesgo | Impacto | Mitigación |
|---|---|---|
| `MustChangePasswordFilter` niega también esta ruta y la cuenta queda sin salida | **Alto** | §5 y §8 lo declaran; `CA-SP-330` de `RF-SP-038` y `T-06` lo prueban con la marca puesta |
| El contador de fallos se escribe en la transacción principal y el `rollback` lo borra | **Alto** | Transacción propia (§7); `CA-SP-389` lo verifica |
| La revocación se saca de la transacción «para no alargarla» | **Alto** | Si falla, el cambio debe fallar. Mismo criterio que `RF-SP-031` §7 |
| Se conserva la sesión actual por comodidad | **Alto** | `CA-SP-324` exige que caiga también la que hizo el cambio |
| Alguna de las dos contraseñas acaba en un registro | **Alto** | `PasswordHash` de `RF-SP-024` enmascara su `toString()`; `CA-SP-326` lo verifica sobre todos los registros |
| Un cliente propio agota el contador con peticiones mal formadas | Medio | El orden de §4 lo evita: la vigente se comprueba la última |
| La persona no puede volver a entrar tras el cambio por no recordar la nueva | Bajo | Riesgo de uso, no de diseño. La salida es `RF-SP-038` o `RF-SP-040` |

## 11. Estrategia de prueba

| Criterio | Nivel | Qué verifica |
|---|---|---|
| `CA-SP-319` | API | Vigente correcta y nueva conforme: `204` |
| `CA-SP-320` | Integración | Se autentica con la nueva y **no** con la anterior |
| `CA-SP-321` | API + Integración | Vigente incorrecta: `422` y evento con `outcome = 'FAILURE'` |
| `CA-SP-322` | API | Política incumplida: `400` indicando **qué** regla |
| `CA-SP-323` | API | Nueva igual a la vigente: `400` |
| `CA-SP-324` | Integración | **Todos** los refresh tokens revocados, **incluido el de la sesión que hizo el cambio** |
| `CA-SP-325` | Integración | El hash almacenado es Argon2id y no es reversible |
| `CA-SP-326` | Integración | Ningún registro contiene ninguna de las dos contraseñas |
| `CA-SP-327` | Integración | Evento `PASSWORD_CHANGED` con severidad alta |
| `CA-SP-389` | Integración | Cinco intentos con la vigente incorrecta **bloquean la cuenta**, con el mismo umbral que `RF-SP-034` |
| `CA-SP-390` | Integración | Se admite una contraseña usada antes, siempre que no sea la vigente |
| `CA-SP-391` | Integración | La revocación queda con motivo `ACCESO_RETIRADO` y `RF-SP-035` no la trata como reutilización |
| `CA-SP-328` | API | El endpoint **no admite** ningún identificador de usuario en el cuerpo |

Casos límite de `spec.md` §13 con prueba propia (Art. VII.3):

| Caso | Nivel | Qué verifica |
|---|---|---|
| Cambio con la marca de cambio obligatorio puesta | **API** | La ruta sigue accesible con `mcp` en verdadero, y la marca queda limpia. Es la prueba que impide dejar la cuenta sin salida |
| Cambios concurrentes desde dos sesiones | Integración | El segundo encuentra la vigente ya sustituida y es rechazado por `EX-001`. El resultado es correcto aunque el mensaje desconcierte |
| Persona bloqueada con sesión todavía válida | API | El cambio se rechaza: una cuenta bloqueada no opera |
| Contraseña nueva igual al nombre de usuario o al correo | API | Se rechaza por `PasswordPolicy` de `RF-SP-024`, que ya lo prohíbe desde `security.md` §3.2 |

**La prueba del cambio con la marca puesta es la más importante de las cuatro** y no es evidente que lo sea. Verifica una interacción entre dos requerimientos —el filtro de `RF-SP-034` y este endpoint— cuyo fallo deja a la persona sin ninguna vía de recuperación por sí misma, sobre una cuenta cuya credencial acaba de fijar otra persona.
