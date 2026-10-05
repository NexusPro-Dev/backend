# TASKS — `RF-MV-047` Consultar la conversión vigente de cada país

| Campo | Valor |
|---|---|
| Requerimiento | `RF-MV-047` |
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
| `T-01` | `currentAll(instante, país)` en el repositorio, en una sentencia | `RF-MV-046` `T-01`, `T-03` | Una sola sentencia, contada | Pendiente |
| `T-02` | `CountryConversionRateService.current` y el `GET /movements/conversion-rates` con `countryId` opcional | `T-01` | Documentado con los códigos de `plan.md` §4 | Pendiente |
| `T-03` | `CountryConversionRatesIT`: `CA-MV-557` a `CA-MV-561` | `T-02` | Cada criterio afirmado en el cuerpo de su prueba | Pendiente |
| `T-04` | `EndpointPermissionsIT`; contrato con la prosa releída | `T-03` | | Pendiente |

---

## 2. Orden de ejecución

`T-01` → `T-02` → `T-03` → `T-04`, después de `RF-MV-046` `T-03`.

---

## 3. Cobertura de los criterios de aceptación

| Criterio | Tarea |
|---|---|
| `CA-MV-557` a `CA-MV-560` | `T-01`, `T-03` |
| `CA-MV-561` | `T-02`, `T-03` |

---

## 4. Bloqueos

Ninguno.

---

## 5. Definición de terminado

- [ ] Las suites afectadas en verde.
- [ ] Los cinco criterios de aceptación con prueba, **cada uno afirmado en el cuerpo de la prueba**.
- [ ] Contrato OpenAPI regenerado, **con la prosa releída**.
- [ ] `requirements.md` al día.
- [ ] **`tasks.md` aprobadas por el responsable del proyecto.**
