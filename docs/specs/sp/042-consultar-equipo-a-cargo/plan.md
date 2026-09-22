# PLAN — `RF-SP-042` Consultar el equipo a cargo de un usuario

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-042` |
| Especificación | [`spec.md`](spec.md) |
| `spec.md` aprobada el | 22-08-2026 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 24-08-2026 |
| Enmendado | 10-09-2026 — el equipo se filtra por **códigos de rol** y cada persona lleva **la lista completa de sus roles** en vez de un `roleCode` único. §3, §4, §8.bis, §9, §10 y §11 quedan afectados |

---

!!! note "Enmienda de Art. I.7 — 19-09-2026, `RF-SP-060`"

    Esta operación exige **`users:read-team`** y no `users:read` desde el 19-09-2026, por `RF-SP-060` —**un permiso por operación**, `RN-SEG-014` ([`security.md` §4.4](../../../security.md#44-catalogo-de-permisos))—: `users:read` gobernaba varias operaciones y se queda con una; esta recibe código propio, sembrado por `V28` y dado a todo rol que portara `users:read`. Las menciones de `users:read` que siguen abajo hablan de su siembra original y se conservan como historia.

## 1. Enfoque

Es la lectura de lo que `RF-SP-041` escribe, y **la consulta que hace ejecutable** el rechazo de `RN-SP-022`. Cuando `RF-SP-028`, `RF-SP-029` o `RF-SP-031` responden «esta persona tiene tres personas a cargo» y deliberadamente no dicen quiénes son, es porque esa respuesta pertenece aquí, con su propio permiso.

Todo su diseño consiste en **mantenerse pequeña**. Las cuatro preguntas abiertas de la especificación se resolvieron en sentido restrictivo —sin historial, sin conteo indirecto, sin filtros, sin variante «mi equipo»— y no por separado: juntas evitan que esta consulta se convierta en el sustituto informal del modelo de alcance que falta. Un plan que las relajara «porque los datos ya están ahí» adelantaría **D-22** sin que nadie lo hubiera decidido.

**Una de las cuatro se rehízo el 10-09-2026: el equipo se filtra por rol** (§4, §8.bis). No se relajó por tener los datos a mano —que es justo lo que el párrafo anterior teme—, sino porque **cambió lo que la estructura contiene**: cuando se decidió que no habría filtros, `user_supervisors` relacionaba vendedores entre sí; desde `RF-SP-045` contiene también la cartera de clientes de cada agente. La pregunta «de la gente que cuelga de este, enséñame solo los clientes» no existía entonces y `RF-SP-025` **no sabe responderla**. Las otras tres siguen intactas, y el filtro se acota al rol precisamente para que la relajación no se extienda sola.

!!! warning "Enmendado el 18-09-2026: la cartera sale del equipo, sin tocar este código"

    Por decisión del responsable del proyecto (`RN-SP-028` revertida; `RF-SP-059`). El cliente deja de colgar de `user_supervisors`, y esta lectura —que lee la tabla sin preguntar por el tipo de rol— **deja de devolverlo sin cambiar una línea**: la migración `V20` mueve las filas y el equipo vuelve a ser fuerza comercial. El filtro por `roles` y la lista de roles **se quedan**: perdieron el motivo que los trajo, no su utilidad. `CA-SP-625` se invierte en `CA-SP-710`, y la prueba la rehace `RF-SP-059 · T-15`.

Lo único que exige diseño real es la paginación del equipo directo junto a un **total que no depende de la página**, porque ese total es el que tiene que coincidir con el que informan los tres rechazos de `RN-SP-022`. Si divergen, quien intenta dar de baja a alguien lee un número aquí y otro en el error, y deja de fiarse de los dos.

## 2. Cambios de esquema

**Ninguno.**

Los dos accesos que la consulta necesita ya existen:

- **De quién depende alguien**: `uq_user_supervisors_vigente`, el único parcial sobre `user_id WHERE ended_at IS NULL` que crea `V21` (`RF-SP-024`). Sirve como índice además de como restricción.
- **Quiénes dependen de alguien**: `ix_user_supervisors_supervisor_vigente`, parcial sobre `supervisor_id`, que crea `V24` (`RF-SP-028`) para su propia comprobación de `RN-SP-022`. Es exactamente la consulta de esta pantalla.

Que la lectura más pesada del requerimiento se resuelva con un índice que otro requerimiento ya necesitaba no es casualidad: **es la misma pregunta** —«¿quién depende de esta persona?»— hecha por dos motivos distintos, y por eso §3 comparte también el componente que la responde.

## 3. Componentes afectados

| Capa | Componente | Nuevo / Modificado | Responsabilidad |
|---|---|---|---|
| `application` | `GetCommercialTeamQuery` | Nuevo | Caso de uso de lectura: superior vigente, equipo directo paginado y total |
| `application` | `SupervisedTeamCounter` | **Modificado** | Puerto de `RF-SP-028`, que hoy solo **cuenta**. Gana la lectura paginada del equipo. **El conteo sigue siendo el mismo método**, y es lo que garantiza que el total coincida con el de los rechazos |
| `application` | `SupervisorAssignmentRepository` | Sin cambios | Puerto de `RF-SP-041`. Aporta la asignación vigente del consultado |
| `domain` | `CommercialStructure` | Sin cambios | Componente de `RF-SP-024`. Aquí solo se usa para saber si la persona porta rol comercial y si es la cúspide |
| `api` | `UserController` | Modificado | Añade `GET /api/v1/users/{id}/team` |
| `api` | `CommercialStructureResponse` | **Modificado** | DTO compartido con `RF-SP-041`. Gana el equipo directo y su paginación |
| `application` | `UserQueryRepository.rolesOf` | **Reutilizado** | Puerto de `RF-SP-025`. Resuelve **por lote** los roles de la página entera, y por eso los roles de esta respuesta no cuestan una consulta por persona. **No se escribe otro**: dos consultas para la misma pregunta divergirían |
| `infrastructure` | `JpaUserRepository.findTeam` | **Modificado** (10-09-2026) | Gana el predicado del filtro —`EXISTS` sobre `user_roles`, nunca `JOIN`— y **pierde la subconsulta del rol único**, que solo miraba a los `VENDEDOR` y devolvía nulo para todo lo demás |

**Ningún componente de dominio nuevo, y ningún puerto nuevo.** Es una consulta sobre una relación que ya está modelada.

**`SupervisedTeamCounter` se amplía en lugar de crear un repositorio de lectura propio**, y esa es la decisión que sostiene `CA-SP-447`. El total que esta pantalla muestra y el que aparece en el mensaje de rechazo de `RF-SP-028`, `RF-SP-029` y `RF-SP-031` **salen del mismo método**. Escritos por separado, un día uno contará las asignaciones vigentes y el otro las personas activas, y nadie se enterará hasta que alguien compare los dos números.

## 4. Contrato de API

| Método | Ruta | Descripción |
|---|---|---|
| `GET` | `/api/v1/users/{id}/team` | Superior inmediato y equipo directo de la persona |

**Parámetros**

| Parámetro | Obligatorio | Descripción |
|---|---|---|
| `page` | No | Página del equipo directo. Por defecto la primera |
| `size` | No | Elementos por página. Por defecto 20, máximo 100 (`architecture.md` §7.4) |
| `roles` | No | **Códigos** de rol por los que se acota el equipo directo. Varios admitidos —`?roles=AGENTE,CLIENTE` o repitiendo el parámetro—, con semántica **O** |

**Un solo filtro, y es el rol** (10-09-2026, `CA-SP-455` invertido). Entra quien porte **alguno** de los códigos pedidos, `totalElements` **cuenta lo filtrado** y un código inexistente devuelve el equipo vacío con `200` —mismo criterio que `RF-SP-025` con su filtro por rol, y por el mismo motivo: validarlo contra el catálogo añade una consulta por petición para producir un fallo que nadie pidió—.

**El filtro no toca al superior ni a la persona consultada**, y esto es contrato, no detalle de implementación: `supervisor` sigue estando **ausente solo cuando la persona es la cúspide**. Si el filtro pudiera quitarlo, la interfaz no podría distinguir las dos cosas.

**Ningún otro filtro.** Ni `search`, ni `status`, ni país: `RF-SP-025` ya los tiene sobre el listado general y duplicarlos aquí obligaría a mantener dos semánticas sincronizadas. El rol es la excepción porque el listado general **no sabe responder** «de la gente que cuelga de este agente, enséñame solo los clientes».

**Respuesta `200`** — `CommercialStructureResponse`, compartido con `RF-SP-041`:

```json
{
  "user": {
    "username": "amartinez", "firstName": "Ana", "lastName": "Martínez",
    "roles": [{ "id": "01a02a33-…-7006", "code": "DIRECTOR", "name": "Director" }]
  },
  "supervisor": {
    "username": "rlopez", "firstName": "Raúl", "lastName": "López",
    "roles": [{ "id": "01a02a33-…-7005", "code": "MANAGER", "name": "Manager" }],
    "since": "2026-03-01T00:00:00Z"
  },
  "team": {
    "content": [{
      "username": "lgarcia", "firstName": "Luis", "lastName": "García",
      "roles": [{ "id": "01a02a33-…-7007", "code": "AGENTE", "name": "Agente o vendedor" }],
      "status": "ACTIVO"
    }],
    "totalElements": 12,
    "totalPages": 1,
    "page": 0,
    "size": 20
  }
}
```

`supervisor` va **ausente**, no en nulo, cuando la persona es la cúspide (`FA-002`). Es lo que permite a la interfaz distinguir «no depende de nadie» de «no se pudo resolver» — la distinción que `CA-SP-445` exige.

**`totalElements` no depende de la página**, y es el número que debe coincidir con el de los rechazos de `RN-SP-022` (`CA-SP-447`). Va dentro de `team` y no fuera porque cuenta el equipo, no la estructura.

**`roles` sustituye a `roleCode`, y es una ruptura declarada.** El campo viejo devolvía **un solo** rol y solo si era de clasificación `VENDEDOR`, de modo que desde `RF-SP-045` —cuando la cartera de clientes pasó a colgar de esta misma estructura— **un cliente llegaba con el rol en nulo** y era indistinguible de un vendedor sin rol. El campo nuevo lleva **todos** los roles, con identificador, código y nombre, ordenados por código, y **va siempre presente aunque vaya vacío**: una persona sin roles es un estado válido, y distinguirlo con la ausencia del campo obligaría al cliente a tratar dos formas del mismo recurso. Es el mismo criterio, y el mismo `RoleRef`, que `RF-SP-025` publica en su listado.

**Errores**

| Código | Cuándo | `error_code` |
|---|---|---|
| `400` | Identificador malformado, o paginación fuera de límites | `VAL-001`, `VAL-003` |
| `401` | Token ausente o inválido | `AUTH-001` |
| `403` | El actor no posee `users:read-team` | `AUTH-002` |
| `404` | La persona no existe o está eliminada (`EX-001`) | `VAL-002` |
| `500` | Fallo no controlado | `ERR-500` |

**El identificador malformado es `400` y la persona inexistente es `404`**, sin confundirlos. Mismo criterio que `RF-SP-026` y `RF-SP-003`, y `spec.md` §13 lo declara explícitamente.

**Quien no pertenece a la fuerza comercial recibe `200`, no `404` ni `409`.** `FA-001` es una respuesta legítima —«esta persona no tiene estructura comercial»— y distinta de «esta persona no existe». Devolver un error obligaría a la interfaz a distinguir dos fallos para pintar lo mismo.

## 5. Autorización

| Endpoint | Permiso requerido |
|---|---|
| `GET /api/v1/users/{id}/team` | `users:read-team` |

**No se crea un permiso propio**, y `spec.md` §7 lo razona: quien puede ver la ficha de una persona puede ver de quién depende. Es el mismo permiso que `RF-SP-025` y `RF-SP-026`.

!!! warning "El alcance de datos es global, y hay que dejarlo escrito"

    Cualquier actor con `users:read` ve la estructura de **cualquiera**. Que un director pueda consultar hoy el equipo de otro director es una consecuencia consciente de que **D-22 no está resuelta**, no un descuido — y `spec.md` §5 pide que quede escrito por el mismo motivo que lo hace `RF-SP-025` §5.

    **El día que exista alcance comercial, esta consulta será de las primeras afectadas**, y este párrafo es el que habrá que venir a buscar.

    Por la misma razón **no existe una variante «mi equipo»** resuelta contra el actor: eso es alcance por persona, que `security.md` §6 reserva. `CA-SP-450` verifica la ausencia.

## 6. Auditoría

**Ninguna.**

Es una consulta de lectura y no aparece en el catálogo cerrado de `security.md` §8.1. Mismo criterio que `RF-SP-039` §6 y que el resto de consultas del módulo.

La única lectura que **sí** se audita es la de la auditoría de seguridad (`SECURITY_AUDIT_READ`, `RF-SP-014`), y la asimetría es deliberada: leer lo que hicieron otros no es lo mismo que leer una estructura organizativa que cualquiera con `users:read-team` puede ver.

## 7. Transaccionalidad

Solo lectura, en transacción de **solo lectura**.

**El superior y el equipo se leen en la misma transacción**, y eso es lo que sostiene la garantía del caso concurrente de `spec.md` §13: como `RF-SP-041` cierra y abre dentro de una sola transacción y bajo bloqueo (`RF-SP-041` §7), esta consulta ve el estado anterior o el posterior, **nunca a la persona sin superior ni con dos**. Leerlos en dos transacciones separadas abriría exactamente esa ventana.

## 8. Impacto sobre otros módulos

- **`RF-SP-041`** comparte `CommercialStructureResponse` y `SupervisorAssignmentRepository`.
- **`RF-SP-028`, `RF-SP-029` y `RF-SP-031`** comparten `SupervisedTeamCounter`. Su rechazo por `RN-SP-022` informa **cuántas** personas sin listarlas, y remite aquí para saber quiénes (`RF-SP-031` §4).
- **`RF-SP-039`** devuelve el superior propio y **nunca** el equipo. Es la frontera que sostiene la reserva de D-22.
- **`RF-SP-025`** conserva el filtrado del listado general. Esta consulta **no lo replica**: se quedó con el rol, que es lo único que aquel no sabe responder acotado a un equipo (§8.bis).
- **Ninguna enmienda a documento transversal.** `requirements/sp.md` §9 ya declara la ruta y el permiso.


### 8.bis Enmiendas del 10-09-2026

El filtro por rol y la lista de roles **no dejan ningún documento como estaba**, y estas son las enmiendas que este plan declara y que se aplican en el mismo pase (Art. I.7):

| # | Documento | Enmienda |
|---|---|---|
| 1 | `docs/requirements/sp.md` | La ficha de `RF-SP-042` gana la enmienda con sus dos mitades, y **§10.7 queda corregida**: afirmaba desde el 01-09-2026 que «cada fila lleva ya los roles de la persona, que es lo que permite distinguirlos», y **no era cierto** — la respuesta llevaba un rol único y solo de clasificación `VENDEDOR`, de modo que la cartera de clientes llegaba con el rol en nulo. v1.48.0 |
| 2 | `docs/specs/sp/041-asignar-superior-comercial/` | Comparte `CommercialStructureResponse`: **su respuesta también pierde `roleCode` y gana `roles`**, sin que el requerimiento cambie de comportamiento. Queda enmendada su `spec.md` y el ejemplo de su `plan.md` |
| 3 | `docs/requirements.md` | Fila de la matriz y control de cambios. v0.122.0 |
| 4 | `docs/api/index.md` | **Es un cambio ROMPEDOR para el frontend** —un campo desaparece del cuerpo— y se anuncia como tal, no se deja descubrir al regenerar el cliente |

**Lo que NO se enmienda, y conviene decir por qué:**

- **`docs/modelo-datos.md`**: no hay cambio de esquema. Los roles se leen de `user_roles`, que existe desde `V6`, y el filtro es un predicado, no una columna.
- **`docs/security.md`**: el permiso sigue siendo `users:read-team` y el alcance sigue siendo global. Filtrar por rol **no enseña a nadie nada que no viera ya** — es un subconjunto de lo que la misma consulta devolvía entero.
- **`RF-SP-039`** (perfil propio) **conserva su `roleCode`**: publica el superior del propio actor por `OwnProfileResponse.SupervisorRef`, que es otro DTO y otro contrato. Cambiarlo de paso sería tocar un requerimiento que nadie ha pedido tocar; queda anotado como asimetría consciente.
## 9. Alternativas consideradas

| Alternativa | Por qué se descartó |
|---|---|
| Devolver el árbol descendente completo | Exige recorrer la estructura, que es justo lo que **D-22** debe gobernar. Reservado hasta que se cierre (`spec.md` §4.2) |
| Devolver el conteo de la rama indirecta, aunque sea solo un número | Obliga al mismo recorrido, y **un total tampoco es inocuo**: revela el tamaño de la red de cada mando (`spec.md` §14, pregunta 2) |
| Una variante «mi equipo» resuelta contra el actor | Es alcance por persona, reservado por `security.md` §6 |
| Devolver el historial de superiores anteriores | No ayuda a ninguno de los dos usos de esta consulta. Quien lo necesitará es una auditoría de comisiones, con permiso propio y filtros por fecha (`spec.md` §14, pregunta 1) |
| Devolver la cadena ascendente completa | Se obtiene encadenando consultas, y devolverla entera invitaría a usarla como sustituto del modelo de alcance que falta |
| Un permiso propio para la estructura | Quien puede ver la ficha puede ver de quién depende. Un permiso más sin una decisión que lo justifique |
| Un repositorio de lectura propio en lugar de ampliar `SupervisedTeamCounter` | El total de esta pantalla y el de los rechazos de `RN-SP-022` divergirían, y nadie se enteraría hasta comparar los dos números (§3) |
| `404` para quien no pertenece a la fuerza comercial | «No tiene estructura» es una respuesta legítima y distinta de «no existe» (`FA-001`) |
| Ocultar del equipo a los subordinados inactivos o bloqueados | El total dejaría de cuadrar con el que impide dar de baja a su superior. Aparecen con su estado a la vista (`spec.md` §13) |
| Leer superior y equipo en transacciones separadas | Abre la ventana en que una reasignación concurrente muestra a la persona sin superior o con dos (§7) |
| Mantener el filtro por `status`, `search` o país sobre el equipo | Es el argumento original y **sigue en pie**: `RF-SP-025` ya los tiene sobre el listado general, y duplicarlos aquí obliga a mantener dos semánticas sincronizadas. Solo entró el rol, y entró porque responde algo que el listado general **no** sabe responder |
| Filtrar por **identificador** de rol, como `RF-SP-025` | Obligaría al cliente a leer el catálogo de roles antes de poder filtrar, cuando **la respuesta ya le entrega los códigos** de cada persona. El código es además estable y legible en la URL |
| Filtrar por **clasificación** de rol (`VENDEDOR`, `CONSUMIDOR`) | Separa la cartera del equipo comercial con un solo valor, pero **no distingue agentes de directores**, que es la otra mitad de la pregunta. El código sirve para las dos y la clasificación solo para una |
| **Un solo** código por consulta | «Enséñame agentes y directores» exigiría dos llamadas y una unión en el cliente, con la paginación rota por el camino |
| Conservar `roleCode` junto a `roles` | Dos formas de decir lo mismo, y la vieja **mentía**: era nula en cuanto la persona no fuera vendedora. Se sustituye, y el cambio se declara como ruptura en `docs/api/index.md` en lugar de esconderse detrás de un campo que sobrevive |
| Resolver los roles **fila a fila** al construir la respuesta | Un `N+1` que no rompe ninguna prueba —la respuesta es correcta, solo lenta— y que crece con el tamaño de página. Se resuelven por lote con `rolesOf`, el puerto que `RF-SP-025` ya usa para lo mismo |
| Filtrar con `JOIN` a `user_roles` en lugar de `EXISTS` | Con dos códigos, quien porta los dos sale **dos veces** y el total cuenta asignaciones en lugar de personas. Es el mismo error que `JpaUserQueryRepository` ya documenta en su predicado |
| Aplicar el filtro también al superior | Haría indistinguible «es la cúspide» de «lo tiene y no casa con el filtro» (`CA-SP-445`) |
| Rechazar con `400` un código de rol inexistente | Añade una consulta al catálogo por petición para producir un fallo que nadie pidió. `RF-SP-025` ya decidió lo contrario para su filtro por rol, y dos criterios distintos para la misma pregunta son peores que uno imperfecto |

## 10. Riesgos

| Riesgo | Impacto | Mitigación |
|---|---|---|
| El total diverge del que informan los rechazos de `RN-SP-022` | **Alto** | Mismo método de `SupervisedTeamCounter`; `CA-SP-447` compara ambos números en una sola prueba |
| Se añade el árbol completo o el conteo indirecto «porque los datos están ahí» | **Alto** | Adelantaría D-22 sin decisión. `CA-SP-449` y `CA-SP-454` lo prohíben explícitamente |
| Aparece una variante «mi equipo» | **Alto** | `CA-SP-450`; es alcance por persona |
| Se ocultan los subordinados inactivos y el total deja de cuadrar | Medio | `spec.md` §13; aparecen con su estado |
| Se devuelven tramos cerrados del historial | Medio | `CA-SP-453`; solo lo vigente |
| Superior y equipo se leen en transacciones distintas | Medio | Transacción de solo lectura única (§7) |
| **El alcance global se toma por definitivo** | Medio | §5 lo declara con su condición de disparo: al cerrarse D-22, esta consulta es de las primeras afectadas |
| **El filtro por rol multiplica filas y el total cuenta asignaciones** | **Alto** | `EXISTS` y no `JOIN`, que es lo que `RF-SP-025` ya aprendió (`JpaUserQueryRepository` §predicado): con un `JOIN`, quien porta dos de los roles pedidos sale dos veces y el conteo cuenta asignaciones en lugar de personas. `CA-SP-627` es la prueba que lo detecta |
| **Los roles se resuelven fila a fila** | Medio | Una consulta por miembro de la página es un `N+1` que no falla ninguna prueba: la respuesta es correcta y solo es lenta. Se resuelven **en una sola consulta por lote**, reutilizando `rolesOf` de `RF-SP-025` en lugar de escribir otra |
| **Se filtra también al superior** | Medio | Haría indistinguible la cúspide de un superior descartado por el filtro. `CA-SP-445` y `CA-SP-628` lo cubren juntos |

## 11. Estrategia de prueba

| Criterio | Nivel | Qué verifica |
|---|---|---|
| `CA-SP-442` | Integración | Superior inmediato y equipo directo de alguien de la fuerza comercial |
| `CA-SP-443` | Integración | **Solo** asignaciones vigentes: quien dejó de estar a su cargo no aparece |
| `CA-SP-444` | API | Sin rol comercial: estructura vacía y `200`, **no** un error |
| `CA-SP-445` | API | Cúspide: `supervisor` **ausente**, distinguible de no haberlo encontrado |
| `CA-SP-446` | API | Sin equipo: equipo vacío y paginación en cero |
| `CA-SP-447` | **Integración de dos requerimientos** | El total coincide con el que `RF-SP-028`, `RF-SP-029` y `RF-SP-031` informan al rechazar por `RN-SP-022` |
| `CA-SP-448` | API | Paginación por defecto y máximo respetados |
| `CA-SP-449` | API | **No** hay árbol descendente: solo un nivel |
| `CA-SP-450` | API | **No** admite resolverse contra el actor |
| `CA-SP-451` | API | Persona eliminada: `404`, sin distinguir de nunca haber existido |
| `CA-SP-452` | API | Sin `users:read-team`: `403` |
| `CA-SP-453` | API | **No** contiene superiores anteriores ni tramos cerrados |
| `CA-SP-454` | API | **No** contiene ningún conteo de la rama indirecta |
| `CA-SP-455` | API | **Invertido.** El filtro por rol **sí** se aplica, y `search` o `status` **no** cambian el resultado |
| `CA-SP-624` | API | Cada persona lleva **la lista completa de sus roles**, y la respuesta **ya no publica un rol único** |
| `CA-SP-710` | Integración | **Sustituye a `CA-SP-625` desde el 18-09-2026.** El equipo de un vendedor con clientes registrados **no los contiene**, y `roles=CLIENTE` devuelve vacío. Es la prueba inversa de la que habría fallado antes del 10-09-2026 |
| `CA-SP-626` | API | Un código: solo quienes lo portan, y **el total cuenta lo filtrado** |
| `CA-SP-627` | Integración | Dos códigos: semántica **O**, y **quien porta los dos aparece una sola vez** — la prueba que distingue `EXISTS` de un `JOIN` |
| `CA-SP-628` | API | Código inexistente: equipo vacío con `200`; **el superior y la persona consultada salen igual** |

Casos límite de `spec.md` §13 con prueba propia (Art. VII.3):

| Caso | Nivel | Qué verifica |
|---|---|---|
| Subordinado inactivo o bloqueado | Integración | **Sigue apareciendo**, con su estado a la vista, y cuenta para el total |
| Subordinado eliminado | Integración | **No** aparece: `RF-SP-029` cerró su asignación |
| Equipo grande | API | Se pagina, y el total va aparte para que la primera página baste cuando solo se necesita el número |
| Consulta durante una reasignación | **Integración concurrente** | Ve el estado anterior o el posterior, **nunca sin superior ni con dos** |
| Persona con rol comercial y otro de distinta clasificación | Integración | Estructura normal; los roles no comerciales no intervienen |
| Identificador con formato incorrecto | API | `400` por validación, **no** `404` |

**`CA-SP-447` es la única prueba del requerimiento que no se puede escribir desde dentro de él**, y es la más valiosa: crea un equipo, consulta el total por esta vía, intenta retirar el rol comercial a su responsable y comprueba que el número del rechazo es **el mismo**. Escritas por separado, las dos mitades pasarían aunque contaran cosas distintas.
