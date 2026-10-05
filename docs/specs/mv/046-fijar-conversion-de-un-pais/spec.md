# SPEC — `RF-MV-046` Fijar la conversión de un país

| Campo | Valor |
|---|---|
| Requerimiento | `RF-MV-046` |
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

Que administración pueda decir **a cuánto se convierte un dólar a la moneda de cada país**, con **un precio para cobrar y otro para pagar retiros** («1 USD = 4.150 COP al cobrar, 3.950 COP al retirar»), y cambiarlo cuando quiera **sin perder a cuánto estaba antes**.

---

## 2. Contexto

**Es lo primero de la integración con la pasarela local** ([`requirements/mv.md`](../../../requirements/mv.md) v0.75.0 §4.9): PayRetailers cobra y paga en la moneda del país, y las ventas son en dólares. Lo decidió el responsable del proyecto el 05-10-2026: **una tabla propia por país, con los dos precios juntos y con historia hacia delante**, como la tasa de puntos (`RF-MV-025`), cuya argumentación se hereda.

**Fijar no es editar.** Cada vez que se fija queda escrita **una conversión nueva**, que rige desde ese instante; la anterior no se toca (`RN-MV-062`). Es lo que permitirá explicar un cobro o un retiro de hace meses: dirán qué conversión usaron, y esa conversión seguirá ahí.

### 2.1 Lo que este requerimiento decide

| Decisión | Qué se decidió |
|---|---|
| **Los dos precios se fijan juntos** | Cambiar uno es volver a enviar los dos: la fila nueva los lleva siempre a la par. Lo pidió así el responsable |
| **La moneda base se toma del sistema** | Es la moneda por omisión en el momento de fijar —hoy USD—, y la conversión la **copia**. Quien fija no la elige |
| **La moneda local se indica** | Un país podría pagar en más de una moneda, y el sistema no guarda la moneda de cada país: se declara al fijar |
| **Rige desde que se fija** | No se programan conversiones futuras, como en `RF-MV-025` |
| **Fijar la misma que rige no escribe nada** | Si la moneda local y los dos precios son los vigentes, se devuelve la vigente |
| **No se exige que el precio de retiro sea menor que el de cobro** | Es política comercial de quien la fija |
| **No se borra ni se retira una conversión** | Para que un país deje de operar no hay operación todavía: §14 |

---

## 3. Actores

| Actor | Rol en esta funcionalidad |
|---|---|
| Quien tiene `movements:set-conversion-rate` | Fija la conversión de cualquier país. Lo portan `SUPERADMIN` y `ADMIN` |

---

## 4. Alcance

### 4.1 Incluye

- Fijar la conversión de un país —moneda local, precio de cobro y precio de retiro—, con efecto inmediato.
- Conservar las anteriores.
- Dejar escrito quién la fijó.

### 4.2 No incluye

- **Consultar las conversiones**: es `RF-MV-047`.
- **Usarlas**: el cobro y el retiro por la pasarela local, que se escriben después.
- **Consultar el histórico**, **programar una conversión futura** o **retirarla**.

---

## 5. Reglas de negocio aplicables

| Regla | Cómo aplica |
|---|---|
| `RN-MV-062` | Una vigente por país, con dos precios mayores que cero; fijar inserta una nueva y la anterior no se toca; país activo, moneda local activa y distinta de la base |

**Ninguna regla nueva.**

---

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción |
|---|---|---|
| País | Sí | El país cuya conversión se fija |
| Moneda local | Sí | La moneda a la que se convierte, la de los métodos de ese país |
| Precio de cobro | Sí | Cuántas unidades de la moneda local vale una de la base **al cobrar**. Mayor que cero, con hasta cuatro decimales |
| Precio de retiro | Sí | Lo mismo **al pagar un retiro** |

### 6.2 Salida

**La conversión que rige ahora**: el país, la moneda local, la moneda base, los dos precios y desde cuándo.

---

## 7. Precondiciones y postcondiciones

| Tipo | Condición |
|---|---|
| Precondición | El actor tiene `movements:set-conversion-rate`; el país existe y está activo; la moneda local existe, está activa y no es la base; los precios son válidos |
| Postcondición | La conversión nueva rige para ese país desde ese instante; las anteriores siguen escritas; queda auditado quién la fijó y qué regía antes |

---

## 8. Flujo principal

1. El actor indica el país, la moneda local y los dos precios.
2. El sistema valida los datos, **antes de tocar nada**.
3. Si la conversión vigente de ese país ya es esa —misma moneda local y mismos dos precios—, sigue por `FA-001`.
4. Escribe la conversión nueva, con la moneda base de ese momento, que rige desde ahora.
5. Audita, con lo que regía antes, y devuelve la nueva.

---

## 9. Flujos alternativos

### FA-001 — La conversión ya es esa

Se devuelve la vigente, **sin escribir nada y sin auditar**: no ha cambiado nada.

---

## 10. Excepciones

| ID | Situación | Resultado |
|---|---|---|
| `EX-001` | Algún dato falta o es inválido: precio no positivo, con más de cuatro decimales o demasiado grande | Rechazo, antes de tocar nada |
| `EX-002` | El país o la moneda local no existen | Rechazo |
| `EX-003` | El país está inactivo, o la moneda local está inactiva | Conflicto |
| `EX-004` | La moneda local es la moneda base | Conflicto |
| `EX-005` | Quien pide no tiene `movements:set-conversion-rate` | Prohibido |

---

## 11. Validaciones

| ID | Validación |
|---|---|
| `VAL-001` | País presente y con forma de identificador |
| `VAL-002` | Moneda local presente y con forma de identificador |
| `VAL-003` | Precio de cobro presente, mayor que cero, con hasta cuatro decimales y hasta diez cifras enteras |
| `VAL-004` | Precio de retiro, con las mismas condiciones |

---

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-MV-548` | Fijar la conversión de un país **sin conversión** la deja vigente desde ese instante, con la **moneda base del sistema** copiada, y la respuesta la devuelve con los dos precios |
| `CA-MV-549` | Fijar otra en el mismo país la **sustituye**: rige la nueva y **la anterior sigue escrita, sin cambios** |
| `CA-MV-550` | Fijar **la misma que rige** —moneda y dos precios— responde con la vigente y **no escribe nada**, ni conversión ni auditoría; cambiar **solo uno** de los precios sí escribe una nueva |
| `CA-MV-551` | Un precio cero, negativo, con cinco decimales o ausente, en cualquiera de los dos: rechazo con los errores **todos juntos**, y **nada cambia** |
| `CA-MV-552` | País o moneda inexistentes: rechazo; país o moneda **inactivos**: conflicto. En todos los casos nada cambia |
| `CA-MV-553` | La moneda local igual a la base: conflicto, y nada cambia |
| `CA-MV-554` | Sin `movements:set-conversion-rate` responde prohibido; sin autenticar, `401` |
| `CA-MV-555` | Queda **auditado**, con quién la fijó y lo que regía antes |
| `CA-MV-556` | Las conversiones de **otros países** no cambian |
| `CA-MV-633` | Con `shopId` y `secretKey`, la conversión guarda **la tienda y su clave cifrada**; la respuesta y la consulta dan `shopId` y `shopSecretKeySet`, **nunca la clave**, y la auditoría tampoco la lleva |
| `CA-MV-634` | Sin `shopId` ni `secretKey` se **heredan** los de la vigente; la misma tienda con la misma clave y los mismos precios no escribe; cambiar solo la clave sí |
| `CA-MV-635` | Una **tienda nueva sin su clave**, o una **clave sin tienda**, es `422` `EX-005`; una tienda vacía es `400` `VAL-005`; sin llave de cifrado en el entorno, guardar una clave es `503` `EX-006`. En ningún caso se escribe |

---

## 13. Casos límite

| Caso | Comportamiento |
|---|---|
| Dos personas fijan la conversión del mismo país a la vez | Quedan las dos, y rige la que se escribió después |
| El precio de retiro es mayor que el de cobro | Se admite (§2.1) |
| Se cambia la moneda por omisión del sistema después de fijar | Las conversiones ya fijadas conservan la base que copiaron; las nuevas toman la nueva |

---

## 14. Preguntas abiertas

**Dejar de operar en un país.** Hoy no hay forma de retirar una conversión; se puede desactivar el país.

**Consultar el histórico.** Se guarda y no se publica, como el de la tasa de puntos.

---

## 15. Control de cambios

| Versión | Fecha | Cambio | Autor |
|---|---|---|---|
| 0.1.0 | 05-10-2026 | Primera versión, con la conversión por país ([`requirements/mv.md`](../../../requirements/mv.md) v0.75.0 §4.9). **Los dos precios juntos, 1 USD = X moneda local, la base copiada del sistema**; fijar inserta una conversión nueva y conserva las anteriores; fijar la vigente no escribe nada. Criterios `CA-MV-548` a `CA-MV-556`. | Responsable del proyecto |
| 0.2.0 | 05-10-2026 | **La tienda de la pasarela local se fija con la conversión** ([`requirements/mv.md`](../../../requirements/mv.md) v0.82.0, `RN-MV-063`, `V71`): `shopId` y `secretKey` opcionales, heredados de la vigente si no se mandan; la clave se cifra y no sale nunca. Criterios `CA-MV-633` a `CA-MV-635`. | Responsable del proyecto |
