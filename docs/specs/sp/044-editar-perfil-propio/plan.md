# PLAN — `RF-SP-044` Editar el propio perfil

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-044` |
| Módulo | `SP` — Sistema Principal |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 31-08-2026 |
| Spec | [`spec.md`](spec.md) |

---

## 1. Enfoque

**Servicio propio y no una rama dentro de `UpdateUserService`.** Es el mismo argumento con el que `RF-SP-039` no se resolvió dentro de `RF-SP-026`: la autorización es distinta —ningún permiso frente a `users:update`—, el sujeto es distinto —siempre el actor frente a un identificador de la ruta— y las reglas de rechazo son distintas —aquí hay una contraseña que comprobar—. Meterlo en el servicio existente lo obligaría a comportarse de dos maneras según quién lo llame, que es justo lo que hace imposible especificar por separado cuándo rechaza cada uno.

**La respuesta se reutiliza.** `OwnProfileResponse` ya existe y es lo que devuelve `GET /api/v1/users/me`: que la escritura devuelva exactamente la misma forma que la lectura evita que una interfaz tenga que mezclar dos representaciones del mismo perfil.

**El tipo `Patchable<String>` se hereda tal cual** de `RF-SP-027`. Distingue las tres cosas que un `PATCH` necesita separar —campo ausente, campo informado y campo puesto a nulo— y ya está resuelto en `shared/patch`.

## 2. Cambios de esquema

**Ninguno.** No hay columna nueva, ni tabla, ni migración. Se escriben `users.first_name`, `users.last_name` y `users.email`, que existen desde `V18`, y se lee `users.password_hash`.

Que un requerimiento no toque el esquema es lo esperado aquí: lo que cambia es **quién** puede escribir esas columnas y **bajo qué prueba**, no qué se guarda.

## 3. Componentes afectados

| Componente | Capa | Acción |
|---|---|---|
| `UpdateOwnProfileRequest` | `application` | **Nuevo.** Tres `Patchable<String>` heredados de `UpdateUserRequest` más `currentPassword` como `String` llano |
| `UpdateOwnProfileService` | `domain/service` | **Nuevo.** El caso de uso |
| `OwnProfileResponse` | `application` | Se reutiliza sin tocar |
| `UserRepository` | `domain/repository` | Se reutiliza: la búsqueda por identificador y la comprobación de correo en uso ya están |
| `PasswordHasher` | `shared/security` | Se reutiliza para comparar la contraseña actual |
| `UserController` | `interfaces` | Gana `@PatchMapping("/me")` |
| `MustChangePasswordFilter` | `shared/security` | **No se toca, y esa es la decisión.** Ver §5 |

## 4. Contrato de API

`PATCH /api/v1/users/me`

```json
{
  "firstName": "William",
  "lastName": "Bonilla Díaz",
  "email": "nuevo@dominio.co",
  "currentPassword": "la contraseña vigente"
}
```

Los cuatro campos son opcionales en el cuerpo. `currentPassword` es **obligatorio si y solo si** viene `email`.

| Código | Cuándo |
|---|---|
| `200` | Perfil actualizado. Devuelve `OwnProfileResponse`, la misma forma que `GET /users/me` |
| `400` | Ningún campo informado (`VAL-001`), campo vaciado (`VAL-002`), correo inválido (`VAL-003`), longitud excedida (`VAL-005`), falta la contraseña actual habiendo correo (`VAL-006`) o campo desconocido |
| `401` | Sin token o con token no admitido |
| `403` | El actor tiene pendiente el cambio obligatorio de contraseña (`CL-003`) |
| `409` | El correo ya está en uso (`EX-001` / `VAL-004`) |
| `422` | La contraseña actual no es correcta (`EX-002` / `VAL-007`) |

**No hay `404`.** El sujeto es quien porta el token: si el token se admite, la persona existe.

**`422` y no `400` para la contraseña incorrecta**, con el mismo criterio que `RF-SP-040`: la petición está bien formada y lo que falla es una comprobación contra el estado del sistema. Un `400` diría que el cliente escribió mal el cuerpo.

## 5. Autorización

**Autenticado y nada más**, igual que `RF-SP-039` y `RF-SP-037`. Sin `@PreAuthorize`: exigir cualquier permiso reintroduciría el problema que este requerimiento existe para resolver.

**El sujeto sale del contexto de seguridad, nunca del cuerpo.** Ni se acepta un identificador ni se mira si coincide con el del token: no existe el campo. Es lo que hace que `CA-SP-495` sea verificable por construcción y no por una comprobación que alguien pueda olvidar.

**`MustChangePasswordFilter` no incorpora esta ruta, y es deliberado.** Su mapa `ALCANZABLE_CON_LA_MARCA` enumera lo que se alcanza con el cambio obligatorio pendiente, y cada entrada tiene su motivo escrito: cambiar la contraseña —que es quien limpia la marca—, leer el propio perfil —para saber por qué te rechazan—, y las cuatro de sesión. **Editar el perfil no pertenece a ese conjunto**: quien tiene una credencial provisional sin estrenar debe estrenarla antes de tocar nada, y muy especialmente antes de tocar el correo, porque esa credencial la conoce quien la emitió.

## 6. Auditoría

| Evento | Cuándo | Registro |
|---|---|---|
| Cambio de datos | Algún campo cambió de valor | `audit_change_log`, con el antes y el después de cada campo modificado, con el propio actor como autor |
| Cambio de correo | El correo cambió | Además `audit_security_log`, severidad **alta**, mismo criterio que `RF-SP-027` |
| Contraseña actual incorrecta | `EX-002` | `audit_security_log`, resultado `FAILURE`, con el actor como objetivo |

**Nada del contenido de la contraseña llega al registro**, ni siquiera su longitud (Art. IV.8).

**El evento de fallo se registra fuera de la transacción de escritura**, porque no hay escritura que confirmar: la operación se rechaza y aun así el intento tiene que sobrevivir. Es el mismo tratamiento que `RequestPasswordRecoveryService` da a su fallo.

## 7. Transaccionalidad

Una sola transacción para comprobar y escribir. La comprobación de correo en uso y la escritura van dentro: separarlas deja una ventana en la que dos personas toman el mismo correo, y `uq_users_email` lo convertiría en un `500` en lugar de en el `409` que la spec pide.

**La verificación de la contraseña va antes de tocar nada** y fuera de cualquier bloqueo: es una comparación de hash, no toca la base más que para leer el hash del propio actor.

## 8. Impacto sobre otros módulos

**Ninguno.** No se publica interfaz, no se consume ninguna nueva y ningún módulo depende de este comportamiento.

**Enmiendas a documentos transversales**, que este plan aplica en el mismo pase:

| Documento | Cambio |
|---|---|
| `requirements/sp.md` | Registra `RF-SP-044` en §6.1, su ficha, y la ruta en la tabla de endpoints. Fila de control de cambios y versión |
| `requirements.md` | Fila en la matriz de trazabilidad §4 |

`security.md`, `architecture.md` y `modelo-datos.md` **no se tocan**: no hay regla de seguridad nueva —la exigencia de contraseña es una validación de este caso de uso, no una regla transversal—, ni componente de arquitectura, ni columna.

## 9. Alternativas consideradas

| Alternativa | Por qué se descarta |
|---|---|
| **Reutilizar `PATCH /users/{id}` comprobando que el id sea el del actor** | Obliga a aquel endpoint a autorizar de dos maneras: con `users:update` para otros y sin nada para uno mismo. Un fallo en esa bifurcación es una escalada de privilegios, y la forma de no tener ese fallo es no tener la bifurcación |
| **No pedir la contraseña para el correo** | Convierte el robo de una sesión en apropiación permanente de la cuenta, porque el correo es la vía de recuperación de `RF-SP-040` |
| **Pedir la contraseña para cualquier cambio** | Cobra el precio donde no hay riesgo: equivocar un apellido no abre ninguna puerta, y obligar a teclear la contraseña para corregirlo empuja a la gente a no corregirlo |
| **Verificar el correo nuevo por enlace antes de aplicarlo** | Es lo correcto y no existe la infraestructura: exige tabla de verificaciones, plantilla, caducidad y un flujo de confirmación. Queda como `RF` futuro en §14 de la spec, y **no bloquea** — hoy el dato mal escrito ni siquiera se puede corregir |
| **Dejar el correo fuera del alcance** | Deja a medias justo el campo que más se corrige, y obliga a molestar a un administrador para el caso más común |

## 10. Riesgos

| Riesgo | Mitigación |
|---|---|
| **Un correo mal tecleado deja a la persona sin vía de recuperación**, porque no se verifica | Declarado en §14 de la spec. El nombre de usuario sigue sirviendo para iniciar sesión y un administrador puede corregirlo con `RF-SP-027`: nadie queda encerrado |
| Alguien con una sesión ajena cambia nombre y apellidos sin contraseña | Aceptado: es un daño reversible y no da acceso. Queda en la auditoría de cambios con el actor |
| La comparación de contraseña se convierte en un oráculo de fuerza bruta | El límite de tasa cubre el endpoint como al resto. **No incrementa los intentos fallidos de inicio de sesión** a propósito (`CA-SP-504`): hacerlo permitiría a quien tenga una sesión ajena bloquear a la persona legítima |

## 11. Estrategia de prueba

| Nivel | Qué cubre |
|---|---|
| Unitaria | `UpdateOwnProfileService` con dobles: la exigencia condicional de contraseña, `FA-001` sin cambio efectivo y que el correo repetido siga exigiéndola |
| Integración (`UpdateOwnProfileIT`) | Los trece criterios de aceptación contra la base real, incluida la comprobación de que roles, membresía, estado y superior quedan intactos |
| Integración | Que un actor **sin ningún permiso** pueda ejecutarla, con una persona de la semilla que no sea administradora |
| Integración | Que la ruta **no** sea alcanzable con la marca de cambio obligatorio (`CL-003`) |
