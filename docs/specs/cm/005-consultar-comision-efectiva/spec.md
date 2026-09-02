# SPEC — `RF-CM-005` Consultar la comisión efectiva de una persona sobre un producto en una fecha

| Campo | Valor |
|---|---|
| Requerimiento | `RF-CM-005` |
| Módulo | `CM` — Comisiones |
| Versión | 0.2.0 |
| Estado | **Aprobada** |
| Autor | Responsable técnico |
| Aprobada por | Responsable del proyecto |
| Fecha de aprobación | 02-09-2026 |

!!! info "Qué va en este documento"

    **Qué debe pasar, y por qué.** Nada más.

    **Prueba de pertenencia:** si un cambio de tecnología lo invalidaría, no pertenece aquí — va a `plan.md`.

!!! warning "Esta especificación se reescribió después de construirse, y construirla destapó algo"

    La precedencia pasó de **cuatro grados a dos**. Y al implementarla se vio una consecuencia que la prosa del módulo describía más suave de lo que es: una tasa personalizada **no se queda callada** cuando su titular deja de vender — **sigue pagando**. Ver §9, `FA-003`.

---

## 1. Objetivo

Responder **cuánto le corresponde a una persona por vender un producto un día concreto**, y **por qué**.

## 2. Contexto

Es el requerimiento que hace que las dos piezas del módulo signifiquen algo. Sin él, el catálogo por rol y las excepciones por persona son dos listas que nadie sabe combinar.

**Vive en un solo sitio a propósito.** Reimplementar una comparación de precedencia en cada consumidor es el defecto que **devuelve resultados plausibles durante meses**: no falla, paga mal. Cuando exista la liquidación, llamará aquí una vez por cada nivel de la cadena comercial (`RN-CM-011`) en lugar de decidir por su cuenta.

**La precedencia es ahora una pregunta y una respuesta de reserva**, donde antes eran cuatro grados ordenados. Si la persona tiene una tasa personalizada vigente ese día, **es esa, sin mirar el producto**. Si no, la que su rol vendedor tenga **asociada a ese producto**. Si tampoco, no hay tarifa.

**Y hay un cambio de significado que esta consulta hereda y no puede suavizar.** Antes, una tarifa sin producto valía para todo el catálogo, de modo que un rol con tarifa por omisión siempre tenía respuesta. Ahora **no hay tarifa por omisión**: sin asociación no hay nada que devolver, aunque el catálogo tenga una tasa para ese rol con su porcentaje.

## 3. Actores

| Actor | Rol en esta funcionalidad |
|---|---|
| Administrador | Comprueba qué le corresponde a alguien, y **por qué tasa** |
| Un módulo futuro de liquidación | Consumirá esta respuesta, una vez por nivel de la cadena |

**No incluye al vendedor consultando la suya**, que es otro actor y depende de **D-22**.

## 4. Alcance

### 4.1 Incluye

- Resolver la comisión de una **persona** sobre un **producto** en una **fecha**.
- Devolver **de cuál de las dos piezas** salió la tasa que ganó.
- Distinguir **tres desenlaces**, ninguno de los cuales es un error.
- Resolver con normalidad sobre un producto **retirado**.

### 4.2 No incluye

- **Calcular ningún importe.** Devuelve un porcentaje; multiplicarlo por una venta es de quien tenga la venta.
- **Resolver la cadena entera.** Devuelve la comisión de **una** persona. El override de `RN-CM-011` es llamar a esto una vez por nivel, y quien recorra la cadena es quien liquide.
- **Comprobar que la suma de la cadena no pasa de cien.** No puede: solo ve un nivel. `RN-CM-011` declara que ese tope **queda sin dueño**.
- **Que el vendedor consulte la suya.** Depende de D-22.

## 5. Reglas de negocio aplicables

| ID | Regla | Origen |
|---|---|---|
| `RN-CM-004` | La personalizada gana siempre | `requirements/cm.md` §5.1 |
| `RN-CM-012` | Una tasa de rol no rige hasta que se asocia | `requirements/cm.md` §5.1 |

**`RN-CM-004` vive aquí y solo aquí.** Es la razón de que este requerimiento exista.

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción | Restricción de negocio |
|---|---|---|---|
| Persona | Sí | De quién se pregunta | Debe existir |
| Producto | Sí | Por vender qué | Debe existir. **Puede estar retirado** |
| Fecha | No | Qué día. Sin ella, **hoy** | Una fecha |

### 6.2 Salida

| Dato | Descripción |
|---|---|
| Desenlace | Cuál de los tres ocurrió |
| Porcentaje | El que le corresponde. **Nulo y presente** cuando no hay tasa — nunca cero |
| Tasa | El identificador de la que ganó, para poder ir a verla |
| **Fuente** | De cuál de las dos piezas salió: la personalizada o la del rol |
| Vigencia | La de la tasa que ganó. **Nula cuando la fuente es el rol**, porque esas no tienen |
| Rol | El rol vendedor de la persona. **Puede llegar nulo con desenlace resuelto**: ver `FA-003` |
| Fecha | La fecha con la que se resolvió, sea la enviada o la de hoy |

!!! danger "Nulo y cero no son lo mismo, y quien consuma esto va a pagar con esa cifra"

    **Cero es una decisión declarada** —«este producto no paga a este rol»—, y solo puede expresarse asociando una tasa del cero por ciento.

    **Nulo es que nadie la tomó.** Devolver cero en la ausencia haría **indistinguible lo pensado de lo olvidado**.

## 7. Precondiciones y postcondiciones

**Precondiciones**

- El actor está autenticado y posee el permiso de lectura de comisiones.

**Postcondiciones**

- Ninguna. No cambia nada.

## 8. Flujo principal

1. El actor envía la persona, el producto y opcionalmente la fecha.
2. El sistema comprueba que la persona y el producto existen.
3. El sistema determina el rol vendedor de la persona, **si porta alguno**.
4. El sistema busca, **de una vez y con la precedencia resuelta**, la tasa que le corresponde: la personalizada vigente ese día si la hay, y si no la que su rol tenga asociada a ese producto.
5. Si hay tasa, el sistema devuelve el porcentaje, la tasa, la fuente y la vigencia.

**El paso 4 es una sola pregunta y no dos encadenadas**, y esa forma es parte del requerimiento: con dos preguntas el orden viviría en el flujo de control, y una reorganización podría invertirlo **sin que nada fallara** — devolvería un porcentaje plausible.

## 9. Flujos alternativos

### FA-001 — No hay tasa aplicable

**Cuándo ocurre:** la persona porta rol vendedor, no tiene personalizada vigente, y **nadie asoció** una tasa de su rol a ese producto.

1. El sistema devuelve el desenlace **sin tarifa**, con el porcentaje **nulo y presente**.
2. **La causa más probable no es que nadie declarara la tasa, sino que nadie la asoció.** El catálogo puede tener una tasa para ese rol, con su porcentaje, y no regir sobre este producto.
3. **No es un error**: es una respuesta.

### FA-002 — La persona no comisiona

**Cuándo ocurre:** la persona **no porta rol vendedor** y **tampoco tiene tasa personalizada** viva.

1. El sistema devuelve el desenlace **no comisiona**, con el porcentaje nulo y sin rol.
2. No es que falte declarar la tasa: **es que esa persona no vende**. Es un dato distinto de `FA-001` y el contrato los distingue.

### FA-003 — Quien ya no vende cobra su personalizada

**Cuándo ocurre:** la persona **no porta rol vendedor** y **sí** tiene una tasa personalizada vigente.

1. El sistema devuelve el desenlace **resuelta**, con la fuente **personalizada** y **el rol nulo**.
2. **Cobra.** Las tasas personalizadas dejaron de llevar rol el 01-09-2026, y con ello dejaron de morir con el rol de su titular.
3. **`cm.md` §5.3 lo describía como que la excepción «se queda callada hasta que alguien la mira». No se queda callada: sigue pagando.** Se descubrió al construir este requerimiento, y queda aquí escrito porque es el único sitio donde se ve.
4. Un rol nulo junto a un desenlace resuelto **no es una incoherencia de la respuesta**: es esta rama.

### FA-004 — La tasa que gana paga cero

**Cuándo ocurre:** la tasa resuelta declara el cero por ciento.

1. El desenlace es **resuelta**, con porcentaje **cero**.
2. Significa «no comisiona por esto», y es una **decisión declarada**. No se confunde con `FA-001`.

### FA-005 — Producto retirado

**Cuándo ocurre:** el producto existe y fue retirado del catálogo.

1. Se resuelve con normalidad.
2. **Preguntar qué se pagaba por algo que ya no se vende es legítimo**, y es la consulta que una liquidación atrasada necesita.

## 10. Excepciones

### EX-001 — La persona no existe

**Condición:** el identificador no corresponde a ninguna persona.
**Respuesta del sistema:** rechaza la consulta diciendo que la persona indicada no existe. **No es un «no encontrado»**: lo que no existe es un dato que el actor envió.

### EX-002 — El producto no existe

**Condición:** el identificador no corresponde a ningún producto.
**Respuesta del sistema:** rechaza la consulta diciendo que el producto indicado no existe.

**No hay ninguna excepción más, y esa escasez es deliberada:** los tres desenlaces de §9 son **respuestas**, no fallos. Convertir «sin tarifa» en un error obligaría a quien liquide a tratar como excepción el caso más común de un sistema recién configurado.

## 11. Validaciones

| ID | Regla | Mensaje |
|---|---|---|
| — | Persona y producto obligatorios, y formato de fecha | Los del sistema |

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-CM-038` | Resuelve la tasa del rol cuando está **asociada** a ese producto, con fuente `ROL` |
| `CA-CM-039` | **Una tasa de rol sin asociar NO paga nada**: el desenlace es «sin tarifa» |
| `CA-CM-040` | La asociación de **otro producto** no sirve para este |
| `CA-CM-041` | La asociación de **otro rol** no sirve para esta persona |
| `CA-CM-042` | La personalizada **gana** sobre la del rol, con fuente `PERSONALIZADA` |
| `CA-CM-043` | La personalizada gana **aunque el producto no tenga ninguna asociación** |
| `CA-CM-044` | Una personalizada **vencida** deja de ganar, y vuelve a mandar la del rol |
| `CA-CM-045` | **Quien no porta rol vendedor pero tiene personalizada viva COBRA**, y el rol llega nulo |
| `CA-CM-046` | Sin rol vendedor y sin personalizada, el desenlace es «no comisiona» |
| `CA-CM-047` | El porcentaje **cero** resuelve, y se distingue de no tener tasa |
| `CA-CM-048` | Una tasa **retirada** deja de resolver aunque su asociación exista |
| `CA-CM-049` | Un producto **retirado** se resuelve con normalidad |
| `CA-CM-050` | Sin fecha se resuelve con la de hoy; la persona y el producto inexistentes se distinguen |

## 13. Casos límite

- **La vigencia de la tasa que gana llega nula:** es lo normal cuando la fuente es el rol, porque **esas no tienen vigencia**. No es un dato que falte.
- **Una tasa retirada con su asociación viva:** deja de resolver, y el producto pasa a no comisionar. **Es el estado que `RN-CM-015` existe para impedir**, y este requerimiento es donde se vería el daño — se prueba a propósito, sembrándolo a mano, para dejar constancia de por qué esa regla existe.
- **La persona porta dos roles vendedores:** no puede (`RN-SP-025`), y este requerimiento **depende de ello**. Sin esa garantía, «el rol vendedor de la persona» no sería una pregunta con una sola respuesta y la resolución elegiría en silencio.
- **La fecha en el pasado sobre una tasa de rol:** devuelve lo que la tasa dice **hoy**, no lo que decía entonces. Sin vigencia, el catálogo no puede responder a «qué regía». Solo las personalizadas responden de verdad a la fecha.
- **La suma de la cadena pasa de cien:** este requerimiento no lo ve ni lo puede ver. `RN-CM-011` declara que el tope **queda sin dueño** hasta que exista quien aplique las tarifas, y que cuando lo tenga debe **rechazar y no recortar** — recortar decidiría en silencio a quién se le quita.
- **Dos personalizadas de la misma persona cubriendo el mismo día:** no puede ocurrir (`RN-CM-006`), y esta resolución **depende de ello**. Con dos, dejaría de ser determinista.

## 14. Preguntas abiertas

| # | Pregunta | Responsable | Estado |
|---|---|---|---|
| — | Ninguna | — | — |

**Queda declarado lo que este requerimiento no puede resolver y alguien tendrá que resolver:** el tope de la suma de la cadena (`RN-CM-011`). No es una pregunta abierta de esta especificación —está decidido que no vive aquí— sino una deuda del módulo que la liquidación heredará.

## 15. Control de cambios

| Versión | Fecha | Cambio | Responsable |
|---|---|---|---|
| 0.1.0 | 28-08-2026 | Redacción inicial. | Responsable técnico |
| 0.2.0 | 02-09-2026 | **Reescrita sobre el modelo de `cm.md` v0.4.0**, y después de construirse el código. La precedencia pasa de **cuatro grados a dos** y el campo que la explicaba deja de ser el **grado** de la tarifa para ser la **fuente** de la resolución — son dos conceptos distintos y por eso no se reutiliza el nombre. Entra el cambio de significado que esta consulta hereda: **desaparece la tarifa por omisión del rol**, de modo que «sin tarifa» pasa a significar casi siempre **«nadie la asoció»** y no «nadie la declaró». **Y se registra lo que construirla destapó**: nace `FA-003`, que la v0.1.0 no podía tener — quien **no porta rol vendedor pero tiene personalizada viva cobra**, porque esas tasas dejaron de llevar rol y con ello dejaron de morir con el de su titular. Lo que `cm.md` §5.3 llamaba «se queda callada» resultó ser **«sigue pagando»**, y la consecuencia visible es que el rol puede llegar **nulo junto a un desenlace resuelto**. §13 recoge además que este requerimiento **depende de dos reglas ajenas para ser determinista** —`RN-SP-025` y `RN-CM-006`— y que la fecha en el pasado **ya no responde de verdad** sobre las tasas de rol. | Responsable técnico |
