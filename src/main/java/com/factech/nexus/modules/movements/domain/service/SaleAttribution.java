package com.factech.nexus.modules.movements.domain.service;

import com.factech.nexus.modules.movements.domain.models.SaleTypeStatus;
import com.factech.nexus.modules.system.users.application.ClientCatalog;
import com.factech.nexus.modules.system.users.application.ClientCatalog.ClientView;
import com.factech.nexus.modules.system.users.application.ClientCatalog.SellerView;
import java.util.List;

/**
 * A quién se atribuye una venta al registrarla, y con qué estado nace (`RN-MV-034`, 23-09-2026).
 *
 * <h2>Una sola pieza, y a propósito</h2>
 *
 * <p>Hasta el 23-09-2026 {@code RegisterSaleService} y {@code BuyPackageService} tenían cada uno su
 * {@code resolverVendedor}, y los dos copiaban lo que {@code ClientCatalog.sellerOf} devolvía. Con
 * la regla nueva lo que hay que hacer es <b>contar</b>, y dos servicios que cuentan por su cuenta
 * acaban contando distinto: el día que uno mire solo el principal, la tienda y la oficina
 * atribuirían la misma compra de forma distinta sin que nada fallara.
 *
 * <h2>La regla</h2>
 *
 * <ul>
 *   <li><b>Por el enlace</b> ({@link #delEnlace}): el dueño del enlace, {@code VALIDADO}, aunque la
 *       compra deje al cliente con varios vendedores (`RN-MV-025`).
 *   <li><b>Un vendedor</b> en {@code client_sellers}: él, {@code VALIDADO}.
 *   <li><b>Varios</b>: nadie, {@code VALIDAR_COMISIONES}. Escoger el principal sería decidir en
 *       silencio a quién se le paga una venta que pudo traer otro; lo decide `RF-MV-016`.
 *   <li><b>Ninguno</b>: quien compra no es cliente de nadie —un agente, la cúspide— y rige
 *       `RN-MV-003` como estaba: su superior vigente, o él mismo. {@code VALIDADO}.
 * </ul>
 */
final class SaleAttribution {

  private final ClientCatalog clientes;

  SaleAttribution(ClientCatalog clientes) {
    this.clientes = clientes;
  }

  /**
   * Lo que se decidió: el vendedor de cada línea —nulo si hay que elegirlo— y el estado.
   *
   * <p>Un vendedor nulo y {@code VALIDAR_COMISIONES} van siempre juntos, y el constructor lo exige:
   * una atribución que dijera «validada» sin vendedor produciría una venta que {@code Movement}
   * rechaza, y es mejor que el error aparezca aquí, donde se cometió.
   */
  record Atribucion(SellerView vendedor, SaleTypeStatus estado) {
    Atribucion {
      if ((vendedor == null) != (estado == SaleTypeStatus.VALIDAR_COMISIONES)) {
        throw new IllegalArgumentException(
            "Una venta por validar no lleva vendedor, y una validada sí.");
      }
    }
  }

  /** La venta del hotlink: el dueño del enlace, siempre (`RN-MV-025`). */
  static Atribucion delEnlace(SellerView dueno) {
    return new Atribucion(dueno, SaleTypeStatus.VALIDADO);
  }

  /** Cualquier otra entrada: la tienda, el funcionario y el alta por enlace. */
  Atribucion deQuienCompra(ClientView cliente) {
    List<SellerView> suyos = clientes.sellersOf(cliente.id());
    if (suyos.size() == 1) {
      return new Atribucion(suyos.get(0), SaleTypeStatus.VALIDADO);
    }
    if (suyos.size() > 1) {
      return new Atribucion(null, SaleTypeStatus.VALIDAR_COMISIONES);
    }
    SellerView vendedor =
        clientes
            .sellerOf(cliente.id())
            .orElseGet(
                () ->
                    new SellerView(
                        cliente.id(), cliente.username(), cliente.firstName(), cliente.lastName()));
    return new Atribucion(vendedor, SaleTypeStatus.VALIDADO);
  }
}
