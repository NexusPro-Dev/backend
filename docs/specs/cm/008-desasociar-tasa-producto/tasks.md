# TASKS — `RF-CM-008` Retirar la asociación de una tasa con un producto

| Campo | Valor |
|---|---|
| Requerimiento | `RF-CM-008` |
| Plan | [`plan.md`](plan.md), aprobado el 02-09-2026 |
| Versión | 0.1.0 |
| Estado | **En revisión** |
| Autor | Responsable técnico |
| Aprobadas por | Pendiente |
| Fecha de aprobación | Pendiente |
| Issue | Pendiente de crear |
| Rama | `feature/flujos-de-pm-y-cm` |

!!! info "Qué va en este documento"

    **En qué pasos se construye** lo que `plan.md` decidió, con su dependencia y su verificación.

!!! warning "Las tareas están hechas antes que este documento"

    El requerimiento se construyó el 02-09-2026 **sin tripleta previa** —excepción al Art. I.1— y esta lista viene detrás. **No planifica: registra.**

---

## 1. Tareas

**Estados:** `Pendiente` · `En curso` · `Hecha` · `Bloqueada`.

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-01` | `remove(...)` en el puerto de escritura de la asociación | `RF-CM-007` · `T-06` | La fila desaparece; no queda marcada | **Hecha el 02-09-2026** |
| `T-02` | `DissociateProductRequest`, **propio y no reutilizado** | — | El contrato publicado habla de desasociar, no de retirar una tasa | **Hecha el 02-09-2026** |
| `T-03` | `DissociateProductService`: motivo **el primero de todo**, luego buscar | `T-01`, `T-02` | Un motivo vacío no cuesta ni una consulta | **Hecha el 02-09-2026** |
| `T-04` | **Exigir motivo por encima de lo que `ck_deletion_reason` obliga** | `T-03` | El esquema lo eximiría; el caso de uso no | **Hecha el 02-09-2026** |
| `T-05` | Buscar la asociación **por tasa y producto**, no por rol y producto | `T-03` | Con dos tasas del mismo rol, borra la que se nombró | **Hecha el 02-09-2026** |
| `T-06` | La instantánea **antes** de borrar | `T-03` | Es la copia, no una precaución | **Hecha el 02-09-2026** |
| `T-07` | Registro de eliminación de tipo **`ASSOCIATION`**, no `PHYSICAL` | `T-06` | Lo que se pierde es un vínculo, no una entidad | **Hecha el 02-09-2026** |
| `T-08` | El rechazo es **`404`** y no `409` | `T-03` | No hay dato que permita afirmar «ya se borró» | **Hecha el 02-09-2026** |
| `T-09` | `POST /api/v1/commission-rates/{id}/products/{productId}/deletion` | `T-03` | `204`, `400`, `403`, `404` | **Hecha el 02-09-2026** |
| `T-10` | Pruebas de los criterios de `spec.md` §12 | `T-09` | `CA-CM-073` a `CA-CM-078` | **Hecha el 02-09-2026** |
| `T-11` | **Prueba de que el registro lleva motivo**, no solo el tipo | `T-10` | `CA-CM-075` | **Hecha el 02-09-2026** |
| `T-12` | **Prueba de que el producto deja de comisionar**, resolviendo | `T-10` | `CA-CM-074` | **Hecha el 02-09-2026** |
| `T-13` | Documentación OpenAPI, diciendo que **el registro es lo único que queda** | `T-09` | La descripción lo dice, y explica el `404` | **Hecha el 02-09-2026** |
| `T-14` | Actualizar la matriz de `docs/requirements.md` y `cm.md` §4 | `T-10` | La fila registra la excepción al Art. I.1 | **Hecha el 02-09-2026** |

## 2. Orden de ejecución

**`T-04` es la única tarea del proyecto que endurece una regla de auditoría en lugar de heredarla**, y por eso está escrita aparte de `T-03`. `ck_deletion_reason` **exime** de motivo a las eliminaciones de tipo asociación —criterio correcto en general: al perder un vínculo, las dos filas que unía siguen contándolo todo—. **Aquí no queda ninguna otra fila que lo diga**, de modo que el motivo se exige en el caso de uso.

**`T-11` es la prueba que sostiene a `T-04`**, y su forma importa. Comprobar que el tipo es `ASSOCIATION` es rutina; comprobar que **el motivo está ahí** es lo que verifica la decisión — porque el motor habría aceptado la fila sin él, y **quitar la validación del caso de uso no rompería ninguna restricción del esquema**. Sin esta prueba, esa regla dependería de que nadie la borrara por parecer redundante.

**`T-05` parece una elección de consulta y es una decisión de negocio.** Buscar por rol y producto —que es la clave primaria— habría borrado la asociación equivocada cuando el producto está asociado a otra tasa del mismo rol.

**`T-08` va contra el reflejo del módulo**, donde los otros dos retiros responden `409` a lo repetido. Aquí no se puede: el borrado es físico y **no hay dato con el que afirmar que ya se hizo**.

## 3. Cobertura de los criterios de aceptación

| Criterio | Tareas |
|---|---|
| `CA-CM-073` | `T-01`, `T-05`, `T-10` |
| `CA-CM-074` | `T-12` |
| `CA-CM-075` | `T-04`, `T-06`, `T-07`, `T-11` |
| `CA-CM-076` | `T-03`, `T-10` |
| `CA-CM-077` | `T-08`, `T-10` |
| `CA-CM-078` | `T-09`, `T-10` |

## 4. Bloqueos

Ninguno.

**Queda declarado un riesgo que no se mitiga:** un motivo de un carácter satisface la regla, por decisión declarada en `architecture.md` §6.6.3. Aquí el coste de un motivo pobre es mayor que en cualquier otra eliminación del sistema, **porque no hay fila que consultar después**.

## 5. Definición de terminado

- Las catorce tareas `Hecha` con su verificación pasando.
- `./mvnw clean verify` en verde. **Comprobado el 02-09-2026**: 278 unitarias y 876 de integración.
- La matriz, `cm.md` y el contrato publicado al día.
