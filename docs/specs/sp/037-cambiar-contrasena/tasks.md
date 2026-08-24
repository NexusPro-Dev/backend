# TASKS — `RF-SP-037` Cambiar la propia contraseña

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-037` |
| Especificación | [`spec.md`](spec.md) |
| Plan | [`plan.md`](plan.md) |
| `plan.md` aprobado el | 24-08-2026 |
| Estado | **En revisión** |
| Issue | Pendiente de crear |
| Rama | `feature/cambiar-contrasena` |
| Aprobadas por | Pendiente |

---

## 1. Tareas

Sin migración y **sin ningún componente de dominio nuevo**: la política la aporta `RF-SP-024`, el bloqueo `RF-SP-034` y la revocación `RF-SP-028` (`plan.md` §3). Lo propio es el orden en que se combinan y tres detalles que fallan en silencio: el contador de fallos en transacción propia, la revocación de **todas** las sesiones incluida la actual, y la excepción de esta ruta en el filtro de cambio obligatorio.

| # | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-01` | `domain/User.changePassword(...)`: sustituye el hash, limpia `must_change_password` y pone a cero el contador | — | Pruebas unitarias sin Spring: los tres efectos ocurren juntos; ninguno por separado | Pendiente |
| `T-02` | `application/ChangeOwnPasswordService` con `@Transactional` y el orden de `plan.md` §4, **con la comprobación de la vigente en último lugar** | `T-01` | Pruebas con dobles: una petición con la contraseña nueva mal formada **no** consume intento del contador. Invertir el orden hace fallar esta prueba y ninguna otra | Pendiente |
| `T-03` | Incremento del contador de fallos en **transacción propia**, que confirma aunque la petición termine en rechazo | `T-02` | Prueba de integración: tras cinco intentos con la vigente incorrecta la cuenta queda bloqueada. Dentro de la transacción principal el `rollback` lo borra y la prueba falla | Pendiente |
| `T-04` | Revocación de **todas** las sesiones con motivo `ACCESO_RETIRADO`, **incluida la que ejecutó el cambio**, dentro de la transacción, más la publicación del corte de tokens de acceso | `T-02` | Prueba de integración: ningún refresh token vigente queda; el token de acceso de la sesión que hizo el cambio deja de admitirse | Pendiente |
| `T-05` | Auditoría: `PASSWORD_CHANGED` con severidad alta y `outcome = 'SUCCESS'` enganchado al commit; el fallo como **`PASSWORD_CHANGED` con `outcome = 'FAILURE'`** y severidad media, sin esperar al commit; `ACCOUNT_LOCKED` cuando se alcanza el umbral | `T-03`, `T-04` | Prueba de integración: **ninguna** fila de `LOGIN_FAILURE` sale de este endpoint; el `detail` no contiene ninguna de las dos contraseñas | Pendiente |
| `T-06` | **Exceptuar esta ruta en `MustChangePasswordFilter`** de `RF-SP-034` | — | Prueba de API: con `mcp` en verdadero, esta ruta responde y **cualquier otra** es negada. Sin la excepción, la cuenta queda sin salida | Pendiente |
| `T-07` | `api/ChangePasswordRequest` y `AuthController`: `POST /api/v1/auth/password` autenticado, respondiendo `204`, **sin identificador de usuario en el cuerpo** | `T-05`, `T-06` | Prueba de API: `422` para la vigente incorrecta y `400` para política y contraseña repetida; el DTO no declara ningún campo por el que dirigir la operación a un tercero | Pendiente |
| `T-08` | Pruebas de API e integración de los criterios de aceptación de `spec.md` §12 | `T-07` | La suite cubre `CA-SP-319` a `CA-SP-328` y `CA-SP-389` a `CA-SP-391` | Pendiente |
| `T-09` | Pruebas de los casos límite de `spec.md` §13: cambio con la marca puesta, cambios concurrentes, persona bloqueada y contraseña igual al nombre de usuario | `T-07` | La primera es la más importante: verifica la interacción con el filtro de `RF-SP-034` cuyo fallo deja la cuenta sin recuperación posible | Pendiente |
| `T-10` | Documentación OpenAPI del endpoint: cuerpo, respuesta `204` y los estados `400`, `401`, `422`, `423` y `500` | `T-08` | El contrato publicado coincide con el comportamiento real (Art. VIII.6) | Pendiente |
| `T-11` | Actualizar la matriz de trazabilidad de `docs/requirements.md` | `T-08` | La fila de `RF-SP-037` refleja el estado y enlaza esta tripleta | Pendiente |

**Estados:** `Pendiente` · `En curso` · `Hecha` · `Bloqueada`.

## 2. Orden de ejecución

```mermaid
graph LR
    T01[T-01] --> T02[T-02]
    T02 --> T03[T-03] --> T05[T-05]
    T02 --> T04[T-04] --> T05
    T05 --> T07[T-07]
    T06[T-06] --> T07
    T07 --> T08[T-08] --> T10[T-10]
    T08 --> T11[T-11]
    T07 --> T09[T-09]
```

`T-06` no depende de nada de este requerimiento y sí de que `RF-SP-034` exista. Conviene hacerla la primera: es la que decide si la cuenta tiene salida.

## 3. Cobertura de los criterios de aceptación

| Criterio | Tarea que lo cubre |
|---|---|
| `CA-SP-319` | `T-01`, `T-07`, `T-08` |
| `CA-SP-320` | `T-01`, `T-08` |
| `CA-SP-321` | `T-05`, `T-07`, `T-08` |
| `CA-SP-322` | `T-07`, `T-08` |
| `CA-SP-323` | `T-07`, `T-08` |
| `CA-SP-324` | `T-04`, `T-08` |
| `CA-SP-325` | `T-01`, `T-08` |
| `CA-SP-326` | `T-05`, `T-08` |
| `CA-SP-327` | `T-05`, `T-08` |
| `CA-SP-389` | `T-03`, `T-08` |
| `CA-SP-390` | `T-08` |
| `CA-SP-391` | `T-04`, `T-08` |
| `CA-SP-328` | `T-07`, `T-08` |

## 4. Bloqueos

| # | Bloqueo | Desde | Responsable | Estado |
|---|---|---|---|---|
| 1 | Ninguna tarea es ejecutable hasta que `RF-SP-034` exista: aporta la verificación de contraseña, `LockoutPolicy`, `refresh_tokens` y el filtro que `T-06` debe exceptuar | 24-08-2026 | Responsable técnico | Abierto |
| 2 | `T-04` consume `SessionRevoker` y `AccessRevocationPublisher`, puertos de `RF-SP-028` implementados por `RF-SP-034`. **Ninguna tarea los escribe** | 24-08-2026 | Responsable técnico | Abierto |
| 3 | `CA-SP-330` de `RF-SP-038` —la marca se limpia al ejecutar este requerimiento— se verifica desde aquel lado y necesita este implementado. Es la única dependencia en esa dirección | 24-08-2026 | Responsable técnico | Abierto |

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
