# SPEC — `RF-SP-082` Asignar una cuenta de broker sin titular

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-082` |
| Módulo | `SP` — Sistema Principal |
| Versión | 0.1.0 |
| Estado | **Aprobada** |
| Autor | Responsable técnico |
| Aprobada por | Responsable del proyecto |
| Fecha de aprobación | 09-10-2026 |

---

## 1. Objetivo

Que administración dé titular a una cuenta de broker que llegó del broker antes que la persona.

## 2. Contexto

El caso 2 del responsable del proyecto (09-10-2026): «creo mediante un enlace mi cuenta del broker, en el enlace va el afftrack del vendedor; guardar ese registro pero sin usuario, después agregar un usuario a esa cuenta». El aviso de registro crea la cuenta `CONSUMIDOR` **sin titular** (`RF-SP-078` v0.4.0). La persona se la asocia sola si declara el mismo número con el mismo vendedor (`RN-SP-072`); **este requerimiento es la otra vía**, la de administración, para lo que no coincide o no se declara.

### 2.1 Lo que este requerimiento decide

- **Solo una cuenta sin titular.** Cambiarle el titular a una cuenta que ya lo tiene sería quitársela a alguien; para eso está borrar y declarar.
- **La persona tiene que ser consumidor** (`RN-SP-068`): la cuenta es `CONSUMIDOR` y tiene FTD; un vendedor no.
- **El origen no cambia**: lo puso el aviso con el `afftrack`, y es lo que atribuye la cuenta.

## 3. Actores

Administración, con `broker-accounts:assign-user`.

## 4. Alcance

### 4.1 Incluye

`PATCH /api/v1/broker-accounts/{brokerAccountId}/holder` con la persona. Encontrar las cuentas sin titular en `RF-SP-057` (`?hasHolder=false`), que hasta hoy no podía listarlas porque toda cuenta tenía titular.

### 4.2 No incluye

Quitar el titular. Cambiar el origen.

## 5. Reglas de negocio aplicables

| ID | Regla | Origen |
|---|---|---|
| `RN-SP-068` | Las cuentas de broker son de dos tipos | `requirements/sp.md` §5.1 |
| `RN-SP-072` | Una cuenta puede existir sin titular, y se asocia después | `requirements/sp.md` §5.1 |

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción |
|---|---|---|
| `brokerAccountId` | Sí | La cuenta, en la ruta |
| `userId` | Sí | La persona que pasa a ser su titular |

### 6.2 Salida

La cuenta, con la forma de una fila de `RF-SP-057`, ya con su titular.

## 7. Precondiciones y postcondiciones

**Precondición:** la cuenta existe y no tiene titular; la persona existe, no está eliminada y es consumidor.

**Postcondición:** la cuenta es de esa persona, y queda **auditado** quién la asignó.

## 8. Flujo principal

1. Administración elige una cuenta sin titular y una persona.
2. El sistema bloquea la cuenta, comprueba que siga sin titular y que la persona sea consumidor.
3. Asigna, audita y devuelve la cuenta.

## 9. Flujos alternativos

Ninguno.

## 10. Excepciones

| Código | Caso | Respuesta |
|---|---|---|
| `VAL-012` | Falta la persona | `400` |
| `VAL-002` | La cuenta no existe, o la persona no existe o está eliminada | `404` |
| `EX-013` | **La cuenta ya tiene titular** | `409` |
| `EX-011` | **La persona no es consumidor** | `422` |
| `AUTH-001` / `AUTH-002` | Sin token / sin el permiso | `401` / `403` |

## 11. Validaciones

Las de §10.

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-SP-969` | Administración asigna una cuenta sin titular a un consumidor: `200`, la cuenta sale en sus cuentas (`RF-SP-079`), conserva su origen, y queda **auditado** |
| `CA-SP-970` | Con titular, `409` (`EX-013`); a quien no es consumidor, `422` (`EX-011`); cuenta o persona inexistentes, `404`; sin persona, `400`; sin el permiso, `403`. Nada cambia |
| `CA-SP-971` | `RF-SP-057` lista las cuentas sin titular —`user` nulo— y `?hasHolder=false` las filtra; `?hasHolder=true`, las demás |
| `CA-SP-972` | `V96` siembra `broker-accounts:assign-user` a `SUPERADMIN` y `ADMIN`, y a nadie más |

## 13. Casos límite

| Caso | Decisión |
|---|---|
| Dos administradores asignan la misma cuenta a la vez | El bloqueo hace que el segundo vea que ya tiene titular: `409` |

## 14. Preguntas abiertas resueltas

| # | Pregunta | Resolución |
|---|---|---|
| 1 | ¿Quién asigna? | Administración (09-10-2026); la persona, sola, si el número y el vendedor coinciden (`RN-SP-072`) |

## 15. Control de cambios

| Versión | Fecha | Cambio | Responsable |
|---|---|---|---|
| 0.1.0 | 09-10-2026 | Redacción inicial, a petición del responsable del proyecto. Criterios `CA-SP-969` a `CA-SP-972`. | Responsable del proyecto |
