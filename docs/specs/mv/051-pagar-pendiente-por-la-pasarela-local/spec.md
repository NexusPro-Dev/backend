# SPEC — `RF-MV-051` Pagar por la pasarela local un pago pendiente propio

| Campo | Valor |
|---|---|
| Requerimiento | `RF-MV-051` |
| Módulo | `MV` — Movimientos |
| Versión | 0.1.0 |
| Estado | **Aprobada** |
| Autor | Responsable técnico |
| Aprobada por | Responsable del proyecto |
| Fecha de aprobación | 05-10-2026 |

!!! info "Qué va en este documento"

    **Qué debe pasar, y por qué.** Nada más.

---

## 1. Objetivo

Que quien tiene una compra propia pendiente con `PSE` pueda **ir a pagarla** a la página de la pasarela local: **retomar** el cobro que ya abrió, o **empezarlo** si la compra la registró otra persona.

---

## 2. Contexto

([`requirements/mv.md`](../../../requirements/mv.md) v0.78.0 §4.10.) Es el gemelo de `RF-MV-042` para la pasarela local, cuya argumentación se hereda: la venta que registra un funcionario y la del alta por enlace **nacen pendientes sin cobro**, porque no hay nadie al otro lado para pagar; y quien sí pagó pero cerró la página **necesita volver a ella**.

### 2.1 Lo que este requerimiento decide

| Decisión | Qué se decidió |
|---|---|
| **Sin cobro, se abre** | Con la conversión vigente **hoy** del país de quien paga (`RN-MV-063`) |
| **Con cobro abierto, se devuelve el mismo** | Su dirección, sin abrir otro |
| **Solo con `PSE`** | Si el pago pendiente es con otro método, se cambia con volver a pagar (`RF-MV-018`) |
| **Solo lo propio** | Una compra de otra persona no existe para quien pregunta |

---

## 3. Actores

| Actor | Rol en esta funcionalidad |
|---|---|
| Quien tiene `movements:pay-pending-locally` | Paga su propia compra pendiente. Lo porta todo rol por su tipo |

---

## 4. Alcance

### 4.1 Incluye

- Abrir el cobro de un pago pendiente propio con `PSE` sin cobro, o devolver el que tiene.

### 4.2 No incluye

- **Cambiar de método**: `RF-MV-018`.
- **Las compras de otros.**

---

## 5. Reglas de negocio aplicables

| Regla | Cómo aplica |
|---|---|
| `RN-MV-063` | La conversión vigente del país de quien paga |
| `RN-MV-064` | Lo confirma la pasarela, no esta operación |

---

## 6. Datos

### 6.1 Entrada

La compra.

### 6.2 Salida

**El cobro local**: el pago, la pasarela, la dirección de la página, la moneda y el importe.

---

## 7. Precondiciones y postcondiciones

| Tipo | Condición |
|---|---|
| Precondición | La compra es propia y tiene un pago pendiente con `PSE`; la pasarela local está configurada |
| Postcondición | El pago tiene un cobro abierto. Nada está confirmado |

---

## 8. Flujo principal

1. Quien compró pide pagar su compra pendiente.
2. Si el pago no tiene cobro, el sistema lo abre como `RF-MV-048`; si lo tiene, devuelve el mismo.

---

## 9. Flujos alternativos

### FA-001 — El cobro abierto ya terminó en la pasarela

Si la pasarela ya lo cerró sin aprobarlo, el pago se rechaza por `RF-MV-049` y la respuesta dice que hay que volver a pagar.

---

## 10. Excepciones

| ID | Situación | Resultado |
|---|---|---|
| `EX-001` | No existe una compra propia con ese identificador | No encontrado |
| `EX-002` | La compra no tiene pago pendiente, o es con otro método | Conflicto |
| `EX-003` | Sin conversión vigente para el país de quien paga | Conflicto |
| `EX-004` | La pasarela local está apagada o no responde | No disponible |
| `EX-005` | Sin `movements:pay-pending-locally` | Prohibido |

---

## 11. Validaciones

Ninguna más allá del identificador.

---

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-MV-625` | Una venta propia registrada **por un funcionario** con `PSE`, sin cobro: la operación **abre el cobro** y devuelve su dirección |
| `CA-MV-626` | Con un cobro **ya abierto**, devuelve **el mismo**, sin abrir otro |
| `CA-MV-627` | Una compra **de otra persona** responde no encontrado; una **sin pago pendiente** o **con otro método**, conflicto |
| `CA-MV-628` | Sin conversión vigente: conflicto; con la pasarela apagada: no disponible |
| `CA-MV-629` | Sin `movements:pay-pending-locally` responde prohibido; sin token, `401` |

---

## 13. Casos límite

| Caso | Comportamiento |
|---|---|
| La conversión cambió desde que se registró la venta | Se cobra con la vigente al abrir el cobro |

---

## 14. Preguntas abiertas

Ninguna.

---

## 15. Control de cambios

| Versión | Fecha | Cambio | Autor |
|---|---|---|---|
| 0.1.0 | 05-10-2026 | Primera versión, con la pasarela local ([`requirements/mv.md`](../../../requirements/mv.md) v0.78.0 §4.10). El gemelo de `RF-MV-042`: abrir o retomar el cobro local de una compra propia pendiente. Criterios `CA-MV-625` a `CA-MV-629`. | Responsable del proyecto |
