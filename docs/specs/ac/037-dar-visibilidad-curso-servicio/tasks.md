# TASKS — `RF-AC-037` Dar visibilidad de un curso a un servicio

| Campo | Valor |
|---|---|
| Requerimiento | `RF-AC-037` |
| Especificación | [`spec.md`](spec.md) |
| Plan | [`plan.md`](plan.md), aprobado el 25-09-2026 |
| Estado | **En revisión** |
| Issue | Pendiente de crear |
| Rama | `feature/ajustes-academia` |
| Autor | Responsable técnico |

---

## 1. Tareas

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-01` | Migración **`V<n>__ac_visibilidad_por_servicio.sql`**, con el número libre al construir: `course_products` con clave compuesta, dos claves foráneas e `ix_course_products_product` | `RF-AC-008` | Integración: aplica; la pareja repetida se rechaza | Pendiente |
| `T-02` | **`PM`**: `ProductCatalog.findKind` y `KindView(id, code, name, bot, retired)`, con su implementación | — | Integración: `BOT`, upgrade, retirado e inexistente | Pendiente |
| `T-03` | `domain/models/CourseProduct` y `CourseProductId`; `CourseProductRepository` + `Jpa…` con traducción de la clave primaria al `409` | `T-01` | Integración: el `INSERT` duplicado llega como `BusinessRuleException` | Pendiente |
| `T-04` | **Enmienda de `CourseOfferability`**: la cuenta de servicios, y el cuarto motivo *«El curso no tiene ninguna membresía ni ningún servicio que lo abra.»* | — | Unitaria: con servicios y sin membresías se ofrece; sin ninguno de los dos, el cuarto motivo | Pendiente |
| `T-05` | **Enmienda de `CourseQueryRepository`**: `CUENTA_DE_SERVICIOS` en `COLUMNAS` y en la sentencia de cursos de la categoría; `CourseRow.productCount`; `findProductsOf` con `JOIN products` | `T-01`, `T-04` | Integración: la cuenta llega a las tres lecturas; el retirado en `PM` sigue saliendo | Pendiente |
| `T-06` | `application/GrantCourseProductRequest`; `domain/service/GrantCourseProductService`: curso vivo con `FOR UPDATE`, producto por `findKind` (`422` `EX-002` / `EX-003`), pareja (`409` nombrando), inserción, auditoría, relectura | `T-02`, `T-03` | `CA-AC-216`, `CA-AC-217`, `CA-AC-218` | Pendiente |
| `T-07` | **Lectores enmendados**: `CourseDetailReader` y `CourseDetailResponse.products` (`CourseProductRef` con `@Schema(name=…)`), y la instantánea de `DeleteCourseService` con `product_ids` | `T-05` | `CA-AC-219`, `CA-AC-220` | Pendiente |
| `T-08` | `interfaces/CourseController`: `POST /api/v1/courses/{courseId}/products`, `201`; la ruta en `EndpointPermissionsIT`; **limpiezas**: `course_products` antes de todo `DELETE FROM courses` y `DELETE FROM products` en `src/test`; regla de ArchUnit `AC` → `PM` si la hay | `T-06`, `T-07` | La ruta entra en `EndpointPermissionsIT`; ninguna suite ajena choca con la clave foránea | Pendiente |
| `T-09` | Pruebas de API (`CourseProductVisibilityIT`) y las cuentas de sentencias que suben en las suites de cursos | `T-08` | `CA-AC-216` a `CA-AC-222` | Pendiente |
| `T-10` | Documentación OpenAPI. **La prosa dice** que solo abre un servicio `BOT`, que se suma a las membresías, que un servicio inactivo se añade y uno retirado no, que el `422` del upgrade es distinto del del inexistente, y que el `409` nombra el servicio; **las `@Operation` del detalle, el listado y el retiro dicen `products`** | `T-08` | El contrato declara `201`, `400`, `401`, `403`, `404`, `409`, `422` | Pendiente |
| `T-11` | Actualizar la matriz de `docs/requirements.md`, `docs/api/index.md`, las cuatro specs enmendadas, `RF-AC-020` (el texto del cuarto motivo) y `ac.md` | `T-09` | Las filas reflejan el estado; `ac.md` sube de versión | Pendiente |

## 2. Orden de ejecución

`T-01` y `T-02` primero; `T-03`, `T-04` y `T-05` independientes; `T-06` y `T-07` en paralelo; `T-09` al final con la suite completa en verde.

## 3. Cobertura de los criterios de aceptación

| Criterio | Tareas |
|---|---|
| `CA-AC-216`, `CA-AC-217`, `CA-AC-218` | `T-02`, `T-03`, `T-06`, `T-09` |
| `CA-AC-219` | `T-04`, `T-05`, `T-07`, `T-09` |
| `CA-AC-220` | `T-05`, `T-07`, `T-09` |
| `CA-AC-221` | `T-03`, `T-09` |
| `CA-AC-222` | `T-08`, `T-09` |

## 4. Bloqueos

| # | Bloqueo | Desde | Responsable | Estado |
|---|---|---|---|---|
| 1 | El número de migración se asigna al construir; `V43` está anotada para `CM` sin código | 25-09-2026 | Responsable técnico | Abierto |
| 2 | **Que un alumno estudie el curso por su servicio** necesita que `SP` publique los productos vigentes de una persona, y lo pide el aula (`RF-AC-033` a `RF-AC-035`), no este requerimiento. Hasta el aula, la lista existe, cuenta para `offerable` y no abre nada a nadie | 25-09-2026 | Responsable técnico | Abierto |

## 5. Definición de terminado

- [ ] Todas las tareas en estado `Hecha`.
- [ ] Todos los criterios de aceptación con prueba automatizada en verde.
- [ ] `mvn verify` en verde en local, **con las suites de `PM` que borran productos**.
- [ ] Toda escritura emite su evento de auditoría, en la transacción que corresponde.
- [ ] El endpoint declara su permiso.
- [ ] El contrato OpenAPI coincide con el comportamiento real, prosa incluida.
- [ ] Matriz de trazabilidad, `docs/api/index.md`, las specs enmendadas y `ac.md` actualizados.
- [ ] Pull Request aprobado por alguien distinto del autor e integrado.
