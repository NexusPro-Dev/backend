# PLAN — `RF-MV-008` Consultar los movimientos propios

| Campo | Valor |
|---|---|
| Requerimiento | `RF-MV-008` |
| Especificación | [`spec.md`](spec.md) v0.1.0 |
| `spec.md` aprobada el | 05-09-2026 |
| Versión | 0.1.0 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 05-09-2026 |

!!! info "Qué va en este documento"

    **Cómo se construye.** Esquema, componentes, contrato, autorización y pruebas.

    **Prueba de pertenencia:** si un cambio de negocio lo invalidaría, pertenece a `spec.md`.

---

## 1. Enfoque

**Dos consultas de lectura y ninguna escritura.** Es el requerimiento más simple del módulo, y toda su dificultad está en un sitio: **que el alcance no se escape**. La condición «es mío» no es un filtro que se añade al final, es lo único que separa esta operación de `RF-MV-006`.

**El alcance se aplica en la sentencia, no después.** No se traen los movimientos y se descartan los ajenos en Java: se pide a la base solo los propios. Traer de más y filtrar después es la forma de que un día alguien mueva el filtro de sitio y no falle nada visible — y de que el conteo total cuente lo que no debe.

**El identificador de quien pregunta NO viaja en la petición**, y ese es el punto. Sale de `AuthenticatedActor`, que es el mismo componente que resuelve el actor en `RF-PM-007` y `RF-SP-039`. Un parámetro que dijera sobre quién sería exactamente el agujero que este requerimiento existe para no abrir.

---

## 2. Cambios de esquema

**Ninguno.** `movements`, `movement_details` y sus vecinas ya existen: las crean `V54` (`RF-MV-001`) y `V55` (`RF-MV-009`).

**Un índice nuevo, y hace falta.** La condición es `client_id = ? OR seller_id = ?`, y **ningún índice sirve a un `OR` sobre dos columnas distintas**: el planificador recorre la tabla entera. `movements` crece sin límite —una fila por venta del sistema— y el síntoma de no tenerlo **no sería un fallo sino lentitud creciente**, que es la clase de defecto que nadie descubre hasta que duele.

| Nombre | Definición | Por qué |
|---|---|---|
| `ix_movements_client` | `movements (client_id, occurred_at DESC)` | La mitad «lo que compré», ya ordenada. La segunda columna evita el ordenamiento en memoria de la página |
| `ix_movements_seller` | `movements (seller_id, occurred_at DESC) WHERE seller_id IS NOT NULL` | La mitad «lo que vendí». **Parcial**: una venta sin vendedor nunca forma parte de esta respuesta y no tiene por qué ocupar sitio en el índice — mismo criterio que `ix_user_supervisors_supervisor_vigente` |

**Dos índices y no uno, porque son dos accesos.** Un índice compuesto sobre `(client_id, seller_id)` no responde a la mitad del `OR`: PostgreSQL solo puede usar la primera columna como prefijo. Con los dos, el planificador puede resolverlo como un `BitmapOr` de dos recorridos de índice.

La migración es `V58__index_movements_por_participante.sql`.

---

## 3. Componentes afectados

| Capa | Componente | Cambio | Nota |
|---|---|---|---|
| `application` | `MyMovementsRequest` | Nuevo | Página, tamaño y estado. **No lleva identificador de persona**, y esa ausencia es el contrato |
| `application` | `MyMovementResponse` | Nuevo | La fila del listado, con el papel y las dos partes |
| `application` | `MovementRole` | Nuevo | `BUYER`, `SELLER`, `BOTH` |
| `domain/repository` | `MovementRepository` | Modificado | Gana `findMine`, `countMine` y `findMineById` |
| `domain/repository` | `JpaMovementRepository` | Modificado | Las tres sentencias |
| `domain/service` | `ListMyMovementsService` | Nuevo | Resuelve el actor, pagina y mapea |
| `domain/service` | `GetMyMovementService` | Nuevo | El detalle, con el alcance dentro de la consulta |
| `interfaces` | `MovementController` | Modificado | Dos `GET` nuevos |

**`SaleResponse` NO se toca, y el detalle lo reutiliza tal cual.** Quien registró una venta y quien la consulta después ven **la misma forma**, que es lo que `spec.md` §6.3 exige. Añadirle el papel obligaría a cambiar el contrato de `RF-MV-001` por una necesidad que no es suya.

**`MovementRole` es un tipo y no una cadena suelta.** Los tres valores son cerrados, y declararlos hace que el contrato publicado los enumere en lugar de decir «texto».

---

## 4. Contrato de API

| Verbo | Ruta | Permiso |
|---|---|---|
| `GET` | `/api/v1/movements/mine` | Autenticado |
| `GET` | `/api/v1/movements/mine/{id}` | Autenticado |

**`/mine` va declarado antes que cualquier `/{id}` del controlador.** Hoy no hay ninguno, pero `RF-MV-007` traerá `GET /api/v1/movements/{id}`, y entonces `mine` empezaría a parecerse a un identificador. Spring resuelve por especificidad y el segmento literal gana, de modo que **funcionará**; lo que se declara aquí es que se escriba en ese orden para que quien lea el archivo lo entienda, y una prueba lo fija — igual que hizo `RF-PM-007` con `/available`.

**`mine` y no `me`.** `SP` usa `/users/me` porque el recurso **es** la persona. Aquí el recurso son los movimientos y `me` no es uno de ellos: `mine` dice «los míos», que es lo que la ruta devuelve.

### 4.1 El listado

Devuelve un `PageResponse` con las filas. Cada una:

| Campo | Tipo | Nota |
|---|---|---|
| `id`, `code`, `status` | | |
| `role` | `BUYER` \| `SELLER` \| `BOTH` | El papel de quien pregunta |
| `client` | objeto | Identificador, nombre de usuario y nombre |
| `seller` | objeto **o nulo** | Se declara nulable **a mano**, con `types = {"object","null"}` |
| `currency`, `paymentMethod` | | |
| `totalAmount`, `discountAmount`, `payableAmount` | | |
| `occurredAt` | | |

**La nulabilidad de `seller` se declara con `types` y no con `nullable`.** Es la trampa que `RF-MV-001` ya pisó y dejó escrita en `SaleResponse`: este contrato se publica como **OpenAPI 3.1**, donde `nullable` dejó de ser palabra clave y **springdoc la descarta en silencio** — la anotación se aplica, el contrato sale igual y nada avisa.

### 4.2 El detalle

Devuelve un `SaleResponse`, idéntico al de `RF-MV-001`.

**`404` para el ajeno y para el inexistente**, sin distinguirlos (`EX-002`). No es un `403`: un `403` diría que existe.

### 4.3 Parámetros

| Parámetro | Nota |
|---|---|
| `page`, `size` | Los resuelve `Pagination`, como todo listado del sistema |
| `status` | Opcional. Un valor no admitido es `400` `VAL-003` |

**No hay parámetro de ordenamiento**, y es una decisión: el orden es fijo, del más reciente al más antiguo. Ofrecer ordenar por importe o por estado invitaría a construir informes sobre un endpoint que existe para que alguien mire lo suyo.

**El desempate es `id` descendente.** Sin él, dos movimientos del mismo instante pueden repetirse en una página y faltar en la siguiente sin que nada falle (`CA-MV-041`).

---

## 5. Autorización

**Ninguna anotación de permiso**, y es lo único que hay que hacer bien: la operación queda cubierta por la regla general —todo exige token salvo tres rutas públicas—, y el alcance lo pone la consulta.

**Las dos rutas entran en la lista blanca de `EndpointPermissionsIT`.** Esa prueba exige que todo endpoint declare permiso salvo los declarados a mano, y sin la entrada fallaría. Declararlas ahí **es la decisión escrita**: la ausencia de permiso queda registrada en un sitio que alguien revisa, en lugar de parecer un olvido.

**`CA-MV-038` se ejercita con `movements:read` puesto.** Tener el permiso de administración **no** debe ampliar lo que esta ruta devuelve; si algún día alguien lo conecta, la prueba lo delata.

---

## 6. Auditoría

**Ninguna.** `spec.md` §7 lo declara: consultar lo propio no es acceso a datos ajenos, y registrarlo llenaría el registro de ruido.

---

## 7. Transaccionalidad

`@Transactional(readOnly = true)` en los dos casos de uso. **Dos sentencias en el listado** —la página y el total—, dentro de la misma transacción para que no puedan describir estados distintos de la tabla.

---

## 8. Impacto sobre otros módulos

**Ninguno.** No se lee ninguna tabla ajena a `MV` salvo `users`, `currencies` y `payment_methods`, que ya se cruzan para resolver los nombres, exactamente como hace `RF-MV-001` al devolver la venta que acaba de crear.

**Y no se pasa por `SP` para resolver quién es quién.** Los nombres de las partes salen del mismo `JOIN` que ya existe; pedirlos por el catálogo publicado costaría una consulta por fila.

---

## 9. Alternativas consideradas

| Alternativa | Por qué no |
|---|---|
| Filtrar en Java lo que devuelve una consulta más amplia | El total contaría movimientos ajenos, y el día que alguien mueva el filtro no fallaría nada visible |
| `UNION` de dos consultas en lugar de un `OR` | Duplicaría el movimiento en que alguien es las dos cosas, y `FA-002` exige que aparezca una vez. Un `UNION` sin `ALL` lo deduplicaría, a cambio de un ordenamiento completo antes de paginar |
| Calcular el papel en el cliente de la API | `spec.md` §6.2: acabaría escrito en cada consumidor, y distinto en cada uno |
| Reutilizar `RF-MV-006` con un parámetro «solo lo mío» | Daría un endpoint con **dos modelos de seguridad** — el mismo argumento con el que §4.1 de `requirements/mv.md` separó registrar de comprar |
| Devolver las líneas en el listado | Multiplica la respuesta por un dato que solo se mira al abrir uno |

---

## 10. Riesgos

| Riesgo | Mitigación |
|---|---|
| **Que el alcance se escape** — el defecto que importa | El filtro va en la sentencia; `CA-MV-038` lo ejercita con el permiso de administración puesto |
| Que `/mine` sea capturado por `/{id}` cuando exista `RF-MV-007` | Prueba propia, como la de `/available` en `RF-PM-007` |
| Recorrido secuencial de `movements` al crecer | Los dos índices de §2. El síntoma sería lentitud y no un fallo |
| Que el papel salga mal cuando alguien es las dos cosas | `CA-MV-037` lo fija, y es el caso que se olvida al escribir el `CASE` |

---

## 11. Estrategia de prueba

| Qué | Nivel | Por qué ahí |
|---|---|---|
| Los tres papeles, incluido `BOTH` | Integración | Depende de la sentencia, no de una clase |
| Que no aparezca ningún movimiento ajeno, **con `movements:read`** | Integración | Es el criterio que sostiene el requerimiento |
| El detalle ajeno responde `404` y no `403` | Integración | La distinción es observable solo por HTTP |
| Paginación, orden y estabilidad entre páginas | Integración | |
| Filtro por estado, y estado no admitido → `400` | Integración | |
| Página vacía para quien no participó | Integración | |
| `401` sin autenticar | Integración | |
| `/mine` no lo captura una variable de ruta | Integración | La declara `MovementRoutingIT` o la propia clase |
