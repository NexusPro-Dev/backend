# SPEC — `RF-MV-026` Consultar las tasas de puntos vigentes

| Campo | Valor |
|---|---|
| Requerimiento | `RF-MV-026` |
| Módulo | `MV` — Movimientos |
| Versión | 0.1.0 |
| Estado | **Aprobada** |
| Autor | Responsable técnico |
| Aprobada por | Responsable del proyecto |
| Fecha de aprobación | 30-09-2026 |

!!! info "Qué va en este documento"

    **Qué debe pasar, y por qué.** Nada más.

    **Prueba de pertenencia:** si un cambio de tecnología lo invalidaría, no pertenece aquí — va a `plan.md`. No se nombran tablas, clases, endpoints ni librerías.

---

## 1. Objetivo

Que quien va a comprar puntos o a pagar con ellos sepa **a cuánto están hoy en cada moneda**: cuántos puntos le da lo que paga, y cuántos le cuesta lo que compra.

---

## 2. Contexto

**Es la lectura de `RF-MV-025`** ([`requirements/mv.md`](../../../requirements/mv.md) v0.54.0 §4.4). Devuelve **solo la tasa que rige** en cada moneda: el histórico se guarda y no se publica.

**Lleva permiso aunque no identifique a nadie**, al revés que el catálogo de métodos de pago (`RF-MV-009`), que es público porque el formulario de registro lo necesita **antes de que exista la cuenta**. La tasa solo le sirve a quien ya puede comprar, y un permiso por tipo de rol la abre a todos ellos (`RN-SEG-015`).

### 2.1 Lo que este requerimiento decide

| Decisión | Qué se decidió |
|---|---|
| **Solo las monedas que venden puntos** | Una moneda sin tasa no aparece: no hay nada que decir de ella |
| **Solo las monedas activas** | Una moneda retirada conserva su tasa y no se enseña, porque en ella no se compra nada |
| **Sin paginar** | Es una fila por moneda, y las monedas son pocas |

---

## 3. Actores

| Actor | Rol en esta funcionalidad |
|---|---|
| Quien tiene `movements:read-points-rates` | Consulta las tasas vigentes. Lo porta todo rol por su tipo |

---

## 4. Alcance

### 4.1 Incluye

- La tasa vigente de cada moneda activa que la tenga.

### 4.2 No incluye

- **El histórico de tasas.**
- **Convertir un importe a puntos**: es multiplicar, y lo hace quien consulta.

---

## 5. Reglas de negocio aplicables

| Regla | Cómo aplica |
|---|---|
| `RN-MV-050` | La vigente es la última fijada que ya rige |

**Ninguna regla nueva.**

---

## 6. Datos

### 6.1 Entrada

Ninguna.

### 6.2 Salida

**Una lista**, una fila por moneda: la moneda, los puntos por unidad y desde cuándo rige. Ordenada por el código de la moneda.

---

## 7. Precondiciones y postcondiciones

| Tipo | Condición |
|---|---|
| Precondición | El actor tiene `movements:read-points-rates` |
| Postcondición | Ninguna: es una lectura |

---

## 8. Flujo principal

1. El actor pide las tasas.
2. El sistema devuelve la vigente de cada moneda activa que tenga una.

---

## 9. Flujos alternativos

### FA-001 — Ninguna moneda vende puntos

Se devuelve una lista vacía.

---

## 10. Excepciones

| ID | Situación | Resultado |
|---|---|---|
| `EX-001` | Quien pide no tiene `movements:read-points-rates` | Prohibido |

---

## 11. Validaciones

Ninguna: no hay entrada.

---

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-MV-301` | Con dos monedas con tasa, devuelve **una fila por moneda** con su tasa y desde cuándo rige, ordenadas por código |
| `CA-MV-302` | Si una moneda tuvo varias tasas, devuelve **solo la última**, no las anteriores |
| `CA-MV-303` | Una moneda **sin tasa** o **inactiva** no aparece |
| `CA-MV-304` | Sin ninguna tasa, la lista está **vacía** |
| `CA-MV-305` | Un cliente —rol de tipo `CONSUMIDOR`— la consulta; sin `movements:read-points-rates` responde prohibido y sin autenticar, `401` |

---

## 13. Casos límite

| Caso | Comportamiento |
|---|---|
| Se fija una tasa entre dos consultas | La segunda ya devuelve la nueva |

---

## 14. Preguntas abiertas

**El histórico, para administración.** Si se pide, será otro requerimiento con otro permiso.

---

## 15. Control de cambios

| Versión | Fecha | Cambio | Autor |
|---|---|---|---|
| 0.1.0 | 30-09-2026 | Primera versión, con la etapa 3 de `MV` ([`requirements/mv.md`](../../../requirements/mv.md) v0.54.0 §4.4). **Solo la vigente**, de las monedas activas, sin paginar. **Con permiso por tipo de rol y no pública.** Criterios `CA-MV-301` a `CA-MV-305`. | Responsable del proyecto |
