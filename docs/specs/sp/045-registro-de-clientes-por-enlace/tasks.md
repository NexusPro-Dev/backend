# TASKS — `RF-SP-045` Registro de clientes por enlace

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-045` |
| Especificación | [`spec.md`](spec.md) |
| Plan | [`plan.md`](plan.md) |
| `plan.md` aprobado el | 01-09-2026 |
| Estado | **En revisión** |
| Issue | Pendiente de crear |
| Rama | `feature/registro-de-clientes-por-enlace` |
| Aprobadas por | Pendiente |

---

## 1. Tareas

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-01` | `V49`: `ck_users_status` sustituye `PENDIENTE` por `FTD_PENDIENTE`, y `UserStatus` con él | — | Ninguna fila cambia: el valor retirado no lo usa nadie. Migración en verde sobre base con datos | Pendiente |
| `T-02` | **`RN-SP-020` gana su rama de consumidor** en `CommercialStructure`: si el subordinado no porta rol vendedor, basta con que el superior porte **alguno** | — | Un cliente cuelga de un `AGENTE`, de un `DIRECTOR` y de un `MANAGER` por igual (`CL-007`); un funcionario sigue siendo rechazado. **Sin migración**: `user_supervisors` ya tiene la forma | Pendiente |
| `T-03` | **`AuthUser.puedeEntrar()`** admite `FTD_PENDIENTE`, escrito como **lista explícita** de los estados que autentican | `T-01` | Prueba de que `INACTIVO` y `BLOQUEADO` **siguen sin poder**. Es el riesgo 1 del plan y la tarea más delicada del requerimiento | Pendiente |
| `T-04` | Enmienda a `RF-SP-028`: `ChangeUserStatusService` admite la salida de `FTD_PENDIENTE` a `ACTIVO` | `T-01` | `CA-SP-524`. Es la única salida mientras no haya webhook | Pendiente |
| `T-05` | **`SP` declara `RegistrableProductLookup`** y **`PM` lo implementa** — producto por código o identificador, con destino, vigencia y estado. **La dependencia va invertida a propósito**: al revés abriría el ciclo `SP` → `PM` → `SP` | — | La regla de ArchUnit sigue en verde, y `ProductCatalog` **no se toca**: la suite de `CM`, que lo consume, no cambia | Pendiente |
| `T-06` | **Revisar las tres suites que `RN-SP-022` empieza a alcanzar**: `RF-SP-028`, `RF-SP-029` y `RF-SP-031` | `T-02` | Sus datos de prueba desactivan o eliminan personas sin mirar si tienen gente a cargo; con clientes en la tabla pueden empezar a chocar. **Es la tarea que más probable es que se olvide** | Pendiente |
| `T-07` | `RegisterClientByLinkService`: los cuatro hechos en **una** transacción | `T-03`, `T-05`, `T-06` | `CA-SP-519`: contar las cuatro tablas antes y después de cada rechazo | Pendiente |
| `T-08` | Resolución del producto **por código o identificador** en el mismo campo | `T-05` | `CA-SP-514`: el mismo producto por las dos vías da el mismo resultado | Pendiente |
| `T-09` | Las cinco excepciones, con `EX-001` y `EX-002` **compartiendo respuesta** y `EX-004` diciendo qué pasó | `T-07` | `CA-SP-515`, `CA-SP-517`, `CA-SP-518`. La asimetría es deliberada y hay que probarla como tal | Pendiente |
| `T-10` | Verificación al arrancar de que existe la membresía `FREE` | — | El contexto **no levanta** sin ella (`CL-005`). Precedente: `CurrencyCatalogStartupCheck` | Pendiente |
| `T-11` | `interfaces`: `POST /api/v1/auth/registration`, público, y su entrada en `RUTAS_PUBLICAS` | `T-07` | Responde **sin token**, y figura en la lista blanca de `EndpointPermissionsIT` con su motivo | Pendiente |
| `T-12` | Límite de tasa por origen, con la política de `RF-SP-040` | `T-11` | Riesgo 2: sin esto el endpoint crea usuarios en bucle | Pendiente |
| `T-13` | Auditoría: `USER_CREATED` con `selfRegistered`, vendedor y producto; los cambios bajo el mismo `correlation_id` | `T-07` | `CA-SP-523`. **Sin migración**: no se añade tipo de evento | Pendiente |
| `T-14` | Prueba de que la persona registrada **autentica** pese a no estar `ACTIVO` | `T-03`, `T-11` | `CA-SP-522`, de extremo a extremo: registro y luego inicio de sesión | Pendiente |
| `T-15` | Prueba de concurrencia sobre el nombre de usuario | `T-07` | Dos registros simultáneos: el segundo recibe `VAL-007`, no un `500` | Pendiente |
| `T-16` | Pruebas de API del resto de criterios de `spec.md` §12 | `T-11` | Cubre `CA-SP-507` a `CA-SP-521` | Pendiente |
| `T-17` | Documentación OpenAPI del endpoint, declarándolo **público** | `T-16` | El contrato no hereda el esquema de seguridad, como los tres de sesión | Pendiente |
| `T-18` | Aplicar las enmiendas de `plan.md` §8 y actualizar la matriz | `T-16` | Cinco documentos, cada uno con su fila de control de cambios | Pendiente |

## 2. Orden de ejecución

`T-01` y `T-02` primero: sin el estado y sin la tabla no hay nada que escribir. **`T-03` es la tarea de riesgo del requerimiento** —toca el camino de inicio de sesión de todo el sistema— y por eso va sola y antes que el caso de uso, con su propia prueba de que los estados que no autentican siguen sin hacerlo.

`T-05` es la única que escribe fuera de `SP`, y es también la única que **invierte la dirección** de una dependencia entre módulos: `SP` declara el puerto y `PM` lo implementa, porque al revés abriría un ciclo. Escrita como las otras tres, compila igual — quien la detecta es la regla de ArchUnit, no el compilador.

## 3. Cobertura de los criterios de aceptación

| Criterio | Tareas |
|---|---|
| `CA-SP-507` | `T-11`, `T-16` |
| `CA-SP-508` | `T-01`, `T-07` |
| `CA-SP-509`, `CA-SP-510`, `CA-SP-511` | `T-07` |
| `CA-SP-512` | `T-07` |
| `CA-SP-513` | `T-07` |
| `CA-SP-525`, `CA-SP-526` | `T-06` |
| `CA-SP-514` | `T-08` |
| `CA-SP-515` a `CA-SP-518` | `T-09` |
| `CA-SP-519` | `T-07` |
| `CA-SP-520` | `T-15`, `T-16` |
| `CA-SP-521` | `T-11` |
| `CA-SP-522` | `T-03`, `T-14` |
| `CA-SP-523` | `T-13` |
| `CA-SP-524` | `T-04` |

## 4. Bloqueos

| # | Bloqueo | Desde | Responsable | Estado |
|---|---|---|---|---|
| 1 | **`T-03` toca el inicio de sesión de todo el sistema.** Una regresión ahí no afecta a este requerimiento: afecta a `RF-SP-034` y `RF-SP-035`, es decir, a que alguien pueda entrar | 01-09-2026 | Responsable técnico | Abierto |
| 2 | **`T-05` invierte la dirección de una dependencia entre módulos**, que es la primera vez que ocurre. Si se escribe como las otras tres —`PM` publicando— el resultado compila y **abre un ciclo** que solo detecta la regla de ArchUnit | 01-09-2026 | Responsable técnico | Abierto |
| 5 | **Meter clientes en `user_supervisors` cambia el comportamiento de cuatro requerimientos ya implementados** sin tocarlos: `RF-SP-028`, `RF-SP-029` y `RF-SP-031` pasan a rechazar más, y `RF-SP-042` empieza a devolver clientes. Es la consecuencia de la unificación y hay que revisarla entera antes de dar el requerimiento por terminado | 01-09-2026 | Responsable técnico | Abierto |
| 3 | El camino de **pago** queda rechazado por `EX-004` hasta que exista el área de Finanzas. No bloquea este requerimiento: bloquea su otra mitad | 01-09-2026 | Responsable del proyecto | Abierto |
| 4 | La **confirmación del depósito** por webhook del bróker se construye más adelante. Hasta entonces, la salida de `FTD_PENDIENTE` es manual (`T-04`) | 01-09-2026 | Responsable del proyecto | Abierto |

| 6 | **El formulario público exige país y NO tiene de dónde sacar la lista** (`RN-SP-034`, 07-09-2026). `GET /api/v1/countries` (`RF-SP-021`) exige `countries:read`, que quien se registra no tiene por definición. Las tres salidas —abrir aquel endpoint, crear uno público propio bajo `/auth`, o dejar que el formulario ofrezca la lista ISO entera y el servidor rechace— publican o cuestan cosas distintas, y **una de ellas contradice la decisión que `EX-006` acaba de tomar** de no revelar en qué mercados opera la plataforma. **No bloquea al resto de la enmienda de `RN-SP-034`**: los otros cinco requerimientos no dependen de ella | 07-09-2026 | **Responsable del proyecto** | **Abierto** |

## 4.bis El formulario público exige país — enmienda del 07-09-2026

`RN-SP-034` obliga a que toda persona declare un país, **también quien se registra por enlace** (`spec.md` §6.1, `CA-SP-582` y `CA-SP-583`). La tarea es `T-47` de [`../024-registrar-usuario/tasks.md`](../024-registrar-usuario/tasks.md) §4.quinquies.

**Dos decisiones son de este requerimiento**, razonadas en `plan.md` §4:

- **El país viaja por código ISO alfa-3 y no por identificador**, como el producto por código y el vendedor por nombre de usuario. `RN-SP-009` hace que ese código no cambie nunca, de modo que es la referencia más estable del sistema y la única que un enlace impreso puede permitirse.
- **El rechazo no distingue inexistente de inactivo**, al revés que en `RF-SP-024` y `RF-SP-027`. Distinguirlo no ayuda a rellenar el formulario y sí permite enumerar en qué mercados opera la plataforma probando códigos ISO — el mismo criterio con el que `EX-001` y `EX-002` ya callan.

**Y deja abierto el bloqueo 6**, que es lo que esta enmienda no puede cerrar sola.

## 4.ter El formulario público exige documento y teléfono — enmienda del 08-09-2026

**La tarea no se duplica aquí.** Es `T-60` de [`../024-registrar-usuario/tasks.md`](../024-registrar-usuario/tasks.md) §4.sexies, y está **bloqueada**.

**Este endpoint es donde la validación de mayoría de edad se pone a prueba de verdad**: es el único alta que cualquiera puede ejecutar **sin credenciales**, y aun así **no ejecuta ninguna comprobación de edad**. Enviar `TI` falla por referencia inexistente, exactamente igual que enviar `XX` — porque el catálogo no tiene esa fila. Es el argumento de `RF-SP-051` puesto en el peor sitio posible.

**El documento repetido no dice que lo esté**, al contrario que el nombre de usuario y el correo de `EX-005`. Un número de documento es un dato que se consigue, y confirmarle a un desconocido que esa persona tiene cuenta aquí es un problema distinto de ayudar a alguien a elegir otro nombre de usuario.

**Y el bloqueo del catálogo público crece a dos.** El formulario necesita ahora la lista de **países** y la de **tipos de documento**, y ninguna de las dos es legible sin permiso. Con un catálogo era una excepción discutible; con dos es una **decisión de forma** — un endpoint público de catálogos bajo `/auth` con su propio límite de tasa, en lugar de dos parches. **Se fusiona con el bloqueo 6 y se decide una sola vez.**
## 5. Definición de terminado

El requerimiento no está terminado hasta cumplir **todas** las condiciones de la constitución §16:

- [ ] Todas las tareas en estado `Hecha`.
- [ ] Todos los criterios de aceptación con prueba automatizada en verde.
- [ ] `mvn verify` en verde en local.
- [ ] Toda escritura emite su evento de auditoría, en la transacción que corresponde.
- [ ] Los endpoints nuevos declaran su permiso, o declaran por qué no lo llevan.
- [ ] El contrato OpenAPI coincide con el comportamiento real.
- [ ] Documentación afectada actualizada en el mismo Pull Request.
- [ ] Matriz de trazabilidad actualizada.
- [ ] Pull Request aprobado por alguien distinto del autor e integrado.
