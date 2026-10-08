# PLAN — `RF-SP-080` Editar una cuenta de broker

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-080` |
| Especificación | [`spec.md`](spec.md) v0.1.0 |
| `spec.md` aprobada el | 08-10-2026 |
| Versión | 0.1.0 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |

---

## 1. Enfoque

**Las piezas comunes están en el plan de [`RF-SP-053`](../053-registrar-cuenta-de-broker/plan.md)**: servicio, repositorio, migración, rutas y auditoría. Aquí solo lo propio de editar.

`ManageBrokerAccountsService.update`: valida `accountId` (`VAL-013`, `VAL-015`); **bloquea la cuenta** con `BrokerAccountWriter.lock(cuenta, persona)`, que solo la encuentra si es de esa persona —`404` si no—; si actúa el titular y la cuenta está en `FIRST_DEPOSIT`, `EX-010`; si el identificador no cambia, devuelve la cuenta sin escribir (`FA-001`); si cambia, `updateAccountId`, que traduce `uq_user_brokers_cuenta` a `EX-009`, y audita antes y después. **Ni el estado ni `broker_username` se tocan.**

## 2. Cambios de esquema

Ninguno propio: los permisos los siembra `V90` (plan de `RF-SP-053` §2).

## 3. Contrato de API

`PATCH /api/v1/users/me/broker-accounts/{brokerAccountId}` y `PATCH /api/v1/users/{id}/broker-accounts/{brokerAccountId}`, cuerpo `{"accountId": "…"}`, `200` con la cuenta. Códigos en `spec.md` §10.

## 4. Estrategia de prueba

`ManageBrokerAccountsIT`, `CA-SP-923` a `CA-SP-930`. El estado `FIRST_DEPOSIT` se siembra por SQL: no hay API que lo mueva (`RF-SP-054`).
