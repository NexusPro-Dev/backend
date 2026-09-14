# TASKS — `RF-SP-056` Consultar las cuentas de broker del equipo

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-056` |
| Especificación | [`spec.md`](spec.md) |
| Plan | [`plan.md`](plan.md), aprobado el 10-09-2026 |
| Estado | **En revisión** — `T-01` a `T-09` **Hecha** el 10-09-2026, con `mvn verify` en **1162 pruebas verdes** |
| Issue | Pendiente de crear |
| Rama | `feature/venta-de-productos` |
| Autor | Responsable técnico |

---

## 1. Tareas

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-01` | `BrokerAccountQueryRepository`: la consulta del equipo vigente con sus dos filtros, y su conteo | `RF-SP-055 · T-04` | `CA-SP-637`, `CA-SP-641`, `CA-SP-642`. El `ended_at IS NULL` está en las **dos** | **Hecha el 10-09-2026** |
| `T-02` | `TeamBrokerAccountItem`: la cuenta **con su titular** como objeto, no como cuatro campos con prefijo | `T-01` | `CA-SP-638` | **Hecha el 10-09-2026** |
| `T-03` | `GetTeamBrokerAccountsService`: resuelve el equipo del actor, valida `status` y pagina | `T-02` | `CA-SP-643`, `CA-SP-644` | **Hecha el 10-09-2026** |
| `T-04` | `UserController`: `GET /api/v1/users/me/team/broker-accounts` con `@PreAuthorize("isAuthenticated()")` | `T-03` | La ruta usa el vocabulario **equipo**, no «clientes» | **Hecha el 10-09-2026** |
| `T-05` | Declarar la ruta en `EndpointPermissionsIT` como autenticada sin permiso, **con su motivo** | `T-04` | La lista cerrada sigue siendo exhaustiva | **Hecha el 10-09-2026** |
| `T-06` | Pruebas de API de `spec.md` §12, **incluidas las tres exclusiones**: ajeno, ex-miembro y **nieto** | `T-05` | `CA-SP-637` a `CA-SP-645` | **Hecha el 10-09-2026** |
| `T-07` | Prueba de paginación sobre dos páginas seguidas | `T-06` | Ni repite ni pierde filas; el total cuenta lo filtrado | **Hecha el 10-09-2026** |
| `T-08` | Documentación OpenAPI del endpoint: **prosa**, no solo esquema | `T-06` | Dice que es **un solo nivel**, que el listado es de **cuentas y no de personas**, y que hoy el estado es siempre `REGISTER` | **Hecha el 10-09-2026** |
| `T-09` | Actualizar la matriz de `docs/requirements.md` | `T-06` | La fila de `RF-SP-056` refleja el estado | **Hecha el 10-09-2026** |

## 2. Orden de ejecución

Después de `RF-SP-055`, que trae la columna, el enumerado y el puerto.

**`T-06` antes que `T-07`**: la paginación no significa nada hasta que el conjunto de filas es el correcto.

**De `T-06`, la prueba del nieto es la que no puede faltar**: es la única que distingue «el equipo directo» de «todo lo que cuelga», y las dos consultas se parecen lo suficiente como para que nadie note la diferencia leyendo el código.

## 3. Cobertura de los criterios de aceptación

| Criterio | Tareas |
|---|---|
| `CA-SP-637` | `T-01`, `T-06` |
| `CA-SP-638` | `T-02`, `T-06` |
| `CA-SP-639`, `CA-SP-640`, `CA-SP-645` | `T-01`, `T-06` |
| `CA-SP-641`, `CA-SP-642` | `T-01`, `T-06` |
| `CA-SP-643` | `T-03`, `T-06` |
| `CA-SP-644` | `T-03`, `T-06` |

## 4. Bloqueos

| # | Bloqueo | Desde | Responsable | Estado |
|---|---|---|---|---|
| 1 | **Depende de `RF-SP-055`**, que trae la columna `status`, el enumerado y el puerto de consulta. Es dependencia de orden, no de decisión | 10-09-2026 | Responsable técnico | **Abierto** |
| 2 | **Nadie mueve la cuenta a `FIRST_DEPOSIT`** (`RF-SP-054`), de modo que el filtro por estado se prueba **sembrando el valor en la base**, no por la API. No bloquea el requerimiento | 10-09-2026 | Responsable del proyecto | **Abierto** |
| 3 | **D-22 sigue abierta.** La excepción de `RN-SP-046` queda acotada a estas dos lecturas | 10-09-2026 | Responsable del proyecto | **Abierto** |

## 5. Definición de terminado

- [x] Todas las tareas en estado `Hecha`.
- [x] Todos los criterios de aceptación con prueba automatizada en verde.
- [x] `mvn verify` en verde en local.
- [x] El endpoint consta en `EndpointPermissionsIT` con su motivo escrito al lado.
- [x] El contrato OpenAPI coincide con el comportamiento real, **prosa incluida**.
- [x] Documentación afectada actualizada en el mismo Pull Request.
- [x] Matriz de trazabilidad actualizada.
- [ ] Pull Request aprobado por alguien distinto del autor e integrado.
