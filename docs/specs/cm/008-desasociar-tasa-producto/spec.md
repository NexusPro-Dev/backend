# SPEC — `RF-CM-008` Retirar la asociación de una tasa con un producto

| Campo | Valor |
|---|---|
| Requerimiento | `RF-CM-008` |
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

    Es una excepción al Art. I.1, registrada en `requirements/cm.md` §4 y en la matriz.

---

## 1. Objetivo

Hacer que **un producto deje de pagar comisión a un rol**, sin destruir la tasa.

## 2. Contexto

Es **la única forma de dejar de pagar sin retirar la tasa**, y por eso existe como operación propia. La tasa sigue en el catálogo, disponible para otros productos; lo que desaparece es que rija sobre este.

**Frente a `RF-CM-004`, la diferencia es de alcance y de daño:**

| | Qué destruye | Qué deja |
|---|---|---|
| **Desasociar** (esto) | La asociación | La tasa entera, viva y reutilizable |
| **Retirar** (`RF-CM-004`) | La tasa | Nada — y **exige desasociar antes** (`RN-CM-015`) |

**Y hay algo propio de esta operación que no tiene ninguna otra del módulo: no queda fila.** La asociación **no tiene retiro lógico**, porque una asociación es configuración vigente y no un hecho del pasado que conservar. De modo que **el registro de eliminación no acompaña a la fila: la sustituye.** Es lo único que quedará de que esa tasa rigió alguna vez sobre ese producto.

## 3. Actores

| Actor | Rol en esta funcionalidad |
|---|---|
| Administrador | Declara que ese producto deja de comisionar a ese rol, e indica por qué |

## 4. Alcance

### 4.1 Incluye

- Retirar la asociación de **una tasa concreta** con **un producto concreto**, con **motivo obligatorio**.
- Dejar en la auditoría de eliminaciones quién, cuándo, por qué y **la instantánea de lo que se retiró**.
- Hacer que ese producto **deje inmediatamente de comisionar** a ese rol.

### 4.2 No incluye

- **Retirar la tasa**, que es `RF-CM-004`. Aquí la tasa no se toca.
- **Cambiar la tasa que rige sobre un producto.** Eso es desasociar y volver a asociar: **dos decisiones, y se toman por separado a propósito**.
- **Retirar todas las asociaciones de una tasa de golpe.** Cada producto que deja de comisionar es una decisión, y en bloque se toma sin mirarlas.
- **Deshacer la desasociación.** Se vuelve a asociar, que es `RF-CM-007`.

## 5. Reglas de negocio aplicables

| ID | Regla | Origen |
|---|---|---|
| `RN-CM-008` | La liquidación conserva el porcentaje | `requirements/cm.md` §5.1 |

**`RN-CM-008` está aquí por la misma razón que en `RF-CM-003`: se necesita, no se cumple.** Desasociar no borra lo ya pagado, pero **borra la única fila que decía con qué porcentaje se estaba pagando**. Si la liquidación no lo ha copiado, esa cifra desaparece.

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción | Restricción de negocio |
|---|---|---|---|
| Tasa | Sí | Cuál deja de regir | — |
| Producto | Sí | Sobre cuál deja de regir | — |
| Motivo | Sí | Por qué | No puede estar en blanco ni exceder la longitud admitida (Art. V.13) |

**La tasa y el producto identifican la asociación, y no el rol**, aunque el rol forme parte de su identidad interna. Quien desasocia nombra **la tasa** que quiere quitar de en medio: si el producto estuviera asociado a **otra** tasa del mismo rol, buscar por rol retiraría una asociación que nadie pidió retirar.

### 6.2 Salida

**Ninguna.** Lo que había que decir está en el registro de eliminación.

## 7. Precondiciones y postcondiciones

**Precondiciones**

- El actor está autenticado y posee el permiso de modificación de comisiones.
- Esa tasa está asociada a ese producto.

**Postcondiciones**

- **La fila de la asociación deja de existir.** No queda marcada: desaparece.
- Ese producto **deja de comisionar a ese rol**, inmediatamente.
- La tasa **sigue viva** y sigue rigiendo sobre los demás productos a los que esté asociada.
- La auditoría de eliminaciones contiene el evento con el motivo y la instantánea, **y es lo único que queda**.

## 8. Flujo principal

1. El actor envía la tasa, el producto y el motivo.
2. El sistema comprueba que el motivo no está vacío ni excede la longitud admitida.
3. El sistema comprueba que esa tasa está asociada a ese producto.
4. El sistema toma la instantánea de la asociación, la borra y emite el evento de eliminación.

**El motivo se verifica el primero de todo**, y aquí pesa más que en cualquier otro retiro del módulo: como la fila desaparece, **ese texto es el único sitio donde quedará escrito por qué se dejó de pagar**.

## 9. Flujos alternativos

### FA-001 — Desasociar para asociar otra tasa

**Cuándo ocurre:** se quiere que ese rol cobre un porcentaje distinto por ese producto.

1. Son **dos operaciones**: esta y `RF-CM-007`.
2. Entre una y otra, **ese producto no comisiona a ese rol**. Es una ventana real y se acepta: fundirlas en una operación de sustitución escondería que se está tomando una decisión sobre lo que se paga.

### FA-002 — Desasociar para poder retirar la tasa

**Cuándo ocurre:** se quiere retirar una tasa que rige sobre productos.

1. Hay que desasociarla de **cada uno** antes (`RN-CM-015`).
2. Es el coste declarado de esa regla, y se paga **a la vista**: cada paso es una decisión explícita de que ese producto deja de comisionar.

### FA-003 — La tasa queda sin ninguna asociación

**Cuándo ocurre:** era la última.

1. La tasa **sigue en el catálogo** y **deja de pagar nada a nadie**.
2. Vuelve al estado en que nació (`RF-CM-001` §7), y el listado lo dice devolviendo cero productos asociados.

## 10. Excepciones

### EX-001 — Esa tasa no está asociada a ese producto

**Condición:** no existe la asociación, sea porque nunca existió o porque ya se retiró.
**Respuesta del sistema:** rechaza la operación diciendo que esa tasa no está asociada a ese producto.

!!! note "Es «no encontrado» y no «conflicto», al revés que en los retiros de tasa"

    En `RF-CM-004` la fila permanece y por eso puede decirse «ya estaba retirada». **Aquí el borrado es físico y no queda nada que distinga «nunca existió» de «ya se borró».**

    Inventar un conflicto sería afirmar algo que el sistema **no sabe**. Se dice lo único que consta: que ahora mismo no están asociados.

## 11. Validaciones

| ID | Regla | Mensaje |
|---|---|---|
| `VAL-007` | Motivo obligatorio | El motivo de la desasociación es obligatorio. |
| `VAL-008` | Longitud del motivo | El motivo no puede exceder 500 caracteres. |

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-CM-073` | Retira la asociación y **la tasa sigue viva** en el catálogo |
| `CA-CM-074` | El producto **deja de comisionar** a ese rol inmediatamente |
| `CA-CM-075` | El registro de eliminación conserva el motivo y la instantánea, **y es lo único que queda** |
| `CA-CM-076` | Rechaza la operación sin motivo o con el motivo en blanco, **sin retirar nada** |
| `CA-CM-077` | Desasociar lo que no está asociado devuelve **«no encontrado»**, y no conflicto |
| `CA-CM-078` | Exige el permiso de modificación de comisiones |

## 13. Casos límite

- **La misma asociación dos veces:** la segunda devuelve «no encontrado». **No es idempotente en la respuesta**, aunque el efecto sea el mismo, y no puede serlo: no queda fila que permita decir que ya se hizo.
- **Desasociar de un producto retirado:** se admite. `RN-CM-010` prohíbe **declarar** sobre lo retirado, no dejar de hacerlo — y quien quiere limpiar la configuración de un producto que ya no se vende tiene un motivo legítimo.
- **Desasociar la última tasa de un producto:** el producto deja de comisionar a nadie. **No es un estado inválido**, y `RF-CM-005` lo dice devolviendo «sin tarifa».
- **Dos desasociaciones simultáneas de la misma asociación:** una borra y la otra encuentra que ya no está. Lo que no puede ocurrir es que se escriban **dos registros de eliminación** sobre un mismo hecho, con dos motivos distintos.
- **El motivo es la única constancia, y nadie lo comprueba:** un motivo de un carácter satisface la regla. Es deliberado (`architecture.md` §6.6.3), y aquí el coste de un motivo pobre es mayor que en cualquier otra eliminación del sistema — porque no hay fila que consultar después.

## 14. Preguntas abiertas

| # | Pregunta | Responsable | Estado |
|---|---|---|---|
| — | Ninguna | — | — |

**Queda declarada una asimetría deliberada con el esquema de auditoría:** `ck_deletion_reason` **exime de motivo** a las eliminaciones de tipo asociación, y **este requerimiento lo exige igual**. El motivo es §2: aquí no se pierde un vínculo entre dos filas que siguen contándolo todo — se pierde **la única constancia** de que ese producto pagaba a ese rol.

## 15. Control de cambios

| Versión | Fecha | Cambio | Responsable |
|---|---|---|---|
| 0.1.0 | 02-09-2026 | Redacción inicial, **después de construirse el requerimiento** — excepción al Art. I.1 declarada en cabecera. Recoge la operación que el rediseño del 01-09-2026 hizo posible: **dejar de pagar sin destruir la tasa**, que antes no existía porque la tarifa y su alcance eran la misma fila. §2 la contrasta con `RF-CM-004` en alcance y daño, y declara lo propio de este requerimiento: **no queda fila**, de modo que el registro de eliminación **no acompaña a la fila sino que la sustituye**. §10 explica por qué su rechazo es «no encontrado» y no «conflicto», al revés que en los retiros de tasa. §14 declara la asimetría con `ck_deletion_reason`, que **exime** de motivo a las eliminaciones de asociación y que este requerimiento **exige igual**. | Responsable técnico |
