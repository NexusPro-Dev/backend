# SPEC — `RF-MV-022` Consultar mis saldos

| Campo | Valor |
|---|---|
| Requerimiento | `RF-MV-022` |
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

Que cada persona sepa **cuánto tiene** —lo que puede retirar, lo que está retenido en retiros pendientes y sus puntos— y **por qué**: cada abono, bono y retiro que movió esos saldos, en orden.

---

## 2. Contexto

**Desde la etapa 6 cada persona tiene saldos** ([`requirements/mv.md`](../../../requirements/mv.md) v0.44.0 §4.3), y hasta este requerimiento no los podía ver. **Y desde el 26-09-2026 «mis compras» trae solo ventas** (`RN-MV-047`), de modo que sus retiros, abonos de comisión y bonos **no aparecían en ninguna consulta propia**. Este requerimiento cierra las dos cosas.

**Son dos preguntas y dos operaciones**, cada una con su permiso (`RN-SEG-014`), como el listado y el detalle de `RF-MV-008`: **«¿cuánto tengo?»** —una respuesta corta, que se pinta en una cabecera— y **«¿por qué?»** —un historial que crece sin límite y se pagina—.

### 2.1 Lo que este requerimiento decide

| Decisión | Qué se decidió |
|---|---|
| **Solo lo propio** | Quien pregunta sale de la credencial, y no hay forma de indicar a nadie más. Ver los saldos de otro es administración y no se ha pedido |
| **Por moneda, las tres cuentas juntas** | La billetera, lo retenido y los puntos de cada moneda en la que la persona tenga algo |
| **Una moneda sin cuentas no aparece** | No se inventan saldos a cero para monedas que la persona nunca usó |
| **El historial es asiento a asiento** | Cada fila es un cambio en una cuenta, con el saldo que dejó. Es lo único que responde «¿por qué tengo 350?» sin sumar nada |
| **El saldo es el guardado, no el sumado** | Se publica el saldo de la cuenta, que es la copia de sus asientos (`RN-MV-041`). Que coincidan lo garantiza el libro, no esta consulta |

---

## 3. Actores

| Actor | Rol en esta funcionalidad |
|---|---|
| Cualquier persona con `movements:read-own-balances` | Consulta sus saldos |
| Cualquier persona con `movements:list-own-entries` | Consulta el historial de sus saldos |

---

## 4. Alcance

### 4.1 Incluye

- **Los saldos**: por cada moneda, la billetera, lo retenido y los puntos.
- **El historial**: los cambios en esas cuentas, del más reciente al más antiguo, paginados y filtrables por moneda, por cuenta y por periodo.

### 4.2 No incluye

- **Los saldos de otra persona**, ni los de la empresa.
- **Sumar** —totales por periodo, cuánto ganó en septiembre—.
- **Exportar.**

---

## 5. Reglas de negocio aplicables

| Regla | Cómo aplica |
|---|---|
| `RN-MV-041` | Tres saldos por moneda; el publicado es la copia guardada |
| `RN-MV-042` | Cada fila del historial es un asiento, con el saldo que dejó |
| `RN-MV-047` | Lo que «mis compras» no trae se ve aquí |

**Ninguna regla nueva.**

---

## 6. Datos

### 6.1 Entrada — los saldos

Ninguna. Quien pregunta sale de la credencial.

### 6.2 Salida — los saldos

Por cada moneda en la que la persona tenga al menos una cuenta: **la moneda**, **la billetera**, **lo retenido** y **los puntos**, con los decimales de la moneda. Una cuenta que no existe se publica **como cero** dentro de una moneda que sí aparece.

### 6.3 Entrada — el historial

| Dato | Obligatorio | Descripción |
|---|---|---|
| Página y tamaño | No | Como en todos los listados |
| Moneda | No | Solo los cambios en esa moneda |
| Cuenta | No | `BILLETERA`, `RETENIDO` o `PUNTOS`. Uno que no sea de persona es un error: es un conjunto cerrado |
| Desde, hasta | No | Sobre cuándo se escribió el asiento, rango semiabierto; «desde» posterior a «hasta» es un error |

### 6.4 Salida — el historial

Cada fila: **cuándo**, **la cuenta** y su moneda, **cuánto** —con signo: positivo entró, negativo salió—, **el saldo que dejó**, **qué evento fue** —solicitud, aprobación, rechazo o abono— y **el movimiento** que lo produjo: su código, su tipo, su estado y, si lo tiene, su concepto o su motivo de rechazo.

---

## 7. Precondiciones y postcondiciones

| Tipo | Condición |
|---|---|
| Precondición | El actor tiene el permiso de la operación |
| Postcondición | Nada cambia |

---

## 8. Flujo principal

**Saldos**: el sistema busca las cuentas del actor, las agrupa por moneda y las devuelve.

**Historial**: el sistema busca los asientos de las cuentas del actor, aplica los filtros, ordena del más reciente al más antiguo y devuelve la página.

---

## 9. Flujos alternativos

### FA-001 — La persona no tiene ninguna cuenta

Saldos: una lista **vacía**. Historial: una página **vacía**. Ninguna de las dos es un error.

---

## 10. Excepciones

| ID | Situación | Resultado |
|---|---|---|
| `EX-001` | Un filtro malformado, una cuenta que no es de persona, o el periodo invertido | Rechazo |
| `EX-002` | Quien pregunta no tiene el permiso de la operación | Prohibido |

---

## 11. Validaciones

| ID | Validación |
|---|---|
| `VAL-001` | La moneda, si viene, es un identificador válido |
| `VAL-002` | La cuenta, si viene, es de las tres de una persona |
| `VAL-003` | «Desde» no es posterior a «hasta» |

---

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-MV-251` | Los saldos traen, por moneda, **la billetera, lo retenido y los puntos**, y una cuenta que no existe sale como cero dentro de su moneda |
| `CA-MV-252` | Una persona sin cuentas recibe una **lista vacía** |
| `CA-MV-253` | Tras pedir un retiro, los saldos muestran **la billetera descontada y lo retenido sumado**; tras negarlo, **vuelven** |
| `CA-MV-254` | **No aparece ninguna cuenta ajena** ni de la empresa, aunque quien pregunta tenga permisos de administración |
| `CA-MV-255` | El historial trae **cada asiento** de las cuentas propias, del más reciente al más antiguo, con su signo, el saldo que dejó, el evento y el movimiento con su código y su tipo |
| `CA-MV-256` | Un retiro pedido y aprobado aparece como **dos filas de `SOLICITUD`** —billetera y retenido— y **una de `APROBACION`** —solo la de retenido: la otra es de la empresa— |
| `CA-MV-257` | Los filtros por moneda, cuenta y periodo **se combinan**; una cuenta de empresa o un periodo invertido responden rechazo |
| `CA-MV-258` | El historial va **paginado** y el orden es estable entre páginas |
| `CA-MV-259` | Cada operación exige **su** permiso: con uno solo de los dos, la otra responde prohibido; sin autenticar, `401` |

---

## 13. Casos límite

| Caso | Comportamiento |
|---|---|
| Un bono de una moneda desactivada | La moneda aparece con su saldo |
| Dos asientos en el mismo instante | Orden estable, por identificador |
| Un lote abonado por importe cero | No escribió asientos (`RN-MV-044`) y no aparece en el historial; el lote sí figura en `CM` |

---

## 14. Preguntas abiertas

**Si administración necesita ver los saldos de cualquiera.** No se ha pedido; sería otra operación con otro permiso.

---

## 15. Control de cambios

| Versión | Fecha | Cambio | Autor |
|---|---|---|---|
| 0.1.0 | 26-09-2026 | Primera versión, con la etapa 6 de `MV` ([`requirements/mv.md`](../../../requirements/mv.md) v0.46.0). **Dos operaciones y dos permisos** —los saldos y su historial—, porque son dos preguntas; el historial es **asiento a asiento** y es donde la persona ve sus retiros, abonos y bonos. Criterios `CA-MV-251` a `CA-MV-259`. | Responsable del proyecto |
