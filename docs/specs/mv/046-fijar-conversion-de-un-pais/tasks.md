# TASKS — `RF-MV-046` Fijar la conversión de un país

| Campo | Valor |
|---|---|
| Requerimiento | `RF-MV-046` |
| Especificación | [`spec.md`](spec.md) v0.1.0 |
| Plan | [`plan.md`](plan.md) v0.1.0 |
| `plan.md` aprobado el | 05-10-2026 |
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
| `T-01` | `V67__mv_conversion_por_pais.sql` (`plan.md` §2): la tabla, sus claves, `CHECK`, índice y unicidad, los dos permisos y sus guardas | — | Aplicada sobre la base de pruebas; las guardas pasan | Pendiente |
| `T-02` | Recuentos del catálogo 179 → 181 en todas las suites que lo cuentan | `T-01` | `grep -rnE "\b179L?\b" src/test` sin restos del catálogo | Pendiente |
| `T-03` | `CountryConversionRate` y `CountryConversionRateRepository` con `current` | `T-01` | La vigente es la de `valid_from` más reciente no futura | Pendiente |
| `T-04` | `CountryConversionRateService.set`, `SetCountryConversionRateRequest`, `CountryConversionRateResponse` | `T-03` | La validación va antes de tocar nada; `FA-001` no escribe | Pendiente |
| `T-05` | `CountryConversionRateController`: `POST /movements/conversion-rates` | `T-04` | Documentado con los códigos de `plan.md` §4 | Pendiente |
| `T-06` | `CountryConversionRatesIT`: `CA-MV-548` a `CA-MV-556`; prueba de esquema de los dos `CHECK` | `T-05` | Cada criterio afirmado en el cuerpo de su prueba | Pendiente |
| `T-07` | `EndpointPermissionsIT` (`PERMISO_DE_CADA_OPERACION`); contrato con la prosa releída | `T-06` | | Pendiente |

---

## 2. Orden de ejecución

`T-01` → `T-02` → `T-03` → `T-04` → `T-05` → `T-06` → `T-07`. **`RF-MV-047` depende de `T-01` y `T-03`.**

---

## 3. Cobertura de los criterios de aceptación

| Criterio | Tarea |
|---|---|
| `CA-MV-548` a `CA-MV-550`, `CA-MV-556` | `T-03`, `T-04`, `T-06` |
| `CA-MV-551` a `CA-MV-553` | `T-04`, `T-06` |
| `CA-MV-554`, `CA-MV-555` | `T-05`, `T-06` |

---

## 4. Bloqueos

Ninguno.

---

## 5. Definición de terminado

- [ ] Las suites afectadas en verde.
- [ ] Los nueve criterios de aceptación con prueba, **cada uno afirmado en el cuerpo de la prueba**.
- [ ] Contrato OpenAPI regenerado, **con la prosa releída**.
- [ ] `requirements.md` al día.
- [ ] **`tasks.md` aprobadas por el responsable del proyecto.**
