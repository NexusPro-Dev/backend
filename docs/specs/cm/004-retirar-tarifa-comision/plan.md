# PLAN — `RF-CM-004` Retirar una tarifa de comisión

| Campo | Valor |
|---|---|
| Requerimiento | `RF-CM-004` |
| Especificación | [`spec.md`](spec.md) |
| `spec.md` aprobada el | 28-08-2026 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 28-08-2026 |

!!! info "Qué va en este documento"

    **Cómo se construye.** Las decisiones técnicas que la especificación deliberadamente no toma.

    **Prueba de pertenencia:** si al negocio no le importa ni lo entendería, va aquí.

El comportamiento es el de [`spec.md`](spec.md) y no se repite. La mecánica común la fijó el plan de [`RF-CM-001`](../001-registrar-tarifa-comision/plan.md) y **este documento la hereda sin repetirla**.

---

## 1. Enfoque

Eliminación lógica con motivo, calcada de `RF-PM-006`: se marca la fila, se emite el registro de eliminación con la instantánea de lo retirado, y **no se toca nada más**.

Lo propio es una consecuencia del esquema que conviene ver antes de construirlo: **retirar libera días**. La restricción de no solapamiento es **parcial sobre las vivas** (`RF-CM-001` §2), de modo que marcar `deleted_at` saca la fila del índice y su periodo queda disponible sin ninguna operación adicional. `CA-CM-037` lo verifica.

## 2. Cambios de esquema

**Ninguno.** `deleted_at` la crea `V44`, y la restricción ya está escrita para ignorar las retiradas.

## 3. Componentes afectados

| Capa | Componente | Nuevo / Modificado | Responsabilidad |
|---|---|---|---|
| `domain/models` | `CommissionRate` | **Modificado** | Gana `delete(...)`, que **devuelve si hubo cambio** |
| `domain/service` | `DeleteCommissionRateService` | Nuevo | Caso de uso: comprueba, retira y registra la eliminación |
| `application` | `DeleteCommissionRateRequest` | Nuevo | El motivo, con Bean Validation (`VAL-007`, `VAL-008`) |
| `interfaces` | `CommissionRateController` | **Modificado** | Gana `POST /api/v1/commission-rates/{id}/deletion` |

**El retiro es un `POST` a un subrecurso y no un `DELETE`**, por lo mismo que en `PM`: `DELETE` no lleva cuerpo de forma interoperable, y aquí el **motivo es obligatorio** (Art. V.13). Un motivo en la cadena de consulta sería un texto libre en el registro de peticiones.

**`delete(...)` devuelve si hubo cambio** y no lanza por sí solo, aunque el caso de uso compruebe antes: retirar dos veces con dos motivos distintos dejaría el segundo escrito sobre un hecho anterior, y que el agregado lo devuelva hace que ese fallo no dependa de acordarse de comprobarlo.

## 4. Contrato de API

`POST /api/v1/commission-rates/{id}/deletion` · `204 No Content`.

| Estado | Cuándo |
|---|---|
| `400` | `VAL-007`, `VAL-008`: motivo ausente, en blanco o demasiado largo |
| `403` | Sin el permiso `commissions:delete` |
| `404` | `EX-001`: la tarifa no existe |
| `409` | `EX-002`: ya estaba retirada |

**`EX-002` es `409` y no `404`**: la tarifa existe, y decir que no existe escondería que el retiro **ya ocurrió**, que es justo lo que quien repite la operación necesita saber.

## 5. Autorización

Permiso `commissions:delete`. Alcance global explícito.

## 6. Auditoría

Registro de **eliminación**, no de cambios: quién, cuándo, **por qué**, y la **instantánea** de la tarifa retirada (Art. V.7 y V.13). La instantánea incluye la vigencia, y esa es la razón de §7.

## 7. Transaccionalidad

`@Transactional`. **La vigencia no se toca**, y es la decisión con nombre de este plan.

Cerrar `valid_to` «de paso» al retirar sería fácil de justificar —deja la tabla más ordenada— y **destruiría la evidencia**: el registro de eliminación debe poder decir **qué periodo cubría** lo que se retiró, y si el retiro modificara la vigencia, todas las instantáneas dirían lo mismo y ese dato dejaría de significar nada. Es exactamente el criterio con el que `RF-PM-006` no toca el estado de un producto al retirarlo, y por el mismo motivo: la salvaguarda no puede destruir lo que protege.

`updatedAt` **sí** se mueve: la fila cambió.

## 8. Impacto sobre otros módulos

**Ninguno.**

## 9. Alternativas consideradas

| Alternativa | Por qué se descartó |
|---|---|
| Cerrar la vigencia al retirar | Destruye el dato de qué periodo cubría lo retirado. Ver §7 |
| `DELETE` sin cuerpo, con el motivo en la cadena de consulta | El motivo acabaría en el registro de peticiones, y `DELETE` con cuerpo no es interoperable |
| Retiro idempotente | Dos motivos distintos sobre un mismo hecho hacen que el registro mienta sobre por qué se retiró |
| Deshacer el retiro | Un retiro con motivo es un hecho registrado. Si la tarifa vuelve a hacer falta, se declara de nuevo — y sus días están libres |
| Borrado físico | Dejaría sin explicación cualquier liquidación que se hubiera apoyado en ella |

## 10. Riesgos

| # | Riesgo | Mitigación |
|---|---|---|
| 1 | Retirar la tarifa que rige hoy deja el caso sin comisión sin avisar | Deliberado y declarado en `spec.md` §13: es una decisión legítima, no un descuido |
| 2 | Dos retiros simultáneos escriban dos registros de eliminación | El agregado devuelve si hubo cambio, y solo se registra cuando lo hubo. Cubierto por la prueba concurrente |

## 11. Estrategia de prueba

| Qué | Nivel | Detalle |
|---|---|---|
| Retiro con motivo | Integración | `CA-CM-031`, `CA-CM-032`: la fila permanece y el registro lleva instantánea |
| Efecto sobre la resolución y el listado | Integración | `CA-CM-033`, `CA-CM-034` |
| Motivo obligatorio | Integración | `CA-CM-035`: ausente y en blanco, y **sin retirar nada** |
| No idempotencia | Integración | `CA-CM-036` |
| **Los días quedan libres** | Integración | `CA-CM-037`: tras retirar, se admite otra tarifa que cubra ese periodo. Es la que verifica que la restricción sea parcial |
| **La vigencia no se toca** | Integración | `CA-CM-038`, y que la instantánea la conserve |
| Retiro concurrente | Integración concurrente | Dos retiros simultáneos: uno `204` y otro `409`, y **un solo registro de eliminación** |
