# TASKS — `RF-CM-015` Registrar una comisión afftrack de rol

| Campo | Valor |
|---|---|
| Requerimiento | `RF-CM-015` |
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
| `T-01` | `V54__cm_comision_afftrack.sql`: las cuatro tablas, el cambio de `commissions` y los nueve permisos (`plan.md` §2) | — | Migración desde cero; los seis recuentos del catálogo a 162 | Pendiente |
| `T-02` | `commission_kind = POR_VENTA` en la inserción de `RF-CM-013`, y la columna en la entidad de `commissions` | `T-01` | La suite de devengo en verde | Pendiente |
| `T-03` | El ayudante de limpieza de las suites gana `afftrack_ftds` y `afftrack_settlements` antes de `movements` | `T-01` | Suite completa en verde, en orden alfabético | Pendiente |
| `T-04` | `ProductCatalog.ftdProductIds` en `PM` y su adaptador | — | Prueba del adaptador: `BECA → BECA` —también retirado— sí; otra pareja y bot no | Pendiente |
| `T-05` | `AfftrackRate`, repositorio y adaptador con la traducción del índice a `409` | `T-01` | | Pendiente |
| `T-06` | Sobrecarga de `ProductCurrencyScale` para un importe suelto | — | Unitaria | Pendiente |
| `T-07` | `RegisterAfftrackRateService`, `record`s con `@Schema(name)`, controlador y la ruta en `PERMISO_DE_CADA_OPERACION` | `T-04`, `T-05`, `T-06` | `EndpointPermissionsIT` | Pendiente |
| `T-08` | `RegisterAfftrackRateIT`: `CA-CM-209` a `CA-CM-216` | `T-07` | `CA-CM-211` con dos hilos | Pendiente |
| `T-09` | Contrato OpenAPI y `requirements.md` | `T-08` | El diff del `openapi.json` sin esquemas fundidos | Pendiente |

---

## 2. Orden de ejecución

`T-01` → `T-02`, `T-03` → `T-04`, `T-05`, `T-06` → `T-07` → `T-08` → `T-09`. **`T-02` y `T-03` van antes que nada más**: sin ellas la suite existente se pone en rojo con la migración.

---

## 3. Cobertura de los criterios de aceptación

| Criterio | Tarea |
|---|---|
| `CA-CM-209` a `CA-CM-216` | `T-07`, `T-08` |

---

## 4. Bloqueos

Ninguno.

---

## 5. Definición de terminado

- [ ] `./mvnw clean verify` en verde.
- [ ] Los ocho criterios de aceptación con prueba.
- [ ] Contrato y `requirements.md` actualizados.
- [ ] **`tasks.md` aprobadas por el responsable del proyecto.**
