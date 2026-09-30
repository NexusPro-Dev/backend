# SPEC — `RF-MV-027` Comprar puntos

| Campo | Valor |
|---|---|
| Requerimiento | `RF-MV-027` |
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

Que una persona pueda **pagar dinero para tener puntos**, con los que después comprará en la tienda, sabiendo desde el principio **cuántos puntos recibirá**.

---

## 2. Contexto

**Es la entrada de la etapa 3** ([`requirements/mv.md`](../../../requirements/mv.md) v0.54.0 §4.4). La compra es **un movimiento propio** y no la venta de un producto, por decisión del responsable del proyecto: así no comisiona, y la comisión se devenga una sola vez, cuando los puntos se gastan (`RF-MV-030`).

**Comprar no abona nada todavía.** Mientras no haya pasarela, que el dinero entró lo declara administración (`RF-MV-028`); hasta entonces la compra queda **pendiente**, con su pago pendiente, y **los puntos no existen** (`RN-MV-004`, `RN-MV-051`). Lo que sí queda fijado desde el primer momento es **cuántos puntos da**: la tasa se congela al comprar, y confirmar abona exactamente eso aunque la tasa cambie después.

**La clave de idempotencia es obligatoria**, como al volver a pagar una venta (`RF-MV-018`) y por lo mismo: abrir un pago dos veces por un doble clic es cobrar dos veces.

### 2.1 Lo que este requerimiento decide

| Decisión | Qué se decidió |
|---|---|
| **Se compra por importe, no por puntos** | La persona dice cuánto paga, en qué moneda y con qué; los puntos salen de la tasa. Es lo que se cobra, y el cobro es lo que tiene que ser exacto |
| **Redondeo hacia abajo** | `importe × tasa`, a dos decimales, hacia abajo (`RN-MV-051`) |
| **Los puntos no se compran con puntos** | Pagar con `POINTS` se rechaza. Tampoco con un método interno, que no se ofrece a nadie (`RN-MV-023`) |
| **Una cuenta que todavía no opera no compra** | Una cuenta en espera de su primer depósito autentica y no opera (`RN-SP-026`), como con las ventas (`RN-MV-008`) y los retiros (`RF-MV-019`) |
| **Sin mínimo ni máximo** | No se ha decidido ninguno |

---

## 3. Actores

| Actor | Rol en esta funcionalidad |
|---|---|
| Quien tiene `movements:buy-points` | Compra puntos **para sí mismo**. Lo porta todo rol por su tipo |

---

## 4. Alcance

### 4.1 Incluye

- Registrar la compra, pendiente, a nombre de quien compra, con su comprobante.
- Congelar la tasa vigente y los puntos que da.
- Abrir su pago pendiente, con el método elegido.
- Reconocer la misma petición repetida.

### 4.2 No incluye

- **Abonar los puntos**: es `RF-MV-028`.
- **Comprar puntos para otra persona.**
- **Cancelar una compra pendiente** quien la pidió (`requirements/mv.md` §4.4).
- **Volver a pagar** una compra cuyo pago se rechazó: rechazar es final (`RF-MV-029`).

---

## 5. Reglas de negocio aplicables

| Regla | Cómo aplica |
|---|---|
| `RN-MV-051` | Nace pendiente, con pago pendiente, tasa y puntos congelados, sin asientos |
| `RN-MV-050` | Sin tasa vigente en la moneda no se compra |
| `RN-MV-040` | La clave evita un segundo pago con la misma petición |
| `RN-MV-046` | Sin líneas, sin vendedor, sin paquete |
| `RN-MV-026` | El sujeto es quien compra |
| `RN-MV-014` | El importe respeta los decimales de su moneda |
| `RN-MV-016` | Lleva comprobante legible, con el prefijo de su tipo |
| `RN-MV-018`, `RN-MV-023` | El método tiene que estar activo, y lo interno no se ofrece |

**Ninguna regla nueva.**

---

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción |
|---|---|---|
| Moneda | Sí | En qué se paga, y de qué moneda serán los puntos |
| Importe | Sí | Cuánto se paga. Mayor que cero, con los decimales de la moneda |
| Método de pago | Sí | Con qué se paga |
| Clave de idempotencia | Sí | Una por compra; se repite tal cual al reenviar |

### 6.2 Salida

**La compra**: comprobante, estado, moneda, importe, **la tasa y los puntos que dará**, cuándo, y su pago con el método y el estado.

---

## 7. Precondiciones y postcondiciones

| Tipo | Condición |
|---|---|
| Precondición | El actor tiene `movements:buy-points` y su cuenta opera; la moneda está activa y tiene tasa vigente; el método existe, está activo, se ofrece y no es `POINTS`; los datos son válidos |
| Postcondición | Existe una compra pendiente a nombre del actor, con la tasa y los puntos congelados y un pago pendiente; **ningún saldo cambió**; está auditada |

---

## 8. Flujo principal

1. El actor indica moneda, importe, método y clave.
2. El sistema valida todo, **antes de tocar nada**.
3. Si la clave ya existe, responde según `FA-001` o `EX-008`, y termina.
4. Toma la tasa vigente de la moneda y calcula los puntos.
5. Registra la compra pendiente y su pago pendiente, en un solo acto.
6. Audita y devuelve la compra.

---

## 9. Flujos alternativos

### FA-001 — La misma petición llega dos veces

Misma clave, misma moneda, importe y método: **se devuelve la compra ya registrada** y no se crea nada más.

---

## 10. Excepciones

| ID | Situación | Resultado |
|---|---|---|
| `EX-001` | Algún dato falta o es inválido —importe no positivo o con decimales de más, clave ausente o malformada— | Rechazo, antes de tocar nada |
| `EX-002` | La moneda no existe | Rechazo |
| `EX-003` | La moneda está inactiva, o no tiene tasa vigente | Conflicto |
| `EX-004` | El método no existe | Rechazo |
| `EX-005` | El método está desactivado, es interno o es `POINTS` | Conflicto |
| `EX-006` | La cuenta del actor todavía no opera | Conflicto |
| `EX-007` | Quien pide no tiene `movements:buy-points` | Prohibido |
| `EX-008` | La clave ya se usó en **otra** petición | Conflicto. Nada cambia |

---

## 11. Validaciones

| ID | Validación |
|---|---|
| `VAL-001` | Moneda y método presentes y con forma de identificador |
| `VAL-002` | Importe mayor que cero y con los decimales de la moneda |
| `VAL-003` | Clave con la forma de `RF-MV-018` `VAL-002` |

---

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-MV-306` | Se registra una compra **pendiente** a nombre del actor, con comprobante de compra de puntos, la tasa vigente y los puntos que da, y **un pago pendiente** por el importe con el método elegido |
| `CA-MV-307` | Los puntos son `importe × tasa` **redondeados hacia abajo** a dos decimales: 10.00 a 0.3336 puntos por unidad (3.336) dan 3.33, y no 3.34 |
| `CA-MV-308` | **Ningún saldo cambia**: ni la cuenta de puntos del actor ni ninguna de la empresa, y no hay asientos |
| `CA-MV-309` | La compra **no tiene líneas** y **no produce ninguna comisión** |
| `CA-MV-310` | La **misma petición repetida** devuelve la misma compra, sin crear otra ni otro pago |
| `CA-MV-311` | La misma clave con **otros datos** responde conflicto y no crea nada |
| `CA-MV-312` | Pagar con `POINTS`, con un método desactivado o con uno interno: conflicto; con uno inexistente: rechazo. Nada se crea |
| `CA-MV-313` | Moneda inexistente: rechazo; moneda inactiva o **sin tasa vigente**: conflicto. Nada se crea |
| `CA-MV-314` | Importe cero, negativo, con decimales de más, o sin clave: rechazo, y **nada cambia** |
| `CA-MV-315` | Una cuenta **a la espera de su primer depósito** no compra: conflicto |
| `CA-MV-316` | Sin `movements:buy-points` responde prohibido; sin autenticar, `401` |
| `CA-MV-317` | Queda **auditada**; si la tasa cambia después, la compra **conserva la suya** y sus puntos |

---

## 13. Casos límite

| Caso | Comportamiento |
|---|---|
| El importe es tan pequeño que da cero puntos | Rechazo: una compra de cero puntos no compra nada. Cae en `EX-001` |
| Dos compras simultáneas con claves distintas | Se registran las dos |
| La tasa cambia entre la validación y el registro | Se usa la que rige al registrar, y es la que se guarda |

---

## 14. Preguntas abiertas

**Mínimos y máximos por compra.** No se han pedido.

---

## 15. Control de cambios

| Versión | Fecha | Cambio | Autor |
|---|---|---|---|
| 0.1.0 | 30-09-2026 | Primera versión, con la etapa 3 de `MV` ([`requirements/mv.md`](../../../requirements/mv.md) v0.54.0 §4.4). **Nace pendiente, sin abonar nada**, con la tasa y los puntos congelados y su pago pendiente; clave de idempotencia obligatoria; no se compra con `POINTS`. Criterios `CA-MV-306` a `CA-MV-317`. | Responsable del proyecto |
