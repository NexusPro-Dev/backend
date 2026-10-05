# PLAN — `RF-MV-052` Ajustar los puntos de una persona

| Campo | Valor |
|---|---|
| Requerimiento | `RF-MV-052` |
| Especificación | [`spec.md`](spec.md) v0.1.0 |
| `spec.md` aprobada el | 05-10-2026 |
| Versión | 0.1.0 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 05-10-2026 |

!!! info "Qué va en este documento"

    **Cómo se construye.** Esquema, componentes, contrato, autorización y pruebas.

    **Prueba de pertenencia:** si un cambio de negocio lo invalidaría, pertenece a `spec.md`.

---

## 1. Enfoque

**El bono (`RF-MV-023` · `plan.md` §1), con puntos en vez de dinero y en los dos sentidos.** Un `INSERT` en `movements` con `idempotency_key` —la repetición la detecta `uq_movements_idempotency_key` y se resuelve igual que en el bono: mismos datos → `200`, otros → `409`— y un evento del `Ledger`, ahora `AJUSTE`:

| Signo | Patas |
|---|---|
| `+N` | `PUNTOS_EMITIDOS` de la empresa −N · `PUNTOS` de la persona +N |
| `−N` | `PUNTOS` de la persona −N · `PUNTOS_EMITIDOS` de la empresa +N |

**La resta no se comprueba antes: la frena el `Ledger`.** `Ledger.apply` mueve cada saldo con la fila bloqueada y devuelve vacío si una cuenta de persona quedaría en negativo (`ck_accounts_saldo`); el servicio lanza entonces `EX-006`, lo que revierte el movimiento ya insertado. Comprobar el saldo antes sería una carrera: dos restas simultáneas lo verían alcanzar las dos.

**La cabecera no lleva dinero**: los tres importes en cero —`ck_movements_amounts` y `ck_movements_payable` lo admiten—, y los puntos con su signo en `points_amount`, sin `points_rate_id`.

---

## 2. Cambios de esquema

**`V72`**:

| Elemento | Definición | Por qué |
|---|---|---|
| `movements.external_reference` | `varchar(120) NULL` | La referencia del comprobante (`RN-MV-076`) |
| `ck_movements_external_reference` | `external_reference IS NULL OR btrim(external_reference) <> ''` | Una referencia en blanco no soporta nada |
| `ck_movements_points` | Se rehace: `(points_rate_id IS NULL OR (points_amount IS NOT NULL AND points_amount > 0)) AND (points_amount IS NULL OR points_amount <> 0)` | El ajuste lleva puntos con signo y sin tasa. **El `IS NOT NULL` es necesario**: un `CHECK` que evalúa a nulo pasa |
| `ck_movement_entries_event` | Se amplía con `AJUSTE` | El evento del ajuste |
| Tipo `AJUSTE_PUNTOS` | Prefijo `AJP`, con su estado del tipo `REGISTRADO` | Como los tipos de `V49` y `V58` |
| Permiso | `movements:adjust-points`, a `SUPERADMIN` y `ADMIN` explícito | Como `movements:grant-bonus`. Catálogo 182 → **183** |

Identificadores literales con la marca de `V58` —`01a0ef9c6800`—, siguiendo las series: tipo `000016`, estado del tipo `000037`, permiso `000062`.

---

## 3. Componentes afectados

| Capa | Componente | Cambio | Nota |
|---|---|---|---|
| `domain/models` | `EntryEvent` | Gana `AJUSTE` | |
| `domain/models` | `Movement` | Gana `ajusteDePuntos(...)` y `externalReference` | Confirmado, importes en cero, puntos con signo y sin tasa |
| `domain/repository` | `MovementRepository` | `saveWithoutLines` escribe `external_reference`; nace `findPointsAdjustment(id)` | La relectura con puntos y referencia |
| `domain/service` | `PointsAdjustmentService` | Nuevo | Validación, persona por `ClientCatalog`, clave, `INSERT`, `Ledger`, auditoría |
| `application` | `PointsRequests.Adjustment`, `PointsAdjustmentResponse` | Nuevos | |
| `interfaces` | `PointsController` | `POST /points-adjustments` | Con la tasa y la compra: es el submódulo Puntos |

**Un servicio propio y no un método más de `CreditService`**: aquel abona dinero en la billetera y solo suma; este mueve puntos en los dos sentidos y puede fallar por saldo. Lo que comparten —la moneda, la persona, la clave— ya está en `LedgerMovements` y en `IdempotencyKey`.

---

## 4. Contrato de API

| Verbo | Ruta | Permiso |
|---|---|---|
| `POST` | `/api/v1/movements/points-adjustments` | `movements:adjust-points` |

**Cabecera `Idempotency-Key`**, obligatoria. **Cuerpo**: `{ "userId", "currencyId", "points", "concept", "reference" }`; `points` con signo.

**Respuesta**: `{ id, code, status, currency, points, concept, reference, occurredAt, confirmedAt, pointsBalance }`, con `pointsBalance` el saldo de puntos en que quedó la persona en esa moneda —**en la repetición, el de ahora**, que puede haber cambiado después—.

| Código | Cuándo |
|---|---|
| `201` | Ajustado |
| `200` | La misma petición repetida |
| `400` | Datos ausentes o malformados; clave ausente o malformada |
| `401` / `403` | Sin token / sin `movements:adjust-points` |
| `409` | La clave es de otra petición (`EX-005`) |
| `422` | La persona o la moneda no existen (`EX-002`); la resta no alcanza (`EX-006`, con los puntos disponibles en el mensaje) |

---

## 5. Autorización

`@PreAuthorize("hasAuthority('movements:adjust-points')")`. Entra en `PERMISO_DE_CADA_OPERACION` de `EndpointPermissionsIT`.

---

## 6. Auditoría

Un `ChangeEvent` sobre `movements`, `CREATE`, con la instantánea del movimiento —puntos, motivo, referencia— y el evento `AJUSTE`. El actor queda en el asiento de auditoría (`RN-MV-026`).

---

## 7. Transaccionalidad

`@Transactional`: movimiento y dos asientos, o nada. La resta que no alcanza lanza dentro de la transacción y revierte el `INSERT`.

---

## 8. Impacto sobre otros módulos

**`SP`**: se lee la persona por `ClientCatalog`, como el bono. Ninguna escritura. **`CM`**: ninguno; el ajuste no comisiona.

---

## 9. Alternativas consideradas

| Alternativa | Por qué no |
|---|---|
| Registrar una `COMPRA_PUNTOS` confirmada a nombre de la persona | Decisión del responsable (spec §2.1): se ajustan puntos, no dinero; y una compra no resta |
| Reutilizar `ABONO` para sumar y `PAGO` para restar | Cada evento dice otra cosa (`requirements/mv.md` §4.11) |
| Los puntos en `payable_amount` | Es dinero; una cifra en puntos ahí mentiría en todo listado que sume importes |
| Comprobar el saldo antes de la resta | Una carrera; lo resuelve el bloqueo del `Ledger` (§1) |

---

## 10. Riesgos

| Riesgo | Mitigación |
|---|---|
| Doble ajuste por doble clic | `uq_movements_idempotency_key`; `CA-MV-640` |
| Saldo en negativo por dos restas a la vez | El bloqueo de fila del `Ledger` y `ck_accounts_saldo` |

---

## 11. Estrategia de prueba

Integración, `PointsAdjustmentIT`: `CA-MV-636` a `CA-MV-646`. `CA-MV-645` pasa por la ruta del historial de `RF-MV-022`.
