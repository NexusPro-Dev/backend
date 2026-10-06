# SPEC — `RF-IN-002` Consultar la evolución de las ventas

| Campo | Valor |
|---|---|
| Requerimiento | `RF-IN-002` |
| Módulo | `IN` — Indicadores |
| Versión | 0.2.0 |
| Estado | **Aprobada** |
| Autor | Responsable técnico |
| Aprobada por | Responsable del proyecto |
| Fecha de aprobación | 06-10-2026 |

!!! warning "Enmendado el 06-10-2026 — sin fechas, todo; y cada indicador se puede partir en tramos (RN-IN-010)"

    Decisión del responsable del proyecto, 06-10-2026: «los indicadores se recogen en su totalidad a no ser que se les envíe una fecha en los filtros», y «tener la capacidad de pedir los indicadores por meses, por días y por semanas, y adicionalmente un filtro de inicio y fin; si van vacíos se consulta todo». **Sin fechas, la serie empieza en el tramo de la primera venta del alcance y llega al de hoy**; sin ninguna venta, es un solo tramo, el de hoy. **Ya no hay tope**: el de 366 días se retira con `RN-IN-010`. El tramo por defecto sigue siendo el día. La serie sigue existiendo aunque el resumen pueda partirse en tramos: es la forma ligera —solo lo confirmado— para dibujar.

!!! info "Qué va en este documento"

    **Qué debe pasar, y por qué.** Nada más.

    **Prueba de pertenencia:** si un cambio de tecnología lo invalidaría, no pertenece aquí — va a `plan.md`. No se nombran tablas, clases, endpoints ni librerías.

---

## 1. Objetivo

Que quien tenga el permiso vea **cómo cambian las ventas confirmadas de su alcance a lo largo de un periodo**, día a día, semana a semana o mes a mes, para dibujarlas.

---

## 2. Contexto

**Es el resumen de `RF-IN-001` partido en tramos**, y hereda de él todo lo que decide qué se cuenta: por línea y no por venta, por moneda, el periodo en días de Bogotá con el último incluido, el tope de 366 días, el alcance y los ceros fuera de él ([`RF-IN-001` · `spec.md`](../001-resumen-de-ventas/spec.md) §2, §6.1). **Aquí no se repite; se aplica.** Lo que este requerimiento decide es solo **cómo se parte** el periodo y **qué pasa con los tramos vacíos**.

**Solo lo confirmado.** Una serie de pendientes no responde ninguna pregunta que el resumen no responda mejor —lo pendiente es un saldo de hoy, no una historia—, y mezclar las tres situaciones en cada tramo triplicaría la respuesta para dibujar una sola línea.

### 2.1 Los tramos vacíos aparecen, con cero

**Una serie con huecos miente al dibujarse**: la línea une el lunes con el jueves y parece que el martes y el miércoles se vendió algo intermedio, cuando no se vendió nada. **Cada tramo del periodo aparece**, aunque sea con ceros, y quien dibuja no tiene que rellenar nada.

### 2.2 Los tramos son del calendario

Un tramo **semanal** va de **lunes a domingo**, y uno **mensual** del uno al último día del mes, **en Bogotá**. **El primero y el último pueden quedar recortados** por el periodo: si se pide del miércoles 10 al martes 23 por semanas, el primer tramo es la semana que empieza el lunes 8, pero solo cuenta del 10 en adelante. Cada tramo dice **dónde empieza en el calendario**, para que el eje sea legible, y la respuesta dice el periodo efectivo, para que nadie lea el primer tramo como una semana entera.

---

## 3. Actores

Los de [`RF-IN-001`](../001-resumen-de-ventas/spec.md) §3, con el permiso de este indicador.

---

## 4. Alcance

### 4.1 Incluye

- Las ventas **confirmadas** del alcance en un periodo, partidas en tramos de **día**, **semana** o **mes**: por tramo, ventas, líneas, unidades e importe por moneda.
- **Todos los tramos**, también los vacíos.
- Acotar a una moneda y a un vendedor de mi alcance, como en `RF-IN-001`.

### 4.2 No incluye

- Pendientes y anuladas por tramo (§2).
- Tramos por hora, por trimestre o por año.
- Acumulados («lo vendido hasta este día»): se calculan sumando los tramos.
- Varias series a la vez —una por vendedor, una por producto—: son `RF-IN-003` y `RF-IN-004`, sin partir en el tiempo.

---

## 5. Reglas de negocio aplicables

Las de [`RF-IN-001`](../001-resumen-de-ventas/spec.md) §5, todas. **`RN-IN-007`** pesa más aquí: el corte de cada día, semana y mes es el de Bogotá.

---

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción |
|---|---|---|
| Desde, hasta, moneda, vendedor | No | Como en `RF-IN-001` §6.1. **Sin «desde», desde el tramo de la primera venta** del alcance (06-10-2026) |
| Tramo | No | Día, semana o mes. **Por defecto, día** |

~~**El tope de 366 días basta para cualquier tramo**~~ **(retirado el 06-10-2026, `RN-IN-010`)**: 366 días, 53 semanas o 13 meses son series que se dibujan sin problema.

### 6.2 Salida

| Dato | Descripción |
|---|---|
| Periodo | Como en `RF-IN-001`, y el tramo efectivo |
| Monedas | Las monedas que aparecen en **algún** tramo del periodo |
| Tramos | En orden cronológico, cada uno con el día en que empieza en el calendario, ventas, líneas, unidades e importe por cada moneda de la lista anterior |

**Cada tramo lleva un importe por cada moneda que aparece en el periodo, con cero donde no vendió en ella**, y no solo las que vendió ese tramo: así cada moneda es una serie completa que se dibuja sin huecos (§2.1).

---

## 7. Precondiciones y postcondiciones

| Tipo | Condición |
|---|---|
| Precondición | El actor está autenticado y tiene el permiso de la evolución de ventas |
| Postcondición | **Ninguna.** No se escribe ni se audita nada |

---

## 8. Flujo principal

1. El actor pide la evolución, con los filtros y el tramo que quiera.
2. El sistema comprueba el permiso, fija y valida el periodo y el tramo.
3. Resuelve el alcance, como en `RF-IN-001`.
4. Suma las líneas confirmadas del alcance por tramo de calendario de Bogotá.
5. Completa los tramos vacíos y las monedas que faltan en cada uno, con ceros.
6. Devuelve la serie con el periodo y el tramo efectivos.

---

## 9. Flujos alternativos

### FA-001 — No hay ventas en el periodo

Todos los tramos, con ceros, y la lista de monedas vacía.

### FA-002 — El vendedor pedido no está en mi alcance, o no existe

Todos los tramos con ceros, **sin consultar**, como `RF-IN-001` · `FA-002`.

### FA-003 — El periodo cabe en un solo tramo

Un tramo, recortado al periodo.

---

## 10. Excepciones

| ID | Situación | Resultado |
|---|---|---|
| `EX-001` | Quien pregunta no tiene el permiso de la evolución de ventas | Prohibido |

---

## 11. Validaciones

| ID | Validación |
|---|---|
| `VAL-001` a `VAL-004` | Las de `RF-IN-001` §11 |
| `VAL-005` | El tramo, si viene, es día, semana o mes |

Devueltas juntas.

---

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-IN-015` | La suma de los tramos coincide con lo **confirmado** del resumen de `RF-IN-001` para el mismo actor, periodo y filtros — ventas, líneas, unidades e importe por moneda |
| `CA-IN-016` | **Cada tramo del periodo aparece**, en orden, también los vacíos, con ceros |
| `CA-IN-017` | Cada tramo lleva un importe por **cada moneda que aparece en el periodo**, con cero donde no vendió en ella |
| `CA-IN-018` | Las semanas van de **lunes a domingo** y los meses del uno al último día, **en Bogotá**; el primero y el último se recortan al periodo y dicen dónde empiezan en el calendario |
| `CA-IN-019` | Una venta a las 20:00 de Bogotá cae en **su** día de Bogotá, no en el siguiente de UTC |
| `CA-IN-020` | Sin tramo, es **día**; ~~sin fechas, el mes en curso hasta hoy~~ — la segunda mitad la sustituye `CA-IN-055` (06-10-2026) |
| `CA-IN-021` | El alcance es el de `RF-IN-001`: un director ve la serie suya y de sus agentes; un vendedor fuera de su red da ceros y no un error |
| `CA-IN-022` | Un tramo desconocido, un rango invertido y más de 366 días son un error, juntos; sin el permiso, **prohibido**, y **el del resumen no lo abre** |
| `CA-IN-055` | **Sin fechas**, la serie empieza en el tramo de la **primera venta** del alcance y acaba en el de hoy; sin ninguna venta, es un tramo, el de hoy (06-10-2026) |
| `CA-IN-056` | **Sin tope**: una serie diaria de más de 366 días es válida (06-10-2026) |

**`CA-IN-015` es el que ata este indicador al anterior**: si las dos cifras no cuadran, uno de los dos cuenta mal, y la prueba no necesita saber cuál para fallar.

---

## 13. Casos límite

| Caso | Comportamiento |
|---|---|
| Un periodo que empieza un domingo, por semanas | El primer tramo es la semana del lunes anterior, recortada a ese domingo |
| Un periodo del 31 de enero al 1 de marzo, por meses | Tres tramos: enero (un día), febrero entero, marzo (un día) |
| El día del cambio de horario | Bogotá no lo tiene; si la zona configurada lo tuviera, el tramo dura lo que dure ese día en ella |
| Una moneda con ventas solo en un tramo | Aparece en todos, con cero en los demás |

---

## 14. Preguntas abiertas

Ninguna propia; la de `RF-IN-001` §14 —contar por confirmación— aplicaría aquí igual.

---

## 15. Control de cambios

| Versión | Fecha | Cambio | Autor |
|---|---|---|---|
| 0.1.0 | 06-10-2026 | Primera versión. Hereda de `RF-IN-001` qué se cuenta y decide **cómo se parte**: tramos de calendario de Bogotá —semana de lunes a domingo—, recortados al periodo, **todos presentes** y con **todas las monedas** del periodo en cada uno. Solo lo confirmado. Ocho criterios, `CA-IN-015` a `CA-IN-022`. | Responsable técnico |
| 0.2.0 | 06-10-2026 | **`RN-IN-010`**: sin fechas, desde la primera venta hasta hoy; sin tope. `CA-IN-055` y `CA-IN-056`. | Responsable técnico |
