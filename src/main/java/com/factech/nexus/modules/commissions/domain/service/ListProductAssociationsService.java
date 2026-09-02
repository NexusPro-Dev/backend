package com.factech.nexus.modules.commissions.domain.service;

import com.factech.nexus.modules.commissions.application.ProductAssociationResponse;
import com.factech.nexus.modules.commissions.domain.repository.ProductCommissionRateQueryRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Las dos lecturas de la asociación (`RF-CM-002`).
 *
 * <p><b>Son dos preguntas distintas y las dos hacen falta.</b> «Sobre qué productos rige esta tasa»
 * la hace quien administra el catálogo —y es la que revela una tasa declarada y sin asociar, que no
 * paga nada—; «qué paga este producto a cada rol» la hace quien va a venderlo, o quien revisa por
 * qué una venta pagó lo que pagó.
 *
 * <p><b>Ninguna comprueba que la tasa o el producto existan.</b> Una lista vacía es la respuesta
 * correcta a «no hay asociaciones» y también a «ese identificador no es de nada», y distinguirlas
 * costaría una consulta a otro módulo para no cambiar lo que el cliente hace después.
 */
@Service
public class ListProductAssociationsService {

  private final ProductCommissionRateQueryRepository consultas;

  public ListProductAssociationsService(ProductCommissionRateQueryRepository consultas) {
    this.consultas = consultas;
  }

  /** Sobre qué productos rige esa tasa. */
  @Transactional(readOnly = true)
  public ProductAssociationResponse byRate(UUID commissionRateId) {
    return ProductAssociationResponse.de(consultas.findByRate(commissionRateId));
  }

  /** Qué paga ese producto, y a qué rol. */
  @Transactional(readOnly = true)
  public ProductAssociationResponse byProduct(UUID productId) {
    return ProductAssociationResponse.de(consultas.findByProduct(productId));
  }
}
