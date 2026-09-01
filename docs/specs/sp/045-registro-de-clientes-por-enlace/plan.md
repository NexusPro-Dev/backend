# PLAN — `RF-SP-045` Registro de clientes por enlace

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-045` |
| Especificación | [`spec.md`](spec.md) |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 01-09-2026 |

---

## 1. Enfoque

Un endpoint **público que escribe**, que es lo que este sistema no tenía. Los seis públicos de hoy o leen, o consumen una credencial que el propio sistema emitió; este crea una persona, le concede un rol, le asigna una membresía y escribe una atribución, todo a petición de alguien que no es nadie todavía.

De ahí salen las tres decisiones del plan: **una sola transacción**, **límite de tasa desde el primer día**, y **un estado de cuenta que autentica sin operar** — el primero del sistema.

## 2. Cambios de esquema

**Dos migraciones.**

**`V48` — el catálogo de estados.** `ck_users_status` pasa de `('ACTIVO','INACTIVO','BLOQUEADO','PENDIENTE')` a `('ACTIVO','INACTIVO','BLOQUEADO','FTD_PENDIENTE')`.

!!! success "El renombrado sale gratis hoy, y no lo será dentro de un mes"

    `PENDIENTE` está declarado **y sin usar a propósito** desde `V18`, que lo dejó escrito: «queda declarado y sin uso a propósito […] para que el día que un requerimiento lo estrene no haga falta alterar el `CHECK` de una tabla en uso». Ninguna fila lo lleva, ninguna semilla lo produce y ningún caso de uso lo escribe — `ChangeUserStatusService` lo **rechaza** explícitamente.

    De modo que esto es sustituir un valor del dominio, **sin migración de datos**. La misma operación con una sola fila en `PENDIENTE` habría exigido decidir a dónde va esa cuenta.

**`V49` — `client_referrals`**, la atribución del cliente a su vendedor.

| Columna | Tipo | Nula | Referencia |
|---|---|---|---|
| `id` | `uuid` | No | — |
| `client_id` | `uuid` | No | `users` |
| `seller_id` | `uuid` | No | `users` |
| `product_id` | `uuid` | No | `products` |
| `started_at` | `timestamptz` | No | — |
| `ended_at` | `timestamptz` | **Sí** | — |
| `created_at` | `timestamptz` | No | — |

**Se llama `client_referrals` y no `user_referrers`**, aunque el prefijo `user_` sea el de sus dos hermanas. Es deliberado: `user_supervisors` y `user_memberships` describen cosas que un usuario **tiene**, y nombrar a esta igual sugeriría que vive en el mismo eje que la primera. **No vive ahí**, y confundirlas es el error probable — `user_supervisors` es la **fuerza comercial**, y `RN-SP-020` exige que el superior porte el rol padre inmediato del rol **vendedor** del subordinado. Un cliente es `CONSUMIDOR`: metido ahí, esa regla lo rechazaría.

**Guarda el producto**, y no es redundante con la membresía que ya está en `user_memberships`: es **con qué producto entró**, y es el dato que la liquidación futura necesitará para resolver la tarifa (`RF-CM-005` resuelve por persona **y producto**).

**Restricciones:**

| Restricción | Sobre | Por qué |
|---|---|---|
| `ck_client_referrals_no_self` | `client_id <> seller_id` | Nadie se trae a sí mismo |
| `ck_client_referrals_periodo` | `ended_at IS NULL OR ended_at > started_at` | La rama `IS NULL` **delante y explícita**: un `CHECK` que evalúa a `NULL` **acepta** la fila, y este proyecto ya pagó una vez por eso con `ck_deletion_reason` |
| `uq_client_referrals_vigente` | Índice único **parcial**: `client_referrals(client_id) WHERE ended_at IS NULL` | `RN-SP-028`: un cliente tiene **como mucho un vendedor vigente**. Parcial y no total porque el historial cerrado sí admite repetición — es historial |
| `ix_client_referrals_seller` | `(seller_id, started_at DESC)` | «Qué clientes trajo esta persona», que es la consulta que la liquidación hará |

**Sin `deleted_at`.** Una atribución no se retira: se **cierra**, poblando `ended_at`. Es la misma distinción que `RN-CM-005` fija para las tarifas — se retira lo que no debió existir, se cierra lo que dejó de regir — y aquí solo cabe lo segundo.

## 3. Componentes afectados

| Capa | Componente | Responsabilidad |
|---|---|---|
| `modules/system/users/application` | `RegistrableProductLookup` | **Puerto nuevo, declarado en `SP`**: el producto por código o identificador, con su destino, su vigencia y su estado |
| `modules/products/domain/repository` | `PublishedProductCatalog` | **Lo implementa `PM`**, que es quien tiene el dato |
| `modules/system/users/application` | `SelfRegistrationRequest` | Los seis datos de la persona más producto y vendedor |
| `modules/system/users/domain/service` | `RegisterClientByLinkService` | El caso de uso, en una transacción |
| `modules/system/users/domain/repository` | `ClientReferralRepository` | La escritura de la atribución |
| `modules/system/users/interfaces` | `RegistrationController` | `POST /api/v1/auth/registration`, público |
| `modules/system/users/domain/models` | `UserStatus` | `PENDIENTE` → `FTD_PENDIENTE` |
| `modules/system/auth/domain/repository` | `AuthUser` | `puedeEntrar()` admite el estado nuevo |
| `shared/security` | `SecurityConfig` | Una ruta pública más |
| `shared/security/ratelimit` | — | La política del endpoint nuevo |

!!! danger "`puedeEntrar()` es la línea más delicada de este requerimiento"

    Hoy es `!deleted && "ACTIVO".equals(status)`. **Es la primera vez que un estado distinto de `ACTIVO` puede autenticarse**, y el cambio toca el camino de inicio de sesión de todo el sistema.

    Se escribe como **lista explícita de los estados que autentican** y no como negación de los que no —`!INACTIVO && !BLOQUEADO`—, porque la forma negada hace que **todo estado futuro nazca autenticando**, que es exactamente el error que este proyecto no quiere cometer en el camino de acceso.

    Y alcanza a dos sitios, no a uno: `LoginService` lo consulta al entrar, y `SessionService.refresh` lo vuelve a consultar en cada rotación. Los dos leen el mismo método, que es lo que impide que diverjan.

## 4. Contrato de API

`POST /api/v1/auth/registration`, **público**, `201`.

```json
{
  "product": "UPGRADE_FREE",
  "referrer": "agente.martinez",
  "firstName": "Ana", "lastName": "Ruiz",
  "username": "ana.ruiz", "email": "ana@ejemplo.com",
  "password": "…"
}
```

**Cuelga de `/auth` y no de `/users`.** Las seis rutas públicas del sistema viven ahí y esta es la séptima; colgarla de `/users` la pondría al lado de `POST /api/v1/users`, que exige `users:create` — dos altas de persona bajo el mismo recurso, una abierta y otra no, es la clase de vecindad que produce el `@PreAuthorize` olvidado.

**`product` admite código o identificador en el mismo campo**, resuelto por forma: lo que parece un UUID se busca por identificador, lo demás por código. Es lo que ya hace el inicio de sesión con `identifier`, que acepta nombre de usuario o correo. Dos campos opcionales y excluyentes habrían obligado a validar que llega exactamente uno.

La respuesta lleva la cuenta creada y **su estado**, y **no lleva credenciales de sesión** (`CA-SP-521`).

## 5. Autorización, y las dos defensas que la sustituyen

**Ninguna.** Es público, y por eso las dos defensas no son opcionales:

**Límite de tasa**, con la política que ya usa la recuperación de contraseña (`RF-SP-040`): por origen. Sin él, este endpoint crea usuarios en bucle. `RATE_LIMIT_EXCEEDED` ya existe en el catálogo de eventos, de modo que no hace falta migración.

**Verificación al arrancar de que la membresía gratuita existe.** La decisión del responsable (01-09-2026) es identificarla por el código `FREE`, y el precedente para sostener una convención así ya está en el sistema: `CurrencyCatalogStartupCheck` comprueba al iniciar que hay una moneda por defecto activa. El equivalente aquí convierte «alguien renombró el nivel» en **un arranque que falla**, en lugar de en un registro que revienta en producción con un `500` que no dice nada.

## 6. Auditoría

**Dos eventos y ninguna migración.**

- `USER_CREATED` en la auditoría de seguridad, con `selfRegistered`, el vendedor y el producto en el detalle.
- Los registros de cambio del alta, del rol, de la membresía y de la atribución, bajo **el mismo `correlation_id`**.

**No se añade un tipo de evento nuevo**, y es deliberado: el catálogo de `event_type` es un `CHECK` sobre `audit_security_log` y ampliarlo cuesta una migración (precedentes `V34` y `V36`). Lo que ocurrió **es** la creación de un usuario; que la pidiera la propia persona es un **detalle** de ese hecho, no otro hecho. Distinguirlo en el tipo obligaría además a que toda consulta que hoy busca altas de usuario supiera preguntar por dos.

**La contraseña no aparece en ningún registro** (Art. VI.5), y el cuerpo tampoco llega a `request_log`, que no guarda cuerpos.

## 7. Transaccionalidad

**Una sola transacción** para los cuatro hechos: cuenta, rol, membresía y atribución.

No es una preferencia: **cualquier corte deja un estado que ninguna regla admite**. Una cuenta con rol de consumidor y sin membresía viola `RN-SP-018`; una con membresía y sin atribución es el cliente huérfano que `EX-002` existe para evitar. `CA-SP-519` lo verifica desde fuera — tras un rechazo, ninguna de las cuatro tablas tiene una fila nueva.

La auditoría de seguridad va **después de confirmar**, como en el resto del sistema: un registro que sobreviviera al fallo afirmaría un alta que no ocurrió.

## 8. Impacto sobre otros módulos, y las enmiendas que este plan aplica

| Documento | Enmienda |
|---|---|
| `requirements/sp.md` | Ficha de `RF-SP-045`, su fila en §6.1, su ruta en §9, las tres reglas nuevas en §5.1, `client_referrals` en §10 y sus restricciones en §10.8. **Y el estado**: `PENDIENTE` → `FTD_PENDIENTE` |
| `security.md` §3.1 | El catálogo de estados. **`FTD_PENDIENTE` autentica**, y es el primero que lo hace sin estar `ACTIVO`: la columna «¿Puede autenticarse?» deja de coincidir con «está activo» |
| `architecture.md` §15.2 | La tabla de lecturas cruzadas pasa de **tres a cuatro**, y la cuarta **rompe la norma de esa sección a propósito** (ver el recuadro siguiente) |
| `requirements.md` | Fila en la matriz e indicadores |
| `modelo-datos.md` | `client_referrals` en el mapa |

!!! danger "Esta lectura NO sigue la norma de D-25, y no seguirla es lo correcto"

    D-25 fijó que **el dueño del dato publica la interfaz y el consumidor la importa**, y las tres lecturas que existen lo cumplen: van de `PM` a `SP`.

    **Esta va al revés.** El consumidor es `SP` —el registro crea una persona, y `users` es suyo— y el dueño del dato es `PM`. Aplicar la norma literalmente pondría a `SP` a importar una interfaz de `PM`, y como `PM` ya importa tres de `SP`, el grafo pasaría a ser `SP` → `PM` → `SP`: **el ciclo que `modules.md` §7 prohíbe**, introducido por seguir al pie de la letra la norma que existe para evitarlo.

    De modo que aquí **se invierte la dependencia**: `SP` declara `RegistrableProductLookup` en su capa `application` y **`PM` lo implementa**. La única dependencia de compilación sigue siendo `PM` → `SP`, que ya existía, y no aparece ninguna arista nueva.

    Y no contradice el descarte de la inversión que §15.2 hace para las otras tres: **aquel se descartó por producir el ciclo y este se elige por evitarlo**. La regla de fondo nunca fue quién declara la interfaz — es **que el grafo no tenga ciclos**, y la dirección la decide en cada caso cuál de las dos formas conserva esa propiedad. Cuando el consumidor es el módulo raíz, la declara él.

**El código del adaptador vive en paquetes de `PM` y la tarea pertenece a este requerimiento**, por el mismo reparto que fijó D-25: ningún actor pide «implementar una interfaz» como comportamiento observable.

**Enmienda a `RF-SP-028`**, ya implementado (Art. I.7): su operación de cambio de estado **debe admitir la salida de `FTD_PENDIENTE` hacia `ACTIVO`**. Hoy `ChangeUserStatusService` rechaza el cuarto estado del dominio con un mensaje que lo nombra. Es la única salida mientras el webhook del bróker no exista, y **desaparece sola** cuando exista: entonces será el webhook quien mueva el estado, y la vía manual quedará como excepción operativa.

## 9. Alternativas consideradas

| Alternativa | Por qué se descartó |
|---|---|
| Persistir el enlace como artefacto emitido, con caducidad y usos | Es la defensa correcta **si el enlace concede algo**, y no concede: el camino de pago pasa por pasarela y el gratuito produce una cuenta que no opera (`spec.md` §2). Queda como condición de reapertura en §14 |
| «Sin depósito» como marca aparte del estado | Recomendada y **descartada por el responsable** (01-09-2026). Deja escrito su coste: `users.status` no puede expresar «sin depósito **y además** bloqueada», porque un solo eje vuelve excluyentes dos hechos que no lo son |
| Una columna en `memberships` que diga cuál es la gratuita | Recomendada y **descartada por el responsable** a favor del código `FREE`. Se mitiga con la verificación al arrancar de §5 |
| Devolver credenciales de sesión al registrar | Duplicaría la emisión de sesiones en dos requerimientos, y el segundo acabaría olvidando alguna regla del primero |
| Un tipo de evento de auditoría propio | Una migración sobre el `CHECK` de `audit_security_log` para distinguir un **detalle** de un hecho que ya tiene tipo, y obligaría a que toda consulta de altas preguntara por dos |
| Reutilizar `user_supervisors` para la atribución | `RN-SP-020` la rechazaría: exige que el superior porte el rol padre inmediato del rol **vendedor** del subordinado, y un cliente es `CONSUMIDOR` |

## 10. Riesgos

| # | Riesgo | Mitigación |
|---|---|---|
| 1 | **`puedeEntrar()` se escribe en negativo** y todo estado futuro nace autenticando | Lista explícita de los que autentican, y prueba de que `INACTIVO` y `BLOQUEADO` siguen sin poder |
| 2 | El endpoint público **crea usuarios en bucle** | Límite de tasa por origen desde la primera versión, no «después» |
| 3 | La convención del código `FREE` se rompe al renombrar el nivel | Verificación al arrancar: falla el arranque, no el registro |
| 4 | Una transacción parcial deja un consumidor **sin membresía** | Una sola transacción, y `CA-SP-519` lo comprueba desde fuera tras un rechazo |
| 5 | El renombrado del estado alcanza al **inicio de sesión de todo el sistema** | La suite de `SP` entera debe seguir en verde sin cambios; cualquier ajuste ahí es señal de que el cambio se coló donde no debía |
| 6 | La atribución forjable ensucia la base de comisiones | Aceptado y declarado (`spec.md` §14), con su condición de reapertura escrita |

## 11. Estrategia de prueba

| Qué se prueba | Nivel | Cómo |
|---|---|---|
| Los dieciocho criterios de `spec.md` §12 | API | |
| **El rechazo no deja nada escrito** | Integración | Contar las cuatro tablas antes y después de cada excepción |
| **La persona registrada autentica** | API | Registro y luego `POST /auth/login`, con la cuenta en `FTD_PENDIENTE` |
| **`INACTIVO` y `BLOQUEADO` siguen sin autenticar** | API | La prueba que impide que el riesgo 1 pase inadvertido |
| Código e identificador dan el mismo resultado | API | El mismo producto por las dos vías |
| Los tres rechazos de producto comparten respuesta | API | Inexistente, inactivo y retirado, comparados entre sí |
| La membresía de pago dice que exige pago | API | La asimetría deliberada con lo anterior |
| Vigencia con y sin `validity_days` | Integración | Fecha de fin poblada y nula |
| Unicidad bajo concurrencia | Integración | Dos registros simultáneos con el mismo nombre de usuario |
| El arranque falla sin la membresía `FREE` | Integración | Contexto que no levanta |
| **La suite de `SP` sigue en verde sin tocarla** | Toda | Es lo que verifica el riesgo 5 |
