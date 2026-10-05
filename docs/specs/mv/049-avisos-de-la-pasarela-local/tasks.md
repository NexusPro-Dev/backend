# TASKS — `RF-MV-049` Recibir los avisos de la pasarela local

| Campo | Valor |
|---|---|
| Requerimiento | `RF-MV-049` |
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
| `T-01` | `LocalChargeReconciler.conciliar` con los cuatro desenlaces y la incidencia `COBRO_TARDIO` | `RF-MV-048` `T-04` | `CA-MV-612`, `CA-MV-613`, `CA-MV-614`, `CA-MV-616` | Pendiente |
| `T-02` | La ruta pública, el guardado del aviso y `porAviso` | `T-01` | `CA-MV-615`, `CA-MV-618`, `CA-MV-619` | Pendiente |
| `T-03` | Anular y volver a pagar rechazan el pago con cobro local abierto | `T-01` | `CA-MV-617` | Pendiente |
| `T-04` | `LocalChargeNotificationIT`; `EndpointPermissionsIT`; contrato | `T-02`, `T-03` | Cada criterio afirmado en el cuerpo de su prueba | Pendiente |

---

## 2. Orden de ejecución

`T-01` → `T-02` → `T-03` → `T-04`.

---

## 3. Cobertura de los criterios de aceptación

| Criterio | Tarea |
|---|---|
| `CA-MV-612` a `CA-MV-614`, `CA-MV-616` | `T-01`, `T-04` |
| `CA-MV-615`, `CA-MV-618`, `CA-MV-619` | `T-02`, `T-04` |
| `CA-MV-617` | `T-03`, `T-04` |

---

## 4. Bloqueos

La forma exacta del aviso se confirma con el sandbox.

---

## 5. Definición de terminado

- [ ] Las suites afectadas en verde.
- [ ] Los ocho criterios de aceptación con prueba.
- [ ] Contrato regenerado, con la prosa releída.
- [ ] **Probado contra el sandbox de PayRetailers.**
- [ ] **`tasks.md` aprobadas por el responsable del proyecto.**
