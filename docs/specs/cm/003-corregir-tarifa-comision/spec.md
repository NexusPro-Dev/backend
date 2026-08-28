# SPEC — `RF-CM-003` Corregir una tarifa de comisión

| Campo | Valor |
|---|---|
| Requerimiento | `RF-CM-003` |
| Módulo | `CM` — Comisiones |
| Estado | **Aprobada** |
| Autor | Responsable técnico |
| Aprobada por | Responsable del proyecto |
| Fecha de aprobación | 28-08-2026 |

!!! info "Qué va en este documento"

    **Qué debe pasar, y por qué.** Nada más.

    **Prueba de pertenencia:** si un cambio de tecnología lo invalidaría, no pertenece aquí — va a `plan.md`. No se nombran tablas, clases, endpoints ni librerías.

    Debe poder leerlo alguien del negocio y entenderlo completo. Es la primera compuerta del Art. I.6: hasta que no esté aprobada, no se escribe `plan.md`.

---

## 1. Objetivo

Arreglar lo que se declaró mal en una tarifa, y **cerrar su vigencia** cuando deja de regir.

## 2. Contexto

**Corregir y cambiar no son lo mismo, y esta es la distinción que sostiene todo el módulo.**

**Corregir** es arreglar un error: se puso un 12 donde iba un 2. La tarifa siempre debió decir 2, y al corregirla **se reescribe lo que esa tarifa dice que rigió**. Eso es lo que hace este requerimiento con el porcentaje.

**Cambiar la comisión a partir de una fecha** es otra cosa: lo que regía era correcto y deja de regir. Eso **no se hace corrigiendo**, se hace **cerrando la vigente y registrando otra** — dos operaciones, esta y `RF-CM-001`. Si se hiciera corrigiendo, el porcentaje anterior desaparecería y con él la explicación de lo que se pagó antes.

Por eso lo corregible son **dos cosas y no una**: el porcentaje, que arregla un error, y el **fin de vigencia**, que es la mitad de «esto deja de regir».

## 3. Actores

| Actor | Rol en esta funcionalidad |
|---|---|
| Administrador | Arregla un porcentaje mal declarado, o cierra la vigencia de una tarifa para poder registrar la siguiente |

## 4. Alcance

### 4.1 Incluye

- Corregir el **porcentaje** de una tarifa.
- Declarar o cambiar su **fin de vigencia**, y **quitarlo** para que vuelva a regir indefinidamente.
- Rechazar lo que no se puede corregir, **diciendo que no se puede** en lugar de ignorarlo.
- Dejar constancia en la auditoría de cambios de **qué cambió**, con su valor anterior y el nuevo.

### 4.2 No incluye

- **Cambiar el rol, el producto, la persona o el inicio de vigencia.** No corrigen la tarifa: crean otra. Ver §10, `EX-002`.
- **Retirar la tarifa**, que es `RF-CM-004`.
- **Registrar la siguiente tarifa** de la serie, que es `RF-CM-001`.

## 5. Reglas de negocio aplicables

| ID | Regla | Origen |
|---|---|---|
| `RN-CM-006` | Dos tarifas del mismo caso no se solapan en el tiempo | `requirements/cm.md` §5.1 |
| `RN-CM-007` | El porcentaje va de cero a cien | `requirements/cm.md` §5.1 |
| `RN-CM-008` | Corregir una tarifa no reescribe lo liquidado | `requirements/cm.md` §5.1 |
| `RN-CM-009` | Toda tarifa declara desde cuándo rige | `requirements/cm.md` §5.1 |

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción | Restricción de negocio |
|---|---|---|---|
| Tarifa | Sí | Cuál se corrige | Debe existir y **no estar retirada** |
| Porcentaje | No | El porcentaje nuevo | De cero a cien (`RN-CM-007`). **No admite vaciarse**: una tarifa sin porcentaje no significa nada |
| Fin de vigencia | No | Hasta qué día rige, inclusive | No puede ser anterior al inicio. **Sí admite vaciarse**, y vaciarlo significa «rige indefinidamente» |

**Se distingue el dato ausente del enviado vacío**, y de ahí salen dos comportamientos opuestos: quitar el fin de vigencia es una orden que se cumple; quitar el porcentaje se rechaza. Es la misma distinción que `RF-PM-004` necesitó para la descripción y el nombre.

### 6.2 Salida

| Dato | Descripción |
|---|---|
| Tarifa | La tarifa ya corregida, con el rol, el producto y la persona resueltos |

## 7. Precondiciones y postcondiciones

**Precondiciones**

- El actor está autenticado y posee el permiso de corrección de tarifas de comisión.
- La tarifa existe y no está retirada.

**Postcondiciones**

- La tarifa refleja lo corregido.
- La auditoría de cambios contiene un evento con **solo los campos que cambiaron**, cada uno con su valor anterior y el nuevo.
- **Una petición que no cambia nada no deja evento.**
- **Ningún día del caso queda cubierto por dos tarifas** después de la corrección (`RN-CM-006`).

## 8. Flujo principal

1. El actor indica la tarifa y los campos que quiere corregir.
2. El sistema comprueba que la tarifa existe y no está retirada.
3. El sistema comprueba que no se envió ninguno de los campos que no se pueden corregir.
4. El sistema comprueba que el porcentaje —si se envió— está entre cero y cien, y que el fin de vigencia —si se envió— no es anterior al inicio.
5. Si cambia el fin de vigencia, el sistema comprueba que el periodo resultante no se solapa con otra tarifa viva del mismo caso.
6. El sistema aplica lo enviado, deja intacto lo demás, y emite el evento de auditoría con lo que cambió.
7. El sistema devuelve la tarifa corregida.

## 9. Flujos alternativos

### FA-001 — Cerrar la vigencia para dar paso a la siguiente

**Cuándo ocurre:** se declara un fin de vigencia en una tarifa que regía indefinidamente.

1. La tarifa pasa a regir **hasta ese día inclusive**.
2. A partir del día siguiente, el caso queda **sin tarifa** hasta que se registre otra con `RF-CM-001`.
3. **El sistema no avisa de ese hueco**, y es deliberado: cerrar una comisión sin sustituirla es una decisión legítima —ese rol deja de comisionar ese producto— y no un descuido que haya que corregir.

### FA-002 — Reabrir una vigencia

**Cuándo ocurre:** se quita el fin de vigencia de una tarifa que lo tenía.

1. La tarifa vuelve a regir **indefinidamente**.
2. Si al hacerlo pisara los días de otra tarifa posterior del mismo caso, se rechaza (`EX-004`).

### FA-003 — Petición que no cambia nada

**Cuándo ocurre:** se envían los valores que la tarifa ya tenía.

1. La operación **tiene éxito** y devuelve la tarifa.
2. **No se registra evento de auditoría.** Un evento por una petición que no cambió nada convierte el registro en ruido.

## 10. Excepciones

### EX-001 — La tarifa no existe o está retirada

**Condición:** la tarifa indicada no existe, o fue retirada.
**Respuesta del sistema:** rechaza la corrección como recurso no encontrado. **Una tarifa retirada se trata como inexistente**: lo que se retiró debe quedar como estaba, para que lo que la referencie siga diciendo la verdad.

### EX-002 — Se intenta cambiar lo que no se corrige

**Condición:** se envía el rol, el producto, la persona o el inicio de vigencia.
**Respuesta del sistema:** rechaza la petición **diciendo que esos campos no se pueden corregir**, y no aplica ningún otro cambio de la misma petición. **Se rechazan y no se ignoran**: ignorarlos haría creer que el cambio se aplicó.

### EX-003 — Petición vacía

**Condición:** no se envía ningún campo corregible.
**Respuesta del sistema:** rechaza la petición diciendo que no hay nada que corregir.

### EX-004 — La vigencia resultante se solapa

**Condición:** el fin de vigencia declarado —o su retirada— hace que la tarifa pise días de otra viva del mismo caso.
**Respuesta del sistema:** rechaza la corrección diciendo con cuál se solapa y qué periodo ocupa esa otra.

## 11. Validaciones

| ID | Regla | Mensaje |
|---|---|---|
| `VAL-003` | Rango del porcentaje | El porcentaje debe estar entre cero y cien. |
| `VAL-005` | Orden de la vigencia | El fin de vigencia no puede ser anterior a su inicio. |
| `VAL-006` | Formato de fecha | La fecha debe expresarse en el formato de fecha admitido. |
| `VAL-009` | Campos no corregibles | El rol, el producto, la persona y el inicio de vigencia de una tarifa no se pueden corregir. |
| `VAL-010` | Petición vacía | Debe enviarse al menos un campo corregible. |

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-CM-023` | El sistema corrige el porcentaje y conserva intacto lo demás |
| `CA-CM-024` | El sistema declara un fin de vigencia en una tarifa que regía indefinidamente |
| `CA-CM-025` | El sistema **quita** el fin de vigencia y la tarifa vuelve a regir indefinidamente |
| `CA-CM-026` | El sistema **rechaza** vaciar el porcentaje |
| `CA-CM-027` | El sistema rechaza el rol, el producto, la persona y el inicio de vigencia con su propio mensaje, y no aplica el resto de la petición |
| `CA-CM-028` | El sistema rechaza una corrección de vigencia que solape con otra tarifa viva del mismo caso |
| `CA-CM-029` | El sistema trata una tarifa retirada como inexistente |
| `CA-CM-030` | El registro de auditoría contiene **solo los campos que cambiaron**, con su valor anterior y el nuevo, y una petición que no cambia nada no deja evento |

## 13. Casos límite

- **Corregir el porcentaje de una tarifa ya vencida:** se admite. Es exactamente el caso «lo declaramos mal en su día», y prohibirlo dejaría el error escrito para siempre. Lo que no cambia es lo ya liquidado, que conserva el porcentaje con el que se pagó (`RN-CM-008`).
- **Cerrar la vigencia en una fecha ya pasada:** se admite, con el mismo argumento. Si la comisión dejó de regir el mes pasado y nadie lo registró, la corrección es el modo de decirlo.
- **Cerrar la vigencia el mismo día del inicio:** se admite. Una tarifa que rigió un solo día es válida.
- **Cerrar la vigencia antes del inicio:** se rechaza (`VAL-005`). No existe una tarifa que termine antes de empezar.
- **Corregir el porcentaje a cero:** se admite, porque el cero es un porcentaje válido y significa «no comisiona». No equivale a retirar la tarifa: la tarifa sigue declarada y sigue ganando a las menos específicas.

## 14. Preguntas abiertas

| # | Pregunta | Responsable | Estado |
|---|---|---|---|
| — | Ninguna | — | — |

## 15. Control de cambios

| Versión | Fecha | Cambio | Responsable |
|---|---|---|---|
| 0.1.0 | 28-08-2026 | Redacción inicial, sin preguntas abiertas. | Responsable técnico |
