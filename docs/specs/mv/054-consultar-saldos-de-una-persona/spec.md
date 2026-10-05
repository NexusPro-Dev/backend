# SPEC — `RF-MV-054` Consultar los saldos de una persona

| Campo | Valor |
|---|---|
| Requerimiento | `RF-MV-054` |
| Módulo | `MV` — Movimientos |
| Versión | 0.1.0 |
| Estado | **Aprobada** |
| Autor | Responsable técnico |
| Aprobada por | Responsable del proyecto |
| Fecha de aprobación | 05-10-2026 |

---

## 1. Objetivo

Que administración vea **los saldos de cualquier persona** —billetera, retenido y puntos, por moneda—. El caso que lo pide: antes de restar puntos (`RF-MV-052`), saber cuántos tiene.

---

## 2. Contexto

Cada persona ve los suyos (`RF-MV-022`). Administración no tenía cómo verlos sin entrar como ella. Lo pidió el frontend en nombre del responsable del proyecto el 05-10-2026 ([`requirements/mv.md`](../../../requirements/mv.md) v0.84.0 §4.11).

| Decisión | Qué se decidió |
|---|---|
| **La misma forma que «mis saldos»** | Una fila por moneda en que tenga algo; vacía si no tiene cuentas |
| **Lectura de administración** | No sigue la estructura comercial: quien tiene el permiso ve a cualquiera |
| **Una persona que no existe, o eliminada** | No encontrada |

---

## 3. Actores

| Actor | Rol |
|---|---|
| Quien tiene `movements:read-user-balances` | Consulta los saldos de cualquier persona |

---

## 4. Alcance

**Incluye** los saldos actuales. **No incluye** el historial asiento a asiento de otra persona.

---

## 5. Reglas de negocio aplicables

| Regla | Cómo aplica |
|---|---|
| `RN-MV-041` | Qué es cada saldo |
| `RN-SEG-014` | Un permiso propio |

---

## 6. Datos

**Entrada**: la persona. **Salida**: por cada moneda, la billetera, lo retenido y los puntos.

---

## 7. Precondiciones y postcondiciones

| Tipo | Condición |
|---|---|
| Precondición | El actor tiene `movements:read-user-balances` |
| Postcondición | Nada cambia |

---

## 8. Flujo principal

1. El actor indica la persona.
2. El sistema comprueba que existe y devuelve sus saldos.

---

## 10. Excepciones

| ID | Situación | Resultado |
|---|---|---|
| `EX-001` | La persona no existe o está eliminada | No encontrada |
| `EX-002` | Sin `movements:read-user-balances` | Prohibido |

---

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-MV-656` | Devuelve, por moneda, la billetera, lo retenido y los puntos de la persona indicada, **y no los de quien pregunta** |
| `CA-MV-657` | Una persona sin cuentas devuelve una lista vacía |
| `CA-MV-658` | Una persona inexistente o eliminada responde no encontrada |
| `CA-MV-659` | Sin `movements:read-user-balances` responde prohibido; sin autenticar, `401` |

---

## 15. Control de cambios

| Versión | Fecha | Cambio | Autor |
|---|---|---|---|
| 0.1.0 | 05-10-2026 | Primera versión ([`requirements/mv.md`](../../../requirements/mv.md) v0.84.0 §4.11). Criterios `CA-MV-656` a `CA-MV-659`. | Responsable técnico |
