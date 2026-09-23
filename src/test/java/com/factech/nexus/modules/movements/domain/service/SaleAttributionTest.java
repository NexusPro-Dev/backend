package com.factech.nexus.modules.movements.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.factech.nexus.modules.movements.domain.models.SaleTypeStatus;
import com.factech.nexus.modules.system.users.application.ClientCatalog;
import com.factech.nexus.modules.system.users.application.ClientCatalog.ClientView;
import com.factech.nexus.modules.system.users.application.ClientCatalog.SellerView;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** `RN-MV-034`: uno, varios, ninguno, y el enlace. */
class SaleAttributionTest {

  private final ClientCatalog clientes = mock(ClientCatalog.class);
  private final SaleAttribution atribuciones = new SaleAttribution(clientes);

  private final ClientView cliente =
      new ClientView(UUID.randomUUID(), "cliente", "Nombre", "Apellido", "ACTIVO");
  private final SellerView ana = new SellerView(UUID.randomUUID(), "ana", "Ana", "Ruiz");
  private final SellerView pedro = new SellerView(UUID.randomUUID(), "pedro", "Pedro", "Gil");

  @Test
  @DisplayName("Un vendedor: él, VALIDADO")
  void uno() {
    when(clientes.sellersOf(cliente.id())).thenReturn(List.of(ana));

    SaleAttribution.Atribucion atribucion = atribuciones.deQuienCompra(cliente);

    assertThat(atribucion.vendedor()).isEqualTo(ana);
    assertThat(atribucion.estado()).isEqualTo(SaleTypeStatus.VALIDADO);
  }

  @Test
  @DisplayName("Varios: nadie, VALIDAR_COMISIONES — y NO se escoge el principal")
  void varios() {
    when(clientes.sellersOf(cliente.id())).thenReturn(List.of(ana, pedro));

    SaleAttribution.Atribucion atribucion = atribuciones.deQuienCompra(cliente);

    assertThat(atribucion.vendedor()).isNull();
    assertThat(atribucion.estado()).isEqualTo(SaleTypeStatus.VALIDAR_COMISIONES);
    // El principal existe, y mirarlo sería la tentación: no se mira.
    verify(clientes, never()).sellerOf(cliente.id());
  }

  @Test
  @DisplayName("Ninguno: su superior vigente, VALIDADO")
  void ningunoConSuperior() {
    when(clientes.sellersOf(cliente.id())).thenReturn(List.of());
    when(clientes.sellerOf(cliente.id())).thenReturn(Optional.of(pedro));

    SaleAttribution.Atribucion atribucion = atribuciones.deQuienCompra(cliente);

    assertThat(atribucion.vendedor()).isEqualTo(pedro);
    assertThat(atribucion.estado()).isEqualTo(SaleTypeStatus.VALIDADO);
  }

  @Test
  @DisplayName("Ninguno y sin superior: él mismo, VALIDADO (RN-MV-003)")
  void ningunoSinSuperior() {
    when(clientes.sellersOf(cliente.id())).thenReturn(List.of());
    when(clientes.sellerOf(cliente.id())).thenReturn(Optional.empty());

    SaleAttribution.Atribucion atribucion = atribuciones.deQuienCompra(cliente);

    assertThat(atribucion.vendedor().id()).isEqualTo(cliente.id());
    assertThat(atribucion.estado()).isEqualTo(SaleTypeStatus.VALIDADO);
  }

  @Test
  @DisplayName("Por el enlace: el dueño, VALIDADO, sin contar a nadie (RN-MV-025)")
  void delEnlace() {
    SaleAttribution.Atribucion atribucion = SaleAttribution.delEnlace(pedro);

    assertThat(atribucion.vendedor()).isEqualTo(pedro);
    assertThat(atribucion.estado()).isEqualTo(SaleTypeStatus.VALIDADO);
  }

  @Test
  @DisplayName("El vendedor nulo y VALIDAR_COMISIONES van siempre juntos")
  void coherencia() {
    assertThatThrownBy(() -> new SaleAttribution.Atribucion(null, SaleTypeStatus.VALIDADO))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new SaleAttribution.Atribucion(ana, SaleTypeStatus.VALIDAR_COMISIONES))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
