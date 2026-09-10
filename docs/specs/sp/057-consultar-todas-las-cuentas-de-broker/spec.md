# SPEC — `RF-SP-057` Consultar y filtrar todas las cuentas de broker

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-057` |
| Módulo | `SP` — Sistema Principal |
| Estado | **Aprobada** |
| Autor | Responsable técnico |
| Aprobada por | Responsable del proyecto |
| Fecha de aprobación | 10-09-2026 |

---

## 1. Objetivo

Ver **todas** las cuentas de broker del sistema y acotarlas — sobre todo **por la red de un vendedor**.

## 2. Contexto

**Nace de un hueco que `RF-SP-056` cerró a propósito y duró un día.** Aquel resolvió «¿cómo va **mi** equipo?» y su §14 dejó escrito: «¿puede un administrador pedir el equipo de otro? **No por aquí** — un `?supervisorId=` es otro requerimiento y **nadie lo ha pedido**». Se pidió al día siguiente.

**Se registra así, con la frase entera, porque prueba que aquella decisión estaba bien tomada.** El parámetro se dejó fuera **por no adivinar**, no por descuido: si se hubiera puesto entonces, hoy tendría la forma que alguien imaginó en lugar de la que el responsable del proyecto pidió — y la que pidió no es la que se habría imaginado, porque **es en profundidad**.

**Lo que había hasta hoy y por qué no bastaba.** `broker-accounts:read` abría las cuentas de cualquiera, pero **persona a persona**: para ver la red de un vendedor había que pedir su equipo, y luego una llamada por cada miembro, y otra vez por cada subordinado. Es el mismo `N + 1` que `RF-SP-056` evita para el vendedor, sin resolver para quien administra.

## 3. Actores

| Actor | Papel |
|---|---|
| **Administrador** con `broker-accounts:read` | Consulta y filtra todas las cuentas |

## 4. Alcance

### 4.1 Incluye

- El listado **paginado de todas las cuentas** del sistema, cada fila con su titular.
- Filtro por **la red completa de un vendedor**, en profundidad (`RN-SP-047`).
- Filtros por **persona**, **estado**, **broker**, **texto** y **rango de fechas** de declaración.

### 4.2 No incluye

- **Escribir**, en cualquier forma. Sigue sin haber quién declare una cuenta fuera del registro por enlace, ni quién la desvincule.
- **Ordenar a voluntad.** El orden lo fija el servidor, como en el resto de listados del módulo.
- **La estructura como árbol.** Devuelve **cuentas planas**, no la jerarquía por la que se filtró: quién depende de quién lo responde `RF-SP-042`.
- **Las personas sin cuentas.** Es un listado de **cuentas**, con el mismo criterio que `RF-SP-056`.
- **Sustituir a `RF-SP-055`.** Aquella existe para el **superior sin permiso**, y su `404` uniforme sigue siendo su razón de ser.

## 5. Reglas de negocio aplicables

| ID | Regla | Origen |
|---|---|---|
| `RN-SP-040` | El nombre de usuario en el broker llega DESPUÉS | `requirements/sp.md` §5.1 |
| `RN-SP-045` | Toda cuenta de broker declara en qué punto está | `requirements/sp.md` §5.1 |
| `RN-SP-047` | La red de un vendedor es TODO lo que cuelga de él | `requirements/sp.md` §5.1 |

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción | Restricción |
|---|---|---|---|
| `supervisorId` | No | Solo las cuentas de **la red** de esa persona | `uuid`. **En profundidad**, y **sin incluirla a ella** |
| `userId` | No | Solo las cuentas de esa persona | `uuid` |
| `status` | No | Solo las cuentas en ese estado | `REGISTER` o `FIRST_DEPOSIT` |
| `brokerId` | No | Solo las cuentas de ese broker | `uuid` |
| `search` | No | Fragmento de **nombre de usuario, correo, nombre completo o identificador de cuenta** | Texto; sin acentos y sin distinguir mayúsculas |
| `from`, `to` | No | Rango de **cuándo se declaró la cuenta** | Instantes con zona. **Semiabierto**: incluye `from`, excluye `to` |
| `page`, `size` | No | Paginación | La del resto del sistema |

**Todos se combinan con Y**, y **`supervisorId` y `userId` no son excluyentes**: juntos responden «de la red de este vendedor, las cuentas de esta persona», que es la comprobación natural al revisar un caso concreto.

**El nombre de los parámetros es el del sistema y no uno nuevo**: `search` es como se llama en `GET /api/v1/users`, y `from`/`to` como en los cuatro listados de auditoría — con su misma semántica semiabierta. Llamarlos `q`, `declaredFrom` o `declaredTo` habría dado dos vocabularios para una sola idea.

**Qué se valida y qué no**, con el criterio ya fijado por `RF-SP-025` y `RF-SP-056`:

- **`status` fuera de los dos valores es `400`.** Una pregunta mal escrita, no una respuesta vacía.
- **`from` posterior a `to` es `400`.** Un rango imposible no es un rango vacío, y devolverlo como vacío haría creer que no hubo altas.
- **`supervisorId`, `userId` y `brokerId` inexistentes devuelven la página vacía, sin error.** Un identificador que no designa nada es una pregunta legítima; validarlos costaría una consulta por petición para producir un fallo que nadie quiere.

### 6.2 Salida

| Dato | Descripción |
|---|---|
| Página de cuentas | **La misma fila que `RF-SP-056`**: titular, broker, identificador de cuenta, nombre de usuario en el broker —puede ser nulo— y estado |
| Total | Las cuentas que cumplen el filtro |

**La fila es idéntica a la del listado del equipo**, campo por campo y a propósito: las dos pantallas pintan lo mismo con **un solo componente**.

**El orden lo fija el servidor**: por nombre de usuario del titular, luego por nombre de broker y luego por identificador de cuenta. Determinista hasta el último desempate, que es lo que impide que dos páginas seguidas repitan u omitan una fila.

## 7. Precondiciones y postcondiciones

**Precondiciones:** actor autenticado con `broker-accounts:read`.

**Postcondiciones:** ninguna. Es una lectura y **no audita**.

## 8. Flujo principal

1. El actor pide el listado, con los filtros que quiera.
2. Si viene `supervisorId`, el sistema resuelve **la red vigente** de esa persona, en profundidad.
3. El sistema devuelve las cuentas que cumplen todos los filtros, ordenadas y paginadas.

## 9. Flujos alternativos

| ID | Caso | Comportamiento |
|---|---|---|
| `FA-001` | Sin ningún filtro | Devuelve **el sistema entero**, paginado. Es lo que el permiso significa |
| `FA-002` | El vendedor no tiene red | `200` con la página vacía |
| `FA-003` | Alguien de la red fue **eliminado** | Sus cuentas **no salen**, y **la red no se corta por él**: sus subordinados siguen colgando y sí salen. Son dos cosas distintas y se prueban por separado |
| `FA-004` | Alguien **dejó** la red ayer | Ni él ni los suyos salen: solo cuenta la estructura **vigente** |
| `FA-005` | `status` inválido, o `from` posterior a `to` | `400`, `VAL-001` |
| `FA-006` | `supervisorId`, `userId` o `brokerId` inexistente | `200` con la página vacía, sin error |
| `FA-007` | `supervisorId` es la propia persona consultada por `userId` | Página vacía: **la raíz no se incluye en su red** |

## 10. Seguridad

**Lo gobierna `broker-accounts:read`**, con un `@PreAuthorize` corriente. Es la diferencia entera con `RF-SP-055`, y de ella salen las tres decisiones de esta especificación:

- **No hay `404` uniforme.** No hay recurso ajeno que pedir: el actor ya puede verlo todo, de modo que no hay existencia que ocultar.
- **No hay cota de un nivel.** La cota que se imponen `RF-SP-042`, `RF-SP-055` y `RF-SP-056` existe porque a aquellas **las autoriza la estructura**, y devolver la rama entera publicaría la empresa a quien solo lleva un equipo. Aquí la autoriza el permiso: **la profundidad no concede nada que el actor no tuviera**, le ahorra recorrer el árbol.
- **No amplía la excepción a D-22.** Este listado **filtra** por estructura; no se **autoriza** por ella. `security.md` §5 lo deja escrito para que nadie lea esta ruta como un precedente.

## 11. Validaciones

| Campo | Regla | Código |
|---|---|---|
| `status` | Uno de `REGISTER` o `FIRST_DEPOSIT` | `VAL-001` |
| `from`, `to` | Instantes con zona, y `from` no posterior a `to` | `VAL-001` |
| `supervisorId`, `userId`, `brokerId` | `uuid` bien formado | `VAL-001` |
| `page`, `size` | Los del resto del sistema | `VAL-003` |

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-SP-646` | Sin filtros, el actor con `broker-accounts:read` obtiene **todas** las cuentas del sistema, paginadas |
| `CA-SP-647` | Sin el permiso, la respuesta es **`403`** — y no el `404` de `RF-SP-055`: aquí no hay recurso ajeno que ocultar |
| `CA-SP-648` | `supervisorId` devuelve **toda la red en profundidad**: la cuenta del **nieto** SÍ aparece |
| `CA-SP-649` | `supervisorId` **no incluye las cuentas de la propia raíz** |
| `CA-SP-650` | Quien **dejó** la estructura no aparece, **ni los que colgaban de él por esa vía** |
| `CA-SP-651` | Una persona **eliminada** no aparece, y **su rama sigue colgando**: los suyos sí aparecen |
| `CA-SP-652` | `userId` acota a esa persona, y **se combina con `supervisorId`** |
| `CA-SP-653` | `search` encuentra por **identificador de cuenta** y por **nombre de usuario**, sin acentos y sin distinguir mayúsculas |
| `CA-SP-654` | `from`/`to` acotan por fecha de declaración, con el rango **semiabierto** |
| `CA-SP-655` | `status` inválido y `from` posterior a `to` devuelven **`400`**; un identificador inexistente devuelve la **página vacía** |
| `CA-SP-656` | El total **cuenta lo filtrado**, y dos páginas seguidas no repiten ni pierden filas |
| `CA-SP-657` | La fila es **idéntica** a la de `RF-SP-056`, campo por campo |

## 13. Casos límite

| Caso | Decisión |
|---|---|
| Una red con un ciclo en los datos | **No cuelga la consulta**: la recursión acumula con `UNION`, de modo que quien ya se vio no se reexpande. No debería poder ocurrir —`RN-SP-020` ata esta cadena a la de roles, que es acíclica— y la consulta no depende de ello |
| Una red de miles de personas | Se pagina igual. El recorrido entra por el índice parcial de `V28` |
| `supervisorId` de alguien que no es vendedor | Página vacía si no tiene a nadie a cargo. No se comprueba su rol: la estructura ya lo dice |
| Dos filtros que se contradicen | Página vacía, sin error. Es la consecuencia de combinarlos con Y |

## 14. Preguntas abiertas resueltas

| # | Pregunta | Resolución |
|---|---|---|
| 1 | ¿Un recurso propio o un parámetro en `/users/me/team/broker-accounts`? | **Propio** (10-09-2026). Colgarlo de `me` haría que la ruta dejara de significar «lo mío» en cuanto llegara un `supervisorId`, y serviría dos preguntas con dos autorizaciones por el mismo camino |
| 2 | ¿La red en profundidad o un nivel? | **En profundidad** (10-09-2026, responsable del proyecto). Es lo que se quiere ver de un vendedor, y el permiso ya alcanza a todos: la profundidad ahorra el recorrido, no concede |
| 3 | ¿Se incluye la raíz? | **No.** «Su red» son los suyos. Sus propias cuentas se piden con `userId`, y los dos filtros se combinan |
| 4 | ¿`q` o `search`? ¿`declaredFrom` o `from`? | **`search`, `from` y `to`**: los nombres que el sistema ya usa en `GET /api/v1/users` y en los listados de auditoría, con su misma semántica |
| 5 | ¿Filtro por rol? | **No** (10-09-2026). Se ofreció y no se pidió: `supervisorId` ya responde la pregunta que motivaba el filtro, y un `roles` aquí duplicaría el de `RF-SP-042` sobre otro conjunto |

## 15. Control de cambios

| Versión | Fecha | Cambio | Responsable |
|---|---|---|---|
| 0.1.0 | 10-09-2026 | Redacción inicial. Nace de una pregunta del responsable del proyecto cuya respuesta era **no**, y cierra el hueco que `RF-SP-056` §14 había declarado cerrado **el día anterior** — se cita entero porque prueba que aquella decisión estaba bien tomada: se dejó fuera por no adivinar, y la forma que se pidió no es la que se habría imaginado. **Lo que carga la especificación es `RN-SP-047`**: la red es **todo lo que cuelga**, lo que rompe a propósito la cota de un solo nivel de los tres requerimientos hermanos. La asimetría se razona una vez y para siempre en §10 — **aquellos los autoriza la estructura y este el permiso**—, y `security.md` §5 la registra para que esta ruta no se lea como un precedente que amplía D-22. | Responsable del proyecto |
