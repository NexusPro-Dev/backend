package com.factech.nexus.modules.products.domain.models;

import com.factech.nexus.shared.error.FieldError;
import com.factech.nexus.shared.error.ValidationException;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Un enlace de un producto: su tipo, su dirección y un identificador externo (`RN-PM-048`).
 *
 * <p><b>No es una entidad: es el valor de un hueco del producto</b>, como {@link PackageItem}. La
 * pareja producto-tipo es la clave, no hay {@code id} propio y <b>no hay {@code deleted_at}</b>:
 * quitar un enlace lo <b>borra</b>. Un borrado lógico aquí obligaría a que las seis lecturas y la
 * clave llevaran el predicado de los vivos para no publicar un enlace retirado.
 *
 * <p><b>La dirección es obligatoria</b>, y ahí está la diferencia con la columna {@code
 * products.video_url} que esta tabla reemplaza: donde el nulo de aquella significaba «no tiene
 * video», ahora lo significa <b>la ausencia de la fila</b>.
 *
 * <p><b>El sistema no sigue ningún enlace</b>: comprueba la forma y nada más (`pm.md` §5.2.8).
 */
@Entity
@Table(name = "product_links")
public class ProductLink {

  /**
   * Una URL absoluta {@code http} o {@code https} sin espacios.
   *
   * <p>Es la misma expresión que {@code ck_product_links_url_format} —y antes que ella, {@code
   * ck_products_video_url_format}—, para que lo que el dominio admite y lo que el esquema admite
   * sean exactamente lo mismo.
   */
  private static final Pattern PATRON_DIRECCION = Pattern.compile("^https?://\\S+$");

  /** Sin espacios y sin cadena vacía: un espacio partiría el enlace al pegarlo. */
  private static final Pattern PATRON_IDENTIFICADOR = Pattern.compile("^\\S+$");

  /**
   * Lo que no cabe en una dirección a la que se le va a pegar un segmento de ruta.
   *
   * <p>Ver {@link #resolver()}: el identificador va <b>al final</b>, y detrás de un {@code ?} o un
   * {@code #} produciría un enlace roto <b>que responde {@code 200}</b>.
   */
  private static final Pattern PATRON_CADENA_DE_CONSULTA = Pattern.compile("[?#]");

  private static final int LARGO_MAXIMO_DIRECCION = 500;
  private static final int LARGO_MAXIMO_IDENTIFICADOR = 100;

  @EmbeddedId private ProductLinkId id;

  @Column(name = "url", nullable = false, length = 500)
  private String url;

  @Column(name = "external_id", length = 100)
  private String externalId;

  @Column(name = "created_at", nullable = false, updatable = false)
  private OffsetDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;

  /** Exigido por JPA. */
  protected ProductLink() {}

  /**
   * Construye un enlace <b>ya validado</b>, con las comprobaciones del alta y de la corrección, que
   * son las mismas.
   *
   * @param codigoDireccionObligatoria el código de la dirección que falta: `VAL-021` en el alta
   *     (`RF-PM-001` §11) y `VAL-016` en la corrección (`RF-PM-004` §11). <b>Es distinto del de la
   *     forma a propósito</b>: no declarar un enlace y declararlo mal son dos errores, y quien
   *     recibe el rechazo necesita distinguirlos
   * @param codigoDireccion el código de la validación de forma de la dirección: `VAL-017` en el
   *     alta (`RF-PM-001` §11) y `VAL-009` en la corrección (`RF-PM-004` §11)
   * @param codigoIdentificador el de la forma del identificador: `VAL-022` y `VAL-017`
   * @param codigoCruzado el de la incompatibilidad entre identificador y cadena de consulta:
   *     `VAL-023` y `VAL-018`
   * @param indice la posición del enlace en la colección recibida, para que el error diga cuál de
   *     ellos la incumple sin que quien lo lee tenga que adivinarlo
   */
  public static ProductLink create(
      UUID productId,
      ProductLinkType type,
      String url,
      String externalId,
      OffsetDateTime ahora,
      String codigoDireccionObligatoria,
      String codigoDireccion,
      String codigoIdentificador,
      String codigoCruzado,
      int indice) {

    String direccion =
        normalizarDireccion(url, codigoDireccionObligatoria, codigoDireccion, indice);
    String identificador = normalizarIdentificador(externalId, codigoIdentificador, indice);
    verificarQueSePuedePegar(direccion, identificador, codigoCruzado, indice);

    ProductLink enlace = new ProductLink();
    enlace.id = new ProductLinkId(productId, type);
    enlace.url = direccion;
    enlace.externalId = identificador;
    enlace.createdAt = ahora;
    enlace.updatedAt = ahora;
    return enlace;
  }

  /**
   * El enlace <b>resuelto</b>: la dirección con el identificador externo pegado al final
   * (`RN-PM-049`).
   *
   * <p>Es la dirección guardada <b>sin su barra final</b>, más {@code /}, más el identificador; y
   * <b>sin identificador, la dirección tal cual</b>. {@code https://t.me/MiBot} con {@code
   * CUPON_ORO_2026} se publica {@code https://t.me/MiBot/CUPON_ORO_2026}.
   *
   * <p><b>Vive aquí y en ningún otro sitio.</b> Seis lecturas la usan; escrita en cada una, un día
   * dejarían de coincidir — y la que se quedara atrás no fallaría: publicaría un enlace que no
   * lleva a donde debe.
   *
   * <p>No normaliza nada más: ni mayúsculas, ni parámetros. Lo que se guardó es lo que se escribió,
   * porque un enlace que el sistema «arregla» puede dejar de resolver.
   */
  public String resolver() {
    if (externalId == null) {
      return url;
    }
    String base = url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    return base + "/" + externalId;
  }

  /** La instantánea de auditoría del enlace, tal como se guardó. */
  public Map<String, Object> instantanea() {
    Map<String, Object> estado = new LinkedHashMap<>();
    estado.put("type", id.getType().name());
    estado.put("url", url);
    estado.put("external_id", externalId);
    return estado;
  }

  /**
   * Si este enlace vale lo mismo que el otro, <b>sin mirar los instantes</b>.
   *
   * <p>Lo usa `RF-PM-004` para que <b>enviar el mismo conjunto que ya estaba no registre
   * evento</b>: una corrección que no cambia nada no es una corrección.
   */
  public boolean mismoValorQue(ProductLink otro) {
    return otro != null
        && id.getType() == otro.id.getType()
        && url.equals(otro.url)
        && java.util.Objects.equals(externalId, otro.externalId);
  }

  private static String normalizarDireccion(
      String valor, String codigoObligatoria, String codigo, int indice) {
    String recortado = valor == null ? null : valor.trim();
    if (recortado == null || recortado.isEmpty()) {
      // La dirección es obligatoria: quitar un enlace es NO DECLARAR su tipo, no
      // enviarlo vacío. `VAL-021` en el alta y `VAL-016` en la corrección.
      String mensaje = "La dirección del enlace es obligatoria.";
      throw new ValidationException(
          codigoObligatoria,
          mensaje,
          List.of(new FieldError(campo(indice, "url"), codigoObligatoria, mensaje)));
    }
    if (recortado.length() > LARGO_MAXIMO_DIRECCION
        || !PATRON_DIRECCION.matcher(recortado).matches()) {
      String mensaje =
          "La dirección del enlace debe ser una dirección absoluta http o https, sin espacios y de"
              + " hasta 500 caracteres.";
      throw new ValidationException(
          codigo, mensaje, List.of(new FieldError(campo(indice, "url"), codigo, mensaje)));
    }
    return recortado;
  }

  private static String normalizarIdentificador(String valor, String codigo, int indice) {
    String recortado = valor == null ? null : valor.trim();
    if (recortado == null || recortado.isEmpty()) {
      return null;
    }
    if (recortado.length() > LARGO_MAXIMO_IDENTIFICADOR
        || !PATRON_IDENTIFICADOR.matcher(recortado).matches()) {
      String mensaje =
          "El identificador externo no admite espacios ni puede exceder 100 caracteres.";
      throw new ValidationException(
          codigo, mensaje, List.of(new FieldError(campo(indice, "externalId"), codigo, mensaje)));
    }
    return recortado;
  }

  private static void verificarQueSePuedePegar(
      String direccion, String identificador, String codigo, int indice) {
    if (identificador == null || !PATRON_CADENA_DE_CONSULTA.matcher(direccion).find()) {
      return;
    }
    String mensaje = "Un enlace con identificador externo no admite una dirección con ? ni #.";
    throw new ValidationException(
        codigo, mensaje, List.of(new FieldError(campo(indice, "url"), codigo, mensaje)));
  }

  /**
   * El campo del error, con el índice del enlace dentro de la colección.
   *
   * <p>Sin el índice, una petición con dos enlaces obligaría a quien la envió a probar los dos para
   * saber cuál falló. Es lo mismo que `VAL-004` hace con los dos precios.
   */
  private static String campo(int indice, String propiedad) {
    return "links[" + indice + "]." + propiedad;
  }

  public ProductLinkType getType() {
    return id.getType();
  }

  public UUID getProductId() {
    return id.getProductId();
  }

  public String getUrl() {
    return url;
  }

  public String getExternalId() {
    return externalId;
  }
}
