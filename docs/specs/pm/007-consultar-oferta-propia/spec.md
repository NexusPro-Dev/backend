# SPEC — `RF-PM-007` Consultar la oferta disponible para uno mismo

| Campo | Valor |
|---|---|
| Requerimiento | `RF-PM-007` |
| Módulo | `PM` — Productos y Mercadeo |
| Estado | **Borrador** |
| Autor | Responsable técnico |
| Aprobada por | — |
| Fecha de aprobación | — |

---

## 1. Objetivo

Que cada persona vea qué puede comprar, sin que sea el navegador quien decida la regla.

## 2. Contexto

El catálogo de `RF-PM-002` lo lee quien administra y contiene todo. Lo que un cliente puede comprar es **un subconjunto que depende de él**: los upgrades solo tienen sentido hacia niveles por encima del suyo, y ofrecerle uno hacia el nivel que ya tiene —o hacia uno inferior— es ofrecerle pagar por nada.

**Esa regla vive en el servidor o no vive.** Si la interfaz filtrara el catálogo por su cuenta, cada pantalla que muestre productos tendría que repetir el mismo cálculo, y la que se quedara atrás **no fallaría: ofrecería de más**. Es la misma decisión que `RF-SP-039` tomó al publicar los permisos efectivos del actor en lugar de dejar que el navegador los dedujera.

**No admite parámetro de persona.** Responde sobre quien llama y sobre nadie más. Un parámetro convertiría esta consulta en «qué puede comprar fulano», que es una pregunta sobre un tercero y que hoy nadie ha decidido quién puede hacer.

## 3. Actores

| Actor | Rol en esta funcionalidad |
|---|---|
| Cualquier persona autenticada | Consulta lo que ella misma puede comprar. **No exige permiso**: exigir el de lectura de productos daría a cada cliente el catálogo entero para que pudiera ver tres líneas |

## 4. Alcance

### 4.1 Incluye

- Devolver los productos **activos** que el actor puede comprar hoy.
- De los upgrades, **solo los que llevan a un nivel superior** al que el actor tiene.
- Los servicios del sistema que estén activos.

### 4.2 No incluye

- **Comprar.** Esta consulta no inicia ninguna compra ni reserva nada.
- **La oferta de un tercero.** Ni con parámetro, ni con permiso: no existe.
- **Los productos inactivos o retirados**, ni el motivo por el que se retiraron.
- **El catálogo completo**, que es `RF-PM-002` y exige permiso.

## 5. Reglas de negocio aplicables

| ID | Regla | Origen |
|---|---|---|
| `RN-PM-009` | Solo se ofrece lo activo | `requirements/pm.md` §5.1 |
| `RN-PM-011` | Un upgrade se ofrece solo hacia arriba | `requirements/pm.md` §5.1 |
| `RN-SP-018` | Todo consumidor tiene membresía | `requirements/sp.md` §5.1 |

## 6. Datos

### 6.1 Entrada

**Ninguna.** La consulta no admite parámetros: ni de persona, ni de filtro, ni de paginación. El actor sale del token de la sesión.

### 6.2 Salida

| Dato | Descripción |
|---|---|
| Productos ofrecibles | Identificador, tipo, nombre, descripción y precio con su moneda |
| Membresía destino | En los upgrades: código, nombre y **nivel**, para que quien mira entienda a dónde sube |
| Nivel actual del actor | Cuál es su membresía hoy, o que no tiene ninguna |

## 7. Precondiciones y postcondiciones

**Precondiciones**

- El actor está autenticado.

**Postcondiciones**

- Ninguna: la consulta no modifica nada.

## 8. Flujo principal

1. El actor pide su oferta.
2. El sistema resuelve **su membresía vigente** y el nivel de esta.
3. El sistema toma los productos activos.
4. De los upgrades, conserva solo aquellos cuyo destino está en un nivel **superior** al del actor.
5. El sistema devuelve los productos resultantes junto con el nivel actual del actor.

## 9. Flujos alternativos

### FA-001 — El actor no tiene membresía

**Cuándo ocurre:** quien consulta no es consumidor —un funcionario, un vendedor— y por tanto no tiene nivel (`RN-SP-018`).

1. **No se le ofrece ningún upgrade**: no hay nivel desde el que subir, y ofrecerle el primero sería venderle una membresía, que no es lo que un upgrade hace.
2. Los servicios activos **sí** se le ofrecen, si la pregunta 1 de §14 se resuelve así.

### FA-002 — El actor está en el nivel más alto

**Cuándo ocurre:** su membresía es la cima de la cadena.

1. Ningún upgrade lleva más arriba, de modo que la lista de upgrades llega vacía.
2. **No es un error ni un mensaje especial**: es una lista vacía, y la interfaz decide qué decir.

### FA-003 — Su membresía está vencida

**Cuándo ocurre:** la membresía del actor tiene fecha de fin y ya pasó.

1. La membresía **no está vigente**, de modo que a efectos de esta consulta el actor no tiene nivel, y se aplica `FA-001`.
2. Vencer no es lo mismo que no tener, pero para decidir «a dónde puede subir» produce el mismo resultado, y conviene que esté escrito en lugar de deducido.

## 10. Excepciones

Ninguna propia. Un actor sin sesión válida se rechaza por la autenticación, que es transversal y no de este requerimiento.

## 11. Validaciones

Ninguna: la consulta no admite entrada.

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-PM-058` | El sistema devuelve solo productos **activos**: ni inactivos, ni retirados |
| `CA-PM-059` | El sistema ofrece a un actor de nivel intermedio **solo los upgrades hacia niveles superiores** al suyo |
| `CA-PM-060` | El sistema **no ofrece** el upgrade hacia el nivel que el actor ya tiene |
| `CA-PM-061` | El sistema **no ofrece** upgrades hacia niveles inferiores al del actor |
| `CA-PM-062` | El sistema devuelve la lista de upgrades vacía a quien está en el nivel más alto de la cadena |
| `CA-PM-063` | El sistema no ofrece ningún upgrade a quien no tiene membresía vigente, incluida la vencida |
| `CA-PM-064` | El sistema devuelve el nivel actual del actor junto con su oferta |
| `CA-PM-065` | El sistema responde **sin exigir ningún permiso**, a cualquier persona autenticada |
| `CA-PM-066` | El sistema **no admite ningún parámetro**: enviarlos no cambia la respuesta ni permite consultar la oferta de otra persona |
| `CA-PM-067` | El sistema no devuelve el motivo de retiro de ningún producto, ni la membresía de terceros |

## 13. Casos límite

- **La cadena se reordena entre dos consultas:** insertar una membresía intermedia (`RN-SP-007`) cambia los niveles de las demás. La oferta se calcula con los niveles **del momento de la consulta**; que la lista cambie de una consulta a otra sin que nadie tocara los productos es correcto y debe estar escrito.
- **Actor con membresía vigente cuyo nivel es el único de la cadena:** no hay ni arriba ni abajo; la lista de upgrades llega vacía.
- **Dos upgrades activos hacia niveles distintos, ambos superiores:** se ofrecen los dos. `RN-PM-004` acota un upgrade por **destino**, no uno en total.
- **Un upgrade se desactiva mientras el actor mira la pantalla:** la consulta siguiente ya no lo trae. No hay reserva ni bloqueo: esta consulta no promete que lo que devuelve seguirá disponible.
- **Actor sin ningún rol de consumidor pero con membresía:** `RN-SP-018` lo hace imposible. Se enumera para que quede escrito que no se defiende ese caso.

## 14. Preguntas abiertas

| # | Pregunta | Responsable | Estado |
|---|---|---|---|
| 1 | **¿Qué ve quien no es consumidor?** Un funcionario o un vendedor no tiene nivel. ¿Se le ofrecen los servicios del sistema —y por tanto puede comprarlos— o su oferta llega vacía porque no es el destinatario comercial de la plataforma? | Responsable del proyecto | **Abierta** |
| 2 | **¿Los servicios dependen también del nivel?** Hoy un servicio se ofrece a todo el mundo por igual. Si mañana hubiera servicios «solo para nivel oro», eso es una regla nueva y una relación nueva entre producto y membresía, no un filtro más | Responsable del proyecto | **Abierta** |
| 3 | **¿La oferta incluye el precio con algún ajuste por el nivel del actor?** Es lo que pediría cualquier esquema de precios por nivel, y es exactamente la puerta de entrada de las promociones, que §1.3 de `requirements/pm.md` deja fuera | Responsable del proyecto | **Abierta** |
| 4 | **¿Se ofrecen todos los upgrades superiores o solo el siguiente?** Ofrecer todos permite saltar dos niveles de golpe pagando un solo producto; ofrecer solo el inmediato obliga a subir escalón a escalón. Las dos son decisiones comerciales legítimas y producen sistemas distintos | Responsable del proyecto | **Abierta** |
| 5 | **¿Esta consulta se pagina?** Hoy no: el catálogo ofrecible a una persona es corto. Con muchos servicios dejaría de serlo, y añadir paginación después cambia la forma de la respuesta para todos los clientes | Responsable técnico | **Abierta** |

**Una spec con preguntas abiertas no puede aprobarse.** Esta sección debe quedar vacía antes de pasar la compuerta.
