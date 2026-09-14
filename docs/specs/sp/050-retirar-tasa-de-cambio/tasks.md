# TASKS — `RF-SP-050` Retirar una tasa de cambio

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-050` |
| Especificación | [`spec.md`](spec.md) |
| Plan | [`plan.md`](plan.md), aprobado el 07-09-2026 |
| Estado | **En revisión** |
| Issue | Pendiente de crear |
| Rama | `feature/tasas-de-cambio` |
| Autor | Responsable técnico |

---

## 1. Tareas

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-01` | `application/DeleteExchangeRateRequest`: solo el motivo, obligatorio y con longitud acotada | `RF-SP-047 · T-05` | Sin motivo devuelve `400` y **no retira nada** (`CA-SP-566`) | Pendiente |
| `T-02` | `ExchangeRate.delete(...)`: marca `deleted_at`, **no toca el estado** y devuelve si hubo cambio | `RF-SP-047 · T-03` | Unitaria: retirar dos veces devuelve `false` la segunda, y `is_active` **no cambia** | Pendiente |
| `T-03` | `ExchangeRate.instantanea()`, **la misma que usa el alta** | `T-02` | El mapa del registro de creación y el del de eliminación tienen **las mismas claves** | Pendiente |
| `T-04` | `DeleteExchangeRateService` con el orden de `plan.md` §5, y **la instantánea capturada ANTES de marcar** | `T-01`, `T-03` | `CA-SP-567`: la instantánea dice `is_active = true` en una tasa que estaba activa. Capturada después diría lo contrario y **nada fallaría** | Pendiente |
| `T-05` | La marca y el registro de eliminación **en la misma transacción** | `T-04` | Una prueba que fuerza el fallo del segundo comprueba que **la marca tampoco queda** | Pendiente |
| `T-06` | `POST /api/v1/exchange-rates/{id}/deletion` con `exchange-rates:delete` sobre el método | `T-04` | `403` sin el permiso (`CA-SP-571`), `404` ante una ya retirada (`CA-SP-570`) | Pendiente |
| `T-07` | Pruebas de API de los siete criterios de `spec.md` §12 | `T-06` | La suite cubre `CA-SP-565` a `CA-SP-571` | Pendiente |
| `T-08` | **Prueba de liberación del periodo**: retirar una tasa y registrar otra sobre los mismos días | `T-06` | `CA-SP-568`. **Es la que prueba que el `WHERE` del `EXCLUDE` está donde debe**: sin él, el periodo quedaría bloqueado para siempre y nada más fallaría | Pendiente |
| `T-09` | **Prueba de que el estado no se toca** | `T-06` | `CA-SP-569`: una tasa activa se retira **activa**. Sin ella, desactivar «de paso» pasaría inadvertido y arruinaría el registro de eliminación | Pendiente |
| `T-10` | Prueba concurrente: **dos retiros simultáneos de la misma tasa** | `T-06` | Uno retira y el otro recibe `404` | Pendiente |
| `T-11` | Documentación OpenAPI del endpoint, **declarando que es un `POST` sobre un subrecurso y por qué no un `DELETE`** | `T-07` | El contrato declara el `200`, el `400`, el `403` y el `404` | Pendiente |
| `T-12` | Actualizar la matriz de `docs/requirements.md` | `T-07` | La fila de `RF-SP-050` refleja el estado | Pendiente |

## 2. Orden de ejecución

`T-01`, `T-02` y `T-03` son independientes y baratas. `T-04` es la que las junta, y **es donde se comete el error que este requerimiento existe para no cometer**: capturar la instantánea después de marcar.

**`T-08` y `T-09` no son opcionales.** Las dos comprueban cosas que **no fallan si se hacen mal**: el periodo que no se libera y el estado que se toca de paso.

## 3. Cobertura de los criterios de aceptación

| Criterio | Tareas |
|---|---|
| `CA-SP-565` | `T-02`, `T-04`, `T-07` |
| `CA-SP-566` | `T-01` |
| `CA-SP-567` | `T-03`, `T-04` |
| `CA-SP-568` | `T-08` |
| `CA-SP-569` | `T-09` |
| `CA-SP-570` | `T-02`, `T-06` |
| `CA-SP-571` | `T-06` |

## 4. Bloqueos

| # | Bloqueo | Desde | Responsable | Estado |
|---|---|---|---|---|
| 1 | Depende de `RF-SP-047`: sin tabla ni agregado no hay nada que retirar | 07-09-2026 | Responsable técnico | Abierto |

## 5. Definición de terminado

- [ ] Todas las tareas en estado `Hecha`.
- [ ] Todos los criterios de aceptación con prueba automatizada en verde.
- [ ] `mvn verify` en verde en local.
- [ ] El retiro escribe su registro de eliminación **con motivo e instantánea**, en la misma transacción.
- [ ] Los endpoints nuevos declaran su permiso.
- [ ] El contrato OpenAPI coincide con el comportamiento real, **prosa incluida**.
- [ ] Documentación afectada actualizada en el mismo Pull Request.
- [ ] Matriz de trazabilidad actualizada.
- [ ] Pull Request aprobado por alguien distinto del autor e integrado.
