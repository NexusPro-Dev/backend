# SPEC — `RF-SP-080` Editar una cuenta de broker

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-080` |
| Módulo | `SP` — Sistema Principal |
| Versión | 0.1.0 |
| Estado | **Aprobada** |
| Autor | Responsable técnico |
| Aprobada por | Responsable del proyecto |
| Fecha de aprobación | 08-10-2026 |

---

## 1. Objetivo

Corregir **el identificador** de una cuenta de broker mal escrito: el titular en las suyas, administración en las de cualquiera.

## 2. Contexto

Nace con `RF-SP-053` y `RF-SP-081`, el 08-10-2026, a petición del responsable del proyecto. **Solo se edita el identificador**, por su decisión del mismo día: quien se equivocó de broker borra la cuenta y declara otra. El nombre de usuario en el broker y el estado los pone el broker (`RN-SP-040`, `RN-SP-045`), y no se escriben a mano.

**Con depósito confirmado, el titular ya no la toca** (`RN-SP-067`): a esa cuenta están atados el primer depósito y los indicadores de la red (`RF-SP-058`), y cambiarla cambiaría a quién se atribuyen. Administración sí puede, para corregir un error.

## 3. Actores

| Actor | Papel |
|---|---|
| **El titular** con `broker-accounts:update-own` | Corrige una cuenta suya en `REGISTER` |
| **Administración** con `broker-accounts:update` | Corrige cualquier cuenta de cualquier persona, en cualquier estado |

## 4. Alcance

### 4.1 Incluye

- Cambiar el identificador de la cuenta.

### 4.2 No incluye

- **Cambiar el broker**: se borra (`RF-SP-081`) y se declara otra (`RF-SP-053`).
- **El nombre de usuario en el broker y el estado**: los pone el broker (`RF-SP-054`).
- **Cambiar de titular**: una cuenta no se traspasa.

## 5. Reglas de negocio aplicables

| ID | Regla | Origen |
|---|---|---|
| `RN-SP-038` | Una cuenta de broker pertenece a UNA sola persona | `requirements/sp.md` §5.1 |
| `RN-SP-067` | El titular gestiona sus cuentas mientras no tengan depósito; administración, cualquiera | `requirements/sp.md` §5.1 |

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción | Restricción |
|---|---|---|---|
| Persona | Solo para administración | De quién es la cuenta | En la ruta |
| Cuenta | Sí | La cuenta que se corrige | Su identificador interno, en la ruta |
| `accountId` | Sí | El identificador nuevo en el broker | Sin espacios a los lados; entre 1 y 80 caracteres |

### 6.2 Salida

La cuenta corregida, con la forma de una fila de `RF-SP-055`. **El broker, el estado y el nombre de usuario no cambian.**

## 7. Precondiciones y postcondiciones

**Precondiciones:** el permiso que corresponda; la cuenta existe y es de esa persona —del actor, si es el titular—; el titular, además, solo sobre una cuenta en `REGISTER`.

**Postcondiciones:** la cuenta tiene el identificador nuevo y **queda auditado** el cambio, antes y después.

## 8. Flujo principal

1. El actor envía el identificador nuevo.
2. El sistema valida el dato, localiza la cuenta y la bloquea.
3. El sistema comprueba `RN-SP-067`.
4. El sistema guarda el identificador, audita el cambio y devuelve la cuenta.

## 9. Flujos alternativos

| ID | Caso | Comportamiento |
|---|---|---|
| `FA-001` | El identificador nuevo es el mismo | `200` con la cuenta, sin escribir ni auditar |

## 10. Excepciones

| Código | Caso | Respuesta |
|---|---|---|
| `VAL-013` | Falta el identificador, o está en blanco | `400` |
| `VAL-015` | El identificador tiene más de 80 caracteres | `400` |
| `VAL-002` | La cuenta no existe, no es de esa persona, o la persona no existe (administración); **para el titular, la cuenta de otro** responde igual | `404` |
| `EX-010` | **El titular sobre una cuenta con depósito confirmado** (`RN-SP-067`) | `409` |
| `EX-009` | El identificador nuevo **ya está declarado** en ese broker (`RN-SP-038`) | `409` |
| `AUTH-001` / `AUTH-002` | Sin token / sin el permiso | `401` / `403` |

## 11. Validaciones

`VAL-013` y `VAL-015` antes de tocar nada.

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-SP-923` | El titular corrige el identificador de una cuenta suya en `REGISTER`: `200` con la cuenta; el broker, el estado y el nombre de usuario no cambian |
| `CA-SP-924` | El titular sobre una cuenta suya en **`FIRST_DEPOSIT`**: `409` (`EX-010`), y nada cambia |
| `CA-SP-925` | Administración corrige la cuenta de otra persona, **también en `FIRST_DEPOSIT`** |
| `CA-SP-926` | Un identificador **ya declarado** en ese broker: `409` (`EX-009`), y nada cambia |
| `CA-SP-927` | La cuenta de otra persona por la ruta propia, una inexistente, o una que no es de la persona de la ruta: `404` |
| `CA-SP-928` | Sin identificador, en blanco o de más de 80 caracteres: `400` |
| `CA-SP-929` | Sin el permiso de cada ruta, `403`; sin token, `401` |
| `CA-SP-930` | El cambio queda **auditado**, con el identificador de antes y el de después |

## 13. Casos límite

| Caso | Decisión |
|---|---|
| Administración corrige una cuenta en `FIRST_DEPOSIT` | Se permite, y el estado y el nombre de usuario se conservan: es la corrección de una errata, no otra cuenta |
| El broker de la cuenta está apagado | Se puede corregir igual: apagar un broker no congela lo declarado |

## 14. Preguntas abiertas resueltas

| # | Pregunta | Resolución |
|---|---|---|
| 1 | ¿Qué se edita? | **Solo el identificador** (08-10-2026) |
| 2 | ¿Con depósito confirmado? | El titular no; administración sí (08-10-2026) |

## 15. Control de cambios

| Versión | Fecha | Cambio | Responsable |
|---|---|---|---|
| 0.1.0 | 08-10-2026 | Redacción inicial, con `RF-SP-053` y `RF-SP-081`. Solo el identificador; el titular no toca una cuenta con depósito (`RN-SP-067`). Criterios `CA-SP-923` a `CA-SP-930`. | Responsable del proyecto |
