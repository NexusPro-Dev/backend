package com.factech.nexus.modules.movements.domain.service;

import com.factech.nexus.modules.movements.application.RegisterSaleRequest;
import com.factech.nexus.modules.movements.domain.repository.MovementRepository;
import com.factech.nexus.modules.system.users.application.RegistrationSaleRegistrar;
import com.factech.nexus.shared.error.FieldError;
import com.factech.nexus.shared.error.UnprocessableEntityException;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * La venta del registro por enlace, <b>delegada al caso de uso de `RF-MV-001`</b> (`RF-SP-045`).
 *
 * <p><b>Implementa un puerto de `SP`</b>, que es lo que impide el ciclo del grafo: la dirección de
 * los datos se invierte —`MV` sirve a `SP`— y la de la importación no.
 *
 * <p><b>No reimplementa nada</b>, y esa es toda su razón de ser: llama a {@link
 * RegisterSaleService} con la petición que este ya conoce, de modo que la venta del registro pasa
 * por las <b>mismas</b> reglas que la de un funcionario — el producto se le ofrece a esa persona,
 * no la baja de nivel, la moneda es una sola y el método de pago admite ese producto.
 *
 * <p><b>Con una sola diferencia, y está acotada a esta clase</b>: entra por {@code
 * registrarAltaDeCliente}, que exime a la venta del alta de `RN-MV-008`. El registro gratuito crea
 * la cuenta {@code FTD_PENDIENTE} y anota su venta a continuación; con la regla aplicada tal cual,
 * esa venta se rechazaría a sí misma y <b>ninguna alta gratuita sería posible</b>. Lo que
 * `RN-MV-008` prohíbe es que una cuenta a la espera de su depósito <b>siga comprando</b>, y esta
 * venta es anterior a que haya nada que esperar.
 *
 * <p><b>El vendedor no se le pasa y no es un olvido</b>: `RF-MV-001` lo resuelve del <b>superior
 * vigente</b> del cliente, y el registro acaba de colgar a esa persona de quien le compartió el
 * enlace. Pasarlo aquí abriría la puerta a una venta cuyo vendedor no fuera el de la estructura
 * comercial, que es justo lo que `CM` no podría comisionar. Lo que sí se hace es <b>verificar que
 * coinciden</b>, para que un enlace manipulado no pase inadvertido.
 */
@Service
public class PublishedRegistrationSaleRegistrar implements RegistrationSaleRegistrar {

  /** Lo único que un registro puede producir. El catálogo hoy no tiene más. */
  private static final String TIPO_VENTA = "VENTA";

  private final RegisterSaleService ventas;
  private final MovementRepository movimientos;

  public PublishedRegistrationSaleRegistrar(
      RegisterSaleService ventas, MovementRepository movimientos) {
    this.ventas = ventas;
    this.movimientos = movimientos;
  }

  @Override
  @Transactional
  public String registerSale(
      UUID userId,
      UUID productId,
      UUID paymentMethodId,
      String movementTypeCode,
      String sellerUsername) {

    verificarTipo(movementTypeCode);

    // Una sola línea y cantidad uno: un registro compra UN producto, que es el
    // del enlace. Vender varios en el alta sería otro requerimiento.
    var respuesta =
        ventas.registrarAltaDeCliente(
            new RegisterSaleRequest(
                userId,
                paymentMethodId,
                List.of(new RegisterSaleRequest.Line(productId, 1)),
                null));

    verificarVendedor(respuesta, sellerUsername);
    return respuesta.code();
  }

  /**
   * El tipo llega por código y <b>se verifica en lugar de asumirse</b>.
   *
   * <p>Hoy el catálogo solo tiene {@code VENTA}, y un registro no puede producir otra cosa: el día
   * que existan más tipos, mandar aquí un {@code DEPOSITO} tiene que fallar y no colarse como
   * venta. Se rechaza con la misma respuesta que el resto de lo que no procede en el registro.
   */
  private void verificarTipo(String codigo) {
    String pedido = codigo == null ? "" : codigo.trim();

    if (!TIPO_VENTA.equalsIgnoreCase(pedido) || movimientos.findTypeByCode(TIPO_VENTA).isEmpty()) {
      String mensaje = "Los datos del enlace no son válidos.";
      throw new UnprocessableEntityException(
          "EX-010", mensaje, List.of(new FieldError("movement", "EX-010", mensaje)));
    }
  }

  /**
   * El vendedor de la venta —el de su única línea, desde el 16-09-2026— es el que el registro acaba
   * de asignar como superior.
   *
   * <p><b>Desde el 09-09-2026 esta rama NO ES ALCANZABLE desde la petición</b>, y se conserva a
   * propósito. Hasta ese día el cuerpo llevaba <b>dos vendedores</b> —{@code referrer} en el primer
   * nivel y {@code sellerUsername} en el movimiento— y esto existía para que un enlace en el que
   * discreparan <b>fallara</b> en lugar de anotar una venta a nombre de otro. Al quitar el
   * duplicado hay un solo vendedor: el registro lo cuelga como superior y `RN-MV-003` lo vuelve a
   * sacar de ahí, de modo que <b>siempre coinciden</b>.
   *
   * <p><b>Se conserva porque esto es un límite entre módulos</b>, y `MV` no da por buena la palabra
   * de quien lo llama: lo que aquí se comprueba ya no es un cuerpo manipulado sino que la
   * atribución <b>surtió efecto</b> —que `assignSupervisor` escribió y que la estructura comercial
   * resuelve a la misma persona—. El día que una de las dos cosas deje de ser cierta, esto falla en
   * lugar de comisionarle a quien no vendió. Es la misma decisión que `RegisterSaleService` toma
   * con `RN-MV-008`: la regla no se sostiene en que el camino sea inalcanzable, sino en que el caso
   * de uso la compruebe.
   */
  private void verificarVendedor(
      com.factech.nexus.modules.movements.application.SaleResponse venta, String esperado) {

    // Una sola línea (ver arriba), y su vendedor nunca es nulo en una venta
    // (`RN-MV-003`); la comprobación se escribe igual, por lo dicho en el Javadoc.
    var linea = venta.lines().isEmpty() ? null : venta.lines().get(0);
    String real = linea == null || linea.seller() == null ? null : linea.seller().username();

    if (esperado != null && !esperado.isBlank() && !esperado.trim().equalsIgnoreCase(real)) {
      String mensaje = "Los datos del enlace no son válidos.";
      throw new UnprocessableEntityException(
          "EX-010", mensaje, List.of(new FieldError("movement", "EX-010", mensaje)));
    }
  }
}
