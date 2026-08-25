# PLAN — `RF-SP-035` Refrescar el token de acceso

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-035` |
| Especificación | [`spec.md`](spec.md) |
| `spec.md` aprobada el | 21-08-2026 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 24-08-2026 |

---

## 1. Enfoque

Es el requerimiento que hace viable la decisión D-08. Sin él, la vida de quince minutos del token de acceso —que es lo que permite validar la mayoría de peticiones sin tocar la base de datos— obligaría a escribir la contraseña cada cuarto de hora.

Y es, además, **el único punto en que una sesión vuelve a pasar por la base de datos**. Todo lo demás del sistema confía en la firma del token; aquí, y solo aquí, se puede revalidar que la persona sigue existiendo, activa y no bloqueada. Esa doble condición —renovar y revalidar— es lo que le da su peso.

La pieza que lo sostiene es la **rotación**: cada uso revoca el token presentado y emite uno nuevo, conservando el vínculo. No es un adorno. Es lo que convierte el robo de un refresh token en algo **detectable**, porque tarde o temprano llegará un token ya revocado y la única lectura razonable es que hay dos copias en circulación.

Sobre esa idea se apoyan tres decisiones que este plan tiene que implementar sin confundirlas entre sí, porque las tres se parecen y significan cosas opuestas:

- **`EX-001`** — token revocado **por rotación**: robo. Se revoca la familia entera y se registra severidad alta.
- **`EX-004`** — token revocado por cierre, retiro de acceso o cambio de contraseña: **no** es robo. Misma respuesta al cliente, pero sin revocar familia y sin evento.
- **`EX-005`** — la familia agotó su duración máxima: **tampoco** es robo. Se revoca la familia y se registra como un cierre.

Lo que las distingue es una sola columna, `revoked_reason`, que `RF-SP-034` hizo obligatoria en el esquema. Sin ella, cerrar sesión y reintentar sería indistinguible de un robo, y el registro de seguridad se llenaría de incidentes falsos hasta volverse inútil — que es la forma más silenciosa de perder una defensa.

## 2. Cambios de esquema

**Ninguno.**

`refresh_tokens` la crea `V27__create_refresh_tokens.sql` (`RF-SP-034`) con las cuatro columnas de las que depende este requerimiento y que no existirían si no se hubieran previsto allí:

- **`revoked_reason`**, con su dominio cerrado y su `CHECK` de obligatoriedad. Es lo que separa `EX-001` de `EX-004`.
- **`family_id`**, que agrupa la cadena de rotación y permite revocarla entera con un solo `UPDATE`.
- **`family_started_at`**, que mide la duración máxima **desde el inicio de sesión** y no desde el último refresco. Sin ella habría que recorrer la cadena hasta su origen en cada llamada.
- **`replaced_by_id`**, que conserva el vínculo con el token que sustituyó al revocado (`CA-SP-303`).

Y dos índices que este requerimiento es quien realmente ejercita: el único sobre `token_hash` —el acceso de cada llamada— e `ix_refresh_tokens_family`, que es el de la revocación de familia.

**La lectura del token se hace bajo bloqueo de fila**, con `SELECT … FOR UPDATE` sobre la fila localizada por su hash. No es una optimización: es lo que decide el caso límite de `spec.md` §13. Dos refrescos concurrentes con el mismo token deben resolverse en **uno que gana y uno que cae en `EX-001`**; sin el bloqueo, ambos leen la fila sin revocar, ambos rotan y quedan dos cadenas vivas de la misma sesión — que es exactamente el estado que la rotación existe para hacer imposible.

## 3. Componentes afectados

| Capa | Componente | Nuevo / Modificado | Responsabilidad |
|---|---|---|---|
| `domain` | `RefreshToken` | Modificado | Componente de `RF-SP-034`. Añade `rotate(...)`, la clasificación del motivo de revocación y el agotamiento de la familia |
| `domain` | `RefreshTokenRepository` | Modificado | Puerto de `RF-SP-034`. Añade la búsqueda por hash **con bloqueo de fila** y la revocación de familia |
| `domain` | `SessionLifetimePolicy` | **Nuevo** | Duración máxima de la familia, a partir de `family_started_at` y del parámetro de configuración. Función pura |
| `application` | `RefreshTokenService` | Nuevo | Caso de uso. Orden de `plan.md` §4, rotación, revalidación y las tres respuestas de revocación |
| `application` | `AccessTokenIssuer` | Sin cambios | Puerto de `RF-SP-034` |
| `application` | `RateLimiter` | Sin cambios | Puerto de `RF-SP-034`, con una cota propia más holgada (§5) |
| `infrastructure` | `JpaRefreshTokenRepository` | Modificado | Adaptador de `RF-SP-034`. Añade el `SELECT … FOR UPDATE` y el `UPDATE` de familia |
| `api` | `AuthController` | Modificado | Añade `POST /api/v1/auth/refresh` |
| `api` | `RefreshRequest` | Nuevo | DTO de entrada |
| `api` | `LoginResponse` | Sin cambios | La salida es la misma que la del inicio de sesión (§4) |

`SessionLifetimePolicy` se extrae por el mismo motivo que `LockoutPolicy` en `RF-SP-034`: es la única aritmética del requerimiento, y su modo de fallo es que la sesión no caduque nunca — un defecto que ninguna prueba funcional del camino feliz detecta.

## 4. Contrato de API

| Método | Ruta | Descripción |
|---|---|---|
| `POST` | `/api/v1/auth/refresh` | Renueva la sesión y rota el refresh token |

**Petición**

```json
{ "refreshToken": "9f2c..." }
```

En el cuerpo y no en una cabecera: `security.md` §5.2 declara que el refresh token se envía **únicamente** a este endpoint, y el cuerpo es lo que lo mantiene fuera de los registros de acceso de cualquier intermediario, que sí registran las URL y a menudo las cabeceras.

**Respuesta `200`** — la misma `LoginResponse` de `RF-SP-034`, con `accessToken`, `refreshToken`, `tokenType`, `expiresIn` y `mustChangePassword`. Compartir el tipo es deliberado: el cliente trata las dos respuestas igual, y una forma distinta le obligaría a dos caminos de código para el mismo resultado.

**Errores**

| Código | Cuándo | `error_code` |
|---|---|---|
| `400` | Refresh token ausente | `VAL-001` |
| `401` | Token inexistente o expirado (`EX-002`) | `VAL-002` |
| `401` | Token revocado **por rotación** — reutilización (`EX-001`) | `VAL-002` |
| `401` | Token revocado por cierre, retiro de acceso o cambio de contraseña (`EX-004`) | `VAL-002` |
| `401` | La familia agotó su duración máxima (`EX-005`) | `VAL-002` |
| `401` | La persona está inactiva, bloqueada o eliminada (`EX-003`) | `VAL-003` |
| `429` | Límite de refrescos por origen superado | `ERR-429` |
| `500` | Fallo no controlado | `ERR-500` |

**Las cinco condiciones de `401` devuelven un cuerpo idéntico**, y esa uniformidad es la implementación literal de `spec.md` §11: el cliente no debe poder deducir de la respuesta si el token fue robado, si expiró o si la cuenta fue desactivada. `VAL-002` y `VAL-003` se distinguen en el `error_code` interno **pero comparten mensaje**, y ninguno de los dos llega a decir cuál de las cinco ocurrió.

Que cinco situaciones tan distintas den la misma respuesta al cliente y **efectos internos opuestos** es la característica que define este requerimiento, y es también donde se implementa mal: la tentación es tratar «revocado» como un solo caso.

**Orden de verificación**

1. Formato y obligatoriedad.
2. Límite de tasa por origen.
3. Localizar el token por su hash, **con bloqueo de fila** (§2).
4. Si no existe o expiró → `401`. **Sin revocar nada**: un token que no existe no identifica ninguna sesión.
5. Si está revocado → clasificar por `revoked_reason`:
   - `ROTACION` → `EX-001`: revocar la familia entera con motivo `REUTILIZACION`, evento de severidad **alta**.
   - cualquier otro → `EX-004`: rechazar sin más.
6. Si la familia agotó su duración máxima → `EX-005`: revocar la familia con motivo `SESION_AGOTADA`, evento de **cierre**.
7. Revalidar a la persona: existe, activa, no bloqueada, no eliminada. Si no → `EX-003`: revocar el token presentado.
8. Rotar y emitir.

El paso 5 va **antes** del 6 y del 7 a propósito. Un token ya revocado por rotación es la señal más grave que puede llegar aquí, y evaluar antes la vigencia de la familia o el estado de la persona haría que un robo sobre una sesión caducada o sobre una cuenta desactivada se registrara como otra cosa — perdiendo justo el evento que importa.

## 5. Autorización

| Endpoint | Permiso requerido |
|---|---|
| `POST /api/v1/auth/refresh` | **Ninguno.** Público, autorizado por el propio refresh token |

Es público porque tiene que serlo: quien lo llama tiene el token de acceso expirado o a punto de expirar, que es la razón misma de llamar. Exigir un token de acceso vigente haría el endpoint inútil justo cuando hace falta.

**Debe añadirse a `RUTAS_PUBLICAS` de `SecurityConfig`**, que hoy solo lista la salud del sistema y el inicio de sesión.

**Límite de tasa por origen, más holgado que el del inicio de sesión** (`security.md` §5.5, añadido el 21-08-2026 al aprobar esta spec). El endpoint es igualmente público y consulta la base de datos en cada llamada, de modo que sin cota es un vector de carga; y se declara más holgado porque un cliente legítimo refresca cada quince minutos, no cada segundo. **La cota no es por credencial**, al contrario que en el inicio de sesión: aquí la credencial es de un solo uso por definición, y contar por ella no acotaría nada.

## 6. Auditoría

| Operación | Registro | Contenido |
|---|---|---|
| Refresco exitoso | — | **Ningún evento.** El catálogo de `security.md` §8.1 es cerrado y no lo incluye (`CA-SP-385`) |
| Reutilización (`EX-001`) | `audit_security_log` | `event_type = 'REFRESH_TOKEN_REUSE'`, `severity = 'ALTA'`, `outcome = 'FAILURE'`, `target_user_id` del titular. En `detail`, el **identificador de la familia y el del registro del token**, nunca su valor |
| Familia agotada (`EX-005`) | `audit_security_log` | `event_type = 'LOGOUT'`, `severity = 'INFORMATIVA'`, `outcome = 'SUCCESS'`, con el motivo `SESION_AGOTADA` en `detail` |
| Token revocado por otro motivo (`EX-004`) | — | **Ningún evento.** No hay dos copias en circulación, solo un cliente que reintenta con una credencial retirada a propósito |
| Token inexistente o expirado (`EX-002`) | — | **Ningún evento.** No identifica ninguna sesión ni a ninguna persona |
| Persona inhabilitada (`EX-003`) | — | **Ningún evento.** El evento que importa lo emitió `RF-SP-028`, `RF-SP-029` o `RF-SP-031` cuando tomó la decisión |
| Exceso de peticiones | — | **No se audita**, por el motivo de `RF-SP-034` §6 |
| Fallo no controlado `5xx` | `audit_error_log` | `error_type = 'UNHANDLED'`, `severity = 'ALTA'` |

**`EX-005` se registra como `LOGOUT` y no como un tipo propio**, y conviene justificarlo porque parece un atajo y no lo es. `security.md` §5.4 dice literalmente que agotar el techo «no es un incidente y se registra como un cierre, no como un robo». El catálogo de §8.1 es **cerrado** y no tiene un literal para el vencimiento de sesión; añadirlo obligaría a alterar `ck_audit_security_log_event_type` en una migración, y el valor que aportaría —distinguir en el filtro un cierre voluntario de uno por techo— ya está en `detail`, que es donde `RF-SP-014` puede leerlo. La alternativa de no registrarlo se descartó: entonces una sesión desaparecería sin dejar rastro de por qué.

**Solo se auditan dos de las seis salidas**, y esa parquedad es intencional. El refresco ocurre decenas de veces por jornada y por persona; auditar lo rutinario multiplicaría el volumen de un registro de **retención prolongada** que no se purga sin decisión documentada (Art. XV.8), sin responder ninguna pregunta que el inicio de sesión no responda ya.

## 7. Transaccionalidad

| Elemento | Transacción |
|---|---|
| Bloqueo de la fila, revocación del token presentado y alta del token nuevo | **La misma** |
| Revocación de familia de `EX-001` y de `EX-005` | **La misma** que la lectura que la detectó |
| Evento `REFRESH_TOKEN_REUSE` y evento de `EX-005` | **Independiente**, `REQUIRES_NEW`, **sin esperar al commit** |

La revocación de familia va **dentro** de la transacción que la detecta, y no después: si se hiciera fuera, dos peticiones simultáneas con el token robado podrían ver ambas la fila sin revocar y ninguna cerraría la sesión. Es el mismo argumento del bloqueo de fila de §2, un nivel más arriba.

Los eventos van sin esperar al commit por el motivo de `RF-SP-034` §7: se escriben **mientras** la transacción se revierte. Un intento de reutilización que se pierde porque la respuesta acabó en error es exactamente el que había que conservar.

## 8. Impacto sobre otros módulos

- **`RF-SP-034`** aporta la tabla, el agregado, el puerto y el emisor de tokens. Este requerimiento no crea ninguna tabla.
- **`RF-SP-036`** revoca con motivo `CIERRE`, y depende de que este requerimiento clasifique por motivo para no tratar un cierre como un robo. `CA-SP-388` y `CA-SP-315` lo verifican desde el lado de aquel requerimiento y `CA-SP-383` desde este.
- **`RF-SP-028`, `RF-SP-029`, `RF-SP-031` y `RF-SP-037`** revocan con sus propios motivos y dependen de lo mismo.
- **`RF-SP-030`** cierra aquí su latencia declarada: `FA-001` emite el token con los roles **vigentes**, que es donde se aplica un rol asignado hasta quince minutos antes (`security.md` §4.5).
- **`SecurityConfig`** amplía `RUTAS_PUBLICAS` con esta ruta (§5).
- **Ninguna enmienda a documento transversal.** `security.md` §5.4 y §5.5 ya recogen la rotación, el motivo de revocación, el techo de sesión y el límite de tasa: los cuatro se incorporaron el 21-08-2026 al aprobar esta misma spec.

## 9. Alternativas consideradas

| Alternativa | Por qué se descartó |
|---|---|
| Tratar «token revocado» como un solo caso | Es el defecto que este requerimiento existe para evitar. Cerrar sesión y reintentar produciría un evento de severidad alta en cada cliente mal comportado, y el registro de seguridad se llenaría de incidentes falsos hasta volverse inútil |
| No rotar: devolver el mismo refresh token | El robo dejaría de ser detectable. La rotación es lo único que hace aparecer la señal de «dos copias en circulación» |
| Leer el token sin bloqueo de fila | Dos refrescos concurrentes rotarían los dos y quedarían dos cadenas vivas de la misma sesión — el estado que la rotación existe para hacer imposible (`spec.md` §13) |
| Revocar la familia **después** del commit | Dos peticiones con el token robado podrían ver ambas la fila sin revocar, y ninguna cerraría la sesión |
| Contar la duración máxima desde el último refresco | La sesión no caducaría nunca: bastaría rotar con disciplina para permanecer dentro indefinidamente, que es justo lo que la rotación pretende impedir (`spec.md` §14, pregunta 1) |
| Recorrer la cadena hasta su origen para medir la sesión | Una consulta recursiva por llamada para un dato que `family_started_at` da en la fila |
| Distinguir en la respuesta el token expirado del robado | Permitiría a un atacante confirmar que un token robado sigue vivo. Las cinco condiciones comparten cuerpo a propósito |
| Registrar el refresco exitoso en la auditoría de seguridad | Multiplicaría el volumen de un registro de retención prolongada por el número de refrescos, sin responder nada que el inicio de sesión no responda (`spec.md` §14, pregunta 4) |
| Un literal propio en el catálogo para la sesión agotada | Obliga a alterar `ck_audit_security_log_event_type` para distinguir algo que `detail` ya distingue (§6) |
| No registrar nada cuando la familia se agota | La sesión desaparecería sin dejar rastro de por qué |
| Enviar el refresh token en una cabecera | Los intermediarios registran URL y cabeceras; el cuerpo es lo que mantiene la credencial fuera de esos registros |
| Limitar la tasa por credencial, como en el inicio de sesión | La credencial es de un solo uso por definición: contar por ella no acota nada |

## 10. Riesgos

| Riesgo | Impacto | Mitigación |
|---|---|---|
| `EX-001` y `EX-004` se implementan como un solo caso | **Alto** | Es el defecto característico del requerimiento. `CA-SP-382` y `CA-SP-383` prueban las dos direcciones, y el `CHECK` de `revoked_reason` en `V27` impide que exista una revocación sin motivo que clasificar |
| Falta el bloqueo de fila y dos refrescos concurrentes dejan dos cadenas vivas | **Alto** | `SELECT … FOR UPDATE`, con prueba de integración concurrente. No produce ningún error visible sin la prueba |
| El techo de sesión se cuenta desde el último refresco | **Alto** | `SessionLifetimePolicy` como función pura sobre `family_started_at`, probada con una cadena de refrescos encadenados (`CA-SP-381`) |
| El valor del refresh token acaba en el evento de reutilización | **Alto** | `security.md` §8.3 lo prohíbe. En `detail` van los identificadores del registro y de la familia. `CA-SP-306` |
| El paso 5 se evalúa después del 6 o del 7 y un robo se registra como otra cosa | Medio | Orden declarado en §4 y probado con dobles: robo sobre sesión caducada y sobre cuenta desactivada deben seguir produciendo `REFRESH_TOKEN_REUSE` |
| Los falsos positivos de `EX-001` por dos pestañas refrescando a la vez | Medio | Coste asumido y declarado en `spec.md` §10: no hay forma de distinguir el accidente del robo, y equivocarse en el otro sentido deja al ladrón dentro. El cliente debe serializar sus refrescos |
| La cadena de siete días genera cientos de filas por sesión | Bajo | La purga de `security.md` §5.5 no es opcional. Queda fuera de esta tripleta y sin requerimiento que la cubra — **anotado como hueco** |

## 11. Estrategia de prueba

| Criterio | Nivel | Qué verifica |
|---|---|---|
| `CA-SP-301` | API | Un refresh token válido devuelve token de acceso y refresh nuevos |
| `CA-SP-302` | Integración | El token presentado queda revocado y no sirve una segunda vez |
| `CA-SP-303` | Integración | `replaced_by_id` apunta al token que lo sustituyó |
| `CA-SP-304` | Integración | Presentar un token ya rotado revoca **toda** la familia |
| `CA-SP-305` | Integración | La reutilización deja fila con `REFRESH_TOKEN_REUSE` y severidad alta |
| `CA-SP-306` | Integración | **Ningún** registro contiene el valor del token, tampoco el evento de reutilización |
| `CA-SP-307` | Integración | Persona inactiva, bloqueada o eliminada: rechazo y revocación del token presentado |
| `CA-SP-308` | Integración | El token nuevo lleva los roles **vigentes**, no los del anterior |
| `CA-SP-309` | API | Token inexistente y expirado comparten respuesta, y **ninguna familia** se revoca |
| `CA-SP-381` | Unitaria + Integración | La familia caduca al agotar el techo **aunque la cadena se haya refrescado sin interrupción** |
| `CA-SP-382` | Integración | Cada revocación guarda su motivo, y **solo** `ROTACION` dispara la revocación de familia |
| `CA-SP-383` | Integración | Cerrar sesión y reintentar **no** produce evento alto ni revoca familia |
| `CA-SP-384` | API | Superar el límite por origen devuelve `429` |
| `CA-SP-385` | Integración | El refresco **exitoso** no deja fila en `audit_security_log` |
| `CA-SP-310` | Integración | El token se localiza por su hash y su valor no está almacenado |

Casos límite de `spec.md` §13 con prueba propia (Art. VII.3):

| Caso | Nivel | Qué verifica |
|---|---|---|
| Dos refrescos concurrentes con el mismo token | **Integración concurrente** | Uno gana y **uno solo**; el otro cae en `EX-001` con la familia revocada. Sin `FOR UPDATE` ambos rotan y la prueba falla dejando dos cadenas vivas |
| Persona desactivada mientras refresca | Integración | `RF-SP-028` ya revocó con motivo `ACCESO_RETIRADO`: el paso 5 lo clasifica como `EX-004` y **no** como reutilización. Es el falso positivo más caro |
| Cambio de contraseña durante la sesión | Integración | El refresco siguiente falla como `EX-004`, sin evento alto |
| Refresco justo al expirar el token | Integración | Se rechaza como expirado, sin estado intermedio |
| Refresco desde una IP distinta a la del inicio | API | Se admite: es lo normal en redes móviles |
| Refresh token de un usuario eliminado | Integración | Se rechaza y su registro se conserva |

**El caso de la persona desactivada merece la prueba más cuidadosa de las seis.** Es el punto en que tres excepciones se solapan —hay un token revocado, un motivo que no es rotación y una cuenta que ya no puede entrar— y resolverlo por el camino equivocado registra un robo que no ocurrió, sobre una persona a la que el sistema acaba de desactivar.

`spec.md` §13 lo atribuye a `EX-003` y el orden de §4 lo resuelve por `EX-004`, un paso antes. **No hay contradicción y conviene dejarlo escrito**: lo observable es idéntico —mismo `401`, mismo cuerpo, ningún evento— y el token que `EX-003` mandaría revocar ya está revocado. Lo que la spec exige de ese caso es que **no** se trate como reutilización, y clasificar por motivo antes de mirar a la persona es precisamente lo que lo garantiza. La prueba verifica la ausencia de `REFRESH_TOKEN_REUSE`, no por cuál de las dos ramas se llegó.
