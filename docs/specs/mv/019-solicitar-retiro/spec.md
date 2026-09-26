# SPEC — `RF-MV-019` Solicitar un retiro

| Campo | Valor |
|---|---|
| Requerimiento | `RF-MV-019` |
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

Que una persona pueda **pedir que le paguen** lo que tiene en su billetera —lo que ganó por comisiones y bonos—, y que **ese dinero quede apartado en el acto**, de modo que nadie pueda gastarlo ni pedirlo dos veces mientras la empresa decide.

---

## 2. Contexto

**Es la primera salida de dinero del sistema.** Hasta la etapa 6 ([`requirements/mv.md`](../../../requirements/mv.md) v0.44.0 §4.3) la plataforma registraba lo que entraba —ventas— y nada de lo que se debía a nadie. Desde el 26-09-2026 cada persona tiene **saldos** —la billetera, lo retenido y los puntos—, y el retiro es la única forma de que lo de la billetera salga de la plataforma.

**Pedir no es cobrar.** El retiro nace **pendiente**, y lo resuelve después alguien de la empresa: lo aprueba cuando el dinero salió (`RF-MV-020`) o lo niega con su motivo (`RF-MV-021`). **Lo que sí ocurre al pedir es que el importe sale de la billetera y queda retenido** (`RN-MV-043`): el saldo que la persona ve es el que de verdad puede usar, y dos peticiones sobre el mismo dinero no pueden pasar las dos.

**Por qué retener y no restar al calcular.** Lo argumenta `requirements/mv.md` §4.3: restar los retiros pendientes funciona mientras cada camino que gasta se acuerde de hacerlo, y el primero que lo olvide paga dos veces. Retener hace que el saldo **sea** el disponible.

### 2.1 Lo que este requerimiento decide

| Decisión | Qué se decidió |
|---|---|
| **Solo de la billetera propia** | Quien pide es quien retira, y retira lo suyo. Ni los puntos (no se retiran, `RN-MV-041`) ni lo retenido |
| **En una moneda** | La persona tiene una billetera por moneda, y un retiro sale de una. Retirar en otra moneda que la del saldo exigiría una conversión que nadie ha pedido |
| **Si no alcanza, no se pide** | Se rechaza **sin tocar nada**. No hay retiros parciales ni en descubierto |
| **Sin clave de idempotencia** | Una petición repetida produce **dos retiros**, y se acepta a sabiendas: **cada uno retiene lo suyo**, de modo que el segundo solo pasa si el saldo alcanza para los dos, y ninguno puede pagar dinero que la persona no tenía. El daño de repetir es un retiro de más que se niega, no un pago de más. Es la diferencia con `RF-MV-018`, donde repetir **cobraba** |
| **Una cuenta que todavía no opera no retira** | Una cuenta en espera de su primer depósito autentica y no opera (`RN-SP-026`), como con las ventas (`RN-MV-008`) |
| **Sin mínimo ni máximo** | No se ha decidido ninguno. El importe tiene que ser mayor que cero y respetar los decimales de la moneda |

---

## 3. Actores

| Actor | Rol en esta funcionalidad |
|---|---|
| Quien tiene saldo, con `movements:request-withdrawal` | Pide retirar de su billetera |

---

## 4. Alcance

### 4.1 Incluye

- Registrar el retiro **pendiente**, a nombre de quien lo pide, con su comprobante.
- **Apartar el importe**: sale de la billetera y queda retenido, en el mismo acto.
- Devolver el retiro y los saldos como quedan.
- Que las operaciones de la **venta** —confirmar, anular, rechazar el pago, volver a pagar— **no alcancen a un retiro** aunque se les pase su identificador.

### 4.2 No incluye

- **Aprobarlo o negarlo** (`RF-MV-020`, `RF-MV-021`).
- **A dónde se paga**: los datos bancarios no se guardan todavía (`requirements/mv.md` v0.45.0 §4.3); la persona los acuerda con la empresa por fuera, hasta que llegue la pasarela de salida.
- **Que la persona cancele su propio retiro pendiente.** No se ha pedido; hoy se lo pide a la empresa, que lo niega.
- **Retirar de los puntos o en otra moneda.**

---

## 5. Reglas de negocio aplicables

| Regla | Cómo aplica |
|---|---|
| `RN-MV-043` | Pedir retiene; se rechaza si la billetera no alcanza; nace pendiente |
| `RN-MV-041` | La billetera no puede quedar en negativo; los puntos no se retiran; la cuenta se crea la primera vez que hace falta |
| `RN-MV-042` | La retención son dos asientos que suman cero; las cuentas se bloquean en orden |
| `RN-MV-046` | El retiro no lleva líneas, ni vendedor, ni paquete |
| `RN-MV-026` | El sujeto del retiro es quien lo pide |
| `RN-MV-016` | Lleva comprobante legible, con su propio prefijo |
| `RN-MV-014` | El importe respeta los decimales de su moneda |
| `RN-SP-026` | Una cuenta en espera de su primer depósito no opera |

**Ninguna regla nueva.**

---

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción |
|---|---|---|
| Moneda | Sí | De qué billetera se retira |
| Importe | Sí | Cuánto. Mayor que cero, con los decimales de la moneda |

**Quién retira no es un dato de entrada**: sale de la credencial.

### 6.2 Salida

**El retiro** —comprobante, estado, importe, moneda, cuándo se pidió— y **los tres saldos de esa moneda** como quedan: la billetera ya descontada y lo retenido con el importe sumado. Devolver los saldos ahorra a quien pide una segunda consulta para ver que el dinero se apartó.

---

## 7. Precondiciones y postcondiciones

| Tipo | Condición |
|---|---|
| Precondición | El actor tiene `movements:request-withdrawal` y su cuenta opera; la moneda existe; el importe es válido; la billetera de esa moneda alcanza |
| Postcondición | Existe un retiro pendiente a su nombre; su billetera bajó en el importe y lo retenido subió en lo mismo; los dos asientos suman cero; todo está auditado |

---

## 8. Flujo principal

1. La persona indica moneda e importe.
2. El sistema valida el importe y la moneda, **antes de tocar ningún saldo**.
3. Registra el retiro pendiente, con su comprobante.
4. **Aparta el importe**: resta de la billetera y suma a lo retenido, bloqueando las dos cuentas en orden y **fallando si la billetera quedaría en negativo**.
5. Audita.
6. Devuelve el retiro y los saldos.

**Los pasos 3 y 4 son un solo acto**: si la billetera no alcanza, el retiro tampoco queda registrado.

---

## 9. Flujos alternativos

### FA-001 — La persona nunca tuvo saldo en esa moneda

No tiene billetera en esa moneda, y la respuesta es la misma que si la tuviera a cero: **no alcanza**. No se crea una cuenta para decir que está vacía.

### FA-002 — Dos peticiones a la vez

Las dos intentan apartar. **La segunda espera a la primera** —la cuenta está bloqueada—, y pasa solo si lo que queda alcanza. Con 100 en la billetera, dos retiros de 60 simultáneos producen **uno** pendiente y un rechazo; dos de 50, **dos**.

---

## 10. Excepciones

| ID | Situación | Resultado |
|---|---|---|
| `EX-001` | El importe falta, es cero o negativo, o tiene más decimales que la moneda | Rechazo, antes de tocar nada |
| `EX-002` | La moneda no existe | Rechazo |
| `EX-003` | **La billetera no alcanza** —o no existe en esa moneda— | Conflicto, diciendo el disponible. Nada cambia |
| `EX-004` | La cuenta del actor está en espera de su primer depósito | Conflicto (`RN-SP-026`). Nada cambia |
| `EX-005` | Quien pregunta no tiene `movements:request-withdrawal` | Prohibido |

---

## 11. Validaciones

| ID | Validación |
|---|---|
| `VAL-001` | La moneda está presente y es un identificador válido |
| `VAL-002` | El importe está presente y es mayor que cero |
| `VAL-003` | El importe no tiene más decimales que su moneda |

---

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-MV-224` | Con saldo suficiente, se registra un **retiro pendiente** a nombre de quien lo pide, con comprobante de retiro; **la billetera baja y lo retenido sube en el importe**, y la respuesta trae los dos saldos |
| `CA-MV-225` | La retención son **dos asientos que suman cero**, y cada uno lleva el saldo de su cuenta después de escribirse |
| `CA-MV-226` | Con saldo **insuficiente** responde conflicto con el disponible, y **no queda ni retiro ni asiento** |
| `CA-MV-227` | Sin billetera en esa moneda responde lo mismo que sin saldo |
| `CA-MV-228` | **Dos peticiones simultáneas** que juntas superan el saldo producen **un** retiro y un conflicto; la billetera nunca queda en negativo |
| `CA-MV-229` | Importe cero, negativo o con decimales de más responde rechazo; una moneda inexistente, también |
| `CA-MV-230` | Una cuenta en espera de su primer depósito no puede pedir retiros |
| `CA-MV-231` | Sin `movements:request-withdrawal` responde prohibido; sin autenticar, `401` |
| `CA-MV-232` | El retiro aparece en el libro de administración (`RF-MV-006`) filtrando por su tipo, y **no** en «mis compras» (`RF-MV-008`) |
| `CA-MV-233` | **Confirmar, anular, rechazar el pago o volver a pagar** sobre el identificador de un retiro responden **no encontrado**, y el retiro no cambia |
| `CA-MV-234` | La billetera **no puede quedar en negativo por ninguna vía**: escribirla por debajo de cero directamente contra la base lo rechaza el esquema |
| `CA-MV-235` | El retiro y la retención quedan **auditados** |

**`CA-MV-228` y `CA-MV-234` son los que sostienen el requerimiento**: el primero prueba que retener funciona con carreras, y el segundo que la defensa no depende de que el código se acuerde.

---

## 13. Casos límite

| Caso | Comportamiento |
|---|---|
| Retirar **todo** el saldo | Se admite: la billetera queda en cero, que no es negativo |
| La misma petición enviada dos veces | Dos retiros, si el saldo alcanza para los dos (§2.1); la empresa niega el que sobra y el dinero vuelve |
| Un importe con los decimales justos de la moneda | Se admite |
| La moneda está **desactivada** | Se retira igual: el saldo existe y es de la persona; desactivar una moneda impide usarla en ventas nuevas, no quedarse con lo ya ganado |

---

## 14. Preguntas abiertas

**Mínimo por retiro y comisión por retirar.** No se han decidido. Si llegan, serán reglas de este requerimiento.

**A dónde se paga.** Llegará con la pasarela de salida, que es quien necesita los datos bancarios.

---

## 15. Control de cambios

| Versión | Fecha | Cambio | Autor |
|---|---|---|---|
| 0.1.0 | 26-09-2026 | Primera versión, con la etapa 6 de `MV` ([`requirements/mv.md`](../../../requirements/mv.md) v0.44.0 y v0.45.0). Lo que la spec carga: **pedir retiene en el acto**, y por eso el saldo de la billetera es el disponible; **sin clave de idempotencia**, porque un retiro repetido retiene dos veces y no puede pagar de más; **sin mínimo ni máximo**, hasta que se decidan. Trae las cuentas y los asientos al sistema (`plan.md`). Criterios `CA-MV-224` a `CA-MV-235`. | Responsable del proyecto |
