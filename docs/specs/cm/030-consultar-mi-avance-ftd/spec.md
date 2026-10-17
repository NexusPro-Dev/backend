# SPEC — `RF-CM-030` Consultar mi avance de FTD en el periodo abierto

| Campo | Valor |
|---|---|
| Requerimiento | `RF-CM-030` |
| Módulo | `CM` — Comisiones |
| Versión | 0.1.0 |
| Estado | **Aprobada** |
| Autor | Responsable técnico |
| Aprobada por | Responsable del proyecto |
| Fecha de aprobación | 17-10-2026 |

!!! info "Qué va en este documento"

    **Qué debe pasar, y por qué.** Nada más.

    **Prueba de pertenencia:** si un cambio de tecnología lo invalidaría, no pertenece aquí — va a `plan.md`. No se nombran tablas, clases, endpoints ni librerías.

---

## 1. Objetivo

Que un vendedor sepa **durante el mes** cómo va su comisión afftrack: cuántos FTD lleva, qué escalón alcanzaría si el cierre fuera ahora, por cuánto, y cuántos le faltan para el siguiente.

---

## 2. Contexto

La comisión afftrack se calcula **dentro del cierre** (`RN-CM-043`), así que hasta hoy el vendedor no veía nada hasta el día 1: lo único propio era lo ya cobrado, en sus lotes (`RF-CM-012`). El responsable del proyecto: «que el vendedor pueda ver su avance de FTD en el mes» (`requirements/cm.md` v0.45.0 §5.13, `RN-CM-056`).

**Este requerimiento corre la cuenta del cierre (`RF-CM-020`) para una sola persona, con el instante de la consulta como corte, y no guarda nada.**

### 2.1 Lo que este requerimiento decide

| Decisión | Qué se decidió | Lo que se descartó |
|---|---|---|
| **Qué se ve** | **La simulación del cierre**: nuevos + remanente = disponibles, el escalón alcanzado con su importe estimado, lo que sobraría y cuántos faltan para el siguiente | *Solo los conteos* — el vendedor tendría que conocer su escala y hacer la cuenta |
| **Qué FTD cuentan** | **Los mismos que el cierre** —los suyos y los de toda su red (`RN-CM-042`)—, **desglosados** en directos y de la red | *Solo el total* — no diría de dónde sale |
| **Qué periodo** | **Solo el abierto** | *También los cierres pasados* — lo cobrado ya está en los lotes (`RF-CM-012`, `RF-CM-026`) |
| **Qué escala** | **La vigente hoy** | *La del último día del periodo* — todavía no se sabe |
| **Si se guarda** | **No**: se calcula al consultar | *Un saldo guardado* — se desfasaría de lo que el cierre calcula |

---

## 3. Actores

| Actor | Rol en esta funcionalidad |
|---|---|
| Vendedor —y todo rol que vea sus lotes— | Consulta lo suyo (`afftrack-settlements:read-own-progress`) |

---

## 4. Alcance

### 4.1 Incluye

- Por cada producto FTD en el que la persona tenga FTD nuevos, remanente o escala vigente: los FTD nuevos —directos y de la red—, el remanente del cierre anterior, los disponibles, el escalón alcanzado con su importe estimado, lo que sobraría, y el siguiente escalón con cuántos faltan.
- Cuándo empezó el periodo abierto y cuándo es el próximo cierre programado.

### 4.2 No incluye

- **Las liquidaciones ya hechas**: las de todas las personas, `RF-CM-021`; lo cobrado, en los lotes (`RF-CM-012`).
- **El avance de otra persona**: ni de un subordinado ni por filtro. La persona la pone la sesión.
- **Pagar o contar nada**: eso lo hace solo el cierre (`RF-CM-020`).

---

## 5. Reglas de negocio aplicables

| Regla | Cómo aplica |
|---|---|
| `RN-CM-056` | Toda la consulta: qué cuenta, contra qué escala, qué se responde y que no escribe |
| `RN-CM-040` | Qué es un FTD nuevo, con el instante de la consulta como corte |
| `RN-CM-041` | Cómo se elige el escalón y cuánto sobra |
| `RN-CM-042` | Los de la red cuentan, con la cadena del día de cada activación |
| `RN-CM-039` | Qué escala rige: la de la persona si tiene, si no la de su rol |
| `RN-CM-055` | Desde cuándo es el periodo abierto |

---

## 6. Datos

### 6.1 Entrada

Ninguna. La persona es la de la sesión.

### 6.2 Salida

**El inicio del periodo abierto** —nulo si nunca hubo cierre— y **el próximo cierre programado** —nulo si está apagado—. Y una entrada por producto FTD con: **el producto y su moneda**; **el remanente**; **los FTD nuevos, los directos y los de la red**; **los disponibles**; **el escalón alcanzado** —límite, valor por FTD, de qué escala sale e **importe estimado**—, o nada; **lo que sobraría**; y **el siguiente escalón** —límite, valor por FTD y **cuántos faltan**—, o nada.

---

## 7. Precondiciones y postcondiciones

| Tipo | Condición |
|---|---|
| Precondición | La persona tiene sesión y el permiso |
| Postcondición | **Nada cambia**: ni liquidación, ni conteo, ni comisión, ni lote |

---

## 8. Flujo principal

1. El vendedor pide su avance.
2. Se toman los FTD activados hasta este instante que todavía no se contaron en ningún cierre, y de cada uno la cadena del día de su activación; quedan los que tienen a la persona en su cadena, separados en los que vendió ella y los de su red.
3. Por cada producto FTD se suma el remanente de su última liquidación.
4. Contra su escala vigente hoy se elige el escalón alcanzado y el siguiente.
5. Se responde, con el inicio del periodo y el próximo cierre.

---

## 9. Flujos alternativos

### FA-001 — No alcanza ningún escalón

No hay escalón alcanzado ni importe; sobraría todo lo disponible, y el siguiente es el primero de su escala.

### FA-002 — Ya pasó el mayor escalón

El alcanzado es el mayor y **no hay siguiente**.

### FA-003 — Sin escala para un producto

Si tiene FTD o remanente en un producto y ninguna escala vigente, el producto aparece con sus cifras y **sin escalón alcanzado ni siguiente**: el cierre los acumularía (`requirements/cm.md` §5.8).

### FA-004 — Nada que mostrar

Sin FTD, sin remanente y sin escala en ningún producto, la lista va **vacía**.

---

## 10. Excepciones

Ninguna más allá de la autorización.

---

## 11. Validaciones

Ninguna: no hay entrada.

---

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-CM-399` | Con FTD propios y de su red activados en el periodo, y remanente del cierre anterior, responde **directos**, **de la red**, **nuevos** = su suma, **remanente** y **disponibles** = nuevos + remanente |
| `CA-CM-400` | Con **55 disponibles** y escalones de **50 y 60**, el alcanzado es el de **50** con importe **50 × su valor**, sobrarían **5**, y el siguiente es el de **60** con **5 que faltan** |
| `CA-CM-401` | Con **40** disponibles no hay alcanzado, sobrarían 40 y faltan **10** para el de 50; con **130** el alcanzado es el de **60** y **no hay siguiente** |
| `CA-CM-402` | Con FTD y sin escala vigente, el producto aparece **sin alcanzado ni siguiente**; un producto FTD sin FTD, sin remanente y sin escala **no aparece** |
| `CA-CM-403` | Con escala de persona vigente, **rige la suya entera** y no la del rol, y lo dice |
| `CA-CM-404` | **No cuentan** los FTD ya contados en un cierre, ni las líneas sin entregar, sin vendedor o de una venta no confirmada |
| `CA-CM-405` | Un FTD de alguien que **era de su red el día de la activación** cuenta aunque hoy ya no lo sea, y uno de quien **entró a su red después** no cuenta |
| `CA-CM-406` | **No escribe nada**: tras la consulta no hay liquidación, conteo, comisión ni cambio de lote nuevos |
| `CA-CM-407` | Responde el **inicio del periodo** —el corte del último cierre, nulo sin cierres— y el **próximo cierre** —nulo con el programado apagado— |
| `CA-CM-408` | Cada persona ve **solo lo suyo**: el avance de otra no aparece aunque comparta productos |
| `CA-CM-409` | Sin `afftrack-settlements:read-own-progress` se rechaza —**también con `afftrack-settlements:read`**—; el permiso lo portan **exactamente** los roles que portan `commission-batches:list-own` |

---

## 13. Casos límite

| Caso | Comportamiento |
|---|---|
| La escala se corrige antes del cierre | El avance cambia; el cierre usará la del último día del periodo. **Por eso el importe es estimado** |
| Un FTD activado sin vendedor, que se asigna en este periodo | Cuenta desde que tiene vendedor, como en el cierre (`RN-CM-040`) |
| Se corrige el vendedor de un FTD no contado | La consulta siguiente ya cuenta con el vendedor nuevo |
| El cierre está corriendo | Responde lo que vea en ese instante; al terminar, los FTD contados dejan de ser nuevos y pasan al remanente |
| Escalones en dos monedas | Cada producto lleva su moneda; no se suman importes entre productos |

---

## 14. Preguntas abiertas

Ninguna.

---

## 15. Control de cambios

| Versión | Fecha | Cambio | Autor |
|---|---|---|---|
| 0.1.0 | 17-10-2026 | Primera versión ([`requirements/cm.md`](../../../requirements/cm.md) v0.45.0 §5.13, `RN-CM-056`), a petición del responsable del proyecto: el vendedor ve su avance de FTD antes del cierre. Criterios `CA-CM-399` a `CA-CM-409`. | Responsable del proyecto |
