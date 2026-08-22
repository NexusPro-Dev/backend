# SPEC — `RF-SP-041` Asignar o cambiar el superior comercial de un usuario

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-041` |
| Módulo | `SP` — Sistema Principal |
| Estado | **Aprobada** |
| Autor | Responsable técnico |
| Aprobada por | Responsable técnico |
| Fecha de aprobación | 22-08-2026 |

---

## 1. Objetivo

Registrar quién está a cargo de quién dentro de la fuerza comercial, y permitir corregirlo cuando la organización cambia.

## 2. Contexto

El sistema ya sabe que el rol `DIRECTOR` manda sobre el rol `AGENTE`: lo declara la jerarquía de roles, que existe para acotar privilegios (`RN-SEG-003`) y que `RN-SP-011` reutiliza como orden de mando. Lo que **no** sabe hasta ahora es que Ana, directora, tiene a cargo a Luis y a Marta. Esa es una relación entre **personas**, no entre roles, y hasta el 22-08-2026 el diseño la mantuvo deliberadamente aparcada.

Se desaparca porque es el dato del que dependen tres cosas que llegarán: las comisiones —que se atribuyen por línea de mando—, el reporte de la propia red, y cualquier consulta que deba acotarse a las personas de las que uno responde.

**Lo que esta funcionalidad no cambia.** Registrar la estructura **no concede a nadie visibilidad sobre los datos de su equipo**. Cómo se determina de quién ve los datos cada uno es un eje distinto del permiso, sin diseñar todavía, y sigue registrado como pendiente en el modelo de seguridad. Esta especificación aporta el dato; no lo interpreta.

**Por qué la estructura de personas no puede contradecir la de roles.** Si un agente pudiera estar a cargo de otro agente, habría dos afirmaciones incompatibles sobre el mismo orden de mando: una en los roles y otra en las personas. Cuando llegara el momento de repartir una comisión, ninguna de las dos serviría para decidir, porque no habría forma de saber cuál está equivocada. Por eso `RN-SP-020` exige que el superior porte exactamente el rol padre inmediato del rol comercial de mayor rango de su subordinado.

## 3. Actores

| Actor | Rol en esta funcionalidad |
|---|---|
| Super Administrador | Reorganiza la estructura comercial sin límite |
| Administrador | Reorganiza la estructura comercial |

## 4. Alcance

### 4.1 Incluye

- Establecer el superior comercial de una persona que porta un rol de la fuerza comercial.
- Sustituir el superior vigente por otro, conservando la asignación anterior como historial cerrado.
- Verificación de que el superior propuesto puede serlo, según el orden de mando que declaran los roles.

### 4.2 No incluye

- Conceder el rol comercial → `RF-SP-024` y `RF-SP-030`, que además exigen indicar el superior en la misma operación cuando conceden el primero o cambian el de mayor rango. Esta funcionalidad **no otorga ni retira roles**.
- Retirar el superior dejando a la persona sin ninguno: no existe como operación. El estado «vendedor sin superior» no se admite, y la única salida es dejar de portar rol comercial → `RF-SP-031`.
- Consultar quién está a cargo de quién → `RF-SP-042`.
- Reasignar un equipo entero en una sola petición. La estructura se corrige **persona a persona**, con una decisión explícita por cada una.
- Determinar qué datos ve cada quien: no es lo que esta relación decide, ni hoy ni al aprobarse esta especificación.

## 5. Reglas de negocio aplicables

| ID | Regla | Origen |
|---|---|---|
| `RN-SP-019` | Todo vendedor tiene superior comercial, salvo la cúspide de la fuerza comercial | `requirements/sp.md` §5.1 |
| `RN-SP-020` | El superior porta el rol padre inmediato del rol vendedor de mayor rango del subordinado | `requirements/sp.md` §5.1 |
| `RN-SP-021` | Un superior vigente por persona; la asignación anterior se cierra y se conserva | `requirements/sp.md` §5.1 |
| `RN-SP-003` | Todo rol se clasifica como `FUNCIONARIO`, `VENDEDOR` o `CONSUMIDOR` | `requirements/sp.md` §5.1 |
| `RN-SP-011` | El orden de mando comercial se expresa con el rol padre | `requirements/sp.md` §5.1 |
| `RN-SP-017` | El actor no aplica la operación sobre su propia cuenta | `requirements/sp.md` §5.1 |

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción | Restricción de negocio |
|---|---|---|---|
| Identificador del subordinado | Sí | Persona cuyo superior se establece o cambia | Debe existir, no estar eliminada y portar al menos un rol de clasificación `VENDEDOR` que no sea la cúspide de la fuerza comercial |
| Identificador del superior | Sí | Persona que pasa a tenerla a cargo | Debe existir, estar `ACTIVO` y portar el rol padre inmediato del rol comercial de mayor rango del subordinado. No puede ser el propio subordinado |
| Motivo | Sí | Por qué cambia la estructura | Texto con contenido real tras recortar los extremos. Se conserva en el evento de auditoría y **no** se admite vacío |

**No se declara desde cuándo rige.** La asignación entra en vigor en el momento de ejecutarse, siempre. Fechar el cambio en el pasado o en el futuro exigiría decidir qué ocurre con periodos solapados, con huecos entre tramos y con cambios retroactivos sobre meses ya liquidados; hoy no hay ningún requerimiento que consuma esas fechas, y ese diseño no tendría a quién servir. Se resolvió así el 22-08-2026, con la salvedad de que el módulo de comisiones podrá reabrirlo cuando exista y sepa qué necesita.

### 6.2 Salida

| Dato | Descripción |
|---|---|
| Subordinado | Persona afectada, con el nombre y el rol comercial que porta |
| Superior vigente | Persona que queda a cargo, con el rol que porta, y desde cuándo |
| Superior anterior | Persona que dejó de tenerla a cargo, y hasta cuándo, cuando la hubo |

## 7. Precondiciones y postcondiciones

**Precondiciones**

- El actor está autenticado y posee el permiso de asignación de superior comercial.
- El subordinado no es el propio actor (`RN-SP-017`).
- Ambas personas existen y ninguna está eliminada.
- El subordinado porta un rol de la fuerza comercial que admite superior.
- El superior porta el rol que el orden de mando exige para ese subordinado.

**Postcondiciones**

- El subordinado queda a cargo del superior indicado, con la asignación vigente y sin fecha de fin.
- Si tenía otro superior, esa asignación queda **cerrada con la fecha de esta operación**, y su fila se conserva: quién estuvo a cargo de quién, y entre qué fechas, es historial de negocio y no una versión vieja de un dato (`RN-SP-021`).
- **El equipo del subordinado se mueve con él.** Nadie más cambia de superior: quienes estaban a cargo del subordinado siguen estándolo, y lo que cambia es de quién depende su rama.
- Los permisos efectivos de todos los implicados quedan **exactamente como estaban**. Esta operación no concede ni retira ninguno.
- Queda constancia en la auditoría de cambios, **con el motivo declarado y con ambos superiores identificables**: el que deja de tenerla a cargo y el que pasa a tenerla.
- **No se emite evento en la auditoría de seguridad.** Esta operación no concede ni retira ningún privilegio, y registrarla allí diluiría la señal de un registro que existe para el control de acceso. Se resolvió así el 22-08-2026 **con condición de disparo declarada**: el día que el modelo de alcance de datos (D-22) haga depender de esta relación qué puede ver cada quien, mover a alguien de rama **sí** cambiará su acceso efectivo, y entonces esta especificación debe volver a su compuerta para añadir el evento de seguridad.

## 8. Flujo principal

1. El actor solicita poner a una persona a cargo de otra y declara el motivo.
2. El sistema verifica que el motivo venga informado y tenga contenido.
3. El sistema verifica que ambas personas existan y no estén eliminadas.
4. El sistema verifica que el subordinado no sea el propio actor.
5. El sistema verifica que el subordinado porte un rol comercial que admita superior.
6. El sistema verifica que el superior esté activo y porte el rol que el orden de mando exige.
7. El sistema cierra la asignación vigente del subordinado, si la tenía, con la fecha de esta operación.
8. El sistema registra la asignación nueva, vigente desde esa misma fecha.
9. El sistema registra el evento en la auditoría de cambios, con el motivo.
10. El sistema informa la estructura resultante para esa persona.

## 9. Flujos alternativos

### FA-001 — El superior propuesto ya lo es

**Cuándo ocurre:** la persona ya está a cargo de quien se indica.

1. El sistema no cierra ni abre nada.
2. La operación es **idempotente**: repetirla no produce error, ni parte el historial en dos tramos consecutivos con el mismo superior.
3. No se registra evento de auditoría: nada cambió. El motivo declarado se descarta, porque no hay hecho al que atribuirlo.
4. El motivo **sigue siendo obligatorio en la entrada**: se valida antes de saber si el cambio es real, y exigirlo solo cuando resulte haber cambio obligaría a validar en dos momentos distintos según el estado previo.

### FA-002 — La persona no tenía superior

**Cuándo ocurre:** el subordinado porta rol comercial y no tiene asignación vigente, situación que solo puede darse por una corrección de datos anterior a esta funcionalidad o por siembra inicial.

1. El sistema omite el cierre y registra directamente la asignación nueva.
2. El resto del flujo es idéntico.

## 10. Excepciones

### EX-001 — El subordinado no pertenece a la fuerza comercial

**Condición:** la persona indicada no porta ningún rol de clasificación `VENDEDOR`.
**Respuesta del sistema:** rechaza la operación y explica que la estructura comercial solo alcanza a quien porta un rol de esa clasificación. Conceder el rol es `RF-SP-030`, y esa operación ya pide el superior.

### EX-002 — El subordinado es la cúspide de la fuerza comercial

**Condición:** el rol comercial de mayor rango de la persona no tiene por encima ningún rol comercial.
**Respuesta del sistema:** rechaza la operación y explica que esa posición no depende de nadie dentro de la estructura comercial. Es la excepción que `RN-SP-019` declara, y funciona igual que el rol raíz de `RN-SEG-007`: la cadena tiene que empezar en alguien.

### EX-003 — El superior no puede serlo

**Condición:** el superior indicado no porta el rol padre inmediato del rol comercial de mayor rango del subordinado — por ejemplo, se intenta poner a un agente a cargo de otro agente, o directamente a cargo de un manager.
**Respuesta del sistema:** rechaza la operación, cita `RN-SP-020` e informa **qué rol debería portar** el superior. Sin ese dato, quien recibe el error no sabe a quién buscar.

### EX-004 — El superior no está activo

**Condición:** el superior indicado está inactivo, bloqueado o eliminado.
**Respuesta del sistema:** rechaza la operación y explica que no puede quedar nadie a cargo de quien no tiene acceso al sistema. Admitirlo crearía justo la situación que `RN-SP-022` impide al retirar el acceso: un equipo cuyo responsable no puede responder por él.

### EX-005 — Alguien a cargo de sí mismo

**Condición:** el subordinado y el superior son la misma persona.
**Respuesta del sistema:** rechaza la operación. Es también la única forma de ciclo que el orden de mando no impide por sí solo.

### EX-006 — Persona inexistente o eliminada

**Condición:** alguno de los dos identificadores no corresponde a un usuario vigente.
**Respuesta del sistema:** rechaza la operación e informa que la persona no existe, sin distinguir entre nunca haber existido y haber sido eliminada.

### EX-007 — Motivo ausente o vacío

**Condición:** no se declara motivo, o el texto no tiene contenido real tras recortar los extremos.
**Respuesta del sistema:** rechaza la operación **antes de ejecutarla** y explica que el motivo es obligatorio.

El Art. V.13 solo lo exige en las eliminaciones, de modo que aquí es una exigencia **adicional y deliberada**, resuelta el 22-08-2026: el historial de mando va a sustentar el reparto de comisiones, y un tramo sin explicación es un agujero justo donde más va a doler —una disputa de dinero meses después, cuando nadie recuerde por qué esa persona cambió de responsable.

### EX-008 — El actor es el propio subordinado

**Condición:** el identificador del subordinado corresponde a la cuenta del actor.
**Respuesta del sistema:** rechaza la operación y cita `RN-SP-017`.

Es el único caso en que quien ejecuta tiene interés directo en el resultado: de la posición en la estructura cuelga la atribución de la producción comercial. `RN-SP-017` se enmendó el 22-08-2026 para alcanzar también a esta operación, además de a la eliminación y al cambio de estado.

## 11. Validaciones

| ID | Validación | Mensaje esperado |
|---|---|---|
| `VAL-001` | Ambos identificadores informados y con formato válido | El identificador indicado no es válido. |
| `VAL-002` | Ambas personas existen y no están eliminadas | La persona solicitada no existe. |
| `VAL-003` | El subordinado porta un rol de la fuerza comercial | Esta persona no forma parte de la fuerza comercial. |
| `VAL-004` | El subordinado no es la cúspide de la fuerza comercial | Esta posición no depende de ninguna otra. |
| `VAL-005` | El superior porta el rol padre inmediato del rol comercial de mayor rango del subordinado | El superior indicado no puede estar a cargo de esta persona. |
| `VAL-006` | El superior está `ACTIVO` | El superior indicado no tiene acceso al sistema. |
| `VAL-007` | El superior no es el propio subordinado | Una persona no puede estar a su propio cargo. |
| `VAL-008` | Motivo obligatorio y no vacío tras recortar los extremos | Debe indicar el motivo del cambio. |
| `VAL-009` | El subordinado no es el propio actor | No es posible cambiar el superior de su propia cuenta. |

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-SP-412` | El sistema pone a un agente a cargo de un director, y la consulta posterior devuelve esa relación |
| `CA-SP-413` | El sistema rechaza poner a un agente a cargo de otro agente, e informa qué rol debería portar el superior |
| `CA-SP-414` | El sistema rechaza poner a un agente directamente a cargo de un manager, saltándose el nivel intermedio |
| `CA-SP-415` | Cambiar de superior **cierra** la asignación anterior con fecha de fin y **conserva** su fila, quedando una sola vigente |
| `CA-SP-416` | Repetir la asignación del mismo superior no produce error, no registra evento y no parte el historial en dos tramos |
| `CA-SP-417` | El sistema rechaza asignar superior a quien no porta ningún rol comercial |
| `CA-SP-418` | El sistema rechaza asignar superior a quien porta el rol comercial de mayor rango |
| `CA-SP-419` | El sistema rechaza un superior inactivo, bloqueado o eliminado |
| `CA-SP-420` | El sistema rechaza poner a una persona a su propio cargo |
| `CA-SP-421` | Mover a alguien con equipo propio **arrastra su equipo**: sus subordinados conservan su superior y cambia solo de quién depende la rama |
| `CA-SP-422` | Los permisos efectivos del subordinado, del superior nuevo y del anterior son los mismos antes y después de la operación |
| `CA-SP-423` | La operación no otorga a nadie acceso a datos que no tuviera antes |
| `CA-SP-424` | El sistema registra el cambio en la auditoría de cambios, con ambos superiores identificables y con el motivo declarado |
| `CA-SP-426` | El sistema rechaza el cambio sin motivo, o con un motivo que queda vacío al recortar los extremos, **antes** de ejecutarlo |
| `CA-SP-427` | El sistema rechaza que el actor cambie el superior de su propia cuenta |
| `CA-SP-428` | El sistema **no** emite evento en la auditoría de seguridad por esta operación |
| `CA-SP-429` | La asignación queda vigente desde el momento de ejecutarse, y la operación no admite declarar otra fecha de inicio |
| `CA-SP-425` | El sistema rechaza la operación a un actor sin el permiso de asignación de superior comercial |

## 13. Casos límite

- **Reasignación concurrente de la misma persona:** las dos operaciones se serializan sobre el subordinado. Una cierra y abre; la otra ve el resultado de la primera. En ningún caso quedan dos asignaciones vigentes, y la garantía se declara en el esquema, no solo en el dominio.
- **El superior nuevo es subordinado del actual:** se admite. Es una inversión de rama legítima —un director pasa a depender de quien antes dependía de él tras un ascenso— y el orden de mando ya la valida: si ambos portan los roles correctos, la relación es válida.
- **Ciclo entre personas:** imposible mientras `RN-SP-020` se cumpla, porque la cadena de personas hereda la aciclicidad de la cadena de roles (`RN-SEG-006`). El único ciclo que las clasificaciones no impiden es el de longitud uno, que `EX-005` rechaza.
- **El subordinado porta dos roles comerciales:** la regla se evalúa contra el de **mayor rango**. Portar además uno inferior no cambia quién puede estar a su cargo.
- **El subordinado está inactivo o bloqueado:** se admite reasignarlo. Reorganizar la estructura de alguien que hoy no entra al sistema es legítimo —y necesario antes de darle la baja definitiva—, y no le concede nada.
- **El superior asciende y sus subordinados no:** esta operación no lo detecta, porque solo mira a las dos personas que recibe. Es el hueco conocido de `RN-SP-020`, que se valida al escribir y no de forma continua; la corrección es ejecutar esta misma operación sobre cada subordinado afectado.
- **Cambio acordado a mitad de mes y registrado tarde:** el sistema lo fecha cuando se ejecuta, no cuando se decidió. Es la consecuencia aceptada de no admitir fecha declarada, y quien recalcule una liquidación afectada tendrá que corregirla fuera del sistema. Se aceptó el 22-08-2026 porque hoy ningún requerimiento consume esas fechas; el módulo de comisiones puede reabrirlo cuando exista.
- **Motivo repetido o poco informativo:** el sistema exige contenido, no calidad. Un motivo de una palabra pasa la validación. Es un límite conocido: la alternativa —longitud mínima— se rechazó en `RF-SP-029` por producir relleno en lugar de información.
- **Persona con equipo a la que se le retira el rol comercial:** no ocurre por aquí. `RN-SP-022` lo rechaza en `RF-SP-028`, `RF-SP-029` y `RF-SP-031` hasta que el equipo se reasigne, una persona a la vez, con esta funcionalidad.

## 14. Preguntas abiertas

Ninguna. Las cuatro se resolvieron el 22-08-2026, antes de aprobar la especificación. Dos de ellas alcanzan a otros documentos: la segunda enmienda `RN-SP-017`, y la cuarta deja anotada una condición de disparo que devolverá esta spec a su compuerta.

| # | Pregunta | Resolución |
|---|---|---|
| 1 | ¿El cambio de superior exige declarar un **motivo**? | **Sí, obligatorio** (`EX-007`, `VAL-008`). El Art. V.13 solo lo exige en las eliminaciones, de modo que es una exigencia adicional y deliberada. La razón es que este historial no describe una configuración, sino **a quién se atribuía la producción de alguien en cada momento**: cuando sustente el reparto de comisiones, un tramo sin explicación será un agujero en plena disputa de dinero, meses después de que nadie recuerde el porqué. Se aceptó a cambio el riesgo de motivos pobres, que §13 declara: el sistema exige contenido, no calidad, igual que resolvió `RF-SP-029` al rechazar una longitud mínima que solo produciría relleno |
| 2 | ¿Puede el actor cambiar **su propio** superior? | **No** (`EX-008`, `VAL-009`). Es el único caso en que quien ejecuta tiene interés directo en el resultado, porque de la posición en la estructura cuelga la atribución de su propia producción. **Enmienda `RN-SP-017`**, que hasta ahora solo alcanzaba a eliminar y a cambiar el estado de la propia cuenta. La consecuencia asumida es que un administrador que además sea vendedor necesita a otro administrador para corregir su propia ficha; es el mismo precio que ya paga con `RF-SP-028` y `RF-SP-029` |
| 3 | ¿La asignación admite **fecha de inicio** distinta del momento de ejecutarla? | **No: siempre rige desde que se ejecuta** (`CA-SP-429`). Admitir fechas declaradas obliga a especificar solapamientos, huecos entre tramos y correcciones retroactivas sobre periodos ya liquidados, y **hoy ningún requerimiento consume esas fechas**: sería diseño sin nadie a quien servir. La consecuencia queda escrita en §13 —un cambio acordado a mitad de mes se registra tarde— y el módulo de comisiones podrá reabrirlo cuando exista y sepa qué necesita |
| 4 | ¿Emite además evento en la auditoría de **seguridad**? | **No, solo en la de cambios** (`CA-SP-428`). La auditoría de seguridad existe para el control de acceso, y esta operación no concede ni retira privilegio alguno; registrarla allí diluiría su señal durante meses. **Con condición de disparo declarada**, en §7: el día que el modelo de alcance de datos (**D-22**) haga depender de esta relación qué ve cada quien, mover a alguien de rama sí cambiará su acceso efectivo, y esta especificación deberá volver a su compuerta para añadirlo |
