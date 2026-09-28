# SPEC — `RF-MV-020` Aprobar un retiro

| Campo | Valor |
|---|---|
| Requerimiento | `RF-MV-020` |
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

Que quien paga los retiros pueda dejar escrito **«este dinero ya salió»**, con la referencia de la transferencia si la tiene, y que lo retenido **salga de la plataforma** de forma definitiva.

---

## 2. Contexto

**Un retiro pedido tiene el dinero apartado y nada más** (`RF-MV-019`): la billetera ya no lo tiene, y la empresa todavía no lo ha pagado. Este requerimiento es el que cierra el camino bueno.

**Hoy se aprueba a mano**, por decisión del responsable del proyecto ([`requirements/mv.md`](../../../requirements/mv.md) v0.45.0 §4.3): las pasarelas de salida se integrarán más adelante, y hasta entonces **quien aprueba declara que el dinero salió** —lo transfirió él, o lo vio en el extracto—. Es la misma postura que `RF-MV-003` tomó con los cobros: **el sistema le cree a una persona lo que mañana le creerá a la pasarela**, y el camino no cambia cuando llegue.

**Aprobar escribe un pago.** El retiro es un movimiento, y lo que lo liquida es un pago —dinero que sale por fuera de la plataforma— con el método **manual** y ya confirmado. Así, el día de la pasarela, aprobar será crear ese mismo pago pendiente y dejar que la pasarela lo confirme.

### 2.1 Lo que este requerimiento decide

| Decisión | Qué se decidió |
|---|---|
| **Solo un retiro pendiente** | Uno aprobado ya salió; uno negado ya devolvió el dinero |
| **La referencia es opcional** | Si hubo transferencia, su número es lo que permite conciliar; si se pagó de otra forma, puede no haberlo. No se inventa |
| **Aprobar dos veces aprueba una vez** | Transición atómica, como confirmar una venta |
| **No se aprueba por un importe distinto** | Se paga lo que se pidió y se retuvo. Pagar menos o más son operaciones que no existen |

---

## 3. Actores

| Actor | Rol en esta funcionalidad |
|---|---|
| Quien tiene `movements:approve-withdrawal` | Aprueba cualquier retiro pendiente. Hoy, quien hace las transferencias |

---

## 4. Alcance

### 4.1 Incluye

- Pasar el retiro de **pendiente a confirmado**, con su instante.
- Registrar **el pago que lo liquida**, con el método manual, confirmado, y la referencia si la hay.
- Sacar lo retenido de la cuenta de la persona hacia la cuenta de retiros de la empresa.
- Devolver el retiro como queda.

### 4.2 No incluye

- **Hacer la transferencia.** El sistema registra que se hizo.
- **Negar** (`RF-MV-021`).
- **Deshacer un retiro aprobado.** El dinero salió; devolverlo sería otro movimiento que no existe.

---

## 5. Reglas de negocio aplicables

| Regla | Cómo aplica |
|---|---|
| `RN-MV-043` | Solo se aprueba lo pendiente, una vez; lo retenido sale hacia la cuenta de retiros de la empresa y se escribe el pago que lo liquida |
| `RN-MV-042` | Dos asientos que suman cero, en un solo evento |
| `RN-MV-039` | El pago del retiro nace confirmado; a lo sumo uno confirmado por movimiento |
| `RN-MV-005` | El retiro va de pendiente a confirmado, y de ahí no se sale |

**Ninguna regla nueva.**

---

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción |
|---|---|---|
| El retiro | Sí | Cuál, por su identificador |
| Referencia | No | La de la transferencia o del comprobante bancario, tal como la da el banco. Acotada en longitud |

### 6.2 Salida

**El retiro**, confirmado, **con su pago**: método manual, importe, referencia y cuándo.

---

## 7. Precondiciones y postcondiciones

| Tipo | Condición |
|---|---|
| Precondición | El actor tiene `movements:approve-withdrawal`; el retiro existe y está pendiente |
| Postcondición | El retiro está confirmado; tiene un pago confirmado con el método manual; lo retenido de la persona bajó en el importe y la cuenta de retiros de la empresa subió en lo mismo; la billetera **no cambió**; todo está auditado |

---

## 8. Flujo principal

1. El actor indica qué retiro aprueba y, si la tiene, la referencia.
2. El sistema valida la referencia, **antes de mirar el retiro**.
3. Pasa el retiro de pendiente a confirmado **en un acto que solo acierta si seguía pendiente**.
4. Registra el pago confirmado, con el método manual.
5. Mueve lo retenido a la cuenta de retiros de la empresa.
6. Audita y devuelve el retiro.

---

## 9. Flujos alternativos

### FA-001 — Aprobar y negar llegan a la vez

Uno gana. Si ganó aprobar, negar recibe «está confirmado» y **no devuelve nada**; si ganó negar, aprobar recibe «está rechazado» y **no paga nada**. Lo retenido se mueve **una sola vez**, hacia un lado o hacia el otro.

---

## 10. Excepciones

| ID | Situación | Resultado |
|---|---|---|
| `EX-001` | El retiro no existe, o el identificador es de otro tipo de movimiento | No encontrado |
| `EX-002` | El retiro no está pendiente | Conflicto, diciendo en qué estado está. Nada cambia |
| `EX-003` | La referencia excede la longitud máxima | Rechazo, antes de tocar nada |
| `EX-004` | Quien pregunta no tiene `movements:approve-withdrawal` | Prohibido |

---

## 11. Validaciones

| ID | Validación |
|---|---|
| `VAL-001` | El identificador es válido |
| `VAL-002` | La referencia, si viene, no excede la longitud máxima; se guarda recortada, y en blanco equivale a ausente |

---

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-MV-236` | Un retiro pendiente pasa a **confirmado**; tiene **un pago confirmado** con el método manual, su importe y la referencia indicada |
| `CA-MV-237` | **Lo retenido baja** en el importe y **la cuenta de retiros de la empresa sube** en lo mismo; **la billetera no cambia**; los dos asientos suman cero y llevan el pago |
| `CA-MV-238` | Sin referencia se aprueba igual, y el pago la lleva vacía |
| `CA-MV-239` | Aprobar **por segunda vez** responde conflicto y no mueve nada |
| `CA-MV-240` | Un retiro **negado** no se puede aprobar: conflicto, y lo retenido no se mueve |
| `CA-MV-241` | **Aprobar y negar a la vez** mueven lo retenido **una sola vez** |
| `CA-MV-242` | El identificador de una **venta** responde no encontrado |
| `CA-MV-243` | Sin `movements:approve-withdrawal` responde prohibido; sin autenticar, `401`. El cambio queda auditado |

---

## 13. Casos límite

| Caso | Comportamiento |
|---|---|
| La persona tiene **otros retiros pendientes** | Se aprueba este y los demás siguen retenidos |
| La persona fue **eliminada** entre pedir y aprobar | Se aprueba: el dinero era suyo y ya estaba apartado. Si se le paga o no lo decide quien aprueba |
| La moneda se **desactivó** | Se aprueba igual |

---

## 14. Preguntas abiertas

**La aprobación con pasarela.** Aprobar pasará a abrir un pago pendiente que la pasarela confirmará. Queda para la integración.

**Si aprobar exige que otra persona lo revise** —cuatro ojos—. No se ha pedido.

---

## 15. Control de cambios

| Versión | Fecha | Cambio | Autor |
|---|---|---|---|
| 0.1.0 | 26-09-2026 | Primera versión, con la etapa 6 de `MV` ([`requirements/mv.md`](../../../requirements/mv.md) v0.45.0). **A mano, con el método manual y la referencia opcional**, hasta que se integren las pasarelas de salida. Aprobar **escribe el pago** que liquida el retiro y saca lo retenido hacia la cuenta de retiros de la empresa; la billetera no se toca. Criterios `CA-MV-236` a `CA-MV-243`. | Responsable del proyecto |
