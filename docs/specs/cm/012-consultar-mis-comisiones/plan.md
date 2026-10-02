# PLAN — `RF-CM-012` Consultar mis comisiones

| Campo | Valor |
|---|---|
| Requerimiento | `RF-CM-012` |
| Especificación | [`spec.md`](spec.md) v0.1.0 |
| `spec.md` aprobada el | 28-09-2026 |
| Versión | 0.4.0 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 28-09-2026 |
| Enmendado el | 29-09-2026 — hereda la clase de cada comisión de `RF-CM-010` §12 |
| Enmendado el | 29-09-2026 — hereda la fuente `DIRECTA` de `RF-CM-010` §13 |
| Enmendado el | 30-09-2026 — hereda lo revertido y lo retirado de `RF-CM-010` §14 |

!!! info "Qué va en este documento"

    **Cómo se construye.** Esquema, componentes, contrato, autorización y pruebas.

    **Prueba de pertenencia:** si un cambio de negocio lo invalidaría, pertenece a `spec.md`.

---

## 1. Enfoque

**`RF-CM-010` con la persona fijada por el token.** Los mismos servicios y el mismo repositorio, llamados con `userId = AuthenticatedActor.id()`; **el filtro de persona no se acepta** en la petición, para que no exista un parámetro que un cliente pueda cambiar. El detalle busca **por identificador y persona a la vez** —`WHERE id = :id AND user_id = :actor`—, y cero filas es `404` sin distinguir por qué (`EX-001`), como `/movements/mine/{id}`.

---

## 2. Cambios de esquema

**Ninguno**. Los permisos los siembra `V51` (`RF-CM-013`) y ya los da a los roles `VENDEDOR`.

---

## 3. Componentes afectados

| Capa | Componente | Cambio | Nota |
|---|---|---|---|
| `domain/service` | `ListCommissionBatchesService`, `GetCommissionBatchService` | Ganan la variante propia | Sin copiar la lógica: un parámetro de persona obligatorio |
| `application` | `MyCommissionBatchesRequest` | Nuevo | Los filtros de `RF-CM-010` sin `userId` |
| `interfaces` | `CommissionBatchController` | Gana `GET /mine` y `GET /mine/{id}` | Devuelven los `record`s de `RF-CM-010` |

---

## 4. Contrato de API

| Verbo | Ruta | Permiso |
|---|---|---|
| `GET` | `/api/v1/commission-batches/mine` | `commission-batches:list-own` |
| `GET` | `/api/v1/commission-batches/mine/{id}` | `commission-batches:read-own` |

Códigos los de `RF-CM-010`. **Un `userId` en la consulta se ignora** —no se declara en el contrato—.

---

## 5. Autorización

Un `@PreAuthorize` por operación; las dos en `PERMISO_DE_CADA_OPERACION`.

---

## 6. Auditoría

Ninguna: son lecturas.

---

## 7. Transaccionalidad

`@Transactional(readOnly = true)`.

---

## 8. Impacto sobre otros módulos

Ninguno.

---

## 9. Alternativas consideradas

| Alternativa | Por qué no |
|---|---|
| `403` para un lote ajeno | Confirmaría que existe |
| Reutilizar `/commission-batches?userId=` con el permiso propio | Un parámetro que el cliente controla decide qué ve; es el defecto que `list-own` existe para evitar |

---

## 10. Riesgos

| Riesgo | Mitigación |
|---|---|
| Que la variante propia filtre mal y enseñe lo ajeno | `CA-CM-197` y `CA-CM-201` con dos vendedores con lotes |

---

## 11. Estrategia de prueba

`MyCommissionBatchesIT`: `CA-CM-197` a `CA-CM-202`, con **dos vendedores en cadena** —uno superior del otro— y ventas reales por la API de `MV`.

## 12. La clase de cada comisión — enmienda del 29-09-2026

**Sin trabajo propio**: las lecturas de «los míos» reutilizan la sentencia y las formas de `RF-CM-010`, que §12 de aquel plan cambia. `CommissionBatchesIT` gana `CA-CM-263`.

## 13. La fuente `DIRECTA` — enmienda del 29-09-2026

Hereda `RF-CM-010` §13 sin cambios de sentencia: la prosa de la `@Operation` gana la tercera fuente. `MyCommissionBatchesIT` gana `CA-CM-272`.

## 14. Lo revertido y lo retirado — enmienda del 30-09-2026

Hereda `RF-CM-010` §14 sin sentencias propias: la variante propia usa las mismas lecturas con el filtro de persona. `CommissionBatchesIT` gana `CA-CM-303`, junto a `CA-CM-272`.
