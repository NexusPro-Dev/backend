# SPEC — `RF-MV-047` Consultar la conversión vigente de cada país

| Campo | Valor |
|---|---|
| Requerimiento | `RF-MV-047` |
| Módulo | `MV` — Movimientos |
| Versión | 0.1.0 |
| Estado | **Aprobada** |
| Autor | Responsable técnico |
| Aprobada por | Responsable del proyecto |
| Fecha de aprobación | 05-10-2026 |

!!! info "Qué va en este documento"

    **Qué debe pasar, y por qué.** Nada más.

    **Prueba de pertenencia:** si un cambio de tecnología lo invalidaría, no pertenece aquí — va a `plan.md`. No se nombran tablas, clases, endpoints ni librerías.

---

## 1. Objetivo

Que quien va a pagar en moneda local o a retirar a una cuenta local sepa **a cuánto está hoy el dólar en cada país**: cuántos pesos pagará por lo que compra y cuántos recibirá por lo que retira.

---

## 2. Contexto

**Es la lectura de `RF-MV-046`** ([`requirements/mv.md`](../../../requirements/mv.md) v0.75.0 §4.9). Devuelve **solo la conversión que rige** en cada país: el histórico se guarda y no se publica. Hereda la argumentación de `RF-MV-026`, la lectura de la tasa de puntos: **lleva permiso aunque no identifique a nadie**, abierto por tipo de rol (`RN-SEG-015`), porque solo le sirve a quien ya puede comprar o retirar.

### 2.1 Lo que este requerimiento decide

| Decisión | Qué se decidió |
|---|---|
| **Solo los países con conversión** | Un país sin conversión no aparece: no hay nada que decir de él |
| **Solo los países activos** | Un país desactivado conserva su conversión y no se enseña, porque en él no se opera |
| **Se puede pedir un solo país** | El cobro de una persona solo necesita el suyo |
| **Sin paginar** | Es una fila por país, y los países con conversión son pocos |

---

## 3. Actores

| Actor | Rol en esta funcionalidad |
|---|---|
| Quien tiene `movements:read-conversion-rates` | Consulta las conversiones vigentes. Lo porta todo rol por su tipo |

---

## 4. Alcance

### 4.1 Incluye

- La conversión vigente de cada país activo que la tenga, o la de uno solo.

### 4.2 No incluye

- **El histórico.**
- **Convertir un importe**: es multiplicar, y lo hace quien consulta. El importe que se cobre o se pague de verdad lo fijarán el cobro y el retiro por la pasarela local.

---

## 5. Reglas de negocio aplicables

| Regla | Cómo aplica |
|---|---|
| `RN-MV-062` | La vigente es la última fijada que ya rige |

**Ninguna regla nueva.**

---

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción |
|---|---|---|
| País | No | Si se indica, solo la conversión de ese país |

### 6.2 Salida

**Una lista**, una fila por país: el país, la moneda local, la moneda base, el precio de cobro, el precio de retiro y desde cuándo rige. Ordenada por el código del país.

---

## 7. Precondiciones y postcondiciones

| Tipo | Condición |
|---|---|
| Precondición | El actor tiene `movements:read-conversion-rates` |
| Postcondición | Ninguna: es una lectura |

---

## 8. Flujo principal

1. El actor pide las conversiones, o la de un país.
2. El sistema devuelve la vigente de cada país activo que tenga una.

---

## 9. Flujos alternativos

### FA-001 — Ningún país tiene conversión, o el pedido no la tiene

Se devuelve una lista vacía.

---

## 10. Excepciones

| ID | Situación | Resultado |
|---|---|---|
| `EX-001` | El país indicado no tiene forma de identificador | Rechazo |
| `EX-002` | Quien pide no tiene `movements:read-conversion-rates` | Prohibido |

---

## 11. Validaciones

| ID | Validación |
|---|---|
| `VAL-001` | El país, si llega, con forma de identificador |

---

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-MV-557` | Con dos países con conversión, devuelve **una fila por país** con su moneda local, la base, los dos precios y desde cuándo rige, ordenadas por código |
| `CA-MV-558` | Si un país tuvo varias conversiones, devuelve **solo la última**, no las anteriores |
| `CA-MV-559` | Un país **sin conversión** o **inactivo** no aparece; sin ninguna, la lista está **vacía** |
| `CA-MV-560` | Pedido un país, devuelve **solo el suyo**; un país sin conversión da la lista vacía |
| `CA-MV-561` | Un cliente —rol de tipo `CONSUMIDOR`— la consulta; sin `movements:read-conversion-rates` responde prohibido y sin autenticar, `401` |

---

## 13. Casos límite

| Caso | Comportamiento |
|---|---|
| Se fija una conversión entre dos consultas | La segunda ya devuelve la nueva |

---

## 14. Preguntas abiertas

**El histórico, para administración.** Si se pide, será otro requerimiento con otro permiso.

---

## 15. Control de cambios

| Versión | Fecha | Cambio | Autor |
|---|---|---|---|
| 0.1.0 | 05-10-2026 | Primera versión, con la conversión por país ([`requirements/mv.md`](../../../requirements/mv.md) v0.75.0 §4.9). **Solo la vigente**, de los países activos, con filtro opcional por país y sin paginar. **Con permiso por tipo de rol y no pública.** Criterios `CA-MV-557` a `CA-MV-561`. | Responsable del proyecto |
