# PLAN — `RF-CM-005` Consultar la comisión efectiva

| Campo | Valor |
|---|---|
| Requerimiento | `RF-CM-005` |
| Especificación | [`spec.md`](spec.md) |
| `spec.md` aprobada el | 02-09-2026 |
| Versión | 0.3.0 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 02-09-2026 |

!!! info "Qué va en este documento"

    **Cómo se construye.** Las decisiones técnicas que la especificación deliberadamente no toma.

El comportamiento es el de [`spec.md`](spec.md) y no se repite. La mecánica común la fijó el plan de [`RF-CM-001`](../001-registrar-tasa-comision-rol/plan.md) y **este documento la hereda sin repetirla**.

---

## 1. Enfoque

**Una sola sentencia que resuelve la precedencia**, y un caso de uso que no ordena nada.

Es la decisión que gobierna todo lo demás de este plan, y la heredó de la v0.1.0 aunque el modelo haya cambiado por completo: **si el orden viviera en el flujo de control de Java, una reorganización podría alterarlo sin que nada falle**. No se rompería ninguna prueba de las que miran un caso a la vez; devolvería un porcentaje **plausible**. Y quien consuma esto va a pagar con esa cifra.

Lo que cambia es cómo se consigue. Con una tabla, la precedencia era un `ORDER BY` sobre dos columnas nulas. **Con dos tablas es un `UNION ALL` con la prioridad materializada en una columna constante**, y la precedencia sigue siendo el `ORDER BY`.

## 2. Cambios de esquema

**Ninguno.**

## 3. Componentes afectados

| Capa | Componente | Nuevo / Modificado | Responsabilidad |
|---|---|---|---|
| `domain/models` | `RateSource` | Nuevo | De cuál de las dos piezas salió la tasa |
| `domain/repository` | `CommissionResolutionRepository` y su adaptador | Nuevos | **La única sentencia que resuelve la precedencia** |
| `domain/service` | `ResolveCommissionService` | **Rehecho** | Comprueba, determina el rol y delega |
| `application` | `EffectiveCommissionResponse` | **Rehecho** | Los tres desenlaces, con la fuente |
| `interfaces` | `CommissionResolutionController` | **Modificado** | `GET /api/v1/commissions/effective` |

**Un puerto propio para la resolución y no un método más del puerto de consulta del catálogo**, porque cruza las tres tablas y no pertenece a ninguna. Es el mismo corte que separa el controlador: no devuelve una tasa del catálogo sino **una respuesta calculada**.

**`RateSource` es un tipo nuevo y no un `RateScope` reducido a dos valores.** El grado era una propiedad de la **tarifa** —deducida de qué campos traía—; la fuente es una propiedad de **la resolución**. Reciclar el nombre habría hecho creer que es el mismo concepto con menos casos.

## 4. La sentencia, y las cuatro cosas que hace

```
rama 0 · la personalizada     prioridad 0
rama 1 · el rol asociado      prioridad 1
                              ORDER BY prioridad LIMIT 1
```

1. **La rama de la persona no filtra por rol ni por producto**, y las dos ausencias son la regla: la tasa personalizada gana **venda lo que venda**, y desde el 01-09-2026 **ya no lleva rol**.
2. **La rama del rol exige la asociación.** Es `RN-CM-012`, y **aquí no hay ningún `OR ... IS NULL`** como en el modelo anterior — esa es exactamente la inversión de significado, escrita en SQL.
3. **El `JOIN` entra por la clave compuesta**, `(id, role_id)`: la consulta no puede leer el porcentaje de una tasa cuyo rol no sea el copiado en la asociación.
4. **Las dos ramas filtran las retiradas.** Una tasa retirada que siguiera resolviendo pagaría por algo que alguien declaró que no debió existir.

**El rol admite el nulo, y es deliberado.** Se pasa como parámetro y, cuando la persona no porta ninguno, apaga la rama del rol y deja respondiendo solo a la personalizada. Es lo que produce `FA-003` — y es la traducción exacta de lo que el diagrama de flujo del módulo ya dibujaba: **el rombo de la personalizada va antes que el del rol**.

### 4.1 Las dos columnas nuevas, y la única forma correcta de traerlas

Cada rama pasa a proyectar `rate_type` y las **dos** columnas del valor, y el `UNION ALL` exige que las dos ramas tengan la misma forma. Eso es rutina. Lo que no lo es:

!!! danger "El `COALESCE` va DESPUÉS del `LIMIT 1`, nunca dentro de las ramas"

    `spec.md` §6.2 decide que la respuesta lleva **un** campo de valor. La tentación es proyectar directamente `COALESCE(percentage, fixed_amount) AS value` en cada rama y ahorrarse el paso.

    **Y con eso se pierde la única información que hace legible el resultado.** Fundidas las dos columnas dentro de la rama, un `10` de salida ya no dice de cuál venía; habría que reconstruirlo a partir de `rate_type`, que es lo mismo que hacer el `COALESCE` fuera pero con un sitio más donde equivocarse.

    Peor: **el `CHECK` de `V49` garantiza que solo una está llena, y esa garantía se aprovecha una sola vez**. Aplicar `COALESCE` dos veces —una por rama y otra al armar la respuesta— no falla, pero deja dos sitios que hay que mantener de acuerdo cuando alguien añada una tercera forma.

    La sentencia devuelve **`rate_type`, `percentage` y `fixed_amount` tal cual**, y **quien arma la respuesta** los funde en un campo con su forma. Es una decisión de proyección, no de consulta.

**Y las dos columnas se leen de la fila que ganó, no de una consulta posterior.** `spec.md` §8 lo exige: buscar la forma después, sabiendo ya qué tasa ganó, permitiría leer una fila distinta si alguien la corrigió entre medias. El `LIMIT 1` trae la fila entera o no trae nada.

## 5. Qué decide el caso de uso, y qué no

**No decide la precedencia.** Comprueba que la persona y el producto existen, busca el rol vendedor —que puede no haber—, y delega.

**Sí decide cómo se clasifica la ausencia**, y eso no contradice lo anterior: cuando la sentencia no devuelve nada, hay que distinguir «no comisiona» de «sin tarifa», y esa distinción **no es de precedencia sino del actor**. Se resuelve mirando si había rol, después de preguntar.

## 6. Contrato de API

`GET /api/v1/commissions/effective` · `200 OK` en los **tres** desenlaces.

| Estado | Cuándo |
|---|---|
| `400` | Parámetros inválidos |
| `403` | Sin el permiso `commissions:read` |
| `422` | `EX-001`, `EX-002`: la persona o el producto no existen |

**Los tres desenlaces son `200` y ninguno es un error.** Convertir «sin tarifa» en `404` obligaría a quien liquide a tratar como excepción el caso más común de un sistema recién configurado.

**`value` y `rateType` viajan siempre presentes, aunque sean nulos.** Un campo que desaparece del resultado es indistinguible de uno que el cliente no conoce, y aquí la diferencia entre nulo y cero es la diferencia entre lo olvidado y lo decidido.

!!! warning "`percentage` desaparece del contrato y lo sustituye `value`: es un cambio incompatible, y hay que romperlo así"

    Renombrar el campo rompe a cualquiera que lo lea. La alternativa compatible existe —conservar `percentage` y añadir `fixedAmount`— y es justo la que `spec.md` §6.2 descarta: **devolvería el nulo con dos causas** y el aviso que impide pagar cero donde no había tarifa dejaría de sostenerse.

    Conservar `percentage` **con el importe fijo dentro** sería peor todavía: el campo mentiría en su nombre.

    De modo que se renombra. El coste es acotado —el módulo no tiene consumidores fuera del proyecto— y el cambio **rompe ruidosamente**, que es la manera correcta de romper: quien lea `percentage` obtendrá un campo que no existe, no un número equivocado.

**Este contrato deja de parecerse al del catálogo a propósito**, y la documentación publicada tiene que decirlo: `GET /commission-rates` devuelve `rateType` con `percentage` o `fixedAmount`; esta consulta devuelve `rateType` con `value`. Un consumidor que use las dos lo notará, y sin una frase que lo explique lo leerá como una inconsistencia.

**Controlador aparte del de tasas**, porque es otro recurso. Es el mismo corte que separó la oferta propia del catálogo en `PM`.

## 7. Autorización

Permiso `commissions:read`. **Solo administrativo**, por decisión del responsable del proyecto (`cm.md` v0.2.0): que un vendedor consulte la suya es otro actor y depende de **D-22**.

## 8. Auditoría

**Ninguna.** Es una consulta.

## 9. De qué depende esta resolución para ser determinista

Dos reglas **de fuera de este requerimiento**, y conviene que estén escritas juntas:

| Regla | Qué pasaría sin ella |
|---|---|
| `RN-SP-025` — una persona no tiene dos roles vendedores | «El rol vendedor de la persona» dejaría de ser una pregunta con una sola respuesta, y la resolución **elegiría en silencio** |
| `RN-CM-006` — una sola personalizada viva por persona y día | Dos tasas cubriendo el mismo día harían que la rama 0 devolviera dos filas y el `LIMIT 1` **eligiera una cualquiera** |

Ninguna de las dos se comprueba aquí, y ninguna debe: son invariantes que sostienen otros. Lo que este plan hace es **nombrarlas**, para que quien las toque sepa qué se lleva por delante.

## 10. Impacto sobre otros módulos

**Ninguno en el código.** Se consumen `UserCatalog`, `SellerRoleCatalog` y `ProductCatalog`, que `SP` y `PM` ya publican.

## 11. Alternativas consideradas

| Alternativa | Por qué se descartó |
|---|---|
| **Dos consultas encadenadas en Java** | Habrían dado el mismo resultado hoy y puesto la regla en un `if`, donde **nada la protege** de que alguien invierta el orden mientras arregla otra cosa. No fallaría: pagaría mal |
| Cortar antes de resolver si la persona no porta rol vendedor | Es lo que hacía la v0.1.0, y **escondería `FA-003`**: la personalizada de quien dejó de vender seguiría rigiendo y esta consulta diría que no comisiona |
| Devolver **cero** cuando no hay tasa | Hace indistinguible lo pensado de lo olvidado. Y quien consuma esto paga con esa cifra |
| Devolver `404` en «sin tarifa» | Convierte en excepción el caso más común de un sistema recién configurado |
| Fundir «sin tarifa» y «no comisiona» | Son datos distintos: uno es un olvido de configuración, el otro una propiedad del actor |
| Reutilizar `RateScope` con dos valores | El grado era de la tarifa; la fuente es de la resolución. El nombre habría mentido |
| Resolver la cadena entera aquí | Este requerimiento resuelve **una persona**. El override es llamarlo una vez por nivel, y quien recorra la cadena es quien liquide |
| Devolver también la vigencia de la tasa de rol | No la tiene. Inventar una fecha sería peor que devolver nulo |
| **`percentage` y `fixedAmount` separados**, como en el catálogo | El nulo pasaría a tener dos causas y el aviso de `spec.md` §6.2 dejaría de poder escribirse. Decisión del responsable del proyecto |
| Conservar el nombre `percentage` **con el importe fijo dentro** | El campo mentiría en su nombre. Romper ruidosamente es preferible a devolver un número que se lee mal |
| **`COALESCE` dentro de cada rama** del `UNION ALL` | Pierde de qué columna venía el valor y duplica la fusión en dos sitios. Ver §4.1 |
| Traer la forma en una **segunda consulta**, sabiendo ya qué tasa ganó | Puede leer una fila distinta de la que decidió la precedencia. `spec.md` §8 |
| **Devolver la moneda del producto** junto al importe fijo | Se preguntó al responsable del proyecto y se descartó: empezaría a mezclar la tarifa con la venta (`cm.md` §1.4). `spec.md` §14 recoge lo que cuesta |
| Rechazar la resolución cuando el importe fijo **supera el precio del producto** | Esta consulta no calcula (`cm.md` §1.4), y `RN-CM-018` deja ese tope a la liquidación — que debe **rechazar y no recortar** |

## 12. Riesgos

| # | Riesgo | Mitigación |
|---|---|---|
| 1 | **La precedencia acabe en el flujo de control** por una refactorización | Vive en la sentencia. `CA-CM-042` y `CA-CM-043` la fijan desde fuera |
| 2 | Alguien lea un rol nulo con desenlace resuelto como un fallo | `FA-003` y `CA-CM-045`, y el contrato publicado lo explica |
| 3 | «Sin tarifa» se interprete como «nadie declaró la tasa» | La descripción publicada dice que la causa más probable es **que nadie la asoció** |
| 4 | `RN-SP-025` o `RN-CM-006` se relajen sin que nadie lo relacione con esto | §9 las nombra. **No es una mitigación técnica**, y no la hay |
| 5 | **La precedencia se invierta al reescribir la sentencia** para las columnas nuevas | `CA-CM-101`, que **cruza las formas** entre las dos ramas. Las pruebas de precedencia existentes usan la misma forma en ambas y **seguirían pasando** |
| 6 | El `COALESCE` acabe dentro de las ramas «para simplificar» | §4.1. No falla y deja dos sitios que mantener de acuerdo |
| 7 | Un consumidor lea `percentage` y obtenga un campo inexistente | **Buscado.** Es la manera correcta de romper: ruidosa. Ver §6 |
| 8 | **Se pague un importe fijo en una moneda que nadie previó** | **No se mitiga**, y esta consulta es donde se podría haber mitigado. Decisión del responsable del proyecto (`spec.md` §14); `CA-CM-104` lo fija como comportamiento esperado |

## 13. Estrategia de prueba

| Qué | Nivel | Detalle |
|---|---|---|
| Resolución por el rol asociado | Integración | `CA-CM-038` |
| **La tasa sin asociar no paga** | Integración | `CA-CM-039`. Es la prueba que clava la inversión de significado: si alguien la deshiciera, el sistema pagaría por productos que nadie configuró **y no fallaría** |
| La asociación es por producto y por rol | Integración | `CA-CM-040`, `CA-CM-041` |
| La personalizada gana | Integración | `CA-CM-042`, `CA-CM-043` |
| La personalizada vencida | Integración | `CA-CM-044` |
| **Quien no vende cobra su personalizada** | Integración | `CA-CM-045`, con el rol **nulo** en la respuesta |
| No comisiona | Integración | `CA-CM-046` |
| El cero resuelve | Integración | `CA-CM-047` |
| **Tasa retirada con asociación viva** | Integración | `CA-CM-048`. Se **siembra a mano** el estado que `RN-CM-015` impide, para dejar constancia de por qué esa regla existe |
| Producto retirado | Integración | `CA-CM-049` |
| Fecha por omisión e inexistentes | Integración | `CA-CM-050` |

| **Resuelve un importe fijo** | Integración | `CA-CM-100`: forma y valor, en **un** campo de valor |
| **La precedencia con formas cruzadas** | Integración | `CA-CM-101`: personalizada en porcentaje contra rol en importe fijo, **y al revés**. Ver abajo |
| El nulo sigue teniendo una sola causa | Integración | `CA-CM-102`: sin tasa, forma y valor **nulos y presentes** |
| Cero en importe fijo | Integración | `CA-CM-103` |
| **Dos monedas, la misma respuesta** | Integración | `CA-CM-104`: y **ninguna señal**. Es lo que `spec.md` §14 acepta |

!!! danger "`CA-CM-101` protege lo viejo de lo nuevo, y es el único criterio de esta versión que no prueba una funcionalidad"

    Las pruebas de precedencia que ya existen —`CA-CM-042`, `CA-CM-043`, `CA-CM-044`— usan **la misma forma en las dos ramas**, porque cuando se escribieron solo había una.

    De modo que una reescritura de la sentencia que invirtiera las ramas al añadir las columnas **las pasaría todas**. Devolvería la tasa equivocada con una cifra plausible, que es el defecto que `spec.md` §2 lleva describiendo desde la v0.1.0: **no falla, paga mal**.

    `CA-CM-101` cruza las formas a propósito, en las dos direcciones. Es barata y es la única que se entera.

**`CA-CM-048` es la única prueba del módulo que construye a mano un estado que el sistema no permite alcanzar.** No comprueba un comportamiento que alguien vaya a usar: comprueba **qué pasaría si `RN-CM-015` no existiera**, y por eso vale la pena tenerla — es la evidencia de que esa regla no es una precaución teórica.
