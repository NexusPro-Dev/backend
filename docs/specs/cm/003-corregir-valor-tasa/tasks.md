# TASKS — `RF-CM-003` Corregir el valor de una tasa

| Campo | Valor |
|---|---|
| Requerimiento | `RF-CM-003` |
| Plan | [`plan.md`](plan.md), aprobado el 02-09-2026 |
| Versión | 0.3.0 |
| Estado | **En revisión** |
| Autor | Responsable técnico |
| Aprobadas por | Pendiente |
| Fecha de aprobación | Pendiente |
| Issue | Pendiente de crear |
| Rama | `feature/flujos-de-pm-y-cm` (`T-01`–`T-14`) · `feature/comision-en-valor-fijo` (`T-15`–`T-22`) |

!!! info "Qué va en este documento"

    **En qué pasos se construye** lo que `plan.md` decidió, con su dependencia y su verificación.

!!! warning "Esta lista tiene dos mitades, y solo una registra"

    **`T-01` a `T-14` están hechas antes que este documento.** El código se rehízo el 02-09-2026 y esa parte viene detrás: **no planifica, registra.**

    **`T-15` a `T-22` se escribieron ANTES de construirse**, y **una de ellas modifica una tarea ya `Hecha`** —`T-02`, la comparación—, que es la primera vez que eso pasa en el módulo. Ver §2.

    La tercera compuerta del Art. I.6 sigue pendiente para las dos, y por eso el documento está `En revisión`.

---

## 1. Tareas

**Estados:** `Pendiente` · `En curso` · `Hecha` · `Bloqueada`.

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-01` | `CommissionRate.update(...)` con un solo campo, que **devuelve qué cambió de verdad** | `RF-CM-001` · `T-04` | Una corrección que no cambia nada devuelve el mapa vacío | **Hecha el 02-09-2026** |
| `T-02` | Comparar los porcentajes **por valor y no por escala** | `T-01` | `10.0000` sobre `10.00` no produce cambio | **Hecha el 02-09-2026** |
| `T-03` | La marca de modificación **solo se mueve si hubo cambio** | `T-01` | Petición idéntica, misma marca | **Hecha el 02-09-2026** |
| `T-04` | Petición con `roleId` **declarado a propósito** para poder rechazarlo | — | Enviarlo da `VAL-009`, no «propiedad desconocida» | **Hecha el 02-09-2026** |
| `T-05` | `UpdateCommissionRateService`: rechaza inmutables y petición vacía **antes de buscar nada** | `T-01`, `T-04` | Con un identificador inexistente y el rol enviado, gana `VAL-009` | **Hecha el 02-09-2026** |
| `T-06` | **Retirar el bloqueo y la traducción del solapamiento** que la v0.1.0 necesitaba | `T-05` | El caso de uso no llama a ningún bloqueo | **Hecha el 02-09-2026** |
| `T-07` | Conservar el **volcado explícito** antes de auditar, ahora por el orden y no por la traducción | `T-05` | El comentario del código dice cuál de las dos razones vale hoy | **Hecha el 02-09-2026** |
| `T-08` | Registro de auditoría con `before` y `after`, **solo si hubo cambio** | `T-05` | Una petición que no cambia nada no escribe ninguna fila | **Hecha el 02-09-2026** |
| `T-09` | Releer la tasa para devolver el rol resuelto y sus productos asociados | `T-05` | Una sentencia, no una llamada por fila | **Hecha el 02-09-2026** |
| `T-10` | `PATCH /api/v1/commission-rates/{id}` | `T-05`, `T-09` | `200`, `400`, `403`, `404`. **Ningún `409`** | **Hecha el 02-09-2026** |
| `T-11` | Pruebas de los criterios de `spec.md` §12 | `T-10` | `CA-CM-021` a `CA-CM-028` | **Hecha el 02-09-2026** |
| `T-12` | **Prueba de que la auditoría es la única copia** del valor anterior | `T-11` | `CA-CM-022`, en sus **dos mitades**: no está en la tabla, sí en el registro | **Hecha el 02-09-2026** |
| `T-13` | Documentación OpenAPI, **con el aviso de que esta operación borra el pasado** | `T-10` | El contrato publicado lo dice en la descripción | **Hecha el 02-09-2026** |
| `T-14` | Actualizar la matriz de `docs/requirements.md` | `T-11` | La fila de `RF-CM-003` refleja el estado | **Hecha el 02-09-2026** |

### 1.1 El valor fijo (`cm.md` v0.7.0)

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-15` | **La igualdad de `CommissionValue`: tipo por identidad, cifra por `compareTo`**, escrita a mano | `RF-CM-001` · `T-20` | `CA-CM-024` **y** `CA-CM-091` a la vez. Ninguna sola basta | **Hecha el 02-09-2026** |
| `T-16` | **Rehacer `T-02`**: `update(...)` compara el valor entero, no la cifra | `T-15`, `T-01` | Un cambio de forma con la misma cifra **produce cambio** | **Hecha el 02-09-2026** |
| `T-17` | La petición lleva `rateType` con su valor **como unidad**, no como tres campos parcheables | `T-04` | `fixedAmount` suelto da `VAL-011`, no un parcheo a medias | **Hecha el 02-09-2026** |
| `T-18` | El mapa de cambios trata el valor como **un** campo | `T-16` | El `before` dice `PORCENTAJE 10`, no `10` | **Hecha el 02-09-2026** |
| `T-19` | `VAL-003` se aplica **solo si la forma es porcentaje** | `T-17` | `150` rechazado en una forma y aceptado en la otra | **Hecha el 02-09-2026** |
| `T-20` | La respuesta devuelve forma y valor, con la cuenta de asociados | `T-17` | Igual que el alta (`RF-CM-001` `T-24`) | **Hecha el 02-09-2026** |
| `T-21` | Pruebas de los criterios nuevos de `spec.md` §12, **`CA-CM-091` también en unitaria** | `T-16`, `T-18`, `T-20` | `CA-CM-090` a `CA-CM-095` | **Hecha el 02-09-2026** |
| `T-22` | OpenAPI: **los dos regímenes del cuerpo**, y que la forma también se corrige | `T-20` | El contrato explica por qué `fixedAmount` suelto se rechaza | **Hecha el 02-09-2026** |

## 2. Orden de ejecución

**`T-06` es una tarea de quitar, y merece figurar como tarea.** Se retiran el bloqueo consultivo, la consulta de solapamiento y la traducción de la violación del motor — tres piezas que existían por un defecto medido el 28-08-2026 y que **dejan de tener objeto** cuando la columna que las causaba desaparece. Dejarlas «por si acaso» habría dejado código que parece defender una invariante inexistente, que es peor que no tenerlo.

!!! danger "`T-16` deshace una tarea `Hecha`, y hay que decirlo así en lugar de editarla en el sitio"

    `T-02` dice «comparar los porcentajes **por valor y no por escala**» y está `Hecha` desde el 02-09-2026. **Era correcta y deja de bastar**: con dos formas, comparar la cifra iguala `10 %` con `10` de importe fijo.

    Se deja `T-02` como está y se añade `T-16` encima, en vez de reescribirla. El motivo es que **`T-02` sigue siendo la mitad de la verdad** —su verificación, `CA-CM-024`, tiene que seguir pasando— y una tarea reescrita perdería la constancia de que el requisito nuevo **no sustituye al viejo sino que se le suma en dirección contraria**.

    Es la primera vez en el módulo que una tarea `Hecha` se corrige, y `T-15` existe precisamente para que la corrección no rompa lo que `T-02` protegía.

**`T-19` parece un detalle de validación y es una regla.** `VAL-003` acota el porcentaje a cien porque el negocio conoce ese número; **al importe no lo acota nada** (`RN-CM-018`). Aplicar `VAL-003` a las dos formas «por simetría» inventaría un tope de cien unidades de dinero, que no significa nada.

**`T-12` no es una prueba más de `T-11`, y por eso va aparte.** Verificar que el registro de auditoría se escribió no demuestra nada: lo que hay que demostrar es que **el valor anterior ya no está en ningún otro sitio**. La prueba mira las dos mitades, y es la que da sentido a `RN-CM-008`.

**`T-13` lleva un aviso que ningún otro contrato del proyecto lleva.** La descripción publicada dice que corregir **borra el porcentaje anterior**, porque quien consuma la API desde fuera no tiene forma de deducirlo de la forma del recurso.

## 3. Cobertura de los criterios de aceptación

| Criterio | Tareas |
|---|---|
| `CA-CM-021` | `T-05`, `T-09`, `T-10`, `T-11` |
| `CA-CM-022` | `T-08`, `T-12` |
| `CA-CM-023` | `T-03`, `T-11` |
| `CA-CM-024` | `T-02`, `T-11` |
| `CA-CM-025` | `T-04`, `T-05`, `T-11` |
| `CA-CM-026` | `T-01`, `T-11` |
| `CA-CM-027` | `T-05`, `T-11` |
| `CA-CM-028` | `T-05`, `T-11` |
| `CA-CM-024` (revisado) | `T-02`, `T-15`, `T-21` |
| `CA-CM-090` | `T-17`, `T-20`, `T-21` |
| `CA-CM-091` | `T-15`, `T-16`, `T-21` |
| `CA-CM-092` | `T-18`, `T-21` |
| `CA-CM-093` | `T-17`, `T-21` |
| `CA-CM-094` | `T-19`, `T-21` |
| `CA-CM-095` | `T-16`, `T-21` |

**`CA-CM-024` aparece dos veces y no es un error de la tabla.** Lo cubría `T-02` sola; desde `T-15` lo cubren las dos, porque la comparación que lo satisface es ahora la misma que satisface a `CA-CM-091` y **cualquiera de las dos se puede romper arreglando la otra**.

## 4. Bloqueos

Ninguno para construir. **`T-15` depende de `RF-CM-001` `T-20`**, que es donde nace `CommissionValue`: dentro del bloque, `RF-CM-001` va primero.

**Uno declarado que no se puede levantar desde aquí:** mientras no exista el módulo de liquidación, `RN-CM-008` no la cumple nadie y **corregir borra el pasado sin rastro**. No bloquea este requerimiento; bloquea la confianza en lo que produce.

**Y la deuda creció con esta versión**: lo que la liquidación tendría que copiar pasó de un número a **tres cosas** —forma, valor y moneda—, y la tercera no está en ninguna tabla de `CM` ni la devuelve la resolución (decisión del responsable del proyecto, 02-09-2026). `spec.md` §5 lo declara.

## 5. Definición de terminado

- Las catorce primeras tareas `Hecha` con su verificación pasando. `./mvnw clean verify` en verde. **Comprobado el 02-09-2026**: 278 unitarias y 876 de integración.
- **Las ocho del valor fijo, `Hecha`**, con `CA-CM-024` y `CA-CM-091` **pasando a la vez** — que es la condición que ninguna de las dos comprueba por su cuenta. **Comprobado el 02-09-2026**: 287 unitarias y 902 de integración, suite entera en verde.
- La matriz y el contrato publicado al día.
