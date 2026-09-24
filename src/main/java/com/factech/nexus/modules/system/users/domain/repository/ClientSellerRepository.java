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
  /**
   * Crea el vínculo <b>por hotlink</b> si no existía (`RN-SP-049`, `RF-MV-011`).
   *
   * <p><b>No toca el principal.</b> La fila {@code REGISTRO} es de quien registró al cliente y es
   * inmutable: comprar por el enlace de otro suma un vendedor, no sustituye a nadie.
   *
   * <p><b>Idempotente por el esquema y no por una comprobación previa</b>: la pareja es la clave
   * primaria, de modo que el segundo intento no crea nada. Comprobar antes de insertar sería una
   * carrera, y es justo la que dos compras simultáneas por el mismo enlace producen.
   *
   * @return {@code true} si el vínculo <b>nació aquí</b>; {@code false} si ya estaba. Lo segundo no
   *     es un error —es `FA-001`— y quien llama lo necesita para no auditar un hecho que no ocurrió
   */
  boolean attachByHotlink(UUID clientId, UUID sellerId, UUID movementId, OffsetDateTime ahora);

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
   * Un vendedor de un cliente, tal como lo publica `RF-SP-059` §6.2: identificador, nombre de
   * usuario, correo, nombre y apellido, teléfono de empresa, estado, origen y desde cuándo. <b>Sin
   * roles</b>, que es lo único que queda de la acotación original.
   *
   * <p><b>El identificador ya viajaba aquí</b> —las autorizaciones lo comparan (`RN-SP-046`)— y
   * hasta el 24-09-2026 la respuesta no lo publicaba. Lo publica desde que `RF-MV-016` estrenó una
   * ruta que consume `sellerId` eligiendo entre los vendedores del cliente, que es exactamente esta
   * lista.
   */
  record ClientSellerRow(
      UUID sellerId,
      String username,
      String email,
      String firstName,
      String lastName,
      String companyPhone,
      String status,
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
