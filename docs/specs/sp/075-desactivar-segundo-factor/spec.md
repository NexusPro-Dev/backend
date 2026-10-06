# SPEC — `RF-SP-075` Desactivar el propio segundo factor

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-075` |
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

Que quien activó la app autenticadora **por su cuenta**, sin que su rol se lo exija, pueda retirarla y volver a entrar solo con la contraseña.

---

## 2. Contexto

Activar el factor es voluntario para quien no está obligado (`RF-SP-071`), y lo voluntario tiene que poder deshacerse. Pero es la operación que más baja la seguridad de una cuenta: una sesión robada que pudiera hacerla dejaría la cuenta en manos de quien tenga la contraseña.

### 2.1 Lo que este requerimiento decide

| Decisión | Qué se decidió |
|---|---|
| **Pide los dos factores a la vez** | La verificación reciente del código (`RF-SP-073`) **y** la contraseña actual. Una sesión robada no lleva ninguna de las dos |
| **Quien está obligado no puede** | Si porta un rol que exige el factor, se rechaza. Para él no hay desactivación, solo cambio de teléfono (`RF-SP-071`) |
| **Cierra las demás sesiones** | Como al cambiar la contraseña (`RF-SP-037`): las sesiones abiertas se abrieron con un factor que ya no existe. **La actual se conserva** |
| **Deja el historial** | El factor queda retirado, no borrado |

---

## 3. Actores

| Actor | Rol en esta funcionalidad |
|---|---|
| Cualquier persona con el factor activo, el permiso de desactivarlo y ningún rol que lo exija | Retira el suyo |

---

## 4. Alcance

### 4.1 Incluye

- Retirar el factor activo —y el pendiente, si lo hay— con sus códigos de recuperación.
- Cerrar las demás sesiones.

### 4.2 No incluye

- **Retirar el de otra persona** → `RF-SP-076`.
- **Cambiar de teléfono** → `RF-SP-071`.

---

## 5. Reglas de negocio aplicables

| Regla | Cómo aplica |
|---|---|
| `RN-SP-062` | Quien está obligado no lo desactiva |
| `RN-SP-063` | Es sensible: exige verificación reciente |
| `security.md` §3.2 | La contraseña incorrecta cuenta para el bloqueo, como en `RF-SP-037` |

---

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción |
|---|---|---|
| Contraseña actual | Sí | La vigente. Nunca se registra |

### 6.2 Salida

Nada que mostrar: que se hizo.

---

## 7. Precondiciones y postcondiciones

| Tipo | Condición |
|---|---|
| Precondición | Permiso; factor activo; ningún rol activo que lo exija; verificación reciente; contraseña correcta |
| Postcondición | La persona no tiene factor activo ni pendiente; sus códigos no sirven; entrar le pide solo la contraseña; solo su sesión actual sigue abierta; está auditado |

---

## 8. Flujo principal

1. La persona reverifica (`RF-SP-073`), si no lo hizo hace poco.
2. Pide desactivar, con su contraseña.
3. El sistema comprueba que ningún rol suyo lo exige y que la contraseña es correcta.
4. Retira el factor y el pendiente, cierra las demás sesiones y audita.

---

## 9. Flujos alternativos

Ninguno.

---

## 10. Excepciones

| ID | Situación | Resultado |
|---|---|---|
| `EX-001` | La persona porta un rol que exige el factor | Conflicto, diciendo que su rol lo exige. Nada cambia |
| `EX-002` | Contraseña incorrecta | Rechazo, diciendo qué falló —quien llama ya está autenticado, como en `RF-SP-037`—, y consume un intento de la cuenta |
| `EX-003` | Ese fallo bloquea la cuenta | Bloqueo, y **se cierran todas sus sesiones**, como al reverificar (`RF-SP-073` `EX-005`) |
| `EX-004` | Sin verificación reciente | Prohibido, con el aviso de reverificación |
| `EX-005` | Sin factor activo | Conflicto: no hay nada que desactivar |
| `EX-006` | Sin el permiso | Prohibido |

---

## 11. Validaciones

| ID | Validación | Mensaje esperado |
|---|---|---|
| `VAL-001` | Contraseña presente | Debe indicar su contraseña actual. |
| `VAL-002` | Contraseña correcta | La contraseña actual no es correcta. |
| `VAL-003` | Ningún rol lo exige | Su rol exige el segundo factor: no puede desactivarlo. |
| `VAL-004` | Factor activo | No tiene activado el segundo factor. |

---

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-SP-859` | Con verificación reciente y la contraseña correcta, el factor queda retirado y **entrar pide solo la contraseña** |
| `CA-SP-860` | Sus códigos de recuperación y un factor pendiente, si lo había, dejan de servir |
| `CA-SP-861` | Las **demás** sesiones de la persona quedan cerradas; la actual sigue funcionando |
| `CA-SP-862` | Si un rol activo suyo exige el factor, se rechaza con conflicto y **nada cambia** |
| `CA-SP-863` | Con la contraseña incorrecta se rechaza, nada cambia y el intento cuenta para el bloqueo |
| `CA-SP-864` | El fallo que bloquea la cuenta cierra **todas** sus sesiones |
| `CA-SP-865` | Sin verificación reciente responde prohibido con el aviso de reverificación |
| `CA-SP-866` | Sin factor activo responde conflicto |
| `CA-SP-867` | Queda un evento de severidad alta, y el factor retirado **sigue en el historial** con su motivo |
| `CA-SP-868` | Sin el permiso responde prohibido; sin autenticar, `401` |

---

## 13. Casos límite

| Caso | Comportamiento |
|---|---|
| Un rol que lo exige, pero **inactivo** | No obliga (`RN-SP-062` habla de roles activos): se admite |
| La credencial de acceso actual lleva la prueba reciente | Sigue valiendo para operaciones sensibles hasta que la prueba venza, como mucho cinco minutos. **Es un resto conocido y aceptado**: la prueba viaja en el token y no se consulta la base en cada petición (`security.md` §5.2) |
| Le marcan el rol después de desactivar | Queda retenido en su siguiente renovación hasta que lo active (`RF-SP-072` `FA-002`) |

---

## 14. Preguntas abiertas

Ninguna.

---

## 15. Control de cambios

| Versión | Fecha | Cambio | Autor |
|---|---|---|---|
| 0.1.0 | 06-10-2026 | Primera versión ([`requirements/sp.md`](../../../requirements/sp.md) v1.94.0, `RN-SP-062`, `RN-SP-063`). | Responsable técnico |
