package com.factech.nexus.modules.academy.interfaces;

import com.factech.nexus.modules.academy.application.ChangeCourseStatusRequest;
import com.factech.nexus.modules.academy.application.ClassifyCourseRequest;
import com.factech.nexus.modules.academy.application.CourseDetailResponse;
import com.factech.nexus.modules.academy.application.CoursePageResponse;
import com.factech.nexus.modules.academy.application.DeleteCourseRequest;
import com.factech.nexus.modules.academy.application.GrantCourseMembershipRequest;
import com.factech.nexus.modules.academy.application.GrantCourseProductRequest;
import com.factech.nexus.modules.academy.application.ListCoursesRequest;
import com.factech.nexus.modules.academy.application.RegisterCourseRequest;
import com.factech.nexus.modules.academy.application.UpdateCourseRequest;
import com.factech.nexus.modules.academy.domain.service.ChangeCourseStatusService;
import com.factech.nexus.modules.academy.domain.service.ClassifyCourseService;
import com.factech.nexus.modules.academy.domain.service.DeclassifyCourseService;
import com.factech.nexus.modules.academy.domain.service.DeleteCourseService;
import com.factech.nexus.modules.academy.domain.service.GetCourseService;
import com.factech.nexus.modules.academy.domain.service.GrantCourseMembershipService;
import com.factech.nexus.modules.academy.domain.service.GrantCourseProductService;
import com.factech.nexus.modules.academy.domain.service.ListCoursesService;
import com.factech.nexus.modules.academy.domain.service.RegisterCourseService;
import com.factech.nexus.modules.academy.domain.service.RevokeCourseMembershipService;
import com.factech.nexus.modules.academy.domain.service.RevokeCourseProductService;
import com.factech.nexus.modules.academy.domain.service.UpdateCourseService;
import com.factech.nexus.modules.academy.domain.service.UploadCourseCoverService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Los cursos (`AC`, `RF-AC-008` a `RF-AC-013`): las seis operaciones de administración del curso en
 * sí, y su clasificación en categorías (`RF-AC-016`, `RF-AC-017`). Las otras dos relaciones y la
 * portada llegan con los bloques 4 y 5 de `ac.md` §6.1, y el aula con el 6, bajo {@code
 * /courses/available}; los módulos y lecciones tienen su controlador.
 *
 * <p><b>El alta, el detalle y las escrituras devuelven la misma forma</b> —el curso con sus
 * relaciones, su árbol y por qué no se ofrece— para que el frontend tenga una sola pantalla de
 * curso. <b>Los {@code course-categories:} no habilitan aquí ni una operación.</b>
 */
@RestController
@RequestMapping("/api/v1/courses")
@Tag(
    name = "Cursos",
    description = "Lo que la membresía o el servicio abren: los cursos de la academia.")
public class CourseController {

  private final RegisterCourseService alta;
  private final ListCoursesService listado;
  private final GetCourseService detalle;
  private final UpdateCourseService correccion;
  private final ChangeCourseStatusService estado;
  private final DeleteCourseService retiro;
  private final ClassifyCourseService clasificacion;
  private final DeclassifyCourseService desclasificacion;
  private final GrantCourseProductService servicio;
  private final RevokeCourseProductService quitaServicio;
  private final UploadCourseCoverService portada;
  private final GrantCourseMembershipService membresia;
  private final RevokeCourseMembershipService quitaMembresia;

  public CourseController(
      RegisterCourseService alta,
      ListCoursesService listado,
      GetCourseService detalle,
      UpdateCourseService correccion,
      ChangeCourseStatusService estado,
      DeleteCourseService retiro,
      ClassifyCourseService clasificacion,
      DeclassifyCourseService desclasificacion,
      GrantCourseProductService servicio,
      RevokeCourseProductService quitaServicio,
      UploadCourseCoverService portada,
      GrantCourseMembershipService membresia,
      RevokeCourseMembershipService quitaMembresia) {
    this.membresia = membresia;
    this.quitaMembresia = quitaMembresia;
    this.portada = portada;
    this.servicio = servicio;
    this.quitaServicio = quitaServicio;
    this.alta = alta;
    this.listado = listado;
    this.detalle = detalle;
    this.correccion = correccion;
    this.estado = estado;
    this.retiro = retiro;
    this.clasificacion = clasificacion;
    this.desclasificacion = desclasificacion;
  }

  @PostMapping
  @PreAuthorize("hasAuthority('courses:create')")
  @Operation(
      summary = "Registrar un curso",
      description =
          """
          Registra un curso con **título, instructor, dificultad y orden**, obligatorios,
          y descripción corta, descripción larga y video de introducción, opcionales. Y
          **quién lo puede ver y dónde se encuentra**, también opcionales:

          - **`categoryIds`** — las categorías en que nace, cada una **viva**.
          - **`productIds`** — los **servicios** que lo abren: productos de tipo
            **`BOT`**, no retirados (un servicio inactivo se admite).
          - **`membershipIds`** — las **membresías** que lo abren. Es una lista, no un
            nivel mínimo: dar `ORO` no lo abre a `PLATINO`.

          **Cada lista es todo o nada**: si algo no sirve responde `422` —`EX-004`
          categorías, `EX-005` servicios (inexistente, retirado o que no es `BOT`),
          `EX-006` membresías— **nombrando todo lo que falla**, y **no se crea nada**.
          Repetidos o nulos en cualquiera, `400` (`VAL-008`). Ausente o vacía es
          «ninguno». Cada fila deja su auditoría, como darla después con su operación.

          **Nace `INACTIVO`**, sin recomendaciones, módulos ni portada —**la portada,
          por `PUT /api/v1/courses/{id}/cover` justo después**—, y **sin código**: un
          curso no se teclea en ninguna venta.

          **El instructor es una persona del sistema que existe, no está retirada y
          porta `courses:teach`**, comprobado **al asignar y solo al asignar**: que lo
          pierda después no toca el curso. Un instructor inexistente o retirado y uno
          sin el permiso son dos `422` distintos, porque se arreglan en sitios
          distintos. **`instructor.fullName` es el nombre actual** de la persona, no
          una copia.

          El título es único entre los cursos **vivos**, sin distinguir mayúsculas ni
          acentos; un retirado libera el suyo. Las descripciones de solo espacios se
          guardan nulas; el video **solo puede ser de YouTube o de Vimeo**, se comprueba
          en su forma y **no se consulta**. Enviar
          `status`, `categories`, `memberships`, `modules`, `coverImageUrl` o `code`
          responde `400`.

          La respuesta es la forma del detalle: `categories` con las pedidas en su
          orden, las demás listas vacías, `coverImageUrl` presente y nulo, cero segundos y cero lecciones, y `offerable: false` con
          `offerableReason` «inactivo». Exige `courses:create`; **los
          `course-categories:` no habilitan**.
          """)
  @ApiResponses({
    @ApiResponse(
        responseCode = "201",
        description = "Curso registrado, inactivo y vacío.",
        content = @Content(schema = @Schema(implementation = CourseDetailResponse.class))),
    @ApiResponse(
        responseCode = "400",
        description =
            "Datos inválidos, juntos (`VAL-001` a `VAL-006`, y un identificador nulo en"
                + " cualquiera de las tres listas en `VAL-008`); un campo no admitido"
                + " (`VAL-007`); o identificadores repetidos en una lista (`VAL-008`)"),
    @ApiResponse(responseCode = "401", description = "Token ausente o inválido (`AUTH-001`)"),
    @ApiResponse(
        responseCode = "403",
        description = "Autenticado sin el permiso `courses:create` (`AUTH-002`)"),
    @ApiResponse(
        responseCode = "409",
        description = "Título ya en uso por un curso vivo (`EX-001`)"),
    @ApiResponse(
        responseCode = "422",
        description =
            "El instructor no existe o está retirado (`EX-002`), no porta `courses:teach`"
                + " (`EX-003`); o, nombrando todo lo que falla, categorías que no existen o"
                + " están retiradas (`EX-004`), productos que no son servicios vivos (`EX-005`)"
                + " o membresías que no existen (`EX-006`)")
  })
  public ResponseEntity<CourseDetailResponse> register(
      @Valid @RequestBody RegisterCourseRequest peticion) {
    CourseDetailResponse creado = alta.register(peticion);
    return ResponseEntity.created(URI.create("/api/v1/courses/" + creado.id())).body(creado);
  }

  @GetMapping
  @PreAuthorize("hasAuthority('courses:read')")
  @Operation(
      summary = "Consultar los cursos",
      description =
          """
          La lista de administración de los cursos, paginada, **en el orden en que se
          enseñan**: por `displayOrder` ascendente, con el más antiguo primero entre
          dos que compartan número. Cada fila trae el instructor resuelto —con su
          nombre **actual**—, `coverImageUrl`, sus categorías vivas, **`offerable`** y
          cuántos módulos y lecciones **vivos** tiene — vivos y no ofrecidos—. Sin
          descripción larga, video ni árbol: para eso está el detalle.

          Filtra por `q` —título, sin distinguir mayúsculas ni acentos—, `categoryId`,
          `instructorId`, `difficulty` y `status`, combinables. `categoryId` deja los
          clasificados en esa categoría **si está viva**: filtrar por una retirada
          devuelve vacío, porque ninguna fila la enseña en `categories`. **Excluye los
          retirados** salvo `includeDeleted=true`, y entonces los trae con `deletedAt`
          y `offerable: false`. «Ofrecible» no es filtro: se publica por fila.

          Orden: `displayOrder` (omisión, ascendente), `title` y `createdAt`
          (descendente por omisión), con `,asc`/`,desc`. Los parámetros inválidos se
          devuelven **juntos** con `400`. Exige `courses:read`;
          `course-categories:read` no habilita.
          """)
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "La página de cursos.",
        content = @Content(schema = @Schema(implementation = CoursePageResponse.class))),
    @ApiResponse(
        responseCode = "400",
        description = "Orden, paginación o filtros inválidos, juntos (`VAL-001` a `VAL-003`)"),
    @ApiResponse(responseCode = "401", description = "Token ausente o inválido (`AUTH-001`)"),
    @ApiResponse(
        responseCode = "403",
        description = "Autenticado sin el permiso `courses:read` (`AUTH-002`)")
  })
  public CoursePageResponse listar(
      @org.springdoc.core.annotations.ParameterObject @ModelAttribute ListCoursesRequest filtros) {
    return listado.list(filtros);
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasAuthority('courses:read')")
  @Operation(
      summary = "Consultar el detalle de un curso",
      description =
          """
          El curso entero: lo suyo, el instructor resuelto, `coverImageUrl`, sus
          **categorías** vivas, los **cursos que recomienda** —con su estado y si se
          ofrecen—, las **membresías** y los **servicios** que lo abren —estos en
          `products`, con código y nombre, también el que productos retiró después—, y
          **sus módulos en orden con sus
          lecciones en orden**, vivos y retirados marcados, cada módulo con `offerable`
          y cada lección con tipo, duración, estado y `open` — **sin el contenido**, que
          se pide por lección. La duración total y la cuenta de lecciones se suman sobre
          lo vivo.

          **`offerable` y `offerableReason` viajan siempre**, con el primer motivo que
          falla en su orden: retirado, inactivo, sin descripción, **sin membresías ni
          servicios** —basta uno de los dos—, sin módulo activo con lección activa con
          contenido. Es la vista con la que se
          arma el curso y se ve qué le falta para publicarse.

          **Se devuelve también un retirado**, con `deletedAt` y `deletionReason`
          —leído de la auditoría— y su árbol marcado: es la lectura de administración
          y devuelve todo.
          """)
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "El curso con sus relaciones y su árbol.",
        content = @Content(schema = @Schema(implementation = CourseDetailResponse.class))),
    @ApiResponse(responseCode = "400", description = "Identificador inválido (`VAL-001`)"),
    @ApiResponse(responseCode = "401", description = "Token ausente o inválido (`AUTH-001`)"),
    @ApiResponse(
        responseCode = "403",
        description = "Autenticado sin el permiso `courses:read` (`AUTH-002`)"),
    @ApiResponse(responseCode = "404", description = "El curso no existe (`EX-001`)")
  })
  public CourseDetailResponse detalle(@PathVariable UUID id) {
    return detalle.detail(id);
  }

  @PatchMapping("/{id}")
  @PreAuthorize("hasAuthority('courses:update')")
  @Operation(
      summary = "Editar un curso",
      description =
          """
          Corrección **parcial**: solo lo que viene cambia. Admite `title`,
          `instructorId`, `difficulty`, `shortDescription`, `longDescription`,
          `introVideoUrl` y `displayOrder`, por separado o juntos, y exige al menos
          uno; el video, **solo de YouTube o de Vimeo**. **Ausente y nulo no significan lo mismo**: ausente es «no lo toques»,
          nulo es «vacíalo» — y **solo las descripciones y el video admiten
          vaciarse**; el nulo en los otros cuatro responde `400`, junto con los demás
          errores de forma.

          **Sin inmutables**. **Reasignar el instructor repite la comprobación del
          alta** sobre el nuevo: existe, no está retirado y porta `courses:teach`; al
          mismo no se comprueba nada y nada cambia. **Vaciar una descripción no
          desactiva**: un curso activo sigue activo y **deja de ofrecerse** —el detalle
          dice «sin descripción»— hasta que alguien la reponga. El estado, la portada,
          las relaciones y los módulos tienen sus propios endpoints, y en el cuerpo
          responden `400`.

          El título nuevo no puede ser el de **otro** curso vivo. Un retirado no se
          corrige (`404`). Un cuerpo sin cambios de valor responde `200` sin avanzar
          `updatedAt` ni auditar. Exige `courses:update`.
          """)
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "El curso corregido, en la forma del detalle.",
        content = @Content(schema = @Schema(implementation = CourseDetailResponse.class))),
    @ApiResponse(
        responseCode = "400",
        description =
            "Identificador inválido, campos que no admiten vaciarse, cuerpo vacío o campo no"
                + " admitido (`VAL-001` a `VAL-008`)"),
    @ApiResponse(responseCode = "401", description = "Token ausente o inválido (`AUTH-001`)"),
    @ApiResponse(
        responseCode = "403",
        description = "Autenticado sin el permiso `courses:update` (`AUTH-002`)"),
    @ApiResponse(
        responseCode = "404",
        description = "El curso no existe o está retirado (`EX-002`)"),
    @ApiResponse(
        responseCode = "409",
        description = "Título ya en uso por otro curso vivo (`EX-001`)"),
    @ApiResponse(
        responseCode = "422",
        description =
            "El instructor nuevo no existe o está retirado (`EX-003`), o no porta"
                + " `courses:teach` (`EX-004`)")
  })
  public CourseDetailResponse corregir(
      @PathVariable UUID id, @Valid @RequestBody UpdateCourseRequest peticion) {
    return correccion.update(id, peticion);
  }

  @PatchMapping("/{id}/status")
  @PreAuthorize("hasAuthority('courses:update')")
  @Operation(
      summary = "Cambiar el estado de un curso",
      description =
          """
          `ACTIVO` publica; `INACTIVO` despublica. **Activar exige las dos
          descripciones y al menos un módulo activo**, comprobado **junto**: si faltan
          varias cosas, la respuesta trae todos los motivos. **No exige una
          membresía ni un servicio**: un curso activo sin ninguno existe y no se
          ofrece, y el detalle lo dice. **Desactivar no exige nada** y no toca módulos ni relaciones.

          El estado es lo que alguien decidió: **vaciar una descripción o retirar el
          último módulo activo después no lo cambia** — el curso deja de ofrecerse, y
          `offerableReason` dice por qué. Pedir el estado que ya tiene responde `200`
          sin escribir ni auditar. Un retirado responde `404`. Exige `courses:update`.
          """)
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "El curso con su estado, en la forma del detalle.",
        content = @Content(schema = @Schema(implementation = CourseDetailResponse.class))),
    @ApiResponse(
        responseCode = "400",
        description =
            "Identificador inválido o estado ausente o fuera de dominio (`VAL-001`, `VAL-002`)"),
    @ApiResponse(responseCode = "401", description = "Token ausente o inválido (`AUTH-001`)"),
    @ApiResponse(
        responseCode = "403",
        description = "Autenticado sin el permiso `courses:update` (`AUTH-002`)"),
    @ApiResponse(
        responseCode = "404",
        description = "El curso no existe o está retirado (`EX-001`)"),
    @ApiResponse(
        responseCode = "409",
        description =
            "No puede activarse: sin descripción corta (`EX-002`), sin descripción larga"
                + " (`EX-003`) o sin módulo activo (`EX-004`), juntos")
  })
  public CourseDetailResponse cambiarEstado(
      @PathVariable UUID id, @Valid @RequestBody ChangeCourseStatusRequest peticion) {
    return estado.change(id, peticion);
  }

  @PostMapping("/{id}/deletion")
  @PreAuthorize("hasAuthority('courses:delete')")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(
      summary = "Retirar un curso",
      description =
          """
          Baja **lógica y con motivo**: la fila se queda con `deletedAt` puesto y nada
          más cambiado —ni el estado—, y el motivo y la instantánea —con los
          identificadores de sus categorías, membresías, servicios, recomendados y
          módulos— van a
          la auditoría de eliminación. **`POST` y no `DELETE`** porque el cuerpo lleva
          el motivo, que es obligatorio.

          **Retirar un curso arrastra sus módulos y lecciones vivos**, con el mismo
          instante y el mismo motivo, y **una fila de auditoría por cada uno**. Sus
          clasificaciones, recomendaciones y visibilidades **se conservan** y dejan de
          verse; **deja de aparecer como recomendado** en el aula. Un curso que se
          ofrece se retira igual y sale del aula en el acto.

          El título del retirado **queda libre**. Sale del listado salvo
          `includeDeleted=true`, y su detalle trae el motivo y el árbol marcado.
          Retirar uno ya retirado responde `409`, distinto del `404` del inexistente.
          Exige `courses:delete`.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "204", description = "Curso retirado, con su árbol."),
    @ApiResponse(
        responseCode = "400",
        description =
            "Identificador inválido o motivo ausente, vacío o largo (`VAL-001` a `VAL-003`)"),
    @ApiResponse(responseCode = "401", description = "Token ausente o inválido (`AUTH-001`)"),
    @ApiResponse(
        responseCode = "403",
        description = "Autenticado sin el permiso `courses:delete` (`AUTH-002`)"),
    @ApiResponse(responseCode = "404", description = "El curso no existe (`EX-001`)"),
    @ApiResponse(responseCode = "409", description = "El curso ya está retirado (`EX-002`)")
  })
  public void retirar(
      @PathVariable UUID id, @RequestBody(required = false) DeleteCourseRequest peticion) {
    retiro.delete(id, peticion);
  }

  @PostMapping("/{courseId}/categories")
  @PreAuthorize("hasAuthority('courses:update')")
  @Operation(
      summary = "Clasificar un curso en una categoría",
      description =
          """
          Pone el curso en una categoría. **La clasificación es libre y no se repite**:
          un curso está en cuantas categorías haga falta —**una por petición**; para
          ponerlo en tres, tres peticiones— y la misma pareja no entra dos veces.

          **No toca el estado ni la ofrecibilidad**: un curso `INACTIVO` se clasifica
          igual, y un curso sin categoría se ofrece igual — la categoría es un filtro
          del catálogo, no una condición.

          El curso tiene que estar **vivo** (`404` si no existe o está retirado, porque
          va en la ruta) y la categoría también (`422` si no existe o está retirada,
          porque va en el cuerpo). La pareja repetida responde `409` **nombrando la
          categoría**. Enviar un campo distinto de `categoryId` responde `400`.

          La respuesta es el curso en la forma del detalle, con la nueva en
          `categories`. Exige `courses:update`; **los `course-categories:` no
          habilitan**.
          """)
  @ApiResponses({
    @ApiResponse(
        responseCode = "201",
        description = "Curso clasificado, en la forma del detalle.",
        content = @Content(schema = @Schema(implementation = CourseDetailResponse.class))),
    @ApiResponse(
        responseCode = "400",
        description =
            "Identificador inválido (`VAL-001`), `categoryId` ausente o mal formado (`VAL-002`),"
                + " o un campo no admitido (`VAL-003`)"),
    @ApiResponse(responseCode = "401", description = "Token ausente o inválido (`AUTH-001`)"),
    @ApiResponse(
        responseCode = "403",
        description = "Autenticado sin el permiso `courses:update` (`AUTH-002`)"),
    @ApiResponse(
        responseCode = "404",
        description = "El curso no existe o está retirado (`EX-001`)"),
    @ApiResponse(
        responseCode = "409",
        description = "El curso ya está en esa categoría; el mensaje la nombra (`EX-003`)"),
    @ApiResponse(
        responseCode = "422",
        description = "La categoría no existe o está retirada (`EX-002`)")
  })
  public ResponseEntity<CourseDetailResponse> clasificar(
      @PathVariable UUID courseId, @Valid @RequestBody ClassifyCourseRequest peticion) {
    CourseDetailResponse curso = clasificacion.classify(courseId, peticion);
    return ResponseEntity.created(URI.create("/api/v1/courses/" + curso.id())).body(curso);
  }

  @DeleteMapping("/{courseId}/categories/{categoryId}")
  @PreAuthorize("hasAuthority('courses:update')")
  @Operation(
      summary = "Desclasificar un curso de una categoría",
      description =
          """
          Saca el curso de la categoría: **borra la pareja, sin motivo**, y lo registra
          como eliminación de una asociación. Sin cuerpo.

          **No toca el estado ni la ofrecibilidad**: el curso que se ofrecía se sigue
          ofreciendo aunque se quede sin categoría. **Una categoría retirada se
          desclasifica igual**: la pareja existe, y así se limpia lo que el retiro
          dejó.

          El curso tiene que estar **vivo**. Los dos `404` se distinguen por el
          mensaje: el curso no existe o está retirado, o el curso no está en esa
          categoría. La respuesta es el curso en la forma del detalle, sin la
          categoría. Exige `courses:update`.
          """)
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Curso desclasificado, en la forma del detalle.",
        content = @Content(schema = @Schema(implementation = CourseDetailResponse.class))),
    @ApiResponse(responseCode = "400", description = "Identificador inválido (`VAL-001`)"),
    @ApiResponse(responseCode = "401", description = "Token ausente o inválido (`AUTH-001`)"),
    @ApiResponse(
        responseCode = "403",
        description = "Autenticado sin el permiso `courses:update` (`AUTH-002`)"),
    @ApiResponse(
        responseCode = "404",
        description =
            "El curso no existe o está retirado (`EX-001`), o no está en esa categoría"
                + " (`EX-002`)")
  })
  public CourseDetailResponse desclasificar(
      @PathVariable UUID courseId, @PathVariable UUID categoryId) {
    return desclasificacion.declassify(courseId, categoryId);
  }

  @PostMapping("/{courseId}/products")
  @PreAuthorize("hasAuthority('courses:update')")
  @Operation(
      summary = "Dar visibilidad de un curso a un servicio",
      description =
          """
          Declara que **un servicio abre el curso**: quien tenga **vigente** ese
          producto podrá estudiarlo. **Se suma a las membresías**: el curso se abre con
          una de sus membresías **o** uno de sus servicios. Una pareja por petición.

          **Solo abre un curso un producto de tipo `BOT`** —el servicio—. El producto
          tiene que existir y **no estar retirado**; **un servicio `INACTIVO` se añade
          igual**, para armar el curso antes de ponerlo a la venta. Un producto
          inexistente o retirado (`EX-002`) y un upgrade de membresía (`EX-003`) son
          dos `422` distintos. La pareja repetida responde `409` **nombrando el
          servicio por su código**. El curso tiene que estar vivo, en cualquier estado.

          **Retirar después el servicio en productos no lo quita de aquí**: quien lo
          compró lo tiene hasta que venza. Con su primer servicio, un curso activo, con
          descripciones y un módulo ofrecible **se ofrece aunque no tenga membresías**.
          Que un alumno lo estudie por su servicio lo decide el aula.

          La respuesta es el curso en la forma del detalle, con el servicio en
          `products` —identificador, código, nombre—. Exige `courses:update`; **los
          permisos de productos no habilitan**.
          """)
  @ApiResponses({
    @ApiResponse(
        responseCode = "201",
        description = "El servicio abre el curso; el curso en la forma del detalle.",
        content = @Content(schema = @Schema(implementation = CourseDetailResponse.class))),
    @ApiResponse(
        responseCode = "400",
        description =
            "Identificador inválido (`VAL-001`), `productId` ausente o mal formado (`VAL-002`),"
                + " o un campo no admitido (`VAL-003`)"),
    @ApiResponse(responseCode = "401", description = "Token ausente o inválido (`AUTH-001`)"),
    @ApiResponse(
        responseCode = "403",
        description = "Autenticado sin el permiso `courses:update` (`AUTH-002`)"),
    @ApiResponse(
        responseCode = "404",
        description = "El curso no existe o está retirado (`EX-001`)"),
    @ApiResponse(
        responseCode = "409",
        description = "Ese servicio ya abre el curso; el mensaje lo nombra (`EX-004`)"),
    @ApiResponse(
        responseCode = "422",
        description =
            "El producto no existe o está retirado (`EX-002`), o no es un servicio (`EX-003`)")
  })
  public ResponseEntity<CourseDetailResponse> darServicio(
      @PathVariable UUID courseId, @Valid @RequestBody GrantCourseProductRequest peticion) {
    CourseDetailResponse curso = servicio.grant(courseId, peticion);
    return ResponseEntity.created(URI.create("/api/v1/courses/" + curso.id())).body(curso);
  }

  @DeleteMapping("/{courseId}/products/{productId}")
  @PreAuthorize("hasAuthority('courses:update')")
  @Operation(
      summary = "Quitar la visibilidad de un curso a un servicio",
      description =
          """
          Deja de abrir el curso con ese servicio: **borra la pareja, sin motivo**, y lo
          registra como eliminación de una asociación. Sin cuerpo.

          **Quitar el último nunca se rechaza**: si el curso tampoco tiene membresías,
          deja de ofrecerse y el detalle lo dice. **Un servicio retirado en productos
          se quita igual.** Los dos `404` se distinguen por el mensaje: el curso no
          existe o está retirado, o ese servicio no abre el curso. La respuesta es el
          curso en la forma del detalle. Exige `courses:update`.
          """)
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "El servicio ya no abre el curso; el curso en la forma del detalle.",
        content = @Content(schema = @Schema(implementation = CourseDetailResponse.class))),
    @ApiResponse(responseCode = "400", description = "Identificador inválido (`VAL-001`)"),
    @ApiResponse(responseCode = "401", description = "Token ausente o inválido (`AUTH-001`)"),
    @ApiResponse(
        responseCode = "403",
        description = "Autenticado sin el permiso `courses:update` (`AUTH-002`)"),
    @ApiResponse(
        responseCode = "404",
        description =
            "El curso no existe o está retirado (`EX-001`), o ese servicio no lo abre (`EX-002`)")
  })
  public CourseDetailResponse quitarServicio(
      @PathVariable UUID courseId, @PathVariable UUID productId) {
    return quitaServicio.revoke(courseId, productId);
  }

  @PostMapping("/{courseId}/memberships")
  @PreAuthorize("hasAuthority('courses:update')")
  @Operation(
      summary = "Dar visibilidad de un curso a una membresía",
      description =
          """
          Declara que **una membresía abre el curso**: quien la tenga **vigente** podrá
          estudiarlo. **Es una lista, no un nivel mínimo**: dar `ORO` no lo abre a
          `PLATINO`; «este nivel y los de arriba» se añaden uno a uno. **Se suma a los
          servicios**: el curso se abre con una de sus membresías **o** uno de sus
          servicios. Una pareja por petición.

          La membresía tiene que existir (`422` `EX-002` si no); la pareja repetida
          responde `409` **nombrando la membresía por su código**. El curso tiene que
          estar vivo, en cualquier estado: la lista se arma antes de publicar. Con su
          primera llave, un curso activo, con descripciones y un módulo ofrecible **se
          ofrece**. Que un alumno lo estudie lo decide el aula.

          La respuesta es el curso en la forma del detalle, con la membresía en
          `memberships` —identificador, código, nombre, color—, en el orden de la cadena.
          Exige `courses:update`.
          """)
  @ApiResponses({
    @ApiResponse(
        responseCode = "201",
        description = "La membresía abre el curso; el curso en la forma del detalle.",
        content = @Content(schema = @Schema(implementation = CourseDetailResponse.class))),
    @ApiResponse(
        responseCode = "400",
        description =
            "Identificador inválido (`VAL-001`), `membershipId` ausente o mal formado"
                + " (`VAL-002`), o un campo no admitido (`VAL-003`)"),
    @ApiResponse(responseCode = "401", description = "Token ausente o inválido (`AUTH-001`)"),
    @ApiResponse(
        responseCode = "403",
        description = "Autenticado sin el permiso `courses:update` (`AUTH-002`)"),
    @ApiResponse(
        responseCode = "404",
        description = "El curso no existe o está retirado (`EX-001`)"),
    @ApiResponse(
        responseCode = "409",
        description = "Esa membresía ya abre el curso; el mensaje la nombra (`EX-003`)"),
    @ApiResponse(responseCode = "422", description = "La membresía no existe (`EX-002`)")
  })
  public ResponseEntity<CourseDetailResponse> darMembresia(
      @PathVariable UUID courseId, @Valid @RequestBody GrantCourseMembershipRequest peticion) {
    CourseDetailResponse curso = membresia.grant(courseId, peticion);
    return ResponseEntity.created(URI.create("/api/v1/courses/" + curso.id())).body(curso);
  }

  @DeleteMapping("/{courseId}/memberships/{membershipId}")
  @PreAuthorize("hasAuthority('courses:update')")
  @Operation(
      summary = "Quitar la visibilidad de un curso a una membresía",
      description =
          """
          Deja de abrir el curso con esa membresía: **borra la pareja, sin motivo**, y lo
          registra como eliminación de una asociación. Sin cuerpo.

          **Quitar la última nunca se rechaza**: si el curso tampoco tiene servicios,
          deja de ofrecerse y el detalle lo dice —es la forma de retirar un curso de la
          vista de todos sin desactivarlo—. Los dos `404` se distinguen por el mensaje:
          el curso no existe o está retirado, o esa membresía no lo abre. La respuesta
          es el curso en la forma del detalle. Exige `courses:update`.
          """)
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "La membresía ya no abre el curso; el curso en la forma del detalle.",
        content = @Content(schema = @Schema(implementation = CourseDetailResponse.class))),
    @ApiResponse(responseCode = "400", description = "Identificador inválido (`VAL-001`)"),
    @ApiResponse(responseCode = "401", description = "Token ausente o inválido (`AUTH-001`)"),
    @ApiResponse(
        responseCode = "403",
        description = "Autenticado sin el permiso `courses:update` (`AUTH-002`)"),
    @ApiResponse(
        responseCode = "404",
        description =
            "El curso no existe o está retirado (`EX-001`), o esa membresía no lo abre"
                + " (`EX-002`)")
  })
  public CourseDetailResponse quitarMembresia(
      @PathVariable UUID courseId, @PathVariable UUID membershipId) {
    return quitaMembresia.revoke(courseId, membershipId);
  }

  @PutMapping(value = "/{id}/cover", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @PreAuthorize("hasAuthority('courses:update')")
  @Operation(
      summary = "Subir o reemplazar la portada de un curso",
      description =
          """
          Recibe **un archivo** —`multipart/form-data`, una sola parte llamada
          `file`— y lo convierte en la portada del curso, **tal cual**. **El alta
          del curso es JSON y no admite la imagen**: se crea el curso y se sube su
          portada con esta operación justo después.

          **El tipo lo deciden los bytes, no la cabecera**: `JPEG`, `PNG` o `WebP`,
          reconocidos por su firma. **Hasta 5 MB** (`VAL-004`); sin archivo o vacío,
          `VAL-002`; otro formato, `VAL-003`. Los tres nombran `file`, y son **los
          mismos de la portada de un producto**.

          **Si ya tenía portada, la reemplaza**: la nueva estrena identificador, **la
          anterior se borra** y `coverImageUrl` cambia de dirección —
          `/api/v1/academy-images/{imageId}`, pública y con caché inmutable—, en el
          detalle y en el listado. **En cualquier estado**: un curso `INACTIVO`, sin
          módulos y sin membresías recibe su portada igual. Un curso retirado
          responde `404`. Exige `courses:update`; **`course-categories:update` no
          habilita**.
          """)
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "El curso, con `coverImageUrl` señalando la imagen nueva.",
        content = @Content(schema = @Schema(implementation = CourseDetailResponse.class))),
    @ApiResponse(
        responseCode = "400",
        description =
            "Identificador inválido (`VAL-001`), sin archivo o vacío (`VAL-002`), ni JPEG ni PNG"
                + " ni WebP por sus bytes (`VAL-003`), más de 5 MB (`VAL-004`) o petición que no"
                + " es `multipart/form-data` (`EX-002`)"),
    @ApiResponse(responseCode = "401", description = "Token ausente o inválido (`AUTH-001`)"),
    @ApiResponse(
        responseCode = "403",
        description = "Autenticado sin el permiso `courses:update` (`AUTH-002`)"),
    @ApiResponse(
        responseCode = "404",
        description = "El curso no existe o está retirado (`EX-001`)")
  })
  public CourseDetailResponse subirPortada(
      @PathVariable UUID id, @RequestPart(value = "file", required = false) MultipartFile file) {
    return portada.upload(id, CoverPart.bytesDe(file));
  }
}
