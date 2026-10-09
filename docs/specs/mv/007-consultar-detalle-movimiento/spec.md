# SPEC — `RF-MV-007` Consultar el detalle de un movimiento, con su comprobante

| Campo | Valor |
|---|---|
| Requerimiento | `RF-MV-007` |
| Módulo | `MV` — Movimientos |
| Versión | 0.4.0 |
| Estado | **Aprobada** |
| Autor | Responsable técnico |
| Aprobada por | Responsable del proyecto |
| Fecha de aprobación | 30-09-2026 |
| Enmendada el | 30-09-2026 — **permiso propio** (`RN-SEG-014`) y lo que no tiene forma de identificador responde **no encontrado** (§3, §10, §11, §12, §14). Ver §15 |
| Enmendada el | 01-10-2026 — **el detalle de un retiro publica su destino** (`RN-MV-056`): §6.2, §12. Ver §15 |
| Enmendada el | 09-10-2026 — **cada línea dice en qué oficina se vendió** (`RN-MV-078`): §6.2, §12, §13. Ver §15 |

!!! warning "Enmendado el 09-10-2026 — cada línea dice en qué oficina se vendió"

    `RN-MV-078` ([`requirements/mv.md`](../../../requirements/mv.md) v0.97.0 §4.13), a petición del responsable del proyecto: saber **en qué oficina se hizo cada venta**, y que un traslado no se lleve lo vendido. La oficina es el **equipo del director de la cadena del vendedor en el instante de la venta**, y queda guardada **en cada línea**, junto a su vendedor. **El comprobante la enseña**: cada línea trae **su oficina** —identificador y nombre—, y la trae **presente y vacía** cuando no la tiene: una línea sin vendedor, la venta de un manager, un vendedor sin director con equipo, o una venta anterior a la oficina que administración aún no ha rellenado (`RF-MV-058`).

    **Es la guardada, no la de hoy**: si el vendedor o su director cambiaron de equipo después, el detalle sigue diciendo la oficina donde se vendió. Y como **toda respuesta que lleva líneas tiene la misma forma** —el detalle propio (`RF-MV-008`), registrar, confirmar, anular, rechazar un pago, volver a pagar y asignar vendedores—, todas ganan el dato a la vez: `CA-MV-290` —«lo mismo que el detalle propio»— se sigue cumpliendo. **Nada más cambia**: ni el permiso, ni la entrada, ni las excepciones. Un retiro o un bono siguen sin líneas, de modo que no tienen oficina que enseñar. `CA-MV-720`.

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
| Quien administra el libro | Abre cualquier movimiento por su identificador. Lo habilita **el permiso del detalle**, que porta todo rol con el del listado de `RF-MV-006` |

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
| `RN-SEG-014` | Un permiso gobierna una operación: el detalle tiene **el suyo**, aparte del del listado —ver §14— |

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Regla |
|---|---|---|
| Identificador del movimiento | Sí | Bien formado; si no, rechazo de validación |

### 6.2 Salida

**La misma que el detalle propio** (`RF-MV-008` §6.3): el movimiento con su código, su tipo, su estado y el estado de su tipo, su sujeto, su moneda, sus pagos, sus totales y **sus líneas**, cada una con lo que se vendió, a quién se le acredita y su estado de entrega. Un movimiento sin líneas —un retiro, un bono— sale con la lista **vacía**, no ausente.

**Desde el 01-10-2026, el detalle de un retiro trae su destino** (`RN-MV-056`): la copia que se escribió al pedirlo —entidad, tipo de cuenta, número y titular con su documento—, **no** los datos vivos de la cuenta. Es lo que lee quien lo aprueba (`RF-MV-020`) para saber a dónde enviar el dinero. **Un retiro pedido antes de ese día, y todo movimiento que no es un retiro, lo traen vacío**. Vale igual para el detalle propio (`RF-MV-008`), que es la misma respuesta.

**Desde el 09-10-2026, cada línea trae su oficina** (`RN-MV-078`): la **guardada** en la línea el día de la venta —identificador y nombre—, no la que tendría hoy su vendedor. **Presente y vacía** cuando la línea no la tiene. Vale igual para el detalle propio (`RF-MV-008`) y para toda respuesta que lleve líneas.

## 7. Precondiciones y postcondiciones

| | |
|---|---|
| Precondición | El actor tiene el permiso del detalle de movimientos |
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
| — | Lo pedido no tiene forma de identificador | No encontrado, **como cualquier ruta que no existe** —ver §11— |

**No hay un «existe pero no es suyo»**, porque aquí no hay alcance: quien tiene el permiso ve todos. Es la diferencia con `RF-MV-008`, donde el ajeno responde como inexistente.

## 11. Validaciones

| Código | Dato | Regla |
|---|---|---|
| — | Identificador | **Sin validación propia**: lo que no tiene forma de identificador no es esta operación y responde no encontrado. Así lo exige `CA-MV-140` de `RF-MV-008`, que promete no encontrado para la ruta que el listado propio abandonó el 22-09-2026 y que, sin esto, se leería como un identificador malformado |

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-MV-287` | El detalle de una venta **ajena** —ni su sujeto ni su vendedor es el actor— se devuelve completo: código, tipo, estado, estado del tipo, sujeto, moneda, totales, pagos y líneas |
| `CA-MV-288` | Cada línea trae **lo copiado al vender**: si el catálogo cambió el nombre o el precio después, el detalle sigue diciendo lo que se vendió |
| `CA-MV-289` | El detalle de un **retiro** se devuelve con su tipo, su estado y su pago, y **con la lista de líneas vacía** |
| `CA-MV-290` | Para un movimiento propio del actor, la respuesta es **la misma** que la de su detalle propio |
| `CA-MV-291` | Un identificador que no existe responde **no encontrado**, y lo que no tiene forma de identificador **también** |
| `CA-MV-292` | Sin el permiso del detalle, **rechazo por permiso**, aunque el movimiento sea propio **o el actor tenga el del listado**; sin sesión, **no autenticado** |
| `CA-MV-424` | **Desde el 01-10-2026.** El detalle de un retiro trae **su destino copiado**, y sigue diciendo lo mismo después de editar o dar de baja la cuenta; el detalle propio del mismo retiro trae el mismo destino |
| `CA-MV-425` | El detalle de una **venta**, y el de un retiro **sin copia**, traen el destino vacío |
| `CA-MV-720` | **Desde el 09-10-2026.** Cada línea del detalle trae **su oficina** —identificador y nombre—, **presente y vacía** cuando no la tiene; es la **guardada** en la línea: después de que el director del vendedor cambie de equipo, el detalle sigue diciendo la oficina de la venta. El detalle propio de la misma venta trae lo mismo (`CA-MV-290`) |

## 13. Casos límite

- **Una venta anulada o con el pago rechazado** se abre igual, con su estado y el motivo que conste.
- **Una línea retenida** —un upgrade que habría bajado de nivel— sale con su estado de entrega y su nota.
- **Un producto borrado del catálogo después de vender** no afecta al detalle: la línea lleva su copia.
- **Una oficina eliminada después de vender** (09-10-2026) se sigue nombrando en la línea: la eliminación de un equipo es lógica (`RN-SP-054`) y lo vendido no se reescribe. **Una oficina renombrada** sale con su nombre de hoy: lo guardado es **cuál** es, no cómo se llamaba.

## 14. Preguntas abiertas

Ninguna abierta.

| # | Pregunta | Resolución |
|---|---|---|
| 1 | ¿«Con su comprobante» pide adjuntar un soporte de pago? | **No.** El comprobante es el documento interno del movimiento (`requirements/mv.md` §1.1, §1.5), y el detalle lo es. El soporte bancario no se guarda hoy y sería otro requerimiento (§4.2) |
| 2 | ¿Un permiso propio para el detalle, por `RN-SEG-014`? | **Sí** (enmienda del 30-09-2026). La versión 0.1.0 decía que no —reutilizar `movements:read`, que `requirements/mv.md` §6 le declaraba desde el 02-09-2026—, con el argumento de que listar sin poder abrir no es un reparto con sentido. **No se sostiene**: `RN-SEG-014` no es un criterio que se pondere caso a caso, es la regla del catálogo desde el 19-09-2026 —separó el listado y el detalle de los `read` de ocho recursos— y la hace cumplir una prueba. Nace el permiso del detalle y **se reparte a todo rol que ya porte el del listado**, de modo que nadie que hoy lista el libro se queda sin abrir sus filas |
| 3 | ¿El detalle de un movimiento que no es venta? | **Sí.** El listado de `RF-MV-006` enseña retiros y bonos, y su detalle es a donde lleva cada fila |

## 15. Control de cambios

| Versión | Fecha | Cambio | Autor |
|---|---|---|---|
| 0.1.0 | 30-09-2026 | Primera versión. Declarado desde el 02-09-2026 en `requirements/mv.md` §4.1 y sin especificar; es el detalle de `RF-MV-006` y el último requerimiento de `MV` sin tripleta | Responsable técnico |
| 0.2.0 | 30-09-2026 | **Permiso propio, y lo que no tiene forma de identificador es no encontrado**, al construir. (1) La 0.1.0 reutilizaba el permiso del listado; `RN-SEG-014` no lo admite y la prueba que lo hace cumplir lo detectó: nace el del detalle, repartido a quien porta el del listado (§3, §12, §14). (2) Sin validación del identificador: la ruta de la operación habría capturado la que `RF-MV-008` retiró, y su `CA-MV-140` promete no encontrado (§10, §11, `CA-MV-291`) | Responsable técnico |
| 0.3.0 | 01-10-2026 | **El detalle de un retiro publica su destino** ([`requirements/mv.md`](../../../requirements/mv.md) v0.61.0 §4.5, `RN-MV-056`): la copia escrita al pedirlo, para que quien aprueba sepa a dónde pagar. Vacío en lo demás. Criterios `CA-MV-424` y `CA-MV-425`. | Responsable del proyecto |
| 0.4.0 | 09-10-2026 | **Cada línea dice en qué oficina se vendió** ([`requirements/mv.md`](../../../requirements/mv.md) v0.97.0 §4.13, `RN-MV-078`; Art. I.7 sobre un requerimiento construido), a petición del responsable del proyecto: la guardada en la línea, presente y vacía cuando no la hay, y no la de la estructura de hoy. Como toda respuesta con líneas tiene la misma forma, el detalle propio y las respuestas de las operaciones sobre la venta la ganan a la vez. Criterio `CA-MV-720`. | Responsable del proyecto |
