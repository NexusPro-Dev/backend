# SPEC — `RF-IN-006` Consultar el resumen de líneas de venta

| Campo | Valor |
|---|---|
| Requerimiento | `RF-IN-006` |
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

Saber, sobre **todas** las líneas de venta de la plataforma, **cuántos productos se vendieron**, **cuántas ventas hubo** y **cuántas ventas y líneas siguen sin vendedor**: la vista de administración de lo que se vende y de lo que falta por atribuir.

---

## 2. Contexto

**Lo pidió el responsable del proyecto el 06-10-2026**: «el siguiente indicador será para las líneas de ventas: total productos vendidos, total de ventas, ventas sin vendedor». Tres decisiones suyas del mismo día lo definen:

| Pregunta | Decisión |
|---|---|
| ¿Qué es un producto vendido? | **Una unidad**: tres bots en una línea son tres productos vendidos |
| ¿Qué es «sin vendedor»? | **Las ventas y las líneas** que aún no tienen vendedor, con sus unidades y su importe |
| ¿Quién lo ve, y qué ve? | **Un indicador propio, sembrado solo para administración**; y **quien porte su permiso lo ve entero**, sin acotar por alcance |

### 2.1 Por qué este indicador no se acota por alcance

Todos los demás indicadores enseñan a cada uno lo que le toca (`RN-IN-002`). Este no, y es a propósito: **lo que no tiene vendedor no está en el alcance de ningún vendedor** (`RN-IN-003`), de modo que acotarlo dejaría la cifra en cero para todos menos para administración, y la cifra existe precisamente para que alguien la vea y la reduzca. Por eso **el permiso es la única puerta**: se siembra solo para administración, y si administración se lo da a otro rol, ese rol ve las cifras enteras. Es `RN-IN-011`.

### 2.2 Qué lo distingue del resumen de ventas

El resumen de ventas (`RF-IN-001`) responde «cuánto vendí yo o mi red»; este responde «cuánto se vendió en la plataforma, línea a línea, y cuánto queda sin atribuir». Comparten la forma de contar —por línea, por moneda, solo ventas, el periodo de `RN-IN-010`— y por eso **sus cifras por estado coinciden con las del resumen de ventas de administración**.

---

## 3. Actores

| Actor | Rol en esta funcionalidad |
|---|---|
| **Administración**, con el permiso | Ve las cifras de **toda** la plataforma |
| Cualquier rol al que administración le dé el permiso | Igual: **las cifras enteras** (`RN-IN-011`) |

---

## 4. Alcance

### 4.1 Incluye

- **Por estado de la venta** —confirmadas, pendientes, anuladas—: ventas, líneas, **unidades** e importe por moneda.
- **El total**: ventas, líneas y unidades sean cuales sean sus estados.
- **Lo sin vendedor**: ventas con alguna línea sin vendedor, esas líneas, sus unidades y su importe por moneda, **de las ventas no anuladas**.
- El periodo, la moneda y los tramos de `RN-IN-010`.

### 4.2 No incluye

- **Por producto** o **por vendedor**: son `RF-IN-003` y `RF-IN-004`.
- **Acotar a un vendedor o a una red** (§2.1).
- **Las gratuitas**, que ya da el resumen de ventas.

---

## 5. Reglas de negocio aplicables

| Regla | Cómo aplica |
|---|---|
| **`RN-IN-001`** | Permiso propio; ningún otro lo abre |
| **`RN-IN-011`** | Sin alcance: quien porta el permiso ve las cifras enteras (nace aquí) |
| `RN-IN-003` | Se cuenta por línea; las líneas sin vendedor existen y aquí se cuentan aparte |
| `RN-IN-004` | Un importe por moneda |
| `RN-IN-005` | Solo ventas, por cuándo ocurrieron |
| `RN-IN-007`, `RN-IN-010` | Días de Bogotá; sin fechas, todo; tramos opcionales |
| `RN-MV-034` | Una venta con varios vendedores posibles nace con líneas sin vendedor |

---

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción |
|---|---|---|
| Desde, hasta, tramo | No | Los de `RN-IN-010` |
| Moneda | No | Solo esa moneda; una inexistente da ceros |

**No hay filtro por vendedor**: el indicador no es de nadie (§2.1).

### 6.2 Salida

| Dato | Descripción |
|---|---|
| Periodo, tramo | Como en `RF-IN-001` |
| Total | Ventas, líneas y unidades de todos los estados |
| Confirmadas, pendientes, anuladas | Ventas, líneas, unidades e importe por moneda |
| Sin vendedor | Ventas con alguna línea sin vendedor, esas líneas, sus unidades y su importe por moneda; **sin las anuladas** |
| Tramos | Si se pidió tramo: los mismos bloques por tramo |

**Lo sin vendedor excluye lo anulado** porque la cifra dice lo que **falta por atribuir**, y una venta anulada no se atribuye: no comisiona ni se cobra.

---

## 7. Precondiciones y postcondiciones

| Tipo | Condición |
|---|---|
| Precondición | El actor está autenticado y tiene el permiso del resumen de líneas de venta |
| Postcondición | **Ninguna.** |

---

## 8. Flujo principal

1. El actor pide el resumen de líneas, con los filtros que quiera.
2. El sistema comprueba el permiso, fija y valida el periodo y el tramo.
3. Suma todas las líneas de venta del periodo por estado y moneda, y aparte las que no tienen vendedor.
4. Si se pidió tramo, lo mismo por tramo.
5. Devuelve el resumen.

---

## 9. Flujos alternativos

### FA-001 — No hay ventas en el periodo

Ceros, y los importes vacíos.

### FA-002 — Todas las líneas tienen vendedor

Lo sin vendedor en cero: es la situación que se busca.

---

## 10. Excepciones

| ID | Situación | Resultado |
|---|---|---|
| `EX-001` | Quien pregunta no tiene el permiso | Prohibido |

---

## 11. Validaciones

Las del periodo y el tramo de `RF-IN-001` §11 (`VAL-001`, `VAL-002`, `VAL-005`).

---

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-IN-059` | **Productos vendidos son unidades**: una línea de tres unidades suma tres |
| `CA-IN-060` | Por estado: ventas, líneas, unidades e importe por moneda; el **total** suma los tres estados |
| `CA-IN-061` | Las cifras por estado **coinciden con las del resumen de ventas** de administración para el mismo periodo y moneda |
| `CA-IN-062` | **Sin vendedor**: las ventas con alguna línea sin vendedor, esas líneas, sus unidades y su importe; una venta con una línea con vendedor y otra sin él cuenta **una** venta y **una** línea sin vendedor |
| `CA-IN-063` | Lo sin vendedor **excluye las anuladas** |
| `CA-IN-064` | **Sin alcance**: un vendedor con el permiso ve las mismas cifras que administración, incluido lo sin vendedor |
| `CA-IN-065` | El periodo, la moneda y los tramos de `RN-IN-010`: con tramo, los mismos bloques por tramo y su suma es el total |
| `CA-IN-066` | Sin el permiso, **prohibido**, también con los permisos de ventas o del listado de líneas de `MV`; sin token, `401`; el permiso se siembra **solo** a `SUPERADMIN` y `ADMIN` |

---

## 13. Casos límite

| Caso | Comportamiento |
|---|---|
| Una venta por validar a la que se asigna vendedor | Deja de contar como sin vendedor en la siguiente consulta |
| Un paquete | Una línea por producto, cada una con sus unidades |

---

## 14. Preguntas abiertas

Ninguna.

---

## 15. Control de cambios

| Versión | Fecha | Cambio | Autor |
|---|---|---|---|
| 0.1.0 | 06-10-2026 | Primera versión, a petición del responsable del proyecto: **unidades** como productos vendidos, **ventas y líneas** sin vendedor, y un indicador **propio, de administración y sin alcance** (`RN-IN-011`). Ocho criterios, `CA-IN-059` a `CA-IN-066`. | Responsable técnico |
