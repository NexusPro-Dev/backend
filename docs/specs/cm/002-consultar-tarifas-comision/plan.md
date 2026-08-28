# PLAN — `RF-CM-002` Consultar las tarifas de comisión

| Campo | Valor |
|---|---|
| Requerimiento | `RF-CM-002` |
| Especificación | [`spec.md`](spec.md) |
| `spec.md` aprobada el | 28-08-2026 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 28-08-2026 |

!!! info "Qué va en este documento"

    **Cómo se construye.** Las decisiones técnicas que la especificación deliberadamente no toma.

    **Prueba de pertenencia:** si al negocio no le importa ni lo entendería, va aquí.

El comportamiento es el de [`spec.md`](spec.md) y no se repite. La mecánica común del módulo —la tabla, los cuatro puertos hacia `SP` y `PM`, y el alcance global— la fijó el plan de [`RF-CM-001`](../001-registrar-tarifa-comision/plan.md) y **este documento la hereda sin repetirla**.

---

## 1. Enfoque

Un listado paginado sobre una **proyección**, sin entidades y sin `domain`: `spec.md` §5 declara que ninguna regla de negocio gobierna esta consulta. Estructuralmente es el listado de productos con otros filtros.

Lo propio son dos cosas: **el rol, el producto y la persona llegan resueltos en la misma sentencia**, y **el filtro por fecha no es una igualdad sino una pertenencia a un rango**.

## 2. Cambios de esquema

**Ninguno.** La tabla y su índice los crea `V44` (`RF-CM-001` §2), y el índice GiST de la restricción cubre el acceso por rol.

**Qué índice NO se crea todavía.** Uno sobre `valid_from DESC` serviría al orden por omisión sin filtros. No se crea: la tabla de tarifas de un sistema real se cuenta en decenas o cientos, no en millones, y cada índice se paga en cada escritura. El criterio es el de `RF-SP-011` §2 — el mínimo que sostiene las consultas reales. Anotado en §10.

## 3. Componentes afectados

| Capa | Componente | Nuevo / Modificado | Responsabilidad |
|---|---|---|---|
| `domain/repository` | `CommissionRateQueryRepository` | Nuevo | Puerto de consulta: filtros, página y total |
| `domain/repository` | `JpaCommissionRateQueryRepository` | Nuevo | Adaptador. Una sentencia con los tres `LEFT JOIN` |
| `domain/service` | `ListCommissionRatesService` | Nuevo | Caso de uso. `@Transactional(readOnly = true)` |
| `application` | `ListCommissionRatesRequest` | Nuevo | Filtros validados y normalizados |
| `application` | `CommissionRateItem` | Nuevo | Modelo de lectura de cada fila, con el **grado** calculado |
| `interfaces` | `CommissionRateController` | **Modificado** | Gana `GET /api/v1/commission-rates` |

## 4. Contrato de API

`GET /api/v1/commission-rates` · `200 OK` con la envoltura paginada del sistema.

| Parámetro | Efecto |
|---|---|
| `roleId`, `productId`, `userId` | Igualdad. Un valor inexistente devuelve la colección vacía (`EX-001`), no un error |
| `onDate` | **Pertenencia**: devuelve las que rigen ese día |
| `includeDeleted` | Por omisión `false` |
| `page`, `size` | Paginación |

**El filtro por fecha se traduce a `valid_from <= :fecha AND (valid_to IS NULL OR valid_to >= :fecha)`**, y no a una comparación con el rango del índice. Es el mismo predicado que la restricción implica, escrito en la forma que el planificador entiende sobre columnas.

**Los filtros por producto y por persona son igualdades sobre la columna, y NO resuelven precedencia.** `spec.md` §13 lo exige: filtrar por persona devuelve las declaradas **para** esa persona, no las que **le aplican**. Lo segundo es `RF-CM-005`, y confundirlos haría que este listado empezara a resolver precedencias por su cuenta — el defecto que `RN-CM-004` existe para evitar.

## 5. Autorización

Permiso `commissions:read`. **Alcance global explícito.** Este es el componente del módulo que **D-22 puede obligar a cambiar**, y por eso el predicado se construye en un solo sitio: el día que haya que añadir el alcance del actor, hay un método que tocar y no seis.

## 6. Auditoría

Ninguna. Es una lectura y no cambia el estado del sistema.

## 7. Transaccionalidad

`@Transactional(readOnly = true)`.

## 8. Impacto sobre otros módulos

**Ninguno nuevo.** Los tres datos resueltos —rol, producto y persona— **no entran por los puertos** de `RF-CM-001` §8, y esa es la decisión de este plan que conviene entender: resolverlos fila a fila contra las interfaces de `SP` y `PM` sería el problema de las `N+1` consultas —cien tarifas, trescientas llamadas—, así que viajan en `LEFT JOIN` dentro de la misma sentencia.

**Eso no rompe la frontera de D-25**, y el motivo está escrito en `modules.md` §5.3: lo que §7 defiende es la frontera del **código** —`CM` no importa repositorios ni entidades ajenos—, mientras que las claves foráneas y el `JOIN` son integridad y lectura declaradas en el motor, que es donde `PM` ya se apoya para hablar de `SP`.

## 9. Alternativas consideradas

| Alternativa | Por qué se descartó |
|---|---|
| Resolver rol, producto y persona por los puertos, fila a fila | `N+1` con otro nombre: cien tarifas serían trescientas llamadas |
| Que el filtro por persona devuelva las que **le aplican** | Es resolver precedencia, y eso vive en `RF-CM-005`. Dos sitios resolviéndola es el defecto que devuelve resultados plausibles |
| Excluir las vencidas por omisión | Escondería el historial, que es la mitad del valor de tener vigencia |
| Un interruptor «solo vigentes» además del filtro por fecha | Los dos podrían contradecirse, y esa contradicción no la detecta nada |

## 10. Riesgos

| # | Riesgo | Mitigación |
|---|---|---|
| 1 | Sin índice sobre `valid_from`, el orden por omisión ordena la tabla entera | Acotado por el tamaño real de la tabla. Si se demostrara lento, es una migración de una línea que no cambia el contrato |
| 2 | D-22 obliga a añadir alcance a esta consulta | El predicado vive en un solo método, escrito para que ese cambio sea local |

## 11. Estrategia de prueba

| Qué | Nivel | Detalle |
|---|---|---|
| Orden y filtros | Integración | `CA-CM-014`, `CA-CM-015` |
| El vacío y presente | Integración | `CA-CM-016`: producto y persona nulos **y presentes** en una tarifa del rol |
| Vencidas y filtro por fecha | Integración | `CA-CM-017`, `CA-CM-018` |
| Retiradas | Integración | `CA-CM-019`, en los dos sentidos |
| Colección vacía sin error | Integración | `CA-CM-020` |
| Historial de un caso | Integración | `CA-CM-022` |
| **Número de sentencias** | Integración | Una por página con independencia del número de filas. Es la prueba que impide que los tres `JOIN` se conviertan en `N+1` en una refactorización, y es la que `RF-SP-026` aprendió a hacer |
