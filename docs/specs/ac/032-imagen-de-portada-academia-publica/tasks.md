# TASKS — `RF-AC-032` Obtener la imagen de una portada de academia, sin autenticación

| Campo | Valor |
|---|---|
| Requerimiento | `RF-AC-032` |
| Especificación | [`spec.md`](spec.md) |
| Plan | [`plan.md`](plan.md), aprobado el 18-09-2026 |
| Estado | **Hecha** — todas las tareas `Hecha` el 25-09-2026; queda el Pull Request |
| Issue | Pendiente de crear |
| Rama | `feature/ajustes-academia` |
| Autor | Responsable técnico |

---

## 1. Tareas

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-01` | `domain/service/GetAcademyImageService` | `RF-AC-006` · `T-03` | Integración: devuelve la fila; vacío si no existe; una sentencia | Hecha |
| `T-02` | `interfaces/AcademyImageController`: `GET /api/v1/academy-images/{imageId}` con las cabeceras de caché, `nosniff`, `inline`, sin `produces` y con `security: []` | `T-01` | `CA-AC-163`, `CA-AC-164` | Hecha |
| `T-03` | **`shared/security`**: la ruta en la lista pública por método `GET` de `SecurityConfig`, la entrada con motivo en `EndpointPermissionsIT`, la familia con contador propio en `RateLimitFilter` | `T-02` | `CA-AC-166`, `CA-AC-167` | Hecha |
| `T-04` | Pruebas de API (`AcademyImageIT`) de `CA-AC-163` a `CA-AC-166` y el caso de `RateLimitIT` para `CA-AC-167` | `T-03` | Los cinco en verde | Hecha |
| `T-05` | Documentación OpenAPI. **La prosa dice** que es pública, que sirve bytes con el tipo real y caché inmutable, que no sabe de qué entidad es la imagen y que es lo único público de Academia | `T-02` | El contrato declara `200` con los tres tipos, `400`, `404`, `429`, `500` | Hecha |
| `T-06` | Actualizar la matriz de `docs/requirements.md`, `docs/api/index.md` y `docs/security.md` §7 (la ruta y la cota, de diseñadas a construidas) | `T-04` | Las filas reflejan el estado | Hecha |

## 2. Orden de ejecución

Lineal, tras `RF-AC-006`.

## 3. Cobertura de los criterios de aceptación

| Criterio | Tareas |
|---|---|
| `CA-AC-163`, `CA-AC-164`, `CA-AC-165` | `T-01`, `T-02`, `T-04` |
| `CA-AC-166`, `CA-AC-167` | `T-03`, `T-04` |

## 4. Bloqueos

| # | Bloqueo | Desde | Responsable | Estado |
|---|---|---|---|---|
| — | Ninguno | | | |

## 5. Definición de terminado

- [x] Todas las tareas en estado `Hecha`.
- [x] Todos los criterios de aceptación con prueba automatizada en verde.
- [x] `mvn verify` en verde en local.
- [x] La ruta pública está declarada en los tres sitios.
- [x] El contrato OpenAPI coincide con el comportamiento real, prosa incluida.
- [x] Matriz de trazabilidad, `docs/api/index.md` y `security.md` actualizados.
- [ ] Pull Request aprobado por alguien distinto del autor e integrado.
