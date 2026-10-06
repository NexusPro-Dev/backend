# SPEC — `RF-SP-076` Restablecer el segundo factor de un usuario

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-076` |
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

Devolver el acceso a quien **perdió el teléfono y los códigos de recuperación**: retirarle el factor para que pueda entrar con su contraseña y, si su rol lo exige, activar uno nuevo.

---

## 2. Contexto

Sin esta operación, perder las dos cosas a la vez deja la cuenta inaccesible para siempre, o deja la solución en manos de quien tenga acceso a la base de datos. Es la operación que más poder tiene sobre el segundo factor de otra persona, y por eso es la que más condiciones lleva.

### 2.1 Lo que este requerimiento decide

| Decisión | Qué se decidió |
|---|---|
| **Motivo obligatorio** | Como toda eliminación (Art. V.13): quién, a quién, cuándo y **por qué** |
| **Cierra todas las sesiones de la persona** | Si alguien perdió el teléfono, una sesión abierta en ese teléfono la tiene otro |
| **Nunca sobre uno mismo** | Si quien administra pudiera restablecer el suyo, una sesión robada de administrador se saltaría el segundo factor |
| **Nunca sobre quien tiene más privilegios** | Un administrador no restablece el factor de una persona que puede cosas que él no. Sin esta regla, quien tenga a la vez restablecer contraseñas y restablecer factores **tomaría la cuenta del superadministrador** en dos peticiones. Es `RN-SEG-010` —nadie otorga lo que no tiene— aplicado al acceso |
| **No toca la contraseña** | Si también la olvidó, son dos operaciones (`RF-SP-038`). Mezclarlas haría que restablecer el factor revelara una credencial |
| **Es sensible** | Exige verificación reciente de quien lo hace |
| **La identificación de la persona queda fuera del sistema** | Cómo comprueba el administrador que quien llama es de verdad el titular —por teléfono, en persona— lo decide la empresa. Es el eslabón débil de todo segundo factor y no tiene solución técnica; lo que el sistema hace es **dejarlo escrito** |

---

## 3. Actores

| Actor | Rol en esta funcionalidad |
|---|---|
| Quien tiene el permiso de restablecer factores | Retira el factor de otra persona con menos o igual privilegio |

---

## 4. Alcance

### 4.1 Incluye

- Retirar el factor activo y el pendiente de la persona, con sus códigos.
- Cerrar todas sus sesiones.
- Registrar el motivo.

### 4.2 No incluye

- **Restablecer la contraseña** → `RF-SP-038`.
- **Activar un factor por la persona.** Lo activa ella al entrar (`RF-SP-071`).
- **Verificar la identidad del titular.** Es un procedimiento de la empresa.

---

## 5. Reglas de negocio aplicables

| Regla | Cómo aplica |
|---|---|
| `RN-SP-064` | Motivo, cierre de sesiones, nunca sobre uno mismo |
| `RN-SP-065` | **Nueva.** Nunca sobre quien tiene permisos que el actor no tiene |
| `RN-SP-062` | Si la persona está obligada, entra retenida hasta activarlo de nuevo |
| `RN-SP-063` | Es sensible |

---

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción |
|---|---|---|
| Persona | Sí | A quién |
| Motivo | Sí | Por qué. Con contenido, hasta 500 caracteres |

### 6.2 Salida

Nada que mostrar: que se hizo.

---

## 7. Precondiciones y postcondiciones

| Tipo | Condición |
|---|---|
| Precondición | Permiso; verificación reciente; la persona existe, no está eliminada, no es el actor y tiene factor activo o pendiente; sus permisos efectivos están contenidos en los del actor; el motivo es válido |
| Postcondición | La persona no tiene factor; sus códigos no sirven; no tiene ninguna sesión abierta; entrar le pide solo la contraseña —y, si está obligada, la retiene hasta activarlo—; está auditado con el motivo |

---

## 8. Flujo principal

1. El administrador comprueba, fuera del sistema, que habla con el titular.
2. Reverifica (`RF-SP-073`), si no lo hizo hace poco.
3. Pide restablecer el factor de la persona, con el motivo.
4. El sistema comprueba las condiciones, retira el factor, cierra las sesiones de la persona y audita.

---

## 9. Flujos alternativos

Ninguno.

---

## 10. Excepciones

| ID | Situación | Resultado |
|---|---|---|
| `EX-001` | La persona es el actor | Prohibido, remitiendo a desactivarlo o cambiarlo él mismo |
| `EX-002` | La persona tiene algún permiso que el actor no tiene | Prohibido, sin decir cuál |
| `EX-003` | La persona no existe o está eliminada | No encontrada, sin distinguir los dos casos |
| `EX-004` | La persona no tiene factor | Conflicto: no hay nada que restablecer |
| `EX-005` | Motivo ausente, vacío o demasiado largo | Rechazo por formato |
| `EX-006` | Sin verificación reciente | Prohibido, con el aviso de reverificación |
| `EX-007` | Sin el permiso | Prohibido |

---

## 11. Validaciones

| ID | Validación | Mensaje esperado |
|---|---|---|
| `VAL-001` | Motivo con contenido, hasta 500 caracteres | Debe indicar el motivo. |
| `VAL-002` | La persona no es el actor | No puede restablecer su propio segundo factor. |
| `VAL-003` | Privilegios contenidos | No puede restablecer el segundo factor de esta persona. |
| `VAL-004` | La persona tiene factor | Esta persona no tiene activado el segundo factor. |

---

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-SP-869` | El factor activo y el pendiente de la persona quedan retirados con el motivo «restablecido», y entrar le pide solo la contraseña |
| `CA-SP-870` | Sus códigos de recuperación dejan de servir |
| `CA-SP-871` | **Todas** sus sesiones quedan cerradas: su renovación deja de funcionar |
| `CA-SP-872` | Si su rol lo exige, su siguiente inicio de sesión entra **retenido** hasta que active uno nuevo |
| `CA-SP-873` | Sobre uno mismo responde prohibido y nada cambia |
| `CA-SP-874` | Sobre una persona con algún permiso que el actor no tiene —un `ADMIN` sobre el superadministrador— responde prohibido y nada cambia |
| `CA-SP-875` | Una persona inexistente o eliminada responde no encontrada; una sin factor, conflicto |
| `CA-SP-876` | Sin motivo, o con uno en blanco o de más de 500 caracteres, se rechaza y nada cambia |
| `CA-SP-877` | Sin verificación reciente responde prohibido con el aviso de reverificación |
| `CA-SP-878` | Queda un evento de seguridad de severidad alta con la persona como objeto, y el motivo en la auditoría de eliminación |
| `CA-SP-879` | Sin el permiso responde prohibido; sin autenticar, `401` |

---

## 13. Casos límite

| Caso | Comportamiento |
|---|---|
| **El último superadministrador pierde teléfono y códigos** | Solo otro superadministrador puede restablecerlo. Si no hay otro, **no hay salida por la API**: queda un procedimiento de recuperación por base de datos, que se documenta en [`deployment.md`](../../../deployment.md) y se ejecuta con acceso a la base, que es justo lo que un administrador de la aplicación no tiene. La alternativa —que `ADMIN` pudiera— es la toma de cuenta que `RN-SP-065` cierra |
| La persona tiene solo un factor pendiente | Se retira igual: es lo que hay |
| Restablecer a dos personas seguidas | La misma verificación reciente vale para las dos |
| La persona está bloqueada | Se admite. El bloqueo y el factor son cosas distintas |

---

## 14. Preguntas abiertas

| # | Pregunta | Estado |
|---|---|---|
| 1 | `RN-SP-065` —nadie restablece el factor de quien tiene más privilegios— **la propuso el responsable técnico** al escribir esta spec, por el riesgo de toma de cuenta descrito en §2.1. **Queda pendiente de confirmación del responsable del proyecto.** `RF-SP-038`, restablecer contraseñas, no tiene hoy esa contención | **Abierta** |

---

## 15. Control de cambios

| Versión | Fecha | Cambio | Autor |
|---|---|---|---|
| 0.1.0 | 06-10-2026 | Primera versión ([`requirements/sp.md`](../../../requirements/sp.md) v1.95.0, `RN-SP-064`; `RN-SP-065` nueva y pendiente de confirmar). | Responsable técnico |
