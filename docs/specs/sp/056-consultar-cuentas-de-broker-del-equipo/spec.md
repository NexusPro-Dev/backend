# SPEC — `RF-SP-056` Consultar las cuentas de broker del equipo

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-056` |
| Módulo | `SP` — Sistema Principal |
| Estado | **Aprobada** |
| Autor | Responsable técnico |
| Aprobada por | Responsable del proyecto |
| Fecha de aprobación | 10-09-2026 |
| Enmendada | 21-09-2026 — exige **`broker-accounts:read-own-team`** (`RF-SP-062`, `RN-SEG-015`: autenticarse no autoriza nada); lo siembra `V31` |

---

!!! note "Enmienda de Art. I.7 — 21-09-2026, `RF-SP-062`"

    Esta operación exige **`broker-accounts:read-own-team`** desde el 21-09-2026, por `RF-SP-062` —**autenticarse no autoriza nada**, `RN-SEG-015` ([`security.md` §4.3](../../../security.md#43-reglas-de-negocio))—, por decisión del responsable del proyecto: «cada endpoint debe tener su propio permiso, ya que uso esto para saber qué vista o consulta mostrar en el front; no basta con solo tener el token». Hasta entonces se atendía con solo el token, y las líneas que abajo dicen «sin permiso» o «autenticado a secas» hablan de esa decisión original y se conservan como historia: el alcance sobre uno mismo sigue siendo exactamente el mismo, lo que cambia es que ahora tiene nombre. `V31` siembra el permiso y lo da a todo rol por su tipo, a `CONSUMIDOR` no.



## 1. Objetivo

Ver de una sola vez **quiénes de mi equipo ya depositaron y quiénes siguen esperando**.

## 2. Contexto

**Es la misma pregunta que `RF-SP-055`, hecha del otro lado**, y por eso son dos requerimientos y no uno. Aquel responde «¿qué cuentas tiene esta persona?» y este «¿cómo va mi equipo?». Con solo el primero, pintar la pantalla del vendedor costaría **una llamada por cliente** —`N + 1` desde el navegador— y ordenar «los que faltan por depositar» sería imposible sin traérselos todos antes.

**Lo que este requerimiento añade a la forma no es el filtro sino el titular.** Cada fila es de una persona distinta, de modo que cada fila **lo lleva dentro**; en `RF-SP-055` el titular es la ruta.

**Y comparte con él la decisión que carga los dos**: la autorización sale de `user_supervisors` (`RN-SP-046`). Aquí es todavía más directa, porque **no hay identificador que pasar**: el equipo es el del actor, y nadie puede pedir el de otro.

## 3. Actores

| Actor | Papel |
|---|---|
| **Cualquier persona autenticada** con `broker-accounts:read-own-team` | Consulta las cuentas de su **propio** equipo directo |

## 4. Alcance

### 4.1 Incluye

- El listado **plano y paginado** de las cuentas de broker de todas las personas que **dependen directamente** del actor.
- **Cada fila con su titular**: identificador, nombre de usuario, nombre y apellidos.
- Filtro por **estado** (`RN-SP-045`) y por **broker**.

### 4.2 No incluye

- **El equipo de otro.** No hay identificador en la ruta y no lo habrá: quien deba ver el equipo de otro usa `RF-SP-055` persona a persona, con su permiso.
- **Más de un nivel.** Quienes reportan **directamente**, como `RF-SP-042`. El árbol descendente publicaría la estructura entera de la empresa por una lectura de cuentas de broker.
- **Las cuentas del propio actor.** El actor no está en su propio equipo, y `RF-SP-055` §4.2 ya decidió que el titular no se ve a sí mismo por esta vía.
- **A las personas del equipo que no tienen ninguna cuenta.** El listado es de **cuentas**, no de personas: quien no declaró ninguna no aparece. Quién hay en el equipo lo responde `RF-SP-042`.
- **Escribir**, en cualquier forma.

## 5. Reglas de negocio aplicables

| ID | Regla | Origen |
|---|---|---|
| `RN-SP-040` | El nombre de usuario en el broker llega DESPUÉS | `requirements/sp.md` §5.1 |
| `RN-SP-045` | Toda cuenta de broker declara en qué punto está | `requirements/sp.md` §5.1 |
| `RN-SP-046` | Las cuentas las ve el superior vigente, o quien traiga el permiso | `requirements/sp.md` §5.1 |

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción | Restricción |
|---|---|---|---|
| `status` | No | Solo las cuentas en ese estado | `REGISTER` o `FIRST_DEPOSIT` |
| `brokerId` | No | Solo las cuentas de ese broker | `uuid` |
| `page`, `size` | No | Paginación | La del resto del sistema |

**Los dos filtros son opcionales y se combinan con Y.** Un `status` que no sea uno de los dos valores es un error de forma (`VAL-001`) y no una página vacía: al revés que un `brokerId` inexistente, que **sí** devuelve la página vacía —criterio de `RF-SP-025`—, porque un identificador que no designa nada es una pregunta legítima con respuesta vacía, mientras que `?status=depositado` es una pregunta mal escrita.

### 6.2 Salida

| Dato | Descripción |
|---|---|
| Página de cuentas | Cada una con su **titular** —identificador, nombre de usuario, nombre y apellidos—, su **broker**, el **identificador de cuenta**, el **nombre de usuario en el broker** —puede ser nulo— y el **estado** |
| Total | Las cuentas que cumplen el filtro, **no** las personas del equipo |

**El total cuenta lo filtrado**, con el mismo criterio que `RF-SP-042` aplica a su filtro por roles.

**El orden lo fija el servidor**: por nombre de usuario del titular, luego por nombre de broker y luego por identificador de cuenta. Es determinista hasta el último desempate a propósito: sin eso, dos páginas consecutivas podrían repetir u omitir una fila.

## 7. Precondiciones y postcondiciones

**Precondiciones:** actor autenticado con `broker-accounts:read-own-team` — **hasta el 21-09-2026 sin permiso** (`RF-SP-062`). El alcance lo sigue poniendo la estructura.

**Postcondiciones:** ninguna. Es una lectura y **no audita**.

## 8. Flujo principal

1. El actor pide las cuentas de su equipo.
2. El sistema resuelve **quiénes dependen de él hoy** (`user_supervisors` con `ended_at` nulo).
3. El sistema devuelve las cuentas de esas personas, filtradas, ordenadas y paginadas.

## 9. Flujos alternativos

| ID | Caso | Comportamiento |
|---|---|---|
| `FA-001` | El actor no tiene equipo | `200` con la página vacía y `totalElements` en cero. **No es un error**: mismo criterio que `RF-SP-042` con quien no pertenece a la fuerza comercial |
| `FA-002` | El equipo existe y nadie declaró cuentas | `200` con la página vacía. Es lo normal fuera del enlace `BECA → BECA` |
| `FA-003` | Una persona del equipo fue **eliminada** | Sus cuentas **no salen**. La eliminación lógica la retira de toda lectura |
| `FA-004` | Un miembro **dejó** el equipo ayer | Sus cuentas **no salen**: la fila cerrada de `user_supervisors` conserva el historial y no concede lectura |
| `FA-005` | `brokerId` no designa ningún broker | `200` con la página vacía, sin error |
| `FA-006` | `status` no es uno de los dos valores | `400`, `VAL-001` |

## 10. Seguridad

**Autenticado y sin permiso**, y esa es la decisión: el alcance sale de `user_supervisors`, no del catálogo (`RN-SP-046`). Es la excepción a **D-22** que `RF-SP-055` §10 declara entera y que `security.md` §5 acota; aquí es **más estrecha todavía**, porque el actor no puede nombrar a nadie: **el conjunto de datos lo determina el sistema a partir de quién pregunta**, y no hay parámetro con el que equivocarse.

**Por eso esta ruta no necesita el `404` de `RF-SP-055`**: no hay recurso ajeno que pedir, y por tanto no hay existencia que ocultar.

**`broker-accounts:read` no la gobierna.** Quien lo tenga no ve por aquí el equipo de otro: para eso está `RF-SP-055`, persona a persona. Un `?supervisorId=` para administradores sería un requerimiento distinto, y hoy nadie lo ha pedido.

## 11. Validaciones

| Campo | Regla | Código |
|---|---|---|
| `status` | Uno de `REGISTER` o `FIRST_DEPOSIT` | `VAL-001` |
| `brokerId` | `uuid` bien formado | `VAL-001` |
| `page`, `size` | Los del resto del sistema | `VAL-001` |

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-SP-637` | El actor obtiene las cuentas de **todas** las personas que dependen directamente de él, **sin traer ningún permiso** |
| `CA-SP-638` | Cada fila lleva **su titular** —identificador, nombre de usuario, nombre y apellidos— además del broker, el identificador de cuenta y el estado |
| `CA-SP-639` | Las cuentas de quien **no** depende del actor **no aparecen**, aunque exista y tenga cuentas |
| `CA-SP-640` | Las cuentas de quien **dejó** el equipo **no aparecen** (`ended_at` no nulo) |
| `CA-SP-641` | El filtro por **estado** devuelve solo ese estado, y el total cuenta **lo filtrado** |
| `CA-SP-642` | El filtro por **broker** devuelve solo ese broker, y un `brokerId` inexistente devuelve la página vacía **sin error** |
| `CA-SP-643` | Un `status` fuera de los dos valores devuelve **`400`**, no una página vacía |
| `CA-SP-644` | Quien no tiene equipo recibe **`200`** con la página vacía, no `404` ni `403` |
| `CA-SP-645` | El **árbol descendente no se publica**: las cuentas de quien depende de un subordinado del actor **no aparecen** |

## 13. Casos límite

| Caso | Decisión |
|---|---|
| Una persona del equipo con **dos cuentas** | Salen **dos filas**, las dos con el mismo titular. El listado es de cuentas |
| El equipo tiene cien personas y ninguna declaró cuenta | `200` con la página vacía. El listado no dice quién hay en el equipo (`RF-SP-042` sí) |
| El actor es a la vez superior de unos y subordinado de otro | Ve **solo hacia abajo**, y un solo nivel |
| Dos miembros con el mismo nombre de usuario | Imposible: `RN-SP-016` lo prohíbe. El orden es determinista igualmente por los dos desempates siguientes |

## 14. Preguntas abiertas resueltas

| # | Pregunta | Resolución |
|---|---|---|
| 1 | ¿Un listado plano o el equipo con sus cuentas anidadas? | **Plano** (10-09-2026). La pregunta es «¿quién falta por depositar?», y anidar obligaría a recorrer dos niveles para contestarla; además, paginar un anidado pagina personas y no cuentas |
| 2 | ¿Puede un administrador pedir el equipo de otro? | **No por aquí** (10-09-2026). `broker-accounts:read` sirve a `RF-SP-055`, persona a persona. Un `?supervisorId=` es otro requerimiento y nadie lo ha pedido |
| 3 | ¿Aparecen las personas del equipo sin cuentas? | **No.** El listado es de cuentas. Quién hay en el equipo lo responde `RF-SP-042` |
| 4 | ¿Cuántos niveles? | **Uno**, el equipo directo, como `RF-SP-042` y por el mismo motivo |

## 15. Control de cambios

| Versión | Fecha | Cambio | Responsable |
|---|---|---|---|
| 0.1.0 | 10-09-2026 | Redacción inicial. Hermano de `RF-SP-055`, del que hereda la autorización por estructura (`RN-SP-046`) **sin repetir su argumento**. Lo propio de esta especificación son tres decisiones: **listado plano** y no anidado —porque la pregunta es «quién falta por depositar» y paginar un anidado pagina personas—; **sin identificador en la ruta**, de modo que el conjunto de datos lo determina el sistema y no el actor, lo que la hace más estrecha que su hermana y le ahorra el `404`; y **es un listado de cuentas, no de personas**, de donde sale que quien no declaró ninguna no aparezca. | Responsable del proyecto |
