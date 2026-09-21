package com.factech.nexus.modules.system.users.domain.service;

import com.factech.nexus.modules.system.users.application.ClientSellersResponse;
import com.factech.nexus.modules.system.users.domain.repository.ClientSellerRepository;
import com.factech.nexus.modules.system.users.domain.repository.UserRepository;
import com.factech.nexus.shared.error.ResourceNotFoundException;
import com.factech.nexus.shared.error.UnauthorizedException;
import com.factech.nexus.shared.security.CurrentActor;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * `RF-SP-059`: los vendedores de un cliente — quién lo registró, que es su principal, y quiénes le
 * vendieron por su enlace.
 *
 * <p>Dos entradas y <b>dos modelos de autorización, y ninguno es la estructura comercial</b>:
 *
 * <ul>
 *   <li>{@link #mine()} es alcance sobre uno mismo, como {@code GET /users/me}: el cliente sale del
 *       token y no hay nada que autorizar más allá de estar autenticado.
 *   <li>{@link #of(UUID)} es {@code users:read}, resuelto en el controlador: quien no lo trae
 *       recibe {@code 403} <b>antes</b> de tocar la base, de modo que aquí solo llega quien puede
 *       leer personas y el {@code 404} del identificador inexistente no revela nada que `RF-SP-025`
 *       no le diera ya.
 * </ul>
 *
 * <p><b>No audita.</b> Es una lectura.
 */
@Service
public class GetClientSellersService {

  private final ClientSellerRepository vinculos;
  private final UserRepository usuarios;
  private final CurrentActor actor;

  public GetClientSellersService(
      ClientSellerRepository vinculos, UserRepository usuarios, CurrentActor actor) {
    this.vinculos = vinculos;
    this.usuarios = usuarios;
    this.actor = actor;
  }

  /**
   * Los vendedores del actor.
   *
   * <p>Quien no es cliente —un vendedor, un funcionario— recibe la colección vacía: nadie lo
   * registró por enlace y nadie le vendió por hotlink. No es un error, es una lista sin filas
   * (`FA-002`).
   */
  @Transactional(readOnly = true)
  public ClientSellersResponse mine() {
    UUID quien =
        actor
            .currentActorId()
            .orElseThrow(() -> new UnauthorizedException("AUTH-001", "Se requiere autenticación."));
    return ClientSellersResponse.de(vinculos.findSellersOf(quien));
  }

  /** Los vendedores de una persona, por identificador. Exige que exista y no esté eliminada. */
  @Transactional(readOnly = true)
  public ClientSellersResponse of(UUID userId) {
    if (usuarios.findNotDeletedById(userId).isEmpty()) {
      throw new ResourceNotFoundException(
          "VAL-002", "No existe una persona con ese identificador.");
    }
    return ClientSellersResponse.de(vinculos.findSellersOf(userId));
  }
}
