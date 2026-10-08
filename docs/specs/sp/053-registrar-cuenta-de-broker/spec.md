# SPEC — `RF-SP-053` Registrar una cuenta de broker

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-053` |
| Módulo | `SP` — Sistema Principal |
| Versión | 0.1.0 |
| Estado | **Aprobada** |
| Autor | Responsable técnico |
| Aprobada por | Responsable del proyecto |
| Fecha de aprobación | 08-10-2026 |

---

## 1. Objetivo

Que una persona declare **una cuenta suya** en un broker cuando quiera, y que administración declare **la de cualquiera**, sin depender del formulario de registro.

## 2. Contexto

**`RF-SP-053` llevaba registrado desde el 08-09-2026 con actor y permiso «por decidir»**, y hasta hoy la única vía para declarar una cuenta era el registro por enlace (`RF-SP-045`, `RN-SP-042`). Quien no la declaró al registrarse, o abrió otra después, no tenía cómo añadirla. El 08-10-2026 el responsable del proyecto lo decidió: **las dos cosas**, el titular sobre sí mismo y administración sobre cualquiera —«crear, editar y eliminar mis cuentas de broker y las de un usuario»—. Editar es `RF-SP-080` y eliminar `RF-SP-081`; los tres comparten `RN-SP-067`.

### 2.1 Lo que este requerimiento decide

| Decisión | Qué se decidió |
|---|---|
| ¿Quién declara? | **El titular**, con un permiso propio que reciben todos los roles por su tipo, y **administración**, con un permiso amplio que solo tienen `SUPERADMIN` y `ADMIN` (08-10-2026). El superior comercial **solo mira** (`RN-SP-046`) |
| ¿Qué se declara? | **El broker y el identificador**, como en el registro (`RN-SP-040`): el nombre de usuario en el broker y el estado los pone después el broker |
| ¿En qué estado nace? | **`REGISTER`**, siempre (`RN-SP-045`), también si la declara administración |

## 3. Actores

| Actor | Papel |
|---|---|
| **Cualquier persona** con `broker-accounts:create-own` | Declara una cuenta suya |
| **Administración** con `broker-accounts:create` | Declara una cuenta a nombre de cualquier persona |

## 4. Alcance

### 4.1 Incluye

- Declarar una cuenta —broker e identificador— a nombre del actor o, con el permiso amplio, de otra persona.

### 4.2 No incluye

- **El nombre de usuario en el broker y el estado**: los pone el broker (`RF-SP-054`).
- **Varias de una vez**: una por petición. El registro por enlace sí declara varias, porque es un formulario único.
- **Que el superior declare por su equipo**: decidido el 08-10-2026, solo administración.

## 5. Reglas de negocio aplicables

| ID | Regla | Origen |
|---|---|---|
| `RN-SP-038` | Una cuenta de broker pertenece a UNA sola persona | `requirements/sp.md` §5.1 |
| `RN-SP-040` | El nombre de usuario en el broker llega DESPUÉS | `requirements/sp.md` §5.1 |
| `RN-SP-045` | Toda cuenta de broker declara en qué punto está | `requirements/sp.md` §5.1 |
| `RN-SP-067` | El titular gestiona sus cuentas mientras no tengan depósito; administración, cualquiera | `requirements/sp.md` §5.1 |

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción | Restricción |
|---|---|---|---|
| Persona | Solo para administración | De quién es la cuenta | Identificador en la ruta; el titular no lo indica, sale del token |
| `brokerId` | Sí | El broker | Del catálogo, activo |
| `accountId` | Sí | El identificador de la cuenta en el broker | Sin espacios a los lados; entre 1 y 80 caracteres |

### 6.2 Salida

**La cuenta declarada**, con la forma de una fila de `RF-SP-055`: broker, identificador, nombre de usuario en el broker —nulo— y estado —`REGISTER`—.

## 7. Precondiciones y postcondiciones

**Precondiciones:** el permiso que corresponda; la persona existe y no está eliminada.

**Postcondiciones:** la cuenta existe a nombre de la persona, en `REGISTER`, y **queda auditada** con quién la declaró.

## 8. Flujo principal

1. El actor envía broker e identificador.
2. El sistema valida los datos y que el broker exista y esté activo.
3. El sistema registra la cuenta a nombre de la persona.
4. El sistema audita el alta y devuelve la cuenta.

## 9. Flujos alternativos

Ninguno.

## 10. Excepciones

| Código | Caso | Respuesta |
|---|---|---|
| `VAL-012` | Falta el broker | `400` |
| `VAL-013` | Falta el identificador, o está en blanco | `400` |
| `VAL-015` | El identificador tiene más de 80 caracteres | `400` |
| `EX-008` | El broker no existe o está apagado — la misma respuesta para los dos, como en el registro | `422` |
| `EX-009` | **Esa cuenta ya está declarada**, por cualquiera, también por la misma persona (`RN-SP-038`) | `409` |
| `VAL-002` | La persona no existe o está eliminada (administración) | `404` |
| `AUTH-001` / `AUTH-002` | Sin token / sin el permiso | `401` / `403` |

## 11. Validaciones

Las de §10, **todas en la misma respuesta** cuando hay varias.

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-SP-915` | El titular declara una cuenta suya: `201`, nace en **`REGISTER`** con `brokerUsername` nulo, y sale en `GET /users/me/broker-accounts` |
| `CA-SP-916` | Administración, con `broker-accounts:create`, declara una cuenta a nombre de otra persona |
| `CA-SP-917` | Una cuenta **ya declarada** —mismo broker e identificador, de quien sea— responde `409` (`EX-009`) y **no se registra nada** |
| `CA-SP-918` | Un broker inexistente o apagado responde `422` (`EX-008`), el mismo para los dos |
| `CA-SP-919` | Sin broker, sin identificador, con el identificador en blanco o de más de 80 caracteres: `400` con **todos** los problemas, y nada cambia |
| `CA-SP-920` | Administración sobre una persona inexistente o eliminada: `404` |
| `CA-SP-921` | Sin el permiso de cada ruta, `403`; sin token, `401` |
| `CA-SP-922` | El alta queda **auditada**, con quién la hizo |
| `CA-SP-937` | `V90` siembra los seis permisos: `create-own`, `update-own` y `delete-own` a **todo rol por su tipo**, y `create`, `update` y `delete` a `SUPERADMIN` y `ADMIN`. Catálogo **216**, `ADMIN` 214, `CLIENTE` 40 |

## 13. Casos límite

| Caso | Decisión |
|---|---|
| Otra cuenta en el mismo broker | Se admite (`RN-SP-038`): lo único que se acota es de quién es cada cuenta |
| El identificador llega con espacios a los lados | Se guardan sin ellos, y la unicidad se comprueba sin ellos |

## 14. Preguntas abiertas resueltas

| # | Pregunta | Resolución |
|---|---|---|
| 1 | ¿Quién declara? | El titular y administración (08-10-2026) |
| 2 | ¿Y el superior comercial? | No: solo mira (08-10-2026) |

## 15. Control de cambios

| Versión | Fecha | Cambio | Responsable |
|---|---|---|---|
| 0.1.0 | 08-10-2026 | Redacción inicial. Cierra el «por decidir» del 08-09-2026: el titular con `broker-accounts:create-own` y administración con `broker-accounts:create`. Nace `RN-SP-067`. Criterios `CA-SP-915` a `CA-SP-922` y `CA-SP-937` (la siembra de los tres requerimientos). | Responsable del proyecto |
