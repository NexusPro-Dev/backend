# PLAN — `RF-MV-054` Consultar los saldos de una persona

| Campo | Valor |
|---|---|
| Requerimiento | `RF-MV-054` |
| Especificación | [`spec.md`](spec.md) v0.1.0 |
| `spec.md` aprobada el | 05-10-2026 |
| Versión | 0.1.0 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 05-10-2026 |

---

## 1. Enfoque

**`BalanceService.balances()` de `RF-MV-022`, con la persona como parámetro**: la agregación por moneda se extrae a un método que recibe a quién, y «mis saldos» lo llama con el actor. La persona se comprueba con `ClientCatalog`, como el bono; inexistente o eliminada, `404`.

## 2. Cambios de esquema

El permiso `movements:read-user-balances`, en `V73` (ver `RF-MV-053` · `plan.md` §2).

## 3. Contrato de API

| Verbo | Ruta | Permiso |
|---|---|---|
| `GET` | `/api/v1/movements/users/{userId}/balances` | `movements:read-user-balances` |

Respuesta: la lista de `BalancesResponse` de `RF-MV-022`. `200`, `401`, `403`, `404`.

## 4. Autorización

`@PreAuthorize("hasAuthority('movements:read-user-balances')")`.

## 5. Estrategia de prueba

Integración, en `PointsAdjustmentListIT`: `CA-MV-656` a `CA-MV-659`.
