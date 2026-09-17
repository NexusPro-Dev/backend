# TASKS — `RF-MV-014` Consultar los productos comprados propios

| Campo | Valor |
|---|---|
| Requerimiento | `RF-MV-014` |
| Especificación | [`spec.md`](spec.md) v0.1.0 |
| Plan | [`plan.md`](plan.md) v0.1.0 |
| `plan.md` aprobado el | 17-09-2026 |
| Estado | **En revisión** |
| Issue | Pendiente de crear |
| Rama | `feature/venta-de-productos` |

!!! info "Qué va en este documento"

    **Qué hay que hacer, en qué orden y cómo se comprueba.** Nada de por qué — eso está en `spec.md` y `plan.md`.

---

## 1. Tareas

**Estados:** `Pendiente` · `En curso` · `Hecha` · `Bloqueada`.

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-01` | `PurchasedProductState` con los seis estados; `MyProductsRequest`; `MyProductResponse` con los nulables por `types` | `RF-MV-003` · `T-01` | El contrato enumera los seis y declara los tres nulables | Pendiente |
| `T-02` | `MovementRepository`: `findMyProducts` y `countMyProducts`, un `CASE` para el estado y el «hasta» en la misma expresión, con el reloj como parámetro | `T-01` | La página y el total filtran igual; el borde del vencimiento es «igual al instante ya venció» | Pendiente |
| `T-03` | `ListMyProductsService`: actor por credencial, estado validado contra el enumerado, total exacto | `T-02` | Sin identificador de persona en ningún sitio | Pendiente |
| `T-04` | `MovementController`: `GET /mine/products` **antes** de `/mine/{id}`, documentado; lista blanca de `EndpointPermissionsIT` | `T-03` | `products` no cae en `{id}` | Pendiente |
| `T-05` | `MyProductsIT`: `CA-MV-099` a `CA-MV-109` | `T-04` | Los dos lados del vencimiento con el reloj fijado | Pendiente |
| `T-06` | Contrato OpenAPI regenerado y prosa releída; `requirements.md` | `T-05` | | Pendiente |

---

## 2. Cobertura de los criterios de aceptación

| Criterio | Tarea |
|---|---|
| `CA-MV-099`, `CA-MV-109` | `T-02`, `T-04`, `T-05` |
| `CA-MV-100` a `CA-MV-105`, `CA-MV-108` | `T-02`, `T-05` |
| `CA-MV-106`, `CA-MV-107` | `T-03`, `T-05` |

---

## 3. Desviaciones respecto del plan

Ninguna todavía.

---

## 4. Definición de terminado

- [ ] `./mvnw clean verify` en verde.
- [ ] Los once criterios de aceptación con prueba.
- [ ] Contrato OpenAPI regenerado, **con la prosa releída**.
- [ ] `requirements/mv.md` y `requirements.md` actualizados.
- [ ] **`tasks.md` aprobadas por el responsable del proyecto.**
