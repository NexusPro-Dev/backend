# TASKS — `RF-MV-031` Consultar mis compras de puntos

| Campo | Valor |
|---|---|
| Requerimiento | `RF-MV-031` |
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
| `T-01` | `JpaMovementRepository.findOwnPointsPurchases` y su recuento | `RF-MV-027` `T-02` | Dos sentencias por página | Pendiente |
| `T-02` | `ListMyPointsPurchasesService` | `T-01` | Los errores de filtro, juntos | Pendiente |
| `T-03` | `PointsPurchaseController`: `GET /movements/mine/points-purchases` | `T-02` | Documentado con los códigos de `plan.md` §4 | Pendiente |
| `T-04` | `ListMyPointsPurchasesIT`: `CA-MV-344` a `CA-MV-350` | `T-03`, `RF-MV-029` `T-02` | Recuento de sentencias | Pendiente |
| `T-05` | `EndpointPermissionsIT`; contrato con la prosa releída; `requirements.md` | `T-04` | | Pendiente |

---

## 2. Orden de ejecución

**Después de `RF-MV-027`** —y de `RF-MV-029` para la compra rechazada—: `T-01` → `T-02` → `T-03` → `T-04` → `T-05`.

---

## 3. Cobertura de los criterios de aceptación

| Criterio | Tarea |
|---|---|
| `CA-MV-344` a `CA-MV-346`, `CA-MV-348`, `CA-MV-350` | `T-01`, `T-04` |
| `CA-MV-347` | `T-02`, `T-04` |
| `CA-MV-349` | `T-03`, `T-04` |

---

## 4. Bloqueos

**`RF-MV-027`**, que crea las compras.

---

## 5. Definición de terminado

- [ ] `./mvnw clean verify` en verde.
- [ ] Los siete criterios de aceptación con prueba, **cada uno afirmado en el cuerpo de la prueba**.
- [ ] Contrato OpenAPI regenerado, **con la prosa releída**.
- [ ] `requirements.md` actualizado.
- [ ] **`tasks.md` aprobadas por el responsable del proyecto.**
