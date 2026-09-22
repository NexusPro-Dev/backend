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
| Enmendadas | 09-09-2026 — `RN-MV-024`: la ruta pasa a ser pública. Entran `T-11` a `T-15`, y `T-05` deja de exigir `401` sin token |

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
| `T-05` | `PaymentMethodController`: `GET /api/v1/payment-methods`, **sin `@PreAuthorize`** | `T-04` | `CA-MV-032` y `CA-MV-033`: responde a un actor sin ninguna autoridad — y **desde el 09-09-2026 también sin token**, ver `T-11` | **Pendiente** |
| `T-06` | `EndpointPermissionsIT`: declarar la ruta en `SIN_PERMISO_A_PROPOSITO` | `T-05` | La lista blanca lo recoge **con su motivo escrito**, en lugar de que la prueba de permisos falle | **Pendiente** |
| `T-07` | Pruebas de los criterios de `spec.md` §12 | `T-05` | `CA-MV-027` a `CA-MV-034` | **Pendiente** |
| `T-08` | **La prueba que afirma la ausencia**: registrar una venta con un método excluido **entra** | `T-01`, `RF-MV-001 · T-13` | `CA-MV-034`. Es la única tarea que impide que alguien convierta esto en una validación sin decidirlo. Ver §4 | **Pendiente** |
| `T-09` | OpenAPI: que la restricción **es informativa** y que el filtro lo aplica el cliente | `T-05` | El contrato publicado lo dice con esas palabras | **Pendiente** |
| `T-10` | Comprobar las dos enmiendas de `plan.md` §8 y llevar la matriz al estado final | `T-07` | `RF-MV-009` deja de estar en `Pendiente`, y la observación 2 de `modelo-datos.md` §6 queda cerrada de verdad | **Pendiente** |
| `T-11` | **`SecurityConfig`**: la ruta entra en `CATALOGOS_PUBLICOS` —la lista que **casa por método**—, no en `RUTAS_PUBLICAS` | `T-05` | Sin token, `GET /api/v1/payment-methods` responde `200` (`CA-MV-033`, invertido). La elección de lista se justifica en `plan.md` §5.1 | **Pendiente** |
| `T-12` | **`RateLimitFilter`**: cuarto catálogo público, **reutilizando la política `public-catalog`** y con cubo propio | `T-11` | No se declara política nueva en `application.yml`: la ruta se acota con los mismos 120/min por origen que los otros tres | **Pendiente** |
| `T-13` | `EndpointPermissionsIT`: **reescribir el motivo** de la entrada que ya existía. Deja de ser «autenticado y sin permiso» y pasa a ser **pública** | `T-11` | La lista blanca dice por qué es pública y **qué no cuesta**: ningún permiso queda huérfano | **Pendiente** |
| `T-14` | Pruebas de la apertura: `CA-MV-033` **invertido**, `CA-MV-051` y `CA-MV-052` | `T-11` | Sin token: `200`, **la misma respuesta** que con token, y **sin lo desactivado ni lo `INTERNO`** | **Pendiente** |
| `T-15` | OpenAPI: que la consulta **es pública**, que el `401` desaparece y que lo que sale es lo **activo y `PUBLICO`** | `T-11` | El contrato regenerado ya no declara `401` en esta operación, y la prosa de `@Operation` **no sigue diciendo «no exige permiso, solo estar autenticado»** | **Pendiente** |

## 2. Orden de ejecución

**`T-01` primero, y es la única que toca el esquema.** El resto es una lectura.

**`T-02` antes que los DTOs, y no al revés.** La forma de la respuesta depende de si la colección anidada se puede traer de una vez: si no se pudiera, el contrato tendría que cambiar —dos llamadas, o exclusiones aparte— y es mejor descubrirlo antes de haber escrito lo que las publica.

**`T-06` va con `T-05` y no después.** `EndpointPermissionsIT` recorre todos los endpoints y exige que cada uno declare permiso; una ruta nueva sin permiso **rompe esa prueba** hasta que se declara la excepción. Hacerlo en el mismo pase evita el rojo intermedio que invita a «arreglarlo» poniéndole un permiso que no le toca.

**`T-08` es la última que importa, y la que más fácil se cae.** No prueba lo que este requerimiento hace: prueba lo que **no** hace. Es la que sostiene la decisión entera de `spec.md` §2, y sin ella «la restricción es informativa» solo está escrito en documentos.

**`T-11` a `T-15` van juntas y en ese orden, y la que manda es `T-11`.** Abrir la ruta sin `T-13` deja `EndpointPermissionsIT` diciendo algo que dejó de ser cierto —la entrada existe, y su motivo describe un endpoint autenticado— y abrirla sin `T-12` la deja **sin cota**, que es el descuido que no falla en ninguna prueba: la ruta responde igual, y sencillamente se puede recorrer en bucle.

**`T-15` es la mitad que no se regenera sola.** El esquema de `docs/api/` lo reescribe `OpenApiContractIT` —el `401` desaparece del contrato sin que nadie lo toque—, pero **la prosa de `@Operation` no sale de ninguna parte**: seguiría diciendo «no exige permiso, solo estar autenticado», que a partir de hoy es falso.

## 3. Cobertura de los criterios de aceptación

| Criterio | Tareas |
|---|---|
| `CA-MV-027`, `CA-MV-028` | `T-02`, `T-07` |
| `CA-MV-029` | `T-01`, `T-02`, `T-03`, `T-07` |
| `CA-MV-030` | `T-02`, `T-03`, `T-07` |
| `CA-MV-031` | `T-03`, `T-07` |
| `CA-MV-032`, `CA-MV-033` | `T-05`, `T-06`, `T-07`, `T-11`, `T-14` |
| `CA-MV-034` | `T-08` |
| `CA-MV-051`, `CA-MV-052` | `T-11`, `T-14` |

**`T-06`, `T-09`, `T-10`, `T-12`, `T-13` y `T-15` no cubren ningún criterio**, y quedan enumeradas para que su ausencia de esta tabla no se lea como que sobran: defienden una excepción declarada, la cota de tasa, el contrato publicado y los documentos que gobiernan. **`T-12` es la que más fácil se olvida** — nada falla si no se hace: la ruta funciona, y sencillamente queda sin cota.

## 4. Bloqueos

**Ninguno.** Es el primer requerimiento de `MV` que no depende de `RF-SP-045` ni de **D-26**, y el motivo es exactamente la decisión que lo define: **no valida nada**, de modo que no necesita saber de qué país es nadie.

**Y queda una pregunta abierta que NO bloquea** (`spec.md` §14): de qué país se decidiría el día que esto tenga que impedir un cobro. Se anota aquí en lugar de omitirse, porque el día que se responda **este requerimiento cambia** — dejaría de ser una lectura y pasaría a tener una regla.

## 5. Definición de terminado

- Las quince tareas `Hecha` con su verificación pasando, y `./mvnw clean verify` en verde. **Las cinco últimas entran el 09-09-2026** con la apertura de la ruta (`RN-MV-024`).
- **La suite de países en verde sin un solo cambio**: la clave foránea nueva no puede alterar el comportamiento de `RF-SP-020` a `RF-SP-022`.
- **`CA-MV-034` pasando**, que es la que afirma que esta consulta informa y no restringe.
- La matriz de trazabilidad y el contrato publicado al día.
