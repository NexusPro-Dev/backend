# SPEC — `RF-MV-007` Consultar el detalle de un movimiento, con su comprobante

| Campo | Valor |
|---|---|
| Requerimiento | `RF-MV-007` |
| Módulo | `MV` — Movimientos |
| Versión | 0.1.0 |
| Estado | **Aprobada** |
| Autor | Responsable técnico |
| Aprobada por | Responsable del proyecto |
| Fecha de aprobación | 30-09-2026 |

!!! info "Qué va en este documento"

    **Qué debe pasar, y por qué.** Nada más.

    **Prueba de pertenencia:** si un cambio de tecnología lo invalidaría, no pertenece aquí — va a `plan.md`. No se nombran tablas, clases, endpoints ni librerías.

---

## 1. Objetivo

Que quien administra el libro pueda **abrir cualquier movimiento** —una venta, un retiro, un bono— y ver **su comprobante entero**: quién es el sujeto, qué se vendió en cada línea y a quién se le acredita, cuánto se cobró, con qué pagos y en qué estado está.

## 2. Contexto

**El requerimiento estaba declarado desde el 02-09-2026** en `requirements/mv.md` §4.1, sin especificar, y es el último de `MV` que no tenía tripleta. **Se especifica el 30-09-2026 porque el listado de administración no lleva a ninguna parte**: `RF-MV-006` devuelve el identificador de cada movimiento y su `spec.md` §14 lo dejó escrito —«este listado devuelve el identificador de cada movimiento y hoy **no hay a dónde llevarlo** salvo que sea propio»—. Quien administra ve la fila de una venta ajena y no puede abrirla.

**«El comprobante» no es un archivo adjunto.** Es el documento interno que este módulo emite por cada movimiento, con su código legible y **sin valor fiscal** (`requirements/mv.md` §1.1 y §1.5): qué se compró, por cuánto y en qué estado quedó. El detalle **es** el comprobante. El soporte bancario que alguien mira antes de confirmar un pago (`RF-MV-003`) no se guarda en el sistema y **no forma parte de este requerimiento** —ver §4.2—.

**Es el detalle propio sin el alcance.** `RF-MV-008` ya publica el detalle de un movimiento en el que la persona participó, y ese detalle ya es el comprobante completo: las líneas, los pagos (`RN-MV-047`), el estado del tipo. Este requerimiento no inventa una forma nueva: da **la misma** a quien tiene autoridad sobre todo el libro, del mismo modo que `RF-MV-006` es el listado de `RF-MV-008` sin el alcance.

## 3. Actores

| Actor | Qué hace |
|---|---|
| Quien administra el libro | Abre cualquier movimiento por su identificador. Lo habilita el permiso de lectura de todos los movimientos, **el mismo** que habilita el listado de `RF-MV-006` |

## 4. Alcance

### 4.1 Incluye

- El detalle de **cualquier movimiento**, de cualquier tipo y en cualquier estado, sin importar quién sea su sujeto ni quién lo vendió.
- **La misma forma** que el detalle propio (`RF-MV-008` §6.3) y que la respuesta de registrar una venta.

### 4.2 No incluye

- **Adjuntar, guardar o exigir un soporte de pago.** Si el negocio lo quiere, es un requerimiento aparte, y `RF-MV-003` §14 ya dejó escrito que habría que decidir si confirmar lo exige.
- **La factura fiscal.** El comprobante no es un documento DIAN (`requirements/mv.md` §1.5).
- **Un detalle acotado por estructura** —el de un vendedor sobre las ventas de su equipo—. `RF-MV-015` es solo listado y el detalle propio no se acotó (`RF-MV-008` §2.1); si hace falta, es otra decisión de alcance (**D-22**).
- **Escribir nada.** Es una consulta.

## 5. Reglas de negocio aplicables

| Regla | Cómo se aplica aquí |
|---|---|
| `RN-MV-013` | El total que se muestra es **el congelado al registrar**, no uno recalculado al leer: es el número del comprobante |
| `RN-MV-002` | Nombre, descripción, precio y vigencia de cada línea son **los copiados al vender**, no los que el catálogo tiene hoy |
| `RN-MV-047` | El detalle publica **los pagos** del movimiento, cada uno con su método y su estado |
| `RN-SEG-014` | Un permiso gobierna una operación: el detalle **reutiliza** el permiso del listado porque la autoridad es la misma —ver §14— |

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Regla |
|---|---|---|
| Identificador del movimiento | Sí | Bien formado; si no, rechazo de validación |

### 6.2 Salida

**La misma que el detalle propio** (`RF-MV-008` §6.3): el movimiento con su código, su tipo, su estado y el estado de su tipo, su sujeto, su moneda, sus pagos, sus totales y **sus líneas**, cada una con lo que se vendió, a quién se le acredita y su estado de entrega. Un movimiento sin líneas —un retiro, un bono— sale con la lista **vacía**, no ausente.

## 7. Precondiciones y postcondiciones

| | |
|---|---|
| Precondición | El actor tiene el permiso de lectura de todos los movimientos |
| Postcondición | Nada cambia |

## 8. Flujo principal

1. El actor pide el detalle de un movimiento por su identificador.
2. El sistema lo busca **sin ningún predicado sobre el actor**.
3. El sistema devuelve el comprobante.

## 9. Flujos alternativos

### FA-001 — El movimiento no es una venta

Un retiro o un bono se abren igual: con su tipo, su estado, sus pagos si los tiene y **sin líneas**.

### FA-002 — El actor es el sujeto del movimiento

No cambia nada: responde lo mismo que respondería a cualquier otro administrador. Quien quiera su vista propia tiene `RF-MV-008`.

## 10. Excepciones

| Código | Cuándo | Respuesta |
|---|---|---|
| `EX-001` | No existe ningún movimiento con ese identificador | No encontrado |

**No hay un «existe pero no es suyo»**, porque aquí no hay alcance: quien tiene el permiso ve todos. Es la diferencia con `RF-MV-008`, donde el ajeno responde como inexistente.

## 11. Validaciones

| Código | Dato | Regla |
|---|---|---|
| `VAL-001` | Identificador | Bien formado |

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-MV-287` | El detalle de una venta **ajena** —ni su sujeto ni su vendedor es el actor— se devuelve completo: código, tipo, estado, estado del tipo, sujeto, moneda, totales, pagos y líneas |
| `CA-MV-288` | Cada línea trae **lo copiado al vender**: si el catálogo cambió el nombre o el precio después, el detalle sigue diciendo lo que se vendió |
| `CA-MV-289` | El detalle de un **retiro** se devuelve con su tipo, su estado y su pago, y **con la lista de líneas vacía** |
| `CA-MV-290` | Para un movimiento propio del actor, la respuesta es **la misma** que la de su detalle propio |
| `CA-MV-291` | Un identificador que no existe responde **no encontrado**; uno malformado, **rechazo de validación** |
| `CA-MV-292` | Sin el permiso de lectura de todos los movimientos, **rechazo por permiso**, aunque el movimiento sea propio; sin sesión, **no autenticado** |

## 13. Casos límite

- **Una venta anulada o con el pago rechazado** se abre igual, con su estado y el motivo que conste.
- **Una línea retenida** —un upgrade que habría bajado de nivel— sale con su estado de entrega y su nota.
- **Un producto borrado del catálogo después de vender** no afecta al detalle: la línea lleva su copia.

## 14. Preguntas abiertas

Ninguna abierta.

| # | Pregunta | Resolución |
|---|---|---|
| 1 | ¿«Con su comprobante» pide adjuntar un soporte de pago? | **No.** El comprobante es el documento interno del movimiento (`requirements/mv.md` §1.1, §1.5), y el detalle lo es. El soporte bancario no se guarda hoy y sería otro requerimiento (§4.2) |
| 2 | ¿Un permiso propio para el detalle, por `RN-SEG-014`? | **No: `movements:read`, el del listado de `RF-MV-006`**, que es el que `requirements/mv.md` §6 le declara desde el 02-09-2026 —«consultar todos los movimientos y el detalle de cualquiera»—. `RN-SEG-014` separa operaciones que un rol podría recibir por separado, y **ver la lista entera del libro sin poder abrir ninguna fila** no es un reparto que tenga sentido: el listado ya enseña de cada movimiento casi todo lo que el detalle trae. Se parte en dos el día que alguien lo pida |
| 3 | ¿El detalle de un movimiento que no es venta? | **Sí.** El listado de `RF-MV-006` enseña retiros y bonos, y su detalle es a donde lleva cada fila |

## 15. Control de cambios

| Versión | Fecha | Cambio | Autor |
|---|---|---|---|
| 0.1.0 | 30-09-2026 | Primera versión. Declarado desde el 02-09-2026 en `requirements/mv.md` §4.1 y sin especificar; es el detalle de `RF-MV-006` y el último requerimiento de `MV` sin tripleta | Responsable técnico |
