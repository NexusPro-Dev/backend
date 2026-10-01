# SPEC — `RF-MV-034` Editar una entidad de cobro

| Campo | Valor |
|---|---|
| Requerimiento | `RF-MV-034` |
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

Que administración pueda **corregir el nombre** de una entidad de cobro y **dejar de ofrecerla** —o volver a ofrecerla— sin borrar nada.

---

## 2. Contexto

**Una entidad no se borra: se desactiva** (`RN-MV-054`). Tiene cuentas de personas y retiros que la nombran, y borrarla los dejaría sin explicación. Desactivarla es lo que se hace cuando un banco cierra, se fusiona o la empresa deja de pagar por él.

**Qué deja de pasar con una entidad inactiva**: no se ofrece en el catálogo (`RF-MV-033`), no admite cuentas nuevas ni cambios hacia ella (`RF-MV-035`, `RF-MV-037`) y **no admite retiros nuevos** hacia las cuentas que ya tiene (`RF-MV-019`). **Lo que no cambia**: esas cuentas siguen existiendo, su dueño las ve marcadas como de una entidad inactiva para registrar otra, y **los retiros ya pedidos se pagan igual**: su destino quedó copiado al pedirlos (`RN-MV-056`).

### 2.1 Lo que este requerimiento decide

| Decisión | Qué se decidió |
|---|---|
| **Se edita el nombre y el estado, y nada más** | El código y el tipo son inmutables (`RN-MV-054`); el país también, porque las cuentas registradas son de personas de ese país. Enviarlos es un error y no se ignora |
| **Corregir el nombre no toca los retiros pedidos** | Su copia guarda el nombre que tenía al pedirse |
| **Reactivar se admite** | Desactivar fue una decisión de la empresa, y puede deshacerla |
| **Editar sin cambiar nada no escribe** | Ni la entidad ni la auditoría |
| **Desactivar no toca las cuentas** | Ni las da de baja ni les quita la marca de principal. Lo decide su dueño |

---

## 3. Actores

| Actor | Rol en esta funcionalidad |
|---|---|
| Quien tiene `movements:update-payout-institution` | Edita cualquier entidad |

---

## 4. Alcance

### 4.1 Incluye

- Cambiar el nombre.
- Desactivar y reactivar.

### 4.2 No incluye

- **Cambiar el código, el tipo o el país.**
- **Borrar una entidad.**
- **Avisar a las personas** que tienen cuentas en una entidad desactivada. No se ha pedido.

---

## 5. Reglas de negocio aplicables

| Regla | Cómo aplica |
|---|---|
| `RN-MV-054` | Código, tipo y país inmutables; se desactiva en vez de borrarse; lo inactivo no se ofrece ni admite cuentas ni retiros nuevos |

**Ninguna regla nueva.**

---

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción |
|---|---|---|
| Entidad | Sí | Cuál |
| Nombre | No | El nuevo. De 1 a 100 caracteres, sin espacios a los lados |
| Activa | No | Sí o no |

**Al menos uno de los dos** tiene que venir.

### 6.2 Salida

**La entidad como queda**, con la forma de `RF-MV-032`.

---

## 7. Precondiciones y postcondiciones

| Tipo | Condición |
|---|---|
| Precondición | El actor tiene `movements:update-payout-institution`; la entidad existe; los datos son válidos |
| Postcondición | La entidad tiene el nombre y el estado pedidos; si algo cambió, queda auditado con el valor anterior |

---

## 8. Flujo principal

1. El actor indica la entidad y lo que cambia.
2. El sistema valida **antes de tocar nada**.
3. Si nada cambia, sigue por `FA-001`.
4. Escribe los cambios.
5. Audita, con lo anterior y lo nuevo, y devuelve la entidad.

---

## 9. Flujos alternativos

### FA-001 — Nada cambia

Se devuelve la entidad, **sin escribir y sin auditar**.

---

## 10. Excepciones

| ID | Situación | Resultado |
|---|---|---|
| `EX-001` | No viene nada que cambiar, el nombre es inválido, o viene el código, el tipo o el país | Rechazo, sin tocar nada |
| `EX-002` | La entidad no existe | No encontrado |
| `EX-003` | Quien pide no tiene `movements:update-payout-institution` | Prohibido |

---

## 11. Validaciones

| ID | Validación |
|---|---|
| `VAL-001` | Viene al menos el nombre o el estado |
| `VAL-002` | El nombre, si viene, no está en blanco y tiene hasta 100 caracteres |
| `VAL-003` | No vienen propiedades desconocidas: el código, el tipo y el país lo son |

---

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-MV-371` | Cambiar el nombre lo cambia, y la respuesta trae la entidad con el nombre nuevo |
| `CA-MV-372` | **Desactivarla** la saca del catálogo por omisión (`RF-MV-033`); **reactivarla** la devuelve |
| `CA-MV-373` | Desactivarla **no cambia ninguna cuenta** de ninguna persona: siguen vivas y la principal sigue siéndolo |
| `CA-MV-374` | Corregir el nombre **no cambia la copia** de un retiro ya pedido hacia una cuenta de esa entidad |
| `CA-MV-375` | Enviar el mismo nombre y el mismo estado **no escribe nada**, ni auditoría |
| `CA-MV-376` | Cuerpo vacío, nombre en blanco, o con código, tipo o país: rechazo, y nada cambia; una entidad que no existe: no encontrado |
| `CA-MV-377` | Sin `movements:update-payout-institution` responde prohibido; sin autenticar, `401`. Un cambio queda **auditado** con el valor anterior |

---

## 13. Casos límite

| Caso | Comportamiento |
|---|---|
| Se desactiva una entidad mientras una persona pide un retiro hacia una cuenta suya | Gana el que escribe primero: si el retiro ya se pidió, se paga; si no, se rechaza (`RF-MV-019`) |
| Una entidad desactivada y reactivada | Sus cuentas vuelven a servir para retirar, sin que su dueño haga nada |

---

## 14. Preguntas abiertas

Ninguna.

---

## 15. Control de cambios

| Versión | Fecha | Cambio | Autor |
|---|---|---|---|
| 0.1.0 | 01-10-2026 | Primera versión, con las cuentas de cobro de `MV` ([`requirements/mv.md`](../../../requirements/mv.md) v0.61.0 §4.5). **Se editan el nombre y el estado**, y nada más; desactivar no toca las cuentas, y los retiros pedidos conservan su copia. Criterios `CA-MV-371` a `CA-MV-377`. | Responsable del proyecto |
