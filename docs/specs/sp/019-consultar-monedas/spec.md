# SPEC — `RF-SP-019` Consultar monedas

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-019` |
| Módulo | `SP` — Sistema Principal |
| Estado | **Borrador** |
| Autor | Responsable técnico |
| Aprobada por | — |
| Fecha de aprobación | — |

---

## 1. Objetivo

Disponer del catálogo de monedas con las que opera el sistema.

## 2. Contexto

Hoy el sistema opera en una sola moneda. El catálogo existe de todas formas, y esa es una decisión deliberada: si los importes se guardaran sin referencia a una moneda, incorporar la segunda obligaría a migrar cada tabla financiera y a revisar cada cálculo.

Teniendo el catálogo desde el principio, agregar una moneda es insertar una fila. Por eso la consulta existe aunque devuelva un único elemento.

## 3. Actores

| Actor | Rol en esta funcionalidad |
|---|---|
| Cualquier rol autenticado con el permiso | Consulta el catálogo para componer operaciones financieras |

## 4. Alcance

### 4.1 Incluye

- Listado de las monedas del catálogo, con su código, nombre y símbolo.

### 4.2 No incluye

- Crear, editar o eliminar monedas: el catálogo se puebla por migración (`RN-SP-010`).
- Tasas de cambio ni conversión entre monedas.

## 5. Reglas de negocio aplicables

| ID | Regla | Origen |
|---|---|---|
| `RN-SP-010` | Las monedas no se crean, editan ni eliminan por la API | `requirements/sp.md` §5.1 |

## 6. Datos

### 6.1 Entrada

Ninguno. El catálogo se devuelve completo.

### 6.2 Salida

| Dato | Descripción |
|---|---|
| Monedas | Código, nombre y símbolo de cada una |

## 7. Precondiciones y postcondiciones

**Precondiciones**

- El actor está autenticado y posee el permiso de lectura de monedas.

**Postcondiciones**

- Ninguna: la consulta no altera el estado del sistema.

## 8. Flujo principal

1. El actor solicita el catálogo de monedas.
2. El sistema recupera todas las monedas.
3. El sistema devuelve el catálogo completo.

## 9. Flujos alternativos

Ninguno.

## 10. Excepciones

Ninguna propia. Los fallos de autenticación y autorización se resuelven en el borde, como en cualquier endpoint.

## 11. Validaciones

Ninguna: la consulta no recibe parámetros.

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-SP-130` | El sistema devuelve el catálogo completo de monedas con código, nombre y símbolo |
| `CA-SP-131` | El sistema no expone ninguna operación de escritura sobre el catálogo |
| `CA-SP-132` | El catálogo contiene al menos la moneda con la que opera el sistema |
| `CA-SP-133` | El sistema rechaza la consulta a un actor sin el permiso de lectura de monedas |

## 13. Casos límite

- **Catálogo vacío:** solo ocurriría si faltara la migración de siembra. Conviene que el sistema lo detecte al arrancar, porque toda operación financiera dependería de él.
- **Moneda sin símbolo:** el símbolo es opcional; se devuelve vacío sin error.
- **Catálogo con una sola moneda:** es el estado esperado hoy; la respuesta sigue siendo una colección, no un objeto suelto.

## 14. Preguntas abiertas

| # | Pregunta | Responsable | Estado |
|---|---|---|---|
| 1 | ¿Debe indicarse cuál es la moneda por defecto del sistema? Con una sola no importa; con dos, sí | Responsable técnico | Abierta |
| 2 | ¿El catálogo debe llevar un indicador de moneda activa, para incorporar una sin habilitarla de inmediato? | Responsable técnico | Abierta |
| 3 | ¿Se registra el número de decimales de cada moneda? No todas usan dos, y el dato condiciona el redondeo de todo cálculo financiero | Responsable técnico | Abierta |

**Una spec con preguntas abiertas no puede aprobarse.** Esta sección debe quedar vacía antes de pasar la compuerta.
