# SPEC — `RF-MV-056` Consultar los movimientos de puntos

| Campo | Valor |
|---|---|
| Requerimiento | `RF-MV-056` |
| Módulo | `MV` — Movimientos |
| Versión | 0.2.0 |
| Estado | **Aprobada** |
| Autor | Responsable técnico |
| Aprobada por | Responsable del proyecto |
| Fecha de aprobación | 06-10-2026 |

!!! info "Qué va en este documento"

    **Qué debe pasar, y por qué.** Nada más.

    **Prueba de pertenencia:** si un cambio de tecnología lo invalidaría, no pertenece aquí — va a `plan.md`. No se nombran tablas, clases, endpoints ni librerías.

---

## 1. Objetivo

Que administración vea **todo lo que mueve puntos** de cualquier persona —**compras y ajustes**— en una sola tabla, con su **detalle** y **el comprobante** de cada ajuste.

---

## 2. Contexto

Administración tenía la tabla de ajustes (`RF-MV-053`) y ninguna de compras de puntos: para ver las compras tenía que ir a los pagos (`RF-MV-043`), que enseñan intentos de cobro y no compras. El responsable del proyecto pidió el 06-10-2026 agrupar las dos cosas ([`requirements/mv.md`](../../../requirements/mv.md) v0.88.0 §4.12). **Este requerimiento sustituye a `RF-MV-053`**, que se retira, y hereda todo lo que aquel decidió: quién lo hizo en la fila, sin documento de identidad, y los mismos filtros.

### 2.1 Lo que este requerimiento decide

| Decisión | Qué se decidió |
|---|---|
| **La fila del alcance propio** | La misma que `RF-MV-055`, más **quién hizo el ajuste** y la persona completa —nombre, usuario y correo— |
| **Sin documento de identidad** | Ni en la fila ni en la búsqueda, como en `RF-MV-053` |
| **El permiso del listado se renombra** | Quien veía los ajustes ve ahora también las compras: se conserva su asignación (`requirements/mv.md` §4.12) |
| **Detalle y descarga, con permiso propio** | Uno por consulta (`RN-SEG-014`) |

---

## 3. Actores

| Actor | Rol en esta funcionalidad |
|---|---|
| Quien tiene `movements:list-points-movements` | Consulta los movimientos de puntos de cualquier persona |
| Quien tiene `movements:read-points-movement` | Consulta el detalle de cualquiera |
| Quien tiene `movements:download-points-receipt` | Descarga el comprobante de cualquier ajuste |

---

## 4. Alcance

### 4.1 Incluye

- La lista paginada de las compras de puntos y los ajustes de todas las personas, los más recientes primero.
- Los filtros de `RF-MV-055`, más la persona, y una búsqueda que alcanza también el nombre, usuario o correo.
- El detalle de cualquiera, y descargar el comprobante de cualquier ajuste.

### 4.2 No incluye

- Confirmar o rechazar el pago de una compra: es `RF-MV-044` y `RF-MV-045`, sobre el pago.
- Ajustar ni adjuntar: `RF-MV-052` y `RF-MV-057`.
- Exportar.

---

## 5. Reglas de negocio aplicables

| Regla | Cómo aplica |
|---|---|
| `RN-MV-051` | Qué es una compra de puntos |
| `RN-MV-076` | Qué es un ajuste |
| `RN-MV-077` | El comprobante, y cómo se descarga |
| `RN-SEG-014` | Un permiso por operación |

---

## 6. Datos

### 6.1 Entrada

Los de `RF-MV-055` §6.1, más:

| Dato | Obligatorio | Descripción |
|---|---|---|
| Persona | No | Los de una persona |
| Búsqueda | No | Además del comprobante, el motivo y la referencia, **el nombre, usuario o correo** de la persona |

### 6.2 Salida

La fila, el detalle y la descarga de `RF-MV-055` §6.2, con **quién hizo el ajuste** —identificador y nombre completo; vacío en una compra y en los ajustes anteriores a que se guardara—. La persona sale con nombre completo, usuario y correo, **sin documento**.

---

## 7. Precondiciones y postcondiciones

| Tipo | Condición |
|---|---|
| Precondición | El actor tiene el permiso de la consulta |
| Postcondición | Nada cambia |

---

## 8. Flujo principal

1. El actor pide la página, con los filtros y el orden que quiera.
2. El sistema valida todo junto y la devuelve.
3. El actor pide el detalle de uno y, si es un ajuste con comprobante, lo descarga.

---

## 9. Flujos alternativos

Ninguno.

---

## 10. Excepciones

| ID | Situación | Resultado |
|---|---|---|
| `EX-001` | Página, tamaño, orden, tipo, estado, sentido o periodo inválidos | Rechazo, con todos los problemas juntos |
| `EX-002` | El movimiento no existe o no es de puntos | No encontrado |
| `EX-003` | El ajuste no tiene comprobante, o el movimiento es una compra | No encontrado |
| `EX-004` | Sin el permiso de la consulta | Prohibido |

---

## 11. Validaciones

Las de `RF-MV-055` §11.

---

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-MV-674` | La lista trae **las compras y los ajustes de todas las personas**, mezclados, los más recientes primero, con la persona y, en el ajuste, **quién lo hizo** |
| `CA-MV-675` | Persona, tipo, estado, moneda y periodo filtran, y se combinan |
| `CA-MV-676` | `SUMA` devuelve compras y ajustes positivos; `RESTA`, solo ajustes negativos |
| `CA-MV-677` | La búsqueda encuentra por comprobante, motivo, referencia, nombre, usuario y correo, sin distinguir acentos ni mayúsculas |
| `CA-MV-678` | Se ordena por fecha, puntos o comprobante, en los dos sentidos |
| `CA-MV-679` | Los parámetros inválidos responden `400` con todos los problemas juntos |
| `CA-MV-680` | **Ni la fila ni la búsqueda usan el documento** de la persona |
| `CA-MV-681` | El detalle de cualquier compra o ajuste trae lo de `RF-MV-055` y quién hizo el ajuste; el de un movimiento que no es de puntos, o inexistente, `404` |
| `CA-MV-682` | La descarga devuelve exactamente el archivo subido, con su tipo, como adjunto; sin comprobante o sobre una compra, `404` |
| `CA-MV-683` | **Un bono, una venta o un retiro no aparecen** en la lista |
| `CA-MV-699` | **Los gastos de todas las personas** salen como `GASTO_PUNTOS`, sin quién hizo el ajuste, y la búsqueda por la persona los alcanza; su detalle trae las líneas |
| `CA-MV-684` | Sin el permiso de cada consulta responde prohibido; sin autenticar, `401`. **La ruta de `RF-MV-053` ya no existe** |

---

## 13. Casos límite

| Caso | Comportamiento |
|---|---|
| Un ajuste anterior a que se guardara quién lo hizo | Sale con quién lo hizo vacío |
| Un filtro sin coincidencias | Página vacía, no un error |

---

## 14. Preguntas abiertas

Ninguna.

---

## 15. Control de cambios

| Versión | Fecha | Cambio | Autor |
|---|---|---|---|
| 0.1.0 | 06-10-2026 | Primera versión ([`requirements/mv.md`](../../../requirements/mv.md) v0.88.0 §4.12, `RN-MV-077`): compras y ajustes de todas las personas en una tabla, con detalle y comprobante; sustituye `RF-MV-053`. Criterios `CA-MV-674` a `CA-MV-684`. | Responsable del proyecto |
| 0.2.0 | 06-10-2026 | **Los gastos también** (`RF-MV-055` v0.2.0, [`requirements/mv.md`](../../../requirements/mv.md) v0.89.0 §4.12). Criterio `CA-MV-699`. | Responsable del proyecto |
