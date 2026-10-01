# SPEC — `RF-MV-037` Editar una cuenta de cobro propia

| Campo | Valor |
|---|---|
| Requerimiento | `RF-MV-037` |
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

Que una persona **corrija** el número o el tipo de una cuenta suya, y que **elija cuál es la principal**.

---

## 2. Contexto

Una cuenta mal escrita es un retiro que el banco devuelve. Corregirla tiene que ser fácil, y **es seguro aunque haya un retiro pendiente hacia ella**: el retiro copió el destino al pedirse, y la corrección no lo toca (`RN-MV-056`). Lo decidió el responsable del proyecto el 01-10-2026.

### 2.1 Lo que este requerimiento decide

| Decisión | Qué se decidió |
|---|---|
| **Se edita el tipo de cuenta, el número y la marca de principal** | **La entidad no**: una cuenta en otro banco es otra cuenta, y se registra (`RF-MV-035`) |
| **La principal se elige, no se quita** | Marcar una como principal desmarca la anterior. Pedir que la principal deje de serlo es un error: siempre hay una, y para cambiarla se marca otra |
| **Las mismas reglas que al registrar** | La forma por tipo de entidad, el número sin espacios ni guiones, y sin repetir otra cuenta viva propia |
| **Una cuenta de una entidad inactiva no se edita** | No sirve para retirar, ni corregida ni como principal. Lo que se hace con ella es darla de baja (`RF-MV-038`) y registrar otra |
| **Editar sin cambiar nada no escribe** | Ni la cuenta ni la auditoría |
| **Una cuenta ajena o dada de baja no existe** | Para quien pregunta, igual que una que nunca existió (`RN-MV-055`) |

---

## 3. Actores

| Actor | Rol en esta funcionalidad |
|---|---|
| Quien tiene `movements:update-own-payout-account` | Edita **las suyas** |

---

## 4. Alcance

### 4.1 Incluye

- Corregir el tipo de cuenta y el número.
- Hacerla la principal.

### 4.2 No incluye

- **Cambiar la entidad.**
- **Que administración edite la cuenta de otra persona.**
- **Cambiar el destino de un retiro ya pedido.**

---

## 5. Reglas de negocio aplicables

| Regla | Cómo aplica |
|---|---|
| `RN-MV-055` | Solo su dueño; forma por tipo; sin repetir; una sola principal |
| `RN-MV-054` | Una entidad inactiva no admite cambios hacia ella |
| `RN-MV-056` | La copia de un retiro pedido no cambia |

**Ninguna regla nueva.**

---

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción |
|---|---|---|
| Cuenta | Sí | Cuál |
| Tipo de cuenta | No | Solo en un banco |
| Número | No | Admite espacios y guiones, que se quitan |
| Principal | No | Solo «sí» |

**Al menos uno** tiene que venir.

### 6.2 Salida

**La cuenta como queda**, con la forma de `RF-MV-036`.

---

## 7. Precondiciones y postcondiciones

| Tipo | Condición |
|---|---|
| Precondición | El actor tiene el permiso; la cuenta es suya y está viva; su entidad está activa; los datos son válidos para su tipo |
| Postcondición | La cuenta tiene los datos pedidos; si se marcó principal, ninguna otra suya lo es; si algo cambió, queda auditado |

---

## 8. Flujo principal

1. La persona indica la cuenta y lo que cambia.
2. El sistema valida la forma **antes de tocar nada**.
3. Lee la cuenta, que tiene que ser suya y estar viva, y su entidad, que tiene que estar activa.
4. Comprueba que lo pedido corresponde al tipo de la entidad y que no repite otra cuenta suya.
5. Si nada cambia, sigue por `FA-001`.
6. Si se marca principal, desmarca la anterior; escribe los cambios.
7. Audita, con lo anterior y lo nuevo, y devuelve la cuenta.

---

## 9. Flujos alternativos

### FA-001 — Nada cambia

Se devuelve la cuenta, **sin escribir y sin auditar**. Marcar como principal la que ya lo es cae aquí.

---

## 10. Excepciones

| ID | Situación | Resultado |
|---|---|---|
| `EX-001` | Nada que cambiar, forma inválida, «principal: no», o una propiedad que no se edita —la entidad— | Rechazo, sin tocar nada |
| `EX-002` | La cuenta no existe, no es suya o está dada de baja | No encontrado |
| `EX-003` | Su entidad está inactiva | Conflicto |
| `EX-004` | Lo pedido no corresponde al tipo de la entidad | Rechazo |
| `EX-005` | El número nuevo repite otra cuenta viva suya en la misma entidad | Conflicto |
| `EX-006` | Quien pide no tiene `movements:update-own-payout-account` | Prohibido |

---

## 11. Validaciones

| ID | Validación |
|---|---|
| `VAL-001` | Viene al menos uno de los tres |
| `VAL-002` | Número, si viene, como en `RF-MV-035` · `VAL-002` |
| `VAL-003` | Tipo de cuenta, si viene, uno de los dos |
| `VAL-004` | Principal, si viene, es «sí» |
| `VAL-005` | Con la entidad leída, `RF-MV-035` · `VAL-004` sobre el resultado de aplicar los cambios |

---

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-MV-395` | Corregir el número de una cuenta de banco lo cambia, guardado solo con dígitos |
| `CA-MV-396` | Cambiar el tipo de cuenta en un banco lo cambia; **enviarlo a una billetera** responde rechazo |
| `CA-MV-397` | Marcar como principal una que no lo es **desmarca la anterior**: queda exactamente una |
| `CA-MV-398` | «Principal: no» responde rechazo, y la principal sigue siéndolo |
| `CA-MV-399` | Un número que repite otra cuenta viva suya en la misma entidad responde conflicto, y nada cambia |
| `CA-MV-400` | Una cuenta **ajena**, **dada de baja** o inexistente responde no encontrado, y nada cambia |
| `CA-MV-401` | Una cuenta de una entidad **inactiva** responde conflicto, y nada cambia |
| `CA-MV-402` | Corregir una cuenta **no cambia la copia** de un retiro pendiente pedido hacia ella |
| `CA-MV-403` | Enviar lo mismo que tiene no escribe nada, ni auditoría; un cambio queda auditado con el valor anterior y el número enmascarado; sin el permiso responde prohibido, y sin autenticar, `401` |

---

## 13. Casos límite

| Caso | Comportamiento |
|---|---|
| Dos ediciones a la vez marcando principales distintas | Se hacen una detrás de otra, y gana la última: queda una sola principal |
| Corregir el número a uno que tuvo una cuenta dada de baja | Se admite: la dada de baja no cuenta |

---

## 14. Preguntas abiertas

Ninguna.

---

## 15. Control de cambios

| Versión | Fecha | Cambio | Autor |
|---|---|---|---|
| 0.1.0 | 01-10-2026 | Primera versión, con las cuentas de cobro de `MV` ([`requirements/mv.md`](../../../requirements/mv.md) v0.61.0 §4.5). Se editan el tipo, el número y la principal, **no la entidad**; la principal se elige y no se quita; una cuenta de entidad inactiva no se edita; los retiros pedidos conservan su copia. Criterios `CA-MV-395` a `CA-MV-403`. | Responsable del proyecto |
