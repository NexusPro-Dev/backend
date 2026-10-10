# SPEC — `RF-SP-083` Consultar las cuentas de broker que originó mi red

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-083` |
| Módulo | `SP` — Sistema Principal |
| Versión | 0.1.0 |
| Estado | **Aprobada** |
| Autor | Responsable técnico |
| Aprobada por | Responsable del proyecto |
| Fecha de aprobación | 10-10-2026 |

---

## 1. Objetivo

Que un vendedor vea **las cuentas de broker de consumidor creadas con su `afftrack` y con el de toda su línea comercial hacia abajo**, y de qué vendedor viene cada una.

## 2. Contexto

Desde el 09-10-2026 cada cuenta `CONSUMIDOR` apunta a la cuenta `VENDEDOR` que la originó (`RN-SP-070`): la del vendedor principal si nació en la plataforma, la del `afftrack` del aviso si nació en el broker. El responsable del proyecto pidió el 10-10-2026: «quiero poder [ver] las cuentas de brokers que se han creado con mi afftrack y con la línea comercial correspondiente».

### 2.1 Lo que este requerimiento decide

- **La línea comercial es toda la red hacia abajo**, en todos los niveles, la vigente —la misma que `RN-SP-047`—, por respuesta del responsable del proyecto.
- **El alcance lo pone el sistema** a partir de quién pregunta: no hay parámetro que lo amplíe.
- **Se mira el origen, no el titular**: una cuenta que llegó del broker sin titular también la originó su enlace, y aparece.
- **No sustituye a `RF-SP-056`**, que mira las cuentas de las personas del equipo directo.

## 3. Actores

Un vendedor —o un funcionario con red—, con `broker-accounts:read-own-referred`.

## 4. Alcance

### 4.1 Incluye

`GET /api/v1/users/me/referred-broker-accounts`, paginado, con el resumen por estado y por broker de `RF-SP-057`.

### 4.2 No incluye

Ver las originadas por otro vendedor fuera de la red propia; las cuentas `VENDEDOR`; editar nada.

## 5. Reglas de negocio aplicables

| ID | Regla | Origen |
|---|---|---|
| `RN-SP-045` | Toda cuenta declara en qué punto está | `requirements/sp.md` §5.1 |
| `RN-SP-047` | La red de un vendedor, en profundidad y vigente | `requirements/sp.md` §5.1 |
| `RN-SP-070` | Una cuenta de consumidor sabe qué cuenta de vendedor la originó | `requirements/sp.md` §5.1 |
| `RN-SP-071` | El `afftrack` es de la cuenta de vendedor | `requirements/sp.md` §5.1 |
| `RN-SP-075` | **Un vendedor ve las cuentas que originó su red** | `requirements/sp.md` §5.1 |

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción |
|---|---|---|
| `status` | No | `REGISTER` o `FIRST_DEPOSIT` |
| `brokerId` | No | Un broker del catálogo |
| `sellerId` | No | **Un vendedor de la red**: solo las que originó su cuenta. Uno de fuera de la red da la página vacía |
| `hasHolder` | No | `false`, las que aún no tienen titular; `true`, las demás |
| `search` | No | Fragmento del **número de cuenta** o del **nombre de usuario en el broker** —también del titular—, sin acentos ni mayúsculas |
| `from` / `to` | No | **Cuándo se creó la cuenta**, semiabierto, como `RF-SP-057` |
| `page` / `size` | No | Paginación |

### 6.2 Salida

La página y el resumen de `RF-SP-057`, con la misma fila: la cuenta, su estado y lo que avisó el broker, **el titular** —nulo si no tiene— y **el origen**: la cuenta `VENDEDOR`, su `afftrack` y **el vendedor**, con nombre y apellido.

## 7. Precondiciones y postcondiciones

**Precondición:** autenticado con el permiso. **Postcondición:** nada cambia.

## 8. Flujo principal

1. El vendedor pide sus cuentas originadas, con o sin filtros.
2. El sistema toma su red vigente —él y los que cuelgan de él—, y devuelve las cuentas `CONSUMIDOR` cuyo origen es de alguno de ellos.

## 9. Flujos alternativos

### FA-001 — Sin cuentas originadas

Quien no tiene cuenta `VENDEDOR`, ni red, ni cuentas originadas recibe `200` con la página vacía y el resumen en ceros.

## 10. Excepciones

| Código | Caso | Respuesta |
|---|---|---|
| `VAL-001` | `status` desconocido, o `from` posterior a `to` | `400` |
| `VAL-003` | Paginación fuera de límites | `400` |
| `AUTH-001` / `AUTH-002` | Sin token / sin el permiso | `401` / `403` |

## 11. Validaciones

Las de §10, iguales a `RF-SP-057`.

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-SP-1003` | Un vendedor ve las cuentas `CONSUMIDOR` originadas por **su** cuenta `VENDEDOR` y por las de **su red en todos los niveles** —también las que no tienen titular—, y cada fila trae **el vendedor de origen** con su `afftrack`, nombre y apellido |
| `CA-SP-1004` | **No ve** las originadas por su superior, por otra rama ni por quien dejó su red, ni las cuentas `VENDEDOR`; quien no tiene cuentas originadas recibe la página vacía con el resumen en ceros |
| `CA-SP-1005` | Los filtros `status`, `brokerId`, `sellerId` —uno de fuera de la red da la página vacía—, `hasHolder`, `search` por número de cuenta y por **nombre de usuario en el broker**, y `from`/`to` acotan la página y el resumen; un `status` desconocido es `400` |
| `CA-SP-1006` | Sin `broker-accounts:read-own-referred`, `403`; `V100` lo siembra a los roles `VENDEDOR` y `FUNCIONARIO` y no a los `CONSUMIDOR` |

## 13. Casos límite

| Caso | Decisión |
|---|---|
| El vendedor de origen fue eliminado | La cuenta sigue apareciendo a quien tenía a ese vendedor en su red mientras la relación siga vigente |
| La cuenta tiene titular de otra red | Aparece: lo que se mira es el origen |

## 14. Preguntas abiertas resueltas

| # | Pregunta | Resolución |
|---|---|---|
| 1 | ¿Qué es la línea comercial? | **Toda la red hacia abajo**, en todos los niveles (10-10-2026) |

## 15. Control de cambios

| Versión | Fecha | Cambio | Responsable |
|---|---|---|---|
| 0.1.0 | 10-10-2026 | Redacción inicial, a petición del responsable del proyecto (`RN-SP-075`). Criterios `CA-SP-1003` a `CA-SP-1006`. | Responsable del proyecto |
