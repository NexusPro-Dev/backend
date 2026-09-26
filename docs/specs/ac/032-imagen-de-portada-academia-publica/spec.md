# SPEC — `RF-AC-032` Obtener la imagen de una portada de academia, sin autenticación

| Campo | Valor |
|---|---|
| Requerimiento | `RF-AC-032` |
| Módulo | `AC` — Academia |
| Estado | **En revisión** |
| Autor | Responsable técnico |
| Aprobada por | — |
| Fecha de aprobación | — |

---

## 1. Objetivo

Que el navegador pinte una portada de Academia **sin token**, a partir de la dirección que las lecturas devuelven en `coverImageUrl`.

## 2. Contexto

Es `RF-PM-016` sobre `academy_images`: la décima ruta pública del sistema y la primera de `AC` (`security.md` §7). **Todo se hereda**: los bytes con su tipo real, solo tres formatos, **caché inmutable de un año** —porque cada subida estrena identificador y una dirección nunca cambia de contenido—, `nosniff`, `404` uniforme para lo que no existe, se reemplazó o se quitó, la cota de tasa por familia, y que **no sabe de qué entidad es la imagen**: sirve los bytes por su identificador sin mirar la categoría, el curso ni el módulo — ni su estado, ni su retiro.

**Es lo único público de Academia** (`ac.md` §5.2.2): el aula entera exige sesión y `courses:learn`, y la lección abierta a todos es «todos los que tienen sesión». La portada se sirve sin token por lo mismo que la de `PM`: un `<img>` no lleva credencial, y el catálogo del alumno la pone en cada tarjeta.

## 3. Actores

| Actor | Papel |
|---|---|
| Cualquiera | Pide la imagen por su dirección |

## 4. Alcance

### 4.1 Incluye

- Servir los bytes de una fila de `academy_images` por su identificador, sin token, con el `Content-Type` detectado al subirla, caché inmutable y `nosniff`.
- Declarar la ruta pública **solo en `GET`** y su cota de tasa por familia.

### 4.2 No incluye

- **Saber de quién es la imagen**, ni comprobar nada de la entidad que la señala.
- **Redimensionar** ni servir variantes.
- **Listar imágenes.** El identificador solo se conoce por `coverImageUrl`.

## 5. Reglas de negocio aplicables

| ID | Regla | Origen |
|---|---|---|
| `RN-AC-004` | Se sirve sin token por su identificador; la dirección es por imagen | `requirements/ac.md` §5.1 |
| `RN-SEG-001`… | Ruta pública declarada por método y con su cota, como `RF-PM-016` | `security.md` §4, §7 |

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción | Restricción |
|---|---|---|---|
| `imageId` | Sí | Qué imagen | Ruta; UUID |

### 6.2 Salida

`200` con los bytes, `Content-Type` el detectado, `Content-Length` el tamaño, `Cache-Control: public, max-age=31536000, immutable`, `X-Content-Type-Options: nosniff`, `Content-Disposition: inline`.

## 7. Precondiciones y postcondiciones

**Precondiciones:** ninguna: sin token. **Postcondiciones:** ninguna.

## 8. Flujo principal

1. Llega el identificador.
2. El sistema valida su forma (`VAL-001`).
3. El sistema lee la fila **y nada más** (`EX-001`).
4. Devuelve los bytes con sus cabeceras.

## 9. Flujos alternativos

### FA-001 — Con token

**Comportamiento:** lo mismo, cabeceras incluidas.

### FA-002 — La entidad que la señalaba está retirada

**Comportamiento:** se sirve igual. La imagen existe mientras alguien la señale, y el retiro no la borra (`RF-AC-005` §4.2, `RF-AC-013` §4.2).

## 10. Excepciones

### EX-001 — La imagen no existe

**Respuesta del sistema:** `404` con el mismo cuerpo para un identificador que nunca existió, uno reemplazado y uno quitado.

## 11. Validaciones

| ID | Validación | Mensaje esperado |
|---|---|---|
| `VAL-001` | Identificador con formato válido | El identificador indicado no tiene un formato válido. |

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-AC-163` | El sistema devuelve **sin token** los bytes exactos que se subieron, con `Content-Type` igual al detectado —un `JPEG` subido como `image/png` vuelve como `image/jpeg`—, `Content-Length`, `Cache-Control: public, max-age=31536000, immutable`, `X-Content-Type-Options: nosniff` y `Content-Disposition: inline`; y **lo mismo con token** |
| `CA-AC-164` | `404` con el mismo cuerpo a un identificador inexistente, a uno **reemplazado** y a uno **quitado**; y `400` a uno mal formado |
| `CA-AC-165` | Sirve la portada de una categoría **retirada**, de un curso **inactivo** y de un módulo **retirado**: no lee ninguna de las tres tablas, y la prueba de sentencias cuenta **una** |
| `CA-AC-166` | La ruta está declarada pública **solo en `GET`** —`EndpointPermissionsIT` la lista como pública a propósito— y **`PUT`/`DELETE` bajo `/academy-images/` no existen**: subir y quitar viven bajo la entidad |
| `CA-AC-167` | `429` a la petición ciento veintiuna de un mismo origen en un minuto sobre identificadores distintos, con el prefijo `/api/v1/academy-images/` en el registro del rechazo, y **sin compartir contador** con `/api/v1/product-images/` |

## 13. Casos límite

| Caso | Decisión |
|---|---|
| `Accept: application/json` | Se sirve la imagen igual: la ruta no negocia contenido, como `RF-PM-016` |
| Un identificador de `product_images` | `404`: son dos tablas y dos rutas |
| `HEAD` | Lo que Spring haga por omisión con un `GET`; no se declara aparte |

## 14. Preguntas abiertas

| # | Pregunta | Resolución |
|---|---|---|
| 1 | ¿Una sola ruta para las dos tablas? | **No.** La ruta es de quien escribe la tabla; una ruta que consultara dos tablas de dos módulos tendría que vivir en `shared/` y conocer a los dos (`ac.md` §5.2.3). Dos rutas iguales sobre dos tablas iguales es el precio de §7 de `modules.md`, y es pequeño |

## 15. Control de cambios

| Versión | Fecha | Cambio | Responsable |
|---|---|---|---|
| 0.1.0 | 18-09-2026 | Redacción inicial. `RF-PM-016` sobre `academy_images`, todo heredado; la décima ruta pública y lo único público de Academia; cota propia por familia. | Responsable técnico |
| 0.2.0 | 25-09-2026 | **Construida** (`AcademyCoverIT` y `RateLimitIT`): pública solo en `GET` en `SecurityConfig`, con su motivo en `EndpointPermissionsIT` y **su propia familia** en `RateLimitFilter`. `CA-AC-166` se comprueba con un `PUT` sin token, que responde `401`: nada bajo el prefijo es público salvo el `GET`. | Responsable técnico |
