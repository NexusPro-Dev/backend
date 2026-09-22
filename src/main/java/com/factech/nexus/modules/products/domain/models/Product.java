package com.factech.nexus.modules.products.domain.models;

import com.factech.nexus.shared.error.FieldError;
import com.factech.nexus.shared.error.ValidationException;
import com.factech.nexus.shared.patch.Patchable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Una unidad de venta del catálogo (`RF-PM-001`).
 *
 * <p><b>Es a la vez agregado y modelo persistente</b>, como {@code Role} y {@code Membership}:
 * `architecture.md` §5.1 sitúa el modelo persistente en {@code domain/models}, de modo que no hay
 * dos representaciones que unir ni mapeador que las una.
 *
 * <p><b>Nace {@link ProductStatus#INACTIVO}</b> (`RN-PM-012`), y el estado no se recibe: no existe
 * forma de crear un producto ya publicado. Es lo que hace verificable que `RN-PM-004` solo pueda
 * violarse desde `RF-PM-005`.
 *
 * <p><b>Lo que no se puede corregir vive aquí sin mutador</b>: el tipo, el código y la membresía
 * destino definen qué derecho otorga el producto, y cambiarlos convertiría lo comprado en otra
 * cosa.
 */
@Entity
@Table(name = "products")
public class Product {

  /**
   * Letras mayúsculas, dígitos y guion bajo, empezando por letra (`VAL-010`).
   *
   * <p>Compilado una sola vez: {@code String.matches} recompila el patrón en cada llamada.
   */
  private static final Pattern PATRON_CODIGO = Pattern.compile("^[A-Z][A-Z0-9_]*$");

  /**
   * Kebab-case en minúsculas, empezando por letra (`VAL-012`).
   *
   * <p>Es como nombran sus iconos los sets al uso —{@code arrow-up-circle}, {@code crown}—, y el
   * backend no conoce ninguno: guarda el nombre y el frontend lo traduce al suyo.
   */
  private static final Pattern PATRON_ICONO = Pattern.compile("^[a-z][a-z0-9-]*$");

  // El patrón del enlace del video vivió aquí entre el 14-09-2026 y el
  // 22-09-2026. Se fue con la columna: el video es ahora un enlace CON TIPO en
  // `product_links`, y su forma la comprueba `ProductLink` (`RN-PM-048`).

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "code", nullable = false, length = 50, updatable = false)
  private String code;

  @Enumerated(EnumType.STRING)
  @Column(name = "type", nullable = false, length = 30, updatable = false)
  private ProductType type;

  @Column(name = "name", nullable = false, length = 150)
  private String name;

  @Column(name = "description")
  private String description;

  /**
   * El icono con el que el frontend pinta el producto (`RN-PM-016`).
   *
   * <p><b>Es un identificador, no una imagen.</b> El backend guarda el nombre y no sabe pintarlo,
   * igual que con {@code memberships.color}: el sistema no almacena binarios, y dónde vivirían es
   * una decisión que este campo no necesita abrir.
   *
   * <p><b>Solo existe en el upgrade, y ni siquiera ahí es obligatorio.</b> Nulo significa «sin
   * icono» y es un estado normal; en un {@link ProductType#BOT} el nulo es el único valor posible.
   */
  @Column(name = "icon", length = 50)
  private String icon;

  // `video_url` fue una columna de esta tabla entre el 14-09-2026 y el
  // 22-09-2026. `V35` la migra a `product_links` y la borra: el video pasó a
  // ser un enlace CON TIPO —`VIDEO_PRESENTACION`—, y los enlaces no son
  // columnas de este agregado sino filas de su tabla anexa, leídas y escritas
  // por `ProductLinkRepository` (`RN-PM-048`, `pm.md` §5.2.14). No se mapean
  // como `@OneToMany` a propósito: una página de veinte productos los resuelve
  // en UNA sentencia, y una asociación los traería producto a producto.

  /**
   * La portada del producto: la fila de {@code product_images} cuyos bytes se sirven sin token
   * (`RN-PM-033`, `V90`).
   *
   * <p><b>Es un identificador y no una asociación</b>, por lo mismo que las membresías: la imagen
   * es un valor que se reemplaza y se borra, y una asociación {@code @OneToOne} la cargaría —cinco
   * megas— en cada lectura del agregado. Nulo significa «no tiene portada», que es el estado de
   * todo producto anterior al 14-09-2026.
   *
   * <p><b>En los dos tipos y sin condición</b> para subirla. La condición está en el otro sentido:
   * un upgrade <b>sin</b> portada necesita icono (`RN-PM-034`), y ese cruce vive en las tres
   * operaciones de este agregado que pueden dejarlo sin nada que pintar — {@link #create}, {@link
   * #update} y {@link #quitarPortada}.
   */
  @Column(name = "cover_image_id")
  private UUID coverImageId;

  /**
   * Identificador y no una asociación {@code @ManyToOne}: apunta a una tabla de otro módulo, y una
   * asociación traería aquí su entidad — que es exactamente lo que D-25 impide. Los datos del
   * destino entran por la interfaz que `SP` publica.
   */
  @Column(name = "target_membership_id", updatable = false)
  private UUID targetMembershipId;

  /**
   * De qué membresía <b>sale</b> el upgrade, desde el 02-09-2026 (`RN-PM-002`).
   *
   * <p><b>Inmutable por el mismo motivo que el destino</b>: cambiar de quién sale un upgrade
   * reescribe a quién iba dirigido lo que ya se vendió.
   *
   * <p><b>No tiene por qué ser la inmediatamente inferior al destino</b> (`RN-PM-018`). Deducirla
   * de la cadena habría hecho imposible exactamente el caso que este campo existe para permitir: el
   * salto de varios niveles, que es un producto distinto y con su propio precio.
   */
  @Column(name = "source_membership_id", updatable = false)
  private UUID sourceMembershipId;

  /**
   * El precio que <b>se cobra</b> (`RN-PM-023`).
   *
   * <p><b>Admite cero desde el 08-09-2026</b> (`RN-PM-006`, `V67`). Lo que tumbó el «mayor que
   * cero» no fue el precio público sino la <b>renovación</b>: un {@code BECA → BECA} es un producto
   * legítimo que vale eso, y prohibirlo obligaba a inventarle un céntimo.
   */
  @Column(name = "price", nullable = false, precision = 14, scale = 4)
  private BigDecimal price;

  /**
   * Lo que <b>NEXUS paga</b> por el producto cuando tiene que comprarlo: el precio de compra
   * (`RN-PM-023`). Ahí se guarda lo que costó.
   *
   * <p><b>No se cobra.</b> Ningún cálculo lo lee: la venta copia {@link #price} y sobre ese mismo
   * calcula `RN-CM-019`. Que un importe no se cobre <b>no es expresable en el esquema</b>, de modo
   * que lo único que sostiene esa regla es <b>dónde no aparece</b> — {@code
   * ProductCatalog.saleViewOf} no lo lleva, y añadirlo ahí bastaría para que empezara a cobrarse
   * sin que nada fallara.
   *
   * <p><b>Y no sale de administración</b> (`RN-PM-024`): lo devuelven el listado y el detalle, bajo
   * {@code products:read}; la oferta y el hotlink <b>no lo seleccionan</b>, porque es el margen y
   * en el hotlink eso sería sin token. Tampoco es expresable en el esquema, y lo sostiene lo mismo:
   * que {@code OfferItem} y la respuesta del hotlink <b>no tengan el campo</b>.
   *
   * <p><b>Nulo no es cero</b>: el nulo significa «no se conoce» —el producto no se ha comprado
   * todavía, o no aplica—, mientras que el cero dice que no costó nada. Los dos estados son
   * alcanzables desde `RF-PM-004`, que es hoy donde se registra lo que costó.
   *
   * <p><b>Y va en la misma moneda</b>: no hay una segunda {@code currency_id}. Si NEXUS paga en
   * otra, quien registra el costo lo convierte al declararlo.
   *
   * <p><b>Se llamó {@code publicPrice} —lo que se anunciaba— del 08-09-2026 al 12-09-2026</b>
   * (`V67` → `V86`). La forma es la misma; lo que cambió es qué es el número y quién puede verlo
   * (`requirements/pm.md` §5.2.6).
   */
  @Column(name = "purchase_price", precision = 14, scale = 4)
  private BigDecimal purchasePrice;

  @Column(name = "currency_id", nullable = false)
  private UUID currencyId;

  /** Días que dura lo adquirido, desde la compra. Nulo: no caduca (`RN-PM-015`). */
  @Column(name = "validity_days")
  private Integer validityDays;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private ProductStatus status;

  /**
   * Hasta dónde se muestra el producto (`RN-PM-019`).
   *
   * <p><b>Obligatorio en los dos tipos</b>, y ahí se aparta de la membresía destino y del icono:
   * aquellos dependen del tipo, y este no — un bot también se muestra en algún sitio.
   *
   * <p><b>Y no lleva valor por omisión</b>, ni aquí ni en la columna. Un producto guardado con el
   * alcance supuesto se ve <b>exactamente igual</b> que uno declarado, de modo que el defecto no se
   * vería nunca: nadie descubriría que nadie decidió dónde se publica.
   */
  @Enumerated(EnumType.STRING)
  @Column(name = "scope", nullable = false, length = 20)
  private ProductScope scope;

  /**
   * Si lo comprado se aplica solo o espera a que alguien lo autorice (`RN-PM-020`).
   *
   * <p><b>Es el campo de este agregado que gobierna a otro módulo</b>: `RN-MV-020` concede la
   * membresía comprada <b>solo</b> si vale {@link ProductImplementation#AUTOMATICA}.
   *
   * <p><b>Se corrige</b> (`RF-PM-004`), y por eso `RN-MV-002` obliga a que la venta lo <b>copie en
   * su línea</b> en lugar de releerlo del catálogo — esa copia está declarada y todavía no
   * construida (`requirements/mv.md` §5.4).
   */
  @Enumerated(EnumType.STRING)
  @Column(name = "implementation", nullable = false, length = 20)
  private ProductImplementation implementation;

  @Column(name = "created_at", nullable = false, updatable = false)
  private OffsetDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;

  @Column(name = "deleted_at")
  private OffsetDateTime deletedAt;

  /** Exigido por JPA. */
  protected Product() {}

  /**
   * Registra un producto, siempre inactivo.
   *
   * <p><b>El estado no se recibe</b> (`CA-PM-068`): nace {@link ProductStatus#INACTIVO} y solo
   * `RF-PM-005` puede publicarlo. Que no haya forma de pasarlo por argumento es lo que lo hace
   * verificable.
   *
   * <p><b>La condición cruzada de `RN-PM-002` se comprueba aquí, en los dos sentidos</b>, y no solo
   * en el esquema: un upgrade sin destino es inservible, y un bot <b>con</b> destino promete un
   * cambio de nivel que nadie va a aplicar. La segunda mitad es la que se olvida, y es la peligrosa
   * — no falla, promete.
   *
   * <p><b>`RN-PM-016` se comprueba igual pero tiene una sola mitad</b>: el icono sobra en un bot y
   * no falta nunca en un upgrade.
   *
   * @param ahora instante del alta, inyectado para que la prueba pueda fijarlo
   */
  public static Product create(
      UUID id,
      String code,
      ProductType type,
      String name,
      String description,
      String icon,
      UUID sourceMembershipId,
      UUID targetMembershipId,
      BigDecimal price,
      BigDecimal purchasePrice,
      UUID currencyId,
      Integer validityDays,
      ProductScope scope,
      ProductImplementation implementation,
      OffsetDateTime ahora) {

    Product producto = new Product();
    producto.id = id;
    producto.code = normalizarCodigo(code);
    producto.type = type;
    producto.name = recortar(name);
    producto.description = recortar(description);
    verificarTipoYMembresias(type, sourceMembershipId, targetMembershipId);
    producto.icon = normalizarIcono(icon);
    verificarTipoEIcono(type, producto.icon);
    // `RN-PM-034`: en el alta no puede haber portada —llega después, con
    // `RF-PM-014`—, de modo que un upgrade necesita el icono. El bot no entra.
    verificarQuePuedePintarse(type, producto.icon, null, "VAL-018");
    // Los enlaces no entran aquí: son filas de `product_links` y los escribe el
    // caso de uso con `ProductLinkRepository`, en la misma transacción. El
    // agregado no los conoce (`RN-PM-048`).
    producto.sourceMembershipId = sourceMembershipId;
    producto.targetMembershipId = targetMembershipId;
    producto.price = price;
    // Ausente y nulo significan LO MISMO aquí, y ahí se aparta del alcance y de
    // la implementación: omitirlo no deja ninguna decisión sin tomar, porque un
    // producto se registra antes de comprarse y el costo se declara cuando se
    // conoce (`RF-PM-004`).
    producto.purchasePrice = purchasePrice;
    producto.currencyId = currencyId;
    producto.validityDays = validityDays;
    producto.scope = scope;
    producto.implementation = implementation;
    producto.status = ProductStatus.INACTIVO;
    producto.createdAt = ahora;
    producto.updatedAt = ahora;
    return producto;
  }

  /**
   * Publica el producto (`RF-PM-005`).
   *
   * <p><b>Devuelve si hubo cambio, y no lanza si ya estaba activo</b> (`FA-001`): quien pulsa dos
   * veces el mismo botón no ha hecho nada malo, y rechazarlo obligaría a la interfaz a consultar el
   * estado antes de cada pulsación. El valor devuelto es lo que decide si se audita — un evento por
   * una petición que no cambió nada convertiría el registro en ruido.
   *
   * <p><b>Aquí no se comprueba `RN-PM-004`</b> —un solo upgrade activo por destino—: eso mira a
   * <b>otras</b> filas y el agregado solo conoce la suya. Vive en el caso de uso y, sobre todo, en
   * {@code uq_products_upgrade_target}.
   *
   * @return {@code true} si el producto pasó de inactivo a activo
   */
  public boolean activate(OffsetDateTime ahora) {
    return cambiarEstado(ProductStatus.ACTIVO, ahora);
  }

  /**
   * Retira el producto de la oferta sin sacarlo del catálogo (`RF-PM-005`).
   *
   * <p><b>Desactivar no es eliminar</b>: la fila sigue viva, sigue apareciendo en el catálogo y
   * puede volver a publicarse. Lo que cambia es que deja de ofrecerse.
   *
   * @return {@code true} si el producto pasó de activo a inactivo
   */
  public boolean deactivate(OffsetDateTime ahora) {
    return cambiarEstado(ProductStatus.INACTIVO, ahora);
  }

  private boolean cambiarEstado(ProductStatus destino, OffsetDateTime ahora) {
    if (status == destino) {
      return false;
    }
    status = destino;
    updatedAt = ahora;
    return true;
  }

  /**
   * Corrige lo corregible y <b>devuelve qué cambió de verdad</b> (`RF-PM-004`).
   *
   * <p><b>El diff lo devuelve quien aplica el cambio</b>, y no el caso de uso comparando antes y
   * después: reconstruirlo fuera obliga a copiar cinco valores previos y a acordarse de cada campo
   * nuevo que se añada. Aquí, un campo que no entre en el diff es un campo que no se auditará, y
   * eso se ve en la misma línea en que se asigna.
   *
   * <p><b>Los campos ausentes no se tocan</b> y los presentes con nulo se aplican <b>donde el nulo
   * es una orden</b>: la descripción y la vigencia admiten vaciarse; el nombre no, y su nulo lo
   * rechaza el caso de uso antes de llegar aquí.
   *
   * <p><b>{@code updatedAt} solo se mueve si algo cambió</b>: una petición que no cambia nada no es
   * un cambio, y moverla haría creer que alguien tocó el producto.
   *
   * @return los campos que cambiaron, cada uno con {@code before} y {@code after}. Vacío si la
   *     petición no cambió nada
   */
  public Map<String, Object> update(
      Patchable<String> nuevoNombre,
      Patchable<String> nuevaDescripcion,
      Patchable<String> nuevoIcono,
      Patchable<BigDecimal> nuevoPrecio,
      Patchable<BigDecimal> nuevoPrecioDeCompra,
      Patchable<UUID> nuevaMoneda,
      Patchable<Integer> nuevaVigencia,
      Patchable<ProductScope> nuevoAlcance,
      Patchable<ProductImplementation> nuevaImplementacion,
      OffsetDateTime ahora) {

    Map<String, Object> cambios = new LinkedHashMap<>();

    if (nuevoNombre.presente()) {
      String valor = recortar(nuevoNombre.valor());
      if (!java.util.Objects.equals(valor, name)) {
        cambios.put("name", Map.of("before", texto(name), "after", texto(valor)));
        name = valor;
      }
    }
    if (nuevaDescripcion.presente()) {
      String valor = recortar(nuevaDescripcion.valor());
      if (!java.util.Objects.equals(valor, description)) {
        cambios.put("description", Map.of("before", texto(description), "after", texto(valor)));
        description = valor;
      }
    }
    if (nuevoIcono.presente()) {
      // El nulo explícito ES una orden: vacía el icono, como en la descripción.
      // Por eso se normaliza y se comprueba ANTES de mirar si cambió — un
      // `" "` que llega como icono es un vaciado, no un valor con formato malo.
      String valor = normalizarIcono(nuevoIcono.valor());
      verificarTipoEIcono(type, valor);
      // `RN-PM-034`: se mira EL ESTADO QUE QUEDA, no el que había — un upgrade
      // con portada vacía el icono sin queja; sin portada, no. Y un upgrade
      // viejo sin icono ni portada que envíe `icon: null` recibe el rechazo,
      // que es lo que `pm.md` §5.2.9 acepta para lo ya registrado.
      verificarQuePuedePintarse(type, valor, coverImageId, "VAL-010");
      if (!java.util.Objects.equals(valor, icon)) {
        cambios.put("icon", Map.of("before", texto(icon), "after", texto(valor)));
        icon = valor;
      }
    }
    // Los enlaces se corrigen EN BLOQUE y fuera de este método: la colección que
    // llega es la que queda, y quien la compara con lo guardado es el caso de
    // uso, que es quien tiene el repositorio. Su diff entra en `cambios` desde
    // allí, con el conjunto entero en `before` y en `after` (`RN-PM-048`).
    if (nuevoPrecio.presente() && nuevoPrecio.valor() != null) {
      BigDecimal valor = nuevoPrecio.valor();
      // `compareTo` y no `equals`: `10.00` y `10.0000` son el mismo precio con
      // distinta escala, y `equals` los daría por distintos — el registro se
      // llenaría de cambios que no cambian nada.
      if (price.compareTo(valor) != 0) {
        cambios.put(
            "price", Map.of("before", price.toPlainString(), "after", valor.toPlainString()));
        price = valor;
      }
    }
    // EL NULO EXPLICITO SI LO VACIA, al revés que el precio del sistema: la
    // columna admite nulo y ese nulo SIGNIFICA «no se conoce el costo», de modo
    // que «bórralo» tiene un estado al que llevar el producto. Va con la
    // descripción, el icono y la vigencia, no con `price`.
    //
    // Y VACIARLO NO ES PONERLO A CERO: uno dice «no sé cuánto costó» y el otro
    // «no costó nada». Los dos casos son alcanzables desde aquí y no se
    // confunden — el cero entra por la rama de abajo, con su `compareTo`.
    if (nuevoPrecioDeCompra.presente()) {
      BigDecimal valor = nuevoPrecioDeCompra.valor();
      if (!mismoImporte(purchasePrice, valor)) {
        cambios.put(
            "purchase_price", Map.of("before", importe(purchasePrice), "after", importe(valor)));
        purchasePrice = valor;
      }
    }
    if (nuevaMoneda.presente() && nuevaMoneda.valor() != null) {
      UUID valor = nuevaMoneda.valor();
      if (!valor.equals(currencyId)) {
        cambios.put(
            "currency_id", Map.of("before", currencyId.toString(), "after", valor.toString()));
        currencyId = valor;
      }
    }
    if (nuevaVigencia.presente()) {
      Integer valor = nuevaVigencia.valor();
      if (!java.util.Objects.equals(valor, validityDays)) {
        cambios.put(
            "validity_days", Map.of("before", numero(validityDays), "after", numero(valor)));
        validityDays = valor;
      }
    }
    // LAS DOS SE CORRIGEN, y el nulo explícito NO las vacía: son obligatorias
    // en la columna, de modo que «bórralo» no tiene ningún estado al que llevar
    // el producto. Quien lo envía recibe un 400 del caso de uso ANTES de llegar
    // aquí; este método solo trata el caso con valor.
    if (nuevoAlcance.presente() && nuevoAlcance.valor() != null) {
      ProductScope valor = nuevoAlcance.valor();
      if (valor != scope) {
        cambios.put("scope", Map.of("before", scope.name(), "after", valor.name()));
        scope = valor;
      }
    }
    if (nuevaImplementacion.presente() && nuevaImplementacion.valor() != null) {
      ProductImplementation valor = nuevaImplementacion.valor();
      if (valor != implementation) {
        cambios.put(
            "implementation", Map.of("before", implementation.name(), "after", valor.name()));
        implementation = valor;
      }
    }

    if (!cambios.isEmpty()) {
      updatedAt = ahora;
    }
    return cambios;
  }

  /**
   * El nulo en el registro de auditoría va como texto y no como ausencia.
   *
   * <p>{@code Map.of} <b>rechaza los nulos</b>, y aunque los admitiera, una clave que desaparece
   * del JSON haría indistinguible «se vació la descripción» de «no se tocó la descripción» — que es
   * justo la distinción que este requerimiento existe para conservar.
   */
  private static String texto(String valor) {
    return valor == null ? "" : valor;
  }

  /**
   * Dos importes que pueden ser nulos, comparados por <b>valor</b> y no por escala.
   *
   * <p><b>{@code compareTo} y no {@code equals}</b>, por lo mismo que en el precio del sistema:
   * {@code 10.00} y {@code 10.0000} son el mismo importe con distinta escala, y {@code equals} los
   * daría por distintos — el registro de auditoría se llenaría de cambios que no cambian nada.
   *
   * <p><b>Y el nulo entra en la comparación</b>, porque aquí sí es un valor: vaciar un precio
   * público que no existía no es un cambio, y ponerle cero a uno vacío sí lo es.
   */
  private static boolean mismoImporte(BigDecimal uno, BigDecimal otro) {
    if (uno == null || otro == null) {
      return uno == otro;
    }
    return uno.compareTo(otro) == 0;
  }

  /**
   * El importe en el registro de auditoría va como texto, y el nulo como cadena vacía.
   *
   * <p>Lo primero porque {@code BigDecimal} serializado a JSON puede perder la escala; lo segundo
   * porque {@code Map.of} rechaza los nulos y, aunque los admitiera, una clave que desaparece haría
   * indistinguible «se vació el precio de compra» de «no se tocó».
   */
  private static String importe(BigDecimal valor) {
    return valor == null ? "" : valor.toPlainString();
  }

  private static Object numero(Integer valor) {
    return valor == null ? "" : valor;
  }

  /**
   * Retira el producto del catálogo (`RF-PM-006`, `RN-PM-010`).
   *
   * <p><b>El estado NO se toca</b>, y no es un olvido: `CA-PM-052` exige que el registro de
   * eliminación diga si el producto <b>estaba a la venta</b> cuando se retiró. Desactivarlo «de
   * paso» haría que todos los registros dijeran «inactivo» y ese dato dejaría de significar nada —
   * la salvaguarda habría destruido justo la evidencia que protege.
   *
   * <p><b>Lo que sí se mueve es {@code updatedAt}</b>: la fila cambió, y la marca de modificación
   * es de la fila y no del estado comercial del producto.
   *
   * <p><b>No es idempotente</b>: retirar dos veces con dos motivos distintos dejaría el segundo
   * escrito sobre un hecho que ocurrió antes y por otra razón. Quien lo llama comprueba antes que
   * no esté ya retirado; aquí se devuelve si hubo cambio para que ese fallo no dependa de recordar
   * comprobarlo.
   *
   * @return {@code true} si el producto pasó de vivo a retirado
   */
  public boolean delete(OffsetDateTime ahora) {
    if (deletedAt != null) {
      return false;
    }
    deletedAt = ahora;
    updatedAt = ahora;
    return true;
  }

  public boolean estaRetirado() {
    return deletedAt != null;
  }

  /**
   * El estado completo del producto, para el registro de auditoría (Art. V.13).
   *
   * <p><b>Vive aquí y no en cada caso de uso</b> porque los dos que la usan —el alta y el retiro—
   * tienen que decir lo mismo: si cada uno armara su mapa, el registro de creación y el de
   * eliminación describirían el mismo producto con claves distintas, y comparar los dos —que es
   * para lo que existen— dejaría de ser posible.
   *
   * <p><b>Las claves usan el nombre de la columna</b> y no el del campo Java: el registro se lee
   * contra el esquema, no contra el código, y quien lo consulte años después tendrá lo primero.
   *
   * <p>El precio va como texto y no como número: {@code BigDecimal} serializado a JSON puede perder
   * la escala, y en un registro de auditoría {@code 49.99} y {@code 49.990} no son lo mismo.
   */
  public Map<String, Object> instantanea() {
    Map<String, Object> estado = new LinkedHashMap<>();
    estado.put("code", code);
    estado.put("type", type.name());
    estado.put("name", name);
    estado.put("description", description);
    estado.put(
        "target_membership_id", targetMembershipId == null ? null : targetMembershipId.toString());
    estado.put(
        "source_membership_id", sourceMembershipId == null ? null : sourceMembershipId.toString());
    estado.put("price", price.toPlainString());
    // ENTRA AUNQUE NO SE COBRE, y no por simetría: es el único sitio donde
    // queda escrito CUÁNTO COSTÓ un producto cuyo costo después se corrige, y
    // sin él una revisión de márgenes no tendría contra qué contrastarse. Nulo
    // cuando no se conoce — `LinkedHashMap` sí lo admite, al revés que
    // `Map.of`. Los eventos anteriores al 12-09-2026 llevan `public_price`.
    estado.put("purchase_price", purchasePrice == null ? null : purchasePrice.toPlainString());
    // `video_url` estuvo en esta instantánea entre el 14-09-2026 y el
    // 22-09-2026. Los enlaces entran ahora desde el caso de uso, que es quien
    // los tiene, y con su propia clave `links`.
    // Como las membresías: el identificador como texto, nulo cuando no hay portada.
    estado.put("cover_image_id", coverImageId == null ? null : coverImageId.toString());
    estado.put("currency_id", currencyId.toString());
    estado.put("validity_days", validityDays);
    estado.put("status", status.name());
    estado.put("scope", scope.name());
    estado.put("implementation", implementation.name());
    return estado;
  }

  /**
   * ¿Tiene descripción con la que publicarse? (`RN-PM-014`)
   *
   * <p>Vive en el agregado y no en el caso de uso porque la respuesta depende de cómo se normaliza
   * la descripción al escribirla: {@link #recortar} deja en nulo la que solo trae espacios, de modo
   * que preguntar por el nulo aquí es preguntar por lo mismo que se guardó.
   */
  public boolean tieneDescripcion() {
    return description != null && !description.isBlank();
  }

  /**
   * Recorta el código y lo pasa a mayúsculas, y rechaza lo que no cumpla el formato.
   *
   * <p><b>Valida en el dominio y no solo en el DTO.</b> El {@code @Pattern} del DTO atiende a quien
   * llega por HTTP; esta comprobación atiende a cualquier otro camino —una siembra, otro caso de
   * uso— y es la que hace que un código mal formado no pueda existir dentro del modelo.
   */
  private static String normalizarCodigo(String valor) {
    String normalizado = valor == null ? null : valor.trim().toUpperCase(Locale.ROOT);
    if (normalizado == null || !PATRON_CODIGO.matcher(normalizado).matches()) {
      String mensaje =
          "El código solo admite letras mayúsculas, dígitos y guion bajo, y debe empezar por letra.";
      throw new ValidationException(
          "VAL-010", mensaje, List.of(new FieldError("code", "VAL-010", mensaje)));
    }
    return normalizado;
  }

  /**
   * `RN-PM-002`, en los dos sentidos.
   *
   * <p><b>Es pública porque el caso de uso la ejecuta ANTES de buscar nada</b> (`plan.md` §4, paso
   * 2). Comprobar que un upgrade TRAE destino no es lo mismo que comprobar que ese destino EXISTE,
   * y hacerlas juntas reportaría un upgrade sin destino como «la membresía no existe» —un `422`
   * sobre un dato que el actor nunca envió— en lugar del `400` que le corresponde. La regla sigue
   * viviendo en un solo sitio: {@link #create} también la llama.
   *
   * <p>El `CHECK` del esquema dice lo mismo, y esta comprobación no es redundante: la restricción
   * produciría un fallo de integridad —un {@code 500}— donde corresponde un {@code 400} que diga
   * <b>cuál</b> de las dos mitades se incumplió.
   */
  public static void verificarTipoYMembresias(ProductType tipo, UUID origen, UUID destino) {
    if (tipo.exigeDestino()) {
      // LAS DOS, y con su propio campo en el error: con dos membresías, un
      // mensaje que no distingue cuál falta obliga a probar las dos.
      if (origen == null) {
        String mensaje = "Un producto de upgrade debe declarar su membresía de origen.";
        throw new ValidationException(
            "VAL-007", mensaje, List.of(new FieldError("sourceMembershipId", "VAL-007", mensaje)));
      }
      if (destino == null) {
        String mensaje = "Un producto de upgrade debe declarar su membresía destino.";
        throw new ValidationException(
            "VAL-007", mensaje, List.of(new FieldError("targetMembershipId", "VAL-007", mensaje)));
      }
      // `RN-PM-017` NO SE COMPRUEBA AQUÍ, y desde el 07-09-2026 no queda de ella
      // ni una mitad en este agregado. Decía «el origen no puede ser el
      // destino», y eso es exactamente lo que la RENOVACIÓN admite: un
      // `ORO → ORO` vende TIEMPO y no nivel (`requirements/pm.md` §5.2.3).
      //
      // Lo que queda de la regla —«el origen no está por encima»— necesita el
      // `level` de dos filas de `memberships`, que este agregado no conoce ni
      // debe. Vive entera en `RegisterProductService.verificarOrigen`, y desde
      // `V61` TAMPOCO tiene una restricción detrás: es el único sitio.
      return;
    }

    // NINGUNA. Esta mitad es la que se olvida y la peligrosa: un bot con
    // membresía no falla — PROMETE un cambio de nivel que nadie va a aplicar.
    if (destino != null) {
      String mensaje = "Un producto de tipo bot no puede declarar membresía destino.";
      throw new ValidationException(
          "VAL-008", mensaje, List.of(new FieldError("targetMembershipId", "VAL-008", mensaje)));
    }
    if (origen != null) {
      String mensaje = "Un producto de tipo bot no puede declarar membresía de origen.";
      throw new ValidationException(
          "VAL-008", mensaje, List.of(new FieldError("sourceMembershipId", "VAL-008", mensaje)));
    }
  }

  /**
   * Pone o reemplaza la portada (`RF-PM-014`).
   *
   * <p><b>Sin condición de tipo ni de estado</b>: subir una portada nunca deja al producto peor de
   * lo que estaba, y por eso esta es la única de las tres operaciones de la portada que {@code
   * RN-PM-034} no mira. Siempre hay cambio, porque cada subida estrena identificador.
   */
  public CambioDePortada asignarPortada(UUID nueva, OffsetDateTime ahora) {
    UUID anterior = coverImageId;
    coverImageId = nueva;
    updatedAt = ahora;
    return CambioDePortada.de(anterior, nueva);
  }

  /**
   * Quita la portada (`RF-PM-015`).
   *
   * <p><b>Primero «¿hay portada?» y después la regla</b>, y el orden no es un detalle: un upgrade
   * anterior al 14-09-2026 sin icono ni portada tiene que recibir «no hay nada que quitar» y no un
   * rechazo por algo que esta operación no puede arreglar (`spec.md` §8). Sin portada, devuelve un
   * diff vacío y no toca {@code updatedAt}: «quítala» sobre un producto sin portada ya ha
   * conseguido lo que quería.
   *
   * <p>Con portada, `RN-PM-034`: un upgrade sin icono no se queda sin nada que pintar (`VAL-002`,
   * que nombra {@code icon} —lo que falta— y no la portada —lo que se pide—). El bot se la quita
   * siempre.
   */
  public CambioDePortada quitarPortada(OffsetDateTime ahora) {
    if (coverImageId == null) {
      return CambioDePortada.ninguno();
    }
    verificarQuePuedePintarse(type, icon, null, "VAL-002");
    UUID anterior = coverImageId;
    coverImageId = null;
    updatedAt = ahora;
    return CambioDePortada.de(anterior, null);
  }

  /**
   * `RN-PM-034`: un upgrade siempre tiene con qué pintarse — portada o icono.
   *
   * <p><b>Una regla, tres caras, un solo método</b>: el alta (`VAL-018`, sin portada posible), la
   * corrección del icono (`VAL-010`) y el retiro de la portada (`VAL-002` de `RF-PM-015`). Las tres
   * miran <b>el estado que quedaría</b> —el icono y la portada que habrá después de la operación— y
   * viven aquí porque el agregado es el único que ve las dos columnas a la vez.
   *
   * <p><b>El bot no entra, y no es una excepción sino una consecuencia</b>: no declara icono
   * (`RN-PM-016`) y el frontend le pinta el suyo por omisión, de modo que la portada le es opcional
   * sin condición (`requirements/pm.md` §5.2.9).
   *
   * <p>El campo que se nombra es siempre {@code icon}: es lo que falta, aunque lo que se pida sea
   * quitar la portada.
   */
  private static void verificarQuePuedePintarse(
      ProductType tipo, String icono, UUID portada, String codigo) {
    if (tipo.admiteIcono() && icono == null && portada == null) {
      String mensaje =
          switch (codigo) {
            case "VAL-018" ->
                "Un producto de upgrade debe declarar su icono mientras no tenga" + " portada.";
            case "VAL-010" ->
                "Un upgrade sin portada no puede quedarse sin icono: suba primero una"
                    + " portada.";
            default ->
                "Un upgrade sin icono no puede quedarse sin portada: declare primero el"
                    + " icono.";
          };
      throw new ValidationException(
          codigo, mensaje, List.of(new FieldError("icon", codigo, mensaje)));
    }
  }

  /**
   * `RN-PM-016`: el icono solo existe en el upgrade.
   *
   * <p><b>Tiene una sola mitad</b>, y ahí se aparta de {@link #verificarTipoYDestino}: un upgrade
   * sin icono es un producto normal, de modo que no hay nada que exigir. Lo que se rechaza es el
   * icono <b>de más</b> — un dato que el frontend pintaría en un sitio donde nadie ha decidido que
   * vaya un icono.
   *
   * <p><b>Es pública por lo mismo que su hermana</b>: el caso de uso del alta la ejecuta antes de
   * buscar el destino en el catálogo de `SP`, para que un icono sobrante no se reporte como «la
   * membresía no existe».
   *
   * <p>El {@code CHECK} del esquema dice lo mismo, y esta comprobación no es redundante: la
   * restricción produciría un fallo de integridad —un {@code 500}— donde corresponde un {@code 400}
   * que nombre el campo.
   *
   * @param icono ya normalizado; el nulo es siempre válido
   */
  public static void verificarTipoEIcono(ProductType tipo, String icono) {
    if (icono != null && !tipo.admiteIcono()) {
      String mensaje = "Un producto de tipo bot no puede declarar icono.";
      throw new ValidationException(
          "VAL-013", mensaje, List.of(new FieldError("icon", "VAL-013", mensaje)));
    }
  }

  /**
   * Recorta, pasa a minúsculas y comprueba la forma (`VAL-012`).
   *
   * <p><b>El vacío se convierte en nulo y no se rechaza</b>, igual que en {@link #recortar}: quien
   * envía {@code ""} está vaciando el icono, y tratarlo como un formato inválido obligaría a
   * distinguir dos formas de borrar lo mismo.
   *
   * <p>Se guarda ya normalizado por lo mismo que el correo en `RF-SP-024`: el dato almacenado es el
   * comparable, y el {@code CHECK} del esquema puede ser una comprobación de forma corriente.
   */
  private static String normalizarIcono(String valor) {
    if (valor == null) {
      return null;
    }
    String normalizado = valor.trim().toLowerCase(Locale.ROOT);
    if (normalizado.isEmpty()) {
      return null;
    }
    if (normalizado.length() > 50 || !PATRON_ICONO.matcher(normalizado).matches()) {
      String mensaje =
          "El icono solo admite minúsculas, dígitos y guion medio, debe empezar por letra y no"
              + " puede exceder 50 caracteres.";
      throw new ValidationException(
          "VAL-012", mensaje, List.of(new FieldError("icon", "VAL-012", mensaje)));
    }
    return normalizado;
  }

  /**
   * Recorta espacios al inicio y al final.
   *
   * <p>Sin este recorte, {@code "Plan Oro "} y {@code "Plan Oro"} serían dos nombres distintos para
   * {@code uq_products_name} y la unicidad se burlaría con un espacio.
   */
  private static String recortar(String valor) {
    if (valor == null) {
      return null;
    }
    String recortado = valor.trim();
    return recortado.isEmpty() ? null : recortado;
  }

  public UUID getId() {
    return id;
  }

  public String getCode() {
    return code;
  }

  public ProductType getType() {
    return type;
  }

  public String getName() {
    return name;
  }

  public String getDescription() {
    return description;
  }

  public String getIcon() {
    return icon;
  }

  /** La imagen de portada. Nulo: no tiene (`RN-PM-033`). */
  public UUID getCoverImageId() {
    return coverImageId;
  }

  public UUID getTargetMembershipId() {
    return targetMembershipId;
  }

  /** De qué membresía sale el upgrade. Nula en los bots. */
  public UUID getSourceMembershipId() {
    return sourceMembershipId;
  }

  public BigDecimal getPrice() {
    return price;
  }

  /**
   * Lo que NEXUS pagó por el producto. Nulo: no se conoce (`RN-PM-023`). <b>No sale de
   * administración</b> (`RN-PM-024`): ninguna lectura pública debe leerlo.
   */
  public BigDecimal getPurchasePrice() {
    return purchasePrice;
  }

  public UUID getCurrencyId() {
    return currencyId;
  }

  public Integer getValidityDays() {
    return validityDays;
  }

  public ProductStatus getStatus() {
    return status;
  }

  /** Hasta dónde se muestra el producto (`RN-PM-019`). */
  public ProductScope getScope() {
    return scope;
  }

  /** Si lo comprado se aplica solo o espera autorización (`RN-PM-020`). */
  public ProductImplementation getImplementation() {
    return implementation;
  }

  public OffsetDateTime getCreatedAt() {
    return createdAt;
  }

  public OffsetDateTime getUpdatedAt() {
    return updatedAt;
  }

  public OffsetDateTime getDeletedAt() {
    return deletedAt;
  }
}
