# SPEC — `RF-MV-038` Dar de baja una cuenta de cobro propia

| Campo | Valor |
|---|---|
| Requerimiento | `RF-MV-038` |
| Módulo | `MV` — Movimientos |
| Versión | 0.1.0 |
| Estado | **Aprobada** |
| Autor | Responsable técnico |
| Aprobada por | Responsable del proyecto |
| Fecha de aprobación | 01-10-2026 |

!!! info "Qué va en este documento"

    **Qué debe pasar, y por qué.** Nada más.

    **Prueba de pertenencia:** si un cambio de tecnología lo invalidaría, no pertenece aquí — va a `plan.md`. No se nombran tablas, clases, endpoints ni librerías.

---

## 1. Objetivo

Que una persona **retire una cuenta** que ya no usa, para que no se le ofrezca más al pedir un retiro.

---

## 2. Contexto

Una cuenta cerrada en el banco, o una de una entidad desactivada (`RN-MV-054`), solo estorba en el selector del retiro. **Darla de baja no borra nada que haya ocurrido**: los retiros que se pagaron o se van a pagar hacia ella conservan su copia (`RN-MV-056`), y por eso se permite aunque haya un retiro pendiente. Lo decidió el responsable del proyecto el 01-10-2026.

### 2.1 Lo que este requerimiento decide

| Decisión | Qué se decidió |
|---|---|
| **Borrado lógico, sin vuelta atrás** | La cuenta deja de existir para su dueño y queda para administración (`RF-MV-039`). Quien la quiera otra vez la registra |
| **Si era la principal, la principal pasa a la más antigua de las que quedan** | `RN-MV-055`: con cuentas vivas siempre hay una principal, y la más antigua es la que lleva más tiempo en uso. Si no queda ninguna, no hay principal |
| **Se puede dar de baja la última** | La persona se queda sin cuentas y no podrá pedir retiros hasta registrar otra. Es su decisión |
| **Los retiros pendientes no cambian** | Su destino está copiado |
| **Una cuenta ajena o ya dada de baja no existe** | Como al editar (`RF-MV-037`) |

---

## 3. Actores

| Actor | Rol en esta funcionalidad |
|---|---|
| Quien tiene `movements:delete-own-payout-account` | Da de baja **las suyas** |

---

## 4. Alcance

### 4.1 Incluye

- Dar de baja una cuenta propia viva.
- Pasar la principal a otra, si hace falta.

### 4.2 No incluye

- **Recuperar una cuenta dada de baja.**
- **Que administración dé de baja la cuenta de otra persona.**

---

## 5. Reglas de negocio aplicables

| Regla | Cómo aplica |
|---|---|
| `RN-MV-055` | Borrado lógico; la principal pasa a la más antigua de las que quedan; solo su dueño |
| `RN-MV-056` | La copia de los retiros no cambia |

**Ninguna regla nueva.**

---

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción |
|---|---|---|
| Cuenta | Sí | Cuál |

### 6.2 Salida

Ninguna: la baja se confirma sin cuerpo.

---

## 7. Precondiciones y postcondiciones

| Tipo | Condición |
|---|---|
| Precondición | El actor tiene el permiso; la cuenta es suya y está viva |
| Postcondición | La cuenta está dada de baja y no es la principal; si lo era y quedan otras, la más antigua lo es; queda auditado |

---

## 8. Flujo principal

1. La persona indica la cuenta.
2. El sistema la lee: tiene que ser suya y estar viva.
3. La da de baja y, si era la principal, le quita la marca.
4. Si era la principal y le quedan cuentas vivas, **marca como principal la más antigua**.
5. Audita.

**Los pasos 3 y 4 son un solo acto.**

---

## 9. Flujos alternativos

Ninguno.

---

## 10. Excepciones

| ID | Situación | Resultado |
|---|---|---|
| `EX-001` | La cuenta no existe, no es suya o ya está dada de baja | No encontrado |
| `EX-002` | Quien pide no tiene `movements:delete-own-payout-account` | Prohibido |

---

## 11. Validaciones

| ID | Validación |
|---|---|
| `VAL-001` | La cuenta tiene forma de identificador |

---

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-MV-404` | Dar de baja una cuenta que no es la principal la saca de «mis cuentas» (`RF-MV-036`), y la principal no cambia |
| `CA-MV-405` | Dar de baja **la principal** hace principal a **la más antigua** de las que quedan |
| `CA-MV-406` | Dar de baja **la última** deja a la persona sin cuentas y sin principal; un retiro después responde que falta la cuenta (`RF-MV-019`) |
| `CA-MV-407` | Darla de baja **no cambia la copia** de un retiro pendiente pedido hacia ella, y ese retiro se puede aprobar |
| `CA-MV-408` | Una cuenta **ajena**, **ya dada de baja** o inexistente responde no encontrado, y nada cambia |
| `CA-MV-409` | Queda **auditada**; sin el permiso responde prohibido, y sin autenticar, `401` |

---

## 13. Casos límite

| Caso | Comportamiento |
|---|---|
| Dar de baja dos cuentas a la vez, una de ellas la principal | Se hacen una detrás de otra, y al final hay exactamente una principal si queda alguna cuenta |
| Registrar después la misma cuenta | Se admite (`RF-MV-035`), como una cuenta nueva |

---

## 14. Preguntas abiertas

Ninguna.

---

## 15. Control de cambios

| Versión | Fecha | Cambio | Autor |
|---|---|---|---|
| 0.1.0 | 01-10-2026 | Primera versión, con las cuentas de cobro de `MV` ([`requirements/mv.md`](../../../requirements/mv.md) v0.61.0 §4.5). **Borrado lógico sin vuelta atrás**; la principal pasa a la más antigua; se admite con retiros pendientes. Criterios `CA-MV-404` a `CA-MV-409`. | Responsable del proyecto |
