# TASKS — `RF-CM-007` Asociar una tasa de rol a un producto

| Campo | Valor |
|---|---|
| Requerimiento | `RF-CM-007` |
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
| `T-01` | `V48`: crear `product_commission_rates` con **clave primaria `(product_id, role_id)`** | `RF-CM-001` · `T-03` | Dos tasas del mismo rol sobre el mismo producto chocan | **Hecha el 02-09-2026** |
| `T-02` | `V48`: la clave foránea **compuesta** hacia `commission_rates(id, role_id)` | `T-01` | Un rol copiado distinto del de la tasa **no se puede escribir** | **Hecha el 02-09-2026** |
| `T-03` | `V48`: índice por tasa, para la lectura «sobre qué productos rige» | `T-01` | La clave primaria ya cubre la otra dirección | **Hecha el 02-09-2026** |
| `T-04` | `ProductCommissionRate` con clave compuesta, y `equals`/`hashCode` en ella | `T-01` | Dos lecturas de la misma fila son la misma entidad | **Hecha el 02-09-2026** |
| `T-05` | `create(...)` **recibe la tasa entera**, no su identificador, y copia el rol de ahí | `T-04` | No hay forma de pasarle un rol distinto | **Hecha el 02-09-2026** |
| `T-06` | Puerto y adaptador de escritura, con búsqueda **por tasa y producto** | `T-04` | Buscar por rol borraría una asociación que nadie pidió retirar | **Hecha el 02-09-2026** |
| `T-07` | Traducir la violación de la clave primaria, con **mensaje sobre el ROL** | `T-06` | El texto no dice «esta tasa ya está asociada» | **Hecha el 02-09-2026** |
| `T-08` | `AssociateProductService`: tasa viva, producto no retirado, y escribir | `T-05`, `T-06` | Tasa retirada `404`, producto retirado `409`, inexistente `422` | **Hecha el 02-09-2026** |
| `T-09` | `AssociateProductRequest` **con un solo campo**, sin rol | `T-08` | Enviar `roleId` da `400`, no se ignora | **Hecha el 02-09-2026** |
| `T-10` | `ProductAssociationResponse`, con la colección **envuelta** | `T-08` | Va bajo `content`, no en la raíz | **Hecha el 02-09-2026** |
| `T-11` | `POST /api/v1/commission-rates/{id}/products`, devolviendo **todas** | `T-08`, `T-09`, `T-10` | `201`, `400`, `403`, `404`, `409`, `422` | **Hecha el 02-09-2026** |
| `T-12` | Auditoría de creación **con el porcentaje** de la tasa | `T-08` | Permite reconstruir qué se puso en vigor ese día | **Hecha el 02-09-2026** |
| `T-13` | Pruebas de los criterios de `spec.md` §12 | `T-11` | `CA-CM-063` a `CA-CM-072` | **Hecha el 02-09-2026** |
| `T-14` | **Prueba de `RN-CM-013` con OTRA tasa del mismo rol** | `T-13` | `CA-CM-066` | **Hecha el 02-09-2026** |
| `T-15` | **Prueba de que el rol se copia de la tasa** | `T-13` | `CA-CM-065`, en sus dos mitades | **Hecha el 02-09-2026** |
| `T-16` | **Prueba concurrente**: dos asociaciones simultáneas del mismo rol | `T-13` | `CA-CM-071`: una `201`, otra `409`, **una sola fila** | **Hecha el 02-09-2026** |
| `T-17` | Documentación OpenAPI, diciendo que **es lo único que pone la tasa en vigor** | `T-11` | La descripción lo dice y nombra `RN-CM-012` | **Hecha el 02-09-2026** |
| `T-18` | Actualizar la matriz de `docs/requirements.md` y `cm.md` §4 | `T-13` | La fila registra la excepción al Art. I.1 | **Hecha el 02-09-2026** |

## 2. Orden de ejecución

**`T-01` y `T-02` son las dos tareas que meten reglas de negocio en el esquema, y ninguna se comprueba en código.** Conviene tenerlas juntas y a la vista:

| Tarea | Regla que se vuelve imposible |
|---|---|
| `T-01` | **Un rol no puede tener dos porcentajes sobre un producto.** Si la clave incluyera la tasa, dos tasas distintas del mismo rol cabrían las dos |
| `T-02` | **El rol copiado no puede divergir del de la tasa.** Sin la foránea compuesta, la resolución buscaría por un rol y pagaría el porcentaje de otro |

**`T-05` es una decisión de firma con consecuencias de negocio.** `create(...)` recibe **la tasa** y no su identificador, y por eso el rol solo puede salir de un sitio. Cambiarla a recibir identificadores sueltos reabriría la puerta que `T-02` cierra en el motor — y el error llegaría entonces como una violación de integridad en lugar de como lo que es.

**`T-14` prueba `RN-CM-013` con OTRA tasa del mismo rol, y esa elección es la prueba.** Repetir la misma tasa dos veces fallaría también con una clave primaria mal puesta sobre `(product_id, commission_rate_id)`. **Solo el caso de dos tasas distintas del mismo rol distingue la clave correcta de la equivocada.**

**`T-16` verifica dónde vive la regla.** `T-14` pasaría igual si `RN-CM-013` se comprobara con una consulta previa; esta no.

## 3. Cobertura de los criterios de aceptación

| Criterio | Tareas |
|---|---|
| `CA-CM-063` | `T-08`, `T-10`, `T-11`, `T-13` |
| `CA-CM-064` | `T-08`, `T-13`, y `RF-CM-005` · `T-12` |
| `CA-CM-065` | `T-05`, `T-09`, `T-15` |
| `CA-CM-066` | `T-01`, `T-07`, `T-14` |
| `CA-CM-067`, `CA-CM-068` | `T-01`, `T-13` |
| `CA-CM-069` | `T-08`, `T-13` |
| `CA-CM-070` | `T-08`, `T-13` |
| `CA-CM-071` | `T-16` |
| `CA-CM-072` | `T-11`, `T-13` |

## 4. Bloqueos

Ninguno.

**Queda declarado un riesgo que no se puede cerrar desde aquí:** la asociación **no tiene retiro lógico** y sobreviviría al retiro de su tasa. Se cierra en `RF-CM-004` con `RN-CM-015`, porque **una clave foránea no distingue una fila viva de una retirada lógicamente**.

## 5. Definición de terminado

- Las dieciocho tareas `Hecha` con su verificación pasando.
- `./mvnw clean verify` en verde, **incluida la concurrente de `T-16`**. Comprobado el 02-09-2026: 278 unitarias y 876 de integración.
- La matriz, `cm.md` y el contrato publicado al día.
