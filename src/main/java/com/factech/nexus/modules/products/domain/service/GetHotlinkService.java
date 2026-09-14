package com.factech.nexus.modules.products.domain.service;

import com.factech.nexus.modules.products.application.ExchangeRef;
import com.factech.nexus.modules.products.application.HotlinkResponse;
import com.factech.nexus.modules.products.application.HotlinkResponse.CurrencyRef;
import com.factech.nexus.modules.products.application.HotlinkResponse.MembershipBadge;
import com.factech.nexus.modules.products.application.HotlinkResponse.ProductRef;
import com.factech.nexus.modules.products.application.HotlinkResponse.SellerRef;
import com.factech.nexus.modules.products.application.ProductPrice;
import com.factech.nexus.modules.products.domain.models.ProductType;
import com.factech.nexus.modules.products.domain.repository.ProductQueryRepository;
import com.factech.nexus.modules.products.domain.repository.ProductQueryRepository.ProductRow;
import com.factech.nexus.modules.system.users.application.PublicSellerLookup;
import com.factech.nexus.modules.system.users.application.PublicSellerLookup.PublicSellerView;
import com.factech.nexus.shared.error.ResourceNotFoundException;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * El hotlink público: producto y vendedor en una llamada (`RF-PM-008`).
 *
 * <h2>Un solo punto de salida para el {@code 404}, y esa es la mitad de la seguridad</h2>
 *
 * <p>Los <b>seis</b> casos que no proceden —nombre de usuario inexistente, persona que no es fuerza
 * comercial, código inexistente, producto inactivo, retirado o de alcance {@code TIENDA}— lanzan
 * <b>la misma</b> excepción, con el mismo código y el mismo mensaje.
 *
 * <p><b>Distinguirlos convertiría el endpoint en un oráculo</b>: bastaría fijar un código bueno e
 * ir variando el nombre de usuario para saber <b>quién existe</b> — y en su variante peor,
 * distinguir «no existe» de «existe y no es vendedor» publicaría <b>quién es cliente</b>.
 *
 * <p>Es tentador escribir dos mensajes porque ayuda a depurar. `CA-PM-134` compara el <b>cuerpo
 * entero</b> de las seis respuestas justamente para que ese día no llegue sin que nadie se entere.
 */
@Service
public class GetHotlinkService {

  private final ProductQueryRepository productos;
  private final PublicSellerLookup vendedores;
  private final ProductExchangeResolver conversiones;

  @Autowired
  public GetHotlinkService(
      ProductQueryRepository productos,
      PublicSellerLookup vendedores,
      ProductExchangeResolver conversiones) {
    this.productos = productos;
    this.vendedores = vendedores;
    this.conversiones = conversiones;
  }

  @Transactional(readOnly = true)
  public HotlinkResponse hotlink(String username, String code) {
    // El vendedor PRIMERO, y no por gusto: los dos pueden fallar y el tercer
    // paso —la tasa— solo se paga si los dos anteriores salieron.
    PublicSellerView vendedor =
        vendedores.findSellerByUsername(username).orElseThrow(GetHotlinkService::noExiste);

    ProductRow producto =
        productos.findPublishedByCode(code).orElseThrow(GetHotlinkService::noExiste);

    return new HotlinkResponse(
        new SellerRef(vendedor.firstName(), vendedor.lastName()), producto(producto));
  }

  /**
   * El {@code 404} único.
   *
   * <p><b>No dice cuál de los seis casos ocurrió</b>, y esa uniformidad es la decisión de seguridad
   * del requerimiento (`spec.md` §10).
   */
  private static ResourceNotFoundException noExiste() {
    return new ResourceNotFoundException("EX-001", "El enlace solicitado no existe.");
  }

  private ProductRef producto(ProductRow fila) {
    CurrencyRef moneda = new CurrencyRef(fila.currencyCode(), fila.currencyDecimalPlaces());
    return new ProductRef(
        fila.id(),
        fila.code(),
        ProductType.valueOf(fila.type()),
        fila.name(),
        fila.description(),
        fila.icon(),
        fila.validityDays(),
        // Solo en el upgrade: un bot no lleva ninguna, y llega presente y nula.
        fila.targetMembershipId() == null
            ? null
            : new MembershipBadge(
                fila.targetMembershipCode(),
                fila.targetMembershipName(),
                fila.targetMembershipColor()),
        // UN importe (`RN-PM-024`, 12-09-2026): el precio de compra es el costo
        // de NEXUS, `ProductRef` no tiene dónde ponerlo y la consulta no lo
        // selecciona. Es la lectura SIN TOKEN: lo que salga por aquí no se
        // retira después.
        ProductPrice.enLaEscalaDe(fila.price(), fila.currencyDecimalPlaces()),
        moneda,
        conversion(fila),
        // `RN-PM-031`, sin token: viene en la misma sentencia que el producto.
        fila.rating());
  }

  /**
   * La conversión a la moneda de casa, si la hay.
   *
   * <p><b>Devuelve nulo en dos casos y ninguno es un error</b>: cuando el producto ya está en la
   * moneda por omisión —no hay nada que convertir, y `RN-SP-029` impide que exista una tasa de una
   * moneda a sí misma— y cuando <b>nadie declaró una tasa vigente</b>.
   *
   * <p><b>Ese segundo caso NO es un {@code 404}</b>, y es la decisión que más fácil se hace al
   * revés: responder «no encontrado» escondería un producto perfectamente vendible porque nadie
   * declaró una tasa, y el enlace dejaría de funcionar sin que nada lo explicara.
   */
  private ExchangeRef conversion(ProductRow fila) {
    // El cálculo salió de aquí el 08-09-2026, al dejar de ser cosa del hotlink:
    // lo hace `ProductExchangeResolver` para las cuatro lecturas. Se convierte
    // SIEMPRE `price` (12-09-2026): fuera de administración es el único importe
    // que se muestra, y el de compra nunca se convierte.
    return conversiones.para(List.of(fila.currencyId())).de(fila.currencyId(), fila.price());
  }
}
