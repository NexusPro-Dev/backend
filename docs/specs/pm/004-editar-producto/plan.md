# PLAN — `RF-PM-004` Editar producto

| Campo | Valor |
|---|---|
| Requerimiento | `RF-PM-004` |
| Especificación | [`spec.md`](spec.md) v0.2.0 |
| `spec.md` aprobada el | 26-08-2026 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Enmendado el | 27-08-2026 — `RN-PM-015` |
| Fecha de aprobación | 26-08-2026 |

---

## 1. Enfoque

Una corrección parcial: se aplica lo que llega y se deja intacto lo que no. El problema de fondo de esta operación **no es la validación, es distinguir tres estados de un campo** —ausente, presente y nulo, presente con valor—, y ese problema ya está resuelto en el sistema.

**Se reutiliza `Patchable` de `shared/patch`**, que `RF-SP-027` creó. No se vuelve a intentar con `Optional`: allí quedó escrito que **falló en silencio**, porque al deserializar Jackson entrega `Optional.empty()` tanto para el campo ausente como para el nulo explícito —`getAbsentValue()` delega en `getNullValue()`—, y los dos estados que hay que separar se funden en uno. El síntoma no se parecía a la causa: enviar solo el nombre rechazaba la petición por «apellido vacío».

Aquí la distinción decide dos comportamientos opuestos: **la descripción admite vaciarse** y el **nombre no**.

## 2. Cambios de esquema

**Ninguno.**

## 3. Componentes afectados

| Capa | Componente | Responsabilidad |
|---|---|---|
| `domain/models` | `Product.update(...)` | Aplica lo recibido y **devuelve qué cambió de verdad** |
| `domain/repository` | `ProductRepository.findAliveByIdForUpdate` | Bloqueo pesimista sobre la fila |
| `domain/service` | `UpdateProductService` | Orden de verificación de §5 |
| `application` | `UpdateProductRequest` | Con `Patchable` en los cuatro campos |
| `interfaces` | `ProductController` | `PATCH /api/v1/products/{id}` |

## 4. Contrato de API

`PATCH /api/v1/products/{id}` con los campos a corregir. `200` con el producto en la misma forma que `RF-PM-003`.

- **El tipo, el código y la membresía destino no se admiten**, y su presencia **devuelve `400`** (`CA-PM-033`). Se rechaza y no se ignora: ignorarlos haría creer al actor que el cambio se aplicó. Se consigue con `FAIL_ON_UNKNOWN_PROPERTIES`, que ya está activo, más un mensaje propio de `VAL-006` si llegan con nombre conocido.
- **`description: null` la borra; `name: null` se rechaza** (`VAL-002`).
- **No se exige motivo** (`spec.md` §14, resolución 2).

## 5. Orden de verificación

Es el contrato de esta operación, y por eso se escribe:

1. **Bloqueo** de la fila. Va primero: validar sobre una fila que otra transacción está corrigiendo produce decisiones tomadas sobre un estado que ya no existe.
2. El producto existe y **no está retirado** (`EX-001`).
3. Cada campo recibido, contra su regla.
4. Si llega nombre: unicidad **excluyendo al propio producto**.
5. Si llega precio o moneda: la moneda existe, está activa, y los decimales cuadran **con la moneda nueva**, no con la anterior.
6. Se aplica, y **solo si algo cambió** se emite el evento.

!!! warning "La unicidad del nombre se comprueba ANTES de tocar el agregado"

    Es un defecto que `RF-SP-004` ya pagó: con el nombre nuevo escrito en la entidad gestionada, el propio `SELECT` de la comprobación dispara el vaciado de Hibernate, la violación del índice llega antes que la comprobación y sale un `500` donde corresponde un `409`. Solo aparece con el dato ya duplicado, que es justo cuando importa.

## 6. Auditoría

Un evento `UPDATE` con **solo los campos que cambiaron**, cada uno con su valor anterior y el nuevo. `Product.update(...)` devuelve el diff en lugar de que el bot lo reconstruya comparando: quien aplica el cambio es quien sabe qué cambió.

**Sin evento cuando nada cambió** (`CA-PM-038`): un cambio que no cambió nada no es un cambio, y registrarlo llena la línea de tiempo de ruido que oculta lo que sí ocurrió.

## 7. Transaccionalidad

Una transacción, con bloqueo pesimista sobre la fila del producto. **Dos correcciones simultáneas se serializan**, y la segunda ve lo que la primera confirmó — que es lo que evita que quede una mezcla de las dos.

Es la misma corrección que `SP` aplicó el 26-08-2026 a las cuatro operaciones que cambiaban roles y membresía sin bloquear: no se repite aquí el defecto de validar contra un estado que otra transacción está a punto de cambiar.

## 8. Impacto sobre otros módulos

Consume el **catálogo de monedas** de `SP` cuando llega precio o moneda. Ninguno más: el destino no se toca.

## 9. Alternativas consideradas

| Alternativa | Por qué se descartó |
|---|---|
| `Optional` en vez de `Patchable` | Ya falló en `RF-SP-027`, y en silencio |
| `PUT` con el recurso completo | Obligaría a reenviar tipo, código y destino, que son inmutables, y a decidir qué hacer si llegan distintos |
| Ignorar los campos inmutables si llegan | Haría creer que el cambio se aplicó |
| Convertir el importe al cambiar de moneda | El sistema **no hace conversión de divisa**. Cambiar de moneda es declarar que ese número siempre estuvo en la otra, y así queda escrito en `spec.md` §13 |

## 10. Riesgos

| # | Riesgo | Mitigación |
|---|---|---|
| 1 | La unicidad excluyendo al propio producto se olvida, y corregir la descripción sin tocar el nombre acaba rechazándose | Prueba dedicada: enviar el nombre actual no es un duplicado consigo mismo |
| 2 | Cambiar moneda y precio a la vez se valida contra la moneda anterior | El orden de §5 lo fija, y la prueba usa monedas de distinta escala |

## 11. Estrategia de prueba

| Qué se prueba | Nivel | Cómo |
|---|---|---|
| Los diez criterios de `spec.md` §12 | API | |
| **Ausente ≠ vacío** | API | Vaciar la descripción la borra; el nombre vacío se rechaza; el campo ausente no se toca |
| Tipo, código o destino en la petición | API | `400`, no se ignoran |
| Nombre igual al actual | API | No es duplicado consigo mismo |
| Moneda nueva con otra escala | API | El precio se valida contra la **nueva** |
| Número de sentencias | Integración | Bloqueo, unicidad **solo si el nombre cambió**, `UPDATE` y evento |
| Sin evento si nada cambió | Integración | `audit_change_log` no crece |
| Dos correcciones simultáneas | Concurrencia | La última queda **entera**, no una mezcla |
