# SPEC — `RF-MV-039` Consultar las cuentas de cobro de una persona

| Campo | Valor |
|---|---|
| Requerimiento | `RF-MV-039` |
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

Que administración vea **las cuentas de cobro de cualquier persona**, también las que dio de baja, para atenderla o para revisar a dónde se ha estado pagando.

---

## 2. Contexto

Lo decidió el responsable del proyecto el 01-10-2026: quien aprueba un retiro ve su destino en el propio retiro (`RF-MV-007`), y **además** administración puede consultar las cuentas de una persona con un permiso propio. Es la lectura de administración de lo que `RF-MV-036` da a cada dueño.

### 2.1 Lo que este requerimiento decide

| Decisión | Qué se decidió |
|---|---|
| **De una persona por vez** | La pregunta es «¿qué cuentas tiene esta persona?». Buscar a quién pertenece un número no se ha pedido |
| **Alcance global, no por estructura comercial** | Es tarea de administración, como el libro de `RF-MV-006`; un director no ve las cuentas de su red con este permiso |
| **Las dadas de baja, si se piden** | Por omisión, solo las vivas, como las ve su dueño. Con el filtro, también las dadas de baja, con su fecha: es lo que se mira al revisar un retiro antiguo |
| **El número completo** | Quien porta el permiso es quien paga, y pagar exige el número |

---

## 3. Actores

| Actor | Rol en esta funcionalidad |
|---|---|
| Quien tiene `movements:read-user-payout-accounts` | Consulta las de cualquier persona |

---

## 4. Alcance

### 4.1 Incluye

- Listar las cuentas de una persona, vivas o también dadas de baja.

### 4.2 No incluye

- **Editar o dar de baja la cuenta de otra persona.**
- **Buscar por número de cuenta.**

---

## 5. Reglas de negocio aplicables

| Regla | Cómo aplica |
|---|---|
| `RN-MV-055` | El titular es el usuario; una principal entre las vivas |
| `RN-MV-054` | Cada cuenta dice si su entidad admite retiros |

**Ninguna regla nueva.**

---

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción |
|---|---|---|
| Persona | Sí | De quién |
| Incluir las dadas de baja | No | Por omisión, no |

### 6.2 Salida

**La lista**, con la forma de `RF-MV-036` y además **cuándo se dio de baja** cada una, vacío en las vivas. Las vivas primero, en el orden de `RF-MV-036`; después las dadas de baja, de la más reciente a la más antigua.

---

## 7. Precondiciones y postcondiciones

| Tipo | Condición |
|---|---|
| Precondición | El actor tiene `movements:read-user-payout-accounts`; la persona existe |
| Postcondición | Ninguna: no escribe nada |

---

## 8. Flujo principal

1. El actor indica la persona y si quiere las dadas de baja.
2. El sistema comprueba que la persona existe.
3. Devuelve sus cuentas.

---

## 9. Flujos alternativos

### FA-001 — La persona no tiene cuentas

Una lista vacía.

---

## 10. Excepciones

| ID | Situación | Resultado |
|---|---|---|
| `EX-001` | El filtro tiene forma inválida | Rechazo |
| `EX-002` | La persona no existe o está eliminada | No encontrado |
| `EX-003` | Quien pide no tiene `movements:read-user-payout-accounts` | Prohibido |

---

## 11. Validaciones

| ID | Validación |
|---|---|
| `VAL-001` | La persona tiene forma de identificador |
| `VAL-002` | El filtro, si viene, es sí o no |

---

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-MV-410` | Devuelve las cuentas **vivas** de la persona indicada, en el orden de «mis cuentas», y ninguna de otra persona |
| `CA-MV-411` | Con el filtro devuelve también las **dadas de baja**, con su fecha, después de las vivas |
| `CA-MV-412` | Una persona sin cuentas devuelve una lista vacía; una que no existe, no encontrado |
| `CA-MV-413` | **Quien porta solo el permiso propio** (`RF-MV-036`) recibe prohibido aquí, incluso para consultar sus propias cuentas |
| `CA-MV-414` | Sin `movements:read-user-payout-accounts` responde prohibido; sin autenticar, `401` |

---

## 13. Casos límite

Ninguno distinto de los criterios.

---

## 14. Preguntas abiertas

**Buscar a quién pertenece un número.** Si administración lo pide, será un requerimiento propio.

---

## 15. Control de cambios

| Versión | Fecha | Cambio | Autor |
|---|---|---|---|
| 0.1.0 | 01-10-2026 | Primera versión, con las cuentas de cobro de `MV` ([`requirements/mv.md`](../../../requirements/mv.md) v0.61.0 §4.5). La lectura de administración: una persona por vez, alcance global, las dadas de baja si se piden. Criterios `CA-MV-410` a `CA-MV-414`. | Responsable del proyecto |
