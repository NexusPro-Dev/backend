# SPEC — `RF-SP-071` Activar el segundo factor con una app autenticadora

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-071` |
| Módulo | `SP` — Sistema Principal |
| Versión | 0.1.0 |
| Estado | **Aprobada** |
| Autor | Responsable técnico |
| Aprobada por | Responsable del proyecto |
| Fecha de aprobación | 06-10-2026 |

!!! info "Qué va en este documento"

    **Qué debe pasar, y por qué.** Nada más.

    **Prueba de pertenencia:** si un cambio de tecnología lo invalidaría, no pertenece aquí — va a `plan.md`. No se nombran tablas, clases, endpoints ni librerías.

---

## 1. Objetivo

Que una persona **vincule una app autenticadora a su cuenta** —Google Authenticator, Microsoft Authenticator, Authy o cualquier otra—, de modo que desde entonces entrar exija, además de la contraseña, el código de seis dígitos que esa app muestra. Y que, al hacerlo, reciba los **códigos de recuperación** con los que volver a entrar si pierde el teléfono.

---

## 2. Contexto

Hasta hoy la contraseña era la única prueba de identidad del sistema. Una contraseña reutilizada en otro servicio, filtrada o adivinada basta para entrar en la cuenta de un administrador que confirma pagos y aprueba retiros. El responsable del proyecto decidió el 06-10-2026 añadir un segundo factor ([`requirements/sp.md`](../../../requirements/sp.md) v1.93.0 §2, [`security.md`](../../../security.md) v0.105.0 §3.3):

### 2.1 Lo que este requerimiento decide

| Decisión | Qué se decidió |
|---|---|
| **El factor es una app autenticadora** | El estándar de código por tiempo que todas entienden. Ni SMS ni correo: el correo ya es la vía de recuperación de la contraseña, y el segundo factor dejaría de ser segundo |
| **Dos pasos: iniciar y confirmar** | Iniciar entrega lo que hay que escanear; el factor **no protege nada** hasta que la persona prueba, con un primer código, que su app lo calcula bien. Si se activara al iniciar, un escaneo fallido dejaría a la persona fuera de su propia cuenta |
| **Lo pendiente caduca** | A los **diez minutos**. Basta para escanear con calma y no deja secretos olvidados esperando |
| **Uno activo y uno pendiente, como mucho** | Iniciar otra vez sustituye al pendiente anterior |
| **Cambiar de teléfono es activar otra vez** | Con un factor activo, iniciar y confirmar otro **retira el anterior**. Pero iniciar exige haber presentado un código **hace poco** (`RF-SP-073`): sin eso, una sesión robada cambiaría el authenticator de su víctima por el suyo |
| **Diez códigos de recuperación, vistos una vez** | Llegan **solo** en la respuesta de la confirmación. El sistema no puede volver a mostrarlos: solo regenerarlos (`RF-SP-074`) |
| **Lo puede hacer cualquiera** | Esté o no obligado por su rol. Proteger la propia cuenta no se le niega a nadie |

---

## 3. Actores

| Actor | Rol en esta funcionalidad |
|---|---|
| Cualquier persona autenticada con los dos permisos de alcance propio | Inicia y confirma su propio factor. Ninguna persona activa el de otra |

---

## 4. Alcance

### 4.1 Incluye

- Generar el secreto y entregar lo que la app necesita: el enlace que se pinta como código QR y el secreto escrito, para quien no pueda escanear.
- Confirmar con el primer código y dejar el factor activo.
- Entregar los diez códigos de recuperación.
- Sustituir un factor activo por otro (cambio de teléfono).

### 4.2 No incluye

- **Entrar con el factor** → `RF-SP-072`.
- **Presentarlo otra vez antes de una operación sensible** → `RF-SP-073`.
- **Regenerar los códigos sin cambiar de teléfono** → `RF-SP-074`.
- **Desactivarlo** → `RF-SP-075`. **Que un administrador lo retire** → `RF-SP-076`.
- **Decidir quién está obligado** → `RF-SP-077`.
- **Pintar el código QR.** Lo hace el cliente con el enlace que recibe.

---

## 5. Reglas de negocio aplicables

| Regla | Cómo aplica |
|---|---|
| `RN-SP-058` | Uno activo y uno pendiente; se activa al confirmarlo; cambiar de teléfono retira el anterior |
| `RN-SP-060` | El código de la confirmación queda usado y no sirve después |
| `RN-SP-061` | Diez códigos, de un solo uso, vistos una vez |
| `RN-SP-063` | Iniciar con un factor ya activo exige verificación reciente |
| `RN-SP-062` | Quien está retenido por no tener el factor alcanza estas dos operaciones: son su salida |

---

## 6. Datos

### 6.1 Entrada

**Iniciar:** nada. La persona es quien llama.

**Confirmar:**

| Dato | Obligatorio | Descripción |
|---|---|---|
| Código | Sí | Seis dígitos, los que muestra la app en ese momento |

### 6.2 Salida

**Iniciar:** el **enlace** que la app entiende —con el nombre del sistema y el nombre de usuario de la persona, para que la app la muestre reconocible—, el **secreto escrito** y **cuándo caduca** el pendiente.

**Confirmar:** que el factor está activo, desde cuándo, y **los diez códigos de recuperación**.

---

## 7. Precondiciones y postcondiciones

| Tipo | Condición |
|---|---|
| Precondición (iniciar) | La persona tiene el permiso de iniciar. Si ya tiene un factor activo, presentó un código hace cinco minutos o menos |
| Postcondición (iniciar) | Existe un factor pendiente nuevo, el único pendiente de la persona; el secreto está guardado **cifrado** |
| Precondición (confirmar) | La persona tiene el permiso de confirmar y un factor pendiente sin caducar; el código vale para ese factor |
| Postcondición (confirmar) | El factor está activo y es el único activo; el anterior, si lo había, quedó retirado con el motivo «reemplazado» y sus códigos ya no sirven; existen diez códigos de recuperación nuevos, guardados solo como resumen; el código presentado queda usado; está auditado |

---

## 8. Flujo principal

1. La persona inicia. El sistema genera el secreto, lo guarda cifrado como pendiente y devuelve el enlace, el secreto escrito y la caducidad.
2. La persona escanea el enlace con su app —o escribe el secreto— y la app empieza a mostrar códigos.
3. La persona confirma con el código que ve.
4. El sistema comprueba el código contra el pendiente y lo marca usado.
5. El sistema activa el factor, retira el anterior si lo había, genera los diez códigos de recuperación, audita y devuelve los códigos.

---

## 9. Flujos alternativos

### FA-001 — Cambio de teléfono

**Cuándo ocurre:** la persona ya tiene un factor activo.

1. Iniciar exige que haya presentado un código hace cinco minutos o menos (`RF-SP-073`). Si no, se le pide (`EX-003`).
2. Mientras el nuevo no se confirma, **el anterior sigue siendo el que vale**: entrar sigue pidiendo el código del teléfono viejo.
3. Al confirmar, el anterior queda retirado y sus códigos de recuperación dejan de servir; los diez nuevos los sustituyen.

### FA-002 — Iniciar con un pendiente sin confirmar

El pendiente anterior **se sustituye**: el código de una app que escaneó el enlace viejo ya no confirma nada.

---

## 10. Excepciones

| ID | Situación | Resultado |
|---|---|---|
| `EX-001` | El código falta o no tiene seis dígitos | Rechazo por formato |
| `EX-002` | No hay pendiente, caducó, o el código no vale para él | Rechazo. **El pendiente sigue vivo** si no caducó: equivocarse al teclear no obliga a escanear otra vez |
| `EX-003` | Con un factor activo, iniciar sin haber presentado un código hace poco | Prohibido, diciendo que hay que reverificar |
| `EX-004` | Sin el permiso de la operación | Prohibido |

**Los fallos al confirmar no consumen intentos de la cuenta**, al contrario que al entrar o al reverificar: confirmar un pendiente no concede nada que la persona no tuviera, y quien llama ya está autenticado.

---

## 11. Validaciones

| ID | Validación | Mensaje esperado |
|---|---|---|
| `VAL-001` | Código presente, seis dígitos | El código debe tener seis dígitos. |
| `VAL-002` | Pendiente vigente y código válido para él | El código no es válido o ha caducado. Si el problema persiste, vuelva a escanear. |

---

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-SP-807` | Iniciar devuelve un enlace de app autenticadora con el nombre del sistema como emisor y el nombre de usuario de la persona, el secreto escrito y su caducidad, y deja un factor **pendiente** |
| `CA-SP-808` | El secreto **no se guarda en claro**, y lo guardado para una persona no se puede descifrar como si fuera de otra |
| `CA-SP-809` | Con un factor solo **pendiente**, entrar sigue pidiendo solo la contraseña |
| `CA-SP-810` | Confirmar con un código válido deja el factor **activo** y devuelve **diez** códigos de recuperación distintos; el sistema guarda solo su resumen |
| `CA-SP-811` | Confirmar con un código inválido se rechaza, **el pendiente sigue vivo** y un segundo intento con el código correcto lo activa |
| `CA-SP-812` | Un pendiente de más de diez minutos ya no se puede confirmar |
| `CA-SP-813` | Iniciar dos veces deja un solo pendiente: el código de la app que escaneó el primero ya no confirma |
| `CA-SP-814` | Con un factor activo, iniciar **sin** código reciente responde prohibido con el aviso de reverificación; **con** él, crea el pendiente |
| `CA-SP-815` | Confirmar un factor nuevo teniendo otro activo **retira el anterior**: entrar pide el código del nuevo, y los códigos de recuperación anteriores ya no sirven |
| `CA-SP-816` | El código usado para confirmar **no sirve** para entrar después |
| `CA-SP-817` | Dos confirmaciones simultáneas dejan **un solo** factor activo |
| `CA-SP-818` | La activación queda en la auditoría de seguridad con severidad alta, y dice si sustituyó a otro; **ni el secreto ni los códigos** aparecen en ningún registro |
| `CA-SP-819` | Sin el permiso de iniciar o de confirmar responde prohibido; sin autenticar, `401` |

---

## 13. Casos límite

| Caso | Comportamiento |
|---|---|
| El reloj del teléfono va medio minuto adelantado o atrasado | Se admite: se acepta el periodo anterior y el siguiente (`RN-SP-060`) |
| La persona confirma, pierde la respuesta y no guardó los códigos | No se pueden volver a ver. Los regenera con `RF-SP-074` |
| Una persona retenida porque su rol lo exige | Alcanza estas dos operaciones: son su salida |
| Una persona con la contraseña provisional pendiente | **Primero la contraseña**: la retención por contraseña manda, y estas operaciones no le quedan alcanzables hasta cambiarla (`security.md` §3.3) |
| La llave de cifrado no está configurada | El sistema no arranca: un secreto sin cifrar no se guarda nunca |

---

## 14. Preguntas abiertas

Ninguna. El tipo de factor, la obligación por rol, los códigos y la reverificación los decidió el responsable del proyecto el 06-10-2026.

---

## 15. Control de cambios

| Versión | Fecha | Cambio | Autor |
|---|---|---|---|
| 0.1.0 | 06-10-2026 | Primera versión ([`requirements/sp.md`](../../../requirements/sp.md) v1.93.0, `RN-SP-058`, `RN-SP-060`, `RN-SP-061`, `RN-SP-063`), con las decisiones del responsable del proyecto: **app autenticadora**, dos pasos, diez códigos de recuperación, y el cambio de teléfono con verificación reciente. | Responsable técnico |
