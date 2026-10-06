# PLAN — `RF-MV-022` Consultar mis saldos

| Campo | Valor |
|---|---|
| Requerimiento | `RF-MV-022` |
| Especificación | [`spec.md`](spec.md) v0.2.0 |
| `spec.md` aprobada el | 26-09-2026 |
| Versión | 0.2.0 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 26-09-2026 |

!!! warning "Enmendado el 06-10-2026 — `account` admite VARIAS cuentas (R-62)"

    A petición del frontend (R-62) y por decisión del responsable del proyecto: el historial de Balance muestra **solo el dinero** —retiros, pagos de comisión, bonos— y no los puntos, que tienen su historial en «Mis puntos». Con una cuenta por petición eso obliga a pedir `BILLETERA` y `RETENIDO` por separado, y filtrar los puntos en el navegador rompería la paginación. **Cómo se construye**: `account` pasa a ser un parámetro **repetible** (`?account=BILLETERA&account=RETENIDO`), que Spring recibe como lista; el servicio la valida entera —cada valor contra los tres de persona, los inválidos juntos en **un** `VAL-002`— y la reduce a un conjunto sin repetidos; `EntryFilter.account` pasa de `String` a `Set<String>` y el predicado de `JpaLedgerRepository` de `a.kind = :cuenta` a **`a.kind IN (:cuentas)`**, con el conjunto vacío como «sin filtro». **Se descartó la lista separada por comas** (`account=BILLETERA,RETENIDO`): es la forma que ningún otro filtro del sistema usa, y obligaría a decidir qué hacer con una coma dentro de un valor. Sin cambio de esquema ni de permisos. En el contrato, `account` pasa de cadena a **arreglo de cadenas**; un cliente que mande un solo valor no nota nada.

!!! info "Qué va en este documento"

    **Cómo se construye.** Esquema, componentes, contrato, autorización y pruebas.

    **Prueba de pertenencia:** si un cambio de negocio lo invalidaría, pertenece a `spec.md`.

---

## 1. Enfoque

**Dos lecturas sobre lo que `RF-MV-019` escribe**, sin cambio de esquema salvo los permisos. Los saldos son una consulta sobre `accounts` —`WHERE user_id = :actor`—, agrupada en el servicio por moneda. El historial es `movement_entries` unida a `accounts` —para el alcance y el tipo de cuenta— y a `movements` —para el código, el tipo y el estado—, con `ix_movement_entries_account` sirviendo el orden.

**El alcance va en la sentencia y antes que los filtros**, como en todos los listados propios: `a.user_id = :actor` excluye por construcción las cuentas de la empresa y las ajenas.

---

## 2. Cambios de esquema

**Ninguno**, salvo **los dos permisos** en la siguiente migración libre: `movements:read-own-balances` y `movements:list-own-entries`, a `SUPERADMIN`, `ADMIN` explícito y a todo rol por su tipo, como `movements:list-own` en `V31`.

---

## 3. Componentes afectados

| Capa | Componente | Cambio | Nota |
|---|---|---|---|
| `domain/repository` | `LedgerRepository` | Gana `saldosDe(persona)` y `historialDe(persona, filtros, página)` | |
| `domain/service` | `MyBalancesService`, `MyEntriesService` | Nuevos | |
| `application` | `BalancesResponse` | **Reutilizado** de `RF-MV-019`, en lista por moneda | |
| `application` | `EntryResponse`, `MyEntriesRequest` | Nuevos | |
| `interfaces` | `MovementController` | `GET /mine/balances` y `GET /mine/balances/entries` | |

---

## 4. Contrato de API

| Verbo | Ruta | Permiso |
|---|---|---|
| `GET` | `/api/v1/movements/mine/balances` | `movements:read-own-balances` |
| `GET` | `/api/v1/movements/mine/balances/entries` | `movements:list-own-entries` |

**Saldos**: `200` con `[{ currency, wallet, held, points }]`. **Historial**: `200` con la página envuelta de siempre; filtros `currencyId`, `account` (`BILLETERA`, `RETENIDO`, `PUNTOS`; **repetible** desde el 06-10-2026), `from`, `to`. **El total es exacto**: es el conjunto de una persona, como en `RF-MV-008`.

| Código | Cuándo |
|---|---|
| `200` | Siempre que el actor tenga el permiso, aunque no haya nada |
| `400` | Filtros malformados, cuenta de empresa o periodo invertido, **todos juntos** |
| `401` / `403` | Sin token / sin el permiso de la operación |

**`/mine/balances/entries` no lo captura `/mine/{id}`**: la variable es un UUID, y la ruta literal gana en Spring, como `/mine/products` desde `RF-MV-014`.

---

## 5. Autorización

Un `@PreAuthorize` por operación; `EndpointPermissionsIT` vigila que sean dos.

---

## 6. Auditoría

Ninguna: son lecturas.

---

## 7. Transaccionalidad

`@Transactional(readOnly = true)`.

---

## 8. Impacto sobre otros módulos

Ninguno.

---

## 9. Alternativas consideradas

| Alternativa | Por qué no |
|---|---|
| Una sola ruta con saldos e historial | Dos preguntas, dos permisos (`RN-SEG-014`), y una paginación que no casa con una cabecera fija |
| Sumar los asientos en vez de leer el saldo | Recorrer la historia en cada consulta para obtener lo que ya está guardado; que coincidan lo prueba `LedgerIT` |
| Historial por movimiento y no por asiento | No respondería «¿por qué tengo esto?» sin sumar |

---

## 10. Riesgos

| Riesgo | Mitigación |
|---|---|
| Que se cuele una cuenta de la empresa | El alcance en la sentencia; `CA-MV-254` |
| Un N+1 al traer el movimiento de cada asiento | Una sola sentencia con las uniones; estadísticas de Hibernate en la prueba |

---

## 11. Estrategia de prueba

Integración, `MyBalancesIT`: `CA-MV-251` a `CA-MV-259`, con los saldos sembrados por el `Ledger` y un retiro real por la ruta de `RF-MV-019` para `CA-MV-253` y `CA-MV-256`.
