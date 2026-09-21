package com.factech.nexus.modules.system.users.domain.repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * `client_sellers`: los vendedores de un cliente (`RN-SP-049`, `RF-SP-059`).
 *
 * <p>Una fila por pareja cliente-vendedor, con el <b>origen</b> del vínculo —{@code REGISTRO},
 * quien lo registró; {@code HOTLINK}, quien le vendió por su enlace— y la venta que lo creó. <b>Sin
 * fin</b>: un vínculo es un hecho y no se cierra. Y <b>sin reasignación</b>: el principal es la
 * fila {@code REGISTRO}, una por cliente —{@code uq_client_sellers_principal}— y para siempre.
 *
 * <p>Desde `V20` (18-09-2026) es <b>la única</b> tabla donde un cliente se relaciona con un
 * vendedor: el cliente <b>no tiene fila</b> en {@code user_supervisors}, que volvió a significar
 * solo mando dentro de la fuerza comercial (`RN-SP-028` revertida). Toda lectura que pregunte
 * «¿quién es el vendedor de este cliente?» pasa por aquí; la que siguiera mirando la tabla de mando
 * no fallaría — <b>devolvería de menos</b>.
 *
 * <p>Sin entidad JPA, como {@code user_supervisors} en {@link JpaUserRepository}: no hay agregado
 * que cargar, solo filas que insertar y proyectar.
 */
public interface ClientSellerRepository {

  /**
   * El vínculo {@code REGISTRO}: quien registró a este cliente, y por tanto su principal.
   *
   * <p>Se escribe <b>antes</b> de la venta del registro, porque la venta resuelve su vendedor con
   * {@code ClientCatalog.sellerOf}, que mira aquí; y se completa después con {@link
   * #attachFirstMovement}. Un segundo {@code REGISTRO} para el mismo cliente lo rechaza el índice
   * único parcial, no este método: la garantía es de la base.
   */
  void registerPrincipal(UUID clientId, UUID sellerId, OffsetDateTime ahora);

  /** Completa el vínculo con la venta que lo creó, una vez que la venta existe. */
  void attachFirstMovement(UUID clientId, UUID sellerId, UUID movementId);

  /** La fila {@code REGISTRO} del cliente, si alguien lo registró. */
  Optional<ClientSellerRow> findPrincipalOf(UUID clientId);

  /**
   * Todos los vendedores del cliente, <b>principal primero</b> y después por fecha de vínculo, la
   * más antigua antes. El orden lo fija la consulta y no quien la llama: significa algo — el
   * primero de la lista es quien lo trajo.
   */
  List<ClientSellerRow> findSellersOf(UUID clientId);

  /**
   * Cuántos clientes <b>no eliminados</b> tiene un vendedor en su cartera, acotados por origen si
   * se pide (`RF-SP-061`). Es la otra columna de la misma tabla: {@link #findSellersOf} mira por
   * {@code client_id}; esto, por {@code seller_id}, con el índice que `RF-SP-059` dejó para ello.
   */
  long countClientsOf(UUID sellerId, String origin);

  /**
   * La cartera de un vendedor, paginada, <b>los vínculos más recientes primero</b> y después por
   * nombre de usuario. El orden lo fija la consulta: la cartera crece por el final y lo nuevo es lo
   * que se atiende (`RF-SP-061` spec §6.2). Los clientes eliminados <b>no salen</b>: para el
   * sistema no existen y su identificador no abriría nada (`FA-007`).
   *
   * @param origin {@code REGISTRO}, {@code HOTLINK} o {@code null} para todos
   */
  List<SellerClientRow> findClientsOf(UUID sellerId, String origin, int offset, int limit);

  /**
   * Un vendedor de un cliente, tal como lo publica `RF-SP-059` §6.2: nombre de usuario, nombre y
   * apellido, origen y desde cuándo. <b>Sin correo, estado ni roles.</b> El identificador viaja
   * aquí porque las autorizaciones lo comparan (`RN-SP-046`), pero la respuesta no lo publica.
   */
  record ClientSellerRow(
      UUID sellerId,
      String username,
      String firstName,
      String lastName,
      String origin,
      OffsetDateTime linkedAt) {

    public static final String REGISTRO = "REGISTRO";

    public boolean esPrincipal() {
      return REGISTRO.equals(origin);
    }
  }

  /**
   * Un cliente de la cartera de un vendedor, tal como lo publica `RF-SP-061` §6.2: <b>con
   * identificador</b> —desde la cartera se abre su ficha— y <b>con estado</b> —una cartera se
   * trabaja—, al contrario que {@link ClientSellerRow} y por la razón inversa.
   */
  record SellerClientRow(
      UUID clientId,
      String username,
      String firstName,
      String lastName,
      String status,
      String origin,
      OffsetDateTime linkedAt) {

    public boolean esPrincipal() {
      return ClientSellerRow.REGISTRO.equals(origin);
    }
  }
}
