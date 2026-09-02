# TASKS — `RF-CM-004` Retirar una tasa de comisión

| Campo | Valor |
|---|---|
| Requerimiento | `RF-CM-004` |
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
| `T-01` | `CommissionRate.delete(...)`: marca el retiro y **devuelve si hubo cambio** | `RF-CM-001` · `T-04` | Retirar dos veces devuelve `false` la segunda | **Hecha el 02-09-2026** |
| `T-02` | `tieneAsociaciones(...)` en el **puerto de escritura**, no en el de consulta | `RF-CM-007` · `T-01` | Una tasa con una asociación devuelve verdadero | **Hecha el 02-09-2026** |
| `T-03` | `DeleteCommissionRateService`: motivo, existencia, no retirada, **y `RN-CM-015`** | `T-01`, `T-02` | El motivo vacío se rechaza **antes** de cualquier consulta | **Hecha el 02-09-2026** |
| `T-04` | El rechazo de `RN-CM-015` **explica el porqué**, no solo el qué | `T-03` | El mensaje nombra la consecuencia: el producto dejaría de comisionar en silencio | **Hecha el 02-09-2026** |
| `T-05` | La comprobación y el retiro, **en la misma transacción** | `T-03` | No hay ventana entre comprobar y marcar | **Hecha el 02-09-2026** |
| `T-06` | `POST /api/v1/commission-rates/{id}/deletion` | `T-03` | `204`, `400`, `403`, `404`, `409` | **Hecha el 02-09-2026** |
| `T-07` | Registro de eliminación **lógica** con quién, cuándo, por qué y la instantánea | `T-03` | La instantánea es la misma forma que emite el alta | **Hecha el 02-09-2026** |
| `T-08` | El contrato publicado **nombra `RF-CM-008` como la alternativa no destructiva** | `T-06` | La descripción del endpoint la menciona | **Hecha el 02-09-2026** |
| `T-09` | Pruebas de los criterios de `spec.md` §12 | `T-06` | `CA-CM-029` a `CA-CM-037` | **Hecha el 02-09-2026** |
| `T-10` | **Prueba de `RN-CM-015` en su pareja**: rechazo con asociación viva, y éxito tras desasociar | `T-09` | `CA-CM-032` y `CA-CM-033` | **Hecha el 02-09-2026** |
| `T-11` | Actualizar la matriz de `docs/requirements.md` y `cm.md` §5 con `RN-CM-015` | `T-09` | Las dos filas de control de cambios | **Hecha el 02-09-2026** |
| `T-12` | Prueba concurrente: dos retiros simultáneos, **un solo registro** | `T-09` | Uno `204` y otro `409` | `Pendiente` |

## 2. Orden de ejecución

**`T-02` depende de un requerimiento posterior, y esa inversión es real.** La comprobación de `RN-CM-015` necesita que exista `product_commission_rates`, que la crea `RF-CM-007`. En el orden de construcción la asociación se hizo antes que el retiro, aunque su número sea mayor — es el mismo caso que `RF-PM-003`, que se implementó después de `RF-PM-006` porque necesitaba una eliminación registrada contra la que probarse.

**`T-04` es una tarea de redacción y está aquí a propósito.** Un `409` que solo dijera «la tasa está asociada» dejaría a quien lo recibe concluyendo que el sistema pone trabas. El mensaje tiene que decir **qué pasaría si no las pusiera**, porque el defecto que evita es invisible.

**`T-10` prueba la regla en pareja y no sola.** Verificar solo el rechazo dejaría demostrado que el sistema dice que no; lo que hay que demostrar es que **hay una salida**, o la regla sería indistinguible de un requerimiento roto.

**`T-12` sigue pendiente**, como lo estaba en la v0.1.0. No es un olvido: la no idempotencia está probada en secuencia (`CA-CM-035`) y lo que falta es la carrera. Queda registrada para que no desaparezca.

## 3. Cobertura de los criterios de aceptación

| Criterio | Tareas |
|---|---|
| `CA-CM-029`, `CA-CM-030` | `T-03`, `T-07`, `T-09` |
| `CA-CM-031` | `T-03`, `T-09` |
| `CA-CM-032` | `T-02`, `T-03`, `T-04`, `T-10` |
| `CA-CM-033` | `T-10` |
| `CA-CM-034` | `T-03`, `T-09` |
| `CA-CM-035` | `T-01`, `T-09`, `T-12` |
| `CA-CM-036` | `T-03`, `T-09` |
| `CA-CM-037` | `RF-CM-007` · `T-06` |

## 4. Bloqueos

Ninguno.

## 5. Definición de terminado

- Once de las doce tareas `Hecha` con su verificación pasando. **`T-12` queda pendiente y declarada.**
- `./mvnw clean verify` en verde. **Comprobado el 02-09-2026**: 278 unitarias y 876 de integración.
- La matriz, `cm.md` y el contrato publicado al día.
