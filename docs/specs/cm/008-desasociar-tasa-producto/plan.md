# PLAN — `RF-CM-008` Retirar la asociación de una tasa con un producto

| Campo | Valor |
|---|---|
| Requerimiento | `RF-CM-008` |
| Especificación | [`spec.md`](spec.md) |
| `spec.md` aprobada el | 02-09-2026 |
| Versión | 0.1.0 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 02-09-2026 |

!!! info "Qué va en este documento"

    **Cómo se construye.** Las decisiones técnicas que la especificación deliberadamente no toma.

El comportamiento es el de [`spec.md`](spec.md) y no se repite. La mecánica común la fijó el plan de [`RF-CM-001`](../001-registrar-tasa-comision-rol/plan.md) y **este documento la hereda sin repetirla**.

---

## 1. Enfoque

**El único borrado físico del módulo**, con motivo obligatorio y registro de eliminación.

Es una operación de cuatro líneas, y lo que hay que decidir en ella no es cómo se borra sino **qué queda después**. La respuesta —**solo el registro de auditoría**— gobierna las tres decisiones de este plan: el tipo de eliminación, la exigencia del motivo, y el código de estado del rechazo.

## 2. Cambios de esquema

**Ninguno.** `product_commission_rates` la crea `RF-CM-007`, y **su falta de `deleted_at` es lo que hace que esta operación sea un borrado de verdad**.

Esa decisión está argumentada en `RF-CM-007` §2.3 y no se repite. Lo que sí toca decir aquí es su consecuencia: **al no haber columna que marcar, no hay forma de distinguir después «nunca existió» de «ya se borró»**. Todo lo que sigue sale de ahí.

## 3. Componentes afectados

| Capa | Componente | Nuevo | Responsabilidad |
|---|---|---|---|
| `domain/repository` | `ProductCommissionRateRepository` | **Modificado** | Gana `remove(...)` |
| `domain/service` | `DissociateProductService` | Sí | Caso de uso |
| `application` | `DissociateProductRequest` | Sí | El motivo |
| `interfaces` | `CommissionRateController` | **Modificado** | `POST /api/v1/commission-rates/{id}/products/{productId}/deletion` |

**La búsqueda es por tasa y producto, no por rol y producto**, aunque la clave primaria sea lo segundo. Quien desasocia nombra **la tasa** que quiere quitar de en medio, y si el producto estuviera asociado a otra tasa del mismo rol, borrar por rol **retiraría una asociación que nadie pidió retirar**.

**`DissociateProductRequest` no reutiliza `DeleteCommissionRateRequest`**, aunque los dos sean un motivo. Es la excepción a lo que se hizo entre las tasas de rol y las personalizadas, y está por el contrato publicado: los mensajes de validación difieren —«el motivo de la desasociación»— y compartir el esquema haría que la documentación de esta operación hablara de retirar una tasa.

## 4. Contrato de API

`POST /api/v1/commission-rates/{id}/products/{productId}/deletion` · `204 No Content`.

| Estado | Cuándo |
|---|---|
| `400` | `VAL-007`, `VAL-008` |
| `403` | Sin el permiso `commissions:update` |
| `404` | `EX-001`: esa tasa no está asociada a ese producto |

**Es un `POST` a un subrecurso y no un `DELETE`**, por lo mismo que en el resto del proyecto: el motivo es obligatorio (Art. V.13), `DELETE` con cuerpo no es interoperable, y en la cadena de consulta el motivo acabaría en el registro de peticiones de cualquier intermediario.

**La ruta es larga a propósito.** `{id}/products/{productId}/deletion` nombra la asociación por sus dos extremos, que es como el actor la piensa. Acortarla con un identificador propio de la asociación habría exigido dárselo, y **la asociación no tiene identificador**: su identidad es la pareja.

!!! warning "El rechazo es `404` y no `409`, al revés que en los otros dos retiros del módulo"

    En `RF-CM-004` y en el retiro de una personalizada la fila permanece, y por eso puede afirmarse «ya estaba retirada».

    **Aquí no queda nada.** Inventar un `409` sería afirmar algo que el sistema **no sabe**: no puede distinguir una asociación que nunca existió de una que se borró hace un minuto. Se responde lo único que consta.

## 5. Autorización

Permiso `commissions:update`, el mismo que asociar y que corregir un porcentaje.

**Y no `commissions:delete`**, aunque esto borre y aquello no. La asimetría es deliberada: lo que se destruye aquí es **configuración**, no una entidad del catálogo. Quien puede decidir que un producto empiece a pagar debe poder decidir que deje de hacerlo, y **eso es lo mismo que corregir un porcentaje a cero**.

## 6. Auditoría

Registro de **eliminación** de tipo **asociación** —no física—, con motivo e instantánea.

**`ASSOCIATION` y no `PHYSICAL`, aunque la fila se borre de verdad**, porque lo que desaparece no es una entidad sino un **vínculo entre dos que siguen vivas**. El catálogo de `AuditEnums` distingue los dos casos precisamente para que quien lea el registro sepa si perdió un objeto o una relación.

!!! danger "El esquema EXIME de motivo a este tipo, y aquí se exige igual"

    `ck_deletion_reason` no obliga a motivo cuando la eliminación es de tipo asociación, y el criterio general es correcto: al perder un vínculo, las dos filas que unía siguen contándolo todo.

    **Aquí no.** La asociación **es** el hecho de que ese producto pagaba a ese rol, y no queda ninguna otra fila que lo diga. **Se exige motivo en el caso de uso**, por encima de lo que el esquema permitiría.

    Es la única operación del proyecto que endurece una regla de auditoría en lugar de heredarla.

**La instantánea se toma antes de borrar, y aquí no es una precaución: es la copia.**

## 7. Transaccionalidad

`@Transactional`. El borrado y el registro van juntos, y ese orden importa más que en ningún otro retiro: **si el registro no se escribiera, no quedaría absolutamente nada.**

## 8. Impacto sobre otros módulos

**Ninguno.**

## 9. Alternativas consideradas

| Alternativa | Por qué se descartó |
|---|---|
| **Retiro lógico en lugar de borrado** | Convierte una configuración vigente en un historial que nadie consultará, y obliga a filtrar por `deleted_at` en la consulta más caliente del módulo. Argumentado en `RF-CM-007` §2.3 |
| **`409` cuando ya no está** | Afirma algo que el sistema no sabe: no puede distinguir «nunca existió» de «ya se borró» |
| Heredar la exención de motivo de `ck_deletion_reason` | Aquí el registro **es** la única constancia. Ver §6 |
| `DeletionType.PHYSICAL` | Lo que desaparece es un vínculo, no una entidad. El catálogo distingue los dos casos a propósito |
| Buscar la asociación **por rol** y producto | Borraría una asociación que nadie pidió retirar, si el producto tuviera otra tasa del mismo rol |
| Una operación de **sustitución** —desasociar y asociar a la vez— | Esconde que se está tomando una decisión sobre lo que se paga. Son dos, y se toman por separado |
| Retirar **todas** las asociaciones de una tasa de golpe | Cada producto que deja de comisionar es una decisión, y en bloque se toma sin mirarlas |
| Reutilizar `DeleteCommissionRateRequest` | El contrato publicado hablaría de retirar una tasa en la operación que retira una asociación |
| `commissions:delete` en lugar de `update` | Lo que se destruye es configuración, no una entidad. Es lo mismo que poner un porcentaje a cero |

## 10. Riesgos

| # | Riesgo | Mitigación |
|---|---|---|
| 1 | **Se pierda con qué porcentaje pagaba ese producto** | La instantánea del registro lo conserva. Y `RN-CM-008` sigue siendo la defensa real, que no existe todavía |
| 2 | Un motivo pobre deje la única constancia sin contenido | **No se mitiga.** Un carácter satisface la regla, por decisión declarada en `architecture.md` §6.6.3. Aquí el coste es mayor que en cualquier otra eliminación |
| 3 | La ventana entre desasociar y volver a asociar | Declarada en `spec.md` `FA-001`. Es el precio de no tener una operación de sustitución que oculte la decisión |
| 4 | Alguien desasocie en bucle para retirar una tasa, sin mirar qué está apagando | Es el coste declarado de `RN-CM-015`, y se paga **a la vista** a propósito |

## 11. Estrategia de prueba

| Qué | Nivel | Detalle |
|---|---|---|
| Desasocia y **la tasa sigue viva** | Integración | `CA-CM-073`: la fila de la asociación desaparece, la de la tasa no |
| **El producto deja de comisionar** | Integración | `CA-CM-074`, verificado **resolviendo** |
| El registro, con su tipo y su motivo | Integración | `CA-CM-075`: tipo `ASSOCIATION` **y motivo presente**, que es lo que el esquema no obligaba |
| Motivo obligatorio | Integración | `CA-CM-076`: y **sin retirar nada** |
| Lo que no está asociado | Integración | `CA-CM-077`: `404`, y no `409` |
| Permiso | Integración | `CA-CM-078` |

**`CA-CM-075` comprueba dos cosas y la segunda es la que importa.** Que el tipo sea `ASSOCIATION` es rutina; que **el motivo esté ahí** es lo que verifica la decisión de §6 — porque `ck_deletion_reason` habría aceptado la fila sin él, y un cambio que quitara la validación del caso de uso **no rompería ninguna restricción del motor**.
