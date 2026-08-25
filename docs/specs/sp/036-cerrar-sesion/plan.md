# PLAN — `RF-SP-036` Cerrar sesión

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-036` |
| Especificación | [`spec.md`](spec.md) |
| `spec.md` aprobada el | 21-08-2026 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 24-08-2026 |

---

## 1. Enfoque

Es el requerimiento más corto del bloque y el que cierra el ciclo que abren `RF-SP-034` y `RF-SP-035`: emitir, renovar, terminar.

Su trabajo real cabe en una frase: **revocar el refresh token con motivo `CIERRE`**. Todo lo demás ya está construido — la tabla, el agregado, el puerto y la clasificación por motivo—, y de hecho este plan no aporta ni un componente de dominio propio.

Lo que sí aporta es una decisión que sostiene la honestidad del sistema. Con un token firmado y sin estado, «cerrar sesión» del lado del cliente es borrar una cadena de texto del navegador: si alguien la copió, sigue funcionando. Aquí se revoca en el servidor, de modo que la sesión no puede prolongarse; y lo que quede del token de acceso —quince minutos como mucho— se agota solo. **Esa ventana residual es el límite honesto de la operación** y hay que decirlo en el contrato, no dejarlo implícito: quien necesite cortar el acceso de alguien *ya* usa `RF-SP-028`, no esto.

El motivo `CIERRE` no es un detalle de registro. Es lo que impide que `RF-SP-035` confunda un cierre con un robo, y sin él cerrar sesión y reintentar produciría un evento de severidad alta en cada cliente mal comportado.

## 2. Cambios de esquema

**Ninguno.**

`refresh_tokens` la crea `V27__create_refresh_tokens.sql` (`RF-SP-034`), incluido el literal `CIERRE` en el dominio cerrado de `revoked_reason` y el índice `ix_refresh_tokens_user` sobre `(user_id) WHERE revoked_at IS NULL`, que es exactamente el acceso de la variante de cierre total.

## 3. Componentes afectados

| Capa | Componente | Nuevo / Modificado | Responsabilidad |
|---|---|---|---|
| `domain` | `RefreshToken` | Sin cambios | Agregado de `RF-SP-034`. Aporta la revocación con motivo |
| `domain` | `RefreshTokenRepository` | Sin cambios | Puerto de `RF-SP-034`. Aporta la revocación individual y por usuario |
| `application` | `LogoutService` | Nuevo | Caso de uso. Resuelve el alcance y emite la auditoría |
| `api` | `AuthController` | Modificado | Añade `POST /api/v1/auth/logout` |
| `api` | `LogoutRequest` | Nuevo | DTO de entrada: token y alcance |

**Ningún componente de dominio propio**, igual que en `RF-SP-033`, y por la misma razón: si esta operación necesitara lógica que ninguna otra tiene, sería señal de que está haciendo algo más que revocar una credencial.

## 4. Contrato de API

| Método | Ruta | Descripción |
|---|---|---|
| `POST` | `/api/v1/auth/logout` | Revoca el refresh token de la sesión, o todos los de la persona |

**Petición**

```json
{
  "refreshToken": "9f2c...",
  "allSessions": false
}
```

`allSessions` es opcional y por defecto falso. Es un dato de entrada y no una ruta aparte porque la variante comparte actor, autorización y reglas con el cierre simple; lo único que cambia es el alcance de la revocación (`spec.md` §14, pregunta 1).

**Respuesta `204 No Content`**, sin cuerpo. `spec.md` §6.2 declara la salida como confirmación sin datos.

**Errores**

| Código | Cuándo | `error_code` |
|---|---|---|
| `400` | Refresh token ausente o con formato inválido (`EX-002`) | `VAL-001` |
| `429` | Límite por origen superado | `ERR-429` |
| `500` | Fallo no controlado | `ERR-500` |

!!! warning "Enmienda a `spec.md` §10 — el token no reconocido devuelve `204`, no un rechazo (Art. I.7)"

    `EX-001` declara que un refresh token que no corresponde a ninguna sesión se rechaza «con la misma respuesta que un formato inválido». Combinado con `FA-001` —que confirma sin error cuando el token ya estaba revocado o expirado— eso **reintroduce exactamente el oráculo que la pregunta 3 de §14 dice evitar**: un valor real, aunque esté revocado, devuelve `204`; un valor inventado devuelve `400`. Bastan dos peticiones para comprobar si una cadena de texto es un refresh token del sistema.

    Este plan lo cierra por el único camino que no deja rendija: **todo token sintácticamente válido devuelve `204`**, exista o no, esté vigente o revocado. El `400` queda reservado a lo que se decide **mirando solo el cuerpo** —token ausente o con formato imposible—, que es la misma frontera que `RF-SP-030` §4 fijó para el resto del módulo.

    No se pierde nada por el camino. El resultado que la operación promete —que ese token no sirva— se cumple en los tres casos, y la idempotencia que `FA-001` ya exigía simplemente se extiende al cuarto. Y no oculta un error real a un cliente legítimo: quien cierra sesión con su propio token nunca cae en este caso.

    `EX-001` queda absorbido por `FA-001`, y `VAL-002` de §11 deja de tener respuesta propia — su comprobación sigue existiendo, pero su resultado ya no es observable. `CA-SP-314` se amplía en consecuencia.

**Orden de verificación**

1. Formato y obligatoriedad del token.
2. Límite de tasa por origen.
3. Localizar el token por su hash. Si no existe → `204`, sin escribir ni auditar.
4. Si ya estaba revocado o expirado → `204`, sin escribir ni auditar (`FA-001`).
5. Revocar con motivo `CIERRE` — el token, o todos los vigentes de la persona si `allSessions`.

## 5. Autorización

| Endpoint | Permiso requerido |
|---|---|
| `POST /api/v1/auth/logout` | **Ninguno.** Público, autorizado por el propio refresh token |

`requirements/sp.md` §9 ya lo declara público desde el 21-08-2026. **Debe añadirse a `RUTAS_PUBLICAS` de `SecurityConfig`**, junto con la de `RF-SP-035`.

Es público porque tiene que serlo, y el argumento es más fuerte que en el refresco: **revocar la credencial de uno mismo no concede nada a nadie**, de modo que no hay privilegio que proteger. Exigir un token de acceso vigente dejaría sin poder cerrar sesión a quien vuelve pasados quince minutos — justo el caso más frecuente (`CA-SP-386`).

Nadie cierra la sesión de otro por este camino: el token presentado identifica a su titular y no hay forma de dirigir la operación a un tercero. La variante `allSessions` alcanza a **las sesiones de quien presenta el token**, nunca a las de otra persona.

**Límite de tasa por origen**, con la cota holgada del refresco. No lo exige `security.md` §5.5, que solo nombra el inicio de sesión y el refresco, pero este endpoint es igualmente público y consulta la base de datos en cada llamada. Se añade por coherencia y se declara aquí; no se enmienda §5.5, porque una cota razonable en un endpoint que no concede nada no es una regla de seguridad que deba subir al documento transversal.

## 6. Auditoría

| Operación | Registro | Contenido |
|---|---|---|
| Cierre efectivo | `audit_security_log` | `event_type = 'LOGOUT'`, `severity = 'INFORMATIVA'`, `outcome = 'SUCCESS'`, `target_user_id` del titular. En `detail`, el alcance —esta sesión o todas— y **cuántas** se revocaron |
| Token inexistente, ya revocado o expirado | — | **Ningún evento**: nada cambió (`CA-SP-314`) |
| Formato inválido (`400`) y exceso de peticiones (`429`) | — | **No se auditan** |
| Fallo no controlado `5xx` | `audit_error_log` | `error_type = 'UNHANDLED'`, `severity = 'ALTA'` |

**Un solo evento también en la variante total**, aunque revoque diez sesiones. El evento documenta una decisión —«esta persona cerró todo»—, no diez revocaciones, y el número va en `detail` para quien lo necesite. Es el mismo criterio que `RF-SP-005` §6 aplicó a los permisos.

**`actor_id` y `target_user_id` son la misma persona**, y es el único evento del módulo donde eso ocurre por construcción. No se colapsan en uno: `RF-SP-014` filtra por `target_user_id` para responder «qué le pasó a esta cuenta», y dejarlo nulo aquí sacaría los cierres de sesión de esa consulta.

## 7. Transaccionalidad

| Elemento | Transacción |
|---|---|
| Revocación del token, o de todos los de la persona | **La misma** |
| Evento `LOGOUT` | **Independiente**, `REQUIRES_NEW`, enganchada al commit |

El evento **sí espera al commit**, al contrario que los de `RF-SP-034` y `RF-SP-035`. La diferencia es qué se está afirmando: allí se registra que alguien **intentó** algo, y el intento ocurrió aunque la transacción se revierta; aquí se registra que una sesión **quedó cerrada**, y si la revocación se revierte eso no es cierto. Un `LOGOUT` de una sesión que sigue viva es un evento fantasma.

## 8. Impacto sobre otros módulos

- **`RF-SP-034`** aporta la tabla, el agregado, el puerto y el literal `CIERRE`. Este requerimiento no crea nada.
- **`RF-SP-035`** depende de que la revocación lleve motivo `CIERRE` para no tratarla como reutilización. `CA-SP-388` lo verifica desde aquí y `CA-SP-383` desde allí; son la misma garantía vista por sus dos lados.
- **`SecurityConfig`** amplía `RUTAS_PUBLICAS` con esta ruta y la de `RF-SP-035`.
- **`RF-SP-028`** es la operación de contención, no esta. El contrato lo dice de forma explícita para que nadie use el cierre de sesión esperando un corte inmediato.
- **`spec.md` §10** se enmienda por §4: `EX-001` queda absorbido por `FA-001`.
- **Ninguna enmienda a documento transversal.**

## 9. Alternativas consideradas

| Alternativa | Por qué se descartó |
|---|---|
| Rechazar el token no reconocido, como declaraba `EX-001` | Deja un oráculo: dos peticiones bastan para comprobar si un valor es un token real del sistema (§4) |
| Exigir token de acceso vigente | Deja sin cerrar sesión a quien vuelve pasados quince minutos, que es el caso más frecuente. Y revocar la credencial de uno mismo no concede nada (`spec.md` §14, pregunta 3) |
| Identificar la sesión por el `jti` del token de acceso | Introduciría un vínculo nuevo entre ambos tokens que hoy no existe y que solo serviría para esto; y dejaría de funcionar justo en el caso más frecuente (`spec.md` §14, pregunta 2) |
| Un requerimiento aparte para el cierre total | Comparte actor, autorización y reglas. Lo único distinto es el alcance, que cabe en un campo de entrada |
| Invalidar también el token de acceso al instante | Exigiría consultar el estado en cada petición, que es lo que D-08 evita. La operación de contención es `RF-SP-028`, y el contrato lo dice |
| Un evento por sesión revocada en la variante total | Multiplica las filas sin añadir información: es una decisión, no diez |
| Emitir el evento sin esperar al commit | Un `LOGOUT` de una sesión que sigue viva es un evento fantasma (§7) |
| Enviar el refresh token en una cabecera | Los intermediarios registran cabeceras y URL; el cuerpo mantiene la credencial fuera de esos registros |

## 10. Riesgos

| Riesgo | Impacto | Mitigación |
|---|---|---|
| Se revoca sin motivo, o con un motivo distinto de `CIERRE`, y `RF-SP-035` lo trata como robo | **Alto** | El `CHECK` de `V27` impide la revocación sin motivo; `CA-SP-388` y `CA-SP-315` verifican el literal y su efecto |
| La variante total alcanza sesiones de otra persona | **Alto** | El alcance se resuelve **desde el titular del token presentado**, nunca desde un identificador de entrada. No hay campo por el que dirigirla a un tercero |
| El token no reconocido devuelve `400` y reabre el oráculo | Medio | Enmienda de §4, con `CA-SP-314` ampliado a los cuatro casos |
| Se espera que cerrar sesión corte el acceso al instante | Medio | Límite declarado en `spec.md` §2 y en el contrato. La operación de contención es `RF-SP-028` |
| El evento se emite antes del commit | Medio | Enganchado al commit (§7) |

## 11. Estrategia de prueba

| Criterio | Nivel | Qué verifica |
|---|---|---|
| `CA-SP-311` | Integración | Tras el cierre, el refresco con ese token se rechaza |
| `CA-SP-312` | API | El token de acceso vigente **sigue admitiéndose** hasta que expira |
| `CA-SP-313` | Integración | Las demás sesiones de la persona siguen abiertas |
| `CA-SP-314` | API | `204` sin evento en los **cuatro** casos sin efecto: ya cerrada, expirada, revocada por otro motivo y **token inexistente** (enmienda de §4) |
| `CA-SP-315` | Integración | Cerrar dos veces **no** se trata como reutilización ni revoca la familia |
| `CA-SP-386` | API | El cierre funciona con el token de acceso expirado y sin presentarlo en absoluto |
| `CA-SP-387` | Integración | La variante total revoca **todas** las sesiones de la persona en una sola petición |
| `CA-SP-388` | Integración | La revocación queda con motivo `CIERRE`, y `RF-SP-035` no la trata como reutilización |
| `CA-SP-317` | Integración | Una fila en `audit_security_log` con `LOGOUT` y severidad informativa |
| `CA-SP-318` | Integración | **Ningún** registro contiene el valor del token presentado |

Casos límite de `spec.md` §13 con prueba propia (Art. VII.3):

| Caso | Nivel | Qué verifica |
|---|---|---|
| Cierre seguido de refresco con el mismo token | **Integración de dos requerimientos** | El refresco lo rechaza **sin** revocar familia y **sin** evento alto, porque el motivo es `CIERRE`. Es `EX-004` de `RF-SP-035` |
| Cierres concurrentes con el mismo token | **Integración concurrente** | Se serializan sobre la fila; el segundo cae en `FA-001` y no deja segundo evento |
| Cuenta desactivada mientras la sesión estaba abierta | Integración | `RF-SP-028` ya revocó: el cierre cae en `FA-001` y se confirma sin más |
| Cierre desde un dispositivo distinto | API | Se admite: la sesión la identifica el token, no el dispositivo |
| Persona con una sola sesión que la cierra | Integración | Queda sin ninguna sesión abierta, estado normal |

**La prueba del cierre seguido de refresco es la única del bloque B que cruza dos requerimientos**, y por eso debe vivir en un solo sitio y ejecutar las dos operaciones seguidas. Repartida entre las dos tripletas, cada mitad pasaría sin que nadie comprobara que el motivo registrado por una es el que la otra lee — que es toda la garantía.
