# SPEC — `RF-CM-012` Consultar mis comisiones

| Campo | Valor |
|---|---|
| Requerimiento | `RF-CM-012` |
| Módulo | `CM` — Comisiones |
| Versión | 0.2.0 |
| Estado | **Aprobada** |
| Autor | Responsable técnico |
| Aprobada por | Responsable del proyecto |
| Fecha de aprobación | 28-09-2026 |
| Enmendada el | 29-09-2026 — **las comisiones propias dicen su clase**, como en `RF-CM-010` (`RN-CM-044`) |

!!! info "Qué va en este documento"

    **Qué debe pasar, y por qué.** Nada más.

    **Prueba de pertenencia:** si un cambio de tecnología lo invalidaría, no pertenece aquí — va a `plan.md`. No se nombran tablas, clases, endpoints ni librerías.

---

## 1. Objetivo

Que cada vendedor vea **lo que ha ganado**: sus lotes —el abierto, que crece con cada venta, los pendientes de pago y los pagados— y dentro de cada uno, de qué ventas sale cada comisión.

---

## 2. Contexto

**Es lo que hace visible el devengo automático** (`requirements/cm.md` v0.19.0, §5.7): la comisión nace en el momento, y el vendedor la ve **el mismo día** en su lote abierto, y no a fin de mes.

**Solo lo propio, y sin D-22.** Es el único caso de «ver comisiones» que no necesita alcance de datos: cada quien ve **lo suyo**, filtrado por su propia identidad, sin recorrer ninguna estructura (`requirements/cm.md` §1.3).

### 2.1 Lo que este requerimiento decide

| Decisión | Qué se decidió |
|---|---|
| **Lo mío, y solo lo mío** | Un lote ajeno **no existe** para quien lo pide: se responde igual que a uno inexistente, para no confirmar que existe |
| **Lo mismo que ve administración** | El listado y el detalle tienen la forma de `RF-CM-010`, sin el filtro de persona |
| **Cada nivel es de quien cobra** | Un lote es de una persona; si ella cobra como superior por la venta de otro, la comisión está en **su** lote, con el nivel que le corresponde |

---

## 3. Actores

| Actor | Rol en esta funcionalidad |
|---|---|
| Cualquier persona que cobre comisiones | Lista sus lotes (`commission-batches:list-own`) y abre uno (`commission-batches:read-own`) |

---

## 4. Alcance

### 4.1 Incluye

- Mis lotes, filtrables por estado, moneda y fechas.
- El detalle de uno de mis lotes.

### 4.2 No incluye

- Los lotes de mi red: depende de **D-22**.
- Las líneas de mis ventas que **no** pagaron comisión: `RF-CM-014`, que es de administración.

---

## 5. Reglas de negocio aplicables

| Regla | Cómo aplica |
|---|---|
| `RN-CM-033` | El abierto, con su total al día |
| `RN-CM-008` | Lo copiado, no lo que dice hoy la tasa |
| `RN-SEG-015` | Ver lo propio también exige permiso |

---

## 6. Datos

### 6.1 Entrada

Como `RF-CM-010`, **sin** persona: la persona es quien pregunta.

### 6.2 Salida

La de `RF-CM-010`, **con la clase de cada comisión desde el 29-09-2026**: el vendedor ve en sus lotes lo que cobró por venta y lo que cobró por afftrack. **No ve su remanente de FTD**: eso está en `RF-CM-021`, sin lectura propia todavía (`requirements/cm.md` §6).

---

## 7. Precondiciones y postcondiciones

| Tipo | Condición |
|---|---|
| Precondición | El actor porta el permiso de la operación |
| Postcondición | Ninguna: no escribe |

---

## 8. Flujo principal

1. La persona pide sus lotes.
2. Se devuelven **los suyos**, el más reciente primero.
3. Abre uno de ellos y se devuelve con sus comisiones.

---

## 9. Flujos alternativos

**Ninguno**: quien no ha ganado nada recibe una lista vacía.

---

## 10. Excepciones

| ID | Situación | Resultado |
|---|---|---|
| `EX-001` | El lote no existe **o es de otra persona** | No encontrado, **el mismo** en los dos casos |

---

## 11. Validaciones

Las de `RF-CM-010`, sin la persona.

---

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-CM-197` | El listado devuelve **solo** los lotes del actor, de todas sus monedas y estados, el más reciente primero |
| `CA-CM-198` | Tras **confirmarse una venta** suya, el total de su lote **abierto** ya incluye la comisión nueva |
| `CA-CM-199` | Un superior ve en **su** lote la comisión que cobra por la venta de su subordinado, con su nivel |
| `CA-CM-200` | El detalle de un lote propio trae sus comisiones con la misma forma que `RF-CM-010` |
| `CA-CM-201` | Pedir el detalle de un lote **ajeno** responde **no encontrado**, igual que uno inexistente |
| `CA-CM-202` | Sin el permiso de cada operación, se rechaza; un rol vendedor **lo porta** desde su siembra |
| `CA-CM-263` | En sus propios lotes, un vendedor ve sus comisiones `POR_AFFTRACK` con su clase, sin venta ni nivel, y el total las incluye (29-09-2026) |

---

## 13. Casos límite

| Caso | Comportamiento |
|---|---|
| Un funcionario que no cobra comisiones pide los suyos | Lista vacía, si porta el permiso |
| La persona tiene un lote abierto y uno pendiente en la misma moneda | Salen los dos |

---

## 14. Preguntas abiertas

Ninguna.

---

## 15. Control de cambios

| Versión | Fecha | Cambio | Autor |
|---|---|---|---|
| 0.1.0 | 28-09-2026 | Primera versión ([`requirements/cm.md`](../../../requirements/cm.md) v0.20.0). Solo lo propio, con el lote abierto al día; un lote ajeno es no encontrado. Criterios `CA-CM-197` a `CA-CM-202`. | Responsable del proyecto |

| 0.2.0 | 29-09-2026 | **Las comisiones propias dicen su clase** (`RN-CM-044`, [`requirements/cm.md`](../../../requirements/cm.md) v0.22.0 §5.8), heredado de `RF-CM-010`. `CA-CM-263`. | Responsable del proyecto |
