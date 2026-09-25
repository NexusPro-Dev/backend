# TASKS — `RF-AC-008` Registrar curso

| Campo | Valor |
|---|---|
| Requerimiento | `RF-AC-008` |
| Especificación | [`spec.md`](spec.md) |
| Plan | [`plan.md`](plan.md), aprobado el 18-09-2026 |
| Estado | **Hecha** — todas las tareas `Hecha` el 25-09-2026; queda el Pull Request |
| Issue | Pendiente de crear |
| Rama | `feature/academia`; la enmienda, `feature/ajustes-academia` |
| Autor | Responsable técnico |

---

## 1. Tareas

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-01` | Migración **`V21__ac_cursos.sql`**: `courses` con `fk_courses_instructor`, los cuatro `CHECK`, `cover_image_id` nulable sin FK, `uq_courses_title` parcial e `ix_courses_instructor` | — | Integración: aplica sobre la migración anterior; dos títulos iguales vivos se rechazan y con uno retirado no; una dificultad fuera de dominio y un video sin esquema se rechazan | **Hecha el 18-09-2026** |
| `T-02` | Migración **`V22__ac_semilla_permisos_cursos.sql`**: los seis `courses:` con identificador literal (`…000005` a `…000010`), asociados a `SUPERADMIN` y `ADMIN` explícitamente, con la guarda que cuenta doce | — | `CoursesPermissionsSeedIT`: seis sembrados, identificadores estables, doce asociaciones, ninguna a `CLIENTE`; las cuatro suites del catálogo pasan de 54 a **60**, y `PermissionsSeedIT.coincideConElCatalogoAprobado` gana los seis códigos | **Hecha el 18-09-2026** |
| `T-03` | **En `SP`**: `users/application/PermissionHolderLookup` y `users/domain/repository/JpaPermissionHolderLookup`, con el predicado de roles vivos y activos **compartido como constante** con `JpaEffectivePermissions` | — | `PermissionHolderLookupIT`: los cinco casos de `plan.md` §11; `JpaEffectivePermissions` sigue en verde con el predicado extraído | **Hecha el 18-09-2026** |
| `T-04` | **Enmiendas declaradas en `plan.md` §8**: `requirements/sp.md` §8 gana el párrafo de la tercera lectura publicada y `architecture.md` §15.2 su fila, con versión y fila de control de cambios, **numerando por encima de lo que deje `feature/vendedores-de-un-cliente`** y avisando a esa sesión antes | `T-03` | Los dos documentos nombran `PermissionHolderLookup`, quién lo pide y por qué no devuelve la lista | **Hecha el 18-09-2026** |
| `T-05` | `domain/models/Course`, `CourseStatus`, `CourseDifficulty` y **`VideoUrl`**: recorte, descripciones vacías → nulas, validación de forma con el mensaje de cada `VAL-` | — | Unitaria: los casos de `plan.md` §11 | **Hecha el 18-09-2026** |
| `T-06` | **`domain/models/CourseOfferability`**: los **cinco** motivos en orden fijo —retirado, inactivo, **sin descripción**, sin membresías, sin módulo activo con lección activa con contenido— sobre entradas planas | — | Unitaria: cada motivo por separado, el orden cuando fallan varios, y el ofrecible | **Hecha el 18-09-2026** |
| `T-07` | `CourseRepository` + `JpaCourseRepository` con traducción de `uq_courses_title` al `409` de `EX-001` | `T-01`, `T-05` | Integración: un `INSERT` duplicado llega como `BusinessRuleException` | **Hecha el 18-09-2026** |
| `T-08` | `CourseQueryRepository.findDetail(id)` con el instructor por `JOIN users` (tres columnas), y las lecturas de relaciones y módulos que **devuelven vacío** hasta sus requerimientos, con la nota en el código | `T-01` | Integración: el curso vuelve con `username`, `first_name`, `last_name` y sin correo; el inexistente, vacío | **Hecha el 18-09-2026** |
| `T-09` | `application/RegisterCourseRequest`, `CourseDetailResponse` con `InstructorRef` y las cuatro listas; `domain/service/CourseDetailReader` | `T-06`, `T-08` | El contrato declara los campos; `offerable`, `offerableReason`, `coverImageUrl` y las listas **siempre presentes** | **Hecha el 18-09-2026** |
| `T-10` | `domain/service/RegisterCourseService`: validación conjunta, título, instructor por `UserCatalog` y `PermissionHolderLookup`, inserción en `INACTIVO`, auditoría `CREATE`, relectura | `T-03`, `T-07`, `T-09` | `CA-AC-034`, `CA-AC-035`, `CA-AC-036`, `CA-AC-039`, `CA-AC-040`, `CA-AC-043` | **Hecha el 18-09-2026** |
| `T-11` | `interfaces/CourseController`: `POST /api/v1/courses`, `@PreAuthorize("hasAuthority('courses:create')")`, `201` con `Location` | `T-10` | `CA-AC-037`, `CA-AC-038`, `CA-AC-041`; la ruta entra en `EndpointPermissionsIT` | **Hecha el 18-09-2026** |
| `T-12` | Pruebas de API (`CoursesIT`) de los diez criterios y la carrera en `CourseConcurrencyIT`; `CourseTestSupport` con la siembra de un instructor con `courses:teach` por un rol de prueba | `T-11` | `CA-AC-034` a `CA-AC-043` | **Hecha el 18-09-2026** |
| `T-13` | Documentación OpenAPI. **La prosa dice** que nace inactivo y vacío, qué exige el instructor y que se comprueba solo al asignar, que el nombre del instructor es el actual, y que los `course-categories:` no habilitan | `T-11` | El contrato declara `201`, `400`, `401`, `403`, `409`, `422` | **Hecha el 18-09-2026** |
| `T-14` | Actualizar la matriz de `docs/requirements.md`, `docs/api/index.md` y `docs/security.md` (los seis sembrados) | `T-12` | La fila de `RF-AC-008` refleja el estado; `security.md` deja de decir que los `courses:` están sin siembra | **Hecha el 18-09-2026** |
| `T-15` | **Enmienda del 25-09-2026**: `RegisterCourseRequest.categoryIds` y `VAL-008`, junto con los demás errores de forma | — | `CA-AC-229` | Hecha |
| `T-16` | `CourseCategoryRepository.findAliveByIds`, y la escritura de `RF-AC-016` extraída a un colaborador que usan el alta y la clasificación | `RF-AC-016` | La suite de `RF-AC-016` sigue en verde | Hecha |
| `T-17` | `RegisterCourseService`: categorías resueltas antes de insertar (`EX-004`, todas las que fallan), clasificación y auditoría tras insertar | `T-15`, `T-16` | `CA-AC-227`, `CA-AC-228` | Hecha |
| `T-18` | Pruebas en `CoursesIT`, la prosa de la `@Operation` del alta —ya no dice «sin categorías»—, y `docs/api/index.md` | `T-17` | `CA-AC-227` a `CA-AC-229`; el contrato declara `categoryIds` | Hecha |
| `T-19` | **Segunda enmienda del 25-09-2026**: `productIds` y `membershipIds` en `RegisterCourseRequest`, `VAL-008` para las tres listas; servicios por `ProductCatalog.findKind` (`EX-005`) y membresías por `MembershipCatalog.find` (`EX-006`) antes de insertar; las escrituras de `RF-AC-037` y `RF-AC-020` extraídas a un colaborador | `RF-AC-020`, `RF-AC-037` | `CA-AC-230` a `CA-AC-232` | Hecha |

## 2. Orden de ejecución

**`T-03` primero**, porque toca `SP` y conviene que quede en su propio commit con su suite en verde antes de que `AC` lo importe. `T-01`, `T-02`, `T-05` y `T-06` son independientes. `T-10` junta todo. **`T-04` va cuando se libere `sp.md`**, y no bloquea el código: el puerto existe desde `T-03`.

## 3. Cobertura de los criterios de aceptación

| Criterio | Tareas |
|---|---|
| `CA-AC-034`, `CA-AC-039`, `CA-AC-040` | `T-05`, `T-09`, `T-10`, `T-12` |
| `CA-AC-035`, `CA-AC-042` | `T-01`, `T-07`, `T-10`, `T-12` |
| `CA-AC-036`, `CA-AC-043` | `T-03`, `T-10`, `T-12` |
| `CA-AC-037`, `CA-AC-038` | `T-05`, `T-09`, `T-11` |
| `CA-AC-041` | `T-02`, `T-11` |

## 4. Bloqueos

| # | Bloqueo | Desde | Responsable | Estado |
|---|---|---|---|---|
| 1 | Los números de migración se asignan al construir: `V21`/`V22` si nadie se adelanta; `V20` está reservada por `SP` | 18-09-2026 | Responsable técnico | **Cerrado** el 18-09-2026: fueron `V21` y `V22`; `V20` la tomó `client_sellers` |
| 2 | `T-04` enmienda `requirements/sp.md` y `architecture.md`, que la rama `feature/vendedores-de-un-cliente` tiene tomados (1.59.0 y su versión): se aplica cuando esa rama confirme, numerando por encima, con aviso previo a su sesión | 18-09-2026 | Responsable técnico | **Cerrado** el 18-09-2026: aquella rama confirmó (`6f6542a`) y las enmiendas van en `sp.md` 1.60.0 y `architecture.md` 0.32.0, en el commit del puerto |
| 3 | `T-02` cambia el recuento del catálogo de permisos (54 → 60) en cuatro suites de `SP` y en la lista literal de `PermissionsSeedIT` | 18-09-2026 | Responsable técnico | **Cerrado** el 18-09-2026: son **cinco** suites —`PermissionsSeedIT`, `PermissionIT`, `JpaPermissionQueryRepositoryIT`, `ListPermissionsServiceIT` y la lista literal— y ninguna descripción nueva contiene «lógicamente» |

## 5. Definición de terminado

- [x] Todas las tareas en estado `Hecha`, **`T-04` incluida**.
- [x] Todos los criterios de aceptación con prueba automatizada en verde.
- [x] `mvn verify` en verde en local, incluidas las suites de `SP` que tocan `T-02` y `T-03`.
- [x] Toda escritura emite su evento de auditoría, en la transacción que corresponde.
- [x] El endpoint declara su permiso.
- [x] El contrato OpenAPI coincide con el comportamiento real, prosa incluida.
- [x] `requirements/sp.md` y `architecture.md` nombran la interfaz nueva.
- [x] Matriz de trazabilidad, `docs/api/index.md` y `docs/security.md` actualizados.
- [ ] Pull Request aprobado por alguien distinto del autor e integrado.
