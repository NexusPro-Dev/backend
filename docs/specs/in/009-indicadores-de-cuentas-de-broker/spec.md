# SPEC — `RF-IN-009` Consultar los indicadores de cuentas de broker de la red

| Campo | Valor |
|---|---|
| Requerimiento | `RF-IN-009` |
| Módulo | `IN` — Indicadores |
| Versión | 0.1.0 |
| Estado | **Aprobada** |
| Autor | Responsable técnico |
| Aprobada por | Responsable del proyecto |
| Fecha de aprobación | 10-10-2026 |

---

## 1. Objetivo

Saber **cuántas cuentas de broker originó cada vendedor y su red**, cuántas hicieron su primer depósito, cuántas operan y cuántas siguen sin titular, en un periodo y por broker.

## 2. Contexto

`RF-SP-058` (10-09-2026) daba el árbol de la fuerza comercial con el FTD de cada nodo, atribuido por **el vendedor principal del titular**. Desde entonces las cuentas de broker cambiaron: tienen tipo (`RN-SP-068`), **origen** en la cuenta `VENDEDOR` de un `afftrack` (`RN-SP-070`), pueden **no tener titular** (`RN-SP-072`), y el broker avisa **cuándo llega el primer depósito** y **cuántas operaciones** van (`RN-SP-073`, `RN-SP-074`). El 10-10-2026 el responsable del proyecto pidió «mover los indicadores de cuentas de broker, actualizando a la nueva novedad».

### 2.1 Lo que este requerimiento decide

Las cinco respuestas del responsable del proyecto (10-10-2026):

- **Se mudan a `IN`** —ruta y permiso de `IN`—, y **`RF-SP-058` se retira** con su ruta y su permiso.
- **Se atribuyen por el `afftrack`**: al dueño de la cuenta de origen, no al principal del titular.
- **Cada cifra por su fecha** (`RN-IN-015`).
- **Administración ve el árbol entero; cada vendedor, su rama.**
- **Tramos solo en los totales.**

Y lo que trae nuevo: **FTD por la fecha del primer depósito**, **operaciones**, **cuentas sin titular** y **desglose por broker**.

## 3. Actores

| Actor | Qué ve |
|---|---|
| Administración (`FUNCIONARIO`) | El árbol entero y lo no atribuido; con `sellerId`, la rama de ese vendedor |
| Un vendedor | Su rama: él como raíz y todo lo que cuelga de él; con `sellerId` de su red, la rama de ese |

## 4. Alcance

### 4.1 Incluye

`GET /api/v1/indicators/broker-accounts/network`.

### 4.2 No incluye

El listado de las cuentas (`RF-SP-057`, `RF-SP-083`); filtrar por broker —va desglosado—; operaciones por periodo exacto: el broker avisa cada operación, pero la cuenta guarda el acumulado, la primera y la última.

## 5. Reglas de negocio aplicables

| ID | Regla | Origen |
|---|---|---|
| `RN-IN-001` | Un indicador, un permiso | `requirements/in.md` §5.1 |
| `RN-IN-002` | Las cifras se acotan al alcance de quien mira | `requirements/in.md` §5.1 |
| `RN-IN-006` | Los indicadores no guardan nada | `requirements/in.md` §5.1 |
| `RN-IN-010` | Sin fechas, todo; y los tramos | `requirements/in.md` §5.1 |
| `RN-IN-015` | **Las cuentas de broker se cuentan por su `afftrack`, y cada cifra por su fecha** | `requirements/in.md` §5.1 |
| `RN-SP-048` | La suma por nodo —lo propio y la red—, sustituida en la atribución por `RN-IN-015` | `requirements/sp.md` §5.1 |
| `RN-SP-068`, `RN-SP-070` | Solo cuentan las `CONSUMIDOR`; su origen | `requirements/sp.md` §5.1 |

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción |
|---|---|---|
| `from` / `to` | No | Días, del calendario del negocio (`RN-IN-010`) |
| `sellerId` | No | Un vendedor del alcance: el árbol se enraíza en él. Fuera del alcance, cifras en cero |
| `granularity` | No | `DAY`, `WEEK` o `MONTH`: tramos en los totales |

### 6.2 Salida

El periodo; **los nodos** —cada vendedor con su usuario, su rol, **lo suyo** (`own`), **lo de su red** (`network`) y sus hijos—; **los totales**; **lo no atribuido** —solo con el alcance entero y sin `sellerId`—; y, con tramo, **las cuentas creadas y los FTD por tramo** de los totales.

**Cada bloque** trae: cuentas, FTD, pendientes, conversión, sin titular, consumidores, cuentas que operaron, operaciones y **el desglose por broker** con las mismas cifras salvo consumidores —todos los brokers del catálogo, en cero los que no tienen—.

## 7. Precondiciones y postcondiciones

**Precondición:** autenticado con el permiso. **Postcondición:** nada cambia.

## 8. Flujo principal

1. Alguien pide los indicadores, con o sin periodo, vendedor o tramo.
2. El sistema resuelve el alcance, arma el árbol de la fuerza comercial, cuenta lo de cada vendedor por el `afftrack` y suma hacia arriba.
3. Responde.

## 9. Flujos alternativos

### FA-001 — Sin nada en el alcance

Nodos vacíos y totales en cero —conversión nula—, nunca `403` ni `404` (`RN-IN-002`).

## 10. Excepciones

| Código | Caso | Respuesta |
|---|---|---|
| `VAL-002` | `from` posterior a `to` | `400` |
| `VAL-005` | `granularity` desconocida | `400` |
| `AUTH-001` / `AUTH-002` | Sin token / sin el permiso | `401` / `403` |

## 11. Validaciones

Las de §10, iguales a los demás indicadores.

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-IN-104` | Administración ve el árbol entero: cada cuenta `CONSUMIDOR` suma en **el dueño de su cuenta de origen** —también sin titular, y aunque el principal de su titular sea otro—, cada nodo trae `own` y `network` y la red suma la de sus hijos; las cuentas sin origen o con origen fuera de la fuerza comercial van a **lo no atribuido**; las `VENDEDOR` y las de titular eliminado no cuentan |
| `CA-IN-105` | **Cada cifra por su fecha**: con periodo, las cuentas son las creadas en él, las pendientes y la conversión son de esas, y los **FTD** son los primeros depósitos **llegados** en él aunque la cuenta sea anterior; sin fechas, los FTD son todas las cuentas en `FIRST_DEPOSIT`, también sin fecha de depósito |
| `CA-IN-106` | **Cuentas que operaron** son las de último aviso de operación en el periodo, y **operaciones** su acumulado; **sin titular** y **consumidores** cuentan sobre las creadas en el periodo, los consumidores sin repetir persona |
| `CA-IN-107` | Cada bloque trae **el desglose por broker** con todos los brokers del catálogo, en cero los que no tienen, y la suma del desglose es el bloque |
| `CA-IN-108` | Un vendedor ve **su rama** —él como raíz— y no lo no atribuido; `sellerId` de su red enraíza el árbol en ese vendedor; uno de fuera, nodos vacíos y cifras en cero |
| `CA-IN-109` | Con `granularity`, los totales traen las cuentas creadas y los FTD por tramo, todos los tramos presentes, y su suma es la de los totales; una `granularity` desconocida o `from` posterior a `to`, `400` |
| `CA-IN-110` | Sin `indicators:read-broker-accounts-network`, `403`; `V101` lo siembra a `FUNCIONARIO` y `VENDEDOR`, retira `broker-accounts:read-indicators` y la ruta vieja ya no existe |

## 13. Casos límite

| Caso | Decisión |
|---|---|
| Un vendedor cuyo superior no es fuerza comercial | Es raíz de su rama, como en `RF-SP-058` |
| Una cuenta en `FIRST_DEPOSIT` sin fecha de depósito (anterior a `V98`) | Cuenta como FTD sin periodo; con periodo no cae en ninguno |
| Un vendedor sin cuenta `VENDEDOR` | Su `own` va en cero; su red suma igual |

## 14. Preguntas abiertas resueltas

| # | Pregunta | Resolución |
|---|---|---|
| 1 | ¿Mudar o actualizar en el sitio? | Mudar a `IN` y retirar la ruta vieja (10-10-2026) |
| 2 | ¿A quién se atribuye? | Al dueño del `afftrack` (10-10-2026) |
| 3 | ¿Qué cuenta un periodo? | Cada cifra por su fecha (10-10-2026) |
| 4 | ¿Quién lo ve? | Administración el árbol; cada vendedor su rama (10-10-2026) |
| 5 | ¿Tramos? | Solo en los totales (10-10-2026) |

## 15. Control de cambios

| Versión | Fecha | Cambio | Responsable |
|---|---|---|---|
| 0.1.0 | 10-10-2026 | Redacción inicial, a petición del responsable del proyecto: sustituye a `RF-SP-058` (`RN-IN-015`). Criterios `CA-IN-104` a `CA-IN-110`. | Responsable del proyecto |
