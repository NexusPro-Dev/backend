# TASKS — `RF-MV-002` Comprar un producto para uno mismo

| Campo | Valor |
|---|---|
| Requerimiento | `RF-MV-002` |
| Plan | [`plan.md`](plan.md), aprobado el 02-09-2026 |
| Versión | 0.1.0 |
| Estado | **En revisión** |
| Autor | Responsable técnico |
| Aprobadas por | Pendiente |
| Fecha de aprobación | Pendiente |
| Issue | Pendiente de crear |
| Rama | `feature/venta-de-productos` |

!!! info "Qué va en este documento"

    **En qué pasos se construye** lo que `plan.md` decidió, con su dependencia y su verificación. Ninguna tarea se da por `Hecha` sin que su verificación pase.

!!! abstract "Seis tareas, y ninguna toca el esquema"

    Todo lo que esta operación necesita lo construye `RF-MV-001`. Lo que queda es **la segunda puerta**: quién es el cliente, de dónde sale la fecha, y qué se devuelve.

---

## 1. Tareas

**Estados:** `Pendiente` · `En curso` · `Hecha` · `Bloqueada`.

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-01` | `RegisterSaleService` recibe **el cliente y la fecha ya resueltos**, y pierde toda rama que pregunte por dónde entró la petición | `RF-MV-001` · `T-11` | El servicio no conoce al actor ni tiene condicionales sobre él | **Pendiente** |
| `T-02` | `PurchaseRequest`: método de pago y líneas. **Sin cliente y sin fecha** | `T-01` | Un `clientId` en el cuerpo **no tiene dónde caer**: no existe en la representación | **Pendiente** |
| `T-03` | `PurchaseResponse`: la venta **sin el vendedor**, como clase propia y no como campo vacío | `T-01` | Ningún consumidor tiene que interpretar una ausencia | **Pendiente** |
| `T-04` | `POST /api/v1/movements/mine`, con el cliente **resuelto del actor antes de mirar el cuerpo** | `T-02`, `T-03` | `201` con `Location`; `401` sin autenticar y **ningún `403`** | **Pendiente** |
| `T-05` | Pruebas de los criterios de `spec.md` §12 | `T-04` | `CA-MV-019` a `CA-MV-026` | **Pendiente** |
| `T-06` | Documentación OpenAPI: la operación **no exige permiso**, no admite cliente ni fecha, y **no devuelve el vendedor** | `T-04` | El contrato publicado dice las tres cosas | **Pendiente** |

## 2. Orden de ejecución

**`T-01` es la tarea de verdad, y es una refactorización y no una función nueva.** Sacar del caso de uso la resolución del cliente es lo que permite que las dos puertas compartan las nueve verificaciones sin que ninguna de las dos tenga una rama propia. Hacerla **después** de escribir el endpoint invitaría a copiar el servicio y ajustarlo, que es la alternativa que `plan.md` §9 descarta.

**`T-04` tiene su verificación en el orden y no en el resultado.** Que el cliente salga del actor no se prueba mirando la respuesta feliz —sería la misma— sino **enviando un cliente ajeno** y comprobando que la venta no es suya. Esa prueba está en `T-05` (`CA-MV-021`) y es la razón de que `T-04` describa un orden.

**`T-05` incluye una prueba que compara las dos puertas** (`CA-MV-026`). Es la única que se rompería si alguien añadiera una condición a una sola de las dos, y por eso vale más que las otras siete juntas.

## 3. Cobertura de los criterios de aceptación

| Criterio | Tareas |
|---|---|
| `CA-MV-019` | `T-04`, `T-05` |
| `CA-MV-020` | `T-03`, `T-05` |
| `CA-MV-021` | `T-02`, `T-04`, `T-05` |
| `CA-MV-022` | `T-01`, `T-02`, `T-05` |
| `CA-MV-023`, `CA-MV-024`, `CA-MV-025` | `T-01`, `T-05` |
| `CA-MV-026` | `T-01`, `T-05` |

**`T-06` no cubre ningún criterio**: es el contrato publicado, y queda enumerada para que no parezca que sobra.

## 4. Bloqueos

**El mismo que `RF-MV-001`: `RF-SP-045`, sin código.** Aquí pesa más, porque **esta operación solo la pueden usar clientes**: sin personas colgadas de un vendedor no hay ni una sola prueba de camino feliz que se pueda escribir.

**No hay bloqueo de decisión.** D-26 no toca a este requerimiento por lo mismo que no toca al anterior: comprar **no escribe** en `SP`.

## 5. Definición de terminado

- Las seis tareas `Hecha` con su verificación pasando, y `./mvnw clean verify` en verde.
- **`CA-MV-021` pasando**, que es la que impide comprar a nombre de otro.
- **`CA-MV-026` pasando**, que es la que garantiza que los siete requerimientos siguientes no tengan que enterarse de que hay dos entradas.
- El contrato publicado al día, con la operación declarada **sin permiso**.
