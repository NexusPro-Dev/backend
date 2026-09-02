# PLAN — `RF-CM-003` Corregir el porcentaje de una tasa

| Campo | Valor |
|---|---|
| Requerimiento | `RF-CM-003` |
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

Corrección parcial contra el agregado, con la distinción de **tres estados** en cada campo —ausente, presente en vacío, presente con valor— que el proyecto ya usa en `RF-SP-027` y `RF-PM-004`.

**Lo propio de este plan es que la corrección se quedó sin lo que la hacía interesante y ganó otra cosa.** Perdió el fin de vigencia, y con él la carrera contra la restricción de no solapamiento que obligaba a un bloqueo, a un volcado explícito y a traducir una violación del motor. **Todo eso desaparece de aquí y reaparece en `RF-CM-006`**, que es donde queda la vigencia.

Lo que gana es un peso que no tenía: **el registro de auditoría del cambio pasa a ser la única copia del valor anterior en todo el sistema.**

## 2. Cambios de esquema

**Ninguno.** `V48` deja la tabla como esta operación la necesita.

## 3. Componentes afectados

| Capa | Componente | Nuevo / Modificado | Responsabilidad |
|---|---|---|---|
| `domain/models` | `CommissionRate` | **Rehecho** | `update(...)` con un solo campo corregible, que **devuelve qué cambió de verdad** |
| `domain/service` | `UpdateCommissionRateService` | **Rehecho** | Caso de uso; pierde el bloqueo y el volcado defensivo |
| `application` | `UpdateCommissionRateRequest` | **Rehecho** | Un campo corregible y **un inmutable declarado a propósito** |
| `interfaces` | `CommissionRateController` | **Modificado** | `PATCH /api/v1/commission-rates/{id}` |

**El inmutable se declara aunque se rechace**, y esa es la decisión con nombre de este plan. `roleId` no se corrige, pero figura en la petición: sin él, quien intentara cambiarlo leería «propiedad desconocida» y **creería que se equivocó de nombre** en lugar de entender que lo que pide no se puede hacer. Es el criterio de `RF-PM-004` con el tipo y el código de un producto.

**`update(...)` devuelve el mapa de lo que cambió y no un booleano**, porque ese mapa **es** el evento de auditoría: construirlo en el caso de uso obligaría a leer el estado anterior por separado, y ahí es donde se pierde.

## 4. Contrato de API

`PATCH /api/v1/commission-rates/{id}` · `200 OK`.

| Estado | Cuándo |
|---|---|
| `400` | `VAL-002`, `VAL-003`, `VAL-009`, `VAL-010` |
| `403` | Sin el permiso `commissions:update` |
| `404` | `EX-001`: no existe, o está retirada |

**Ya no hay `409`.** La v0.1.0 lo declaraba para la vigencia solapada, y esa columna desapareció. **Su ausencia es informativa**: dice que esta operación no puede entrar en conflicto con ninguna otra fila.

**`PATCH` y no `PUT`**: se corrige un campo, no se sustituye la tasa. Un `PUT` exigiría enviar el rol —que es inmutable— para no borrarlo.

## 5. Autorización

Permiso `commissions:update`. Alcance global explícito.

Es el mismo permiso que gobierna **asociar y desasociar** (`cm.md` §6), y esa decisión está declarada allí como discutible: asociar cambia lo que se paga tanto como corregir un porcentaje.

## 6. Auditoría

Registro de **cambios**, acción de actualización, con `before` y `after` de cada campo que cambió de verdad.

!!! danger "Este registro dejó de ser un complemento"

    Con vigencia, el valor anterior seguía existiendo en la fila cerrada y la auditoría solo lo acompañaba. **Sin vigencia, la fila se sobrescribe y este registro es el único sitio del sistema donde queda el porcentaje que había.**

    De modo que **si esta escritura fallara en silencio, el dato se perdería sin rastro**. No se degrada a mejor esfuerzo: va dentro de la misma transacción que la corrección.

**Solo se emite si hubo cambio.** Una petición que declara lo que la tasa ya dice no produce evento, porque no lo hubo.

## 7. Transaccionalidad

`@Transactional`. La entidad está gestionada y el `UPDATE` sale con la confirmación.

**Se conserva el volcado explícito antes de auditar**, aunque aquí ya no haya ninguna violación que traducir. No es inercia: fija **el orden** entre la escritura de la tasa y la de la auditoría, en lugar de dejarlo a lo que decida el proveedor de persistencia. Con el registro convertido en la única copia del valor anterior, ese orden importa.

## 8. Impacto sobre otros módulos

**Ninguno.**

## 9. Alternativas consideradas

| Alternativa | Por qué se descartó |
|---|---|
| **Conservar la operación «cambiar a partir de una fecha»** | No hay dónde escribir la fecha. Mantenerla exigiría devolver la vigencia al catálogo, que es la decisión que el responsable del proyecto tomó al revés |
| Ignorar el rol si llega, en vez de rechazarlo | Haría creer que el cambio se aplicó, y quien lo pidió seguiría creyendo que la tasa paga a otro rol |
| No declarar el rol en la petición | Quien intentara cambiarlo leería «propiedad desconocida» y buscaría el error en el nombre del campo |
| Permitir cambiar el rol | La tasa se convierte en otra y **arrastra sus asociaciones** a un rol que nadie eligió. Registrar una tasa nueva es la vía correcta |
| Tratar el campo ausente y el vacío igual | Son dos peticiones distintas y la segunda pide algo imposible. Fundirlas obliga a elegir un comportamiento que miente sobre la otra |
| Comparar los porcentajes como texto | `10.00` y `10.0000` son el mismo porcentaje. Llenaría la auditoría de cambios que no cambian nada |
| **Impedir corregir una tasa asociada** | Sería coherente con `RN-CM-015`, y es la restricción equivocada: retirar destruye la tasa, corregir la mantiene. Obligaría a desasociar veinte productos para arreglar una errata |

## 10. Riesgos

| # | Riesgo | Mitigación |
|---|---|---|
| 1 | **Corregir reescriba lo ya pagado sin dejar rastro** | **No se mitiga en este módulo.** Depende de `RN-CM-008` y del módulo de liquidación, que no existe. Declarado en `spec.md` y en `cm.md` §1.4 |
| 2 | Una corrección cambie en silencio lo que pagan veinte productos | La respuesta devuelve **cuántos son**, de modo que el efecto es visible en el momento de hacerlo |
| 3 | Alguien busque «cerrar la vigencia» y no la encuentre | `spec.md` §13 lo declara: la operación no existe, y la alternativa tampoco conserva el historial |

## 11. Estrategia de prueba

| Qué | Nivel | Detalle |
|---|---|---|
| Corrección y respuesta resuelta | Integración | `CA-CM-021` |
| **El valor anterior solo sobrevive en la auditoría** | Integración | `CA-CM-022`: se comprueba **en la tabla** que el 10 ya no está, y **en el registro** que sí |
| Petición que no cambia nada | Integración | `CA-CM-023`: la marca de modificación **no se mueve** |
| Escala distinta, mismo porcentaje | Unitaria | `CA-CM-024`: no produce cambio |
| Rechazo del inmutable | Integración | `CA-CM-025`: `400`, y **el rol en base sigue siendo el de antes** |
| Vaciar el porcentaje | Integración y unitaria | `CA-CM-026` |
| Petición vacía | Integración | `CA-CM-027` |
| Tasa retirada | Integración | `CA-CM-028`: `404` |

**`CA-CM-022` es la prueba que justifica este requerimiento**, y su forma importa: no basta con verificar que el registro se escribió. Comprueba **las dos mitades** —que el valor anterior desapareció de la tabla y que sobrevivió en la auditoría—, porque lo que se está afirmando es que ese registro es la única copia.
