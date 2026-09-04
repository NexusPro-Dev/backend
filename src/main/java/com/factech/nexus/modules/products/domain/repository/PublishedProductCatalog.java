package com.factech.nexus.modules.products.domain.repository;

import com.factech.nexus.modules.products.application.ProductCatalog;
import com.factech.nexus.modules.products.domain.models.Product;
import com.factech.nexus.modules.system.users.application.CurrentMembershipLookup;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
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
public class PublishedProductCatalog implements ProductCatalog {

  private final EntityManager em;
  private final ProductQueryRepository consultas;
  private final CurrentMembershipLookup membresias;

  public PublishedProductCatalog(
      EntityManager em, ProductQueryRepository consultas, CurrentMembershipLookup membresias) {
    this.em = em;
    this.consultas = consultas;
    this.membresias = membresias;
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
                SELECT p.id AS id, p.code AS code, p.name AS name, p.type AS type,
                       p.price AS price,
                       p.currency_id AS c_id, c.code AS c_code,
                       c.decimal_places AS c_decimales,
                       p.validity_days AS validity_days,
                       p.target_membership_id AS m_id, m.level AS m_level
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
      resultado.add(
          new SaleView(
              (UUID) fila.get("id"),
              (String) fila.get("code"),
              (String) fila.get("name"),
              "UPGRADE_MEMBRESIA".equals(fila.get("type")),
              (BigDecimal) fila.get("price"),
              (UUID) fila.get("c_id"),
              (String) fila.get("c_code"),
              ((Number) fila.get("c_decimales")).intValue(),
              entero(fila.get("validity_days")),
              (UUID) fila.get("m_id"),
              entero(fila.get("m_level"))));
    }
    return resultado;
  }

  /**
   * Qué de ese lote se le ofrece hoy a esa persona (`RF-MV-001` · `T-06`).
   *
   * <p><b>No hay ninguna consulta nueva aquí, y es la mitad del diseño</b>: se resuelve la
   * membresía vigente por el mismo puerto que usa `RF-PM-007` y se pide la oferta al mismo {@code
   * findOffer}. Lo único que este método añade es la intersección con el lote.
   *
   * <p>Escribir un {@code SELECT} propio que filtrara «los que están por encima de su nivel» habría
   * sido más corto y habría creado <b>la segunda definición de la oferta</b>. Cuando `RF-PM-007` ·
   * `T-20` reescriba {@code findOffer} para coincidir por origen, esta lectura cambia con ella sin
   * que nadie la toque; con una consulta propia, seguiría vendiendo por nivel y nada fallaría.
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
    Integer nivel =
        membresias.currentMembershipOf(userId).map(m -> (Integer) m.level()).orElse(null);

    Set<UUID> pedidos = new LinkedHashSet<>(ids);
    return consultas.findOffer(nivel).stream()
        .map(ProductQueryRepository.ProductRow::id)
        .filter(pedidos::contains)
        .collect(Collectors.toCollection(LinkedHashSet::new));
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
