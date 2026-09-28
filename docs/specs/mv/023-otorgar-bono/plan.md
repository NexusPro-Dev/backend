# PLAN — `RF-MV-023` Otorgar un bono

| Campo | Valor |
|---|---|
| Requerimiento | `RF-MV-023` |
| Especificación | [`spec.md`](spec.md) v0.1.0 |
| `spec.md` aprobada el | 26-09-2026 |
| Versión | 0.1.0 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 26-09-2026 |

!!! info "Qué va en este documento"

    **Cómo se construye.** Esquema, componentes, contrato, autorización y pruebas.

    **Prueba de pertenencia:** si un cambio de negocio lo invalidaría, pertenece a `spec.md`.

---

## 1. Enfoque

**Un `INSERT` en `movements` que el esquema deja pasar o no, y un evento del `Ledger`.** El movimiento nace `CONFIRMADA`, con `confirmed_at`, `concept` y `idempotency_key`; la repetición la detecta `uq_movements_idempotency_key` y se traduce como en `RF-MV-018` · `plan.md` §7: relectura en transacción nueva, mismos datos → `200` con el bono, otros → `409`.

**El `Ledger` se reutiliza entero** (`RF-MV-019`): `BONOS` de la empresa −X, `BILLETERA` de la persona +X, evento `ABONO`. `BONOS` no tiene `ck_accounts_saldo` que la frene —es de la empresa— y la billetera solo sube, de modo que **un bono no puede fallar por saldo**.

---

## 2. Cambios de esquema

**La siguiente migración libre al construir**:

| Elemento | Definición | Por qué |
|---|---|---|
| `movements.idempotency_key` | `varchar(80) NULL` | [`requirements/mv.md`](../../../requirements/mv.md) v0.46.0 §7.1 |
| `uq_movements_idempotency_key` | `UNIQUE (idempotency_key) WHERE idempotency_key IS NOT NULL` | `RN-MV-045`; la usa también `RF-MV-024` |
| Permiso | `movements:grant-bonus`, a `SUPERADMIN` y `ADMIN` explícito | Regalar dinero es tarea de administración |

---

## 3. Componentes afectados

| Capa | Componente | Cambio | Nota |
|---|---|---|---|
| `domain/models` | `Movement` | Gana `bono(persona, moneda, importe, motivo, clave, instante)` | Sin líneas, `REGISTRADO`, `CONFIRMADA` |
| `domain/models` | `BonusConcept` | Nuevo | La forma de `VoidReason` |
| `domain/service` | `GrantBonusService` | Nuevo | Validación, persona por la interfaz de `SP`, `INSERT`, traducción, `Ledger`, auditoría |
| `application` | `GrantBonusRequest`, `BonusResponse` | Nuevos | |
| `interfaces` | `MovementController` | `POST /bonuses` | |

---

## 4. Contrato de API

| Verbo | Ruta | Permiso |
|---|---|---|
| `POST` | `/api/v1/movements/bonuses` | `movements:grant-bonus` |

**Cabecera `Idempotency-Key`**, obligatoria, como en `RF-MV-018`. **Cuerpo**: `{ "userId", "currencyId", "amount", "concept" }`.

| Código | Cuándo |
|---|---|
| `201` | Otorgado |
| `200` | La misma petición repetida |
| `400` | Datos ausentes o malformados, **todos juntos**; clave ausente o malformada |
| `401` / `403` | Sin token / sin `movements:grant-bonus` |
| `409` | La clave es de otro bono |
| `422` | La persona o la moneda no existen |

---

## 5. Autorización

`@PreAuthorize("hasAuthority('movements:grant-bonus')")`.

---

## 6. Auditoría

Un `ChangeEvent` sobre `movements`, `INSERT`, con la persona, el importe, la moneda, el motivo y el evento `ABONO`. **El actor queda en el propio asiento de auditoría**: es quien otorgó, y no es el sujeto (`RN-MV-026`).

---

## 7. Transaccionalidad

`@Transactional`: movimiento y dos asientos. La relectura tras una clave repetida, en transacción nueva.

---

## 8. Impacto sobre otros módulos

**`SP`**: se lee la persona —existe, no está eliminada— por la interfaz publicada que ya usa `MV`. Ninguna escritura.

---

## 9. Alternativas consideradas

| Alternativa | Por qué no |
|---|---|
| La clave en un pago ficticio | Un bono no tiene pago (`spec.md` §2); inventarlo mentiría en `payments` |
| Sin clave | El doble clic regala dos veces, y no hay pago que lo frene |
| Negar el bono a cuentas bloqueadas | El saldo es de la persona; lo que se le impide es retirar mientras no opere |

---

## 10. Riesgos

| Riesgo | Mitigación |
|---|---|
| Doble otorgamiento | `uq_movements_idempotency_key`; `CA-MV-262` con dos hilos |

---

## 11. Estrategia de prueba

Integración, `GrantBonusIT`: `CA-MV-260` a `CA-MV-268`; `CA-MV-262` con dos hilos y la misma clave; `CA-MV-267` pasa por las rutas de `RF-MV-022` y `RF-MV-019`.
