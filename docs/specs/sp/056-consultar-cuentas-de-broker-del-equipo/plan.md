# PLAN — `RF-SP-056` Consultar las cuentas de broker del equipo

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-056` |
| Especificación | [`spec.md`](spec.md), aprobada el 10-09-2026 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 10-09-2026 |
| Enmendado | 21-09-2026 — exige **`broker-accounts:read-own-team`** (`RF-SP-062`, `RN-SEG-015`: autenticarse no autoriza nada); lo siembra `V31` |

---

!!! note "Enmienda de Art. I.7 — 21-09-2026, `RF-SP-062`"

    Esta operación exige **`broker-accounts:read-own-team`** desde el 21-09-2026, por `RF-SP-062` —**autenticarse no autoriza nada**, `RN-SEG-015` ([`security.md` §4.3](../../../security.md#43-reglas-de-negocio))—, por decisión del responsable del proyecto: «cada endpoint debe tener su propio permiso, ya que uso esto para saber qué vista o consulta mostrar en el front; no basta con solo tener el token». Hasta entonces se atendía con solo el token, y las líneas que abajo dicen «sin permiso» o «autenticado a secas» hablan de esa decisión original y se conservan como historia: el alcance sobre uno mismo sigue siendo exactamente el mismo, lo que cambia es que ahora tiene nombre. `V31` siembra el permiso y lo da a todo rol por su tipo, a `CONSUMIDOR` no.



## 1. Enfoque

**Se apoya entero en `RF-SP-055` y no repite nada suyo**: el enumerado, el puerto de consulta, la proyección de una cuenta y el DTO del broker ya existen allí. Lo que este requerimiento añade es **una consulta más en el mismo puerto** —la del equipo— y **un DTO que envuelve al de la cuenta con su titular**.

**Se construye después y en la misma entrega.** Separarlos en dos entregas obligaría a publicar dos veces el mismo esquema OpenAPI y dejaría al frontend con la mitad de la pantalla.

## 2. Cambios de esquema

**Ninguno.** La columna la trae `RF-SP-055 · T-01` y los dos índices que la consulta necesita ya existen:

| Índice | De dónde sale | Qué sirve aquí |
|---|---|---|
| `ix_user_supervisors_supervisor_vigente` | `V28`, parcial sobre `ended_at IS NULL` | El equipo vigente del actor, sin recorrer el historial |
| `ix_user_brokers_persona` | `V74` | Las cuentas de cada miembro |

**Y ninguno nuevo**: `status` tiene dos valores y un índice sobre él no sería selectivo; `broker_id` ya entra por el único `(broker_id, external_id)`.

## 3. Componentes afectados

| Capa | Elemento | Módulo |
|---|---|---|
| `brokers/domain/repository` | `BrokerAccountQueryRepository` — **un método más**: las cuentas del equipo, con su conteo | `SP` |
| `brokers/application` | `TeamBrokerAccountItem` — la cuenta **con su titular** | `SP` |
| `brokers/domain/service` | `GetTeamBrokerAccountsService` | `SP` |
| `users/interfaces` | `UserController` — `GET /api/v1/users/me/team/broker-accounts` | `SP` |

**El conteo y la página son dos consultas y no una.** Es lo que hace el resto del sistema (`RF-SP-025`, `RF-SP-042`), y la alternativa —una ventana `COUNT(*) OVER ()`— devuelve el total repetido en cada fila y se rompe justo en el caso que importa: la página vacía no trae ninguna fila donde leerlo.

## 4. Contrato de API

`GET /api/v1/users/me/team/broker-accounts?status=REGISTER&brokerId=…&page=0&size=20`

```json
{
  "content": [
    {
      "id": "018f…",
      "user": {
        "id": "018d…",
        "username": "lgomez",
        "firstName": "Laura",
        "lastName": "Gómez"
      },
      "broker": { "id": "018e…", "name": "EXNOVA" },
      "accountId": "70123456",
      "brokerUsername": null,
      "status": "REGISTER",
      "declaredAt": "2026-09-10T14:03:11Z"
    }
  ],
  "totalElements": 1,
  "page": 0,
  "size": 20,
  "totalPages": 1
}
```

- **La ruta es `/users/me/team/...` y no `/users/me/clients/...`**: el vocabulario del sistema para «quienes dependen de mí» es **equipo**, fijado por `GET /users/{id}/team` (`RF-SP-042`). Llamarlo «clientes» aquí sugeriría que filtra por rol, y no lo hace — el equipo de un supervisor puede tener vendedores.
- **`user` es un objeto y no cuatro campos sueltos** con prefijo. Es la forma que ya tiene `CommercialStructureResponse.Person`, y deja crecer el titular sin renombrar nada.
- **La cuenta se serializa igual que en `RF-SP-055`**, campo por campo. Que las dos respuestas coincidan es lo que permite al frontend tener **un solo componente** para pintar una cuenta.
- **`PageResponse` del sistema**, sin inventar otra envoltura.

## 5. Autorización

**`@PreAuthorize("isAuthenticated()")`**, y nada más. No hay permiso porque **no hay nada que acotar**: el actor no puede nombrar a otro, y el conjunto de datos sale de `user_supervisors`.

Esto la hace **más estrecha que su hermana** y le ahorra el `404`: donde `RF-SP-055` tiene que ocultar la existencia de un recurso ajeno, aquí no hay recurso ajeno que pedir.

**Entra en `EndpointPermissionsIT`** como autenticada sin permiso, con su motivo escrito al lado.

## 6. Auditoría

**No audita.** Es una lectura.

## 7. Transaccionalidad

`@Transactional(readOnly = true)`. El conteo y la página se leen **de la misma foto**: separados, una reasignación simultánea daría un total que no cuadra con las filas.

## 8. Alternativas consideradas

| Alternativa | Por qué se descartó |
|---|---|
| **Anidar las cuentas dentro de cada miembro del equipo** | Paginar un anidado pagina **personas**, y la pregunta —«¿quién falta por depositar?»— es sobre cuentas. Además obliga al cliente a recorrer dos niveles para contestarla |
| **Añadir las cuentas a la respuesta de `RF-SP-042`** | Mezcla dos preguntas en un endpoint y le cuelga una segunda consulta a todo el que solo quiere el equipo. Y `RF-SP-042` publica personas sin cuentas, que aquí no van |
| **`?supervisorId=` para que un administrador vea el equipo de otro** | Nadie lo ha pedido, y abrirlo obligaría a decidir el `404` otra vez. Quien tiene el permiso ya llega persona a persona por `RF-SP-055` |
| **Reutilizar `findTeam` de `UserRepository` y consultar cuentas por cada miembro** | Un `N + 1` que ninguna prueba detectaría —la respuesta sería correcta y solo lenta—, que es exactamente por lo que hay que evitarlo a propósito. Es la lección que `RF-SP-042` dejó escrita al resolver los roles en una sola consulta |
| **`COUNT(*) OVER ()` en la misma consulta** | Se rompe en la página vacía, que es un caso normal aquí (`FA-001`, `FA-002`) |
| **Llamar `clients` al recurso** | El vocabulario del sistema es «equipo», y el equipo no es solo de clientes |

## 9. Riesgos

| # | Riesgo | Mitigación |
|---|---|---|
| 1 | **Se publica el árbol descendente** por escribir la consulta recursiva «porque es más útil» | `CA-SP-645` lo prueba con tres niveles: las cuentas del nieto **no aparecen** |
| 2 | **El historial concede lectura** por olvidar `ended_at IS NULL` | `CA-SP-640`, con un miembro que dejó el equipo ayer |
| 3 | El orden no es determinista y la paginación **repite u omite** filas | Tres desempates —titular, broker, identificador de cuenta— y una prueba que recorre dos páginas |
| 4 | `?status=cualquiercosa` devuelve **página vacía** en lugar de `400`, y se lee como «no hay ninguna» | `CA-SP-643`. Es la asimetría deliberada con `brokerId`, razonada en la spec §6.1 |

## 10. Estrategia de prueba

- **Integración**: equipo con dos miembros y tres cuentas; alguien fuera del equipo con cuentas que **no** salen; un ex-miembro que **no** sale; un nieto que **no** sale.
- **Filtros**: por estado, por broker, los dos a la vez, `brokerId` inexistente y `status` inválido.
- **Paginación**: dos páginas seguidas sin repetir ni perder filas, y el total contando **lo filtrado**.
- **Vacíos**: sin equipo y con equipo sin cuentas, los dos con `200`.
