# TASKS — `RF-PM-001` Registrar producto

| Campo | Valor |
|---|---|
| Requerimiento | `RF-PM-001` |
| Plan | [`plan.md`](plan.md), aprobado el 26-08-2026 |
| Estado | **Aprobadas** |
| Autor | Responsable técnico |
| Aprobadas por | Responsable del proyecto |
| Fecha de aprobación | 26-08-2026 |

!!! info "Qué va en este documento"

    **En qué pasos se construye** lo que `plan.md` decidió, con su dependencia y su verificación. Ninguna tarea se da por `Hecha` sin que su verificación pase.

---

## 1. Tareas

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-01` | Migración `V39__create_products.sql`: la tabla con sus doce columnas, los cinco `CHECK`, las dos claves foráneas y los tres índices únicos de `plan.md` §2.1 | — | `mvn flyway:info` la lista aplicada; una prueba comprueba que `ck_products_type_target` **rechaza las dos mitades** —upgrade sin destino y servicio con destino— y que `status` toma `INACTIVO` por omisión | Pendiente |
| `T-02` | Migración `V40__seed_products_permissions.sql`: los cuatro permisos con UUID v7 literales, **y su asociación a `SUPERADMIN` y `ADMIN` en la misma migración** | `T-01` | Prueba de integración: los cuatro permisos existen y **los dos roles los tienen**. Sin esta segunda mitad, `ADMIN` no podría conceder `products:create` y el rechazo no diría que falta una siembra | Pendiente |
| `T-03` | `domain/models/Product`: agregado y modelo persistente. Normaliza el código a mayúsculas, recorta nombre y descripción, y valida el formato del código en el dominio | `T-01` | Unitaria sin Spring: `upgrade_oro` se guarda `UPGRADE_ORO`; `1ORO`, `ORO-PLUS` y el vacío se rechazan con `VAL-010` | Pendiente |
| `T-04` | `domain/models/ProductType` y `ProductStatus`: dominios cerrados | — | Compila; un valor fuera del dominio no es representable | Pendiente |
| `T-05` | **En `SP`**: `MembershipCatalog` con su adaptador, en `modules/system/memberships/application`. Devuelve `Optional<MembershipView>` con `id`, `code`, `name` y `level` | — | Integración: devuelve la membresía sembrada y **vacío** —no excepción— ante un identificador inexistente | Pendiente |
| `T-06` | **En `SP`**: `CurrencyCatalog` con su adaptador, en `modules/system/currencies/application`. Devuelve `id`, `code`, `decimalPlaces` y `active` | — | Integración: devuelve `USD` con dos decimales y **activa**; una moneda desactivada llega con `active` en falso, no ausente | Pendiente |
| `T-07` | Regla de **ArchUnit**: ninguna clase de `..modules.products..` depende de `..modules.system..domain..` | `T-05`, `T-06` | La regla falla si alguien importa un repositorio o una entidad de `SP`. **Sin ella D-25 es una convención** | Pendiente |
| `T-08` | `application`: `RegisterProductRequest` —**sin campo `status`**— y `RegisterProductCommand` | `T-04` | Enviar `status` devuelve `400` por `FAIL_ON_UNKNOWN_PROPERTIES`, no se ignora | Pendiente |
| `T-09` | `domain/repository`: puerto `ProductRepository` y adaptador `JpaProductRepository`, con **traducción por nombre de restricción** y `flush` explícito | `T-03` | Integración: el código duplicado produce `409` sobre el campo `code`, y el nombre duplicado sobre `name`. **Nunca por el texto del driver** | Pendiente |
| `T-10` | `domain/service/RegisterProductService` con el orden de verificación de `plan.md` §4 | `T-05`, `T-06`, `T-08`, `T-09` | Integración: cada rechazo llega con su código y **no se registra nada** | Pendiente |
| `T-11` | Auditoría: evento `CREATE` con el estado inicial completo, en la misma transacción | `T-10` | `audit_change_log` contiene el evento con las ocho claves, precio incluido (`CA-PM-011`) | Pendiente |
| `T-12` | Serialización del precio **con los decimales de su moneda**, en un componente compartido por las cuatro respuestas del módulo | `T-06` | Unitaria: `49.9900` sobre una moneda de dos decimales sale `49.99`; sobre una de cero, `50` | Pendiente |
| `T-13` | `interfaces/ProductController`: `POST /api/v1/products` con `products:create` declarado sobre el método, y `Location` en la respuesta | `T-10`, `T-12` | Prueba de API: `201` con cabecera, y `403` sin el permiso | Pendiente |
| `T-14` | Pruebas de API de los doce criterios de `spec.md` §12 | `T-13` | La suite cubre `CA-PM-001` a `CA-PM-012` y `CA-PM-068` a `CA-PM-071`, con sus estados y sus `error_code` | Pendiente |
| `T-15` | Prueba concurrente: **dos altas simultáneas con el mismo código** | `T-13` | Una queda y la otra recibe `409`. **No basta la verificación previa**: la garantía es el índice único | Pendiente |
| `T-16` | Documentación OpenAPI del endpoint, con los ocho campos, el `201` y los estados `400`, `403`, `409` y `422` | `T-14` | `OpenApiContractIT` regenera el contrato y la prueba comprueba que declara el endpoint | Pendiente |
| `T-17` | Actualizar la matriz de `docs/requirements.md` y anotar en `requirements/sp.md` que `SP` publica las dos interfaces | `T-14` | La fila de `RF-PM-001` refleja el estado y `SP` deja constancia de lo que publica | Pendiente |
| `T-18` | **Vigencia**: la columna en `T-01`, el campo opcional en el DTO, en el agregado y en la respuesta | `T-08`, `T-13` | Registrar **sin** vigencia deja un producto que no caduca (`CA-PM-092`); cero, negativa o no entera se rechazan con `VAL-011` (`CA-PM-093`) | Pendiente |

## 2. Orden de ejecución

`T-01` y `T-02` primero: sin tabla ni permisos no hay nada que probar de extremo a extremo. `T-05`, `T-06` y `T-07` son independientes del resto y conviene escribirlas pronto, porque **son la parte que toca otro módulo** y la que más riesgo tiene de descubrir algo.

`T-03`, `T-04`, `T-08` y `T-09` no dependen entre sí. `T-10` es la que las junta.

## 3. Cobertura de los criterios de aceptación

| Criterio | Tareas |
|---|---|
| `CA-PM-001`, `CA-PM-002` | `T-10`, `T-13`, `T-14` |
| `CA-PM-003`, `CA-PM-004` | `T-10`, `T-14` |
| `CA-PM-005`, `CA-PM-006` | `T-06`, `T-10`, `T-14` |
| `CA-PM-007` | `T-06`, `T-10` |
| `CA-PM-008` | `T-09`, `T-14` |
| `CA-PM-010` | `T-05`, `T-10` |
| `CA-PM-011` | `T-11` |
| `CA-PM-012` | `T-13` |
| `CA-PM-068` | `T-01`, `T-08` |
| `CA-PM-069` | `T-01`, `T-09` |
| `CA-PM-070` | `T-03` |
| `CA-PM-071` | `T-10` |

## 4. Bloqueos

| # | Bloqueo | Desde | Responsable | Estado |
|---|---|---|---|---|
| 1 | `T-05` y `T-06` escriben código **en paquetes de `SP`**, un módulo que ya tiene su suite en verde. Cualquier regresión allí es responsabilidad de este requerimiento | 26-08-2026 | Responsable técnico | Abierto |

## 5. Definición de terminado

El requerimiento no está terminado hasta cumplir **todas** las condiciones de la constitución §16:

- [ ] Todas las tareas en estado `Hecha`.
- [ ] Todos los criterios de aceptación con prueba automatizada en verde.
- [ ] `mvn verify` en verde en local, **incluida la suite de `SP` sin cambios**.
- [ ] Toda escritura emite su evento de auditoría, en la transacción que corresponde.
- [ ] Los endpoints nuevos declaran su permiso.
- [ ] El contrato OpenAPI coincide con el comportamiento real.
- [ ] Documentación afectada actualizada en el mismo Pull Request.
- [ ] Matriz de trazabilidad actualizada.
- [ ] Pull Request aprobado por alguien distinto del autor e integrado.
