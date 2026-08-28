# SPEC — `RF-CM-004` Retirar una tarifa de comisión

| Campo | Valor |
|---|---|
| Requerimiento | `RF-CM-004` |
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

Sacar del sistema una tarifa que **no debió existir**, sin que desaparezca lo que explica.

## 2. Contexto

**Retirar y cerrar la vigencia son cosas distintas, y confundirlas destruye el historial.**

**Cerrar la vigencia** (`RF-CM-003`) dice «esta comisión rigió hasta tal día». La tarifa fue correcta y sigue explicando lo que se pagó mientras estuvo vigente.

**Retirar** dice otra cosa: «esta tarifa **no debió declararse**» — se creó sobre el rol equivocado, se duplicó por error, se registró una excepción para quien no correspondía. Es el reconocimiento de un error, no el final natural de una comisión.

De ahí que **se exija motivo**, que el Art. V.13 impone a toda eliminación. Y de ahí que la fila **no desaparezca**: si una liquidación pasada se apoyó en ella, borrarla dejaría un pago sin explicación.

## 3. Actores

| Actor | Rol en esta funcionalidad |
|---|---|
| Administrador | Retira una tarifa declarada por error, y deja escrito por qué |

## 4. Alcance

### 4.1 Incluye

- Retirar una tarifa **con motivo obligatorio**.
- Dejarla fuera de la resolución y, por omisión, fuera del listado.
- **Conservar la fila** y su motivo, con la instantánea de lo retirado.

### 4.2 No incluye

- **Cerrar la vigencia de una tarifa correcta**, que es `RF-CM-003`. Ver §2.
- **Deshacer el retiro.** Ver §14.
- **Borrar la fila.** La eliminación es lógica (Art. V.13).

## 5. Reglas de negocio aplicables

| ID | Regla | Origen |
|---|---|---|
| `RN-CM-005` | La tarifa no desaparece | `requirements/cm.md` §5.1 |
| `RN-CM-006` | Dos tarifas del mismo caso no se solapan en el tiempo | `requirements/cm.md` §5.1 |

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción | Restricción de negocio |
|---|---|---|---|
| Tarifa | Sí | Cuál se retira | Debe existir y no estar ya retirada |
| Motivo | **Sí** | Por qué se retira | Texto no vacío, con longitud acotada (Art. V.13) |

### 6.2 Salida

| Dato | Descripción |
|---|---|
| Confirmación | Que la tarifa quedó retirada, y desde cuándo |

## 7. Precondiciones y postcondiciones

**Precondiciones**

- El actor está autenticado y posee el permiso de eliminación de tarifas de comisión.
- La tarifa existe y no está retirada.

**Postcondiciones**

- La tarifa queda retirada y **deja de participar en la resolución** de `RF-CM-005`.
- El registro de eliminación contiene **quién la retiró, cuándo, por qué**, y la instantánea de lo retirado.
- **Los días que ocupaba quedan libres**, de modo que puede declararse otra tarifa que los cubra (`RN-CM-006`).
- **La vigencia no se toca.** Ver §13.

## 8. Flujo principal

1. El actor indica la tarifa y el motivo.
2. El sistema comprueba que la tarifa existe y no está retirada.
3. El sistema comprueba que el motivo viene y no está en blanco.
4. El sistema retira la tarifa y registra la eliminación con su motivo y la instantánea.
5. El sistema confirma el retiro.

## 9. Flujos alternativos

Ninguno.

## 10. Excepciones

### EX-001 — La tarifa no existe

**Condición:** la tarifa indicada no existe.
**Respuesta del sistema:** rechaza la operación como recurso no encontrado.

### EX-002 — La tarifa ya estaba retirada

**Condición:** la tarifa ya fue retirada antes.
**Respuesta del sistema:** rechaza la operación. **No es idempotente a propósito**: retirar dos veces con dos motivos distintos dejaría el segundo escrito sobre un hecho que ocurrió antes y por otra razón, y el registro pasaría a mentir sobre por qué se retiró.

### EX-003 — Motivo ausente o en blanco

**Condición:** no se envía motivo, o solo espacios.
**Respuesta del sistema:** rechaza la operación y **no retira nada**. El motivo es la mitad del valor del registro de eliminación.

## 11. Validaciones

| ID | Regla | Mensaje |
|---|---|---|
| `VAL-007` | Motivo obligatorio | El motivo del retiro es obligatorio. |
| `VAL-008` | Longitud del motivo | El motivo no puede exceder la longitud admitida. |

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-CM-031` | El sistema retira la tarifa con su motivo y **conserva la fila** |
| `CA-CM-032` | El registro de eliminación contiene quién, cuándo, por qué y la instantánea de lo retirado |
| `CA-CM-033` | Una tarifa retirada **deja de participar** en la resolución de `RF-CM-005` |
| `CA-CM-034` | Una tarifa retirada **no aparece** en el listado por omisión, y aparece marcada cuando se piden las retiradas |
| `CA-CM-035` | El sistema rechaza el retiro sin motivo, o con un motivo en blanco, y no retira nada |
| `CA-CM-036` | El sistema rechaza retirar dos veces la misma tarifa |
| `CA-CM-037` | Tras el retiro, **se admite** declarar otra tarifa que cubra los días que ocupaba |
| `CA-CM-038` | El retiro **no modifica la vigencia** de la tarifa retirada |

## 13. Casos límite

- **La vigencia no se toca al retirar**, y es deliberado: el registro de eliminación debe poder decir **qué periodo cubría** la tarifa que se retiró. Cerrarla «de paso» haría que todas las retiradas dijeran lo mismo, y ese dato dejaría de significar nada — la salvaguarda habría destruido la evidencia que protege. Es el mismo criterio con el que `RF-PM-006` no toca el estado de un producto al retirarlo.
- **Retirar la tarifa que rige hoy:** se admite, y a partir de ese momento el caso queda sin comisión hasta que se declare otra. El sistema **no avisa**, por lo mismo que en `RF-CM-003`: dejar un caso sin tarifa es una decisión legítima.
- **Retirar una tarifa ya vencida:** se admite. El error puede descubrirse después de que la tarifa dejara de regir, y el registro de eliminación es el sitio donde eso queda dicho.
- **Retirar la única tarifa de un rol:** se admite. Ninguna regla exige que un rol vendedor tenga comisión declarada.

## 14. Preguntas abiertas

| # | Pregunta | Responsable | Estado |
|---|---|---|---|
| — | Ninguna | — | — |

**Deshacer un retiro no es una pregunta abierta: es una decisión tomada.** No se admite, por lo mismo que en el resto del sistema — un retiro con motivo es un hecho registrado, y deshacerlo dejaría el motivo apuntando a algo que ya no ocurrió. Si la tarifa vuelve a hacer falta, se declara de nuevo con `RF-CM-001`.

## 15. Control de cambios

| Versión | Fecha | Cambio | Responsable |
|---|---|---|---|
| 0.1.0 | 28-08-2026 | Redacción inicial, sin preguntas abiertas. | Responsable técnico |
