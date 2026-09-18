# PLAN — `RF-AC-032` Obtener la imagen de una portada de academia, sin autenticación

| Campo | Valor |
|---|---|
| Requerimiento | `RF-AC-032` |
| Especificación | [`spec.md`](spec.md), en revisión desde el 18-09-2026 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 18-09-2026 |

---

## 1. Enfoque

**`ProductImageController` y `GetProductImageService` sobre `academy_images`, y tres declaraciones en `shared/security`.** El controlador copia el de `PM` —sin `produces`, `security: []` en el contrato, las cabeceras de caché, el `404` uniforme—; lo que hay que hacer con cuidado son las tres declaraciones: la ruta en la lista **por método** de `SecurityConfig`, la excepción con motivo en `EndpointPermissionsIT`, y **la familia propia** en `RateLimitFilter` para que no comparta contador con la de `PM`.

## 2. Cambios de esquema

**Ninguno.** `academy_images` la creó `RF-AC-006`.

## 3. Componentes afectados

| Capa | Elemento | Módulo |
|---|---|---|
| `domain/service` | `GetAcademyImageService`: `findById` y nada más; `404` si no está | `AC` |
| `interfaces` | **`AcademyImageController`** — `GET /api/v1/academy-images/{imageId}` | `AC` |
| `application` | `AcademyImageUrls` (de `RF-AC-001`), **sin cambio**: ya conoce la ruta | `AC` |
| `shared/security` | `SecurityConfig`: `/api/v1/academy-images/*` en la lista pública **por método `GET`**, junto a `PORTADAS_PUBLICAS` de `PM`; `EndpointPermissionsIT`: la entrada con su motivo; `RateLimitFilter`: la familia `/api/v1/academy-images/` con la cota de los catálogos (120 por minuto y origen), **contador propio** | `shared` |

## 4. Contrato de API

`GET /api/v1/academy-images/{imageId}` — público. `200` con `image/jpeg`, `image/png` o `image/webp` (`string`/`binary`); `400`, `404`, `429`, `500` como `problem+json`.

## 5. Autorización

**Ninguna en la ruta, y declarada en tres sitios**: `SecurityConfig` (por método), `EndpointPermissionsIT` (con motivo) y el contrato (`security: []`).

## 6. Auditoría

No audita: es una lectura pública de bytes, como `RF-PM-016`.

## 7. Transaccionalidad

`@Transactional(readOnly = true)`. **Una sentencia.**

## 8. Impacto sobre otros módulos

**Ninguno.** `shared/security` gana una ruta pública y una familia de cota; `security.md` §7 ya la tiene diseñada y pasa a construida.

## 9. Alternativas consideradas

| Alternativa | Por qué se descartó |
|---|---|
| **Una ruta para las dos tablas** | `spec.md` §14.1 |
| **Compartir el contador de tasa con `/product-images/`** | Un catálogo de cursos con muchas portadas agotaría la cota de los productos; cada familia la suya, como cada catálogo público la suya |
| **`/**` en la lista pública** | Abriría cualquier ruta futura bajo el prefijo; el patrón es de un segmento, como el de `PM` |

## 10. Riesgos

| # | Riesgo | Mitigación |
|---|---|---|
| 1 | **Se declara pública con `/**`** o sin acotar el método | `CA-AC-166`: `PUT`/`DELETE` bajo el prefijo no existen y la lista blanca de `EndpointPermissionsIT` solo tiene el `GET` |
| 2 | **La familia se olvida en `RateLimitFilter`** y la ruta queda sin cota | `CA-AC-167` |

## 11. Estrategia de prueba

- **Integración de API** (`AcademyImageIT`): `CA-AC-163` a `CA-AC-166`, con el contador de sentencias.
- **De cota** (`RateLimitIT`, que enciende el límite para su clase): `CA-AC-167`, con la familia propia.
