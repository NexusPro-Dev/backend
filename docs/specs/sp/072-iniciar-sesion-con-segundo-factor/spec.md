# SPEC — `RF-SP-072` Iniciar sesión con el segundo factor

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-072` |
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

Que quien tiene el segundo factor activo **no pueda entrar solo con la contraseña**: que la sesión nazca únicamente cuando, además, presente el código de su app autenticadora —o uno de sus códigos de recuperación—. Y que quien **está obligado** por su rol y todavía no lo tiene entre al sistema solo para activarlo.

---

## 2. Contexto

`RF-SP-034` entrega la sesión al acertar la contraseña, con un orden de comprobaciones diseñado para no revelar qué cuentas existen. Este requerimiento **lo enmienda** (Art. I.7) sin tocar ese orden: lo que cambia es **qué se entrega al final**.

### 2.1 Lo que este requerimiento decide

| Decisión | Qué se decidió |
|---|---|
| **Dos pasos** | Con el factor activo, la contraseña correcta no da la sesión: da un **desafío** de un solo uso. La sesión nace al presentar el desafío con un código |
| **El desafío dura cinco minutos y admite cinco intentos** | Basta para abrir la app y teclear; al quinto fallo muere y hay que volver a la contraseña |
| **Los fallos del código cuentan para el bloqueo de la cuenta** | Con el mismo contador y la misma progresión que la contraseña. Quien falla aquí **acertó la contraseña**: sin este freno, probaría el millón de combinaciones |
| **Acertar la contraseña no limpia el contador** | Lo limpia **completar** el segundo paso. Si no, cada contraseña correcta regalaría cinco intentos más contra el código |
| **Un código de recuperación vale lo mismo que el del teléfono** | Una vez cada uno, y su uso queda avisado |
| **No revela qué cuentas tienen el factor** | El desafío solo aparece tras una contraseña correcta: quien la tiene ya sabe que la cuenta existe |
| **La obligación del rol retiene, no rechaza** | Quien porta un rol que lo exige y no lo tiene **entra**, y solo alcanza lo que necesita para activarlo — igual que la contraseña provisional. Rechazarle le dejaría sin forma de activarlo |
| **Dos desafíos a la vez se admiten** | Dos pestañas o dos dispositivos que entran al mismo tiempo son un caso normal |

---

## 3. Actores

| Actor | Rol en esta funcionalidad |
|---|---|
| Cualquier persona con el factor activo | Presenta el desafío y un código |
| Cualquier persona obligada por su rol y sin factor | Entra retenida hasta activarlo |

No hay permiso asociado: el segundo paso es público y lo autoriza el desafío, como el primero lo autoriza la contraseña.

---

## 4. Alcance

### 4.1 Incluye

- Emitir el desafío en lugar de la sesión cuando la cuenta tiene el factor activo.
- Abrir la sesión con el desafío y un código, o un código de recuperación.
- Contar los fallos contra el desafío y contra la cuenta.
- Retener a quien está obligado y no tiene el factor.

### 4.2 No incluye

- **Activar el factor** → `RF-SP-071`.
- **Pedir el código otra vez dentro de la sesión** → `RF-SP-073`.
- **Recordar el dispositivo** («no pedir el código en este equipo durante treinta días»). No se ha pedido.

---

## 5. Reglas de negocio aplicables

| Regla | Cómo aplica |
|---|---|
| `RN-SP-059` | La contraseña sola no abre sesión; desafío de cinco minutos y cinco intentos; los fallos cuentan para el bloqueo |
| `RN-SP-060` | Cada código vale una vez, con un periodo de tolerancia a cada lado |
| `RN-SP-061` | Cada código de recuperación vale una vez y su uso se avisa |
| `RN-SP-062` | El rol obliga; quien no lo tiene entra retenido |
| `security.md` §3.2 | El bloqueo por intentos, sus avisos y su techo, sin cambios |

---

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción |
|---|---|---|
| Desafío | Sí | El que entregó el primer paso |
| Código | Sí | **O** el de seis dígitos de la app, **o** uno de recuperación. Uno y solo uno |

### 6.2 Salida

**Primer paso, con factor activo:** que hace falta el segundo factor, el desafío y cuánto dura. **Ningún token.**

**Segundo paso:** lo mismo que entrega `RF-SP-034` —token de acceso, refresh token, vigencia, aviso de cambio de contraseña— más el aviso de **activación obligatoria pendiente**. Si se usó un código de recuperación, **cuántos quedan**.

---

## 7. Precondiciones y postcondiciones

| Tipo | Condición |
|---|---|
| Precondición | El desafío existe, no caducó, no se consumió y le quedan intentos; el código vale; la cuenta sigue habilitada y no está bloqueada |
| Postcondición | La persona tiene sesión, con la prueba de que verificó el segundo factor ahora; el desafío está consumido; el código, usado; el contador de intentos de la cuenta, a cero; el último acceso, registrado; está auditado |

---

## 8. Flujo principal

1. La persona presenta su contraseña (`RF-SP-034`). Todo sucede como hasta hoy hasta el final.
2. La cuenta tiene el factor activo: el sistema emite el desafío y lo devuelve **en lugar de** la sesión.
3. La persona abre su app y presenta el desafío con el código.
4. El sistema comprueba el desafío, comprueba que la cuenta sigue habilitada, comprueba el código y lo marca usado.
5. El sistema consume el desafío, pone a cero el contador, registra el acceso y abre la sesión.

---

## 9. Flujos alternativos

### FA-001 — Código de recuperación

En el paso 3 la persona presenta uno de sus códigos de recuperación. Si es uno de los vigentes, queda usado, la sesión se abre, la respuesta dice cuántos le quedan y queda un evento de seguridad de severidad alta.

### FA-002 — La persona está obligada y no tiene el factor

**Cuándo ocurre:** porta al menos un rol activo que exige el factor, y no tiene ninguno activo (puede tenerlo pendiente).

1. La autenticación **tiene éxito con la contraseña sola**: no hay factor que pedir.
2. La respuesta advierte que debe activarlo.
3. Toda operación le responde que lo active, salvo **activar y confirmar el factor**, **consultar su propio perfil** y **las tres operaciones públicas de sesión**.
4. **Si además tiene la contraseña provisional pendiente, esa manda**: primero la cambia, después activa el factor. Activar un authenticator sobre una credencial que otra persona conoce protegería la cuenta para las dos.
5. Renovar la sesión **recalcula la obligación**, como recalcula la de la contraseña: quien activa el factor deja de estar retenido en su siguiente renovación, y a quien le marcan el rol empieza a estarlo.

### FA-003 — La persona tiene el factor y no está obligada

Igual que el flujo principal. La obligación no cambia cómo se entra; solo qué pasa si no hay factor.

---

## 10. Excepciones

| ID | Situación | Resultado |
|---|---|---|
| `EX-001` | El desafío no existe, caducó, se consumió o agotó sus intentos | **La misma respuesta para los cuatro casos**, la de credenciales inválidas. No consume intento de ninguna cuenta: no se sabe de quién es |
| `EX-002` | El código no vale —mal tecleado, ya usado, fuera de su ventana, o un código de recuperación que no existe o ya se usó— | La respuesta de credenciales inválidas **con los intentos que le quedan a la cuenta**, como en `RF-SP-034`. Consume un intento del desafío y uno de la cuenta, y deja un evento de seguridad |
| `EX-003` | Ese fallo alcanza el umbral de la cuenta | La cuenta se bloquea, como en `RF-SP-034` `EX-003`, y el desafío muere con ella |
| `EX-004` | La cuenta se bloqueó, desactivó o eliminó entre los dos pasos | La cuenta bloqueada responde como bloqueada; la desactivada o eliminada, como `EX-001`. **No se abre sesión** |
| `EX-005` | Llegan los dos códigos, o ninguno, o un formato que no es ninguno de los dos | Rechazo por formato |
| `EX-006` | Demasiados intentos desde el mismo origen | Exceso de peticiones, sin comprobar nada |

---

## 11. Validaciones

| ID | Validación | Mensaje esperado |
|---|---|---|
| `VAL-001` | Desafío presente | Debe indicar el desafío. |
| `VAL-002` | Exactamente un código, con forma de código de app o de recuperación | Indique el código de su aplicación o un código de recuperación. |
| `VAL-003` | Desafío vigente y código válido | El código no es válido. *(Con los intentos restantes, como `RF-SP-034`.)* |

---

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-SP-820` | Con el factor activo, la contraseña correcta devuelve **un desafío y ningún token**, sin registrar el acceso, sin poner a cero el contador y sin evento de inicio de sesión exitoso |
| `CA-SP-821` | Sin factor activo —o con uno solo pendiente—, la contraseña correcta abre la sesión como siempre |
| `CA-SP-822` | El desafío con un código válido abre la sesión; el token lleva la prueba de la verificación con el instante actual; el contador vuelve a cero y el acceso queda registrado |
| `CA-SP-823` | Un código inválido se rechaza con la respuesta de credenciales inválidas y los intentos restantes de la cuenta, y deja un evento de **código rechazado** |
| `CA-SP-824` | Los fallos del código y los de la contraseña **suman en el mismo contador**, y al quinto la cuenta se bloquea |
| `CA-SP-825` | Al quinto fallo, el desafío muere: el código correcto ya no abre nada |
| `CA-SP-826` | Un desafío inexistente, caducado, consumido o agotado reciben **la misma** respuesta |
| `CA-SP-827` | El código de un periodo anterior o posterior se acepta; el de dos periodos de distancia, no; y uno ya usado, tampoco |
| `CA-SP-828` | Un código de recuperación abre la sesión, queda usado —no sirve otra vez—, la respuesta dice cuántos quedan y queda un evento de severidad alta |
| `CA-SP-829` | Si la cuenta se desactiva entre los dos pasos, el segundo no abre sesión |
| `CA-SP-830` | Dos desafíos de la misma persona emitidos a la vez funcionan los dos |
| `CA-SP-831` | El sistema guarda solo el resumen del desafío |
| `CA-SP-832` | El segundo paso tiene su límite de intentos por origen |
| `CA-SP-833` | Renovar la sesión **conserva** el instante de la verificación sin moverlo |
| `CA-SP-834` | Quien porta un rol que lo exige y no tiene el factor **entra** y la respuesta lo advierte |
| `CA-SP-835` | Retenido así, **una operación cualquiera** responde prohibido con un aviso propio —distinto del de falta de permiso y del de cambio de contraseña— y la ruta para activarlo |
| `CA-SP-836` | Retenido así, siguen alcanzables activar y confirmar el factor, el propio perfil y cerrar la sesión |
| `CA-SP-837` | Con la contraseña provisional **y** la obligación pendientes, manda la contraseña |
| `CA-SP-838` | La renovación recalcula la obligación: tras activar el factor, la siguiente renovación ya no retiene |

---

## 13. Casos límite

| Caso | Comportamiento |
|---|---|
| Se presenta el desafío de otra persona con el código propio | No casa: el código se comprueba contra el factor de la dueña del desafío |
| El factor se cambia —otro teléfono— entre los dos pasos | El desafío sigue valiendo; el código tiene que ser del factor **activo en el segundo paso** |
| El factor se retira por un administrador entre los dos pasos | El segundo paso se rechaza como `EX-002`: ya no hay factor contra el que comprobar. La persona vuelve a la contraseña, que ahora entra sin desafío |
| Los diez códigos de recuperación gastados | Solo queda el teléfono. La respuesta del último uso dice que quedan cero |
| La retención y la sesión ya abierta | Quien tenía sesión antes de que su rol lo exigiera queda retenido en su siguiente renovación, no en el acto (`RF-SP-077`) |

---

## 14. Preguntas abiertas

**Recordar el dispositivo**: no se ha pedido.

---

## 15. Control de cambios

| Versión | Fecha | Cambio | Autor |
|---|---|---|---|
| 0.1.0 | 06-10-2026 | Primera versión ([`requirements/sp.md`](../../../requirements/sp.md) v1.93.0, `RN-SP-059` a `RN-SP-062`). **Enmienda `RF-SP-034`**: con el factor activo, la contraseña entrega un desafío; y añade la retención por obligación del rol. | Responsable técnico |
