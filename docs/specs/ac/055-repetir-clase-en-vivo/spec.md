# SPEC — `RF-AC-055` Repetir una clase en vivo

| Campo | Valor |
|---|---|
| Requerimiento | `RF-AC-055` |
| Módulo | `AC` — Academia |
| Versión | 1.0.0 |
| Estado | **Aprobada** |
| Autor | Responsable técnico |
| Aprobada por | Responsable del proyecto |
| Fecha de aprobación | 13-10-2026 |

---

## 1. Objetivo

Que una clase en vivo se repita **cada día, cada semana o cada mes, a la misma hora, hasta una fecha**, sin programarla otra vez.

## 2. Contexto

El responsable del proyecto lo pidió el 13-10-2026 ([`requirements/ac.md`](../../../requirements/ac.md) §5.2.16): «solo quiero marcar que la reunión sea cada día de tal a tal hora, semanal o mensual». **Marcar**, y no fabricar una clase por fecha: la clase sigue siendo **una**, con **una reunión recurrente de Zoom con hora fija**, y el alumno **se registra una vez** y entra a todas las fechas con el mismo enlace personal (`RN-AC-027`). Las tres respuestas: termina con **una fecha hasta**; la semanal, **el día de la semana del inicio**; la mensual, **el mismo día del mes**.

**Sin ruta propia**: los campos entran en programar y corregir, y las lecturas los devuelven. Por eso no hay permiso nuevo ni `CA` de permisos.

## 3. Actores

| Actor | Permiso |
|---|---|
| Administrador | `live-sessions:create`, `live-sessions:update` |
| Instructor, sobre las clases de sus cursos | `live-sessions:create-own`, `live-sessions:update-own` |

## 4. Alcance

### 4.1 Incluye

- `recurrence` y `repeatUntil` al programar (`RF-AC-044`, `RF-AC-049`) y al corregir (`RF-AC-045`, `RF-AC-050`).
- La reunión de Zoom, recurrente con hora fija, con el número de fechas que resulta.
- `recurrence`, `repeatUntil`, `nextStartsAt` y `nextEndsAt` en el listado y el detalle (`RF-AC-042`, `RF-AC-043`, `RF-AC-048`) y en la vitrina (`RF-AC-053`).
- «Terminada» por la última fecha en todas las operaciones (`RN-AC-029`).

### 4.2 No incluye

- Anual, «cada X días», varios días de la semana o «el segundo martes» (§5.2.16).
- Cancelar o mover una fecha suelta: la serie se corrige o se cancela entera.

## 5. Reglas de negocio aplicables

| Regla | Cómo aplica |
|---|---|
| `RN-AC-026` | La reunión, ahora recurrente; Zoom primero |
| `RN-AC-029` | Terminada con la última fecha; inicio y fin como siempre |
| `RN-AC-031` | La repetición entera |

## 6. Datos

### 6.1 Entrada (programar y corregir)

| Campo | Obligatorio | Regla |
|---|---|---|
| `recurrence` | No | `DAILY`, `WEEKLY` o `MONTHLY`; ausente o nula, una sola fecha. **Al corregir**, ausente no cambia nada y `NONE` quita la repetición |
| `repeatUntil` | Con `recurrence` | Fecha `2026-12-31`, el último día de la serie, incluido, en `America/Bogota`. Sin repetición, no se admite |

### 6.2 Salida

| Campo | Dónde | Valor |
|---|---|---|
| `recurrence`, `repeatUntil` | Listado, detalle, vitrina | Los guardados; nulos sin repetición |
| `nextStartsAt`, `nextEndsAt` | Listado, detalle, vitrina | **La fecha en curso o la siguiente**; nulos si terminó o está cancelada |
| `startsAt`, `endsAt` | Listado, detalle, vitrina | **La primera fecha**, como siempre |
| `startsAt`, `endsAt` | Entrar (`RF-AC-054`) e iniciar (`RF-AC-047`, `RF-AC-052`) | **La fecha en curso o la siguiente**: es la que interesa a quien entra |
| `ended` | Listado, detalle | Terminó **la última fecha** |
| `inProgress` | Vitrina | **Alguna** fecha está en curso |

## 7. Precondiciones y postcondiciones

| | |
|---|---|
| Precondición | Las de programar y corregir |
| Postcondición | La fila con `recurrence`, `repeat_until` y `series_ends_at`; la reunión de Zoom recurrente con el mismo número de fechas |

## 8. Flujo principal

1. Se leen `recurrence` y `repeatUntil` con el resto de la petición.
2. Se calculan las fechas, de la primera hasta `repeatUntil`, y se comprueban (§11).
3. Se crea o corrige la reunión en Zoom —recurrente, con su tipo, su día y su número de fechas— y se guarda la clase con el fin de su última fecha.

## 9. Flujos alternativos

### FA-001 — Quitar la repetición

`recurrence: NONE` deja la clase con su primera fecha, y la reunión de Zoom pasa a no recurrente. Si esa fecha ya pasó, la clase queda terminada.

### FA-002 — Una serie ya empezada

La primera fecha pasó y la última no: la clase **no ha terminado** —se corrige, se cancela, se inicia y se entra—. Corregir `repeatUntil` no exige un inicio nuevo.

## 10. Excepciones

Las de programar y corregir; ninguna nueva.

## 11. Validaciones

| Código | Campo | Regla |
|---|---|---|
| `VAL-002` | `recurrence` | Un valor que no es `DAILY`, `WEEKLY`, `MONTHLY` —ni `NONE` al corregir—: `400` |
| `VAL-002` | `repeatUntil` | Ausente con repetición, o fuera de forma: `400` |
| `VAL-003` | `repeatUntil` | Sin repetición; o da **menos de 2 fechas** o **más de 60**; o **la última ya terminó**: `400` |
| `VAL-003` | `startsAt` | Con `MONTHLY`, **un día del mes después del 28**: `400` |

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-AC-306` | Programar con `recurrence` y `repeatUntil`: `201`, el detalle con `recurrence`, `repeatUntil` y la primera fecha en `nextStartsAt`; Zoom recibe **una** reunión **recurrente con hora fija**: diaria; semanal con **el día de la semana del inicio**; o mensual con **el día del mes del inicio**; y **el número de fechas** que va del inicio a `repeatUntil` |
| `CA-AC-307` | Sin `recurrence`, la clase es la de siempre —`recurrence` y `repeatUntil` nulos, Zoom recibe una reunión no recurrente—; `recurrence` desconocida, `repeatUntil` ausente o mal formada, `repeatUntil` sin repetición, menos de 2 o más de 60 fechas, o una mensual que empieza después del día 28: `400` y **Zoom no recibe nada** |
| `CA-AC-308` | Corregir `repeatUntil` o `recurrence` **corrige la reunión** con la repetición nueva; `NONE` la quita —reunión no recurrente, `repeatUntil` nulo—; se audita con el antes y el después |
| `CA-AC-309` | **Una serie empezada no ha terminado**: su primera fecha pasó y la última no; se corrige sin inicio nuevo, `ended` es falso, `nextStartsAt` es la fecha en curso o la siguiente, y **entrar** devuelve esa fecha en `startsAt` y `endsAt`; pasada la última, `ended` es verdadero y corregir responde `409` |
| `CA-AC-310` | **La vitrina** trae la serie hasta que termina su última fecha, con `inProgress` si **alguna** está en curso y `nextStartsAt`, **ordenada por la fecha siguiente** |
| `CA-AC-311` | **El listado**: `ended` mira la última fecha, y `from`/`to` traen una serie si alguna de sus fechas puede caer en el rango —empieza antes de `to` y termina después de `from`—; una clase sola, por su inicio, como siempre |

## 13. Casos límite

| Caso | Decisión |
|---|---|
| `repeatUntil` el mismo día del inicio | Una sola fecha: `400` (menos de 2) |
| Mensual que empieza el 28 | Se acepta: todos los meses tienen 28 |
| Semanal hasta un día que no es el de la semana | La última fecha es la última de ese día de la semana anterior o igual a `repeatUntil` |
| Corregir el inicio de una serie | Las fechas se recalculan desde el inicio nuevo, que no puede estar en el pasado |

## 14. Preguntas abiertas

Ninguna.

## 15. Control de cambios

| Versión | Fecha | Cambio | Responsable |
|---|---|---|---|
| 1.0.0 | 13-10-2026 | Nace con las clases que se repiten (`ac.md` v0.25.0 §5.2.16, `RN-AC-031`). | Responsable técnico |
