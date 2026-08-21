# SPEC — `RF-SP-037` Cambiar la propia contraseña

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-037` |
| Módulo | `SP` — Sistema Principal |
| Estado | **Aprobada** |
| Autor | Responsable técnico |
| Aprobada por | Responsable técnico |
| Fecha de aprobación | 21-08-2026 |

---

## 1. Objetivo

Permitir que una persona sustituya su propia contraseña por otra, probando antes que conoce la vigente.

## 2. Contexto

Es la operación con la que alguien recupera el control de su cuenta: la usa quien sospecha que su contraseña se filtró, quien entró por primera vez con la que le dieron, y quien simplemente quiere otra. Y es la única de las dos operaciones sobre la credencial que **ejecuta el propio titular**; la otra, `RF-SP-038`, la ejecuta un administrador sobre una cuenta ajena y tiene reglas distintas por eso mismo.

Exigir la contraseña vigente es lo que sostiene toda la operación. Sin ese requisito, quien encontrara una sesión abierta —un portátil sin bloquear, un token robado— podría apoderarse de la cuenta cambiando la contraseña, y el titular legítimo quedaría fuera de la suya. Con él, poseer la sesión no basta: hay que conocer el secreto.

La segunda garantía es la revocación. `security.md` §5.5 obliga a que un cambio de contraseña **revoque todos los refresh tokens** de la persona. Es lo que hace que cambiarla sirva de algo cuando ya la robaron: si las sesiones abiertas sobrevivieran al cambio, quien la hubiera robado seguiría dentro por mucho que el titular la cambiara, y la operación sería un gesto vacío.

Y una condición del sistema que conviene repetir: el sistema **no puede recuperar** una contraseña. Se guarda con Argon2id y solo puede sustituirse, nunca leerse (`security.md` §3.2).

## 3. Actores

| Actor | Rol en esta funcionalidad |
|---|---|
| Cualquier persona autenticada | Cambia su propia contraseña |

No hay permiso asociado más allá de estar autenticado (`requirements/sp.md` §6.1). Nadie cambia la contraseña de otro por este camino.

## 4. Alcance

### 4.1 Incluye

- Sustitución de la contraseña de la persona autenticada, previa verificación de la vigente.
- Revocación de todas sus sesiones abiertas.
- Registro del cambio en la auditoría de seguridad.

### 4.2 No incluye

- Cambiar la contraseña de otra persona → `RF-SP-038`.
- Recuperar la contraseña vigente: es imposible por diseño.
- El flujo de «olvidé mi contraseña» sin conocer la vigente → `RF-SP-040`, registrado el 21-08-2026 y todavía sin especificar. Hasta que exista, quien la olvida depende de que un administrador se la restablezca (`RF-SP-038`).
- Cambiar el nombre de usuario o el correo → `RF-SP-027`.

## 5. Reglas de negocio aplicables

| ID | Regla | Origen |
|---|---|---|
| `RNF-SEG-006` | Los eventos de seguridad quedan registrados en la auditoría de seguridad | `security.md` §11 |

La política de contraseña —longitud mínima, verificación contra contraseñas comunes y prohibición de reutilizar la vigente— está definida en `security.md` §3.2 y no se redefine aquí.

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción | Restricción de negocio |
|---|---|---|---|
| Contraseña vigente | Sí | Credencial actual de la persona | Debe coincidir con la registrada |
| Contraseña nueva | Sí | Credencial que la sustituye | Debe cumplir la política mínima y **no puede ser la vigente** |

Ninguna de las dos se registra en ningún log, ni en claro ni transformada (Art. IV.8, Art. XV.5).

### 6.2 Salida

| Dato | Descripción |
|---|---|
| Confirmación | Resultado de la operación, sin cuerpo de datos ni credencial alguna |

## 7. Precondiciones y postcondiciones

**Precondiciones**

- La persona está autenticada.
- La contraseña vigente presentada coincide con la registrada.
- La contraseña nueva cumple la política mínima y difiere de la vigente.

**Postcondiciones**

- La contraseña queda sustituida y almacenada con Argon2id.
- **Todos** los refresh tokens de la persona quedan revocados con motivo `ACCESO_RETIRADO`, incluido el de la sesión desde la que se hizo el cambio.
- La persona debe autenticarse de nuevo con la contraseña nueva.
- Si la cuenta tenía marcado el cambio obligatorio de contraseña, la marca queda limpia.
- Queda constancia en la auditoría de seguridad con severidad alta, **sin ningún dato de ninguna de las dos contraseñas**.

## 8. Flujo principal

1. La persona autenticada solicita cambiar su contraseña y presenta la vigente y la nueva.
2. El sistema verifica que la contraseña vigente coincida con la registrada, de forma resistente a ataques de temporización.
3. El sistema verifica que la contraseña nueva cumpla la política mínima.
4. El sistema verifica que la contraseña nueva no sea la vigente.
5. El sistema sustituye la credencial.
6. El sistema revoca todos los refresh tokens de la persona, con motivo `ACCESO_RETIRADO`, de modo que `RF-SP-035` no los trate como reutilización sospechosa.
7. El sistema registra el cambio en la auditoría de seguridad, con severidad alta.
8. El sistema confirma la operación.

## 9. Flujos alternativos

Ninguno. La operación no admite variantes: o se cumplen todas las condiciones, o se rechaza.

## 10. Excepciones

### EX-001 — Contraseña vigente incorrecta

**Condición:** la contraseña vigente presentada no coincide con la registrada.
**Respuesta del sistema:** rechaza el cambio sin tocar nada e informa que la contraseña actual no es correcta. Registra el intento fallido en la auditoría de seguridad: alguien intentando cambiar una contraseña sin conocerla es exactamente el escenario contra el que existe la verificación. El intento cuenta además para el bloqueo de la cuenta, con el mismo umbral de `RF-SP-034`.

Aquí **sí** se dice qué falló, a diferencia de `RF-SP-034`: quien hace esta petición ya está autenticado, de modo que no se le revela nada que no supiera.

### EX-002 — Contraseña nueva que no cumple la política

**Condición:** la contraseña nueva es más corta que el mínimo, aparece en la lista de contraseñas comunes o incumple cualquier otra regla de la política.
**Respuesta del sistema:** rechaza el cambio e informa **qué** regla incumple, sin reproducir la contraseña en el mensaje ni en ningún registro.

### EX-003 — La contraseña nueva es la vigente

**Condición:** ambas contraseñas coinciden.
**Respuesta del sistema:** rechaza el cambio e informa que la contraseña nueva debe ser distinta de la actual. Aceptarlo revocaría todas las sesiones sin haber cambiado nada.

## 11. Validaciones

| ID | Validación | Mensaje esperado |
|---|---|---|
| `VAL-001` | Contraseña vigente obligatoria | Debe indicar su contraseña actual. |
| `VAL-002` | Contraseña nueva obligatoria | Debe indicar la contraseña nueva. |
| `VAL-003` | Contraseña vigente correcta | La contraseña actual no es correcta. |
| `VAL-004` | Contraseña nueva conforme a la política mínima | La contraseña no cumple la política de seguridad. |
| `VAL-005` | Contraseña nueva distinta de la vigente | La contraseña nueva debe ser distinta de la actual. |

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-SP-319` | El sistema sustituye la contraseña cuando la vigente es correcta y la nueva cumple la política |
| `CA-SP-320` | La persona puede autenticarse con la contraseña nueva y no con la anterior |
| `CA-SP-321` | El sistema rechaza el cambio cuando la contraseña vigente presentada es incorrecta, y registra el intento |
| `CA-SP-322` | El sistema rechaza una contraseña nueva que no cumple la política, indicando qué regla incumple |
| `CA-SP-323` | El sistema rechaza que la contraseña nueva sea igual a la vigente |
| `CA-SP-324` | Todos los refresh tokens de la persona quedan revocados tras el cambio, incluido el de la sesión que lo hizo |
| `CA-SP-325` | La credencial queda almacenada con Argon2id y no es recuperable |
| `CA-SP-326` | Ningún registro contiene ninguna de las dos contraseñas, ni en claro ni transformada |
| `CA-SP-327` | El sistema registra el cambio en la auditoría de seguridad con severidad alta |
| `CA-SP-389` | Los intentos con la contraseña vigente incorrecta **cuentan** para el bloqueo de la cuenta, con el mismo umbral que `RF-SP-034` |
| `CA-SP-390` | El sistema admite una contraseña que la persona ya usó antes, siempre que no sea la vigente: no hay historial |
| `CA-SP-391` | La revocación queda registrada con motivo `ACCESO_RETIRADO`, y `RF-SP-035` no la trata como reutilización sospechosa |
| `CA-SP-328` | El sistema no expone ninguna operación que permita a una persona cambiar la contraseña de otra por este camino |

## 13. Casos límite

- **La sesión desde la que se cambia la contraseña también cae:** es deliberado. Revocar todo menos la sesión actual dejaría abierta la posibilidad de que la sesión conservada fuera la del atacante.
- **Cambio de contraseña con un cambio obligatorio pendiente:** es el uso previsto tras `RF-SP-024` o `RF-SP-038`. La marca queda limpia y la persona opera con normalidad.
- **Contraseña nueva igual a una anterior, pero no a la vigente:** se admite. `security.md` §3.2 solo prohíbe reutilizar la **vigente**; no hay historial de contraseñas. Ver la pregunta 2.
- **Cambios concurrentes desde dos sesiones:** ambos verifican la vigente; el segundo la encontrará ya sustituida y será rechazado por `EX-001`. El resultado es correcto, aunque el mensaje resulte desconcertante.
- **Persona bloqueada con sesión todavía válida:** no debería ocurrir, porque el bloqueo revoca sus tokens. Si ocurriera, el cambio debe rechazarse: una cuenta bloqueada no opera.
- **Contraseña nueva idéntica al nombre de acceso o al correo:** depende de que la política declarada lo prohíba, y hoy `security.md` §3.2 no lo dice. Es la misma laguna anotada en `RF-SP-024`.
- **Intentos repetidos con la contraseña vigente incorrecta:** conviene decidir si cuentan para el bloqueo de la cuenta. Ver la pregunta 3.

## 14. Preguntas abiertas

Ninguna. Las cuatro se resolvieron el 21-08-2026, antes de aprobar la especificación. La cuarta dio lugar a un requerimiento nuevo, `RF-SP-040`.

| # | Pregunta | Resolución |
|---|---|---|
| 1 | ¿Se revocan todas las sesiones, o todas menos la actual? | **Todas, incluida la actual**, que es lo que `security.md` §5.5 ya exigía. Conservar la de quien ejecutó el cambio es más cómodo, pero el sistema **no sabe si quien lo ejecutó era el titular**: si fue un atacante desde una sesión tomada, la sesión conservada sería la suya y el cambio habría servido para expulsar al legítimo dueño en lugar de al intruso. Se asume la molestia de volver a entrar. La revocación se registra con motivo `ACCESO_RETIRADO`, no como rotación, para que `RF-SP-035` no la confunda con una reutilización sospechosa |
| 2 | ¿Debe existir historial de contraseñas? | **No.** Solo se prohíbe reutilizar la **vigente**, como declara `security.md` §3.2. Guardar los últimos hashes impediría alternar entre dos contraseñas —que es lo que hace la gente cuando se le obliga a cambiarla—, pero exige **conservar credenciales que ya no están en uso**, que es exactamente lo que el resto del diseño evita: cada hash almacenado es superficie de exposición, y aquí serían varios por persona sin que nadie los use nunca. La defensa queda en la política mínima y en la lista de contraseñas comunes. `CA-SP-390` deja verificado que una contraseña anterior se admite |
| 3 | ¿Los intentos fallidos aquí cuentan para el bloqueo de la cuenta? | **Sí, con el mismo umbral que `RF-SP-034`.** Los dos escenarios en juego son opuestos y hay que elegir cuál pesa más: contarlos protege contra quien tomó una sesión ajena y prueba contraseñas para apoderarse de la cuenta; no contarlos evita que alguien con acceso momentáneo a una sesión pueda bloquear la cuenta de su titular a propósito. **Se elige protegerse de la apropiación**, que es el daño mayor y permanente, frente al bloqueo, que es temporal y reversible con `RF-SP-028` |
| 4 | ¿Debe existir un flujo de «olvidé mi contraseña»? | **Sí, y se registra como `RF-SP-040`**, todavía sin especificar. Hoy quien la olvida depende de que un administrador ejecute `RF-SP-038`, lo que significa que **ese administrador conoce temporalmente la credencial de otra persona** — un riesgo acotado por el indicador de cambio obligatorio, pero no eliminado. El autoservicio exige un canal de correo y un token de un solo uso que ningún requerimiento cubre, de modo que no se resuelve aquí: se deja registrado para que la necesidad no se pierda y para que su ausencia sea visible en la matriz |
