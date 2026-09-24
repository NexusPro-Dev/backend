package com.factech.nexus.modules.movements.domain.service;

import com.factech.nexus.modules.movements.application.HotlinkPurchaseRequest;
import com.factech.nexus.modules.movements.application.PurchaseResponse;
import com.factech.nexus.modules.movements.application.RegisterSaleRequest;
import com.factech.nexus.modules.products.application.ProductCatalog;
import com.factech.nexus.modules.products.application.ProductCatalog.SaleView;
import com.factech.nexus.modules.system.users.application.ClientCatalog.SellerView;
import com.factech.nexus.modules.system.users.application.ClientSellerBond;
import com.factech.nexus.modules.system.users.application.ClientSellerBond.BondOrder;
import com.factech.nexus.modules.system.users.application.PublicSellerLookup;
import com.factech.nexus.modules.system.users.application.PublicSellerLookup.PublicSellerView;
import com.factech.nexus.shared.error.FieldError;
import com.factech.nexus.shared.error.ResourceNotFoundException;
import com.factech.nexus.shared.error.UnauthorizedException;
import com.factech.nexus.shared.error.UnprocessableEntityException;
import com.factech.nexus.shared.security.CurrentActor;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Comprar un producto por el hotlink de un vendedor (`RF-MV-011`).
 *
 * <h2>Qué hace distinto</h2>
 *
 * <p><b>Dos cosas, y solo dos</b> (`spec.md` §4). El vendedor de la línea es <b>el dueño del
 * enlace</b> y no quien ya le vendía a ese cliente (`RN-MV-025`), y nace el vínculo {@code HOTLINK}
 * en {@code client_sellers} si no existía. Todo lo demás —las nueve verificaciones, el precio y la
 * vigencia congelados, el comprobante— es {@link RegisterSaleService} sin tocar.
 *
 * <h2>El 404 es uno solo, y es una decisión de seguridad</h2>
 *
 * <p>Se hereda de `RF-PM-008`: que el vendedor no exista, que no sea vendedor, que esté inactivo o
 * que el producto no se ofrezca por enlace responden <b>el mismo cuerpo</b>. Distinguirlos
 * convertiría esta ruta en un oráculo para averiguar quién trabaja aquí y con qué nombre de
 * usuario.
 *
 * <h2>Y el vínculo no lo escribe este módulo</h2>
 *
 * <p>`MV` <b>pide</b> a `SP` que vincule, por {@link ClientSellerBond} (`architecture.md` §15.2.1),
 * con el mismo argumento de <b>D-26</b>: qué significa vincular —quién es el principal, que no se
 * toca— es regla de `SP`, y una segunda definición aquí divergiría sin que nada fallara.
 */
@Service
public class BuyByHotlinkService {

  private final RegisterSaleService ventas;
  private final PublicSellerLookup vendedores;
  private final ProductCatalog productos;
  private final ClientSellerBond vinculos;
  private final CurrentActor actor;

  public BuyByHotlinkService(
      RegisterSaleService ventas,
      PublicSellerLookup vendedores,
      ProductCatalog productos,
      ClientSellerBond vinculos,
      CurrentActor actor) {
    this.ventas = ventas;
    this.vendedores = vendedores;
    this.productos = productos;
    this.vinculos = vinculos;
    this.actor = actor;
  }

  /**
   * <b>Una sola transacción</b>: la venta y el vínculo caen juntos o quedan juntos. Un cliente
   * vinculado a un vendedor por una venta que no existe sería una atribución sin respaldo, y una
   * venta por enlace sin vínculo perdería el rastro de por qué se le atribuyó a ese vendedor.
   */
  @Transactional
  public PurchaseResponse buy(String username, String codigo, HotlinkPurchaseRequest peticion) {
    UUID quien =
        actor
            .currentActorId()
            .orElseThrow(() -> new UnauthorizedException("AUTH-001", "Se requiere autenticación."));

    // EL ENLACE PRIMERO Y COMPLETO, con las dos mitades resueltas antes de
    // tocar nada: las dos pueden fallar y las dos fallan igual.
    PublicSellerView dueno =
        vendedores.findSellerByUsername(username).orElseThrow(BuyByHotlinkService::noExiste);
    SaleView producto =
        productos.hotlinkSaleViewOf(codigo).orElseThrow(BuyByHotlinkService::noExiste);

    verificarQueNoEsSuPropioEnlace(quien, dueno.id());

    // El registro recibe la atribución YA DECIDIDA. No lleva bandera ni sabe por
    // dónde entró la petición (`RF-MV-002` · `plan.md` §3.1).
    PurchaseResponse venta =
        ventas.comprarPorElEnlace(
            new RegisterSaleRequest(
                quien,
                peticion == null ? null : peticion.paymentMethodId(),
                List.of(new RegisterSaleRequest.Line(producto.id(), 1)),
                null),
            new SellerView(dueno.id(), null, dueno.firstName(), dueno.lastName()));

    // Y EL VÍNCULO DESPUÉS DE LA VENTA, no antes: su `first_movement_id` es esta
    // venta, de modo que antes de registrarla no hay con qué vincular.
    vinculos.bind(new BondOrder(quien, dueno.id(), venta.id()));

    return venta;
  }

  /**
   * `EX-003`: un vendedor no es su propio cliente.
   *
   * <p>Sin esto, cualquiera con enlace podría acreditarse sus propias compras — y con `RN-MV-025`
   * detrás, además quedaría vinculado a sí mismo en {@code client_sellers}.
   */
  private static void verificarQueNoEsSuPropioEnlace(UUID quienCompra, UUID dueno) {
    if (quienCompra.equals(dueno)) {
      String mensaje = "No se puede comprar por el propio hotlink.";
      throw new UnprocessableEntityException(
          "EX-003", mensaje, List.of(new FieldError("username", "EX-003", mensaje)));
    }
  }

  /**
   * El {@code 404} único de `RF-PM-008`, repetido aquí a propósito.
   *
   * <p><b>No dice cuál de los casos ocurrió.</b> Esa uniformidad es la decisión de seguridad que
   * esta ruta hereda: el enlace es público y cualquiera puede probar nombres de usuario.
   */
  private static ResourceNotFoundException noExiste() {
    return new ResourceNotFoundException("EX-001", "El enlace solicitado no existe.");
  }
}
