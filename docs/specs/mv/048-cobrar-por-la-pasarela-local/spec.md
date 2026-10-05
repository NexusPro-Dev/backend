# SPEC — `RF-MV-048` Cobrar por la pasarela local

| Campo | Valor |
|---|---|
| Requerimiento | `RF-MV-048` |
| Módulo | `MV` — Movimientos |
| Versión | 0.1.0 |
| Estado | **Aprobada** |
| Autor | Responsable técnico |
| Aprobada por | Responsable del proyecto |
| Fecha de aprobación | 05-10-2026 |

!!! info "Qué va en este documento"

    **Qué debe pasar, y por qué.** Nada más.

    **Prueba de pertenencia:** si un cambio de tecnología lo invalidaría, no pertenece aquí — va a `plan.md`. No se nombran tablas, clases, endpoints ni librerías.

---

## 1. Objetivo

Que quien compra pueda **pagar con los métodos locales de su país** —en Colombia: PSE, Nequi, Efecty y Bre-B— **en su moneda**, en la página de pago de la pasarela local, y que la compra quede esperando a que la pasarela diga si el dinero entró.

---

## 2. Contexto

Es el cobro de la pasarela local ([`requirements/mv.md`](../../../requirements/mv.md) v0.78.0 §4.10), decidido por el responsable del proyecto el 05-10-2026. **Sigue el camino de la tarjeta** (`RF-MV-040`), cuya argumentación se hereda —el cobro se abre al registrar el pago, en las entradas en que quien compra es quien paga, y nada lo confirma salvo la pasarela—, con **tres diferencias**:

| | Tarjeta (`RF-MV-040`) | Pasarela local (este requerimiento) |
|---|---|---|
| Dónde paga el cliente | En la app, con el formulario de la pasarela | **En la página de la pasarela**, a la que la app lo lleva |
| En qué moneda | La del movimiento | **La del país de quien paga**, con la conversión vigente (`RN-MV-063`) |
| Qué lo confirma | La notificación firmada | **La consulta a la pasarela** que dispara un aviso o el barrido (`RF-MV-049`, `RF-MV-050`) |

### 2.1 Lo que este requerimiento decide

| Decisión | Qué se decidió |
|---|---|
| **El método es `PSE`, «Múltiples métodos de pago»** | Lo cobra la pasarela local. El cliente elige el método concreto en la página de la pasarela, no en la app |
| **El país es el de quien paga** | El de su ficha. Sin conversión vigente para ese país, **el pago no se registra** y la compra tampoco |
| **El importe se redondea hacia arriba a unidad entera** | 10 USD × 4.150,5 = 41.505 COP. Nunca se cobra de menos (`RN-MV-063`) |
| **El pago guarda cómo se convirtió** | La moneda local, el importe convertido y la conversión usada. No cambian aunque la conversión cambie después |
| **Sin la pasarela, `PSE` sigue como antes** | Apagada —sin credenciales—, el pago nace pendiente sin cobro y lo confirma una persona |

---

## 3. Actores

| Actor | Rol en esta funcionalidad |
|---|---|
| Quien compra para sí —producto, paquete o hotlink—, vuelve a pagar o compra puntos | Elige `PSE` y es llevado a la página de la pasarela |
| La pasarela local | Abre el cobro y devuelve la dirección de su página |

---

## 4. Alcance

### 4.1 Incluye

- Abrir el cobro en la pasarela local al registrar un pago con `PSE` en las compras propias (`RF-MV-002`, `RF-MV-011` a `RF-MV-013`), al volver a pagar (`RF-MV-018`) y al comprar puntos (`RF-MV-027`).
- Convertir el importe a la moneda local y guardar con qué se convirtió.
- Devolver la dirección de la página de pago.

### 4.2 No incluye

- **Saber si el dinero entró**: es `RF-MV-049` y `RF-MV-050`.
- **La venta que registra un funcionario** (`RF-MV-001`) **y la del alta por enlace** (`RF-SP-045`): admiten `PSE` y **nacen pendientes sin cobro**; quien compró la paga con `RF-MV-051`.
- **El retiro** por la pasarela local.

---

## 5. Reglas de negocio aplicables

| Regla | Cómo aplica |
|---|---|
| `RN-MV-062` | La conversión vigente del país; sin ella, no se cobra |
| `RN-MV-063` | Moneda local, precio de cobro, hacia arriba a unidad entera; el pago guarda la conversión |
| `RN-MV-039`, `RN-MV-040` | A lo sumo un pago pendiente; el cobro se referencia en el pago |
| `RN-MV-058` | Un pago con cobro abierto no lo confirma ni lo rechaza una persona |

---

## 6. Datos

### 6.1 Entrada

La de cada entrada de compra, con el método `PSE`. **Ningún dato nuevo**: el país sale de la ficha de quien paga.

### 6.2 Salida

La de cada entrada, más **el cobro local**: el pago, la pasarela, **la dirección de la página de pago**, la moneda local y el importe que se cobrará en ella.

---

## 7. Precondiciones y postcondiciones

| Tipo | Condición |
|---|---|
| Precondición | La pasarela local está configurada; el país de quien paga tiene conversión vigente |
| Postcondición | El pago queda `PENDIENTE`, con el cobro abierto, la moneda local, el importe convertido y la conversión usada. **Nada está confirmado** |

---

## 8. Flujo principal

1. Quien compra elige `PSE` en una de las entradas del §4.1.
2. El sistema registra la compra y su pago pendiente, como siempre.
3. Busca la conversión vigente del país de esa persona y calcula el importe en moneda local, hacia arriba.
4. Abre el cobro en la pasarela, con el pago como referencia propia.
5. Guarda en el pago el cobro, la moneda, el importe convertido y la conversión.
6. Devuelve la compra con la dirección de la página de pago.

---

## 9. Flujos alternativos

### FA-001 — La pasarela local está apagada

El pago nace pendiente **sin cobro**, como antes; la respuesta no trae cobro local y una persona lo confirma.

### FA-002 — La misma petición se repite

Devuelve la compra ya registrada con **el mismo cobro**, sin abrir otro.

---

## 10. Excepciones

| ID | Situación | Resultado |
|---|---|---|
| `EX-001` | El país de quien paga no tiene conversión vigente | Conflicto; **no se registra nada** |
| `EX-002` | La pasarela no responde al abrir el cobro | No disponible; **no se registra nada** |
| `EX-003` | La pasarela rechaza el cobro —datos de la persona que no acepta, importe fuera de sus límites— | Entidad no procesable, con el motivo; **no se registra nada** |

---

## 11. Validaciones

Las de cada entrada. Ninguna nueva.

---

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-MV-600` | Una compra propia con `PSE` **abre el cobro** en la pasarela y la respuesta trae la **dirección de la página**, la moneda local y el importe convertido; el pago queda `PENDIENTE` con el cobro, la moneda, el importe y la conversión guardados |
| `CA-MV-601` | El importe se convierte con el **precio de cobro** de la conversión vigente del país de quien paga y **se redondea hacia arriba a unidad entera** |
| `CA-MV-602` | Lo mismo en una compra por **hotlink**, en una de **paquete**, al **volver a pagar** con `PSE` y al **comprar puntos** con `PSE` |
| `CA-MV-603` | Sin **conversión vigente** para el país de quien paga: conflicto, y **ni la compra ni el pago** quedan escritos |
| `CA-MV-604` | Si la pasarela **no responde**: no disponible, y nada queda escrito; si **rechaza** el cobro: entidad no procesable con su motivo, y nada queda escrito |
| `CA-MV-605` | Repetir la misma petición devuelve **el mismo cobro**, sin abrir otro en la pasarela |
| `CA-MV-606` | **Apagada la pasarela**, `PSE` nace pendiente sin cobro y la respuesta no trae cobro local |
| `CA-MV-607` | La venta que **registra un funcionario** con `PSE` nace pendiente **sin cobro** |
| `CA-MV-608` | Un pago con `PSE` y cobro abierto **no se confirma ni se rechaza a mano**; uno **sin** cobro sí |
| `CA-MV-609` | La **tarjeta no cambia**: `CREDIT_CARD` sigue yendo a su pasarela, y `PSE` nunca va a la de la tarjeta |
| `CA-MV-610` | La venta, sus líneas y su total siguen **en su moneda**; solo el cobro está en moneda local |

---

## 13. Casos límite

| Caso | Comportamiento |
|---|---|
| La conversión cambia entre registrar el pago y pagarlo | Se cobra lo que se calculó al abrir el cobro |
| Una compra de importe cero | No llega aquí: se paga con `GRATIS` |
| Quien paga no tiene país | No puede ocurrir: el país es obligatorio en la ficha (`RN-SP-034`) |

---

## 14. Preguntas abiertas

**Qué datos de la persona exige la pasarela para cada método** —documento, teléfono—. Se envían los de la ficha; lo confirmará el sandbox, y si falta alguno la pasarela lo rechaza con `EX-003`.

---

## 15. Control de cambios

| Versión | Fecha | Cambio | Autor |
|---|---|---|---|
| 0.1.0 | 05-10-2026 | Primera versión, con la pasarela local ([`requirements/mv.md`](../../../requirements/mv.md) v0.78.0 §4.10). **`PSE` lo cobra PayRetailers**, en su página, en moneda local con el precio de cobro y hacia arriba. Criterios `CA-MV-600` a `CA-MV-610`. | Responsable del proyecto |
