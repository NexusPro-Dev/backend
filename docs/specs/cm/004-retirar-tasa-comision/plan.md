# PLAN — `RF-CM-004` Retirar una tasa de comisión

| Campo | Valor |
|---|---|
| Requerimiento | `RF-CM-004` |
| Especificación | [`spec.md`](spec.md) |
| `spec.md` aprobada el | 02-09-2026 |
| Versión | 0.2.0 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 02-09-2026 |

!!! info "Qué va en este documento"

    **Cómo se construye.** Las decisiones técnicas que la especificación deliberadamente no toma.

El comportamiento es el de [`spec.md`](spec.md) y no se repite. La mecánica común la fijó el plan de [`RF-CM-001`](../001-registrar-tasa-comision-rol/plan.md) y **este documento la hereda sin repetirla**.

---

## 1. Enfoque

Eliminación lógica con motivo, calcada de `RF-PM-006`: se marca la fila, se emite el registro de eliminación con la instantánea de lo retirado, y no se toca nada más.

**Lo propio de este plan es una comprobación que no estaba prevista**, y conviene explicar de dónde salió. El esquema declara `product_commission_rates` **sin retiro lógico** —una asociación es configuración vigente, no un hecho del pasado que conservar—, y `commission_rates` **con** retiro lógico. Esas dos decisiones son correctas por separado y **juntas abren un agujero**: al marcar una tasa como retirada, su asociación sigue existiendo, y la resolución —que filtra las retiradas— deja de encontrar tarifa. **El producto deja de pagar y ninguna fila está mal.**

Una clave foránea no lo evita: **no distingue una fila viva de una retirada lógicamente**. Por eso `RN-CM-015` vive en el caso de uso y no en el motor, y por eso este plan la explica en lugar de darla por obvia.

## 2. Cambios de esquema

**Ninguno.** `deleted_at` está en la tabla desde `V44` y `V48` no la toca.

**Lo que sí hace falta es una consulta nueva**, no una columna: saber si la tasa tiene asociaciones. Se resuelve con una cuenta sobre `product_commission_rates`, y se declara en el **puerto de escritura** y no en el de consulta, porque quien la necesita es un caso de uso que escribe y su respuesta forma parte de la decisión de escribir.

## 3. Componentes afectados

| Capa | Componente | Nuevo / Modificado | Responsabilidad |
|---|---|---|---|
| `domain/models` | `CommissionRate` | **Modificado** | `delete(...)`, que **devuelve si hubo cambio** |
| `domain/repository` | `CommissionRateRepository` | **Modificado** | Gana `tieneAsociaciones(...)` |
| `domain/service` | `DeleteCommissionRateService` | **Rehecho** | Caso de uso, con la comprobación de `RN-CM-015` |
| `application` | `DeleteCommissionRateRequest` | Sin cambios | El motivo |
| `interfaces` | `CommissionRateController` | **Modificado** | `POST /api/v1/commission-rates/{id}/deletion` |

**El retiro es un `POST` a un subrecurso y no un `DELETE`**, por lo mismo que en `PM`: `DELETE` no lleva cuerpo de forma interoperable, y aquí el **motivo es obligatorio** (Art. V.13). Un motivo en la cadena de consulta sería un texto libre en el registro de peticiones de cualquier intermediario.

**`delete(...)` devuelve si hubo cambio** y no lanza por sí solo, aunque el caso de uso compruebe antes: retirar dos veces con dos motivos distintos dejaría el segundo escrito sobre un hecho anterior, y que el agregado lo devuelva hace que ese fallo no dependa de acordarse de comprobarlo.

**`DeleteCommissionRateRequest` lo comparten las tasas de rol y las personalizadas**, porque es un motivo y nada más. Duplicarlo habría dado dos esquemas idénticos en el contrato publicado.

## 4. Contrato de API

`POST /api/v1/commission-rates/{id}/deletion` · `204 No Content`.

| Estado | Cuándo |
|---|---|
| `400` | `VAL-007`, `VAL-008`: motivo ausente, en blanco o demasiado largo |
| `403` | Sin el permiso `commissions:delete` |
| `404` | `EX-001`: la tasa no existe |
| `409` | `EX-002`: ya estaba retirada · **`EX-003`: sigue asociada a algún producto** |

**`EX-002` es `409` y no `404`**: la tasa existe, y decir que no existe escondería que el retiro **ya ocurrió**.

**`EX-003` comparte el `409` con `EX-002`, y llevan códigos de error distintos** —`EX-002` y `EX-005`— porque las acciones que corresponden no se parecen en nada: ante uno no hay nada que hacer, ante el otro hay que desasociar. **El mensaje explica el porqué y no solo el qué**, porque quien lo recibe podría concluir que el sistema es caprichoso.

## 5. Autorización

Permiso `commissions:delete`. Alcance global explícito.

**Es el único de los cuatro permisos que gobierna una sola operación de este módulo** — dos, contando la de las personalizadas. Desasociar, que es la vía no destructiva, va con `commissions:update`, y esa asimetría es deliberada: destruir y dejar de aplicar no deberían necesitar el mismo permiso.

## 6. Auditoría

Registro de **eliminación**, no de cambios: quién, cuándo, **por qué**, y la **instantánea** de la tasa retirada (Art. V.7 y V.13), de tipo lógico.

La instantánea ya no lleva vigencia —no la hay— y se reduce a rol y porcentaje. **Es la misma que emite el alta**, y eso permite comparar el estado con el que nació y aquel con el que se retiró.

## 7. Transaccionalidad

`@Transactional`. La comprobación de asociaciones y el retiro ocurren **en la misma transacción**, y eso es lo que cierra la ventana que `spec.md` §13 menciona: asociar exige que la tasa esté viva, de modo que las dos operaciones no pueden dejar el sistema en el estado que `RN-CM-015` evita.

**La vigencia no se toca** — no porque se decida no tocarla, sino porque ya no existe en esta tabla. El criterio que la protegía **se conserva en `RF-CM-006`**, que es donde sigue habiendo vigencia que un retiro podría cerrar «de paso».

## 8. Impacto sobre otros módulos

**Ninguno en el código.**

**Una enmienda ya aplicada** en el mismo pase que este plan: `requirements/cm.md` v0.5.0 declara `RN-CM-015` en §5.1 y explica en §5.2 por qué es crítica y por qué su origen es distinto al de las demás.

## 9. Alternativas consideradas

| Alternativa | Por qué se descartó |
|---|---|
| **Retirar las asociaciones en cascada** | Destruye configuración que nadie pidió destruir, y en silencio: quien retira una tasa por una errata se llevaría por delante veinte decisiones de negocio |
| **Dejar las asociaciones apuntando a la tasa retirada** | Es el defecto que `RN-CM-015` existe para evitar: el producto deja de pagar y **ninguna fila está mal** |
| Dar retiro lógico a la asociación | Convierte una configuración vigente en un historial que nadie va a consultar, y obliga a filtrar por `deleted_at` en la consulta más caliente del módulo. Lo que hay que conservar del pasado lo conserva la liquidación (`RN-CM-008`) |
| Declarar `RN-CM-015` con una clave foránea | **No se puede.** Una clave foránea no distingue una fila viva de una retirada lógicamente |
| Retiro idempotente | Dos motivos distintos sobre un mismo hecho hacen que el registro mienta sobre por qué se retiró |
| Deshacer el retiro | Un retiro con motivo es un hecho registrado. Si la tasa vuelve a hacer falta, se declara de nuevo — son dos campos |
| Borrado físico | Dejaría sin explicación cualquier liquidación que se hubiera apoyado en ella |
| `DELETE` con el motivo en la cadena de consulta | El motivo acabaría en el registro de peticiones, y `DELETE` con cuerpo no es interoperable |

## 10. Riesgos

| # | Riesgo | Mitigación |
|---|---|---|
| 1 | **Retirar una tasa asociada deje un producto sin comisionar en silencio** | `RN-CM-015`. Es el riesgo que este plan existe para cerrar |
| 2 | Quien retira una tasa que rige sobre veinte productos crea que el sistema le pone trabas | El mensaje del `409` **explica el porqué**, no solo el qué |
| 3 | Dos retiros simultáneos escriban dos registros de eliminación | El agregado devuelve si hubo cambio, y solo se registra cuando lo hubo |
| 4 | Alguien retire buscando «dejar de pagar» y no encuentre `RF-CM-008` | El contrato publicado de esta operación **nombra la alternativa** en su descripción |

## 11. Estrategia de prueba

| Qué | Nivel | Detalle |
|---|---|---|
| Retiro con motivo | Integración | `CA-CM-029`, `CA-CM-030`: la fila permanece y el registro lleva instantánea |
| Efecto sobre la resolución y el listado | Integración | `CA-CM-031` |
| **`RN-CM-015`** | Integración | `CA-CM-032`: con una asociación viva, `409` **y la tasa sigue viva** |
| **La secuencia correcta** | Integración | `CA-CM-033`: desasociar y luego retirar funciona. Es la que demuestra que la regla **acota y no impide** |
| Motivo obligatorio | Integración | `CA-CM-034`: ausente y en blanco, y **sin retirar nada** |
| No idempotencia | Integración | `CA-CM-035` |
| Tasa inexistente | Integración | `CA-CM-036` |
| Una retirada no se asocia | Integración | `CA-CM-037`, y vive en la suite de `RF-CM-007` porque es la otra mitad de la misma invariante |

**`CA-CM-032` y `CA-CM-033` son una pareja y no dos pruebas sueltas.** La primera sola dejaría demostrado que el sistema rechaza; la segunda es la que demuestra que **hay una salida**, y sin ella la regla sería indistinguible de un requerimiento roto.
