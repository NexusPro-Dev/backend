package com.factech.nexus.modules.movements.domain.service;

import com.factech.nexus.modules.movements.application.SaleDiscountResponse;
import com.factech.nexus.modules.movements.application.SaleLineResponse;
import com.factech.nexus.modules.movements.application.SaleResponse;
import com.factech.nexus.modules.movements.domain.repository.MovementRepository.LineDiscountRow;
import com.factech.nexus.modules.movements.domain.repository.MovementRepository.MovementDetailView;
import com.factech.nexus.modules.movements.domain.repository.MovementRepository.MovementLineRow;
import com.factech.nexus.modules.movements.domain.repository.MovementRepository.MyMovementRow;
import java.util.ArrayList;
import java.util.List;

/**
 * De la vista de detalle del repositorio a {@code SaleResponse}, escrito una vez.
 *
 * <p>Lo usan el detalle propio (`RF-MV-008`) y la confirmación (`RF-MV-003`), y mañana el detalle
 * de administración (`RF-MV-007`): quien registró, quien confirmó y quien consulta ven <b>la misma
 * forma</b>, y esa promesa se sostiene teniendo un solo sitio donde se arma.
 */
final class SaleDetailMapper {

  private SaleDetailMapper() {}

  static SaleResponse de(MovementDetailView detalle) {
    MyMovementRow cabecera = detalle.header();

    List<SaleLineResponse> lineas = new ArrayList<>(detalle.lines().size());
    for (MovementLineRow linea : detalle.lines()) {
      List<SaleDiscountResponse> rebajas = new ArrayList<>(linea.discounts().size());
      for (LineDiscountRow rebaja : linea.discounts()) {
        rebajas.add(
            new SaleDiscountResponse(rebaja.type(), rebaja.value(), rebaja.discountValue()));
      }
      lineas.add(
          new SaleLineResponse(
              linea.productId(),
              linea.productCode(),
              linea.productName(),
              linea.productDescription(),
              linea.quantity(),
              linea.unitPrice(),
              linea.lineAmount(),
              linea.validityDays(),
              linea.lineDiscount(),
              rebajas,
              // Nulo en los tipos de movimiento que no venden nada y, desde el
              // 23-09-2026, en una venta por validar (`RN-MV-034`).
              linea.sellerId() == null
                  ? null
                  : new SaleResponse.Party(
                      linea.sellerId(),
                      linea.sellerUsername(),
                      ListMyMovementsService.nombreCompleto(
                          linea.sellerFirstName(), linea.sellerLastName())),
              linea.implementation(),
              linea.deliveryStatus(),
              linea.deliveredAt(),
              linea.deliveryNote()));
    }

    return new SaleResponse(
        cabecera.id(),
        cabecera.code(),
        cabecera.status(),
        cabecera.typeStatus(),
        new SaleResponse.Party(
            cabecera.userId(),
            cabecera.userUsername(),
            ListMyMovementsService.nombreCompleto(
                cabecera.userFirstName(), cabecera.userLastName())),
        cabecera.packageId(),
        new SaleResponse.Money(cabecera.currencyId(), cabecera.currencyCode()),
        cabecera.paymentMethod(),
        lineas,
        cabecera.totalAmount(),
        cabecera.discountAmount(),
        cabecera.payableAmount(),
        cabecera.occurredAt(),
        cabecera.confirmedAt(),
        cabecera.voidedAt(),
        cabecera.voidReason(),
        cabecera.createdAt());
  }
}
