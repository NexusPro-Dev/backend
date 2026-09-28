# SPEC — `RF-MV-021` Negar un retiro

| Campo | Valor |
|---|---|
| Requerimiento | `RF-MV-021` |
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

Que la empresa pueda **negar un retiro por motivos internos**, dejando escrito por qué, y que **el dinero apartado vuelva a la billetera** de la persona en el mismo acto.

---

## 2. Contexto

**Es el único uso que queda del estado «rechazado» de un movimiento**, por decisión del responsable del proyecto ([`requirements/mv.md`](../../../requirements/mv.md) v0.44.0 §4.3): la venta dejó de rechazarse —se rechaza su pago (`RF-MV-004`)— y el retiro negado es lo que ese estado significa ahora.

**Negar no es perder el dinero.** Lo que se niega es **sacarlo de la plataforma**: el importe estaba retenido desde que se pidió (`RF-MV-019`) y vuelve a la billetera, donde la persona puede pedirlo otra vez, gastarlo o dejarlo.

### 2.1 Lo que este requerimiento decide

| Decisión | Qué se decidió |
|---|---|
| **El motivo es obligatorio** | Es lo que le dice a la persona por qué no le pagaron. Se guarda en el retiro, no solo en la auditoría |
| **Devuelve entero** | No hay negativas parciales: lo que se retuvo vuelve |
| **Solo un retiro pendiente** | Uno aprobado ya salió; uno negado ya devolvió |
| **Negar dos veces niega una vez** | Transición atómica |

---

## 3. Actores

| Actor | Rol en esta funcionalidad |
|---|---|
| Quien tiene `movements:reject-withdrawal` | Niega cualquier retiro pendiente, con motivo |

---

## 4. Alcance

### 4.1 Incluye

- Pasar el retiro de **pendiente a rechazado**, con **cuándo** y **por qué**.
- **Devolver lo retenido a la billetera**.
- Devolver el retiro como queda.

### 4.2 No incluye

- **Aprobar** (`RF-MV-020`).
- **Que la persona cancele su propio retiro** (`RF-MV-019` §4.2).
- **Bloquear a la persona** para que no vuelva a pedir. Si hace falta, es una decisión sobre su cuenta, de `SP`.

---

## 5. Reglas de negocio aplicables

| Regla | Cómo aplica |
|---|---|
| `RN-MV-043` | Negar devuelve lo retenido a la billetera; el retiro queda rechazado con su motivo; de ahí no se sale |
| `RN-MV-042` | Dos asientos que suman cero, en un solo evento |
| `RN-MV-005` | El retiro va de pendiente a rechazado |

**Ninguna regla nueva.**

---

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción |
|---|---|---|
| El retiro | Sí | Cuál, por su identificador |
| Motivo | **Sí** | Por qué se niega, escrito para la persona. Con contenido y acotado en longitud |

### 6.2 Salida

**El retiro**, rechazado, con su instante y su motivo.

---

## 7. Precondiciones y postcondiciones

| Tipo | Condición |
|---|---|
| Precondición | El actor tiene `movements:reject-withdrawal`; el retiro existe y está pendiente; el motivo tiene contenido |
| Postcondición | El retiro está rechazado, con instante y motivo; lo retenido bajó en el importe y la billetera subió en lo mismo; **no hay ningún pago**; todo está auditado |

---

## 8. Flujo principal

1. El actor indica qué retiro niega y por qué.
2. El sistema comprueba el motivo, **antes de mirar el retiro**.
3. Pasa el retiro de pendiente a rechazado **en un acto que solo acierta si seguía pendiente**, con el instante y el motivo.
4. Devuelve lo retenido a la billetera.
5. Audita y devuelve el retiro.

---

## 9. Flujos alternativos

### FA-001 — Negar y aprobar llegan a la vez

`RF-MV-020` `FA-001`: uno gana, y lo retenido se mueve una sola vez.

---

## 10. Excepciones

| ID | Situación | Resultado |
|---|---|---|
| `EX-001` | El retiro no existe, o el identificador es de otro tipo de movimiento | No encontrado |
| `EX-002` | El retiro no está pendiente | Conflicto, diciendo en qué estado está. Nada cambia |
| `EX-003` | El motivo falta o está vacío | Rechazo, antes de tocar nada |
| `EX-004` | Quien pregunta no tiene `movements:reject-withdrawal` | Prohibido |

---

## 11. Validaciones

| ID | Validación |
|---|---|
| `VAL-001` | El identificador es válido |
| `VAL-002` | El motivo tiene contenido |
| `VAL-003` | El motivo no excede la longitud máxima |

---

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-MV-244` | Un retiro pendiente pasa a **rechazado**, con su instante y su motivo, y la respuesta los trae |
| `CA-MV-245` | **Lo retenido baja y la billetera sube** en el importe; los dos asientos suman cero; **no se escribe ningún pago** |
| `CA-MV-246` | Tras negarlo, la persona **puede pedir otro retiro** con el dinero devuelto |
| `CA-MV-247` | Negar **por segunda vez**, o negar uno **aprobado**, responde conflicto y no mueve nada |
| `CA-MV-248` | Sin motivo, o con motivo en blanco, responde rechazo **y el retiro sigue pendiente** |
| `CA-MV-249` | El identificador de una **venta** responde no encontrado |
| `CA-MV-250` | Sin `movements:reject-withdrawal` responde prohibido —**también con `movements:approve-withdrawal`**—; sin autenticar, `401`. El cambio queda auditado con el motivo |

---

## 13. Casos límite

| Caso | Comportamiento |
|---|---|
| Motivo con espacios alrededor | Se guarda recortado |
| La persona tiene otros retiros pendientes | Solo vuelve lo de este |

---

## 14. Preguntas abiertas

Ninguna.

---

## 15. Control de cambios

| Versión | Fecha | Cambio | Autor |
|---|---|---|---|
| 0.1.0 | 26-09-2026 | Primera versión, con la etapa 6 de `MV` ([`requirements/mv.md`](../../../requirements/mv.md) v0.44.0). **Es el retiro negado por motivos internos**, que es lo que el estado rechazado de un movimiento significa desde ese día. **Motivo obligatorio**, **devuelve entero** a la billetera, **sin pago**. Criterios `CA-MV-244` a `CA-MV-250`. | Responsable del proyecto |
