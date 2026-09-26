package com.factech.nexus.modules.system.users.application;

import com.factech.nexus.modules.system.users.domain.repository.ClientSellerRepository.ClientSellerRow;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

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
   * <p><b>Sin roles</b>, y es lo único que queda de la acotación original (`spec.md` §10): qué
   * papeles porta alguien es administración de accesos y no tiene que ver con «quién me vende».
   *
   * <p><b>El identificador y el correo entraron el 24-09-2026</b>, y el primero no por comodidad:
   * la decisión que los dejaba fuera decía que «un cliente no tiene ninguna ruta donde usar un
   * identificador ajeno», y esa premisa se rompió el 23-09-2026 — `RF-MV-016` publicó {@code POST
   * /movements/{id}/seller-assignments}, que asigna los vendedores de una venta <b>eligiéndolos
   * entre los del cliente</b> y los recibe por {@code sellerId}. Esta lista es ese conjunto.
   *
   * <p>{@code principal} se deriva de {@code origin} y <b>se publica igualmente</b>: es la pregunta
   * que motivó el requerimiento —«¿quién es mi agente?»— y obligar a cada consumidor del contrato a
   * derivarla es repartir una regla de negocio.
   */
  // `companyPhone` viaja EN NULO y no ausente, y para eso hace falta esta
  // anotacion: `application.yml` fija `default-property-inclusion: non_null`,
  // de modo que sin ella el campo DESAPARECE de la fila del vendedor que no lo
  // declaro — y "no lo tiene" seria indistinguible de "esta version del
  // contrato no lo manda". Es el mismo apartado que hace `SaleResponse` con su
  // vendedor nulo, y lo fija `CA-SP-798` comprobando el JSON en crudo.
  @JsonInclude(JsonInclude.Include.ALWAYS)
  public record SellerItem(
      @Schema(
              description =
                  "Identificador del vendedor. **Desde el 24-09-2026**, y es el que"
                      + " `POST /movements/{id}/seller-assignments` (`RF-MV-016`) recibe como"
                      + " `sellerId`: los vendedores de una venta se eligen entre los del"
                      + " cliente, y esta lista es ese conjunto.")
          UUID id,
      @Schema(description = "Nombre de usuario del vendedor, el mismo de su hotlink.")
          String username,
      @Schema(
              description =
                  "Correo del vendedor. **Desde el 24-09-2026**, y se publica también en"
                      + " `/users/me/sellers`: para quien compra es un canal de contacto"
                      + " comercial más, como el teléfono de empresa.")
          String email,
      String firstName,
      String lastName,
      @Schema(
              description =
                  "Teléfono de EMPRESA del vendedor, el canal por el que la empresa quiere que"
                      + " se le contacte. **Presente y nulo** cuando no lo declaró: ausente y nulo"
                      + " significarían lo mismo y uno de los dos sobra. El teléfono personal no"
                      + " se publica aquí.")
          String companyPhone,
      @Schema(
              description =
                  "Estado de la cuenta del vendedor. **Puede ser el de una cuenta eliminada**:"
                      + " el vínculo sobrevive al vendedor y esa fila ya salía, pero hasta el"
                      + " 22-09-2026 era indistinguible de la de un vendedor activo.")
          String status,
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
          fila.sellerId(),
          fila.username(),
          fila.email(),
          fila.firstName(),
          fila.lastName(),
          fila.companyPhone(),
          fila.status(),
          fila.origin(),
          fila.esPrincipal(),
          fila.linkedAt());
    }
  }
}
