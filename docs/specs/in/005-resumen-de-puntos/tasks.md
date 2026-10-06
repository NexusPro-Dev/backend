# TASKS — `RF-IN-005` Consultar el resumen de puntos

| Campo | Valor |
|---|---|
| Requerimiento | `RF-IN-005` |
| Especificación | [`spec.md`](spec.md) v0.1.0 |
| Plan | [`plan.md`](plan.md) v0.1.0 |
| `plan.md` aprobado el | 06-10-2026 |
| Estado | **En revisión** |
| Issue | Pendiente de crear |
| Rama | `develop` (commits directos desde el 05-10-2026) |

!!! info "Qué va en este documento"

    **Qué hay que hacer, en qué orden y cómo se comprueba.** Nada de por qué — eso está en `spec.md` y `plan.md`.

---

## 1. Tareas

**Estados:** `Pendiente` · `En curso` · `Hecha` · `Bloqueada`.

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-01` | **`V76`**: `indicators:read-points-summary` (`…-5e7ad8000005`) a `FUNCIONARIO` y `VENDEDOR` por tipo; guardas 197 / 197 / 195 / ningún `CONSUMIDOR` / contención | — | Recuentos del catálogo a 197; `SystemRolesSeedIT` y `IntegrationTestBase` con el permiso | Pendiente |
| `T-02` | `MV` publica `PointsFigures`; `JpaPointsFigures` con las dos sentencias de `plan.md` §4.4 y el mapeo que falla ante un evento desconocido | — | Compila; no importa nada de `IN` | Pendiente |
| `T-03` | `PointsSummaryResponse` con `@Schema` propios; `GetPointsSummaryService` con el corte a ceros y la unión por moneda | `T-02` | Monedas por código; todo en positivo | Pendiente |
| `T-04` | `PointsIndicatorsController`: `GET /api/v1/indicators/points/summary`, documentado | `T-03` | La ruta en `PERMISO_DE_CADA_OPERACION` | Pendiente |
| `T-05` | `PointsSummaryIT`: `CA-IN-041` a `CA-IN-049`, con compras, pagos y ajustes por las rutas de `MV` | `T-01`, `T-04` | `CA-IN-044` cuadra | Pendiente |
| `T-06` | Contrato regenerado y prosa releída; `api/index.md`, `requirements/mv.md` §3, `architecture.md` §15.2, `security.md` (sembrado), `requirements/in.md` y matriz | `T-05` | Solo altas en el contrato | Pendiente |

---

## 2. Cobertura de los criterios de aceptación

| Criterio | Tarea |
|---|---|
| `CA-IN-041` a `CA-IN-045` | `T-02`, `T-05` |
| `CA-IN-046`, `CA-IN-047` | `T-03`, `T-05` |
| `CA-IN-048` | `T-02`, `T-05` |
| `CA-IN-049` | `T-01`, `T-04`, `T-05` |

---

## 3. Desviaciones respecto del plan

Ninguna todavía.

---

## 4. Bloqueos declarados

Ninguno.

---

## 5. Definición de terminado

- [ ] `./mvnw clean verify` en verde.
- [ ] Los nueve criterios de aceptación con prueba.
- [ ] Contrato OpenAPI regenerado, **con la prosa releída**.
- [ ] `requirements/in.md`, `requirements/mv.md`, `architecture.md`, `security.md`, `requirements.md` y `api/index.md` actualizados.
- [ ] **`tasks.md` aprobadas por el responsable del proyecto.**
