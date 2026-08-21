# SPEC — `RF-SP-023` Cambiar el estado de una moneda

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-023` |
| Módulo | `SP` — Sistema Principal |
| Estado | **Aprobada** |
| Autor | Responsable técnico |
| Aprobada por | Responsable técnico |
| Fecha de aprobación | 21-08-2026 |

---

## 1. Objetivo

Habilitar o retirar una moneda del catálogo sin tocar su definición ni los importes ya registrados en ella.

## 2. Contexto

El catálogo de monedas se puebla por migración y no admite alta, edición ni borrado por la API (`RN-SP-010`). El estado es la única palanca que queda, y sirve para dos cosas distintas que conviene no confundir:

- **Incorporar una moneda sin habilitarla todavía.** La migración puede sembrar una moneda meses antes de que se opere con ella; hasta entonces existe, tiene sus decimales fijados y no se ofrece en ningún formulario.
- **Retirar una que deja de usarse.** Deja de poder seleccionarse en operaciones nuevas, pero los importes históricos siguen expresados en ella y siguen resolviendo su definición: sus decimales son los que hacen que un importe guardado signifique lo que significa.

Hay un límite que no se negocia: **la moneda por defecto no puede desactivarse.** `RF-SP-019` garantiza que exactamente una lo esté (`CA-SP-169`), y es la referencia con la que se interpretan los importes del sistema. Desactivarla los dejaría sin referencia válida.

## 3. Actores

| Actor | Rol en esta funcionalidad |
|---|---|
| Super Administrador | Activa o desactiva monedas |

Es el único actor. La ficha de `requirements/sp.md` §6.2 lo reserva a `SUPERADMIN`: el catálogo se puebla por migración y su estado condiciona todo cálculo financiero, de modo que la operación pertenece a quien mantiene la plataforma, no a la administración del negocio.

## 4. Alcance

### 4.1 Incluye

- Desactivar una moneda activa y reactivar una inactiva.

### 4.2 No incluye

- Crear, editar o eliminar monedas: el catálogo se puebla por migración (`RN-SP-010`).
- Cambiar cuál es la moneda por defecto: no existe requerimiento que lo permita y se hace por migración.
- Cambiar el número de decimales, el símbolo o el nombre.
- Convertir importes ya registrados a otra moneda, ni recalcular nada.

## 5. Reglas de negocio aplicables

| ID | Regla | Origen |
|---|---|---|
| `RN-SP-010` | Las monedas no se crean, editan ni eliminan por la API; lo único modificable es su estado, y la moneda por defecto no puede desactivarse | `requirements/sp.md` §5.1 |

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción | Restricción de negocio |
|---|---|---|---|
| Identificador de la moneda | Sí | Moneda cuyo estado cambia | Debe existir en el catálogo |
| Estado | Sí | Nuevo estado | Activo o inactivo |

### 6.2 Salida

| Dato | Descripción |
|---|---|
| Moneda | Moneda con su código, nombre, símbolo, decimales, indicador de moneda por defecto y estado actualizado |

## 7. Precondiciones y postcondiciones

**Precondiciones**

- El actor está autenticado y posee el permiso de modificación de monedas.
- La moneda existe en el catálogo.
- Si se solicita desactivarla, no es la moneda por defecto.

**Postcondiciones**

- La moneda queda en el estado solicitado.
- Si quedó inactiva, deja de aparecer en `RF-SP-019` salvo que se pidan explícitamente las inactivas, y deja de poder seleccionarse en operaciones nuevas.
- Su definición —código, nombre, símbolo y decimales— permanece intacta, y los importes ya expresados en ella la siguen resolviendo.
- La moneda por defecto sigue siendo exactamente una y sigue activa.
- Queda constancia en la auditoría de cambios, y **no** en la de seguridad.

## 8. Flujo principal

1. El actor solicita cambiar el estado de una moneda.
2. El sistema verifica que la moneda exista.
3. Si se solicita desactivarla, el sistema verifica que no sea la moneda por defecto.
4. El sistema aplica el nuevo estado.
5. El sistema registra el evento en la auditoría de cambios, con el estado anterior y el nuevo.
6. El sistema informa la moneda actualizada.

## 9. Flujos alternativos

### FA-001 — La moneda ya está en ese estado

**Cuándo ocurre:** se solicita activar una moneda activa, o desactivar una inactiva.

1. El sistema no aplica cambio ni registra evento de auditoría.
2. Devuelve la moneda sin tratarlo como error: la operación es idempotente.

## 10. Excepciones

### EX-001 — Moneda por defecto

**Condición:** se solicita desactivar la moneda marcada como moneda por defecto del sistema.
**Respuesta del sistema:** rechaza la operación, cita `RN-SP-010` y explica que los importes del sistema quedarían sin referencia válida. Cambiar cuál es la moneda por defecto es una operación de migración, no de API.

### EX-002 — Moneda inexistente

**Condición:** el identificador no corresponde a ninguna moneda del catálogo.
**Respuesta del sistema:** rechaza la operación e informa que la moneda no existe.

## 11. Validaciones

| ID | Validación | Mensaje esperado |
|---|---|---|
| `VAL-001` | Estado obligatorio y dentro del dominio | El estado indicado no es válido. |
| `VAL-002` | Moneda existente | La moneda solicitada no existe. |
| `VAL-003` | La moneda por defecto no se desactiva | No es posible desactivar la moneda por defecto del sistema. |

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-SP-185` | El sistema desactiva una moneda activa que no es la moneda por defecto, y la reactiva después |
| `CA-SP-186` | El sistema rechaza desactivar la moneda por defecto |
| `CA-SP-187` | La moneda inactiva deja de aparecer en el listado por defecto de `RF-SP-019` |
| `CA-SP-188` | El código, nombre, símbolo y número de decimales no cambian al cambiar el estado |
| `CA-SP-189` | Los importes ya expresados en una moneda desactivada siguen resolviendo su definición y sus decimales |
| `CA-SP-190` | El sistema no registra evento cuando la moneda ya estaba en el estado solicitado |
| `CA-SP-339` | El sistema registra el cambio de estado en la auditoría de cambios, con el valor anterior y el nuevo, y **no** en la de seguridad |
| `CA-SP-340` | La operación no solicita ni admite un motivo |
| `CA-SP-191` | El sistema rechaza la operación a un actor sin el permiso de modificación de monedas |

## 13. Casos límite

- **Catálogo con una sola moneda:** es el estado esperado hoy, y esa moneda es la de defecto. Ninguna operación de este requerimiento puede aplicarse sobre ella, lo que en la práctica lo deja inerte hasta que se siembre la segunda. Es correcto: existe para cuando haya más de una.
- **Moneda inactiva con importes históricos:** siguen siendo legibles y calculables. Desactivar cierra la entrada, no reinterpreta lo ya guardado.
- **Reactivar una moneda retirada:** se admite sin condiciones; su definición nunca cambió.
- **Moneda sembrada por migración e inactiva desde el origen:** es un uso previsto, no una anomalía.
- **Cambio concurrente sobre la misma moneda:** las peticiones se serializan sobre la fila; la segunda cae en `FA-001`.
- **Desactivar la moneda por defecto para poder cambiarla:** no es un camino. La sustitución de la moneda por defecto debe hacerse en una sola migración que marque la nueva y desmarque la anterior, sin que exista un instante con ninguna.

## 14. Preguntas abiertas

Ninguna. Las tres se resolvieron el 21-08-2026, antes de aprobar la especificación. La segunda se arrastró de `RF-SP-022`, aprobada el mismo día, junto con la del motivo, que aquí no llegó a plantearse.

| # | Pregunta | Resolución |
|---|---|---|
| 1 | ¿Debe rechazarse desactivar una moneda que ya tiene importes registrados? | **No: se desactiva igual.** Retirar de la circulación una moneda que se dejó de usar es precisamente el segundo motivo por el que existe el estado, y exigir que no tenga historia lo dejaría aplicable solo a monedas sembradas y nunca usadas, que es el caso menos urgente. Los importes históricos no se invalidan: siguen resolviendo su definición y sus decimales (`CA-SP-189`), solo dejan de crearse nuevos. Es la misma resolución que en `RF-SP-022` con los países ya referenciados, y por el mismo argumento: impedirlo dejaría sin salida justo al caso que más urge retirar |
| 2 | ¿El cambio de estado se registra también en la auditoría de seguridad? | **Solo en la de cambios**, arrastrado de la resolución 1 de `RF-SP-022`. No hay privilegio en juego: una moneda inactiva deja de ofrecerse, no retira acceso a nadie, y `security.md` §8.1 es un catálogo cerrado de eventos de control de acceso. `CA-SP-339` lo verifica en los dos sentidos. **Queda fijado como criterio del módulo:** el cambio de estado de un catálogo se audita en `audit_change_log` y solo ahí |
| 3 | ¿Hace falta un requerimiento para cambiar cuál es la moneda por defecto? | **No se crea todavía.** Mientras haya una sola moneda no hay nada que elegir, y el requerimiento nacería sin poder probarse. Cuando haya varias, la sustitución tendrá que ser **atómica** —marcar la nueva y desmarcar la anterior en el mismo paso, sin un instante en que no haya ninguna, porque el índice único parcial sobre `currencies.is_default` no admite ese estado— y arrastrará una decisión sobre cómo se reinterpretan los importes ya guardados, que excede a un catálogo. Hasta entonces se hace por migración, que es la misma vía por la que el catálogo se puebla |

**Sobre el motivo.** Esta especificación no lo pide y no llegó a plantearse como pregunta. La resolución 2 de `RF-SP-022` lo fijó para toda esta clase de operación: el Art. V.13 obliga al motivo solo en las eliminaciones, y aquí el registro sigue existiendo y la auditoría ya guarda quién y cuándo. `CA-SP-340` deja la ausencia verificada, para que no se cuele en el contrato al escribir el plan.
