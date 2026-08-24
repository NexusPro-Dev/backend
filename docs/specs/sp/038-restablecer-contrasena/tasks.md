# TASKS — `RF-SP-038` Restablecer la contraseña de un usuario

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-038` |
| Especificación | [`spec.md`](spec.md) |
| Plan | [`plan.md`](plan.md) |
| `plan.md` aprobado el | 24-08-2026 |
| Estado | **En revisión** |
| Issue | Pendiente de crear |
| Rama | `feature/restablecer-contrasena` |
| Aprobadas por | Pendiente |

---

## 1. Tareas

Una migración de una columna y ningún componente de dominio nuevo (`plan.md` §3). Todo el peso está en lo que la operación **no** debe hacer —levantar un bloqueo, devolver la credencial, aplicarse sobre el propio actor— y en una mitad que no vive aquí: la caducidad se fija en `T-02` y se comprueba en `RF-SP-034`.

| # | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-01` | Migración `V28__add_provisional_credential_expiry.sql`: `provisional_password_expires_at` sobre `users`, con el **`CHECK` que la ata a `must_change_password`** (`plan.md` §2) | — | Prueba de integración de esquema: una fecha de caducidad sin la marca **falla**; una credencial provisional sin caducidad también | Pendiente |
| `T-02` | `domain/User.resetPasswordBy(...)`: sustituye el hash, marca el cambio obligatorio y fija la caducidad. **No toca el estado ni el bloqueo** | `T-01` | Pruebas unitarias sin Spring: sobre una cuenta bloqueada, el bloqueo permanece; sobre una inactiva, el estado permanece | Pendiente |
| `T-03` | Consumir `SelfOperationGuard` de `RF-SP-028` para `RN-SP-017`, **sin reimplantarlo** | — | Prueba unitaria: el actor sobre sí mismo se rechaza; sobre otro, procede | Pendiente |
| `T-04` | `application/ResetUserPasswordService` con `@Transactional` y el orden de `plan.md` §4 | `T-02`, `T-03` | Pruebas con dobles: la política se comprueba **antes** de leer la cuenta; `RN-SP-017` **después** de resolverla | Pendiente |
| `T-05` | Revocación de todas las sesiones y publicación del corte de tokens de acceso, dentro de la transacción | `T-04` | Prueba de integración: la sesión abierta de la persona afectada cae de inmediato, no en quince minutos | Pendiente |
| `T-06` | Auditoría: `PASSWORD_RESET` con severidad alta y `target_user_id` de la persona afectada, con **la fecha de caducidad en `detail`** y nada más; el `409` de `RN-SP-017` en `audit_error_log` | `T-05` | Prueba de integración: `actor_id` y `target_user_id` son **personas distintas**; ni la contraseña ni su longitud aparecen en `detail` | Pendiente |
| `T-07` | `api/ResetPasswordRequest` y `UserController`: `POST /api/v1/users/{id}/password-reset` con el permiso `users:reset-password`, respondiendo `204` | `T-06` | Prueba de API: `204` sin cuerpo; un actor con `users:update` y sin `users:reset-password` recibe `403`; el `409` cita `RF-SP-037` | Pendiente |
| `T-08` | **Comprobación de la caducidad en `RF-SP-034`**: pasado el plazo, la credencial provisional deja de autenticar | `T-07` | Prueba que ejecuta el restablecimiento, adelanta el reloj e intenta iniciar sesión. Es la mitad del requerimiento que no vive en él (`plan.md` §11) | Pendiente |
| `T-09` | Pruebas de API e integración de los criterios de aceptación de `spec.md` §12 | `T-07` | La suite cubre `CA-SP-329` a `CA-SP-337` y `CA-SP-392` a `CA-SP-394` | Pendiente |
| `T-10` | Pruebas de los casos límite de `spec.md` §13: usuario inactivo, último superadministrador, sesión abierta, credencial nunca usada, dos restablecimientos concurrentes y restablecimiento seguido de cambio propio | `T-07` | Ninguno levanta un bloqueo ni cambia el estado de la cuenta | Pendiente |
| `T-11` | Documentación OpenAPI del endpoint: cuerpo, respuesta `204` y los estados `400`, `401`, `403`, `404`, `409` y `500` | `T-09` | El contrato publicado coincide con el comportamiento real (Art. VIII.6), y **no** documenta ningún cuerpo de respuesta | Pendiente |
| `T-12` | Aplicar las enmiendas de `plan.md` §8: `requirements/sp.md` §10.10 gana la columna, `security.md` §3.2 gana la caducidad de la credencial provisional. Actualizar la matriz de trazabilidad | `T-09` | Ambos documentos con su fila de control de cambios y versión subida; la fila de `RF-SP-038` en la matriz enlaza esta tripleta | Pendiente |

**Estados:** `Pendiente` · `En curso` · `Hecha` · `Bloqueada`.

## 2. Orden de ejecución

```mermaid
graph LR
    T01[T-01] --> T02[T-02] --> T04[T-04]
    T03[T-03] --> T04
    T04 --> T05[T-05] --> T06[T-06] --> T07[T-07]
    T07 --> T08[T-08]
    T07 --> T09[T-09] --> T11[T-11]
    T09 --> T12[T-12]
    T07 --> T10[T-10]
```

## 3. Cobertura de los criterios de aceptación

| Criterio | Tarea que lo cubre |
|---|---|
| `CA-SP-329` | `T-02`, `T-09` |
| `CA-SP-330` | `T-02`, `T-09` |
| `CA-SP-331` | `T-05`, `T-09` |
| `CA-SP-332` | `T-03`, `T-07`, `T-09` |
| `CA-SP-333` | `T-04`, `T-09` |
| `CA-SP-334` | `T-07`, `T-09` |
| `CA-SP-335` | `T-02`, `T-09` |
| `CA-SP-336` | `T-06`, `T-09` |
| `CA-SP-392` | `T-01`, `T-08` |
| `CA-SP-393` | `T-06`, `T-07`, `T-09` |
| `CA-SP-394` | `T-02`, `T-10` |
| `CA-SP-337` | `T-07`, `T-09` |

## 4. Bloqueos

| # | Bloqueo | Desde | Responsable | Estado |
|---|---|---|---|---|
| 1 | `T-01` no es ejecutable hasta que `RF-SP-024` cree `users` con `must_change_password`, y `T-05` hasta que `RF-SP-034` cree `refresh_tokens` | 24-08-2026 | Responsable técnico | Abierto |
| 2 | `T-08` **modifica `RF-SP-034`**: la comprobación de la caducidad vive en el inicio de sesión. Debe coordinarse con aquella tripleta y no duplicarse en esta | 24-08-2026 | Responsable técnico | Abierto |
| 3 | `CA-SP-330` necesita `RF-SP-037` implementado para verificar que la marca y la caducidad se limpian | 24-08-2026 | Responsable técnico | Abierto |
| 4 | El **plazo de caducidad** de la credencial provisional no tiene valor decidido. Se declara en configuración junto al resto de parámetros de credenciales (`security.md` §3.2), y se fija al implementar `T-01` | 24-08-2026 | Responsable técnico | Abierto |
| 5 | **El aviso a la persona afectada sigue sin resolverse aquí** (`spec.md` §14, pregunta 3). Depende de **D-23** y lo cierra `RF-SP-040`. Mientras tanto, un restablecimiento que la persona no pidió solo es detectable revisando la auditoría | 24-08-2026 | Responsable del proyecto | Abierto |

## 5. Definición de terminado

El requerimiento no está terminado hasta cumplir **todas** las condiciones de la constitución §16:

- [ ] Todas las tareas en estado `Hecha`.
- [ ] Todos los criterios de aceptación con prueba automatizada en verde.
- [ ] `mvn verify` en verde en local.
- [ ] Toda escritura emite su evento de auditoría, en la transacción que corresponde.
- [ ] Los endpoints nuevos declaran su permiso.
- [ ] El contrato OpenAPI coincide con el comportamiento real.
- [ ] Documentación afectada actualizada en el mismo Pull Request.
- [ ] Matriz de trazabilidad actualizada.
- [ ] Pull Request aprobado por alguien distinto del autor e integrado.
