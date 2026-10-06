# SPEC — `RF-IN-003` Consultar las ventas por producto

| Campo | Valor |
|---|---|
| Requerimiento | `RF-IN-003` |
| Módulo | `IN` — Indicadores |
| Versión | 0.1.0 |
| Estado | **Aprobada** |
| Autor | Responsable técnico |
| Aprobada por | Responsable del proyecto |
| Fecha de aprobación | 06-10-2026 |

!!! info "Qué va en este documento"

    **Qué debe pasar, y por qué.** Nada más.

    **Prueba de pertenencia:** si un cambio de tecnología lo invalidaría, no pertenece aquí — va a `plan.md`. No se nombran tablas, clases, endpoints ni librerías.

---

## 1. Objetivo

Que quien tenga el permiso sepa **qué productos se venden más en su alcance** en un periodo, de más a menos.

---

## 2. Contexto

**Es lo confirmado de `RF-IN-001` agrupado por producto**, y hereda de él qué se cuenta, el periodo, el alcance y los ceros fuera de él ([`RF-IN-001` · `spec.md`](../001-resumen-de-ventas/spec.md) §2, §6.1). Lo que este requerimiento decide es **qué es «el producto» de una línea**, **cómo se ordena** cuando hay varias monedas y **cuántas filas** se devuelven.

### 2.1 El producto es el de la línea, con el nombre con que se vendió

Una venta **congela** el nombre del producto en su línea (`RN-MV-002`): si después se renombra o se retira, lo vendido sigue diciendo lo que se vendió. Aquí se agrupa por **el producto** —su identidad, no su nombre— y se muestra **el nombre de su venta más reciente del periodo**. Así un producto renombrado a mitad de mes sale **una vez** y con el nombre que tenía al final, y uno retirado sigue apareciendo con lo que vendió.

**Un paquete no es un producto aquí.** Un paquete se vende como una línea por cada producto que lleva (`RN-MV-028`), de modo que sus productos suman cada uno lo suyo. «Qué paquetes se venden más» es otra pregunta, que no se ha pedido.

### 2.2 Cómo se ordena con varias monedas

**«De más a menos» necesita una sola magnitud**, y los importes en monedas distintas no se comparan (`RN-IN-004`). Por eso:

- **Con una moneda elegida**, se ordena por **importe** en esa moneda.
- **Sin moneda**, se ordena por **unidades** vendidas, que no tienen moneda.

En los dos casos el desempate es por número de ventas y luego por nombre, para que el orden sea estable.

---

## 3. Actores

Los de [`RF-IN-001`](../001-resumen-de-ventas/spec.md) §3, con el permiso de este indicador.

---

## 4. Alcance

### 4.1 Incluye

- Por producto, en el periodo y el alcance: **ventas** en que aparece, **líneas**, **unidades** e **importe por moneda**, solo de lo confirmado.
- Un **límite** de filas, y cuántos productos distintos hubo en total, para saber si la lista está cortada.
- Acotar a una moneda y a un vendedor de mi alcance.

### 4.2 No incluye

- Pendientes y anuladas por producto.
- Paquetes como tales (§2.1).
- Paginar: es un ranking, no un catálogo; quien quiera más filas sube el límite hasta su tope.
- Productos sin ventas en el periodo.
- La serie temporal de un producto: se obtiene de `RF-IN-002` cuando acepte filtrar por producto, que no se ha pedido.

---

## 5. Reglas de negocio aplicables

Las de [`RF-IN-001`](../001-resumen-de-ventas/spec.md) §5, y además:

| Regla | Cómo aplica |
|---|---|
| `RN-MV-002` | El nombre del producto se congela en la línea (§2.1) |
| `RN-MV-028` | Un paquete aporta una línea por producto (§2.1) |

---

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción |
|---|---|---|
| Desde, hasta, moneda, vendedor | No | Como en `RF-IN-001` §6.1 |
| Límite | No | Cuántos productos devolver. Por defecto **10**; como mucho **50** |

### 6.2 Salida

| Dato | Descripción |
|---|---|
| Periodo | Como en `RF-IN-001` |
| Orden | Si se ordenó por importe o por unidades (§2.2) |
| Productos | Cada uno con su identidad y nombre (§2.1), ventas, líneas, unidades e importe por moneda; en orden |
| Total de productos | Cuántos productos distintos tuvieron ventas, aunque no quepan en el límite |

---

## 7. Precondiciones y postcondiciones

| Tipo | Condición |
|---|---|
| Precondición | El actor está autenticado y tiene el permiso de las ventas por producto |
| Postcondición | **Ninguna.** |

---

## 8. Flujo principal

1. El actor pide las ventas por producto, con los filtros y el límite que quiera.
2. El sistema comprueba el permiso, fija y valida el periodo y el límite.
3. Resuelve el alcance, como en `RF-IN-001`.
4. Agrupa las líneas confirmadas del alcance por producto y las ordena según §2.2.
5. Devuelve las primeras hasta el límite, con el total de productos.

---

## 9. Flujos alternativos

### FA-001 — No hay ventas en el periodo

Lista vacía, total cero.

### FA-002 — El vendedor pedido no está en mi alcance, o no existe

Lista vacía y total cero, **sin consultar**.

---

## 10. Excepciones

| ID | Situación | Resultado |
|---|---|---|
| `EX-001` | Quien pregunta no tiene el permiso de las ventas por producto | Prohibido |

---

## 11. Validaciones

| ID | Validación |
|---|---|
| `VAL-001` a `VAL-004` | Las de `RF-IN-001` §11 |
| `VAL-005` | El límite, si viene, está entre 1 y 50 |

Devueltas juntas.

---

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-IN-023` | La suma de las filas —sin límite— coincide con lo **confirmado** del resumen de `RF-IN-001` en líneas, unidades e importe por moneda; **las ventas no**: una venta de dos productos cuenta en las dos filas |
| `CA-IN-024` | Sin moneda, el orden es por **unidades**; con moneda, por **importe** en ella; el desempate, por ventas y por nombre |
| `CA-IN-025` | Un producto **renombrado** dentro del periodo sale **una vez**, con el nombre de su venta más reciente; uno **retirado** sigue saliendo |
| `CA-IN-026` | Un **paquete** suma en cada uno de sus productos, y el paquete no aparece como fila |
| `CA-IN-027` | El **límite** corta la lista y el **total** dice cuántos productos hubo; por defecto 10 |
| `CA-IN-028` | El alcance es el de `RF-IN-001`: una venta con líneas de dos ramas suma en cada producto **solo** las líneas de mi rama; un vendedor fuera de mi red da lista vacía |
| `CA-IN-029` | Un límite fuera de 1..50, un rango invertido y más de 366 días son un error, juntos; sin el permiso, **prohibido**, y otro `indicators:` no lo abre |

---

## 13. Casos límite

| Caso | Comportamiento |
|---|---|
| Dos productos con las mismas unidades y ventas | Por nombre |
| Un producto vendido en dos monedas | Una fila, dos importes |
| Una línea de varias unidades | Una línea, varias unidades |
| Dos productos distintos con el mismo nombre | Dos filas: se agrupa por identidad |

---

## 14. Preguntas abiertas

Ninguna.

---

## 15. Control de cambios

| Versión | Fecha | Cambio | Autor |
|---|---|---|---|
| 0.1.0 | 06-10-2026 | Primera versión. Hereda de `RF-IN-001` qué se cuenta y decide: **el producto es el de la línea**, con el nombre de su venta más reciente; **los paquetes suman en sus productos**; el orden es **por importe con moneda y por unidades sin ella**; límite 10, tope 50, con el total. Siete criterios, `CA-IN-023` a `CA-IN-029`. | Responsable técnico |
