# SPEC — `RF-SP-081` Eliminar una cuenta de broker

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-081` |
| Módulo | `SP` — Sistema Principal |
| Versión | 0.1.0 |
| Estado | **Aprobada** |
| Autor | Responsable técnico |
| Aprobada por | Responsable del proyecto |
| Fecha de aprobación | 08-10-2026 |

---

## 1. Objetivo

Que una cuenta de broker deje de estar a nombre de una persona: el titular retira las suyas y administración, las de cualquiera.

## 2. Contexto

Nace con `RF-SP-053` y `RF-SP-080`, el 08-10-2026. **El borrado es físico y queda auditado**, por decisión del responsable del proyecto del mismo día: nada del sistema apunta a una cuenta de broker, y la auditoría guarda cómo era. **La cuenta queda libre**: otra persona —o la misma— puede volver a declararla (`RN-SP-038`). Es lo que `user_brokers` dejó pendiente al nacer sin `deleted_at` —«desvincular no está decidido»—.

**Con depósito confirmado, el titular no la borra** (`RN-SP-067`), por lo mismo que no la edita; administración sí.

## 3. Actores

| Actor | Papel |
|---|---|
| **El titular** con `broker-accounts:delete-own` | Retira una cuenta suya en `REGISTER` |
| **Administración** con `broker-accounts:delete` | Retira cualquier cuenta de cualquier persona |

## 4. Alcance

### 4.1 Incluye

- Borrar la cuenta y auditar cómo era.

### 4.2 No incluye

- **Borrado lógico**: descartado el 08-10-2026.
- **Impedir borrar la última**: quien se registró por un enlace `BECA → BECA` necesita una para que su depósito se reconozca (`RN-SP-042`), pero puede declarar otra en cualquier momento (`RF-SP-053`).

## 5. Reglas de negocio aplicables

| ID | Regla | Origen |
|---|---|---|
| `RN-SP-038` | Una cuenta de broker pertenece a UNA sola persona | `requirements/sp.md` §5.1 |
| `RN-SP-067` | El titular gestiona sus cuentas mientras no tengan depósito; administración, cualquiera | `requirements/sp.md` §5.1 |

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción |
|---|---|---|
| Persona | Solo para administración | En la ruta |
| Cuenta | Sí | Su identificador interno, en la ruta |

### 6.2 Salida

Ninguna: `204`.

## 7. Precondiciones y postcondiciones

**Precondiciones:** las de `RF-SP-080`.

**Postcondiciones:** la cuenta no existe y **queda auditada** con todos sus datos.

## 8. Flujo principal

1. El actor pide borrar la cuenta.
2. El sistema la localiza y la bloquea.
3. El sistema comprueba `RN-SP-067`.
4. El sistema la borra y audita cómo era.

## 9. Flujos alternativos

Ninguno.

## 10. Excepciones

| Código | Caso | Respuesta |
|---|---|---|
| `VAL-002` | La cuenta no existe, no es de esa persona, o la persona no existe; para el titular, la de otro responde igual | `404` |
| `EX-010` | El titular sobre una cuenta con depósito confirmado | `409` |
| `AUTH-001` / `AUTH-002` | Sin token / sin el permiso | `401` / `403` |

## 11. Validaciones

Ninguna más allá de las de la ruta.

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-SP-931` | El titular borra una cuenta suya en `REGISTER`: `204`, deja de salir en sus cuentas, y **el mismo broker e identificador se pueden volver a declarar** |
| `CA-SP-932` | El titular sobre una cuenta suya en **`FIRST_DEPOSIT`**: `409` (`EX-010`), y la cuenta sigue |
| `CA-SP-933` | Administración borra la cuenta de otra persona, **también en `FIRST_DEPOSIT`** |
| `CA-SP-934` | La cuenta de otro por la ruta propia, una inexistente, o una que no es de la persona de la ruta: `404` |
| `CA-SP-935` | Sin el permiso de cada ruta, `403`; sin token, `401` |
| `CA-SP-936` | El borrado queda **auditado**, con el broker, el identificador, el nombre de usuario y el estado que tenía |

## 13. Casos límite

| Caso | Decisión |
|---|---|
| Dos borrados simultáneos de la misma cuenta | Uno borra; el otro responde `404` |

## 14. Preguntas abiertas resueltas

| # | Pregunta | Resolución |
|---|---|---|
| 1 | ¿Físico o lógico? | **Físico, auditado** (08-10-2026) |
| 2 | ¿Con depósito confirmado? | El titular no; administración sí (08-10-2026) |

## 15. Control de cambios

| Versión | Fecha | Cambio | Responsable |
|---|---|---|---|
| 0.1.0 | 08-10-2026 | Redacción inicial, con `RF-SP-053` y `RF-SP-080`. Borrado físico y auditado; el titular no borra una cuenta con depósito (`RN-SP-067`). Criterios `CA-SP-931` a `CA-SP-936`. | Responsable del proyecto |
