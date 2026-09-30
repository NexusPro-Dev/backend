# TASKS — `RF-MV-026` Consultar las tasas de puntos vigentes

| Campo | Valor |
|---|---|
| Requerimiento | `RF-MV-026` |
| Especificación | [`spec.md`](spec.md) v0.1.0 |
| Plan | [`plan.md`](plan.md) v0.1.0 |
| `plan.md` aprobado el | 30-09-2026 |
| Estado | **En revisión** |
| Issue | Pendiente de crear |
| Rama | `feature/compra-de-puntos` |

!!! info "Qué va en este documento"

    **Qué hay que hacer, en qué orden y cómo se comprueba.** Nada de por qué — eso está en `spec.md` y `plan.md`.

---

## 1. Tareas

**Estados:** `Pendiente` · `En curso` · `Hecha` · `Bloqueada`.

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-01` | `JpaPointsRateRepository.vigentes(instante)` | `RF-MV-025` `T-04` | Una sentencia; solo monedas activas | Pendiente |
| `T-02` | `ListPointsRatesService` | `T-01` | | Pendiente |
| `T-03` | `PointsRateController`: `GET /movements/points-rates` | `T-02` | Documentado con los códigos de `plan.md` §4 | Pendiente |
| `T-04` | `ListPointsRatesIT`: `CA-MV-301` a `CA-MV-305` | `T-03` | Recuento de sentencias | Pendiente |
| `T-05` | `EndpointPermissionsIT`; contrato con la prosa releída; `requirements.md` | `T-04` | | Pendiente |

---

## 2. Orden de ejecución

**Después de `RF-MV-025`**: `T-01` → `T-02` → `T-03` → `T-04` → `T-05`.

---

## 3. Cobertura de los criterios de aceptación

| Criterio | Tarea |
|---|---|
| `CA-MV-301` a `CA-MV-304` | `T-01`, `T-04` |
| `CA-MV-305` | `T-03`, `T-04` |

---

## 4. Bloqueos

**`RF-MV-025`**, que trae la tabla, el permiso y la lectura de la vigente.

---

## 5. Definición de terminado

- [ ] `./mvnw clean verify` en verde.
- [ ] Los cinco criterios de aceptación con prueba, **cada uno afirmado en el cuerpo de la prueba**.
- [ ] Contrato OpenAPI regenerado, **con la prosa releída**.
- [ ] `requirements.md` actualizado.
- [ ] **`tasks.md` aprobadas por el responsable del proyecto.**
