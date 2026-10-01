# SPEC — `RF-MV-036` Consultar mis cuentas de cobro

| Campo | Valor |
|---|---|
| Requerimiento | `RF-MV-036` |
| Módulo | `MV` — Movimientos |
| Versión | 0.1.0 |
| Estado | **Aprobada** |
| Autor | Responsable técnico |
| Aprobada por | Responsable del proyecto |
| Fecha de aprobación | 01-10-2026 |

!!! info "Qué va en este documento"

    **Qué debe pasar, y por qué.** Nada más.

    **Prueba de pertenencia:** si un cambio de tecnología lo invalidaría, no pertenece aquí — va a `plan.md`. No se nombran tablas, clases, endpoints ni librerías.

---

## 1. Objetivo

Que una persona vea **sus cuentas de cobro** —cuál es la principal y cuáles ya no sirven para retirar— para elegir a dónde pedir un retiro y mantenerlas al día.

---

## 2. Contexto

Es la lectura de lo que registra `RF-MV-035`, y la que usa el formulario de retiro para ofrecer el destino (`RF-MV-019`). **Solo lo propio**: quien pregunta es el dueño, y no hay filtro por persona (`RN-SEG-015`).

### 2.1 Lo que este requerimiento decide

| Decisión | Qué se decidió |
|---|---|
| **Solo las vivas** | Las dadas de baja no se pueden usar ni editar; mostrarlas solo serviría para confundir. Administración las ve con `RF-MV-039` |
| **La principal primero**, y después de la más reciente a la más antigua | Lo que un selector necesita: la que se usa por omisión, arriba |
| **Cada cuenta dice si sirve para retirar** | Una cuenta de una entidad desactivada sigue siendo de la persona, pero no admite retiros (`RN-MV-054`). La persona tiene que verlo para registrar otra |
| **El número se muestra completo** | Es de quien pregunta, y lo necesita para reconocerla |
| **Sin paginar** | Una persona tiene pocas cuentas |

---

## 3. Actores

| Actor | Rol en esta funcionalidad |
|---|---|
| Quien tiene `movements:list-own-payout-accounts` | Consulta las suyas |

---

## 4. Alcance

### 4.1 Incluye

- Listar las cuentas vivas propias, con su titular y si sirven para retirar.

### 4.2 No incluye

- **Las cuentas de otra persona**: es `RF-MV-039`.
- **A qué retiros se pagó cada cuenta**: lo dice cada retiro (`RF-MV-007`).

---

## 5. Reglas de negocio aplicables

| Regla | Cómo aplica |
|---|---|
| `RN-MV-055` | Solo las del dueño; el titular es el usuario |
| `RN-MV-054` | Una entidad inactiva no admite retiros: la cuenta lo dice |

**Ninguna regla nueva.**

---

## 6. Datos

### 6.1 Entrada

Ninguna: **quién** sale de la credencial.

### 6.2 Salida

**La lista**: de cada cuenta, lo que devuelve `RF-MV-035` y además **si sirve para retirar**.

---

## 7. Precondiciones y postcondiciones

| Tipo | Condición |
|---|---|
| Precondición | El actor tiene `movements:list-own-payout-accounts` |
| Postcondición | Ninguna: no escribe nada |

---

## 8. Flujo principal

1. La persona pide sus cuentas.
2. El sistema devuelve sus cuentas vivas, la principal primero.

---

## 9. Flujos alternativos

### FA-001 — No tiene ninguna

Una lista vacía. El formulario de retiro se lo dirá: sin cuenta no hay retiro (`RN-MV-056`).

---

## 10. Excepciones

| ID | Situación | Resultado |
|---|---|---|
| `EX-001` | Quien pide no tiene `movements:list-own-payout-accounts` | Prohibido |

---

## 11. Validaciones

Ninguna.

---

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-MV-390` | Devuelve **solo las cuentas vivas de quien pregunta**: ni las de otras personas ni las dadas de baja |
| `CA-MV-391` | La **principal va primero**, y las demás de la más reciente a la más antigua |
| `CA-MV-392` | Una cuenta de una entidad **desactivada** aparece, marcada como que **no sirve para retirar**; las demás, como que sí |
| `CA-MV-393` | Cada cuenta trae **el titular** —nombre y documento del usuario—, y si administración corrige el documento, la cuenta lo muestra corregido |
| `CA-MV-394` | Sin cuentas devuelve una lista vacía; sin `movements:list-own-payout-accounts` responde prohibido, y sin autenticar, `401` |

---

## 13. Casos límite

Ninguno distinto de los criterios.

---

## 14. Preguntas abiertas

Ninguna.

---

## 15. Control de cambios

| Versión | Fecha | Cambio | Autor |
|---|---|---|---|
| 0.1.0 | 01-10-2026 | Primera versión, con las cuentas de cobro de `MV` ([`requirements/mv.md`](../../../requirements/mv.md) v0.61.0 §4.5). Solo las vivas, la principal primero, y cada una dice si sirve para retirar. Criterios `CA-MV-390` a `CA-MV-394`. | Responsable del proyecto |
