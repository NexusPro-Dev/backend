# PLAN — `RF-SP-039` Consultar el propio perfil

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-039` |
| Especificación | [`spec.md`](spec.md) |
| `spec.md` aprobada el | 22-08-2026 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 24-08-2026 |

---

## 1. Enfoque

Es la consulta más simple del módulo y la que más fácilmente se estropea al implementarla, porque la tentación es reutilizar `RF-SP-026` pasándole el identificador del actor. Hacerlo perdería la propiedad que la hace segura: **esta consulta no admite entrada**, y por eso es imposible desviarla hacia el perfil de otra persona. Un endpoint que acepta un identificador y comprueba que coincide con el actor es una comprobación que un día se olvida; uno que no lo acepta no puede olvidarse de nada.

Lo que sí exige cuidado es de dónde salen los permisos efectivos. Deben resolverse **con el mismo componente que autoriza las peticiones**, no con una consulta paralela escrita para esta pantalla. Es la decisión que `RF-SP-026` tomó al aprobarse su plan, y la razón es que si divergen, la interfaz describiría un sistema distinto del que atiende las peticiones: mostraría un botón que el servidor rechaza, o escondería uno que sí funciona.

Y una asimetría que hay que conocer y no corregir: **el perfil puede mostrar menos permisos de los que el token todavía admite**. La consulta lee del sistema; el token transporta lo que había al emitirse. Es correcto y es lo que `spec.md` §13 declara.

## 2. Cambios de esquema

**Ninguno.** La consulta no escribe nada y todos los datos que devuelve ya existen: `users` (`V18`), `user_roles` (`V19`), `user_memberships` (`V20`), `user_supervisors` (`V21`) y `last_login_at` (`V26`, `RF-SP-034`).

## 3. Componentes afectados

| Capa | Componente | Nuevo / Modificado | Responsabilidad |
|---|---|---|---|
| `application` | `GetOwnProfileQuery` | Nuevo | Caso de uso de lectura. **No recibe ningún identificador**: resuelve el actor del contexto de seguridad |
| `application` | `AuthenticatedActor` | Sin cambios | Puerto de `RF-SP-001`. Aporta el identificador del actor y sus permisos efectivos |
| `application` | `UserDetailQueryRepository` | Sin cambios | Puerto de `RF-SP-026`. **Se reutiliza tal cual**: los datos del perfil propio son un subconjunto de los del detalle |
| `api` | `UserController` | Modificado | Añade `GET /api/v1/users/me` |
| `api` | `OwnProfileResponse` | Nuevo | DTO de salida propio, **distinto** de `UserDetailResponse` (§4) |

**Ningún componente de dominio.** Es una consulta: no hay regla de negocio que aplicar más allá de `RN-SEG-009` y `RN-SEG-002`, que ya viven en la resolución de permisos.

**El repositorio se comparte con `RF-SP-026` y el DTO no**, y esa combinación es deliberada. Compartir la lectura evita dos consultas que puedan divergir; no compartir la salida evita que este endpoint devuelva por descuido los campos administrativos que `CA-SP-471` prohíbe —fechas de creación y modificación, expiración de bloqueo—. Un DTO propio hace que añadirlos exija escribirlos, en lugar de heredarlos.

## 4. Contrato de API

| Método | Ruta | Descripción |
|---|---|---|
| `GET` | `/api/v1/users/me` | Perfil de la persona autenticada |

**`me` es un literal, no un identificador.** No se admite `GET /api/v1/users/{id}` con el propio identificador como forma alternativa: esa es `RF-SP-026` y exige `users:read`.

**Sin parámetros de ningún tipo** — ni de ruta, ni de consulta, ni de cuerpo. `CA-SP-434` y `CA-SP-450` verifican la ausencia, y es la implementación literal de `spec.md` §6.1.

**Respuesta `200`**

```json
{
  "username": "jperez",
  "email": "jperez@ejemplo.com",
  "firstName": "Juan",
  "lastName": "Pérez",
  "status": "ACTIVO",
  "roles": [{ "code": "AGENTE", "name": "Agente o vendedor", "status": "ACTIVO" }],
  "permissions": ["users:read", "roles:read"],
  "membership": { "code": "PREMIUM", "level": 2, "endsAt": null },
  "lastLoginAt": "2026-08-24T09:14:00Z",
  "supervisor": { "username": "amartinez", "firstName": "Ana", "lastName": "Martínez", "roleCode": "DIRECTOR" },
  "mustChangePassword": false
}
```

`membership` y `supervisor` van **ausentes**, no en nulo, cuando no aplican: `spring.jackson.default-property-inclusion` ya está en `non_null` (`application.yml`), de modo que sale gratis y la interfaz distingue «no tiene» de «no se pudo resolver».

**`permissions` no se pagina.** Es el perfil de una sola persona, y partirlo obligaría a la interfaz a pedirlo en trozos para poder pintar un menú (`spec.md` §13).

**Errores**

| Código | Cuándo | `error_code` |
|---|---|---|
| `401` | Sin credencial válida (`EX-001`) | `AUTH-001` |
| `401` | La cuenta del actor fue eliminada tras emitirse su token (`EX-002`) | `AUTH-001` |
| `500` | Fallo no controlado | `ERR-500` |

**No hay `403` ni `404`.** No hay permiso que faltar y no hay recurso que no exista: si el actor está autenticado, su perfil existe por definición — salvo `EX-002`, que se responde como **`401` y no como `404`**, porque lo que ha dejado de ser válido es la sesión, no la ruta.

**No hay `400`.** Sin entrada no hay nada que validar (`spec.md` §11).

## 5. Autorización

| Endpoint | Permiso requerido |
|---|---|
| `GET /api/v1/users/me` | **Ninguno más allá de estar autenticado** |

Es lo que resuelve el agujero que `spec.md` §2 describe: sin este endpoint, quien no tiene `users:read` —la inmensa mayoría del sistema: agentes, estudiantes, clientes— no puede ver ni sus propios datos, y la interfaz no puede ni saludar por su nombre.

**No es alcance de datos por persona**, y por tanto la reserva de `security.md` §6 no lo alcanza: no hay conjunto que acotar, hay un único sujeto que no se elige.

**El `MustChangePasswordFilter` de `RF-SP-034` sí lo alcanza**, y debe ser así. `FA-002` existe precisamente para que la interfaz lleve a la persona a `RF-SP-037` en lugar de dejarla chocar contra el rechazo de todos los endpoints — pero para eso el perfil tiene que poder leerse. **Esta ruta se añade a la excepción del filtro, junto con `RF-SP-037`.**

!!! important "Sin esta excepción, la marca de cambio obligatorio deja la interfaz ciega"

    Con `mcp` en verdadero, el filtro niega todo salvo `RF-SP-037`. Si niega también el perfil, la interfaz no sabe **por qué** la están rechazando —solo que la rechazan— y no puede llevar a la persona a la pantalla que lo resuelve. Es el segundo endpoint que hay que exceptuar y el más fácil de olvidar, porque el primero es evidente y este no.

## 6. Auditoría

**Ninguna.**

Es una consulta de lectura sobre los propios datos, y no aparece en el catálogo cerrado de `security.md` §8.1. Registrarla multiplicaría el volumen de `audit_security_log` por el número de veces que una interfaz pinta su cabecera —varias por sesión y por persona—, sobre un registro de **retención prolongada** que no se purga sin decisión documentada (Art. XV.8).

Es el mismo criterio que `RF-SP-035` §6 aplicó al refresco exitoso, y contrasta a propósito con `RF-SP-014`: leer la auditoría de seguridad **sí** emite `SECURITY_AUDIT_READ`, porque leer lo que hicieron otros no es lo mismo que leer lo propio.

## 7. Transaccionalidad

Solo lectura, en transacción de **solo lectura**. Nada que confirmar y nada que revertir.

## 8. Impacto sobre otros módulos

- **`RF-SP-034`** exceptúa esta ruta en `MustChangePasswordFilter` (§5). Es la dependencia crítica.
- **`RF-SP-026`** comparte `UserDetailQueryRepository` y el componente de resolución de permisos. **No comparte el DTO** (§3).
- **`RF-SP-041`** aporta el superior comercial vigente que este perfil devuelve.
- **`RF-SP-042`** es donde se consulta el equipo. Este perfil **no lo devuelve**, y esa frontera es la que sostiene la reserva de D-22: a quién reporta uno es un dato del actor; quiénes dependen de uno es un conjunto de terceros.
- **Ninguna enmienda a documento transversal.**

## 9. Alternativas consideradas

| Alternativa | Por qué se descartó |
|---|---|
| Resolverlo con `RF-SP-026` pasando el identificador del actor | Convierte la imposibilidad en una comprobación, y las comprobaciones se olvidan. Además obligaría a un endpoint a comportarse de dos maneras según a quién señale el identificador (`spec.md` §2) |
| Admitir `GET /api/v1/users/{id}` con el propio identificador como alternativa | Dos caminos para lo mismo, uno de ellos con permiso y otro sin él. La divergencia es cuestión de tiempo |
| Reutilizar `UserDetailResponse` como salida | Heredaría los campos administrativos que `CA-SP-471` prohíbe. Un DTO propio obliga a escribir lo que se añade |
| Resolver los permisos con una consulta propia para esta pantalla | La interfaz describiría un sistema distinto del que atiende las peticiones (`RF-SP-026`, §1) |
| Devolver también el equipo a cargo | Es alcance por persona y `security.md` §6 lo reserva hasta D-22. `CA-SP-470` lo prohíbe explícitamente |
| Devolver el acceso **anterior** además del actual | Sería la señal de intrusión que la pregunta 1 de `spec.md` §14 buscaba, y reabriría `RF-SP-034`, ya aprobada. Queda como hueco declarado |
| Permitir editar el propio perfil aquí | Convertiría una consulta sin entrada —imposible de desviar— en una escritura con reglas propias (`spec.md` §14, pregunta 3) |
| Paginar los permisos efectivos | La interfaz tendría que pedirlos en trozos para pintar un menú |
| Auditar la consulta | Multiplica un registro de retención prolongada por cada vez que se pinta una cabecera |
| Excluir esta ruta del `MustChangePasswordFilter` sin exceptuarla | Deja a la interfaz sin saber por qué la rechazan (§5) |

## 10. Riesgos

| Riesgo | Impacto | Mitigación |
|---|---|---|
| Se implementa delegando en `RF-SP-026` con el identificador del actor | **Alto** | §3, §4 y §9 lo declaran; `CA-SP-434` verifica que no hay parámetro que admitir |
| Los permisos se resuelven por una vía distinta de la que autoriza | **Alto** | Mismo componente que `RF-SP-026`; `CA-SP-431` y `CA-SP-432` |
| El filtro de cambio obligatorio niega también esta ruta | **Alto** | §5 lo declara y `T-05` lo prueba. Es el más fácil de olvidar de los dos |
| El DTO hereda campos administrativos | Medio | DTO propio; `CA-SP-471` |
| Se añade el equipo a cargo «porque ya se tienen los datos» | Medio | `CA-SP-470`; es la frontera que sostiene D-22 |
| La interfaz interpreta `lastLoginAt` como señal de intrusión | Medio | Hueco declarado en `spec.md` §4.2 y §13. La respuesta no puede evitarlo; la documentación del endpoint debe decirlo |
| Nadie puede corregir sus propios datos | Bajo, y **aceptado** | Declarado. Su síntoma —tráfico de soporte por correcciones triviales— es la condición para registrar el requerimiento que hoy no existe |

## 11. Estrategia de prueba

| Criterio | Nivel | Qué verifica |
|---|---|---|
| `CA-SP-430` | API | Una persona **sin** `users:read` obtiene su perfil completo |
| `CA-SP-431` | Integración | Permisos efectivos resueltos, sin duplicados, como unión de roles activos |
| `CA-SP-432` | Integración | Los permisos de un rol inactivo **no** aparecen |
| `CA-SP-433` | API | Sin roles activos, `permissions` llega vacío y sin error |
| `CA-SP-434` | API | **Ningún** parámetro permite obtener el perfil de otra persona |
| `CA-SP-435` | API | Ningún dato de la credencial, ni transformado |
| `CA-SP-436` | API | El indicador aparece con la marca puesta y desaparece tras `RF-SP-037` |
| `CA-SP-437` | API | Membresía vigente cuando la hay; **ausente** cuando no |
| `CA-SP-438` | API | Sin credencial: `401`, no `403` |
| `CA-SP-439` | API | Cuenta eliminada tras emitirse el token: `401` |
| `CA-SP-440` | Integración | Devuelve el momento de la sesión en curso; nada si nunca ha entrado |
| `CA-SP-441` | Integración | Superior vigente cuando lo hay; ausente si no es fuerza comercial o es la cúspide |
| `CA-SP-470` | API | La respuesta **no** contiene el equipo a cargo |
| `CA-SP-471` | API | **No** contiene fechas de creación ni modificación, ni expiración de bloqueo |
| `CA-SP-472` | API | El endpoint **no** admite ningún método de escritura |

Casos límite de `spec.md` §13 con prueba propia (Art. VII.3):

| Caso | Nivel | Qué verifica |
|---|---|---|
| Persona **con** `users:read` | API | Obtiene exactamente lo mismo que cualquiera. La consulta no tiene dos comportamientos |
| Rol retirado con la sesión abierta | Integración | El perfil refleja el estado real, **aunque el token siga transportando el código retirado**. Es la asimetría declarada |
| Cuenta desactivada durante la sesión | API | No llega hasta aquí: su token de acceso deja de admitirse |
| Primer inicio de sesión | API | `lastLoginAt` ausente solo si se consulta antes de que `RF-SP-034` lo registre |

**La prueba del rol retirado es la que documenta la asimetría** y conviene que exista aunque parezca redundante: sin ella, alguien que encuentre la discrepancia entre el token y el perfil la tomará por un defecto y «arreglará» la consulta para que lea del token — que es exactamente lo contrario de lo que debe hacer.
