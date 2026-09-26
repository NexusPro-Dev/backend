# TASKS — `RF-AC-006` Subir o reemplazar la portada de una categoría

| Campo | Valor |
|---|---|
| Requerimiento | `RF-AC-006` |
| Especificación | [`spec.md`](spec.md) |
| Plan | [`plan.md`](plan.md), aprobado el 18-09-2026 |
| Estado | **Hecha** — todas las tareas `Hecha` el 25-09-2026; queda el Pull Request |
| Issue | Pendiente de crear |
| Rama | `feature/ajustes-academia` |
| Autor | Responsable técnico |

---

## 1. Tareas

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-01` | **Refactorización en `shared/images`**: mover `ImageSignature` y `CambioDePortada` desde `PM`; `ImageSignature` gana `TAMANO_MAXIMO`, `CAMPO` y `validar(bytes)` con los tres `VAL` en su orden; `ProductImage.de` delega; imports de `PM` | — | `ProductCoverIT`, `PackageCoverIT`, `ProductImageIT`, `ProductImageTest`, `LayerRulesTest` en verde; unitaria nueva de `validar` | Hecha |
| `T-02` | Migración **`V44__ac_imagenes.sql`**: `academy_images` con sus dos `CHECK`, y las seis restricciones de las tres columnas `cover_image_id` | `RF-AC-022` · `T-01` | Integración: aplica; un `content_type` fuera de los tres y un `content` de 5 242 881 bytes se rechazan; una columna con un identificador inexistente se rechaza; dos filas con la misma imagen se rechazan | Hecha |
| `T-03` | `domain/models/AcademyImage` y `AcademyImageRepository` + `Jpa…`; `CourseCategory.asignarPortada` | `T-01`, `T-02` | Unitaria de `AcademyImage.de` y de `asignarPortada`; integración de `save`/`deleteById` | Hecha |
| `T-04` | `domain/service/UploadCourseCategoryCoverService`: archivo antes que la base, categoría viva con `FOR UPDATE`, insertar, apuntar, volcar, borrar la vieja, auditar, releer | `T-03` | `CA-AC-155`, `CA-AC-156`, `CA-AC-157`, `CA-AC-160` | Hecha |
| `T-05` | `interfaces/CourseCategoryController`: `PUT /api/v1/course-categories/{id}/cover` con `consumes = multipart/form-data` y la traducción del no-`multipart` a `400` | `T-04` | `CA-AC-158`, `CA-AC-159`; la ruta entra en `EndpointPermissionsIT` | Hecha |
| `T-06` | Pruebas de API (`CourseCategoryCoverIT`) de `CA-AC-155` a `CA-AC-160`, con los archivos de prueba de `PM` reutilizados, y la doble subida simultánea | `T-05` | Los seis en verde | Hecha |
| `T-07` | Prueba de esquema para `CA-AC-162` y verificación de `CA-AC-161` | `T-01`, `T-02` | Las seis restricciones muerden; las suites de `PM` en verde | Hecha |
| `T-08` | Documentación OpenAPI. **La prosa dice** que es `multipart` con `file`, que el tipo va por los bytes, el tope, que reemplaza y borra la anterior, que no hay condición de estado, y que la imagen se sirve en `/academy-images/{id}` | `T-05` | El contrato declara `200`, `400`, `401`, `403`, `404` | Hecha |
| `T-09` | Actualizar la matriz de `docs/requirements.md`, `docs/api/index.md`, `docs/modelo-datos.md` (`academy_images` escrita, las restricciones de las tres columnas) y `docs/requirements/pm.md` (nota de que el detector vive en `shared/`) | `T-06` | Las filas reflejan el estado | Hecha |

## 2. Orden de ejecución

**`T-01` primero y en su propio commit**: toca `PM`, y sus cuatro suites en verde son la condición para seguir. `T-02` y `T-03` después; `T-06` y `T-07` al final.

## 3. Cobertura de los criterios de aceptación

| Criterio | Tareas |
|---|---|
| `CA-AC-155`, `CA-AC-156`, `CA-AC-157`, `CA-AC-160` | `T-03`, `T-04`, `T-06` |
| `CA-AC-158`, `CA-AC-159` | `T-01`, `T-05`, `T-06` |
| `CA-AC-161` | `T-01`, `T-07` |
| `CA-AC-162` | `T-02`, `T-07` |

## 4. Bloqueos

| # | Bloqueo | Desde | Responsable | Estado |
|---|---|---|---|---|
| 1 | Los números de migración se asignan al construir: `V28` si nadie se adelanta | 18-09-2026 | Responsable técnico | **Cerrado** el 25-09-2026: `V44` |
| 2 | `T-01` toca `PM` mientras otras sesiones construyen sobre el mismo árbol: avisar antes de mover los archivos | 18-09-2026 | Responsable técnico | Abierto |
| 3 | `V28` exige que `course_modules` exista (`RF-AC-022`): el bloque 5 va después del 3 en la construcción | 18-09-2026 | Responsable técnico | Abierto |

## 5. Definición de terminado

- [x] Todas las tareas en estado `Hecha`.
- [x] Todos los criterios de aceptación con prueba automatizada en verde.
- [x] `mvn verify` en verde en local, **incluidas las cuatro suites de imágenes de `PM` con el detector en `shared/`**.
- [x] Toda escritura emite su evento de auditoría, en la transacción que corresponde.
- [x] El endpoint declara su permiso.
- [x] El contrato OpenAPI coincide con el comportamiento real, prosa incluida.
- [x] Matriz de trazabilidad, `docs/api/index.md`, `modelo-datos.md` y `pm.md` actualizados.
- [ ] Pull Request aprobado por alguien distinto del autor e integrado.
