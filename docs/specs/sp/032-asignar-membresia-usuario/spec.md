# SPEC — `RF-SP-032` Asignar membresía a un usuario

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-032` |
| Módulo | `SP` — Sistema Principal |
| Estado | **Aprobada** |
| Autor | Responsable técnico |
| Aprobada por | Responsable técnico |
| Fecha de aprobación | 21-08-2026 |

---

## 1. Objetivo

Fijar hasta dónde alcanza un consumidor dentro de los servicios y contenidos del sistema.

## 2. Contexto

El rol dice **qué puede hacer** una persona; la membresía, **hasta dónde llega**. Son dos ejes distintos y por eso son dos asignaciones distintas: hay cursos abiertos a todos y cursos reservados a niveles superiores, y esa diferencia no se expresa bien con permisos.

Dos reglas la gobiernan, y las dos vienen de que la membresía solo tiene sentido para clientes. `RN-SP-013`: la persona debe portar al menos un rol de clasificación `CONSUMIDOR`, o se rechaza; un funcionario con membresía sería un dato sin significado que ningún módulo sabría interpretar. `RN-SP-014`: **una sola membresía vigente por persona**, de modo que asignar otra sustituye la anterior. No se acumulan niveles ni se comparan entre sí: el alcance de alguien es un punto de la cadena, no un conjunto.

Esa sustitución es lo que distingue esta operación de `RF-SP-030`. Asignar roles agrega; asignar membresía **reemplaza**, y el reemplazo tiene que quedar auditado con el nivel anterior y el nuevo, porque es la única forma de responder después por qué alguien dejó de ver un contenido al que llegaba.

### La membresía puede tener fecha de fin

La asignación admite una **fecha de fin opcional**. Sin ella, la membresía dura hasta que alguien la cambie o la retire; con ella, deja de estar vigente cuando esa fecha pasa. Es lo que permite vender un nivel por un periodo sin que nadie tenga que acordarse de retirarlo.

**La vigencia se evalúa por fecha en el momento de consultarla, no la retira ningún proceso.** Esa decisión es deliberada y conviene entender qué compra y qué cuesta:

- **Compra** que no haga falta un proceso programado que recorra las membresías y las vaya venciendo. Ese proceso sería un requerimiento nuevo —con su horario, su registro de ejecución y su comportamiento ante fallos— que hoy nada cubre, y sin él una fecha de fin no significaría nada.
- **Cuesta** que la fila siga ahí después de vencer. La persona conserva su única plaza de membresía ocupada por una caducada, y eso es visible: `RF-SP-026` devuelve la membresía **y su fecha**, de modo que se distingue una vigente de una vencida.

Vencer no es lo mismo que no tener: una membresía vencida **no concede nivel alguno**, pero deja constancia de cuál fue el último y hasta cuándo. Renovarla es volver a asignar la misma con una fecha nueva, que es exactamente lo que esta operación hace.

## 3. Actores

| Actor | Rol en esta funcionalidad |
|---|---|
| Super Administrador | Asigna membresías |
| Administrador | Asigna membresías |

## 4. Alcance

### 4.1 Incluye

- Asignar una membresía a una persona que porta un rol `CONSUMIDOR`.
- Sustituir la membresía vigente por otra, dejando constancia del cambio.

### 4.2 No incluye

- Retirar la membresía sin poner otra en su lugar → `RF-SP-033`.
- Crear membresías ni alterar la cadena → `RF-SP-016`.
- Asignar el rol `CONSUMIDOR` que habilita esta operación → `RF-SP-030`.
- Definir qué contenido exige qué nivel: corresponde a los módulos de academia y productos.
- Un proceso que retire las membresías vencidas: la vigencia se evalúa al consultarla (§2), no la retira nadie.

## 5. Reglas de negocio aplicables

| ID | Regla | Origen |
|---|---|---|
| `RN-SP-013` | La membresía exige al menos un rol de clasificación `CONSUMIDOR` | `requirements/sp.md` §5.1 |
| `RN-SP-014` | Un usuario tiene como mucho una membresía vigente; asignar otra sustituye la anterior y el cambio queda auditado | `requirements/sp.md` §5.1 |

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción | Restricción de negocio |
|---|---|---|---|
| Identificador del usuario | Sí | Persona a la que se asigna la membresía | Debe existir, no estar eliminada y portar un rol `CONSUMIDOR` |
| Membresía | Sí | Nivel de acceso que se le concede | Debe existir en la cadena |
| Fecha de fin | No | Hasta cuándo está vigente | Debe ser posterior al momento de la asignación. Sin ella, la membresía es indefinida |

### 6.2 Salida

| Dato | Descripción |
|---|---|
| Usuario | Usuario con su membresía vigente, el nivel que le corresponde y su fecha de fin cuando la tiene |

## 7. Precondiciones y postcondiciones

**Precondiciones**

- El actor está autenticado y posee el permiso de asignación de membresías.
- El usuario existe, no está eliminado y porta al menos un rol de clasificación `CONSUMIDOR`.
- La membresía indicada existe.

**Postcondiciones**

- La persona queda con esa membresía como su única membresía asignada, vigente desde ese momento y hasta la fecha de fin si se indicó.
- Si tenía otra —vigente o ya vencida—, deja de estarlo y el cambio queda registrado con el nivel anterior y el nuevo.
- Su alcance sobre contenidos y servicios pasa a evaluarse por el nivel de la membresía nueva.
- Queda constancia en la auditoría de cambios.

## 8. Flujo principal

1. El actor solicita asignar una membresía a un usuario.
2. El sistema verifica que el usuario exista y no esté eliminado.
3. El sistema verifica que la membresía indicada exista.
4. El sistema verifica que el usuario porte al menos un rol de clasificación `CONSUMIDOR`.
5. Si se indicó fecha de fin, el sistema verifica que sea posterior al momento de la asignación.
6. El sistema sustituye la membresía anterior por la indicada, con su vigencia, o la establece si no tenía ninguna.
7. El sistema registra el evento en la auditoría de cambios, con la membresía anterior y la nueva, y con sus fechas de fin.
8. El sistema informa el usuario con su membresía vigente.

## 9. Flujos alternativos

### FA-001 — Primera membresía de la persona

**Cuándo ocurre:** el usuario no tenía ninguna membresía.

1. La membresía queda establecida sin que haya nada que sustituir.
2. El evento de auditoría registra la membresía nueva y ninguna anterior.

### FA-002 — La membresía indicada es la que ya tiene, con la misma vigencia

**Cuándo ocurre:** el usuario ya tiene esa misma membresía y la fecha de fin enviada coincide con la suya —o ambas están ausentes.

1. El sistema no aplica cambio ni registra evento.
2. Devuelve al usuario sin tratarlo como error: la operación es idempotente.

### FA-003 — Renovación

**Cuándo ocurre:** el usuario ya tiene esa misma membresía, pero se envía una fecha de fin distinta.

1. El sistema actualiza la vigencia y **sí registra evento**: el nivel no cambió, pero hasta cuándo lo tiene, sí.
2. Es el caso normal de renovar un periodo, y también el de convertir una membresía indefinida en una con fecha, o al revés.
3. Funciona igual sobre una membresía ya **vencida**: renovarla es devolverle vigencia.

## 10. Excepciones

### EX-001 — El usuario no es consumidor

**Condición:** la persona no porta ningún rol de clasificación `CONSUMIDOR`.
**Respuesta del sistema:** rechaza la operación, cita `RN-SP-013` e indica que primero debe asignarse un rol consumidor con `RF-SP-030`.

### EX-002 — Membresía inexistente

**Condición:** la membresía indicada no existe en la cadena.
**Respuesta del sistema:** rechaza la operación e informa que la membresía no es válida.

### EX-003 — Usuario inexistente o eliminado

**Condición:** el identificador no corresponde a ningún usuario vigente.
**Respuesta del sistema:** rechaza la operación e informa que el usuario no existe.

### EX-004 — Fecha de fin no posterior al momento de la asignación

**Condición:** se indica una fecha de fin igual o anterior al instante en que se asigna.
**Respuesta del sistema:** rechaza la operación e informa que la fecha debe ser posterior. Aceptarla dejaría a la persona con una membresía nacida ya vencida, que no concede nada y que nadie pidió.

## 11. Validaciones

| ID | Validación | Mensaje esperado |
|---|---|---|
| `VAL-001` | Membresía obligatoria | La membresía es obligatoria. |
| `VAL-002` | Membresía existente | La membresía indicada no existe. |
| `VAL-003` | El usuario porta un rol de clasificación consumidor | La persona debe tener un rol de consumidor para recibir una membresía. |
| `VAL-004` | Usuario existente y no eliminado | El usuario solicitado no existe. |
| `VAL-005` | Fecha de fin posterior al momento de la asignación | La fecha de fin debe ser posterior a hoy. |

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-SP-272` | El sistema asigna una membresía a una persona que porta un rol de clasificación consumidor |
| `CA-SP-273` | El sistema rechaza la asignación a una persona sin ningún rol consumidor, e indica que debe asignarse uno primero |
| `CA-SP-274` | Asignar una membresía a quien ya tenía otra sustituye la anterior: la persona queda con una sola vigente |
| `CA-SP-275` | La auditoría de cambios conserva la membresía anterior y la nueva en el mismo evento |
| `CA-SP-276` | El sistema no registra evento cuando la membresía indicada es la que la persona ya tenía |
| `CA-SP-277` | El sistema rechaza la asignación de una membresía inexistente |
| `CA-SP-278` | El sistema asigna membresía a una persona con más de un rol, bastando que uno sea consumidor |
| `CA-SP-279` | La membresía asignada no altera los roles ni los permisos efectivos de la persona |
| `CA-SP-364` | El sistema asigna una membresía **sin** fecha de fin y esta no vence nunca |
| `CA-SP-365` | El sistema asigna una membresía **con** fecha de fin, y al pasar esa fecha la persona deja de tener nivel sin que ningún proceso intervenga |
| `CA-SP-366` | Una membresía vencida se distingue de no tener ninguna: se devuelve con su fecha, y no concede nivel |
| `CA-SP-367` | Renovar —misma membresía, fecha distinta— actualiza la vigencia y **sí** registra evento |
| `CA-SP-368` | El sistema rechaza una fecha de fin anterior o igual al momento de la asignación |
| `CA-SP-280` | El sistema rechaza la operación a un actor sin el permiso de asignación de membresías |

## 13. Casos límite

- **Persona con rol consumidor y rol funcionario a la vez:** se admite. `RN-SP-013` exige **al menos uno** consumidor, no que todos lo sean.
- **Sustituir por una membresía de nivel inferior:** se admite sin condiciones. Bajar de nivel es tan legítimo como subir, y es lo que ocurre cuando alguien deja de pagar el nivel alto.
- **Cadena vacía:** ninguna membresía existe todavía, de modo que toda asignación se rechaza por `EX-002`. Es el estado inicial del sistema y es correcto.
- **Inserción de un nivel intermedio en la cadena:** no toca esta asignación. La persona conserva su membresía y su alcance se sigue evaluando por nivel, según se resolvió en la pregunta 3 de `RF-SP-016`.
- **Asignación concurrente de dos membresías al mismo usuario:** ambas pretenden ser la vigente. La restricción de unicidad sobre la membresía vigente del usuario debe resolver el empate sin dejar dos.
- **Retiro del rol consumidor después de asignar la membresía:** lo impide `RN-SP-015` en `RF-SP-031`, que rechaza el retiro mientras haya membresía.
- **Usuario inactivo o bloqueado:** puede recibir membresía. El alcance se evaluará cuando vuelva a entrar.
- **Membresía vencida:** la persona conserva su plaza ocupada por ella y no tiene nivel alguno. `RN-SP-015` deja de protegerla, de modo que `RF-SP-031` sí admite retirarle el último rol consumidor. Es coherente: ya no hay nivel de acceso que quede huérfano.
- **Fecha de fin que vence entre dos peticiones:** la vigencia se evalúa al consultar, de modo que la respuesta cambia sola sin que nada se haya escrito. No es un defecto, es el diseño.
- **Renovar una membresía vencida:** se admite y le devuelve vigencia. No hace falta retirarla antes.
- **Nadie renueva y la fecha pasa:** la persona pierde el nivel en silencio, sin aviso previo. Es la contrapartida de no tener proceso de vencimiento, y la interfaz debería hacerlo visible antes de que ocurra.

## 14. Preguntas abiertas

Ninguna. Las cuatro se resolvieron el 21-08-2026, antes de aprobar la especificación. La segunda se resolvió en contra de la propuesta del borrador, lo que obligó a añadir a §2 el apartado sobre la fecha de fin y a `RN-SP-014` una precisión sobre qué significa «vigente».

| # | Pregunta | Resolución |
|---|---|---|
| 1 | ¿La sustitución de la membresía anterior se audita como un cambio, o como una eliminación más un alta? | **Un solo evento de cambio**, con el nivel anterior y el nuevo. Es como se hace la pregunta en la práctica —«¿de qué nivel a qué nivel pasó, y quién lo hizo?»— y responderla no debería exigir cruzar dos registros. La alternativa era más fiel al modelo de datos, donde desaparece una fila y aparece otra, pero esa fidelidad no la necesita nadie: la asignación de membresía no es una asociación cualquiera, es el atributo que dice hasta dónde llega una persona. `CA-SP-275` lo verifica |
| 2 | ¿La membresía tiene fechas de vigencia? | **Sí, con fecha de fin opcional.** Se resolvió en contra de la propuesta del borrador, que la dejaba fuera hasta que existiera cobro recurrente. Sin fecha, la membresía es indefinida y el comportamiento es el que la spec ya describía; con fecha, deja de estar vigente al pasar. **La decisión que hace viable la fecha sin arrastrar un requerimiento nuevo es que la vigencia se evalúa al consultarla, no la retira ningún proceso**: un proceso programado de vencimiento tendría su horario, su registro de ejecución y su comportamiento ante fallos, y nada de eso está especificado. El coste queda declarado en §2 y en §13: la fila permanece tras vencer, ocupando la única plaza de membresía de la persona, y quien no renueve pierde el nivel en silencio |
| 3 | ¿Debe registrarse también en la auditoría de seguridad? | **Solo en la de cambios.** El catálogo de `security.md` §8.1 es cerrado y enumera la asignación de **roles**, no la de membresía: una membresía no concede permisos del sistema ni interviene en la resolución de `security.md` §4.5, solo determina el alcance sobre contenido. Es el mismo criterio con el que `RF-SP-016` §6 dejó fuera el alta de membresía, y coherente con `RF-SP-022`, `RF-SP-023` y con la mitad de `RF-SP-027` que no toca vías de acceso |
| 4 | ¿Quién debería poder asignar membresías? | **El permiso se mantiene separado, y esta spec no fija qué roles lo reciben.** `users:assign-membership` ya es un permiso propio, distinto de `users:update`, que es lo que importa: en la práctica quien asigna membresías es comercial o soporte, no quien administra la seguridad del sistema. Qué roles del catálogo lo reciben se decide **al sembrarlos**, no aquí: atar la especificación al catálogo de roles la obligaría a cambiar cada vez que el catálogo cambie, sin que el requerimiento haya cambiado |
