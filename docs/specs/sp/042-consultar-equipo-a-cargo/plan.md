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

---

## 1. Enfoque

Es la lectura de lo que `RF-SP-041` escribe, y **la consulta que hace ejecutable** el rechazo de `RN-SP-022`. Cuando `RF-SP-028`, `RF-SP-029` o `RF-SP-031` responden «esta persona tiene tres personas a cargo» y deliberadamente no dicen quiénes son, es porque esa respuesta pertenece aquí, con su propio permiso.

Todo su diseño consiste en **mantenerse pequeña**. Las cuatro preguntas abiertas de la especificación se resolvieron en sentido restrictivo —sin historial, sin conteo indirecto, sin filtros, sin variante «mi equipo»— y no por separado: juntas evitan que esta consulta se convierta en el sustituto informal del modelo de alcance que falta. Un plan que las relajara «porque los datos ya están ahí» adelantaría **D-22** sin que nadie lo hubiera decidido.

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

**Ningún filtro.** `CA-SP-455` lo exige, y la razón está en la resolución 3 de `spec.md` §14: `RF-SP-025` ya filtra el listado general de usuarios, y replicar esa semántica sobre un subconjunto que cabe en una o dos páginas obligaría a mantener dos filtrados sincronizados sin responder ninguna pregunta nueva.

**Respuesta `200`** — `CommercialStructureResponse`, compartido con `RF-SP-041`:

```json
{
  "user": { "username": "amartinez", "firstName": "Ana", "lastName": "Martínez", "roleCode": "DIRECTOR" },
  "supervisor": { "username": "rlopez", "firstName": "Raúl", "lastName": "López", "roleCode": "MANAGER", "since": "2026-03-01T00:00:00Z" },
  "team": {
    "content": [{ "username": "lgarcia", "firstName": "Luis", "lastName": "García", "roleCode": "AGENTE", "status": "ACTIVO" }],
    "totalElements": 12,
    "totalPages": 1,
    "page": 0,
    "size": 20
  }
}
```

`supervisor` va **ausente**, no en nulo, cuando la persona es la cúspide (`FA-002`). Es lo que permite a la interfaz distinguir «no depende de nadie» de «no se pudo resolver» — la distinción que `CA-SP-445` exige.

**`totalElements` no depende de la página**, y es el número que debe coincidir con el de los rechazos de `RN-SP-022` (`CA-SP-447`). Va dentro de `team` y no fuera porque cuenta el equipo, no la estructura.

**Errores**

| Código | Cuándo | `error_code` |
|---|---|---|
| `400` | Identificador malformado, o paginación fuera de límites | `VAL-001`, `VAL-003` |
| `401` | Token ausente o inválido | `AUTH-001` |
| `403` | El actor no posee `users:read` | `AUTH-002` |
| `404` | La persona no existe o está eliminada (`EX-001`) | `VAL-002` |
| `500` | Fallo no controlado | `ERR-500` |

**El identificador malformado es `400` y la persona inexistente es `404`**, sin confundirlos. Mismo criterio que `RF-SP-026` y `RF-SP-003`, y `spec.md` §13 lo declara explícitamente.

**Quien no pertenece a la fuerza comercial recibe `200`, no `404` ni `409`.** `FA-001` es una respuesta legítima —«esta persona no tiene estructura comercial»— y distinta de «esta persona no existe». Devolver un error obligaría a la interfaz a distinguir dos fallos para pintar lo mismo.

## 5. Autorización

| Endpoint | Permiso requerido |
|---|---|
| `GET /api/v1/users/{id}/team` | `users:read` |

**No se crea un permiso propio**, y `spec.md` §7 lo razona: quien puede ver la ficha de una persona puede ver de quién depende. Es el mismo permiso que `RF-SP-025` y `RF-SP-026`.

!!! warning "El alcance de datos es global, y hay que dejarlo escrito"

    Cualquier actor con `users:read` ve la estructura de **cualquiera**. Que un director pueda consultar hoy el equipo de otro director es una consecuencia consciente de que **D-22 no está resuelta**, no un descuido — y `spec.md` §5 pide que quede escrito por el mismo motivo que lo hace `RF-SP-025` §5.

    **El día que exista alcance comercial, esta consulta será de las primeras afectadas**, y este párrafo es el que habrá que venir a buscar.

    Por la misma razón **no existe una variante «mi equipo»** resuelta contra el actor: eso es alcance por persona, que `security.md` §6 reserva. `CA-SP-450` verifica la ausencia.

## 6. Auditoría

**Ninguna.**

Es una consulta de lectura y no aparece en el catálogo cerrado de `security.md` §8.1. Mismo criterio que `RF-SP-039` §6 y que el resto de consultas del módulo.

La única lectura que **sí** se audita es la de la auditoría de seguridad (`SECURITY_AUDIT_READ`, `RF-SP-014`), y la asimetría es deliberada: leer lo que hicieron otros no es lo mismo que leer una estructura organizativa que cualquiera con `users:read` puede ver.

## 7. Transaccionalidad

Solo lectura, en transacción de **solo lectura**.

**El superior y el equipo se leen en la misma transacción**, y eso es lo que sostiene la garantía del caso concurrente de `spec.md` §13: como `RF-SP-041` cierra y abre dentro de una sola transacción y bajo bloqueo (`RF-SP-041` §7), esta consulta ve el estado anterior o el posterior, **nunca a la persona sin superior ni con dos**. Leerlos en dos transacciones separadas abriría exactamente esa ventana.

## 8. Impacto sobre otros módulos

- **`RF-SP-041`** comparte `CommercialStructureResponse` y `SupervisorAssignmentRepository`.
- **`RF-SP-028`, `RF-SP-029` y `RF-SP-031`** comparten `SupervisedTeamCounter`. Su rechazo por `RN-SP-022` informa **cuántas** personas sin listarlas, y remite aquí para saber quiénes (`RF-SP-031` §4).
- **`RF-SP-039`** devuelve el superior propio y **nunca** el equipo. Es la frontera que sostiene la reserva de D-22.
- **`RF-SP-025`** conserva el filtrado del listado general; esta consulta no lo replica.
- **Ninguna enmienda a documento transversal.** `requirements/sp.md` §9 ya declara la ruta y el permiso.

## 9. Alternativas consideradas

| Alternativa | Por qué se descartó |
|---|---|
| Devolver el árbol descendente completo | Exige recorrer la estructura, que es justo lo que **D-22** debe gobernar. Reservado hasta que se cierre (`spec.md` §4.2) |
| Devolver el conteo de la rama indirecta, aunque sea solo un número | Obliga al mismo recorrido, y **un total tampoco es inocuo**: revela el tamaño de la red de cada mando (`spec.md` §14, pregunta 2) |
| Una variante «mi equipo» resuelta contra el actor | Es alcance por persona, reservado por `security.md` §6 |
| Devolver el historial de superiores anteriores | No ayuda a ninguno de los dos usos de esta consulta. Quien lo necesitará es una auditoría de comisiones, con permiso propio y filtros por fecha (`spec.md` §14, pregunta 1) |
| Admitir filtros sobre el equipo directo | Dos filtrados que mantener sincronizados sobre un subconjunto que cabe en dos páginas. Quien busque a alguien concreto tiene `RF-SP-025` |
| Devolver la cadena ascendente completa | Se obtiene encadenando consultas, y devolverla entera invitaría a usarla como sustituto del modelo de alcance que falta |
| Un permiso propio para la estructura | Quien puede ver la ficha puede ver de quién depende. Un permiso más sin una decisión que lo justifique |
| Un repositorio de lectura propio en lugar de ampliar `SupervisedTeamCounter` | El total de esta pantalla y el de los rechazos de `RN-SP-022` divergirían, y nadie se enteraría hasta comparar los dos números (§3) |
| `404` para quien no pertenece a la fuerza comercial | «No tiene estructura» es una respuesta legítima y distinta de «no existe» (`FA-001`) |
| Ocultar del equipo a los subordinados inactivos o bloqueados | El total dejaría de cuadrar con el que impide dar de baja a su superior. Aparecen con su estado a la vista (`spec.md` §13) |
| Leer superior y equipo en transacciones separadas | Abre la ventana en que una reasignación concurrente muestra a la persona sin superior o con dos (§7) |

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
| `CA-SP-452` | API | Sin `users:read`: `403` |
| `CA-SP-453` | API | **No** contiene superiores anteriores ni tramos cerrados |
| `CA-SP-454` | API | **No** contiene ningún conteo de la rama indirecta |
| `CA-SP-455` | API | **No** admite filtros sobre el equipo directo |

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
