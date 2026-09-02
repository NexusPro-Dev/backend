# TASKS — `RF-CM-002` Consultar las tasas de comisión

| Campo | Valor |
|---|---|
| Requerimiento | `RF-CM-002` |
| Plan | [`plan.md`](plan.md), aprobado el 02-09-2026 |
| Versión | 0.2.0 |
| Estado | **En revisión** |
| Autor | Responsable técnico |
| Aprobadas por | Pendiente |
| Fecha de aprobación | Pendiente |
| Issue | Pendiente de crear |
| Rama | `feature/flujos-de-pm-y-cm` |

!!! info "Qué va en este documento"

    **En qué pasos se construye** lo que `plan.md` decidió, con su dependencia y su verificación.

!!! warning "Las tareas están hechas antes que este documento"

    El código se rehízo el 02-09-2026 y esta lista viene detrás. **No planifica: registra.** La tercera compuerta del Art. I.6 sigue pendiente y por eso el documento está `En revisión`.

---

## 1. Tareas

**Estados:** `Pendiente` · `En curso` · `Hecha` · `Bloqueada`.

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-01` | Puerto y adaptador del catálogo, con el rol por `LEFT JOIN` | `RF-CM-001` · `T-01` | Cien tasas, **una sentencia** | **Hecha el 02-09-2026** |
| `T-02` | **La cuenta de asociaciones como subconsulta correlacionada** | `T-01`, `RF-CM-007` · `T-01` | Una tasa con dos asociaciones aparece **una vez** | **Hecha el 02-09-2026** |
| `T-03` | Orden del catálogo: código de rol, y porcentaje descendente dentro de cada rol | `T-01` | El orden se publica en la respuesta | **Hecha el 02-09-2026** |
| `T-04` | **Retirar del catálogo los filtros por producto, por persona y por fecha** | `T-01` | Los tres parámetros dejan de existir en la petición | **Hecha el 02-09-2026** |
| `T-05` | Puerto y adaptador de las personalizadas, con la persona por `LEFT JOIN` | `RF-CM-006` · `T-02` | La vigencia llega como fecha, no como instante | **Hecha el 02-09-2026** |
| `T-06` | Filtro por fecha de las personalizadas, **por pertenencia y no por igualdad** | `T-05` | Una tasa de enero a marzo sale filtrando por 15 de febrero | **Hecha el 02-09-2026** |
| `T-07` | Puerto y adaptador de la asociación, **en las dos direcciones** | `RF-CM-007` · `T-01` | El `JOIN` entra por la clave **compuesta** | **Hecha el 02-09-2026** |
| `T-08` | `CommissionRows`: las conversiones del driver, compartidas por los tres adaptadores | `RF-CM-001` · `T-07` | Ninguno de los tres las duplica | **Hecha el 02-09-2026** |
| `T-09` | Los tres servicios de listado | `T-02`, `T-05`, `T-07` | Cada uno con su orden fijo | **Hecha el 02-09-2026** |
| `T-10` | DTO de las páginas y de los ítems, con las colecciones de asociación **envueltas** | `T-09` | Las asociaciones van bajo `content`, no en la raíz | **Hecha el 02-09-2026** |
| `T-11` | `GET /api/v1/commission-rates` y `GET /api/v1/commission-rates/{id}/products` | `T-09`, `T-10` | `200`, `400`, `403` | **Hecha el 02-09-2026** |
| `T-12` | `GET /api/v1/user-commission-rates` | `T-09`, `T-10` | `200`, `400`, `403` | **Hecha el 02-09-2026** |
| `T-13` | `GET /api/v1/product-commission-rates`, **con recurso raíz propio** | `T-09`, `T-10` | No compite en forma con `/commission-rates/{id}` | **Hecha el 02-09-2026** |
| `T-14` | Pruebas de los criterios de `spec.md` §12 | `T-11`, `T-12`, `T-13` | `CA-CM-009` a `CA-CM-020` | **Hecha el 02-09-2026** |
| `T-15` | Documentación OpenAPI de las cuatro lecturas | `T-11`, `T-12`, `T-13` | Cada una dice qué significa su colección vacía | **Hecha el 02-09-2026** |
| `T-16` | Prueba de **número de sentencias** del catálogo | `T-14` | Impide que el `JOIN` se convierta en `N+1` | `Pendiente` |

## 2. Orden de ejecución

**`T-02` es la tarea con más riesgo escondido del requerimiento**, y su verificación no es la obvia. No comprueba que la cuenta sea correcta —eso es `CA-CM-010`— sino que **la tasa aparezca una sola vez**. Con un `LEFT JOIN` agrupado mal, el `LIMIT` de la paginación contaría filas del producto cartesiano y **pedir veinte tasas devolvería menos de veinte**. El síntoma no se parece a la causa: nadie miraría la cuenta de asociaciones al investigar por qué la paginación devuelve de menos.

**`T-04` es una tarea de quitar, y por eso está escrita.** Los filtros por producto, por persona y por fecha del catálogo **no se rompieron: dejaron de tener columna**. Conservarlos aceptando el parámetro y no filtrando nada habría sido lo peor de las dos opciones.

**`T-02`, `T-05` y `T-07` dependen de requerimientos posteriores**, y esa inversión es real: las tablas que consultan las crean `RF-CM-006` y `RF-CM-007`. En el orden de construcción esas dos fueron antes, aunque su número sea mayor.

**`T-16` queda pendiente.** La prueba de número de sentencias existía en la v0.1.0 sobre el listado anterior y hay que rehacerla contra el catálogo nuevo. No bloquea nada y se registra para que no desaparezca — es lo único que impide que una refactorización que «limpie» los `JOIN` traiga de vuelta las `N+1`.

## 3. Cobertura de los criterios de aceptación

| Criterio | Tareas |
|---|---|
| `CA-CM-009` | `T-01`, `T-03`, `T-11`, `T-14` |
| `CA-CM-010` | `T-02`, `T-14` |
| `CA-CM-011` | `T-02`, `T-14` |
| `CA-CM-012` | `T-01`, `T-14` |
| `CA-CM-013` | `T-03`, `T-14` |
| `CA-CM-014` | `T-04`, `T-14` |
| `CA-CM-015`, `CA-CM-017` | `T-05`, `T-12`, `T-14` |
| `CA-CM-016` | `T-06`, `T-14` |
| `CA-CM-018`, `CA-CM-019` | `T-07`, `T-11`, `T-13`, `T-14` |
| `CA-CM-020` | `T-11`, `T-12`, `T-13`, `T-14` |

## 4. Bloqueos

Ninguno.

**Queda declarado un condicionante**: este es el requerimiento que **D-22** puede tener que cambiar. El predicado de cada listado vive en un solo método para que ese día haya uno que tocar por listado y no tres.

## 5. Definición de terminado

- Quince de las dieciséis tareas `Hecha` con su verificación pasando. **`T-16` queda pendiente y declarada.**
- `./mvnw clean verify` en verde. **Comprobado el 02-09-2026**: 278 unitarias y 876 de integración.
- La matriz y el contrato publicado al día.
