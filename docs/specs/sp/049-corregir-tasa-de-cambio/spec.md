# SPEC — `RF-SP-049` Corregir una tasa de cambio

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-049` |
| Módulo | `SP` — Sistema Principal |
| Estado | **Aprobada** |
| Autor | Responsable técnico |
| Aprobada por | Responsable del proyecto |
| Fecha de aprobación | 07-09-2026 |

---

## 1. Objetivo

Enmendar lo que se declaró mal **sin reescribir lo que ya se convirtió**.

## 2. Contexto

Una tasa se teclea, y lo que se teclea se equivoca. Sin este requerimiento, corregir un dígito obligaría a retirar la tasa y registrar otra — y el registro de eliminación se llenaría de «error de tecleo», que es exactamente el ruido que el Art. V.13 quiere evitar.

**Y es la operación que puede violar `RN-SP-032` sin que el alta lo haya hecho**: mover una vigencia o activar una tasa suspendida puede pisar a la que ya rige.

## 3. Actores

| Actor | Papel |
|---|---|
| Administrador | Corrige lo corregible |

## 4. Alcance

### 4.1 Incluye

- Corregir el **precio**, la **vigencia** —inicio y fin— y el **estado**.
- Admitir que la fecha de fin **se vacíe**: una tasa acotada pasa a ser vitalicia.
- Rechazar la corrección que produce un **solapamiento**.
- Registrar en la auditoría **solo lo que cambió**, con su valor anterior y el nuevo.

### 4.2 No incluye

- **Cambiar ninguna de las dos monedas.** Son las que definen qué cambio expresa la tasa; tocarlas la convertiría en otra. Ver §14, resolución 1.
- **Exigir motivo.** La auditoría ya registra qué cambió, de cuánto a cuánto, quién y cuándo. Exigirlo en cada coma llena ese campo de «ajuste», que es lo que `RF-PM-004` ya resolvió igual.
- **Corregir una tasa retirada.** Lo que se retiró queda como estaba.

## 5. Reglas de negocio aplicables

| ID | Regla | Origen |
|---|---|---|
| `RN-SP-030` | El precio es mayor que cero | `requirements/sp.md` §5.1 |
| `RN-SP-031` | La vigencia empieza siempre y puede no terminar | `requirements/sp.md` §5.1 |
| `RN-SP-032` | **Dos tasas vigentes del mismo par no se solapan** | `requirements/sp.md` §5.2 |

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción | Restricción |
|---|---|---|---|
| Identificador | Sí | Cuál se corrige | Debe existir y **no estar retirada** |
| Precio | No | Precio nuevo | Mayor que cero, hasta ocho decimales. **No admite vaciarse** |
| Fecha de inicio | No | Inicio nuevo | **No admite vaciarse**: es obligatoria en la columna |
| Fecha de fin | No | Fin nuevo | **Sí admite vaciarse**, y hacerlo convierte la tasa en vitalicia |
| Estado | No | Activa o suspendida | **No admite vaciarse** |

**Ausente y vacío no son lo mismo.** No enviar un campo significa «déjalo como está»; enviarlo vacío significa «bórralo», y **solo la fecha de fin tiene un estado al que ir**. Los otros tres son obligatorios en la columna, de modo que su nulo explícito **se rechaza** en lugar de borrar.

### 6.2 Salida

La tasa corregida, con las dos monedas resueltas — la misma forma que devuelven el alta y el listado.

## 7. Precondiciones y postcondiciones

**Precondiciones**

- El actor está autenticado y porta `exchange-rates:update`.
- La tasa existe y no está retirada.

**Postcondiciones**

- La fila refleja lo corregido.
- Se registró un evento de **modificación** con **solo los campos que cambiaron**, cada uno con su valor anterior y el nuevo.
- **Si la petición no cambió nada, no hay evento y `updated_at` no se mueve.**

## 8. Flujo principal

1. El actor envía los campos que quiere corregir.
2. El sistema rechaza los que no se pueden corregir, **nombrándolos**.
3. El sistema **bloquea la fila** y comprueba que existe y no está retirada.
4. El sistema valida cada campo recibido contra su regla.
5. El sistema aplica y **vuelca el cambio** para que el `EXCLUDE` decida antes de responder.
6. Si algo cambió, el sistema registra el evento y devuelve la tasa.

## 9. Flujos alternativos

### FA-001 — La petición no cambia nada

**Comportamiento:** `200` con la tasa tal cual. **Sin evento y sin mover `updated_at`**: una petición que no cambia nada no es un cambio, y registrarla llena la línea de tiempo de ruido.

### FA-002 — Se vacía la fecha de fin

**Comportamiento:** la tasa pasa a ser **vitalicia**. Es el único vaciado admitido, y **el que más puede chocar**: extender una vigencia hacia el infinito puede pisar a la siguiente.

### FA-003 — Se suspende la tasa

**Comportamiento:** `is_active` pasa a falso y **la tasa libera su periodo**, porque el `EXCLUDE` es parcial. A partir de ahí, otra tasa puede cubrir esos días.

## 10. Excepciones

### EX-001 — Tasa inexistente o retirada

**Respuesta del sistema:** responde que el recurso no existe. **Una retirada responde lo mismo que una inexistente** para esta operación: lo que se retiró no se corrige.

### EX-002 — Se solapa con otra tasa vigente

**Condición:** la corrección deja dos tasas activas y vivas del mismo par con vigencias que se tocan.
**Respuesta del sistema:** rechaza como **conflicto** y **no aplica ninguno** de los demás cambios enviados.

### EX-003 — Se intenta cambiar una moneda

**Respuesta del sistema:** rechaza la petición **nombrando el campo**, y **no la ignora**: ignorarla haría creer que el cambio se aplicó, y lo descubriría quien convirtiera con la tasa equivocada.

## 11. Validaciones

| ID | Validación | Mensaje esperado |
|---|---|---|
| `VAL-001` | Identificador con formato válido | El identificador indicado no tiene un formato válido. |
| `VAL-002` | Precio mayor que cero y con ocho decimales | El precio de la tasa debe ser mayor que cero. |
| `VAL-003` | El precio no admite vaciarse | El precio de la tasa no puede quedar vacío. |
| `VAL-004` | La fecha de inicio no admite vaciarse | La fecha de inicio no puede quedar vacía. |
| `VAL-005` | El estado no admite vaciarse | El estado de la tasa no puede quedar vacío. |
| `VAL-006` | Fin no anterior al inicio | La fecha de fin no puede ser anterior a la de inicio. |
| `VAL-007` | Las monedas no se admiten | La moneda de origen y la de destino no se pueden modificar. |

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-SP-553` | El sistema corrige el precio, la vigencia y el estado, y conserva las dos monedas |
| `CA-SP-554` | El sistema aplica **solo los campos enviados** y deja intactos los ausentes |
| `CA-SP-555` | El sistema **vacía la fecha de fin** con nulo explícito, y la tasa pasa a ser vitalicia |
| `CA-SP-556` | El sistema **rechaza vaciar** el precio, la fecha de inicio y el estado: los tres son obligatorios en la columna |
| `CA-SP-557` | El sistema rechaza la petición que trae **una moneda**, en lugar de ignorarla |
| `CA-SP-558` | El sistema rechaza una corrección que **produce un solapamiento**, y **no aplica ninguno** de los demás cambios enviados |
| `CA-SP-559` | El sistema **admite activar** una tasa suspendida cuando su periodo está libre, y **la rechaza** cuando lo pisa |
| `CA-SP-560` | El sistema **libera el periodo al suspender**: después de suspender una tasa, otra puede cubrir esos mismos días |
| `CA-SP-561` | El sistema registra en la auditoría **solo los campos que cambiaron**, cada uno con su valor anterior y el nuevo |
| `CA-SP-562` | El sistema **no registra evento** cuando la petición no cambia nada, y **no mueve `updated_at`** |
| `CA-SP-563` | El sistema rechaza corregir una tasa **retirada** |
| `CA-SP-564` | El sistema rechaza la corrección a un actor sin `exchange-rates:update` |

## 13. Casos límite

| Caso | Decisión |
|---|---|
| Se corrige el precio de una tasa que ya rigió | **Se admite**, y es el caso normal: corregir un tecleo. **Lo que ya se convirtió con ella no se reescribe**, y no porque este requerimiento lo impida sino porque **nadie convierte todavía**. El día que exista la conversión tendrá que **copiar la tasa aplicada** en su propia fila, exactamente como `RN-MV-002` obliga a copiar el precio de un producto — y esa condición queda impuesta aquí, sobre una operación que aún no existe |
| Dos correcciones simultáneas de la misma tasa | El **bloqueo pesimista** las serializa: la última queda **entera**, no una mezcla de las dos |
| Dos correcciones simultáneas de tasas **distintas** hacia el mismo periodo | El bloqueo **no** las serializa —cada una bloquea su propia fila— y decide el `EXCLUDE`: una queda y la otra recibe `409` |
| Se corrige el inicio hacia atrás, cubriendo días de una tasa anterior | **Se rechaza** si la anterior está activa y viva. Si está suspendida o retirada, **se admite**: su periodo está libre |

## 14. Preguntas abiertas

| # | Pregunta | Resolución |
|---|---|---|
| 1 | ¿Se pueden corregir las monedas? | **No.** Son las que definen **qué cambio expresa** la tasa: corregirlas la convierte en otra, y quien mirase la auditoría vería un precio que nunca fue el de ese par. Es el mismo criterio con el que `RF-PM-004` rechaza el tipo y las dos membresías de un producto. Quien necesite otro par registra otra tasa y retira esta |
| 2 | ¿Se exige motivo? | **No**, por coherencia con `RF-PM-004` y con los catálogos de `SP`. La auditoría ya dice qué cambió, de cuánto a cuánto, quién y cuándo; el motivo se exige donde el dato **desaparece** de la vista, que es el retiro |
| 3 | ¿Corregir una tasa vencida tiene sentido? | **Sí, y se admite.** Una tasa vencida sigue explicando lo que pasó, y si se tecleó mal conviene poder arreglarla. **No puede solaparse con nada** por definición si su periodo ya pasó y nadie lo ocupó |
| 4 | ¿Hace falta un requerimiento aparte para el estado, como `RF-PM-005`? | **No.** Aquel existe porque en `PM` el estado gobierna la oferta y `RN-PM-004` vive entera ahí. Aquí el estado es **un campo más de la vigencia** —suspender es dejar de regir— y separarlo daría dos endpoints que compiten por la misma restricción |

## 15. Control de cambios

| Versión | Fecha | Cambio | Responsable |
|---|---|---|---|
| 0.1.0 | 07-09-2026 | Redacción inicial. **La decisión que carga el requerimiento es que las monedas no se corrigen**: son las que definen qué cambio expresa la tasa, y tocarlas dejaría en la auditoría un precio que nunca fue el de ese par. **Y la que más cuesta leer es la de §13**: corregir el precio de una tasa que ya rigió se admite, y lo que impide que eso reescriba el pasado **no es este requerimiento sino que nadie convierte todavía** — el día que exista la conversión tendrá que **copiar la tasa aplicada** en su propia fila, y esa condición queda impuesta aquí sobre una operación que aún no existe, exactamente como `PM` se la impuso a `MV` con el precio. **El estado no tiene requerimiento propio**, al revés que `RF-PM-005`: aquí suspender es dejar de regir, que es parte de la vigencia, y separarlo daría dos endpoints compitiendo por la misma restricción. | Responsable del proyecto |
