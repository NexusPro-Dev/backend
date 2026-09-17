# PLAN — `RF-PM-029` Quitar la portada de un paquete

| Campo | Valor |
|---|---|
| Requerimiento | `RF-PM-029` |
| Especificación | [`spec.md`](spec.md), en revisión desde el 16-09-2026 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 16-09-2026 |

---

## 1. Enfoque

**`RemoveProductCoverService` con el paquete, y sin la regla.**

Los pasos son los de `RF-PM-015` `plan.md` §1: encontrar bloqueando, cambiar `cover_image_id`, volcar, borrar la imagen, auditar — y el atajo: sin portada no hay nada que hacer, y lo dice el agregado devolviendo un diff vacío. Lo que **no** hay es `RN-PM-034`: `ProductPackage.quitarPortada()` no comprueba nada, porque el paquete no tiene icono del que depender (`RN-PM-045`). El método existe desde `RF-PM-028` (`T-03` de aquella); este requerimiento le pone el servicio y la ruta.

## 2. Cambios de esquema

**Ninguno.** `V11` (`RF-PM-028`) ya tiene la columna, y la clave foránea sin `ON DELETE` es lo que obliga a soltar la imagen **antes** de borrarla.

## 3. Componentes afectados

| Capa | Elemento | Módulo |
|---|---|---|
| `domain/models` | `ProductPackage.quitarPortada(ahora)` → `CambioDePortada` con la imagen que había y el diff; **vacío y sin diff** si no había; **nunca lanza** | `PM` |
| `domain/service` | **`RemovePackageCoverService`** | `PM` |
| `domain/repository` | Reutiliza `ProductImageRepository.deleteById` de `RF-PM-014` | `PM` |
| `interfaces` | `PackageController` — `DELETE /api/v1/packages/{id}/cover` → `200` con `PackageDetailResponse` | `PM` |

## 4. Contrato de API

`DELETE /api/v1/packages/{id}/cover` — `packages:update`. Sin cuerpo. `200` con `PackageDetailResponse`.

- **Sin `@RequestBody`**: un cuerpo, si llega, ni se lee.
- **`200` y no `204`**, con el paquete: `spec.md` §14.2.
- **La prosa de la `@Operation` dice tres cosas**: que **nunca responde `400`** —el paquete no declara icono ni color, y sin portada el cliente pinta los suyos por omisión—; que sin portada responde igual y no escribe; y que la imagen se borra y su dirección deja de servir. Es la prosa que le dice al frontend qué pintar cuando `coverImageUrl` es nula.

## 5. Autorización

`@PreAuthorize("hasAuthority('packages:update')")`. Sin cambios en el catálogo ni en `SecurityConfig`. `EndpointPermissionsIT` la incorpora.

## 6. Auditoría

`ChangeEvent` `UPDATE` de `product_packages` con solo `cover_image_id`: `{"before": <uuid>, "after": ""}`. **Ninguna fila cuando no había portada** (`FA-001`).

## 7. Transaccionalidad

`@Transactional`. **Cuatro sentencias** cuando hay portada: el paquete con `FOR UPDATE`, el `UPDATE`, el `DELETE` de la imagen, la auditoría; **una** cuando no la hay; y después, las de `PackageDetailReader` para la respuesta. **`flush()` explícito** entre el `UPDATE` y el `DELETE`, por lo que `RF-PM-015` `plan.md` §7 explica: el orden correcto no debe depender de una regla de Hibernate que nadie va a recordar.

## 8. Impacto sobre otros módulos

**Ninguno.**

## 9. Alternativas consideradas

| Alternativa | Por qué se descartó |
|---|---|
| **Exigirle al paquete «algo con qué pintarse» como al upgrade** | No tiene nada que declarar: el icono y el color los pone el frontend (`requirements/pm.md` §5.2.12). Sería una regla sin un dato que la sostenga |
| **`204` sin cuerpo** | Queda un paquete que cambió y el cliente lo repinta con su cuenta hecha (`spec.md` §14.2) |
| **`404` cuando no hay portada** | «Quítala» sobre un paquete sin portada ya está hecho; un `404` diría que el paquete no existe, y existe |
| **Un servicio común para las dos entidades** | Dos repositorios, dos agregados y dos lectores detrás de un `if`, para veinte líneas (`RF-PM-028` `plan.md` §9) |

## 10. Riesgos

| # | Riesgo | Mitigación |
|---|---|---|
| 1 | **Alguien añade una condición «por simetría» con el producto** | `CA-PM-362` dice en su texto que nunca responde `400`, y la prosa del contrato lo repite |
| 2 | **El `DELETE` de la imagen se vuelca antes que el `UPDATE`** | `flush()` explícito (§7) y `CA-PM-362` comprueba que la fila desaparece |
| 3 | **Se escribe auditoría o `updated_at` sin portada** | `CA-PM-364` lo prueba con el paquete sin portada |

## 11. Estrategia de prueba

- **Unitaria** (`ProductPackageTest`): `quitarPortada` con portada devuelve el anterior y el diff; sin portada, vacío, sin diff y sin excepción.
- **Integración de API** (`PackageCoverIT`, la misma suite que la subida): los cinco criterios de `spec.md` §12. **La que define el requerimiento**: `CA-PM-362` — se quita siempre, y la dirección vieja responde `404`.
- **De efecto**: `CA-PM-365` — un paquete activo y ofrecible sigue en la oferta y en el hotlink con `coverImageUrl` nulo.
