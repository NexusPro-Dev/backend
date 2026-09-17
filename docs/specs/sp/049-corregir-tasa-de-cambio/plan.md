# PLAN — `RF-SP-049` Corregir una tasa de cambio

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-049` |
| Especificación | [`spec.md`](spec.md), aprobada el 07-09-2026 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 07-09-2026 |

---

## 1. Enfoque

Hereda entero el diseño de `RF-PM-004`, que es la corrección más parecida del sistema, y **no repite su argumentación**: `Patchable` con tres estados, bloqueo antes de validar, diff devuelto por el agregado y evento solo si algo cambió. Lo que sí escribe es **lo que aquí es distinto**, que es el `EXCLUDE`.

## 2. Cambios de esquema

**Ninguno.** La tabla y su restricción las crea `RF-SP-047`.

## 3. Componentes afectados

| Capa | Elemento |
|---|---|
| `application` | `UpdateExchangeRateRequest` con `Patchable` |
| `domain/models` | `ExchangeRate.update(...)`, que devuelve el diff |
| `domain/service` | `UpdateExchangeRateService` |
| `interfaces` | `PATCH /api/v1/exchange-rates/{id}` |

## 4. Contrato de API

`PATCH /api/v1/exchange-rates/{id}`

```json
{ "price": 4200.50000000, "validTo": null, "isActive": true }
```

- **`Patchable` distingue los tres estados** que un `PATCH` necesita: ausente, presente con nulo y con valor. Aquí la distinción **decide dos comportamientos opuestos**: `validTo: null` **vacía** —la tasa pasa a vitalicia— mientras que `price: null`, `validFrom: null` e `isActive: null` **se rechazan**.
- **`sourceCurrencyId` y `targetCurrencyId` se declaran igualmente**, como `Patchable<Object>`, **para poder rechazarlos con su mensaje**. Sin ellos, `FAIL_ON_UNKNOWN_PROPERTIES` ya daría `400`, pero con el texto genérico de Jackson: quien intente cambiar una moneda leería «propiedad desconocida» y creería que se equivocó de nombre.
- **No se vuelve a intentar con `Optional`**: falló en `RF-SP-027` y falló **en silencio**, porque Jackson entrega `Optional.empty()` tanto para el campo ausente como para el nulo explícito.

## 5. Orden de verificación

Es el contrato:

1. **Las monedas no llegan** (`VAL-007`), y **algo se informa**. Va lo primero, antes de buscar nada: una petición que pide lo imposible no debe costar una consulta.
2. **Bloqueo** de la fila. Antes de validar contra el estado: validar sobre una fila que otra transacción está corrigiendo produce decisiones tomadas sobre un estado que ya no existe.
3. La tasa **existe y no está retirada**.
4. Cada campo recibido, contra su regla — **incluidos los tres nulos que se rechazan**.
5. Se aplica, y **se vuelca**.
6. Si algo cambió, se emite el evento.

!!! danger "El volcado explícito es lo que separa un `409` de un `500`"

    Es el defecto que `RF-PM-004` ya pagó, y aquí es **peor** porque la restricción no es un índice único sino un `EXCLUDE`: sin `flush()` explícito, la violación llega **en el `commit`**, cuando ya no hay nadie escuchando que pueda traducirla, y el actor recibe un `500` sobre una regla de negocio perfectamente expresable.

    **Y la comprobación previa no basta**, por lo mismo de siempre: dos correcciones simultáneas de tasas **distintas** hacia el mismo periodo la atraviesan las dos — cada una bloquea **su propia** fila, de modo que el bloqueo no las serializa.

!!! important "Suspender y activar cruzan la misma restricción por caminos opuestos"

    El `EXCLUDE` es parcial sobre `is_active`. De ahí salen dos comportamientos que conviene tener juntos:

    - **Suspender siempre se puede**, y **libera el periodo**: la fila deja de participar en la restricción.
    - **Activar es la operación peligrosa**: una tasa suspendida que se reactiva vuelve a entrar, y puede chocar con la que ocupó su sitio mientras estaba fuera.

    Es exactamente el reparto que `RN-PM-004` tiene en `PM`, con una diferencia: allí hay un requerimiento dedicado al estado (`RF-PM-005`) y aquí no, de modo que **esta operación carga las dos mitades**.

## 6. Autorización

`@PreAuthorize("hasAuthority('exchange-rates:update')")`.

## 7. Auditoría

Evento `UPDATE` con **solo los campos que cambiaron**, cada uno con `before` y `after`. **El diff lo devuelve el agregado** y no el caso de uso comparando antes y después: reconstruirlo fuera obliga a copiar los valores previos y a acordarse de cada campo nuevo. Aquí, un campo que no entre en el diff es un campo que no se auditará, **y eso se ve en la misma línea en que se asigna**.

Sin evento si el diff está vacío, y **sin mover `updated_at`**.

## 8. Transaccionalidad

Una transacción con **bloqueo pesimista** sobre la fila. Es lo que hace que dos correcciones de la **misma** tasa produzcan la última entera y no una mezcla.

## 9. Impacto sobre otros módulos

Ninguno hoy. **Y una condición sobre uno que no existe**: el día que algo convierta con una tasa, tendrá que **copiar la tasa aplicada** en su propia fila, porque este requerimiento deja el precio corregible — es la misma condición que `PM` le impuso a `MV` con el precio de los productos (`RN-MV-002`).

## 10. Alternativas consideradas

| Alternativa | Por qué se descartó |
|---|---|
| **Congelar el precio de una tasa que ya rigió** | Cada corrección costaría un retiro y un alta, y el registro de eliminación se llenaría de «error de tecleo». Es la misma decisión que `RF-PM-004` tomó con el precio de un producto |
| **Un endpoint aparte para el estado**, como `RF-PM-005` | Aquí suspender **es** dejar de regir, que es parte de la vigencia. Dos endpoints competirían por la misma restricción y los dos tendrían que traducirla |
| **Permitir corregir las monedas** | Dejaría en la auditoría un precio que nunca fue el de ese par |
| **Confiar en la comprobación previa** | Dos correcciones simultáneas de filas distintas la atraviesan las dos |

## 11. Riesgos

| # | Riesgo | Mitigación |
|---|---|---|
| 1 | **Sin `flush()` explícito**, la violación del `EXCLUDE` sale como `500` en el `commit` | `CA-SP-558`, y la prueba concurrente de dos tasas distintas hacia el mismo periodo |
| 2 | Los tres nulos que **no** se vacían se tratan como el que sí | `CA-SP-556` prueba los tres, y `CA-SP-555` el que sí |
| 3 | **Activar** se implementa sin pasar por la restricción | `CA-SP-559` prueba las dos mitades: periodo libre y periodo ocupado |
| 4 | El evento se emite aunque nada cambie | `CA-SP-562` comprueba que `audit_change_log` **no crece** y que `updated_at` no se mueve |

## 12. Estrategia de prueba

- **Unitarias del agregado**: el diff trae solo lo que cambió; el mismo valor no es un cambio.
- **Integración**: los doce criterios de `spec.md` §12.
- **Concurrente 1**: dos correcciones de la **misma** tasa — la última queda entera.
- **Concurrente 2**: dos correcciones de tasas **distintas** hacia el mismo periodo — una queda, la otra recibe `409`. Es la que demuestra que el bloqueo no sustituye al `EXCLUDE`.
