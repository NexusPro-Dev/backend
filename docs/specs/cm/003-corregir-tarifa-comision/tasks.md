# TASKS — `RF-CM-003` Corregir una tarifa de comisión

| Campo | Valor |
|---|---|
| Requerimiento | `RF-CM-003` |
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
| `T-01` | `CommissionRate.update(...)`: aplica lo enviado y **devuelve qué cambió** | `RF-CM-001` · `T-07` | Un campo que no entra en el diff no se audita, y eso se ve en la línea en que se asigna | `Pendiente` |
| `T-02` | Los **tres estados** en el porcentaje y en el fin de vigencia | `T-01` | Ausente no toca; nulo **vacía** el fin y **se rechaza** en el porcentaje | `Pendiente` |
| `T-03` | `updatedAt` **solo se mueve si algo cambió** | `T-01` | Una petición que no cambia nada deja la marca donde estaba | `Pendiente` |
| `T-04` | DTO con los campos **no corregibles declarados**, para rechazarlos con `VAL-009` | `T-02` | Enviar el rol devuelve «no se puede corregir», no «propiedad desconocida» | `Pendiente` |
| `T-05` | `UpdateCommissionRateService`, con el orden de `spec.md` §8 | `T-02`, `T-04` | Cada rechazo con su código | `Pendiente` |
| `T-06` | **Volcado explícito antes de salir de la transacción**, para que la violación del solapamiento se pueda traducir | `T-05` | El solape al corregir devuelve **`409` y no `500`** | `Pendiente` |
| `T-07` | `PATCH /api/v1/commission-rates/{id}` | `T-05` | `200`, `400`, `403`, `404`, `409` | `Pendiente` |
| `T-08` | Evento de edición con **solo lo que cambió**, y **sin evento** cuando no cambia nada | `T-05` | Los dos casos, comprobados en la auditoría | `Pendiente` |
| `T-09` | Pruebas de los criterios de `spec.md` §12 | `T-07` | `CA-CM-023` a `CA-CM-030` | `Pendiente` |
| `T-10` | Prueba de **reabrir una vigencia que pisa otra** | `T-09` | `FA-002` con choque: se rechaza | `Pendiente` |
| `T-11` | Documentación OpenAPI del endpoint | `T-07` | Los tres campos y los cinco estados | `Pendiente` |
| `T-12` | Actualizar la matriz de `docs/requirements.md` | `T-09` | La fila de `RF-CM-003` refleja el estado | `Pendiente` |

## 2. Orden de ejecución

`T-06` es la que más se olvida y la que más cuesta descubrir: **`RF-SP-027` ya la vivió** —el `UPDATE` sale en el `commit`, fuera de todo `try`, y el `409` se convierte en `500`—. Se escribe junto con su prueba y no después.

## 3. Cobertura de los criterios de aceptación

| Criterio | Tareas |
|---|---|
| `CA-CM-023` a `CA-CM-026` | `T-01`, `T-02`, `T-09` |
| `CA-CM-027` | `T-04`, `T-09` |
| `CA-CM-028` | `T-06`, `T-09`, `T-10` |
| `CA-CM-029` | `T-05`, `T-09` |
| `CA-CM-030` | `T-01`, `T-03`, `T-08`, `T-09` |

## 4. Bloqueos

Ninguno.

## 5. Definición de terminado

- Las doce tareas `Hecha` con su verificación pasando.
- `./mvnw clean verify` en verde, **incluida la que comprueba que el solape devuelve `409` y no `500`**.
- La matriz y el contrato publicado al día.
