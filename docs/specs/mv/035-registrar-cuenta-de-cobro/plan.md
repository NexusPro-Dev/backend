# PLAN — `RF-MV-035` Registrar una cuenta de cobro propia

| Campo | Valor |
|---|---|
| Requerimiento | `RF-MV-035` |
| Especificación | [`spec.md`](spec.md) v0.1.0 |
| `spec.md` aprobada el | 01-10-2026 |
| Versión | 0.1.0 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 01-10-2026 |

!!! info "Qué va en este documento"

    **Cómo se construye.** Esquema, componentes, contrato, autorización y pruebas.

    **Prueba de pertenencia:** si un cambio de negocio lo invalidaría, pertenece a `spec.md`.

---

## 1. Enfoque

**Todo lo que escribe sobre las cuentas de una persona se serializa por persona**, con un bloqueo consultivo de transacción, `pg_advisory_xact_lock(ns, hashtext(user_id))`, en un espacio propio que la construcción numera sin chocar con los de `CM` (`4309`, `4313`). Registrar, editar y dar de baja (`RF-MV-035`, `RF-MV-037`, `RF-MV-038`) lo toman **antes de leer** las cuentas de esa persona. Es lo que hace correcto «¿es la primera?» y «desmarca la anterior» con dos peticiones a la vez (`CA-MV-387`). **No se bloquea la fila de `users`**, que es de `SP`.

**Los dos índices parciales son la segunda defensa**: `uq_payout_accounts_principal` y `uq_payout_accounts_numero` rechazan lo que el bloqueo no hubiera evitado, y se traducen por su nombre a `500` y a `EX-006` respectivamente. El primero no debería dispararse nunca.

**El titular se lee de `SP`** por una interfaz publicada nueva, `PayoutHolderLookup`. Es la misma que usará el retiro (`RF-MV-019`) para copiarlo.

---

## 2. Cambios de esquema

Ninguno: las tablas las trae `RF-MV-032`.

---

## 3. Componentes afectados

| Capa | Componente | Cambio | Nota |
|---|---|---|---|
| `SP` · `users/application` | `PayoutHolderLookup` | **Nueva interfaz publicada** | `holderOf(id)` → `HolderView(id, firstName, lastName, countryId, documentType, documentNumber)`, con `documentType` como la abreviatura (`CC`). La implementa `users` con una lectura por clave unida a `document_types`. Vacío si la persona no existe o está eliminada |
| `domain/models` | `PayoutAccountType` | Nuevo | `AHORROS`, `CORRIENTE` |
| `domain/models` | `PayoutAccountNumber` | Nuevo | Quita espacios y guiones y valida dígitos; `VAL-002`. La longitud por tipo la valida `PayoutAccount` |
| `domain/models` | `PayoutAccount` | Nuevo | `PayoutAccount.registrar(titular, entidad, tipo, numero, principal)`: `VAL-004`, país, entidad activa |
| `domain/repository` | `PayoutAccountRepository`, `JpaPayoutAccountRepository` | Nuevos | `lockOwner(userId)`, `liveOf(userId)`, `insert`, `unmarkPrincipal(userId)`, y lo que añadan `RF-MV-036` a `RF-MV-039` |
| `domain/service` | `PayoutAccountService` | Nuevo | `register`: validación, titular, entidad, bloqueo, principal, `INSERT`, auditoría |
| `application` | `PayoutAccountRequests`, `PayoutAccountResponse` | Nuevos | La respuesta lleva `institution`, `accountType`, `number`, `principal`, `holder` (`name`, `documentType`, `documentNumber`), `createdAt` |
| `interfaces` | `PayoutAccountController` | Nuevo | `POST /movements/mine/payout-accounts` |

**El titular de la respuesta se lee en el momento**, no se guarda (`requirements/mv.md` §7.12): si administración corrige el documento, la cuenta lo muestra corregido.

---

## 4. Contrato de API

| Verbo | Ruta | Permiso |
|---|---|---|
| `POST` | `/api/v1/movements/mine/payout-accounts` | `movements:create-own-payout-account` |

**Bajo `/mine`** porque es sobre lo propio, como el retiro. **Cuerpo**: `{ "institutionId", "accountType"?, "number", "principal"? }`. **Respuesta**: `201` con `PayoutAccountResponse` y `Location` a la lista propia.

| Código | Cuándo |
|---|---|
| `201` | Registrada |
| `400` | Datos ausentes o malformados, todos juntos (`EX-001`), o que no corresponden al tipo de la entidad (`EX-004`) |
| `401` / `403` | Sin token / sin `movements:create-own-payout-account` |
| `409` | Entidad inactiva o de otro país (`EX-003`), persona sin documento (`EX-005`) o cuenta repetida (`EX-006`) |
| `422` | La entidad no existe (`EX-002`) |

---

## 5. Autorización

`@PreAuthorize("hasAuthority('movements:create-own-payout-account')")`; el titular es siempre el actor, de `AuthenticatedActor`.

---

## 6. Auditoría

Un `ChangeEvent` sobre `payout_accounts`, `INSERT`, con la entidad, el tipo, si es principal y **el número enmascarado** —los cuatro últimos dígitos—. Si desmarcó otra, un segundo `UPDATE` sobre esa. **El número completo no va a los registros de aplicación** (`security.md` §7.3): es un dato financiero, y la tabla ya lo guarda.

---

## 7. Transaccionalidad

`@Transactional`: el bloqueo de la persona, las lecturas, el desmarcado y el `INSERT`. El bloqueo se suelta al terminar la transacción.

---

## 8. Impacto sobre otros módulos

**`SP`**: **gana `PayoutHolderLookup`**, de solo lectura. `architecture.md` §15.2 recoge la fila.

**El frontend**: el formulario de cuenta, con el selector de `RF-MV-033` filtrado por el país del usuario. **El contrato OpenAPI se regenera y la prosa de la `@Operation` se relee**.

---

## 9. Alternativas consideradas

| Alternativa | Por qué no |
|---|---|
| Guardar nombre y documento en la cuenta | Dos copias del mismo dato vivo, que discreparían al corregir el documento; la copia que importa es la del retiro (`requirements/mv.md` §7.12) |
| Ampliar `ClientCatalog` con el documento | Esa interfaz es de la venta, y el documento no le hace falta a nadie que venda. Una interfaz por pregunta (`architecture.md` §15.2) |
| Bloquear la fila de `users` | Es de `SP`; el bloqueo consultivo serializa lo mismo sin tocar otro módulo |
| Confiar solo en los índices parciales | Dos «primeras» simultáneas: la segunda chocaría con `uq_payout_accounts_principal` y daría un error en vez de registrarse sin ser principal |
| Validar el número contra el banco | No hay integración, y el responsable decidió no verificar |

---

## 10. Riesgos

| Riesgo | Mitigación |
|---|---|
| Que el número completo acabe en un registro | Enmascarado en la auditoría; ningún `log` con la petición |
| Suites que dejen cuentas | Limpiar `payout_accounts` y `payout_institutions` al empezar y al terminar |

---

## 11. Estrategia de prueba

**Unitarias**: `PayoutAccountNumber`, `PayoutAccount.registrar` (`VAL-004` por tipo). **Integración**, `RegisterPayoutAccountIT`: `CA-MV-378` a `CA-MV-389`, con dos hilos en `CA-MV-387` y una persona sin documento creada en la prueba para `CA-MV-386`. **`PayoutHolderLookup`** con su propia prueba en `SP`.
