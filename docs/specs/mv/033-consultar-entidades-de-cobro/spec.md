# SPEC — `RF-MV-033` Consultar las entidades de cobro

| Campo | Valor |
|---|---|
| Requerimiento | `RF-MV-033` |
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

Que quien va a registrar una cuenta de cobro **vea entre qué bancos y billeteras puede elegir**, y que administración vea el catálogo entero, también lo desactivado.

---

## 2. Contexto

El catálogo lo llena `RF-MV-032` y lo corrige `RF-MV-034`. **Lo leen dos públicos con la misma operación**: la persona que registra su cuenta (`RF-MV-035`), que necesita las activas de su país, y administración, que necesita todas. Es una sola lectura con filtros, y por eso un solo permiso, por tipo de rol (`RN-SEG-015`).

### 2.1 Lo que este requerimiento decide

| Decisión | Qué se decidió |
|---|---|
| **Por omisión, solo las activas** | Es lo que se ofrece a las personas (`RN-MV-054`). Las inactivas se piden expresamente |
| **Filtra por país y por tipo** | El formulario de la cuenta pide las de un país; elegir entre banco y billetera es la primera pregunta del formulario |
| **El país no se deduce de quien pregunta** | Administración consulta países que no son el suyo. Que una persona solo pueda **usar** las de su país lo garantiza `RF-MV-035`, que es donde importa |
| **Sin paginar** | Un catálogo de entidades por país es de decenas de filas, como los métodos de pago (`RF-MV-009`) |
| **Ordenado** por país y por nombre | Lo que un selector necesita |
| **No es pública** | Al revés que los métodos de pago (`RF-MV-009`): a esta lista solo le sirve a quien ya tiene cuenta y va a cobrar |

---

## 3. Actores

| Actor | Rol en esta funcionalidad |
|---|---|
| Quien tiene `movements:read-payout-institutions` | Consulta el catálogo |

---

## 4. Alcance

### 4.1 Incluye

- Listar las entidades, con filtros por país, tipo y estado.

### 4.2 No incluye

- **El detalle de una entidad**: la fila ya lo lleva todo.
- **Cuántas cuentas tiene cada entidad**: no se ha pedido.

---

## 5. Reglas de negocio aplicables

| Regla | Cómo aplica |
|---|---|
| `RN-MV-054` | Lo inactivo no se ofrece: queda fuera salvo que se pida |

**Ninguna regla nueva.**

---

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción |
|---|---|---|
| País | No | Solo las de ese país |
| Tipo | No | `BANCO` o `BILLETERA_MOVIL` |
| Estado | No | `ACTIVA` —por omisión—, `INACTIVA` o `TODAS` |

### 6.2 Salida

**La lista**: de cada entidad, identificador, código, nombre, tipo, país (identificador, código y nombre) y si está activa.

---

## 7. Precondiciones y postcondiciones

| Tipo | Condición |
|---|---|
| Precondición | El actor tiene `movements:read-payout-institutions`; los filtros son válidos |
| Postcondición | Ninguna: no escribe nada |

---

## 8. Flujo principal

1. El actor pide el catálogo, con o sin filtros.
2. El sistema valida los filtros.
3. Devuelve las entidades que cumplen, ordenadas por nombre de país y nombre de entidad.

---

## 9. Flujos alternativos

### FA-001 — Nada cumple los filtros

Una lista vacía. **Un país que no existe también da una lista vacía**, y no un error: es un filtro, y lo que dice es que no hay entidades de ese país.

---

## 10. Excepciones

| ID | Situación | Resultado |
|---|---|---|
| `EX-001` | Un filtro tiene forma inválida —tipo o estado desconocido, país que no es un identificador— | Rechazo |
| `EX-002` | Quien pide no tiene `movements:read-payout-institutions` | Prohibido |

---

## 11. Validaciones

| ID | Validación |
|---|---|
| `VAL-001` | País, si viene, con forma de identificador |
| `VAL-002` | Tipo, si viene, uno de los dos |
| `VAL-003` | Estado, si viene, uno de los tres |

---

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-MV-366` | Sin filtros devuelve **solo las activas**, de todos los países, ordenadas por país y por nombre |
| `CA-MV-367` | Con país y tipo devuelve solo las de ese país y ese tipo |
| `CA-MV-368` | Con estado `INACTIVA` devuelve solo las inactivas, y con `TODAS`, las dos |
| `CA-MV-369` | Un país sin entidades, o que no existe, devuelve una lista vacía; un tipo o un estado desconocidos responden rechazo |
| `CA-MV-370` | Con el permiso por tipo de rol, **un cliente** la consulta; sin el permiso responde prohibido, y sin autenticar, `401` |

---

## 13. Casos límite

| Caso | Comportamiento |
|---|---|
| Una entidad desactivada mientras alguien llena el formulario | Deja de salir en la lista; si la elige igual, `RF-MV-035` lo rechaza |

---

## 14. Preguntas abiertas

Ninguna.

---

## 15. Control de cambios

| Versión | Fecha | Cambio | Autor |
|---|---|---|---|
| 0.1.0 | 01-10-2026 | Primera versión, con las cuentas de cobro de `MV` ([`requirements/mv.md`](../../../requirements/mv.md) v0.61.0 §4.5). **Por omisión, solo las activas**; filtros por país, tipo y estado; sin paginar. Criterios `CA-MV-366` a `CA-MV-370`. | Responsable del proyecto |
