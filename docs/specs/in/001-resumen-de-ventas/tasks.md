# TASKS — `RF-IN-001` Consultar el resumen de ventas

| Campo | Valor |
|---|---|
| Requerimiento | `RF-IN-001` |
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
| `T-01` | **Migración de los cuatro permisos** (`V74` o la siguiente libre): serie `5e7ad8`, reparto a `FUNCIONARIO` y `VENDEDOR`, guardas del catálogo, de `SUPERADMIN` y `ADMIN`, de `CONSUMIDOR` sin ninguno y de contención | — | `PermissionsSeedIT`, `PermissionIT` y los recuentos del catálogo con el número nuevo; `IntegrationTestBase` repone los cuatro donde reponga los de tipo de rol | Pendiente |
| `T-02` | `MV` publica **`SalesFigures`** con `SalesScope`, `Interval` y `Summary`; javadoc con qué cuenta y qué no (solo `VENTA`, por línea, sin vendedor solo con `everything()`) | — | Compila; la interfaz no importa nada de `IN` ni de `SP` | Pendiente |
| `T-03` | `JpaSalesFigures.summary`: la sentencia de `plan.md` §4.4, de centésimas a decimales con `MinorUnits` al mapear | `T-02` | `SalesFiguresIT` en `MV`: por línea, una venta por `DISTINCT`, las tres situaciones, la moneda, el intervalo semiabierto | Pendiente |
| `T-04` | `SalesPeriodResolver`: por defecto, solo `from`, solo `to`, `VAL-001` a `VAL-003`, y el `Interval` en la zona de `BusinessCalendar` | — | `SalesPeriodResolverTest`: mes en curso, `to` solo, 366 sí y 367 no, bisiesto, día único, medianoche de Bogotá | Pendiente |
| `T-05` | `SalesScopeResolver`: la tabla de `plan.md` §3.2 | — | `SalesScopeResolverTest`, las nueve casillas, con `CommercialReach` doblado | Pendiente |
| `T-06` | `SalesIndicatorRequest`, `SalesSummaryResponse`, `IndicatorPeriod` con `@Schema` propios; `GetSalesSummaryService` con el corte a ceros sin consultar | `T-03`, `T-04`, `T-05` | El corte se ve por HTTP: ceros con `sellerId` ajeno | Pendiente |
| `T-07` | `SalesIndicatorsController`: `GET /api/v1/indicators/sales/summary`, documentado —qué ve cada tipo de rol, por qué puede no cuadrar con el listado, ceros fuera del alcance, un importe por moneda— | `T-06` | La ruta en `PERMISO_DE_CADA_OPERACION` de `EndpointPermissionsIT` | Pendiente |
| `T-08` | `SalesSummaryIT`: `CA-IN-001` a `CA-IN-014` sobre el árbol de `SalesIT` con importes distintos por línea y una venta de dos ramas | `T-01`, `T-07` | Cada nivel ve exactamente su suma; coste: una sentencia de suma con red de 2 y de 20 | Pendiente |
| `T-09` | `LayerRulesTest`: `IN` no depende de `domain` de otro módulo y nadie depende de `IN` | `T-07` | La regla falla si se rompe a propósito | Pendiente |
| `T-10` | Contrato OpenAPI regenerado y prosa releída; `docs/api/index.md`; `requirements/in.md` y `mv.md` (§3, `SalesFigures`); `architecture.md` §15.2; `security.md` §4.4 (sembrados); matriz | `T-08` | `openapi.json` declara la ruta con `x-required-permission` y ningún esquema fundido | Pendiente |

---

## 2. Cobertura de los criterios de aceptación

| Criterio | Tarea |
|---|---|
| `CA-IN-001` a `CA-IN-004` | `T-03`, `T-05`, `T-08` |
| `CA-IN-005`, `CA-IN-006` | `T-03`, `T-08` |
| `CA-IN-007`, `CA-IN-008` | `T-03`, `T-08` |
| `CA-IN-009`, `CA-IN-010` | `T-04`, `T-08` |
| `CA-IN-011`, `CA-IN-012` | `T-05`, `T-06`, `T-08` |
| `CA-IN-013` | `T-04`, `T-06`, `T-08` |
| `CA-IN-014` | `T-01`, `T-07`, `T-08` |

---

## 3. Desviaciones respecto del plan

Ninguna todavía.

---

## 4. Bloqueos declarados

1. **El número de la migración** se fija al construir: otra sesión documenta el doble factor de `SP` en paralelo y puede tomar `V74` antes.
2. **La pregunta de §14 de la spec** —contar por confirmación y no por ocurrencia— no bloquea; se decide con el responsable si se pide.

---

## 5. Definición de terminado

- [ ] `./mvnw clean verify` en verde.
- [ ] Los catorce criterios de aceptación con prueba.
- [ ] Contrato OpenAPI regenerado, **con la prosa releída**.
- [ ] `requirements/in.md`, `requirements/mv.md`, `architecture.md`, `security.md`, `requirements.md` y `api/index.md` actualizados.
- [ ] **`tasks.md` aprobadas por el responsable del proyecto.**
