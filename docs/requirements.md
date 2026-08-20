# Catálogo de Módulos y Trazabilidad — NEXUS

| Campo | Valor |
|---|---|
| Proyecto | NEXUS — Renovación de plataforma |
| Empresa | FACTECH GROUP SAS |
| Documento | `requirements.md` |
| Versión | 0.1.0 |
| Estado | Borrador |
| Responsable técnico | Bonilla Diaz William Steven |
| Fecha de creación | 20-08-2026 |
| Última actualización | 20-08-2026 |
| Documento superior | `constitution.md` v0.3.0 |

---

## 1. Propósito

Este documento es el **registro central de módulos** del sistema y la **matriz de trazabilidad** de sus requerimientos.

Responde tres preguntas:

1. ¿Qué módulos componen NEXUS y qué hace cada uno?
2. ¿Dónde vive cada módulo — su documento de requerimientos, su paquete de código, sus permisos?
3. ¿En qué estado está cada requerimiento y qué lo implementa?

Es el punto de entrada obligado antes de crear un módulo o un requerimiento nuevo. **Un módulo que no está en §2 no existe** para efectos del proyecto.

---

## 2. Catálogo de módulos

!!! warning "Catálogo incompleto"

    Solo están registrados los módulos que el Documento Marco nombra de forma explícita. **Falta inventariar el resto del alcance del producto.** Cada fila nueva debe completarse antes de iniciar el desarrollo del módulo correspondiente.

| Código | Módulo | Propósito | Paquete Java | Prefijo de permisos | Depende de | Estado | Documento |
|---|---|---|---|---|---|---|---|
| `SP` | Sistema Principal | Roles, permisos y configuración transversal del sistema | `modules/system` | `roles:`, `permissions:` | — | En diseño | `requirements/sp.md` |
| `USR` | Usuarios | Identidad, credenciales y asignación de roles | `modules/users` | `users:` | `SP` | En diseño | `requirements/usr.md` |
| | | *(pendiente de inventariar)* | | | | | |

**Estados posibles de un módulo:** `Propuesto` · `En diseño` · `En desarrollo` · `Implementado` · `Obsoleto`.

### 2.1 Reglas del catálogo

- El **código** es corto, en mayúsculas, único y estable. Una vez usado en un identificador de requerimiento, **no se cambia nunca**: rompería la trazabilidad de todo lo ya escrito.
- El **paquete Java** y el **prefijo de permisos** se declaran aquí y son la referencia para el código. Nadie los elige al momento de programar.
- Las **dependencias** entre módulos deben ser acíclicas (`architecture.md` §5.3). Si dos módulos se necesitan mutuamente, o son un solo módulo o falta extraer un tercero.
- Un módulo `Obsoleto` conserva su fila y su código; no se borra, porque sus requerimientos siguen referenciados en la historia.

### 2.2 Punto abierto: código de módulo frente a nombre de paquete

Los ejemplos de `architecture.md` y `security.md` usan el paquete `modules/security` para el trabajo de roles y permisos, mientras que el Documento Marco asigna ese alcance al módulo `SP` (Sistema Principal), cuyo paquete natural sería `modules/system`.

Hay que resolverlo antes de escribir la primera clase, porque el nombre queda fijado en cientos de archivos. Dos salidas:

- **Alinear el paquete al código:** `SP` → `modules/system`. Conserva la nomenclatura del Documento Marco, ya aprobado.
- **Alinear el código al paquete:** renombrar el módulo a `SEG`/`security`. Más descriptivo, pero `SEG` ya se usa como categoría de requerimiento no funcional (§3.2) y como prefijo de las reglas `RN-SEG-…` de `security.md`, lo que genera ambigüedad.

Esta tabla registra provisionalmente la primera opción. **Requiere confirmación.**

---

## 3. Nomenclatura de identificadores

### 3.1 Formatos

| Tipo | Formato | Ejemplo |
|---|---|---|
| Requerimiento funcional | `RF-[MÓDULO]-NNN` | `RF-SP-001` |
| Requerimiento no funcional | `RNF-[CATEGORÍA]-NNN` | `RNF-SEG-001` |
| Regla de negocio | `RN-[MÓDULO]-NNN` | `RN-SP-003` |
| Criterio de aceptación | `CA-[MÓDULO]-NNN` | `CA-SP-001` |
| Validación | `VAL-NNN` | `VAL-001` |
| Especificación | `SPEC-[MÓDULO]-NNN` | `SPEC-SP-001` |
| Prueba | `T-[MÓDULO]-NNN` | `T-SP-001` |

La numeración es correlativa **dentro de cada módulo** y nunca se reutiliza, ni siquiera si el requerimiento se descarta. Un identificador retirado se marca como tal en la matriz.

### 3.2 Categorías de requerimientos no funcionales

Las categorías **no son módulos**: comparten el espacio de nombres pero clasifican atributos de calidad.

| Categoría | Atributo |
|---|---|
| `SEG` | Seguridad |
| `PERF` | Rendimiento |
| `USA` | Usabilidad |
| `MAN` | Mantenibilidad |
| `PORT` | Portabilidad |
| `FIA` | Fiabilidad |
| `COMP` | Compatibilidad |

---

## 4. Cómo se incorpora un módulo

1. Registrar la fila en el catálogo de §2, con código, propósito, paquete, prefijo de permisos y dependencias.
2. Crear `docs/requirements/<código en minúscula>.md` a partir de la plantilla de requerimientos por módulo.
3. Documentar en él: descripción, objetivo, alcance y **no alcance**, dependencias, reglas de negocio, requerimientos funcionales, integraciones, API y persistencia.
4. Agregar el documento a `nav` en `mkdocs.yml`. Sin esto, la construcción del sitio falla (`development-guide.md` §2.5).
5. Registrar sus requerimientos en la matriz de §5.
6. Solo entonces comienza la especificación de cada funcionalidad en `docs/specs/<módulo>/`.

El orden importa: la especificación precede a la implementación (Art. I.1), y el catálogo precede a la especificación.

---

## 5. Matriz de trazabilidad

Implementa el Art. III.1. Se actualiza **como parte del cambio**, no después (Art. III.6).

| ID | Requerimiento | Módulo | Especificación | Issue | PR | Pruebas | Estado |
|---|---|---|---|---|---|---|---|
| — | *(sin requerimientos registrados)* | — | — | — | — | — | — |

**Estados:** `Pendiente` · `Especificado` · `En desarrollo` · `En revisión` · `Implementado` · `Descartado`.

Un requerimiento solo pasa a `Implementado` cuando cumple **todas** las condiciones de la definición de terminado (Art. §16 de la constitución).

---

## 6. Estado general

| Indicador | Valor |
|---|---|
| Módulos registrados | 2 |
| Módulos implementados | 0 |
| Requerimientos registrados | 0 |
| Requerimientos implementados | 0 |

---

## 7. Control de cambios

| Versión | Fecha | Cambio | Responsable |
|---|---|---|---|
| 0.1.0 | 20-08-2026 | Creación inicial. Catálogo con los módulos nombrados por el Documento Marco. | Responsable técnico |
