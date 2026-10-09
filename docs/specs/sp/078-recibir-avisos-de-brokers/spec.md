# SPEC — `RF-SP-078` Recibir los avisos de los brokers

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-078` |
| Módulo | `SP` — Sistema Principal |
| Versión | 0.3.0 |
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

**Cómo se autentica el broker**, la única pregunta de `RF-SP-054` que se responde sin ver un aviso: con **un secreto propio de cada broker que va en la dirección** a la que avisa (`RN-SP-066`). Una firma sería mejor y no está disponible; una cabecera, tampoco, porque los paneles de afiliados solo dejan escribir la dirección. Una lista de direcciones de origen no resiste que el broker cambie de servidor.

---

## 3. Actores

| Actor | Rol en esta funcionalidad |
|---|---|
| Un broker del catálogo | Avisa; no tiene sesión y se identifica por su secreto |

---

## 4. Alcance

### 4.1 Incluye

- Recibir avisos **con los datos en la dirección y con los datos en el cuerpo**, porque no se sabe cuál de las dos formas usará cada broker.
- Comprobar que el aviso es del broker que dice ser, **antes de guardar nada**.
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
| `RN-SP-066` | El aviso se guarda entero y sin interpretar; lo autentica un secreto por broker en la dirección; el secreto no se guarda; los repetidos no se descartan; no se edita ni se borra |
| `RN-SP-039` | El catálogo de brokers no se administra por la API: un broker que no está en él, o no está activo, no puede avisar |
| `RN-SP-069` | Por la dirección común, el broker lo dice el `advertiser` del aviso, con un solo secreto para todos |

**Una regla nueva**, `RN-SP-066`.

---

## 6. Datos

### 6.1 Entrada

**Lo que el broker mande, como lo mande**: datos en la dirección, en el cuerpo o en los dos, con cualquier tipo de contenido. Además, **el secreto**, en la dirección, y **qué broker es**: por la dirección común, en el **`advertiser`** del propio aviso (`RN-SP-069`); por la obsoleta, en la ruta.

### 6.2 Salida

Un acuse de recibo, **sin datos**: el broker solo necesita saber que se recibió.

### 6.3 Lo que se guarda

Qué broker avisó, cuándo, desde qué dirección de red, con qué método, **los datos de la dirección**, **las cabeceras**, **el cuerpo tal cual** y su tipo de contenido. **No se guardan** el secreto ni las cabeceras que llevan credenciales.

---

## 7. Precondiciones y postcondiciones

| Tipo | Condición |
|---|---|
| Precondición | El broker existe en el catálogo y está activo; su secreto está configurado en el entorno; el aviso trae ese secreto |
| Postcondición | El aviso está guardado, una fila por llegada. **Nada más cambia en el sistema**: ni cuentas, ni personas, ni ventas |

---

## 8. Flujo principal

1. Llega un aviso para un broker, con su secreto.
2. El sistema comprueba que el broker existe y está activo, que su secreto está configurado y que el aviso trae ese secreto, **antes de guardar nada**.
3. Guarda el aviso entero, sin el secreto.
4. Responde que lo recibió.

---

## 9. Flujos alternativos

### FA-001 — El mismo aviso llega dos veces

**Se guarda dos veces**, y se responde las dos que se recibió. Hasta que `RF-SP-054` sepa qué identifica a un aviso, no hay forma segura de decir que dos son el mismo: dos depósitos iguales del mismo cliente el mismo día también se parecen. Y cuántas veces reenvía un broker es justo uno de los datos que hay que observar.

---

## 10. Excepciones

| ID | Situación | Resultado |
|---|---|---|
| `EX-001` | El aviso no trae el secreto, o trae otro —también el de otro broker— | No autenticado, **sin guardar nada** |
| `EX-002` | El secreto de ese broker no está configurado en este entorno | Servicio no disponible, **sin guardar nada** |
| `EX-003` | El broker no existe en el catálogo o no está activo; por la dirección común, **el aviso no trae `advertiser`, lo trae repetido o no es el de ningún broker activo** | No encontrado, **sin guardar nada** |
| `EX-004` | El cuerpo pasa del tamaño máximo | Rechazo, **sin guardar nada** |

---

## 11. Validaciones

| ID | Validación |
|---|---|
| `VAL-001` | El secreto coincide con el configurado para ese broker, comparado sin delatar por el tiempo cuánto acierta |
| `VAL-002` | El cuerpo no pasa de 64 KiB |

---

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-SP-897` | Un aviso **con los datos en la dirección** y el secreto correcto se acepta, **sin sesión**, y queda guardado con su broker, su método, sus datos —**todos, también los repetidos**—, sus cabeceras, su origen y su hora; la respuesta no trae datos |
| `CA-SP-898` | Un aviso **con los datos en el cuerpo** guarda el cuerpo **exactamente como llegó**, con su tipo de contenido |
| `CA-SP-899` | Un aviso **de formulario** guarda el cuerpo tal cual, **sin mezclar** sus campos con los de la dirección |
| `CA-SP-900` | Un aviso **sin secreto o con uno equivocado** se rechaza como no autenticado y **no se guarda** |
| `CA-SP-901` | **El secreto de un broker no sirve para otro**: con el de `EXNOVA`, la ruta de `IQOPTION` lo rechaza y no guarda nada |
| `CA-SP-902` | Un broker **sin secreto configurado** responde servicio no disponible y **no guarda nada**, aunque el aviso traiga un secreto |
| `CA-SP-903` | Un broker **que no existe** en el catálogo, o que **no está activo**, responde no encontrado y **no guarda nada** |
| `CA-SP-904` | Un cuerpo de **más de 64 KiB** se rechaza y **no se guarda** |
| `CA-SP-905` | **El mismo aviso dos veces** se guarda **dos veces** |
| `CA-SP-906` | **El secreto no queda escrito en ninguna parte**: ni entre los datos guardados del aviso ni en el registro de peticiones, que guarda la dirección con el valor oculto; tampoco se guardan las cabeceras de credenciales |
| `CA-SP-907` | **Recibir no cambia nada más**: con un aviso que nombra una cuenta declarada, la cuenta sigue en `REGISTER` sin nombre de usuario, y su titular sigue en `FTD_PENDIENTE` |
| `CA-SP-946` | Por la **dirección común**, un aviso con el secreto común y `advertiser=iq_option` se guarda **a nombre de `IQOPTION`**, con el `advertiser` entre sus datos |
| `CA-SP-947` | `advertiser` se reconoce **sin distinguir mayúsculas**, y también **en el cuerpo**: de formulario o como campo de primer nivel de un JSON |
| `CA-SP-948` | Sin `advertiser`, con dos, con uno que no está en el catálogo o con el de un broker apagado: no encontrado (`EX-003`) y **no se guarda nada** |
| `CA-SP-949` | Sin el secreto común, o con el de un broker de la ruta por nombre: no autenticado (`EX-001`), **aunque el `advertiser` sea válido**, y no se guarda |
| `CA-SP-950` | Con un `advertiser` desconocido y **sin** secreto la respuesta es `401`, no `404`: el catálogo no se puede sondear |
| `CA-SP-951` | Sin el secreto común configurado: servicio no disponible (`EX-002`) y no se guarda |
| `CA-SP-952` | **La ruta por nombre sigue funcionando** con su secreto por broker |
| `CA-SP-908` | **El broker se nombra en la dirección por su nombre, sin distinguir mayúsculas**: `iqoption` e `IQOPTION` son el mismo broker, y su secreto vale para los dos |

---

## 13. Casos límite

| Caso | Comportamiento |
|---|---|
| El broker avisa con un método distinto de los dos | No se admite, como cualquier ruta del sistema con un método que no declara |
| Un broker sin `advertiser` en el catálogo | No puede usar la dirección común; usa la obsoleta hasta que una migración lo cargue |
| El aviso trae el secreto dos veces en la dirección | Se rechaza como sin secreto (`EX-001`): no se elige cuál vale |
| Un aviso sin datos de ningún tipo, solo con el secreto | Se guarda: puede ser la comprobación de que la dirección responde, que los paneles suelen ofrecer |
| Se cambia el secreto de un broker | Los avisos con el viejo se rechazan desde que el entorno toma el nuevo; los ya guardados no cambian |

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
