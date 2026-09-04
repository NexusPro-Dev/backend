package com.factech.nexus.modules.movements.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.factech.nexus.modules.movements.application.RegisterSaleRequest;
import com.factech.nexus.modules.movements.domain.repository.MovementRepository;
import com.factech.nexus.modules.movements.domain.repository.MovementRepository.MovementTypeView;
import com.factech.nexus.modules.movements.domain.repository.MovementRepository.PaymentMethodView;
import com.factech.nexus.modules.products.application.ProductCatalog;
import com.factech.nexus.modules.products.application.ProductCatalog.SaleView;
import com.factech.nexus.modules.system.users.application.ClientCatalog;
import com.factech.nexus.modules.system.users.application.ClientCatalog.ClientView;
import com.factech.nexus.modules.system.users.application.ClientCatalog.SellerView;
import com.factech.nexus.modules.system.users.application.CurrentMembershipLookup;
import com.factech.nexus.modules.system.users.application.CurrentMembershipLookup.CurrentMembershipView;
import com.factech.nexus.shared.audit.AuditWriter;
import com.factech.nexus.shared.error.BusinessRuleException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * `RN-MV-006` <b>existe por su cuenta</b>, y esta clase es la única que lo demuestra.
 *
 * <h2>Por qué hace falta una prueba unitaria para esto</h2>
 *
 * <p>La oferta de `RF-PM-007` ya excluye lo que no sube de nivel, de modo que por HTTP <b>nunca se
 * llega</b> a la comprobación de `MV`: la petición muere antes, en `EX-004`. `RegisterSaleIT` lo
 * comprueba así, y con razón — es lo que hoy ocurre de verdad.
 *
 * <p>Pero la oferta es una decisión de <b>`PM`</b> y puede ampliarse: el día que se vendan
 * renovaciones del mismo nivel, `RN-MV-007` dejará de implicar `RN-MV-006`. Que una venta no baje a
 * nadie de nivel es una regla de <b>`MV`</b>, y no puede depender de que otro módulo siga tomando
 * la misma decisión que hoy.
 *
 * <p>Con el catálogo simulado la oferta se amplía a mano, y `EX-005` <b>se alcanza</b>. Sin esta
 * prueba, borrar la comprobación de nivel del caso de uso dejaría la suite entera en verde: es
 * exactamente el defecto que no falla.
 */
class RegisterSaleServiceTest {

  private static final UUID CLIENTE = UUID.randomUUID();
  private static final UUID VENDEDOR = UUID.randomUUID();
  private static final UUID METODO = UUID.randomUUID();
  private static final UUID MONEDA = UUID.randomUUID();
  private static final UUID MEMBRESIA = UUID.randomUUID();

  private MovementRepository movimientos;
  private ProductCatalog productos;
  private ClientCatalog clientes;
  private CurrentMembershipLookup membresias;
  private RegisterSaleService servicio;

  @BeforeEach
  void preparar() {
    movimientos = mock(MovementRepository.class);
    productos = mock(ProductCatalog.class);
    clientes = mock(ClientCatalog.class);
    membresias = mock(CurrentMembershipLookup.class);

    servicio =
        new RegisterSaleService(
            movimientos,
            productos,
            clientes,
            membresias,
            mock(AuditWriter.class),
            java.time.Clock.systemUTC());

    when(clientes.findClient(CLIENTE))
        .thenReturn(Optional.of(new ClientView(CLIENTE, "cliente", "Ana", "Ruiz", "ACTIVO")));
    when(clientes.sellerOf(CLIENTE))
        .thenReturn(Optional.of(new SellerView(VENDEDOR, "vendedor", "Luis", "Paz")));
    when(movimientos.findPaymentMethod(METODO))
        .thenReturn(
            Optional.of(new PaymentMethodView(METODO, "CREDIT_CARD", "Tarjeta de credito", true)));
    when(movimientos.findTypeByCode("VENTA"))
        .thenReturn(Optional.of(new MovementTypeView(UUID.randomUUID(), "VENTA", "VTA")));
  }

  @Test
  @DisplayName("EX-005: un upgrade al MISMO nivel se rechaza, aunque la oferta lo incluya")
  void elUpgradeAlMismoNivel() {
    // El cliente está en el nivel 3 y el producto lleva al 3. Subir es ir a un
    // número MENOR: esto no sube.
    UUID producto = ofrecer(upgrade("UP_IGUAL", 3));
    enNivel(3);

    assertThatThrownBy(() -> servicio.register(peticion(producto, 1)))
        .isInstanceOf(BusinessRuleException.class)
        .hasMessageContaining("UP_IGUAL");

    // Y no se registra nada: se rechaza AL REGISTRAR y no al confirmar, que es
    // lo único que evita cobrarle a alguien por algo que no le da nada.
    verify(movimientos, never()).save(any(), any());
  }

  @Test
  @DisplayName("EX-005: un upgrade a un nivel INFERIOR se rechaza")
  void elUpgradeQueBaja() {
    UUID producto = ofrecer(upgrade("UP_ABAJO", 4));
    enNivel(3);

    assertThatThrownBy(() -> servicio.register(peticion(producto, 1)))
        .isInstanceOf(BusinessRuleException.class);
  }

  @Test
  @DisplayName("El upgrade que SÍ sube se registra")
  void elUpgradeQueSube() {
    UUID producto = ofrecer(upgrade("UP_ARRIBA", 2));
    enNivel(3);

    assertThat(servicio.register(peticion(producto, 1)).status()).isEqualTo("PENDIENTE");
    verify(movimientos).save(any(), any());
  }

  @Test
  @DisplayName("Sin membresía no se rechaza: cualquier destino está por encima de no tener nivel")
  void sinMembresiaNoHayNadaQueComparar() {
    UUID producto = ofrecer(upgrade("UP_ARRIBA", 1));
    when(membresias.currentMembershipOf(CLIENTE)).thenReturn(Optional.empty());

    assertThat(servicio.register(peticion(producto, 1)).code()).startsWith("VTA-");
  }

  @Test
  @DisplayName("Un bot no pasa por la comprobación de nivel: RN-MV-006 no le aplica (FA-001)")
  void elBotNoMiraNiveles() {
    UUID producto = ofrecer(bot("BOT_X"));
    enNivel(4);

    assertThat(servicio.register(peticion(producto, 7)).lines().get(0).quantity()).isEqualTo(7);
    // Nada acota cuántos bots caben en una venta, y ponerle un número sería
    // inventarlo (`spec.md` §13).
  }

  // ---------------------------------------------------------------------------
  // Ayudas
  // ---------------------------------------------------------------------------

  private void enNivel(int nivel) {
    when(membresias.currentMembershipOf(CLIENTE))
        .thenReturn(
            Optional.of(new CurrentMembershipView(MEMBRESIA, "NIVEL_" + nivel, "Nivel", nivel)));
  }

  /** Mete el producto en el catálogo <b>y en la oferta</b>, que es lo que `PM` decide de verdad. */
  private UUID ofrecer(SaleView producto) {
    when(productos.saleViewOf(anyCollection())).thenReturn(List.of(producto));
    when(productos.offeredTo(any(), anyCollection())).thenReturn(Set.of(producto.id()));
    return producto.id();
  }

  private static SaleView upgrade(String codigo, int nivelDestino) {
    return new SaleView(
        UUID.randomUUID(),
        codigo,
        "Ascenso",
        true,
        new BigDecimal("20.0000"),
        MONEDA,
        "USD",
        2,
        30,
        UUID.randomUUID(),
        nivelDestino);
  }

  private static SaleView bot(String codigo) {
    return new SaleView(
        UUID.randomUUID(),
        codigo,
        "Bot",
        false,
        new BigDecimal("10.0000"),
        MONEDA,
        "USD",
        2,
        null,
        null,
        null);
  }

  private static RegisterSaleRequest peticion(UUID producto, int cantidad) {
    return new RegisterSaleRequest(
        CLIENTE, METODO, List.of(new RegisterSaleRequest.Line(producto, cantidad)), null);
  }
}
