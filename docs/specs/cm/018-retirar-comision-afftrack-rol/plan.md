# PLAN — `RF-CM-018` Retirar una comisión afftrack de rol

| Campo | Valor |
|---|---|
| Requerimiento | `RF-CM-018` |
| Especificación | [`spec.md`](spec.md) v0.1.0 |
| `spec.md` aprobada el | 29-09-2026 |
| Versión | 0.1.0 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 29-09-2026 |

!!! info "Qué va en este documento"

    **Cómo se construye.** Esquema, componentes, contrato, autorización y pruebas.

    **Prueba de pertenencia:** si un cambio de negocio lo invalidaría, pertenece a `spec.md`.

La mecánica es la de [`RF-CM-004`](../004-retirar-tasa-comision/plan.md): `POST …/deletion` con motivo, `deleted_at` en la fila y el motivo en `audit_deletion_log`.

---

## 1. Enfoque

Se lee la fila `FOR UPDATE` —sin filtrar las retiradas, para distinguir `404` de `409`—, se marca `deleted_at` y se registra el retiro. **El remanente no se toca**: vive en `afftrack_settlements` (`cm.md` §7.11) y no depende de que el escalón exista.

---

## 2. Cambios de esquema

**Ninguno**: `V54`.

---

## 3. Componentes afectados

| Módulo | Capa | Componente | Cambio |
|---|---|---|---|
| `CM` | `domain/models` | `AfftrackRate` | Gana `retirar(instante)` |
| `CM` | `domain/service` | `DeleteAfftrackRateService` | Nuevo |
| `CM` | `application` | `DeleteAfftrackRateRequest` | Nuevo, `@Schema(name)` |
| `CM` | `interfaces` | `AfftrackRateController` | Gana `POST /{id}/deletion` |

---

## 4. Contrato de API

| Verbo | Ruta | Permiso |
|---|---|---|
| `POST` | `/api/v1/afftrack-rates/{id}/deletion` | `afftrack-rates:delete` |

Cuerpo: `reason`. Respuesta `204`. Códigos: `204`, `400`, `401`, `403`, `404`, `409`.

---

## 5. Autorización

`@PreAuthorize("hasAuthority('afftrack-rates:delete')")`; en `PERMISO_DE_CADA_OPERACION`.

---

## 6. Auditoría

`AuditWriter.recordDeletion` sobre `afftrack_rates`, con el motivo y la instantánea.

---

## 7. Transaccionalidad

`@Transactional`. **Un cierre en curso** que ya leyó la escala paga con lo que leyó: el retiro rige desde el siguiente, igual que una corrección.

---

## 8. Impacto sobre otros módulos

Ninguno.

---

## 9. Alternativas consideradas

| Alternativa | Por qué no |
|---|---|
| Liquidar el remanente al retirar el último escalón | Pagaría fuera de un cierre, y a un valor que el negocio acaba de decidir quitar |

---

## 10. Riesgos

Ninguno propio.

---

## 11. Estrategia de prueba

`DeleteAfftrackRateIT`: `CA-CM-227` a `CA-CM-230`. `CA-CM-227` y `CA-CM-228` corren un cierre (`RF-CM-020`).
