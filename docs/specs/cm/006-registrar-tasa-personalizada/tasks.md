# TASKS — `RF-CM-006` Registrar la tasa personalizada de una persona

| Campo | Valor |
|---|---|
| Requerimiento | `RF-CM-006` |
| Plan | [`plan.md`](plan.md), aprobado el 02-09-2026 |
| Versión | 0.1.0 |
| Estado | **En revisión** |
| Autor | Responsable técnico |
| Aprobadas por | Pendiente |
| Fecha de aprobación | Pendiente |
| Issue | Pendiente de crear |
| Rama | `feature/flujos-de-pm-y-cm` |

!!! info "Qué va en este documento"

    **En qué pasos se construye** lo que `plan.md` decidió, con su dependencia y su verificación.

!!! warning "Las tareas están hechas antes que este documento"

    El requerimiento se construyó el 02-09-2026 **sin tripleta previa** —excepción al Art. I.1, declarada en `spec.md`— y esta lista viene detrás. **No planifica: registra.**

---

## 1. Tareas

**Estados:** `Pendiente` · `En curso` · `Hecha` · `Bloqueada`.

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-01` | `V48`: crear `user_commission_rates`, con la vigencia en `date` | `RF-CM-001` · `T-01` | Las dos comprobaciones de rango y de orden están en la tabla | **Hecha el 02-09-2026** |
| `T-02` | `V48`: el `EXCLUDE` con **rango cerrado** y **parcial sobre las vivas** | `T-01` | Dos periodos que comparten el día de corte chocan | **Hecha el 02-09-2026** |
| `T-03` | La comprobación de vigencia con **la rama `IS NULL` delante y explícita** | `T-01` | Sin ella, toda tasa indefinida pasaría sin comprobarse | **Hecha el 02-09-2026** |
| `T-04` | `UserCommissionRate`: el agregado, con **los dos nulos opuestos** | `T-01` | Vaciar el fin se cumple; vaciar el porcentaje lanza | **Hecha el 02-09-2026** |
| `T-05` | `delete(...)` **sin tocar la vigencia**, y devolviendo si hubo cambio | `T-04` | Tras retirar, `valid_to` sigue como estaba | **Hecha el 02-09-2026** |
| `T-06` | Puerto y adaptador de escritura, con **el bloqueo por persona** | `T-02` | Dos altas simultáneas no se interbloquean | **Hecha el 02-09-2026** |
| `T-07` | **Traducción por `SQLState` `23P01`**, no por el nombre de la restricción | `T-06` | El solapamiento sale como `409`, nunca `500` | **Hecha el 02-09-2026** |
| `T-08` | Atrapar `RuntimeException` y no `PersistenceException` | `T-07` | El fallo se traduce salga por donde salga el volcado | **Hecha el 02-09-2026** |
| `T-09` | `RegisterUserCommissionRateService`, **sin ninguna comprobación de rol** | `T-04`, `T-06` | Se admite a quien no porta rol vendedor | **Hecha el 02-09-2026** |
| `T-10` | `UpdateUserCommissionRateService`, con **el bloqueo antes de tocar la entidad** | `T-04`, `T-06` | Y el volcado explícito antes de auditar | **Hecha el 02-09-2026** |
| `T-11` | `DeleteUserCommissionRateService`, con motivo y **la instantánea antes de retirar** | `T-05` | La instantánea conserva la vigencia | **Hecha el 02-09-2026** |
| `T-12` | Los DTO, con el fin de vigencia **vacío y presente** | `T-09` | El nulo llega como nulo, no ausente | **Hecha el 02-09-2026** |
| `T-13` | `UserCommissionRateController` con las cuatro operaciones | `T-09`, `T-10`, `T-11`, `T-12` | `201`, `200`, `204`, y `400`/`403`/`404`/`409`/`422` | **Hecha el 02-09-2026** |
| `T-14` | Pruebas de los criterios de `spec.md` §12 | `T-13` | `CA-CM-051` a `CA-CM-062` | **Hecha el 02-09-2026** |
| `T-15` | **Prueba de que se admite a quien no vende** | `T-14` | `CA-CM-053` | **Hecha el 02-09-2026** |
| `T-16` | **Prueba del día de corte** | `T-14` | `CA-CM-055`. Es la que verifica el **rango cerrado** | **Hecha el 02-09-2026** |
| `T-17` | **Prueba de que retirar libera los días** | `T-14` | `CA-CM-058`. Es la que verifica la restricción **parcial** | **Hecha el 02-09-2026** |
| `T-18` | **Prueba concurrente**: dos altas simultáneas del mismo periodo | `T-14` | Una `201`, otra `409`, **ninguna `500`**, y **una sola fila** | **Hecha el 02-09-2026** |
| `T-19` | Pruebas unitarias del agregado | `T-04` | Vigencia de un día, orden invertido, instantánea | **Hecha el 02-09-2026** |
| `T-20` | Documentación OpenAPI, **con el aviso de que no lleva rol y qué implica** | `T-13` | La descripción lo dice | **Hecha el 02-09-2026** |
| `T-21` | Actualizar la matriz de `docs/requirements.md` y `cm.md` §4 | `T-14` | La fila registra la excepción al Art. I.1 | **Hecha el 02-09-2026** |

## 2. Orden de ejecución

**`T-16`, `T-17` y `T-18` prueban tres decisiones del esquema y no tres comportamientos.** Es lo que las distingue del resto de `T-14`:

| Prueba | Qué decisión verifica | Qué pasaría si esa decisión estuviera mal |
|---|---|---|
| `T-16` | El rango lleva **los dos extremos incluidos** | El día de corte quedaría cubierto dos veces, y la resolución dejaría de ser determinista **ese día concreto** |
| `T-17` | La restricción es **parcial sobre las vivas** | Retirar dejaría el periodo inutilizable **para siempre**, y nada más fallaría |
| `T-18` | La regla vive **en el motor** | Dos peticiones simultáneas insertarían las dos, y las demás pruebas seguirían en verde |

Las tres comparten una propiedad: **su fallo no se parece a su causa**. Por eso se enumeran aparte en lugar de diluirse en la lista de criterios.

**`T-09` es una tarea de NO hacer algo**, y por eso está escrita. El caso de uso **no comprueba el rol** de la persona, y eso no es un olvido: es la decisión del responsable del proyecto de que la tasa sea de la persona y punto. Sin esta línea, quien lea el código podría «arreglarlo» añadiendo la comprobación que el modelo anterior tenía.

**`T-10` tiene dos ordenaciones que hay que respetar y ninguna se ve leyendo el resultado**: el bloqueo antes de tocar la entidad, y el volcado antes de auditar. Las dos existen por defectos vividos y las dos vuelven a producirse si alguien reordena «para que quede más limpio».

## 3. Cobertura de los criterios de aceptación

| Criterio | Tareas |
|---|---|
| `CA-CM-051`, `CA-CM-052` | `T-09`, `T-12`, `T-14` |
| `CA-CM-053` | `T-09`, `T-15` |
| `CA-CM-054` | `T-02`, `T-07`, `T-14` |
| `CA-CM-055` | `T-02`, `T-16` |
| `CA-CM-056`, `CA-CM-057` | `T-02`, `T-14` |
| `CA-CM-058` | `T-02`, `T-17` |
| `CA-CM-059` | `T-05`, `T-11`, `T-14` |
| `CA-CM-060` | `T-04`, `T-10`, `T-14`, `T-19` |
| `CA-CM-061`, `CA-CM-062` | `T-10`, `T-12`, `T-14` |

## 4. Bloqueos

Ninguno.

**Queda declarada una consecuencia que no es un bloqueo y que nadie va a cerrar desde aquí:** una tasa personalizada **sigue pagando** a quien deje de vender. Está en `spec.md` §13 y en `RF-CM-005` `FA-003`, y la forma de cerrarla es un acto deliberado.

## 5. Definición de terminado

- Las veintiuna tareas `Hecha` con su verificación pasando.
- `./mvnw clean verify` en verde, **incluida la concurrente de `T-18`**. Comprobado el 02-09-2026: 278 unitarias y 876 de integración.
- La matriz, `cm.md` y el contrato publicado al día.
