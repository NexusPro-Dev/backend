# SPEC — `RF-IN-010` Consultar el resumen de cuentas de broker y de mi personal

| Campo | Valor |
|---|---|
| Requerimiento | `RF-IN-010` |
| Módulo | `IN` — Indicadores |
| Versión | 0.4.0 |
| Estado | **Aprobada** |
| Autor | Responsable técnico |
| Aprobada por | Responsable del proyecto |
| Fecha de aprobación | 15-10-2026 |

---

## 1. Objetivo

Ver de un vistazo **cuántas cuentas de broker hay y cuántas depositaron**, en total y hoy, y **cuánto lleva cada persona de mi personal**, agrupada por rol.

## 2. Contexto

`RF-IN-009` responde con un árbol y un periodo. El 15-10-2026 el responsable del proyecto pidió unas tarjetas y un listado plano: «1. Registros, FTDs, Registros sin usuarios, registros sin agentes, Registros de hoy (registros y ftd [a estos se les trae por la fecha de depósito]) 2. Listado de mi personal clasificado por roles, con total registros - total ftds - Registros hoy y FTDS hoy».

### 2.1 Lo que este requerimiento decide

Las cuatro respuestas del responsable del proyecto (15-10-2026):

- **Alcance como `RF-IN-009`**: administración ve todas las cuentas y todo el personal; un vendedor, lo originado por su red y las personas de ella.
- **Cada persona cuenta lo que originó ella**, por su `afftrack` (`RN-IN-015`), no lo de su red: la columna se puede sumar.
- ~~**«Sin agentes» es sin vendedor de origen**: lo no atribuido de `RF-IN-009`.~~ **Sustituido el 16-10-2026 por «sin operar»** (`RN-IN-016`): en `FIRST_DEPOSIT` y sin ninguna operación.
- **El registro se fecha por la creación en la plataforma** (`created_at`); **el FTD, por la fecha del primer depósito**.

## 3. Actores

| Actor | Qué ve |
|---|---|
| Administración (`FUNCIONARIO`) | Todas las cuentas `CONSUMIDOR` y toda la fuerza comercial |
| Un vendedor | Lo originado por él y por su red, y esas personas —él incluido— |

## 4. Alcance

### 4.1 Incluye

`GET /api/v1/indicators/broker-accounts/summary`.

### 4.2 No incluye

Periodo elegible —son «total» y «hoy»; para otro periodo está `RF-IN-009`—; desglose por broker; el listado de las cuentas (`RF-SP-057`, `RF-SP-083`).

## 5. Reglas de negocio aplicables

| ID | Regla | Origen |
|---|---|---|
| `RN-IN-001` | Un indicador, un permiso | `requirements/in.md` §5.1 |
| `RN-IN-002` | Las cifras se acotan al alcance de quien mira | `requirements/in.md` §5.1 |
| `RN-IN-006` | Los indicadores no guardan nada | `requirements/in.md` §5.1 |
| `RN-IN-015` | Las cuentas de broker se cuentan por su `afftrack`, y cada cifra por su fecha | `requirements/in.md` §5.1 |
| `RN-SP-068`, `RN-SP-070` | Solo cuentan las `CONSUMIDOR`; su origen | `requirements/sp.md` §5.1 |

## 6. Datos

### 6.1 Entrada

Ninguna. «Hoy» es el día del calendario del negocio (`BusinessCalendar`).

### 6.2 Salida

- **`today`** — el día que se tomó como hoy.
- **`totals`** — `accounts` (todas), `ftd` (todas en `FIRST_DEPOSIT`), `withoutHolder`, `withoutOperations` (en `FIRST_DEPOSIT` y sin ninguna operación, `RN-IN-016`; hasta el 16-10-2026 iba aquí `withoutSeller`), `accountsToday` (creadas hoy) y `ftdToday` (primer depósito llegado hoy).
- **`staff`** — un grupo por rol, en el orden de la jerarquía (de la cúspide hacia abajo), cada uno con `roleCode`, las cuatro cifras sumadas y **`people`**: cada persona con su usuario y sus cuatro cifras —`accounts`, `ftd`, `accountsToday`, `ftdToday`—, por nombre de usuario. **Personas en cero también salen.**

## 7. Precondiciones y postcondiciones

**Precondición:** autenticado con el permiso. **Postcondición:** nada cambia.

## 8. Flujo principal

1. Alguien pide el resumen.
2. El sistema resuelve el alcance, cuenta por vendedor de origen la historia entera y el día de hoy, y agrupa a las personas por rol.
3. Responde.

## 9. Flujos alternativos

### FA-001 — Un vendedor sin nada originado

Sus cifras en cero; él sale en el listado.

## 10. Excepciones

| Código | Caso | Respuesta |
|---|---|---|
| `AUTH-001` / `AUTH-002` | Sin token / sin el permiso | `401` / `403` |

## 11. Validaciones

No hay parámetros.

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-IN-111` | Administración: `accounts` cuenta todas las `CONSUMIDOR` —con titular o sin él, con origen o sin él—, `ftd` las que están en `FIRST_DEPOSIT`, `withoutHolder` las sin titular y `withoutOperations` las en `FIRST_DEPOSIT` sin ninguna operación (antes `withoutSeller`, 16-10-2026); las `VENDEDOR` no cuentan |
| `CA-IN-112` | **Hoy por su fecha**: `accountsToday` son las creadas hoy y `ftdToday` las de primer depósito llegado hoy, aunque la cuenta sea de otro día |
| `CA-IN-113` | `staff` agrupa a la fuerza comercial por rol, de la cúspide hacia abajo; cada persona cuenta **lo que originó ella** —no su red— con sus cuatro cifras, las personas sin nada salen en cero, y cada grupo suma a sus personas |
| `CA-IN-114` | Un vendedor ve solo lo originado por él y su red, y a esas personas; ~~`withoutSeller` va nulo~~ (16-10-2026: `withoutOperations` es el de su alcance) |
| `CA-IN-115` | Sin `indicators:read-broker-accounts-summary`, `403`; la migración lo siembra a `FUNCIONARIO` y `VENDEDOR` |
| `CA-IN-116` | `withoutOperations` cuenta las `CONSUMIDOR` en `FIRST_DEPOSIT` con cero operaciones, del alcance de quien pregunta: una que depositó y opera no cuenta, una en `REGISTER` tampoco (16-10-2026) |
| `CA-IN-117` | Cada persona de `staff[].people[]` trae `supervisorId` —su superior vigente, nulo si no tiene; puede ser alguien fuera de la respuesta— y `afftracks`, el `afftrack` de cada cuenta `VENDEDOR` suya con su broker, por nombre de broker, vacío si no tiene (16-10-2026) |
| `CA-IN-118` | Filtros opcionales: `from`/`to` —días del negocio, `to` entero— acotan `totals` y `staff`, cada cifra por su fecha, **y no lo de hoy**; `brokerId` acota todo, también lo de hoy y los `afftracks`; `sellerId` deja la rama de ese vendedor, él incluido, sin lo no atribuido, y de fuera del alcance todo en cero; `from` posterior a `to` es `400` (16-10-2026) |

## 13. Casos límite

| Caso | Decisión |
|---|---|
| Una cuenta con origen fuera de la fuerza comercial | Cuenta en los totales de administración, como lo no atribuido de `RF-IN-009` (hasta el 16-10-2026 iba en `withoutSeller`) |
| Una cuenta en `FIRST_DEPOSIT` sin fecha de depósito | Cuenta en `ftd`; nunca en `ftdToday` |
| Un vendedor con dos roles vendedores | Va en el grupo de su rol de mayor rango (`roleCode` de la fuerza comercial) |

## 14. Preguntas abiertas resueltas

| # | Pregunta | Resolución |
|---|---|---|
| 1 | ¿Quién lo ve? | Administración todo; un vendedor, su red (15-10-2026) |
| 2 | ¿Qué cuenta cada persona? | Lo que originó ella (15-10-2026) |
| 3 | ¿Qué es «sin agentes»? | Sin vendedor de origen (15-10-2026) |
| 4 | ¿Qué fecha tiene un registro? | La de creación en la plataforma (15-10-2026) |

## 15. Control de cambios

| Versión | Fecha | Cambio | Responsable |
|---|---|---|---|
| 0.1.0 | 15-10-2026 | Redacción inicial, a petición del responsable del proyecto. Criterios `CA-IN-111` a `CA-IN-115`. | Responsable del proyecto |
| 0.2.0 | 16-10-2026 | **«Sin agentes» pasa a «sin operar»** (`RN-IN-016`, Art. I.7), a petición del responsable del proyecto: `withoutSeller` sale y entra `withoutOperations`, para todo alcance. `CA-IN-116`. | Responsable del proyecto |
| 0.3.0 | 16-10-2026 | **La persona del personal trae su superior y sus `afftrack`**, a petición del responsable del proyecto: «para los indicadores del staff, en people traerá también los afftrack que tenga, además de traer el id del supervisorId, para que el front se encargue de hacer los acumulativos por la red». `supervisorId` y `afftracks` en `people`; las cifras siguen siendo las propias, sin acumular. `CA-IN-117`. | Responsable del proyecto |
| 0.4.0 | 16-10-2026 | **El resumen se filtra por fecha, broker y vendedor**, a petición del responsable del proyecto: «los indicadores filtran por fecha, brokers y agente?» — «en summary, lo de hoy solo no aplica la fecha». `from`/`to` no tocan lo de hoy; `brokerId` y `sellerId` sí. `CA-IN-118`. | Responsable del proyecto |
