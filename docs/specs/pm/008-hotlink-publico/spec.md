# SPEC — `RF-PM-008` Consultar un hotlink: producto y vendedor, sin autenticación

| Campo | Valor |
|---|---|
| Requerimiento | `RF-PM-008` |
| Módulo | `PM` — Productos y Mercadeo |
| Estado | **Aprobada** |
| Autor | Responsable técnico |
| Aprobada por | Responsable del proyecto |
| Fecha de aprobación | 07-09-2026 |
| Enmendada el | 07-09-2026 — **el upgrade trae su membresía destino con el color** (`RN-SP-024`). Ver §15 |
| Enmendada el | 08-09-2026 — **el precio que publica es el que se ANUNCIA** (`RN-PM-023`, `RN-PM-024`), y la conversión se calcula sobre él. Ver §15 |

---

## 1. Objetivo

Que **un enlace repartido por un vendedor abra una pantalla**: qué se vende, cuánto cuesta —en su moneda y en la de casa— y quién lo ofrece. Sin token.

## 2. Contexto

**`scope` lleva desde el 07-09-2026 declarado sin filtrar nada.** `RN-PM-019` dice que `HOTLINKS` marca lo que llega a ese canal, y hasta hoy el canal no existía: la oferta no puede filtrarlo —la escala es acumulativa— y el catálogo administrativo solo lo lista. **Este requerimiento es su primer consumidor real**, y con él el alcance pasa de ser un dato que se declara a ser uno que decide.

**Y es el primer endpoint público de `PM`**, además del primero del sistema que publica el nombre de una persona. Eso, y no la conversión de moneda, es lo que gobierna su diseño.

## 3. Actores

| Actor | Papel |
|---|---|
| **Cualquiera, sin autenticar** | Abre el enlace |

**Llevar un token no cambia la respuesta.** No hay una versión «enriquecida» para quien está dentro: el mismo enlace responde lo mismo a todo el mundo, que es lo que hace que la pantalla se pueda cachear y compartir.

## 4. Alcance

### 4.1 Incluye

- Resolver **un nombre de usuario y un código de producto** en una sola llamada.
- Devolver del vendedor **nombre y apellido**, y nada más.
- Devolver el producto con su **precio en su moneda** y con la **conversión a la moneda por omisión**, usando la tasa vigente hoy.
- Responder **`404` uniforme** a los seis casos que no proceden.

### 4.2 No incluye

- **Comprar.** El enlace enseña; la venta es de `MV` y exige autenticación.
- **Registrar a quien llega.** El alta de clientes por enlace es `RF-SP-045`, y es otra ruta y otro requerimiento.
- **Contar visitas.** Ninguna métrica de negocio: quién abrió el enlace y cuántas veces no se registra. Ver §14, resolución 4.
- **Elegir la moneda de conversión.** Es siempre la de por omisión. Ver §14, resolución 1.
- **Publicar nada más de la persona.** Ni correo, ni identificador, ni estado, ni roles (`RN-PM-022`).

## 5. Reglas de negocio aplicables

| ID | Regla | Origen |
|---|---|---|
| `RN-PM-009` | Solo se ofrece lo activo | `requirements/pm.md` §5.1 |
| `RN-PM-019` | El alcance dice hasta dónde se muestra, y es acumulativo | `requirements/pm.md` §5.1 |
| `RN-PM-021` | **El hotlink solo publica lo activo y de alcance `HOTLINKS`** | `requirements/pm.md` §5.1 |
| `RN-PM-022` | **De la persona solo el nombre, y solo si es fuerza comercial** | `requirements/pm.md` §5.1 |
| `RN-PM-024` | **El precio del sistema no sale de administración**, y esto es lo más lejos de administración que hay | `requirements/pm.md` §5.1 |
| `RN-SP-032` | Dos tasas vigentes del mismo par no se solapan | `requirements/sp.md` §5.2 |

**`RN-SP-032` es la que hace que «la tasa vigente» sea una y no varias.** Sin ella esta consulta tendría que elegir entre dos precios simultáneos para el mismo cambio, y elegiría el que el índice listara primero.

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción | Restricción |
|---|---|---|---|
| Nombre de usuario | Sí | De quién es el enlace | Va en la ruta |
| Código del producto | Sí | Qué se ofrece | Va en la ruta. Se compara **sin distinguir mayúsculas**, porque un enlace se teclea |

**Ningún parámetro de consulta.** No hay moneda que elegir, ni idioma, ni formato: cada parámetro sería superficie pública que alguien tendría que vigilar.

### 6.2 Salida

| Dato | Descripción |
|---|---|
| Vendedor | **Nombre y apellido**, y nada más |
| Producto | Código, tipo, nombre, descripción, icono, vigencia en días |
| Precio | El importe **en la moneda del producto**, con los decimales de esa moneda. **Es el precio a mostrar** —el público si el producto lo declara y el del sistema si no—, y **el del sistema no viaja nunca por aquí** (`RN-PM-024`) |
| Conversión | La moneda de destino, **la tasa aplicada** y el **importe convertido**. **Vacía y presente** cuando no hay conversión que hacer. **Se calcula sobre el mismo importe que se publica**, no sobre el otro |
| Membresía destino | **Solo en los upgrades**: código, nombre y **color**. **Vacía y presente en los bots**, que no llevan ninguna |

**La tasa viaja además del importe convertido**, y no es redundante: sin ella la pantalla no puede decir *«a 4.150 por dólar»*, que es lo que hace creíble el número. Con ella, además, quien lea la respuesta puede comprobar la cuenta.


**El color viene de la membresía y es lo que la pantalla pinta.** `RN-SP-024` obliga a que toda membresía declare el suyo —seis dígitos hexadecimales sin `#`— y este es el primer sitio del sistema donde ese dato sale **sin autenticación**. No es un dato personal ni comercial: es la identidad visual de un nivel, y sin él la pantalla de un enlace tendría que inventarse un color o pedirlo aparte.

!!! important "El hotlink publica MENOS de la membresía que los otros cuatro endpoints, y es deliberado"

    Los demás devuelven `id`, `code`, `name`, `level` y `color`. Aquí van **solo tres**: ni el identificador, que no sirve a quien no puede llamar a nada más, ni el **nivel**, que publicaría la forma de la cadena comercial sin token.

    **No son dos formas del mismo dato**, que es lo que los javadoc de `ProductItem` y `OfferItem` prohíben: es la misma forma **recortada**, y quien lea el hotlink lee tres campos que ya conoce. Lo que se evita al recortar es publicar de más, que en un endpoint público es la decisión por omisión.
!!! danger "El precio que publica este endpoint es el que se ANUNCIA, y puede no ser el que se cobre"

    Desde el 08-09-2026 un producto lleva dos importes y **solo uno se cobra** (`requirements/pm.md` §5.2.4). Este endpoint publica **el otro** cuando existe: el precio público.

    **Convertir uno y publicar el otro sería el defecto grave**, y por eso se escribe: la respuesta llevaría dos importes que no se corresponden, y quien los mirara juntos podría **deducir la diferencia** entre lo anunciado y lo cobrado — en el único endpoint del módulo que no pide token.

    Lo que se cobra sale de `products.price` y lo dice el comprobante de la venta (`RF-MV-002`). Aquí no aparece **por ninguna vía**, ni siquiera dividiendo el importe convertido entre la tasa.

!!! danger "El importe convertido es informativo, y esto tiene que llegar hasta el frontend"

    **Lo que se cobra no es este número.** Una venta va en **una sola moneda** (`RN-MV-012`) y congela su importe al registrarse; esta conversión se calcula al vuelo, cambia el día que cambie la tasa y **no reserva nada**.

    Es la misma advertencia que `RF-PM-003` dejó escrita para el precio de un producto —un número JSON pasa por coma flotante en cualquier cliente JavaScript—, y aquí pesa más porque hay una multiplicación de por medio.

## 7. Precondiciones y postcondiciones

**Precondiciones:** ninguna. No hay actor que autenticar.

**Postcondiciones:** ninguna. Es una lectura, **no audita** y **no reserva nada**: que el producto aparezca aquí no promete que siga activo cuando alguien lo compre.

## 8. Flujo principal

1. Llega una petición con el nombre de usuario y el código.
2. El sistema resuelve **el vendedor** por la lectura que `SP` publica. Si esa lectura devuelve vacío —no existe, o existe y no es fuerza comercial—, la respuesta es `404`.
3. El sistema resuelve **el producto** por su código, exigiendo **activo, no retirado y de alcance `HOTLINKS`**. Si no lo encuentra, `404`.
4. El sistema pide a `SP` **la tasa vigente hoy** desde la moneda del producto hasta la moneda por omisión.
5. El sistema devuelve el vendedor, el producto y la conversión —si la hubo—.

**El orden no importa para la respuesta y sí para el coste**: los dos primeros pasos pueden fallar, y el tercero solo se paga si los dos anteriores salieron.

## 9. Flujos alternativos

### FA-001 — No hay tasa vigente para ese par

**Condición:** nadie declaró una tasa desde la moneda del producto, o la que hay no rige hoy.
**Comportamiento:** **el producto se devuelve igual**, con la conversión **vacía y presente**. Responder `404` escondería un producto perfectamente vendible porque nadie declaró una tasa, y el enlace dejaría de funcionar sin que nada lo explicara.

### FA-002 — El producto ya está en la moneda por omisión

**Condición:** la moneda del producto **es** la de casa.
**Comportamiento:** conversión **vacía y presente**. No hay nada que convertir, y `RN-SP-029` impide que exista una tasa de una moneda a sí misma, de modo que buscarla sería buscar algo que no puede haber.

### FA-003 — Llega con un token válido

**Comportamiento:** **la misma respuesta**. El endpoint no mira quién pregunta.

## 10. Excepciones

### EX-001 — El enlace no lleva a ninguna parte

**Condición:** cualquiera de estos seis casos —nombre de usuario inexistente, persona que no es fuerza comercial, código inexistente, producto inactivo, producto retirado, producto de alcance `TIENDA`—.
**Respuesta del sistema:** `404` con **el mismo cuerpo** en los seis. **No dice cuál falló.**

!!! danger "Por qué los seis responden lo mismo"

    Distinguirlos convierte el endpoint en un **oráculo**. Basta fijar un código de producto que se sepa bueno e ir variando el nombre de usuario: si «producto no encontrado» y «vendedor no encontrado» fueran dos respuestas distintas, cada intento diría **si esa persona existe**.

    Y la variante peor es la de dentro: distinguir «no existe» de «existe pero no es vendedor» publicaría **quién es cliente** de la plataforma.

    **Lo que la uniformidad no resuelve** es el recorrido a ciegas —probar diez mil nombres de usuario y quedarse con los que devuelven `200`—. Eso lo acota `RateLimitFilter` **por origen**, y queda escrito que **acotar no es impedir**: quien reparta la carga entre muchos orígenes lo consigue igual. El diseño asume que el conjunto publicable es **pequeño y ya público** —la fuerza comercial reparte enlaces con su nombre— y que ese es el motivo por el que se admite el riesgo.

## 11. Validaciones

| ID | Validación | Mensaje esperado |
|---|---|---|
| `VAL-001` | Nombre de usuario con forma admisible | El enlace solicitado no existe. |
| `VAL-002` | Código con forma admisible | El enlace solicitado no existe. |

**Las dos validaciones responden `404` y no `400`**, y es deliberado: un `400` sobre la forma del nombre de usuario diría que la forma importa, y de ahí se deduce cuál es. En una ruta pública, **la forma también es información**.

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-PM-127` | El sistema devuelve, **sin token**, el vendedor y el producto en una sola llamada |
| `CA-PM-128` | El sistema devuelve la **tasa aplicada** y el **importe convertido**, redondeado a los decimales de la moneda de destino |
| `CA-PM-129` | El sistema devuelve el producto **con la conversión vacía y presente** cuando no hay tasa vigente, **y no `404`** |
| `CA-PM-130` | El sistema devuelve la conversión **vacía y presente** cuando el producto ya está en la moneda por omisión |
| `CA-PM-131` | El sistema responde `404` a un producto de alcance **`TIENDA`**: es el primer sitio donde el alcance **filtra** |
| `CA-PM-132` | El sistema responde `404` a un producto **inactivo** y a uno **retirado** |
| `CA-PM-133` | El sistema responde `404` a un nombre de usuario que **existe y no es fuerza comercial** |
| `CA-PM-134` | El sistema responde `404` a un nombre de usuario **inexistente**, **con el mismo cuerpo** que en los cinco casos anteriores |
| `CA-PM-135` | La respuesta **no lleva** correo, identificador, estado ni roles del vendedor: solo nombre y apellido |
| `CA-PM-136` | El sistema responde **lo mismo** con un token válido que sin él |
| `CA-PM-137` | El sistema **acota por origen** las peticiones a esta ruta, y el exceso recibe `429` |
| `CA-PM-138` | El sistema devuelve, en un **upgrade**, la membresía destino con **código, nombre y color** |
| `CA-PM-139` | El sistema **no publica el identificador ni el nivel** de la membresía en este endpoint, al revés que en los otros cuatro |
| `CA-PM-140` | El sistema devuelve la membresía **vacía y presente** en un producto de tipo **bot** |
| `CA-PM-161` | El sistema publica el **precio público** del producto que lo declara, y el **del sistema** del que no |
| `CA-PM-162` | El sistema calcula la **conversión sobre el importe que publica**, y no sobre el otro: el importe convertido dividido por la tasa devuelve el publicado |
| `CA-PM-163` | La respuesta **no lleva** el precio del sistema en ningún campo cuando el producto declara precio público |

## 13. Casos límite

| Caso | Decisión |
|---|---|
| El vendedor **deja de serlo** después de repartir sus enlaces | Los enlaces **dejan de funcionar**, y devuelven el mismo `404` que uno inventado. Es correcto: `RN-PM-022` publica a la fuerza comercial **de hoy**, no a la de cuando se generó el enlace. Que un enlace caduque por eso es una consecuencia aceptada y no un defecto |
| Dos personas con nombre y apellido iguales | El enlace las distingue por **nombre de usuario**, que es único. Que la pantalla enseñe dos veces «Ana Ruiz» no es problema de este endpoint |
| El producto **cambia de alcance** a `TIENDA` | El enlace deja de funcionar. Es exactamente lo que `RN-PM-019` existe para permitir: retirar algo de un canal sin retirarlo del catálogo |
| El producto **gana o pierde su precio público** entre dos visitas | La segunda visita muestra el otro importe, sin aviso. Es lo mismo que ocurre con la tasa, y por el mismo motivo: este endpoint publica el estado de hoy y **no reserva nada** |
| El precio público vale **cero** y el del sistema no | Se publica **el cero**, y la conversión de cero es cero. El endpoint no compara los dos importes ni «corrige» el que le parezca raro: quien los declara es quien decide (`requirements/pm.md` §5.2.4) |
| La tasa cambia entre dos visitas | La segunda visita muestra otro importe. **Es correcto y hay que decirlo en la pantalla**: la conversión es informativa y no reserva nada (§6.2) |
| El nombre de usuario lleva mayúsculas | Se compara **sin distinguirlas**, como el correo en `RF-SP-024`: un enlace se teclea y se comparte por WhatsApp, y exigir la caja exacta rompería la mitad de las visitas |
| Alguien pide un producto de otro vendedor | **Se devuelve igual.** No hay relación entre vendedor y producto en el modelo: el enlace **compone** dos cosas que existen por separado, y quién puede enlazar qué no está declarado en ningún sitio. Ver §14, resolución 3 |

## 14. Preguntas abiertas

| # | Pregunta | Resolución |
|---|---|---|
| 1 | ¿La moneda de conversión se elige? | **No: es siempre la de por omisión.** `currencies.is_default` está garantizada única por un índice parcial, de modo que no hay ambigüedad ni parámetro. Admitir una moneda por consulta añadiría superficie pública y abriría la pregunta de qué hacer cuando no hay tasa para ese par — que es una decisión sin dueño |
| 2 | ¿Qué gobierna entonces `products:hotlink`, sembrado el mismo día en `V60`? | **Queda sin endpoint que lo exija, y la decisión NO se toma aquí.** `security.md` lo declaró como «la vista de hotlinks», y esa vista resultó ser pública. La reconciliación que este documento **propone** —y que el responsable del proyecto tiene que confirmar o descartar— es que gobierne la vista **autenticada** en la que un vendedor consulta y genera **sus** enlaces, que es otro requerimiento y no existe. Mientras tanto, el permiso sigue sembrado y sin usar, exactamente como los cuatro `movements:` estuvieron desde `V51` |
| 3 | ¿Debería el enlace comprobar que ese producto es **de** ese vendedor? | **Hoy no se puede**: nada asocia un producto con una persona en el modelo. El enlace **compone** dos cosas independientes. Queda anotado que el día que exista esa relación, esta consulta tendrá que exigirla — y que hasta entonces **cualquier vendedor puede enlazar cualquier producto de alcance `HOTLINKS`**, que es un hecho del diseño y no un descuido |
| 4 | ¿Se cuentan las visitas? | **No.** Contar visitas es analítica, tiene su propio almacenamiento y su propia decisión de privacidad, y meterla aquí de rebote la dejaría sin ninguna de las dos. Queda fuera del alcance |
| 5 | ¿Se audita? | **No.** Es una lectura pública y anónima; auditarla llenaría `audit_change_log` de filas sin actor. Lo que sí queda es el registro de peticiones de `request_log`, que ya recoge toda llamada HTTP |
| 6 | ¿Por qué el hotlink publica menos de la membresía que los otros cuatro endpoints? | **Porque es público.** El `id` no le sirve a quien no puede llamar a nada más, y el `level` publicaría **la forma de la cadena comercial** sin token — cuántos niveles hay y en qué orden. No es una segunda forma del mismo dato: es la misma **recortada**, y recortar es la decisión por omisión en una ruta pública |

## 15. Control de cambios

| Versión | Fecha | Cambio | Responsable |
|---|---|---|---|
| 0.1.0 | 07-09-2026 | Redacción inicial. **La decisión que gobierna el requerimiento no es la conversión de moneda sino el `404` uniforme**: los seis casos que no proceden responden lo mismo, porque distinguirlos convertiría el endpoint en un oráculo que dice **quién existe** —y, peor, **quién es cliente**—. Queda escrito lo que esa uniformidad **no** resuelve: el recorrido a ciegas, que `RateLimitFilter` acota por origen y que **acotar no es impedir**; se admite porque el conjunto publicable es la fuerza comercial, que ya reparte su nombre. **Sin tasa vigente el producto se devuelve igual**, con la conversión vacía: responder `404` escondería un producto vendible porque nadie declaró una tasa. **Las validaciones responden `404` y no `400`**, porque en una ruta pública la forma también es información. **Y quedan dos cosas declaradas y sin dueño**: `products:hotlink` se queda sin endpoint —la reconciliación se propone y no se decide— y **nada asocia un producto con un vendedor**, de modo que hoy cualquiera de la fuerza comercial puede enlazar cualquier producto del canal. | Responsable del proyecto |
| 0.2.0 | 07-09-2026 | **El producto trae la membresía destino cuando es un upgrade, con su COLOR**, por decisión del responsable del proyecto. `RN-SP-024` obliga a que toda membresía declare el suyo, y este es el primer sitio donde ese dato sale **sin autenticación**: no es personal ni comercial, es la identidad visual de un nivel, y sin él la pantalla de un enlace tendría que inventárselo. **El hotlink publica solo código, nombre y color** —ni identificador ni nivel—, al revés que los otros cuatro endpoints del módulo, que devuelven la referencia completa: el `id` no sirve a quien no puede llamar a nada más, y el `level` publicaría la forma de la cadena comercial sin token. **No son dos formas del mismo dato**, que es lo que los javadoc de `ProductItem` y `OfferItem` prohíben: es la misma **recortada**. Entran `CA-PM-138` a `CA-PM-140`. | Responsable del proyecto |
| 0.3.0 | 08-09-2026 | **El precio que este endpoint publica es el que se ANUNCIA, no el que se cobra** (`RN-PM-023`, `RN-PM-024`). Un producto lleva desde hoy dos importes y solo uno es dinero; el hotlink devuelve **el público si el producto lo declara y el del sistema si no**, y el del sistema **no viaja por aquí por ninguna vía**. Es el sitio donde esa regla pesa más: es el único endpoint del módulo **sin token**, de modo que cualquier fuga es pública y no se puede retirar. **La conversión se calcula sobre el importe que se publica**, y esa frase es la mitad de la enmienda: convertir uno y publicar el otro dejaría en la misma respuesta dos números que no se corresponden, y quien los mirara juntos —dividiendo el convertido entre la tasa, que también viaja— **deduciría exactamente la diferencia** entre lo anunciado y lo cobrado. Entran `CA-PM-161` a `CA-PM-163`, y el tercero prueba una **ausencia**. Dos casos límite nuevos: el producto que gana o pierde su precio público entre dos visitas —cambia lo que se muestra, sin aviso, como ya ocurre con la tasa— y el precio público en **cero**, que se publica tal cual porque este endpoint no compara los dos importes ni corrige al que lo declaró. | Responsable del proyecto |
