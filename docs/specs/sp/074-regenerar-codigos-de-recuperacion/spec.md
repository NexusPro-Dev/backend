# SPEC — `RF-SP-074` Regenerar los propios códigos de recuperación

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-074` |
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

Que quien gastó, perdió o dejó a la vista sus códigos de recuperación obtenga **diez nuevos** y los anteriores **dejen de servir**, sin tener que cambiar de teléfono.

---

## 2. Contexto

Los diez códigos se entregan al activar el factor (`RF-SP-071`) y solo se ven entonces. Se gastan —uno por cada vez que la persona entra sin el teléfono— y se pierden o se exponen: un papel olvidado, una captura de pantalla compartida. Sin esta operación, la única forma de tener códigos nuevos sería activar otro factor.

### 2.1 Lo que este requerimiento decide

| Decisión | Qué se decidió |
|---|---|
| **Es una operación sensible** | Los códigos valen lo mismo que el teléfono: quien los regenera con una sesión robada se lleva diez entradas a la cuenta. Exige haber presentado el código hace poco (`RF-SP-073`) |
| **Anula todos los anteriores** | Usados o no. No hay forma de anular uno solo: si uno se expuso, los demás pudieron exponerse con él |
| **Se ven una sola vez** | Como al activar |

---

## 3. Actores

| Actor | Rol en esta funcionalidad |
|---|---|
| Cualquier persona con el factor activo y el permiso de regenerar | Regenera los suyos. Nadie regenera los de otra persona |

---

## 4. Alcance

### 4.1 Incluye

- Generar diez códigos nuevos y entregarlos.
- Anular los anteriores.

### 4.2 No incluye

- **Ver los códigos vigentes.** No se puede: el sistema solo guarda su resumen.
- **Cuántos quedan** fuera del momento de usarlos. Lo dice la respuesta de `RF-SP-072` y `RF-SP-073` cuando se gasta uno.
- **Regenerar los de otra persona.** Quien perdió todo lo resuelve `RF-SP-076`.

---

## 5. Reglas de negocio aplicables

| Regla | Cómo aplica |
|---|---|
| `RN-SP-061` | Diez, de un solo uso, vistos una vez; regenerar anula todos |
| `RN-SP-063` | Es sensible: exige verificación reciente |

---

## 6. Datos

### 6.1 Entrada

Ninguna. La persona es quien llama.

### 6.2 Salida

Los **diez códigos nuevos** y cuándo se generaron.

---

## 7. Precondiciones y postcondiciones

| Tipo | Condición |
|---|---|
| Precondición | Permiso de regenerar; factor activo; código presentado hace cinco minutos o menos |
| Postcondición | Hay diez códigos vigentes, todos nuevos, guardados solo como resumen; ninguno anterior sirve; está auditado |

---

## 8. Flujo principal

1. La persona reverifica (`RF-SP-073`), si no lo hizo hace poco.
2. Pide códigos nuevos.
3. El sistema anula los vigentes, genera diez, guarda su resumen, audita y los devuelve.

---

## 9. Flujos alternativos

Ninguno.

---

## 10. Excepciones

| ID | Situación | Resultado |
|---|---|---|
| `EX-001` | Sin verificación reciente | Prohibido, con el aviso de reverificación. Nada cambia |
| `EX-002` | Sin factor activo | Rechazo: no hay de qué regenerar |
| `EX-003` | Sin el permiso | Prohibido |

---

## 11. Validaciones

| ID | Validación | Mensaje esperado |
|---|---|---|
| `VAL-001` | Factor activo | No tiene activado el segundo factor. |

---

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-SP-853` | Con verificación reciente, devuelve **diez** códigos nuevos y distintos; el sistema guarda solo su resumen |
| `CA-SP-854` | Ningún código anterior —usado o no— sirve después para entrar ni para reverificar; los nuevos sí |
| `CA-SP-855` | Sin verificación reciente responde prohibido con el aviso de reverificación, y **los anteriores siguen sirviendo** |
| `CA-SP-856` | Sin factor activo se rechaza |
| `CA-SP-857` | Queda un evento de severidad alta; los códigos no aparecen en ningún registro |
| `CA-SP-858` | Sin el permiso responde prohibido; sin autenticar, `401` |

---

## 13. Casos límite

| Caso | Comportamiento |
|---|---|
| Regenerar dos veces seguidas | Valen los de la segunda |
| Regenerar con la sesión que se abrió con un código de recuperación | Se admite: es justo el momento en que la persona sabe que le quedan pocos |
| Cambiar de teléfono después | La activación nueva trae sus propios diez y anula estos (`RF-SP-071`) |

---

## 14. Preguntas abiertas

Ninguna.

---

## 15. Control de cambios

| Versión | Fecha | Cambio | Autor |
|---|---|---|---|
| 0.1.0 | 06-10-2026 | Primera versión ([`requirements/sp.md`](../../../requirements/sp.md) v1.94.0, `RN-SP-061`, `RN-SP-063`). | Responsable técnico |
