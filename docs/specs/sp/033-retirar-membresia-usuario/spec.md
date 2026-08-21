# SPEC — `RF-SP-033` Retirar la membresía de un usuario

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-033` |
| Módulo | `SP` — Sistema Principal |
| Estado | **Aprobada** |
| Autor | Responsable técnico |
| Aprobada por | Responsable técnico |
| Fecha de aprobación | 21-08-2026 |

---

## 1. Objetivo

Retirar la membresía de quien ya no es consumidor, dejando su cuenta coherente.

## 2. Contexto

Este requerimiento cambió de naturaleza el 21-08-2026, al resolverse que **todo usuario con un rol `CONSUMIDOR` debe tener membresía** (`RN-SP-018`). Conviene explicar en qué se convirtió, porque su nombre sugiere algo que ya no hace.

### El rol de consumidor y la membresía son inseparables

`RN-SP-013` no admite membresía sin rol de consumidor. `RN-SP-018` no admite rol de consumidor sin membresía. Juntas hacen que el nivel de acceso no sea un atributo opcional de un cliente, sino **parte de lo que significa ser cliente**: se conceden juntos con `RF-SP-024` o `RF-SP-030`, y se sueltan juntos con `RF-SP-031`.

Eso deja fuera el caso que esta operación parecía cubrir. **Un cliente que deja de pagar no se queda sin membresía: baja de nivel** (`RF-SP-032`), o deja de ser cliente (`RF-SP-031`). «Consumidor sin nivel» no es un estado del sistema.

### Para qué existe, entonces

Para lo único que queda: **corregir la incoherencia**. Un usuario que conserva membresía sin ningún rol `CONSUMIDOR` no debería poder existir, pero puede aparecer —una migración, una corrección manual sobre la base de datos, un defecto que se arregla después—, y sin esta operación no habría forma de limpiarlo por la API.

Su uso es **excepcional por diseño**, y eso no la hace prescindible: sin ella, la única salida de un estado inconsistente sería otra intervención manual sobre los datos, que es exactamente lo que produjo el problema.

Retirar la membresía **elimina una asociación**, no una entidad de negocio, de modo que no se exige motivo por la excepción del Art. V.13 que `RN-SP-005` ya aplicó.

## 3. Actores

| Actor | Rol en esta funcionalidad |
|---|---|
| Super Administrador | Retira membresías incoherentes |
| Administrador | Retira membresías incoherentes |

## 4. Alcance

### 4.1 Incluye

- Retirar la membresía de una persona que **no porta ningún rol de clasificación `CONSUMIDOR`**.

### 4.2 No incluye

- Retirar la membresía de un consumidor: lo impide `RN-SP-018`, y lo que se busca en ese caso es bajar de nivel (`RF-SP-032`) o dejar de ser consumidor (`RF-SP-031`).
- Retirar el rol `CONSUMIDOR` de la persona → `RF-SP-031`, que es la puerta de salida y arrastra la membresía por su cuenta.
- Sustituir una membresía por otra → `RF-SP-032`.
- Eliminar la membresía de la cadena: las membresías son inmutables y no se borran (`RN-SP-008`).

## 5. Reglas de negocio aplicables

| ID | Regla | Origen |
|---|---|---|
| `RN-SP-018` | Todo usuario con rol `CONSUMIDOR` debe tener membresía | `requirements/sp.md` §5.1 |
| `RN-SP-014` | Un usuario tiene como mucho una membresía asignada | `requirements/sp.md` §5.1 |
| `RN-SP-005` | La eliminación de una asociación se audita sin motivo declarado | `requirements/sp.md` §5.1 |

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción | Restricción de negocio |
|---|---|---|---|
| Identificador del usuario | Sí | Persona a la que se retira la membresía | Debe existir, no estar eliminada y **no portar ningún rol `CONSUMIDOR`** |

No se indica cuál membresía se retira: solo puede haber una asignada (`RN-SP-014`). No se declara motivo: es la eliminación de una asociación (Art. V.13).

### 6.2 Salida

| Dato | Descripción |
|---|---|
| Confirmación | Resultado de la operación, sin cuerpo de datos |

## 7. Precondiciones y postcondiciones

**Precondiciones**

- El actor está autenticado y posee el permiso de asignación de membresías.
- El usuario existe y no está eliminado.
- El usuario **no porta ningún rol de clasificación `CONSUMIDOR`**.

**Postcondiciones**

- La persona queda sin ninguna membresía asignada.
- Sus roles, sus permisos efectivos y el estado de su cuenta se conservan intactos.
- La cuenta queda coherente con `RN-SP-018`: ni rol de consumidor ni membresía.
- Queda constancia en la auditoría de eliminación, sin motivo declarado, con la membresía que tenía al retirarse y su vigencia.

## 8. Flujo principal

1. El actor solicita retirar la membresía de un usuario.
2. El sistema verifica que el usuario exista y no esté eliminado.
3. El sistema verifica que el usuario no porte ningún rol de clasificación `CONSUMIDOR`.
4. El sistema retira la membresía asignada.
5. El sistema registra el evento en la auditoría de eliminación, conservando cuál era la membresía retirada y hasta cuándo estaba vigente.
6. El sistema confirma la operación.

## 9. Flujos alternativos

### FA-001 — La persona no tenía membresía

**Cuándo ocurre:** el usuario no tiene ninguna membresía asignada.

1. El sistema no aplica cambio ni registra evento.
2. Confirma la operación sin tratarlo como error: la operación es idempotente y su resultado —persona sin membresía— ya se cumplía.

## 10. Excepciones

### EX-001 — La persona porta un rol de consumidor

**Condición:** el usuario tiene al menos un rol de clasificación `CONSUMIDOR`.
**Respuesta del sistema:** rechaza la operación, cita `RN-SP-018` y explica las dos salidas reales: bajar de nivel con `RF-SP-032`, o dejar de ser consumidor con `RF-SP-031`, que retira la membresía por su cuenta.

Es la excepción que define este requerimiento: sin ella, esta operación sería una vía para producir el estado que `RN-SP-018` prohíbe.

### EX-002 — Usuario inexistente o eliminado

**Condición:** el identificador no corresponde a ningún usuario vigente.
**Respuesta del sistema:** rechaza la operación e informa que el usuario no existe.

## 11. Validaciones

| ID | Validación | Mensaje esperado |
|---|---|---|
| `VAL-001` | Identificador con formato válido | El identificador del usuario no es válido. |
| `VAL-002` | Usuario existente y no eliminado | El usuario solicitado no existe. |
| `VAL-003` | El usuario no porta ningún rol de consumidor | No es posible retirar la membresía de un consumidor. Cambie su nivel o retire su rol de consumidor. |

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-SP-281` | El sistema retira la membresía de una persona sin ningún rol de consumidor y la deja sin ninguna |
| `CA-SP-374` | El sistema **rechaza** retirar la membresía de quien porta un rol de consumidor, e indica las dos salidas reales |
| `CA-SP-282` | Los roles y los permisos efectivos de la persona no cambian al retirarle la membresía |
| `CA-SP-284` | El sistema confirma la operación sin error ni evento cuando la persona no tenía membresía |
| `CA-SP-285` | La auditoría de eliminación conserva cuál era la membresía retirada y hasta cuándo estaba vigente |
| `CA-SP-286` | La operación no solicita ni admite un motivo |
| `CA-SP-287` | La membresía retirada sigue existiendo en la cadena y puede volver a asignarse |
| `CA-SP-288` | El sistema rechaza la operación a un actor sin el permiso de asignación de membresías |

`CA-SP-283` queda **retirado**: verificaba que tras el retiro `RF-SP-031` admitiera quitar el último rol consumidor, y ese orden dejó de existir al invertirse la cascada. Su número queda consumido.

## 13. Casos límite

- **Persona con rol consumidor y membresía:** es el estado normal, y esta operación lo rechaza. No es un caso límite sino la regla.
- **Persona sin rol consumidor y con membresía:** es el único caso en que esta operación actúa, y es precisamente el que no debería existir. Que exista significa que algo lo produjo fuera de la API.
- **Persona con membresía vencida y sin rol consumidor:** se retira igual. Vencida o vigente, la asignación sobra.
- **Retiro seguido de reasignación:** la membresía sigue en la cadena y vuelve a asignarse, pero solo junto con un rol de consumidor (`RF-SP-030`). Retirar no borra nada del catálogo.
- **Retiro concurrente con la asignación de un rol consumidor:** ambas se serializan sobre el usuario. Si el rol se asigna primero, el retiro se rechaza; si el retiro va primero, la asignación exige indicar membresía. Ningún orden deja una cuenta incoherente.
- **Usuario inactivo o bloqueado:** admite el retiro; el eliminado se trata como inexistente, y además `RF-SP-029` ya le retiró la membresía.
- **Retirar la membresía superior de la cadena:** sin particularidad. El nivel no interviene.

## 14. Preguntas abiertas

Ninguna. Las tres se resolvieron el 21-08-2026, antes de aprobar la especificación. La primera cambió el diseño del módulo y obligó a enmendar `RF-SP-024`, `RF-SP-030` y `RF-SP-031`, ya aprobadas (Art. I.7).

| # | Pregunta | Resolución |
|---|---|---|
| 1 | ¿Qué alcance tiene un consumidor sin membresía? | **Ese estado no existe.** La pregunta se resolvió cambiando la premisa en lugar de contestándola: `RN-SP-018` establece que **todo usuario con rol `CONSUMIDOR` tiene membresía**. Un consumidor sin nivel era un dato sin significado que cada módulo habría interpretado a su manera, y las dos lecturas posibles —«ningún contenido» o «el nivel más bajo»— eran ambas defendibles, que es la señal de que la pregunta estaba mal planteada. **La regla obligó a resolver dos bloqueos mutuos:** con `RN-SP-013` exigiendo rol para tener membresía y `RN-SP-018` exigiendo membresía para tener rol, nadie podría entrar ni salir del estado de consumidor. La salida es que se conceden juntos —`RF-SP-024` y `RF-SP-030` exigen indicar la membresía al dar el primer rol de consumidor— y se sueltan juntos —`RF-SP-031` retira la membresía al quitar el último—. Este requerimiento queda como la operación **correctiva** de un estado que no debería producirse |
| 2 | ¿El retiro es una operación propia, o basta con `RF-SP-032` admitiendo una membresía vacía? | **Operación propia**, que es además lo que la tabla de API de `requirements/sp.md` §9 ya declara. Un endpoint que a veces asigna y a veces retira según venga o no un dato oculta dos operaciones con reglas distintas bajo una sola, y aquí las reglas son muy distintas: `RF-SP-032` exige rol de consumidor y esta operación exige lo contrario. Fundirlas habría hecho imposible declarar `EX-001` |
| 3 | ¿Debe conservarse el histórico de membresías de una persona? | **No se añade histórico ahora.** Se reconstruye desde la auditoría de cambios y la de eliminación, y desde que `RF-SP-032` incorporó la **fecha de fin** se ve además hasta cuándo estuvo vigente la última asignación. Queda anotado como riesgo: si «¿qué nivel tenía esta persona en marzo?» pasa a ser una consulta habitual —y lo será el día que se facture por periodos—, reconstruirla desde la auditoría cada vez es caro, y entonces será una tabla nueva con su política de retención |
