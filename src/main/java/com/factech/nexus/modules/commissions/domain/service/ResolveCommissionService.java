package com.factech.nexus.modules.commissions.domain.service;

import com.factech.nexus.modules.commissions.application.EffectiveCommissionResponse;
import com.factech.nexus.modules.commissions.domain.repository.CommissionResolutionRepository;
import com.factech.nexus.modules.commissions.domain.repository.CommissionResolutionRepository.ResolvedRate;
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
 * La comisión efectiva de una persona sobre un producto en una fecha (`RF-CM-005`).
 *
 * <p><b>Este caso de uso NO ordena nada.</b> La precedencia de `RN-CM-004` se resuelve en la
 * sentencia —un {@code UNION ALL} con la prioridad en el {@code ORDER BY}—, y aquí solo se
 * determina el rol vendedor y se delega. Si el orden viviera en este flujo de control, una
 * refactorización podría alterarlo <b>sin que nada falle</b>: devolvería un porcentaje plausible,
 * que es el error que este requerimiento existe para evitar.
 *
 * <p><b>El rol se busca ANTES de resolver y puede no existir</b>, y eso es una decisión del modelo
 * nuevo, no un descuido. Desde que las tasas personalizadas dejaron de llevar rol (01-09-2026),
 * <b>quien no vende puede seguir teniendo una viva</b> y cobrando por ella (`cm.md` §5.3). Cortar
 * aquí por «no porta rol vendedor», como hacía la versión anterior, escondería exactamente el
 * efecto que esa decisión produjo.
 *
 * <p><b>El producto retirado se resuelve con normalidad</b>: preguntar qué se pagaba por algo que
 * ya no se vende es legítimo, y es la consulta que una liquidación atrasada necesita.
 */
@Service
public class ResolveCommissionService {

  private final CommissionResolutionRepository resolucion;
  private final UserCatalog usuarios;
  private final SellerRoleCatalog rolesVendedores;
  private final ProductCatalog productos;
  private final Clock reloj;

  @Autowired
  public ResolveCommissionService(
      CommissionResolutionRepository resolucion,
      UserCatalog usuarios,
      SellerRoleCatalog rolesVendedores,
      ProductCatalog productos) {
    this(resolucion, usuarios, rolesVendedores, productos, Clock.systemUTC());
  }

  ResolveCommissionService(
      CommissionResolutionRepository resolucion,
      UserCatalog usuarios,
      SellerRoleCatalog rolesVendedores,
      ProductCatalog productos,
      Clock reloj) {
    this.resolucion = resolucion;
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

    Optional<UUID> rol = rolesVendedores.sellerRoleOf(userId);

    Optional<ResolvedRate> tasa = resolucion.resolve(rol.orElse(null), productId, userId, fecha);

    if (tasa.isPresent()) {
      return EffectiveCommissionResponse.resuelta(tasa.get(), rol.orElse(null), fecha);
    }

    // LOS DOS FINALES SIN TASA NO SON EL MISMO, y ninguno es cero. Sin rol
    // vendedor no es que falte declarar la tasa: es que esa persona no vende.
    // Con rol y sin tasa, casi siempre significa que NADIE LA ASOCIÓ a ese
    // producto (`RN-CM-012`) — la tasa puede existir en el catálogo y no regir.
    if (rol.isEmpty()) {
      return EffectiveCommissionResponse.noComisiona(fecha);
    }

    // NO se devuelve cero: cero es «no comisiona», que es una decisión
    // declarada, y la ausencia es que nadie la tomó. Confundirlas haría
    // indistinguible lo pensado de lo olvidado, y quien consuma esto va a pagar
    // con esa cifra.
    return EffectiveCommissionResponse.sinTarifa(rol.get(), fecha);
  }
}
