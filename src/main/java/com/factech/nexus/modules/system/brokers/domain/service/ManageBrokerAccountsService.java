package com.factech.nexus.modules.system.brokers.domain.service;

import com.factech.nexus.modules.system.brokers.application.AssignBrokerAccountHolderRequest;
import com.factech.nexus.modules.system.brokers.application.BrokerAccountItem;
import com.factech.nexus.modules.system.brokers.application.CreateBrokerAccountRequest;
import com.factech.nexus.modules.system.brokers.application.TeamBrokerAccountItem;
import com.factech.nexus.modules.system.brokers.application.UpdateBrokerAccountRequest;
import com.factech.nexus.modules.system.brokers.domain.models.BrokerAccountKind;
import com.factech.nexus.modules.system.brokers.domain.models.UserBrokerStatus;
import com.factech.nexus.modules.system.brokers.domain.repository.BrokerAccountQueryRepository;
import com.factech.nexus.modules.system.brokers.domain.repository.BrokerAccountWriter;
import com.factech.nexus.modules.system.brokers.domain.repository.BrokerAccountWriter.LockedAccount;
import com.factech.nexus.modules.system.users.domain.repository.BrokerAccountRegistrar;
import com.factech.nexus.modules.system.users.domain.repository.BrokerAccountRegistrar.BrokerRef;
import com.factech.nexus.modules.system.users.domain.repository.UserRepository;
import com.factech.nexus.shared.audit.AuditEnums.ChangeAction;
import com.factech.nexus.shared.audit.AuditEnums.DeletionType;
import com.factech.nexus.shared.audit.AuditEvents.ChangeEvent;
import com.factech.nexus.shared.audit.AuditEvents.DeletionEvent;
import com.factech.nexus.shared.audit.AuditWriter;
import com.factech.nexus.shared.error.BusinessRuleException;
import com.factech.nexus.shared.error.FieldError;
import com.factech.nexus.shared.error.ResourceNotFoundException;
import com.factech.nexus.shared.error.UnauthorizedException;
import com.factech.nexus.shared.error.UnprocessableEntityException;
import com.factech.nexus.shared.error.ValidationException;
import com.factech.nexus.shared.persistence.UuidV7Generator;
import com.factech.nexus.shared.security.CurrentActor;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registrar, editar y eliminar cuentas de broker (`RF-SP-053`, `RF-SP-080`, `RF-SP-081`).
 *
 * <h2>Dos puertas por operación y un solo cuerpo</h2>
 *
 * <p>La puerta propia toma a la persona del token; la de administración, de la ruta, y comprueba
 * que existe. Detrás hay un solo cuerpo, que recibe <b>si quien actúa es el titular</b>, porque es
 * lo único que cambia entre las dos: `RN-SP-067` —no tocar una cuenta con depósito confirmado— se
 * aplica al titular y no a administración.
 *
 * <h2>La cuenta de otro responde {@code 404}</h2>
 *
 * <p>{@link BrokerAccountWriter#lock} solo encuentra la cuenta si es de esa persona. Para el
 * titular, una cuenta ajena es una cuenta que no existe: distinguirlas dejaría averiguar
 * identificadores de cuentas de otros.
 *
 * <h2>Bloquear antes de decidir</h2>
 *
 * <p>Editar y borrar bloquean la fila antes de mirar su estado: sin el bloqueo, el broker podría
 * confirmar el depósito entre la comprobación y la escritura.
 */
@Service
public class ManageBrokerAccountsService {

  private static final String MODULO = "SP";
  private static final String ENTIDAD = "user_brokers";
  private static final int LARGO_MAXIMO = 80;

  private final BrokerAccountWriter cuentas;
  private final BrokerAccountQueryRepository lecturas;
  private final BrokerAccountRegistrar brokers;
  private final UserRepository usuarios;
  private final CurrentActor actor;
  private final AuditWriter auditoria;
  private final UuidV7Generator ids;

  public ManageBrokerAccountsService(
      BrokerAccountWriter cuentas,
      BrokerAccountQueryRepository lecturas,
      BrokerAccountRegistrar brokers,
      UserRepository usuarios,
      CurrentActor actor,
      AuditWriter auditoria,
      UuidV7Generator ids) {
    this.cuentas = cuentas;
    this.lecturas = lecturas;
    this.brokers = brokers;
    this.usuarios = usuarios;
    this.actor = actor;
    this.auditoria = auditoria;
    this.ids = ids;
  }

  // ---------------------------------------------------------------------------
  // `RF-SP-053` — registrar
  // ---------------------------------------------------------------------------

  @Transactional
  public BrokerAccountItem createOwn(CreateBrokerAccountRequest peticion) {
    return create(yo(), peticion);
  }

  @Transactional
  public BrokerAccountItem create(UUID userId, CreateBrokerAccountRequest peticion) {
    String cuenta = validarAlta(peticion);
    String afftrack = afftrack(peticion.afftrack());
    existe(userId);
    BrokerAccountKind tipo =
        cuentas.kindFor(userId).orElseThrow(ManageBrokerAccountsService::sinTipo);
    if (afftrack != null && tipo != BrokerAccountKind.VENDEDOR) {
      throw afftrackSoloVendedor();
    }
    brokers
        .find(peticion.brokerId())
        .filter(BrokerRef::active)
        .orElseThrow(ManageBrokerAccountsService::brokerNoProcede);

    UUID origen = null;
    if (tipo == BrokerAccountKind.CONSUMIDOR) {
      UUID vendedor = cuentas.principalSellerOf(userId).orElse(null);
      // `RN-SP-072`: llegó antes por el broker con el origen de su vendedor.
      Optional<UUID> asociada = cuentas.claim(peticion.brokerId(), cuenta, userId, vendedor);
      if (asociada.isPresent()) {
        Map<String, Object> cambio = new LinkedHashMap<>();
        cambio.put("before", null);
        cambio.put("after", userId.toString());
        Map<String, Object> cambios = new LinkedHashMap<>();
        cambios.put("user_id", cambio);
        auditoria.recordChange(
            new ChangeEvent(MODULO, ENTIDAD, asociada.get(), ChangeAction.UPDATE, cambios));
        return leer(userId, asociada.get());
      }
      // `RN-SP-070`: la `VENDEDOR` de su vendedor en ese broker, si la tiene.
      origen = cuentas.vendorAccount(vendedor, peticion.brokerId()).orElse(null);
    }

    UUID id = ids.next();
    cuentas.insert(id, userId, peticion.brokerId(), cuenta, tipo, afftrack, origen);

    Map<String, Object> despues = new LinkedHashMap<>();
    despues.put("user_id", userId.toString());
    despues.put("broker_id", peticion.brokerId().toString());
    despues.put("external_id", cuenta);
    despues.put("status", UserBrokerStatus.REGISTER.name());
    despues.put("kind", tipo.name());
    despues.put("afftrack", afftrack);
    despues.put("referrer_account_id", origen == null ? null : origen.toString());
    auditoria.recordChange(
        new ChangeEvent(MODULO, ENTIDAD, id, ChangeAction.CREATE, Map.of("after", despues)));

    return leer(userId, id);
  }

  // ---------------------------------------------------------------------------
  // `RF-SP-080` — editar
  // ---------------------------------------------------------------------------

  @Transactional
  public BrokerAccountItem updateOwn(UUID brokerAccountId, UpdateBrokerAccountRequest peticion) {
    return update(yo(), brokerAccountId, peticion, true);
  }

  @Transactional
  public BrokerAccountItem update(
      UUID userId, UUID brokerAccountId, UpdateBrokerAccountRequest peticion) {
    existe(userId);
    return update(userId, brokerAccountId, peticion, false);
  }

  private BrokerAccountItem update(
      UUID userId, UUID brokerAccountId, UpdateBrokerAccountRequest peticion, boolean titular) {
    String crudo = peticion == null ? null : peticion.accountId();
    String afftrackCrudo = peticion == null ? null : peticion.afftrack();
    if (crudo == null && afftrackCrudo == null) {
      String mensaje = "Debe indicar el identificador de la cuenta o el afftrack.";
      throw new ValidationException(
          "VAL-013", mensaje, List.of(new FieldError("accountId", "VAL-013", mensaje)));
    }
    String nuevo = crudo == null ? null : validarIdentificador(crudo, null);
    String afftrack = afftrack(afftrackCrudo);
    LockedAccount cuenta = bloquear(brokerAccountId, userId, titular);
    if (afftrackCrudo != null && cuenta.kind() != BrokerAccountKind.VENDEDOR) {
      throw afftrackSoloVendedor();
    }

    // `FA-001`: lo que no cambia no escribe ni audita.
    Map<String, Object> cambios = new LinkedHashMap<>();
    if (nuevo != null && !nuevo.equals(cuenta.accountId())) {
      cuentas.updateAccountId(cuenta.id(), nuevo);
      cambios.put("external_id", antesYDespues(cuenta.accountId(), nuevo));
    }
    if (afftrackCrudo != null && !java.util.Objects.equals(afftrack, cuenta.afftrack())) {
      cuentas.updateAfftrack(cuenta.id(), afftrack);
      cambios.put("afftrack", antesYDespues(cuenta.afftrack(), afftrack));
    }
    if (!cambios.isEmpty()) {
      auditoria.recordChange(
          new ChangeEvent(MODULO, ENTIDAD, cuenta.id(), ChangeAction.UPDATE, cambios));
    }
    return leer(userId, cuenta.id());
  }

  // ---------------------------------------------------------------------------
  // `RF-SP-081` — eliminar
  // ---------------------------------------------------------------------------

  @Transactional
  public void deleteOwn(UUID brokerAccountId) {
    delete(yo(), brokerAccountId, true);
  }

  @Transactional
  public void delete(UUID userId, UUID brokerAccountId) {
    existe(userId);
    delete(userId, brokerAccountId, false);
  }

  private void delete(UUID userId, UUID brokerAccountId, boolean titular) {
    LockedAccount cuenta = bloquear(brokerAccountId, userId, titular);
    cuentas.delete(cuenta.id());

    Map<String, Object> instantanea = new LinkedHashMap<>();
    instantanea.put("user_id", cuenta.userId().toString());
    instantanea.put("broker_id", cuenta.brokerId().toString());
    instantanea.put("broker_name", cuenta.brokerName());
    instantanea.put("external_id", cuenta.accountId());
    instantanea.put("broker_username", cuenta.brokerUsername());
    instantanea.put("status", cuenta.status().name());
    instantanea.put("kind", cuenta.kind().name());
    instantanea.put("afftrack", cuenta.afftrack());
    instantanea.put(
        "referrer_account_id",
        cuenta.referrerAccountId() == null ? null : cuenta.referrerAccountId().toString());
    auditoria.recordDeletion(
        new DeletionEvent(
            MODULO,
            ENTIDAD,
            cuenta.id(),
            DeletionType.PHYSICAL,
            titular ? "RF-SP-081: la retiró su titular." : "RF-SP-081: la retiró administración.",
            instantanea));
  }

  // ---------------------------------------------------------------------------
  // `RF-SP-082` — asignar titular a una cuenta sin él
  // ---------------------------------------------------------------------------

  @Transactional
  public TeamBrokerAccountItem assignHolder(
      UUID brokerAccountId, AssignBrokerAccountHolderRequest peticion) {
    UUID persona = peticion == null ? null : peticion.userId();
    if (persona == null) {
      String mensaje = "Debe indicar la persona.";
      throw new ValidationException(
          "VAL-012", mensaje, List.of(new FieldError("userId", "VAL-012", mensaje)));
    }
    LockedAccount cuenta =
        cuentas
            .lockAny(brokerAccountId)
            .orElseThrow(
                () -> new ResourceNotFoundException("VAL-002", "No existe esa cuenta de broker."));
    if (cuenta.userId() != null) {
      String mensaje = "La cuenta ya tiene titular.";
      throw new BusinessRuleException(
          "EX-013", mensaje, List.of(new FieldError("brokerAccountId", "EX-013", mensaje)));
    }
    existe(persona);
    if (cuentas.kindFor(persona).orElse(null) != BrokerAccountKind.CONSUMIDOR) {
      String mensaje = "Solo un consumidor puede ser titular de esta cuenta.";
      throw new UnprocessableEntityException(
          "EX-011", mensaje, List.of(new FieldError("userId", "EX-011", mensaje)));
    }

    cuentas.assignUser(cuenta.id(), persona);
    Map<String, Object> cambios = new LinkedHashMap<>();
    cambios.put("user_id", antesYDespues(null, persona.toString()));
    auditoria.recordChange(
        new ChangeEvent(MODULO, ENTIDAD, cuenta.id(), ChangeAction.UPDATE, cambios));
    return lecturas.findOne(cuenta.id()).orElseThrow();
  }

  // ---------------------------------------------------------------------------
  // Lo común
  // ---------------------------------------------------------------------------

  /** `RN-SP-067`: el titular no toca una cuenta con depósito confirmado; administración sí. */
  private LockedAccount bloquear(UUID brokerAccountId, UUID userId, boolean titular) {
    LockedAccount cuenta =
        cuentas
            .lock(brokerAccountId, userId)
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "VAL-002", "No existe esa cuenta de broker para esa persona."));
    if (titular && cuenta.status() == UserBrokerStatus.FIRST_DEPOSIT) {
      String mensaje =
          "La cuenta ya tiene el primer depósito confirmado y no se puede cambiar ni retirar."
              + " Pídaselo a administración.";
      throw new BusinessRuleException(
          "EX-010", mensaje, List.of(new FieldError("brokerAccountId", "EX-010", mensaje)));
    }
    return cuenta;
  }

  private String validarAlta(CreateBrokerAccountRequest peticion) {
    List<FieldError> errores = new ArrayList<>();
    if (peticion == null || peticion.brokerId() == null) {
      errores.add(new FieldError("brokerId", "VAL-012", "Debe indicar el broker."));
    }
    String cuenta = validarIdentificador(peticion == null ? null : peticion.accountId(), errores);
    if (!errores.isEmpty()) {
      throw new ValidationException(errores.get(0).code(), "Los datos no son válidos.", errores);
    }
    return cuenta;
  }

  /**
   * Devuelve el identificador sin espacios a los lados. Con {@code errores} acumula; sin él, lanza.
   */
  private static String validarIdentificador(String valor, List<FieldError> errores) {
    List<FieldError> propios = errores == null ? new ArrayList<>() : errores;
    String limpio = valor == null ? "" : valor.strip();
    if (limpio.isEmpty()) {
      propios.add(
          new FieldError(
              "accountId", "VAL-013", "Debe indicar el identificador de la cuenta en el broker."));
    } else if (limpio.length() > LARGO_MAXIMO) {
      propios.add(
          new FieldError(
              "accountId",
              "VAL-015",
              "El identificador de la cuenta no puede tener más de "
                  + LARGO_MAXIMO
                  + " caracteres."));
    }
    if (errores == null && !propios.isEmpty()) {
      throw new ValidationException(propios.get(0).code(), "Los datos no son válidos.", propios);
    }
    return limpio;
  }

  /** El `afftrack` sin espacios a los lados; vacío o ausente es nulo. Hasta 80 (`VAL-016`). */
  private static String afftrack(String valor) {
    String limpio = valor == null ? null : valor.strip();
    if (limpio == null || limpio.isEmpty()) {
      return null;
    }
    if (limpio.length() > LARGO_MAXIMO) {
      String mensaje = "El afftrack no puede tener más de " + LARGO_MAXIMO + " caracteres.";
      throw new ValidationException(
          "VAL-016", mensaje, List.of(new FieldError("afftrack", "VAL-016", mensaje)));
    }
    return limpio;
  }

  private static ValidationException afftrackSoloVendedor() {
    String mensaje = "Solo una cuenta de vendedor lleva afftrack.";
    return new ValidationException(
        "VAL-016", mensaje, List.of(new FieldError("afftrack", "VAL-016", mensaje)));
  }

  private static Map<String, Object> antesYDespues(Object antes, Object despues) {
    Map<String, Object> cambio = new LinkedHashMap<>();
    cambio.put("before", antes);
    cambio.put("after", despues);
    return cambio;
  }

  private void existe(UUID userId) {
    if (usuarios.findNotDeletedById(userId).isEmpty()) {
      throw new ResourceNotFoundException(
          "VAL-002", "No existe una persona con ese identificador.");
    }
  }

  private UUID yo() {
    return actor
        .currentActorId()
        .orElseThrow(() -> new UnauthorizedException("AUTH-001", "Se requiere autenticación."));
  }

  private BrokerAccountItem leer(UUID userId, UUID id) {
    return lecturas.findByUser(userId).stream()
        .filter(c -> c.id().equals(id))
        .findFirst()
        .orElseThrow();
  }

  /** `RN-SP-068`: sin rol vendedor ni consumidor no hay de qué tipo hacer la cuenta. */
  private static UnprocessableEntityException sinTipo() {
    String mensaje =
        "Esa persona no es vendedor ni consumidor, y solo ellos tienen cuentas de broker.";
    return new UnprocessableEntityException(
        "EX-011", mensaje, List.of(new FieldError("userId", "EX-011", mensaje)));
  }

  /** Inexistente y apagado responden lo mismo, como en el registro por enlace (`EX-008`). */
  private static UnprocessableEntityException brokerNoProcede() {
    String mensaje = "Ese broker no existe o no está disponible.";
    return new UnprocessableEntityException(
        "EX-008", mensaje, List.of(new FieldError("brokerId", "EX-008", mensaje)));
  }
}
