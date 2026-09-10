# PLAN — `RF-SP-057` Consultar y filtrar todas las cuentas de broker

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-057` |
| Especificación | [`spec.md`](spec.md), aprobada el 10-09-2026 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 10-09-2026 |

---

## 1. Enfoque

**Se apoya entero en `RF-SP-056` y no repite nada suyo**: el enumerado, el puerto, la fila con su titular y su DTO ya existen. Lo que este requerimiento añade son **dos cosas**: un método más en el mismo puerto —el listado global— y **la recursiva**.

**La fila se reutiliza tal cual**, no se copia. Que las dos respuestas coincidan campo por campo es contrato (`CA-SP-657`): el frontend pinta las dos pantallas con un solo componente.

## 2. Cambios de esquema

Una migración:

| Migración | Qué hace |
|---|---|
| `V82__user_brokers_search_index.sql` | `ix_user_brokers_busqueda`, gin de trigramas sobre `f_unaccent(lower(external_id))` |

### 2.1 Las expresiones del índice son LAS DEL PREDICADO

```sql
CREATE INDEX ix_user_brokers_busqueda ON user_brokers USING gin (
    f_unaccent(lower(external_id)) gin_trgm_ops
);
```

Es la lección que `V29` dejó escrita para `ix_users_busqueda`, y se repite aquí porque el defecto **no se manifiesta como un error**: si la expresión del índice y la del predicado divergen en un solo carácter, el índice existe, el planificador **no lo usa nunca**, y lo que se ve es una consulta lenta que nadie relaciona con esta migración.

**Los otros tres campos de `search` ya están indexados**: `username`, `email` y el nombre completo, en `ix_users_busqueda` (`V29`). El que faltaba era el número de cuenta, que es **el que se busca aquí de verdad** — quien revisa un caso concreto llega con el número que le dio el broker.

### 2.2 Ningún índice más

El recorrido de la red entra por `ix_user_supervisors_supervisor_vigente` (`V28`, parcial sobre `ended_at IS NULL`) y las cuentas por `ix_user_brokers_persona` (`V74`). `status` tiene dos valores y no sería selectivo; `broker_id` ya encabeza el único `(broker_id, external_id)`.

## 3. Componentes afectados

| Capa | Elemento | Módulo |
|---|---|---|
| `brokers/application` | `ListBrokerAccountsRequest` — los siete filtros | `SP` |
| `brokers/domain/repository` | `BrokerAccountQueryRepository` — **dos métodos más**: el listado y su conteo | `SP` |
| `brokers/domain/service` | `ListBrokerAccountsService` | `SP` |
| `brokers/interfaces` | `BrokerAccountController` — `GET /api/v1/broker-accounts` | `SP` |

**Controlador propio y no `UserController`**: la ruta es de primer nivel y no cuelga de una persona. Es la contraria a la decisión de `RF-SP-055`, y por el mismo criterio — **la ruta manda**.

## 4. La recursiva, que es lo único nuevo de verdad

```sql
WITH RECURSIVE red AS (
    SELECT us.user_id
      FROM user_supervisors us
     WHERE us.supervisor_id = CAST(:raiz AS uuid)
       AND us.ended_at IS NULL
    UNION
    SELECT us.user_id
      FROM user_supervisors us
      JOIN red r ON us.supervisor_id = r.user_id
     WHERE us.ended_at IS NULL
)
```

Cuatro decisiones, y ninguna es de estilo:

- **`UNION` y no `UNION ALL`.** Es lo que hace que la consulta **no pueda colgarse** aunque los datos tuvieran un ciclo: quien ya se vio no se reexpande. Que el ciclo no deba existir —`RN-SP-020` ata esta cadena a la de roles, que es acíclica— es un argumento sobre los datos, y **la terminación no debería depender de un argumento**.
- **`ended_at IS NULL` en LOS DOS brazos.** Omitirlo en el recursivo haría descender por la estructura **de ayer** sin que nada fallara — el error que no rompe nada y devuelve de más.
- **La raíz NO se siembra en el brazo base.** El brazo base son **sus subordinados**, no ella: por eso su red la excluye (`CA-SP-649`).
- **`deleted_at` se filtra FUERA de la recursiva**, al unir con `users`. Dentro cortaría la rama: una persona eliminada dejaría de expandirse y **sus subordinados desaparecerían del resultado** aunque sigan vivos y colgando. Es `FA-003`, y son dos cosas distintas que se prueban por separado (`CA-SP-651`).

**El predicado se escribe UNA vez** y lo comparten la página y el conteo, como en `RF-SP-056`: dos copias son lo que hace que un día el total diga una cosa y la página otra.

## 5. Contrato de API

`GET /api/v1/broker-accounts?supervisorId=…&userId=…&status=REGISTER&brokerId=…&search=7012&from=…&to=…&page=0&size=20`

La respuesta es **`PageResponseTeamBrokerAccountItem`**, el mismo esquema que devuelve `GET /api/v1/users/me/team/broker-accounts`.

**Y se declara sin `@Schema(implementation = …)` en el `200`**, dejando que springdoc use el tipo de retorno. No es un detalle: el anotado publica la envoltura **cruda** —`content` sin tipo— y el cliente generado no sabría qué hay en cada fila. Es el defecto que este mismo endpoint hermano tuvo el 10-09-2026 y que **solo se ve leyendo `docs/api/openapi.json`**, porque el contrato sigue siendo válido.

## 6. Autorización

`@PreAuthorize("hasAuthority('broker-accounts:read')")`. Corriente, y esa normalidad es la diferencia con `RF-SP-055`: aquí **no** hay que resolver nada contra el actor, de modo que la anotación basta y no hay que declarar nada en `EndpointPermissionsIT`.

## 7. Auditoría y transaccionalidad

**No audita.** `@Transactional(readOnly = true)`, con el conteo y la página en la misma foto.

## 8. Alternativas consideradas

| Alternativa | Por qué se descartó |
|---|---|
| **`?supervisorId=` en `/users/me/team/broker-accounts`** | La ruta dejaría de significar «lo mío» en cuanto llegara el parámetro, y serviría dos preguntas con dos autorizaciones por el mismo camino |
| **Un nivel, como los tres hermanos** | Lo pidió en profundidad, y el argumento que sostiene la cota en aquellos —los autoriza la estructura— **aquí no aplica** |
| **Resolver la red en Java, nivel a nivel** | Una consulta por nivel y por rama. El `N + 1` que este requerimiento existe para quitar, movido del navegador al servidor |
| **`UNION ALL` con un tope de profundidad** | El tope es un número inventado que algún día se alcanza, y hasta entonces esconde el ciclo en vez de tolerarlo |
| **Incluir la raíz en su propia red** | «Su red» son los suyos. Quien quiera las de ella las pide con `userId`, y los dos filtros se combinan |
| **Filtro por rol** | Se ofreció y no se pidió. `supervisorId` responde la pregunta que lo motivaba, y duplicaría el de `RF-SP-042` |
| **Retirar `RF-SP-055`** | Existe para el superior **sin** permiso. Son la misma forma con dos autorizaciones distintas |

## 9. Riesgos

| # | Riesgo | Mitigación |
|---|---|---|
| 1 | **La recursiva se cuela hacia arriba** —`user_id` y `supervisor_id` intercambiados— y devuelve la cadena de mando en vez de la red | `CA-SP-648` con tres niveles, y `CA-SP-649`: la raíz **no** aparece. Las dos juntas fijan la dirección |
| 2 | El `deleted_at` se mete **dentro** de la recursiva y **corta la rama** | `CA-SP-651` lo prueba: el eliminado no sale y **los suyos sí**. Sin esa prueba, el defecto devuelve de menos y nadie lo nota |
| 3 | El `ended_at IS NULL` falta en el brazo recursivo | `CA-SP-650`: quien dejó la estructura no aparece **ni los que colgaban de él por esa vía** |
| 4 | El índice de trigramas no se usa porque la expresión no coincide con el predicado | Se escriben juntos y se declara en §2.1. Es el defecto que solo se ve como lentitud |
| 5 | El `200` publica la envoltura cruda otra vez | §5 lo declara, y se comprueba **leyendo el `openapi.json`** — ninguna prueba lo detecta |

## 10. Estrategia de prueba

- **Profundidad**: cadena de tres niveles; el nieto aparece, la raíz no.
- **Vigencia y borrado**: un miembro que dejó la estructura y una persona eliminada **con subordinados vivos** — las dos afirman cosas contrarias sobre la rama.
- **Filtros**: cada uno por separado, `supervisorId` + `userId` combinados, e inexistentes que devuelven vacío.
- **Texto**: por número de cuenta y por nombre de usuario, con acento y con mayúsculas.
- **Fechas**: el borde del rango semiabierto, que es donde se equivoca quien lo escribe.
- **Errores**: `status` inválido, `from` posterior a `to`, y `403` sin permiso.
- **Contrato**: la fila coincide campo por campo con la de `RF-SP-056`.
