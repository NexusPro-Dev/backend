# SPEC — `RF-CM-001` Registrar una tasa de comisión por rol

| Campo | Valor |
|---|---|
| Requerimiento | `RF-CM-001` |
| Módulo | `CM` — Comisiones |
| Versión | 0.2.0 |
| Estado | **Aprobada** |
| Autor | Responsable técnico |
| Aprobada por | Responsable del proyecto |
| Fecha de aprobación | 02-09-2026 |

!!! info "Qué va en este documento"

    **Qué debe pasar, y por qué.** Nada más.

    **Prueba de pertenencia:** si un cambio de tecnología lo invalidaría, no pertenece aquí — va a `plan.md`. No se nombran tablas, clases, endpoints ni librerías.

    Debe poder leerlo alguien del negocio y entenderlo completo. Es la primera compuerta del Art. I.6: hasta que no esté aprobada, no se escribe `plan.md`.

!!! warning "Esta especificación se reescribió DESPUÉS de construirse"

    La v0.1.0 describía el modelo de cuatro grados que el responsable del proyecto sustituyó el 01-09-2026 (`requirements/cm.md` v0.4.0). El código se rehízo el 02-09-2026 y **esta reescritura viene detrás**, que es el orden inverso al que manda el Art. I.6.

    Queda dicho porque cambia cómo leerla: **no propone, describe**. Lo que aquí se declara es lo que el responsable aprobó como modelo y lo que la suite verifica hoy.

---

## 1. Objetivo

Declarar **cuánto gana un rol vendedor** por vender, para que exista en el sistema el dato sobre el que una liquidación futura podrá calcular.

## 2. Contexto

Hoy el sistema sabe **qué se vende** y **quién vende**, y **no sabe cuánto se le paga a quien vende**. Este es el primer requerimiento del módulo y el que crea el objeto del que dependerán después el cálculo y la liquidación.

**Lo que esta operación registra es un catálogo, no una configuración aplicada.** Es el cambio de fondo respecto a la versión anterior de este documento, y es lo que más fácil se lee mal: hasta el 01-09-2026 una tasa sin producto regía sobre **todo el catálogo**, y ahora **no rige sobre nada** hasta que alguien la asocie a un producto (`RN-CM-012`, y la operación es `RF-CM-007`).

**La ausencia cambió de significado**: pasó de «todos» a «ninguno». Una tasa recién registrada **parece configurada y no paga nada a nadie**, y eso no falla — se descubriría liquidando. Por eso esta operación **devuelve explícitamente sobre cuántos productos rige**, que al registrarla es siempre **cero**: sin ese dato, la respuesta sería idéntica para una tasa que paga y para una que no.

**Varias tasas del mismo rol son legítimas.** El catálogo puede ofrecer «`AGENTE` 10 %» y «`AGENTE` 15 %» para asociarlas a productos distintos. Lo que no puede repetirse es un rol sobre el **mismo** producto, y eso no lo decide esta operación (`RN-CM-013`, y lo comprueba `RF-CM-007`).

## 3. Actores

| Actor | Rol en esta funcionalidad |
|---|---|
| Administrador | Declara la tasa: a qué rol corresponde y qué porcentaje paga |

## 4. Alcance

### 4.1 Incluye

- Registrar una tasa de comisión **para un rol de tipo vendedor**.
- Declarar el **porcentaje**, que puede ser **cero**.
- Verificar que el rol existe y que es de tipo vendedor.
- Informar de que la tasa **todavía no rige sobre ningún producto**.
- Dejar constancia del alta en la auditoría de cambios.

### 4.2 No incluye

- **Poner la tasa en vigor.** Eso es asociarla a un producto, y es `RF-CM-007`. Sin esa operación, lo que aquí se registra no paga nada a nadie.
- **Acotar la tasa a un producto o a una persona.** Ya no son campos de la tasa: el producto vive en la asociación (`RF-CM-007`) y la excepción por persona es otra cosa distinta (`RF-CM-006`).
- **Declarar desde cuándo rige.** Las tasas de rol **no tienen vigencia**; la única que la tiene es la personalizada. Ver §13.
- **Corregir una tasa ya registrada**, que es `RF-CM-003`, ni retirarla, que es `RF-CM-004`.
- **Calcular ni liquidar comisiones.** No existe ninguna venta sobre la que calcular (`requirements/cm.md` §1.4).

## 5. Reglas de negocio aplicables

| ID | Regla | Origen |
|---|---|---|
| `RN-CM-001` | Solo comisionan los roles vendedores | `requirements/cm.md` §5.1 |
| `RN-CM-007` | El porcentaje va de cero a cien | `requirements/cm.md` §5.1 |
| `RN-CM-012` | Una tasa de rol no rige hasta que se asocia | `requirements/cm.md` §5.1 |

**Tres reglas donde la versión anterior citaba siete**, y ninguna se relajó: **se mudaron**. `RN-CM-002` y `RN-CM-010` —el producto— viajaron a `RF-CM-007`; `RN-CM-003` desapareció con el rol de las personalizadas; `RN-CM-006` y `RN-CM-009` —el solapamiento y la vigencia— viajaron a `RF-CM-006`, que es lo único que conserva fechas.

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción | Restricción de negocio |
|---|---|---|---|
| Rol | Sí | A qué rol corresponde la comisión | Debe existir y ser de **tipo vendedor** (`RN-CM-001`) |
| Porcentaje | Sí | Qué proporción de la venta gana | De **cero a cien** (`RN-CM-007`). El cero significa «esto no comisiona», y **no es lo mismo que no declarar la tasa** |

**Dos campos, y ninguno opcional.** Es lo que queda del alta anterior, que tenía seis y dejaba que la ausencia de tres de ellos decidiera el alcance. Aquí no hay nada que deducir.

### 6.2 Salida

| Dato | Descripción |
|---|---|
| Tasa | La tasa registrada, con su identificador y su porcentaje |
| Rol resuelto | El código y el nombre del rol, y no solo su identificador |
| Productos asociados | **Cuántos productos hacen que esta tasa rija.** Al registrarla es **siempre cero**, y ese cero significa que no paga nada a nadie |

## 7. Precondiciones y postcondiciones

**Precondiciones**

- El actor está autenticado y posee el permiso de creación de tasas de comisión.
- Existe al menos un rol de tipo vendedor.

**Postcondiciones**

- La tasa queda registrada en el catálogo **y no rige sobre ningún producto**.
- La auditoría de cambios contiene un evento de creación con el estado inicial completo de la tasa.
- **Nadie cobra nada distinto por esta operación.** Es la postcondición que conviene leer dos veces: registrar una tasa no cambia ninguna comisión hasta que se la asocia.

## 8. Flujo principal

1. El actor envía el rol y el porcentaje.
2. El sistema comprueba que el porcentaje está entre cero y cien.
3. El sistema comprueba que el rol existe.
4. El sistema comprueba que el rol es de tipo vendedor.
5. El sistema registra la tasa y emite el evento de auditoría de creación.
6. El sistema devuelve la tasa registrada, con el rol resuelto y con **cero productos asociados**.

## 9. Flujos alternativos

### FA-001 — Segunda tasa del mismo rol

**Cuándo ocurre:** ya existe una tasa para ese rol.

1. **No hay ningún conflicto que comprobar**, y esa es la diferencia con el modelo anterior: sin vigencia, dos tasas del mismo rol no pueden solaparse en el tiempo porque no hay tiempo que solapar.
2. Las dos quedan en el catálogo, disponibles para asociarse a productos distintos.
3. El resto del flujo es idéntico.

### FA-002 — Porcentaje cero

**Cuándo ocurre:** se declara una tasa del cero por ciento.

1. Se registra con normalidad. Es la forma de declarar «este rol **no cobra** por el producto al que se asocie esta tasa».
2. **No es lo mismo que no tener tasa**, y `RF-CM-005` las distingue: el cero es una decisión, la ausencia es que nadie la tomó.

### FA-003 — Primera tasa del sistema

**Cuándo ocurre:** no hay ninguna tasa registrada.

1. La tasa queda registrada con normalidad. **No es un caso especial**, y se enumera para que quede escrito que no lo es.

## 10. Excepciones

### EX-001 — El rol no es de tipo vendedor

**Condición:** el rol existe, y es funcionario o consumidor.
**Respuesta del sistema:** rechaza el alta diciendo que solo los roles de tipo vendedor pueden llevar comisión, y no registra nada.

### EX-002 — El rol no existe

**Condición:** el rol indicado no existe.
**Respuesta del sistema:** rechaza el alta diciendo que el rol indicado no existe. **No es un «no encontrado»**: lo que no existe es un dato que el actor envió, no el recurso que estaba pidiendo. Se distingue de `EX-001` a propósito — quien escribió bien el identificador no debe buscar el error donde no está.

!!! note "Las cinco excepciones que esta especificación perdió"

    La v0.1.0 tipificaba siete. Las cinco que faltan —producto inexistente, producto retirado, persona inexistente, persona sin el rol y solapamiento— **no se eliminaron: se mudaron** con los campos que las causaban, a `RF-CM-007` y a `RF-CM-006`. Se dice aquí para que la reducción no se lea como una relajación de las comprobaciones.

## 11. Validaciones

| ID | Regla | Mensaje |
|---|---|---|
| `VAL-001` | Rol obligatorio | El rol de la tasa es obligatorio. |
| `VAL-002` | Porcentaje obligatorio | El porcentaje es obligatorio. |
| `VAL-003` | Rango del porcentaje | El porcentaje debe estar entre cero y cien. |

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-CM-001` | El sistema registra la tasa de un rol vendedor y devuelve el rol resuelto |
| `CA-CM-002` | La tasa recién registrada declara **cero productos asociados**, y ese cero significa que no rige sobre ninguno |
| `CA-CM-003` | La respuesta **no lleva** producto, persona, vigencia ni grado: son campos que el modelo ya no tiene |
| `CA-CM-004` | El sistema admite **varias tasas del mismo rol**, para asociarlas a productos distintos |
| `CA-CM-005` | El sistema registra una tasa con porcentaje **cero**, y la distingue de no tener tasa |
| `CA-CM-006` | El sistema rechaza una tasa sobre un rol que no es de tipo vendedor |
| `CA-CM-007` | El sistema rechaza una tasa sobre un rol inexistente, y **lo distingue** de un rol que no es vendedor |
| `CA-CM-008` | El sistema rechaza un porcentaje negativo o mayor que cien, y una petición sin rol |

## 13. Casos límite

- **Una tasa registrada y nunca asociada:** queda en el catálogo con su porcentaje y **no paga nada a nadie**. Es el caso límite más importante de este requerimiento y **no es un error del sistema**: es la consecuencia de `RN-CM-012`, y la respuesta lo dice devolviendo cero productos asociados.
- **Dos tasas idénticas del mismo rol y el mismo porcentaje:** se admiten. No hay ninguna regla que lo impida y prohibirlo obligaría a decidir si dos tasas iguales son un error o una preparación para asociarlas a productos distintos — decisión que el sistema no puede tomar por quien administra.
- **Cambiar el porcentaje a partir de una fecha:** **no se puede**, y hay que saberlo. Sin vigencia en las tasas de rol, la única operación disponible es corregir (`RF-CM-003`), que **reescribe lo que la tasa dijo siempre**. Registrar una tasa nueva y reasociar el producto es la alternativa, y tampoco conserva desde cuándo rigió cada una.
- **El rol se desactiva o se elimina después de registrarse la tasa:** la tasa **permanece**. `RN-CM-001` se comprueba al registrar, no continuamente: retirar un rol no es motivo para borrar el historial de lo que se pagó por él.
- **Porcentaje cero frente a ausencia de tasa:** son cosas distintas y el sistema no las confunde. Cero es una respuesta —no comisiona—; la ausencia es que nadie lo declaró, y quien resuelve decide qué hacer con cada una (`RF-CM-005`).

## 14. Preguntas abiertas

| # | Pregunta | Responsable | Estado |
|---|---|---|---|
| — | Ninguna | — | — |

**Queda declarado un condicionante que no es una pregunta de este requerimiento:** quién puede ver las tasas de quién depende de **D-22**, abierta. Esta especificación se escribe con **alcance global explícito** —quien tiene el permiso, opera sobre todas—, y lo que puede tener que cambiar el día que D-22 se cierre es `RF-CM-002`, no esta.

**Y queda declarada una pérdida aceptada a conciencia**, que no es una pregunta abierta sino una decisión tomada: sin vigencia, este catálogo **no conserva historial**. La defensa del pasado es que la liquidación copie el porcentaje que aplicó (`RN-CM-008`), y esa liquidación **no existe todavía**.

## 15. Control de cambios

| Versión | Fecha | Cambio | Responsable |
|---|---|---|---|
| 0.1.0 | 28-08-2026 | Redacción inicial, sin preguntas abiertas. | Responsable técnico |
| 0.2.0 | 02-09-2026 | **Reescrita sobre el modelo de `cm.md` v0.4.0**, y **después de construirse** el código — orden inverso al del Art. I.6, declarado en cabecera. El alta pierde cuatro de sus seis campos: el producto viaja a `RF-CM-007`, la persona a `RF-CM-006` y la vigencia desaparece del catálogo. Con ellos se van **cinco de las siete excepciones y cuatro de las siete reglas**, y §10 avisa de que **no se relajaron, se mudaron**. Lo que entra en su lugar es el cambio de fondo: **lo que esta operación registra ya no rige** (`RN-CM-012`), de modo que §7 declara como postcondición que **nadie cobra nada distinto** por ejecutarla, y §6.2 añade el número de productos asociados —siempre cero al registrar— porque sin él la respuesta sería idéntica para una tasa que paga y para una que no. §13 recoge la consecuencia que más incomoda: **cambiar un porcentaje a partir de una fecha ya no se puede**. | Responsable técnico |
