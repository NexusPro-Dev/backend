# TASKS — `RF-CM-004` Retirar una tarifa de comisión

| Campo | Valor |
|---|---|
| Requerimiento | `RF-CM-004` |
| Plan | [`plan.md`](plan.md), aprobado el 28-08-2026 |
| Estado | **En revisión** |
| Autor | Responsable técnico |
| Aprobadas por | Pendiente |
| Fecha de aprobación | Pendiente |
| Issue | Pendiente de crear |
| Rama | `feature/modulo-comisiones` |

!!! info "Qué va en este documento"

    **En qué pasos se construye** lo que `plan.md` decidió, con su dependencia y su verificación. Ninguna tarea se da por `Hecha` sin que su verificación pase.

---

## 1. Tareas

**Estados:** `Pendiente` · `En curso` · `Hecha` · `Bloqueada`.

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-01` | `CommissionRate.delete(...)`: marca el retiro y **devuelve si hubo cambio** | `RF-CM-001` · `T-07` | Retirar dos veces devuelve `false` la segunda | `Pendiente` |
| `T-02` | **La vigencia no se toca al retirar**, y `updatedAt` sí se mueve | `T-01` | `valid_from` y `valid_to` quedan como estaban | `Pendiente` |
| `T-03` | `DeleteCommissionRateService`: comprueba, retira y registra la eliminación | `T-01` | El motivo llega al registro, no a la respuesta | `Pendiente` |
| `T-04` | DTO del motivo con `VAL-007` y `VAL-008` | `T-03` | Ausente, en blanco y demasiado largo | `Pendiente` |
| `T-05` | `POST /api/v1/commission-rates/{id}/deletion` | `T-03`, `T-04` | `204`, `400`, `403`, `404`, `409` | `Pendiente` |
| `T-06` | Registro de eliminación con quién, cuándo, por qué y **la instantánea** | `T-03` | La instantánea conserva la vigencia que la tarifa tenía | `Pendiente` |
| `T-07` | Pruebas de los criterios de `spec.md` §12 | `T-05` | `CA-CM-031` a `CA-CM-038` | `Pendiente` |
| `T-08` | **Prueba de que los días quedan libres**: tras retirar, se admite otra tarifa que cubra ese periodo | `T-07` | `CA-CM-037`. Es la que verifica que la restricción del motor sea **parcial** sobre las vivas | `Pendiente` |
| `T-09` | Prueba concurrente: dos retiros simultáneos | `T-07` | Uno `204` y otro `409`, y **un solo registro de eliminación** | `Pendiente` |
| `T-10` | Documentación OpenAPI del endpoint | `T-05` | El cuerpo con motivo y los cinco estados |  `Pendiente` |
| `T-11` | Actualizar la matriz de `docs/requirements.md` | `T-07` | La fila de `RF-CM-004` refleja el estado | `Pendiente` |

## 2. Orden de ejecución

`T-08` merece atención propia: **no prueba este requerimiento, prueba una decisión de `RF-CM-001`** — que la restricción de no solapamiento se declaró parcial sobre las vivas. Si aquella se hubiera declarado sobre todas las filas, retirar dejaría el periodo inutilizable para siempre y **nada más fallaría**. Es exactamente el tipo de defecto que no se ve en la respuesta.

## 3. Cobertura de los criterios de aceptación

| Criterio | Tareas |
|---|---|
| `CA-CM-031`, `CA-CM-032` | `T-03`, `T-06`, `T-07` |
| `CA-CM-033`, `CA-CM-034` | `T-03`, `T-07` |
| `CA-CM-035` | `T-04`, `T-07` |
| `CA-CM-036` | `T-01`, `T-07`, `T-09` |
| `CA-CM-037` | `T-08` |
| `CA-CM-038` | `T-02`, `T-06`, `T-07` |

## 4. Bloqueos

Ninguno.

## 5. Definición de terminado

- Las once tareas `Hecha` con su verificación pasando.
- `./mvnw clean verify` en verde, incluidas `T-08` y la concurrente de `T-09`.
- La matriz y el contrato publicado al día.
