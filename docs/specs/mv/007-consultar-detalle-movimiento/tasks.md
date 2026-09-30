# TASKS — `RF-MV-007` Consultar el detalle de un movimiento, con su comprobante

| Campo | Valor |
|---|---|
| Requerimiento | `RF-MV-007` |
| Especificación | [`spec.md`](spec.md) v0.1.0 |
| Plan | [`plan.md`](plan.md) v0.1.0 |
| `plan.md` aprobado el | 30-09-2026 |
| Estado | **En revisión** |
| Issue | Pendiente de crear |
| Rama | `feature/detalle-de-movimiento` |

!!! info "Qué va en este documento"

    **Qué hay que hacer, en qué orden y cómo se comprueba.** Nada de por qué — eso está en `spec.md` y `plan.md`.

---

## 1. Tareas

**Estados:** `Pendiente` · `En curso` · `Hecha` · `Bloqueada`.

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-01` | `GetMovementService`: `findById` + `SaleDetailMapper.de`, `EX-001` si no existe; sin actor | — | Lo ajeno se abre | Pendiente |
| `T-02` | `MovementController`: `GET /{id}` con `movements:read`, al final de la clase, con los códigos de `plan.md` §4 | `T-01` | Las rutas literales siguen respondiendo | Pendiente |
| `T-03` | `MovementDetailIT`: `CA-MV-287` a `CA-MV-292` | `T-02` | `CA-MV-290` compara el cuerpo entero | Pendiente |
| `T-04` | `EndpointPermissionsIT`; contrato regenerado con la prosa releída; `requirements.md`, `requirements/mv.md`, `api/index.md` | `T-03` | | Pendiente |

## 2. Orden de ejecución

`T-01` → `T-02` → `T-03` → `T-04`.

## 3. Cobertura de los criterios de aceptación

| Criterio | Tarea |
|---|---|
| `CA-MV-287`, `CA-MV-288`, `CA-MV-289` | `T-01`, `T-03` |
| `CA-MV-290` | `T-01`, `T-03` |
| `CA-MV-291` | `T-02`, `T-03` |
| `CA-MV-292` | `T-02`, `T-03`, `T-04` |

## 4. Bloqueos

Ninguno.

## 5. Definición de terminado

- [ ] `./mvnw clean verify` en verde.
- [ ] Los seis criterios de aceptación con prueba.
- [ ] Contrato OpenAPI regenerado, **con la prosa releída**.
- [ ] `requirements.md` y `requirements/mv.md` actualizados.
- [ ] **`tasks.md` aprobadas por el responsable del proyecto.**
