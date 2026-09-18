package com.factech.nexus.modules.system.users.application;

import com.factech.nexus.modules.system.users.domain.repository.ClientSellerRepository.ClientSellerRow;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Los vendedores de un cliente (`RF-SP-059` §6.2).
 *
 * <p><b>Envuelto en {@code content}</b>, como las cuentas de broker de una persona: deja sitio a
 * paginar sin romper a nadie. <b>Sin totales</b>: un cliente tiene un puñado de vendedores y esto
 * no se pagina.
 *
 * <p><b>Principal primero.</b> El orden lo fija la consulta y significa algo: el primero de la
 * lista es quien lo trajo.
 */
public record ClientSellersResponse(List<SellerItem> content) {

  public static ClientSellersResponse de(List<ClientSellerRow> filas) {
    return new ClientSellersResponse(filas.stream().map(SellerItem::de).toList());
  }

  /**
   * Lo que se publica de un vendedor, y es lo mismo que publica su hotlink (`RN-PM-022`) más el
   * nombre de usuario, que el cliente ya conoce porque forma parte del enlace que usó.
   *
   * <p><b>Sin identificador, correo, estado ni roles</b>, y es decisión (`spec.md` §14.5): el
   * cliente no tiene ninguna ruta donde usar un identificador ajeno, y un administrador que lo
   * necesite tiene el nombre de usuario y `RF-SP-025`.
   *
   * <p>{@code principal} se deriva de {@code origin} y <b>se publica igualmente</b>: es la pregunta
   * que motivó el requerimiento —«¿quién es mi agente?»— y obligar a cada consumidor del contrato a
   * derivarla es repartir una regla de negocio.
   */
  public record SellerItem(
      @Schema(description = "Nombre de usuario del vendedor, el mismo de su hotlink.")
          String username,
      String firstName,
      String lastName,
      @Schema(
              description =
                  "`REGISTRO` si lo registró —su principal—, `HOTLINK` si le vendió por su enlace.",
              allowableValues = {"REGISTRO", "HOTLINK"})
          String origin,
      @Schema(description = "`true` solo en la fila `REGISTRO`. Hay exactamente una por cliente.")
          boolean principal,
      @Schema(
              description =
                  "Desde cuándo es su vendedor. En las filas que `V20` trajo desde"
                      + " `user_supervisors`, desde cuándo colgaba de él allí.")
          OffsetDateTime linkedAt) {

    static SellerItem de(ClientSellerRow fila) {
      return new SellerItem(
          fila.username(),
          fila.firstName(),
          fila.lastName(),
          fila.origin(),
          fila.esPrincipal(),
          fila.linkedAt());
    }
  }
}
