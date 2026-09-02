# TASKS — `RF-CM-003` Corregir el porcentaje de una tasa

| Campo | Valor |
|---|---|
| Requerimiento | `RF-CM-003` |
| Plan | [`plan.md`](plan.md), aprobado el 02-09-2026 |
| Versión | 0.2.0 |
| Estado | **En revisión** |
| Autor | Responsable técnico |
| Aprobadas por | Pendiente |
| Fecha de aprobación | Pendiente |
| Issue | Pendiente de crear |
| Rama | `feature/flujos-de-pm-y-cm` |

!!! info "Qué va en este documento"

    **En qué pasos se construye** lo que `plan.md` decidió, con su dependencia y su verificación.

!!! warning "Las tareas están hechas antes que este documento"

    El código se rehízo el 02-09-2026 y esta lista viene detrás. **No planifica: registra.** La tercera compuerta del Art. I.6 sigue pendiente y por eso el documento está `En revisión`.

---

## 1. Tareas

**Estados:** `Pendiente` · `En curso` · `Hecha` · `Bloqueada`.

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-01` | `CommissionRate.update(...)` con un solo campo, que **devuelve qué cambió de verdad** | `RF-CM-001` · `T-04` | Una corrección que no cambia nada devuelve el mapa vacío | **Hecha el 02-09-2026** |
| `T-02` | Comparar los porcentajes **por valor y no por escala** | `T-01` | `10.0000` sobre `10.00` no produce cambio | **Hecha el 02-09-2026** |
| `T-03` | La marca de modificación **solo se mueve si hubo cambio** | `T-01` | Petición idéntica, misma marca | **Hecha el 02-09-2026** |
| `T-04` | Petición con `roleId` **declarado a propósito** para poder rechazarlo | — | Enviarlo da `VAL-009`, no «propiedad desconocida» | **Hecha el 02-09-2026** |
| `T-05` | `UpdateCommissionRateService`: rechaza inmutables y petición vacía **antes de buscar nada** | `T-01`, `T-04` | Con un identificador inexistente y el rol enviado, gana `VAL-009` | **Hecha el 02-09-2026** |
| `T-06` | **Retirar el bloqueo y la traducción del solapamiento** que la v0.1.0 necesitaba | `T-05` | El caso de uso no llama a ningún bloqueo | **Hecha el 02-09-2026** |
| `T-07` | Conservar el **volcado explícito** antes de auditar, ahora por el orden y no por la traducción | `T-05` | El comentario del código dice cuál de las dos razones vale hoy | **Hecha el 02-09-2026** |
| `T-08` | Registro de auditoría con `before` y `after`, **solo si hubo cambio** | `T-05` | Una petición que no cambia nada no escribe ninguna fila | **Hecha el 02-09-2026** |
| `T-09` | Releer la tasa para devolver el rol resuelto y sus productos asociados | `T-05` | Una sentencia, no una llamada por fila | **Hecha el 02-09-2026** |
| `T-10` | `PATCH /api/v1/commission-rates/{id}` | `T-05`, `T-09` | `200`, `400`, `403`, `404`. **Ningún `409`** | **Hecha el 02-09-2026** |
| `T-11` | Pruebas de los criterios de `spec.md` §12 | `T-10` | `CA-CM-021` a `CA-CM-028` | **Hecha el 02-09-2026** |
| `T-12` | **Prueba de que la auditoría es la única copia** del valor anterior | `T-11` | `CA-CM-022`, en sus **dos mitades**: no está en la tabla, sí en el registro | **Hecha el 02-09-2026** |
| `T-13` | Documentación OpenAPI, **con el aviso de que esta operación borra el pasado** | `T-10` | El contrato publicado lo dice en la descripción | **Hecha el 02-09-2026** |
| `T-14` | Actualizar la matriz de `docs/requirements.md` | `T-11` | La fila de `RF-CM-003` refleja el estado | **Hecha el 02-09-2026** |

## 2. Orden de ejecución

**`T-06` es una tarea de quitar, y merece figurar como tarea.** Se retiran el bloqueo consultivo, la consulta de solapamiento y la traducción de la violación del motor — tres piezas que existían por un defecto medido el 28-08-2026 y que **dejan de tener objeto** cuando la columna que las causaba desaparece. Dejarlas «por si acaso» habría dejado código que parece defender una invariante inexistente, que es peor que no tenerlo.

**`T-12` no es una prueba más de `T-11`, y por eso va aparte.** Verificar que el registro de auditoría se escribió no demuestra nada: lo que hay que demostrar es que **el valor anterior ya no está en ningún otro sitio**. La prueba mira las dos mitades, y es la que da sentido a `RN-CM-008`.

**`T-13` lleva un aviso que ningún otro contrato del proyecto lleva.** La descripción publicada dice que corregir **borra el porcentaje anterior**, porque quien consuma la API desde fuera no tiene forma de deducirlo de la forma del recurso.

## 3. Cobertura de los criterios de aceptación

| Criterio | Tareas |
|---|---|
| `CA-CM-021` | `T-05`, `T-09`, `T-10`, `T-11` |
| `CA-CM-022` | `T-08`, `T-12` |
| `CA-CM-023` | `T-03`, `T-11` |
| `CA-CM-024` | `T-02`, `T-11` |
| `CA-CM-025` | `T-04`, `T-05`, `T-11` |
| `CA-CM-026` | `T-01`, `T-11` |
| `CA-CM-027` | `T-05`, `T-11` |
| `CA-CM-028` | `T-05`, `T-11` |

## 4. Bloqueos

Ninguno para construir.

**Uno declarado que no se puede levantar desde aquí:** mientras no exista el módulo de liquidación, `RN-CM-008` no la cumple nadie y **corregir borra el pasado sin rastro**. No bloquea este requerimiento; bloquea la confianza en lo que produce.

## 5. Definición de terminado

- Las catorce tareas `Hecha` con su verificación pasando.
- `./mvnw clean verify` en verde. **Comprobado el 02-09-2026**: 278 unitarias y 876 de integración.
- La matriz y el contrato publicado al día.
