# TASKS — `RF-MV-048` Cobrar por la pasarela local

| Campo | Valor |
|---|---|
| Requerimiento | `RF-MV-048` |
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
| `T-01` | `V69__mv_pasarela_local.sql` (`plan.md` §2) con sus guardas | — | Aplicada sobre la base de pruebas | Pendiente |
| `T-02` | Recuentos del catálogo 181 → 182 | `T-01` | Las suites de siembra en verde | Pendiente |
| `T-03` | `LocalPaymentGateway`, `PayRetailersGateway`, `PayRetailersSettings`, apagable | — | Sin credenciales no se crea y se avisa al arrancar | Pendiente |
| `T-04` | `LocalPayment` con la conversión y el redondeo hacia arriba; `PaymentRepository.setLocalCharge` | `T-01`, `T-03` | Unitarias del redondeo | Pendiente |
| `T-05` | `CardPayment` exige `STRIPE` | — | `CA-MV-609` | Pendiente |
| `T-06` | Las cuatro entradas llaman a `LocalPayment`; `localCharge` en las tres respuestas | `T-04` | `CA-MV-600` a `CA-MV-607`, `CA-MV-610` | Pendiente |
| `T-07` | Confirmar y rechazar a mano no alcanzan un cobro local abierto | `T-01` | `CA-MV-608` | Pendiente |
| `T-08` | `LocalChargeIT`; prosa de las `@Operation`; contrato regenerado | `T-06`, `T-07` | Cada criterio afirmado en el cuerpo de su prueba | Pendiente |
| `T-09` | Una fila en el control de cambios de las tripletas enmendadas (`plan.md` §8) | `T-08` | | Pendiente |

---

## 2. Orden de ejecución

`T-01` → `T-02` → `T-03` → `T-04` → `T-05` → `T-06` → `T-07` → `T-08` → `T-09`. **`RF-MV-049`, `RF-MV-050` y `RF-MV-051` dependen de `T-01`, `T-03` y `T-04`.**

---

## 3. Cobertura de los criterios de aceptación

| Criterio | Tarea |
|---|---|
| `CA-MV-600` a `CA-MV-607`, `CA-MV-610` | `T-04`, `T-06`, `T-08` |
| `CA-MV-608` | `T-07`, `T-08` |
| `CA-MV-609` | `T-05`, `T-08` |

---

## 4. Bloqueos

**Las credenciales del sandbox** no bloquean la construcción —el puerto se dobla en las pruebas— pero sí **la verificación contra PayRetailers**, que queda para cuando lleguen.

---

## 5. Definición de terminado

- [ ] Las suites afectadas en verde.
- [ ] Los once criterios de aceptación con prueba, **cada uno afirmado en el cuerpo de la prueba**.
- [ ] Contrato OpenAPI regenerado, **con la prosa releída**.
- [ ] `requirements.md` al día.
- [ ] **Probado contra el sandbox de PayRetailers.**
- [ ] **`tasks.md` aprobadas por el responsable del proyecto.**
