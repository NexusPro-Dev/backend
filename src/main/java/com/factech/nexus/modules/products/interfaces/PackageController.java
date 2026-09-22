package com.factech.nexus.modules.products.interfaces;

import com.factech.nexus.modules.products.application.AssociatePackageProductRequest;
import com.factech.nexus.modules.products.application.ChangePackageStatusRequest;
import com.factech.nexus.modules.products.application.CorrectPackageDiscountRequest;
import com.factech.nexus.modules.products.application.DeletePackageRequest;
import com.factech.nexus.modules.products.application.ListPackagesRequest;
import com.factech.nexus.modules.products.application.PackageDetailResponse;
import com.factech.nexus.modules.products.application.PackagePageResponse;
import com.factech.nexus.modules.products.application.RegisterPackageRequest;
import com.factech.nexus.modules.products.application.UpdatePackageRequest;
import com.factech.nexus.modules.products.domain.service.AssociatePackageProductService;
import com.factech.nexus.modules.products.domain.service.ChangePackageStatusService;
import com.factech.nexus.modules.products.domain.service.CorrectPackageDiscountService;
import com.factech.nexus.modules.products.domain.service.DeletePackageService;
import com.factech.nexus.modules.products.domain.service.DissociatePackageProductService;
import com.factech.nexus.modules.products.domain.service.GetPackageService;
import com.factech.nexus.modules.products.domain.service.ListPackagesService;
import com.factech.nexus.modules.products.domain.service.RegisterPackageService;
import com.factech.nexus.modules.products.domain.service.RemovePackageCoverService;
import com.factech.nexus.modules.products.domain.service.UpdatePackageService;
import com.factech.nexus.modules.products.domain.service.UploadPackageCoverService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.io.IOException;
import java.io.UncheckedIOException;
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
 * Los paquetes de productos (`PM`, `RF-PM-017` a `RF-PM-025`, `RF-PM-028` y `RF-PM-029`): las once
 * operaciones de administración. El hotlink público del paquete (`RF-PM-026`) vive en {@link
 * HotlinkController}, y la imagen de la portada la sirve {@link ProductImageController} — la misma
 * ruta que la del producto.
 *
 * <p><b>Un recurso propio con sus cuatro permisos</b> (`packages:`), por decisión del responsable
 * del proyecto: armar combos y tocar el catálogo son dos capacidades, y los {@code products:} no
 * habilitan aquí ni una operación.
 *
 * <p><b>Todas las operaciones devuelven el paquete entero con su cuenta hecha</b>, porque lo que
 * cambia al asociar, corregir o quitar un producto es su precio — y el precio no está en ninguna
 * columna (`RN-PM-036`).
 */
@RestController
@RequestMapping("/api/v1/packages")
@Tag(name = "Paquetes", description = "Paquetes de productos: varios productos con su descuento.")
public class PackageController {

  private final RegisterPackageService alta;
  private final ListPackagesService listado;
  private final GetPackageService detalle;
  private final UpdatePackageService correccion;
  private final ChangePackageStatusService estado;
  private final DeletePackageService retiro;
  private final AssociatePackageProductService asociacion;
  private final CorrectPackageDiscountService descuento;
  private final DissociatePackageProductService desasociacion;
  private final UploadPackageCoverService portada;
  private final RemovePackageCoverService quitarPortada;

  public PackageController(
      RegisterPackageService alta,
      ListPackagesService listado,
      GetPackageService detalle,
      UpdatePackageService correccion,
      ChangePackageStatusService estado,
      DeletePackageService retiro,
      AssociatePackageProductService asociacion,
      CorrectPackageDiscountService descuento,
      DissociatePackageProductService desasociacion,
      UploadPackageCoverService portada,
      RemovePackageCoverService quitarPortada) {
    this.alta = alta;
    this.listado = listado;
    this.detalle = detalle;
    this.correccion = correccion;
    this.estado = estado;
    this.retiro = retiro;
    this.asociacion = asociacion;
    this.descuento = descuento;
    this.desasociacion = desasociacion;
    this.portada = portada;
    this.quitarPortada = quitarPortada;
  }

  @PostMapping
  @PreAuthorize("hasAuthority('packages:create')")
  @Operation(
      summary = "Registrar un paquete",
      description =
          """
          Registra un paquete de productos, **vacío e inactivo**: los productos entran
          uno a uno después (`POST /packages/{id}/products`) y publicarlo es otra
          operación (`PATCH /packages/{id}/status`), que exige descripción y al menos
          dos productos.

          **El paquete no declara precio: se calcula.** Su `price` es la suma de sus
          productos con su descuento, `listPrice` la suma sin descuentos y `savings` la
          diferencia, todo con el precio de catálogo **de hoy** y redondeado por
          producto a los decimales de la moneda. No se guarda en ninguna columna: si un
          producto cambia de precio, el paquete cambia solo. Enviar `price` —o `products`,
          o `status`— responde `400`.

          **La moneda es obligatoria e inmutable**, y solo se le asocian productos en
          esa moneda: un paquete recién creado no tiene productos y aun así sabe en qué
          se expresa. `scope` dice en qué vistas se publica —`TIENDA`, `HOTLINK`, `AMBOS`
          o `NINGUNO`, que existe y se activa pero no se ofrece en ninguna— y es del
          paquete: el alcance de sus productos no filtra dentro de él.

          **Y declara su vigencia** (`RN-PM-047`, desde el 16-09-2026): `validFrom`,
          **obligatorio**, es el primer día en que se ofrece, y `validTo`, opcional, el
          **último** —nulo es indefinidamente; un fin anterior al inicio responde
          `400`—. **No hay regla contra el pasado**: un inicio futuro programa el
          paquete y un fin de ayer lo deja cerrado, que es raro pero no es un error.
          Fuera de esas fechas el paquete **se oculta** de la oferta y del hotlink
          y **no cambia de estado**; el detalle lo dice en `offerableReason`.

          La respuesta es la misma forma que el detalle: `items` vacío, los tres
          importes en cero, `exchange` nulo, `validFrom` y `validTo` tal como se
          declararon, y `offerable: false` con su motivo — «menos de dos productos».
          `offerable` y `offerableReason` viajan **siempre**.

          Exige `packages:create`. **Los `products:` no habilitan**: un actor con los
          cuatro permisos del catálogo y ninguno de paquetes recibe `403`.
          """)
  @ApiResponses({
    @ApiResponse(
        responseCode = "201",
        description = "Paquete registrado, vacío y en estado `INACTIVO`.",
        content = @Content(schema = @Schema(implementation = PackageDetailResponse.class))),
    @ApiResponse(
        responseCode = "400",
        description =
            "Datos inválidos, juntos (`VAL-001` a `VAL-004`), o un cuerpo con `price`, `products`"
                + " o `status` (`VAL-005`)"),
    @ApiResponse(responseCode = "401", description = "Token ausente o inválido (`AUTH-001`)"),
    @ApiResponse(
        responseCode = "403",
        description = "Autenticado sin el permiso `packages:create` (`AUTH-002`)"),
    @ApiResponse(
        responseCode = "409",
        description =
            "Código ya en uso —también por un paquete retirado— o nombre ya en uso por un paquete"
                + " vivo (`EX-001`, `EX-002`)"),
    @ApiResponse(responseCode = "422", description = "Moneda inexistente o inactiva (`EX-003`)")
  })
  public ResponseEntity<PackageDetailResponse> register(
      @Valid @RequestBody RegisterPackageRequest peticion) {
    PackageDetailResponse creado = alta.register(peticion);
    return ResponseEntity.created(URI.create("/api/v1/packages/" + creado.id())).body(creado);
  }

  @PostMapping("/{id}/products")
  @PreAuthorize("hasAuthority('packages:add-product')")
  @Operation(
      summary = "Asociar un producto al paquete",
      description =
          """
          Mete un producto en el paquete con su descuento, y devuelve **el paquete
          entero** con el producto nuevo, su `priceInPackage` y los totales rehechos.
          Es la operación que define al paquete: siete verificaciones en fila, cada
          una con su código.

          **Forma y valor van juntos y son obligatorios.** `PORCENTAJE` de `0` a
          `100` con hasta dos decimales; `FIJO` de `0` al precio del producto, con los
          decimales de la moneda del paquete. **El cero se admite**: es un producto que
          entra al paquete a su precio.

          **La cota se comprueba contra el precio de hoy, y nadie la vuelve a
          mirar.** Un fijo mayor que el precio del producto responde `409` (`EX-006`,
          nombrando el precio); sobre un producto **gratuito** solo se admite cero. Si
          después el precio del producto baja por debajo de su fijo, el producto
          cuenta **cero** dentro del paquete — nunca negativo — y nada avisa.

          **Qué significa cada `409`**: `EX-003` el producto existe pero está inactivo
          o retirado — se distingue del inexistente, que es `422` (`EX-002`), porque
          quien llama ve el catálogo entero—; `EX-004` el producto está en otra
          moneda, y el mensaje nombra las dos; `EX-005` el producto ya está en el
          paquete — corrija su descuento en lugar de asociarlo de nuevo—; `EX-006` el
          descuento deja al producto por debajo de cero; `EX-007` **el paquete ya
          tiene un upgrade** (`RN-PM-046`, desde el 16-09-2026): un paquete lleva **un
          solo `UPGRADE_MEMBRESIA`** —del origen y el destino que sea, y aunque hoy esté
          inactivo: el sitio lo ocupa la fila— y cuantos bots se quiera. El mensaje
          **nombra el código** del upgrade que ya está, y el sitio se libera al
          desasociarlo. Hasta el 16-09-2026 este código significaba «el upgrade no
          comparte origen con los ya dentro»; con uno solo no hay orígenes que
          comparar. Los bots entran sin mirar cuántos upgrades hay.

          Se asocia **a un paquete inactivo** —es el estado en el que un paquete se
          arma— y no a uno retirado (`404`). El producto no cambia: la fila es del
          paquete, y el mismo producto puede estar en otros paquetes con otro
          descuento. Exige `packages:add-product`.
          """)
  @ApiResponses({
    @ApiResponse(
        responseCode = "201",
        description = "Producto asociado; el paquete entero con su precio recalculado.",
        content = @Content(schema = @Schema(implementation = PackageDetailResponse.class))),
    @ApiResponse(
        responseCode = "400",
        description =
            "Identificador inválido, campos ausentes o forma del descuento inválida (`VAL-001` a"
                + " `VAL-005`)"),
    @ApiResponse(responseCode = "401", description = "Token ausente o inválido (`AUTH-001`)"),
    @ApiResponse(
        responseCode = "403",
        description = "Autenticado sin el permiso `packages:add-product` (`AUTH-002`)"),
    @ApiResponse(
        responseCode = "404",
        description = "El paquete no existe o está retirado (`EX-001`)"),
    @ApiResponse(
        responseCode = "409",
        description =
            "Producto inactivo o retirado, en otra moneda, ya en el paquete, descuento por encima"
                + " del precio, o el paquete ya tiene un upgrade (`EX-003` a `EX-007`)"),
    @ApiResponse(responseCode = "422", description = "El producto no existe (`EX-002`)")
  })
  public ResponseEntity<PackageDetailResponse> associate(
      @PathVariable UUID id, @Valid @RequestBody AssociatePackageProductRequest peticion) {
    PackageDetailResponse paquete = asociacion.associate(id, peticion);
    return ResponseEntity.created(URI.create("/api/v1/packages/" + id)).body(paquete);
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasAuthority('packages:read')")
  @Operation(
      summary = "Consultar el detalle de un paquete",
      description =
          """
          El paquete con sus productos y **la cuenta hecha**: cada producto con su
          descuento y su `priceInPackage`, y `listPrice`, `price` y `savings` que
          cuadran con las líneas. **El precio se calcula en cada lectura** con el
          precio de catálogo **de hoy** de cada producto: corregir el precio de un
          producto cambia el paquete sin tocarlo, y un fijo que hoy supera su precio
          cuenta **cero**, nunca negativo. `exchange` es la conversión de `price` a la
          moneda de casa; nula si el paquete ya está en ella o no hay tasa.

          Es la lectura de administración: **devuelve todo**. Un producto inactivo o
          retirado dentro del paquete **se devuelve igual**, con su `status` y su
          `deleted`, y **sigue sumando** — esta es la única pantalla desde la que se
          arregla. Y `purchasePrice` de cada producto viaja, presente y nulo cuando
          no se conoce; la oferta y el hotlink no lo llevan.

          `offerable` dice si el paquete se puede ofrecer hoy, y `offerableReason` el
          **primer** motivo que lo impide, en un orden fijo: menos de dos productos,
          sin descripción, inactivo, retirado, **fuera de su vigencia** —«todavía no
          está vigente: empieza el …» o «la vigencia terminó el …», con la fecha—, y
          un producto inactivo o retirado —**nombrado por su código**—. Nulo cuando
          es ofrecible. `validFrom` y `validTo` viajan siempre, el fin nulo cuando es
          indefinido, y **el día de fin cuenta entero**: un paquete que termina hoy
          se ofrece hoy. «Hoy» es el día en UTC.

          **Un paquete retirado no es un `404`**: se devuelve con `deletedAt` y
          `deletionReason`, como el producto. Exige `packages:read`.
          """)
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "El paquete con su cuenta hecha.",
        content = @Content(schema = @Schema(implementation = PackageDetailResponse.class))),
    @ApiResponse(responseCode = "400", description = "Identificador inválido (`VAL-001`)"),
    @ApiResponse(responseCode = "401", description = "Token ausente o inválido (`AUTH-001`)"),
    @ApiResponse(
        responseCode = "403",
        description = "Autenticado sin el permiso `packages:read` (`AUTH-002`)"),
    @ApiResponse(responseCode = "404", description = "El paquete no existe (`EX-001`)")
  })
  public PackageDetailResponse detalle(@PathVariable UUID id) {
    return detalle.detail(id);
  }

  @GetMapping
  @PreAuthorize("hasAuthority('packages:list')")
  @Operation(
      summary = "Consultar los paquetes",
      description =
          """
          La lista de administración de los paquetes, paginada, **con la cuenta hecha
          por fila**: cada paquete con `itemCount`, `listPrice`, `price`, `savings`,
          `exchange` y `offerable`, y sin sus líneas — para eso está el detalle. Los
          importes salen de la misma cuenta que el detalle y cuadran con él.

          Filtra por `status`, `scope`, `currencyId` y `q` —búsqueda por nombre, sin
          distinguir mayúsculas ni acentos—, y se combinan. **Excluye los retirados**
          salvo `includeDeleted=true`, y entonces los trae con `deletedAt`.
          **`offerable` no es filtro**: quien administra quiere ver precisamente los
          que no se ofrecen; se queda como columna, y el motivo lo da el detalle.
          **Tampoco lo es la vigencia**: cada fila trae `validFrom` y `validTo`
          (`RN-PM-047`), y un paquete que empieza mañana o terminó ayer sale con
          `offerable: false` y **sigue listado con su estado**, como uno con un
          producto inactivo — no hay proceso que lo desactive al vencer.

          Orden: `createdAt` (omisión, descendente), `name` y `price`, con
          `,asc`/`,desc`. **El orden por `price` ordena por el precio calculado**: el
          precio no está en ninguna columna, de modo que un paquete cuyo producto
          cambió de precio cambia de posición en la siguiente lectura. Se ordena con
          la suma sin redondear; la diferencia con el importe que viaja cabe en un
          céntimo por producto y puede alterar el orden entre paquetes casi iguales.

          Los filtros inválidos se devuelven **juntos** con `400`. Exige
          `packages:list`; `products:read` no habilita.
          """)
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "La página de paquetes.",
        content = @Content(schema = @Schema(implementation = PackagePageResponse.class))),
    @ApiResponse(
        responseCode = "400",
        description = "Filtros, orden o paginación inválidos, juntos (`VAL-001` a `VAL-005`)"),
    @ApiResponse(responseCode = "401", description = "Token ausente o inválido (`AUTH-001`)"),
    @ApiResponse(
        responseCode = "403",
        description = "Autenticado sin el permiso `packages:list` (`AUTH-002`)")
  })
  public PackagePageResponse listar(
      @org.springdoc.core.annotations.ParameterObject @ModelAttribute ListPackagesRequest filtros) {
    return listado.list(filtros);
  }

  @PatchMapping("/{id}")
  @PreAuthorize("hasAuthority('packages:update')")
  @Operation(
      summary = "Corregir un paquete",
      description =
          """
          Corrige **nombre, descripción, alcance y las dos fechas de vigencia**
          (`validFrom`, `validTo`), por separado o juntos, y devuelve el detalle.
          Cada campo puede venir ausente —no se toca—, con valor, o **presente y
          nulo**: el nulo vacía la descripción y el fin de vigencia —que vuelve a
          indefinido— y **se rechaza** en nombre, alcance e inicio de vigencia, que
          son obligatorios. **La pareja de fechas resultante se comprueba entera**:
          corregir solo el inicio a una fecha posterior al fin que ya había responde
          `400`; quien quiera mover las dos las manda juntas.

          **El código y la moneda no se pueden modificar**, y enviarlos responde `400`
          —se rechazan, no se ignoran—. La moneda es la unidad en la que se suma el
          paquete: cambiarla dejaría los descuentos fijos expresados en otra cosa.

          **Vaciar la descripción de un paquete activo se permite**: no lo desactiva,
          pero lo saca de la oferta hasta que vuelva a tenerla, y el detalle lo dice
          en `offerableReason`. **Cerrar la vigencia es lo mismo con otra fecha**
          (`RN-PM-047`): `validTo` en ayer —o en hoy, para que sea el último día—
          retira el paquete de la oferta y del hotlink **sin desactivarlo**, y vaciar
          el fin lo vuelve a abrir. Cambiar el alcance de `AMBOS` o `HOTLINK` a
          `TIENDA` hace que el hotlink del paquete deje de resolver; la oferta no
          cambia.

          Un cuerpo sin cambios de valor responde `200` sin avanzar `updatedAt` ni
          auditar. Exige `packages:update`.
          """)
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "El paquete corregido, con su cuenta hecha.",
        content = @Content(schema = @Schema(implementation = PackageDetailResponse.class))),
    @ApiResponse(
        responseCode = "400",
        description =
            "Identificador inválido, nombre vacío o de más de 150, alcance nulo o fuera de dominio,"
                + " cuerpo vacío, o `code`/`currencyId` presentes (`VAL-001` a `VAL-005`, `EX-003`)"),
    @ApiResponse(responseCode = "401", description = "Token ausente o inválido (`AUTH-001`)"),
    @ApiResponse(
        responseCode = "403",
        description = "Autenticado sin el permiso `packages:update` (`AUTH-002`)"),
    @ApiResponse(
        responseCode = "404",
        description = "El paquete no existe o está retirado (`EX-001`)"),
    @ApiResponse(
        responseCode = "409",
        description = "El nombre ya lo usa otro paquete vivo (`EX-002`)")
  })
  public PackageDetailResponse corregir(
      @PathVariable UUID id, @RequestBody UpdatePackageRequest peticion) {
    return correccion.update(id, peticion);
  }

  @PatchMapping("/{id}/status")
  @PreAuthorize("hasAuthority('packages:change-status')")
  @Operation(
      summary = "Activar o desactivar un paquete",
      description =
          """
          Cambia el estado del paquete y devuelve el detalle.

          **Activar exige descripción y al menos dos productos**, y los dos motivos
          van **juntos** en el mismo `409` cuando faltan a la vez. **No exige que los
          productos estén activos hoy**: eso lo mira la oferta cada vez, y un paquete
          activo con un producto inactivo dentro es un estado legítimo — el detalle lo
          devuelve `offerable: false` nombrando el producto.

          **Desactivar no tiene condiciones**: el paquete sale de la oferta y del
          hotlink, y sus productos no cambian.

          Pedir el estado que ya tiene responde `200` sin escribir ni auditar. Exige
          `packages:change-status`.
          """)
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "El paquete en su estado nuevo, con su cuenta hecha.",
        content = @Content(schema = @Schema(implementation = PackageDetailResponse.class))),
    @ApiResponse(
        responseCode = "400",
        description =
            "Identificador inválido o `status` ausente o fuera de dominio (`VAL-001`, `VAL-002`)"),
    @ApiResponse(responseCode = "401", description = "Token ausente o inválido (`AUTH-001`)"),
    @ApiResponse(
        responseCode = "403",
        description = "Autenticado sin el permiso `packages:change-status` (`AUTH-002`)"),
    @ApiResponse(
        responseCode = "404",
        description = "El paquete no existe o está retirado (`EX-001`)"),
    @ApiResponse(
        responseCode = "409",
        description =
            "Activar sin descripción o con menos de dos productos (`EX-002`, `EX-003`), juntos")
  })
  public PackageDetailResponse cambiarEstado(
      @PathVariable UUID id, @Valid @RequestBody ChangePackageStatusRequest peticion) {
    return estado.change(id, peticion);
  }

  @PostMapping("/{id}/deletion")
  @PreAuthorize("hasAuthority('packages:delete')")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(
      summary = "Retirar un paquete",
      description =
          """
          Retira el paquete con **eliminación lógica y motivo obligatorio** (Art. V.13):
          la fila permanece con su `status` y **sus filas de asociación**, y el detalle
          sigue devolviéndolo con `deletedAt` y `deletionReason`. Sale de la oferta y
          del hotlink en el acto; **sus productos no cambian** — eran suyos antes del
          paquete y lo siguen siendo después, en el catálogo y en los demás paquetes.

          El motivo se valida **antes de cualquier consulta**. El paquete inexistente
          es `404` y el **ya retirado es `409`**, y se distinguen a propósito: el
          catálogo devuelve los retirados a quien tiene `packages:read`, y quien
          retira dos veces merece saber que la primera funcionó.

          **El código de un paquete retirado no se libera**: un alta con ese código
          responde `409`. El nombre sí. Exige `packages:delete`.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "204", description = "Paquete retirado."),
    @ApiResponse(
        responseCode = "400",
        description =
            "Identificador inválido o motivo ausente, vacío o de más de 500 (`VAL-001` a `VAL-003`)"),
    @ApiResponse(responseCode = "401", description = "Token ausente o inválido (`AUTH-001`)"),
    @ApiResponse(
        responseCode = "403",
        description = "Autenticado sin el permiso `packages:delete` (`AUTH-002`)"),
    @ApiResponse(responseCode = "404", description = "El paquete no existe (`EX-001`)"),
    @ApiResponse(responseCode = "409", description = "El paquete ya está retirado (`EX-002`)")
  })
  public void retirar(
      @PathVariable UUID id, @RequestBody(required = false) DeletePackageRequest peticion) {
    retiro.delete(id, peticion);
  }

  @PatchMapping("/{id}/products/{productId}")
  @PreAuthorize("hasAuthority('packages:update-product')")
  @Operation(
      summary = "Corregir el descuento de un producto del paquete",
      description =
          """
          Corrige **forma y valor juntos** —son un solo dato, y los dos son
          obligatorios: no es parcial— y devuelve el paquete entero con su
          `priceInPackage` y sus totales rehechos.

          **La cota se comprueba contra el precio de hoy del producto**, no contra el
          que tenía al asociarlo: un fijo que entró cuando el producto valía 100 y hoy
          vale 50 no se puede corregir a 60 — sí a 50 o menos. Es donde el hueco
          temporal de `RN-PM-037` se cierra solo. Sobre un producto gratuito solo se
          admite cero.

          **No mira el estado del producto**: un producto inactivo dentro del paquete
          se corrige igual — la fila existe y el descuento es del paquete—, y el
          paquete sigue sin ofrecerse por él. `productId` va en la ruta; en el cuerpo
          es un campo desconocido y responde `400`.

          El producto que no está en el paquete es `404`, sin distinguir «no existe»
          de «no está aquí»: lo que se corrige es la pareja. Sin cambio de valor
          responde `200` sin auditar. Exige `packages:update-product`.
          """)
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "El paquete con el descuento corregido y su cuenta rehecha.",
        content = @Content(schema = @Schema(implementation = PackageDetailResponse.class))),
    @ApiResponse(
        responseCode = "400",
        description =
            "Identificadores inválidos, forma o valor ausentes, o forma inválida (`VAL-001` a `VAL-005`)"),
    @ApiResponse(responseCode = "401", description = "Token ausente o inválido (`AUTH-001`)"),
    @ApiResponse(
        responseCode = "403",
        description = "Autenticado sin el permiso `packages:update-product` (`AUTH-002`)"),
    @ApiResponse(
        responseCode = "404",
        description =
            "El paquete no existe o está retirado, o el producto no está en él (`EX-001`, `EX-002`)"),
    @ApiResponse(
        responseCode = "409",
        description = "El descuento deja al producto por debajo de cero hoy (`EX-003`)")
  })
  public PackageDetailResponse corregirDescuento(
      @PathVariable UUID id,
      @PathVariable UUID productId,
      @Valid @RequestBody CorrectPackageDiscountRequest peticion) {
    return descuento.correct(id, productId, peticion);
  }

  @DeleteMapping("/{id}/products/{productId}")
  @PreAuthorize("hasAuthority('packages:remove-product')")
  @Operation(
      summary = "Quitar un producto del paquete",
      description =
          """
          Quita el producto del paquete **sin cuerpo y sin motivo** —la fila es una
          asociación (Art. V.13): se borra físicamente y su baja queda en la
          auditoría de eliminación como `ASSOCIATION`, con el descuento y el precio
          del producto en la instantánea— y responde **`200` con el paquete**, no
          `204`: lo que cambió es su precio.

          El paquete **no cambia de estado** aunque quede con uno o con cero
          productos: deja de ofrecerse por «menos de dos» y el detalle lo dice. Si se
          quita el upgrade, **el sitio queda libre** (`RN-PM-046`): el siguiente
          upgrade que entre puede ser de cualquier origen, y mientras no entre ninguno
          el paquete —solo bots— se ofrece a todo el mundo. **El producto no cambia**: sigue
          activo en el catálogo y en los demás paquetes que lo contengan.

          El producto que no está —también el que ya se quitó— es `404`: con el
          borrado físico no queda nada que los distinga. Exige `packages:remove-product`.
          """)
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "El paquete sin el producto, con su cuenta rehecha.",
        content = @Content(schema = @Schema(implementation = PackageDetailResponse.class))),
    @ApiResponse(responseCode = "400", description = "Identificadores inválidos (`VAL-001`)"),
    @ApiResponse(responseCode = "401", description = "Token ausente o inválido (`AUTH-001`)"),
    @ApiResponse(
        responseCode = "403",
        description = "Autenticado sin el permiso `packages:remove-product` (`AUTH-002`)"),
    @ApiResponse(
        responseCode = "404",
        description =
            "El paquete no existe o está retirado, o el producto no está en él (`EX-001`, `EX-002`)")
  })
  public PackageDetailResponse desasociar(@PathVariable UUID id, @PathVariable UUID productId) {
    return desasociacion.dissociate(id, productId);
  }

  @PutMapping(value = "/{id}/cover", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @PreAuthorize("hasAuthority('packages:set-cover')")
  @Operation(
      summary = "Subir o reemplazar la portada de un paquete",
      description =
          """
          **La misma imagen que la del producto, con las mismas condiciones y la
          misma ruta** (`RN-PM-045`, `requirements/pm.md` §5.2.12): recibe **un
          archivo** —`multipart/form-data`, una sola parte llamada `file`—,
          `JPEG`, `PNG` o `WebP` **reconocidos por sus bytes** (la cabecera y el
          nombre se ignoran; `VAL-003`), **hasta 5 MB** (`VAL-004`), sin archivo
          o vacío `VAL-002`, y lo guarda **tal cual**. La imagen se sirve sin token
          en `GET /api/v1/product-images/{imageId}`, que no sabe si lo que sirve es
          la portada de un producto o de un paquete.

          **Si el paquete ya tenía portada, la reemplaza**: la nueva estrena
          identificador, **la anterior se borra** y **la dirección cambia**. **Sin
          condición de estado ni de contenido**: un paquete inactivo, vacío o sin
          descripción la admite igual, y la activación sigue sin mirarla.

          **El paquete no declara icono ni color.** Cuando `coverImageUrl` es
          nula, el cliente pinta **el icono de promoción y el color por omisión
          del sistema**, los mismos para todos los paquetes — y por eso quitar la
          portada nunca se rechaza. El alta no admite la imagen: `POST
          /api/v1/packages` sigue siendo JSON. Responde con el paquete entero, con
          su cuenta hecha, como todas sus escrituras; **un paquete retirado no
          admite portada**.
          """)
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "El paquete, con `coverImageUrl` señalando la imagen nueva.",
        content = @Content(schema = @Schema(implementation = PackageDetailResponse.class))),
    @ApiResponse(
        responseCode = "400",
        description =
            "Identificador sin forma canónica (`VAL-001`), sin archivo o vacío (`VAL-002`), ni"
                + " JPEG ni PNG ni WebP por sus bytes (`VAL-003`), más de 5 MB (`VAL-004`) o"
                + " petición que no es `multipart/form-data` (`EX-003`)",
        content = @Content),
    @ApiResponse(
        responseCode = "401",
        description = "Token ausente o inválido (`AUTH-001`)",
        content = @Content),
    @ApiResponse(
        responseCode = "403",
        description = "Autenticado sin el permiso `packages:set-cover` (`AUTH-002`)",
        content = @Content),
    @ApiResponse(
        responseCode = "404",
        description = "No existe un paquete vivo con ese identificador (`EX-001`)",
        content = @Content),
    @ApiResponse(
        responseCode = "500",
        description = "Fallo no controlado (`ERR-500`)",
        content = @Content)
  })
  public PackageDetailResponse subirPortada(
      @PathVariable UUID id, @RequestPart(value = "file", required = false) MultipartFile file) {
    // `required = false` a propósito, como en el producto: la parte ausente es
    // `VAL-002` con el sobre de errores del sistema.
    return portada.upload(id, bytesDe(file));
  }

  @DeleteMapping("/{id}/cover")
  @PreAuthorize("hasAuthority('packages:remove-cover')")
  @Operation(
      summary = "Quitar la portada de un paquete",
      description =
          """
          Vacía la portada y **la imagen se borra**: su dirección deja de servir.
          El paquete vuelve a pintarse con **el icono de promoción y el color por
          omisión** que el cliente le pone cuando `coverImageUrl` es nula.

          **Nunca responde `400` por el estado del paquete** — es la única
          diferencia con `DELETE /api/v1/products/{id}/cover`: el paquete no
          declara icono ni color (`RN-PM-045`), de modo que no puede quedarse sin
          nada con qué pintarse. Un paquete activo y ofrecible sigue activo y
          ofrecible sin portada.

          **Sin portada responde igual y no escribe nada**: ni auditoría ni
          `updatedAt`. Responde `200` con el paquete, y no `204`: se vacía un
          campo, no se retira una entidad. Sin cuerpo; si llega uno, se ignora.
          """)
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "El paquete, con `coverImageUrl` nulo.",
        content = @Content(schema = @Schema(implementation = PackageDetailResponse.class))),
    @ApiResponse(
        responseCode = "400",
        description = "Identificador sin forma canónica (`VAL-001`)",
        content = @Content),
    @ApiResponse(
        responseCode = "401",
        description = "Token ausente o inválido (`AUTH-001`)",
        content = @Content),
    @ApiResponse(
        responseCode = "403",
        description = "Autenticado sin el permiso `packages:remove-cover` (`AUTH-002`)",
        content = @Content),
    @ApiResponse(
        responseCode = "404",
        description = "No existe un paquete vivo con ese identificador (`EX-001`)",
        content = @Content),
    @ApiResponse(
        responseCode = "500",
        description = "Fallo no controlado (`ERR-500`)",
        content = @Content)
  })
  public PackageDetailResponse quitarPortada(@PathVariable UUID id) {
    return quitarPortada.remove(id);
  }

  /** Los bytes de la parte, o nulo si no llegó: el dominio convierte el nulo en `VAL-002`. */
  private static byte[] bytesDe(MultipartFile file) {
    if (file == null) {
      return null;
    }
    try {
      return file.getBytes();
    } catch (IOException fallo) {
      throw new UncheckedIOException("No se pudo leer la imagen de portada de la petición.", fallo);
    }
  }
}
