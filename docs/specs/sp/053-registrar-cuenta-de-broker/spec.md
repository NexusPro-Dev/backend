# SPEC — `RF-SP-053` Registrar una cuenta de broker

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-053` |
| Módulo | `SP` — Sistema Principal |
| Versión | 0.3.0 |
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
| `RN-SP-068` | Las cuentas de broker son de dos tipos: de vendedor y de consumidor | `requirements/sp.md` §5.1 |
| `RN-SP-070` | Una cuenta de consumidor sabe qué cuenta de vendedor la originó | `requirements/sp.md` §5.1 |
| `RN-SP-071` | El `afftrack` es de la cuenta de vendedor, y uno por broker | `requirements/sp.md` §5.1 |
| `RN-SP-072` | Una cuenta puede existir sin titular, y se asocia después | `requirements/sp.md` §5.1 |

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción | Restricción |
|---|---|---|---|
| Persona | Solo para administración | De quién es la cuenta | Identificador en la ruta; el titular no lo indica, sale del token |
| `brokerId` | Sí | El broker | Del catálogo, activo |
| `accountId` | Sí | El identificador de la cuenta en el broker | Sin espacios a los lados; entre 1 y 80 caracteres |
| `afftrack` | No | El código de afiliado del vendedor en ese broker (`RN-SP-071`) | Solo si la cuenta será `VENDEDOR`; sin espacios a los lados; hasta 80 caracteres |

### 6.2 Salida

**La cuenta declarada**, con la forma de una fila de `RF-SP-055`: broker, identificador, nombre de usuario en el broker —nulo— y estado —`REGISTER`— y **tipo** —`kind`, `VENDEDOR` o `CONSUMIDOR` (`RN-SP-068`)—.

## 7. Precondiciones y postcondiciones

**Precondiciones:** el permiso que corresponda; la persona existe y no está eliminada.

**Postcondiciones:** la cuenta existe a nombre de la persona, en `REGISTER`, y **queda auditada** con quién la declaró.

## 8. Flujo principal

1. El actor envía broker e identificador.
2. El sistema valida los datos y que el broker exista y esté activo.
3. El sistema decide el tipo por el tipo de rol del titular (`RN-SP-068`). Si es `CONSUMIDOR`, busca **la cuenta `VENDEDOR` de su vendedor principal en ese broker** como origen (`RN-SP-070`).
4. Si el número ya existe **sin titular** y su origen es de ese mismo vendedor, **la asocia a la persona** (`RN-SP-072`); si no, registra la cuenta a nombre de la persona, con su origen o sin él.
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
| `EX-011` | **El titular no es vendedor ni consumidor**: no porta ningún rol de esos dos tipos (`RN-SP-068`) | `422` |
| `VAL-016` | Un `afftrack` en una cuenta que será `CONSUMIDOR`, o de más de 80 caracteres | `400` |
| `EX-014` | **Ese `afftrack` ya es de otra cuenta en ese broker** (`RN-SP-071`) | `409` |
| `EX-015` | **El vendedor ya tiene su cuenta `VENDEDOR` en ese broker** (`RN-SP-070`) | `409` |
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
| `CA-SP-938` | La cuenta de quien porta un rol **vendedor** nace `VENDEDOR`, y la de quien porta uno **consumidor**, `CONSUMIDOR`; cuando la declara administración, el tipo es **el del titular**, no el de quien declara |
| `CA-SP-939` | Quien porta un rol vendedor **y** uno consumidor declara cuentas `VENDEDOR` |
| `CA-SP-940` | Quien no porta rol vendedor ni consumidor recibe `422` (`EX-011`), por las dos rutas, y **no se registra nada** |
| `CA-SP-941` | El registro por enlace (`RF-SP-045`) declara sus cuentas `CONSUMIDOR` |
| `CA-SP-942` | El motor rechaza una cuenta `VENDEDOR` en `FIRST_DEPOSIT` y un tipo fuera de los dos (`ck_user_brokers_ftd_solo_consumidor`, `ck_user_brokers_kind`) |
| `CA-SP-943` | Toda fila de `RF-SP-055`, `RF-SP-056`, `RF-SP-057` y `RF-SP-079` lleva `kind`; `RF-SP-057` filtra por `?kind=` y su resumen lo respeta; un `kind` desconocido es `400` (`VAL-001`) |
| `CA-SP-944` | Los indicadores de la red (`RF-SP-058`) **no cuentan** las cuentas `VENDEDOR`, aunque su titular porte también un rol consumidor |
| `CA-SP-945` | `V91` clasifica las cuentas existentes: `VENDEDOR` la de quien porta rol vendedor, `CONSUMIDOR` las demás y **toda cuenta ya depositada** |
| `CA-SP-954` | Una cuenta `VENDEDOR` se registra con su `afftrack`, y sale en las consultas; un `afftrack` en una que será `CONSUMIDOR` es `400` (`VAL-016`) |
| `CA-SP-955` | El mismo `afftrack` en el mismo broker, también con otras mayúsculas, es `409` (`EX-014`); en otro broker se admite |
| `CA-SP-956` | Una segunda cuenta `VENDEDOR` del mismo vendedor en el mismo broker es `409` (`EX-015`); en otro broker se admite |
| `CA-SP-957` | El `afftrack` se cambia al editar, por la ruta propia y por la de administración, y queda auditado; un `PATCH` sin `accountId` ni `afftrack` es `400` |
| `CA-SP-958` | La cuenta `CONSUMIDOR` nace con **origen**: la `VENDEDOR` de su vendedor principal en ese broker; si el vendedor no tiene, **sin origen**, y se registra igual |
| `CA-SP-959` | El registro por enlace (`RF-SP-045`) declara la cuenta con origen en la `VENDEDOR` del vendedor del enlace |
| `CA-SP-960` | Si el número ya existe **sin titular** y su origen es la `VENDEDOR` del vendedor de quien declara, la cuenta **se asocia** a quien declara —por las dos rutas de alta y por el registro por enlace—, y queda auditado |
| `CA-SP-961` | Si existe sin titular pero su origen es de **otro** vendedor, o no tiene, es `409` (`EX-009`) y la cuenta **sigue sin titular** |
| `CA-SP-962` | Una cuenta `VENDEDOR` que originó cuentas **no se borra** (`409`, `EX-012`), ni por su titular ni por administración |
| `CA-SP-963` | Toda fila de las consultas trae `afftrack` y `referrer` —la cuenta de origen: identificador, `afftrack` y su titular— |
| `CA-SP-937` | `V90` siembra los seis permisos: `create-own`, `update-own` y `delete-own` a **todo rol por su tipo**, y `create`, `update` y `delete` a `SUPERADMIN` y `ADMIN`. Catálogo **216**, `ADMIN` 214, `CLIENTE` 40 |

## 13. Casos límite

| Caso | Decisión |
|---|---|
| El titular cambia de rol después de declararla | La cuenta **conserva su tipo** (`RN-SP-068`); si hay que corregirlo, administración la borra y la declara de nuevo |
| Otra cuenta en el mismo broker | Se admite (`RN-SP-038`): lo único que se acota es de quién es cada cuenta |
| El identificador llega con espacios a los lados | Se guardan sin ellos, y la unicidad se comprueba sin ellos |

## 14. Preguntas abiertas resueltas

| # | Pregunta | Resolución |
|---|---|---|
| 1 | ¿Quién declara? | El titular y administración (08-10-2026) |
| 2 | ¿Y el superior comercial? | No: solo mira (08-10-2026) |
| 3 | ¿Qué distingue una cuenta de vendedor de una de consumidor? | Una columna de tipo en la cuenta; la de vendedor no tiene FTD; el tipo lo pone el sistema por el tipo de rol del titular (09-10-2026) |

## 15. Control de cambios

| Versión | Fecha | Cambio | Responsable |
|---|---|---|---|
| 0.1.0 | 08-10-2026 | Redacción inicial. Cierra el «por decidir» del 08-09-2026: el titular con `broker-accounts:create-own` y administración con `broker-accounts:create`. Nace `RN-SP-067`. Criterios `CA-SP-915` a `CA-SP-922` y `CA-SP-937` (la siembra de los tres requerimientos). | Responsable del proyecto |
| 0.2.0 | 09-10-2026 | **Dos tipos de cuenta** (`RN-SP-068`), a petición del responsable del proyecto: el alta fija `kind` por el tipo de rol del titular, `EX-011` para quien no es vendedor ni consumidor, la de vendedor sin FTD. Criterios `CA-SP-938` a `CA-SP-945`. | Responsable del proyecto |
| 0.3.0 | 09-10-2026 | **`afftrack`, cuenta de origen y cuentas sin titular** (`RN-SP-070` a `RN-SP-072`), con los dos casos del responsable del proyecto: la cuenta que nace en la plataforma toma como origen la `VENDEDOR` del vendedor principal, y la que llegó antes del broker se asocia sola si el número y el vendedor coinciden. Criterios `CA-SP-954` a `CA-SP-963`. Cubre también lo que cambia en `RF-SP-080` (editar el `afftrack`) y `RF-SP-081` (no borrar un origen). | Responsable del proyecto |
