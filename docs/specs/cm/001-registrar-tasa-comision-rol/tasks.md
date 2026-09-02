# TASKS — `RF-CM-001` Registrar una tasa de comisión por rol

| Campo | Valor |
|---|---|
| Requerimiento | `RF-CM-001` |
| Plan | [`plan.md`](plan.md), aprobado el 02-09-2026 |
| Versión | 0.2.0 |
| Estado | **En revisión** |
| Autor | Responsable técnico |
| Aprobadas por | Pendiente |
| Fecha de aprobación | Pendiente |
| Issue | Pendiente de crear |
| Rama | `feature/flujos-de-pm-y-cm` |

!!! info "Qué va en este documento"

    **En qué pasos se construye** lo que `plan.md` decidió, con su dependencia y su verificación. Ninguna tarea se da por `Hecha` sin que su verificación pase.

!!! warning "Las tareas están hechas antes que este documento"

    El código se rehízo el 02-09-2026 y esta lista viene detrás. **No planifica: registra**, y por eso cada tarea nace `Hecha`. La tercera compuerta del Art. I.6 sigue pendiente y por eso el documento está `En revisión`.

---

## 1. Tareas

**Estados:** `Pendiente` · `En curso` · `Hecha` · `Bloqueada`.

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-01` | `V48`: soltar las restricciones y **dejar caer** `product_id`, `user_id`, `valid_from` y `valid_to` | — | La tabla queda con rol, porcentaje y marcas de tiempo | **Hecha el 02-09-2026** |
| `T-02` | `V48`: **vaciar** `commission_rates`, con el motivo escrito en la migración | `T-01` | Ninguna fila del modelo anterior sobrevive | **Hecha el 02-09-2026** |
| `T-03` | `V48`: `uq_commission_rates_id_role`, **redundante con la PK a propósito** | `T-01` | La clave foránea compuesta de `RF-CM-007` puede declararse | **Hecha el 02-09-2026** |
| `T-04` | Rehacer `CommissionRate`: rol, porcentaje, y nada más | `T-01` | El agregado no compila con referencias a producto o vigencia | **Hecha el 02-09-2026** |
| `T-05` | **Eliminar `RateScope`** y crear `RateSource` | `T-04` | No queda ninguna referencia a los cuatro grados | **Hecha el 02-09-2026** |
| `T-06` | Rehacer el puerto de escritura **sin `lockCase` ni `findOverlapping`** | `T-04` | El módulo compila sin ellos | **Hecha el 02-09-2026** |
| `T-07` | `CommissionRows`: las conversiones del driver, en un solo sitio | — | Los tres adaptadores de consulta la usan | **Hecha el 02-09-2026** |
| `T-08` | Puerto de consulta con la **cuenta de asociaciones como subconsulta correlacionada** | `T-07` | Una tasa con dos asociaciones aparece **una vez**, no dos | **Hecha el 02-09-2026** |
| `T-09` | `RegisterCommissionRateService`, con la única verificación que queda | `T-04` | Rol inexistente `422`, rol no vendedor `400` | **Hecha el 02-09-2026** |
| `T-10` | DTOs de entrada y salida, con `associatedProducts` | `T-09` | La respuesta del alta lo devuelve en cero | **Hecha el 02-09-2026** |
| `T-11` | `POST /api/v1/commission-rates` | `T-09`, `T-10` | `201` con `Location`, `400`, `403`, `422` | **Hecha el 02-09-2026** |
| `T-12` | Registro de auditoría de creación, armado por el agregado | `T-09` | La instantánea lleva rol y porcentaje | **Hecha el 02-09-2026** |
| `T-13` | Pruebas de los criterios de `spec.md` §12 | `T-11` | `CA-CM-001` a `CA-CM-008` | **Hecha el 02-09-2026** |
| `T-14` | Documentación OpenAPI, con el aviso de que **lo registrado no rige** | `T-11` | El contrato publicado lo dice en la descripción | **Hecha el 02-09-2026** |
| `T-15` | Aplicar las cuatro enmiendas de `plan.md` §8 | `T-13` | Los cuatro documentos con su fila de control de cambios | **Hecha el 02-09-2026** |

## 2. Orden de ejecución

**`T-02` es la única tarea irreversible del módulo y por eso va con `T-01`, no después.** Dejar el borrado para más adelante habría dado una ventana en la que la tabla tiene la forma nueva y filas con el significado viejo — que es exactamente la situación que la migración existe para evitar.

**`T-03` parece prescindible y no lo es.** Es la restricción única redundante con la clave primaria, y sin ella `RF-CM-007` **no puede declarar** su clave foránea compuesta. Se hace aquí porque es un cambio sobre esta tabla, aunque quien la necesite sea otro requerimiento.

**`T-08` tiene una verificación que no es la obvia.** No comprueba que la cuenta sea correcta —eso lo hace `CA-CM-002`— sino que la tasa **aparezca una sola vez**. Con un `LEFT JOIN` agrupado mal, cada tasa saldría una vez por producto y el `LIMIT` de la paginación contaría filas del producto cartesiano en lugar de tasas, **devolviendo menos tasas de las pedidas sin que nada fallara**.

## 3. Cobertura de los criterios de aceptación

| Criterio | Tareas |
|---|---|
| `CA-CM-001` | `T-09`, `T-11`, `T-13` |
| `CA-CM-002` | `T-08`, `T-10`, `T-13` |
| `CA-CM-003` | `T-04`, `T-05`, `T-10`, `T-13` |
| `CA-CM-004` | `T-01`, `T-13` |
| `CA-CM-005` | `T-04`, `T-13` |
| `CA-CM-006`, `CA-CM-007` | `T-09`, `T-13` |
| `CA-CM-008` | `T-10`, `T-13` |

## 4. Bloqueos

Ninguno.

**Queda una deuda que no bloquea nada**: esta tripleta se escribió **después** del código, invirtiendo el Art. I.6. Está declarada en `spec.md`, en `requirements.md` v0.87.0 y en la matriz.

## 5. Definición de terminado

- Las quince tareas `Hecha` con su verificación pasando.
- `./mvnw clean verify` en verde. **Comprobado el 02-09-2026**: 278 unitarias y 876 de integración.
- La matriz y el contrato publicado al día.
