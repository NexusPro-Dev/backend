package com.factech.nexus.modules.products.domain.repository;

import com.factech.nexus.modules.products.application.ProductCatalog;
import com.factech.nexus.modules.products.domain.models.Product;
import com.factech.nexus.modules.products.domain.models.ProductLinkType;
import com.factech.nexus.modules.products.domain.models.ProductType;
import com.factech.nexus.modules.system.users.application.CurrentMembershipLookup;
import com.factech.nexus.modules.system.users.application.RegistrableProductLookup;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Adaptador de la interfaz que `PM` <b>publica</b> hacia otros módulos (**D-25**).
 *
 * <p>Se llama {@code Published…} por el mismo motivo que sus equivalentes en `SP`: este es el
 * contrato que <b>cruza la frontera del módulo</b>, distinto de los repositorios internos.
 *
 * <p><b>No filtra por retirado</b>, y devuelve la marca. Es lo que permite a `CM` rechazar declarar
 * una tarifa nueva sobre un producto retirado (`RN-CM-010`) y a la vez <b>resolver con
 * normalidad</b> sobre él (`RF-CM-005`): preguntar qué se pagaba por algo que ya no se vende es
 * legítimo.
 */
@Repository
public class PublishedProductCatalog implements ProductCatalog, RegistrableProductLookup {

  private final EntityManager em;
  private final ProductLinkRepository enlaces;
  private final ProductQueryRepository consultas;
  private final CurrentMembershipLookup membresias;

  public PublishedProductCatalog(
      EntityManager em,
      ProductQueryRepository consultas,
      CurrentMembershipLookup membresias,
      ProductLinkRepository enlaces) {
    this.em = em;
    this.consultas = consultas;
    this.membresias = membresias;
    this.enlaces = enlaces;
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<ProductView> find(UUID id) {
    if (id == null) {
      return Optional.empty();
    }
    return em
        .createQuery("SELECT p FROM Product p WHERE p.id = :id", Product.class)
        .setParameter("id", id)
        .setMaxResults(1)
        .getResultList()
        .stream()
        .findFirst()
        .map(
            producto ->
                new ProductView(
                    producto.getId(),
                    producto.getCode(),
                    producto.getName(),
                    producto.estaRetirado()));
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<BigDecimal> findPrice(UUID id) {
    if (id == null) {
      return Optional.empty();
    }
    return em
        .createQuery("SELECT p.price FROM Product p WHERE p.id = :id", BigDecimal.class)
        .setParameter("id", id)
        .setMaxResults(1)
        .getResultList()
        .stream()
        .findFirst();
  }

  /**
   * La vista de venta del lote (`RF-MV-001` · `T-05`).
   *
   * <p><b>Una sentencia para todo el lote</b>, y ese es el punto: es lo que impide la {@code N+1}
   * que `plan.md` §9 descartó — la que no parece una porque cada llamada sería un método Java.
   *
   * <p><b>Baja a SQL nativo y no a JPQL</b> por lo mismo que {@code findOffer}: hacen falta el
   * nivel de la membresía destino y los decimales de la moneda, que viven en tablas de `SP`. Se
   * leen con un {@code LEFT JOIN} y no cargando sus entidades, que es lo que D-25 impide: aquí no
   * entra ninguna clase de otro módulo, solo columnas.
   *
   * <p><b>Los dos {@code LEFT JOIN} son {@code LEFT} a propósito.</b> Un {@link
   * com.factech.nexus.modules.products.domain.models.ProductType#BOT} no tiene destino, y con un
   * {@code JOIN} interno los bots —que son la mayoría de lo que se vende— desaparecerían del
   * resultado sin error: la venta rechazaría el producto por inexistente.
   */
  @Override
  @Transactional(readOnly = true)
  public List<SaleView> saleViewOf(Collection<UUID> ids) {
    if (ids == null || ids.isEmpty()) {
      return List.of();
    }
    // Se deduplica antes de consultar: una petición con el mismo producto dos
    // veces la rechaza `VAL-006` en la capa de entrada, y este puerto no
    // depende de que eso haya ocurrido ya.
    Set<UUID> unicos = new LinkedHashSet<>(ids);

    List<Tuple> filas =
        em.createNativeQuery(
                """
                SELECT p.id AS id, p.code AS code, p.name AS name,
                       p.description AS description, p.type AS type,
                       p.price AS price,
                       p.currency_id AS c_id, c.code AS c_code,
                       c.decimal_places AS c_decimales,
                       p.validity_days AS validity_days,
                       p.target_membership_id AS m_id, m.level AS m_level,
                       p.implementation AS implementation
                  FROM products p
                  LEFT JOIN memberships m ON m.id = p.target_membership_id
                  LEFT JOIN currencies  c ON c.id = p.currency_id
                 WHERE p.id IN (:ids)
                """,
                Tuple.class)
            .setParameter("ids", unicos)
            .getResultList();

    List<SaleView> resultado = new ArrayList<>(filas.size());
    for (Tuple fila : filas) {
      resultado.add(saleView(fila));
    }
    return resultado;
  }

  /**
   * La fila a {@link SaleView}, escrita una vez.
   *
   * <p>La comparten el lote de `RF-MV-001` y el producto del enlace de `RF-MV-011`: las dos
   * lecturas seleccionan las mismas columnas, y dos mapeos separados divergirían el día que una de
   * las dos gane un campo.
   */
  private static SaleView saleView(Tuple fila) {
    return new SaleView(
        (UUID) fila.get("id"),
        (String) fila.get("code"),
        (String) fila.get("name"),
        (String) fila.get("description"),
        "UPGRADE_MEMBRESIA".equals(fila.get("type")),
        (BigDecimal) fila.get("price"),
        (UUID) fila.get("c_id"),
        (String) fila.get("c_code"),
        ((Number) fila.get("c_decimales")).intValue(),
        entero(fila.get("validity_days")),
        (UUID) fila.get("m_id"),
        entero(fila.get("m_level")),
        (String) fila.get("implementation"));
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<SaleView> hotlinkSaleViewOf(String code) {
    if (code == null || code.isBlank()) {
      return Optional.empty();
    }
    // EL PREDICADO ES EL DE `RF-PM-008`, copiado a proposito y no inventado:
    // activo, no retirado y de alcance HOTLINK o AMBOS. Vender por enlace algo
    // que el enlace no publica seria una puerta trasera al catalogo.
    @SuppressWarnings("unchecked")
    List<Tuple> filas =
        em.createNativeQuery(
                """
                SELECT p.id AS id, p.code AS code, p.name AS name,
                       p.description AS description, p.type AS type,
                       p.price AS price,
                       p.currency_id AS c_id, c.code AS c_code,
                       c.decimal_places AS c_decimales,
                       p.validity_days AS validity_days,
                       p.target_membership_id AS m_id, m.level AS m_level,
                       p.implementation AS implementation
                  FROM products p
                  LEFT JOIN memberships m ON m.id = p.target_membership_id
                  LEFT JOIN currencies  c ON c.id = p.currency_id
                 WHERE upper(p.code) = upper(:codigo)
                   AND p.status = 'ACTIVO'
                   AND p.deleted_at IS NULL
                   AND p.scope IN ('HOTLINK', 'AMBOS')
                """,
                Tuple.class)
            .setParameter("codigo", code)
            .getResultList();

    return filas.stream().findFirst().map(PublishedProductCatalog::saleView);
  }

  /**
   * Qué de ese lote se le ofrece hoy a esa persona (`RF-MV-001` · `T-06`).
   *
   * <p><b>No hay ninguna consulta nueva aquí, y es la mitad del diseño</b>: se resuelve la
   * membresía vigente por el mismo puerto que usa `RF-PM-007` y se pide la oferta al mismo {@code
   * findOffer}. Lo único que este método añade es la intersección con el lote.
   *
   * <p>Escribir un {@code SELECT} propio que filtrara «los que están por encima de su nivel» habría
   * sido más corto y habría creado <b>la segunda definición de la oferta</b>. `RF-PM-007` · `T-20`
   * reescribió {@code findOffer} para coincidir por origen el 07-09-2026, y esta lectura cambió con
   * él sin que nadie la tocara — <b>lo único que hubo que cambiar fue el tipo de una variable</b>.
   * Con una consulta propia, seguiría vendiendo por nivel y nada fallaría.
   *
   * <p><b>Dos consultas y no una</b> —la membresía y la oferta—, que son exactamente las mismas dos
   * que `GetOwnOfferService` hace para responder a la misma pregunta. La oferta completa cabe en
   * memoria: son los upgrades activos más los bots activos, unas decenas (`RF-PM-007` · `plan.md`
   * §10, riesgo 3), y el día que crezca lo hará para las dos lecturas a la vez.
   */
  @Override
  @Transactional(readOnly = true)
  public Set<UUID> offeredTo(UUID userId, Collection<UUID> ids) {
    if (userId == null || ids == null || ids.isEmpty()) {
      return Set.of();
    }
    // Vacío significa «hoy no tiene nivel», y cubre tres casos: quien nunca
    // tuvo membresía, quien la tuvo y venció, y quien no existe. `findOffer`
    // acepta el nulo y devuelve solo los bots, que es la respuesta correcta
    // para los tres (`RF-PM-007` · `FA-001` y `FA-003`).
    //
    // ES EL IDENTIFICADOR Y NO EL NIVEL desde el 07-09-2026, y ese es TODO el
    // cambio que costó aquí la coincidencia por origen: el aviso de arriba
    // decía que esta lectura cambiaría «sin que nadie la toque», y lo único que
    // hubo que tocar fue el tipo de esta variable. Una consulta propia habría
    // seguido vendiendo por nivel, sin que nada fallara.
    UUID membresia = membresias.currentMembershipOf(userId).map(m -> (UUID) m.id()).orElse(null);

    Set<UUID> pedidos = new LinkedHashSet<>(ids);
    return consultas.findOffer(membresia).stream()
        .map(ProductQueryRepository.ProductRow::id)
        .filter(pedidos::contains)
        .collect(Collectors.toCollection(LinkedHashSet::new));
  }

  @Override
  @Transactional(readOnly = true)
  public Set<UUID> publishedByHotlink(Collection<UUID> ids) {
    if (ids == null || ids.isEmpty()) {
      return Set.of();
    }
    return new LinkedHashSet<>(consultas.findPublishedByHotlink(new LinkedHashSet<>(ids)));
  }

  @Override
  public Map<UUID, String> couponLinksOf(Collection<UUID> ids) {
    if (ids == null || ids.isEmpty()) {
      return Map.of();
    }
    // UNA sentencia para todo el lote, y el enlace ya compuesto por
    // `ProductLink.resolver()`: quien llama no sabe —ni tiene que saber— que el
    // identificador externo va pegado al final (`RN-PM-049`).
    return enlaces.findResolvedByType(new LinkedHashSet<>(ids), ProductLinkType.CUPON_BOT);
  }

  /**
   * El producto de un enlace de registro (`RF-SP-045` · `T-05`).
   *
   * <p><b>Este método implementa un puerto de `SP`</b>, al revés que todo lo demás de esta clase, y
   * el motivo está escrito en {@link RegistrableProductLookup}: `SP` no puede importar de `PM` sin
   * abrir un ciclo, de modo que declara lo que necesita y `PM` lo cumple.
   *
   * <p><b>Devuelve vacío en los tres casos que no proceden</b> —no existe, inactivo, retirado— y no
   * los distingue: quien lo consume es un endpoint público.
   *
   * <p><b>Una sentencia con sus dos uniones</b>, y las dos son externas a propósito: un bot no
   * tiene membresías, y con uniones internas desaparecería de esta lectura en lugar de llegar para
   * que el caso de uso lo rechace con `EX-003` — que es lo que el criterio exige.
   */
  @Override
  @Transactional(readOnly = true)
  public Optional<RegistrableProductView> findRegistrable(String codigoOIdentificador) {
    if (codigoOIdentificador == null || codigoOIdentificador.isBlank()) {
      return Optional.empty();
    }

    String valor = codigoOIdentificador.trim();
    UUID comoId = comoUuid(valor);

    List<Tuple> filas =
        em.createNativeQuery(
                """
                SELECT p.id AS id, p.code AS code, p.type AS type,
                       p.source_membership_id AS s_id, s.code AS s_code,
                       p.target_membership_id AS m_id, m.code AS m_code,
                       p.validity_days AS validity_days
                  FROM products p
                  LEFT JOIN memberships s ON s.id = p.source_membership_id
                  LEFT JOIN memberships m ON m.id = p.target_membership_id
                 WHERE p.status = 'ACTIVO'
                   AND p.deleted_at IS NULL
                   AND ( (CAST(:id AS uuid) IS NOT NULL AND p.id = CAST(:id AS uuid))
                      OR (CAST(:id AS uuid) IS NULL AND upper(p.code) = upper(:codigo)) )
                 LIMIT 1
                """,
                Tuple.class)
            .setParameter("id", comoId)
            .setParameter("codigo", valor)
            .getResultList();

    return filas.stream()
        .findFirst()
        .map(
            fila ->
                new RegistrableProductView(
                    (UUID) fila.get("id"),
                    (String) fila.get("code"),
                    ProductType.UPGRADE_MEMBRESIA.name().equals(fila.get("type")),
                    (UUID) fila.get("s_id"),
                    (String) fila.get("s_code"),
                    (UUID) fila.get("m_id"),
                    (String) fila.get("m_code"),
                    entero(fila.get("validity_days"))));
  }

  /**
   * ¿Lo que llega es un identificador o un código?
   *
   * <p><b>Se decide por la forma y no por un parámetro</b>: el enlace lo compone quien lo reparte,
   * y pedirle que además diga qué clase de referencia usó sería trasladarle una decisión nuestra.
   * Un valor que no es un UUID no es un error — es un código.
   */
  private static UUID comoUuid(String valor) {
    try {
      return UUID.fromString(valor);
    } catch (IllegalArgumentException noEsUnIdentificador) {
      return null;
    }
  }

  /**
   * {@code smallint} llega como {@code Short} e {@code integer} como {@code Integer}: se normaliza
   * en un solo sitio, igual que en {@link JpaProductQueryRepository}. El nulo se conserva —una
   * vigencia nula significa «no caduca» y un destino nulo, «es un bot»—, de modo que {@code
   * intValue()} directo lo convertiría en cero.
   */
  private static Integer entero(Object valor) {
    return valor == null ? null : ((Number) valor).intValue();
  }
}
