# SPEC — `RF-CM-023` Devolver a su lote pendiente una comisión retirada

| Campo | Valor |
|---|---|
| Requerimiento | `RF-CM-023` |
| Módulo | `CM` — Comisiones |
| Versión | 0.3.0 |
| Estado | **Aprobada** |
| Autor | Responsable técnico |
| Aprobada por | Responsable del proyecto |
| Fecha de aprobación | 30-09-2026 |
| Enmendada el | 07-10-2026 — **se retira `EX-005`**: ya no hay comisiones revertidas (`RN-CM-047`) |
| Enmendada el | 07-10-2026 — **el abierto que se queda sin comisiones se borra** (`RN-CM-052`): `CA-CM-366`; se retira `CA-CM-284` |

!!! info "Qué va en este documento"

    **Qué debe pasar, y por qué.** Nada más.

    **Prueba de pertenencia:** si un cambio de tecnología lo invalidaría, no pertenece aquí — va a `plan.md`. No se nombran tablas, clases, endpoints ni librerías.

---

## 1. Objetivo

Que Finanzas **deshaga un retiro hecho por error**, devolviendo la comisión al lote pendiente del que salió para que se pague con él.

---

## 2. Contexto

**Es la otra mitad de `RF-CM-022`**, y el responsable del proyecto la pidió con esas palabras: «si por error lo mando al otro lote y aún no se ha pagado, poder enviarlo al pendiente» (`requirements/cm.md` v0.26.0 §5.10).

**Es deshacer, no mover.** Por eso solo se devuelve **al lote de donde salió**, y solo mientras las dos mitades del retiro sigan como quedaron: el origen **pendiente** y la comisión **en un lote abierto**. Si el abierto se cerró, la comisión está en un lote que alguien puede estar revisando para pagar, y llevarla a otro pendiente ya no es corregir un error sino decidir otra cosa.

### 2.1 Lo que este requerimiento decide

| Decisión | Qué se decidió |
|---|---|
| **Solo a su origen** | El pendiente del que se retiró, y ningún otro (`RN-CM-046`) |
| **Solo mientras el origen esté pendiente** | Un lote pagado no cambia |
| **Solo desde un lote abierto** | Si el abierto se cerró, la comisión se queda en su nuevo pendiente |
| **Nunca lo nacido en el abierto** | Una comisión que no se retiró de ningún sitio no tiene a dónde volver |
| **Se entra por el pendiente** | Es el lote que Finanzas está revisando, y el que lista lo que se le retiró |

---

## 3. Actores

| Actor | Rol en esta funcionalidad |
|---|---|
| Finanzas o administración | Devuelve la comisión (`commission-batches:return-commission`) |

---

## 4. Alcance

### 4.1 Incluye

- Devolver una comisión retirada a su lote pendiente de origen, desde el lote abierto en que está.
- Ajustar los dos totales, y borrar la anotación del origen.
- Auditarlo.

### 4.2 No incluye

- **Enviar a un pendiente una comisión que no salió de él.**
- **Devolver a un lote pagado**, o desde uno que ya no está abierto.
- **Devolver varias en una petición.**

---

## 5. Reglas de negocio aplicables

| Regla | Cómo aplica |
|---|---|
| `RN-CM-046` | A dónde se devuelve y mientras qué |
| `RN-CM-029` | La comisión vuelve como salió: su importe no cambia |
| `RN-CM-048` | ~~Un pendiente que se había quedado sin comisiones vivas vuelve a poder pagarse~~ Desde el 07-10-2026 un pendiente vaciado se borra, y no queda nada a lo que devolver |
| `RN-CM-052` | El abierto que se queda sin comisiones al devolver se borra |

---

## 6. Datos

### 6.1 Entrada

| Dato | Descripción |
|---|---|
| Lote | El lote pendiente de origen, por su identificador |
| Comisión | La comisión retirada, por su identificador |

### 6.2 Salida

**El lote pendiente como queda**, en la forma del detalle de `RF-CM-010`: con la comisión otra vez entre las suyas, fuera de las retiradas, y el total restituido.

---

## 7. Precondiciones y postcondiciones

| Tipo | Condición |
|---|---|
| Precondición | El lote existe y está pendiente; la comisión se retiró de él, está en un lote abierto y está viva |
| Postcondición | La comisión está en el lote pendiente, sin lote de origen anotado; el total del abierto bajó y el del pendiente subió en su importe; queda auditado |

---

## 8. Flujo principal

1. Finanzas pide devolver una comisión a un lote.
2. Se comprueba que el lote está pendiente, que la comisión salió de él, que sigue en un lote abierto y que está viva.
3. La comisión vuelve al pendiente, sin anotación de origen.
4. Se ajustan los dos totales.
5. Se audita y se devuelve el lote pendiente.

---

## 9. Flujos alternativos

### FA-001 — El abierto se queda sin comisiones vivas

~~Sigue abierto, y el cierre **no lo cierra** mientras siga vacío (`RN-CM-048`): lo siguiente que devengue esa persona entra en él.~~

**Desde el 07-10-2026 se borra** (`RN-CM-052`), en el mismo acto, y queda auditado. Lo siguiente que devengue esa persona **abre otro**, como si no hubiera tenido ninguno.

### FA-002 — Devolver y cerrar a la vez

Uno espera al otro. Si el cierre llega antes, el abierto pasa a pendiente con la comisión dentro, y la devolución **responde conflicto**; si la devolución llega antes, el cierre no la encuentra en el abierto.

### FA-003 — Devolver y pagar el origen a la vez

Si el pago llega antes, la devolución encuentra el origen pagado y responde conflicto; si la devolución llega antes, el pago abona el total **con** la comisión devuelta.

---

## 10. Excepciones

| ID | Situación | Resultado |
|---|---|---|
| `EX-001` | El lote no existe | No encontrado |
| `EX-002` | La comisión no existe, o **no se retiró de este lote** —incluida la que nunca se retiró de ninguno— | No encontrado |
| `EX-003` | El lote está **pagado** | Conflicto: «El lote ya está pagado: lo retirado se queda donde está» |
| `EX-004` | La comisión **ya no está en un lote abierto** —el abierto se cerró— | Conflicto: «El lote en que está la comisión ya se cerró» |
| ~~`EX-005`~~ | ~~La comisión está **revertida**~~ | **Retirada el 07-10-2026**: la comisión de una línea cuyo vendedor se corrige se borra, y deja de figurar entre las retiradas (`RN-CM-047`) |

**El lote de origen no puede estar abierto**: se retiró de él porque estaba pendiente, y un lote no vuelve a abrirse (`RN-CM-030`).

---

## 11. Validaciones

| ID | Validación |
|---|---|
| `VAL-001` | Los dos identificadores bien formados |

---

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-CM-282` | Devolver una comisión retirada la pone otra vez en su lote **pendiente** de origen, **sin** anotación de origen y con su importe intacto; el total del abierto **baja** y el del pendiente **sube** en su importe |
| `CA-CM-283` | La respuesta es el pendiente como queda, con la comisión **entre las suyas** y fuera de las retiradas |
| ~~`CA-CM-284`~~ | ~~Un pendiente que se había quedado **sin comisiones vivas** vuelve a poder **pagarse** tras la devolución~~ **Retirado el 07-10-2026**: un pendiente vaciado por retiros se borra, y lo retirado de él pierde su origen (`RN-CM-052`, `RF-CM-022` `CA-CM-364`) |
| `CA-CM-285` | Devolver a un lote **del que no salió** —o una comisión **nacida en el abierto**— responde no encontrado, y nada se mueve |
| `CA-CM-286` | Si el origen **está pagado**, responde conflicto y la comisión se queda en el abierto |
| `CA-CM-287` | Si el abierto **se cerró** después del retiro, responde conflicto y la comisión se queda en su nuevo pendiente; una **revertida**, conflicto |
| `CA-CM-288` | **Devolver y cerrar** a la vez: la comisión acaba en el pendiente de origen **o** en el lote que el cierre cerró, nunca en los dos ni en ninguno, y los totales cuadran con las comisiones vivas |
| `CA-CM-289` | Sin `commission-batches:return-commission`, se rechaza —**también con `withdraw-commission`**—; queda **auditado**, con la comisión, los dos lotes y el importe |
| `CA-CM-366` | Devolver la **única** comisión de un abierto **lo borra**, auditado como eliminación física; la respuesta es el pendiente, y lo siguiente que devenga esa persona **abre un abierto nuevo** (07-10-2026). Un abierto al que le queda alguna comisión no se borra |

---

## 13. Casos límite

| Caso | Comportamiento |
|---|---|
| El abierto al que fue la comisión se abrió **con** su retiro | Tras devolverla queda vacío y **se borra** (`FA-001`) |
| Se retiró de un pendiente y otro pendiente de la misma persona se pagó entre medias | Da igual: solo cuenta el de origen |
| La persona del lote está eliminada | Se devuelve igual |

---

## 14. Preguntas abiertas

Ninguna.

---

## 15. Control de cambios

| Versión | Fecha | Cambio | Autor |
|---|---|---|---|
| 0.1.0 | 30-09-2026 | Primera versión ([`requirements/cm.md`](../../../requirements/cm.md) v0.26.0 §5.10, `RN-CM-046`), por decisión del responsable del proyecto: lo retirado por error **vuelve a su pendiente de origen** mientras no se haya pagado y siga en el lote abierto. Criterios `CA-CM-282` a `CA-CM-289`. | Responsable del proyecto |
| 0.3.0 | 07-10-2026 | **El abierto que se queda sin comisiones se borra** ([`requirements/cm.md`](../../../requirements/cm.md) v0.40.0, `RN-CM-052`), por decisión del responsable del proyecto. `FA-001` cambia; nace `CA-CM-366`, y **se retira `CA-CM-284`**: un pendiente vaciado por retiros ya no existe para devolverle nada. | Responsable del proyecto |
| 0.2.0 | 07-10-2026 | **Se retira `EX-005`** ([`requirements/cm.md`](../../../requirements/cm.md) v0.34.0, `RN-CM-047` enmendada): una comisión retirada cuya línea cambia de vendedor se borra, de modo que ya no está entre las retiradas de su pendiente y responde no encontrado. **`CA-CM-287` se lee** sin su última mitad. | Responsable del proyecto |
