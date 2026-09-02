# PLAN — `RF-CM-001` Registrar una tasa de comisión por rol

| Campo | Valor |
|---|---|
| Requerimiento | `RF-CM-001` |
| Especificación | [`spec.md`](spec.md) |
| `spec.md` aprobada el | 02-09-2026 |
| Versión | 0.2.0 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 02-09-2026 |

!!! info "Qué va en este documento"

    **Cómo se construye.** Las decisiones técnicas que la especificación deliberadamente no toma.

    **Prueba de pertenencia:** si al negocio no le importa ni lo entendería, va aquí.

Este plan **funda la mecánica del módulo** y los demás la heredan sin repetirla. Lo que decida aquí sobre el esquema, la frontera con otros módulos y la traducción de errores rige para `RF-CM-002` a `RF-CM-008`.

---

## 1. Enfoque

Alta corriente contra una tabla propia, con **una sola verificación de fuera** —que el rol sea vendedor— y ninguna comprobación de concurrencia.

**Lo que este plan tiene que explicar no es el alta sino lo que la rodea**: por qué `V48` rehace una tabla que ya existía, por qué borra sus filas, y por qué el alta más simple del módulo devuelve un campo calculado que ninguna otra respuesta de alta del proyecto devuelve.

## 2. Cambios de esquema

`V48` — **la migración que rehace el módulo entero**. Toca las tres tablas y es la única del proyecto que borra datos a propósito.

### 2.1 Qué le hace a `commission_rates`

Suelta la restricción de exclusión, la de vigencia y las dos claves foráneas hacia `products` y `users`; **deja caer `product_id`, `user_id`, `valid_from` y `valid_to`**; y añade dos cosas:

| Añadido | Para qué |
|---|---|
| `uq_commission_rates_id_role` — único sobre `(id, role_id)` | **Redundante con la clave primaria, y esa es toda su función.** PostgreSQL exige que el destino de una clave foránea **compuesta** sea una restricción única sobre exactamente esas columnas, y sin ella la de `product_commission_rates` —que es lo que impide que el rol copiado diverja— no se puede declarar |
| `idx_commission_rates_role`, parcial sobre las vivas | El listado filtra por rol y es su única consulta |

**No hay unicidad sobre `role_id`**, y es deliberado: varias tasas del mismo rol son legítimas (`spec.md` §9, `FA-001`). Lo que no puede repetirse es un rol sobre el mismo producto, y eso lo cierra la clave primaria de la asociación.

### 2.2 Por qué `V48` borra las filas

**Ninguna de las cuatro formas del modelo anterior tiene traducción al nuevo.** Una tarifa de persona no es una tasa de rol —perdió el rol y ganó una tabla propia—; una de producto necesitaría una asociación que nadie declaró; y una por omisión regía sobre todo el catálogo, que es justo lo que `RN-CM-012` deja de permitir.

Conservarlas dejándolas caer a «tasa de rol» las convertiría en **filas plausibles y falsas**: seguirían ahí, con su porcentaje, sin asociación, y sin que nada dijera que significan otra cosa que el día que se escribieron. **Se borran para que la pérdida sea visible en vez de silenciosa.**

Es admisible porque el sistema no está en producción y esa tabla solo tiene datos de desarrollo y de prueba. **El día que lo esté, esta migración no se podría repetir**, y conviene que quede escrito.

## 3. Componentes afectados

| Capa | Componente | Nuevo / Modificado | Responsabilidad |
|---|---|---|---|
| `domain/models` | `CommissionRate` | **Rehecho** | El agregado, reducido a rol y porcentaje |
| `domain/models` | `RateScope` | **Eliminado** | Los cuatro grados desaparecieron: no hay nada que graduar |
| `domain/models` | `RateSource` | Nuevo | De cuál de las dos piezas salió una comisión resuelta. Lo usa `RF-CM-005` |
| `domain/repository` | `CommissionRateRepository` y su adaptador | **Rehechos** | Pierden el bloqueo y la consulta de solapamiento |
| `domain/repository` | `CommissionRateQueryRepository` y su adaptador | **Rehechos** | El listado, con la cuenta de asociaciones |
| `domain/repository` | `CommissionRows` | Nuevo | Las conversiones de tipos del driver, compartidas por los tres adaptadores de consulta |
| `domain/service` | `RegisterCommissionRateService` | **Rehecho** | Caso de uso |
| `application` | `RegisterCommissionRateRequest`, `CommissionRateResponse` | **Rehechos** | Dos campos de entrada, y la cuenta de asociaciones en la salida |
| `interfaces` | `CommissionRateController` | **Rehecho** | Gana además las tres operaciones de asociación (`RF-CM-007`, `RF-CM-008`) |

**El puerto de escritura pierde `lockCase` y `findOverlapping`**, y no es una simplificación gratuita: **sin vigencia no hay nada que pueda solaparse**. Dos altas simultáneas del mismo rol producen dos tasas distintas del catálogo, que es legítimo. La única regla de este módulo que dos peticiones pueden burlar se mudó con la vigencia, a `RF-CM-006`.

**`CommissionRows` existe porque las conversiones que agrupa nacieron de un defecto vivido** —el driver devuelve los instantes de tres formas distintas— y una copia que se quedara atrás volvería a producirlo en la única consulta que nadie hubiera corregido.

## 4. Contrato de API

`POST /api/v1/commission-rates` · `201 Created`, con `Location`.

| Estado | Cuándo |
|---|---|
| `400` | `VAL-001` a `VAL-003`, y `EX-001`: el rol no es vendedor |
| `403` | Sin el permiso `commissions:create` |
| `422` | `EX-002`: el rol no existe |

**`EX-001` es `400` y `EX-002` es `422`**, y la distinción es la del proyecto: un dato **mal formado o que no vale para esto** es `400`; un dato bien formado que **no resuelve** contra otro módulo es `422`.

**La respuesta lleva `associatedProducts`, que aquí vale siempre cero.** Es el único campo calculado de un alta en todo el proyecto, y está por lo que dice `spec.md` §2: sin él, la respuesta de una tasa que paga y la de una que no serían idénticas.

## 5. Autorización

Permiso `commissions:create`. Alcance global explícito (D-22 abierta).

**No se estrena ningún permiso**: los cuatro `commissions:` los sembró `V45` el 28-08-2026 y siguen valiendo. Sus descripciones mencionan «por producto y por persona», que era el modelo anterior — **no se corrigen**, porque una migración aplicada no se edita y el texto no gobierna nada.

## 6. Auditoría

Registro de **cambios**, acción de creación, con la instantánea completa: rol y porcentaje. La arma el agregado y no el caso de uso, por lo mismo que en `PM` — si cada caso de uso armara su mapa, dos registros describirían la misma tasa con claves distintas y compararlos dejaría de ser posible.

**Esa instantánea gana peso con este modelo.** Sin vigencia, la tabla no conserva historial, de modo que los registros de auditoría son **el único rastro** de qué porcentajes existieron. `RF-CM-003` lleva esa consecuencia al extremo.

## 7. Transaccionalidad

`@Transactional`. Un `INSERT` y un registro de auditoría.

## 8. Impacto sobre otros módulos

**Ninguno en el código.** `SP` no cambia: se consume `RoleCatalog`, que ya publica.

**En la documentación, cuatro enmiendas ya aplicadas** en el mismo pase que este plan:

| Documento | Enmienda |
|---|---|
| `requirements/cm.md` v0.5.0 | Nace `RN-CM-015` (§5.1) y §4 registra la excepción al Art. I.1 |
| `requirements.md` v0.87.0 | Las ocho filas de la matriz, con sus tres deudas |
| `modules.md` v0.17.0 | `CM` es dueño de **tres** tablas |
| `modelo-datos.md` v0.16.0 | Veintiuna tablas escritas, **ninguna pendiente** |

## 9. Alternativas consideradas

| Alternativa | Por qué se descartó |
|---|---|
| **Migrar las filas antiguas** en vez de borrarlas | Ninguna de las cuatro formas tiene traducción. Dejarlas caer a «tasa de rol» produce filas plausibles y falsas. Ver §2.2 |
| Unicidad sobre `role_id` | Prohibiría el caso legítimo de dos tasas del mismo rol para productos distintos, que es lo que hace reutilizable el catálogo |
| Conservar `RateScope` con dos valores | El grado era una propiedad de la tarifa; ahora la fuente es una propiedad de **la resolución**. Reciclar el nombre habría hecho creer que es el mismo concepto reducido |
| Conservar la vigencia en las tasas de rol | Decisión del responsable del proyecto (`cm.md` v0.4.0). Su coste —perder el historial— está declarado y aceptado en §1.4 de ese documento |
| No devolver `associatedProducts` | Deja la respuesta de una tasa que rige indistinguible de la de una que no, que es el malentendido central del rediseño |
| Renumerar los requerimientos ya construidos | Un identificador no se cambia jamás. `RF-CM-001` sigue siendo el alta aunque el alta haya cambiado |

## 10. Riesgos

| # | Riesgo | Mitigación |
|---|---|---|
| 1 | **Alguien registre tasas y dé por hecho que ya se paga por ellas** | Es el riesgo principal del rediseño. Se mitiga con `associatedProducts` en la respuesta y en el listado, y con `RN-CM-012` declarada. **No se elimina**: el sistema no puede saber si una tasa sin asociar está a medio configurar o mal configurada |
| 2 | `V48` se aplique sobre un entorno con datos que importen | El sistema no está en producción. Queda escrito en §2.2 que esta migración **no se podría repetir** el día que lo esté |
| 3 | La descripción de los permisos de `V45` mencione un modelo que ya no existe | Aceptado: una migración aplicada no se edita, y el texto no gobierna ninguna comprobación |

## 11. Estrategia de prueba

| Qué | Nivel | Detalle |
|---|---|---|
| Alta y rol resuelto | Integración | `CA-CM-001` |
| **Nace sin regir** | Integración | `CA-CM-002`: `associatedProducts` es cero **y** la tabla de asociación está vacía |
| **Lo que la respuesta ya no lleva** | Integración | `CA-CM-003`: producto, persona, vigencia y grado **ausentes**. Es la prueba que delataría un modelo a medio migrar |
| Varias tasas por rol | Integración | `CA-CM-004` |
| Porcentaje cero | Integración | `CA-CM-005` |
| Rol no vendedor e inexistente | Integración | `CA-CM-006`, `CA-CM-007`: `400` frente a `422` |
| Validaciones de entrada | Integración | `CA-CM-008` |
| El agregado, sin base de datos | Unitaria | Rango del porcentaje, instantánea y no idempotencia del retiro |

**No hay prueba concurrente en este requerimiento**, y su ausencia es una afirmación: no queda ninguna regla que dos altas simultáneas puedan burlar. Las que sí quedan se prueban en `RF-CM-006` y `RF-CM-007`.
