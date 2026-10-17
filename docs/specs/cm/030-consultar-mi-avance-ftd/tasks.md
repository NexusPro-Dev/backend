# TASKS — `RF-CM-030` Consultar mi avance de FTD en el periodo abierto

| Campo | Valor |
|---|---|
| Requerimiento | `RF-CM-030` |
| Especificación | [`spec.md`](spec.md) v0.1.0 |
| Plan | [`plan.md`](plan.md) v0.1.0 |
| `plan.md` aprobado el | 17-10-2026 |
| Estado | **En revisión** |
| Issue | Pendiente de crear |
| Rama | `develop` |

!!! info "Qué va en este documento"

    **Qué hay que hacer, en qué orden y cómo se comprueba.** Nada de por qué — eso está en `spec.md` y `plan.md`.

---

## 1. Tareas

**Estados:** `Pendiente` · `En curso` · `Hecha` · `Bloqueada`.

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-01` | **`V117`**: `afftrack-settlements:read-own-progress` a todo rol que porte `commission-batches:list-own`, con sus guardas; los recuentos 252 → 253 y `ADMIN` 250 → 251 en las suites de `plan.md` §10, y `SystemRolesSeedIT` y `RoleDetailIT` | — | Las suites de siembra en verde | Pendiente |
| `T-02` | `AfftrackTierPicker.siguiente` y `AfftrackTierPickerTest`, nueva | — | `CA-CM-400`, `CA-CM-401` | Pendiente |
| `T-03` | `GetOwnAfftrackProgressService` y `AfftrackProgressResponse` con `@Schema(name)` | `T-02` | Compila | Pendiente |
| `T-04` | `GET /afftrack-settlements/mine/progress`, en `PERMISO_DE_CADA_OPERACION` | `T-01`, `T-03` | `EndpointPermissionsIT` | Pendiente |
| `T-05` | `AfftrackProgressIT`: `CA-CM-399` a `CA-CM-409` | `T-04` | — | Pendiente |
| `T-06` | Contrato OpenAPI, `api/index.md`, `security.md` (sembrado, en el bloque), `requirements/cm.md` y `requirements.md` | `T-05` | Diff del `json`; `OpenApiContractIT` | Pendiente |

---

## 2. Orden de ejecución

`T-01` y `T-02` → `T-03` → `T-04` → `T-05` → `T-06`.

---

## 3. Cobertura de los criterios de aceptación

| Criterio | Tarea |
|---|---|
| `CA-CM-399`, `CA-CM-402` a `CA-CM-408` | `T-03`, `T-05` |
| `CA-CM-400`, `CA-CM-401` | `T-02`, `T-05` |
| `CA-CM-409` | `T-01`, `T-04`, `T-05` |

---

## 4. Bloqueos

Ninguno.

---

## 5. Definición de terminado

- [ ] `./mvnw clean verify` en verde.
- [ ] Los once criterios de aceptación con prueba.
- [ ] Contrato y `requirements.md` actualizados.
- [ ] **`tasks.md` aprobadas por el responsable del proyecto.**
