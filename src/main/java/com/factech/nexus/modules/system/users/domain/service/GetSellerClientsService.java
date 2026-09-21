package com.factech.nexus.modules.system.users.domain.service;

import com.factech.nexus.modules.system.users.application.ClientOrigin;
import com.factech.nexus.modules.system.users.application.SellerClientItem;
import com.factech.nexus.modules.system.users.domain.repository.ClientSellerRepository;
import com.factech.nexus.modules.system.users.domain.repository.UserRepository;
import com.factech.nexus.shared.error.ResourceNotFoundException;
import com.factech.nexus.shared.error.UnauthorizedException;
import com.factech.nexus.shared.pagination.PageResponse;
import com.factech.nexus.shared.pagination.Pagination;
import com.factech.nexus.shared.security.CurrentActor;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * `RF-SP-061`: la cartera de un vendedor — a quiénes registró, que son suyos, y a quiénes les
 * vendió por su enlace, que son vinculados. La lectura inversa de {@link GetClientSellersService},
 * sobre la misma tabla mirada por la otra columna.
 *
 * <p>Los mismos dos modelos de autorización que `RF-SP-059`, <b>y ninguno es la estructura
 * comercial</b>: {@link #mine} es alcance sobre el actor; {@link #of} es {@code
 * users:read-clients}, resuelto en el controlador antes de tocar la base. El director de un agente
 * <b>no</b> ve la cartera del agente por serlo (D-22 no se amplía; `RN-SP-046` sigue siendo la
 * única excepción, y es sobre cuentas de broker): quien deba ver carteras ajenas porta el permiso.
 *
 * <p>Paginada como `RF-SP-056` y con conteo exacto: una cartera se cuenta en cientos y va por
 * índice. <b>No audita.</b> Es una lectura.
 */
@Service
public class GetSellerClientsService {

  private final ClientSellerRepository vinculos;
  private final UserRepository usuarios;
  private final CurrentActor actor;
  private final Pagination paginacion;

  public GetSellerClientsService(
      ClientSellerRepository vinculos,
      UserRepository usuarios,
      CurrentActor actor,
      Pagination paginacion) {
    this.vinculos = vinculos;
    this.usuarios = usuarios;
    this.actor = actor;
    this.paginacion = paginacion;
  }

  /**
   * La cartera del actor.
   *
   * <p>Quien no es vendedor —un cliente, un funcionario— recibe la página vacía: nadie se registró
   * con su enlace ni le compró por él. No es un error, es una cartera sin filas (`FA-002`).
   */
  @Transactional(readOnly = true)
  public PageResponse<SellerClientItem> mine(String origin, Integer page, Integer size) {
    UUID quien =
        actor
            .currentActorId()
            .orElseThrow(() -> new UnauthorizedException("AUTH-001", "Se requiere autenticación."));
    return cartera(quien, origin, page, size);
  }

  /** La cartera de una persona, por identificador. Exige que exista y no esté eliminada. */
  @Transactional(readOnly = true)
  public PageResponse<SellerClientItem> of(
      UUID sellerId, String origin, Integer page, Integer size) {
    if (usuarios.findNotDeletedById(sellerId).isEmpty()) {
      throw new ResourceNotFoundException(
          "VAL-002", "No existe una persona con ese identificador.");
    }
    return cartera(sellerId, origin, page, size);
  }

  private PageResponse<SellerClientItem> cartera(
      UUID vendedor, String origin, Integer page, Integer size) {
    // Los filtros se validan ANTES de contar: un `origin` inválido es 400 aunque
    // la cartera esté vacía, y la paginación fuera de límites también.
    String origen = ClientOrigin.resolver(origin);
    Pagination.Slice trozo = paginacion.resolver(page, size);
    long total = vinculos.countClientsOf(vendedor, origen);
    return PageResponse.de(
        vinculos.findClientsOf(vendedor, origen, trozo.offset(), trozo.size()).stream()
            .map(SellerClientItem::de)
            .toList(),
        total,
        trozo.page(),
        trozo.size());
  }
}
