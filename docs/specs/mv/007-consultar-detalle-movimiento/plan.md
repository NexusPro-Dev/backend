# PLAN — `RF-MV-007` Consultar el detalle de un movimiento, con su comprobante

| Campo | Valor |
|---|---|
| Requerimiento | `RF-MV-007` |
| Especificación | [`spec.md`](spec.md) v0.2.0 |
| Versión | 0.2.0 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 30-09-2026 |

!!! info "Qué va en este documento"

    **Cómo se construye, y por qué así.** Las decisiones de negocio están en `spec.md`.

---

## 1. Enfoque

**No hay nada que construir en la capa de datos.** `MovementRepository.findById` es desde el 17-09-2026 el detalle **sin alcance** que usan confirmar (`RF-MV-003`), rechazar un pago (`RF-MV-004`), volver a pagar (`RF-MV-018`) y asignar vendedores (`RF-MV-016`): la misma proyección que `findMineById` —la del detalle propio de `RF-MV-008`— sin el predicado del actor, y su Javadoc dice por qué: **no tener dos proyecciones de la misma cabecera**. `SaleDetailMapper` la convierte en `SaleResponse`, que es la respuesta del detalle propio y la de registrar una venta.

Lo que falta es **la ruta y el servicio que la une a esas dos piezas**. Por eso el requerimiento es pequeño, y por eso CA-MV-290 —«lo mismo que el detalle propio»— se cumple por construcción: las dos rutas leen con la misma cabecera y arman con el mismo traductor.

## 2. Cambios de esquema

**`V56`**: el permiso **`movements:read-detail`**, con literal v7 en la serie de `movements:` (000041), **repartido a todo rol que porte `movements:read`** —hoy `SUPERADMIN`, `ADMIN`, `MANAGER` y `DIRECTOR`, por `V40`— como `V47` repartió los del aula a quien portaba `courses:learn`. Guardas por conjunto y de contención (`RN-SEG-003`). Catálogo 162 → **163**, `ADMIN` 160 → **161**.

*Enmienda del 30-09-2026: la 0.1.0 decía «ningún permiso nuevo»; ver `spec.md` §14, pregunta 2.*

## 3. Componentes afectados

| Componente | Cambio |
|---|---|
| `domain/service/GetMovementService` | **Nuevo.** `findById` + `SaleDetailMapper.de`; `EX-001` si no existe |
| `interfaces/MovementController` | `GET /api/v1/movements/{id}` con `movements:read-detail`, la variable con forma de UUID |
| `application/SaleResponse` | **Gana `type`** —`VENTA`, `RETIRO`, `BONO`—: el detalle no decía el tipo, y este abre movimientos que no son ventas. Campo nuevo, compatible |
| `db/migration/V56` | El permiso |
| `EndpointPermissionsIT` | La ruta nueva en el mapa de cada operación con su permiso |
| Recuentos del catálogo | `PermissionIT`, `PermissionsSeedIT`, `MovementsPermissionsSeedIT`, `TeamsPermissionsSeedIT`, `SaleLinesPermissionSeedIT` |
| `MovementDetailIT` | **Nueva**: `CA-MV-287` a `CA-MV-292` |

**Un servicio aparte y no un segundo método en `GetMyMovementService`**: aquel lleva el actor inyectado y su Javadoc dice que el alcance va dentro de la consulta para que no haya una comprobación de pertenencia «que alguien pueda mover de sitio». Poner a su lado un método sin alcance es justo eso.

## 4. Contrato de API

```
GET /api/v1/movements/{id}
```

**Es el `GET` del recurso que `POST /api/v1/movements` crea y `GET /api/v1/movements` lista**, sin segmento, como dejó dicho `RF-MV-006` · `plan.md` §4.

**No choca con ninguna ruta literal.** `/sales`, `/sales/lines`, `/mine/shopping`, `/mine/products` y `/mine/balances` son literales y Spring las prefiere a la variable; `/mine/{id}` tiene dos segmentos. La ruta se declara **al final** del controlador, junto a su hermana propia.

**La variable solo admite la forma de un UUID** (enmienda del 30-09-2026). Sin la expresión, `GET /movements/mine` —la ruta que `RF-MV-008` retiró el 22-09-2026— caería aquí y respondería `400` por identificador malformado, y su `CA-MV-140` promete `404`: la prueba lo detectó al construir. Con ella, un segmento que no es un identificador no es esta ruta. El precio es que el identificador malformado deja de ser `400` y pasa a `404`, en esta ruta y solo en esta.

| Respuesta | Cuándo |
|---|---|
| `200` | El comprobante (`SaleResponse`) |
| `401` | Sin token (`AUTH-001`) |
| `403` | Sin `movements:read-detail` (`AUTH-002`) |
| `404` | No existe (`EX-001`), o no tiene forma de UUID |

## 5. Autorización

`@PreAuthorize("hasAuthority('movements:read-detail')")`: un permiso por operación (`RN-SEG-014`, `spec.md` §14, pregunta 2). **No hay alcance**: el servicio no recibe al actor.

## 6. Auditoría

Ninguna: es una lectura.

## 7. Transaccionalidad

`@Transactional(readOnly = true)`, como el detalle propio: cabecera, líneas y pagos se leen en la misma instantánea.

## 8. Impacto sobre otros módulos y documentos

| Documento | Cambio |
|---|---|
| `requirements/mv.md` | §6: el permiso nuevo, y `movements:read` deja de decir «y el detalle de cualquiera»; control de cambios |
| `requirements.md` | La fila con su tripleta y **En desarrollo**; spec redactadas, aprobadas y planes +1; endpoints funcionando +1 |
| `security.md` | §4.4: el permiso nuevo en el bloque y en la prosa; catálogo 163 |
| `api/index.md` | La ruta nueva y el campo `type` |
| `RF-MV-006` · `spec.md` §14 y `RF-MV-008` · `spec.md` | Ninguno: dejaron escrito que el detalle «no existe»; es historia y no se reescribe |

## 9. Alternativas consideradas

| Alternativa | Por qué no |
|---|---|
| **Abrir `/mine/{id}` a quien tenga `movements:read`** | Mezcla dos alcances en una ruta, y el 404 del ajeno —la defensa de `RF-MV-008`— pasaría a depender de un permiso |
| **Una respuesta propia de administración** | Dos formas del mismo comprobante envejecen por separado; `spec.md` §4.1 pide la misma |
| **Reutilizar `movements:read`** (la 0.1.0 de este plan) | Viola `RN-SEG-014`, y `EndpointPermissionsIT` lo rechaza (`spec.md` §14, pregunta 2) |
| **Validar el identificador con `400`, como el detalle propio** | Captura `GET /movements/mine`, que `CA-MV-140` promete en `404` |

## 10. Riesgos

| Riesgo | Mitigación |
|---|---|
| Que alguien acote `findById` pensando en el detalle propio | Su Javadoc ya dice que es el detalle sin alcance y quién lo usa; `CA-MV-287` fija que lo ajeno se abre |

## 11. Estrategia de prueba

`MovementDetailIT`, de integración y por HTTP. **La venta se registra por el camino de verdad** —`POST /api/v1/movements`— y no con `INSERT`, para que el detalle lea lo que el sistema escribe; el retiro, con `LedgerFixtures` y la ruta de pedirlo. `CA-MV-290` compara **el cuerpo entero** de las dos rutas sobre el mismo movimiento.
