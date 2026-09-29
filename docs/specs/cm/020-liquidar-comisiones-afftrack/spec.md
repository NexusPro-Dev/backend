# SPEC — `RF-CM-020` Liquidar las comisiones afftrack en el cierre

| Campo | Valor |
|---|---|
| Requerimiento | `RF-CM-020` |
| Módulo | `CM` — Comisiones |
| Versión | 0.1.0 |
| Estado | **Aprobada** |
| Autor | Responsable técnico |
| Aprobada por | Responsable del proyecto |
| Fecha de aprobación | 29-09-2026 |

!!! info "Qué va en este documento"

    **Qué debe pasar, y por qué.** Nada más.

    **Prueba de pertenencia:** si un cambio de tecnología lo invalidaría, no pertenece aquí — va a `plan.md`. No se nombran tablas, clases, endpoints ni librerías.

---

## 1. Objetivo

Que en **cada cierre** cada persona cobre **el escalón que alcanzó** con los FTD que ella y su red activaron, y que **lo que sobre pase al cierre siguiente**.

---

## 2. Contexto

Es la mitad que paga de la comisión afftrack ([`requirements/cm.md`](../../../requirements/cm.md) v0.22.0, §5.8); la configuración son `RF-CM-015` a `RF-CM-019`. El responsable del proyecto lo dijo con un ejemplo: «si en el mes vendí 55, y tengo dos comisiones, una de 50 y otra de 60, se multiplica 50 por el valor de la comisión y los 5 que sobraron se dejan para el siguiente corte».

**No tiene ruta ni lo lanza nadie**, como `RF-CM-013`: **va dentro del cierre** (`RF-CM-009`), después del barrido y antes de que los lotes pasen a pendiente (`RN-CM-043`). Por eso lo que paga **entra en el lote que ese cierre cierra**, junto a lo devengado por venta, y se paga con él.

### 2.1 Cómo se hace la cuenta

Para cada persona y cada producto FTD:

| Paso | Qué |
|---|---|
| **Remanente** | Lo que le sobró en su liquidación anterior de ese producto, o cero |
| **Nuevos** | Los FTD **activados antes del cierre** que aún no se le habían contado: los que vendió y los de **toda su red**, cada uno con la cadena **del día en que se activó** (`RN-CM-040`, `RN-CM-042`) |
| **Disponibles** | Remanente + nuevos |
| **Escala** | La suya si tiene al menos un escalón vigente el día del cierre; si no, la de su rol vendedor (`RN-CM-039`) |
| **Escalón** | El de **mayor límite que no pase de los disponibles** |
| **Pago** | `límite × valor` de ese escalón, **una vez** (`RN-CM-041`) |
| **Remanente nuevo** | Disponibles − límite; o todos los disponibles si no alcanzó ninguno |

| Disponibles | Escalones | Se paga | Remanente |
|---|---|---|---|
| 55 | 50, 60 | 50 × valor del de 50 | 5 |
| 130 | 50, 60 | 60 × valor del de 60 | 70 |
| 40 | 50, 60 | nada | 40 |
| 12 | ninguno | nada | 12 |

### 2.2 Lo que este requerimiento decide

| Decisión | Qué se decidió |
|---|---|
| **Cada FTD se cuenta una vez por persona** | Al vendedor y a cada superior, pero nunca dos veces al mismo: un cierre relanzado no vuelve a contar nada |
| **Queda escrita cada liquidación** | También la que no pagó: su remanente es lo que la siguiente necesita. **No se escribe** para quien no tenía nada que liquidar |
| **La fila del lote dice de qué clase es** | `POR_AFFTRACK`, con cuántos FTD pagó, a qué valor y de qué escalón (`RN-CM-044`) |
| **O todo o nada** | Si la liquidación falla, el cierre no cierra: ni pago, ni conteo, ni remanente a medias |

---

## 3. Actores

| Actor | Rol en esta funcionalidad |
|---|---|
| El cierre (`RF-CM-009`) | Lo invoca, programado o a mano |

---

## 4. Alcance

### 4.1 Incluye

- Contar los FTD nuevos de cada persona y de su red, y anotar a quién se le contó cada uno.
- Resolver la escala de cada persona el día del cierre.
- Pagar el escalón alcanzado en el lote abierto de la persona, en la moneda del producto.
- Escribir la liquidación de cada persona y producto, con su remanente.

### 4.2 No incluye

- **Configurar escalones**: `RF-CM-015` a `RF-CM-019`.
- **Consultar las liquidaciones**: `RF-CM-021`.
- **Recalcular un cierre pasado** (`RN-CM-029`).
- **Contar FTD que llegan de fuera del sistema** (`cm.md` §1.3).

---

## 5. Reglas de negocio aplicables

| Regla | Cómo aplica |
|---|---|
| `RN-CM-036` | Qué línea es un FTD, y su momento |
| `RN-CM-039` | Qué escala rige: la de la persona sustituye la del rol; el día del cierre |
| `RN-CM-040` | Un FTD cuenta en el primer cierre tras activarse, una vez por persona |
| `RN-CM-041` | El mayor límite alcanzado, una vez; el resto, remanente; sin escala, se acumula |
| `RN-CM-042` | Cuentan el vendedor y toda su cadena, la del día de la activación |
| `RN-CM-043` | Dentro del cierre, en su transacción, antes del paso a pendiente |
| `RN-CM-044` | La comisión generada es `POR_AFFTRACK` |
| `RN-CM-033` | El pago entra en el lote abierto de la persona y moneda, o lo abre |

---

## 6. Datos

### 6.1 Entrada

**Ninguna**: la da el cierre —su instante—.

### 6.2 Salida

**Ninguna hacia fuera.** Deja escritas las liquidaciones, los FTD contados y las comisiones `POR_AFFTRACK`; se consultan por `RF-CM-021` y por los lotes (`RF-CM-010`, `RF-CM-012`).

---

## 7. Precondiciones y postcondiciones

| Tipo | Condición |
|---|---|
| Precondición | Hay un cierre en curso, con el barrido hecho |
| Postcondición | Todo FTD activado antes del instante del corte y con vendedor está contado a toda su cadena |
| Postcondición | Cada persona con algo que liquidar tiene su liquidación de este cierre, y su remanente es el que dice la cuenta |
| Postcondición | Lo pagado está en un lote abierto que el cierre pasa a pendiente a continuación |

---

## 8. Flujo principal

1. El cierre fija **el instante del corte**.
2. Se buscan los FTD activados antes de ese instante que **aún no se contaron a quien los vendió**.
3. Para cada uno se reconstruye la cadena del día de su activación y se le cuenta a cada persona de ella.
4. Para cada persona y producto con FTD nuevos **o con remanente**: se hace la cuenta de §2.1.
5. Se escribe la liquidación, se anota a quién se contó cada FTD y, si alcanzó un escalón, se paga en su lote abierto.
6. El cierre continúa: pasa los lotes a pendiente, **con lo pagado dentro**.

---

## 9. Flujos alternativos

### FA-001 — No hay productos FTD, o no hay nada que liquidar

No se escribe nada y el cierre sigue.

### FA-002 — Un FTD activado sin vendedor

No cuenta a nadie. Cuando se le asigne vendedor, contará en el primer cierre posterior.

### FA-003 — Una persona con remanente y sin FTD nuevos

Se liquida igual: su remanente puede alcanzar un escalón nuevo o corregido.

### FA-004 — Una persona sin escala

Sus FTD se cuentan, no se paga nada y todo queda de remanente (`RN-CM-041`).

### FA-005 — Un escalón de valor cero alcanzado

Se paga cero: la fila existe en el lote con importe cero y **el límite se descuenta** del remanente. Es lo que el escalón declara (`RF-CM-015` §6.1).

### FA-006 — Un FTD que se activa mientras el cierre corre

Si su activación es **anterior al instante del corte**, cuenta en este; si es posterior, en el siguiente. **Nunca en los dos ni en ninguno.**

---

## 10. Excepciones

| ID | Situación | Resultado |
|---|---|---|
| `EX-001` | La liquidación falla | **El cierre entero se revierte** (`RF-CM-009` `EX-002`): los lotes siguen abiertos, no queda ni un conteo ni una liquidación, y el siguiente cierre lo hace todo |

**Es a propósito distinto del barrido** (`RF-CM-009` `FA-003`), donde una línea que falla no para el cierre. Allí cada línea es independiente; aquí el remanente de una persona **es la entrada del cierre siguiente**, y un cierre que siguiera con la liquidación a medias dejaría a alguien con FTD contados y sin liquidación que los recoja.

---

## 11. Validaciones

Ninguna: no hay entrada.

---

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-CM-241` | Con 55 FTD y escalones de 50 y 60, se paga **50 × el valor del de 50**, en una comisión `POR_AFFTRACK` del lote que el cierre cierra, y el remanente es **5** |
| `CA-CM-242` | En el cierre siguiente, sin FTD nuevos y con un escalón de 5, **el remanente de 5 lo alcanza** y se paga; el remanente queda en 0 |
| `CA-CM-243` | Con 130 y escalones de 50 y 60 se paga **una vez** el de 60 y quedan **70** |
| `CA-CM-244` | Con 40 y escalones de 50 y 60 no se paga nada, la liquidación queda escrita con los 40 de remanente y el lote no gana ninguna fila |
| `CA-CM-245` | Un FTD de un vendedor con dos superiores cuenta **a los tres**, cada uno contra su propia escala y con su propio remanente; el superior suma los suyos y los de toda su red |
| `CA-CM-246` | La cadena es la **del día de la activación**: un superior que asumió la red después no cuenta ese FTD |
| `CA-CM-247` | Con escala personal vigente el día del cierre, **se usa solo esa**, aunque la del rol tenga un escalón que la persona alcanzaría; vencida la personal, vuelve la del rol |
| `CA-CM-248` | Un FTD **no activado**, **sin vendedor**, de una venta **no confirmada**, o de un producto **que no es FTD**, no cuenta; uno activado **después del corte** cuenta en el siguiente |
| `CA-CM-249` | **Relanzar** un cierre —o un segundo cierre sin FTD nuevos— no vuelve a contar ningún FTD ya contado |
| `CA-CM-250` | Sin escala, los FTD se cuentan y **se acumulan**; al registrarse un escalón, el siguiente cierre los paga |
| `CA-CM-251` | La comisión `POR_AFFTRACK` lleva el valor por FTD, los FTD pagados, el escalón exacto y su escala, y **ninguna línea ni nivel**; su lote está en la moneda del producto y su total la incluye |
| `CA-CM-252` | Si la liquidación falla, **el cierre se revierte entero**: ningún lote cerrado, ninguna liquidación, ningún FTD contado |
| `CA-CM-253` | Una línea FTD **no devenga por venta** aunque haya una tasa sobre su producto registrada antes de `RN-CM-037`, y el barrido no la recoge |
| `CA-CM-254` | Una fila de comisión con línea y clase `POR_AFFTRACK`, o sin línea y clase `POR_VENTA`, **la rechaza el esquema** |

---

## 13. Casos límite

| Caso | Comportamiento |
|---|---|
| Una persona con escalones en dos productos FTD de monedas distintas | Dos liquidaciones y, si paga en las dos, dos lotes |
| El producto FTD se retira | Sus FTD ya activados siguen contando, y su remanente se sigue liquidando mientras haya escala viva |
| Una persona que es superior de sí misma por un ciclo en la estructura | La cadena lleva guarda de ciclo (`RF-CM-013`); cada persona cuenta el FTD una sola vez |
| Muchas personas y muchos FTD en un mismo cierre | La cuenta se hace por conjuntos, no persona a persona contra la base (ver `plan.md`) |

---

## 14. Preguntas abiertas

Ninguna. Los tres supuestos de `cm.md` §5.8 —la activación es la entrega, sin escala se acumula, el superior cuenta aunque su red sea de otro rol— quedan aplicados aquí.

---

## 15. Control de cambios

| Versión | Fecha | Cambio | Autor |
|---|---|---|---|
| 0.1.0 | 29-09-2026 | Primera versión, con la comisión afftrack ([`requirements/cm.md`](../../../requirements/cm.md) v0.22.0 §5.8). Criterios `CA-CM-241` a `CA-CM-254`. | Responsable del proyecto |
