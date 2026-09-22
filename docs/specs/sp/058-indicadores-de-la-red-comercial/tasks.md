# TASKS — `RF-SP-058` Consultar los indicadores de la red comercial

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-058` |
| Especificación | [`spec.md`](spec.md) |
| Plan | [`plan.md`](plan.md), aprobado el 10-09-2026 |
| Estado | **En revisión** — `T-01` a `T-11` **Hecha** el 10-09-2026 |
| Issue | Pendiente de crear |
| Rama | `feature/venta-de-productos` |
| Autor | Responsable técnico |

---

## 1. Tareas

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-01` | Las **tres consultas planas**: fuerza comercial, cuentas por superior y estado, consumidores por superior | — | `EXISTS` y no `JOIN` sobre `user_roles` — el `JOIN` multiplicaría la cuenta por cada rol | **Hecha el 10-09-2026** |
| `T-02` | `NetworkIndicatorsResponse`: nodo, `own`, `network`, `children`, `totals` y `unassigned` | — | `children` va **siempre**, aunque vaya vacío | **Hecha el 10-09-2026** |
| `T-03` | **La suma**: recorrido en post-orden con registro de visitados | `T-01`, `T-02` | `CA-SP-658`, `CA-SP-659`. `conversion` se **recalcula**, no se promedia | **Hecha el 10-09-2026** |
| `T-04` | `unassigned`: lo que no cuelga de ningún vendedor | `T-03` | `CA-SP-664` | **Hecha el 10-09-2026** |
| `T-05` | `rootId`: la rama, con la persona **como raíz**, y `404` si no es fuerza comercial | `T-03` | `CA-SP-665`, `FA-003` | **Hecha el 10-09-2026** |
| `T-06` | `BrokerAccountController`: `GET /api/v1/broker-accounts/indicators` | `T-05` | `CA-SP-668` | **Hecha el 10-09-2026** |
| `T-07` | **La prueba de la aritmética**, sobre cuatro niveles: cada `own` y cada `network`, uno a uno | `T-06` | `CA-SP-658` a `CA-SP-661` | **Hecha el 10-09-2026** |
| `T-08` | **La prueba que cuadra**: `totals` + `unassigned` contra el total de `GET /api/v1/broker-accounts`, con los mismos datos | `T-07` | `CA-SP-664`. Es la que sostiene el requerimiento | **Hecha el 10-09-2026** |
| `T-09` | Las exclusiones y los bordes: cuenta de vendedor, eliminado en medio, ex-miembro, conversión nula | `T-07` | `CA-SP-661` a `CA-SP-663`, `CA-SP-666`, `CA-SP-667` | **Hecha el 10-09-2026** |
| `T-10` | Documentación OpenAPI: **prosa**, con la advertencia de no sumar la columna de `network` | `T-07` | Se comprueba leyendo `docs/api/openapi.json` | **Hecha el 10-09-2026** |
| `T-11` | Actualizar la matriz de `docs/requirements.md` | `T-07` | La fila de `RF-SP-058` refleja el estado | **Hecha el 10-09-2026** |

## 2. Orden de ejecución

`T-01` y `T-02` primero.

**`T-03` es la tarea del requerimiento** y conviene escribirla con `T-07` delante: la aritmética se equivoca de formas que devuelven números plausibles, y un número plausible no se distingue leyendo el código.

**`T-08` va separada a propósito.** No comprueba un valor sino **una igualdad entre dos endpoints**, y es lo único que detecta un doble conteo o una omisión.

## 3. Cobertura de los criterios de aceptación

| Criterio | Tareas |
|---|---|
| `CA-SP-658`, `CA-SP-659` | `T-03`, `T-07` |
| `CA-SP-660`, `CA-SP-661` | `T-01`, `T-07`, `T-09` |
| `CA-SP-662` | `T-01`, `T-07` |
| `CA-SP-663` | `T-03`, `T-09` |
| `CA-SP-664` | `T-04`, `T-08` |
| `CA-SP-665` | `T-05`, `T-07` |
| `CA-SP-666`, `CA-SP-667` | `T-01`, `T-09` |
| `CA-SP-668` | `T-06`, `T-07` |

## 4. Bloqueos

| # | Bloqueo | Desde | Responsable | Estado |
|---|---|---|---|---|
| 1 | **¿Puede un vendedor consultar los indicadores de su propia red?** Hoy **no**: lo gobierna `broker-accounts:read-indicators` y nada más. Abrirlo obligaría a recortar el árbol a su rama. **No se adivina** — es la misma clase de decisión que `RF-SP-056` dejó abierta con el `supervisorId`, y que acabó pidiéndose al día siguiente | 10-09-2026 | Responsable del proyecto | **Abierto** |
| 2 | **Todos los FTD valen cero hasta que exista el webhook** (`RF-SP-054`). El embudo se verá entero en `pending`. No bloquea: el indicador es correcto y el estado es real | 10-09-2026 | Responsable del proyecto | **Abierto** |
| 3 | **No hay serie temporal.** Es una foto de hoy; medir la evolución es otro requerimiento | 10-09-2026 | Responsable del proyecto | **Abierto** |

## 5. Definición de terminado

- [x] Todas las tareas en estado `Hecha`.
- [x] Todos los criterios de aceptación con prueba automatizada en verde.
- [x] `mvn verify` en verde en local.
- [x] **Los números cuadran con `RF-SP-057`**, comprobado en una prueba y no a ojo.
- [x] El contrato OpenAPI coincide con el comportamiento real, **prosa incluida**.
- [x] Documentación afectada actualizada en el mismo Pull Request.
- [x] Matriz de trazabilidad actualizada.
- [ ] Pull Request aprobado por alguien distinto del autor e integrado.
