package com.factech.nexus.modules.movements.domain.service;

import com.factech.nexus.modules.movements.domain.repository.MovementRepository;
import com.factech.nexus.modules.products.application.ProductCatalog;
import com.factech.nexus.modules.system.users.application.ClientCatalog;
import com.factech.nexus.modules.system.users.application.ClientCatalog.ClientView;
import com.factech.nexus.modules.system.users.application.ClientCatalog.SellerView;
import com.factech.nexus.modules.system.users.application.CurrentMembershipLookup;
import com.factech.nexus.modules.system.users.application.FirstDepositSale;
import com.factech.nexus.modules.system.users.application.PublicSellerLookup;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * La beca del primer depósito (`RN-MV-079`, `RN-SP-079`): la venta {@code BECA → BECA} de quien se
 * asoció a su cuenta de broker desde la plataforma, <b>registrada, confirmada y entregada</b> en la
 * transacción que movió la cuenta a {@code FIRST_DEPOSIT}.
 *
 * <p><b>Decide todo antes de escribir nada.</b> Un «no procede» lanzado como excepción cruzaría un
 * {@code @Transactional} y marcaría para deshacer la transacción del aviso del broker, que se
 * perdería entero. Por eso las cuatro condiciones se preguntan primero, y lo que se lanza después
 * es un defecto, no un caso de negocio.
 *
 * <p><b>La venta es la de siempre</b>: {@link RegisterSaleService} con la atribución fijada, como
 * la compra por el enlace, y {@link ConfirmSaleService} con su entrega y su aviso a `CM`. La
 * entrega fecha el FTD (`RN-CM-036`).
 */
@Service
public class PublishedFirstDepositSale implements FirstDepositSale {

  private static final Logger LOG = LoggerFactory.getLogger(PublishedFirstDepositSale.class);

  /** La membresía del suelo: la única que la beca no rebaja (`RN-SP-079`). */
  private static final String BECA = "BECA";

  /**
   * Solo a quien opera: en {@code FTD_PENDIENTE} su venta del alta la activa el depósito por otro
   * camino (`RN-SP-073`), y a una cuenta bloqueada o inactiva no se le vende.
   */
  private static final String ACTIVO = "ACTIVO";

  private final RegisterSaleService ventas;
  private final ConfirmSaleService confirmacion;
  private final MovementRepository movimientos;
  private final ProductCatalog productos;
  private final ClientCatalog clientes;
  private final CurrentMembershipLookup membresias;
  private final PublicSellerLookup vendedores;

  public PublishedFirstDepositSale(
      RegisterSaleService ventas,
      ConfirmSaleService confirmacion,
      MovementRepository movimientos,
      ProductCatalog productos,
      ClientCatalog clientes,
      CurrentMembershipLookup membresias,
      PublicSellerLookup vendedores) {
    this.ventas = ventas;
    this.confirmacion = confirmacion;
    this.movimientos = movimientos;
    this.productos = productos;
    this.clientes = clientes;
    this.membresias = membresias;
    this.vendedores = vendedores;
  }

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public boolean grantBeca(UUID userId, String sellerUsername) {
    Optional<ClientView> cliente = clientes.findClient(userId);
    if (cliente.isEmpty() || !ACTIVO.equals(cliente.get().status())) {
      return false;
    }
    // Con una membresía vigente superior, no: la beca no la rebaja, y el
    // responsable decidió que tampoco cuenta como FTD (15-10-2026).
    boolean superior =
        membresias
            .currentMembershipOf(userId)
            .map(vigente -> !BECA.equalsIgnoreCase(vigente.code()))
            .orElse(false);
    if (superior) {
      return false;
    }
    Set<UUID> ftd = productos.ftdProductIds();
    // Una por persona: la del alta por enlace también cuenta.
    if (movimientos.hasSaleOfProducts(userId, ftd)) {
      return false;
    }
    Optional<UUID> producto = productos.publishedByHotlink(ftd).stream().findFirst();
    if (producto.isEmpty()) {
      LOG.warn(
          "Beca del primer depósito sin registrar para {}: no hay producto BECA → BECA activo.",
          userId);
      return false;
    }

    SellerView vendedor =
        sellerUsername == null
            ? null
            : vendedores
                .findSellerByUsername(sellerUsername)
                .map(v -> new SellerView(v.id(), null, v.firstName(), v.lastName()))
                .orElse(null);
    UUID venta = ventas.registrarBecaDelDeposito(userId, producto.get(), vendedor);
    confirmacion.confirmInternal(venta);
    return true;
  }
}
