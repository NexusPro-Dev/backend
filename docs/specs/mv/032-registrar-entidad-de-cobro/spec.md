# SPEC — `RF-MV-032` Registrar una entidad de cobro

| Campo | Valor |
|---|---|
| Requerimiento | `RF-MV-032` |
| Módulo | `MV` — Movimientos |
| Versión | 0.3.0 |
| Estado | **Aprobada** |
| Autor | Responsable técnico |
| Aprobada por | Responsable del proyecto |
| Fecha de aprobación | 01-10-2026 |

!!! info "Qué va en este documento"

    **Qué debe pasar, y por qué.** Nada más.

    **Prueba de pertenencia:** si un cambio de tecnología lo invalidaría, no pertenece aquí — va a `plan.md`. No se nombran tablas, clases, endpoints ni librerías.

---

## 1. Objetivo

Que administración pueda dar de alta **los bancos y las billeteras móviles** a los que la empresa paga los retiros, para que cada persona elija el suyo de una lista en vez de escribirlo.

---

## 2. Contexto

**Es lo primero de las cuentas de cobro** ([`requirements/mv.md`](../../../requirements/mv.md) v0.61.0 §4.5): sin entidades nadie puede registrar una cuenta (`RF-MV-035`), y sin cuenta nadie puede pedir un retiro (`RN-MV-056`). **Desde el 08-10-2026 el catálogo nace con las entidades de Colombia** —veintidós bancos y cuatro billeteras móviles, con su código ACH—, por decisión del responsable del proyecto; las de los demás países las da de alta administración con este requerimiento.

**Por qué un catálogo y no texto libre.** Lo decidió el responsable del proyecto el 01-10-2026: escrito a mano, el mismo banco aparece de cinco formas, y nadie puede responder cuánto se paga a cada uno ni detectar una errata en el nombre de un banco.

### 2.1 Lo que este requerimiento decide

| Decisión | Qué se decidió |
|---|---|
| **El código identifica la entidad, y no cambia** | `BANCOLOMBIA`, `NEQUI`. En mayúsculas, dígitos y guion bajo, como los demás códigos del sistema. Es lo que viaja en la copia de cada retiro, y por eso es inmutable (`RN-MV-054`) |
| **Dos tipos, y nada más** | `BANCO` o `BILLETERA_MOVIL`. El tipo decide qué se le pide a una cuenta (`RN-MV-055`) y tampoco cambia |
| **Un país por entidad** | Un banco que opera en dos países son dos entidades, con dos códigos: sus cuentas no son intercambiables |
| **El código es único en todo el catálogo**, no por país | La copia del retiro guarda el código, y un código repetido en dos países no diría a cuál se pagó |
| **Nace activa** | Registrar una entidad es para usarla. Se desactiva con `RF-MV-034` |
| **Solo en países activos** | Un país retirado no tiene personas nuevas a las que pagar |

---

## 3. Actores

| Actor | Rol en esta funcionalidad |
|---|---|
| Quien tiene `movements:create-payout-institution` | Registra entidades de cualquier país |

---

## 4. Alcance

### 4.1 Incluye

- Registrar una entidad con su código, su nombre, su tipo y su país.
- Rechazar un código que ya exista.

### 4.2 No incluye

- **Consultarlas**: es `RF-MV-033`.
- **Cambiar el nombre o desactivarla**: es `RF-MV-034`.
- **Borrarla**: no existe (`RN-MV-054`).
- **Sembrar entidades de otros países**: solo nacen sembradas las de Colombia (`CA-MV-703`).

---

## 5. Reglas de negocio aplicables

| Regla | Cómo aplica |
|---|---|
| `RN-MV-054` | Código único e inmutable, tipo `BANCO` o `BILLETERA_MOVIL`, un país, nace activa |

**Ninguna regla nueva.**

---

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción |
|---|---|---|
| Código | Sí | De 2 a 30 caracteres: empieza por letra **o por dígito** y sigue con mayúsculas, dígitos o guion bajo. Se admite en minúsculas y se guarda en mayúsculas |
| Nombre | Sí | El que verá la persona. De 1 a 100 caracteres, sin espacios a los lados |
| Tipo | Sí | `BANCO` o `BILLETERA_MOVIL` |
| País | Sí | El país donde opera |

### 6.2 Salida

**La entidad registrada**: identificador, código, nombre, tipo, país (identificador, código y nombre), si está activa y cuándo se registró.

---

## 7. Precondiciones y postcondiciones

| Tipo | Condición |
|---|---|
| Precondición | El actor tiene `movements:create-payout-institution`; los datos son válidos; el país existe y está activo; el código no existe |
| Postcondición | La entidad existe, activa, y se ofrece a las personas de su país; queda auditada |

---

## 8. Flujo principal

1. El actor indica código, nombre, tipo y país.
2. El sistema valida los cuatro datos **antes de tocar nada**, y los informa todos juntos.
3. Comprueba que el país existe y está activo.
4. Registra la entidad, activa.
5. Audita y la devuelve.

---

## 9. Flujos alternativos

Ninguno.

---

## 10. Excepciones

| ID | Situación | Resultado |
|---|---|---|
| `EX-001` | Algún dato falta o es inválido | Rechazo, con todos los problemas juntos y sin tocar nada |
| `EX-002` | El país no existe | Rechazo |
| `EX-003` | El país está inactivo | Conflicto |
| `EX-004` | **El código ya existe**, en este o en otro país, activa o no | Conflicto. Nada cambia |
| `EX-005` | Quien pide no tiene `movements:create-payout-institution` | Prohibido |

---

## 11. Validaciones

| ID | Validación |
|---|---|
| `VAL-001` | Código presente, de 2 a 30 caracteres, con la forma de §6.1 una vez pasado a mayúsculas |
| `VAL-002` | Nombre presente, no en blanco, de hasta 100 caracteres |
| `VAL-003` | Tipo presente y uno de los dos |
| `VAL-004` | País presente y con forma de identificador |

---

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-MV-358` | Con datos válidos se registra la entidad **activa**, y la respuesta la devuelve con su país resuelto |
| `CA-MV-359` | Un código escrito en minúsculas se guarda y se devuelve **en mayúsculas**, y el nombre sin espacios a los lados |
| `CA-MV-360` | Un código que **ya existe** —también en otro país, y también si esa entidad está inactiva— responde conflicto, y **no se registra nada** |
| `CA-MV-361` | Dos peticiones simultáneas con el mismo código registran **una** entidad; la otra responde conflicto |
| `CA-MV-362` | Código con forma inválida, nombre en blanco, tipo desconocido o país ausente: rechazo con **todos** los problemas en la misma respuesta, y nada cambia |
| `CA-MV-363` | País inexistente: rechazo; país **inactivo**: conflicto. En los dos casos nada cambia |
| `CA-MV-364` | Sin `movements:create-payout-institution` responde prohibido; sin autenticar, `401` |
| `CA-MV-365` | Queda **auditada**, con quién la registró |
| `CA-MV-547` | Un código que **empieza por dígito** —`1007`, `0507_NEQUI`— se registra; uno que empieza por guion bajo se rechaza (05-10-2026) |
| `CA-MV-703` | Al migrar, el catálogo trae **las veintiséis entidades de Colombia** —veintidós bancos y cuatro billeteras móviles—, **activas** y con su **código ACH** como código; aplicar la siembra otra vez no duplica ninguna, y **no pisa** una entidad que ya existiera con el mismo código (08-10-2026) |

---

## 13. Casos límite

| Caso | Comportamiento |
|---|---|
| Dos entidades con el mismo **nombre** y distinto código | Se admite: el nombre es para leerlo, y dos países pueden tener un banco que se llame igual |
| El mismo banco en dos países | Dos entidades, con dos códigos (`BANCOLOMBIA`, `BANCOLOMBIA_PA`) |
| Un código solo de dígitos (`1007`) | Se admite desde el 05-10-2026: es el código de compensación con el que muchos bancos se identifican |

---

## 14. Preguntas abiertas

Ninguna.

---

## 15. Control de cambios

| Versión | Fecha | Cambio | Autor |
|---|---|---|---|
| 0.1.0 | 01-10-2026 | Primera versión, con las cuentas de cobro de `MV` ([`requirements/mv.md`](../../../requirements/mv.md) v0.61.0 §4.5). **Código único en todo el catálogo e inmutable**, tipo inmutable, un país, nace activa. Criterios `CA-MV-358` a `CA-MV-365`. | Responsable del proyecto |
| 0.2.0 | 05-10-2026 | **El código puede empezar por dígito** ([`requirements/mv.md`](../../../requirements/mv.md) v0.74.0), a petición del responsable del proyecto: muchos bancos tienen un código numérico. §6.1 y `VAL-001` lo admiten, y `CA-MV-547` lo fija. El guion bajo sigue sin poder ir al principio. | Responsable del proyecto |
| 0.3.0 | 08-10-2026 | **El catálogo nace con las entidades de Colombia** ([`requirements/mv.md`](../../../requirements/mv.md) v0.96.0, `RN-MV-054` enmendada), por decisión del responsable del proyecto: veintidós bancos y cuatro billeteras móviles, activos, con el código ACH como código. §2 y §4.2 dejan de decir que nace vacío; `CA-MV-703` lo fija. | Responsable del proyecto |
