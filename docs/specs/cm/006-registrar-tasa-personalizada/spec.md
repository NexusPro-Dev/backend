# SPEC — `RF-CM-006` Registrar la tasa personalizada de una persona

| Campo | Valor |
|---|---|
| Requerimiento | `RF-CM-006` |
| Módulo | `CM` — Comisiones |
| Versión | 0.1.0 |
| Estado | **Aprobada** |
| Autor | Responsable técnico |
| Aprobada por | Responsable del proyecto |
| Fecha de aprobación | 02-09-2026 |

!!! info "Qué va en este documento"

    **Qué debe pasar, y por qué.** Nada más.

    **Prueba de pertenencia:** si un cambio de tecnología lo invalidaría, no pertenece aquí — va a `plan.md`.

!!! danger "Este requerimiento se construyó ANTES de tener especificación"

    Es una excepción al Art. I.1, registrada en `requirements/cm.md` §4 y en la matriz. El motivo: el módulo se rehizo entero el 02-09-2026 y **sin esta pieza no había forma de probarlo de punta a punta**.

    Queda dicho porque cambia cómo leerla: **no propone, describe**.

---

## 1. Objetivo

Declarar que **una persona concreta gana un porcentaje distinto** del que le correspondería por su rol, y **desde cuándo**.

## 2. Contexto

El catálogo por rol dice lo que gana un `AGENTE`. Pero se negocia con personas, no con roles: alguien entra con condiciones mejores, alguien las gana por resultados. **Eso es una excepción, y no un grado más del catálogo.**

**Se separó del alta de rol el 01-09-2026**, y no por gusto: **no son la misma operación**. Una escribe en un catálogo sin fechas y no rige hasta que se la asocia; la otra registra una excepción **con vigencia**, que **rige desde el primer día** y **sin asociarse a nada**. Hasta entonces eran un solo endpoint con campos opcionales, y eso obligaba a validaciones que dependían de qué campo había llegado.

**Y esta tasa no lleva rol**, por decisión del responsable del proyecto: es de la persona y punto. Lo que eso cuesta está en §13, y es la parte de este requerimiento que más fácil se subestima.

**Es la única pieza del módulo con vigencia**, y por tanto **el único historial que le queda**. Sus filas cerradas dicen qué ganó esa persona y hasta cuándo. El catálogo por rol perdió esa capacidad.

## 3. Actores

| Actor | Rol en esta funcionalidad |
|---|---|
| Administrador | Declara la excepción de una persona, con su porcentaje y su vigencia |

## 4. Alcance

### 4.1 Incluye

- Registrar la tasa personalizada de una persona, con **porcentaje** y **vigencia**.
- Verificar que la persona existe.
- Garantizar que **ningún día queda cubierto por dos tasas vivas de la misma persona**.
- **Corregir** el porcentaje y el fin de vigencia de una tasa ya registrada.
- **Retirar** una tasa con motivo obligatorio.
- Dejar constancia de todo ello en la auditoría.

**La corrección y el retiro están aquí y no en `RF-CM-003` y `RF-CM-004`**, aunque sean la misma operación conceptual. El motivo es que **se comportan distinto**: aquí corregir **no borra el pasado** —hay vigencia, y cambiar lo que se gana a partir de una fecha es cerrar la vigente y abrir otra—, y el retiro **sí tiene una vigencia que podría cerrarse «de paso»** y no debe. Describirlas junto a las de rol habría obligado a un documento lleno de «salvo en el caso de».

### 4.2 No incluye

- **Acotarla a un producto.** Una tasa personalizada **no se acota** (`RN-CM-014`): quien la tiene gana lo mismo venda lo que venda.
- **Exigir que la persona sea vendedora.** Se consideró y **se descartó al quitarle el rol**. Ver §13.
- **Resolver cuál se aplica.** Es `RF-CM-005`.
- **Calcular ni liquidar.**

## 5. Reglas de negocio aplicables

| ID | Regla | Origen |
|---|---|---|
| `RN-CM-004` | La personalizada gana siempre | `requirements/cm.md` §5.1 |
| `RN-CM-005` | La tasa no desaparece | `requirements/cm.md` §5.1 |
| `RN-CM-006` | Una sola tasa personalizada vigente por persona | `requirements/cm.md` §5.1 |
| `RN-CM-007` | El porcentaje va de cero a cien | `requirements/cm.md` §5.1 |
| `RN-CM-009` | Toda tasa personalizada declara desde cuándo rige | `requirements/cm.md` §5.1 |
| `RN-CM-014` | Solo las tasas de rol se asocian a productos | `requirements/cm.md` §5.1 |

**`RN-CM-006` es la que sostiene a `RN-CM-004`**: con dos tasas cubriendo el mismo día, la resolución dejaría de ser determinista.

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción | Restricción de negocio |
|---|---|---|---|
| Persona | Sí | De quién es la tasa | Debe existir. **No se le exige portar rol vendedor** |
| Porcentaje | Sí | Qué gana | De **cero a cien** (`RN-CM-007`) |
| Inicio de vigencia | Sí | Desde qué día rige | Una fecha. Puede ser pasada o futura |
| Fin de vigencia | No | Hasta qué día rige, **inclusive** | No puede ser anterior al inicio. **Sin él, rige indefinidamente** (`RN-CM-009`) |

**No hay rol ni producto**, y su ausencia no significa nada: **no son campos de esta tasa.** Es distinto de la ausencia en el modelo anterior, donde sí significaba «para todos».

### 6.2 Salida

| Dato | Descripción |
|---|---|
| Tasa | Identificador, porcentaje y vigencia |
| Persona resuelta | Nombre de usuario y nombre |

**El fin de vigencia viaja vacío y presente** cuando la tasa rige indefinidamente: un campo que desaparece del resultado es indistinguible de uno que el cliente no conoce, y aquí significa algo.

## 7. Precondiciones y postcondiciones

**Precondiciones**

- El actor está autenticado y posee el permiso correspondiente.
- La persona existe.

**Postcondiciones**

- La tasa queda registrada y **rige desde el día que declara**, que puede no ser hoy.
- **Rige sin necesidad de nada más**, al revés que una tasa de rol.
- **Ningún día queda cubierto por dos tasas vivas de esa persona** (`RN-CM-006`).
- La auditoría contiene el evento correspondiente.

## 8. Flujo principal

**Registrar**

1. El actor envía la persona, el porcentaje y el inicio de vigencia, y opcionalmente el fin.
2. El sistema comprueba que el porcentaje está entre cero y cien, y que el fin —si se envió— no es anterior al inicio.
3. El sistema comprueba que la persona existe.
4. El sistema comprueba que **ningún día del periodo declarado está ya cubierto** por otra tasa viva de esa persona.
5. El sistema registra la tasa y emite el evento de auditoría de creación.
6. El sistema devuelve la tasa, con la persona resuelta.

**Corregir**

1. El actor envía el porcentaje corregido, el fin de vigencia, o los dos.
2. El sistema rechaza la petición si trae la persona o el inicio de vigencia, o si no informa nada.
3. El sistema comprueba que la tasa existe y está viva, y que el resultado **no se solapa** con otra.
4. El sistema aplica lo que cambió de verdad y emite el evento con el antes y el después.

**Retirar**

1. El actor envía el motivo.
2. El sistema lo verifica, comprueba que la tasa existe y no está ya retirada.
3. El sistema toma la instantánea **con la vigencia intacta**, marca el retiro y emite el evento de eliminación.

## 9. Flujos alternativos

### FA-001 — Varias tasas consecutivas

**Cuándo ocurre:** una termina el 31 y la siguiente empieza el 1.

1. Las dos conviven. **Son el historial**, y es el único que el módulo conserva.
2. `RN-CM-006` prohíbe el solapamiento, no la sucesión.

### FA-002 — Cambiar lo que gana alguien a partir de una fecha

**Cuándo ocurre:** se quiere subir el porcentaje desde el mes que viene.

1. Son **dos operaciones**: cerrar la vigente poniéndole fin, y registrar otra desde el día siguiente.
2. **Y aquí sí se puede**, al revés que con una tasa de rol (`RF-CM-003` §13). Es lo que la vigencia compra.

### FA-003 — Quitar el fin de vigencia

**Cuándo ocurre:** se corrige enviando el fin explícitamente vacío.

1. Es **una orden que se cumple**: la tasa vuelve a regir indefinidamente.
2. Se trata **al revés que el porcentaje**, que vaciarlo se rechaza. Son dos campos y dos comportamientos opuestos ante el mismo gesto.

### FA-004 — La persona no porta rol vendedor

**Cuándo ocurre:** se declara la tasa de alguien que no vende, o que dejó de vender.

1. **Se admite.** No hay ninguna comprobación que lo impida.
2. **Y esa tasa rige**: `RF-CM-005` `FA-003` la resuelve y la persona cobra. Ver §13.

### FA-005 — Retirar libera los días

**Cuándo ocurre:** se retira una tasa y se quiere declarar otra que cubra su periodo.

1. Se admite. Los días que ocupaba **quedan libres**.
2. Es lo que distingue una tasa **retirada** de una **vencida**: la vencida sigue explicando lo que se pagó, la retirada no debió existir.

## 10. Excepciones

### EX-001 — La persona no existe

**Condición:** el identificador no corresponde a ninguna persona.
**Respuesta del sistema:** rechaza el alta diciendo que la persona indicada no existe. **No es un «no encontrado»**: lo que no existe es un dato que el actor envió.

### EX-002 — La tasa se solapa con otra

**Condición:** ya existe una tasa viva de esa persona que cubre alguno de los días declarados.
**Respuesta del sistema:** rechaza la operación diciendo que esa persona ya tiene una tasa viva en parte de ese periodo, y no registra nada.

### EX-003 — La tasa no existe o está retirada

**Condición:** al corregir, el identificador no corresponde a ninguna tasa viva.
**Respuesta del sistema:** rechaza la corrección diciendo que no existe.

### EX-004 — Ya estaba retirada

**Condición:** al retirar, la tasa existe y ya fue retirada.
**Respuesta del sistema:** rechaza el retiro diciendo que ya estaba retirada. **No es idempotente a propósito**: dos motivos distintos sobre un mismo hecho harían que el registro mienta.

### EX-005 — Se intenta cambiar la persona o el inicio de vigencia

**Condición:** la corrección trae alguno de los dos.
**Respuesta del sistema:** los rechaza diciendo que no se pueden corregir. **Se rechazan y no se ignoran.**

## 11. Validaciones

| ID | Regla | Mensaje |
|---|---|---|
| `VAL-001` | Persona obligatoria | La persona de la tasa es obligatoria. |
| `VAL-002` | Porcentaje obligatorio, y no se vacía | El porcentaje es obligatorio. / El porcentaje no puede vaciarse. |
| `VAL-003` | Rango del porcentaje | El porcentaje debe estar entre cero y cien. |
| `VAL-004` | Inicio de vigencia obligatorio | El inicio de vigencia es obligatorio. |
| `VAL-005` | Orden de la vigencia | El fin de vigencia no puede ser anterior a su inicio. |
| `VAL-007` | Motivo obligatorio | El motivo del retiro es obligatorio. |
| `VAL-008` | Longitud del motivo | El motivo no puede exceder 500 caracteres. |
| `VAL-009` | Campos no corregibles | La persona y el inicio de vigencia de una tasa personalizada no se pueden corregir. |
| `VAL-010` | Petición vacía | Debe enviarse al menos un campo corregible. |

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-CM-051` | Registra la tasa de una persona, con la persona resuelta y **sin rol ni producto** |
| `CA-CM-052` | El fin de vigencia ausente viaja **vacío y presente**, y significa «indefinidamente» |
| `CA-CM-053` | **Admite a quien no porta rol vendedor**, y esa tasa queda registrada |
| `CA-CM-054` | Rechaza dos tasas de la misma persona que **comparten algún día** |
| `CA-CM-055` | **El día de corte cuenta**: si una termina el 30, la siguiente no empieza el 30 |
| `CA-CM-056` | Admite **varias consecutivas**: son el historial |
| `CA-CM-057` | Dos **personas distintas** pueden solapar sin conflicto |
| `CA-CM-058` | Retirar **libera los días** que ocupaba |
| `CA-CM-059` | Retirar **no cierra la vigencia** |
| `CA-CM-060` | Corregir **vacía** el fin de vigencia, y la tasa vuelve a regir indefinidamente |
| `CA-CM-061` | Rechaza corregir la persona o el inicio de vigencia |
| `CA-CM-062` | Rechaza el fin anterior al inicio, la persona inexistente, y exige el permiso |

## 13. Casos límite

!!! danger "La protección que se perdió al quitarle el rol"

    El modelo anterior exigía que la persona **portara el rol** de la tarifa, y con ello impedía que una excepción **sobreviviera a que su titular dejara de vender**.

    Al quitarle el rol el 01-09-2026, esa protección **desapareció**. Una tasa personalizada sigue viva —y **sigue pagando**— aunque su titular pase a un rol que no comisiona, o se quede sin ninguno.

    `cm.md` §5.3 lo describía como que «no falla — se queda callada hasta que alguien la mira». **Construir `RF-CM-005` demostró que no se queda callada**: la resolución la consulta **antes** que el rol, de modo que responde y la persona cobra. La forma de cerrarla es **retirarla** o **ponerle fin de vigencia**, y las dos son actos deliberados que alguien tiene que acordarse de hacer.

- **Inicio de vigencia en el pasado:** se admite. Declarar el día 5 una tasa que rige desde el día 1 es un caso real y prohibirlo obligaría a mentir en la fecha.
- **Inicio de vigencia en el futuro:** se admite, y es la mitad del valor de tener vigencia — permite programar el cambio con antelación.
- **Una tasa que rige un solo día:** se admite. El fin igual al inicio es un periodo válido.
- **Porcentaje cero personalizado:** se admite, y significa que esa persona **no cobra nada**, ganando sobre lo que su rol tuviera asociado. Es una decisión legítima y drástica.
- **Dos altas simultáneas del mismo periodo:** una queda y la otra recibe el conflicto. Es la **única regla del módulo que dos peticiones simultáneas pueden burlar**, y por eso no se comprueba consultando antes de escribir.
- **La persona se elimina después de registrarse la tasa:** la tasa permanece, como todo el historial. No se aplica, porque no hay a quién.

## 14. Preguntas abiertas

| # | Pregunta | Responsable | Estado |
|---|---|---|---|
| — | Ninguna | — | — |

**Queda declarada una decisión que podría revisarse y que hoy está tomada:** que la tasa personalizada **no lleve rol**. Devolvérselo recuperaría la protección de §13 y volvería a atarla a que su titular siga vendiendo. El responsable del proyecto decidió lo contrario el 01-09-2026, sabiendo el coste, y aquí queda escrito qué habría que deshacer si algún día se quisiera revertir.

## 15. Control de cambios

| Versión | Fecha | Cambio | Responsable |
|---|---|---|---|
| 0.1.0 | 02-09-2026 | Redacción inicial, **después de construirse el requerimiento** — excepción al Art. I.1 declarada en cabecera. Recoge la pieza que nació al partir el alta en dos el 01-09-2026: la **excepción por persona**, con vigencia, sin rol y sin producto. §4.1 argumenta por qué su corrección y su retiro viven aquí y no en `RF-CM-003` y `RF-CM-004` — **se comportan distinto**, y describirlos juntos habría llenado aquellos documentos de «salvo en el caso de». §13 recoge, con la evidencia que dio construir `RF-CM-005`, **la protección que se perdió al quitarle el rol**: la tasa no «se queda callada» cuando su titular deja de vender, **sigue pagando**, y cerrarla exige un acto deliberado. §14 deja escrito qué habría que deshacer para revertir esa decisión. | Responsable técnico |
