# El contrato de la API

| Campo | Valor |
|---|---|
| Proyecto | NEXUS — Renovación de plataforma |
| Empresa | FACTECH GROUP SAS |
| Documento | `api/index.md` |
| Versión | 1.6.0 |
| Estado | Publicado |
| Responsable técnico | Bonilla Diaz William Steven |
| Fecha de creación | 25-08-2026 |
| Última actualización | 10-09-2026 |
| Documento superior | `architecture.md` v0.14.0 |

---

## 1. Dónde está

La especificación OpenAPI de **todas** las rutas se publica como archivo versionado, en los dos formatos, y **no exige credenciales ni que haya nada en ejecución**:

| Formato | URL |
|---|---|
| JSON | <https://nexuspro-dev.github.io/backend/api/openapi.json> |
| YAML | <https://nexuspro-dev.github.io/backend/api/openapi.yaml> |

En el repositorio son `docs/api/openapi.json` y `docs/api/openapi.yaml`.

El Art. VIII.7 hace de esta especificación el **único** contrato entre backend y frontend: no deben acordarse comportamientos por fuera de ella. Si algo que el frontend necesita no está aquí, la respuesta correcta no es escribirlo a mano en el cliente sino **abrir un issue en este repositorio**.

## 2. Por qué un archivo y no una instancia en ejecución

Porque atar la construcción del frontend a que haya un backend levantado hace que un Pull Request suyo deje de ser reproducible, y en un entorno sin red no se pueda construir. Es la decisión de [`ADR-001`](../architecture/ADR-001-publicacion-del-contrato-openapi.md).

**`/v3/api-docs` y Swagger UI siguen cerrados en los entornos desplegados** (`EXPOSE_API_DOCS` en `false`). Que el contrato sea legible aquí no autoriza a dejar Swagger abierto donde hay datos reales, donde además invita a probar contra ellos. En **local** sí están abiertos: `docker-compose.yml` fija la bandera en `true`, de modo que quien levante el entorno tiene `http://localhost:8080/swagger-ui.html`.

## 3. Cómo se mantiene al día

No a mano. `OpenApiContractIT` **reescribe** los dos archivos durante `mvn verify`, y CI falla si lo comprometido no coincide con lo generado (Art. VIII.6). Eso convierte en pipeline en rojo lo que de otro modo sería un contrato que envejece sin que nadie lo note.

En la práctica: **quien cambia un endpoint ejecuta `mvn verify` y commitea `docs/api/`**. No hay un paso manual de publicación.

## 4. Lo que el cliente necesita saber

**Base.** Todas las rutas cuelgan de `/api/v1`. La URL del servidor depende del entorno y **no** viaja en el contrato: se configura en el cliente.

**Autenticación.** `Bearer` con JWT en la cabecera `Authorization`. El contrato lo declara como esquema global, de modo que **toda operación lo exige salvo tres**: `POST /auth/login`, `POST /auth/refresh` y `POST /auth/logout`, que no pueden pedir el token que aún no se tiene —o que ya no se tiene—.

**El token de acceso dura quince minutos** y no es revocable: solo expira. El refresco rota el token de refresco en cada uso, así que el cliente debe **guardar el nuevo y descartar el anterior**; reutilizar uno ya usado se interpreta como robo y revoca la familia entera.

**CORS.** El navegador solo puede leer la respuesta desde un origen autorizado, y la lista se declara por entorno en `CORS_ALLOWED_ORIGINS` (`security.md` §6.1). En un despliegue nuevo **está vacía**, que es el valor seguro: si el frontend no puede llamar, es lo primero que hay que mirar. La respuesta expone `Location` y `X-Correlation-Id`.

**Errores.** Formato RFC 9457 uniforme (`architecture.md` §7.3), con `correlationId` siempre presente: es el identificador que conviene mostrar al usuario cuando algo falla, porque es con el que el equipo lo localiza.

**Paginación.** Los listados devuelven `content`, `page`, `size`, `totalElements`, `totalPages` y **`totalIsExact`**. Ese último importa: en los registros de auditoría el total es exacto **hasta un techo** y aproximado por encima, y `totalPages` es entonces una cota inferior — pedir una página más allá sigue funcionando (`architecture.md` §7.4).

**Límite de tasa.** Los endpoints **públicos** están acotados y responden `429` con `Retry-After` y `retryAfterSeconds`: los de sesión y recuperación, el hotlink, el registro por enlace y los **cuatro catálogos que se leen sin token** —países, tipos de documento, brokers y **métodos de pago**—, estos últimos a 120 peticiones por minuto y por origen, **con un cubo por catálogo**, de modo que agotar uno no deja sin los otros al mismo formulario. El cliente debe **descontar** esos segundos y no calcular la espera con su propio reloj.

## 5. Generar el cliente

Con cualquier generador que acepte una URL. Por ejemplo:

```bash
npx @hey-api/openapi-ts \
  -i https://nexuspro-dev.github.io/backend/api/openapi.yaml \
  -o src/api
```

Se recomienda **fijar la generación en la construcción** del frontend y no commitear el cliente generado editado a mano: en cuanto se toca, deja de ser un reflejo del contrato y vuelve el acuerdo por fuera que el Art. VIII.7 prohíbe.

## 6. Lo que el contrato todavía no dice

**Las tres advertencias que esta sección traía desde el 25-08-2026 ya no valen, y se retiran en lugar de dejarse como fósil**: el `423` de cuenta bloqueada está documentado en `POST /auth/login` y `POST /auth/password`; el `429` lo está en las dos rutas de recuperación; y la recuperación de contraseña **existe** desde que se cerró **D-23** el 26-08-2026 —`POST /auth/password-recovery`, su `/confirmation` y `POST /auth/password`—.

Lo que sigue siendo cierto, y conviene saberlo antes de tropezar:

- **El `429` no está documentado en `POST /auth/login` ni en `/auth/refresh`**, aunque el filtro también las acota. Lo produce un filtro, que no pasa por las anotaciones del controlador: el comportamiento existe y es el descrito arriba, y la anotación falta en esas dos.
- **Los `operationId` llevan sufijos numéricos** —`registrar_3`, `listar_4`, `corregir_2`— en 24 de las 65 operaciones. Los genera springdoc al chocar dos métodos con el mismo nombre en controladores distintos, y **no son estables**: añadir otro `registrar` en cualquier módulo puede reasignar el número. `openapi-typescript` indexa por ruta y método, de modo que hoy no afecta al cliente generado; **cualquier generador que use el `operationId` como nombre de función sí se rompería en silencio**. Si el frontend cambia de generador, esto hay que resolverlo antes.
- **Los nombres de esquema tienen el mismo riesgo**, y por eso los registros anidados se declaran a mano: uno se publicaría como `Line`, `Party` o `Money` —genéricos en un espacio de nombres plano y compartido por todos los módulos—, de modo que los de `MV` se publican como `SaleLineRequest`, `SaleParty` y `SaleCurrency`, y los del registro por enlace como **`RegistrationMovement`** y **`RegistrationBrokerAccount`** (09-09-2026). **Ese último caso no era hipotético**: el bloque del movimiento se llama `Movement` en el código y `Movement` es **el nombre del agregado de `MV`**, de modo que el día que `MV` publicara el suyo habrían salido `Movement` y `Movement_1` sin garantía de cuál es cuál. **Quedan sin resolver los que ya existían**: `Item`, `Neighbor`, `Person`, `RoleRef`, `UserRef` y el `Offered` de `RF-PM-007`. No es urgente y no es gratis: renombrarlos cambia tipos que el frontend ya usa.

## 7. Control de cambios

| Versión | Fecha | Cambio | Responsable |
|---|---|---|---|
| 1.0.0 | 25-08-2026 | Se publica esta página. El contrato ya se versionaba desde `ADR-001`, pero **nada decía dónde encontrarlo ni cómo consumirlo**, de modo que el frontend tenía el archivo y no la instrucción. Se añade además el **YAML**, que es el formato que asumen por defecto los generadores de cliente: publicar solo el JSON obligaba a cada consumidor a convertirlo, y una conversión hecha en el lado del cliente es una copia del contrato que envejece por su cuenta. | Responsable técnico |
| 1.1.0 | 04-09-2026 | **Se retiran las tres advertencias de §6, que llevaban desde el 25-08-2026 diciendo lo contrario de lo que ocurre.** El `423` está documentado, el `429` también en las dos rutas de recuperación, y **la recuperación de contraseña existe** desde que D-23 se cerró el 26-08-2026 — el frontend leía aquí que no había endpoint mientras el backend lo publicaba. Se retiran en lugar de dejarse tachadas: una advertencia falsa cuesta más que ninguna. **En su sitio quedan dos riesgos reales del contrato generado**, que hasta hoy no estaban escritos en ninguna parte. El primero: **24 de los 65 `operationId` llevan sufijo numérico** —`registrar_3`, `listar_4`— porque springdoc los genera al chocar dos métodos con el mismo nombre en controladores distintos, y **el número no es estable**; hoy no afecta porque `openapi-typescript` indexa por ruta y método, pero cualquier generador que use el `operationId` como nombre de función se rompería **en silencio** al añadirse otro `registrar`. El segundo: **los nombres de esquema tienen el mismo problema**, porque el espacio de nombres es plano y compartido por todos los módulos. Por eso los tres registros anidados que estrena `MV` se publican con nombre declarado a mano —`SaleLineRequest`, `SaleParty` y `SaleCurrency` en lugar de `Line`, `Party` y `Money`—: mientras nadie los consume es gratis, y después es un cambio incompatible. Queda dicho lo que **no** se tocó y por qué: `Item`, `Neighbor`, `Person`, `RoleRef`, `UserRef` y `Offered` ya existen y renombrarlos cambiaría tipos que el frontend usa. | Responsable técnico |
| 1.2.0 | 09-09-2026 | **El registro por enlace cambia de forma, y es un cambio incompatible para el frontend.** `POST /api/v1/auth/registration` **pierde `product` y `referrer`** del primer nivel del cuerpo: los dos se mudan al bloque **`movement`**, que es obligatorio y lleva `productId`, `paymentMethodId`, `sellerUsername` y `movementTypeCode`. Tres cosas que hay que saber antes de tocar el formulario. **Una: el producto va ahora por IDENTIFICADOR y no por código** —`movement.productId` es un `uuid`—, de modo que la pantalla tiene que resolver el producto del enlace antes de enviar; el enlace repartido sigue llevando el código. **Dos: `movement.paymentMethodId` es condicional y su regla no está en el esquema** —Bean Validation no puede expresarla—: se **omite** cuando el producto vale cero (esa venta se anota con el pago gratuito, que el catálogo de `GET /payment-methods` **no devuelve** y nadie puede elegir) y es **obligatorio** cuando tiene importe; enviarlo cuando sobra devuelve `409 RN-MV-022`. **Tres: la respuesta gana `sale`** —el código de la venta que el alta anota— y `status` **ya no es siempre `FTD_PENDIENTE`**: con un producto de pago la cuenta nace `ACTIVO`. Los esquemas nuevos se publican como **`RegistrationMovement`** y **`RegistrationBrokerAccount`**, con nombre declarado a mano por lo que dice §6 — `Movement` a secas habría chocado con el agregado de `MV`. | Responsable técnico |
| 1.3.0 | 09-09-2026 | **`GET /api/v1/payment-methods` pasa a ser PÚBLICO**, y para el frontend eso son dos cosas. **Una: se puede llamar sin token**, que es lo que le faltaba al formulario de registro por enlace — `movement.paymentMethodId` se elige antes de que exista la cuenta, igual que el país, el tipo de documento y el broker. La operación **deja de declarar `401`** y declara `429`: está acotada a **120 peticiones por minuto y por origen**, con cubo propio, como los demás catálogos públicos. **Dos: la respuesta no cambia ni un campo.** Con token y sin él sale exactamente lo mismo —los métodos **activos y de visibilidad `PUBLICO`**—, y **no hay parámetro que amplíe eso**: ni `includeInactive` ni `includeHidden`. En particular **`GRATIS` no aparece nunca** (`RN-MV-023`), de modo que quien depure una venta de importe cero verá un `paymentMethodId` que **este catálogo no resuelve**; es deliberado y no una carencia. El campo de visibilidad **tampoco se publica**: si todo lo que sale es `PUBLICO`, declararlo sugeriría que puede salir otra cosa. | Responsable técnico |
| 1.4.0 | 10-09-2026 | **`GET /api/v1/users/{id}/team` cambia de forma, y es un cambio INCOMPATIBLE.** **Uno: `roleCode` desaparece** de las cuatro personas que la respuesta puede llevar —la consultada, el superior, el superior anterior y cada miembro del equipo— y en su lugar viaja **`roles`**, la lista completa de los roles de esa persona, cada uno con `id`, `code` y `name`, ordenada por código y **presente aunque vaya vacía**. Es el mismo objeto `RoleRef` que `GET /api/v1/users` ya devuelve en cada fila, de modo que el componente que pinta roles vale para los dos. **El campo viejo mentía**: devolvía un solo rol y solo si era de la fuerza comercial, así que **un cliente de la cartera llegaba con el rol en nulo** y era indistinguible de un vendedor sin rol. Quien pintaba `roleCode` debe pasar a pintar `roles[0].code`, o mejor, todos. **Dos: entra el parámetro `roles`**, opcional, con **códigos** de rol y varios a la vez —`?roles=AGENTE,CLIENTE` o repitiendo el parámetro—; entra en el equipo quien porte **alguno** de ellos, `team.totalElements` **cuenta lo filtrado** y un código que no existe devuelve el equipo vacío con `200`, no un error. **El filtro NO toca al superior ni a la persona consultada**: `supervisor` sigue estando ausente **solo** cuando la persona es la cúspide, que es la distinción que el frontend usa para pintar «no depende de nadie». **`PATCH /api/v1/users/{id}/supervisor` comparte el cuerpo de respuesta y cambia igual**, sin cambiar de comportamiento. **`GET /api/v1/users/me` no cambia**: su `supervisor.roleCode` sigue donde estaba. | Responsable técnico |
| 1.5.0 | 10-09-2026 | **Nacen dos endpoints de cuentas de broker, y el frontend gana con ellos un campo nuevo y una regla de acceso que no se parece a ninguna anterior.** `GET /api/v1/users/{id}/broker-accounts` devuelve las cuentas de **una** persona —sin paginar— y `GET /api/v1/users/me/team/broker-accounts` devuelve, **paginadas**, las de **todo el equipo directo del actor**, cada fila con su titular y con filtro por `status` y por `brokerId`. **El campo nuevo es `status`**: `REGISTER` o `FIRST_DEPOSIT` (`RN-SP-045`), y **hoy todas las cuentas están en `REGISTER`** porque quien las mueve es el webhook del broker, que todavía no existe — no es un fallo de la consulta, es el estado real. **Los dos valores van en inglés** y son los únicos así en toda la API: son el vocabulario del broker. **La regla de acceso es la novedad de verdad**: el listado del equipo **no exige ningún permiso**, solo estar autenticado, porque el conjunto de datos lo determina el sistema a partir de quién pregunta; y la consulta por persona la abre **ser su superior comercial vigente** o traer **`broker-accounts:read`**. **Quien no es ninguna de las dos cosas recibe `404`, no `403`**, y el cuerpo es **indistinguible** del de una persona inexistente: es deliberado, para que nadie pueda recorrer identificadores y averiguar cuáles son personas reales. Al integrar, **no se debe deducir de ese `404` que la persona no existe**. Dos cosas más que conviene saber de la forma: **`brokerUsername` viaja en nulo y el campo está presente** —su nulo significa «el broker aún no lo ha confirmado» (`RN-SP-040`), no «sin nombre»—, y el listado del equipo es **de cuentas y no de personas**: quien no declaró ninguna **no aparece**, y quien declaró dos aparece **dos veces**. | Responsable técnico |
| 1.6.0 | 10-09-2026 | **Nace `GET /api/v1/broker-accounts`, el listado de administración**, y con él llegan **dos correcciones de contrato que afectaban a lo publicado ayer**. El endpoint devuelve **todas** las cuentas del sistema, paginadas, y admite acotarlas por `supervisorId`, `userId`, `status`, `brokerId`, `search` y el rango `from`/`to`. Exige **`broker-accounts:read`** y responde **`403`** sin él —no el `404` de `GET /api/v1/users/{id}/broker-accounts`, que oculta la existencia a propósito porque allí el actor es un vendedor cualquiera—. **Lo que hay que saber para integrarlo**: `supervisorId` devuelve **la red ENTERA en profundidad** y no un nivel, al revés que `GET /api/v1/users/{id}/team` y `GET /api/v1/users/me/team/broker-accounts`; **no incluye a la propia raíz** —sus cuentas se piden con `userId`, y los dos filtros se combinan—; un identificador inexistente devuelve **página vacía sin error**, mientras que un `status` inválido o un `from` posterior a `to` son **`400`**; y `from`/`to` son **instantes con zona**, no fechas sueltas, con el rango **semiabierto** —incluye `from`, excluye `to`—, igual que en los listados de auditoría. **La fila es el mismo esquema `TeamBrokerAccountItem`** que devuelve el listado del equipo: una sola forma para las dos pantallas. **Las dos correcciones**: `GET /api/v1/users/me/team/broker-accounts` publicaba `PageResponse` **sin parametrizar** —`content` sin tipo, de modo que el cliente generado no veía la fila— y ahora publica `PageResponseTeamBrokerAccountItem`; y **`GET /api/v1/brokers` publicaba un único parámetro llamado `filtros`** en vez de `includeInactive`, defecto que arrastraba desde el 08-09-2026. Quien haya generado el cliente antes de hoy debe regenerarlo. | Responsable técnico |
