# SPEC — `RF-PM-005` Cambiar el estado de un producto

| Campo | Valor |
|---|---|
| Requerimiento | `RF-PM-005` |
| Módulo | `PM` — Productos y Mercadeo |
| Estado | **Aprobada** |
| Autor | Responsable técnico |
| Aprobada por | Responsable del proyecto |
| Fecha de aprobación | 26-08-2026 |

---

## 1. Objetivo

Decidir si un producto se ofrece, sin borrarlo.

## 2. Contexto

Es la operación del día a día del catálogo: se deja de vender algo por una temporada, se prepara una oferta nueva antes de publicarla, se retira un bot mientras se rehace. Sin ella la única forma de dejar de ofrecer un producto sería eliminarlo, y eliminar es definitivo.

**Desactivar no toca nada de lo ya vendido.** El producto sigue existiendo, sigue apareciendo en el catálogo administrativo y sigue pudiendo explicarse; lo único que cambia es que deja de ofrecerse (`RN-PM-009`).

**Reactivar vuelve a competir por el destino.** Un upgrade que se desactivó libera su destino, y en ese intervalo puede haberse publicado otro hacia el mismo nivel. Volver a activarlo exige comprobar `RN-PM-004` otra vez: es el momento en que dos precios simultáneos para lo mismo entrarían por la puerta de atrás.

## 3. Actores

| Actor | Rol en esta funcionalidad |
|---|---|
| Administrador | Publica o retira de la venta un producto |

## 4. Alcance

### 4.1 Incluye

- Activar un producto inactivo y desactivar uno activo.
- Comprobar, al activar un upgrade, que ningún otro upgrade activo apunta a su mismo destino.
- Comprobar, al activar, que el producto tiene descripción (`RN-PM-014`).
- Dejar constancia del cambio en la auditoría.

### 4.2 No incluye

- **Corregir los datos del producto**, que es `RF-PM-004`.
- **Retirarlo definitivamente**, que es `RF-PM-006`.
- **Exigir un motivo.** El Art. V.13 solo lo obliga en las eliminaciones, y `RF-PM-004` tampoco lo pide al corregir: pedirlo solo aquí dejaría la operación más repetida del catálogo como la única que interroga a quien la usa. Resuelto el 26-08-2026.
- **Programar la publicación para una fecha.** Un producto se publica cuando alguien lo publica; la vigencia con fechas es cosa de las promociones, que hoy están fuera de alcance.

## 5. Reglas de negocio aplicables

| ID | Regla | Origen |
|---|---|---|
| `RN-PM-004` | Un solo upgrade activo por destino | `requirements/pm.md` §5.1 |
| `RN-PM-009` | Solo se ofrece lo activo | `requirements/pm.md` §5.1 |
| `RN-PM-014` | No se publica lo que no se explica | `requirements/pm.md` §5.1 |

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción | Restricción de negocio |
|---|---|---|---|
| Identificador del producto | Sí | Cuál cambia de estado | Debe existir y no estar retirado |
| Estado nuevo | Sí | Activo o inactivo | Uno de los dos valores admitidos |

### 6.2 Salida

| Dato | Descripción |
|---|---|
| Producto | El producto con su estado nuevo |

## 7. Precondiciones y postcondiciones

**Precondiciones**

- El actor está autenticado y posee el permiso de modificación de productos.
- El producto existe y no está retirado.

**Postcondiciones**

- El producto queda en el estado pedido.
- Si quedó activo y es un upgrade, es el **único** upgrade activo hacia su destino.
- La auditoría de cambios contiene el cambio de estado con su valor anterior y el nuevo.

## 8. Flujo principal

1. El actor envía el identificador y el estado que quiere.
2. El sistema comprueba que el producto existe y no está retirado.
3. Si el estado pedido es «activo», el sistema comprueba que el producto tiene descripción.
4. Si además es un upgrade, el sistema comprueba que ningún otro upgrade activo apunta a su destino.
5. El sistema aplica el estado y emite el evento de auditoría.
6. El sistema devuelve el producto.

## 9. Flujos alternativos

### FA-001 — El producto ya está en ese estado

**Cuándo ocurre:** se pide activar uno activo, o desactivar uno inactivo.

1. El sistema responde con normalidad y **no emite evento de auditoría**: no hubo cambio.
2. **No es un error.** Quien pulsa dos veces el mismo botón no ha hecho nada malo, y responder con un rechazo obligaría a la interfaz a consultar el estado antes de cada pulsación.

### FA-002 — Desactivar un upgrade

**Cuándo ocurre:** el estado pedido es «inactivo» y el producto es un upgrade.

1. No se comprueba nada sobre el destino: **liberarlo nunca produce conflicto**.
2. El destino queda libre para que otro upgrade pueda activarse.

## 10. Excepciones

### EX-001 — Producto inexistente o retirado

**Condición:** el identificador no corresponde a ningún producto, o corresponde a uno retirado.
**Respuesta del sistema:** rechaza la operación. Un producto retirado no vuelve a la venta cambiándole el estado.

### EX-002 — Ya hay un upgrade activo hacia ese destino

**Esta excepción llegó aquí desde `RF-PM-001` el 26-08-2026**, al resolverse que el producto nace inactivo: registrar ya no puede chocar con un upgrade activo, de modo que la comprobación de `RN-PM-004` vive **solo** en esta operación.

**Condición:** se pide activar un upgrade y otro upgrade activo apunta a la misma membresía.
**Respuesta del sistema:** rechaza la activación **nombrando el producto que ocupa el destino**, para que el actor sepa cuál desactivar.

## 11. Validaciones

| ID | Validación | Mensaje esperado |
|---|---|---|
| `VAL-001` | Identificador con formato válido | El identificador indicado no tiene un formato válido. |
| `VAL-002` | Estado obligatorio y dentro del dominio | El estado indicado no es válido. |
| `VAL-003` | Descripción exigida para activar | Un producto sin descripción no puede publicarse. |

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-PM-040` | El sistema desactiva un producto activo, y este deja de aparecer en la oferta sin desaparecer del catálogo |
| `CA-PM-041` | El sistema activa un producto inactivo |
| `CA-PM-042` | El sistema rechaza activar un upgrade cuando otro upgrade activo apunta a su mismo destino, y el mensaje nombra a ese producto |
| `CA-PM-043` | El sistema permite activar un upgrade **después** de desactivar el que ocupaba su destino |
| `CA-PM-044` | El sistema acepta sin error una petición que deja el producto en el estado que ya tenía, y **no registra evento** |
| `CA-PM-045` | El sistema rechaza cambiar el estado de un producto retirado |
| `CA-PM-046` | El sistema registra el cambio de estado en la auditoría con su valor anterior y el nuevo |
| `CA-PM-047` | El sistema rechaza la operación a un actor sin el permiso de modificación de productos |
| `CA-PM-072` | El sistema **rechaza activar un producto sin descripción** (`RN-PM-014`), y lo admite en cuanto `RF-PM-004` se la pone |
| `CA-PM-073` | El sistema **sí permite desactivar** un producto sin descripción: la regla acota lo que se publica, no lo que se retira |
| `CA-PM-085` | El sistema **no exige motivo** para activar ni para desactivar |

## 13. Casos límite

- **Dos activaciones simultáneas hacia el mismo destino:** dos upgrades inactivos hacia el mismo nivel se activan a la vez. Uno debe quedar y el otro ser rechazado; que queden los dos es exactamente el desenlace que `RN-PM-004` existe para impedir, y no basta con comprobarlo antes de escribir.
- **Desactivar y activar en carrera:** uno desactiva el upgrade que ocupa el destino mientras otro activa el suyo. Cualquiera de los dos desenlaces es correcto; lo que no puede quedar es ninguno activo por un rechazo mal ordenado.
- **Activar un producto de bot:** no comprueba destino, porque no lo tiene. Debe probarse que la comprobación **no se ejecuta**, no solo que no falla.
- **Desactivar el único upgrade hacia el nivel más alto:** se admite. El sistema no exige que todo nivel tenga upgrade: que no se pueda comprar el ascenso a un nivel es una decisión comercial legítima.

## 14. Preguntas abiertas

Ninguna. Las tres quedaron cerradas el 26-08-2026: una por decisión, una por la aprobación de `RF-PM-001` y una por no tener hoy nada que decidir.

| # | Pregunta | Resolución |
|---|---|---|
| 1 | ¿Desactivar exige motivo? | **No.** El Art. V.13 solo lo obliga en las eliminaciones, `RF-SP-022` y `RF-SP-023` no lo piden para países y monedas, y `RF-PM-004` tampoco al corregir. Exigirlo solo aquí dejaría **la operación que más se repite** del catálogo como la única que interroga a quien la usa. Lo que queda registrado es el cambio de estado con su valor anterior y el nuevo, que es lo que permite reconstruir cuándo dejó de venderse algo |
| 2 | ¿El estado admite un tercer valor, `BORRADOR`? | **No por ahora.** Lo cerró la aprobación de `RF-PM-001`: con `RN-PM-012` el producto **nace `INACTIVO`**, de modo que ese tercer valor solo separaría «nunca publicado» de «retirado de la venta» — una distinción fina, que no urge, y que se añade después con una migración barata sobre un `varchar` con `CHECK` |
| 3 | ¿Desactivar un upgrade afecta a quien lo tenía en el carrito? | **Hoy no aplica, y por eso no se decide**: no existen ni el carrito ni la compra (`requirements/pm.md` §1.3). Lo que esta spec sí deja fijado —y es lo que quien escriba la compra necesita saber— es que **esta operación no reserva nada**: `RF-PM-007` no promete que lo que devuelve seguirá disponible, y desactivar surte efecto en la consulta siguiente. La pregunta se traslada a quien escriba la compra en lugar de responderse aquí a ciegas |

---

## 15. Control de cambios

| Versión | Fecha | Cambio | Responsable |
|---|---|---|---|
| 0.2.0 | 26-08-2026 | **Aprobada.** No se exige motivo para activar ni desactivar, por coherencia con `RF-PM-004` y con los catálogos de `SP`. Recibió además dos cosas de fuera: la excepción del **upgrade activo hacia el mismo destino**, que llegó desde `RF-PM-001` al resolverse que el producto nace inactivo —y con ella la comprobación de `RN-PM-004` vive solo aquí—, y la regla `RN-PM-014`, que impide **publicar un producto sin descripción**. La pregunta del carrito se traslada a quien escriba la compra, con lo único que esta spec puede afirmar: aquí no se reserva nada. Criterios `CA-PM-072`, `CA-PM-073` y `CA-PM-085`. | Responsable del proyecto |
| 0.1.0 | 26-08-2026 | Redacción inicial, con tres preguntas abiertas. | Responsable técnico |
