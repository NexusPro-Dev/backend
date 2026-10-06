# SPEC — `RF-MV-052` Ajustar los puntos de una persona

| Campo | Valor |
|---|---|
| Requerimiento | `RF-MV-052` |
| Módulo | `MV` — Movimientos |
| Versión | 0.2.0 |
| Estado | **Aprobada** |
| Autor | Responsable técnico |
| Aprobada por | Responsable del proyecto |
| Fecha de aprobación | 05-10-2026 |

!!! info "Qué va en este documento"

    **Qué debe pasar, y por qué.** Nada más.

    **Prueba de pertenencia:** si un cambio de tecnología lo invalidaría, no pertenece aquí — va a `plan.md`. No se nombran tablas, clases, endpoints ni librerías.

---

## 1. Objetivo

Que administración pueda **sumar o restar puntos a una persona a mano**, **dejando escrito por qué** y, si lo hay, **con qué comprobante**. El caso que lo pide: la persona le pagó a la empresa **por fuera de la plataforma** —una consignación directa a la cuenta empresarial— y hay que darle los puntos que pagó.

---

## 2. Contexto

Hasta hoy el único camino que llenaba los puntos de una persona era **comprarlos** (`RF-MV-027`): la compra la pide el propio comprador, lleva un importe en dinero y una tasa, y espera a que su pago se confirme. **No sirve para lo que pasó fuera**: no hay pago en la plataforma que confirmar, y administración no puede comprar en nombre de otro.

Decisiones del responsable del proyecto del 05-10-2026, preguntadas antes de escribir ([`requirements/mv.md`](../../../requirements/mv.md) v0.83.0 §4.11):

### 2.1 Lo que este requerimiento decide

| Decisión | Qué se decidió |
|---|---|
| **Se ajustan puntos, no dinero** | Administración escribe **cuántos puntos**; no hay importe en dinero ni se aplica ninguna tasa. La cuenta entre el dinero recibido y los puntos la hace quien ajusta |
| **Suma y resta** | Un ajuste negativo corrige un error. **Nunca deja el saldo de puntos por debajo de cero**: si no alcanza, no se hace nada |
| **El motivo es obligatorio; la referencia, opcional** | Un ajuste sin motivo son puntos regalados o quitados que nadie sabe explicar. La referencia es el número del comprobante de la consignación, cuando lo hay |
| **La clave de idempotencia es obligatoria** | Sin pago detrás, nada impide que un doble clic ajuste dos veces. La misma petición repetida devuelve el ajuste ya hecho |
| **Nace confirmado** | No hay cobro que esperar: los puntos se mueven en el acto |
| **A una persona que exista y no esté eliminada** | A quien esté bloqueado o a la espera de su primer depósito **se le puede ajustar**, como al bono (`RF-MV-023`): el saldo es suyo |
| **No se revierte** | Un ajuste equivocado se compensa con otro del signo contrario, que deja su propio rastro |
| **No comisiona** | La comisión llega, como siempre, cuando los puntos se gastan en una venta (`RN-MV-051`) |

---

## 3. Actores

| Actor | Rol en esta funcionalidad |
|---|---|
| Quien tiene `movements:adjust-points` | Ajusta los puntos de cualquier persona |

---

## 4. Alcance

### 4.1 Incluye

- Registrar el ajuste, **confirmado**, a nombre de quien lo recibe, con su comprobante, sus puntos con signo, su motivo y su referencia.
- Sumar los puntos desde la cuenta de puntos emitidos de la empresa, o restarlos hacia ella.
- Reconocer la misma petición repetida.

### 4.2 No incluye

- **Revertir un ajuste.**
- **Convertir un importe en dinero a puntos.** Es la compra (`RF-MV-027`).
- **Adjuntar el comprobante como archivo.**
- **Topes, o aprobación por un segundo actor.**
- **Listar los ajustes aparte.** Se ven en el historial de saldos de la persona (`RF-MV-022`) y en el libro entero (`RF-MV-006`).

---

## 5. Reglas de negocio aplicables

| Regla | Cómo aplica |
|---|---|
| `RN-MV-076` | **Nueva.** Motivo obligatorio, referencia opcional, clave obligatoria; nace confirmado; suma o resta sin dejar el saldo en negativo; no se revierte |
| `RN-MV-042` | Dos asientos que suman cero |
| `RN-MV-041` | El saldo de una cuenta de persona nunca es negativo |
| `RN-MV-046` | Sin líneas, sin vendedor, sin paquete, sin comisión |
| `RN-MV-026` | El sujeto es quien recibe el ajuste, no quien lo hace |

---

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción |
|---|---|---|
| Persona | Sí | A quién |
| Moneda | Sí | En qué cuenta de puntos: los puntos son de una moneda (§4.4 de `requirements/mv.md`) |
| Puntos | Sí | **Distintos de cero**, con dos decimales como mucho. Positivos suman, negativos restan |
| Motivo | Sí | Por qué, escrito para una persona. Con contenido y acotado |
| Referencia | No | El comprobante que lo soporta. Si viene, con contenido y acotada |
| Clave de idempotencia | Sí | Una por ajuste; se repite tal cual al reenviar |

### 6.2 Salida

**El ajuste** —comprobante, persona, moneda, puntos con su signo, motivo, referencia, cuándo— **confirmado**, y **el saldo de puntos** en que dejó a la persona.

---

## 7. Precondiciones y postcondiciones

| Tipo | Condición |
|---|---|
| Precondición | El actor tiene `movements:adjust-points`; la persona existe y no está eliminada; la moneda existe; los datos y la clave son válidos; si resta, la persona tiene al menos esos puntos |
| Postcondición | Existe un ajuste confirmado a nombre de la persona; su saldo de puntos cambió exactamente en los puntos del ajuste y la cuenta de puntos emitidos de la empresa en lo contrario; está auditado con quién lo hizo |

---

## 8. Flujo principal

1. El actor indica persona, moneda, puntos, motivo, la referencia si la hay, y la clave.
2. El sistema valida todo, **antes de tocar nada**.
3. Si la clave ya existe, responde según `FA-001` o `EX-005`, y termina.
4. Registra el ajuste confirmado y mueve los puntos, en un solo acto.
5. Audita y devuelve el ajuste con el saldo resultante.

---

## 9. Flujos alternativos

### FA-001 — La misma petición llega dos veces

Misma clave, misma persona, moneda y puntos: **se devuelve el ajuste ya hecho** y no se mueve nada más.

---

## 10. Excepciones

| ID | Situación | Resultado |
|---|---|---|
| `EX-001` | Algún dato falta o es inválido —puntos en cero o con decimales de más, motivo vacío o largo, referencia en blanco o larga, clave ausente o malformada— | Rechazo, antes de tocar nada |
| `EX-002` | La persona o la moneda no existen, o la persona está eliminada | Rechazo, diciendo cuál |
| `EX-004` | Quien pregunta no tiene `movements:adjust-points` | Prohibido |
| `EX-005` | La clave ya se usó para **otra** petición | Conflicto. Nada cambia |
| `EX-006` | Una resta mayor que los puntos que tiene la persona | Rechazo, con los puntos disponibles. **Nada cambia**: ni el saldo ni el movimiento |

---

## 11. Validaciones

| ID | Validación |
|---|---|
| `VAL-001` | Persona y moneda presentes |
| `VAL-002` | Puntos presentes y distintos de cero |
| `VAL-003` | Puntos con dos decimales como mucho |
| `VAL-004` | Motivo con contenido, hasta 500 caracteres |
| `VAL-005` | Referencia, si viene, con contenido y hasta 120 caracteres |
| `VAL-006` | Clave con la forma de `RF-MV-018` `VAL-002` |

---

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-MV-636` | Un ajuste positivo registra un **movimiento de ajuste confirmado** a nombre de la persona, con comprobante de ajuste, su motivo y su referencia; **sus puntos suben** y los puntos emitidos de la empresa **bajan** en lo mismo, y la respuesta trae el saldo resultante |
| `CA-MV-637` | El ajuste son **dos asientos de ajuste que suman cero**; **no tiene ningún pago** ni importe en dinero, y guarda los puntos con su signo |
| `CA-MV-638` | Un ajuste **negativo** baja los puntos de la persona y sube los emitidos de la empresa |
| `CA-MV-639` | Una resta **mayor que el saldo** se rechaza con los puntos disponibles, y **nada cambia**: ni saldo ni movimiento |
| `CA-MV-640` | La **misma petición repetida** devuelve el mismo ajuste y el saldo cambia **una sola vez** |
| `CA-MV-641` | La misma clave con **otros datos** responde conflicto y no mueve nada |
| `CA-MV-642` | Una persona **bloqueada** recibe el ajuste; una **eliminada** o inexistente, o una moneda inexistente, no |
| `CA-MV-643` | Puntos en cero o con decimales de más, sin motivo, referencia en blanco o demasiado larga, o sin clave: rechazo, y **nada cambia** |
| `CA-MV-644` | Sin `movements:adjust-points` responde prohibido; sin autenticar, `401` |
| `CA-MV-645` | El ajuste aparece en el **historial de saldos** de la persona (`RF-MV-022`), en su cuenta de puntos |
| `CA-MV-646` | Queda **auditado**, con quién lo hizo, el motivo y la referencia |

---

## 13. Casos límite

| Caso | Comportamiento |
|---|---|
| La persona no tiene cuenta de puntos en esa moneda | Se crea al sumar. Al restar, su saldo es cero y la resta se rechaza (`EX-006`) |
| La moneda no vende puntos —no tiene tasa vigente— | **Se admite**: el saldo es de la persona y la tasa puede fijarse después. Gastarlos sí exige tasa (`RN-MV-052`) |
| Una resta que deja el saldo exactamente en cero | Se admite |
| Quien ajusta se ajusta a sí mismo | Se admite y queda auditado, como el bono |

---

## 14. Preguntas abiertas

**Revertir un ajuste**, **topes** y **aprobación por un segundo actor**: no se han pedido.

---

## 15. Control de cambios

| Versión | Fecha | Cambio | Autor |
|---|---|---|---|
| 0.1.0 | 05-10-2026 | Primera versión ([`requirements/mv.md`](../../../requirements/mv.md) v0.83.0 §4.11, `RN-MV-076`), con las decisiones del responsable del proyecto: **ajuste libre en puntos**, **suma y resta** sin saldo negativo, **motivo obligatorio y referencia opcional**, clave de idempotencia obligatoria. Criterios `CA-MV-636` a `CA-MV-646`. | Responsable del proyecto |
| 0.2.0 | 06-10-2026 | **El ajuste admite un comprobante como archivo** ([`requirements/mv.md`](../../../requirements/mv.md) v0.88.0 §4.12, `RN-MV-077`): lo que §4.2 dejaba fuera lo trae [`RF-MV-057`](../057-adjuntar-comprobante-de-ajuste/spec.md), por la misma petición o después. Sin archivo, este requerimiento no cambia. | Responsable del proyecto |
