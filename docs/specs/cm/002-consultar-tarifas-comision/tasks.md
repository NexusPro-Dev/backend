# TASKS — `RF-CM-002` Consultar las tarifas de comisión

| Campo | Valor |
|---|---|
| Requerimiento | `RF-CM-002` |
| Plan | [`plan.md`](plan.md), aprobado el 28-08-2026 |
| Estado | **En revisión** |
| Autor | Responsable técnico |
| Aprobadas por | Pendiente |
| Fecha de aprobación | Pendiente |
| Issue | Pendiente de crear |
| Rama | `feature/modulo-comisiones` |

!!! info "Qué va en este documento"

    **En qué pasos se construye** lo que `plan.md` decidió, con su dependencia y su verificación. Ninguna tarea se da por `Hecha` sin que su verificación pase.

---

## 1. Tareas

**Estados:** `Pendiente` · `En curso` · `Hecha` · `Bloqueada`.

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-01` | Puerto `CommissionRateQueryRepository` con los filtros de `plan.md` §4 | `RF-CM-001` · `T-01` | Compila; los filtros nulos significan «sin filtro» | **Hecha el 28-08-2026** |
| `T-02` | Adaptador con **una sentencia** y los tres `LEFT JOIN` que resuelven rol, producto y persona | `T-01` | Rol, producto y persona llegan resueltos sin consulta adicional | **Hecha el 28-08-2026** |
| `T-03` | El **predicado en un solo método**, incluido el filtro por fecha como pertenencia | `T-02` | `valid_from <= fecha AND (valid_to IS NULL OR valid_to >= fecha)` | **Hecha el 28-08-2026** |
| `T-04` | El **grado** calculado en el modelo de lectura | `T-02` | Los cuatro grados salen de qué columnas vienen nulas | **Hecha el 28-08-2026** |
| `T-05` | `ListCommissionRatesService`, de solo lectura | `T-03` | `@Transactional(readOnly = true)` | **Hecha el 28-08-2026** |
| `T-06` | DTO de filtros con `VAL-006` y `VAL-011` | `T-05` | Fecha mal formada y paginación fuera de límites | **Hecha el 28-08-2026** |
| `T-07` | `GET /api/v1/commission-rates` con la envoltura paginada del sistema | `T-05`, `T-06` | `200` con total y orden; `403` sin permiso | **Hecha el 28-08-2026** |
| `T-08` | Pruebas de los criterios de `spec.md` §12 | `T-07` | `CA-CM-014` a `CA-CM-022` | **Hecha el 28-08-2026** |
| `T-09` | **Prueba de número de sentencias**: una por página con independencia del número de filas | `T-08` | Es la que impide que los tres `JOIN` se conviertan en `N+1` en una refactorización | `Pendiente` |
| `T-10` | Prueba de que filtrar por persona **no** devuelve las que le aplican | `T-08` | `spec.md` §13: el listado no resuelve precedencia | `Pendiente` |
| `T-11` | Documentación OpenAPI del endpoint | `T-07` | Los seis parámetros y la envoltura | **Hecha el 28-08-2026** |
| `T-12` | Actualizar la matriz de `docs/requirements.md` | `T-08` | La fila de `RF-CM-002` refleja el estado | **Hecha el 28-08-2026** |

## 2. Orden de ejecución

`T-01` a `T-04` son la sentencia y su proyección, y van juntas. `T-09` y `T-10` se escriben **después** de las funcionales a propósito: las dos verifican algo que no se ve en la respuesta, y escribirlas antes tienta a aflojar la afirmación hasta que pase.

## 3. Cobertura de los criterios de aceptación

| Criterio | Tareas |
|---|---|
| `CA-CM-014`, `CA-CM-015` | `T-03`, `T-08` |
| `CA-CM-016` | `T-02`, `T-08` |
| `CA-CM-017`, `CA-CM-018` | `T-03`, `T-08` |
| `CA-CM-019` | `T-03`, `T-08` |
| `CA-CM-020` a `CA-CM-022` | `T-05`, `T-07`, `T-08` |

## 4. Bloqueos

| # | Bloqueo | Efecto | Quién lo levanta |
|---|---|---|---|
| 1 | **D-22, abierta** | Este es el requerimiento del módulo que puede tener que cambiar: hoy se construye con **alcance global explícito**. No impide construirlo; obliga a revisarlo después | Responsable del proyecto, al cerrar D-22 (issue #28) |

## 5. Definición de terminado

- Las doce tareas `Hecha` con su verificación pasando.
- `./mvnw clean verify` en verde, incluida la prueba de número de sentencias.
- La matriz y el contrato publicado al día.
