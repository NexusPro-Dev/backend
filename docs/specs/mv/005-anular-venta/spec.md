# SPEC — `RF-MV-005` Anular una venta pendiente

| Campo | Valor |
|---|---|
| Requerimiento | `RF-MV-005` |
| Módulo | `MV` — Movimientos |
| Versión | 0.2.0 |
| Estado | **Aprobada** |
| Autor | Responsable técnico |
| Aprobada por | Responsable del proyecto |
| Fecha de aprobación | 17-09-2026 |

!!! warning "Enmendada el 26-09-2026 — anular cierra también el pago pendiente"

    La etapa 6 de `MV` ([`requirements/mv.md`](../../../requirements/mv.md) v0.44.0 §4.3, `RN-MV-039`) pone el método y el resultado del cobro en **cada intento de pago**. Anular una venta **cierra su pago pendiente como rechazado**, con el motivo de la anulación precedido de «Venta anulada:», en el mismo acto: un pago pendiente de una venta que ya no existe sería un cobro esperando a nadie. **Si la venta no tiene pago pendiente** —el último se rechazó— se anula igual. La ruta, el motivo y el permiso no cambian.

    **«Anular no es rechazar» sigue siendo verdad, y ahora se ve mejor**: rechazar es del **pago** y deja la venta abierta (`RF-MV-004`); anular es de la **venta** y la cierra. Criterio `CA-MV-220`; lo construye `RF-MV-018` · `tasks.md` `T-08`.

!!! info "Qué va en este documento"

    **Qué debe pasar, y por qué.** Nada más.

    **Prueba de pertenencia:** si un cambio de tecnología lo invalidaría, no pertenece aquí — va a `plan.md`. No se nombran tablas, clases, endpoints ni librerías.

---

## 1. Objetivo

Que quien administra pueda **sacar del embudo una venta pendiente que no debía existir** —se registró por error, al cliente equivocado, con el producto equivocado— dejando escrito **por qué**, sin borrarla y sin que pueda volver.

---

## 2. Contexto

**Hoy una venta pendiente solo tiene una salida, y es la buena.** `RF-MV-003` la confirma; nada la retira. Una venta registrada por error se queda `PENDIENTE` para siempre, aparece en el registro de lo comprado de la persona como algo que le falta pagar (`RF-MV-014`) y en el libro como cobro esperado (`RF-MV-006`). **Este requerimiento es la salida que dice «esto nunca debió estar aquí»**, y estaba declarado desde el 02-09-2026 en `requirements/mv.md` §4.1.

**Anular no es rechazar, y la diferencia es de negocio** (`requirements/mv.md` §4.1). Una venta **rechazada** es un cobro que se intentó y no entró; una **anulada** es una venta que **no debía existir**. Fundirlas ahorraría un estado y borraría el único número que responde «cuánto se intenta cobrar y no entra». Por eso son dos requerimientos, con **dos permisos**: quien concilia pagos (`movements:confirm`) no tiene por qué poder hacer desaparecer del embudo ventas ajenas (`movements:void`). `RF-MV-004` queda para su día; este no lo escribe.

**Y anular no es borrar.** `RN-MV-001` dice que la venta no se toca y `RN-MV-005` que `ANULADA` es final: la fila sigue ahí, con su código, sus líneas y sus importes, y **con el motivo**. Una venta anulada que no dijera por qué obligaría a buscarlo en la auditoría, y quien la mire dentro de un año en el detalle o en el listado necesita leerlo ahí.

### 2.1 Lo que este requerimiento decide, y lo que no

| Decisión | Qué se decidió |
|---|---|
| **Solo lo pendiente** | Una confirmada ya entregó; retirar lo entregado es una operación que no existe (`requirements/mv.md` §5.3). Una rechazada o anulada ya es final |
| **El motivo es obligatorio** | Es lo que separa «anulada» de «desaparecida». Se exige antes de tocar nada, como el motivo de una eliminación (Art. V.13) |
| **Anular dos veces anula una vez** | Misma transición atómica que confirmar: la segunda encuentra la venta anulada y no hace nada |
| **Quién anula** | Quien tiene `movements:void`. **No el comprador**: que una persona retire su propia compra pendiente sería otro requerimiento, sin permiso y con otras preguntas —¿puede si ya pagó y nadie ha confirmado?—, y no se decide aquí |

**Anular no entrega ni retira nada.** Una venta pendiente no concedió (`RN-MV-004`): sus líneas siguen `PENDIENTE` de entrega y así se quedan, porque ya no hay nada que entregar. El registro de lo comprado las muestra `ANULADO` (`RF-MV-014`).

---

## 3. Actores

| Actor | Rol en esta funcionalidad |
|---|---|
| Quien tiene `movements:void` | Anula cualquier venta pendiente del sistema, con motivo. Hoy, el superadministrador (`requirements/mv.md` §6.1) |

---

## 4. Alcance

### 4.1 Incluye

- Pasar una venta de **pendiente a anulada**, con **cuándo** y **por qué**.
- Devolver la venta como queda, con el motivo.
- Auditar el cambio.

### 4.2 No incluye

- **Rechazar** (`RF-MV-004`): es la otra salida, con otro significado y otro permiso.
- **Deshacer una venta confirmada** (`requirements/mv.md` §5.3).
- **Que el comprador retire su propia compra** (§2.1).
- **Borrar** la venta: no se borra nada, nunca (`RN-MV-001`).
- **Anular en lote.**

---

## 5. Reglas de negocio aplicables

| Regla | Cómo aplica |
|---|---|
| `RN-MV-001` | La venta no se toca: ningún importe, ninguna línea. Cambian el estado, el instante y el motivo, que son **de la resolución** y no de la venta |
| `RN-MV-004` | Una pendiente no concedió nada; anularla no tiene nada que retirar |
| `RN-MV-005` | Solo se anula lo **pendiente**; `ANULADA` es final; anular dos veces anula una vez — la transición es atómica |

**Ninguna regla nueva.**

---

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción |
|---|---|---|
| La venta | Sí | Cuál se anula, por su identificador |
| Motivo | **Sí** | Por qué no debía existir, escrito para una persona. Con contenido y acotado en longitud |

### 6.2 Salida

**La venta, tal como queda** —la misma forma que confirmar y que el detalle—, con dos datos que solo una anulada lleva: **cuándo se anuló** y **por qué**.

---

## 7. Precondiciones y postcondiciones

| Tipo | Condición |
|---|---|
| Precondición | El actor tiene `movements:void`; la venta existe y está pendiente; el motivo tiene contenido |
| Postcondición | La venta está anulada, con su instante y su motivo; nada más cambió; el cambio está auditado con el motivo |

---

## 8. Flujo principal

1. El actor indica qué venta anula y por qué.
2. El sistema comprueba que el motivo tiene contenido, **antes de mirar la venta**.
3. Pasa la venta de pendiente a anulada **en un solo acto que solo acierta si seguía pendiente**, anotando el instante y el motivo.
4. Audita el cambio.
5. Devuelve la venta como queda.

---

## 9. Flujos alternativos

### FA-001 — La venta lleva un upgrade

Se anula igual; no hay membresía que retirar porque no se concedió. Es lo que hace que anular sea barato y confirmar no: el orden de las dos reglas (`RN-MV-004` y `RN-MV-020`) es lo que deja «anulada» sin efectos que deshacer.

### FA-002 — Anular y confirmar llegan a la vez

Una gana. Si ganó anular, confirmar recibe «no está pendiente: está anulada»; si ganó confirmar, anular recibe «está confirmada» y **no puede deshacerlo**.

---

## 10. Excepciones

| ID | Situación | Resultado |
|---|---|---|
| `EX-001` | La venta no existe | No encontrado |
| `EX-002` | La venta **no está pendiente** | Conflicto, diciendo en qué estado está. Nada cambia |
| `EX-003` | El motivo falta o está vacío | Rechazo, antes de tocar la venta |
| `EX-004` | Quien pregunta no tiene `movements:void` | Prohibido |

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
| `CA-MV-110` | Una venta pendiente pasa a **anulada**, con su instante y su motivo, y la respuesta los trae |
| `CA-MV-111` | Anularla **por segunda vez** responde conflicto y no cambia nada |
| `CA-MV-112` | Una venta **confirmada** no se puede anular: conflicto diciendo su estado, y la membresía concedida sigue ahí |
| `CA-MV-113` | Una venta que **no existe** responde no encontrado |
| `CA-MV-114` | Sin motivo, o con motivo en blanco, responde rechazo **y la venta sigue pendiente** |
| `CA-MV-115` | Sin `movements:void` responde prohibido —**también con `movements:confirm`**—; sin autenticar, `401` |
| `CA-MV-116` | Las líneas de una venta anulada siguen **pendientes de entrega**, y el registro de lo comprado las muestra `ANULADO` |
| `CA-MV-117` | El cambio queda **auditado con el motivo** |
| `CA-MV-118` | El detalle de la venta —propio y de administración— muestra cuándo y por qué se anuló |
| `CA-MV-220` | Anular una venta con un pago pendiente lo deja **rechazado**, con el motivo de la anulación; sin pago pendiente, la venta se anula igual (26-09-2026) |

**`CA-MV-115` con `movements:confirm` puesto es el que sostiene la separación de permisos**: quien concilia no puede hacer desaparecer ventas del embudo.

---

## 13. Casos límite

| Caso | Comportamiento |
|---|---|
| Una venta de **importe cero** (`GRATIS`) | Se anula como cualquier otra |
| Una venta de **paquete** | Se anula entera: una venta es una venta. No hay anulación por línea |
| Motivo con espacios alrededor | Se guarda recortado |

---

## 14. Preguntas abiertas

**Si el comprador puede retirar su propia compra pendiente.** Hoy no; es otro requerimiento con sus propias preguntas.

**Rechazar** (`RF-MV-004`) hereda la forma de este —transición atómica, instante, y si lleva motivo— y se escribe cuando exista quien lo pida.

---

## 15. Control de cambios

| Versión | Fecha | Cambio | Autor |
|---|---|---|---|
| 0.1.0 | 17-09-2026 | Primera versión, a petición del responsable del proyecto —«¿y para anular un movimiento?»—. **Estaba declarado desde el 02-09-2026**. Lo que la spec carga: **anular no es borrar** —la fila se queda con su motivo, que es lo que separa «anulada» de «desaparecida»—, **el motivo es obligatorio** y se exige antes de tocar nada, **solo lo pendiente** porque lo confirmado ya entregó, y **la misma transición atómica** que confirmar. Lo que deja fuera: rechazar (`RF-MV-004`, otro significado y otro permiso) y que el comprador retire lo suyo. | Responsable del proyecto |
| 0.2.0 | 26-09-2026 | **Anular cierra también el pago pendiente**, como rechazado y con el motivo de la anulación (`requirements/mv.md` v0.44.0, `RN-MV-039`; Art. I.7 sobre un requerimiento construido). La ruta, el motivo y el permiso no cambian. `CA-MV-220`. | Responsable del proyecto |
