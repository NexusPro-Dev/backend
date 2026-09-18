# PLAN — `RF-SP-059` Consultar los vendedores de un cliente

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-059` |
| Especificación | [`spec.md`](spec.md), aprobada el 18-09-2026 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 18-09-2026 |

---

## 1. Enfoque

**Una lectura pequeña que arrastra una mudanza.** La consulta en sí son dos rutas sobre una tabla de cinco columnas; lo que hace al plan es que la tabla no existe, que los datos que tiene que contener están en otra, y que **cinco lecturas construidas** leen hoy esa otra tabla para responder «¿quién es el vendedor de este cliente?». El plan se organiza alrededor de eso: **primero la migración, después el cambio de sitio de cada lectura, y la ruta nueva al final**, porque es lo único que no rompe nada si se retrasa.

**Se mueve, no se duplica.** La alternativa cómoda era crear `client_sellers`, copiar y dejar `user_supervisors` como estaba «por si acaso»: cada lectura seguiría funcionando y el cambio se haría poco a poco. Se descarta porque es exactamente el estado que la decisión del 18-09-2026 quiso terminar —dos tablas diciendo lo mismo, y cada consulta eligiendo cuál creer—. `V20` borra, y las lecturas cambian **en el mismo Pull Request**, porque después de `V20` las que no hayan cambiado devuelven **menos** sin que nada falle.

**El repositorio es propio y nativo, sin entidad JPA**, como `user_supervisors` en `JpaUserRepository` y `user_brokers` en el submódulo de brokers: no hay agregado que cargar, solo filas que insertar y proyectar. Vive en `users/domain/repository`, porque la tabla es de personas y `RF-SP-045` —que la escribe— y `ClientCatalog` —que la lee para `MV`— ya están ahí.

**El controlador es `UserController`**, por lo mismo que `RF-SP-055`: las dos rutas cuelgan de `/users` y partirlas repartiría el prefijo entre dos ficheros.

## 2. Cambios de esquema

Una migración: `V20__sp_vendedores_del_cliente.sql`. **Se numeró comprobando el máximo aplicado**: `V19` es de `feature/academia`, sobre la que esta rama nace, y `backend-ff` reservó `V21` en adelante para `AC`.

### 2.1 La tabla, tal como §10.19 la exige

```sql
CREATE TABLE client_sellers (
    client_id         uuid        NOT NULL,
    seller_id         uuid        NOT NULL,
    origin            varchar(20) NOT NULL,
    first_movement_id uuid        NULL,
    created_at        timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT pk_client_sellers          PRIMARY KEY (client_id, seller_id),
    CONSTRAINT fk_client_sellers_client   FOREIGN KEY (client_id)         REFERENCES users (id),
    CONSTRAINT fk_client_sellers_seller   FOREIGN KEY (seller_id)         REFERENCES users (id),
    CONSTRAINT fk_client_sellers_movement FOREIGN KEY (first_movement_id) REFERENCES movements (id),
    CONSTRAINT ck_client_sellers_no_self  CHECK (client_id <> seller_id),
    CONSTRAINT ck_client_sellers_origin   CHECK (origin IN ('REGISTRO', 'HOTLINK'))
);

CREATE UNIQUE INDEX uq_client_sellers_principal
    ON client_sellers (client_id) WHERE origin = 'REGISTRO';

CREATE INDEX ix_client_sellers_vendedor
    ON client_sellers (seller_id);
```

- **Sin clave sustituta**, al contrario que `user_supervisors`: allí el mismo par se repite en el tiempo y lo que distingue una fila es el periodo; aquí la pareja **es** la fila, y una segunda compra por el mismo hotlink no añade nada.
- **El índice único parcial es lo que hace cierto a `RN-SP-049`.** «Un principal por cliente» dicho en una regla es una intención; dicho en un índice es un rechazo. El día que `RF-MV-011` escriba desde otra transacción, la base lo garantiza sin que nadie tenga que acordarse.
- **`ix_client_sellers_vendedor` no lo usa nadie hoy** y se crea igual: es la entrada de `RF-SP-061` (los clientes de un vendedor; `RF-SP-060` en la rama original), de la unión con el equipo en `RF-SP-056` y de la hoja de la recursiva en `RF-SP-057`, y las tres llegan en este mismo plan o en el siguiente.
- **La clave foránea hacia `movements` es la primera de `SP` hacia `MV`**, y no viola la dirección de dependencia entre módulos: ArchUnit vigila paquetes de Java, no claves de la base, y un vínculo que cita la venta que lo creó es más honesto que uno que la olvida. **Sin `ON DELETE`**: los movimientos no se borran (`RF-MV-001`).

### 2.2 La mudanza, en la misma migración

```sql
INSERT INTO client_sellers (client_id, seller_id, origin, first_movement_id, created_at)
SELECT us.user_id, us.supervisor_id, 'REGISTRO', NULL, us.started_at
  FROM user_supervisors us
 WHERE us.ended_at IS NULL
   AND EXISTS (SELECT 1 FROM user_roles ur
                WHERE ur.user_id = us.user_id AND ur.role_type = 'CONSUMIDOR')
   AND NOT EXISTS (SELECT 1 FROM user_roles ur
                    WHERE ur.user_id = us.user_id AND ur.role_type = 'VENDEDOR');

DELETE FROM user_supervisors us
 WHERE EXISTS (SELECT 1 FROM user_roles ur
                WHERE ur.user_id = us.user_id AND ur.role_type = 'CONSUMIDOR')
   AND NOT EXISTS (SELECT 1 FROM user_roles ur
                    WHERE ur.user_id = us.user_id AND ur.role_type = 'VENDEDOR');
```

- **Quién es «cliente» para la migración**: quien porta un rol `CONSUMIDOR` **y ninguno `VENDEDOR`**. Una persona con los dos —un cliente ascendido a agente por `RF-SP-030`— **se queda** en `user_supervisors`: su fila vigente es mando, y tocarla la dejaría sin superior siendo vendedora (`RN-SP-019`). Es el caso límite de la spec §13, resuelto del lado de no romper una regla crítica.
- **Solo la fila vigente se copia**; **todas** las del cliente se borran. `created_at` toma `started_at`, que es desde cuándo cuelga, y no `now()`, que sería desde cuándo se migró.
- **`first_movement_id` queda nulo**, y es el único caso en que lo estará (§10.19): la venta del registro existe en `movements`, pero unirla por persona y fecha es una conjetura, y una clave foránea no se rellena con conjeturas.
- **No lleva guarda de recuento.** Podría comprobarse que las filas insertadas igualan a las vigentes borradas y abortar si no; pero la desigualdad tiene un caso legítimo —un cliente con **solo** tramos cerrados, sin vigente— y abortar la migración de producción por él sería peor que dejarlo sin principal, que es lo que ya era.

## 3. Componentes afectados

| Capa | Elemento | Cambio | Módulo |
|---|---|---|---|
| `db/migration` | `V20__sp_vendedores_del_cliente.sql` | Nueva | `SP` |
| `users/domain/repository` | `ClientSellerRepository` (puerto) y `JpaClientSellerRepository` | Nuevos: `registerPrincipal`, `attachFirstMovement`, `principalOf`, `findSellersOf` | `SP` |
| `users/application` | `ClientSellersResponse` y su `SellerItem` | Nuevos | `SP` |
| `users/domain/service` | `GetClientSellersService` | Nuevo | `SP` |
| `users/interfaces` | `UserController` | `GET /users/me/sellers` y `GET /users/{id}/sellers` | `SP` |
| `users/domain/service` | `RegisterClientByLinkService` | **`assignSupervisor` → `registerPrincipal`**, y `attachFirstMovement` después de la venta | `SP` |
| `users/application` · `movements/domain/service` | `RegistrationSaleRegistrar` y `PublishedRegistrationSaleRegistrar` | `registerSale` devuelve **`RegisteredSale(id, code)`**, no solo el código | `SP` · `MV` |
| `users/domain/repository` | `PublishedUserCatalog.sellerOf` | **Primero `client_sellers` (`REGISTRO`), después `user_supervisors`** | `SP` |
| `brokers/domain/service` | `GetBrokerAccountsService` | La autorización admite **superior vigente o principal** | `SP` |
| `brokers/domain/repository` | `JpaBrokerAccountQueryRepository` | El equipo de `RF-SP-056`, la recursiva de `RF-SP-057`, los conteos de `RF-SP-058` y «lo no atribuido» | `SP` |
| `db/dev-seed` | `semilla-desarrollo.sql` | Los tres clientes a `client_sellers`; `DevelopmentSeedIT` lo comprueba | — |
| `users/domain/security` | `CommercialStructure` | **Sin cambios de código**: la rama de consumidor nunca se construyó. Su Javadoc deja de anunciarla | `SP` |

**Lo que NO cambia y conviene decir por qué.** `GetCommercialTeamService` y `countActiveSubordinates` (`RN-SP-022`) leen `user_supervisors` sin preguntar por el tipo de rol: después de `V20` dejan de ver clientes **sin tocar una línea**, y las pruebas que lo afirmaban se invierten (`CA-SP-696`, `CA-SP-698`, `CA-SP-699`). `AssignSupervisorService` tampoco cambia: nunca supo asignar a un cliente porque `T-02` de `RF-SP-045` no se hizo.

## 4. Contrato de API

`GET /api/v1/users/me/sellers` · `GET /api/v1/users/{id}/sellers`

```json
{
  "content": [
    {
      "username": "agente1",
      "firstName": "Ana",
      "lastName": "Martínez",
      "origin": "REGISTRO",
      "principal": true,
      "linkedAt": "2026-09-04T10:12:03Z"
    },
    {
      "username": "agente7",
      "firstName": "Luis",
      "lastName": "Rojas",
      "origin": "HOTLINK",
      "principal": false,
      "linkedAt": "2026-09-17T18:40:55Z"
    }
  ]
}
```

- **Envuelto en `content`**, como `RF-SP-055`: deja sitio a paginar sin romper a nadie.
- **Mismo cuerpo en las dos rutas.** Un administrador no necesita más que un cliente: si quiere ir al vendedor, tiene el nombre de usuario y `RF-SP-025`.
- **`principal` viaja aunque se derive de `origin`** (spec §6.2). Es la pregunta que motivó el requerimiento.
- **`linkedAt` es `created_at`** y se publica con ese nombre porque lo que significa es desde cuándo ese vendedor es suyo; para las filas migradas es la fecha en que colgó de él en `user_supervisors`.
- **Sin `id` del vendedor**, por decisión escrita en la spec §14.5.

## 5. Autorización

- **`/me/sellers`: sin `@PreAuthorize`**, y la ausencia es deliberada, con el motivo escrito al lado y la ruta declarada en `EndpointPermissionsIT` como autenticada sin permiso. El actor sale del token; no hay identificador que validar ni existencia que comprobar.
- **`/{id}/sellers`: `@PreAuthorize("hasAuthority('users:read')")`**, el modelo general. El `403` sale antes de tocar la base; el `404` solo aparece con el permiso puesto y una persona que no existe.

**`RN-SP-046` cambia de tabla para un caso** y hay que decirlo entero, porque `security.md` §5 la acota como la única lectura autorizada por estructura: `GetBrokerAccountsService` pasa a preguntar **dos cosas** —«¿el actor es el superior vigente de esta persona en `user_supervisors`?» **o** «¿es su principal en `client_sellers`?»— y cualquiera de las dos autoriza. El perímetro no crece: sigue siendo un nivel, siguen siendo las cuentas de broker, y el vinculado por `HOTLINK` sigue recibiendo `404` (`CA-SP-693`).

## 6. Auditoría

**No audita.** Es una lectura. La migración tampoco escribe en `audit_change_log`: mueve datos de sitio sin cambiar lo que significan, y una fila de auditoría por cliente diría «cambió su superior» cuando no cambió nada.

## 7. Transaccionalidad

`@Transactional(readOnly = true)` en la lectura. En `RegisterClientByLinkService` el orden importa y queda fijado:

1. `registerPrincipal(cliente, vendedor)` — **antes** de la venta, porque `registrarAltaDeCliente` resuelve el vendedor con `ClientCatalog.sellerOf`, que desde hoy mira `client_sellers`.
2. `registerSale(...)` — devuelve el identificador de la venta.
3. `attachFirstMovement(cliente, vendedor, venta)` — completa la fila.

Todo en la transacción del registro, que ya existía: si la venta se rechaza, no queda ni la persona ni el vínculo.

## 8. Enmiendas a otros documentos

Las enmiendas de diseño **se aplicaron el 18-09-2026, antes que esta tripleta**, en el orden que el proyecto fija —módulo, modelo de datos, tripletas—: `requirements/sp.md` 1.63.0, `requirements/mv.md` 0.28.0, `modelo-datos.md` 0.64.0, `security.md` 0.64.0 y `requirements.md` 0.179.0 (1.59.0, 0.27.0, 0.61.0 y 0.168.0 en la rama; renumeradas el 21-09-2026 al integrar sobre `feature/academia`). Lo que este plan añade es la enmienda de **las tripletas construidas** que afirmaban lo contrario:

| Tripleta | Enmienda |
|---|---|
| `RF-SP-042` | `CA-SP-625` **invertido** en `CA-SP-696`: el equipo no contiene clientes. `roles` y su filtro se conservan |
| `RF-SP-045` | `CA-SP-513`, `CA-SP-525` y `CA-SP-526` **invertidos** en `CA-SP-697` a `CA-SP-699`; `T-02` pasa de «no hizo falta» a **«ya no existe»**; §4.1 y §5 de su spec dejan de colgar al cliente en `user_supervisors` |
| `RF-SP-057` | `CA-SP-648` **precisado**: la red en profundidad incluye a los clientes `REGISTRO` de cada nodo, y el «nieto» es uno de ellos |
| `RF-SP-058` | `CA-SP-658` y `CA-SP-664` **precisados**: «cuelgan de» es `client_sellers`, y «no cuelgan de ningún vendedor» es no tener `REGISTRO` con un vendedor |
| `RF-MV-001` | `CA-MV-002` **precisado**: el vendedor resuelto es el principal del cliente en `client_sellers` |
| `RF-MV-013` | Su plan deja de decir que `client_sellers` la crea `RF-MV-011`, y que el principal se comprueba en `user_supervisors` |

Y al terminar la construcción: `api/index.md` con las dos rutas, el contrato regenerado y la matriz.

## 9. Alternativas consideradas

| Alternativa | Por qué se descartó |
|---|---|
| **Copiar y no borrar** —dejar `user_supervisors` como estaba y cambiar las lecturas poco a poco— | Es el estado que la decisión quiso terminar: dos tablas diciendo lo mismo y cada consulta eligiendo cuál creer. Y después de `V20` las lecturas sin cambiar **devolverían de menos sin fallar** |
| **Cerrar las filas de clientes** (`ended_at`) en lugar de borrarlas | Conserva filas de clientes en una tabla que ya no los admite; `RF-SP-042` y `RN-SP-022` tendrían que seguir filtrándolas. Decisión del responsable del proyecto (spec §14.3) |
| **Mover también al cliente ascendido a vendedor** | Lo dejaría sin superior siendo vendedor, contra `RN-SP-019`. Se queda, y su `REGISTRO` —si lo tiene— convive |
| **Entidad JPA `ClientSeller`** | No hay agregado ni comportamiento: filas que se insertan y se proyectan. Es el criterio de `user_supervisors` y `user_brokers` |
| **Que `registerSale` siga devolviendo solo el código** y `first_movement_id` se rellene buscando la venta por persona | Una clave foránea rellenada por conjetura. Devolver el identificador cuesta un `record` |
| **Un solo endpoint `/users/{id}/sellers` que admita `me`** | `me` como identificador es un caso especial en una ruta tipada como `uuid`; el proyecto ya tiene `/users/me` y `/users/me/team/broker-accounts` como rutas propias |
| **Publicar el `id` del vendedor** | El cliente no tiene dónde usarlo y el hotlink no lo da (`RN-PM-022`) |
| **`404` para quien no trae `users:read`**, como `RF-SP-055` | Aquí no hay actor autorizado por estructura al que proteger de un oráculo; el `403` es el modelo general |

## 10. Riesgos

| # | Riesgo | Mitigación |
|---|---|---|
| 1 | **Una lectura se queda leyendo `user_supervisors`** y después de `V20` devuelve de menos sin que nada falle | `CA-SP-693`, `CA-SP-694` y `CA-SP-695` prueban las tres lecturas con un cliente que **solo** existe en `client_sellers`; `DevelopmentSeedIT` comprueba que la semilla no deja clientes en `user_supervisors` |
| 2 | **La migración borra una fila de mando** por clasificar mal a una persona con los dos tipos de rol | El predicado exige `CONSUMIDOR` **y no** `VENDEDOR`; la prueba de esquema siembra ese caso y comprueba que su fila sigue |
| 3 | **El vínculo se inserta después de la venta** y `sellerOf` no lo encuentra: la venta se atribuye al propio cliente (`CA-MV-017`) | El orden queda fijado en §7 y `CA-SP-697` lo prueba mirando la línea de la venta del registro |
| 4 | **El frontend usaba `supervisor` de `GET /users/me` para enseñar «mi agente»** a un cliente, y deja de venir | Era una desviación de `CA-SP-441`, no un contrato. Se avisa a las sesiones de frontend con la ruta nueva antes de integrar |
| 5 | Se numera una migración que otra rama ya tomó | `V20` reservada por mensaje a `backend-ff` y `backend-02` el 18-09-2026; `AC` sigue en `V21` |

## 11. Estrategia de prueba

- **Esquema** (`ClientSellersSchemaIT`): el `CHECK` de origen, el `CHECK` de no-a-sí-mismo, el índice único parcial (**dos `REGISTRO` se rechazan; `REGISTRO` + `HOTLINK` conviven**), y la mudanza sobre datos sembrados en la prueba: cliente con vigente y cerrada, cliente ascendido a vendedor, vendedor sin cambios.
- **Integración del endpoint** (`ClientSellersIT`): `CA-SP-686` a `CA-SP-691`, con una fila `HOTLINK` insertada a mano para probar el orden y `principal` en falso.
- **Las lecturas que cambian de tabla**: `BrokerAccountsIT` (`CA-SP-693`, y el equipo de `RF-SP-056` con un cliente), `NetworkIndicatorsIT` (`CA-SP-694`), `RegisterSaleIT` (`CA-SP-695`), `ListBrokerAccountsIT` (`CA-SP-648` precisado).
- **Las que se invierten**: `SelfRegistrationIT` (`CA-SP-697` a `CA-SP-699`), `CommercialTeamIT` (`CA-SP-696`).
- **Semilla**: `DevelopmentSeedIT` invierte «los tres clientes cuelgan en `user_supervisors`» por «los tres tienen `REGISTRO` y ninguno está en `user_supervisors`».
- **Rutas**: `EndpointPermissionsIT` con `/me/sellers` como autenticada sin permiso y su motivo.
