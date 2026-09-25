# Requerimientos del Módulo — `AC` Academia

| Campo | Valor |
|---|---|
| Módulo | `AC` — Academia |
| Paquete | `modules/academy` |
| Prefijos de permiso | `course-categories:`, `courses:` |
| Versión | 0.13.0 |
| Estado | **Borrador** |
| Responsable | Bonilla Diaz William Steven |
| Fecha de creación | 17-09-2026 |
| Última actualización | 25-09-2026 |

!!! info "Qué va en este documento"

    El catálogo de requerimientos del módulo: qué debe hacer, bajo qué reglas y con qué permisos.

    El comportamiento detallado de cada requerimiento —flujos, validaciones, criterios de aceptación y casos límite— vive en su tripleta, en `docs/specs/ac/`. Aquí no se repite.

!!! warning "Documento en Borrador: tres cosas lo condicionan"

    1. **El código `AC`.** Un código, en cuanto aparece en un identificador, no se cambia jamás ([`modules.md` §2.1](../modules.md#21-regla-de-decision)). Se procede por decisión del responsable del proyecto, como con `PM`, `CM` y `MV`, y [`modules.md` §5.5](../modules.md#55-ac-academia) deja escrito el riesgo que se asume.
    2. **La frontera del alcance** (§1.3): este módulo **enseña**; no vende, no concede membresías, no aloja videos y no lleva el progreso del alumno. El motivo, en §1.4.
    3. **Una interfaz que `SP` no publicaba**: «¿esta persona porta este permiso?». La exige `RN-AC-006`, la pidió `RF-AC-008` (§3) y **`SP` la publica desde el 18-09-2026** (`PermissionHolderLookup`, `sp.md` 1.60.0 §8).
    4. **Otra que `SP` no publica todavía** (desde el 25-09-2026): «¿qué productos tiene VIGENTES esta persona?». La exige `RN-AC-020` para abrir un curso por su servicio, y la pedirá el aula (`RF-AC-033` a `RF-AC-035`) al construirse, no antes (§3).

---

## 1. Información del módulo

### 1.1 Descripción

`AC` es dueño de **lo que se enseña y de a quién se le abre**. Lo resuelve con cuatro entidades encajadas y cuatro relaciones:

| | Qué es | Cuelga de | Se pinta con | Estado |
|---|---|---|---|---|
| **Categoría** | Un cajón del catálogo: «Trading», «Mentalidad» | — | Portada, **color e icono** | Viva o retirada |
| **Curso** | Lo que un instructor enseña, con dificultad y video de introducción | Cero o más categorías | Portada | `ACTIVO` / `INACTIVO`, o retirado |
| **Módulo** | Una parte del curso, con su video de presentación | **Un** curso | Portada | `ACTIVO` / `INACTIVO`, o retirado |
| **Lección** | Lo que se estudia: **un video o un texto**, con su duración | **Un** módulo | — | `ACTIVO` / `INACTIVO`, o retirado |

| Relación | Qué dice | Cardinalidad |
|---|---|---|
| **Clasificación** | En qué categorías se encuentra el curso | `N:M`, y un curso sin categoría es legítimo |
| **Recomendación** | «Antes de este curso conviene ver aquel» | `N:M` entre cursos, **sin candado** (`RN-AC-011`) |
| **Visibilidad por membresía** | Qué membresías abren el curso | `N:M` con `memberships` de `SP`, **lista explícita** (`RN-AC-012`) |
| **Visibilidad por servicio** | Qué productos `BOT` de `PM` abren el curso a quien los tiene vigentes | `N:M` con `products` de `PM`, **lista explícita** (`RN-AC-020`, desde el 25-09-2026) |

**Las dos visibilidades conviven y se suman**: el curso se abre a quien tiene vigente **una de sus membresías o uno de sus servicios**. Es **el curso** quien declara quién lo puede ver, en las dos listas.

**Todo se ordena a mano** (`RN-AC-002`): categorías y cursos con un orden global, módulos dentro de su curso, lecciones dentro de su módulo. **Todo nace inactivo y se publica cuando tiene con qué** (`RN-AC-008`, `RN-AC-009`). **Nada desaparece** (`RN-AC-018`).

**Y el alumno lo ve así**: un catálogo con **todos los cursos que se ofrecen**, con lo que su membresía o sus servicios le abren marcado y lo que no, y **un curso se abre entero o no se abre** — salvo las lecciones que administración marcó **abiertas a todos**, que son la demostración con la que se le invita a subir de nivel (`RN-AC-013`, `RN-AC-014`).

### 1.2 Objetivo

Hoy el sistema sabe **quién es cada persona y qué nivel tiene** (`SP`) y **qué se le vende para subir de nivel** (`PM`), y **no sabe qué obtiene con ese nivel**. La membresía es una promesa de acceso a contenidos que no existen en ninguna tabla. Este módulo pone los contenidos en el sistema y ata cada curso a las membresías que lo abren, que es el paso sin el cual «subir de nivel» no le da al alumno nada que mirar.

### 1.3 Alcance

**Incluye**

- Registrar y mantener las **categorías**: nombre, descripción, portada, color, icono y orden; y retirarlas con motivo.
- Registrar y mantener los **cursos**: título, instructor, dificultad, descripción corta y larga, video de introducción, portada, orden y estado; **clasificarlos** en categorías, **recomendar** qué curso conviene ver antes, y declarar **qué membresías y qué servicios los abren**; y retirarlos con motivo.
- Registrar y mantener los **módulos** de un curso —portada, título, descripciones, video de presentación, orden y estado— y las **lecciones** de un módulo —tipo, título, descripción, contenido, duración, orden, estado y bandera de demostración—; y retirarlos con motivo.
- **Las portadas** de categorías, cursos y módulos: subirlas, reemplazarlas, quitarlas y **servirlas sin autenticación** por su identificador, con las mismas condiciones que las de `PM`.
- **Lo que ve el alumno**: el catálogo de cursos que se ofrecen, filtrable por categoría, con lo que su membresía le abre; el detalle de un curso con sus módulos y lecciones; y el contenido de una lección, cuando le corresponde o cuando es demostración.

**No incluye**

- **Vender un curso.** El curso **no tiene precio**: se abre **por la membresía o por un servicio** que la persona tiene, y lo que se vende es el servicio, en `PM`. Ver §1.4.
- **Conceder ni comprobar la vigencia de la membresía ni la de un servicio**, que son de `SP`: este módulo pregunta cuál es la membresía vigente (`CurrentMembershipLookup`) y qué productos tiene vigentes (§3), y no las calcula.
- **Alojar el video.** Se guardan **enlaces** a una plataforma que ya lo sirve, como el video del producto (`RN-PM-032`). Lo único que se guarda como archivo es la portada (`RN-AC-004`).
- **Interpretar el contenido.** El texto de una lección es Markdown que el backend guarda y devuelve tal cual; quien lo pinta es el frontend.
- **El progreso del alumno** —qué lección completó, qué curso terminó— y **los certificados**. Es la etapa siguiente, y su ausencia es la razón de que la recomendación sea una sugerencia y no un candado (`RN-AC-011`).
- **Las sesiones en vivo** (HU13, HU14). Son del mismo módulo y se escribirán como `RF-AC-NNN` en su tanda.
- **Comentarios, valoraciones y preguntas** sobre un curso o una lección. `PM` tiene reseñas sobre el producto; si un curso las necesita, se decidirá entonces si son las mismas.

### 1.4 La frontera, y por qué está donde está

**Un curso no tiene precio.** Lo que se paga es la membresía **o un servicio**, y el curso es lo que la una o el otro abren. Poner un precio en el curso —«o lo tienes por tu nivel, o lo compras suelto»— obligaría a duplicar en `AC` lo que `PM` ya sabe hacer con un producto: moneda, dos precios, alcance, hotlink.

**Desde el 25-09-2026 un curso se abre también por un servicio**, por decisión del responsable del proyecto (§5.2.8): el producto `BOT` de `PM` —el que «da derecho a una prestación», y que hasta el 28-08-2026 se llamó `SERVICIO`— **es la prestación**, y el curso es parte de ella. La frontera se mueve, pero **no como §1.4 anticipaba el 17-09-2026**. Aquella versión preveía un tipo de producto nuevo que **declarara el curso**, con `PM` consumiendo a `AC`; lo decidido es lo contrario: **es el curso quien declara qué servicios lo abren**, igual que declara qué membresías. `PM` no sabe nada de cursos, no nace ningún tipo de producto, y lo que se vende sigue siendo exactamente lo que se vendía.

**Y por eso la dependencia sigue yendo en un solo sentido**, ahora hacia dos módulos: `AC` → `SP` y `AC` → `PM`. `PM` no consume a `AC`, y `PM` → `SP` ya existía, de modo que no se cierra ningún ciclo. `modules.md` §5.2 anticipó desde el 26-08-2026 «Academia para saber qué nivel da acceso a qué», suponiendo que el nivel se leería del producto: el nivel sigue siendo la **membresía**, que es de `SP` y se lee de donde vive. Lo que se lee de `PM` es otra cosa: **qué es un servicio** —que existe, que es `BOT`, que no está retirado—, al armar la lista. **Quién tiene un servicio no se lee de `PM`**: lo que alguien posee vive en `user_products`, que es de `SP` (`RN-SP-056`).

!!! danger "El demo se decide en la lección, y eso tiene una consecuencia que hay que aceptar entera"

    Por decisión del responsable del proyecto, la demostración es **una bandera de la lección** —«abierta a todos»— y no un curso aparte ni un módulo aparte. Es lo que permite que un curso cerrado enseñe **su primera lección** a quien no tiene nivel para verlo, y que le interese subir.

    Lo que cuesta: **el catálogo del alumno enseña todos los cursos que se ofrecen, tenga o no la membresía o el servicio que los abre** (`RN-AC-013`). Si un alumno solo viera los cursos que su nivel abre, la lección abierta no serviría de nada — nadie ve la demostración de un curso que no ve. La lista de visibilidad **no esconde el curso: cierra su contenido**. Un alumno de `BRONCE` ve la portada, el título, el instructor y la lista de módulos y lecciones de un curso de `ORO`, con cada lección marcada como cerrada salvo las abiertas, y al pedir el contenido de una cerrada recibe un `403` que dice qué membresía la abre.

    Se acepta a conciencia. La alternativa —esconder el curso entero y publicar las lecciones abiertas en un escaparate aparte— exigía una tercera vista y dejaba la demostración sin contexto: una lección suelta sin el curso al que invita.

---

## 2. Submódulos

| Submódulo | Responsabilidad | Entidades principales |
|---|---|---|
| Categorías | Alta, consulta, corrección, retiro y portada | `course_categories` |
| Cursos | Alta, consulta, corrección, estado, retiro y portada; clasificación, recomendaciones y las dos visibilidades | `courses`, `course_category_items`, `course_recommendations`, `course_memberships`, `course_products` |
| Módulos | Las partes de un curso: alta, corrección, estado, retiro y portada | `course_modules` |
| Lecciones | Lo que se estudia: alta, **lectura**, corrección, estado y retiro | `lessons` |
| Aula | Lo que el alumno ve: catálogo, detalle de un curso y contenido de una lección | Las anteriores, y la membresía y los productos vigentes que `SP` publica |
| Portadas | Los bytes de las portadas y la ruta pública que los sirve | `academy_images` |

**Los módulos y las lecciones no tienen listado propio.** Se leen **dentro del curso** —el detalle de administración (`RF-AC-010`) y el del alumno (`RF-AC-034`) los traen ordenados— porque no existen fuera de él: un módulo no cambia de curso ni una lección de módulo (`RN-AC-019`), de modo que «todos los módulos» no es una pregunta que nadie haga. **La lección sí tiene detalle propio** (`RF-AC-036`, 18-09-2026), porque su contenido no viaja en ningún árbol y administración tiene que poder leerlo sin editarlo.

**El aula no es dueña de ninguna tabla**, y es submódulo por lo mismo que la resolución lo es en `CM`: opera sobre las tablas del módulo y las lee con otra pregunta —«qué se le ofrece a esta persona»— en lugar de «qué hay».

---

## 3. Dependencias

| Módulo | Tipo | Para qué |
|---|---|---|
| `SP` | Consume | **Usuarios** (`RN-AC-006`): que el instructor exista, no esté retirado y **porte `courses:teach`**; y su identidad —nombre de usuario y nombre completo— para publicarla con el curso |
| `SP` | Consume | **Membresías** (`RN-AC-012`): que la que se asocia a un curso exista; y su código, nombre y color, para publicarlos en la lista de visibilidad y en la lección cerrada |
| `SP` | Consume | **Membresía vigente** de quien pregunta (`RN-AC-013`): para decidir qué se le abre |
| `SP` | Consume | **Productos vigentes** de quien pregunta (`RN-AC-013`, `RN-AC-020`): para decidir qué servicios le abren un curso. **Interfaz que `SP` todavía no publica** |
| `PM` | Consume | **Productos** (`RN-AC-020`): que el que se asocia a un curso exista, sea `BOT` y no esté retirado; y su código y nombre, para publicarlos en la lista de visibilidad y en la lección cerrada |

La dependencia es **acíclica**: `AC` → `SP` y, desde el 25-09-2026, `AC` → `PM` (§1.4). `PM` no consume a `AC`.

!!! danger "Lo que `SP` publica y lo que todavía no"

    Se consumen por las interfaces que `SP` publica (**D-25**, [`architecture.md` §15.2](../architecture.md#152-como-consume-un-modulo-los-datos-de-otro-cierre-de-d-25)). Dos ya existen porque `PM` y `CM` las pidieron antes: `MembershipCatalog.find` y `CurrentMembershipLookup.currentMembershipOf`, y **se consumen tal cual**. `UserCatalog.find` también existe y publica identidad y marca de retiro.

    **La tercera no existía hasta el 18-09-2026**, y hoy es `PermissionHolderLookup.holds(userId, permissionCode)`: «¿esta persona porta este permiso?». Ningún consumidor la había necesitado, porque hasta hoy ningún módulo condicionaba un dato suyo a un permiso de `SP`. La publica `SP` como una interfaz de lectura más —un método, un booleano—, y **la ampliación pertenece a `RF-AC-008`**, que es quien la necesita, y no a un requerimiento nuevo de `SP`: es el mismo reparto que se decidió al cerrar D-25 y el que `RF-CM-001` · `T-04` aplicó con `UserCatalog`. **No devuelve la lista de permisos**: responde sí o no sobre uno, para no dar con qué reconstruir fuera de `SP` la autorización que es suya.

    **La cuarta tampoco existe todavía** (25-09-2026): «¿qué productos tiene vigentes esta persona?», sobre `user_products`, con la misma definición de vigencia que `CurrentMembershipLookup` —empezada, sin fin pasado y sin cerrar—. **La pide el aula** (`RF-AC-033` a `RF-AC-035`) al construirse, que es quien la necesita; `RF-AC-037` y `RF-AC-038` solo arman la lista y no preguntan por nadie. Responde **un conjunto de identificadores** para una persona, de modo que decidir `accessible` en un catálogo entero sigue costando una llamada y no una por curso.

    **Y de `PM` basta `ProductCatalog`** con una lectura más: la que dice **el tipo** del producto, que `ProductView` no lleva. La amplía `RF-AC-037`, que es quien la necesita, como `RF-CM-001` amplió la suya — **una interfaz por lectura**, sin tocar la vista que ya consume `CM`.

---

## 4. Actores

| Actor | Rol en el módulo | Permisos típicos |
|---|---|---|
| Administrador | Construye y gobierna el catálogo entero: categorías, cursos, módulos, lecciones, relaciones y portadas | `course-categories:*`, `courses:read`, `courses:create`, `courses:update`, `courses:delete` |
| Instructor | **Es asignado** a un curso. Hoy no administra nada: el permiso lo habilita para figurar, no para editar | `courses:teach` |
| Alumno | Recorre el catálogo que se le ofrece y estudia lo que su membresía o sus servicios le abren, más las demostraciones | `courses:learn` |

**El alumno no lleva `courses:read`, y es a propósito.** Ese permiso abre el catálogo completo, con lo inactivo y lo retirado dentro, y con lo que no se le abre. `RF-AC-033` responde con lo que se ofrece y marca lo suyo. Es la misma decisión que `PM` tomó con `products:sale` y `RF-SP-039` con el perfil propio: **un permiso de vista**, que se concede a los roles de tipo `CONSUMIDOR` por `RF-SP-006` y no por siembra — el rol `CLIENTE` nace sin permisos (`V30`) y este módulo no le abre una excepción.

**`courses:teach` no gobierna ninguna ruta hoy**, y no es un descuido. Existe para que **el instructor sea alguien que administración eligió para eso** y no cualquier persona con cuenta: `RN-AC-006` lo exige al asignar. Quién lo porta lo decide quien administra roles, y el destinatario natural es un rol nuevo de tipo `FUNCIONARIO` —«Instructor»— que este módulo **no siembra**: sembrar roles es de `SP` (`V7`) y qué roles existen es decisión del negocio. El día que el instructor edite sus propios cursos, este permiso ya estará donde tiene que estar, y lo que habrá que escribir es la regla de propiedad —como `RN-PM-027` con la reseña— y no el permiso.

**Los diez permisos se siembran asociados a `SUPERADMIN` y a `ADMIN`**, sin reserva, por la obligación de [`security.md` §4.4](../security.md#44-catalogo-de-permisos). `courses:teach` y `courses:learn` **también**: un administrador que los porte puede ser instructor y puede recorrer el aula, y ninguna de las dos cosas es una operación que deba quedar exclusiva de la raíz. **A `CLIENTE` no**, por lo mismo de siempre.

---

## 5. Reglas de negocio

### 5.1 Catálogo

| ID | Regla | Cuándo aplica | Qué debe ocurrir | Prioridad |
|---|---|---|---|---|
| `RN-AC-001` | **El nombre es único entre los vivos, en su ámbito** | Al registrar y al corregir | El nombre de una categoría y el título de un curso no se repiten **en todo el catálogo**; el título de un módulo no se repite **dentro de su curso**; el de una lección, **dentro de su módulo**. En los cuatro casos **entre los no retirados** y **sin distinguir mayúsculas ni acentos** (`RN-PM-005` por extensión). Dos módulos «Introducción» en cursos distintos son legítimos; dos en el mismo curso, no | Alta |
| `RN-AC-002` | **El orden es una posición, no una identidad** | Al registrar, al corregir y en toda lectura | Categorías, cursos, módulos y lecciones declaran un **entero mayor o igual que cero**, obligatorio, que decide en qué lugar se enseñan: categorías y cursos con **un orden global** —el curso vale lo mismo en todas sus categorías—, módulos **dentro de su curso**, lecciones **dentro de su módulo**. **No es único**: dos con el mismo número son legítimos y se desempatan por identificador, que es cronológico. Corregirlo no reordena a los demás: quien quiera «mover al tercer puesto» corrige los números que haga falta | Media |
| `RN-AC-003` | **La categoría declara color e icono, y el color no es único** | Al registrar y al corregir una categoría | El color tiene **la forma de `RN-SP-024`** —seis dígitos hexadecimales sin `#`, normalizados a mayúsculas— y es **obligatorio**; **no es único**, porque las categorías no son una cadena que haya que distinguir por el color y dos cajones del mismo tono no confunden a nadie. El icono es **un identificador y no una imagen** (`RN-PM-016`), obligatorio: la categoría se pinta con color e icono **siempre**, y con la portada cuando la tiene. Los dos se corrigen (`RF-AC-004`) | Media |
| `RN-AC-004` | **La portada es un archivo, en tabla propia, y opcional sin condición** | Al subir, al quitar y siempre que se consulte una categoría, un curso o un módulo, con token o sin él | Categoría, curso y módulo pueden llevar **una** imagen de portada **con las mismas condiciones que la del producto** (`RN-PM-033`): `JPEG`, `PNG` o `WebP` de hasta 5 MB, el tipo por los bytes, sin tratar, **cada subida estrena identificador** y la reemplazada **se borra**. Los bytes viven en **`academy_images`**, tabla de este módulo, y se sirven **sin token** por **`/api/v1/academy-images/{id}`** (`RF-AC-032`). **Ninguna de las tres entidades declara qué se pinta sin portada**: el frontend pone lo suyo —la categoría tiene color e icono, el curso y el módulo el icono por omisión del sistema—, de modo que quitar la portada **nunca se rechaza** (`RN-PM-045` por extensión). Toda lectura devuelve `coverImageUrl`, **presente y nula** cuando no hay (§5.2.3) | Alta |
| `RN-AC-005` | **Un video es un enlace, y el sistema no lo sigue** | Al registrar, al corregir y en toda lectura de curso, módulo o lección | El video de introducción del curso, el de presentación del módulo y el contenido de una lección `VIDEO` son **la dirección de un video**: URL absoluta `http` o `https`, sin espacios, de hasta 500 caracteres, de cualquier dominio (`RN-PM-032` por extensión). El sistema comprueba su forma y **no lo sigue**: no comprueba que exista, no lo descarga, no lo incrusta. En el curso y en el módulo es **opcional, se corrige y se vacía**; en la lección es su contenido y `RN-AC-016` dice cuándo hace falta | Media |
| `RN-AC-006` | **El instructor porta `courses:teach`** | Al registrar un curso y al reasignar su instructor | El instructor es **una persona de `SP`** que existe, **no está retirada** y **porta el permiso `courses:teach`** por alguno de sus roles, comprobado **al asignar** contra la interfaz que `SP` publica (§3). **Que lo pierda después no toca el curso**: se le revocó el permiso, o se retiró la persona, y el curso sigue diciendo quién lo enseñó hasta que administración lo reasigne (`RF-AC-011`). Es una comprobación de **quién puede figurar**, no de quién puede editar: el instructor hoy no edita nada (§4) | Alta |
| `RN-AC-007` | **La dificultad es una de tres** | Al registrar y al corregir un curso | `PRINCIPIANTE`, `INTERMEDIO` o `AVANZADO`, obligatoria. No es un orden ni una cadena: no hay «superior a», y un curso no exige haber visto los de la dificultad anterior. Es una etiqueta para que el alumno elija | Baja |
| `RN-AC-008` | **Curso, módulo y lección nacen inactivos** | Al registrar | Los tres se registran `INACTIVO` (`RN-PM-012` por extensión): existen, no se ofrecen, y se publican con su cambio de estado. Es lo que permite armar un curso entero —módulos, lecciones, portadas— antes de que ningún alumno lo vea a medias. **La categoría no tiene estado**: está viva o retirada, y una categoría sin cursos ofrecidos simplemente sale vacía | Alta |
| `RN-AC-009` | **No se publica lo que está vacío** | Al activar | **Una lección** no se activa sin contenido (`RN-AC-016`). **Un módulo** no se activa sin **al menos una lección `ACTIVA`** no retirada. **Un curso** no se activa sin **descripción corta y larga** y sin **al menos un módulo `ACTIVO`** no retirado. Las tres son la misma regla de `RN-PM-014` —no se ofrece lo que no se explica— mirada desde tres entidades, y las tres se comprueban **al activar y solo al activar**: desactivar nunca se rechaza, y **lo que después se vacía no desactiva nada, pero deja de ofrecerse** (`RN-AC-015`, desde el 18-09-2026) —retirar la última lección activa de un módulo activo deja el módulo `ACTIVO` y **no ofrecible**, y es `RN-AC-015` quien lo enseña—. El estado es lo que alguien decidió (`requirements/pm.md` §5.2.10) | Alta |
| `RN-AC-010` | **La clasificación es libre y no se repite** | Al clasificar y al desclasificar | Un curso pertenece a **cero o más** categorías; la pareja curso–categoría **no se repite**; no se clasifica en una categoría **retirada** ni un curso **retirado**. Desclasificar **borra la fila**: la clasificación no es una entidad sino el valor de una relación, y no cabe en el Art. V.13 como baja lógica — cabe como **`ASSOCIATION`**: dar la pareja se audita como `CREATE` de la fila y quitarla como eliminación de asociación sin motivo, con el curso como entidad, que es el precedente de `RF-PM-023` y `RF-PM-025` (precisado el 18-09-2026). **Un curso sin categoría se ofrece igual** (`RN-AC-015`): la categoría es un filtro del catálogo, no una condición | Media |
| `RN-AC-011` | **La recomendación es una sugerencia, no un candado** | Al recomendar y en el aula | «Antes de este curso conviene ver aquel» **se enseña y no impide nada**: el alumno entra al curso con o sin haber visto el recomendado, y el sistema **no sabría** si lo vio, porque no lleva progreso (§1.3). Un curso **no se recomienda a sí mismo**; la pareja **no se repite**; no se recomienda un curso **retirado**. **No se exige que sea acíclico**: `A` recomienda `B` y `B` recomienda `A` es una sugerencia tonta, no un estado inválido, y comprobar ciclos costaría un recorrido por cada alta para prohibir algo que no rompe nada. En el aula **solo se enseñan las recomendaciones cuyo curso se ofrece** (`RN-AC-015`); las demás se conservan y no se ven. Retirar la recomendación **borra la fila**, como la clasificación | Media |
| `RN-AC-012` | **La visibilidad es una lista explícita, y sin lista nadie abre el curso** | Al dar y quitar visibilidad, y en el aula | Un curso declara **qué membresías lo abren**: una lista de membresías de `SP`, cada una existente (`MembershipCatalog`), **sin repetir**. **Es una lista y no un nivel mínimo**, por decisión del responsable del proyecto: un curso de `ORO` **no** lo abre `PLATINO` salvo que `PLATINO` esté en su lista, y quien quiera «este nivel y todos los superiores» los añade uno a uno. **Un curso sin ninguna membresía y sin ningún servicio no se ofrece** (`RN-AC-015`, `RN-AC-020`) — ni entero ni sus lecciones abiertas—: es un curso que existe y no se enseña, como un producto de alcance `NINGUNO`. Quitar una membresía **borra la fila** | Alta |
| `RN-AC-013` | **El curso se ve con sesión, y se abre con membresía o con servicio** | En el aula | **Todo curso que se ofrece aparece en el catálogo de todo alumno** con `courses:learn`, tenga o no una membresía o un servicio que lo abra: portada, título, instructor, dificultad, descripciones, video de introducción, categorías, recomendaciones, y **la lista de módulos y lecciones** con su título, tipo y duración. **El contenido de una lección se abre a quien tiene VIGENTE una de las membresías del curso** (`CurrentMembershipLookup`), **a quien tiene VIGENTE uno de sus servicios** (`RN-AC-020`) **o a cualquiera si la lección está abierta** (`RN-AC-014`); a los demás se les niega con `403` diciendo **qué membresías y qué servicios lo abren**, que es la invitación. **Enmendada el 25-09-2026** (§5.2.8): hasta entonces solo abría la membresía. Cada lectura del aula marca `accessible` en el curso y en cada lección, para que el frontend pinte el candado sin volver a preguntar (§1.4) | Alta |
| `RN-AC-014` | **La lección abierta es la demostración** | Al registrar y corregir una lección, y en el aula | Una lección declara `open`, **falso por omisión**: abierta a todos. Una lección abierta **se abre a cualquier alumno con sesión** aunque su membresía no abra el curso. **No exime de nada más**: tiene que estar `ACTIVA`, en un módulo `ACTIVO`, en un curso **que se ofrezca** (`RN-AC-015`) — la demostración de un curso que no se ofrece no se ve. Se corrige en cualquier momento, en los dos sentidos | Alta |
| `RN-AC-015` | **La ofrecibilidad se calcula, y no se guarda** | En toda lectura de un curso, de administración o del aula | Un curso **se ofrece** cuando **todo** esto es cierto: no está retirado, está `ACTIVO`, **tiene las dos descripciones**, tiene **al menos una membresía o un servicio que lo abra** (`RN-AC-012`, `RN-AC-020`; «o un servicio» desde el 25-09-2026) y tiene **al menos un módulo ofrecible**. **Se calcula en cada lectura y nunca se guarda**: administración lo ve como `offerable` con `offerableReason` —el primer motivo que falla, **en ese orden de cinco**— como el paquete de `PM` (`requirements/pm.md` §5.2.10, «el paquete se ofrece entero o no se ofrece»); el aula **solo enseña lo ofrecido**. Un **módulo** se ofrece dentro de un curso ofrecido si está `ACTIVO`, no retirado y con al menos una lección ofrecible; una **lección**, si está `ACTIVA`, no retirada **y con contenido**. **Lo que no se ofrece no aparece en el aula**, ni como módulo vacío ni como lección inactiva ni como lección sin contenido. **Enmendada el 18-09-2026** (§5.2.7): hasta entonces no miraba las descripciones ni el contenido, y lo que se vaciaba después de activarse se enseñaba vacío | Alta |
| `RN-AC-016` | **El tipo manda sobre el contenido** | Al registrar, al corregir y al activar una lección | Una lección es `VIDEO` o `TEXTO`, obligatorio. Su contenido es **una URL** (`RN-AC-005`) si es `VIDEO` y **un texto Markdown** si es `TEXTO`, **que el backend guarda y devuelve sin interpretar** — ni lo valida como Markdown, ni lo convierte, ni lo sanea: quien lo pinta es el frontend, y eso queda escrito en §5.2.4. **El contenido es opcional al registrar** —la lección se prepara— y **obligatorio para activar** (`RN-AC-009`). **El tipo se corrige**, y el contenido de la misma petición —o el que ya hay— se valida contra **el tipo resultante**: pasar a `VIDEO` con un texto guardado exige traer la URL en esa petición | Alta |
| `RN-AC-017` | **La duración se mide en minutos enteros** | Al registrar y al corregir una lección | Un entero **mayor que cero**, **obligatorio en los dos tipos**: en `VIDEO` es lo que dura; en `TEXTO`, el tiempo estimado de lectura que administración declara. Sirve para que el alumno sepa cuánto le espera y para sumar la duración del módulo y del curso **en cada lectura**, sin guardarla | Media |
| `RN-AC-018` | **Nada desaparece, y retirar arrastra hacia abajo** | Al eliminar | Categoría, curso, módulo y lección se retiran con **baja lógica y motivo** (Art. V.13), y **una fila de auditoría de eliminación por entidad retirada**. **Retirar un curso retira sus módulos y lecciones** con el mismo motivo y en la misma transacción; **retirar un módulo retira sus lecciones**. **Retirar una categoría no arrastra nada**: sus cursos siguen vivos, y la clasificación deja de contar (`RN-AC-010`). **Lo retirado no se corrige, no cambia de estado y no se reactiva**; sus clasificaciones, recomendaciones y visibilidades **se conservan** y dejan de verse. Y **un curso retirado deja de recomendarse** donde figure (`RN-AC-011`) | Alta |
| `RN-AC-019` | **El módulo y la lección no cambian de padre** | Al corregir | Un módulo nace en un curso y una lección en un módulo, y **ninguno se mueve**: `course_id` y `module_id` no se corrigen. Mover una lección de módulo es retirarla y registrarla en el otro, y la razón es que el orden (`RN-AC-002`), la unicidad del título (`RN-AC-001`) y la ofrecibilidad del padre (`RN-AC-015`) están definidos **dentro del padre**, y moverla los invalidaría los tres a la vez | Media |
| `RN-AC-020` | **Un servicio también abre el curso, y es el curso quien lo declara** | Al dar y quitar la visibilidad por servicio, y en el aula | Un curso declara, **además de sus membresías** (`RN-AC-012`), **qué productos lo abren**: una lista explícita de productos de `PM` **de tipo `BOT`** —el servicio—, cada uno existente y **no retirado al añadirlo** (`ProductCatalog`), **sin repetir**. **Un upgrade no abre cursos**: su efecto es el nivel, y el nivel ya tiene su lista; un paquete tampoco, porque lo que se posee al comprarlo son sus productos, no él. **Un servicio `INACTIVO` se añade igual**: la lista se arma antes de poner el servicio a la venta, como el curso se arma antes de activarse. **Lo tiene quien lo tiene VIGENTE** en `SP` —una fila de `user_products` con ese producto, empezada, sin fin pasado y sin cerrar—; al vencer, el curso se le cierra y lo sigue viendo en el catálogo (`RN-AC-013`). **Retirar el servicio en `PM` no toca la lista**: quien lo compró lo tiene hasta que venza, y el curso le sigue abierto — la lista dice qué abre el curso, no qué se vende hoy. Las dos listas **se suman**: basta una membresía **o** un servicio. Quitar un servicio **borra la fila**, como la membresía | Alta |

### 5.2 Decisiones que definen el módulo — 17-09-2026

Todas por decisión del responsable del proyecto, el día que se incorporó el módulo. Se preguntaron antes de escribir una línea, y la respuesta y lo que se descartó quedan aquí.

#### 5.2.1 La lección cuelga del módulo, y el módulo tiene video

| Pregunta | Decisión | Descartado |
|---|---|---|
| **¿De qué cuelga la lección?** | **Del módulo**, y el módulo del curso (`RN-AC-019`) | *Del curso, con el módulo como agrupación paralela* — era la forma en que llegó la lista de campos, y dejaba al módulo sin nada que agrupar |
| **¿Qué es el «contenido url» del módulo?** | **El video de presentación del módulo** (`RN-AC-005`), opcional | *Quitarlo* — el módulo sería solo un título con portada. *Material de apoyo* — un enlace a un PDF o una carpeta, que nadie pidió |

#### 5.2.2 La visibilidad es una lista, y la demostración es de la lección

| Pregunta | Decisión | Descartado |
|---|---|---|
| **¿Lista explícita o nivel mínimo?** | **Lista explícita** (`RN-AC-012`) | *Un nivel mínimo en el curso, que abre ese nivel y los superiores* — era la opción que menos filas costaba y la que aprovechaba que la cadena es lineal (`RN-SP-006`); se descartó porque **hay cursos que son de un nivel y no de los de arriba**: un curso de bienvenida a `BRONCE` no tiene por qué verlo `PLATINO`, y con un mínimo no habría forma de decirlo |
| **¿Qué ve quien no tiene una membresía del curso?** | **El curso entero menos el contenido de sus lecciones cerradas**; las abiertas, sí (`RN-AC-013`, `RN-AC-014`) | *Nada* — la demostración no serviría a quien está pensado que sirva (§1.4) |
| **¿Y un curso sin ninguna membresía?** | **No se ofrece** (`RN-AC-015`), ni sus lecciones abiertas — **salvo que lo abra un servicio**, desde el 25-09-2026 (§5.2.8) | *Abierto a todos con sesión* — «sin filas» sería la configuración más permisiva, y es la que se obtiene **por olvido**: un curso recién armado se publicaría a todos hasta que alguien recordara cerrarlo |
| **¿Quién ve una lección abierta?** | **Cualquier usuario con sesión** y `courses:learn` | *Cualquiera, sin token, como el hotlink* — el aula entera exige sesión y nada de Academia es público salvo las portadas; un escaparate sin sesión es una decisión de mercadeo que se tomará con `PM` si se toma |

#### 5.2.3 La portada vive en una tabla propia

`PM` guarda las portadas en `product_images` (`requirements/pm.md` §5.2.9), y **la tentación era señalar esa tabla desde `courses`**: mismo detector, mismas restricciones, misma ruta pública. **Se descartó porque `product_images` es de `PM`** y [`modules.md` §7](../modules.md#7-reglas-de-dependencia) prohíbe que un módulo escriba la tabla de otro: subir la portada de un curso sería un `INSERT` de `AC` en una tabla de `PM`, y borrar la reemplazada, un `DELETE`. **`academy_images` es la misma tabla, columna a columna, en el módulo que la escribe** (§8.8), y la sirve una ruta propia, `/api/v1/academy-images/{id}`, por lo mismo.

**Lo que sí se comparte es el detector de firma**: los primeros bytes de un `JPEG`, un `PNG` y un `WebP` no dependen de quién los guarde. El objeto que hoy vive en `PM` **se mueve a `shared/`** en el primer requerimiento de portada de este módulo (`RF-AC-006`), y `PM` lo importa de allí — es una refactorización sin cambio de comportamiento, con la suite de `PM` en verde como definición de terminado. Con él se mueven el tope de 5 MB y la lista de tipos: **subir el tope o admitir un formato será una constante y dos restricciones**, una por tabla.

Se descartó **promover el almacén de imágenes a un componente transversal** —una tabla `images` de `shared`, como la auditoría— porque es una decisión de arquitectura con ADR, obliga a migrar `product_images` y a reescribir `RF-PM-014` a `RF-PM-016` y `RF-PM-028`, `RF-PM-029`, y lo que compra es no tener dos tablas iguales. **Queda anotada la condición para reabrirlo**: el tercer módulo que necesite guardar una imagen.

**Y ninguna de las tres entidades tiene la regla del icono** (`RN-PM-034`). El upgrade la necesita porque tiene que distinguirse de otro upgrade y no tiene nada más; la categoría **siempre** tiene color e icono, y el curso y el módulo se pintan con el icono por omisión del sistema, como el bot y el paquete. Quitar la portada **nunca se rechaza**.

#### 5.2.4 El texto de una lección es Markdown, y el backend no lo mira

**El contenido de una lección `TEXTO` se guarda como llega y se devuelve como se guardó.** No se valida que sea Markdown —no hay Markdown inválido—, no se convierte a HTML y **no se sanea**. La razón de no sanear es que **no hay qué sanear**: el backend no lo sirve como HTML en ningún sitio, y lo único que hace un `text` es viajar en un JSON. Quien lo convierte es el frontend, y **es el frontend quien tiene que hacerlo con un conversor que no ejecute lo que encuentre** —que escape el HTML embebido o lo prohíba—, porque quien escribe la lección es administración, y una lección con un `<script>` dentro no es un ataque de administración sino un descuido que el frontend tiene que poder absorber. Queda escrito aquí porque **es una obligación que este módulo impone al frontend** y no puede comprobar.

Se descartó **HTML del editor** porque obliga a sanear en el backend con una biblioteca y una lista blanca que hay que mantener, y **texto plano** porque una lección sin títulos ni listas ni énfasis no es una lección.

**Sin límite corto.** La columna es `text` y el único tope es el tamaño máximo de una petición HTTP que el servidor admite. Una lección de veinte páginas es legítima.

#### 5.2.5 El instructor porta un permiso y no un rol

| Decisión | Descartado |
|---|---|
| **Quien porte `courses:teach`** (`RN-AC-006`) | *Cualquier usuario activo* — el instructor es una figura pública del catálogo, y que administración pueda poner de instructor a un cliente por error es exactamente lo que un permiso evita. *Un rol «Instructor» que `SP` siembre y `AC` exija por nombre* — `SP` no sabe de academia, y un rol con nombre fijo es una constante en dos módulos; con un permiso, **qué roles lo portan** lo decide quien administra roles, como todo lo demás (`RF-SP-006`). *Solo administradores* — el instructor no administra, enseña |

**Lo que cuesta:** `SP` tiene que publicar una interfaz más (§3), y es la primera que responde sobre **un permiso** y no sobre un dato. Se acepta porque la alternativa era que `AC` leyera `user_roles` y `role_permissions`, que es lo que §7 prohíbe.

#### 5.2.6 Lo que no tiene código, y lo que no tiene estado

**El curso no lleva código estable** como el producto (`RN-PM-013`). El código del producto existe porque se teclea en una venta y se imprime en un comprobante; un curso no se vende ni se imprime, y su identificador basta. El día que un curso se venda suelto, **el código lo tendrá el producto que lo venda** (§1.4).

**La categoría no tiene estado** (`RN-AC-008`): está viva o retirada. Un cajón vacío no molesta y un cajón «inactivo» con cursos activos dentro plantearía una pregunta —¿se ofrecen esos cursos?— que nadie quiere responder.

#### 5.2.7 Lo que se vacía deja de ofrecerse — 18-09-2026

Las tripletas del bloque 2 y del bloque 3 dejaron escritos dos huecos de la regla del 17-09-2026 (`RF-AC-011` §14.1, `RF-AC-029` §14): `RN-AC-009` exige descripciones y contenido **al activar**, y `RN-AC-015` no los miraba después, de modo que **un curso activo al que se le vaciaba una descripción seguía ofreciéndose sin ella, y una lección activa vaciada se enseñaba vacía**. Por decisión del responsable del proyecto, **«sin descripción» y «sin contenido» son motivos de `RN-AC-015`** —el tercero del curso y la condición de la lección— y **no desactivaciones**: el estado sigue siendo lo que alguien decidió, y lo que lo detiene es un hecho de la fila que se enseña en lugar de copiarse (`requirements/pm.md` §5.2.10). Se descartó *desactivar al vaciar* por lo mismo que `RN-PM-040`: cambiaría un estado decidido por un hecho, y reponer la descripción no lo reactivaría solo.

**Y administración lee la lección sin editarla.** El segundo hueco de `RF-AC-029` §14 —el detalle del curso no trae el contenido, y la corrección exige un campo— se cierra con **`RF-AC-036`**, un `GET` de lección con `courses:read` y no con el contenido dentro del árbol: la pantalla de edición abre una lección a la vez.

#### 5.2.8 El curso también se abre por un servicio — 25-09-2026

Por decisión del responsable del proyecto —«que sea el curso el que se asigne quién lo puede ver, pero ya no a membresía sino al producto, referenciado como servicio que se le proporciona»—. Se preguntaron cuatro cosas antes de escribir:

| Pregunta | Decisión | Descartado |
|---|---|---|
| **¿Qué productos abren un curso?** | **Solo los `BOT`** (`RN-AC-020`): es el tipo que «da derecho a una prestación» | *Cualquier producto* — un upgrade abre un nivel, y el nivel ya abre cursos por su lista; además, subir de `ORO` a `PLATINO` **cierra** la fila del upgrade a `ORO` y le quitaría a la persona los cursos que ese upgrade abría. *Un tipo de producto nuevo* — obligaba a tocar `PM` (tipo, reglas, venta) para algo que el `BOT` ya significa |
| **¿Y las membresías?** | **Conviven**: el curso declara las dos listas y se abre con cualquiera | *Reemplazarlas* — el curso por nivel es la forma en que se pensó el módulo, y hay cursos que un nivel entero debe ver sin comprar nada |
| **¿Cuándo tiene alguien el servicio?** | **Mientras esté vigente** en `user_products` | *Si lo tuvo alguna vez* — un servicio con vigencia (`RN-PM-015`) dejaría de ser una suscripción |
| **¿Cómo se llama en la API?** | **`products`** —`POST /api/v1/courses/{courseId}/products` y `products` en el detalle— | *`services`* — «servicio» es cómo se piensa, pero el recurso es un producto del catálogo y se llama como en `PM` |

**Lo que no cambia**: un curso **no se vende**; se vende el servicio, en `PM`, con su moneda, su precio y su vigencia, y el curso es parte de lo que da. **Y lo que cuesta**: `AC` pasa a depender de `PM` (§1.4, §3) y `SP` tiene que publicar una interfaz más.

#### 5.2.9 El curso nace en sus categorías, y la portada va aparte — 25-09-2026

Por decisión del responsable del proyecto: **el alta del curso admite la lista de sus categorías** (`categoryIds`), para asociarlas de una vez, y **la portada se sube por su propia operación** justo después (`RF-AC-014`), con el archivo.

| Pregunta | Decisión | Descartado |
|---|---|---|
| **¿Qué relaciones admite el alta?** | **Las categorías, y desde una segunda decisión del mismo día, los servicios y las membresías**, cada lista todo o nada y con su propio `422` que nombra todo lo que falla (`RF-AC-008` `EX-004` a `EX-006`). Es lo que la pantalla de alta pide: el curso nace con quién lo puede ver | *Todas, recomendaciones incluidas* — una recomendación apunta a otro curso y tiene reglas contra sí mismo; va por su operación. *Solo las categorías* —la primera respuesta del día—: obligaba al frontend a tres llamadas más para dejar el curso listo. *Crear con las que sirvan* — el rollback parcial que la spec temía el 18-09-2026 |
| **¿Y la portada en la misma alta?** | **No: `PUT /api/v1/courses/{id}/cover`** con el archivo, después | *El alta en `multipart`* — una llamada menos, a cambio de que el alta del curso sea la única del sistema que no es JSON; producto y paquete ya separan las dos cosas |

Con esto, **el bloque 5 adelanta tres requerimientos** —`RF-AC-006`, `RF-AC-032` y `RF-AC-014`— porque la portada del curso los necesita: la tabla `academy_images` y el detector compartido nacen con la de la categoría, y sin la ruta pública `coverImageUrl` señalaría a nada.

### 5.3 Reglas de otros documentos que este módulo aplica

| Regla | Dónde vive | Cómo la aplica este módulo |
|---|---|---|
| `RN-SP-006`, `RN-SP-007` | [`requirements/sp.md` §5.1](sp.md#51-reglas-propias-del-modulo) | La cadena de membresías es lineal y ordenada. `RN-AC-012` **no se apoya en ese orden** a propósito: la visibilidad es una lista, y añadir «los superiores» es decisión de quien administra |
| `RN-SP-024` | [`requirements/sp.md` §5.1](sp.md#51-reglas-propias-del-modulo) | El formato del color de la categoría (`RN-AC-003`), sin la unicidad |
| `RN-SP-056` | [`requirements/sp.md` §5.1](sp.md#51-reglas-propias-del-modulo) | Lo que cada persona tiene vive en `user_products`, con su periodo: es de donde sale **quién tiene un servicio vigente** (`RN-AC-020`) |
| `RN-PM-001`, `RN-PM-015` | [`requirements/pm.md` §5.1](pm.md#51-reglas-propias-del-modulo) | El tipo del producto decide qué da, y el `BOT` es el que da una prestación: solo él abre un curso (`RN-AC-020`). Su vigencia es la de lo vendido |
| `RN-PM-005`, `RN-PM-012`, `RN-PM-014`, `RN-PM-016`, `RN-PM-032`, `RN-PM-033`, `RN-PM-045` | [`requirements/pm.md` §5.1](pm.md#51-reglas-propias-del-modulo) | Por extensión, donde cada regla de §5.1 lo dice: nombre único entre vivos, nacer inactivo, no publicar lo vacío, icono como identificador, video como enlace, portada como archivo y portada sin condición |
| `RN-SEG-001` a `RN-SEG-004` | [`security.md` §4](../security.md) | Toda operación exige su permiso (§7); las rutas de portada pública son las únicas sin token, como `RF-PM-016` |
| Art. V.7, V.13 | [`constitution.md`](../constitution.md) | Auditoría de cambios en toda escritura; baja lógica con motivo y registro en toda eliminación (`RN-AC-018`), **incluidas las arrastradas** |

---

## 6. Requerimientos funcionales

### 6.1 Resumen

| ID | Nombre | Submódulo | Prioridad | Permiso | Estado |
|---|---|---|---|---|---|
| `RF-AC-001` | Registrar categoría | Categorías | Alta | `course-categories:create` | **En desarrollo** (17-09-2026) |
| `RF-AC-002` | Consultar categorías | Categorías | Alta | `course-categories:read` | **En desarrollo** (17-09-2026) |
| `RF-AC-003` | Consultar el detalle de una categoría | Categorías | Media | `course-categories:read` | **En desarrollo** (17-09-2026) |
| `RF-AC-004` | Editar categoría | Categorías | Alta | `course-categories:update` | **En desarrollo** (17-09-2026) |
| `RF-AC-005` | Eliminar categoría | Categorías | Media | `course-categories:delete` | **En desarrollo** (17-09-2026) |
| `RF-AC-006` | Subir o reemplazar la portada de una categoría | Portadas | Media | `course-categories:update` | **En desarrollo** (25-09-2026) |
| `RF-AC-007` | Quitar la portada de una categoría | Portadas | Baja | `course-categories:update` | **Tasks en revisión** (18-09-2026) |
| `RF-AC-008` | Registrar curso | Cursos | Alta | `courses:create` | **En desarrollo** (18-09-2026) |
| `RF-AC-009` | Consultar cursos | Cursos | Alta | `courses:read` | **En desarrollo** (18-09-2026) |
| `RF-AC-010` | Consultar el detalle de un curso | Cursos | Alta | `courses:read` | **En desarrollo** (18-09-2026) |
| `RF-AC-011` | Editar curso | Cursos | Alta | `courses:update` | **En desarrollo** (18-09-2026) |
| `RF-AC-012` | Cambiar el estado de un curso | Cursos | Alta | `courses:update` | **En desarrollo** (18-09-2026) |
| `RF-AC-013` | Eliminar curso | Cursos | Media | `courses:delete` | **En desarrollo** (18-09-2026) |
| `RF-AC-014` | Subir o reemplazar la portada de un curso | Portadas | Media | `courses:update` | **En desarrollo** (25-09-2026) |
| `RF-AC-015` | Quitar la portada de un curso | Portadas | Baja | `courses:update` | **Tasks en revisión** (18-09-2026) |
| `RF-AC-016` | Clasificar un curso en una categoría | Cursos | Alta | `courses:update` | **En desarrollo** (25-09-2026) |
| `RF-AC-017` | Desclasificar un curso de una categoría | Cursos | Media | `courses:update` | **En desarrollo** (25-09-2026) |
| `RF-AC-018` | Recomendar un curso previo | Cursos | Media | `courses:update` | **Tasks en revisión** (18-09-2026) |
| `RF-AC-019` | Retirar una recomendación | Cursos | Baja | `courses:update` | **Tasks en revisión** (18-09-2026) |
| `RF-AC-020` | Dar visibilidad de un curso a una membresía | Cursos | Alta | `courses:update` | **Tasks en revisión** (18-09-2026) |
| `RF-AC-021` | Quitar la visibilidad de un curso a una membresía | Cursos | Media | `courses:update` | **Tasks en revisión** (18-09-2026) |
| `RF-AC-022` | Registrar módulo | Módulos | Alta | `courses:update` | **En desarrollo** (19-09-2026) |
| `RF-AC-023` | Editar módulo | Módulos | Alta | `courses:update` | **En desarrollo** (19-09-2026) |
| `RF-AC-024` | Cambiar el estado de un módulo | Módulos | Alta | `courses:update` | **En desarrollo** (19-09-2026) |
| `RF-AC-025` | Eliminar módulo | Módulos | Media | `courses:update` | **En desarrollo** (19-09-2026) |
| `RF-AC-026` | Subir o reemplazar la portada de un módulo | Portadas | Baja | `courses:update` | **Tasks en revisión** (18-09-2026) |
| `RF-AC-027` | Quitar la portada de un módulo | Portadas | Baja | `courses:update` | **Tasks en revisión** (18-09-2026) |
| `RF-AC-028` | Registrar lección | Lecciones | Alta | `courses:update` | **En desarrollo** (19-09-2026) |
| `RF-AC-029` | Editar lección | Lecciones | Alta | `courses:update` | **En desarrollo** (19-09-2026) |
| `RF-AC-030` | Cambiar el estado de una lección | Lecciones | Alta | `courses:update` | **En desarrollo** (19-09-2026) |
| `RF-AC-031` | Eliminar lección | Lecciones | Media | `courses:update` | **En desarrollo** (19-09-2026) |
| `RF-AC-032` | Obtener la imagen de una portada de academia, sin autenticación | Portadas | Alta | **Público** | **En desarrollo** (25-09-2026) |
| `RF-AC-033` | Consultar el catálogo de cursos como alumno | Aula | Alta | `courses:learn` | **Tasks en revisión** (18-09-2026) |
| `RF-AC-034` | Consultar el detalle de un curso como alumno | Aula | Alta | `courses:learn` | **Tasks en revisión** (18-09-2026) |
| `RF-AC-035` | Consultar el contenido de una lección | Aula | Alta | `courses:learn` | **Tasks en revisión** (18-09-2026) |
| `RF-AC-036` | Consultar el detalle de una lección | Lecciones | Media | `courses:read` | **En desarrollo** (19-09-2026) |
| `RF-AC-037` | Dar visibilidad de un curso a un servicio | Cursos | Alta | `courses:update` | **En desarrollo** (25-09-2026) |
| `RF-AC-038` | Quitar la visibilidad de un curso a un servicio | Cursos | Media | `courses:update` | **En desarrollo** (25-09-2026) |

**Treinta y ocho requerimientos** —treinta y cinco del 17-09-2026, `RF-AC-036` del 18 (§5.2.7) y `RF-AC-037` y `RF-AC-038` del 25 (§5.2.8)—, y la cifra merece una explicación: no es que el módulo sea grande, es que **cada entidad paga el mismo precio** —alta, corrección, estado, retiro— y **cada relación cobra dos** —dar y quitar—. Es la misma forma que `PM` con el producto y el paquete, y la razón de no juntar «asociar» y «desasociar» en un solo requerimiento es la de siempre: son dos operaciones con dos reglas distintas y dos auditorías distintas.

**Los módulos, las lecciones y las cuatro relaciones se administran con `courses:update`** y no con un recurso propio (§7): son partes del curso, y quien puede corregir un curso puede armarlo.

**Orden sugerido de implementación.** Por bloques, cada uno con su tripleta revisada antes de escribir código:

1. **Categorías** — `RF-AC-001` → `RF-AC-002` → `RF-AC-003` → `RF-AC-004` → `RF-AC-005`. El alta crea la tabla y siembra los cuatro `course-categories:`. Sin portada todavía.
2. **Cursos** — `RF-AC-008` → `RF-AC-009` → `RF-AC-010` → `RF-AC-011` → `RF-AC-012` → `RF-AC-013`. El alta crea `courses`, siembra los seis `courses:` y **pide a `SP` la interfaz del permiso** (§3). El detalle nace con módulos y lecciones vacíos y con `offerable: false` siempre, hasta el bloque 3.
3. **Módulos y lecciones** — `RF-AC-022` → `RF-AC-028` → `RF-AC-023` → `RF-AC-029` → `RF-AC-024` → `RF-AC-030` → `RF-AC-025` → `RF-AC-031` → `RF-AC-036`. Las lecciones van antes que la corrección de módulos porque activar un módulo (`RF-AC-024`) necesita una lección activa. **`RN-AC-015` se construye entero aquí**, y enmienda el detalle del bloque 2 (Art. I.7).
4. **Relaciones** — `RF-AC-016` → `RF-AC-017` → `RF-AC-037` → `RF-AC-038` → `RF-AC-020` → `RF-AC-021` → `RF-AC-018` → `RF-AC-019`. Las visibilidades van antes que las recomendaciones porque sin ellas ningún curso se ofrece, y **la del servicio va primero** desde el 25-09-2026 porque es la que el responsable del proyecto pidió; cualquiera de las dos basta para que un curso se ofrezca.
5. **Portadas** — `RF-AC-006` → `RF-AC-032` → `RF-AC-007` → `RF-AC-014` → `RF-AC-015` → `RF-AC-026` → `RF-AC-027`. La primera subida crea `academy_images` y **mueve el detector a `shared/`**; la ruta pública va segunda porque sin ella `coverImageUrl` señalaría a nada; y las demás entidades heredan.
6. **Aula** — `RF-AC-033` → `RF-AC-034` → `RF-AC-035`. Va al final porque lee todo lo anterior, y es el bloque que **enseña el resultado**. `RF-AC-036` se redactó con este bloque y se construye con el 3, que es de donde sale su forma.

### 6.2 Fichas

#### `RF-AC-001` — Registrar categoría

| Campo | Valor |
|---|---|
| Objetivo | Que exista un cajón del catálogo con qué pintarlo |
| Actor | Administrador |
| Permiso requerido | `course-categories:create` |
| Prioridad | Alta |
| Reglas aplicables | `RN-AC-001`, `RN-AC-002`, `RN-AC-003` |
| Depende de | — |
| Tripleta | `docs/specs/ac/001-registrar-categoria/` |
| Estado | **En desarrollo** (17-09-2026) |

Registra una categoría con **nombre, color, icono y orden**, obligatorios, y descripción opcional. Nace **viva y sin portada** —la imagen se sube después con `RF-AC-006`, y la respuesta trae `coverImageUrl` presente y nulo—. Es el requerimiento que **crea `course_categories`** y **siembra los cuatro permisos `course-categories:`**, asociados a `SUPERADMIN` y a `ADMIN` en la misma migración. El color se normaliza a mayúsculas antes de escribir, como en la membresía.

#### `RF-AC-002` — Consultar categorías

| Campo | Valor |
|---|---|
| Objetivo | Ver el catálogo de categorías para administrarlo |
| Actor | Administrador |
| Permiso requerido | `course-categories:read` |
| Prioridad | Alta |
| Reglas aplicables | `RN-AC-002`, `RN-AC-018` |
| Depende de | `RF-AC-001` |
| Tripleta | `docs/specs/ac/002-consultar-categorias/` |
| Estado | **En desarrollo** (17-09-2026) |

Lista paginada **por orden y desempate por identificador** (`RN-AC-002`), con **las retiradas fuera salvo que se pidan**, como el catálogo de productos (`RF-PM-002`). Cada fila trae **cuántos cursos vivos** tiene clasificados, calculado en la misma consulta, y `coverImageUrl`. Filtro por nombre.

#### `RF-AC-003` — Consultar el detalle de una categoría

| Campo | Valor |
|---|---|
| Objetivo | Ver una categoría entera, con los cursos que contiene |
| Actor | Administrador |
| Permiso requerido | `course-categories:read` |
| Prioridad | Media |
| Reglas aplicables | `RN-AC-002`, `RN-AC-018` |
| Depende de | `RF-AC-001` |
| Tripleta | `docs/specs/ac/003-consultar-detalle-categoria/` |
| Estado | **En desarrollo** (17-09-2026) |

La categoría con todos sus campos, **incluida una retirada**, y **la lista de sus cursos vivos** —identificador, título, estado, orden y `offerable`— en su orden. Es la vista con la que administración decide qué reordenar.

#### `RF-AC-004` — Editar categoría

| Campo | Valor |
|---|---|
| Objetivo | Corregir lo que se declaró |
| Actor | Administrador |
| Permiso requerido | `course-categories:update` |
| Prioridad | Alta |
| Reglas aplicables | `RN-AC-001`, `RN-AC-002`, `RN-AC-003`, `RN-AC-018` |
| Depende de | `RF-AC-001` |
| Tripleta | `docs/specs/ac/004-editar-categoria/` |
| Estado | **En desarrollo** (17-09-2026) |

Corrección **parcial** —solo lo que viene cambia, como `RF-PM-004`— de nombre, descripción, color, icono y orden. **La descripción se vacía** con nulo explícito; los otros cuatro no admiten vaciarse. Una retirada no se corrige. **La portada no se corrige por aquí**: tiene sus endpoints (`RF-AC-006`, `RF-AC-007`).

#### `RF-AC-005` — Eliminar categoría

| Campo | Valor |
|---|---|
| Objetivo | Retirar un cajón del catálogo sin perder su historia |
| Actor | Administrador |
| Permiso requerido | `course-categories:delete` |
| Prioridad | Media |
| Reglas aplicables | `RN-AC-010`, `RN-AC-018` |
| Depende de | `RF-AC-001` |
| Tripleta | `docs/specs/ac/005-eliminar-categoria/` |
| Estado | **En desarrollo** (17-09-2026) |

Baja lógica **con motivo** y registro de eliminación (Art. V.13). **No arrastra nada**: los cursos clasificados en ella siguen vivos y ofrecidos, y sus filas de clasificación se conservan y dejan de contar. **Nunca se rechaza por tener cursos**: la categoría es un filtro, y retirar un filtro no puede dejar nada roto. Retirar una ya retirada devuelve `409`.

#### `RF-AC-006` — Subir o reemplazar la portada de una categoría

| Campo | Valor |
|---|---|
| Objetivo | Que la categoría tenga una imagen con la que presentarse |
| Actor | Administrador |
| Permiso requerido | `course-categories:update` |
| Prioridad | Media |
| Reglas aplicables | `RN-AC-004` |
| Depende de | `RF-AC-001` |
| Tripleta | `docs/specs/ac/006-subir-portada-categoria/` |
| Estado | **En desarrollo** (25-09-2026) |

`RF-PM-014` aplicado a la categoría: un archivo `multipart/form-data`, comprobado por sus primeros bytes y su tamaño, guardado tal cual; si ya había portada, la nueva estrena identificador y la vieja se borra en la misma transacción. **Es el requerimiento que crea `academy_images`** y **el que mueve el detector de firma de `PM` a `shared/`** (§5.2.3), con la suite de `PM` en verde como condición.

#### `RF-AC-007` — Quitar la portada de una categoría

| Campo | Valor |
|---|---|
| Objetivo | Dejar la categoría sin imagen, que se pinte con su color e icono |
| Actor | Administrador |
| Permiso requerido | `course-categories:update` |
| Prioridad | Baja |
| Reglas aplicables | `RN-AC-004` |
| Depende de | `RF-AC-006` |
| Tripleta | `docs/specs/ac/007-quitar-portada-categoria/` |
| Estado | **Tasks en revisión** (18-09-2026) |

Suelta la imagen y la borra. **Nunca se rechaza**: la categoría siempre tiene color e icono. Responde `200` con la categoría, como `RF-PM-029` con el paquete; sin portada, `200` sin escribir nada (precisado el 18-09-2026: decía `204`).

#### `RF-AC-008` — Registrar curso

| Campo | Valor |
|---|---|
| Objetivo | Que exista un curso, todavía vacío, con quién lo enseña |
| Actor | Administrador |
| Permiso requerido | `courses:create` |
| Prioridad | Alta |
| Reglas aplicables | `RN-AC-001`, `RN-AC-002`, `RN-AC-005`, `RN-AC-006`, `RN-AC-007`, `RN-AC-008` |
| Depende de | — |
| Tripleta | `docs/specs/ac/008-registrar-curso/` |
| Estado | **En desarrollo** (18-09-2026) |

Registra un curso con **título, instructor, dificultad y orden**, obligatorios, y descripción corta, descripción larga, video de introducción y, desde el 25-09-2026 (§5.2.9), **categorías, servicios y membresías** (`categoryIds`, `productIds`, `membershipIds`), opcionales. **Nace `INACTIVO`, con las relaciones pedidas y ninguna otra, sin módulos y sin portada.** El instructor se comprueba contra `SP` —existe, no retirado, porta `courses:teach`— y **es el requerimiento que pide a `SP` la interfaz del permiso** (§3). Crea `courses` y siembra los seis `courses:`, asociados a `SUPERADMIN` y a `ADMIN`. La respuesta trae el instructor resuelto —identificador, nombre de usuario y nombre completo—, `coverImageUrl` nulo y `offerable: false` con su motivo.

#### `RF-AC-009` — Consultar cursos

| Campo | Valor |
|---|---|
| Objetivo | Ver el catálogo de cursos para administrarlo |
| Actor | Administrador |
| Permiso requerido | `courses:read` |
| Prioridad | Alta |
| Reglas aplicables | `RN-AC-002`, `RN-AC-015`, `RN-AC-018` |
| Depende de | `RF-AC-008` |
| Tripleta | `docs/specs/ac/009-consultar-cursos/` |
| Estado | **En desarrollo** (18-09-2026) |

Lista paginada **por orden y desempate por identificador**, con las retiradas fuera salvo que se pidan; filtros por título, categoría, instructor, dificultad y estado. Cada fila trae el instructor resuelto, las categorías, `coverImageUrl`, **`offerable` con su motivo** y **cuántos módulos y lecciones vivos** tiene, calculados en la misma consulta. **No trae módulos ni lecciones**: eso es el detalle.

#### `RF-AC-010` — Consultar el detalle de un curso

| Campo | Valor |
|---|---|
| Objetivo | Ver un curso entero: lo suyo, sus relaciones y su árbol |
| Actor | Administrador |
| Permiso requerido | `courses:read` |
| Prioridad | Alta |
| Reglas aplicables | `RN-AC-002`, `RN-AC-015`, `RN-AC-018` |
| Depende de | `RF-AC-008` |
| Tripleta | `docs/specs/ac/010-consultar-detalle-curso/` |
| Estado | **En desarrollo** (18-09-2026) |

El curso con todos sus campos, **incluido uno retirado**; el instructor resuelto; **las categorías** en que está; **los cursos que recomienda** —identificador, título, estado, `offerable`—; **las membresías que lo abren** —identificador, código, nombre, color—; y **los módulos en su orden, cada uno con sus lecciones en su orden**, vivos y retirados marcados, cada lección con tipo, duración, estado y `open`, **sin el contenido** —que es del aula y de la edición—. Y `offerable` con su motivo, **también por módulo**. **La duración total** del curso y de cada módulo, sumada en la lectura. Es la vista con la que administración arma el curso y ve qué le falta para publicarse.

#### `RF-AC-011` — Editar curso

| Campo | Valor |
|---|---|
| Objetivo | Corregir lo que se declaró, incluido el instructor |
| Actor | Administrador |
| Permiso requerido | `courses:update` |
| Prioridad | Alta |
| Reglas aplicables | `RN-AC-001`, `RN-AC-002`, `RN-AC-005`, `RN-AC-006`, `RN-AC-007`, `RN-AC-018` |
| Depende de | `RF-AC-008` |
| Tripleta | `docs/specs/ac/011-editar-curso/` |
| Estado | **En desarrollo** (18-09-2026) |

Corrección **parcial** de título, instructor, dificultad, descripciones, video y orden. **Las descripciones y el video se vacían** con nulo explícito **aunque el curso esté `ACTIVO`**: `RN-AC-009` rige al activar y no después, y un curso activo que se queda sin descripción sigue activo — es el mismo trato que `RF-PM-004` da a la descripción. **Reasignar el instructor** repite la comprobación de `RN-AC-006` sobre el nuevo. Un retirado no se corrige. Las relaciones, el estado y la portada tienen sus propios endpoints.

#### `RF-AC-012` — Cambiar el estado de un curso

| Campo | Valor |
|---|---|
| Objetivo | Publicar o despublicar un curso |
| Actor | Administrador |
| Permiso requerido | `courses:update` |
| Prioridad | Alta |
| Reglas aplicables | `RN-AC-008`, `RN-AC-009`, `RN-AC-015`, `RN-AC-018` |
| Depende de | `RF-AC-008` |
| Tripleta | `docs/specs/ac/012-cambiar-estado-curso/` |
| Estado | **En desarrollo** (18-09-2026) |

`ACTIVO` ↔ `INACTIVO`. **Activar exige** descripción corta, descripción larga y al menos un módulo `ACTIVO` no retirado (`RN-AC-009`); **desactivar nunca se rechaza**. Activar **no exige membresías**: un curso activo sin lista existe y no se ofrece, y el detalle lo dice (`RN-AC-015`). Un retirado no cambia de estado; el mismo estado devuelve `409`.

#### `RF-AC-013` — Eliminar curso

| Campo | Valor |
|---|---|
| Objetivo | Retirar un curso con todo lo que cuelga de él |
| Actor | Administrador |
| Permiso requerido | `courses:delete` |
| Prioridad | Media |
| Reglas aplicables | `RN-AC-011`, `RN-AC-018` |
| Depende de | `RF-AC-008` |
| Tripleta | `docs/specs/ac/013-eliminar-curso/` |
| Estado | **En desarrollo** (18-09-2026) |

Baja lógica con motivo, **y arrastra**: todos sus módulos y lecciones vivos se retiran con el mismo motivo, en la misma transacción, **con un registro de eliminación cada uno** (Art. V.13). Sus clasificaciones, recomendaciones y visibilidades se conservan y dejan de verse; **donde figure como recomendado, deja de enseñarse**. Retirar uno retirado devuelve `409`.

#### `RF-AC-014` — Subir o reemplazar la portada de un curso

| Campo | Valor |
|---|---|
| Objetivo | Que el curso tenga una imagen con la que presentarse |
| Actor | Administrador |
| Permiso requerido | `courses:update` |
| Prioridad | Media |
| Reglas aplicables | `RN-AC-004` |
| Depende de | `RF-AC-008`, `RF-AC-006` |
| Tripleta | `docs/specs/ac/014-subir-portada-curso/` |
| Estado | **En desarrollo** (25-09-2026) |

`RF-AC-006` aplicado al curso, sobre la misma tabla y el mismo detector. Sin condición, en cualquier estado.

#### `RF-AC-015` — Quitar la portada de un curso

| Campo | Valor |
|---|---|
| Objetivo | Dejar el curso sin imagen |
| Actor | Administrador |
| Permiso requerido | `courses:update` |
| Prioridad | Baja |
| Reglas aplicables | `RN-AC-004` |
| Depende de | `RF-AC-014` |
| Tripleta | `docs/specs/ac/015-quitar-portada-curso/` |
| Estado | **Tasks en revisión** (18-09-2026) |

Suelta la imagen y la borra. **Nunca se rechaza**: el curso se pinta con el icono por omisión del sistema.

#### `RF-AC-016` — Clasificar un curso en una categoría

| Campo | Valor |
|---|---|
| Objetivo | Que el curso se encuentre en un cajón |
| Actor | Administrador |
| Permiso requerido | `courses:update` |
| Prioridad | Alta |
| Reglas aplicables | `RN-AC-010`, `RN-AC-018` |
| Depende de | `RF-AC-001`, `RF-AC-008` |
| Tripleta | `docs/specs/ac/016-clasificar-curso/` |
| Estado | **En desarrollo** (25-09-2026) |

Añade la pareja curso–categoría. Rechaza la repetida (`409`, nombrando la categoría), la categoría retirada y el curso retirado. Responde con el curso, como toda escritura sobre él.

#### `RF-AC-017` — Desclasificar un curso de una categoría

| Campo | Valor |
|---|---|
| Objetivo | Sacar el curso de un cajón |
| Actor | Administrador |
| Permiso requerido | `courses:update` |
| Prioridad | Media |
| Reglas aplicables | `RN-AC-010`, `RN-AC-018` |
| Depende de | `RF-AC-016` |
| Tripleta | `docs/specs/ac/017-desclasificar-curso/` |
| Estado | **En desarrollo** (25-09-2026) |

Borra la fila. La auditoría de cambios del curso conserva antes y después. Una pareja que no existe devuelve `404`.

#### `RF-AC-018` — Recomendar un curso previo

| Campo | Valor |
|---|---|
| Objetivo | Decirle al alumno qué conviene ver antes |
| Actor | Administrador |
| Permiso requerido | `courses:update` |
| Prioridad | Media |
| Reglas aplicables | `RN-AC-011`, `RN-AC-018` |
| Depende de | `RF-AC-008` |
| Tripleta | `docs/specs/ac/018-recomendar-curso/` |
| Estado | **Tasks en revisión** (18-09-2026) |

Añade «antes de **este** conviene ver **aquel**». Rechaza recomendarse a sí mismo, la pareja repetida y el recomendado retirado. **No comprueba ciclos.** El recomendado puede estar `INACTIVO` o sin membresías: se conserva y **el aula no lo enseña** hasta que se ofrezca.

#### `RF-AC-019` — Retirar una recomendación

| Campo | Valor |
|---|---|
| Objetivo | Dejar de sugerir un curso previo |
| Actor | Administrador |
| Permiso requerido | `courses:update` |
| Prioridad | Baja |
| Reglas aplicables | `RN-AC-011` |
| Depende de | `RF-AC-018` |
| Tripleta | `docs/specs/ac/019-retirar-recomendacion/` |
| Estado | **Tasks en revisión** (18-09-2026) |

Borra la fila. Una pareja que no existe devuelve `404`.

#### `RF-AC-020` — Dar visibilidad de un curso a una membresía

| Campo | Valor |
|---|---|
| Objetivo | Que un nivel abra el curso |
| Actor | Administrador |
| Permiso requerido | `courses:update` |
| Prioridad | Alta |
| Reglas aplicables | `RN-AC-012`, `RN-AC-015`, `RN-AC-018` |
| Depende de | `RF-AC-008` |
| Tripleta | `docs/specs/ac/020-dar-visibilidad-curso/` |
| Estado | **Tasks en revisión** (18-09-2026) |

Añade la pareja curso–membresía. La membresía se resuelve contra `MembershipCatalog` (`404` si no existe); la repetida, `409`. **No exige que el curso esté activo**: la lista se arma antes de publicar. Responde con el curso y su lista resuelta —código, nombre, color—.

#### `RF-AC-021` — Quitar la visibilidad de un curso a una membresía

| Campo | Valor |
|---|---|
| Objetivo | Que un nivel deje de abrir el curso |
| Actor | Administrador |
| Permiso requerido | `courses:update` |
| Prioridad | Media |
| Reglas aplicables | `RN-AC-012`, `RN-AC-015` |
| Depende de | `RF-AC-020` |
| Tripleta | `docs/specs/ac/021-quitar-visibilidad-curso/` |
| Estado | **Tasks en revisión** (18-09-2026) |

Borra la fila. **Quitar la última nunca se rechaza**: el curso deja de ofrecerse y el detalle lo dice (`RN-AC-015`). Es la forma de retirar un curso de la vista de todos sin desactivarlo.

#### `RF-AC-022` — Registrar módulo

| Campo | Valor |
|---|---|
| Objetivo | Que el curso tenga una parte donde poner lecciones |
| Actor | Administrador |
| Permiso requerido | `courses:update` |
| Prioridad | Alta |
| Reglas aplicables | `RN-AC-001`, `RN-AC-002`, `RN-AC-005`, `RN-AC-008`, `RN-AC-018`, `RN-AC-019` |
| Depende de | `RF-AC-008` |
| Tripleta | `docs/specs/ac/022-registrar-modulo/` |
| Estado | **En desarrollo** (19-09-2026) |

Registra un módulo **dentro de un curso** —la ruta lo dice: `POST /courses/{courseId}/modules`— con **título y orden**, obligatorios, y descripción corta, larga y video de presentación, opcionales. Nace `INACTIVO` y sin portada. Un curso retirado no admite módulos. Crea `course_modules`.

#### `RF-AC-023` — Editar módulo

| Campo | Valor |
|---|---|
| Objetivo | Corregir lo que se declaró |
| Actor | Administrador |
| Permiso requerido | `courses:update` |
| Prioridad | Alta |
| Reglas aplicables | `RN-AC-001`, `RN-AC-002`, `RN-AC-005`, `RN-AC-018`, `RN-AC-019` |
| Depende de | `RF-AC-022` |
| Tripleta | `docs/specs/ac/023-editar-modulo/` |
| Estado | **En desarrollo** (19-09-2026) |

Parcial, sobre título, descripciones, video y orden; descripciones y video se vacían. **El curso no se corrige** (`RN-AC-019`). Un retirado no se corrige.

#### `RF-AC-024` — Cambiar el estado de un módulo

| Campo | Valor |
|---|---|
| Objetivo | Publicar o despublicar una parte del curso |
| Actor | Administrador |
| Permiso requerido | `courses:update` |
| Prioridad | Alta |
| Reglas aplicables | `RN-AC-008`, `RN-AC-009`, `RN-AC-015`, `RN-AC-018` |
| Depende de | `RF-AC-022` |
| Tripleta | `docs/specs/ac/024-cambiar-estado-modulo/` |
| Estado | **En desarrollo** (19-09-2026) |

Activar exige **al menos una lección `ACTIVA`** no retirada; desactivar nunca se rechaza, **aunque sea el último módulo activo de un curso activo** — el curso queda activo y no ofrecible, y `RN-AC-015` lo enseña.

#### `RF-AC-025` — Eliminar módulo

| Campo | Valor |
|---|---|
| Objetivo | Retirar una parte del curso con sus lecciones |
| Actor | Administrador |
| Permiso requerido | `courses:update` |
| Prioridad | Media |
| Reglas aplicables | `RN-AC-018` |
| Depende de | `RF-AC-022` |
| Tripleta | `docs/specs/ac/025-eliminar-modulo/` |
| Estado | **En desarrollo** (19-09-2026) |

Baja lógica con motivo, **y arrastra sus lecciones** con el mismo motivo y un registro cada una. Se hace con `courses:update` y no con `courses:delete`: retirar una parte es corregir el curso; retirar el curso es otra cosa.

#### `RF-AC-026` — Subir o reemplazar la portada de un módulo

| Campo | Valor |
|---|---|
| Objetivo | Que el módulo tenga una imagen con la que presentarse |
| Actor | Administrador |
| Permiso requerido | `courses:update` |
| Prioridad | Baja |
| Reglas aplicables | `RN-AC-004` |
| Depende de | `RF-AC-022`, `RF-AC-006` |
| Tripleta | `docs/specs/ac/026-subir-portada-modulo/` |
| Estado | **Tasks en revisión** (18-09-2026) |

`RF-AC-006` aplicado al módulo.

#### `RF-AC-027` — Quitar la portada de un módulo

| Campo | Valor |
|---|---|
| Objetivo | Dejar el módulo sin imagen |
| Actor | Administrador |
| Permiso requerido | `courses:update` |
| Prioridad | Baja |
| Reglas aplicables | `RN-AC-004` |
| Depende de | `RF-AC-026` |
| Tripleta | `docs/specs/ac/027-quitar-portada-modulo/` |
| Estado | **Tasks en revisión** (18-09-2026) |

Suelta la imagen y la borra. Nunca se rechaza.

#### `RF-AC-028` — Registrar lección

| Campo | Valor |
|---|---|
| Objetivo | Que exista algo que estudiar |
| Actor | Administrador |
| Permiso requerido | `courses:update` |
| Prioridad | Alta |
| Reglas aplicables | `RN-AC-001`, `RN-AC-002`, `RN-AC-005`, `RN-AC-008`, `RN-AC-014`, `RN-AC-016`, `RN-AC-017`, `RN-AC-018`, `RN-AC-019` |
| Depende de | `RF-AC-022` |
| Tripleta | `docs/specs/ac/028-registrar-leccion/` |
| Estado | **En desarrollo** (19-09-2026) |

Registra una lección **dentro de un módulo** —`POST /courses/{courseId}/modules/{moduleId}/lessons`— con **tipo, título, duración y orden**, obligatorios, y descripción, contenido y `open`, opcionales (`open` falso por omisión). El contenido, si viene, se valida contra el tipo. Nace `INACTIVA`. Un módulo retirado no admite lecciones. Crea `lessons`.

#### `RF-AC-029` — Editar lección

| Campo | Valor |
|---|---|
| Objetivo | Corregir lo que se declaró, incluido el tipo y el contenido |
| Actor | Administrador |
| Permiso requerido | `courses:update` |
| Prioridad | Alta |
| Reglas aplicables | `RN-AC-001`, `RN-AC-002`, `RN-AC-005`, `RN-AC-014`, `RN-AC-016`, `RN-AC-017`, `RN-AC-018`, `RN-AC-019` |
| Depende de | `RF-AC-028` |
| Tripleta | `docs/specs/ac/029-editar-leccion/` |
| Estado | **En desarrollo** (19-09-2026) |

Parcial, sobre tipo, título, descripción, contenido, duración, orden y `open`. **Cambiar el tipo exige que el contenido resultante case con él** (`RN-AC-016`): pasar a `VIDEO` con un texto guardado y sin URL en la petición se rechaza sin aplicar nada. **El contenido se vacía** con nulo explícito **aunque la lección esté activa**, como la descripción del curso: `RN-AC-009` rige al activar. **El módulo no se corrige** (`RN-AC-019`).

#### `RF-AC-030` — Cambiar el estado de una lección

| Campo | Valor |
|---|---|
| Objetivo | Publicar o despublicar una lección |
| Actor | Administrador |
| Permiso requerido | `courses:update` |
| Prioridad | Alta |
| Reglas aplicables | `RN-AC-008`, `RN-AC-009`, `RN-AC-015`, `RN-AC-018` |
| Depende de | `RF-AC-028` |
| Tripleta | `docs/specs/ac/030-cambiar-estado-leccion/` |
| Estado | **En desarrollo** (19-09-2026) |

Activar exige **contenido**; desactivar nunca se rechaza, aunque sea la última activa de su módulo.

#### `RF-AC-031` — Eliminar lección

| Campo | Valor |
|---|---|
| Objetivo | Retirar una lección sin perder su historia |
| Actor | Administrador |
| Permiso requerido | `courses:update` |
| Prioridad | Media |
| Reglas aplicables | `RN-AC-018` |
| Depende de | `RF-AC-028` |
| Tripleta | `docs/specs/ac/031-eliminar-leccion/` |
| Estado | **En desarrollo** (19-09-2026) |

Baja lógica con motivo y registro. No arrastra nada: no hay nada debajo.

#### `RF-AC-032` — Obtener la imagen de una portada de academia, sin autenticación

| Campo | Valor |
|---|---|
| Objetivo | Que el navegador pinte la portada sin token |
| Actor | Cualquiera |
| Permiso requerido | **Ninguno** |
| Prioridad | Alta |
| Reglas aplicables | `RN-AC-004` |
| Depende de | `RF-AC-006` |
| Tripleta | `docs/specs/ac/032-imagen-de-portada-academia-publica/` |
| Estado | **En desarrollo** (25-09-2026) |

`RF-PM-016` sobre `academy_images`: `GET /api/v1/academy-images/{id}`, los bytes con su `Content-Type` real, **caché inmutable** de un año —la dirección no cambia nunca porque cada subida estrena identificador—, `404` si no existe, y la misma cota de tasa y la misma entrada en la lista pública de `SecurityConfig` que su hermana de `PM`. **No dice de qué entidad es la portada**, y no hace falta.

#### `RF-AC-033` — Consultar el catálogo de cursos como alumno

| Campo | Valor |
|---|---|
| Objetivo | Que el alumno vea qué se enseña y qué le abre su nivel |
| Actor | Alumno |
| Permiso requerido | `courses:learn` |
| Prioridad | Alta |
| Reglas aplicables | `RN-AC-002`, `RN-AC-012`, `RN-AC-013`, `RN-AC-015` |
| Depende de | `RF-AC-020`, `RF-AC-030` |
| Tripleta | `docs/specs/ac/033-consultar-catalogo-alumno/` |
| Estado | **Tasks en revisión** (18-09-2026) |

`GET /api/v1/courses/available`: **todos los cursos que se ofrecen** (`RN-AC-015`), en su orden, filtrables por categoría y por dificultad, **sin paginar** —son decenas, como la oferta de `PM`—. Cada uno con portada, título, instructor resuelto, dificultad, descripción corta, categorías —vivas—, **duración total**, **cuántas lecciones** y **`accessible`**: si la membresía vigente de quien pregunta está en su lista **o si tiene vigente uno de sus servicios** (`RN-AC-020`, enmienda del 25-09-2026 que se escribe en la tripleta al construir). **Trae además las categorías vivas** con su color, icono, portada y orden, para que el frontend pinte los cajones sin otra petición. Quien no tiene membresía vigente ve el catálogo entero con todo cerrado — y las demostraciones abiertas.

#### `RF-AC-034` — Consultar el detalle de un curso como alumno

| Campo | Valor |
|---|---|
| Objetivo | Que el alumno vea el curso entero antes de entrar, y sepa qué le abre |
| Actor | Alumno |
| Permiso requerido | `courses:learn` |
| Prioridad | Alta |
| Reglas aplicables | `RN-AC-011`, `RN-AC-013`, `RN-AC-014`, `RN-AC-015` |
| Depende de | `RF-AC-033` |
| Tripleta | `docs/specs/ac/034-consultar-detalle-curso-alumno/` |
| Estado | **Tasks en revisión** (18-09-2026) |

`GET /api/v1/courses/available/{id}`: el curso con sus dos descripciones y su video de introducción, el instructor, las categorías, **los cursos recomendados que se ofrezcan**, **las membresías que lo abren** —código, nombre, color—, **los servicios que lo abren** —código y nombre, desde el 25-09-2026— y **el árbol ofrecido**: módulos `ACTIVOS` con lección activa, en su orden, con portada, descripciones, video de presentación y duración; y sus lecciones activas, en su orden, con tipo, título, descripción, duración y **`accessible`** —verdadero si el curso lo es o si la lección está abierta—, **sin el contenido**. Un curso que no se ofrece devuelve `404`: para el alumno no existe.

#### `RF-AC-035` — Consultar el contenido de una lección

| Campo | Valor |
|---|---|
| Objetivo | Estudiar |
| Actor | Alumno |
| Permiso requerido | `courses:learn` |
| Prioridad | Alta |
| Reglas aplicables | `RN-AC-013`, `RN-AC-014`, `RN-AC-015`, `RN-AC-016` |
| Depende de | `RF-AC-034` |
| Tripleta | `docs/specs/ac/035-consultar-contenido-leccion/` |
| Estado | **Tasks en revisión** (18-09-2026) |

`GET /api/v1/courses/available/{courseId}/lessons/{lessonId}`: la lección con su **contenido** —la URL si es `VIDEO`, el Markdown si es `TEXTO`— y lo demás. **`404`** si el curso no se ofrece o la lección no se ofrece dentro de él; **`403`** si se ofrece y ni la membresía vigente de quien pregunta está en la lista del curso, ni tiene vigente uno de sus servicios, ni la lección está abierta — con **las membresías y los servicios que lo abren** en el cuerpo del error, que es la invitación. Es el único sitio donde el contenido de una lección sale hacia un alumno.

#### `RF-AC-036` — Consultar el detalle de una lección

| Campo | Valor |
|---|---|
| Objetivo | Que administración lea el contenido de una lección sin editarla |
| Actor | Administrador |
| Permiso requerido | `courses:read` |
| Prioridad | Media |
| Reglas aplicables | `RN-AC-016`, `RN-AC-018`, `RN-AC-019` |
| Depende de | `RF-AC-028` |
| Tripleta | `docs/specs/ac/036-consultar-detalle-leccion/` |
| Estado | **En desarrollo** (19-09-2026) |

`GET /api/v1/courses/{courseId}/modules/{moduleId}/lessons/{lessonId}`: la lección entera **con su contenido**, en la misma forma que devuelven sus escrituras (`RF-AC-028`), **viva o retirada** —la retirada con fecha y motivo, también la que arrastró el retiro de su módulo o de su curso—. La ruta afirma la pertenencia en los tres niveles: una lección de otro módulo, o un módulo de otro curso, es `404`. Nace el 18-09-2026 (§5.2.7) porque el detalle del curso no trae el contenido a propósito y la corrección exige un campo: administración no tenía cómo ver lo que escribió.

#### `RF-AC-037` — Dar visibilidad de un curso a un servicio

| Campo | Valor |
|---|---|
| Objetivo | Que quien tiene un servicio pueda estudiar el curso |
| Actor | Administrador |
| Permiso requerido | `courses:update` |
| Prioridad | Alta |
| Reglas aplicables | `RN-AC-015`, `RN-AC-018`, `RN-AC-020` |
| Depende de | `RF-AC-008` |
| Tripleta | `docs/specs/ac/037-dar-visibilidad-curso-servicio/` |
| Estado | **En desarrollo** (25-09-2026) |

`POST /api/v1/courses/{courseId}/products` con `{ "productId" }`: añade la pareja curso–producto. El producto se resuelve contra `ProductCatalog`: inexistente o retirado, `422`; que no sea `BOT`, `422` distinto; la pareja repetida, `409` **nombrando el producto**. **No exige que el curso esté activo ni que el servicio esté a la venta.** Responde `201` con el curso y su lista resuelta —código y nombre—. Crea `course_products` (§8.5.1) y enmienda la ofrecibilidad, el detalle y el retiro del curso.

#### `RF-AC-038` — Quitar la visibilidad de un curso a un servicio

| Campo | Valor |
|---|---|
| Objetivo | Que un servicio deje de abrir el curso |
| Actor | Administrador |
| Permiso requerido | `courses:update` |
| Prioridad | Media |
| Reglas aplicables | `RN-AC-015`, `RN-AC-020` |
| Depende de | `RF-AC-037` |
| Tripleta | `docs/specs/ac/038-quitar-visibilidad-curso-servicio/` |
| Estado | **En desarrollo** (25-09-2026) |

`DELETE /api/v1/courses/{courseId}/products/{productId}`: borra la fila, sin motivo, como `ASSOCIATION`. **Quitar el último servicio nunca se rechaza**: si tampoco hay membresías, el curso deja de ofrecerse y el detalle lo dice. Un servicio retirado se quita igual.

---

## 7. Permisos

| Código | Recurso | Acción | Para qué |
|---|---|---|---|
| `course-categories:read` | `course-categories` | `read` | Listar y ver categorías, incluidas las retiradas |
| `course-categories:create` | `course-categories` | `create` | Registrar una categoría |
| `course-categories:update` | `course-categories` | `update` | Corregir una categoría y **su portada** |
| `course-categories:delete` | `course-categories` | `delete` | Retirar una categoría |
| `courses:read` | `courses` | `read` | Listar y ver cursos completos, con lo inactivo, lo retirado y lo que no se ofrece; **y leer una lección con su contenido** (`RF-AC-036`) |
| `courses:create` | `courses` | `create` | Registrar un curso |
| `courses:update` | `courses` | `update` | Corregir un curso, cambiar su estado, su portada, **sus relaciones**, y **registrar, corregir, cambiar de estado, retirar y poner portada a sus módulos y lecciones** |
| `courses:delete` | `courses` | `delete` | Retirar un curso, con lo que arrastra |
| `courses:teach` | `courses` | `teach` | **Poder ser instructor** de un curso (`RN-AC-006`). No gobierna ninguna ruta hoy |
| `courses:learn` | `courses` | `learn` | **La vista del alumno**: catálogo, detalle y contenido de lo que se ofrece |

**Las categorías tienen recurso propio y los módulos y lecciones no**, y la diferencia es la de `PM` entre `packages:` y la portada. Una categoría **existe sin cursos** y la administra quien organiza el catálogo, que puede no ser quien arma un curso; un módulo y una lección **no existen sin su curso**, y quien puede corregir el curso tiene que poder armarlo, o `courses:update` no serviría para nada. Separar «corregir el curso» de «armar el curso» tendría sentido el día que alguien deba poder lo uno sin lo otro — el instructor, cuando edite—, y ese día lo que se separa es la propiedad, no el permiso.

**`courses:teach` y `courses:learn` no son `<recurso>:<acción>` sobre una escritura**, como `products:sale` y `products:hotlink` no lo son: uno habilita a figurar y el otro gobierna una vista. Se siembran con los demás en la migración de `RF-AC-008`, asociados a `SUPERADMIN` y a `ADMIN` (§4).

---

## 8. Modelo de datos

Nueve tablas, todas de este módulo. Cuatro son **entidades con historia** —categoría, curso, módulo, lección, con `deleted_at`—, cuatro son **relaciones** sin identidad propia —la cuarta, `course_products`, desde el 25-09-2026— y una es **el valor de una columna** sacado a una tabla. Los tipos siguen a `products` (`requirements/pm.md` §10.1) donde el campo es el mismo.

### 8.1 `course_categories`

| Columna | Tipo | Nula | Referencia |
|---|---|---|---|
| `id` | `uuid` | No | — |
| `name` | `varchar(150)` | No | — |
| `description` | `text` | **Sí** | — |
| `color` | `varchar(6)` | No | `RN-AC-003` |
| `icon` | `varchar(50)` | No | `RN-AC-003` |
| `display_order` | `integer` | No | `RN-AC-002` |
| `cover_image_id` | `uuid` | **Sí** | `academy_images` |
| `created_at` | `timestamptz` | No | — |
| `updated_at` | `timestamptz` | No | — |
| `deleted_at` | `timestamptz` | **Sí** | Retiro lógico |

**Sin `status`** (`RN-AC-008`, §5.2.6). **`display_order` y no `order`**, que es palabra reservada, ni `position`, que es una función de PostgreSQL y se lee mal en un `SELECT`.

### 8.2 `courses`

| Columna | Tipo | Nula | Referencia |
|---|---|---|---|
| `id` | `uuid` | No | — |
| `title` | `varchar(150)` | No | — |
| `instructor_id` | `uuid` | No | `users` — `RN-AC-006` |
| `difficulty` | `varchar(20)` | No | `PRINCIPIANTE` \| `INTERMEDIO` \| `AVANZADO` |
| `short_description` | `varchar(300)` | **Sí** | — |
| `long_description` | `text` | **Sí** | — |
| `intro_video_url` | `varchar(500)` | **Sí** | `RN-AC-005` |
| `display_order` | `integer` | No | `RN-AC-002` |
| `status` | `varchar(20)` | No | `ACTIVO` \| `INACTIVO` |
| `cover_image_id` | `uuid` | **Sí** | `academy_images` |
| `created_at` | `timestamptz` | No | — |
| `updated_at` | `timestamptz` | No | — |
| `deleted_at` | `timestamptz` | **Sí** | Retiro lógico |

**`instructor_id` es clave foránea a `users` y se declara**, por lo mismo que `CM` declara las suyas: la frontera de §7 es la del código. **Sin `ON DELETE`**: el usuario no se borra físicamente nunca (`RF-SP-029`). **Sin código** (§5.2.6). **`short_description` acotada a 300** porque es lo que cabe en una tarjeta del catálogo, y **`long_description` sin tope** porque es la página del curso.

### 8.3 `course_category_items`

| Columna | Tipo | Nula | Referencia |
|---|---|---|---|
| `course_id` | `uuid` | No | `courses` |
| `category_id` | `uuid` | No | `course_categories` |
| `created_at` | `timestamptz` | No | — |

Clave primaria compuesta. **Sin `id` propio, sin `deleted_at`**: no es una entidad, es una relación, y desclasificar **borra** (`RN-AC-010`). Si la categoría o el curso se retiran, la fila sobrevive y las lecturas la ignoran por el `deleted_at` del otro lado.

### 8.4 `course_recommendations`

| Columna | Tipo | Nula | Referencia |
|---|---|---|---|
| `course_id` | `uuid` | No | `courses` — el curso **al que se entra** |
| `recommended_course_id` | `uuid` | No | `courses` — el que **conviene ver antes** |
| `created_at` | `timestamptz` | No | — |

Clave primaria compuesta. **El orden de las columnas es el de la frase**: «antes de `course_id` conviene ver `recommended_course_id`». Sin `display_order`: las recomendaciones de un curso se enseñan en el orden global de los cursos recomendados (`RN-AC-002`).

### 8.5 `course_memberships`

| Columna | Tipo | Nula | Referencia |
|---|---|---|---|
| `course_id` | `uuid` | No | `courses` |
| `membership_id` | `uuid` | No | `memberships` — `RN-AC-012` |
| `created_at` | `timestamptz` | No | — |

Clave primaria compuesta. **La clave foránea a `memberships` se declara** hacia `SP`, como `products.target_membership_id`. La membresía es inmutable y no se retira (`RN-SP-008`), de modo que esta fila no tiene «otro lado» que pueda morir.

### 8.5.1 `course_products` — 25-09-2026

| Columna | Tipo | Nula | Referencia |
|---|---|---|---|
| `course_id` | `uuid` | No | `courses` |
| `product_id` | `uuid` | No | `products` — `RN-AC-020`, de tipo `BOT` |
| `created_at` | `timestamptz` | No | — |

`course_memberships` con otro lado. Clave primaria compuesta, **la clave foránea a `products` se declara** hacia `PM`, como la de `user_products.product_id` y `commission_rates.product_id`. **Sin `ON DELETE`**: el producto se retira en lógico y no se borra nunca. **Que sea `BOT` no cabe en el esquema** —el tipo vive en `products` y ningún `CHECK` de esta tabla puede mirarlo—: lo comprueba el dominio al añadir, contra `ProductCatalog`, y como el tipo de un producto **no cambia nunca** (`RN-PM-001`), lo comprobado al añadir sigue siendo cierto. Un servicio retirado después **deja su fila** (`RN-AC-020`). **Se numera `§8.5.1` y no `§8.6`** para no renumerar las tablas que ya citan las tripletas.

### 8.6 `course_modules`

| Columna | Tipo | Nula | Referencia |
|---|---|---|---|
| `id` | `uuid` | No | — |
| `course_id` | `uuid` | No | `courses` — **no se corrige** (`RN-AC-019`) |
| `title` | `varchar(150)` | No | — |
| `short_description` | `varchar(300)` | **Sí** | — |
| `long_description` | `text` | **Sí** | — |
| `presentation_video_url` | `varchar(500)` | **Sí** | `RN-AC-005` |
| `display_order` | `integer` | No | `RN-AC-002` |
| `status` | `varchar(20)` | No | `ACTIVO` \| `INACTIVO` |
| `cover_image_id` | `uuid` | **Sí** | `academy_images` |
| `created_at` | `timestamptz` | No | — |
| `updated_at` | `timestamptz` | No | — |
| `deleted_at` | `timestamptz` | **Sí** | Retiro lógico |

**`course_modules` y no `modules`**, que en este proyecto nombra otra cosa (`modules.md`, `modules/`).

### 8.7 `lessons`

| Columna | Tipo | Nula | Referencia |
|---|---|---|---|
| `id` | `uuid` | No | — |
| `module_id` | `uuid` | No | `course_modules` — **no se corrige** (`RN-AC-019`) |
| `type` | `varchar(20)` | No | `VIDEO` \| `TEXTO` |
| `title` | `varchar(150)` | No | — |
| `description` | `text` | **Sí** | — |
| `content` | `text` | **Sí** | `RN-AC-016`: URL o Markdown según `type`; nulo hasta que se prepare |
| `duration_minutes` | `integer` | No | `RN-AC-017` |
| `display_order` | `integer` | No | `RN-AC-002` |
| `open` | `boolean` | No | `RN-AC-014`, por omisión `false` |
| `status` | `varchar(20)` | No | `ACTIVO` \| `INACTIVO` |
| `created_at` | `timestamptz` | No | — |
| `updated_at` | `timestamptz` | No | — |
| `deleted_at` | `timestamptz` | **Sí** | Retiro lógico |

**Una sola columna `content` para los dos tipos**, y no `video_url` más `body`: una lección tiene **un** contenido, y dos columnas con la regla «exactamente una, y la que corresponda al tipo» es lo que `CM` tuvo que escribir en `ck_commission_rates_forma` para una fila que sí declara dos formas a la vez. Aquí el tipo dice cómo leer la columna, y el `CHECK` de forma solo tiene que mirar la URL cuando el tipo es `VIDEO`.

**Sin portada**: la lección se estudia, no se presenta.

### 8.8 `academy_images`

| Columna | Tipo | Nula | Referencia |
|---|---|---|---|
| `id` | `uuid` | No | — |
| `content_type` | `varchar(30)` | No | `image/jpeg` \| `image/png` \| `image/webp` |
| `content` | `bytea` | No | — |
| `created_at` | `timestamptz` | No | — |

`product_images` columna a columna (`requirements/pm.md` §10.5), en el módulo que la escribe (§5.2.3). Sin `updated_at` ni `deleted_at`: una fila no se modifica nunca y la reemplazada se borra. **Sin columna que diga de qué entidad es**: quien la señala lo dice, y una imagen nace señalada desde una entidad y solo una — el mismo argumento de `requirements/pm.md` §5.2.12, ahora con tres tablas señalando en vez de dos.

### 8.9 Restricciones exigidas en el esquema

| Restricción | Sobre | Regla que implementa |
|---|---|---|
| `uq_course_categories_name` | Índice único **parcial** sobre `f_unaccent(lower(name))`, `WHERE deleted_at IS NULL` | `RN-AC-001`. Como `uq_products_name` |
| `ck_course_categories_color_format` | `color ~ '^[0-9A-F]{6}$'` | `RN-AC-003`. La misma expresión que `ck_memberships_color_format`; **sin `uq_`**: no es único |
| `ck_course_categories_display_order` | `display_order >= 0` | `RN-AC-002` |
| `uq_course_categories_cover_image` | Único sobre `cover_image_id` | Una imagen es portada de una categoría como máximo (`RN-AC-004`) |
| `fk_course_categories_cover_image` | `cover_image_id` → `academy_images(id)` | `RN-AC-004`. **Sin `ON DELETE`**: se suelta antes de borrar |
| `uq_courses_title` | Índice único parcial sobre `f_unaccent(lower(title))`, `WHERE deleted_at IS NULL` | `RN-AC-001` |
| `fk_courses_instructor` | `instructor_id` → `users(id)`, sin `ON DELETE` | `RN-AC-006`. Que porte el permiso **no cabe aquí**: vive en el dominio, contra la interfaz de `SP` |
| `ck_courses_difficulty` | `difficulty IN ('PRINCIPIANTE','INTERMEDIO','AVANZADO')` | `RN-AC-007` |
| `ck_courses_status` | `status IN ('ACTIVO','INACTIVO')` | `RN-AC-008` |
| `ck_courses_display_order` | `display_order >= 0` | `RN-AC-002` |
| `ck_courses_intro_video_url` | `intro_video_url IS NULL OR intro_video_url ~ '^https?://[^[:space:]]+$'` | `RN-AC-005`. La rama `IS NULL` delante y explícita |
| `uq_courses_cover_image`, `fk_courses_cover_image` | Como en la categoría | `RN-AC-004` |
| `pk_course_category_items` | `(course_id, category_id)` | `RN-AC-010`: la pareja no se repite |
| `fk_course_category_items_course`, `fk_course_category_items_category` | Hacia `courses` y `course_categories`, sin `ON DELETE` | Nada se borra físicamente |
| `pk_course_recommendations` | `(course_id, recommended_course_id)` | `RN-AC-011`: la pareja no se repite |
| `ck_course_recommendations_not_self` | `course_id <> recommended_course_id` | `RN-AC-011`: un curso no se recomienda a sí mismo. **Es lo único de la regla que cabe en el motor**: la aciclicidad no se exige y el retiro del recomendado se mira en el dominio |
| `fk_course_recommendations_course`, `fk_course_recommendations_recommended` | Las dos hacia `courses` | — |
| `pk_course_memberships` | `(course_id, membership_id)` | `RN-AC-012`: la pareja no se repite |
| `fk_course_memberships_course`, `fk_course_memberships_membership` | Hacia `courses` y `memberships`, sin `ON DELETE` | `RN-AC-012` |
| `pk_course_products` | `(course_id, product_id)` | `RN-AC-020`: la pareja no se repite |
| `fk_course_products_course`, `fk_course_products_product` | Hacia `courses` y `products`, sin `ON DELETE` | `RN-AC-020`. Que el producto sea `BOT` **no cabe aquí**: vive en el dominio (§8.5.1) |
| `ix_course_products_product` | `(product_id)` | Sostiene «qué cursos abre este servicio», que es la pregunta del aula al revés: con los productos vigentes de una persona, qué cursos se le abren |
| `uq_course_modules_title` | Índice único parcial sobre `(course_id, f_unaccent(lower(title)))`, `WHERE deleted_at IS NULL` | `RN-AC-001`: único **dentro del curso** |
| `fk_course_modules_course` | `course_id` → `courses(id)`, sin `ON DELETE` | `RN-AC-019` |
| `ck_course_modules_status`, `ck_course_modules_display_order`, `ck_course_modules_presentation_video_url` | Como en el curso | `RN-AC-008`, `RN-AC-002`, `RN-AC-005` |
| `uq_course_modules_cover_image`, `fk_course_modules_cover_image` | Como en la categoría | `RN-AC-004` |
| `uq_lessons_title` | Índice único parcial sobre `(module_id, f_unaccent(lower(title)))`, `WHERE deleted_at IS NULL` | `RN-AC-001`: único **dentro del módulo** |
| `fk_lessons_module` | `module_id` → `course_modules(id)`, sin `ON DELETE` | `RN-AC-019` |
| `ck_lessons_type` | `type IN ('VIDEO','TEXTO')` | `RN-AC-016` |
| `ck_lessons_video_content` | `type <> 'VIDEO' OR content IS NULL OR content ~ '^https?://[^[:space:]]+$'` | `RN-AC-016`: cuando es video y hay contenido, es una URL. Las tres ramas son predicados que no evalúan a `NULL` |
| `ck_lessons_duration` | `duration_minutes > 0` | `RN-AC-017` |
| `ck_lessons_status`, `ck_lessons_display_order` | Como en el curso | `RN-AC-008`, `RN-AC-002` |
| `ck_academy_images_content_type`, `ck_academy_images_size` | Como `ck_product_images_*` | `RN-AC-004`: los tres tipos, y de 1 byte a 5 MB |
| `ix_course_category_items_category` | `(category_id)` | Sostiene el filtro por categoría de `RF-AC-009` y `RF-AC-033`, y el conteo de `RF-AC-002` |
| `ix_course_modules_course` | `(course_id, display_order, id)`, parcial `WHERE deleted_at IS NULL` | Sostiene el árbol de `RF-AC-010` y `RF-AC-034` en su orden |
| `ix_lessons_module` | `(module_id, display_order, id)`, parcial `WHERE deleted_at IS NULL` | Lo mismo, un nivel abajo |

**Lo que NO se puede declarar en el esquema, y por eso vive en el dominio:** que el instructor porte `courses:teach` (`RN-AC-006`, otro módulo); que lo que se activa tenga con qué (`RN-AC-009`, filas de otra tabla); que no se clasifique en una categoría retirada ni se recomiende un curso retirado (`RN-AC-010`, `RN-AC-011`, el `deleted_at` de otra fila); que el contenido de un `TEXTO` sea Markdown (no hay Markdown inválido); y **la ofrecibilidad entera** (`RN-AC-015`), que es una cuenta sobre tres tablas y una lista y **no se guarda**. Un `CHECK` no consulta otra tabla.

**Y lo que el retiro en cascada exige del dominio**: `RF-AC-013` y `RF-AC-025` escriben `deleted_at` en varias tablas **y un registro de eliminación por fila**, en una sola transacción. No hay `ON DELETE CASCADE` que lo haga, porque no se borra nada.

---

## 9. Control de cambios

| Versión | Fecha | Cambio | Responsable |
|---|---|---|---|
| 0.1.0 | 17-09-2026 | **Nace el documento**, con el módulo incorporado en [`modules.md`](../modules.md) v0.21.0 §5.5 por decisión del responsable del proyecto. **Diecinueve reglas** (`RN-AC-001` a `RN-AC-019`), **treinta y cinco requerimientos** (`RF-AC-001` a `RF-AC-035`) en seis submódulos, **diez permisos** y **ocho tablas**. Las decisiones que lo definen, todas del responsable del proyecto y todas de hoy, están en §5.2: la lección cuelga del módulo y el «contenido» del módulo es su video de presentación; la visibilidad es **una lista explícita** de membresías y no un nivel mínimo, un curso sin lista no se ofrece, y **la demostración es una bandera de la lección** que se abre a cualquier alumno con sesión — con la consecuencia, aceptada entera en §1.4, de que **el catálogo del alumno enseña todos los cursos ofrecidos y la lista cierra el contenido en lugar de esconder el curso**; la portada vive en **`academy_images`**, tabla propia, y el detector de firma de `PM` se mueve a `shared/`; el texto de una lección es **Markdown que el backend no mira**, con la obligación que eso le impone al frontend; el instructor **porta `courses:teach`** y no un rol, lo que exige de `SP` **una interfaz que no publica todavía** —«¿porta este permiso?»— y que pedirá `RF-AC-008`; curso, módulo y lección **nacen inactivos y se activan con contenido**, y **lo que se vacía después no desactiva nada** porque la ofrecibilidad se calcula y no se guarda; retirar **arrastra hacia abajo** con un registro por fila, y la categoría no arrastra. El curso **no lleva código** y la categoría **no tiene estado**. Fuera, y escrito: vender el curso, alojar el video, el progreso del alumno y las sesiones en vivo. | Responsable del proyecto |
| 0.2.0 | 17-09-2026 | **Las cinco tripletas de Categorías quedan redactadas** (`RF-AC-001` a `RF-AC-005`, bloque 1 de §6.1) y pasan a `Tasks en revisión`. Sin cambio de reglas. Dos precisiones que las tripletas fijan y este documento hereda sin enmienda: `cover_image_id` nace en `course_categories` desde su primera migración, nulable y sin clave foránea hasta que `RF-AC-006` cree `academy_images` (§8.9 ya declara la restricción, que llegará entonces); y el detalle de la categoría (`RF-AC-003`) trae `courses` vacío y `offerable` falso hasta `RF-AC-008`, `RF-AC-016` y el bloque 3, con las tres enmiendas declaradas en su spec. | Responsable del proyecto |
| 0.3.0 | 17-09-2026 | **Las categorías están construidas** (`RF-AC-001` a `RF-AC-005`, `En desarrollo`): `V18` crea `course_categories` tal como §8.1 la declara —con `cover_image_id` nulable y sin clave foránea hasta `RF-AC-006`— y `V19` siembra los cuatro `course-categories:` de §7. Sin cambio de reglas. Dos precisiones que las tripletas fijaron al construir y que este documento no necesita enmendar: la descripción se acota a 1000 caracteres, como la del producto, y el motivo de retiro reparte `VAL-002`/`VAL-003` como `PM`, porque `DeletionReason` es ahora de `shared/audit` (§5.2.3 anticipaba el detector de imágenes como primer código compartido; el motivo se le adelantó). | Responsable técnico |
| 0.4.0 | 18-09-2026 | **Las seis tripletas de Cursos quedan redactadas** (`RF-AC-008` a `RF-AC-013`, bloque 2 de §6.1) y pasan a `Tasks en revisión`. Sin cambio de reglas, y **dos precisiones a §6.1 que este documento asume**: `CourseOfferability` —el objeto de `RN-AC-015`— **nace en el bloque 2** con su orden completo y no en el 3, porque ya puede decidir dos de sus cuatro motivos y la respuesta del alta necesita `offerableReason` desde el primer día; y **`RF-AC-012` se construye en el bloque 2** aunque ningún curso pueda activarse hasta que existan módulos, con su criterio bloqueado y declarado. **Una pregunta abierta para el responsable del proyecto**, escrita en `RF-AC-011` §14.1: `RN-AC-015` no lista las descripciones entre sus motivos, de modo que un curso activo que se queda sin descripción sigue ofreciéndose; si debe ocultarse, es un motivo más en la regla —como «sin descripción» en el paquete de `PM`— y no una desactivación. | Responsable del proyecto |
| 0.5.0 | 18-09-2026 | **Las ocho tripletas de Módulos y lecciones quedan redactadas** (bloque 3 de §6.1) y pasan a `Tasks en revisión`. Sin cambio de reglas, y **tres precisiones que este documento asume**: las escrituras sobre módulos y lecciones **devuelven la pieza y no el curso** —§2 decía que no tienen listado propio, y siguen sin tenerlo: el detalle del curso trae el árbol, y cada pieza tiene su forma para sus propias escrituras—; **la auditoría de cambios de una lección lleva `content_length`** y solo la de eliminación el contenido entero; y `RF-AC-025` y `RF-AC-031` van con `courses:update` como §7 declara. **Dos huecos para el responsable del proyecto**, escritos en `RF-AC-029` §14: `RN-AC-015` no mira el contenido —una lección activa vacía se enseña vacía, como el curso sin descripción (v0.4.0)—, y **ningún requerimiento permite a administración leer el contenido de una lección sin editarla**: o el detalle del módulo lo trae, o nace un `GET` de lección con `courses:read`. | Responsable del proyecto |
| 0.6.0 | 18-09-2026 | **Las seis tripletas de Relaciones quedan redactadas** (`RF-AC-016` a `RF-AC-021`, bloque 4 de §6.1) y pasan a `Tasks en revisión`. **Una precisión a `RN-AC-010`**: la auditoría de dar y quitar una relación es la de la fila —`CREATE` al dar, `ASSOCIATION` sin motivo al quitar, con el curso como entidad— y no un `UPDATE` del curso con la lista antes y después, que crecería con cada categoría y diría lo mismo peor; vale para las tres relaciones. Las tripletas fijan además que el otro lado de la pareja que no sirve —categoría retirada, membresía inexistente, curso recomendado retirado— es `422` y no `404`, porque viene en el cuerpo y no en la ruta; que el `409` de la pareja repetida **nombra** el otro lado; y que el recomendado **no se bloquea**. Con el bloque 4, `RN-AC-015` tiene sus cuatro motivos construibles. | Responsable del proyecto |
| 0.7.0 | 18-09-2026 | **Las siete tripletas de Portadas quedan redactadas** (bloque 5 de §6.1) y pasan a `Tasks en revisión`. Sin cambio de reglas. **Dos precisiones**: quitar una portada responde `200` con la entidad —como `RF-PM-029`— y no `204` como decía la ficha de `RF-AC-007`; y **las seis restricciones de las tres columnas `cover_image_id`** que §8.9 declara llegan todas con la migración de `academy_images` (`RF-AC-006`), porque las columnas nacieron antes que la tabla. §5.2.3 se cumple tal como se escribió: tabla propia, detector compartido — `ImageSignature` y `CambioDePortada` pasan a `shared/images`, y `DeletionReason` se les adelantó el 17-09-2026. | Responsable del proyecto |
| 0.8.0 | 18-09-2026 | **Las tres tripletas del Aula quedan redactadas** (`RF-AC-033` a `RF-AC-035`, bloque 6 de §6.1, el último) y pasan a `Tasks en revisión`. **Dos decisiones del responsable del proyecto, del mismo día**: **`RN-AC-015` gana dos motivos** —«sin descripción» en el curso, tercero de cinco, y «sin contenido» en la lección— para que lo que se vacía después de activarse **deje de ofrecerse** en lugar de enseñarse vacío (§5.2.7; `RN-AC-009` lo dice al lado); y **nace `RF-AC-036`**, consultar el detalle de una lección con `courses:read`, que cierra el hueco de `RF-AC-029` §14.2: **treinta y seis requerimientos**, §2, §6.1 (orden del bloque 3), §6.2 y §7 lo recogen. Las tripletas del aula fijan además que **la ofrecibilidad no se reescribe en SQL** —se lee lo vivo con las mismas cuentas y deciden los mismos objetos que administración—, que `accessible` lo decide un solo objeto, que el `404` del aula no distingue «no se ofrece» de «no existe», y que en la lección va primero si se ofrece y después si se abre. Con esto el catálogo del módulo tiene **todas sus tripletas redactadas**. | Responsable del proyecto |
| 0.9.0 | 18-09-2026 | **Los cursos están construidos**: `RF-AC-008` a `RF-AC-013` pasan a `En desarrollo` (bloque 2 de §6.1). `V21` crea `courses` y `V22` siembra los seis `courses:` con el catálogo en **60**; **`SP` publica `PermissionHolderLookup`** (§3 y la advertencia de cabecera dejan de decir «no existe»), y `CourseOfferability` nace con los cinco motivos. **Dos criterios quedan bloqueados y declarados** hasta el bloque 3: `CA-AC-064` (activar con un módulo activo) y `CA-AC-074` (el arrastre), y las lecturas de relaciones y árbol devuelven vacío con la nota de qué las sustituye. Sin cambio de reglas. | Responsable técnico |
| 0.10.0 | 19-09-2026 | **Los módulos y las lecciones están construidos**: `RF-AC-022` a `RF-AC-025`, `RF-AC-028` a `RF-AC-031` y `RF-AC-036` pasan a `En desarrollo` (bloque 3 de §6.1, con el `GET` de lección que nació con el 6). `V23` crea `course_modules` y `V24` `lessons`; **`RN-AC-015` queda cerrada por dentro** —`ModuleOfferability` y `LessonOfferability` cuentan lecciones activas con contenido, y `CourseOfferability` recibe módulos ofrecibles reales— y solo le falta la cuenta de membresías del bloque 4. **Se desbloquean `CA-AC-064` y `CA-AC-074`** de los cursos. Sin cambio de reglas; cuatro precisiones en las specs: el árbol se lee siempre (los retirados también se enseñan), las validaciones del alta de lección van por el caso de uso para llegar juntas, la carrera de la corrección sale con el código del alta, y el quinto motivo del curso solo se observa con una membresía delante. | Responsable técnico |
| 0.11.0 | 25-09-2026 | **La clasificación está construida**: `RF-AC-016` y `RF-AC-017` pasan a `En desarrollo` (primeros dos del bloque 4 de §6.1). `V42` crea `course_category_items` tal como §8.3 la declara —clave compuesta, sin `id` ni `deleted_at`, sin `ON DELETE`—; el plan la numeraba `V25` y el número se asignó al construir. **Las seis enmiendas declaradas por los bloques 1 y 2 quedan cerradas** —`courseCount`, los cursos del detalle de la categoría con su `offerable`, los `course_ids` de su retiro, `categories` y el filtro del listado de cursos, `categories` del detalle, y los `category_ids` del retiro del curso—. Sin cambio de reglas; una precisión a `RF-AC-009`: **`categoryId` solo acota por una categoría viva**, porque la retirada no sale en `categories` de ninguna fila. Los permisos siguen siendo `courses:update`, como las tripletas dicen: `courses:assign-category` y `courses:revoke-category` ya están sembrados por `V28` y los reparte el tramo 3 de `RF-SP-060` con todo `AC`. | Responsable técnico |
| 0.12.0 | 25-09-2026 | **Un curso se abre también por un servicio**, por decisión del responsable del proyecto (§5.2.8): **es el curso quien declara qué productos `BOT` lo abren**, además de qué membresías, y las dos listas **se suman**. Nace **`RN-AC-020`** —solo `BOT`; existente y no retirado al añadir; un servicio inactivo se añade; lo tiene quien lo tiene **vigente** en `user_products`; retirarlo en `PM` no toca la lista—, y **`RF-AC-037`** y **`RF-AC-038`**, con tripleta, en `Tasks en revisión`. **Se enmiendan tres reglas**: `RN-AC-012` y `RN-AC-015` —un curso sin membresías **y sin servicios** no se ofrece— y `RN-AC-013` —el contenido lo abre la membresía **o el servicio** vigente—. **La frontera de §1.4 se mueve, y no como ella misma anticipaba**: aquella versión preveía un tipo de producto nuevo que declarara el curso, con `PM` consumiendo a `AC`; lo decidido es lo contrario, y **`AC` pasa a depender de `PM`** (§3), sin ciclo. Nace **`course_products`** (§8.5.1) —la cuarta relación, nueve tablas— y **dos interfaces que no existen**: la lectura del tipo en `ProductCatalog`, que amplía `RF-AC-037`, y «los productos vigentes de una persona» en `SP`, que pedirá el aula. **Cuatro preguntas hechas antes de escribir**, con sus descartes en §5.2.8. El bloque 4 de §6.1 pasa a ocho requerimientos, con el servicio antes que la membresía. | Responsable del proyecto |
| 0.13.0 | 25-09-2026 | **Construidos cinco requerimientos y una enmienda**, en `feature/ajustes-academia`: **`RF-AC-037` y `RF-AC-038`** (`V43`, `course_products`) —el curso se abre también por un servicio—; **`RF-AC-006`, `RF-AC-032` y `RF-AC-014`** (`V44`, `academy_images`), adelantados del bloque 5 porque la portada del curso los necesita (§5.2.9); y **la enmienda de `RF-AC-008`** —el alta admite `categoryIds`, todo o nada—. `ImageSignature` y `CambioDePortada` pasan a `shared/images` (§5.2.3 lo anticipaba). **Las demás portadas siguen en `Tasks en revisión`**: quitar (`RF-AC-007`, `RF-AC-015`) y las del módulo (`RF-AC-026`, `RF-AC-027`). `CourseOfferability` suma las dos llaves; la de membresías sigue siendo cero hasta `RF-AC-020`. | Responsable técnico |
