# TASKS — `RF-MV-048` Cobrar por la pasarela local

| Campo | Valor |
|---|---|
| Requerimiento | `RF-MV-048` |
| Especificación | [`spec.md`](spec.md) v0.1.0 |
| Plan | [`plan.md`](plan.md) v0.1.0 |
| `plan.md` aprobado el | 05-10-2026 |
| Estado | **En revisión** — todas las tareas `Hecha` el 05-10-2026 |
| Issue | Pendiente de crear |
| Rama | `develop` (commits directos desde el 05-10-2026) |

!!! info "Qué va en este documento"

    **Qué hay que hacer, en qué orden y cómo se comprueba.** Nada de por qué — eso está en `spec.md` y `plan.md`.

---

## 1. Tareas

**Estados:** `Pendiente` · `En curso` · `Hecha` · `Bloqueada`.

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-01` | `V69__mv_pasarela_local.sql` (`plan.md` §2) con sus guardas | — | Aplicada sobre la base de pruebas | **Hecha** — 05-10-2026 |
| `T-02` | Recuentos del catálogo 181 → 182 | `T-01` | Las suites de siembra en verde | **Hecha** — 05-10-2026 |
| `T-03` | `LocalPaymentGateway`, `PayRetailersGateway`, `PayRetailersSettings`, apagable | — | Sin credenciales no se crea y se avisa al arrancar | **Hecha** — 05-10-2026 |
| `T-04` | `LocalPayment` con la conversión y el redondeo hacia arriba; `PaymentRepository.setLocalCharge` | `T-01`, `T-03` | El redondeo, por integración: `CA-MV-601` (41.505 y 83.010) | **Hecha** — 05-10-2026 |
| `T-05` | `CardPayment` exige `STRIPE` | — | `CA-MV-609` | **Hecha** — 05-10-2026 |
| `T-06` | Las cuatro entradas llaman a `LocalPayment`; `localCharge` en las tres respuestas | `T-04` | `CA-MV-600` a `CA-MV-607`, `CA-MV-610` | **Hecha** — 05-10-2026 |
| `T-07` | Confirmar y rechazar a mano no alcanzan un cobro local abierto | `T-01` | `CA-MV-608` | **Hecha** — 05-10-2026 |
| `T-08` | `LocalChargeIT`; prosa de las `@Operation`; contrato regenerado | `T-06`, `T-07` | Cada criterio afirmado en el cuerpo de su prueba | **Hecha** — 05-10-2026 |
| `T-09` | Una fila en el control de cambios de las tripletas enmendadas (`plan.md` §8) | `T-08` | | **Hecha** — 05-10-2026 |

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

## 3.1 Desviaciones respecto del plan

**Una sola suite para las cuatro tripletas**: `LocalChargeIT` lleva `CA-MV-600` a `CA-MV-629`, porque las cuatro comparten la siembra —la conversión de prueba y el cobro abierto— y repetirla en tres clases solo añadía contextos. **PayRetailers abre un *paywall***, no una transacción: `provider_reference` guarda el `uid` del paywall, y la transacción —que nace cuando el cliente elige método— se busca por **nuestro `trackingId`**, el identificador del pago (`GET /transactions/byTracking/{trackingId}`); `404` es «todavía no eligió». **Sin cancelación al revertir**, como dice el plan §7. **`CA-MV-607`** se prueba con una venta sembrada por SQL con `PSE` y sin cobro, que es lo que deja el registro de un funcionario; la entrada de oficina no abre cobro porque no llama a `LocalPayment`. **Lo que descubrió la construcción**: `CardPayment` trataba como tarjeta todo método con pasarela —con `PSE` en `PAYRETAILERS` lo habría cobrado Stripe— y ahora exige `STRIPE`; volver a pagar pedía retomar el cobro abierto con cualquier método con pasarela, y ahora solo con la **misma**; y el reintento de avisos de Stripe habría leído los de PayRetailers, y ahora filtra por pasarela.

## 4. Bloqueos

**Las credenciales del sandbox** no bloquean la construcción —el puerto se dobla en las pruebas— pero sí **la verificación contra PayRetailers**, que queda para cuando lleguen.

---

## 5. Definición de terminado

- [x] Las suites afectadas en verde.
- [x] Los once criterios de aceptación con prueba, **cada uno afirmado en el cuerpo de la prueba**.
- [x] Contrato OpenAPI regenerado, **con la prosa releída**.
- [x] `requirements.md` al día.
- [ ] **Probado contra el sandbox de PayRetailers.**
- [ ] **`tasks.md` aprobadas por el responsable del proyecto.**
