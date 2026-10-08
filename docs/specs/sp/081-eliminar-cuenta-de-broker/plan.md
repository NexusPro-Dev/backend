# PLAN — `RF-SP-081` Eliminar una cuenta de broker

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-081` |
| Especificación | [`spec.md`](spec.md) v0.1.0 |
| `spec.md` aprobada el | 08-10-2026 |
| Versión | 0.1.0 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |

---

## 1. Enfoque

**Las piezas comunes están en el plan de [`RF-SP-053`](../053-registrar-cuenta-de-broker/plan.md).** Aquí solo lo propio de borrar.

`ManageBrokerAccountsService.delete`: **bloquea la cuenta** con `BrokerAccountWriter.lock(cuenta, persona)` —`404` si no es de esa persona—; si actúa el titular y está en `FIRST_DEPOSIT`, `EX-010`; si no, `DELETE` y una auditoría `DELETE` con la fila entera —broker, identificador, nombre de usuario y estado—. El segundo de dos borrados simultáneos espera al bloqueo y no encuentra la fila: `404`.

## 2. Cambios de esquema

Ninguno propio. **`user_brokers` sigue sin `deleted_at`**: el borrado es físico (`spec.md` §2), y nada referencia la tabla.

## 3. Contrato de API

`DELETE /api/v1/users/me/broker-accounts/{brokerAccountId}` y `DELETE /api/v1/users/{id}/broker-accounts/{brokerAccountId}`, `204`. Códigos en `spec.md` §10.

## 4. Estrategia de prueba

`ManageBrokerAccountsIT`, `CA-SP-931` a `CA-SP-936`.
