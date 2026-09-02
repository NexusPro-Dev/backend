# PLAN — `RF-CM-002` Consultar las tasas de comisión

| Campo | Valor |
|---|---|
| Requerimiento | `RF-CM-002` |
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

Cuatro consultas de solo lectura, dos paginadas y dos no.

**La decisión de fondo la tomó `spec.md`** —cuatro lecturas y no una— y lo que queda aquí es cómo se resuelve lo de fuera sin cruzar la frontera de código de **D-25**, y una trampa de paginación que no se ve hasta que se cae en ella.

## 2. Cambios de esquema

**Ninguno.** `V48` deja los índices que estas consultas necesitan: `idx_commission_rates_role` para el filtro del catálogo, `idx_product_commission_rates_rate` para la lectura por tasa, y la clave primaria de la asociación para la lectura por producto.

## 3. Componentes afectados

| Capa | Componente | Nuevo / Modificado | Responsabilidad |
|---|---|---|---|
| `domain/repository` | `CommissionRateQueryRepository` y su adaptador | **Rehechos** | El catálogo, con la cuenta de asociaciones |
| `domain/repository` | `UserCommissionRateQueryRepository` y su adaptador | Nuevos | Las personalizadas, con vigencia |
| `domain/repository` | `ProductCommissionRateQueryRepository` y su adaptador | Nuevos | Las dos direcciones de la asociación |
| `domain/service` | `ListCommissionRatesService` | **Rehecho** | El catálogo |
| `domain/service` | `ListUserCommissionRatesService` | Nuevo | Las personalizadas |
| `domain/service` | `ListProductAssociationsService` | Nuevo | Las dos lecturas de la asociación |
| `application` | Los DTO de página e ítem de cada listado | **Rehechos y nuevos** | — |
| `interfaces` | `CommissionRateController` | **Modificado** | El catálogo y las asociaciones de una tasa |
| `interfaces` | `UserCommissionRateController` | Nuevo | Las personalizadas |
| `interfaces` | `ProductCommissionRateController` | Nuevo | Las asociaciones de un producto |

**Tres puertos de consulta y no uno con tres métodos**, porque las tres filas que devuelven no se parecen: una tiene asociaciones y no vigencia, otra vigencia y no rol, y la tercera ni siquiera tiene identificador propio. Un puerto único devolvería un tipo con la mitad de los campos vacíos en cada caso.

## 4. Cómo se resuelve lo de otros módulos

**Con `LEFT JOIN` en la misma sentencia, no llamando a los puertos publicados fila a fila.**

Resolver el rol, la persona y el producto contra `SP` y `PM` uno a uno sería el problema de las `N+1` consultas —cien tasas, cien llamadas—, y **eso no lo arregla que las llamadas sean a un puerto en lugar de a una tabla**.

**Y no rompe la frontera de D-25.** Lo que `modules.md` §7 defiende es la frontera del **código**: estas clases no importan repositorios ni entidades de otro módulo. El `JOIN` y las claves foráneas son integridad y lectura **declaradas en el motor**, que es donde `PM` ya se apoya para hablar de `SP`. La regla de ArchUnit lo ancla: sin ella la frontera sería una convención.

!!! warning "La lectura por producto une `commission_rates` por su clave COMPUESTA"

    El `JOIN` no es `c.id = a.commission_rate_id` sino **`c.id = a.commission_rate_id AND c.role_id = a.role_id`** — la misma pareja de columnas que declara la clave foránea.

    Así la consulta **no puede** leer el porcentaje de una tasa cuyo rol no sea el copiado, ni siquiera si algún día alguien lograra escribir esa fila. Es defensa redundante con el esquema, y redundar aquí cuesta cero.

## 5. La trampa de la cuenta de asociaciones

`associatedProducts` se resuelve con una **subconsulta correlacionada** y no con un `LEFT JOIN` agrupado, y esta es la decisión que más fácil se toma mal.

Con un `LEFT JOIN` sobre la asociación, **cada tasa aparecería una vez por producto**. Habría que agrupar, y el `LIMIT` de la paginación se aplicaría a las filas del **producto cartesiano** en lugar de a las tasas: pedir veinte tasas devolvería menos de veinte, y el total no cuadraría con el contenido.

**El síntoma no se parece a la causa**: nadie miraría la cuenta de asociaciones al investigar por qué la paginación devuelve de menos. De ahí que `CA-CM-011` no verifique que el número sea correcto —eso lo hace `CA-CM-010`— sino que **la tasa aparezca una sola vez**.

## 6. Contrato de API

| Verbo y ruta | Devuelve |
|---|---|
| `GET /api/v1/commission-rates` | Página del catálogo |
| `GET /api/v1/user-commission-rates` | Página de personalizadas |
| `GET /api/v1/commission-rates/{id}/products` | Las asociaciones de esa tasa |
| `GET /api/v1/product-commission-rates?productId=` | Las asociaciones de ese producto |

Todas `200 OK`; `400` por parámetros inválidos y `403` sin el permiso `commissions:read`.

**La lectura por producto tiene recurso raíz propio y no es `/commission-rates/by-product/{id}`.** Ese camino habría competido en forma con `/commission-rates/{id}`: Spring resuelve antes el segmento literal y funcionaría, pero **el día que alguien lo renombrara el síntoma sería un `400` por identificador inválido en una ruta que nadie tocó**. Es el mismo riesgo que `PM` aceptó a propósito en `/products/available` —y allí con una prueba que lo vigila—; aquí se evita en lugar de vigilarse, porque no había motivo para colgarlo de ese recurso.

**Las dos colecciones de asociaciones viajan envueltas** en lugar de ser un array en la raíz. Hoy no se paginan (`spec.md` §13), y el día que haga falta, añadir los campos de paginación junto al contenido no rompe a nadie. Devolver el array desnudo obligaría a un cambio incompatible.

## 7. Orden y paginación

| Listado | Orden | Por qué no lo elige el cliente |
|---|---|---|
| Catálogo | Código de rol ascendente, **luego la forma**, y dentro de cada forma el valor de mayor a menor | Un catálogo se lee **agrupado por a quién paga**, y desde v0.3.0 también **por en qué paga** |
| Personalizadas | Inicio de vigencia descendente, desempate por identificador | El historial se lee **del presente hacia atrás** |
| Asociaciones de una tasa | Código de producto | — |
| Asociaciones de un producto | Código de rol | — |

**El orden se publica en la respuesta** de las dos paginadas, para que quien recibe una página sepa sobre qué está paginando.

### 7.1 El orden del catálogo, escrito con cuidado

`spec.md` §6.2 argumenta **por qué** el valor deja de ordenar entre formas. Lo que toca decidir aquí es **cómo se escribe sin volver a caer en ello**, porque las dos columnas del valor están una en cada lado y **la ordenación ingenua las mezcla sola**.

La cláusula es `role_code ASC, rate_type ASC, COALESCE(percentage, fixed_amount) DESC`, y **el `COALESCE` solo es correcto porque va detrás de `rate_type`**: dentro de un grupo ya no hay más que una de las dos columnas llena, de modo que la fusión no compara nada heterogéneo. Puesto delante —o sin `rate_type` en medio— **haría exactamente lo que §6.2 prohíbe**, y sin ningún síntoma: la consulta funciona, la página se llena, y el orden miente.

!!! warning "`rate_type ASC` ordena alfabéticamente, y eso hay que aceptarlo o nombrarlo"

    `FIJO` va antes que `PORCENTAJE` por el alfabeto, no porque nadie lo haya decidido. **Es un orden arbitrario y estable**, que es todo lo que hace falta: lo que importa es que los dos grupos **no se intercalen**, no cuál va primero.

    Se deja así en lugar de imponer un orden explícito porque cualquier otro sería igual de arbitrario y habría que mantenerlo. **Lo que no se puede hacer es dejarlo sin declarar**, porque el día que alguien renombre uno de los dos valores del tipo el orden de los grupos cambiaría solo — y `CA-CM-098` no lo detectaría, ya que solo comprueba que no se intercalen.

**El orden publicado en la respuesta cambia de texto**, y conviene que cambie: quien recibe una página tiene que poder ver que está paginando sobre tres criterios y no sobre dos.

## 8. Autorización

Permiso `commissions:read` en las cuatro. Alcance global explícito.

**El día que D-22 se cierre, el filtro entra aquí.** El predicado de cada listado vive en un solo método a propósito: hay uno que tocar por listado y no tres.

## 9. Auditoría

**Ninguna.** Son consultas.

## 10. Impacto sobre otros módulos

**Ninguno.**

## 11. Alternativas consideradas

| Alternativa | Por qué se descartó |
|---|---|
| **Un listado único con un campo «tipo»** | Devuelve filas con la mitad de los campos vacíos y obliga al cliente a deducir de qué tipo es cada una |
| Partir el requerimiento en tres | Multiplica por tres una especificación cuyo contenido propio cabe en dos párrafos, y esconde que **las tres juntas responden a la pregunta** |
| **`LEFT JOIN` agrupado para contar asociaciones** | Rompe la paginación en silencio. Ver §5 |
| Resolver el rol y la persona llamando a los puertos publicados | `N+1` consultas. Que la llamada sea a un puerto no la hace barata |
| No devolver la cuenta de asociaciones | Deja `RN-CM-012` sin ningún sitio donde verse: no produce error en ninguna parte |
| `/commission-rates/by-product/{id}` | Compite en forma con `/commission-rates/{id}`. Ver §6 |
| Paginar las asociaciones | Complejidad sin cliente. La colección va envuelta para poder añadirla sin romper |
| Devolver el motivo del retiro en el listado | En bloque sería una exportación de decisiones comerciales. Mismo criterio que `RF-PM-002` |
| Un interruptor «solo vigentes» junto al filtro por fecha | Podrían contradecirse, y esa contradicción no la detecta nada |
| **Ordenar el catálogo por `COALESCE(percentage, fixed_amount)` sin agrupar por forma** | Produce una lista que **parece** de mayor a menor y no lo es, sin ningún síntoma. Ver §7.1 |
| Conservar el orden por porcentaje y **dejar los importes fijos al final** | Es lo mismo con otro nombre, y además esconde la mitad del catálogo debajo de la otra |
| **Ordenar por «lo que más paga» de verdad**, calculándolo | Exigiría el precio de cada producto asociado, y una tasa puede tener varios con precios distintos: **no existe un número que ordenar** |
| Un campo `sort` en la petición, ya que el orden se complicó | El catálogo se lee de una manera y el historial de otra; dárselo a elegir al cliente añade combinaciones que nadie pidió y que habría que probar |
| **Filtro por forma también en las personalizadas** | Una persona tiene una sola tasa vigente (`RN-CM-006`): filtrar por forma un historial de una línea no responde a ninguna pregunta |
| Devolver solo el valor, sin la forma, y que el cliente mire el tipo de la tasa | En la lectura **por producto** no hay tasa que mirar: la columna de cifras quedaría sin interpretar. `spec.md` §6.2 |

## 12. Riesgos

| # | Riesgo | Mitigación |
|---|---|---|
| 1 | **La paginación devuelva de menos** si alguien sustituye la subconsulta por un `JOIN` | `CA-CM-011`, que comprueba que la tasa aparezca **una vez** y no que la cuenta sea correcta |
| 2 | Alguien lea el catálogo y dé por hecho que todo lo que ve se está pagando | `associatedProducts` en cada fila. **No se elimina**: el sistema no puede distinguir una tasa a medio configurar de una mal configurada |
| 3 | Filtrar las personalizadas por persona se confunda con «qué cobra esa persona» | Declarado en `spec.md` §4.2 y §13, y en la descripción publicada del endpoint |
| 4 | Las `N+1` vuelvan por una refactorización que «limpie» los `JOIN` | La prueba de número de sentencias del listado, heredada de la v0.1.0 |
| 5 | **El `COALESCE` del orden se mueva delante de `rate_type`** y la lista mienta sin síntoma | `CA-CM-098`, **con cifras que se cruzan a propósito**. Con cifras que no se crucen la prueba pasa igual y no verifica nada |
| 6 | Alguien sume las cifras de la lectura por producto **mezclando formas** | La forma viaja en cada entrada (`spec.md` §6.2), y `CA-CM-099` lo comprueba. **No se mitiga del todo**: nada impide sumarlas de todos modos |
| 7 | El orden de los dos grupos cambie al renombrar un valor del tipo | Declarado en §7.1. `CA-CM-098` **no lo detectaría**, y se acepta: lo que importa es que no se intercalen |

## 13. Estrategia de prueba

| Qué | Nivel | Detalle |
|---|---|---|
| Catálogo, orden y paginación | Integración | `CA-CM-009`, `CA-CM-013` |
| Cuenta de asociaciones | Integración | `CA-CM-010`: dos asociadas y una sin asociar, en la misma página |
| **La cuenta no multiplica las filas** | Integración | `CA-CM-011`: la tasa con dos asociaciones aparece **una vez**, y el total cuadra |
| Retiradas | Integración | `CA-CM-012`: fuera por omisión, dentro y marcadas si se piden |
| El catálogo no admite fecha | Integración | `CA-CM-014` |
| Historial de personalizadas | Integración | `CA-CM-015`, `CA-CM-016`, `CA-CM-017` |
| Las dos direcciones de la asociación | Integración | `CA-CM-018`, `CA-CM-019` |
| Permiso | Integración | `CA-CM-020` |
| **Forma y valor en las cuatro lecturas** | Integración | `CA-CM-096`: el campo de la otra forma **vacío, no omitido** |
| Filtro por forma | Integración | `CA-CM-097`: filtra, y **ausente no filtra** |
| **El orden no intercala las formas** | Integración | `CA-CM-098`: con `50 %` y `100` fijos del **mismo rol**. Ver abajo |
| La forma en la lectura por producto | Integración | `CA-CM-099` |

!!! danger "`CA-CM-098` solo verifica algo si sus datos se cruzan, y por eso los datos van escritos aquí"

    El caso es **un mismo rol** con dos tasas: `50 %` y `100` de importe fijo.

    | Implementación | Resultado |
    |---|---|
    | Correcta (§7.1) | `100` fijo, luego `50 %` — **agrupados**, `FIJO` antes por alfabeto |
    | `COALESCE` sin agrupar | `100` fijo, luego `50 %` — **idéntico** |

    **Con estos números las dos coinciden**, y ahí está la trampa: hay que añadir una tercera, `80 %` del mismo rol. La correcta da `100 fijo · 80 % · 50 %`; la perezosa da `100 fijo · 80 % · 50 %` también… **salvo que el importe fijo sea menor que algún porcentaje**.

    De modo que el dato que verifica es **un importe fijo pequeño**: `10` fijo con `50 %` y `80 %`. Correcta: `10 fijo · 80 % · 50 %`. Perezosa: `80 % · 50 % · 10 fijo` — **el importe fijo se cuela entre medias o al final**, y ahí se ve.

    Se escribe en el plan y no solo en la prueba porque **una prueba con los datos equivocados pasa siempre y no avisa de nada**.
