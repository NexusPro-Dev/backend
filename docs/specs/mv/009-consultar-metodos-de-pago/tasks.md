# TASKS — `RF-MV-009` Consultar los métodos de pago

| Campo | Valor |
|---|---|
| Requerimiento | `RF-MV-009` |
| Plan | [`plan.md`](plan.md), aprobado el 04-09-2026 |
| Versión | 0.1.0 |
| Estado | **En revisión** |
| Autor | Responsable técnico |
| Aprobadas por | Pendiente |
| Fecha de aprobación | Pendiente |
| Issue | Pendiente de crear |
| Rama | `feature/venta-de-productos` |

!!! info "Qué va en este documento"

    **En qué pasos se construye** lo que `plan.md` decidió, con su dependencia y su verificación. Ninguna tarea se da por `Hecha` sin que su verificación pase.

!!! note "Va en la rama de `RF-MV-001` y no en una propia"

    No es por comodidad: **esta consulta no tiene sentido sin aquel requerimiento**, porque los métodos de pago los creó su migración y quien los consume es su endpoint. Separarlas dejaría una rama que añade una tabla a un catálogo que en `develop` todavía no existe.

---

## 1. Tareas

**Estados:** `Pendiente` · `En curso` · `Hecha` · `Bloqueada`.

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-01` | **`V55`**: `payment_method_exclusions`, con clave primaria compuesta, `CASCADE` hacia el método y `RESTRICT` hacia el país. **Se crea vacía** | `RF-MV-001 · T-01` | La tabla no admite el mismo par dos veces, y **desactivar un país sigue funcionando**: la suite de países en verde sin cambios | **Pendiente** |
| `T-02` | `MovementRepository`: los métodos **activos con sus exclusiones**, en **una sentencia** y con `LEFT JOIN` | `T-01` | Tres métodos con exclusiones **no producen cuatro consultas**. Con `JOIN` interno el catálogo vendría vacío, y eso lo detecta `T-07` | **Pendiente** |
| `T-03` | DTOs: `PaymentMethodCatalogResponse` envuelto en `content`, con `excludedCountries` **siempre presente** | — | `CA-MV-030` y `CA-MV-031` | **Pendiente** |
| `T-04` | `ListPaymentMethodsService`, `@Transactional(readOnly = true)` | `T-02`, `T-03` | Ordena por código, de forma estable | **Pendiente** |
| `T-05` | `PaymentMethodController`: `GET /api/v1/payment-methods`, **sin `@PreAuthorize`** | `T-04` | `CA-MV-032` y `CA-MV-033`: responde a un actor sin ninguna autoridad, y `401` sin token | **Pendiente** |
| `T-06` | `EndpointPermissionsIT`: declarar la ruta en `SIN_PERMISO_A_PROPOSITO` | `T-05` | La lista blanca lo recoge **con su motivo escrito**, en lugar de que la prueba de permisos falle | **Pendiente** |
| `T-07` | Pruebas de los criterios de `spec.md` §12 | `T-05` | `CA-MV-027` a `CA-MV-034` | **Pendiente** |
| `T-08` | **La prueba que afirma la ausencia**: registrar una venta con un método excluido **entra** | `T-01`, `RF-MV-001 · T-13` | `CA-MV-034`. Es la única tarea que impide que alguien convierta esto en una validación sin decidirlo. Ver §4 | **Pendiente** |
| `T-09` | OpenAPI: que la restricción **es informativa** y que el filtro lo aplica el cliente | `T-05` | El contrato publicado lo dice con esas palabras | **Pendiente** |
| `T-10` | Comprobar las dos enmiendas de `plan.md` §8 y llevar la matriz al estado final | `T-07` | `RF-MV-009` deja de estar en `Pendiente`, y la observación 2 de `modelo-datos.md` §6 queda cerrada de verdad | **Pendiente** |

## 2. Orden de ejecución

**`T-01` primero, y es la única que toca el esquema.** El resto es una lectura.

**`T-02` antes que los DTOs, y no al revés.** La forma de la respuesta depende de si la colección anidada se puede traer de una vez: si no se pudiera, el contrato tendría que cambiar —dos llamadas, o exclusiones aparte— y es mejor descubrirlo antes de haber escrito lo que las publica.

**`T-06` va con `T-05` y no después.** `EndpointPermissionsIT` recorre todos los endpoints y exige que cada uno declare permiso; una ruta nueva sin permiso **rompe esa prueba** hasta que se declara la excepción. Hacerlo en el mismo pase evita el rojo intermedio que invita a «arreglarlo» poniéndole un permiso que no le toca.

**`T-08` es la última que importa, y la que más fácil se cae.** No prueba lo que este requerimiento hace: prueba lo que **no** hace. Es la que sostiene la decisión entera de `spec.md` §2, y sin ella «la restricción es informativa» solo está escrito en documentos.

## 3. Cobertura de los criterios de aceptación

| Criterio | Tareas |
|---|---|
| `CA-MV-027`, `CA-MV-028` | `T-02`, `T-07` |
| `CA-MV-029` | `T-01`, `T-02`, `T-03`, `T-07` |
| `CA-MV-030` | `T-02`, `T-03`, `T-07` |
| `CA-MV-031` | `T-03`, `T-07` |
| `CA-MV-032`, `CA-MV-033` | `T-05`, `T-06`, `T-07` |
| `CA-MV-034` | `T-08` |

**`T-06`, `T-09` y `T-10` no cubren ningún criterio**, y quedan enumeradas para que su ausencia de esta tabla no se lea como que sobran: la primera defiende una excepción declarada, la segunda es el contrato publicado y la tercera, los documentos que gobiernan.

## 4. Bloqueos

**Ninguno.** Es el primer requerimiento de `MV` que no depende de `RF-SP-045` ni de **D-26**, y el motivo es exactamente la decisión que lo define: **no valida nada**, de modo que no necesita saber de qué país es nadie.

**Y queda una pregunta abierta que NO bloquea** (`spec.md` §14): de qué país se decidiría el día que esto tenga que impedir un cobro. Se anota aquí en lugar de omitirse, porque el día que se responda **este requerimiento cambia** — dejaría de ser una lectura y pasaría a tener una regla.

## 5. Definición de terminado

- Las diez tareas `Hecha` con su verificación pasando, y `./mvnw clean verify` en verde.
- **La suite de países en verde sin un solo cambio**: la clave foránea nueva no puede alterar el comportamiento de `RF-SP-020` a `RF-SP-022`.
- **`CA-MV-034` pasando**, que es la que afirma que esta consulta informa y no restringe.
- La matriz de trazabilidad y el contrato publicado al día.
