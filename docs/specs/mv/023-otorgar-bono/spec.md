# SPEC — `RF-MV-023` Otorgar un bono

| Campo | Valor |
|---|---|
| Requerimiento | `RF-MV-023` |
| Módulo | `MV` — Movimientos |
| Versión | 0.1.0 |
| Estado | **Aprobada** |
| Autor | Responsable técnico |
| Aprobada por | Responsable del proyecto |
| Fecha de aprobación | 26-09-2026 |

!!! info "Qué va en este documento"

    **Qué debe pasar, y por qué.** Nada más.

    **Prueba de pertenencia:** si un cambio de tecnología lo invalidaría, no pertenece aquí — va a `plan.md`. No se nombran tablas, clases, endpoints ni librerías.

---

## 1. Objetivo

Que la empresa pueda **darle dinero a una persona** —un premio, una compensación, un incentivo— **dejando escrito por qué**, y que ese dinero quede en su billetera, listo para retirarlo.

---

## 2. Contexto

**Es la segunda forma de llenar una billetera**, junto al abono de un lote de comisión (`RF-MV-024`), por decisión del responsable del proyecto ([`requirements/mv.md`](../../../requirements/mv.md) v0.44.0 §4.3): el bono es un tipo de movimiento, en el mismo libro que las ventas y los retiros.

**Un bono no es un pago.** No entra dinero de fuera: sale de la cuenta de bonos de la empresa y entra en la billetera de la persona. Por eso **nace confirmado y sin ningún pago** —no hay cobro que esperar—, y por eso **no hay nada que lo detenga si se otorga dos veces**: la única defensa contra el doble clic es la clave de idempotencia (`RN-MV-045`).

### 2.1 Lo que este requerimiento decide

| Decisión | Qué se decidió |
|---|---|
| **El motivo es obligatorio** | Un bono sin motivo es dinero regalado que nadie sabe explicar. Se guarda en el movimiento, no solo en la auditoría |
| **La clave de idempotencia es obligatoria** | La misma petición repetida devuelve el bono que ya otorgó |
| **A una persona que exista y no esté eliminada** | A quien esté bloqueado o a la espera de su primer depósito **se le puede otorgar**: el saldo es suyo, y lo que no puede hacer es retirarlo mientras su cuenta no opere (`RF-MV-019`) |
| **No se revierte** | Un bono otorgado por error se compensa con otra operación que no existe todavía. Queda declarado |
| **Sin tope** | No se ha decidido ninguno |

---

## 3. Actores

| Actor | Rol en esta funcionalidad |
|---|---|
| Quien tiene `movements:grant-bonus` | Otorga un bono a cualquier persona |

---

## 4. Alcance

### 4.1 Incluye

- Registrar el bono, **confirmado**, a nombre de quien lo recibe, con su comprobante y su motivo.
- Abonarlo en su billetera desde la cuenta de bonos de la empresa.
- Reconocer la misma petición repetida.

### 4.2 No incluye

- **Revertir un bono.**
- **Bonos en puntos.** Los puntos son de la etapa 3.
- **Bonos automáticos** —por metas, por fechas—. Este requerimiento es el acto manual.
- **Cupones de descuento**, que no son un movimiento (`requirements/mv.md` §4.3).

---

## 5. Reglas de negocio aplicables

| Regla | Cómo aplica |
|---|---|
| `RN-MV-045` | Abona la billetera desde `BONOS`; motivo y clave obligatorios; nace confirmado |
| `RN-MV-042` | Dos asientos que suman cero, evento `ABONO` |
| `RN-MV-046` | Sin líneas, sin vendedor, sin paquete |
| `RN-MV-026` | El sujeto es quien lo recibe, no quien lo otorga |
| `RN-MV-014` | El importe respeta los decimales de su moneda |

**Ninguna regla nueva.**

---

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción |
|---|---|---|
| Persona | Sí | Quién lo recibe |
| Moneda | Sí | En qué billetera |
| Importe | Sí | Mayor que cero, con los decimales de la moneda |
| Motivo | Sí | Por qué, escrito para una persona. Con contenido y acotado |
| Clave de idempotencia | Sí | Una por bono; se repite tal cual al reenviar |

### 6.2 Salida

**El bono** —comprobante, persona, importe, moneda, motivo, cuándo— **confirmado**.

---

## 7. Precondiciones y postcondiciones

| Tipo | Condición |
|---|---|
| Precondición | El actor tiene `movements:grant-bonus`; la persona existe y no está eliminada; la moneda existe; importe, motivo y clave son válidos |
| Postcondición | Existe un bono confirmado a nombre de la persona; su billetera subió en el importe y la cuenta de bonos de la empresa bajó en lo mismo; está auditado con quién lo otorgó |

---

## 8. Flujo principal

1. El actor indica persona, moneda, importe, motivo y clave.
2. El sistema valida todo, **antes de tocar nada**.
3. Si la clave ya existe, responde según `FA-001` o `EX-005`, y termina.
4. Registra el bono confirmado y lo abona, en un solo acto.
5. Audita y devuelve el bono.

---

## 9. Flujos alternativos

### FA-001 — La misma petición llega dos veces

Misma clave, misma persona, moneda e importe: **se devuelve el bono ya otorgado** y no se abona nada más.

---

## 10. Excepciones

| ID | Situación | Resultado |
|---|---|---|
| `EX-001` | Algún dato falta o es inválido —importe no positivo o con decimales de más, motivo vacío, clave malformada— | Rechazo, antes de tocar nada |
| `EX-002` | La persona no existe o está eliminada | Rechazo, diciendo que no existe |
| `EX-003` | La moneda no existe | Rechazo |
| `EX-004` | Quien pregunta no tiene `movements:grant-bonus` | Prohibido |
| `EX-005` | La clave ya se usó para **otro** bono | Conflicto. Nada cambia |

---

## 11. Validaciones

| ID | Validación |
|---|---|
| `VAL-001` | Persona y moneda presentes y válidas |
| `VAL-002` | Importe mayor que cero y con los decimales de la moneda |
| `VAL-003` | Motivo con contenido y dentro de la longitud máxima |
| `VAL-004` | Clave con la forma de `RF-MV-018` `VAL-002` |

---

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-MV-260` | Se registra un **bono confirmado** a nombre de la persona, con comprobante de bono y su motivo; **su billetera sube** y la cuenta de bonos de la empresa **baja** en el importe |
| `CA-MV-261` | El abono son **dos asientos de `ABONO` que suman cero**, y el bono **no tiene ningún pago** |
| `CA-MV-262` | La **misma petición repetida** devuelve el mismo bono y la billetera **sube una sola vez** |
| `CA-MV-263` | La misma clave con **otros datos** responde conflicto y no abona nada |
| `CA-MV-264` | Una persona **bloqueada** o a la espera de su primer depósito **recibe** el bono; una **eliminada** o inexistente, no |
| `CA-MV-265` | Sin motivo, importe no positivo, decimales de más o sin clave: rechazo, y **nada cambia** |
| `CA-MV-266` | Sin `movements:grant-bonus` responde prohibido; sin autenticar, `401` |
| `CA-MV-267` | El bono aparece en el **historial de saldos** de la persona (`RF-MV-022`) y puede **retirarse** (`RF-MV-019`) |
| `CA-MV-268` | Queda **auditado**, con quién lo otorgó |

---

## 13. Casos límite

| Caso | Comportamiento |
|---|---|
| La persona no tiene billetera en esa moneda | Se crea al abonar |
| La empresa no tiene cuenta de bonos en esa moneda | Se crea, y queda en negativo: es una contrapartida |
| Quien otorga se otorga un bono a sí mismo | Se admite y queda auditado. Impedirlo exigiría decidir qué pasa con un superadministrador, y no se ha pedido |

---

## 14. Preguntas abiertas

**Revertir un bono otorgado por error.** No existe.

**Topes y aprobación por un segundo actor.** No se han pedido.

---

## 15. Control de cambios

| Versión | Fecha | Cambio | Autor |
|---|---|---|---|
| 0.1.0 | 26-09-2026 | Primera versión, con la etapa 6 de `MV` ([`requirements/mv.md`](../../../requirements/mv.md) v0.46.0). **Nace confirmado y sin pago**, con **motivo y clave de idempotencia obligatorios** —sin pago detrás, la clave es la única defensa contra el doble clic—. Se otorga también a cuentas bloqueadas o en espera, que no podrán retirarlo hasta operar. Criterios `CA-MV-260` a `CA-MV-268`. | Responsable del proyecto |
