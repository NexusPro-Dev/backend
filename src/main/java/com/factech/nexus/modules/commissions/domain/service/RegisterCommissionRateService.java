package com.factech.nexus.modules.commissions.domain.service;

import com.factech.nexus.modules.commissions.application.CommissionRateResponse;
import com.factech.nexus.modules.commissions.application.RegisterCommissionRateRequest;
import com.factech.nexus.modules.commissions.domain.models.CommissionRate;
import com.factech.nexus.modules.commissions.domain.repository.CommissionRateRepository;
import com.factech.nexus.modules.products.application.ProductCatalog;
import com.factech.nexus.modules.products.application.ProductCatalog.ProductView;
import com.factech.nexus.modules.system.roles.application.RoleCatalog;
import com.factech.nexus.modules.system.roles.application.RoleCatalog.RoleView;
import com.factech.nexus.modules.system.users.application.SellerRoleCatalog;
import com.factech.nexus.modules.system.users.application.UserCatalog;
import com.factech.nexus.modules.system.users.application.UserCatalog.UserView;
import com.factech.nexus.shared.audit.AuditEnums.ChangeAction;
import com.factech.nexus.shared.audit.AuditEvents.ChangeEvent;
import com.factech.nexus.shared.audit.AuditWriter;
import com.factech.nexus.shared.error.BusinessRuleException;
import com.factech.nexus.shared.error.FieldError;
import com.factech.nexus.shared.error.UnprocessableEntityException;
import com.factech.nexus.shared.error.ValidationException;
import com.factech.nexus.shared.persistence.UuidV7Generator;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Alta de una tarifa de comisión (`RF-CM-001`).
 *
 * <p><b>El orden de verificación es el contrato</b> (`plan.md` §4):
 *
 * <ol>
 *   <li>El rol existe y es de tipo vendedor.
 *   <li>El producto existe y no está retirado.
 *   <li>La persona existe <b>y porta el rol</b>.
 *   <li>El solapamiento, que lo resuelve la base.
 * </ol>
 *
 * <p>La persona se comprueba en dos pasos y no en uno: sin ellos, quien envía una persona
 * inexistente leería «no porta ese rol», que es un dato distinto y le haría buscar el error donde
 * no está.
 *
 * <p><b>Las cuatro lecturas de fuera entran por interfaces publicadas</b> (**D-25**), nunca por las
 * tablas de `SP` ni de `PM`. La regla de ArchUnit lo ancla: sin ella la frontera sería una
 * convención.
 */
@Service
public class RegisterCommissionRateService {

  private static final String MODULO = "CM";
  private static final String ENTIDAD = "commission_rates";

  private final CommissionRateRepository tarifas;
  private final RoleCatalog roles;
  private final UserCatalog usuarios;
  private final SellerRoleCatalog rolesVendedores;
  private final ProductCatalog productos;
  private final AuditWriter auditoria;
  private final UuidV7Generator ids;
  private final Clock reloj;

  /**
   * Constructor de producción. La anotación es obligatoria porque la clase declara dos
   * constructores —el segundo existe para que la prueba pueda fijar el reloj— y Spring solo infiere
   * cuando hay uno.
   */
  @Autowired
  public RegisterCommissionRateService(
      CommissionRateRepository tarifas,
      RoleCatalog roles,
      UserCatalog usuarios,
      SellerRoleCatalog rolesVendedores,
      ProductCatalog productos,
      AuditWriter auditoria,
      UuidV7Generator ids) {
    this(tarifas, roles, usuarios, rolesVendedores, productos, auditoria, ids, Clock.systemUTC());
  }

  RegisterCommissionRateService(
      CommissionRateRepository tarifas,
      RoleCatalog roles,
      UserCatalog usuarios,
      SellerRoleCatalog rolesVendedores,
      ProductCatalog productos,
      AuditWriter auditoria,
      UuidV7Generator ids,
      Clock reloj) {
    this.tarifas = tarifas;
    this.roles = roles;
    this.usuarios = usuarios;
    this.rolesVendedores = rolesVendedores;
    this.productos = productos;
    this.auditoria = auditoria;
    this.ids = ids;
    this.reloj = reloj;
  }

  @Transactional
  public CommissionRateResponse register(RegisterCommissionRateRequest peticion) {
    RoleView rol = verificarRol(peticion);
    ProductView producto = verificarProducto(peticion);
    UserView persona = verificarPersona(peticion, rol);

    CommissionRate nueva =
        tarifas.save(
            CommissionRate.create(
                ids.next(),
                peticion.roleId(),
                peticion.productId(),
                peticion.userId(),
                peticion.percentage(),
                peticion.validFrom(),
                peticion.validTo(),
                OffsetDateTime.now(reloj)));

    auditoria.recordChange(
        new ChangeEvent(MODULO, ENTIDAD, nueva.getId(), ChangeAction.CREATE, nueva.instantanea()));

    return CommissionRateResponse.from(nueva, rol, producto, persona);
  }

  /**
   * `RN-CM-001`, en dos mitades que se distinguen.
   *
   * <p>Que el rol no exista es un dato equivocado —`422`—; que exista y no sea vendedor es un dato
   * que <b>no vale para esto</b> —`400`—. Devolver lo mismo haría que quien escribió bien el
   * identificador buscara el error donde no está.
   */
  private RoleView verificarRol(RegisterCommissionRateRequest peticion) {
    RoleView rol =
        roles
            .find(peticion.roleId())
            .orElseThrow(
                () ->
                    new UnprocessableEntityException(
                        "EX-002",
                        "El rol indicado no existe.",
                        List.of(new FieldError("roleId", "EX-002", "El rol indicado no existe."))));

    if (!rol.esVendedor()) {
      String mensaje = "Solo los roles de tipo vendedor pueden llevar comisión.";
      throw new ValidationException(
          "EX-001", mensaje, List.of(new FieldError("roleId", "EX-001", mensaje)));
    }
    return rol;
  }

  /** `RN-CM-002` y `RN-CM-010`: el retirado se distingue del inexistente. */
  private ProductView verificarProducto(RegisterCommissionRateRequest peticion) {
    if (peticion.productId() == null) {
      return null;
    }
    ProductView producto =
        productos
            .find(peticion.productId())
            .orElseThrow(
                () ->
                    new UnprocessableEntityException(
                        "EX-003",
                        "El producto indicado no existe.",
                        List.of(
                            new FieldError(
                                "productId", "EX-003", "El producto indicado no existe."))));

    if (producto.retired()) {
      String mensaje = "No se pueden declarar tarifas sobre un producto retirado.";
      throw new BusinessRuleException(
          "EX-004", mensaje, List.of(new FieldError("productId", "EX-004", mensaje)));
    }
    return producto;
  }

  /**
   * `RN-CM-003`, y <b>es la mitad que se olvida</b>.
   *
   * <p>Si la persona no porta el rol, la tarifa quedaría registrada y <b>no se aplicaría nunca</b>,
   * sin que nada fallara: no falla, se queda callada. Es el mismo tipo de defecto que la segunda
   * mitad de `RN-PM-002` evita.
   */
  private UserView verificarPersona(RegisterCommissionRateRequest peticion, RoleView rol) {
    if (peticion.userId() == null) {
      return null;
    }
    UserView persona =
        usuarios
            .find(peticion.userId())
            .orElseThrow(
                () ->
                    new UnprocessableEntityException(
                        "EX-005",
                        "La persona indicada no existe.",
                        List.of(
                            new FieldError("userId", "EX-005", "La persona indicada no existe."))));

    Optional<java.util.UUID> suRol = rolesVendedores.sellerRoleOf(peticion.userId());
    if (suRol.isEmpty() || !suRol.get().equals(rol.id())) {
      String mensaje = "Esa persona no porta el rol de la tarifa.";
      throw new UnprocessableEntityException(
          "EX-006", mensaje, List.of(new FieldError("userId", "EX-006", mensaje)));
    }
    return persona;
  }
}
