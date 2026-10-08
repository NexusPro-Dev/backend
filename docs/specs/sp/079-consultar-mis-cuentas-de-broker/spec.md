# SPEC — `RF-SP-079` Consultar mis cuentas de broker

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-079` |
| Módulo | `SP` — Sistema Principal |
| Versión | 0.1.0 |
| Estado | **Aprobada** |
| Autor | Responsable técnico |
| Aprobada por | Responsable del proyecto |
| Fecha de aprobación | 08-10-2026 |

---

## 1. Objetivo

Que cada persona vea **sus propias cuentas de broker**: en qué broker las tiene, con qué identificador y nombre de usuario, y **en qué punto está cada una**.

## 2. Contexto

**Hasta hoy nadie podía ver las suyas.** Las tres lecturas de cuentas de broker miran hacia abajo o hacia todo: `RF-SP-055` las de una persona a cargo, `RF-SP-056` las del equipo directo y `RF-SP-057` todas, para administración. El titular quedó fuera **a propósito** el 10-09-2026 (`RF-SP-055` §4.2 y §14, pregunta 2): «la lectura se definió sobre el equipo; abrirla al titular es otra decisión y tiene otra vía». El 08-10-2026 el responsable del proyecto tomó esa decisión: «agreguemos para ver mis propias».

**La vía es una ruta propia y no `GET /users/me`**, aunque `RF-SP-055` apuntaba a `RF-SP-039`. Aquella frase es anterior a `RN-SEG-015` (21-09-2026): desde entonces cada operación lleva su permiso porque **el frontend decide con él qué vista ofrece**, y meter las cuentas dentro del perfil las haría visibles a quien porte `users:read-own-profile`, sin un permiso que diga «a esta persona se le enseña la pestaña de sus cuentas». Tampoco se relaja `RF-SP-055`: su `FA-005` —el titular que pide las suyas por su identificador recibe `404`— se conserva, porque aquella ruta se autoriza por la estructura y mezclar «soy yo» con «es de mi equipo» en la misma comprobación es justo lo que `RN-SP-046` acota.

### 2.1 Lo que este requerimiento decide

| Decisión | Qué se decidió |
|---|---|
| ¿Quién recibe el permiso? | **Todo rol por su tipo, los tres** —`FUNCIONARIO`, `VENDEDOR` y `CONSUMIDOR`—. Los clientes son quienes más cuentas declaran (`RN-SP-042`), y el vendedor también puede tener la suya |
| ¿Se pagina? | **No**, como `RF-SP-055`: una persona tiene unas pocas cuentas |
| ¿Qué devuelve? | **Lo mismo que `RF-SP-055`** para una persona: el frontend pinta las dos con el mismo componente |

## 3. Actores

| Actor | Papel |
|---|---|
| **Cualquier persona autenticada** con `broker-accounts:read-own` | Consulta sus propias cuentas |

## 4. Alcance

### 4.1 Incluye

- Devolver **todas las cuentas del actor**: broker, identificador de cuenta, nombre de usuario en el broker y estado.

### 4.2 No incluye

- **Las de otra persona**, ni siquiera las de quien depende de él: eso es `RF-SP-055` y `RF-SP-056`.
- **Escribir**: declarar, corregir o desvincular una cuenta sigue sin existir para el titular (`RF-SP-053` pendiente).
- **Paginar ni filtrar**: ver §2.1.

## 5. Reglas de negocio aplicables

| ID | Regla | Origen |
|---|---|---|
| `RN-SP-040` | El nombre de usuario en el broker llega DESPUÉS | `requirements/sp.md` §5.1 |
| `RN-SP-045` | Toda cuenta de broker declara en qué punto está | `requirements/sp.md` §5.1 |
| `RN-SP-046` | Las cuentas las ve el superior vigente, o quien traiga el permiso — **y su titular por esta vía** (enmendada el 08-10-2026) | `requirements/sp.md` §5.1 |
| `RN-SEG-015` | Autenticarse no autoriza nada | `security.md` §4.3 |

## 6. Datos

### 6.1 Entrada

Ninguna. **La persona sale del token**: no hay identificador en la ruta y no lo habrá.

### 6.2 Salida

La de `RF-SP-055` §6.2, con su orden —por nombre de broker y, dentro de él, por identificador de cuenta— y con `brokerUsername` **presente y nulo** mientras el broker no lo confirme.

## 7. Precondiciones y postcondiciones

**Precondiciones:** actor autenticado con `broker-accounts:read-own`.

**Postcondiciones:** ninguna. Es una lectura y **no audita**.

## 8. Flujo principal

1. La persona pide sus cuentas.
2. El sistema la identifica por su token.
3. El sistema devuelve sus cuentas, ordenadas.

## 9. Flujos alternativos

| ID | Caso | Comportamiento |
|---|---|---|
| `FA-001` | La persona no declaró ninguna cuenta | `200` con la colección vacía |
| `FA-002` | La persona pide las suyas por `GET /users/{id}/broker-accounts` sin `broker-accounts:read` | `404`, como hasta hoy (`RF-SP-055` `FA-005`): esta ruta no cambia aquella |

## 10. Excepciones

| Código | Caso | Respuesta |
|---|---|---|
| `AUTH-001` | Sin token, o inválido | `401` |
| `AUTH-002` | Autenticado sin `broker-accounts:read-own` | `403` |

## 11. Validaciones

Ninguna: no hay entrada.

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-SP-909` | Con el permiso, la persona obtiene **sus** cuentas —broker, identificador de cuenta, nombre de usuario en el broker y estado—, ordenadas por nombre de broker e identificador de cuenta, y **ninguna de otra persona**, tampoco las de quien depende de ella |
| `CA-SP-910` | `brokerUsername` llega **en nulo** mientras el broker no lo haya confirmado, y el campo **está presente** |
| `CA-SP-911` | Quien no declaró ninguna cuenta recibe `200` con la colección vacía |
| `CA-SP-912` | Sin `broker-accounts:read-own` responde `403`; sin token, `401` |
| `CA-SP-913` | La migración siembra `broker-accounts:read-own` a **todo rol por su tipo** —también a `CLIENTE`—; el catálogo pasa a **210**, `SUPERADMIN` porta 210 y `ADMIN` 208 |
| `CA-SP-914` | Pedir las propias por `GET /users/{id}/broker-accounts`, sin `broker-accounts:read`, sigue respondiendo `404` |

## 13. Casos límite

| Caso | Decisión |
|---|---|
| Dos cuentas en el mismo broker | Salen las dos (`RN-SP-038`) |
| Una cuenta en un broker **apagado** | Sale igual: apagar un broker no borra lo declarado (`RF-SP-052` §13) |
| Un vendedor con equipo | Ve solo las suyas; las del equipo son `RF-SP-056` |

## 14. Preguntas abiertas resueltas

| # | Pregunta | Resolución |
|---|---|---|
| 1 | ¿Ruta propia o dentro de `GET /users/me`? | **Ruta propia** (08-10-2026), por `RN-SEG-015`: el permiso dice a quién se le enseña la vista |
| 2 | ¿Se abre `RF-SP-055` al titular? | **No**: su autorización es por estructura y se queda como está |

## 15. Control de cambios

| Versión | Fecha | Cambio | Responsable |
|---|---|---|---|
| 0.1.0 | 08-10-2026 | Redacción inicial, a petición del responsable del proyecto: «agreguemos para ver mis propias». Ruta propia con permiso propio, sembrado a todo rol por su tipo; sin paginar; la respuesta de `RF-SP-055`. Criterios `CA-SP-909` a `CA-SP-914`. | Responsable del proyecto |
