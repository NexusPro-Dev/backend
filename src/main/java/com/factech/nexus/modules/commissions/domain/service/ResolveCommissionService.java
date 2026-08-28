package com.factech.nexus.modules.commissions.domain.service;

import com.factech.nexus.modules.commissions.application.EffectiveCommissionResponse;
import com.factech.nexus.modules.commissions.domain.repository.CommissionRateQueryRepository;
import com.factech.nexus.modules.products.application.ProductCatalog;
import com.factech.nexus.modules.system.users.application.SellerRoleCatalog;
import com.factech.nexus.modules.system.users.application.UserCatalog;
import com.factech.nexus.shared.error.FieldError;
import com.factech.nexus.shared.error.UnprocessableEntityException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * La comision efectiva de una persona sobre un producto en una fecha (`RF-CM-005`).
 *
 * <p><b>Este caso de uso NO ordena nada.</b> La precedencia de `RN-CM-004` se resuelve en la
 * sentencia, y aqui solo se determina el rol vendedor y se delega. Si el orden viviera en este
 * flujo de control, una refactorizacion podria alterarlo SIN QUE NADA FALLE — devolveria un
 * porcentaje plausible, que es el error que este requerimiento existe para evitar.
 *
 * <p><b>El producto retirado se resuelve con normalidad</b>: preguntar que se pagaba por algo que
 * ya no se vende es legitimo, y es la consulta que una liquidacion atrasada necesita.
 */
@Service
public class ResolveCommissionService {

  private final CommissionRateQueryRepository consultas;
  private final UserCatalog usuarios;
  private final SellerRoleCatalog rolesVendedores;
  private final ProductCatalog productos;
  private final Clock reloj;

  @Autowired
  public ResolveCommissionService(
      CommissionRateQueryRepository consultas,
      UserCatalog usuarios,
      SellerRoleCatalog rolesVendedores,
      ProductCatalog productos) {
    this(consultas, usuarios, rolesVendedores, productos, Clock.systemUTC());
  }

  ResolveCommissionService(
      CommissionRateQueryRepository consultas,
      UserCatalog usuarios,
      SellerRoleCatalog rolesVendedores,
      ProductCatalog productos,
      Clock reloj) {
    this.consultas = consultas;
    this.usuarios = usuarios;
    this.rolesVendedores = rolesVendedores;
    this.productos = productos;
    this.reloj = reloj;
  }

  @Transactional(readOnly = true)
  public EffectiveCommissionResponse resolve(UUID userId, UUID productId, LocalDate onDate) {
    LocalDate fecha = onDate == null ? LocalDate.now(reloj) : onDate;

    usuarios
        .find(userId)
        .orElseThrow(
            () ->
                new UnprocessableEntityException(
                    "EX-001",
                    "La persona indicada no existe.",
                    List.of(new FieldError("userId", "EX-001", "La persona indicada no existe."))));

    productos
        .find(productId)
        .orElseThrow(
            () ->
                new UnprocessableEntityException(
                    "EX-002",
                    "El producto indicado no existe.",
                    List.of(
                        new FieldError("productId", "EX-002", "El producto indicado no existe."))));

    // Sin rol vendedor no es que falte declarar la tarifa: es que esa persona no
    // vende. Son dos respuestas distintas y el contrato las distingue.
    Optional<UUID> rol = rolesVendedores.sellerRoleOf(userId);
    if (rol.isEmpty()) {
      return EffectiveCommissionResponse.noComisiona(fecha);
    }

    return consultas
        .resolve(rol.get(), productId, userId, fecha)
        .map(fila -> EffectiveCommissionResponse.resuelta(fila, rol.get(), fecha))
        // NO se devuelve cero: cero es «no comisiona», que es una decision
        // declarada, y la ausencia es que nadie la tomo. Confundirlas haria
        // indistinguible lo pensado de lo olvidado, y quien consuma esto va a
        // pagar con esa cifra.
        .orElseGet(() -> EffectiveCommissionResponse.sinTarifa(rol.get(), fecha));
  }
}
