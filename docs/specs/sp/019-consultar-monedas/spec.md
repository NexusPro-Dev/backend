# SPEC — `RF-SP-019` Consultar monedas

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-019` |
| Módulo | `SP` — Sistema Principal |
| Estado | **Aprobada** |
| Autor | Responsable técnico |
| Aprobada por | Responsable técnico |
| Fecha de aprobación | 21-08-2026 |

---

## 1. Objetivo

Disponer del catálogo de monedas con las que opera el sistema.

## 2. Contexto

Hoy el sistema opera en una sola moneda. El catálogo existe de todas formas, y esa es una decisión deliberada: si los importes se guardaran sin referencia a una moneda, incorporar la segunda obligaría a migrar cada tabla financiera y a revisar cada cálculo.

Teniendo el catálogo desde el principio, agregar una moneda es insertar una fila. Por eso la consulta existe aunque devuelva un único elemento.

Cada moneda declara además **cuántos decimales usa** y si es la **moneda por defecto** del sistema. Los decimales no son un adorno: el redondeo de todo cálculo financiero depende de ellos, y no todas las monedas usan dos. Incorporarlos después obligaría a revisar cada importe ya guardado.

## 3. Actores

| Actor | Rol en esta funcionalidad |
|---|---|
| Cualquier rol autenticado con el permiso | Consulta el catálogo para componer operaciones financieras |

## 4. Alcance

### 4.1 Incluye

- Listado completo de las monedas del catálogo, con su código, nombre, símbolo, número de decimales, indicador de moneda por defecto y estado.
- Por defecto solo las monedas activas; las inactivas se piden explícitamente.

### 4.2 No incluye

- Crear, editar o eliminar monedas: el catálogo se puebla por migración (`RN-SP-010`).
- Cambiar el estado de una moneda → `RF-SP-023`.
- Tasas de cambio ni conversión entre monedas.

## 5. Reglas de negocio aplicables

| ID | Regla | Origen |
|---|---|---|
| `RN-SP-010` | Las monedas no se crean, editan ni eliminan por la API; lo único que puede cambiarse es su estado | `requirements/sp.md` §5.1 |

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción | Restricción de negocio |
|---|---|---|---|
| Incluir inactivas | No | Incorpora al resultado las monedas dadas de baja | Por defecto no |

No se pagina: el catálogo se devuelve completo.

### 6.2 Salida

| Dato | Descripción |
|---|---|
| Monedas | Código, nombre, símbolo, número de decimales, indicador de moneda por defecto y estado de cada una |

## 7. Precondiciones y postcondiciones

**Precondiciones**

- El actor está autenticado y posee el permiso de lectura de monedas.

**Postcondiciones**

- Ninguna: la consulta no altera el estado del sistema.

## 8. Flujo principal

1. El actor solicita el catálogo de monedas.
2. El sistema recupera las monedas activas, o todas si se pidieron también las inactivas.
3. El sistema devuelve el catálogo completo resultante.

## 9. Flujos alternativos

Ninguno.

## 10. Excepciones

Ninguna propia. Los fallos de autenticación y autorización se resuelven en el borde, como en cualquier endpoint.

## 11. Validaciones

Ninguna. El único parámetro es un indicador opcional que no admite valores inválidos.

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-SP-130` | El sistema devuelve el catálogo completo de monedas con código, nombre, símbolo, decimales, indicador de moneda por defecto y estado |
| `CA-SP-131` | El sistema no expone operación de creación, edición ni eliminación sobre el catálogo; el único cambio admitido es el estado, por `RF-SP-023` |
| `CA-SP-168` | Cada moneda devuelve su número de decimales |
| `CA-SP-169` | Exactamente una moneda del catálogo está marcada como moneda por defecto |
| `CA-SP-170` | Las monedas inactivas no aparecen salvo que se soliciten explícitamente |
| `CA-SP-132` | El catálogo contiene al menos la moneda con la que opera el sistema |
| `CA-SP-133` | El sistema rechaza la consulta a un actor sin el permiso de lectura de monedas |

## 13. Casos límite

- **Catálogo vacío:** solo ocurriría si faltara la migración de siembra. Conviene que el sistema lo detecte al arrancar, porque toda operación financiera dependería de él.
- **Moneda sin símbolo:** el símbolo es opcional; se devuelve vacío sin error.
- **Catálogo con una sola moneda:** es el estado esperado hoy; la respuesta sigue siendo una colección, no un objeto suelto.
- **Moneda sin decimales:** hay monedas que no usan fracción. Cero decimales es un valor legítimo y distinto de «no se sabe».
- **Moneda por defecto inactiva:** no debe poder ocurrir. Dar de baja la moneda con la que opera el sistema dejaría los importes sin referencia válida; lo impide `RF-SP-023`.

## 14. Preguntas abiertas

Ninguna. Las tres se resolvieron el 21-08-2026, antes de aprobar la especificación.

| # | Pregunta | Resolución |
|---|---|---|
| 1 | ¿Debe indicarse la moneda por defecto? | **Sí.** Es gratis hoy, con una sola moneda, y deja de serlo el día que haya dos: en ese momento habría que deducirla de alguna convención, y las convenciones deducidas no sobreviven. Exactamente una moneda del catálogo la lleva marcada (`CA-SP-169`) |
| 2 | ¿Indicador de moneda activa? | **Sí**, y por partida doble: permite incorporar una moneda sin habilitarla todavía, y es la salida para un alta equivocada en un catálogo que no se edita. Cambiarlo es un requerimiento nuevo, `RF-SP-023`, con su propio permiso. La moneda por defecto no puede desactivarse |
| 3 | ¿Se registra el número de decimales? | **Sí, y es el más importante de los tres.** Todo redondeo financiero depende de él, no todas las monedas usan dos, y añadirlo cuando ya haya importes guardados obliga a revisar cada cálculo hecho hasta entonces. Es el mismo argumento del §2: tener el catálogo desde el principio para que la segunda moneda no cueste una migración |
