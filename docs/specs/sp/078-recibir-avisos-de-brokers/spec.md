# SPEC — `RF-SP-078` Recibir los avisos de los brokers

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-078` |
| Módulo | `SP` — Sistema Principal |
| Versión | 0.7.0 |
| Estado | **Aprobada** |
| Autor | Responsable técnico |
| Aprobada por | Responsable del proyecto |
| Fecha de aprobación | 08-10-2026 |

!!! info "Qué va en este documento"

    **Qué debe pasar, y por qué.** Nada más.

    **Prueba de pertenencia:** si un cambio de tecnología lo invalidaría, no pertenece aquí — va a `plan.md`. No se nombran tablas, clases, endpoints ni librerías.

---

## 1. Objetivo

Que cada broker con el que opera la plataforma pueda **avisar** al sistema de lo que pasa en sus cuentas —un registro, un depósito—, y que **todo lo que avise quede guardado tal como llegó**, para decidir después, con avisos reales delante, cómo se interpreta.

---

## 2. Contexto

El sistema espera desde el 08-09-2026 a que los brokers confirmen las cuentas que la gente declara: el nombre de usuario en el broker queda vacío hasta que el broker lo confirme (`RN-SP-040`), la cuenta no sale de `REGISTER` hasta que el broker avise del primer depósito (`RN-SP-045`), y el cliente que se registró por un enlace gratuito no sale de `FTD_PENDIENTE` —y no recibe lo que compró (`RN-SP-057`)— hasta ese mismo aviso. Quien lo iba a resolver, `RF-SP-054`, quedó registrado **sin nada decidido**: ni cómo se autentica el broker, ni qué hacer con una cuenta que nadie declaró, ni si el broker puede cambiar el identificador, ni cómo no aplicar dos veces el mismo aviso.

El 08-10-2026 el responsable del proyecto fijó con qué brokers se empieza —**`IQOPTION`, `EXNOVA` y `EXOPTION`**, los tres del catálogo— y dijo lo que pesa más: **no se sabe qué datos devolverán**. Ninguno publica el formato de sus avisos; vive en el panel de afiliados de cada uno, y suele ser una llamada con los datos en la dirección, sin firma.

### 2.1 Lo que este requerimiento decide

**Primero se escucha; después se interpreta.** Este requerimiento es la primera mitad de lo que `RF-SP-054` registró como una sola cosa:

| | Este requerimiento | `RF-SP-054`, después |
|---|---|---|
| Recibir el aviso y comprobar que es del broker | **Sí** | — |
| Guardarlo entero | **Sí** | — |
| Saber qué cuenta, qué persona y qué hecho dice | No | **Sí** |
| Completar la cuenta, moverla a primer depósito, activar a la persona | No | **Sí** |
| No aplicar dos veces el mismo aviso | No: aquí no se aplica nada | **Sí** |

**Cómo se autentica el broker**, la única pregunta de `RF-SP-054` que se responde sin ver un aviso: con **un secreto que va en la dirección** a la que avisa (`RN-SP-066`). Una firma sería mejor y no está disponible; una cabecera, tampoco, porque los paneles de afiliados solo dejan escribir la dirección. Una lista de direcciones de origen no resiste que el broker cambie de servidor. **Hasta el 13-10-2026 hubo uno por broker**, para la ruta con su nombre; desde entonces es **uno solo**, el de la dirección común.

### 2.2 Ningún aviso se pierde (13-10-2026)

La dirección común reconoce al broker por el `advertiser` del aviso (`RN-SP-069`), y **solo `IQOPTION` lo tiene cargado**: los de `EXNOVA` y `EXOPTION` no se conocen porque nunca se ha visto un aviso suyo. Hasta el 13-10-2026, un aviso así se rechazaba **y se perdía**, y con él la única forma de saber cómo se nombra ese broker. El responsable del proyecto pidió lo contrario: **capturar todos y clasificarlos después por el `advertiser`** (`RN-SP-076`). El aviso que no se reconoce **se guarda sin broker y no se interpreta**; cuando el `advertiser` de su broker se cargue, **se le asocia y se interpreta**, como si acabara de llegar.

### 2.3 Lo que trae el aviso se guarda en la cuenta (15-10-2026)

El responsable del proyecto pidió: «de las cuentas de broker quita lo del broker_username, que ahí mismo se guarden la fecha de recibir el evento, el país de origen y el valor del depósito si llega» (`RN-SP-077`). **El nombre de usuario en el broker se retira** (`RN-SP-040`): ningún aviso lo trajo en una semana de avisos reales. En su lugar, la cuenta guarda:

- **Cuándo llegó su aviso de registro**, una vez: ni una reentrega ni un segundo registro la mueven.
- **Su país**, el del primer aviso de la cuenta que lo trae —registro, depósito u operación—, y no cambia después.
- **Sus depósitos**: el importe del primero —el aviso que la pasa a `FIRST_DEPOSIT`— y el total de todos, en la moneda del primero que la trae. Un depósito en otra moneda no suma; uno sin importe se aplica igual (`RN-SP-073`) y no suma.

**El aviso de operación contado se borra** (`RN-SP-074` enmendada): el contador de la cuenta es la constancia, y una reentrega cuenta otra vez, porque ya no queda el aviso con el que reconocerla. El que no se cuenta —un número sin cuenta, o sin broker todavía— se queda.

**El nombre del campo del importe y el de la moneda se configuran**, como los demás: no ha llegado todavía ningún aviso real de depósito, y adivinar el nombre sería escribir una regla sobre un dato inventado. Lo que la cuenta no guarda sigue en el aviso, tal como llegó.

---

## 3. Actores

| Actor | Rol en esta funcionalidad |
|---|---|
| Un broker del catálogo | Avisa; no tiene sesión y se identifica por su secreto |

---

## 4. Alcance

### 4.1 Incluye

- Recibir avisos **con los datos en la dirección y con los datos en el cuerpo**, porque no se sabe cuál de las dos formas usará cada broker.
- Comprobar que el aviso trae el secreto, **antes de guardar nada**.
- Reconocer de qué broker es por su `advertiser`, y **guardarlo igual si no se reconoce** (`RN-SP-076`).
- Guardar **todo** lo que llegó, salvo el secreto y las credenciales.
- Responder en cuanto queda guardado.

### 4.2 No incluye

- **Interpretar** el aviso, y nada de lo que sigue de interpretarlo: completar, mover o activar (`RF-SP-054`).
- **Consultar** los avisos guardados por la API. Hoy se miran desde la base, para escribir `RF-SP-054`; si administración necesita verlos, será un requerimiento propio.
- **Administrar los secretos** por la API: se configuran en el entorno, como las credenciales de las pasarelas.
- **Avisar a nadie** de que llegó algo.

---

## 5. Reglas de negocio aplicables

| Regla | Cómo aplica |
|---|---|
| `RN-SP-066` | El aviso se guarda entero y sin interpretar; lo autentica un secreto en la dirección —uno solo desde el 13-10-2026—; el secreto no se guarda; los repetidos no se descartan; no se edita ni se borra |
| `RN-SP-039` | El catálogo de brokers no se administra por la API: un broker que no está en él, o no está activo, no puede avisar |
| `RN-SP-069` | El broker lo dice el `advertiser` del aviso, con un solo secreto para todos |
| `RN-SP-076` | **El aviso que no se reconoce se guarda sin broker**, y se asocia e interpreta al arrancar cuando su `advertiser` se cargue |
| `RN-SP-070`, `RN-SP-072` | **El aviso de registro crea la cuenta `CONSUMIDOR` sin titular**, con origen en la `VENDEDOR` de su `afftrack` |
| `RN-SP-073` | **El aviso de depósito es el primer depósito**: la cuenta `CONSUMIDOR` pasa a `FIRST_DEPOSIT` y su titular, si espera en `FTD_PENDIENTE`, pasa a `ACTIVO` con lo que compró; los siguientes no cambian nada; de un número sin cuenta o de una `VENDEDOR`, solo se guarda |
| `RN-SP-074` | **El aviso de operación se cuenta** en la cuenta de ese número: cuántas, la primera y la última |

**Dos reglas nuevas**: `RN-SP-066` y, desde el 13-10-2026, `RN-SP-076`.

---

## 6. Datos

### 6.1 Entrada

**Lo que el broker mande, como lo mande**: datos en la dirección, en el cuerpo o en los dos, con cualquier tipo de contenido. Además, **el secreto**, en la dirección, y **qué broker es**, en el **`advertiser`** del propio aviso (`RN-SP-069`).

### 6.2 Salida

Un acuse de recibo, **sin datos**: el broker solo necesita saber que se recibió.

### 6.3 Lo que se guarda

Qué broker avisó —**o que todavía no se sabe**, `RN-SP-076`—, **el `advertiser` que trajo**, cuándo, desde qué dirección de red, con qué método, **los datos de la dirección**, **las cabeceras**, **el cuerpo tal cual** y su tipo de contenido. **No se guardan** el secreto ni las cabeceras que llevan credenciales.

---

## 7. Precondiciones y postcondiciones

| Tipo | Condición |
|---|---|
| Precondición | El secreto común está configurado en el entorno, y el aviso lo trae |
| Postcondición | El aviso está guardado, una fila por llegada. **Si es de un evento configurado** —registro, depósito u operación— y nombra un número, se aplica **una vez** aunque se reenvíe (`RN-SP-072` a `RN-SP-074`); **si no, nada más cambia**. **Si su `advertiser` no es el de ningún broker activo**, queda guardado **sin broker** y no cambia nada más, hasta que ese `advertiser` se cargue (`RN-SP-076`) |

---

## 8. Flujo principal

1. Llega un aviso, con el secreto.
2. El sistema comprueba que el secreto está configurado y que el aviso lo trae, **antes de guardar nada**.
3. Reconoce al broker por el `advertiser` del aviso.
4. Guarda el aviso entero, sin el secreto, con su `advertiser`.
5. Si el aviso es de un evento configurado, lo interpreta (`RN-SP-072` a `RN-SP-074`).
6. Responde que lo recibió.

---

## 9. Flujos alternativos

### FA-001 — El mismo aviso llega dos veces

**Se guarda dos veces**, y se responde las dos que se recibió. Hasta que `RF-SP-054` sepa qué identifica a un aviso, no hay forma segura de decir que dos son el mismo: dos depósitos iguales del mismo cliente el mismo día también se parecen. Y cuántas veces reenvía un broker es justo uno de los datos que hay que observar.

---

### FA-002 — El aviso no dice de qué broker es, o el que dice no se conoce

Falta el `advertiser`, viene dos veces, o no es el de ningún broker activo del catálogo. **Se guarda igual, sin broker**, con el `advertiser` que trajo —ninguno si faltaba o venía repetido—, **no se interpreta**, y se responde que se recibió (`RN-SP-076`).

### FA-003 — Se carga el `advertiser` de un broker

Una migración carga en el catálogo el `advertiser` de un broker, o vuelve a encender uno apagado. **Al arrancar**, el sistema busca los avisos guardados sin broker con ese `advertiser`, **se los asocia y los interpreta uno a uno, en el orden en que llegaron**, como si acabaran de llegar: un registro crea la cuenta, un depósito posterior la mueve, y una reentrega no se aplica dos veces. Un aviso cuya interpretación falla queda sin broker y se vuelve a intentar en el siguiente arranque; los demás siguen.

---

## 10. Excepciones

| ID | Situación | Resultado |
|---|---|---|
| `EX-001` | El aviso no trae el secreto, o trae otro | No autenticado, **sin guardar nada** |
| `EX-002` | El secreto no está configurado en este entorno | Servicio no disponible, **sin guardar nada** |
| ~~`EX-003`~~ | ~~El broker no existe en el catálogo o no está activo~~ — **retirada el 13-10-2026**: el aviso de un broker que no se reconoce se guarda sin broker (`FA-002`). El número queda consumido | — |
| `EX-004` | El cuerpo pasa del tamaño máximo | Rechazo, **sin guardar nada** |

---

## 11. Validaciones

| ID | Validación |
|---|---|
| `VAL-001` | El secreto coincide con el configurado, comparado sin delatar por el tiempo cuánto acierta |
| `VAL-002` | El cuerpo no pasa de 64 KiB |

---

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-SP-897` | Un aviso **con los datos en la dirección** y el secreto correcto se acepta, **sin sesión**, y queda guardado con su broker, su método, sus datos —**todos, también los repetidos**—, sus cabeceras, su origen y su hora; la respuesta no trae datos |
| `CA-SP-898` | Un aviso **con los datos en el cuerpo** guarda el cuerpo **exactamente como llegó**, con su tipo de contenido |
| `CA-SP-899` | Un aviso **de formulario** guarda el cuerpo tal cual, **sin mezclar** sus campos con los de la dirección |
| `CA-SP-900` | Un aviso **sin secreto o con uno equivocado** se rechaza como no autenticado y **no se guarda** |
| ~~`CA-SP-901`~~ | ~~El secreto de un broker no sirve para otro~~ — **retirado el 13-10-2026**: ya no hay secreto por broker |
| ~~`CA-SP-902`~~ | ~~Un broker sin secreto configurado responde servicio no disponible~~ — **retirado el 13-10-2026**: lo cubre `CA-SP-951` para el secreto común |
| ~~`CA-SP-903`~~ | ~~Un broker que no existe, o que no está activo, responde no encontrado~~ — **retirado el 13-10-2026**: se **invierte** en `CA-SP-1009` y `CA-SP-1011` |
| `CA-SP-904` | Un cuerpo de **más de 64 KiB** se rechaza y **no se guarda** |
| `CA-SP-905` | **El mismo aviso dos veces** se guarda **dos veces** |
| `CA-SP-906` | **El secreto no queda escrito en ninguna parte**: ni entre los datos guardados del aviso ni en el registro de peticiones, que guarda la dirección con el valor oculto; tampoco se guardan las cabeceras de credenciales |
| `CA-SP-907` | **Recibir no cambia nada más**: con un aviso **de un evento no configurado** que nombra una cuenta declarada, la cuenta sigue en `REGISTER` sin nombre de usuario, y su titular sigue en `FTD_PENDIENTE` |
| `CA-SP-946` | Por la **dirección común**, un aviso con el secreto común y `advertiser=iq_option` se guarda **a nombre de `IQOPTION`**, con el `advertiser` entre sus datos **y en el suyo propio** |
| `CA-SP-947` | `advertiser` se reconoce **sin distinguir mayúsculas**, y también **en el cuerpo**: de formulario o como campo de primer nivel de un JSON |
| ~~`CA-SP-948`~~ | ~~Sin `advertiser`, con dos, con uno que no está en el catálogo o con el de un broker apagado: no encontrado~~ — **retirado el 13-10-2026**: se **invierte** en `CA-SP-1009` a `CA-SP-1011` |
| `CA-SP-949` | Sin el secreto común, o con otro: no autenticado (`EX-001`), **aunque el `advertiser` sea válido**, y no se guarda |
| `CA-SP-950` | Con un `advertiser` desconocido y **sin** secreto la respuesta es `401`, no `404`: el catálogo no se puede sondear |
| `CA-SP-951` | Sin el secreto común configurado: servicio no disponible (`EX-002`) y no se guarda |
| ~~`CA-SP-952`~~ | ~~La ruta por nombre sigue funcionando con su secreto por broker~~ — **retirado el 13-10-2026**: se **invierte** en `CA-SP-1015` |
| `CA-SP-964` | Un **aviso de registro** —el evento configurado— con `trader_id` y `afftrack` crea la cuenta `CONSUMIDOR` **sin titular**, en `REGISTER`, con origen en la `VENDEDOR` de ese `afftrack` en ese broker; el aviso se guarda igual |
| `CA-SP-965` | Con un `afftrack` que no es de ninguna `VENDEDOR` de ese broker, la cuenta se crea **sin origen** |
| `CA-SP-966` | **El mismo registro dos veces no duplica la cuenta**; si la cuenta ya existía —declarada en la plataforma— sin origen, el aviso **se lo pone**; si ya tenía, no lo cambia, y su titular tampoco |
| `CA-SP-967` | **Cualquier otro evento, o ninguno configurado**, solo se guarda: no crea ni cambia cuentas |
| `CA-SP-968` | Un aviso de registro **sin `trader_id`** solo se guarda |
| `CA-SP-995` | Un **aviso de depósito** de una cuenta `CONSUMIDOR` cuyo titular espera en `FTD_PENDIENTE` pasa la cuenta a `FIRST_DEPOSIT` y al titular a `ACTIVO`, **con lo que compró al registrarse entregado**, y lo deja auditado |
| `CA-SP-996` | El depósito de una cuenta **sin titular** la pasa a `FIRST_DEPOSIT`, con su momento, y no toca a nadie |
| `CA-SP-997` | **Un segundo depósito no cambia nada**: ni el estado, ni el momento del primero, ni deja otra auditoría; y **a un titular que no está en `FTD_PENDIENTE`** —bloqueado, por ejemplo— el depósito no le cambia el estado ni le entrega nada |
| `CA-SP-998` | El depósito de una **`VENDEDOR`** o de un **número sin cuenta** solo se guarda: no la mueve ni crea ninguna |
| `CA-SP-999` | **Cada aviso de operación suma una** a la cuenta de ese número, sea del tipo que sea, con la primera y la última, **y se borra** (15-10-2026); operar **no** la mueve a `FIRST_DEPOSIT`; la de un número sin cuenta solo se guarda |
| `CA-SP-1000` | **Una reentrega de depósito** —mismo `event_id`, mismo broker— **se guarda y no se aplica dos veces**; el mismo `event_id` en otro broker es otro aviso. **La de operación cuenta otra vez** desde el 15-10-2026: el aviso contado se borró |
| `CA-SP-1001` | **Asociar una cuenta que ya tiene el depósito activa a su titular**: al registrarse por enlace con ese número —sale `ACTIVO`— y al asignarla administración |
| `CA-SP-1002` | Las cuentas devuelven **lo que avisó el broker**: el momento del primer depósito y las operaciones, con la primera y la última |
| ~~`CA-SP-908`~~ | ~~El broker se nombra en la dirección por su nombre, sin distinguir mayúsculas~~ — **retirado el 13-10-2026**: la ruta por nombre ya no existe |
| `CA-SP-1009` | Un aviso con el secreto común y un **`advertiser` que no está en el catálogo** responde que se recibió y queda guardado **sin broker**, con ese `advertiser`; **no se interpreta**: un aviso de registro así no crea ninguna cuenta |
| `CA-SP-1010` | **Sin `advertiser`, con dos, o con uno de más de 100 caracteres**, el aviso se guarda sin broker y **sin `advertiser`** |
| `CA-SP-1011` | Con el `advertiser` de un **broker apagado**, el aviso se guarda sin broker |
| `CA-SP-1012` | Todo aviso guarda **su `advertiser` aparte**, recortado y tal como vino, también cuando se reconoce, y también cuando llega **en el cuerpo** |
| `CA-SP-1013` | **Al arrancar**, los avisos sin broker cuyo `advertiser` ya es el de un broker activo **se le asocian y se interpretan en el orden en que llegaron**: un registro crea la cuenta `CONSUMIDOR` y el depósito que llegó después la pasa a `FIRST_DEPOSIT`; **una reentrega con el mismo `event_id` no se aplica dos veces** |
| `CA-SP-1014` | Al arrancar, los avisos cuyo `advertiser` **sigue sin conocerse** quedan sin broker, y **un aviso que ya tiene broker no se toca**, aunque otro broker tenga su mismo `advertiser` |
| `CA-SP-1015` | **La ruta con el nombre del broker ya no recibe**: un aviso a `/brokers/iqoption/notifications`, con cualquier secreto, **no se guarda** |
| `CA-SP-1026` | Un **aviso de registro** guarda en la cuenta **cuándo llegó y su país**, en mayúsculas; en una cuenta ya declarada en la plataforma los rellena; **una reentrega o un segundo registro no los cambian** |
| `CA-SP-1027` | **El país lo fija el primer aviso de la cuenta que lo trae** —también un depósito o una operación—, y uno posterior con otro país no lo cambia; **un valor que no son dos letras no se guarda** |
| `CA-SP-1028` | El depósito que pasa la cuenta a `FIRST_DEPOSIT` guarda **su importe como el del primer depósito**, con su moneda, y lo cuenta en el total |
| `CA-SP-1029` | **Un segundo depósito suma al total** y no cambia el del primero; **una reentrega no suma**; **uno en otra moneda no suma** |
| `CA-SP-1030` | **Un importe ausente, que no es un número, cero o negativo no se guarda**, y el depósito se aplica igual —estado y activación—; con más de dos decimales se redondea al céntimo |
| `CA-SP-1031` | Las cuentas devuelven en `activity` **la fecha del registro, el país, el primer depósito, el total y la moneda**, y **ya no devuelven `brokerUsername`** |

---

## 13. Casos límite

| Caso | Comportamiento |
|---|---|
| El broker avisa con un método distinto de los dos | No se admite, como cualquier ruta del sistema con un método que no declara |
| Un broker sin `advertiser` en el catálogo | Sus avisos se guardan sin broker y se le asocian el día que una migración lo cargue (`FA-003`) |
| El primer aviso de un broker nuevo llega mientras el sistema arranca y asocia los viejos | Se interpreta en cuanto llega, y los viejos en su orden: un depósito viejo que se aplica después del registro nuevo sigue moviendo la cuenta, porque el depósito solo mira que la cuenta exista |
| El aviso trae el secreto dos veces en la dirección | Se rechaza como sin secreto (`EX-001`): no se elige cuál vale |
| Un aviso sin datos de ningún tipo, solo con el secreto | Se guarda: puede ser la comprobación de que la dirección responde, que los paneles suelen ofrecer |
| Se cambia el secreto | Los avisos con el viejo se rechazan desde que el entorno toma el nuevo; los ya guardados no cambian |

---

## 14. Preguntas abiertas

**Cuánto se conservan los avisos.** Hoy, siempre: son pocos y son la base sobre la que se escribirá `RF-SP-054`. Si crecen, la retención será una decisión propia.

---

## 15. Control de cambios

| Versión | Fecha | Cambio | Autor |
|---|---|---|---|
| 0.1.0 | 08-10-2026 | Primera versión, por decisión del responsable del proyecto: recibir los avisos de `IQOPTION`, `EXNOVA` y `EXOPTION` sin saber todavía qué mandan ([`requirements/sp.md`](../../../requirements/sp.md) v1.107.0, `RN-SP-066`). Parte `RF-SP-054`: aquí se recibe y se guarda; allí se interpretará. Criterios `CA-SP-897` a `CA-SP-907`. | Responsable del proyecto |
| 0.2.0 | 08-10-2026 | **El broker se nombra por su nombre y no por su identificador**, a petición del responsable del proyecto («para no enviar el id, enviar el nombre»): una dirección legible en el panel. Nace `CA-SP-908`. Si un broker se renombra, su dirección cambia. | Responsable del proyecto |
| 0.3.0 | 09-10-2026 | **Una sola dirección, sin el nombre del broker** (`RN-SP-069`), a petición del responsable del proyecto («para no enviar el nombre del broker ya que este viene en la notificación»): secreto común y broker según el `advertiser`. La ruta por nombre se mantiene, obsoleta. Criterios `CA-SP-946` a `CA-SP-952`. | Responsable del proyecto |
| 0.4.0 | 09-10-2026 | **El aviso de registro crea la cuenta sin titular** (`RN-SP-070`, `RN-SP-072`): la primera interpretación de un aviso, y la única por ahora. Carga la parte de `RF-SP-054` que el responsable del proyecto pidió —«guardar ese registro pero sin usuario»—; el resto de `RF-SP-054` sigue pendiente. Criterios `CA-SP-964` a `CA-SP-968`. | Responsable del proyecto |
| 0.5.0 | 10-10-2026 | **El depósito y la operación** (`RN-SP-073`, `RN-SP-074`), por decisión del responsable del proyecto: el depósito es el FTD entero —cuenta a `FIRST_DEPOSIT`, titular de `FTD_PENDIENTE` a `ACTIVO` con lo que compró—; la operación se cuenta; de un número sin cuenta, solo se guarda; una reentrega con el mismo identificador no se aplica dos veces. Responde la idempotencia que `FA-001` dejó a `RF-SP-054`. Criterios `CA-SP-995` a `CA-SP-1002`; `CA-SP-907` se acota a los eventos no configurados. | Responsable del proyecto |
| 0.6.0 | 13-10-2026 | **Una sola dirección, y ningún aviso se pierde** (`RN-SP-076`), a petición del responsable del proyecto: «solo la ruta broker/notification, para capturar todas las notificaciones y desde ahí clasificarlas por el advertiser». El aviso que no se reconoce se guarda sin broker (`FA-002`) y se asocia e interpreta al arrancar cuando su `advertiser` se cargue (`FA-003`). Se retira la ruta por nombre con sus secretos por broker, y con ella `EX-003`, `CA-SP-901` a `CA-SP-903`, `CA-SP-908`, `CA-SP-948` y `CA-SP-952`. Criterios `CA-SP-1009` a `CA-SP-1015`. | Responsable del proyecto |
| 0.7.0 | 15-10-2026 | **Lo que trae el aviso se guarda en la cuenta** (`RN-SP-077`, §2.3), a petición del responsable del proyecto: la fecha del registro, el país y los depósitos —el primero y el total—; **se retira el nombre de usuario en el broker** (`RN-SP-040`); y **el aviso de operación contado se borra** (`RN-SP-074` enmendada; `CA-SP-999` y `CA-SP-1000` enmendados). Criterios `CA-SP-1026` a `CA-SP-1031`. | Responsable del proyecto |
