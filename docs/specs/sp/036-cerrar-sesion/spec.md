# SPEC — `RF-SP-036` Cerrar sesión

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-036` |
| Módulo | `SP` — Sistema Principal |
| Estado | **Aprobada** |
| Autor | Responsable técnico |
| Aprobada por | Responsable técnico |
| Fecha de aprobación | 21-08-2026 |
| Enmendada | 24-08-2026 — §10: `EX-001` queda absorbido por `FA-001`; un token no reconocido devuelve `204` y no un rechazo, porque distinguirlos reintroducía el oráculo que la pregunta 3 de §14 evita. Al aprobarse el [`plan.md`](plan.md) §4 (Art. I.7) |

---

## 1. Objetivo

Terminar la sesión de quien lo pide, de modo que su credencial de vida larga deje de servir.

## 2. Contexto

Cerrar sesión parece trivial y no lo es. Con un token firmado y sin estado, «cerrar sesión» del lado del cliente es simplemente borrarlo del navegador: si alguien lo copió antes, sigue funcionando hasta que expire. Un cierre que solo borra en el cliente es una promesa que el sistema no cumple.

Aquí sí se cumple, y esa es la razón de existir del refresh token persistido. El cierre **revoca el refresh token en el servidor**: la sesión no puede prolongarse, y lo que quede del token de acceso —quince minutos como mucho— se agota solo. Es la parte que hace verdadera la decisión D-08.

Esa ventana residual es el límite honesto de esta operación y conviene decirlo: cerrar sesión **no** invalida el token de acceso al instante. Si lo que hace falta es cortar el acceso de alguien **ya**, la operación es `RF-SP-028`, que sí exige verificar el estado vigente en cada petición. Cerrar sesión es un acto voluntario de quien se va, no una medida de contención.

## 3. Actores

| Actor | Rol en esta funcionalidad |
|---|---|
| Cualquier persona con una sesión abierta | Cierra su sesión actual, o todas las suyas |

No hay permiso asociado. **El endpoint es público y su autorización es el propio refresh token**, igual que el de refresco: revocar la credencial de uno mismo no concede nada a nadie, y exigir un token de acceso vigente dejaría sin poder cerrar sesión a quien vuelve pasados quince minutos —justo cuando más querría hacerlo—. Nadie cierra la sesión de otro por este camino: el token presentado identifica a su titular.

## 4. Alcance

### 4.1 Incluye

- Revocación del refresh token de la sesión que se cierra, con motivo `CIERRE`.
- **Cierre de todas las sesiones** de la persona, como variante de la misma operación.
- Registro del cierre en la auditoría de seguridad.

### 4.2 No incluye

- Invalidar el token de acceso ya emitido, que se agota por vigencia.
- Cerrar la sesión de otra persona: el token presentado identifica a su titular, y no hay forma de dirigir la operación a un tercero.
- Cambiar la contraseña → `RF-SP-037`, que además revoca todas las sesiones por su cuenta.

## 5. Reglas de negocio aplicables

| ID | Regla | Origen |
|---|---|---|
| `RNF-SEG-006` | Los eventos de seguridad quedan registrados en la auditoría de seguridad | `security.md` §11 |

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción | Restricción de negocio |
|---|---|---|---|
| Refresh token | Sí | Credencial de la sesión que se cierra, y lo que autoriza la operación | Nunca se registra en ningún log |
| Alcance | No | Si se cierra solo esta sesión o todas las de la persona | Por defecto, solo esta |

### 6.2 Salida

| Dato | Descripción |
|---|---|
| Confirmación | Resultado de la operación, sin cuerpo de datos |

## 7. Precondiciones y postcondiciones

**Precondiciones**

- Se presenta un refresh token que identifica una sesión de la persona.


**Postcondiciones**

- El refresh token queda revocado y no puede volver a usarse.
- La sesión no puede prolongarse: el siguiente refresco se rechaza.
- El token de acceso vigente sigue siendo válido hasta que expire, como mucho quince minutos.
- Las demás sesiones de la persona, si las hay, **no se ven afectadas** — salvo que se pidiera cerrarlas todas, en cuyo caso quedan revocadas con el mismo motivo.
- Queda constancia en la auditoría de seguridad con severidad informativa.

## 8. Flujo principal

1. La persona solicita cerrar sesión presentando su refresh token, e indica si quiere cerrar solo esa sesión o todas.
2. El sistema localiza el token por su hash y resuelve a qué persona pertenece.
3. El sistema revoca el token —o todos los de la persona, si se pidió— con motivo `CIERRE`, que es lo que impide que `RF-SP-035` lo confunda con una reutilización sospechosa.
4. El sistema registra el cierre en la auditoría de seguridad, con severidad informativa.
5. El sistema confirma la operación.

## 9. Flujos alternativos

### FA-001 — El token ya estaba revocado o expirado

**Cuándo ocurre:** la sesión ya se había cerrado, el token ya había caducado, o la cuenta fue desactivada y `RF-SP-028` ya lo revocó.

1. El sistema confirma la operación sin registrar evento.
2. **No** se trata como error: el resultado que se pedía —que ese token no sirva— ya se cumplía. La operación es idempotente.
3. Tampoco se trata como reutilización sospechosa: cerrar sesión dos veces no es señal de robo, a diferencia de `RF-SP-035`.

## 10. Excepciones

### EX-001 — Refresh token inexistente o no reconocido

**Condición:** el token presentado no corresponde a ninguna sesión.
**Respuesta del sistema:** rechaza la petición con la misma respuesta que un formato inválido, e indica que la solicitud no es válida. No distingue ambos casos: el endpoint es público, y separarlos permitiría comprobar si un valor es un token real.

### EX-002 — Refresh token ausente o con formato inválido

**Condición:** no se presenta token, o su formato no corresponde al de un refresh token.
**Respuesta del sistema:** rechaza la petición e indica que la solicitud no es válida.

## 11. Validaciones

| ID | Validación | Mensaje esperado |
|---|---|---|
| `VAL-001` | Refresh token obligatorio | La solicitud no es válida. |
| `VAL-002` | El token corresponde a una sesión existente | La solicitud no es válida. |

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-SP-311` | El sistema revoca el refresh token presentado y el refresco posterior se rechaza |
| `CA-SP-312` | El token de acceso vigente sigue admitiéndose hasta que expira |
| `CA-SP-313` | Las demás sesiones de la persona siguen abiertas tras cerrar una |
| `CA-SP-314` | Cerrar una sesión ya cerrada se confirma sin error y sin registrar evento |
| `CA-SP-315` | Cerrar una sesión ya cerrada **no** se trata como reutilización sospechosa ni revoca la familia |
| `CA-SP-386` | El cierre funciona **con el token de acceso expirado**, o sin presentarlo en absoluto |
| `CA-SP-387` | La variante de cierre total revoca **todas** las sesiones de la persona en una sola petición |
| `CA-SP-388` | La revocación queda registrada con motivo `CIERRE`, y `RF-SP-035` no la trata como reutilización sospechosa |
| `CA-SP-317` | El sistema registra el cierre en la auditoría de seguridad con severidad informativa |
| `CA-SP-318` | Ningún registro contiene el valor del refresh token presentado |

## 13. Casos límite

- **Cierre seguido de un refresco con el mismo token:** el refresco lo rechaza como token revocado **sin** disparar la revocación de familia, porque el motivo registrado es `CIERRE` y no rotación. Es `EX-004` de `RF-SP-035`.
- **Cierre desde un dispositivo distinto de aquel en que se abrió la sesión:** se admite mientras el token sea el de esa sesión. La sesión la identifica el token, no el dispositivo.
- **Cierre con el token de acceso ya expirado pero el refresh todavía vigente:** se admite. El endpoint no exige token de acceso, y es justamente el caso más frecuente: quien vuelve pasado un rato y quiere cerrar sesión.
- **Cuenta desactivada mientras la sesión estaba abierta:** `RF-SP-028` ya revocó el token; el cierre cae en `FA-001` y se confirma sin más.
- **Cierres concurrentes con el mismo token:** ambos se serializan sobre la fila; el segundo cae en `FA-001`.
- **Persona con una sola sesión que la cierra:** queda sin ninguna sesión abierta, que es un estado normal y no requiere nada especial.

## 14. Preguntas abiertas

Ninguna. Las tres se resolvieron el 21-08-2026, antes de aprobar la especificación. La tercera cambió la naturaleza del endpoint, que pasó de autenticado a público.

| # | Pregunta | Resolución |
|---|---|---|
| 1 | ¿Debe existir un cierre de todas las sesiones? | **Sí, como variante de esta misma operación**, no como requerimiento aparte. Es lo que se busca cuando alguien sospecha que su cuenta fue comprometida —«ciérrame la sesión en todas partes»—, y hasta ahora la única forma era que un administrador desactivara y reactivara la cuenta: desproporcionado, y dejaba a la persona sin poder resolverlo sola. No justifica un requerimiento propio porque comparte actor, autorización y reglas con el cierre simple; lo único que cambia es el alcance de la revocación, que viaja como un dato de entrada. `CA-SP-387` lo verifica |
| 2 | ¿El refresh token se envía en el cuerpo, o basta con el token de acceso para identificar la sesión? | **El refresh token, en el cuerpo.** Es lo que dibuja `security.md` §5.3, y el cliente ya lo conserva para poder refrescar. La alternativa —guardar el `jti` del token de acceso junto a cada refresh token y cerrar por él— evitaría enviar la credencial más sensible, pero introduciría un **vínculo nuevo entre ambos tokens** que hoy no existe y que solo serviría para esto. Con la resolución 3, además, esa alternativa dejaría de funcionar en el caso más frecuente: cerrar sesión cuando el token de acceso ya expiró |
| 3 | ¿Puede cerrarse la sesión con el token de acceso expirado? | **Sí, y eso convierte el endpoint en público**, autorizado por el propio refresh token igual que el de refresco. Antes no podía: la petición se rechazaba por autenticación y la persona se quedaba sin poder revocar un refresh token que seguía vivo hasta siete días, justo en el momento en que más querría cerrarlo. **Revocar la credencial de uno mismo no concede nada a nadie**, de modo que no hay privilegio que proteger. Como contrapartida, el endpoint deja de poder distinguir «token de otra persona» de «token inexistente»: ambos reciben la misma respuesta genérica (`EX-001`), porque separarlos permitiría comprobar si un valor es un token real. `CA-SP-386` verifica el caso |
