# PLAN — `RF-SP-055` Consultar las cuentas de broker de una persona

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-055` |
| Especificación | [`spec.md`](spec.md), aprobada el 10-09-2026 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 10-09-2026 |
| Enmendado | 21-09-2026 — exige **`broker-accounts:read-team-member`** (`RF-SP-062`, `RN-SEG-015`: autenticarse no autoriza nada); lo siembra `V31`, que abre la ruta; `RN-SP-046` sigue decidiendo el alcance |

---

!!! note "Enmienda de Art. I.7 — 21-09-2026, `RF-SP-062`"

    Esta operación exige **`broker-accounts:read-team-member`** desde el 21-09-2026, por `RF-SP-062` —**autenticarse no autoriza nada**, `RN-SEG-015` ([`security.md` §4.3](../../../security.md#43-reglas-de-negocio))—, por decisión del responsable del proyecto: «cada endpoint debe tener su propio permiso, ya que uso esto para saber qué vista o consulta mostrar en el front; no basta con solo tener el token». Hasta entonces se atendía con solo el token, y las líneas que abajo dicen «sin permiso» o «autenticado a secas» hablan de esa decisión original y se conservan como historia: el alcance sobre uno mismo sigue siendo exactamente el mismo, lo que cambia es que ahora tiene nombre. `V31` siembra el permiso y lo da a todo rol por su tipo, a `CONSUMIDOR` no. **Abre la ruta y no decide el alcance**: la autorización por estructura del servicio se conserva entera debajo del permiso.



## 1. Enfoque

**Dos cosas que se construyen juntas porque una sin la otra no sirve**: la columna `status` de `user_brokers` (`RN-SP-045`) y la primera lectura de esa tabla. La columna sola sería un campo que nadie mira; la lectura sola devolvería un número de cuenta y no respondería la pregunta que se quiere hacer.

**El endpoint cuelga de `/users/{id}` y no de un recurso propio.** Las cuentas de broker **no existen sin su titular** —`user_brokers.user_id` es `NOT NULL` y no hay ninguna consulta que las mire sin saber de quién son—, de modo que un `/api/v1/broker-accounts` de primer nivel obligaría a llevar el titular en un parámetro obligatorio, que es la misma jerarquía escrita peor. Es la forma que ya tiene `GET /users/{id}/team`.

**`user_brokers` gana por fin su entidad JPA.** `RF-SP-052 · §3` decidió **no** crearla —«no hay caso de uso que la lea ni que la escriba, y una entidad sin uso es código que hay que mantener»— y ese argumento **caduca hoy**: hay dos casos de uso que la leen. Se crea aquí, con la forma que estos dos requerimientos necesitan y no la que se hubiera adivinado hace dos días.

## 2. Cambios de esquema

Dos migraciones:

| Migración | Qué hace |
|---|---|
| `V80__user_brokers_status.sql` | La columna `status` con su `CHECK`, su `DEFAULT` y el respaldo de las filas existentes |
| `V81__seed_broker_accounts_permission.sql` | El permiso `broker-accounts:read` y su asociación a `SUPERADMIN` y `ADMIN` |

### 2.1 La columna nace con `DEFAULT` y `NOT NULL`, y las dos cosas importan

```sql
ALTER TABLE user_brokers
    ADD COLUMN status varchar(20) NOT NULL DEFAULT 'REGISTER';

ALTER TABLE user_brokers
    ADD CONSTRAINT ck_user_brokers_status
    CHECK (status IN ('REGISTER', 'FIRST_DEPOSIT'));
```

**El `DEFAULT` no es comodidad: es lo que hace que las filas ya escritas queden en un estado cierto.** `RF-SP-045` lleva declarando cuentas desde el 09-09-2026 y ninguna de ellas tiene depósito confirmado, de modo que `REGISTER` **es su valor correcto** y no un relleno. Una columna anulable dejaría un tercer estado —«no se sabe»— que `RN-SP-045` no admite y que nadie sabría interpretar.

**El `CHECK` va en el motor y no solo en el enumerado de Java**, con el criterio de `ck_users_status` (Art. V.6): el conjunto de valores es una regla de negocio, y el día que alguien escriba esta tabla desde una migración de datos, un `register` en minúscula entraría sin que nada fallara.

**Se numeró comprobando el máximo aplicado.** `V62` y `V67` se planificaron y las tomó otra rama el mismo día, dos veces en dos días; aquí se miró antes, y el máximo era `V79`.

### 2.2 Ningún índice nuevo

La consulta filtra por `user_brokers.user_id`, que ya tiene `ix_user_brokers_persona` (`V74`), y la de `RF-SP-056` entra por `user_supervisors.supervisor_id` vigente, que ya tiene `ix_user_supervisors_supervisor_vigente` (`V28`) — el índice que `V21` declaró **no crear hasta que alguien lo necesitara** y que `RF-SP-042` acabó necesitando. **No se declara un índice sobre `status`**: con dos valores, la selectividad es nula y el motor recorrerá la tabla igualmente.

## 3. Componentes afectados

| Capa | Elemento | Módulo |
|---|---|---|
| `brokers/domain/models` | `UserBrokerStatus` — el enumerado de `RN-SP-045` | `SP` |
| `brokers/domain/repository` | `BrokerAccountQueryRepository` (puerto) y su implementación con `EntityManager` | `SP` |
| `brokers/application` | `BrokerAccountItem`, `BrokerAccountsResponse` | `SP` |
| `brokers/domain/service` | `GetBrokerAccountsService` | `SP` |
| `users/interfaces` | `UserController` — `GET /api/v1/users/{id}/broker-accounts` | `SP` |
| `brokers/domain/repository` | `JpaBrokerAccountRegistrar` — **sin cambios**: el `INSERT` omite `status` y el `DEFAULT` lo pone | `SP` |

**El controlador es `UserController` y no uno nuevo**, porque la ruta cuelga de `/users/{id}` y partirla en dos controladores repartiría el mismo prefijo entre dos ficheros. El servicio y el repositorio, en cambio, viven en el submódulo de **brokers**: el dato es suyo.

**Se lee con `EntityManager` y consulta nativa**, como el resto del submódulo (`JpaBrokerQueryRepository`, `JpaBrokerAccountRegistrar`): la proyección cruza `user_brokers` con `brokers` y no hay agregado que cargar.

## 4. Contrato de API

`GET /api/v1/users/{id}/broker-accounts`

```json
{
  "content": [
    {
      "id": "018f…",
      "broker": { "id": "018e…", "name": "EXNOVA" },
      "accountId": "70123456",
      "brokerUsername": null,
      "status": "REGISTER",
      "declaredAt": "2026-09-10T14:03:11Z"
    }
  ]
}
```

- **Envuelto en `content`** y no como arreglo desnudo, como los cuatro catálogos: deja sitio a paginar el día que haga falta sin romper a nadie.
- **`accountId` se llama igual que en la entrada de `RF-SP-045`**, donde el titular lo declara. Publicarlo como `externalId` —el nombre de la columna— obligaría al frontend a saber que son el mismo dato con dos nombres.
- **`brokerUsername` se emite aunque sea nulo**, por `RN-SP-040`. Omitirlo confundiría «no confirmado» con «confirmado sin nombre».
- **`declaredAt` es `created_at`**, y se publica con ese nombre porque lo que significa es cuándo la declaró la persona. `updatedAt` **no** se publica: hoy nadie la actualiza, y el día que el webhook lo haga, publicarlo será una decisión de `RF-SP-054`.
- **Sin `userId` en cada fila**: la ruta ya lo lleva. El listado que sí lo necesita es `RF-SP-056`, donde cada fila es de una persona distinta.

## 5. Autorización

**No hay `@PreAuthorize`**, y esa ausencia es la decisión del plan. La autorización de esta ruta **no es una función del actor sino del par (actor, persona consultada)**, y `@PreAuthorize` no puede expresar eso sin meter una consulta a la base dentro de una expresión SpEL — donde no se prueba, no se lee y no se depura.

Se resuelve **en el servicio**, en dos pasos y en este orden:

1. **¿Trae `broker-accounts:read`?** Si sí, pasa. Se comprueba con la autoridad de Spring Security, como la comprobación de `products:read` que ya hace `RF-PM-007`.
2. **¿Es el superior vigente de esa persona?** `usuarios.findActiveSupervisor(id)` y comparar con el actor. La fila **cerrada** no cuenta (`FA-004`).

Si ninguna, `ResourceNotFoundException` — **el mismo `404` que si la persona no existiera**, y por eso la comprobación de existencia y la de autorización **acaban en la misma excepción**: dos excepciones distintas acabarían dando dos cuerpos distintos y el oráculo volvería por la puerta de atrás.

**La ruta entra en `EndpointPermissionsIT` con su motivo escrito al lado**: es autenticada, no declara permiso en la anotación y **no es un olvido**.

## 6. Auditoría

**No audita.** Es una lectura, como `RF-SP-042`.

## 7. Transaccionalidad

`@Transactional(readOnly = true)`. La persona, el superior y las cuentas se leen **de la misma foto**: leídos por separado, una reasignación simultánea podría dejar pasar a quien acaba de dejar de ser superior.

## 8. Alternativas consideradas

| Alternativa | Por qué se descartó |
|---|---|
| **`403` para quien no es el superior** | Convierte la ruta en un oráculo de identificadores para cualquier vendedor. `security.md` §5 admite la excepción con justificación expresa, y §10 de la spec la da |
| **Un recurso propio `/api/v1/broker-accounts?userId=…`** | Las cuentas no existen sin titular. Sería la misma jerarquía con el padre metido en un parámetro obligatorio |
| **`@PreAuthorize` con una expresión que consulte la estructura** | La autorización depende de la pareja actor–consultado. En SpEL no se prueba, no se lee y no se depura |
| **Resolver el alcance con `users:read`** | Mezcla dos lecturas distintas bajo un permiso que ya tiene medio sistema. Un permiso propio se puede conceder y retirar solo |
| **Abrirla también al titular** | Decisión del responsable del proyecto (10-09-2026): la lectura se definió sobre el equipo. Su vía natural es `RF-SP-039` |
| **`status` como columna anulable sin `DEFAULT`** | Deja un tercer estado —«no se sabe»— que `RN-SP-045` no admite, y obliga a cada lector a decidir qué hacer con él |

## 9. Riesgos

| # | Riesgo | Mitigación |
|---|---|---|
| 1 | **El `404` se relaja a `403`** en una refactorización, sin que nadie note que era deliberado | `CA-SP-634` lo prueba **comparando los dos cuerpos**: el de una persona ajena y el de una inexistente tienen que ser indistinguibles |
| 2 | **El historial de `user_supervisors` concede lectura** por olvidar el `ended_at IS NULL` | `CA-SP-635` lo prueba con un superior **cerrado**. Es el error que el índice parcial de `V28` ya anticipa |
| 3 | La excepción a **D-22** se cita como precedente para abrir otras lecturas por estructura | Queda acotada por escrito en `RN-SP-046`, en `security.md` §5 y en §10 de la spec: **ninguna otra puede añadirse citándola** |
| 4 | Se numera una migración que otra rama ya tomó | Se comprobó el máximo aplicado (`V79`) antes de escribir |

## 10. Estrategia de prueba

- **Integración del endpoint**: el superior vigente ve; el que trae el permiso ve; el ajeno recibe `404`; el ex-superior recibe `404`; el titular sin permiso recibe `404`; sin cuentas, `200` vacío.
- **El cuerpo del `404`**: idéntico al de una persona inexistente.
- **Esquema**: el `CHECK` rechaza un valor fuera del par, y una fila insertada sin `status` queda en `REGISTER`.
- **Siembra**: el permiso existe con identificador literal y lo tienen los dos roles; las listas cerradas del catálogo cuentan **cuarenta y cinco**.
