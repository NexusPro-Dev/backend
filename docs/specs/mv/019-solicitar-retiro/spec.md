# SPEC — `RF-MV-019` Solicitar un retiro

| Campo | Valor |
|---|---|
| Requerimiento | `RF-MV-019` |
| Módulo | `MV` — Movimientos |
| Versión | 0.2.0 |
| Estado | **Aprobada** |
| Autor | Responsable técnico |
| Aprobada por | Responsable del proyecto |
| Fecha de aprobación | 26-09-2026 |
| Enmendada el | 01-10-2026 — **el retiro exige una cuenta de cobro y copia su destino** (`RN-MV-056`): §4, §5, §6, §7, §8, §10, §11, §12, §14. Ver §15 |

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
- ~~**A dónde se paga**: los datos bancarios no se guardan todavía (`requirements/mv.md` v0.45.0 §4.3); la persona los acuerda con la empresa por fuera, hasta que llegue la pasarela de salida.~~ **Superado el 01-10-2026**: el retiro dice a dónde se paga (§4.3).
- **Registrar, editar o dar de baja las cuentas de cobro**: son `RF-MV-035` a `RF-MV-038`.
- **Que la persona cancele su propio retiro pendiente.** No se ha pedido; hoy se lo pide a la empresa, que lo niega.
- **Retirar de los puntos o en otra moneda.**

### 4.3 A dónde se paga — enmienda del 01-10-2026

Lo pidió el responsable del proyecto: «crearemos cuentas bancarias para los usuarios para solicitar retiros de disponible y saber a dónde enviar» ([`requirements/mv.md`](../../../requirements/mv.md) v0.61.0 §4.5). **Desde ese día un retiro sin cuenta de cobro no se puede pedir.** Se indica cuál, o si no se indica, **va a la principal**; quien no tiene ninguna registra una antes (`RF-MV-035`).

**El retiro copia el destino al pedirse**: entidad, tipo de cuenta, número, nombre del titular y su documento, tal como eran en ese instante. Es lo que lee quien lo aprueba y lo que dice el comprobante para siempre: editar la cuenta, darla de baja, renombrar la entidad o corregir el documento de la persona **no cambia a dónde se pagó** un retiro ya pedido (`RN-MV-056`, `RN-MV-001`).

**La cuenta tiene que servir**: ser de quien pide, estar viva y ser de una entidad activa (`RN-MV-054`). **Y quien pide tiene que tener documento**, porque sin él no hay titular que copiar. **Todas estas comprobaciones van antes de apartar el saldo**: un retiro rechazado por su destino no retiene nada.

**Los retiros pedidos antes del 01-10-2026 no tienen destino**, y no se les inventa uno: se aprueban y se niegan como siempre.

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
| **`RN-MV-056`** | **Desde el 01-10-2026.** El retiro exige una cuenta de cobro —la indicada o la principal— y copia su destino al pedirse; la copia no cambia nunca |
| **`RN-MV-055`** | **Desde el 01-10-2026.** La cuenta es del que pide y está viva; el titular es él, con su documento |
| **`RN-MV-054`** | **Desde el 01-10-2026.** La entidad de la cuenta está activa |

**Ninguna regla nueva.**

---

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción |
|---|---|---|
| Moneda | Sí | De qué billetera se retira |
| Importe | Sí | Cuánto. Mayor que cero, con los decimales de la moneda |
| Cuenta de cobro | No | **Desde el 01-10-2026.** A dónde se paga. Si no viene, la principal de quien pide |

**Quién retira no es un dato de entrada**: sale de la credencial.

### 6.2 Salida

**El retiro** —comprobante, estado, importe, moneda, cuándo se pidió **y, desde el 01-10-2026, su destino**: entidad, tipo de cuenta, número y titular, tal como quedaron copiados— y **los tres saldos de esa moneda** como quedan: la billetera ya descontada y lo retenido con el importe sumado. Devolver los saldos ahorra a quien pide una segunda consulta para ver que el dinero se apartó.

---

## 7. Precondiciones y postcondiciones

| Tipo | Condición |
|---|---|
| Precondición | El actor tiene `movements:request-withdrawal` y su cuenta opera; la moneda existe; el importe es válido; **tiene documento y una cuenta de cobro que sirve —la indicada o la principal—**; la billetera de esa moneda alcanza |
| Postcondición | Existe un retiro pendiente a su nombre, **con la copia de su destino**; su billetera bajó en el importe y lo retenido subió en lo mismo; los dos asientos suman cero; todo está auditado |

---

## 8. Flujo principal

1. La persona indica moneda e importe.
2. El sistema valida el importe y la moneda, **antes de tocar ningún saldo**.
2b. **Desde el 01-10-2026**: resuelve la cuenta de cobro —la indicada o la principal— y comprueba que es suya, está viva, su entidad está activa y quien pide tiene documento, **antes de tocar ningún saldo**.
3. Registra el retiro pendiente, con su comprobante **y la copia de su destino**.
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
| `EX-006` | **No indica cuenta y no tiene ninguna** | Conflicto, diciendo que registre una. Nada cambia |
| `EX-007` | La cuenta indicada no existe, no es suya o está dada de baja | Rechazo. Nada cambia |
| `EX-008` | La entidad de la cuenta está inactiva | Conflicto, diciendo que elija otra. Nada cambia |
| `EX-009` | Quien pide no tiene documento | Conflicto, diciendo que lo complete administración. Nada cambia |

---

## 11. Validaciones

| ID | Validación |
|---|---|
| `VAL-001` | La moneda está presente y es un identificador válido |
| `VAL-002` | El importe está presente y es mayor que cero |
| `VAL-003` | El importe no tiene más decimales que su moneda |
| `VAL-004` | La cuenta de cobro, si viene, tiene forma de identificador |

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
| `CA-MV-415` | **Desde el 01-10-2026.** Con una cuenta indicada, el retiro guarda **la copia de su destino** —código, nombre y tipo de la entidad, tipo de cuenta, número, nombre del titular y su documento— y la respuesta la trae |
| `CA-MV-416` | Sin indicar cuenta, el retiro va a **la principal** |
| `CA-MV-417` | Quien **no tiene ninguna cuenta** recibe conflicto, y **no queda ni retiro, ni copia, ni asiento**: la billetera no cambia |
| `CA-MV-418` | Una cuenta **ajena**, **dada de baja** o inexistente responde rechazo, y nada cambia |
| `CA-MV-419` | Una cuenta de una entidad **inactiva** responde conflicto, y nada cambia; con la entidad reactivada, el mismo retiro se pide |
| `CA-MV-420` | Quien **no tiene documento** recibe conflicto, y nada cambia |
| `CA-MV-421` | **La copia no cambia** al editar la cuenta, darla de baja, renombrar la entidad o corregir el documento de la persona |
| `CA-MV-422` | Un retiro **sin destino** —pedido antes del 01-10-2026— se aprueba y se niega como siempre |
| `CA-MV-423` | La auditoría del retiro lleva el destino **con el número enmascarado** |

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

~~**A dónde se paga.** Llegará con la pasarela de salida, que es quien necesita los datos bancarios.~~ **Cerrada el 01-10-2026** (§4.3).

**La moneda de la cuenta.** La cuenta no lleva moneda, y quien paga convierte por fuera (`requirements/mv.md` §4.5).

---

## 15. Control de cambios

| Versión | Fecha | Cambio | Autor |
|---|---|---|---|
| 0.1.0 | 26-09-2026 | Primera versión, con la etapa 6 de `MV` ([`requirements/mv.md`](../../../requirements/mv.md) v0.44.0 y v0.45.0). Lo que la spec carga: **pedir retiene en el acto**, y por eso el saldo de la billetera es el disponible; **sin clave de idempotencia**, porque un retiro repetido retiene dos veces y no puede pagar de más; **sin mínimo ni máximo**, hasta que se decidan. Trae las cuentas y los asientos al sistema (`plan.md`). Criterios `CA-MV-224` a `CA-MV-235`. | Responsable del proyecto |
| 0.2.0 | 01-10-2026 | **El retiro exige una cuenta de cobro y copia su destino** ([`requirements/mv.md`](../../../requirements/mv.md) v0.61.0 §4.5, `RN-MV-054` a `RN-MV-056`), por decisión del responsable del proyecto. La cuenta se indica o se usa la principal; sin ninguna, sin documento, con una ajena o dada de baja, o con una entidad inactiva, **no se pide y no se retiene nada**. La copia no cambia nunca. **Cambio rompedor del contrato**: un retiro sin cuenta deja de admitirse. Criterios `CA-MV-415` a `CA-MV-423`. | Responsable del proyecto |
