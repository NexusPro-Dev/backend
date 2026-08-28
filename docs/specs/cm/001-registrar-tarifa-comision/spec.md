# SPEC — `RF-CM-001` Registrar una tarifa de comisión

| Campo | Valor |
|---|---|
| Requerimiento | `RF-CM-001` |
| Módulo | `CM` — Comisiones |
| Estado | **En revisión** |
| Autor | Responsable técnico |
| Aprobada por | Pendiente |
| Fecha de aprobación | Pendiente |

!!! info "Qué va en este documento"

    **Qué debe pasar, y por qué.** Nada más.

    **Prueba de pertenencia:** si un cambio de tecnología lo invalidaría, no pertenece aquí — va a `plan.md`. No se nombran tablas, clases, endpoints ni librerías.

    Debe poder leerlo alguien del negocio y entenderlo completo. Es la primera compuerta del Art. I.6: hasta que no esté aprobada, no se escribe `plan.md`.

---

## 1. Objetivo

Declarar cuánto gana un rol vendedor por vender, para que exista en el sistema el dato sobre el que una liquidación futura podrá calcular.

## 2. Contexto

Hoy el sistema sabe **qué se vende** y **quién vende**, y **no sabe cuánto se le paga a quien vende**. Ese dato no está en ningún sitio. Este es el primer requerimiento del módulo y el que crea el objeto del que dependerán después el cálculo y la liquidación.

**Una tarifa se declara en cuatro grados de precisión**, y la ausencia es la que da el alcance: sin persona, rige para todos los del rol; sin producto, para todo el catálogo. No hay ninguna casilla que diga «para todos», porque podría contradecir a lo demás y esa contradicción no la detectaría nadie.

**Y toda tarifa rige durante un periodo.** Declara desde cuándo, y opcionalmente hasta cuándo. Eso convierte al conjunto de tarifas en el **historial de lo que se pagó** y no solo en la foto de lo que se paga hoy, y permite **programar** un cambio con antelación en lugar de tener que hacerlo el día que entra en vigor.

## 3. Actores

| Actor | Rol en esta funcionalidad |
|---|---|
| Administrador | Declara la tarifa: a qué rol corresponde, sobre qué producto y a qué persona se acota, qué porcentaje paga y durante cuánto tiempo rige |

## 4. Alcance

### 4.1 Incluye

- Registrar la tarifa **por omisión de un rol**: sin producto y sin persona.
- Acotarla a un **producto**, a una **persona**, o a los dos.
- Declarar el **porcentaje**, que puede ser **cero**.
- Declarar **desde cuándo rige** y, si se sabe, **hasta cuándo**.
- Verificar que el rol es de tipo vendedor, que el producto y la persona existen, que la persona porta ese rol, y que la tarifa **no se solapa** con otra del mismo caso.
- Dejar constancia del alta en la auditoría de cambios.

### 4.2 No incluye

- **Corregir una tarifa ya registrada**, que es `RF-CM-003`, ni retirarla, que es `RF-CM-004`.
- **Cambiar la comisión a partir de una fecha.** No es una operación: son dos —cerrar la vigente y registrar esta—, y la primera es `RF-CM-003`. Ver §13.
- **Calcular ni liquidar comisiones.** No existe ninguna venta sobre la que calcular (`requirements/cm.md` §1.4).
- **Decidir a qué vendedor se le atribuye una venta.** Es una decisión de la venta, no de la tarifa.

## 5. Reglas de negocio aplicables

| ID | Regla | Origen |
|---|---|---|
| `RN-CM-001` | Solo comisionan los roles vendedores | `requirements/cm.md` §5.1 |
| `RN-CM-002` | El producto acotado debe existir | `requirements/cm.md` §5.1 |
| `RN-CM-003` | La persona de una excepción debe existir y portar el rol | `requirements/cm.md` §5.1 |
| `RN-CM-006` | Dos tarifas del mismo caso no se solapan en el tiempo | `requirements/cm.md` §5.1 |
| `RN-CM-007` | El porcentaje va de cero a cien | `requirements/cm.md` §5.1 |
| `RN-CM-009` | Toda tarifa declara desde cuándo rige | `requirements/cm.md` §5.1 |
| `RN-CM-010` | No se configura lo que ya no se vende | `requirements/cm.md` §5.1 |

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción | Restricción de negocio |
|---|---|---|---|
| Rol | Sí | A qué rol corresponde la comisión | Debe existir y ser de **tipo vendedor** (`RN-CM-001`) |
| Producto | No | Producto al que se acota la tarifa | Si se envía, debe existir y **no estar retirado** (`RN-CM-002`, `RN-CM-010`). **Sin él, la tarifa rige para todo el catálogo** |
| Persona | No | Persona a la que se acota la tarifa | Si se envía, debe existir y **portar el rol de la tarifa** (`RN-CM-003`). **Sin ella, la tarifa rige para todos los del rol** |
| Porcentaje | Sí | Qué proporción de la venta gana | De **cero a cien** (`RN-CM-007`). El cero significa «esto no comisiona», y **no es lo mismo que no declarar la tarifa** |
| Inicio de vigencia | Sí | Desde qué día rige | Una fecha. Puede ser pasada o futura (§13) |
| Fin de vigencia | No | Hasta qué día rige, inclusive | Si se envía, no puede ser anterior al inicio. **Sin él, la tarifa rige indefinidamente** (`RN-CM-009`) |

### 6.2 Salida

| Dato | Descripción |
|---|---|
| Tarifa | La tarifa registrada, con su identificador, su porcentaje y su vigencia |
| Rol resuelto | El código y el nombre del rol, y no solo su identificador |
| Producto resuelto | Cuando la tarifa se acota a uno: su código y su nombre. **Vacío y presente** cuando rige para todo el catálogo |
| Persona resuelta | Cuando la tarifa se acota a una: su nombre de usuario y su nombre. **Vacía y presente** cuando rige para todos los del rol |

## 7. Precondiciones y postcondiciones

**Precondiciones**

- El actor está autenticado y posee el permiso de creación de tarifas de comisión.
- Existe al menos un rol de tipo vendedor.

**Postcondiciones**

- La tarifa queda registrada y **rige desde el día que declara**, que puede no ser hoy.
- La auditoría de cambios contiene un evento de creación con el estado inicial completo de la tarifa.
- **Ningún día del caso declarado queda cubierto por dos tarifas** (`RN-CM-006`).

## 8. Flujo principal

1. El actor envía el rol, el porcentaje y el inicio de vigencia, y opcionalmente el producto, la persona y el fin de vigencia.
2. El sistema comprueba que el porcentaje está entre cero y cien, y que el fin de vigencia —si se envió— no es anterior al inicio.
3. El sistema comprueba que el rol existe y que es de tipo vendedor.
4. Si se envió producto, el sistema comprueba que existe y que no está retirado.
5. Si se envió persona, el sistema comprueba que existe y que porta el rol de la tarifa.
6. El sistema comprueba que ningún día del periodo declarado está ya cubierto por otra tarifa viva del mismo rol, el mismo producto y la misma persona.
7. El sistema registra la tarifa y emite el evento de auditoría de creación.
8. El sistema devuelve la tarifa registrada, con el rol, el producto y la persona resueltos.

## 9. Flujos alternativos

### FA-001 — Tarifa por omisión del rol

**Cuándo ocurre:** no se envía producto ni persona.

1. Se omiten los pasos 4 y 5: no hay nada que validar.
2. La tarifa rige para **cualquiera con ese rol y por cualquier producto**, y es la que se aplicará cuando no exista ninguna más específica.
3. El resto del flujo es idéntico.

### FA-002 — Excepción de una persona

**Cuándo ocurre:** se envía persona.

1. El sistema **exige que esa persona porte el rol de la tarifa** (`RN-CM-003`).
2. La tarifa se antepone a la del rol para esa persona, según la precedencia que fija `RF-CM-005`.
3. El resto del flujo es idéntico.

### FA-003 — Primera tarifa del sistema

**Cuándo ocurre:** no hay ninguna tarifa registrada.

1. La comprobación de solapamiento no tiene con qué chocar.
2. La tarifa queda registrada con normalidad. **No es un caso especial**, y se enumera para que quede escrito que no lo es.

## 10. Excepciones

### EX-001 — El rol no es de tipo vendedor

**Condición:** el rol existe, y es funcionario o consumidor.
**Respuesta del sistema:** rechaza el alta diciendo que solo los roles de tipo vendedor pueden llevar comisión, y no registra nada.

### EX-002 — El rol no existe

**Condición:** el rol indicado no existe.
**Respuesta del sistema:** rechaza el alta diciendo que el rol indicado no existe. **No es un «no encontrado»**: lo que no existe es un dato que el actor envió, no el recurso que estaba pidiendo.

### EX-003 — El producto no existe

**Condición:** se envió un producto y no existe.
**Respuesta del sistema:** rechaza el alta diciendo que el producto indicado no existe.

### EX-004 — El producto está retirado

**Condición:** se envió un producto que existe y ha sido retirado del catálogo.
**Respuesta del sistema:** rechaza el alta diciendo que no se pueden declarar tarifas sobre un producto retirado (`RN-CM-010`). Se distingue de `EX-003`: quien escribió bien el identificador no debe buscar el error donde no está.

### EX-005 — La persona no existe

**Condición:** se envió una persona y no existe.
**Respuesta del sistema:** rechaza el alta diciendo que la persona indicada no existe.

### EX-006 — La persona no porta el rol

**Condición:** se envió una persona que existe y que **no tiene asignado** el rol de la tarifa.
**Respuesta del sistema:** rechaza el alta diciendo que esa persona no porta ese rol. **Es la excepción que evita el defecto silencioso**: sin ella, la tarifa quedaría registrada y **no se aplicaría nunca**, sin que nada fallara.

### EX-007 — La tarifa se solapa con otra

**Condición:** ya existe una tarifa viva del mismo rol, el mismo producto y la misma persona que cubre alguno de los días declarados.
**Respuesta del sistema:** rechaza el alta diciendo **con cuál** se solapa y **qué periodo** ocupa esa otra, y no registra nada. Sin ese dato, quien recibe el rechazo no sabe qué fecha elegir.

## 11. Validaciones

| ID | Regla | Mensaje |
|---|---|---|
| `VAL-001` | Rol obligatorio | El rol de la tarifa es obligatorio. |
| `VAL-002` | Porcentaje obligatorio | El porcentaje es obligatorio. |
| `VAL-003` | Rango del porcentaje | El porcentaje debe estar entre cero y cien. |
| `VAL-004` | Inicio de vigencia obligatorio | El inicio de vigencia es obligatorio. |
| `VAL-005` | Orden de la vigencia | El fin de vigencia no puede ser anterior a su inicio. |
| `VAL-006` | Formato de fecha | La fecha debe expresarse en el formato de fecha admitido. |

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-CM-001` | El sistema registra la tarifa por omisión de un rol vendedor, sin producto y sin persona, y devuelve el rol resuelto |
| `CA-CM-002` | El sistema registra una tarifa acotada a un producto, y devuelve el producto resuelto |
| `CA-CM-003` | El sistema registra una excepción de una persona que porta el rol |
| `CA-CM-004` | El sistema registra una tarifa con porcentaje **cero**, y la distingue de no tener tarifa |
| `CA-CM-005` | El sistema registra una tarifa **sin fin de vigencia** y la trata como vigente indefinidamente |
| `CA-CM-006` | El sistema rechaza una tarifa sobre un rol que no es de tipo vendedor |
| `CA-CM-007` | El sistema rechaza una tarifa sobre un producto retirado, y lo distingue de un producto inexistente |
| `CA-CM-008` | El sistema rechaza una excepción sobre una persona que **no porta** el rol de la tarifa |
| `CA-CM-009` | El sistema rechaza una tarifa que se solapa con otra viva del mismo caso, e indica con cuál y en qué periodo |
| `CA-CM-010` | El sistema **admite** dos tarifas consecutivas del mismo caso que no comparten ningún día |
| `CA-CM-011` | El sistema rechaza un porcentaje negativo o mayor que cien |
| `CA-CM-012` | El sistema rechaza un fin de vigencia anterior a su inicio |
| `CA-CM-013` | Dos tarifas del mismo rol y periodo pero **de productos distintos** conviven sin conflicto |

## 13. Casos límite

- **Inicio de vigencia en el pasado:** se admite. Declarar el día 5 una tarifa que rige desde el día 1 es un caso real —la decisión se toma antes de registrarse— y prohibirlo obligaría a mentir en la fecha. Lo que **no** hace es reescribir lo ya liquidado: eso lo garantiza la liquidación guardando el porcentaje que aplicó (`RN-CM-008`).
- **Inicio de vigencia en el futuro:** se admite, y es la mitad del valor de tener vigencia — permite programar el cambio con antelación.
- **Tarifa que empieza el mismo día en que otra termina:** es solapamiento y se rechaza. El fin de vigencia es **inclusive**: si una termina el día 31, la siguiente empieza el 1.
- **Porcentaje cero frente a ausencia de tarifa:** son cosas distintas y el sistema no las confunde. Cero es una respuesta —no comisiona—; la ausencia es que nadie lo declaró, y quien resuelve decide qué hacer con cada una (`RF-CM-005`).
- **Dos excepciones de personas distintas sobre el mismo rol y producto:** conviven. La combinación que no admite solapamiento incluye a la persona.
- **La persona pierde el rol después de registrarse la tarifa:** la tarifa **permanece**. `RN-CM-003` se comprueba al registrar, no continuamente: retirar un rol no es motivo para borrar el historial de lo que esa persona ganó mientras lo tuvo. Lo que ocurre es que la tarifa deja de aplicarse, porque la resolución parte del rol vigente.
- **Un producto se retira después de registrarse la tarifa:** la tarifa permanece, por lo mismo. `RN-CM-010` prohíbe declarar, no conservar.

## 14. Preguntas abiertas

| # | Pregunta | Responsable | Estado |
|---|---|---|---|
| — | Ninguna | — | — |

Las cuatro que había —el porcentaje cero, la vigencia, el alcance de la resolución y qué ocurre con el producto retirado— las resolvió el responsable del proyecto el 28-08-2026, antes de redactarse esta especificación, y están recogidas en `requirements/cm.md` §8.

**Queda declarado un condicionante que no es una pregunta de este requerimiento:** quién puede ver las tarifas de quién depende de **D-22**, abierta. Esta especificación se escribe con **alcance global explícito** —quien tiene el permiso, opera sobre todas—, y lo que puede tener que cambiar el día que D-22 se cierre es `RF-CM-002`, no esta.

## 15. Control de cambios

| Versión | Fecha | Cambio | Responsable |
|---|---|---|---|
| 0.1.0 | 28-08-2026 | Redacción inicial, sin preguntas abiertas. | Responsable técnico |
