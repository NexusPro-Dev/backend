package com.factech.nexus.modules.products.interfaces;

import com.factech.nexus.modules.products.application.ChangeProductStatusRequest;
import com.factech.nexus.modules.products.application.DeleteProductRequest;
import com.factech.nexus.modules.products.application.HotlinkCatalogResponse;
import com.factech.nexus.modules.products.application.ListProductsRequest;
import com.factech.nexus.modules.products.application.OfferResponse;
import com.factech.nexus.modules.products.application.ProductDetailResponse;
import com.factech.nexus.modules.products.application.ProductPageResponse;
import com.factech.nexus.modules.products.application.ProductResponse;
import com.factech.nexus.modules.products.application.RegisterProductRequest;
import com.factech.nexus.modules.products.application.UpdateProductRequest;
import com.factech.nexus.modules.products.domain.service.ChangeProductStatusService;
import com.factech.nexus.modules.products.domain.service.DeleteProductService;
import com.factech.nexus.modules.products.domain.service.GetHotlinkCatalogService;
import com.factech.nexus.modules.products.domain.service.GetOwnOfferService;
import com.factech.nexus.modules.products.domain.service.GetProductService;
import com.factech.nexus.modules.products.domain.service.ListProductsService;
import com.factech.nexus.modules.products.domain.service.RegisterProductService;
import com.factech.nexus.modules.products.domain.service.RemoveProductCoverService;
import com.factech.nexus.modules.products.domain.service.UpdateProductService;
import com.factech.nexus.modules.products.domain.service.UploadProductCoverService;
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
 * El catálogo de productos (`PM`).
 *
 * <p>Las seis operaciones del catálogo —alta, listado, detalle, corrección, cambio de estado y
 * retiro— más la oferta del cliente (`RF-PM-007`).
 *
 * <p><b>El listado no es la oferta, y por eso son dos endpoints.</b> El listado devuelve el
 * catálogo completo —lo activo, lo inactivo y, si se pide, lo retirado—, lo lee quien administra y
 * exige `products:read`. La oferta devuelve <b>solo lo que quien llama puede comprar</b>, la lee el
 * cliente y no exige ningún permiso. Responden preguntas distintas, a actores distintos y en
 * órdenes distintos; fundirlas en una con un filtro habría dado a cada cliente el catálogo entero.
 */
@RestController
@RequestMapping("/api/v1/products")
@Tag(name = "Productos", description = "Catálogo de venta: upgrades de membresía y bots.")
public class ProductController {

  private final RegisterProductService alta;
  private final ListProductsService listado;
  private final GetProductService detalle;
  private final ChangeProductStatusService estado;
  private final DeleteProductService retiro;
  private final UpdateProductService correccion;
  private final GetOwnOfferService ofertaPropia;
  private final UploadProductCoverService portada;
  private final RemoveProductCoverService quitarPortada;
  private final GetHotlinkCatalogService catalogoDeHotlinks;

  public ProductController(
      RegisterProductService alta,
      ListProductsService listado,
      GetProductService detalle,
      ChangeProductStatusService estado,
      DeleteProductService retiro,
      UpdateProductService correccion,
      GetOwnOfferService ofertaPropia,
      UploadProductCoverService portada,
      RemoveProductCoverService quitarPortada,
      GetHotlinkCatalogService catalogoDeHotlinks) {
    this.alta = alta;
    this.listado = listado;
    this.detalle = detalle;
    this.estado = estado;
    this.retiro = retiro;
    this.correccion = correccion;
    this.ofertaPropia = ofertaPropia;
    this.portada = portada;
    this.quitarPortada = quitarPortada;
    this.catalogoDeHotlinks = catalogoDeHotlinks;
  }

  @PostMapping
  @PreAuthorize("hasAuthority('products:create')")
  @Operation(
      summary = "Registrar un producto",
      description =
          """
          Registra un producto del catálogo, **siempre inactivo**: publicarlo es otra
          operación (`RF-PM-005`).

          El tipo decide qué campos son obligatorios: un `UPGRADE_MEMBRESIA` debe
          declarar **las dos** membresías —de qué nivel sale y a cuál lleva— y un
          `BOT` no puede declarar ninguna.

          **El origen no puede estar por encima del destino**: eso sería vender un
          descenso llamándolo upgrade, y se rechaza con `422` (`EX-006`). **Sí
          puede ser el MISMO**, y entonces el producto es una **renovación**: lo
          que vende es tiempo —su `validityDays`— y no un cambio de nivel.

          **Y no tiene por qué ser el inmediatamente inferior**: saltar niveles es
          legítimo, y es la razón de que el origen se declare en lugar de
          deducirse de la cadena.

          `icon` es el **nombre** del icono con el que el frontend pinta el producto
          —no una imagen—, en minúsculas y guion medio. Es **opcional**, y solo un
          `UPGRADE_MEMBRESIA` puede llevarlo: en un `BOT` se rechaza (`RN-PM-016`).

          `videoUrl` es **la dirección de un video** que presenta el producto —no
          el video—. Es **opcional y vale en los dos tipos**, sin la condición
          del icono. Se comprueba **solo la forma**: una URL absoluta `http` o
          `https`, sin espacios y de hasta 500 caracteres; lo demás se rechaza
          con `VAL-017`. **El sistema no sigue el enlace** —no comprueba que el
          video exista ni lo descarga—, y lo guarda **tal cual se escribió**,
          recortado y sin normalizar nada más. Ausente o nulo significan lo
          mismo: no tiene video. **Al revés que el precio de compra, sale en las
          cuatro lecturas**, el hotlink sin token incluido (`RN-PM-032`).

          La vigencia es opcional en los dos tipos: sin ella, lo adquirido no caduca.

          `scope` e `implementation` son **obligatorios y en los dos tipos**, y
          **no tienen valor por omisión**: `scope` dice hasta dónde se muestra el
          producto —`HOTLINKS` **incluye** `TIENDA`, no la sustituye— e
          `implementation` dice si lo comprado se aplica solo (`AUTOMATICA`) o
          espera a que un funcionario lo autorice (`MANUAL`).

          **Un producto lleva DOS precios y solo uno se cobra.** `price` es el
          del sistema —el que copia la venta y sobre el que se comisiona— y
          `purchasePrice` es el **precio de compra**: lo que NEXUS paga por el
          producto cuando tiene que comprarlo, y donde se guarda lo que costó.
          Es **opcional**, no interviene en ningún cálculo, se expresa en la
          **misma moneda** y **no sale de administración**: la oferta y el
          hotlink no lo devuelven. Ausente o nulo significan lo mismo —no se
          conoce todavía—, y **eso no es «costó cero»** (`RN-PM-023`). Se llamó
          `publicPrice` hasta el 12-09-2026; ese nombre es hoy una propiedad
          desconocida y devuelve `400`.

          Los dos importes se validan igual: **no negativos** —el **cero se
          admite** desde que existe la renovación de una membresía gratuita— y
          con los decimales que declare su moneda. El rechazo **nombra el
          campo** que incumple.

          **Los dos solo se ven desde administración** (`RN-PM-024`): la oferta
          de un cliente y el hotlink público devuelven **uno**.
          """)
  @ApiResponses({
    @ApiResponse(
        responseCode = "201",
        description = "Producto registrado, en estado `INACTIVO`.",
        content = @Content(schema = @Schema(implementation = ProductResponse.class))),
    @ApiResponse(responseCode = "400", description = "Datos inválidos (serie `VAL-nnn`)"),
    @ApiResponse(responseCode = "401", description = "Token ausente o inválido (`AUTH-001`)"),
    @ApiResponse(
        responseCode = "403",
        description = "Autenticado sin el permiso `products:create` (`AUTH-002`)"),
    @ApiResponse(
        responseCode = "409",
        description = "Código o nombre ya en uso (`EX-005`, `EX-001`)"),
    @ApiResponse(
        responseCode = "422",
        description =
            "Membresía inexistente, moneda inexistente o inactiva, o un origen POR ENCIMA del"
                + " destino (`EX-002`, `EX-003`, `EX-006`)")
  })
  public ResponseEntity<ProductResponse> register(
      @Valid @RequestBody RegisterProductRequest peticion) {

    ProductResponse creado = alta.register(peticion.toCommand());
    return ResponseEntity.created(URI.create("/api/v1/products/" + creado.id())).body(creado);
  }

  @GetMapping
  @PreAuthorize("hasAuthority('products:read')")
  @Operation(
      summary = "Consultar el catálogo de productos",
      description =
          """
          Devuelve el catálogo **paginado**, con el destino de cada upgrade y la
          moneda de cada precio ya resueltos: no hace falta una segunda consulta.

          **Los retirados quedan fuera salvo que se pidan** con `includeDeleted`.
          Al pedirlos se indica **desde cuándo** lo están; el **motivo** no viaja
          en el listado —uno a uno lo devuelve el detalle (`RF-PM-003`), en
          bloque sería una exportación de decisiones comerciales—. Verlos **no
          exige un permiso propio**: basta `products:read`.

          Cada fila trae **los dos precios**: `price` —el que se cobra— y
          `purchasePrice` —el **precio de compra**, lo que NEXUS paga por el
          producto—, este **presente y nulo** en los productos cuyo costo no se
          conoce. Este listado y el detalle son **los dos únicos sitios** donde
          se ven juntos: la oferta y el hotlink no devuelven el de compra
          (`RN-PM-024`, 12-09-2026).

          Y trae **`exchange`**, la conversión a la moneda por omisión con la
          tasa vigente hoy, calculada **sobre `price`** —el de compra nunca se
          convierte—. Llega **presente y nula** cuando el producto ya está en
          esa moneda o cuando nadie declaró una tasa: eso **no es un error** y
          el producto se devuelve igual.

          Cada fila trae también **`videoUrl`**, la dirección del video que
          presenta el producto, **tal cual se guardó** y **presente y nula**
          cuando no tiene (`RN-PM-032`). **No es un filtro.**

          Solo se puede ordenar por la lista blanca —`name`, `price`,
          `createdAt`—, con `,asc` o `,desc`. **`purchasePrice` no está en
          ella**: ordenar por una columna que admite nulos obligaría a decidir
          dónde van los productos sin costo conocido, y nadie lo ha decidido.
          **Un campo fuera de la lista se rechaza y no se ignora**:
          ignorarlo devolvería un orden distinto del pedido sin decirlo. Por omisión se ordena por **fecha de alta
          descendente**, y el orden aplicado viaja en la respuesta.

          **`targetMembershipId` no se valida contra el catálogo de membresías.**
          Filtrar por un destino inexistente devuelve la colección vacía y no es
          un error; combinarlo con `type=BOT` también, porque ningún
          bot tiene destino.

          La búsqueda va sobre el nombre, **sin distinguir acentos ni
          mayúsculas** y por fragmento. En blanco equivale a no filtrar.

          **`scope` e `implementation` filtran como `type` y `status`**: se
          admiten en cualquier caja y un valor fuera de dominio se rechaza junto
          al resto de parámetros inválidos, no en una vuelta aparte. El de
          alcance es **el único sitio donde ese dato se consulta hoy** — la
          oferta de `RF-PM-007` no filtra por él.

          Un filtro sin coincidencias devuelve `200` con la colección vacía, y
          una página más allá de la última hace lo mismo **con el total real**.
          No hay `404` ni `422`.
          """)
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Página del catálogo, con el orden aplicado.",
        content = @Content(schema = @Schema(implementation = ProductPageResponse.class))),
    @ApiResponse(
        responseCode = "400",
        description =
            "Paginación fuera de rango (`VAL-001`), tipo (`VAL-002`) o estado (`VAL-003`) fuera de"
                + " su dominio, identificador mal formado (`VAL-004`) o campo de ordenamiento no"
                + " admitido (`VAL-005`). Los cuatro primeros se devuelven **juntos**",
        content = @Content),
    @ApiResponse(
        responseCode = "401",
        description = "Token ausente o inválido (`AUTH-001`)",
        content = @Content),
    @ApiResponse(
        responseCode = "403",
        description = "Autenticado sin el permiso `products:read` (`AUTH-002`)",
        content = @Content),
    @ApiResponse(
        responseCode = "500",
        description = "Fallo no controlado (`ERR-500`)",
        content = @Content)
  })
  public ProductPageResponse listar(
      @org.springdoc.core.annotations.ParameterObject @ModelAttribute ListProductsRequest filtros) {
    return listado.list(filtros);
  }

  /**
   * <b>Va declarado antes que {@code /{id}} a propósito.</b> Spring no resuelve por orden de
   * declaración sino por especificidad —el segmento literal gana a la variable de ruta—, de modo
   * que esto no cambia el comportamiento: cambia quién lo entiende al leerlo. Que funcione lo fija
   * una prueba, porque el síntoma de romperlo sería un {@code 400} por identificador inválido en la
   * única ruta que un cliente usa a diario.
   */
  @GetMapping("/available")
  @PreAuthorize("hasAuthority('products:sale')")
  @Operation(
      summary = "Consultar la oferta disponible para uno mismo",
      description =
          """
          Devuelve lo que **quien llama** puede comprar hoy, y no el catálogo.

          **No admite ningún parámetro**: ni de persona, ni de filtro, ni de
          paginación. El actor sale del token, de modo que no hay forma de
          preguntar qué puede comprar otra persona.

          **Exige `products:sale`**, un permiso propio de esta vista y no
          `products:read` — ese abre el catálogo administrativo entero, lo
          inactivo, lo retirado y el motivo del retiro, para que alguien pudiera
          ver tres líneas.

          **Solo lo activo.** Ni lo inactivo ni lo retirado aparecen aquí,
          aunque el catálogo de `RF-PM-002` sí los muestre a quien administra.

          **Upgrades: los declarados DESDE la membresía vigente de quien mira**,
          y ninguno más. La coincidencia es exacta: no se comparan niveles ni se
          recorre la cadena. Quien registró el producto ya decidió a quién va
          dirigido.

          Eso incluye **todos** los declarados desde ahí y no solo el inmediato
          —quien está en el peldaño más bajo elige cuánto saltar, y el precio de
          cada uno ya expresa el salto—, y llegan ordenados **del salto más
          corto al más largo**.

          **La RENOVACIÓN entra aquí**, y va primera: un producto `X → X`
          declarado desde su propia membresía es el salto de longitud cero. Lo
          que vende es **tiempo** —su `validityDays`— y no un cambio de nivel.

          **Un upgrade hacia el nivel que ya se tiene NO se ofrece cuando su
          origen no es el suyo**: sería el salto de otra persona que acaba donde
          quien mira ya está. Y **ninguna bajada** llega hasta aquí, aunque este
          filtro ya no la mire: lo impide `RN-PM-017` **al registrar**, porque un
          producto declarado desde mi membresía no puede apuntar por debajo.

          **Bots: todos los activos, para cualquiera.** No dependen del nivel de
          quien mira ni de que tenga uno.

          **Cada producto trae `videoUrl`**, la dirección del video que lo
          presenta, tal cual se guardó y **presente y nula** cuando no tiene
          (`RN-PM-032`). Es lo contrario del precio de compra: material de
          venta, que existe para que lo vea quien compra, y por eso **sí** viaja
          por aquí.

          **Publica `scope` e `implementation` de cada producto y NO filtra por
          ninguno de los dos.** El alcance no puede filtrar aquí: `HOTLINKS`
          incluye `TIENDA`, de modo que los dos valores llegan a esta vista y un
          filtro devolvería siempre lo mismo que no ponerlo. La implementación
          viaja para que quien compra sepa **antes de pagar** si lo que se lleva
          se le entrega en el acto.

          **Quien no tiene membresía vigente —incluida la vencida— no ve ningún
          upgrade**, y sí todos los bots. No hay nivel desde el que subir, y
          ofrecerle el primero sería venderle una membresía, que no es lo que un
          upgrade hace.

          **Quien está en la cima recibe la lista de upgrades vacía.** No es un
          error ni un mensaje especial: es una lista vacía.

          **El precio es igual para todos**: un importe distinto según quién
          mira sería un descuento, y los descuentos son promociones, que están
          fuera de alcance.

          **Viene UN importe** (`RN-PM-024`, reescrita el 12-09-2026): `price`,
          el que la venta cobra. **El precio de compra no viaja por aquí**: es
          lo que NEXUS paga por el producto, y quien compra no tiene por qué
          conocer el margen. Entre el 08-09-2026 y el 12-09-2026 esta respuesta
          traía también `publicPrice`, cuando ese importe era lo que se
          anunciaba; ese campo **ya no existe**.

          Y viene **`exchange`**, la conversión de `price` a la moneda por
          omisión con la tasa vigente hoy. **Presente y nula** cuando no hay
          nada que convertir.

          **Quien construya la pantalla de compra tiene que saberlo**: el
          importe que confirma la venta es `price`, el mismo que se enseña.

          Las dos colecciones viajan **envueltas en un objeto** y no como
          arreglos en la raíz: hoy la oferta no se pagina, y así el día que
          haya que paginarla no romperá a ningún cliente.
          """)
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "La oferta del actor, con el nivel desde el que mira.",
        content = @Content(schema = @Schema(implementation = OfferResponse.class))),
    @ApiResponse(
        responseCode = "401",
        description = "Token ausente o inválido (`AUTH-001`)",
        content = @Content),
    @ApiResponse(
        responseCode = "403",
        description = "Autenticado sin el permiso `products:sale` (`AUTH-002`)",
        content = @Content),
    @ApiResponse(
        responseCode = "500",
        description = "Fallo no controlado (`ERR-500`)",
        content = @Content)
  })
  public OfferResponse oferta() {
    return ofertaPropia.offer();
  }

  // Segmento literal bajo `/products`, como `/available`: Spring lo resuelve
  // antes que `/{id}`, y por eso mismo tiene prueba (`CA-PM-346`).
  @GetMapping("/hotlinks")
  @PreAuthorize("hasAuthority('products:hotlink')")
  @Operation(
      summary = "Consultar el catálogo de hotlinks",
      description =
          """
          **Lo que un vendedor puede repartir**: los productos **activos y de
          alcance `HOTLINKS`**, de los dos tipos, en `upgrades` y `services`
          — exactamente el conjunto que `GET /api/v1/hotlinks/{username}/{code}`
          resuelve enlace a enlace (`RN-PM-021`), visto entero y con token.

          **No mira la membresía de quien llama**, y ahí se aparta de la oferta
          (`GET /api/v1/products/available`): el vendedor no compra lo que
          reparte, de modo que un `BECA → ORO` le interesa aunque él esté en
          `ORO`. Por eso la respuesta no trae `currentMembership`. Cada producto
          va en la forma de venta —`price`, `exchange`, `videoUrl`,
          `coverImageUrl`, `rating`— y **sin `purchasePrice`** (`RN-PM-024`).

          **No trae el enlace armado.** El cliente lo compone con el `username`
          de `GET /api/v1/users/me` y el `code` de cada producto:
          `/api/v1/hotlinks/{username}/{code}`. Que ese enlace resuelva exige
          además que quien lo reparte sea fuerza comercial (`RN-PM-022`); esta
          lista dice qué se publica, no quién puede publicarlo. **Sin
          parámetros y sin paginar**: es el catálogo publicable, entero. Los
          paquetes llegarán a esta misma respuesta con `RF-PM-026`.
          """)
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "El catálogo de hotlinks: upgrades y bots publicables.",
        content = @Content(schema = @Schema(implementation = HotlinkCatalogResponse.class))),
    @ApiResponse(
        responseCode = "401",
        description = "Token ausente o inválido (`AUTH-001`)",
        content = @Content),
    @ApiResponse(
        responseCode = "403",
        description = "Autenticado sin el permiso `products:hotlink` (`AUTH-002`)",
        content = @Content),
    @ApiResponse(
        responseCode = "500",
        description = "Fallo no controlado (`ERR-500`)",
        content = @Content)
  })
  public HotlinkCatalogResponse catalogoDeHotlinks() {
    return catalogoDeHotlinks.catalog();
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasAuthority('products:read')")
  @Operation(
      summary = "Consultar el detalle de un producto",
      description =
          """
          Devuelve el producto con su membresía destino y su moneda **resueltas**,
          sin exigir una segunda consulta.

          Trae **los dos precios** —`price`, el que se cobra, y `purchasePrice`,
          el **precio de compra**: lo que NEXUS paga por el producto, que llega
          **nulo y presente** si no se conoce— y **`exchange`**, la conversión
          de `price` a la moneda por omisión con la tasa vigente hoy. La
          conversión llega **presente y nula** cuando el producto ya está en esa
          moneda o cuando nadie declaró una tasa (`RN-PM-024`). El precio de
          compra solo se ve aquí y en el listado: la oferta y el hotlink no lo
          devuelven.

          Trae **`videoUrl`**, la dirección del video que presenta el producto,
          **tal cual se guardó** y **presente y nula** cuando no tiene
          (`RN-PM-032`) — también en un producto retirado.

          **Un producto retirado se devuelve marcado como tal**, no como
          inexistente: `deletedAt` dice desde cuándo y `deletionReason` **por
          qué**. Los dos campos **solo aparecen si el producto está retirado** —
          su ausencia significa que sigue vivo—. El motivo llega con
          `products:read` y **sin exigir permiso de auditoría**; es la
          contrapartida asumida de que el detalle lo devuelva.

          **No devuelve autoría en ninguna forma**: ni quién lo creó, ni quién lo
          corrigió, ni quién lo retiró. Eso vive en la auditoría y tiene su
          propio permiso.

          `targetMembership` y `validityDays` viajan **presentes en nulo** cuando
          no aplican: un bot no tiene destino y un producto puede no
          caducar, y un campo ausente es indistinguible de uno que el cliente no
          conoce.

          El **nivel** del destino es el **actual**, no el que tenía cuando se
          creó el producto: la cadena de membresías se reordena al insertar un
          eslabón.

          Un identificador con forma laxa se rechaza como **dato inválido**
          (`400`), no como recurso no encontrado.
          """)
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "El producto, con su destino y su moneda resueltos.",
        content = @Content(schema = @Schema(implementation = ProductDetailResponse.class))),
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
        description = "Autenticado sin el permiso `products:read` (`AUTH-002`)",
        content = @Content),
    @ApiResponse(
        responseCode = "404",
        description = "No existe un producto con ese identificador (`EX-001`)",
        content = @Content),
    @ApiResponse(
        responseCode = "500",
        description = "Fallo no controlado (`ERR-500`)",
        content = @Content)
  })
  public ProductDetailResponse detalle(@PathVariable UUID id) {
    return detalle.detail(id);
  }

  @PatchMapping("/{id}")
  @PreAuthorize("hasAuthority('products:update')")
  @Operation(
      summary = "Corregir un producto",
      description =
          """
          Corrige el **nombre**, la **descripción**, el **icono**, el **enlace
          del video**, **los dos precios**, la **moneda**, la **vigencia**, el
          **alcance** y la **implementación**. Se aplica lo que llega y se deja
          intacto lo que no.

          **Distingue el campo ausente del enviado vacío**, y de ahí salen dos
          comportamientos opuestos: `description: null`, `icon: null`,
          `videoUrl: null`, `validityDays: null` y `purchasePrice: null`
          **vacían** el campo,
          mientras que `name: null` y `price: null` se **rechazan**, porque un
          producto sin nombre o sin precio del sistema no puede existir.

          **`purchasePrice` es el precio de compra** —lo que NEXUS paga por el
          producto— y esta operación es donde hoy se registra lo que costó.
          **Vaciarlo NO es ponerlo a cero**: con nulo el costo pasa a «no se
          conoce», y con cero a «no costó nada». Son dos estados distintos y
          los dos se alcanzan desde aquí. Se llamó `publicPrice` hasta el
          12-09-2026; ese nombre es hoy una propiedad desconocida y devuelve
          `400`.

          **`scope: null` e `implementation: null` también se rechazan**, y ahí
          van con el nombre y no con la descripción: son obligatorios en la
          columna, de modo que «bórralo» no tiene ningún estado al que llevar el
          producto. Devuelven `400` con `VAL-007` y `VAL-008`.

          **El alcance y la implementación SÍ se corrigen, aunque el tipo y las
          membresías no**: ninguna de las dos define qué derecho otorga el
          producto —una dice hasta dónde se muestra y la otra quién lo aplica—,
          de modo que corregirlas no reescribe lo que compró quien lo compró.

          **El icono sí se corrige, aunque el tipo no**: es el aspecto del
          producto y no lo que otorga. En un `BOT`, cualquier valor distinto de
          nulo se rechaza con `VAL-013` (`RN-PM-016`).

          **`videoUrl` se corrige y se vacía en los DOS tipos** —con `null` o
          con `""`, que aquí también es un vaciado— y sin la condición del
          icono (`RN-PM-032`). Se comprueba **solo la forma** —URL absoluta
          `http` o `https`, sin espacios, hasta 500 caracteres— y lo demás se
          rechaza con `VAL-009` **sin aplicar ningún otro cambio** de la misma
          petición. Se guarda tal cual, recortado, y el sistema no lo sigue.

          **El tipo, el código y la membresía destino NO se pueden corregir**, y
          enviarlos devuelve `400` con `VAL-006`. Se rechazan y no se ignoran:
          ignorarlos haría creer que el cambio se aplicó. Definen qué derecho
          otorga el producto, y cambiarlos convertiría lo comprado en otra cosa.

          **Los DOS importes se validan contra la moneda que va a quedar**, y no
          solo el que llega en la petición: cambiar **solo** la moneda puede
          dejar sin caber a un precio que nadie tocó, y el rechazo **nombra el
          campo** que no cabe. Y el importe **no se convierte**: el sistema no
          hace conversión de divisa — cambiar de moneda es declarar que ese
          número siempre estuvo en la otra.

          **Un producto retirado no se corrige**: lo que se retiró debe quedar
          como estaba para que lo que lo referencie siga diciendo la verdad.

          **No se exige motivo**, ni siquiera al cambiar el precio. Una petición
          que no cambia nada devuelve `200` y **no registra evento**.
          """)
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "El producto, ya corregido.",
        content = @Content(schema = @Schema(implementation = ProductDetailResponse.class))),
    @ApiResponse(
        responseCode = "400",
        description =
            "Identificador sin forma canónica (`VAL-001`), nombre vacío o ningún campo informado"
                + " (`VAL-002`), longitud excedida (`VAL-003`), precio negativo o precio del"
                + " sistema vaciado (`VAL-004`),"
                + " decimales que la moneda no admite (`VAL-005`), campos inmutables en la"
                + " petición (`VAL-006`) o vigencia no positiva (`VAL-011`)",
        content = @Content),
    @ApiResponse(
        responseCode = "401",
        description = "Token ausente o inválido (`AUTH-001`)",
        content = @Content),
    @ApiResponse(
        responseCode = "403",
        description = "Autenticado sin el permiso `products:update` (`AUTH-002`)",
        content = @Content),
    @ApiResponse(
        responseCode = "404",
        description = "No existe un producto vivo con ese identificador (`EX-001`)",
        content = @Content),
    @ApiResponse(
        responseCode = "409",
        description = "El nombre ya lo tiene otro producto vivo (`EX-002`)",
        content = @Content),
    @ApiResponse(
        responseCode = "422",
        description = "La moneda no existe o está desactivada (`EX-003`)",
        content = @Content),
    @ApiResponse(
        responseCode = "500",
        description = "Fallo no controlado (`ERR-500`)",
        content = @Content)
  })
  public ProductDetailResponse corregir(
      @PathVariable UUID id, @RequestBody UpdateProductRequest peticion) {
    return correccion.update(id, peticion);
  }

  @PatchMapping("/{id}/status")
  @PreAuthorize("hasAuthority('products:update')")
  @Operation(
      summary = "Publicar o retirar de la oferta un producto",
      description =
          """
          Cambia el estado del producto entre `ACTIVO` e `INACTIVO`. **Es un
          recurso propio y no un campo de la edición**: publicar y corregir son
          decisiones distintas, y mezclarlas haría que una corrección de texto
          pudiera poner algo a la venta.

          **Pedir el estado que el producto ya tiene devuelve `200` sin cambiar
          nada y sin registrar evento.** No es un error: quien pulsa dos veces el
          mismo botón no ha hecho nada malo.

          **No se publica un producto sin descripción** (`RN-PM-014`). Sí se
          permite **desactivarlo** sin ella: la regla acota lo que se ofrece, no
          lo que se retira.

          **Solo puede haber un upgrade activo hacia cada membresía destino**
          (`RN-PM-004`). Al activar uno cuyo destino ya está ocupado, el rechazo
          **nombra el producto que lo ocupa**, para que se sepa cuál desactivar.
          Desactivar no comprueba nada: liberar un destino nunca produce
          conflicto.

          **Un producto retirado no vuelve a la venta por aquí**: responde que no
          existe.

          No se exige motivo para activar ni para desactivar.
          """)
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "El producto, con su estado ya aplicado.",
        content = @Content(schema = @Schema(implementation = ProductDetailResponse.class))),
    @ApiResponse(
        responseCode = "400",
        description =
            "Identificador sin forma canónica (`VAL-001`), estado ausente o fuera de su dominio"
                + " (`VAL-002`), o activación de un producto sin descripción (`VAL-003`)",
        content = @Content),
    @ApiResponse(
        responseCode = "401",
        description = "Token ausente o inválido (`AUTH-001`)",
        content = @Content),
    @ApiResponse(
        responseCode = "403",
        description = "Autenticado sin el permiso `products:update` (`AUTH-002`)",
        content = @Content),
    @ApiResponse(
        responseCode = "404",
        description = "No existe un producto vivo con ese identificador (`EX-001`)",
        content = @Content),
    @ApiResponse(
        responseCode = "409",
        description = "Ya hay otro upgrade activo hacia esa membresía destino (`EX-002`)",
        content = @Content),
    @ApiResponse(
        responseCode = "500",
        description = "Fallo no controlado (`ERR-500`)",
        content = @Content)
  })
  public ProductDetailResponse cambiarEstado(
      @PathVariable UUID id, @Valid @RequestBody ChangeProductStatusRequest peticion) {
    return estado.change(id, peticion);
  }

  @PutMapping(value = "/{id}/cover", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @PreAuthorize("hasAuthority('products:update')")
  @Operation(
      summary = "Subir o reemplazar la portada de un producto",
      description =
          """
          Recibe **un archivo** —`multipart/form-data`, una sola parte llamada
          `file`— y lo convierte en la portada del producto. **Es el primer
          archivo que el sistema guarda** (`RN-PM-033`, `requirements/pm.md`
          §5.2.9), y lo guarda **tal cual**: ni recorte, ni redimensión, ni
          conversión.

          **El tipo lo deciden los bytes, no la cabecera**: `JPEG`, `PNG` o
          `WebP`, reconocidos por su firma; la cabecera `Content-Type` de la
          parte y el nombre del archivo se ignoran. Un `GIF`, un `SVG` o un
          texto con extensión `.png` se rechazan con `VAL-003`. **Hasta 5 MB**
          (`VAL-004`); sin archivo o vacío, `VAL-002`. Los tres nombran `file`.

          **Si el producto ya tenía portada, la reemplaza**: la nueva estrena
          identificador, **la anterior se borra** y **la dirección cambia** —
          `coverImageUrl` señala una imagen concreta, no «la portada del
          producto», y por eso `GET /api/v1/product-images/{imageId}` se sirve
          con caché inmutable. **En los dos tipos y sin condición**: subir una
          portada nunca deja al producto peor.

          **El alta no admite la imagen**: `POST /api/v1/products` sigue siendo
          JSON, y la portada se sube después con esta operación. Responde con
          el producto, como la corrección, y **un producto retirado no admite
          portada**.
          """)
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "El producto, con `coverImageUrl` señalando la imagen nueva.",
        content = @Content(schema = @Schema(implementation = ProductDetailResponse.class))),
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
        description = "Autenticado sin el permiso `products:update` (`AUTH-002`)",
        content = @Content),
    @ApiResponse(
        responseCode = "404",
        description = "No existe un producto vivo con ese identificador (`EX-001`)",
        content = @Content),
    @ApiResponse(
        responseCode = "500",
        description = "Fallo no controlado (`ERR-500`)",
        content = @Content)
  })
  public ProductDetailResponse subirPortada(
      @PathVariable UUID id, @RequestPart(value = "file", required = false) MultipartFile file) {
    // `required = false` a propósito: la parte ausente es `VAL-002` con el
    // sobre de errores del sistema, no el `400` genérico de Spring sin cuerpo
    // útil. El resto del módulo no sabe que existió una petición HTTP: recibe
    // bytes.
    return portada.upload(id, bytesDe(file));
  }

  @DeleteMapping("/{id}/cover")
  @PreAuthorize("hasAuthority('products:update')")
  @Operation(
      summary = "Quitar la portada de un producto",
      description =
          """
          Vacía la portada: el producto vuelve a pintarse con su icono —o con el
          que el frontend le pone por omisión, si es un bot— y **la imagen se
          borra**: su dirección deja de servir.

          **Es la única de las tres operaciones de la portada que puede decir
          que no**: un upgrade **sin icono** no puede quedarse sin portada
          (`RN-PM-034`), y se rechaza con `VAL-002` nombrando `icon` — lo que
          falta. El orden para arreglarlo es declarar el icono con
          `PATCH /api/v1/products/{id}` y después quitar la portada. A un bot
          se le quita siempre.

          **Sin portada responde igual y no escribe nada**: ni auditoría ni
          `updatedAt`. Responde `200` con el producto, y no `204`: se vacía un
          campo, no se retira una entidad. Sin cuerpo; si llega uno, se ignora.
          """)
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "El producto, con `coverImageUrl` nulo.",
        content = @Content(schema = @Schema(implementation = ProductDetailResponse.class))),
    @ApiResponse(
        responseCode = "400",
        description =
            "Identificador sin forma canónica (`VAL-001`) o upgrade sin icono que se quedaría sin"
                + " nada que pintar (`VAL-002`)",
        content = @Content),
    @ApiResponse(
        responseCode = "401",
        description = "Token ausente o inválido (`AUTH-001`)",
        content = @Content),
    @ApiResponse(
        responseCode = "403",
        description = "Autenticado sin el permiso `products:update` (`AUTH-002`)",
        content = @Content),
    @ApiResponse(
        responseCode = "404",
        description = "No existe un producto vivo con ese identificador (`EX-001`)",
        content = @Content),
    @ApiResponse(
        responseCode = "500",
        description = "Fallo no controlado (`ERR-500`)",
        content = @Content)
  })
  public ProductDetailResponse quitarPortada(@PathVariable UUID id) {
    return quitarPortada.remove(id);
  }

  /**
   * Los bytes de la parte, o nulo si no llegó: el dominio convierte el nulo en `VAL-002`.
   *
   * <p>Un fallo de lectura del cuerpo no es un dato inválido sino un fallo de transporte, y sube
   * como no controlado.
   */
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

  @PostMapping("/{id}/deletion")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize("hasAuthority('products:delete')")
  @Operation(
      summary = "Retirar un producto del catálogo",
      description =
          """
          Retira el producto: **eliminación lógica y con motivo** (Art. V.13).
          La fila se conserva —lo vendido tiene que seguir resolviendo a lo que
          se vendió— y el producto deja de ofrecerse.

          **`POST` sobre un subrecurso y no `DELETE` con cuerpo**, igual que al
          eliminar un rol o una persona: RFC 9110 no define semántica para el
          cuerpo de un `DELETE` y un intermediario puede descartarlo, con lo que
          la petición se convertiría en un rechazo por motivo ausente que quien
          la envió no puede entender ni corregir. Y tampoco en la URL, donde el
          motivo quedaría escrito en los registros de acceso de cualquier proxy.

          **El motivo es obligatorio** y se comprueba lo primero de todo, antes
          de tocar la base.

          **El estado no se modifica al retirar.** El registro de eliminación
          conserva si el producto **estaba a la venta**: desactivarlo «de paso»
          haría que todos los registros dijeran «inactivo» y ese dato dejaría de
          significar nada.

          **Qué libera el retiro y qué no**: el **destino** del upgrade queda
          libre para que otro se active, y el **nombre** queda libre para otro
          producto. El **código no se libera nunca** — el día que una factura
          diga `UPGRADE_ORO` tiene que resolver a un solo producto para siempre.

          **No es idempotente a propósito**: retirar uno ya retirado devuelve
          `409`. Dos motivos sobre un solo hecho es evidencia contradictoria.

          No devuelve el producto: lo que se acaba de retirar no es algo que el
          sistema deba seguir ofreciendo a quien lo pidió.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "204", description = "Producto retirado.", content = @Content),
    @ApiResponse(
        responseCode = "400",
        description =
            "Identificador sin forma canónica (`VAL-001`), motivo ausente o en blanco (`VAL-002`)"
                + " o demasiado largo (`VAL-003`)",
        content = @Content),
    @ApiResponse(
        responseCode = "401",
        description = "Token ausente o inválido (`AUTH-001`)",
        content = @Content),
    @ApiResponse(
        responseCode = "403",
        description = "Autenticado sin el permiso `products:delete` (`AUTH-002`)",
        content = @Content),
    @ApiResponse(
        responseCode = "404",
        description = "No existe un producto con ese identificador (`EX-001`)",
        content = @Content),
    @ApiResponse(
        responseCode = "409",
        description = "El producto ya estaba retirado (`EX-002`)",
        content = @Content),
    @ApiResponse(
        responseCode = "500",
        description = "Fallo no controlado (`ERR-500`)",
        content = @Content)
  })
  public void retirar(
      @PathVariable UUID id, @RequestBody(required = false) DeleteProductRequest peticion) {
    retiro.delete(id, peticion);
  }
}
