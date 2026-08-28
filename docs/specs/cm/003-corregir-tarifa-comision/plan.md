# PLAN — `RF-CM-003` Corregir una tarifa de comisión

| Campo | Valor |
|---|---|
| Requerimiento | `RF-CM-003` |
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

Una corrección parcial con **tres estados por campo** —ausente, presente en nulo, presente con valor—, exactamente como `RF-PM-004`. Aquí la distinción decide dos comportamientos **opuestos**: quitar el fin de vigencia es una orden que se cumple, quitar el porcentaje se rechaza.

Lo propio es que **corregir la vigencia puede producir un solapamiento**, y por tanto esta operación vuelve a chocar con la restricción del motor — con una diferencia respecto del alta: aquí el `UPDATE` no sale hasta el `commit`.

## 2. Cambios de esquema

**Ninguno.**

## 3. Componentes afectados

| Capa | Componente | Nuevo / Modificado | Responsabilidad |
|---|---|---|---|
| `domain/models` | `CommissionRate` | **Modificado** | Gana `update(...)`, que aplica lo enviado y **devuelve qué cambió** |
| `domain/service` | `UpdateCommissionRateService` | Nuevo | Caso de uso, con el orden de verificación de `spec.md` §8 |
| `application` | `UpdateCommissionRateRequest` | Nuevo | Campos `Patchable`, incluidos los **no corregibles** para poder rechazarlos con su mensaje |
| `interfaces` | `CommissionRateController` | **Modificado** | Gana `PATCH /api/v1/commission-rates/{id}` |

**El diff lo devuelve el agregado**, no el caso de uso comparando antes y después. Un campo que no entre en el diff es un campo que no se auditará, y así se ve en la misma línea en que se asigna — el criterio que `RF-PM-004` estableció.

**Los cuatro campos no corregibles se declaran igualmente** en el cuerpo, para poder rechazarlos con `VAL-009`. Sin declararlos, el rechazo llegaría con el texto genérico del deserializador y quien intentara cambiar el rol leería «propiedad desconocida», creyendo que se equivocó de nombre en lugar de enterarse de que **el rol no se cambia nunca**.

## 4. Contrato de API

`PATCH /api/v1/commission-rates/{id}` · `200 OK`.

| Estado | Cuándo |
|---|---|
| `400` | `VAL-003`, `VAL-005`, `VAL-006`, `VAL-009` —campos no corregibles— y `VAL-010` —petición vacía— |
| `403` | Sin el permiso `commissions:update` |
| `404` | `EX-001`: no existe, **o está retirada** |
| `409` | `EX-004`: la vigencia resultante se solapa |

## 5. Autorización

Permiso `commissions:update`. Alcance global explícito.

## 6. Auditoría

Evento de **edición** con **solo los campos que cambiaron**, cada uno con `before` y `after`. **Una petición que no cambia nada no emite evento** (`FA-003`): el valor devuelto por `update(...)` es lo que lo decide.

`updatedAt` **solo se mueve si algo cambió**, por lo mismo: moverlo haría creer que alguien tocó la tarifa.

## 7. Transaccionalidad

`@Transactional`, y **una trampa heredada que aquí sí muerde**.

El alta traduce la violación de la restricción dentro del `save(...)`. **La corrección no llama a `save`**: la entidad está gestionada y el `UPDATE` sale en el `commit`, **fuera de todo `try`**. Es exactamente el defecto que `RF-SP-027` tuvo con el correo duplicado y que devolvía `500` en lugar de `409`.

**Se resuelve forzando el volcado dentro del caso de uso**, antes de salir de la transacción, de modo que la violación ocurra donde se puede capturar y traducir a `EX-004`. Es la corrección que `RF-SP-027` ya aplicó y que aquí se hereda a propósito, en lugar de volver a descubrirla.

## 8. Impacto sobre otros módulos

**Ninguno.** La corrección no consulta roles, productos ni personas: esos campos no se corrigen.

## 9. Alternativas consideradas

| Alternativa | Por qué se descartó |
|---|---|
| Admitir cambiar el rol, el producto o la persona | No corrige la tarifa: crea otra. Y reescribiría a quién se le pagó |
| Ignorar los campos no corregibles en lugar de rechazarlos | Haría creer que el cambio se aplicó |
| Un endpoint que cierre la vigente y cree la siguiente de una vez | Escondería que la primera decide **hasta cuándo rigió lo anterior**, que es el dato que la liquidación leerá. Son dos operaciones y se ven como dos |
| Comprobar el solapamiento con una consulta previa | La misma carrera que en el alta. Lo resuelve la restricción |
| Dejar que la violación salga en el `commit` | Produce `500` donde corresponde `409`. Es el defecto vivido en `RF-SP-027` |

## 10. Riesgos

| # | Riesgo | Mitigación |
|---|---|---|
| 1 | Olvidar el volcado explícito y devolver `500` en el solapamiento | Prueba de integración dedicada, que es la que lo destapó en `RF-SP-027` |
| 2 | Corregir el porcentaje de una tarifa ya liquidada | Fuera de alcance por `RN-CM-008`: la liquidación guardará el porcentaje que aplicó. Queda como obligación escrita sobre un módulo que no existe |

## 11. Estrategia de prueba

| Qué | Nivel | Detalle |
|---|---|---|
| Los tres estados por campo | Unitaria e integración | `CA-CM-023` a `CA-CM-026`: corregir, declarar el fin, **quitarlo**, y el rechazo de vaciar el porcentaje |
| Campos no corregibles | Integración | `CA-CM-027`, con su mensaje propio y sin aplicar el resto |
| Solapamiento al corregir | Integración | `CA-CM-028` — y que devuelva **`409` y no `500`**, que es el riesgo 1 |
| Tarifa retirada | Integración | `CA-CM-029`: se trata como inexistente |
| El diff de auditoría | Integración | `CA-CM-030`: solo lo que cambió, y **sin evento** cuando no cambia nada |
| Reabrir una vigencia que pisa otra | Integración | `FA-002` con choque: se rechaza |
