# PLAN — `RF-MV-034` Editar una entidad de cobro

| Campo | Valor |
|---|---|
| Requerimiento | `RF-MV-034` |
| Especificación | [`spec.md`](spec.md) v0.1.0 |
| `spec.md` aprobada el | 01-10-2026 |
| Versión | 0.1.0 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 01-10-2026 |

!!! info "Qué va en este documento"

    **Cómo se construye.** Esquema, componentes, contrato, autorización y pruebas.

    **Prueba de pertenencia:** si un cambio de negocio lo invalidaría, pertenece a `spec.md`.

---

## 1. Enfoque

**Lectura con bloqueo y `UPDATE`**: la fila se lee `FOR UPDATE`, se compara con lo pedido y, si algo cambia, se escribe `name`, `is_active` y `updated_at`. El bloqueo es lo que hace que la auditoría lleve **el valor anterior verdadero** cuando dos personas editan a la vez.

**La carrera con un retiro** (`spec.md` §13) se resuelve en `RF-MV-019`, que lee la entidad de la cuenta con `FOR SHARE` dentro de su transacción: la desactivación espera al retiro, o el retiro ve la entidad ya inactiva. Este requerimiento no hace nada especial por ello.

---

## 2. Cambios de esquema

Ninguno.

---

## 3. Componentes afectados

| Capa | Componente | Cambio | Nota |
|---|---|---|---|
| `domain/models` | `PayoutInstitution` | Gana `editar(nombre, activa)` | Devuelve si cambió algo |
| `domain/repository` | `PayoutInstitutionRepository` | Gana `lock(id)` y `update` | |
| `domain/service` | `PayoutInstitutionService` | Gana `update` | `FA-001`, auditoría con lo anterior |
| `application` | `PayoutInstitutionRequests.Update` | Nuevo | `{ name, active }`; **sin** `code`, `kind` ni `countryId` |
| `interfaces` | `PayoutInstitutionController` | Gana `PATCH /{id}` | |

**Las propiedades desconocidas dan `400`** por la configuración global de Jackson (`FAIL_ON_UNKNOWN_PROPERTIES`), como en `RF-SP-044` (`CA-SP-599`): enviar el código es un error visible, no un cambio que se ignora en silencio.

---

## 4. Contrato de API

| Verbo | Ruta | Permiso |
|---|---|---|
| `PATCH` | `/api/v1/movements/payout-institutions/{id}` | `movements:update-payout-institution` |

**Cuerpo**: `{ "name"?, "active"? }`. **Respuesta**: `200` con `PayoutInstitutionResponse`.

| Código | Cuándo |
|---|---|
| `200` | Editada, o sin cambios (`FA-001`) |
| `400` | Nada que cambiar, nombre inválido o propiedad desconocida (`EX-001`) |
| `401` / `403` | Sin token / sin `movements:update-payout-institution` |
| `404` | La entidad no existe (`EX-002`) |

---

## 5. Autorización

`@PreAuthorize("hasAuthority('movements:update-payout-institution')")`.

---

## 6. Auditoría

Un `ChangeEvent` sobre `payout_institutions`, `UPDATE`, con los campos que cambiaron, antes y después. `FA-001` no audita.

---

## 7. Transaccionalidad

`@Transactional`: el bloqueo, la comparación y el `UPDATE`.

---

## 8. Impacto sobre otros módulos

**El frontend**: la pantalla de administración del catálogo. **El contrato OpenAPI se regenera y la prosa de la `@Operation` se relee**.

---

## 9. Alternativas consideradas

| Alternativa | Por qué no |
|---|---|
| Un `DELETE` lógico | Una entidad no se borra; «inactiva» dice lo que pasa y admite volver atrás |
| Dos rutas, editar y cambiar estado, como hacen los países en `SP` | Allí el estado tiene consecuencias propias sobre las personas de ese país, y por eso su permiso; aquí la entidad solo tiene dos datos editables, y quien corrige el nombre es quien decide si se sigue pagando por ella. Si alguna vez hace falta separarlos, se separa la ruta sin tocar la tabla |
| Dar de baja las cuentas al desactivar | Quitaría a la persona la decisión, y una reactivación no las devolvería (`spec.md` §2.1) |

---

## 10. Riesgos

| Riesgo | Mitigación |
|---|---|
| Que la copia del retiro dependa del nombre vivo | La copia es una columna propia (`requirements/mv.md` §7.13); `CA-MV-374` lo prueba |

---

## 11. Estrategia de prueba

Integración, `EditPayoutInstitutionIT`: `CA-MV-371` a `CA-MV-377`. `CA-MV-374` necesita un retiro con destino, y por eso se escribe **después** de la enmienda de `RF-MV-019`; hasta entonces se prueba insertando la copia directamente en la base.
