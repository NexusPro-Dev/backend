# SPEC — `RF-CM-018` Retirar una comisión afftrack de rol

| Campo | Valor |
|---|---|
| Requerimiento | `RF-CM-018` |
| Módulo | `CM` — Comisiones |
| Versión | 0.1.0 |
| Estado | **Aprobada** |
| Autor | Responsable técnico |
| Aprobada por | Responsable del proyecto |
| Fecha de aprobación | 29-09-2026 |

!!! info "Qué va en este documento"

    **Qué debe pasar, y por qué.** Nada más.

    **Prueba de pertenencia:** si un cambio de tecnología lo invalidaría, no pertenece aquí — va a `plan.md`. No se nombran tablas, clases, endpoints ni librerías.

---

## 1. Objetivo

Que un escalón de rol **deje de pagar desde el siguiente cierre**, con motivo y sin borrarlo.

---

## 2. Contexto

Hereda la forma de [`RF-CM-004`](../004-retirar-tasa-comision/spec.md): retiro **lógico y con motivo** (`RN-CM-005`, Art. V.13), **no idempotente** —retirar dos veces se rechaza—, y sin deshacer.

**Retirar un escalón no borra los FTD de nadie.** Los que la persona haya reunido siguen en su remanente, y el siguiente cierre los compara con **lo que quede** de su escala (`RN-CM-041`). Si era el único escalón, se acumulan hasta que haya otro.

---

## 3. Actores

| Actor | Rol en esta funcionalidad |
|---|---|
| Administración | Retira e indica el motivo (`afftrack-rates:delete`) |

---

## 4. Alcance

### 4.1 Incluye

- Retirar un escalón con motivo obligatorio.

### 4.2 No incluye

- **Deshacer el retiro**: si vuelve a hacer falta, se registra otro.
- **Tocar lo pagado o el remanente de nadie** (`RN-CM-029`).

---

## 5. Reglas de negocio aplicables

| Regla | Cómo aplica |
|---|---|
| `RN-CM-005` | Retiro lógico, con motivo |
| `RN-CM-041` | Los FTD reunidos no se pierden: siguen en el remanente |

---

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción |
|---|---|---|
| Escalón | Sí | |
| Motivo | Sí | No en blanco, dentro de la longitud admitida (Art. V.13) |

### 6.2 Salida

Nada: la operación se confirma sin cuerpo.

---

## 7. Precondiciones y postcondiciones

| Tipo | Condición |
|---|---|
| Precondición | El actor porta el permiso; el escalón existe y está vivo |
| Postcondición | El escalón está retirado y **ningún cierre posterior lo aplica** |
| Postcondición | La auditoría de retiros tiene el motivo y el estado completo |

---

## 8. Flujo principal

1. Se envían el escalón y el motivo.
2. Se comprueba que existe y está vivo.
3. Se retira, se audita con el motivo y se confirma.

---

## 9. Flujos alternativos

Ninguno.

---

## 10. Excepciones

| ID | Condición | Respuesta |
|---|---|---|
| `EX-001` | El escalón no existe | `404` |
| `EX-002` | Ya estaba retirado | `409`. **No es un «no encontrado»**: el retiro ya ocurrió, y es lo que quien repite necesita saber |

---

## 11. Validaciones

| ID | Regla | Mensaje |
|---|---|---|
| `VAL-001` | Motivo obligatorio, no en blanco y dentro de la longitud admitida | El motivo del retiro es obligatorio. |

---

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-CM-227` | Se retira un escalón con motivo; deja de listarse por omisión y **el siguiente cierre no lo aplica** |
| `CA-CM-228` | Los FTD reunidos por una persona **no se pierden**: tras retirar su único escalón, el siguiente cierre los deja en el remanente |
| `CA-CM-229` | Retirar un inexistente responde `404`; retirar dos veces, `409`; sin motivo, `400` |
| `CA-CM-230` | El retiro queda en la auditoría de retiros con su motivo; sin `afftrack-rates:delete`, se rechaza |

---

## 13. Casos límite

| Caso | Comportamiento |
|---|---|
| Se retira el escalón que el cierre pagó la última vez | La liquidación pasada sigue apuntándolo y diciendo lo que pagó |

---

## 14. Preguntas abiertas

Ninguna.

---

## 15. Control de cambios

| Versión | Fecha | Cambio | Autor |
|---|---|---|---|
| 0.1.0 | 29-09-2026 | Primera versión, con la comisión afftrack ([`requirements/cm.md`](../../../requirements/cm.md) v0.22.0 §5.8). Criterios `CA-CM-227` a `CA-CM-230`. | Responsable del proyecto |
