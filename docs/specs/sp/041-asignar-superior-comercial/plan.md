# PLAN — `RF-SP-041` Asignar o cambiar el superior comercial de un usuario

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-041` |
| Especificación | [`spec.md`](spec.md) |
| `spec.md` aprobada el | 22-08-2026 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 24-08-2026 |

---

## 1. Enfoque

Registra una relación **entre personas**, la única del módulo (`requirements/sp.md` §10.7). Todo lo demás vincula a alguien con un rol, un permiso o una membresía; aquí Ana tiene a cargo a Luis.

La operación es un **cierre y una apertura en la misma transacción**, no una edición. La fila anterior no se modifica ni se borra: se cierra con su `ended_at` y se conserva, porque quién estuvo a cargo de quién y entre qué fechas es historial de negocio del que colgará el reparto de comisiones (`RN-SP-021`). Confundirlo con un `UPDATE` del superior es el error más fácil y destruye exactamente el dato que justifica la tabla.

Lo que le da su dificultad real es `RN-SP-020`: **el superior debe portar el rol padre inmediato del rol comercial de mayor rango del subordinado**. No basta con que sea «alguien de más arriba». Un agente reporta a quien porta `DIRECTOR`, nunca a otro agente ni directamente a un manager. Esa exigencia es lo que impide que la estructura de personas contradiga la de roles — y si se contradijeran, ninguna de las dos serviría para repartir una comisión, porque no habría forma de saber cuál está equivocada.

De ahí sale además una propiedad que ahorra una regla entera: **la cadena de personas hereda la aciclicidad de la cadena de roles** (`RN-SEG-006`). No hace falta detección de ciclos; el único que las clasificaciones no impiden es el de longitud uno, y lo rechaza `EX-005`.

Y una exigencia adicional al Art. V.13: **el motivo es obligatorio**, aunque esto no sea una eliminación. Un tramo del historial sin explicación será un agujero justo donde más va a doler, meses después, en una disputa de dinero.

## 2. Cambios de esquema

**Ninguno.**

`user_supervisors` la crea `V21__create_user_supervisors.sql` (`RF-SP-024`) con todo lo que este requerimiento necesita, y dos de sus restricciones son las que sostienen el diseño:

- **`uq_user_supervisors_vigente`**, único **parcial** sobre `user_id WHERE ended_at IS NULL`. Es lo que declara `RN-SP-021` en el esquema: una persona no puede tener dos superiores vigentes, y la garantía no depende de que el caso de uso se acuerde. `spec.md` §13 exige exactamente eso — «la garantía se declara en el esquema, no solo en el dominio».
- **La clave sustituta**, que esta tabla lleva y `user_roles` no. El mismo par `(user_id, supervisor_id)` puede repetirse legítimamente si alguien vuelve a estar a cargo de quien ya lo estuvo, y lo que distingue una fila de otra es **el periodo** (`requirements/sp.md` §10.7).

**El motivo no se persiste en la tabla.** Vive **solo en el evento de `audit_change_log`**, y es deliberado: `user_supervisors` describe el estado de la estructura, no por qué llegó a serlo. Añadir una columna de motivo duplicaría en una tabla de negocio un dato cuya sede es la auditoría (Art. V.7), y obligaría a mantener dos copias que un día dirán cosas distintas.

**El índice de lectura por superior** —`ix_user_supervisors_supervisor_vigente`— lo crea `RF-SP-028` en `V24`. Este requerimiento lo consume; `RF-SP-042` también.

## 3. Componentes afectados

| Capa | Componente | Nuevo / Modificado | Responsabilidad |
|---|---|---|---|
| `domain` | `CommercialStructure` | **Modificado** | Componente de `RF-SP-024`, ampliado por `RF-SP-030`. Aquí gana `RN-SP-020` en su forma completa: **qué rol debe portar el superior** de un subordinado dado, y si ese subordinado es la cúspide |
| `domain` | `SelfOperationGuard` | Sin cambios | `RN-SP-017`, creado por `RF-SP-028`. **Este requerimiento es el cuarto que lo consume** |
| `domain` | `SupervisorAssignment` | **Nuevo** | Agregado de la asignación: apertura, cierre con fecha y la regla de que solo una está vigente |
| `domain` | `SupervisorAssignmentRepository` | **Nuevo** | Puerto: asignación vigente de una persona, cierre y apertura |
| `domain` | `ChangeReason` | Sin cambios | Objeto de valor del motivo, creado por `RF-SP-028` como `StatusChangeReason`. **Se generaliza aquí** (§9) |
| `application` | `AssignSupervisorService` | Nuevo | Caso de uso. `@Transactional`, orden de `plan.md` §4 y auditoría |
| `application` | `UserChangeAuditor` | Sin cambios | Puerto de `RF-SP-024` hacia `shared/audit` |
| `api` | `UserController` | Modificado | Añade `PATCH /api/v1/users/{id}/supervisor` |
| `api` | `AssignSupervisorRequest` | Nuevo | DTO de entrada: superior y motivo |
| `api` | `CommercialStructureResponse` | **Nuevo, compartido** | Salida de esta operación **y de `RF-SP-042`**: subordinado, superior vigente y superior anterior |

`CommercialStructureResponse` se declara compartido con `RF-SP-042` a propósito. Las dos operaciones describen la misma realidad —de quién depende alguien— y dos formas distintas obligarían a la interfaz a dos caminos de código para pintar lo mismo. `RF-SP-042` la amplía con el equipo directo y su paginación.

## 4. Contrato de API

| Método | Ruta | Descripción |
|---|---|---|
| `PATCH` | `/api/v1/users/{id}/supervisor` | Establece o cambia el superior comercial de la persona |

`PATCH` sobre el subrecurso, tal como declara `requirements/sp.md` §9. No `PUT`: el cuerpo no representa el estado completo del subrecurso —falta el periodo, que lo fija el sistema— y `PUT` invitaría a pensar que se puede enviar.

**Petición**

```json
{
  "supervisorId": "018f3a2b-7c41-7000-9a3d-1f2e5b8c9d90",
  "reason": "Reorganización de la zona norte"
}
```

**No se admite fecha de inicio.** La asignación rige desde que se ejecuta, siempre (`CA-SP-429`). Declararla obligaría a especificar solapamientos, huecos entre tramos y correcciones retroactivas sobre periodos ya liquidados, y hoy ningún requerimiento consume esas fechas.

**No se admite retirar el superior.** No hay forma de enviar `supervisorId` en nulo: el estado «vendedor sin superior» no existe, y la única salida es dejar de portar rol comercial con `RF-SP-031`.

**Respuesta `200`** — `CommercialStructureResponse` con el subordinado, el superior vigente y **el anterior con su fecha de cierre**, cuando lo hubo. Devolver el anterior no es adorno: es lo que permite a quien reorganiza confirmar de un vistazo que cerró el tramo que creía cerrar.

**Errores**

| Código | Cuándo | `error_code` |
|---|---|---|
| `400` | Identificador malformado, o motivo ausente o vacío (`EX-007`) | `VAL-001`, `VAL-008` |
| `401` | Token ausente o inválido | `AUTH-001` |
| `403` | El actor no posee `users:assign-supervisor` | `AUTH-002` |
| `404` | Alguna de las dos personas no existe o está eliminada (`EX-006`) | `VAL-002` |
| `409` | El actor es el propio subordinado (`EX-008`) | `RN-SP-017` |
| `409` | El subordinado no pertenece a la fuerza comercial (`EX-001`) | `VAL-003` |
| `409` | El subordinado es la cúspide (`EX-002`) | `VAL-004` |
| `409` | El superior no porta el rol que exige el orden de mando (`EX-003`) | `RN-SP-020` |
| `409` | El superior no está activo (`EX-004`) | `VAL-006` |
| `409` | Alguien a su propio cargo (`EX-005`) | `VAL-007` |
| `500` | Fallo no controlado | `ERR-500` |

**El motivo vacío es `400` y todo lo demás es `409`**, y la frontera es la de siempre: el motivo se decide mirando el cuerpo; las seis condiciones restantes exigen leer a las dos personas y sus roles, y todas son reglas de negocio violadas sobre datos que existen.

**El cuerpo del `409` de `EX-003` debe informar qué rol debería portar el superior.** `spec.md` lo exige de forma explícita, y sin ese dato quien recibe el error no sabe a quién buscar — que es la diferencia entre un error accionable y uno que obliga a adivinar.

**Orden de verificación**

1. Formato de los identificadores y **motivo con contenido** (`EX-007`).
2. Ambas personas existen y no están eliminadas.
3. El subordinado no es el propio actor (`RN-SP-017`).
4. Subordinado y superior no son la misma persona.
5. El subordinado porta rol comercial y no es la cúspide.
6. El superior está `ACTIVO` y porta el rol que exige `RN-SP-020`.

El paso 1 va el primero **aunque no sepamos todavía si habrá cambio**, y `FA-001` lo confirma: el motivo se valida antes de saber si la operación es efectiva. Exigirlo solo cuando resulte haber cambio obligaría a validar en dos momentos distintos según el estado previo, que es la clase de condicional que acaba dejando pasar un caso.

## 5. Autorización

| Endpoint | Permiso requerido |
|---|---|
| `PATCH /api/v1/users/{id}/supervisor` | `users:assign-supervisor` |

Permiso propio, sembrado en `V3__seed_permissions.sql` e incorporado a `security.md` §4.4 el 22-08-2026. No acompaña a `users:update`: reorganizar la estructura comercial es una decisión de negocio, no de administración de datos.

**`RN-SP-017` no es autorización.** Se comprueba en el caso de uso con `SelfOperationGuard` y su rechazo es `409`, no `403` — mismo criterio que `RF-SP-038` §5.

**Y una frontera que conviene repetir aquí:** este permiso concede **registrar** la estructura, no ver los datos de nadie. Registrar la estructura no concede visibilidad sobre el equipo, y **D-22 sigue abierta** (`security.md` §6, `requirements/sp.md` §10.7).

## 6. Auditoría

| Operación | Registro | Contenido |
|---|---|---|
| Cambio efectivo | `audit_change_log` | `action = UPDATE` sobre la entidad `users`, `entity_id` del **subordinado**, con `before` y `after` del superior —ambos identificables— y **el motivo declarado** |
| Sin cambio (`FA-001`) | — | **Ningún evento**: nada cambió, y el motivo se descarta porque no hay hecho al que atribuirlo (`CA-SP-416`) |
| Rechazos `409` | `audit_error_log` | `resource = 'users'`, `error_type = 'BUSINESS_RULE'`, `severity = 'MEDIA'` |
| Rechazos `404` y `400` | — | **No se auditan**: `ck_audit_error_log_status` rechaza ambos estados |
| Denegación `403` | `audit_security_log` | `AUTHORIZATION_DENIED`, severidad media. Lo emite la capa de seguridad compartida |
| Fallo no controlado `5xx` | `audit_error_log` | `error_type = 'UNHANDLED'`, `severity = 'ALTA'` |

**Un solo evento y no dos**, pese a que la operación cierra una fila y abre otra. Lo que ocurrió es un hecho —«esta persona pasó a depender de aquella»— y `before`/`after` lo expresan entero. Dos eventos obligarían a cruzarlos para responder la única pregunta que se hace.

**`entity_id` es el subordinado y no el superior.** La línea de tiempo que `RF-SP-011` reconstruye responde «qué le pasó a esta persona», y lo que pasó le pasó a quien cambió de responsable. El superior aparece en `changes`, donde es consultable, no como sujeto del evento.

!!! warning "Ningún evento de seguridad — con condición de disparo declarada"

    `CA-SP-428` exige que esta operación **no** emita evento en `audit_security_log`: no concede ni retira privilegio alguno, y registrarla allí diluiría durante meses la señal de un registro que existe para el control de acceso.

    La condición de disparo está escrita en `spec.md` §7 y se repite aquí porque es este plan el que tendría que cambiar: **el día que D-22 haga depender de esta relación qué datos ve cada quien**, mover a alguien de rama sí cambiará su acceso efectivo, y entonces la especificación vuelve a su compuerta para añadir el evento — y este plan, con ella.

## 7. Transaccionalidad

| Elemento | Transacción |
|---|---|
| Cierre de la asignación anterior y apertura de la nueva | **La misma**, y bajo bloqueo sobre el subordinado |
| Evento de `audit_change_log` | **La misma** (Art. V.14) |
| Auditoría de los rechazos | **Independiente**, `REQUIRES_NEW` |
| Revocación de sesiones | **No aplica**: la operación no cambia ningún permiso efectivo |

**El cierre y la apertura comparten transacción y bloqueo**, y de ahí sale la garantía que `spec.md` §13 exige de la consulta concurrente: `RF-SP-042` ve el estado anterior o el posterior, **nunca a la persona sin superior ni con dos**. Sin el bloqueo sobre el subordinado, dos reasignaciones simultáneas cerrarían ambas la misma fila y abrirían dos — y `uq_user_supervisors_vigente` haría fallar una con `23505`, que saldría como `500` en lugar de serializarse.

**Ninguna sesión se revoca y ningún permiso cambia**, lo que hace de esta la única operación de escritura del módulo sobre una persona que no toca su acceso. `CA-SP-422` y `CA-SP-423` lo verifican, y conviene que existan: la tentación de «refrescar» algo tras mover a alguien de rama es real, y hacerlo expulsaría de su sesión a tres personas por un cambio administrativo.

## 8. Impacto sobre otros módulos

- **`RF-SP-024` y `RF-SP-030`** ya exigen indicar el superior al conceder el primer rol `VENDEDOR` o al ascender. Esta operación **no otorga ni retira roles**: solo corrige la estructura de quien ya lo porta.
- **`RF-SP-028`, `RF-SP-029` y `RF-SP-031`** rechazan por `RN-SP-022` mientras quede equipo a cargo, y **esta operación es la única vía de desbloquearlo**, una persona a la vez.
- **`RF-SP-042`** comparte `CommercialStructureResponse` y consume el mismo repositorio.
- **`RF-SP-039`** devuelve el superior vigente que esta operación fija.
- **`RF-SP-028`** aporta `SelfOperationGuard` y el objeto de valor del motivo, que aquí se generaliza (§9).
- **`security.md` §6 y `requirements/sp.md` §10.7** no cambian: **D-22 sigue abierta** y esta operación no la adelanta.
- **Ninguna enmienda a documento transversal.** `RN-SP-017` ya se amplió el 22-08-2026 al aprobarse esta spec.

## 9. Alternativas consideradas

| Alternativa | Por qué se descartó |
|---|---|
| `UPDATE` del superior sobre una sola fila | Destruye el historial que justifica la tabla: quién estuvo a cargo de quién y entre qué fechas (`RN-SP-021`) |
| Persistir el motivo en `user_supervisors` | Duplica en una tabla de negocio un dato cuya sede es la auditoría (Art. V.7), y obliga a mantener dos copias que un día dirán cosas distintas |
| Admitir fecha de inicio declarada | Obliga a especificar solapamientos, huecos y correcciones retroactivas sobre periodos liquidados, sin ningún requerimiento que consuma esas fechas (`spec.md` §14, pregunta 3) |
| Admitir retirar el superior enviando nulo | El estado «vendedor sin superior» no existe. La salida es `RF-SP-031` |
| Reasignar un equipo entero en una petición | La estructura se corrige persona a persona, con una decisión explícita por cada una (`spec.md` §4.2) |
| Detección de ciclos entre personas | Innecesaria: la cadena hereda la aciclicidad de la de roles. Solo el ciclo de longitud uno necesita rechazo, y es `EX-005` |
| Validar contra cualquier rol superior en la cadena, no el padre inmediato | Permitiría poner a un agente directamente a cargo de un manager, saltando el nivel. `RN-SP-020` exige el inmediato, igual que `RN-SEG-004` |
| Emitir dos eventos, uno de cierre y otro de apertura | Obliga a cruzarlos para responder la única pregunta que se hace |
| Emitir evento de seguridad | Diluye la señal de un registro que existe para el control de acceso. Con condición de disparo declarada para cuando D-22 se cierre (§6) |
| Revocar sesiones tras el cambio | Expulsaría a tres personas de su sesión por un cambio administrativo que no altera ningún permiso |
| Exigir el motivo solo cuando haya cambio real | Obliga a validar en dos momentos según el estado previo (`FA-001`) |
| Un `StatusChangeReason` propio para esta operación | Sería el segundo objeto de valor idéntico. Se **generaliza** el de `RF-SP-028` a `ChangeReason`, que ambas consumen |

## 10. Riesgos

| Riesgo | Impacto | Mitigación |
|---|---|---|
| Se implementa como `UPDATE` y se pierde el historial | **Alto** | `CA-SP-415` verifica que la fila anterior **permanece** con su `ended_at` |
| Falta el bloqueo y dos reasignaciones concurrentes producen `500` | **Alto** | Bloqueo sobre el subordinado (§7), con prueba de integración concurrente |
| `RN-SP-020` se valida contra la cadena completa en vez del padre inmediato | **Alto** | `CA-SP-414` prueba el salto de nivel: agente directamente bajo manager debe rechazarse |
| El `409` de `EX-003` no dice qué rol debería portar el superior | Medio | `CA-SP-413` lo exige; sin él el error obliga a adivinar |
| Se revocan sesiones «por si acaso» | Medio | `CA-SP-422` y `CA-SP-423` verifican que nada cambia para nadie |
| El motivo se persiste en la tabla por comodidad | Medio | §2 y §9 lo declaran; su sede es la auditoría |
| El superior asciende y sus subordinados quedan incumpliendo `RN-SP-020` | Medio | Hueco conocido y declarado (`spec.md` §13): se valida al escribir, no de forma continua. La corrección es esta misma operación sobre cada subordinado |
| Un cambio acordado a mitad de mes se registra tarde | Bajo, **aceptado** | Consecuencia de no admitir fecha declarada. Quien recalcule una liquidación la corrige fuera del sistema |
| El motivo es poco informativo | Bajo, **aceptado** | El sistema exige contenido, no calidad. Una longitud mínima produce relleno, como resolvió `RF-SP-029` |

## 11. Estrategia de prueba

| Criterio | Nivel | Qué verifica |
|---|---|---|
| `CA-SP-412` | Integración | Un agente queda a cargo de un director y la consulta lo devuelve |
| `CA-SP-413` | API | Agente bajo agente: `409` indicando **qué rol** debería portar el superior |
| `CA-SP-414` | API | Agente directamente bajo manager: `409`. Es la prueba del salto de nivel |
| `CA-SP-415` | Integración | La asignación anterior **se cierra y su fila permanece**; queda **una** vigente |
| `CA-SP-416` | Integración | Repetir el mismo superior: sin error, **sin evento** y **sin partir el historial** |
| `CA-SP-417` | API | Sin rol comercial: `409` |
| `CA-SP-418` | API | Cúspide: `409` |
| `CA-SP-419` | API | Superior inactivo, bloqueado o eliminado: `409` |
| `CA-SP-420` | API | Alguien a su propio cargo: `409` |
| `CA-SP-421` | Integración | **El equipo se mueve con él**: los subordinados del reasignado conservan su superior |
| `CA-SP-422` | Integración | Los permisos efectivos de los tres implicados son idénticos antes y después |
| `CA-SP-423` | Integración | Nadie gana acceso a ningún dato que no tuviera |
| `CA-SP-424` | Integración | Un evento de cambio con **ambos superiores** identificables y con el motivo |
| `CA-SP-426` | API | Motivo ausente o en blanco: `400` **antes** de ejecutar nada |
| `CA-SP-427` | API | El actor sobre su propia cuenta: `409` con `RN-SP-017` |
| `CA-SP-428` | Integración | **Ninguna** fila en `audit_security_log` |
| `CA-SP-429` | Integración | La asignación rige desde el momento de ejecutarse; el endpoint **no admite** otra fecha |
| `CA-SP-425` | API | Sin `users:assign-supervisor`: `403` |

Casos límite de `spec.md` §13 con prueba propia (Art. VII.3):

| Caso | Nivel | Qué verifica |
|---|---|---|
| Reasignación concurrente de la misma persona | **Integración concurrente** | Se serializan; **nunca** quedan dos vigentes ni sale un `500` por `23505` |
| El superior nuevo era subordinado del actual | Integración | Se admite: inversión de rama legítima si ambos portan los roles correctos |
| El subordinado porta dos roles comerciales | Unitaria | La regla se evalúa contra el de **mayor rango** |
| El subordinado está inactivo o bloqueado | API | Se admite reasignarlo: es necesario antes de darle la baja |
| Motivo de una sola palabra | API | Pasa la validación. El sistema exige contenido, no calidad |

**`CA-SP-421` es la prueba menos evidente y la que más fácilmente se da por supuesta.** Mover a alguien con equipo propio **no toca a su equipo**: sus subordinados siguen a su cargo y lo que cambia es de quién depende la rama entera. Una implementación que «arrastrara» a los subordinados al superior nuevo pasaría todas las demás pruebas y reorganizaría la empresa entera con cada reasignación.
