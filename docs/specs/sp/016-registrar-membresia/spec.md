# SPEC — `RF-SP-016` Registrar membresía

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-016` |
| Módulo | `SP` — Sistema Principal |
| Estado | **Borrador** |
| Autor | Responsable técnico |
| Aprobada por | — |
| Fecha de aprobación | — |

---

## 1. Objetivo

Definir un nivel de acceso para los consumidores del sistema, situándolo en el orden correcto respecto de los ya existentes.

## 2. Contexto

La membresía determina a qué servicios y contenidos llega un cliente: hay cursos abiertos a todos y cursos reservados a niveles superiores. Es al consumidor lo que el rol es al funcionario, pero opera en un eje distinto: el rol dice **qué puede hacer**, la membresía **hasta dónde alcanza**.

Las membresías forman una **cadena ordenada, no un árbol**: cada una está sujeta a una de mayor nivel y solo la superior queda libre. Al crear una se indica cuál será su **hija**, de modo que la nueva membresía se **inserta en medio** y la cadena se reordena.

Esa mecánica de inserción es lo que distingue este requerimiento de un alta corriente: no añade un elemento al final, lo intercala.

## 3. Actores

| Actor | Rol en esta funcionalidad |
|---|---|
| Super Administrador | Registra membresías |
| Administrador | Registra membresías |

## 4. Alcance

### 4.1 Incluye

- Alta de una membresía con su código, nombre y descripción.
- Indicación de su membresía hija y reordenamiento de la cadena.

### 4.2 No incluye

- Editar o eliminar membresías: son inmutables una vez creadas (`RN-SP-008`).
- Asignar membresías a personas → módulo `USR`.
- Definir qué contenido exige qué nivel: corresponde a los módulos de academia y productos.

## 5. Reglas de negocio aplicables

| ID | Regla | Origen |
|---|---|---|
| `RN-SP-006` | Toda membresía está sujeta a una de mayor nivel, salvo la superior | `requirements/sp.md` §5.1 |
| `RN-SP-007` | Al crear se indica la membresía hija y se reordena la jerarquía | `requirements/sp.md` §5.1 |
| `RN-SP-008` | Las membresías no se editan ni eliminan | `requirements/sp.md` §5.1 |

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción | Restricción de negocio |
|---|---|---|---|
| Código | Sí | Identificador corto y estable | Único |
| Nombre | Sí | Nombre legible del nivel | Único |
| Descripción | No | Qué alcance concede | — |
| Membresía hija | No | Membresía que quedará por debajo de la nueva | Debe existir; su superior actual pasará a ser la nueva membresía |

Si no se indica membresía hija, la nueva se sitúa en el extremo inferior de la cadena.

### 6.2 Salida

| Dato | Descripción |
|---|---|
| Membresía | Membresía creada, con su nivel y su posición en la cadena |

## 7. Precondiciones y postcondiciones

**Precondiciones**

- El actor está autenticado y posee el permiso de creación de membresías.
- Si se indica membresía hija, esta existe.

**Postcondiciones**

- La membresía queda insertada en la posición correspondiente.
- La cadena sigue siendo lineal: cada membresía tiene como mucho una hija.
- Los niveles de las membresías afectadas quedan recalculados.
- Queda constancia en la auditoría de cambios.

## 8. Flujo principal

1. El actor solicita registrar una membresía y proporciona sus datos.
2. El sistema valida el formato y la obligatoriedad.
3. El sistema verifica que el código y el nombre no estén en uso.
4. El sistema verifica que la membresía hija indicada exista.
5. El sistema sitúa la nueva membresía por encima de la hija indicada y por debajo de la superior actual de esa hija.
6. El sistema recalcula los niveles de las membresías afectadas.
7. El sistema registra el evento en la auditoría de cambios.
8. El sistema informa la membresía creada.

## 9. Flujos alternativos

### FA-001 — Primera membresía del sistema

**Cuándo ocurre:** no existe ninguna membresía todavía.

1. La nueva se convierte en la membresía superior, sin membresía por encima.
2. No se indica hija, porque no hay ninguna.

### FA-002 — Inserción en el extremo inferior

**Cuándo ocurre:** no se indica membresía hija.

1. La nueva se sitúa por debajo de todas las existentes.
2. No hay reordenamiento: nada queda por debajo de ella.

## 10. Excepciones

### EX-001 — Código o nombre ya en uso

**Condición:** existe otra membresía con el mismo código o nombre.
**Respuesta del sistema:** rechaza el alta e informa cuál está duplicado.

### EX-002 — Membresía hija inexistente

**Condición:** la membresía hija indicada no existe.
**Respuesta del sistema:** rechaza el alta e informa que la membresía indicada no es válida.

### EX-003 — La hija indicada ya tiene otra superior distinta de la nueva

**Condición:** insertar la nueva membresía dejaría a dos membresías compartiendo la misma superior.
**Respuesta del sistema:** este caso no debe darse: la inserción reasigna la superior de la hija. Si la restricción del esquema lo rechazara, se trata como error del sistema y no como validación de negocio.

## 11. Validaciones

| ID | Validación | Mensaje esperado |
|---|---|---|
| `VAL-001` | Código obligatorio | El código de la membresía es obligatorio. |
| `VAL-002` | Nombre obligatorio | El nombre de la membresía es obligatorio. |
| `VAL-003` | Código único | Ya existe una membresía con ese código. |
| `VAL-004` | Nombre único | Ya existe una membresía con ese nombre. |
| `VAL-005` | Membresía hija existente | La membresía indicada no existe. |

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-SP-111` | El sistema registra la primera membresía como la superior de la cadena |
| `CA-SP-112` | El sistema inserta una membresía por encima de la hija indicada y reordena la cadena |
| `CA-SP-113` | El sistema sitúa la membresía en el extremo inferior cuando no se indica hija |
| `CA-SP-114` | Tras la inserción, cada membresía sigue teniendo como mucho una hija |
| `CA-SP-115` | El sistema recalcula los niveles de las membresías afectadas |
| `CA-SP-116` | El sistema rechaza el alta con código o nombre ya en uso |
| `CA-SP-117` | El sistema rechaza el alta con una membresía hija inexistente |
| `CA-SP-118` | El sistema registra el alta y el reordenamiento en la auditoría de cambios |
| `CA-SP-119` | El sistema rechaza el alta a un actor sin el permiso de creación de membresías |

## 13. Casos límite

- **Insertar por encima de la membresía superior:** convierte a la nueva en la superior. Debe admitirse.
- **Reordenamiento y auditoría:** la inserción modifica otras membresías. Ver pregunta abierta 2.
- **Inserción concurrente sobre la misma hija:** ambas pretenderían ser su superior. La restricción única debe resolver el empate sin dejar la cadena bifurcada.
- **Cadena con una sola membresía:** insertar por encima o por debajo son las dos únicas posibilidades.
- **Consumidores ya asignados:** insertar un nivel intermedio cambia el orden relativo. Ver pregunta abierta 3.

## 14. Preguntas abiertas

| # | Pregunta | Responsable | Estado |
|---|---|---|---|
| 1 | Al no poder editarse ni eliminarse, un error de escritura en el nombre queda para siempre. ¿Se admite alguna corrección, o es deliberado? | Responsable técnico | Abierta |
| 2 | El reordenamiento modifica membresías que el actor no tocó. ¿Se audita cada una por separado, o basta un evento sobre la creada? | Responsable técnico | Abierta |
| 3 | Insertar un nivel intermedio altera el alcance efectivo de quienes ya tenían membresía. ¿Se recalcula su acceso, o conservan el que tenían? | Responsable técnico | Abierta |
| 4 | ¿Toda membresía aplica a cualquier rol de tipo consumidor, o cada una se asocia a roles concretos? La guía dice «crear nuevas membresías para ciertos roles» | Responsable técnico | Abierta |

**Una spec con preguntas abiertas no puede aprobarse.** Esta sección debe quedar vacía antes de pasar la compuerta.
