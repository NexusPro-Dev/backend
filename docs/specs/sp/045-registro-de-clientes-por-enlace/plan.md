# PLAN — `RF-SP-045` Registro de clientes por enlace

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-045` |
| Especificación | [`spec.md`](spec.md) |
| Estado | **Aprobado** |
| Enmendado el | 09-09-2026 — **la cuenta de broker** (`RN-SP-042`) y **los tres catálogos públicos**, que cierran el bloqueo 6 |
| Enmendado el | 09-09-2026 — **el movimiento del alta** (`RN-SP-043`) y **el camino de pago** (`RN-SP-044`): dos componentes nuevos, la segunda inversión de dirección de la tripleta y un sexto hecho en la transacción |
| Enmendado el | 09-09-2026 — **mueren `product` y `referrer` del primer nivel**: el enlace viaja entero dentro de `movement`, y con el duplicado se van `VAL-016` y la mitad de `EX-010` |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 01-09-2026 |
| Reabierto el | 07-09-2026 — `RN-SP-034`: el cuerpo público exige `countryCode`, ver §4 (Art. I.7) |
| Reabierto el | 08-09-2026 — `RN-SP-035` y `RN-SP-037`: el cuerpo exige documento y teléfono, ver §4 (Art. I.7) |

| Reabierto el | 10-09-2026 — el cuerpo **NO admite** `companyPhone`, que nace ese día para el resto del sistema: enviarlo es `400` por propiedad desconocida, ver §4 (Art. I.7) |
---

## 1. Enfoque

Un endpoint **público que escribe**, que es lo que este sistema no tenía. Los seis públicos de hoy o leen, o consumen una credencial que el propio sistema emitió; este crea una persona, le concede un rol, le asigna una membresía y escribe una atribución, todo a petición de alguien que no es nadie todavía.

De ahí salen las tres decisiones del plan: **una sola transacción**, **límite de tasa desde el primer día**, y **un estado de cuenta que autentica sin operar** — el primero del sistema.

## 2. Cambios de esquema

**Una sola migración**, y en la primera versión de este plan eran dos.

**`V49` — el catálogo de estados.** `ck_users_status` pasa de `('ACTIVO','INACTIVO','BLOQUEADO','PENDIENTE')` a `('ACTIVO','INACTIVO','BLOQUEADO','FTD_PENDIENTE')`.

!!! success "El renombrado sale gratis hoy, y no lo será dentro de un mes"

    `PENDIENTE` está declarado **y sin usar a propósito** desde `V18`, que lo dejó escrito: «queda declarado y sin uso a propósito […] para que el día que un requerimiento lo estrene no haga falta alterar el `CHECK` de una tabla en uso». Ninguna fila lo lleva, ninguna semilla lo produce y ningún caso de uso lo escribe — `ChangeUserStatusService` lo **rechaza** explícitamente.

    De modo que esto es sustituir un valor del dominio, **sin migración de datos**. La misma operación con una sola fila en `PENDIENTE` habría exigido decidir a dónde va esa cuenta.

**Y la atribución no trae ninguna**, que es lo que cambia respecto a la primera versión de este plan. El cliente cuelga de su vendedor en **`user_supervisors`**, y esa tabla ya tiene exactamente la forma que hace falta: `user_id`, `supervisor_id`, `started_at`, `ended_at` y su unicidad parcial del vigente. **No hay columna nueva, no hay índice nuevo y no hay `V50`.**

!!! warning "Revertido el 18-09-2026: el cliente sale de `user_supervisors` y la atribución sí tiene tabla"

    Por decisión del responsable del proyecto (`RN-SP-028` revertida, `RF-SP-059`). Lo que este plan celebró como «lo que no hay que escribir» **se escribió diecisiete días después**, y con motivo: la tabla de mando mezclaba equipo y cartera en cada consulta, `RN-SP-022` hacía irretirable a quien hubiera registrado a alguien, y la rama de consumidor de `RN-SP-020` nunca llegó a construirse. La relación vive en **`client_sellers`** (`requirements/sp.md` §10.19), la crea `V20` de `RF-SP-059`, y este caso de uso escribe allí su fila `REGISTRO` **antes** de la venta y la completa con el identificador de esa venta después (`RF-SP-059` plan §7). Los tres párrafos que siguen se conservan como historia de por qué se hizo al revés.

!!! success "Lo mejor de la decisión del responsable es lo que NO hay que escribir"

    Se había propuesto `client_referrals`, con siete columnas, cuatro restricciones y un índice — y con una copia del razonamiento de `RN-SP-021` sobre por qué la unicidad es parcial. Reutilizar `user_supervisors` **borra esa migración entera** y hereda lo ya resuelto: un superior vigente, el historial y la protección de `RN-SP-022`.

    Lo único que hay que escribir es la **enmienda de una regla**, `RN-SP-020`, que es donde estaba el obstáculo real: exige que el superior porte el rol padre inmediato del rol vendedor del subordinado, y un cliente no tiene ninguno. Gana su rama de consumidor —basta con que el superior porte **algún** rol `VENDEDOR`— y con ella la tabla admite las dos clases de fila.

**Lo que la relación NO guarda es el producto**, y esa ausencia es deliberada. `user_supervisors` dice **quién trajo a quién**; con qué producto entró el cliente pertenece al hecho comisionable —el depósito, cuando exista— por el mismo criterio con el que `requirements/pm.md` §1.4 exige que cada compra guarde su propio importe y su propia vigencia en lugar de leerlos del catálogo. Añadir `product_id` a `user_supervisors` habría metido una columna de producto en la tabla de la jerarquía comercial, que es justo la mezcla que unificar pretendía evitar.

**Una fila de esta tabla pasa a significar dos cosas según quién sea el subordinado** —«reporta a» entre vendedores, «fue traído por» cuando es un cliente— y eso hay que escribirlo donde se lea: §10.7 de `requirements/sp.md` lo recoge.

## 3. Componentes afectados

| Capa | Componente | Responsabilidad |
|---|---|---|
| `modules/system/users/application` | `RegistrableProductLookup` | **Puerto nuevo, declarado en `SP`**: el producto por código o identificador, con su destino, su vigencia y su estado |
| `modules/products/domain/repository` | `PublishedProductCatalog` | **Lo implementa `PM`**, que es quien tiene el dato |
| `modules/system/users/application` | `SelfRegistrationRequest` | Los datos de la persona, sus cuentas de broker y el **bloque `movement`**, que desde el 09-09-2026 es también **de donde sale el enlace** — producto y vendedor |
| `modules/system/users/domain/service` | `RegisterClientByLinkService` | El caso de uso, en una transacción |
| `modules/system/users/domain/repository` | `UserRepository` | **Se reutiliza**: `assignSupervisor` ya existe para `RF-SP-041`, y colgar un cliente es la misma escritura |
| `modules/system/users/domain/security` | `CommercialStructure` | ~~Gana la rama de consumidor de `RN-SP-020`~~ — **nunca la ganó, y desde el 18-09-2026 no la necesita**: el cliente no cuelga de `user_supervisors` |
| `modules/system/users/interfaces` | `RegistrationController` | `POST /api/v1/auth/registration`, público |
| `modules/system/users/domain/models` | `UserStatus` | `PENDIENTE` → `FTD_PENDIENTE` |
| `modules/system/auth/domain/repository` | `AuthUser` | `puedeEntrar()` admite el estado nuevo |
| `shared/security` | `SecurityConfig` | Una ruta pública más |
| `modules/system/brokers/application` | `BrokerAccountRegistrar` | **Puerto nuevo**: declara la cuenta de broker de una persona. Lo implementa el submódulo de brokers, que es de quien es la tabla |
| `shared/security/ratelimit` | — | La política del endpoint nuevo |
| `modules/system/users/application` | `RegistrationSaleRegistrar` | **Puerto nuevo, declarado en `SP`**: la venta del alta. **Segunda inversión de dirección** de esta tripleta, por lo mismo que `RegistrableProductLookup` — `SP` es la raíz del grafo y no consume de nadie |
| `modules/movements/domain/service` | `PublishedRegistrationSaleRegistrar` | **Lo implementa `MV`**, y **delega en `RegisterSaleService`**: no reimplementa la venta. Verifica el tipo y el vendedor (`EX-010`) |
| `modules/movements/domain/service` | `RegisterSaleService` | Gana una entrada **de paquete** para la venta del alta, exenta de `RN-MV-008`. Todo lo demás es idéntico |
| `modules/system/users/domain/models` | `User` | `selfRegister` recibe el **estado inicial**: ya no es siempre el mismo (`RN-SP-044`) |
| `modules/system/memberships/application` | `MembershipCatalog` | **Se reutiliza**: `floor()` da la membresía del suelo, que es la que recibe el camino de pago |

!!! danger "`puedeEntrar()` es la línea más delicada de este requerimiento"

    Hoy es `!deleted && "ACTIVO".equals(status)`. **Es la primera vez que un estado distinto de `ACTIVO` puede autenticarse**, y el cambio toca el camino de inicio de sesión de todo el sistema.

    Se escribe como **lista explícita de los estados que autentican** y no como negación de los que no —`!INACTIVO && !BLOQUEADO`—, porque la forma negada hace que **todo estado futuro nazca autenticando**, que es exactamente el error que este proyecto no quiere cometer en el camino de acceso.

    Y alcanza a dos sitios, no a uno: `LoginService` lo consulta al entrar, y `SessionService.refresh` lo vuelve a consultar en cada rotación. Los dos leen el mismo método, que es lo que impide que diverjan.

## 4. Contrato de API

`POST /api/v1/auth/registration`, **público**, `201`.

```json
{
  "firstName": "Ana", "lastName": "Ruiz",
  "username": "ana.ruiz", "email": "ana@ejemplo.com",
  "password": "…",
  "countryCode": "COL",
  "documentType": "CC",
  "documentNumber": "1020304050",
  "phone": "+573001234567",
  "addressLine1": null, "addressLine2": null, "city": null,
  "brokerAccounts": [
    { "brokerId": "01a081f0-6000-7101-9c4f-5e7adb000001", "accountId": "12345678" },
    { "brokerId": "01a081f0-6000-7102-9c4f-5e7adb000002", "accountId": "87654321" }
  ],
  "movement": {
    "productId": "01a06f2c-1800-7001-9c4f-5e7ad8000001",
    "paymentMethodId": null,
    "sellerUsername": "agente.martinez",
    "movementTypeCode": "VENTA"
  }
}
```

**Cuelga de `/auth` y no de `/users`.** Las seis rutas públicas del sistema viven ahí y esta es la séptima; colgarla de `/users` la pondría al lado de `POST /api/v1/users`, que exige `users:create` — dos altas de persona bajo el mismo recurso, una abierta y otra no, es la clase de vecindad que produce el `@PreAuthorize` olvidado.

**`product` y `referrer` DESAPARECIERON del primer nivel** (09-09-2026): decían lo mismo que `movement.productId` y `movement.sellerUsername`, y dos campos para un dato son dos valores que pueden discrepar. El enlace viaja **entero dentro de `movement`**, y con el duplicado se fueron las dos comprobaciones que lo vigilaban (`VAL-016` y la mitad de `EX-010`). **El producto pasa a ir por identificador**, como `brokerId` y por el mismo argumento: el formulario tiene que leer el producto antes de pintarse, de modo que ya lo tiene. `findRegistrable` **sigue admitiendo código o identificador** —resuelto por forma— porque la capacidad es del puerto y no de este cuerpo. Es lo que ya hace el inicio de sesión con `identifier`, que acepta nombre de usuario o correo. Dos campos opcionales y excluyentes habrían obligado a validar que llega exactamente uno.

**`documentType` va por abreviación, como el país por código y el producto por el suyo**: los cuatro campos de referencia de este cuerpo evitan los UUID, porque es un formulario público al que no se le pide conocer identificadores internos.

**Y este endpoint es donde la validación de mayoría de edad se pone a prueba de verdad**: es el único alta que cualquiera puede ejecutar sin credenciales, y **no ejecuta ninguna comprobación de edad**. No la necesita — enviar `TI` falla por referencia inexistente, igual que enviar `XX`, porque el catálogo no tiene esa fila. Es el argumento de `RF-SP-051` puesto en el peor sitio posible.

**El documento repetido NO dice que lo esté**, al contrario que el nombre de usuario y el correo de `EX-005`. La asimetría es deliberada y está razonada en `spec.md` `EX-007`: un número de documento es un dato que se consigue, y confirmarle a un desconocido que esa persona tiene cuenta aquí es un problema distinto del de ayudar a alguien a elegir otro nombre de usuario.

**`countryCode` es alfa-3 y no un identificador** (07-09-2026), y es el tercer campo de referencia de este cuerpo que evita los UUID: el producto admite su código, el vendedor va por nombre de usuario. Aquí el argumento es incluso más firme — `RN-SP-009` hace que el código de un país **no cambie jamás**, de modo que es el identificador más estable del sistema.


**`brokerId` va por IDENTIFICADOR y no por nombre**, y es el único campo de referencia de este cuerpo que lo hace. Los otros tres —producto, vendedor, país— evitan los UUID porque quien rellena el formulario los teclea o los trae el enlace; **el broker lo elige de un desplegable** que acaba de leer del catálogo público, de modo que el identificador ya lo tiene en la mano. Y es lo correcto por lo que el catálogo declara de sí mismo: **el nombre es su clave de negocio y renombrar un broker es una migración**, de modo que referenciarlo por nombre desde un formulario ataría el registro a una cadena que puede cambiar.

**Es una LISTA y se exige AL MENOS UNA** cuando el producto del enlace es `BECA → BECA` (`RN-SP-042`): una persona puede operar con varios brokers, y este formulario es hoy la única vía para declararlos. **La comprobación se hace DESPUÉS de resolver el producto**, no en la validación del cuerpo: no cabe en una anotación porque depende de un dato que hay que ir a buscar.

**Las repetidas dentro de la misma petición se rechazan como dato inválido** y no con el `409` del índice, aunque el índice también las cazaría: ese mensaje dice «ya está declarada por otra persona», y ahí la otra persona sería ella misma dos líneas más arriba del mismo formulario.

**Se normaliza a mayúsculas antes de buscar**, porque un formulario público recibirá `col` y `Col`, y `ck_countries_code_format` solo admite mayúsculas. Rechazar por la caja sería rechazar por algo que el sistema puede arreglar sin ambigüedad — es el mismo trato que el correo recibe en `RF-SP-024`.

**El rechazo no distingue inexistente de inactivo** (`EX-006`, `CA-SP-583`), al revés que en `RF-SP-024` y `RF-SP-027`. Es el criterio que este endpoint ya aplica al producto y al vendedor: distinguirlo no ayudaría a rellenar el formulario y sí permitiría **enumerar en qué mercados opera la plataforma** probando los doscientos cuarenta y nueve códigos ISO.

La respuesta lleva la cuenta creada y **su estado**, y **no lleva credenciales de sesión** (`CA-SP-521`).

## 5. Autorización, y las dos defensas que la sustituyen

**Ninguna.** Es público, y por eso las dos defensas no son opcionales:

**Límite de tasa**, con la política que ya usa la recuperación de contraseña (`RF-SP-040`): por origen. Sin él, este endpoint crea usuarios en bucle. `RATE_LIMIT_EXCEEDED` ya existe en el catálogo de eventos, de modo que no hace falta migración.

**Verificación al arrancar de que la membresía gratuita existe.** La decisión del responsable (01-09-2026) es identificarla por el código `BECA`, y el precedente para sostener una convención así ya está en el sistema: `CurrencyCatalogStartupCheck` comprueba al iniciar que hay una moneda por defecto activa. El equivalente aquí convierte «alguien renombró el nivel» en **un arranque que falla**, en lugar de en un registro que revienta en producción con un `500` que no dice nada.

## 6. Auditoría

**Dos eventos y ninguna migración.**

- `USER_CREATED` en la auditoría de seguridad, con `selfRegistered`, el vendedor y el producto en el detalle.
- Los registros de cambio del alta, del rol, de la membresía y de la atribución, bajo **el mismo `correlation_id`**.

**No se añade un tipo de evento nuevo**, y es deliberado: el catálogo de `event_type` es un `CHECK` sobre `audit_security_log` y ampliarlo cuesta una migración (precedentes `V34` y `V36`). Lo que ocurrió **es** la creación de un usuario; que la pidiera la propia persona es un **detalle** de ese hecho, no otro hecho. Distinguirlo en el tipo obligaría además a que toda consulta que hoy busca altas de usuario supiera preguntar por dos.

**La contraseña no aparece en ningún registro** (Art. VI.5), y el cuerpo tampoco llega a `request_log`, que no guarda cuerpos.

## 7. Transaccionalidad

**Una sola transacción** para los SEIS hechos: cuenta, rol, membresía, atribución, **cuenta de broker** y **la venta** (09-09-2026).

No es una preferencia: **cualquier corte deja un estado que ninguna regla admite**. Una cuenta con rol de consumidor y sin membresía viola `RN-SP-018`; una con membresía y sin atribución es el cliente huérfano que `EX-002` existe para evitar. `CA-SP-519` lo verifica desde fuera — tras un rechazo, ninguna de las cuatro tablas tiene una fila nueva.
**Y la cuenta de broker entra en la MISMA**, aunque su fallo llegue del motor y no de una regla: si el índice de `RN-SP-038` rechaza la cuenta —ya la declaró otro—, lo que no puede quedar es una persona registrada, con membresía y atribución, y sin la cuenta que su producto exigía. El estado `FTD_PENDIENTE` la dejaría esperando un depósito que nadie podría atribuirle.

**Y la venta también, y es la ÚLTIMA que se ejecuta.** El orden importa: `RN-MV-003` saca el vendedor del superior del cliente y `RN-MV-007` mira la oferta de su membresía, de modo que la venta necesita la persona, su nivel y su atribución **ya escritos**. Y necesita estar dentro: **registrar a alguien cuya venta no se pudo anotar** y **anotar una venta de alguien que no existe** son los dos estados que esta transacción evita, y el segundo no lo evitaría ningún otro orden.


La auditoría de seguridad va **después de confirmar**, como en el resto del sistema: un registro que sobreviviera al fallo afirmaría un alta que no ocurrió.

## 8. Impacto sobre otros módulos, y las enmiendas que este plan aplica

| Documento | Enmienda |
|---|---|
| `requirements/sp.md` | Ficha de `RF-SP-045`, su fila en §6.1, su ruta en §9 y las tres reglas nuevas en §5.1. **Y tres enmiendas sobre reglas vigentes**: `RN-SP-020` gana su rama de consumidor, `RN-SP-022` pasa a cubrir la cartera de clientes, y §10.7 declara que `user_supervisors` ya no contiene solo vendedores. Más el estado: `PENDIENTE` → `FTD_PENDIENTE` |
| `security.md` §3.1 | El catálogo de estados. **`FTD_PENDIENTE` autentica**, y es el primero que lo hace sin estar `ACTIVO`: la columna «¿Puede autenticarse?» deja de coincidir con «está activo» |
| `architecture.md` §15.2 | La tabla de lecturas cruzadas pasa de **tres a cuatro**, y la cuarta **rompe la norma de esa sección a propósito** (ver el recuadro siguiente) |
| `requirements.md` | Fila en la matriz e indicadores |
| `modelo-datos.md` | El mapa no gana aristas: cambia **qué significa** una fila de `user_supervisors` |

!!! danger "Esta lectura NO sigue la norma de D-25, y no seguirla es lo correcto"

    D-25 fijó que **el dueño del dato publica la interfaz y el consumidor la importa**, y las tres lecturas que existen lo cumplen: van de `PM` a `SP`.

    **Esta va al revés.** El consumidor es `SP` —el registro crea una persona, y `users` es suyo— y el dueño del dato es `PM`. Aplicar la norma literalmente pondría a `SP` a importar una interfaz de `PM`, y como `PM` ya importa tres de `SP`, el grafo pasaría a ser `SP` → `PM` → `SP`: **el ciclo que `modules.md` §7 prohíbe**, introducido por seguir al pie de la letra la norma que existe para evitarlo.

    De modo que aquí **se invierte la dependencia**: `SP` declara `RegistrableProductLookup` en su capa `application` y **`PM` lo implementa**. La única dependencia de compilación sigue siendo `PM` → `SP`, que ya existía, y no aparece ninguna arista nueva.

    Y no contradice el descarte de la inversión que §15.2 hace para las otras tres: **aquel se descartó por producir el ciclo y este se elige por evitarlo**. La regla de fondo nunca fue quién declara la interfaz — es **que el grafo no tenga ciclos**, y la dirección la decide en cada caso cuál de las dos formas conserva esa propiedad. Cuando el consumidor es el módulo raíz, la declara él.

**El código del adaptador vive en paquetes de `PM` y la tarea pertenece a este requerimiento**, por el mismo reparto que fijó D-25: ningún actor pide «implementar una interfaz» como comportamiento observable.

!!! danger "Cuatro requerimientos ya implementados cambian de comportamiento sin que nadie los toque"

    Es la consecuencia de meter a los clientes en `user_supervisors`, y es lo que hay que revisar antes de dar por buena la decisión (Art. I.7):

    | Requerimiento | Qué le pasa |
    |---|---|
    | `RF-SP-028` — cambiar el estado | **Dos cosas**: admitir la salida de `FTD_PENDIENTE`, y que ahora rechace desactivar a un vendedor **con cartera de clientes** por `RN-SP-022` |
    | `RF-SP-029` — eliminar usuario | Lo mismo: un agente con clientes deja de poder eliminarse hasta reasignarlos |
    | `RF-SP-031` — retirar roles | Retirar el rol `VENDEDOR` a quien tiene clientes se rechaza |
    | `RF-SP-042` — consultar el equipo a cargo | **Empieza a devolver clientes** junto al equipo comercial, sin que su contrato cambie |

    Las tres primeras son **más restrictivas que antes**, y eso es lo correcto: un cliente huérfano es peor que una operación que hay que preparar. La cuarta no rompe a nadie —cada fila ya lleva los roles de la persona, que es lo que permite distinguirlos—, pero **se prueba a propósito** (`CA-SP-526`) para que sea una decisión y no una sorpresa.

    Y hay un efecto colateral que hay que buscar activamente: **los datos de prueba de esas tres suites pueden empezar a chocar con `RN-SP-022`** allí donde hoy desactivan a alguien sin mirar si tiene gente a cargo.

**La salida manual de `FTD_PENDIENTE`**, en `RF-SP-028`: su operación de cambio de estado **debe admitir la transición hacia `ACTIVO`**. Hoy `ChangeUserStatusService` rechaza el cuarto estado del dominio con un mensaje que lo nombra. Es la única salida mientras el webhook del bróker no exista, y **desaparece sola** cuando exista: entonces será el webhook quien mueva el estado, y la vía manual quedará como excepción operativa.

## 9. Alternativas consideradas

| Alternativa | Por qué se descartó |
|---|---|
| Persistir el enlace como artefacto emitido, con caducidad y usos | Es la defensa correcta **si el enlace concede algo**, y no concede: el camino de pago pasa por pasarela y el gratuito produce una cuenta que no opera (`spec.md` §2). Queda como condición de reapertura en §14 |
| «Sin depósito» como marca aparte del estado | Recomendada y **descartada por el responsable** (01-09-2026). Deja escrito su coste: `users.status` no puede expresar «sin depósito **y además** bloqueada», porque un solo eje vuelve excluyentes dos hechos que no lo son |
| Una columna en `memberships` que diga cuál es la gratuita | Recomendada y **descartada por el responsable** a favor del código `BECA`. Se mitiga con la verificación al arrancar de §5 |
| Devolver credenciales de sesión al registrar | Duplicaría la emisión de sesiones en dos requerimientos, y el segundo acabaría olvidando alguna regla del primero |
| Un tipo de evento de auditoría propio | Una migración sobre el `CHECK` de `audit_security_log` para distinguir un **detalle** de un hecho que ya tiene tipo, y obligaría a que toda consulta de altas preguntara por dos |
| Una tabla propia para la atribución (`client_referrals`) | **Propuesta y descartada por el responsable** (01-09-2026). El argumento a favor era formal —`RN-SP-020` exige un rol padre que un `CONSUMIDOR` no porta—, pero eso es un problema de la regla y no de la tabla. Con dos estructuras, subir de un cliente hasta el manager que cobra por él exige un join y **un caso especial en la hoja**, que es donde está el dinero y donde un caso especial se implementa mal |
| Guardar `product_id` en `user_supervisors` | Metería una columna de producto en la tabla de la jerarquía comercial, que es la mezcla que unificar pretendía evitar. Con qué producto entró el cliente pertenece al depósito, no al vínculo |

## 10. Riesgos

| # | Riesgo | Mitigación |
|---|---|---|
| 1 | **`puedeEntrar()` se escribe en negativo** y todo estado futuro nace autenticando | Lista explícita de los que autentican, y prueba de que `INACTIVO` y `BLOQUEADO` siguen sin poder |
| 2 | El endpoint público **crea usuarios en bucle** | Límite de tasa por origen desde la primera versión, no «después» |
| 3 | La convención del código `BECA` se rompe al renombrar el nivel | Verificación al arrancar: falla el arranque, no el registro |
| 4 | Una transacción parcial deja un consumidor **sin membresía** | Una sola transacción, y `CA-SP-519` lo comprueba desde fuera tras un rechazo |
| 5 | El renombrado del estado alcanza al **inicio de sesión de todo el sistema** | La suite de `SP` entera debe seguir en verde sin cambios; cualquier ajuste ahí es señal de que el cambio se coló donde no debía |
| 6 | La atribución forjable ensucia la base de comisiones | Aceptado y declarado (`spec.md` §14), con su condición de reapertura escrita |
| 7 | **`RN-SP-022` empieza a rechazar operaciones que hoy pasan.** Retirar a un agente con cartera se rechazará, y nadie lo espera | Es correcto —un cliente huérfano es peor— y se prueba a propósito (`CA-SP-525`). La suite de `RF-SP-028`, `RF-SP-029` y `RF-SP-031` hay que revisarla: sus datos de prueba pueden empezar a chocar con la regla |
| 8 | **`RF-SP-042` empieza a devolver clientes** en el equipo a cargo, sin que su contrato cambie | Cada fila ya lleva los roles de la persona, que es lo que permite distinguirlos. Se prueba (`CA-SP-526`) para que el cambio sea deliberado y no una sorpresa |

## 11. Estrategia de prueba

| Qué se prueba | Nivel | Cómo |
|---|---|---|
| Los dieciocho criterios de `spec.md` §12 | API | |
| **El rechazo no deja nada escrito** | Integración | Contar las cuatro tablas antes y después de cada excepción |
| **La persona registrada autentica** | API | Registro y luego `POST /auth/login`, con la cuenta en `FTD_PENDIENTE` |
| **`INACTIVO` y `BLOQUEADO` siguen sin autenticar** | API | La prueba que impide que el riesgo 1 pase inadvertido |
| ~~Código e identificador dan el mismo resultado~~ | — | **Retirada el 09-09-2026**: el cuerpo ya no nombra el producto por código |
| El producto y el vendedor del movimiento son obligatorios | API | Sin `productId`, `VAL-001`; sin `sellerUsername`, `VAL-002` |
| Los tres rechazos de producto comparten respuesta | API | Inexistente, inactivo y retirado, comparados entre sí |
| La membresía de pago dice que exige pago | API | La asimetría deliberada con lo anterior |
| Vigencia con y sin `validity_days` | Integración | Fecha de fin poblada y nula |
| Unicidad bajo concurrencia | Integración | Dos registros simultáneos con el mismo nombre de usuario |
| El arranque falla sin la membresía `BECA` | Integración | Contexto que no levanta |
| **La suite de `SP` sigue en verde sin tocarla** | Toda | Es lo que verifica el riesgo 5 |
