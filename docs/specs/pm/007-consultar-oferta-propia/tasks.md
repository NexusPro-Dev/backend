# TASKS — `RF-PM-007` Consultar la oferta disponible para uno mismo

| Campo | Valor |
|---|---|
| Requerimiento | `RF-PM-007` |
| Enmendadas | 02-09-2026 — la oferta coincide por **origen**, no compara niveles |
| Plan | [`plan.md`](plan.md), aprobado el 26-08-2026 |
| Estado | **Aprobadas** |
| Autor | Responsable técnico |
| Aprobadas por | Responsable del proyecto |
| Fecha de aprobación | 26-08-2026 |

---

## 1. Tareas

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-01` | **En `SP`**: `CurrentMembershipLookup` con su adaptador, en `modules/system/users/application`. Devuelve la membresía **vigente** ya evaluada, o vacío | `RF-PM-001 · T-07` | Integración: una membresía con fecha de fin **pasada** devuelve vacío, y una con fecha **igual al instante consultado** también — ese borde ya está fijado en `SP` y aquí se hereda, no se reimplementa | Pendiente |
| `T-02` | `application/OfferResponse`: `currentMembership` más **dos colecciones envueltas** | — | La respuesta tiene `upgrades.content` y `services.content`, **no arreglos en la raíz** (`CA-PM-091`) | Pendiente |
| `T-03` | `ProductQueryRepository.findOffer(UUID membresia)`: una sentencia, solo activos, upgrades cuyo `source_membership_id` **es** la membresía del actor | `RF-PM-002 · T-05` | Integración: **una** sentencia. El nulo no coincide con ningún origen, de modo que `FA-001` sale del propio filtro | Pendiente |
| `T-04` | Orden: upgrades por nivel destino, bots por fecha de alta | `T-03` | `CA-PM-078`, `CA-PM-079` | Pendiente |
| `T-05` | `domain/service/GetOwnOfferService`: el actor sale del token, nunca de un parámetro | `T-01`, `T-03` | Enviar `userId` no cambia la respuesta (`CA-PM-066`) | Pendiente |
| `T-06` | **Prueba de la coincidencia de origen**: un upgrade desde su membresía se ofrece; uno desde otra, no; y uno **hacia** la suya, tampoco | `T-05` | `CA-PM-106` a `CA-PM-108`. El tercero es el que más fácil se olvida, y con la coincidencia exacta sale solo — su origen es otro | Pendiente |
| `T-07` | `interfaces`: `GET /api/v1/products/available`, **sin permiso**, solo autenticado | `T-05` | Responde a un actor autenticado sin ninguna autoridad (`CA-PM-065`) | Pendiente |
| `T-08` | Prueba de que la ruta literal **no se confunde** con `/products/{id}` | `T-07` | `available` responde `200` y no `400` por identificador inválido. Spring resuelve antes el segmento literal, y esta prueba es lo que impide que un renombrado lo rompa en silencio | Pendiente |
| `T-09` | Pruebas de API del resto de criterios de `spec.md` §12 | `T-07` | Cubre `CA-PM-058` a `CA-PM-067`, `CA-PM-078`, `CA-PM-079`, `CA-PM-088` a `CA-PM-091` | Pendiente |
| `T-10` | Prueba de quien **no tiene membresía** y de quien la tiene **vencida** | `T-09` | Cero upgrades y todos los bots, en los dos casos (`FA-001`, `FA-003`) | Pendiente |
| `T-11` | Prueba de quien está **en la cima**: lista de upgrades vacía, sin error | `T-09` | No es un mensaje especial: es una lista vacía | Pendiente |
| `T-12` | Documentación OpenAPI del endpoint, **declarando que no admite parámetros** | `T-09` | El contrato no lista ninguno | Pendiente |
| `T-13` | Actualizar la matriz de trazabilidad | `T-09` | La fila refleja el estado | Pendiente |
| `T-14` | La **vigencia** viaja en cada producto ofrecido | `T-02`, `T-03` | Vacía en los que no caducan (`CA-PM-095`) | Pendiente |

## 2. Orden de ejecución

`T-01` primero: es la tercera y última lectura de D-25, y la única que este requerimiento estrena. `T-06` dejó de ser la prueba que decide si el requerimiento hace lo contrario de lo que dice: **desde el 02-09-2026 aquí no se comparan niveles**, y esa comparación se hace una vez al registrar el producto (`RN-PM-017`).

## 3. Cobertura de los criterios de aceptación

| Criterio | Tareas |
|---|---|
| `CA-PM-058` | `T-03` |
| `CA-PM-059` a `CA-PM-061` | `T-03`, `T-06` |
| `CA-PM-062` | `T-11` |
| `CA-PM-063` | `T-01`, `T-10` |
| `CA-PM-064` | `T-02`, `T-05` |
| `CA-PM-065` | `T-07` |
| `CA-PM-066` | `T-05` |
| `CA-PM-067` | `T-09` |
| `CA-PM-078`, `CA-PM-079` | `T-04` |
| `CA-PM-088` | `T-10` |
| `CA-PM-089` | `T-06` |
| `CA-PM-090` | `T-09` |
| `CA-PM-091` | `T-02` |

## 4. Bloqueos

| # | Bloqueo | Desde | Responsable | Estado |
|---|---|---|---|---|
| 1 | `T-01` escribe en paquetes de `SP`, sobre `user_memberships`. Una regresión ahí alcanza a `RF-SP-032` y `RF-SP-033` | 26-08-2026 | Responsable técnico | Abierto |
| 2 | `T-06` y `T-10` necesitan una cadena de al menos **cuatro** niveles, personas en varios de ellos **y upgrades declarados desde varios orígenes**: la preparación de datos es la mitad del trabajo de estas pruebas | 26-08-2026 | Responsable técnico | Abierto |

## 5. Definición de terminado

El requerimiento no está terminado hasta cumplir **todas** las condiciones de la constitución §16:

- [ ] Todas las tareas en estado `Hecha`.
- [ ] Todos los criterios de aceptación con prueba automatizada en verde.
- [ ] `mvn verify` en verde en local.
- [ ] Toda escritura emite su evento de auditoría, en la transacción que corresponde.
- [ ] Los endpoints nuevos declaran su permiso.
- [ ] El contrato OpenAPI coincide con el comportamiento real.
- [ ] Documentación afectada actualizada en el mismo Pull Request.
- [ ] Matriz de trazabilidad actualizada.
- [ ] Pull Request aprobado por alguien distinto del autor e integrado.
