# TASKS — `RF-CM-016` Consultar las comisiones afftrack de rol

| Campo | Valor |
|---|---|
| Requerimiento | `RF-CM-016` |
| Especificación | [`spec.md`](spec.md) v0.1.0 |
| Plan | [`plan.md`](plan.md) v0.1.0 |
| `plan.md` aprobado el | 29-09-2026 |
| Estado | **En revisión** |
| Issue | Pendiente de crear |
| Rama | `feature/comision-afftrack` |

!!! info "Qué va en este documento"

    **Qué hay que hacer, en qué orden y cómo se comprueba.** Nada de por qué — eso está en `spec.md` y `plan.md`.

---

## 1. Tareas

**Estados:** `Pendiente` · `En curso` · `Hecha` · `Bloqueada`.

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-01` | Repositorio de consulta, servicio, `record`s con `@Schema(name)`, el `GET` y la ruta en `PERMISO_DE_CADA_OPERACION` | `RF-CM-015` `T-01`, `T-05` | `EndpointPermissionsIT` | Pendiente |
| `T-02` | `ListAfftrackRatesIT`: `CA-CM-217` a `CA-CM-220` | `T-01`, `RF-CM-015` `T-07` | Número de sentencias en `CA-CM-220` | Pendiente |
| `T-03` | Contrato OpenAPI y `requirements.md` | `T-02` | | Pendiente |

---

## 2. Orden de ejecución

**Después de `RF-CM-015`**: `T-01` → `T-02` → `T-03`.

---

## 3. Cobertura de los criterios de aceptación

| Criterio | Tarea |
|---|---|
| `CA-CM-217` a `CA-CM-220` | `T-01`, `T-02` |

---

## 4. Bloqueos

**`RF-CM-015`**: la migración y el alta con la que se siembran los datos de prueba.

---

## 5. Definición de terminado

- [ ] `./mvnw clean verify` en verde.
- [ ] Los cuatro criterios de aceptación con prueba.
- [ ] Contrato y `requirements.md` actualizados.
- [ ] **`tasks.md` aprobadas por el responsable del proyecto.**
