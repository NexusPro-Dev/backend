# SPEC — `RF-SP-077` Exigir el segundo factor a los portadores de un rol

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-077` |
| Módulo | `SP` — Sistema Principal |
| Versión | 0.1.0 |
| Estado | **Aprobada** |
| Autor | Responsable técnico |
| Aprobada por | Responsable del proyecto |
| Fecha de aprobación | 06-10-2026 |

!!! info "Qué va en este documento"

    **Qué debe pasar, y por qué.** Nada más.

    **Prueba de pertenencia:** si un cambio de tecnología lo invalidaría, no pertenece aquí — va a `plan.md`. No se nombran tablas, clases, endpoints ni librerías.

---

## 1. Objetivo

Decidir, **rol por rol**, si quienes lo portan están obligados a usar la app autenticadora. Y que cada persona sepa, al consultar su perfil, si tiene el factor y si está obligada.

---

## 2. Contexto

El responsable del proyecto decidió el 06-10-2026 que el segundo factor sea **obligatorio por rol**: ni para todos —sería una barrera para clientes y vendedores que no manejan dinero ajeno— ni para nadie. `SUPERADMIN` y `ADMIN` nacen obligados; el resto lo decide quien administra roles con esta operación.

### 2.1 Lo que este requerimiento decide

| Decisión | Qué se decidió |
|---|---|
| **Es una operación propia, no un dato más de la edición del rol** | Tiene su propio permiso y es sensible; cambiar el nombre de un rol no lo es |
| **Se admite sobre los roles de sistema** | Lo que los roles de sistema protegen es su identidad y su posición en la jerarquía, no lo que exigen a sus portadores. Es la misma excepción que ya tienen sus permisos |
| **La raíz lo exige siempre** | A `SUPERADMIN` no se le puede quitar |
| **Nadie lo cambia en un rol que porta** | Como con los permisos (`RN-SEG-011`): quien porta `ADMIN` no se quita la obligación a sí mismo. Solo alguien que no lo porta —en la práctica, el superadministrador— cambia la de `ADMIN` |
| **El efecto llega con la siguiente renovación de sesión** | Quien ya tiene sesión queda retenido, o liberado, en quince minutos como mucho. **No se cierran sesiones**: retener a la vez a todas las personas de un rol sin dejarlas activar nada sería peor que esperar un cuarto de hora |
| **Un rol inactivo no obliga** | Como no concede permisos (`RN-SEG-002`), tampoco exige el factor. Se puede marcar igual |
| **La respuesta dice a quién afecta** | Cuántas personas activas portan el rol y cuántas de ellas no tienen el factor activo: las que quedarán retenidas |

---

## 3. Actores

| Actor | Rol en esta funcionalidad |
|---|---|
| Quien tiene el permiso de exigir el factor | Marca o desmarca un rol que no porta |
| Cualquier persona | Ve en su perfil si tiene el factor y si está obligada |

---

## 4. Alcance

### 4.1 Incluye

- Marcar y desmarcar un rol.
- Mostrar la marca en el listado y el detalle de roles.
- Mostrar en el propio perfil el estado del factor y la obligación.

### 4.2 No incluye

- **Obligar a una persona concreta** sin pasar por su rol.
- **Un plazo de gracia** antes de retener.
- **Avisar por correo** a las personas afectadas.

---

## 5. Reglas de negocio aplicables

| Regla | Cómo aplica |
|---|---|
| `RN-SP-062` | El rol obliga; la raíz siempre |
| `RN-SEG-011` | Nadie lo cambia en un rol que porta directamente |
| `RN-SEG-012` | No impide esta operación sobre roles de sistema |
| `RN-SEG-002` | Un rol inactivo no obliga |
| `RN-SP-063` | Es sensible |

---

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción |
|---|---|---|
| Rol | Sí | Cuál |
| Exigido | Sí | Sí o no |

### 6.2 Salida

**Marcar:** el rol con su marca, cuántas personas activas lo portan y cuántas no tienen el factor.

**Perfil propio** (enmienda de `RF-SP-039`): si la persona tiene el factor activo, desde cuándo, y si alguno de sus roles lo exige.

**Listado y detalle de roles** (enmienda de `RF-SP-002` y `RF-SP-003`): la marca de cada rol.

---

## 7. Precondiciones y postcondiciones

| Tipo | Condición |
|---|---|
| Precondición | Permiso; verificación reciente; el rol existe y no está eliminado; el actor no lo porta directamente; no se desmarca la raíz |
| Postcondición | El rol tiene la marca pedida; si cambió, está auditado; nada más cambia hasta que cada portador renueve su sesión |

---

## 8. Flujo principal

1. El actor reverifica (`RF-SP-073`), si no lo hizo hace poco.
2. Indica el rol y si lo exige.
3. El sistema comprueba las condiciones, guarda la marca, audita y devuelve el rol con las dos cifras.

---

## 9. Flujos alternativos

### FA-001 — La marca ya tiene ese valor

Se responde con el rol y las cifras, **sin escribir nada ni auditar**: no ha pasado nada.

---

## 10. Excepciones

| ID | Situación | Resultado |
|---|---|---|
| `EX-001` | Desmarcar la raíz | Rechazo, diciendo que la raíz lo exige siempre. Nada cambia |
| `EX-002` | El actor porta el rol directamente | Prohibido, como al tocar sus permisos |
| `EX-003` | El rol no existe o está eliminado | No encontrado |
| `EX-004` | Falta la marca | Rechazo por formato |
| `EX-005` | Sin verificación reciente | Prohibido, con el aviso de reverificación |
| `EX-006` | Sin el permiso | Prohibido |

---

## 11. Validaciones

| ID | Validación | Mensaje esperado |
|---|---|---|
| `VAL-001` | Marca presente | Debe indicar si el rol exige el segundo factor. |
| `VAL-002` | No se desmarca la raíz | El rol raíz exige siempre el segundo factor. |
| `VAL-003` | El actor no porta el rol | No puede cambiar la exigencia de un rol que tiene asignado. |

---

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-SP-880` | Marcar un rol hace que sus portadores sin factor queden **retenidos en su siguiente renovación** de sesión; sus sesiones **no** se cierran |
| `CA-SP-881` | Desmarcarlo los libera en su siguiente renovación, y desde entonces pueden desactivar su factor |
| `CA-SP-882` | Desmarcar la raíz se rechaza y nada cambia |
| `CA-SP-883` | Pedir el valor que ya tiene responde con el rol, **sin escribir ni auditar** |
| `CA-SP-884` | Se admite sobre un rol de sistema que el actor no porta |
| `CA-SP-885` | Sobre un rol que el actor porta directamente responde prohibido y nada cambia |
| `CA-SP-886` | Un rol inexistente o eliminado responde no encontrado |
| `CA-SP-887` | Un rol **inactivo** se puede marcar y **no retiene** a nadie mientras siga inactivo |
| `CA-SP-888` | La respuesta dice cuántas personas activas portan el rol y cuántas de ellas no tienen el factor activo |
| `CA-SP-889` | El cambio queda en la auditoría de seguridad con severidad alta y en la de cambios con el antes y el después |
| `CA-SP-890` | Sin verificación reciente responde prohibido con el aviso de reverificación |
| `CA-SP-891` | El listado y el detalle de roles muestran la marca; `SUPERADMIN` y `ADMIN` la tienen desde la siembra |
| `CA-SP-892` | El propio perfil dice si la persona tiene el factor activo, desde cuándo, y si está obligada |
| `CA-SP-893` | Sin el permiso responde prohibido; sin autenticar, `401` |

---

## 13. Casos límite

| Caso | Comportamiento |
|---|---|
| Una persona porta dos roles, uno obligado y otro no | Está obligada: basta uno (`RN-SP-062`) |
| Se marca un rol cuyas personas ya tienen todas el factor | No retiene a nadie; la respuesta dice cero sin factor |
| Se asigna un rol obligado a alguien sin factor (`RF-SP-030`) | Queda retenido en su siguiente renovación, igual que si se marcara el rol después |
| El superadministrador cambia la marca de `ADMIN` | Se admite: no porta `ADMIN` |

---

## 14. Preguntas abiertas

**Plazo de gracia** y **aviso por correo** a los afectados: no se han pedido.

---

## 15. Control de cambios

| Versión | Fecha | Cambio | Autor |
|---|---|---|---|
| 0.1.0 | 06-10-2026 | Primera versión ([`requirements/sp.md`](../../../requirements/sp.md) v1.95.0, `RN-SP-062`). **Enmienda `RF-SP-002`, `RF-SP-003`** —la marca en el listado y el detalle de roles— **y `RF-SP-039`** —el estado del factor en el propio perfil—. | Responsable técnico |
