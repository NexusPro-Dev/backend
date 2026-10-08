# SPEC — `RF-CM-027` Borrar los lotes vacíos

| Campo | Valor |
|---|---|
| Requerimiento | `RF-CM-027` |
| Módulo | `CM` — Comisiones |
| Versión | 0.1.0 |
| Estado | **Aprobada** |
| Autor | Responsable técnico |
| Aprobada por | Responsable del proyecto |
| Fecha de aprobación | 08-10-2026 |

!!! info "Qué va en este documento"

    **Qué debe pasar, y por qué.** Nada más.

    **Prueba de pertenencia:** si un cambio de tecnología lo invalidaría, no pertenece aquí — va a `plan.md`. No se nombran tablas, clases, endpoints ni librerías.

---

## 1. Objetivo

Que Finanzas o administración **quite de una vez los lotes que no tienen nada que pagar**, cuando lo decida, y sepa cuáles quitó.

---

## 2. Contexto

**Un lote sin pagar puede quedarse vacío** desde el 30-09-2026: se le retiran todas sus comisiones (`RF-CM-022`), se le devuelve lo único que tenía (`RF-CM-023`) o se borra la cadena de la única línea que contenía (`RF-CM-024`). **El 07-10-2026** el responsable del proyecto decidió que un lote así se borrara en el mismo acto que lo vaciaba (`RN-CM-052`). **Un día después lo cambió**: «no elimines los lotes vacíos aún, creemos un endpoint para eliminar los lotes vacíos» (`requirements/cm.md` v0.41.0 §5.10, «Cuarta enmienda»).

### 2.1 Lo que este requerimiento decide

| Decisión | Qué se decidió | Lo que se descartó |
|---|---|---|
| **Cuándo se borra** | **Cuando alguien lo pide**; ninguna otra operación borra un lote | *En el acto que lo vacía* —lo del 07-10-2026—: un pendiente vaciado por retiros dejaba de admitir devoluciones |
| **Cuáles** | **Todos** los lotes abiertos y pendientes que en ese momento no tienen ninguna comisión, de todas las personas y monedas | *Uno por su identificador* y *una lista elegida*: el responsable del proyecto eligió todos a la vez —se le ofrecieron las tres— |
| **Los vacíos de antes** | **Entran**: no importa cuándo ni cómo se vaciaron | *Solo los vaciados desde el 07-10-2026* — no hay nada que distinga un vacío de otro |
| **Si uno recibe una comisión mientras se borra** | **No se borra**: decide lo que tiene en el momento de borrarlo | *Decidir con una lista tomada antes* — borraría un lote con una comisión recién llegada |

---

## 3. Actores

| Actor | Rol en esta funcionalidad |
|---|---|
| Finanzas o administración | Pide el borrado (`commission-batches:delete-empty`) |

---

## 4. Alcance

### 4.1 Incluye

- Borrar todos los lotes abiertos y pendientes sin comisiones.
- Decir cuáles se borraron, con lo que era cada uno.

### 4.2 No incluye

- **Borrar un lote con comisiones**, aunque su total sea cero.
- **Borrar un lote pagado**: nunca está vacío, y lo pagado no se borra (`RN-CM-029`).
- **Elegir qué lotes**: son todos los vacíos.
- **Hacerlo solo**, programado: lo pide una persona.

---

## 5. Reglas de negocio aplicables

| Regla | Cómo aplica |
|---|---|
| `RN-CM-052` | Qué se borra, cuándo y cómo queda constancia |
| `RN-CM-048` | Hasta que se borra, un lote vacío no se cierra ni se paga |
| `RN-CM-046` | Lo retirado de un pendiente que se borra pierde su origen y ya no vuelve |
| `RN-CM-029` | Lo pagado no se borra |
| `RN-CM-033` | Lo siguiente que devengue la persona de un abierto borrado abre otro |

---

## 6. Datos

### 6.1 Entrada

Ninguna.

### 6.2 Salida

**Los lotes borrados**, cada uno con su identificador, su código, su persona, su moneda, el estado que tenía y su periodo; y **cuántos** fueron.

---

## 7. Precondiciones y postcondiciones

| Tipo | Condición |
|---|---|
| Precondición | Ninguna |
| Postcondición | No queda ningún lote abierto ni pendiente sin comisiones que existiera al empezar; cada uno borrado queda auditado con lo que era; los demás lotes, como estaban |

---

## 8. Flujo principal

1. Finanzas pide borrar los lotes vacíos.
2. Se buscan los lotes abiertos y pendientes sin ninguna comisión.
3. Cada uno se borra **si sigue vacío** en el momento de borrarlo, y se audita.
4. Se responde con los borrados.

---

## 9. Flujos alternativos

### FA-001 — No hay ninguno

La respuesta dice que no se borró ninguno. **No es un error**.

### FA-002 — Un lote recibe una comisión mientras se borra

Si la comisión llega antes, el lote **no se borra** y no sale en la respuesta. Si el borrado llega antes, la comisión **va a otro lote** de esa persona —un abierto nuevo, si no tiene—, como si el borrado hubiera ocurrido un instante antes. **Nunca se pierde una comisión.**

### FA-003 — Un pendiente vaciado por retiros

Se borra, y lo que se retiró de él **pierde su origen**: se queda en el abierto donde está y ya no se puede devolver (`RN-CM-046`).

---

## 10. Excepciones

Ninguna propia.

---

## 11. Validaciones

Ninguna: no hay entrada.

---

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-CM-368` | Borrar quita **todos** los lotes abiertos y pendientes sin comisiones, de varias personas; los que tienen alguna comisión —también de importe cero— y los pagados **no cambian** |
| `CA-CM-369` | La respuesta trae **cada lote borrado** con su identificador, código, persona, moneda, estado y periodo, y **cuántos** fueron; después, ninguno aparece en el listado ni en su detalle |
| `CA-CM-370` | **Sin lotes vacíos**, responde éxito, sin ninguno y con cero |
| `CA-CM-371` | Cada borrado queda **auditado como eliminación física**, con lo que era el lote |
| `CA-CM-372` | Lo **retirado de un pendiente borrado** pierde su origen: no sale como devolvible, y devolverlo responde no encontrado. **Antes del borrado sí se podía devolver** |
| `CA-CM-373` | Tras borrar el **abierto** vacío de una persona, lo siguiente que devenga abre **otro**; y el cierre sigue funcionando |
| `CA-CM-374` | **Borrar y devengar a la vez** sobre el abierto vacío de una persona: la comisión acaba **siempre** en un lote, el borrado o uno nuevo, nunca en ninguno |
| `CA-CM-375` | Sin `commission-batches:delete-empty`, se rechaza —**también con `commission-batches:settle`**— y no se borra nada |

---

## 13. Casos límite

| Caso | Comportamiento |
|---|---|
| Un lote vacío de una persona eliminada | Se borra igual |
| Dos borrados a la vez | Cada lote se borra una vez; uno lo cuenta y el otro no |
| Un pendiente vacío al que se le devolvía algo mientras tanto | Si la devolución llega antes, ya no está vacío y no se borra; si llega después, la comisión no tiene adónde volver y la devolución responde no encontrado |
| Un periodo con su lote borrado | Queda un hueco; los demás lotes no se estiran |

---

## 14. Preguntas abiertas

Ninguna.

---

## 15. Control de cambios

| Versión | Fecha | Cambio | Autor |
|---|---|---|---|
| 0.1.0 | 08-10-2026 | Primera versión ([`requirements/cm.md`](../../../requirements/cm.md) v0.41.0, `RN-CM-052` enmendada), por decisión del responsable del proyecto: el lote vacío ya no se borra en el acto; se borran **todos los vacíos a la vez**, a petición. Criterios `CA-CM-368` a `CA-CM-375`. | Responsable del proyecto |
