package com.factech.nexus.modules.system.brokers.domain.service;

import com.factech.nexus.modules.system.brokers.application.BrokerAccountsResponse;
import com.factech.nexus.modules.system.brokers.domain.repository.BrokerAccountQueryRepository;
import com.factech.nexus.modules.system.users.domain.repository.ClientSellerRepository;
import com.factech.nexus.modules.system.users.domain.repository.ClientSellerRepository.ClientSellerRow;
import com.factech.nexus.modules.system.users.domain.repository.UserRepository;
import com.factech.nexus.modules.system.users.domain.repository.UserSupervisor;
import com.factech.nexus.shared.error.ResourceNotFoundException;
import com.factech.nexus.shared.error.UnauthorizedException;
import com.factech.nexus.shared.security.CurrentActor;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Las cuentas de broker de una persona (`RF-SP-055`).
 *
 * <h2>Es la primera lectura del sistema que autoriza por estructura comercial</h2>
 *
 * <p>Hasta hoy {@code user_supervisors} decía a quién se atribuye cada resultado y <b>no concedía
 * alcance de datos</b>: lo declara `V21` —«registrar la estructura no concede alcance de datos»— y
 * lo deja abierto la decisión <b>D-22</b>. Aquí sí lo concede, <b>y solo aquí</b>: `RN-SP-046` lo
 * acota a esta lectura, a un solo nivel y a este dato, y `security.md` §5 lo registra como
 * excepción declarada en lugar de disimularla. <b>D-22 sigue abierta, y ninguna otra lectura puede
 * añadirse citando esta.</b>
 *
 * <h2>La autorización NO está en un {@code @PreAuthorize}, y es deliberado</h2>
 *
 * <p>No es una función del actor sino <b>del par (actor, persona consultada)</b>, y expresarla en
 * SpEL exigiría meter una consulta a la base dentro de una anotación — donde no se prueba, no se
 * lee y no se depura. Vive aquí, en dos pasos: <b>el permiso</b>, y si no, <b>ser el superior
 * vigente</b>.
 *
 * <h2>El {@code 404} cubre dos casos a propósito</h2>
 *
 * <p>«No existe» y «no es tuya» devuelven <b>la misma excepción</b>, no dos parecidas. El actor de
 * esta ruta es <b>un vendedor cualquiera</b>: con un {@code 403} podría recorrer identificadores y
 * saber cuáles corresponden a personas reales. Es la excepción expresa que `security.md` §5 exige
 * justificar, y el mismo razonamiento del hotlink de `RF-PM-008`. Dos excepciones distintas
 * acabarían dando dos cuerpos distintos y el oráculo volvería por la puerta de atrás.
 *
 * <h2>Y «vigente» significa vigente</h2>
 *
 * <p>Se lee {@code findActiveSupervisor}, que ya excluye las filas cerradas. Quien <b>fue</b>
 * superior y ya no lo es recibe {@code 404} el mismo día: el historial de {@code user_supervisors}
 * dice a quién se atribuía cada resultado, no quién puede mirar hoy.
 *
 * <h2>Y «superior vigente» de un CLIENTE se resuelve en otra tabla desde el 18-09-2026</h2>
 *
 * <p>El cliente salió de {@code user_supervisors} (`RN-SP-028` revertida, `RF-SP-059`): su vendedor
 * <b>principal</b> es la fila {@code REGISTRO} de {@code client_sellers}, y es él quien ve sus
 * cuentas (`RN-SP-046`). La pregunta pasa a tener <b>dos mitades</b> —¿es su superior en la tabla
 * de mando? ¿es su principal en la de vínculos?— y cualquiera autoriza. <b>El perímetro no
 * crece</b>: sigue siendo un nivel, siguen siendo las cuentas de broker, y un vendedor solo
 * vinculado por {@code HOTLINK} sigue recibiendo {@code 404} (`CA-SP-707`).
 */
@Service
public class GetBrokerAccountsService {

  /**
   * El permiso que salta la estructura.
   *
   * <p>Propio y no {@code users:read}: aquel gobierna quién puede leer personas, y una cuenta de
   * broker es un dato de otra naturaleza que se debe poder conceder y retirar por separado.
   */
  private static final String PERMISO = "broker-accounts:read";

  private final BrokerAccountQueryRepository cuentas;
  private final UserRepository usuarios;
  private final ClientSellerRepository vinculos;
  private final CurrentActor actor;

  public GetBrokerAccountsService(
      BrokerAccountQueryRepository cuentas,
      UserRepository usuarios,
      ClientSellerRepository vinculos,
      CurrentActor actor) {
    this.cuentas = cuentas;
    this.usuarios = usuarios;
    this.vinculos = vinculos;
    this.actor = actor;
  }

  /**
   * Todo en <b>una transacción de solo lectura</b>.
   *
   * <p>La persona, su superior y las cuentas se leen de la misma foto. Leídos por separado, una
   * reasignación simultánea podría dejar pasar a quien acaba de dejar de ser superior.
   */
  @Transactional(readOnly = true)
  public BrokerAccountsResponse of(UUID userId) {
    UUID quien =
        actor
            .currentActorId()
            .orElseThrow(() -> new UnauthorizedException("AUTH-001", "Se requiere autenticación."));

    // La existencia se comprueba ANTES que el alcance, y las dos acaban en el
    // mismo `404`: el orden solo importa para no consultar la estructura de una
    // persona que no existe.
    if (usuarios.findNotDeletedById(userId).isEmpty()) {
      throw noExiste();
    }

    if (!puedeVer(quien, userId)) {
      throw noExiste();
    }

    return BrokerAccountsResponse.de(cuentas.findByUser(userId));
  }

  /**
   * Permiso, o superior vigente, o principal. En ese orden.
   *
   * <p>El permiso primero porque se resuelve sin tocar la base — {@code CurrentActor} ya trae los
   * permisos efectivos—, y quien lo tiene no necesita que se le busque una estructura que
   * probablemente no tenga. Después la tabla de mando —un vendedor consultado por su superior— y
   * después la de vínculos —un cliente consultado por quien lo registró—. Las dos consultas son por
   * clave y ninguna persona tiene, en la práctica, filas en ambas.
   */
  private boolean puedeVer(UUID quien, UUID userId) {
    if (actor.currentPermissions().contains(PERMISO)) {
      return true;
    }
    Optional<UserSupervisor> superior = usuarios.findActiveSupervisor(userId);
    if (superior.map(UserSupervisor::supervisorId).filter(quien::equals).isPresent()) {
      return true;
    }
    Optional<ClientSellerRow> principal = vinculos.findPrincipalOf(userId);
    return principal.map(ClientSellerRow::sellerId).filter(quien::equals).isPresent();
  }

  /**
   * El mismo mensaje para los dos casos, y esa igualdad ES el requisito.
   *
   * <p>Se construye en un solo sitio para que nadie los separe «para dar mejor información»: dos
   * mensajes distintos vuelven a distinguir «no existe» de «no es tuya», que es justo lo que
   * `CA-SP-634` prohíbe.
   */
  private static ResourceNotFoundException noExiste() {
    return new ResourceNotFoundException("VAL-002", "No existe una persona con ese identificador.");
  }
}
