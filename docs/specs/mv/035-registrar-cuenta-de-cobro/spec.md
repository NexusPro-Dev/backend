# SPEC — `RF-MV-035` Registrar una cuenta de cobro propia

| Campo | Valor |
|---|---|
| Requerimiento | `RF-MV-035` |
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

Que una persona deje escrito **a dónde quiere que le paguen**: una cuenta en un banco o una billetera móvil, a su nombre, para elegirla después al pedir un retiro.

---

## 2. Contexto

Lo pidió el responsable del proyecto el 01-10-2026 ([`requirements/mv.md`](../../../requirements/mv.md) v0.61.0 §4.5): «crearemos cuentas bancarias para los usuarios para solicitar retiros de disponible y saber a dónde enviar». **Desde ese día un retiro sin cuenta no se puede pedir** (`RN-MV-056`), de modo que este requerimiento es el paso previo de toda persona que quiera cobrar.

**El titular no se escribe** (`RN-MV-055`): es quien registra la cuenta, con el nombre y el documento de su usuario. Es la defensa contra quien robe una sesión: no puede añadir una cuenta a nombre de otro, y una cuenta ajena se nota.

### 2.1 Lo que este requerimiento decide

| Decisión | Qué se decidió |
|---|---|
| **Sin documento no hay cuenta** | Sin documento no hay titular. La persona no puede completarlo sola —es identidad, `RN-SP-037`—, y el error le dice que lo pida a administración |
| **Solo entidades activas de su país** | `RN-MV-054`. Elegir una de otro país se rechaza aunque exista |
| **Lo que se pide depende del tipo de entidad** | En un banco, tipo de cuenta —ahorros o corriente— y número de 4 a 20 dígitos; en una billetera móvil, el celular, de 7 a 15 dígitos, y **ningún** tipo de cuenta |
| **El número admite espacios y guiones al escribirlo** | Se guardan solo los dígitos. Así lo copia la gente del extracto, y guardarlo limpio es lo que permite detectar el duplicado |
| **No se repite** | La misma entidad y el mismo número, para la misma persona, una sola vez entre las cuentas vivas |
| **La primera es la principal sin pedirlo** | Para que pedir un retiro funcione desde la primera cuenta. Las siguientes no lo son, salvo que se pida, y entonces **la anterior deja de serlo en el mismo acto** |
| **Sin verificación** | Decisión del responsable: se usa desde que se registra |
| **Una cuenta que todavía no opera puede registrarla** | Registrar una cuenta no mueve dinero; el retiro sí lo comprueba (`RF-MV-019`) |
| **Sin tope** | No se ha pedido ninguno (`requirements/mv.md` §4.5) |

---

## 3. Actores

| Actor | Rol en esta funcionalidad |
|---|---|
| Quien tiene `movements:create-own-payout-account` | Registra una cuenta **a su nombre** |

---

## 4. Alcance

### 4.1 Incluye

- Registrar la cuenta, a nombre de quien la registra.
- Hacerla la principal, si se pide o si es la primera.

### 4.2 No incluye

- **Registrar una cuenta a nombre de otra persona**, ni siquiera desde administración.
- **Verificarla** con el banco.
- **Consultar, editar o dar de baja**: son `RF-MV-036` a `RF-MV-038`.

---

## 5. Reglas de negocio aplicables

| Regla | Cómo aplica |
|---|---|
| `RN-MV-055` | El titular es el usuario; sin documento no hay cuenta; la forma depende del tipo de entidad; no se repite; una sola principal |
| `RN-MV-054` | Solo entidades activas del país de la persona |

**Ninguna regla nueva.**

---

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción |
|---|---|---|
| Entidad | Sí | El banco o la billetera, del catálogo |
| Tipo de cuenta | En un banco, sí; en una billetera, **no debe venir** | `AHORROS` o `CORRIENTE` |
| Número | Sí | Número de cuenta o de celular. Admite espacios y guiones, que se quitan |
| Principal | No | Si se quiere que sea la principal. Por omisión no, salvo que sea la primera |

**El titular no es un dato de entrada**: sale de la credencial.

### 6.2 Salida

**La cuenta registrada**: identificador, entidad (identificador, código, nombre, tipo y si está activa), tipo de cuenta, número, si es la principal, **el titular** —nombre y documento, tal como están en el usuario— y cuándo se registró.

---

## 7. Precondiciones y postcondiciones

| Tipo | Condición |
|---|---|
| Precondición | El actor tiene el permiso; tiene documento; la entidad existe, está activa y es de su país; los datos son válidos para su tipo; no tiene ya esa cuenta |
| Postcondición | La cuenta existe, viva, a su nombre; si es la principal, ninguna otra suya lo es; queda auditado |

---

## 8. Flujo principal

1. La persona indica la entidad, el número y, si es un banco, el tipo de cuenta.
2. El sistema valida la forma de los datos **antes de tocar nada**, y los informa todos juntos.
3. Lee al titular —nombre, país y documento— y la entidad.
4. Comprueba que tiene documento, que la entidad está activa y es de su país, y que lo que trae corresponde a su tipo.
5. Si es su primera cuenta viva, o pidió que sea la principal, la marca como principal y **desmarca la anterior**.
6. Registra la cuenta.
7. Audita y la devuelve.

**Los pasos 5 y 6 son un solo acto**: nunca hay dos principales, ni ninguna cuando hay cuentas.

---

## 9. Flujos alternativos

### FA-001 — Pide que sea la principal y ya tiene otra

La anterior deja de ser la principal y esta lo es, en el mismo acto.

### FA-002 — Dos registros a la vez de la misma persona

Se hacen uno detrás de otro. Si los dos eran «la primera», solo el primero queda como principal. Si los dos eran la misma cuenta, el segundo es un duplicado.

---

## 10. Excepciones

| ID | Situación | Resultado |
|---|---|---|
| `EX-001` | Algún dato falta o tiene forma inválida | Rechazo, todos juntos, sin tocar nada |
| `EX-002` | La entidad no existe | Rechazo |
| `EX-003` | La entidad está inactiva, o es de otro país | Conflicto |
| `EX-004` | El tipo de cuenta no corresponde a la entidad: falta en un banco, sobra en una billetera; o el número no tiene la longitud de su tipo | Rechazo |
| `EX-005` | **La persona no tiene documento** | Conflicto, diciendo que lo complete administración |
| `EX-006` | Ya tiene esa cuenta viva —misma entidad y número— | Conflicto. Nada cambia |
| `EX-007` | Quien pide no tiene `movements:create-own-payout-account` | Prohibido |

---

## 11. Validaciones

| ID | Validación |
|---|---|
| `VAL-001` | Entidad presente y con forma de identificador |
| `VAL-002` | Número presente; quitados espacios y guiones, solo dígitos, de 4 a 20 |
| `VAL-003` | Tipo de cuenta, si viene, uno de los dos |
| `VAL-004` | Con la entidad ya leída: en un banco, tipo de cuenta presente y número de 4 a 20 dígitos; en una billetera, sin tipo de cuenta y número de 7 a 15 dígitos |

---

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-MV-378` | Con datos válidos en un **banco** se registra la cuenta a nombre de quien la pide, y la respuesta trae **el titular con su nombre y su documento** |
| `CA-MV-379` | En una **billetera móvil** se registra con el celular y **sin** tipo de cuenta; enviarle un tipo de cuenta responde rechazo |
| `CA-MV-380` | Un banco **sin** tipo de cuenta, o un número con la longitud de otro tipo, responde rechazo, y nada cambia |
| `CA-MV-381` | Un número escrito con espacios y guiones se guarda y se devuelve **solo con dígitos** |
| `CA-MV-382` | La **primera** cuenta es la principal sin pedirlo; la segunda no lo es |
| `CA-MV-383` | Registrar otra pidiendo que sea la principal **desmarca la anterior**: queda exactamente una principal |
| `CA-MV-384` | La misma entidad y el mismo número otra vez responden conflicto —también escrito con otros espacios—, y **no se registra nada** |
| `CA-MV-385` | Una entidad **inactiva** o **de otro país** responde conflicto; una inexistente, rechazo. En los tres casos nada cambia |
| `CA-MV-386` | Una persona **sin documento** recibe conflicto, y no se registra nada |
| `CA-MV-387` | **Dos registros simultáneos** de la primera cuenta de una persona dejan **una sola principal** |
| `CA-MV-388` | Sin `movements:create-own-payout-account` responde prohibido; sin autenticar, `401` |
| `CA-MV-389` | Queda **auditada**, sin el número completo en los registros de aplicación |

---

## 13. Casos límite

| Caso | Comportamiento |
|---|---|
| La misma cuenta en dos personas | Se admite: el esquema no lo impide y no es un error de este requerimiento. El titular la delata: el banco solo la acepta a nombre de uno |
| Volver a registrar una cuenta dada de baja | Se admite: la dada de baja no cuenta como viva (`RF-MV-038`) |
| Una persona cambia de país después | Sus cuentas siguen; las nuevas serán de su país nuevo |

---

## 14. Preguntas abiertas

**La misma cuenta en dos personas.** Si administración quiere detectarlo, será una consulta propia.

---

## 15. Control de cambios

| Versión | Fecha | Cambio | Autor |
|---|---|---|---|
| 0.1.0 | 01-10-2026 | Primera versión, con las cuentas de cobro de `MV` ([`requirements/mv.md`](../../../requirements/mv.md) v0.61.0 §4.5). **El titular es quien registra**, y sin documento no hay cuenta; solo entidades activas de su país; la forma depende del tipo de entidad; **la primera es la principal**. Criterios `CA-MV-378` a `CA-MV-389`. | Responsable del proyecto |
