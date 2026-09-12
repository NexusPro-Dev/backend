package com.factech.nexus.modules.commissions.domain.service;

import com.factech.nexus.modules.commissions.application.UserRateProductsResponse;
import com.factech.nexus.modules.commissions.domain.repository.UserRateProductRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * La quinta lectura de `RF-CM-002`: sobre qué productos rige una tasa personalizada (12-09-2026).
 *
 * <p><b>Nació un día después que la asociación que lee.</b> `V85` le dio a la personalizada su
 * tabla de asociación con sus dos escrituras y sin ninguna lectura: la única forma de saber sobre
 * qué regía una excepción era la respuesta de la operación que la asociaba. Por eso devuelve
 * <b>exactamente la misma forma</b> que asociar y desasociar — {@link UserRateProductsResponse} — y
 * lee por el mismo método que ellas ya usaban para responder: una segunda forma para el mismo dato
 * habría obligado al cliente a dos modelos.
 *
 * <p><b>No comprueba que la tasa exista</b>, como su gemela de rol ({@link
 * ListProductAssociationsService#byRate}): una lista vacía es la respuesta correcta a «no tiene
 * asociaciones» y también a «ese identificador no es de nada», y distinguirlas costaría una
 * consulta para no cambiar lo que el cliente hace después.
 */
@Service
public class ListUserRateProductsService {

  private final UserRateProductRepository asociaciones;

  public ListUserRateProductsService(UserRateProductRepository asociaciones) {
    this.asociaciones = asociaciones;
  }

  /** Los productos de esa tasa, por código. Vacío si no rige sobre nada — o si no existe. */
  @Transactional(readOnly = true)
  public UserRateProductsResponse byRate(UUID rateId) {
    return new UserRateProductsResponse(
        rateId,
        asociaciones.asociadosDe(rateId).stream()
            .map(
                fila ->
                    new UserRateProductsResponse.ProductRef(fila.id(), fila.code(), fila.name()))
            .toList());
  }
}
