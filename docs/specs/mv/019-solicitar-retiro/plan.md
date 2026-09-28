# PLAN — `RF-MV-019` Solicitar un retiro

| Campo | Valor |
|---|---|
| Requerimiento | `RF-MV-019` |
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

**Este requerimiento trae los saldos al sistema**, y por eso carga con `accounts`, `movement_entries` y el componente que escribe asientos —el **libro**—, que después reutilizan `RF-MV-020`, `RF-MV-021`, `RF-MV-023` y `RF-MV-024`. Lo propio del retiro es pequeño: un movimiento sin líneas y un evento de dos asientos.

**El saldo no se lee para decidir: se escribe y el esquema decide.** Apartar el importe es `UPDATE accounts SET balance = balance + :delta WHERE id = :id RETURNING balance`, una vez por cuenta, **en orden de identificador**. Si la billetera quedaría en negativo, `ck_accounts_saldo` rechaza la sentencia y el servicio lo traduce a `EX-003`. **No hay un `SELECT` previo del saldo**, que es lo que permitiría a dos peticiones simultáneas ver las dos que alcanza (`CA-MV-228`). El `UPDATE` bloquea la fila, de modo que la segunda petición espera a la primera y ve su resultado.

**El cuadre lo comprueba el esquema al cerrar la transacción**, con un disparador de restricción diferido: los asientos de cada `(movement_id, event)` tienen que sumar cero y ser de cuentas de la misma moneda. Un error en el libro no deja un asiento cojo: aborta el `COMMIT`.

---

## 2. Cambios de esquema

**La siguiente migración libre al construir** —`V49` si `RF-MV-018` tomó la `V48`—, `V49__mv_saldos_y_retiros.sql`:

| Elemento | Definición | Por qué |
|---|---|---|
| `accounts` | Las columnas de [`requirements/mv.md` §7.8](../../../requirements/mv.md) | `RN-MV-041` |
| `uq_accounts_titular` | `UNIQUE NULLS NOT DISTINCT (user_id, kind, currency_id)` | Sin `NULLS NOT DISTINCT`, la empresa podría tener dos `BONOS` en la misma moneda. PostgreSQL 17 lo admite |
| `uq_accounts_number`, `ck_accounts_kind`, `ck_accounts_saldo` | Los de `requirements/mv.md` §7.6 | |
| `fk_accounts_user` | `user_id` → `users(id)` **`ON DELETE CASCADE`** | En producción nadie borra personas (`RF-SP-029` es lógico); **las suites sí**, y una FK sin `ON DELETE` rompe suites lejos de aquí |
| `fk_accounts_currency` | `currency_id` → `currencies(id)` `RESTRICT` | Nadie borra monedas |
| `movement_entries` | Las columnas de `requirements/mv.md` §7.9 | `RN-MV-042` |
| `fk_movement_entries_movement` | → `movements(id)` **`ON DELETE CASCADE`** | Como `payments` |
| `fk_movement_entries_account` | → `accounts(id)` **`ON DELETE CASCADE`** | Sigue a la cuenta en las suites |
| `fk_movement_entries_payment` | → `payments(id)` `ON DELETE SET NULL` | El pago es un dato del asiento, no su dueño |
| `ck_movement_entries_amount`, `ck_movement_entries_event` | `amount <> 0`; `event IN ('SOLICITUD','APROBACION','RECHAZO','ABONO')` | |
| `ix_movement_entries_account` | `(account_id, created_at DESC, id DESC)` | El historial de una cuenta (`RF-MV-022`) |
| `ix_movement_entries_movement` | `(movement_id, event)` | El cuadre |
| `tg_movement_entries_cuadre` | `CREATE CONSTRAINT TRIGGER … AFTER INSERT ON movement_entries DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION f_movement_entries_cuadre()` | `RN-MV-042`. La función suma los asientos de `(NEW.movement_id, NEW.event)` y cuenta sus monedas; si la suma no es cero o hay más de una moneda, `RAISE EXCEPTION` con `ERRCODE = 'check_violation'` |
| `movement_types` | Fila `RETIRO`, prefijo `RET`; y `PAGO_COMISION` (`PCM`) y `BONO` (`BON`), que se siembran aquí para no volver a tocar el catálogo en `RF-MV-023` y `RF-MV-024` | `requirements/mv.md` §7.2 |
| `movement_type_statuses` | `REGISTRADO` para cada uno de los tres | §7.2.2; `type_status_id` es obligatorio |
| `movements` | `rejected_at`, `rejection_reason`, `concept`, y `ck_movements_rejected` | `requirements/mv.md` §7.1; los usan `RF-MV-021` y `RF-MV-023` |
| Permiso | `movements:request-withdrawal`, a `SUPERADMIN`, `ADMIN` explícito y a todo rol por su tipo, como `movements:list-own` en `V31` | Lo pide cualquiera que tenga saldo |

**La inmutabilidad de los asientos no se declara con un disparador que prohíba `UPDATE` y `DELETE`**, y es deliberado: las suites limpian con `DELETE`, y un disparador así las rompería todas. La sostienen el código —el libro solo inserta— y `RN-MV-001`, como en `movements`.

**Las cuentas de la empresa no se siembran**: se crean la primera vez que hacen falta, como las de las personas, porque dependen de la moneda y las monedas se crean en caliente (`RF-SP-047`).

---

## 3. Componentes afectados

| Capa | Componente | Cambio | Nota |
|---|---|---|---|
| `domain/models` | `AccountKind`, `EntryEvent`, `Money` (importe + moneda) | Nuevos | `AccountKind` sabe si es de persona o de empresa, como `ck_accounts_kind` |
| `domain/models` | `AccountNumber` | Nuevo | `CTA-` + diez caracteres del alfabeto de Crockford, como `MovementCode`; se reintenta ante colisión de `uq_accounts_number` |
| `domain/repository` | `LedgerRepository`, `JpaLedgerRepository` | Nuevos | `cuentaDe(titular, tipo, moneda)` —`INSERT … ON CONFLICT DO NOTHING` y relectura—, `mover(cuenta, delta)` —el `UPDATE … RETURNING`—, `asentar(...)`, `saldos(persona, moneda)` |
| `domain/service` | `Ledger` | Nuevo | **El único que escribe asientos**: recibe un evento con sus pares `(cuenta, delta)`, ordena las cuentas por identificador, las mueve y escribe los asientos con `balance_after`. Traduce `ck_accounts_saldo` a «no alcanza» |
| `domain/models` | `Movement` | Gana `retiro(...)`: sin líneas, sin paquete, `REGISTRADO` | `RN-MV-046` |
| `domain/service` | `RequestWithdrawalService` | Nuevo | Validación, estado de la cuenta del actor, movimiento, `Ledger`, auditoría |
| `domain/service` | `ConfirmSaleService`, `VoidSaleService`, `RejectPaymentService`, `RetryPaymentService` | **Fijan `VENTA`** en su sentencia | `CA-MV-233` |
| `application` | `WithdrawalRequest`, `WithdrawalResponse`, `BalancesResponse` | Nuevos | La respuesta lleva el retiro y los tres saldos de la moneda |
| `interfaces` | `MovementController` | `POST /mine/withdrawals` | |

**Por qué un `Ledger` único.** Cinco requerimientos escriben asientos, y el orden de bloqueo, el `balance_after` y la traducción del saldo negativo tienen que ser idénticos en los cinco. Escritos cinco veces, el primero que se equivoque de orden produce el `40P01` que este proyecto ya pagó una vez con un `EXCLUDE`.

---

## 4. Contrato de API

| Verbo | Ruta | Permiso |
|---|---|---|
| `POST` | `/api/v1/movements/mine/withdrawals` | `movements:request-withdrawal` |

**Bajo `/mine`** porque es sobre lo propio. **Cuerpo**: `{ "currencyId": "…", "amount": 150.00 }`.

**Respuesta**: `201` con `WithdrawalResponse` —el movimiento (id, código, estado, importe, moneda, `occurredAt`) y `balances` (`wallet`, `held`, `points`) de esa moneda—. `Location` apunta al detalle del movimiento.

| Código | Cuándo |
|---|---|
| `201` | Retiro pedido |
| `400` | Moneda o importe ausentes o malformados, importe no positivo o con decimales de más (`EX-001`) |
| `401` / `403` | Sin token / sin `movements:request-withdrawal` |
| `409` | No alcanza, con el disponible en el mensaje (`EX-003`), o la cuenta no opera (`EX-004`) |
| `422` | La moneda no existe (`EX-002`) |

---

## 5. Autorización

`@PreAuthorize("hasAuthority('movements:request-withdrawal')")`; el sujeto es siempre el actor.

---

## 6. Auditoría

Un `ChangeEvent` sobre `movements`, `INSERT`, con el retiro; **los asientos no llevan un asiento de auditoría cada uno**: son el propio registro de lo que pasó, inmutable y con su instante, y auditarlos sería escribir dos veces lo mismo. El evento del movimiento cita el evento contable (`SOLICITUD`).

---

## 7. Transaccionalidad

`@Transactional`: el movimiento, los dos `UPDATE` de saldo y los dos asientos, en una transacción. El disparador de cuadre se evalúa al `COMMIT`. **Las cuentas se bloquean en orden de identificador**, siempre, por el `Ledger` (`RN-MV-042`).

---

## 8. Impacto sobre otros módulos

**`SP`**: se lee el estado de la cuenta del actor por la interfaz publicada que ya usa `RN-MV-008`. Ninguna escritura.

---

## 9. Alternativas consideradas

| Alternativa | Por qué no |
|---|---|
| Leer el saldo y comparar antes de escribir | Dos peticiones simultáneas pasarían las dos (`CA-MV-228`) |
| Restar los retiros pendientes al calcular el disponible | `requirements/mv.md` §4.3 |
| Clave de idempotencia en la petición | Una repetición retiene dos veces y no puede pagar de más (`spec.md` §2.1); la clave obligaría a una columna en `movements` para un daño que el saldo ya acota |
| Sembrar las cuentas de la empresa | Dependen de la moneda, y las monedas se crean en caliente |
| Prohibir `UPDATE`/`DELETE` de asientos con un disparador | Rompería la limpieza de todas las suites (§2) |

---

## 10. Riesgos

| Riesgo | Mitigación |
|---|---|
| Un interbloqueo entre dos eventos | Orden de bloqueo único en el `Ledger`; una prueba con dos hilos en orden contrario |
| Que una operación de venta alcance a un retiro | `VENTA` fijada en sus sentencias; `CA-MV-233` |
| Un asiento cojo | El disparador diferido; una prueba que lo provoca a mano |
| Suites que dejan saldos entre sí | Limpiar `movement_entries` y `accounts` **al empezar y al terminar** cada suite de saldos, con nombres de moneda únicos |

---

## 11. Estrategia de prueba

**Unitarias**: `AccountNumber`, `AccountKind`, `Money`. **Integración**: `RequestWithdrawalIT` —`CA-MV-224` a `CA-MV-235`, con dos hilos en `CA-MV-228` y un `UPDATE` directo contra la base en `CA-MV-234`—; `LedgerIT` —el cuadre diferido, el orden de bloqueo con dos hilos cruzados, `balance_after`—. **El saldo inicial se siembra en la prueba** con el `Ledger`: hasta que existan `RF-MV-023` y `RF-MV-024` no hay otra vía de llenar una billetera.
