package com.factech.nexus.modules.products.domain.repository;

import com.factech.nexus.modules.products.domain.models.ProductLink;
import com.factech.nexus.modules.products.domain.models.ProductLinkType;
import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Repository;

/** {@link ProductLinkRepository} sobre JPA. */
@Repository
public class JpaProductLinkRepository implements ProductLinkRepository {

  private final EntityManager em;

  public JpaProductLinkRepository(EntityManager em) {
    this.em = em;
  }

  @Override
  public List<ProductLink> findByProduct(UUID productId) {
    if (productId == null) {
      return List.of();
    }
    return em.createQuery(
            "SELECT e FROM ProductLink e WHERE e.id.productId = :producto ORDER BY e.id.type",
            ProductLink.class)
        .setParameter("producto", productId)
        .getResultList();
  }

  @Override
  public Map<UUID, List<ProductLink>> findByProducts(Collection<UUID> productIds) {
    return agrupar(consultar(productIds, null));
  }

  @Override
  public Map<UUID, List<ProductLink>> findPublicablesByProducts(Collection<UUID> productIds) {
    // RN-PM-050: el filtro va EN EL PREDICADO. El CUPON_BOT no sale de la base.
    List<ProductLinkType> publicables =
        java.util.Arrays.stream(ProductLinkType.values())
            .filter(ProductLinkType::esMaterialDeVenta)
            .toList();
    return agrupar(consultar(productIds, publicables));
  }

  @Override
  public Map<UUID, String> findResolvedByType(Collection<UUID> productIds, ProductLinkType type) {
    if (type == null) {
      return Map.of();
    }
    Map<UUID, String> resueltos = new HashMap<>();
    for (ProductLink enlace : consultar(productIds, List.of(type))) {
      resueltos.put(enlace.getProductId(), enlace.resolver());
    }
    return resueltos;
  }

  @Override
  public List<ProductLink> replace(UUID productId, List<ProductLink> nuevos) {
    // Dos sentencias y no una por enlace: se borra el conjunto y se inserta el que
    // llega. La colección que llega ES la que queda (`RN-PM-048`).
    em.createQuery("DELETE FROM ProductLink e WHERE e.id.productId = :producto")
        .setParameter("producto", productId)
        .executeUpdate();
    // El DELETE es masivo y no pasa por el contexto de persistencia: sin este
    // `clear` selectivo, un enlace ya cargado seguiría vivo en la sesión y el
    // `persist` siguiente chocaría contra una fila que la base ya no tiene.
    em.flush();
    em.clear();
    return saveAll(nuevos);
  }

  @Override
  public List<ProductLink> saveAll(List<ProductLink> enlaces) {
    if (enlaces == null || enlaces.isEmpty()) {
      return List.of();
    }
    for (ProductLink enlace : enlaces) {
      em.persist(enlace);
    }
    em.flush();
    return List.copyOf(enlaces);
  }

  /**
   * La lectura de todas las variantes, con o sin filtro de tipo.
   *
   * <p><b>Una sentencia</b>, con {@code IN} sobre los identificadores ya resueltos de la página.
   */
  private List<ProductLink> consultar(Collection<UUID> productIds, List<ProductLinkType> tipos) {
    if (productIds == null || productIds.isEmpty() || (tipos != null && tipos.isEmpty())) {
      return List.of();
    }
    String jpql =
        "SELECT e FROM ProductLink e WHERE e.id.productId IN :productos"
            + (tipos == null ? "" : " AND e.id.type IN :tipos")
            + " ORDER BY e.id.productId, e.id.type";
    var consulta =
        em.createQuery(jpql, ProductLink.class)
            .setParameter(
                "productos", productIds instanceof List ? productIds : List.copyOf(productIds));
    if (tipos != null) {
      consulta.setParameter("tipos", tipos);
    }
    return consulta.getResultList();
  }

  private static Map<UUID, List<ProductLink>> agrupar(List<ProductLink> enlaces) {
    if (enlaces.isEmpty()) {
      return Collections.emptyMap();
    }
    Map<UUID, List<ProductLink>> porProducto = new LinkedHashMap<>();
    for (ProductLink enlace : enlaces) {
      porProducto.computeIfAbsent(enlace.getProductId(), id -> new ArrayList<>()).add(enlace);
    }
    return porProducto;
  }

  /** Los identificadores distintos de una colección, para no repetirlos en el {@code IN}. */
  static List<UUID> distintos(Collection<UUID> ids) {
    return ids == null
        ? List.of()
        : ids.stream().filter(java.util.Objects::nonNull).distinct().collect(Collectors.toList());
  }
}
