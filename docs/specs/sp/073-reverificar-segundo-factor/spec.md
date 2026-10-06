# SPEC — `RF-SP-073` Reverificar el segundo factor antes de una operación sensible

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-073` |
| Módulo | `SP` — Sistema Principal |
| Versión | 0.1.0 |
| Estado | **Aprobada** |
| Autor | Responsable técnico |
| Aprobada por | Responsable del proyecto |
| Fecha de aprobación | 06-10-2026 |

!!! info "Qué va en este documento"

    **Qué debe pasar, y por qué.** Nada más.

    **Prueba de pertenencia:** si un cambio de tecnología lo invalidaría, no pertenece aquí — va a `plan.md`. No se nombran tablas, clases, endpoints ni librerías.

---

## 1. Objetivo

Que las operaciones que mueven dinero o cambian quién puede qué **no se puedan hacer solo con una sesión abierta**: que exijan haber presentado el código de la app autenticadora **hace poco**. Y dar a la persona la forma de presentarlo sin cerrar su sesión.

---

## 2. Contexto

Una sesión dura hasta siete días renovándose sola. Si alguien la roba —un equipo desbloqueado, un token copiado—, el segundo factor del inicio de sesión ya no le estorba: la sesión ya está abierta. El responsable del proyecto pidió el 06-10-2026 que las operaciones sensibles **vuelvan a pedir el código**, y nombró expresamente **la configuración de los permisos de los roles** ([`security.md`](../../../security.md) v0.105.0 §3.3 y §4.4).

### 2.1 Lo que este requerimiento decide

| Decisión | Qué se decidió |
|---|---|
| **Qué es sensible lo dice el catálogo de permisos** | Cada permiso lleva una marca. Hoy hay dieciocho marcados (`security.md` §4.4): administrar permisos y roles, restablecer contraseñas y factores ajenos, eliminar personas, confirmar y rechazar pagos, aprobar retiros, pagar comisiones, ajustar puntos y fijar conversiones y tasas de puntos. La lista crece por migración, no por la interfaz |
| **«Hace poco» son cinco minutos** | Bastan para asignar varios permisos o confirmar varios pagos seguidos sin teclear el código cada vez, y son poco tiempo para quien encuentre una sesión abandonada. El número vive en configuración |
| **Reverificar no cierra ni renueva la sesión** | Se presenta el código y la sesión sigue, ahora con la prueba fresca |
| **Renovar la sesión no renueva la prueba** | La renovación automática no es presentar un código |
| **Sin factor activo no hay operación sensible** | Quien no tenga app autenticadora no puede confirmar un pago, la exija su rol o no |
| **Primero el permiso, después la prueba** | Quien no puede hacer la operación recibe «no tiene permiso», no «reverifique»: lo contrario le enseñaría qué operaciones existen |
| **Los fallos cuentan para el bloqueo** | Como al entrar. Y si el bloqueo llega por aquí, **se cierran todas las sesiones de la persona**: quien falla cinco veces con una sesión en la mano es, con toda probabilidad, quien la robó |

---

## 3. Actores

| Actor | Rol en esta funcionalidad |
|---|---|
| Cualquier persona con el factor activo y el permiso de reverificar | Presenta el código para refrescar la prueba |
| Cualquier persona que intenta una operación sensible | Recibe la petición de reverificar si su prueba no es reciente |

---

## 4. Alcance

### 4.1 Incluye

- Presentar el código —o uno de recuperación— y recibir una credencial de acceso con la prueba fresca.
- Exigir la prueba reciente en las operaciones marcadas, con una respuesta que diga qué falta y dónde resolverlo.
- Publicar en el catálogo de permisos cuáles son sensibles, para que la interfaz pida el código **antes** de intentar.

### 4.2 No incluye

- **Decidir qué es sensible por la interfaz.** Es una migración.
- **Pedir el código en cada operación, sin ventana.**
- **Aprobación por una segunda persona.**

---

## 5. Reglas de negocio aplicables

| Regla | Cómo aplica |
|---|---|
| `RN-SP-063` | Las operaciones marcadas exigen prueba de cinco minutos o menos; sin factor no se hacen |
| `RN-SP-060` | El código presentado vale una vez |
| `RN-SP-061` | Un código de recuperación vale una vez y se avisa |
| `security.md` §3.2 | Los fallos consumen el bloqueo de la cuenta |

---

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción |
|---|---|---|
| Código | Sí | **O** el de seis dígitos, **o** uno de recuperación. Uno y solo uno |

### 6.2 Salida

Una **credencial de acceso nueva**, con los mismos roles y avisos que la anterior y la prueba en el instante actual; su vigencia; **hasta cuándo vale la prueba**; y, si se usó un código de recuperación, cuántos quedan.

---

## 7. Precondiciones y postcondiciones

| Tipo | Condición |
|---|---|
| Precondición | La persona tiene el permiso de reverificar, un factor activo y la cuenta sin bloquear; el código vale |
| Postcondición | Tiene una credencial de acceso con la prueba en el instante actual; el código queda usado; su contador de intentos vuelve a cero. La sesión —su renovación— no cambia |

---

## 8. Flujo principal

1. La persona intenta una operación sensible y el sistema le responde que reverifique, con la ruta para hacerlo.
2. La persona presenta el código de su app.
3. El sistema lo comprueba, lo marca usado y devuelve una credencial de acceso con la prueba fresca.
4. La persona repite la operación con la nueva credencial, y se atiende.

---

## 9. Flujos alternativos

### FA-001 — La interfaz pide el código antes

La interfaz sabe por el catálogo de permisos qué operaciones son sensibles, y puede pedir el código **antes** de intentar. El paso 1 desaparece.

### FA-002 — Código de recuperación

Como en `RF-SP-072` `FA-001`: vale una vez, la respuesta dice cuántos quedan y queda un evento de severidad alta.

---

## 10. Excepciones

| ID | Situación | Resultado |
|---|---|---|
| `EX-001` | Operación sensible con la prueba vencida o sin prueba | Prohibido, con un aviso **propio** —distinto de la falta de permiso, del cambio de contraseña y de la activación obligatoria— y la ruta para reverificar. **Nada cambia** |
| `EX-002` | Operación sensible **sin el permiso** | Prohibido por falta de permiso, como siempre. **No** se pide reverificar |
| `EX-003` | Reverificar sin factor activo | Rechazo: no hay nada que verificar, y hay que activarlo (`RF-SP-071`) |
| `EX-004` | El código no vale | Rechazo con los intentos restantes; consume un intento de la cuenta y deja evento |
| `EX-005` | Ese fallo bloquea la cuenta | Bloqueo como en `RF-SP-034`, **y se cierran todas las sesiones** de la persona |
| `EX-006` | Los dos códigos, ninguno o un formato desconocido | Rechazo por formato |

---

## 11. Validaciones

| ID | Validación | Mensaje esperado |
|---|---|---|
| `VAL-001` | Exactamente un código, con forma de código de app o de recuperación | Indique el código de su aplicación o un código de recuperación. |
| `VAL-002` | Factor activo | No tiene activado el segundo factor. |
| `VAL-003` | Código válido | El código no es válido. *(Con los intentos restantes.)* |

---

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-SP-839` | Un código válido devuelve una credencial de acceso con los mismos roles y avisos, la prueba en el instante actual y hasta cuándo vale; **la renovación de la sesión no cambia** |
| `CA-SP-840` | Con la prueba de hace cinco minutos o menos, una operación sensible **se atiende** |
| `CA-SP-841` | Con la prueba vencida, o sin prueba, una operación sensible responde prohibido con el aviso de reverificación y su ruta, y **nada cambia** |
| `CA-SP-842` | Sin el permiso de la operación sensible, la respuesta es la de **falta de permiso**, no la de reverificación |
| `CA-SP-843` | Una operación **no** marcada se atiende sin prueba |
| `CA-SP-844` | Sin factor activo, reverificar se rechaza y una operación sensible no se atiende |
| `CA-SP-845` | Un código inválido se rechaza con los intentos restantes y deja un evento de código rechazado; los fallos suman con los del inicio de sesión |
| `CA-SP-846` | El fallo que bloquea la cuenta **cierra todas sus sesiones**: su renovación deja de funcionar |
| `CA-SP-847` | Un código ya usado —el del inicio de sesión, o el de una reverificación anterior— no sirve |
| `CA-SP-848` | Un código de recuperación vale, queda usado, la respuesta dice cuántos quedan y deja evento de severidad alta |
| `CA-SP-849` | Renovar la sesión **no** refresca la prueba |
| `CA-SP-850` | El catálogo de permisos —listado y detalle— dice de cada permiso **si es sensible**, y los dieciocho de `security.md` §4.4 lo son |
| `CA-SP-851` | La reverificación correcta **no** deja evento de seguridad |
| `CA-SP-852` | Sin el permiso de reverificar responde prohibido; sin autenticar, `401` |

---

## 13. Casos límite

| Caso | Comportamiento |
|---|---|
| La prueba vence a mitad de una operación ya admitida | La operación termina: la prueba se mira al entrar, no durante |
| Dos operaciones sensibles seguidas en el mismo minuto | Las dos se atienden con una sola reverificación |
| La credencial de acceso anterior, sin prueba, sigue viva | Sigue valiendo para lo no sensible hasta caducar. No hay que descartarla |
| Una persona retenida por la contraseña provisional o por la activación obligatoria | La retención manda: no llega a ninguna operación sensible |

---

## 14. Preguntas abiertas

Ninguna.

---

## 15. Control de cambios

| Versión | Fecha | Cambio | Autor |
|---|---|---|---|
| 0.1.0 | 06-10-2026 | Primera versión ([`requirements/sp.md`](../../../requirements/sp.md) v1.93.0, `RN-SP-063`), con la decisión del responsable del proyecto de volver a pedir el código en las operaciones sensibles, **incluida la configuración de los permisos de los roles**. **Enmienda `RF-SP-010` y `RF-SP-015`**: el catálogo publica qué permiso es sensible. | Responsable técnico |
