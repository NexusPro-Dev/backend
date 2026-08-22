# TASKS — `RF-SP-029` Eliminar usuario

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-029` |
| Especificación | [`spec.md`](spec.md) |
| Plan | [`plan.md`](plan.md) |
| `plan.md` aprobado el | 22-08-2026 |
| Estado | **En revisión** |
| Issue | Pendiente de crear |
| Rama | `feature/eliminar-usuario` |
| Aprobadas por | Pendiente |

!!! info "Qué va en este documento"

    **En qué pasos, en qué orden y cómo se verifica cada uno.**

    **Prueba de pertenencia:** si no puede marcarse como hecho, no es una tarea.

    **Es la fuente de verdad de las tareas.** El Issue de GitHub coordina y enlaza aquí; no la sustituye ni la duplica. Si las dos listas discrepan, manda este archivo.

    No se escribe hasta que `plan.md` esté aprobado, y ninguna tarea se ejecuta hasta que este documento lo esté (Art. I.6).

---

## 1. Tareas

Sin migración propia. La lista es corta porque **cinco componentes se reutilizan de `RF-SP-028`** y ninguno se reescribe: `SelfOperationGuard`, `RootAdministratorPresence`, `RootRoleHolderRepository`, `SupervisedTeamCounter` y `SessionRevoker`. Que este requerimiento no los vuelva a escribir es parte de lo que hay que revisar, no un detalle de implementación.

Dos tareas concentran el riesgo:

- **`T-04`** captura el estado **antes** de destruir nada. Invertir esos dos pasos pierde la información para siempre y **no produce ningún error**: es el único defecto del requerimiento que no falla, solo deja un `snapshot` vacío que nadie mira hasta que hace falta.
- **`T-02`** añade `USER_DELETED` al `CHECK` de `V4`. Sin ella, la eliminación funciona y **falla su auditoría de seguridad** dentro de la transacción `REQUIRES_NEW`, con un síntoma que no apunta a la eliminación.

| # | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-01` | Comprobar que `RF-SP-028` y `RF-SP-034` están integrados: los cinco componentes reutilizados, `ix_user_supervisors_supervisor_vigente` y `refresh_tokens` | — | Prueba de integración: el índice es parcial y la tabla de sesiones existe. **Antes de empezar `T-05`** | Pendiente |
| `T-02` | Añadir `USER_DELETED` a `ck_audit_security_log_event_type` en `V4__create_audit_logs.sql` (`RF-SP-001`), que pasa de dieciocho a **diecinueve** literales. **Antes del primer despliegue** | — | Prueba de integración: la restricción acepta `USER_DELETED` y rechaza variantes de capitalización. Después del despliegue esto sería una alteración sobre una tabla en uso | Pendiente |
| `T-03` | `domain`: `DeletionReason` —recorta y exige contenido, **distinto de `StatusChangeReason`**— y `User.delete(motivo)`, que marca el borrado y **devuelve el estado previo** | — | Pruebas unitarias **sin Spring ni base de datos**: el motivo en blanco lanza; `delete` **no cambia `status`**; el estado devuelto contiene lo que había antes de marcar | Pendiente |
| `T-04` | `application/DeleteUserService` con `@Transactional`: el orden de `plan.md` §4, con la **captura del estado en el paso 6, antes de tocar nada** | `T-03` | Pruebas con dobles: el auditor de eliminación recibe roles y membresía **no vacíos** aunque las tablas ya estén borradas al final de la transacción | Pendiente |
| `T-05` | `JpaUserRepository`: carga bloqueada con `FOR UPDATE`, captura del estado completo, `DELETE` de `user_roles` y `user_memberships`, y `UPDATE` de cierre de `user_supervisors` **con la misma marca de tiempo** que `deleted_at` | `T-01`, `T-04` | Prueba de integración: `ended_at` de la asignación de superior es **exactamente igual** a `deleted_at`, y su fila **permanece** | Pendiente |
| `T-06` | Reutilizar `RootRoleHolderRepository` y `SupervisedTeamCounter` de `RF-SP-028`, con el mismo bloqueo del conjunto y el mismo orden ascendente | `T-01`, `T-04` | Revisión de código: **no existe una segunda consulta** de portadores del rol raíz en el repositorio. Prueba concurrente en `T-12` | Pendiente |
| `T-07` | Revocación de sesiones **dentro** de la transacción con motivo `ACCESO_RETIRADO`, y publicación del corte de tokens de acceso **tras el commit** | `T-04` | Prueba de integración: forzando el fallo de la revocación, **no queda nada eliminado**; y el token de acceso emitido antes recibe `401` en la petición siguiente | Pendiente |
| `T-08` | Auditoría: `audit_deletion_log` con `deletion_type = 'LOGICAL'`, motivo y `snapshot` **sin `password_hash`**, en la misma transacción; y `USER_DELETED` en `audit_security_log` con `severity = 'ALTA'` y `target_user_id`, enganchado al commit | `T-02`, `T-04` | Prueba de integración: forzando el fallo del evento, **la eliminación tampoco ocurre**; el `snapshot` contiene roles y membresía y **no** el hash | Pendiente |
| `T-09` | Auditoría de los rechazos: `AUTHORIZATION_DENIED` con severidad **`ALTA`** para `RN-SP-017`, emitido por el caso de uso sin esperar al commit; y `audit_error_log` con `severity = 'ALTA'` para los dos `409` | `T-04` | Prueba de integración: el `403` deja fila en `audit_security_log` y **ninguna** en `audit_error_log`; el `404` y los `400` no dejan ninguna. La severidad es **`ALTA`**, a diferencia de `RF-SP-028` | Pendiente |
| `T-10` | `api`: `DeleteUserRequest` con el motivo y rechazo de propiedades desconocidas, y `POST /api/v1/users/{id}/deletion` con el permiso `users:delete`, devolviendo `204` | `T-04` | Prueba de API: un actor con `users:update` pero **sin** `users:delete` recibe `403` | Pendiente |
| `T-11` | Pruebas de los criterios de aceptación de `spec.md` §12 | `T-08`, `T-10` | La suite cubre `CA-SP-241` a `CA-SP-250`, `CA-SP-358` a `CA-SP-360`, `CA-SP-408` y `CA-SP-409` | Pendiente |
| `T-12` | Pruebas **concurrentes** con transacciones reales: eliminación contra inicio de sesión, y dos eliminaciones de superadministradores distintos | `T-06`, `T-10` | Nunca queda una sesión viva sobre una cuenta eliminada; y queda al menos un portador activo del rol raíz | Pendiente |
| `T-13` | Prueba de que **`RF-SP-009` no necesitó cambios**: un rol que solo portaba la persona eliminada pasa a poder eliminarse | `T-10` | `CA-SP-359` se verifica ejecutando `RF-SP-009` tal como está, **sin tocar su código** | Pendiente |
| `T-14` | Pruebas del resto de casos límite de `spec.md` §13 y de `plan.md` §11: motivo de un carácter, motivo en blanco, ya eliminado, identificador no canónico, `INSERT` directo sin motivo y ausencia de restauración | `T-10` | No existe ningún endpoint que devuelva `deleted_at` a nulo, ni directo ni por edición | Pendiente |
| `T-15` | Documentación OpenAPI: el cuerpo con el motivo, la respuesta `204` y los estados `400`, `401`, `403`, `404`, `409` y `500` | `T-11` | El contrato publicado coincide con el comportamiento real (Art. VIII.6), y documenta que la eliminación es **lógica, irreversible y sin liberación de identidad** | Pendiente |
| `T-16` | Enmendar `security.md` §8.1 con «Baja de un usuario — Alta — `SUCCESS`»; corregir `RF-SP-014` §2 —diecinueve literales y `USER_STATUS_CHANGED` sin `RF-SP-029` como emisor—; enmendar `requirements/sp.md` §6.1 con las precedencias; y actualizar la matriz de trazabilidad | `T-11` | El catálogo cerrado y el `CHECK` del esquema dicen lo mismo, que es la única forma de que un catálogo cerrado signifique algo | Pendiente |

**Estados:** `Pendiente` · `En curso` · `Hecha` · `Bloqueada`.

## 2. Orden de ejecución

```mermaid
graph LR
    T01[T-01] --> T05[T-05]
    T01 --> T06[T-06]
    T02[T-02] --> T08[T-08]
    T03[T-03] --> T04[T-04] --> T05
    T04 --> T06
    T04 --> T07[T-07]
    T04 --> T08
    T04 --> T09[T-09]
    T04 --> T10[T-10]
    T08 --> T11[T-11] --> T15[T-15]
    T10 --> T11 --> T16[T-16]
    T06 --> T12[T-12]
    T10 --> T12
    T10 --> T13[T-13]
    T10 --> T14[T-14]
```

`T-02` es independiente y **urgente**: mientras `V4` no esté aplicada, corregirla cuesta una edición; después es una alteración de restricción sobre la tabla de auditoría en uso.

## 3. Cobertura de los criterios de aceptación

| Criterio | Tarea que lo cubre |
|---|---|
| `CA-SP-241` | `T-05`, `T-10`, `T-11` |
| `CA-SP-242` | `T-03`, `T-10`, `T-11` |
| `CA-SP-243` | `T-07`, `T-11` |
| `CA-SP-244` | `T-11` |
| `CA-SP-245` | `T-05`, `T-11` |
| `CA-SP-246` | `T-08`, `T-11` |
| `CA-SP-358` | `T-05`, `T-11` |
| `CA-SP-359` | `T-13` |
| `CA-SP-360` | `T-04`, `T-08`, `T-11` |
| `CA-SP-408` | `T-06`, `T-11` |
| `CA-SP-409` | `T-05`, `T-11` |
| `CA-SP-247` | `T-09`, `T-11` |
| `CA-SP-248` | `T-06`, `T-11`, `T-12` |
| `CA-SP-249` | `T-14` |
| `CA-SP-250` | `T-10`, `T-11` |

`CA-SP-360` es el criterio más importante de la lista y el más fácil de probar mal. Verificar que existe una fila en `audit_deletion_log` da verde con la implementación equivocada; hay que verificar **su contenido**: que el `snapshot` trae los roles y la membresía que la persona tenía. La forma honesta de comprobar que la prueba sirve es invertir los pasos en una implementación de control y ver que **falla**.

`CA-SP-244` es el otro criterio silencioso: comprueba que **no** ocurre algo. Su defecto —un índice único parcial escrito por costumbre— no se manifiesta hasta que alguien reutiliza la identidad de una persona eliminada, y para entonces la auditoría ya no puede separarlas.

## 4. Bloqueos

| # | Bloqueo | Desde | Responsable | Estado |
|---|---|---|---|---|
| 1 | **`RF-SP-028` y `RF-SP-034` deben implementarse antes.** El primero aporta cinco componentes y el índice; el segundo, `refresh_tokens` y `SessionRevoker` (`plan.md` §2) | 22-08-2026 | Responsable técnico | Abierto |
| 2 | `T-02` toca `V4__create_audit_logs.sql`, de `RF-SP-001`, y `T-16` corrige `RF-SP-014` §2, un plan aprobado (Art. I.7). **Ambas cosas van en este mismo Pull Request**: un catálogo cerrado que no coincide con el `CHECK` del esquema deja de ser un catálogo | 22-08-2026 | Responsable técnico | Abierto |
| 3 | **Obligación sobre `RF-SP-034`:** el inicio de sesión rechaza a quien tiene `deleted_at` informado **sin mirar `status`**, porque esta operación no lo cambia (`plan.md` §4) | 22-08-2026 | Responsable técnico | Abierto |
| 4 | Con más de una instancia, el token de acceso de la persona eliminada sigue admitiéndose hasta quince minutos en las demás. Heredado de `RF-SP-028` §10, con la misma acotación: los refresh tokens **sí** quedan revocados en la base de datos | 22-08-2026 | Responsable del proyecto | Abierto |
| 5 | **Nombre y correo del eliminado se conservan indefinidamente.** Riesgo declarado en `spec.md` §14, resolución 4, con su condición de disparo: el día que exista una obligación formal de supresión, la decisión alcanza a **todos** los módulos y se documenta en `docs/security/`, no en esta tripleta | 22-08-2026 | Responsable del proyecto | Abierto |
| 6 | La operación **no tiene vuelta**. `spec.md` §2 recomienda `RF-SP-028` para casi todos los casos, y no hay mitigación técnica posible: es una decisión de quien opera, acotada por un permiso propio | 22-08-2026 | Responsable del proyecto | Abierto |

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
