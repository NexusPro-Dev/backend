package com.factech.nexus.modules.movements.domain.service;

import com.factech.nexus.modules.movements.application.PayoutAccountResponse;
import com.factech.nexus.modules.movements.application.PayoutRequests;
import com.factech.nexus.modules.movements.domain.models.PayoutAccountNumber;
import com.factech.nexus.modules.movements.domain.models.PayoutAccountType;
import com.factech.nexus.modules.movements.domain.models.PayoutInstitutionKind;
import com.factech.nexus.modules.movements.domain.repository.PayoutAccountRepository;
import com.factech.nexus.modules.movements.domain.repository.PayoutAccountRepository.AccountRow;
import com.factech.nexus.modules.movements.domain.repository.PayoutInstitutionRepository;
import com.factech.nexus.modules.movements.domain.repository.PayoutInstitutionRepository.InstitutionRow;
import com.factech.nexus.modules.system.roles.application.AuthenticatedActor;
import com.factech.nexus.modules.system.users.application.PayoutHolderLookup;
import com.factech.nexus.modules.system.users.application.PayoutHolderLookup.HolderView;
import com.factech.nexus.shared.audit.AuditEnums.ChangeAction;
import com.factech.nexus.shared.audit.AuditEnums.DeletionType;
import com.factech.nexus.shared.audit.AuditEvents.ChangeEvent;
import com.factech.nexus.shared.audit.AuditEvents.DeletionEvent;
import com.factech.nexus.shared.audit.AuditWriter;
import com.factech.nexus.shared.error.BusinessRuleException;
import com.factech.nexus.shared.error.FieldError;
import com.factech.nexus.shared.error.ResourceNotFoundException;
import com.factech.nexus.shared.error.UnprocessableEntityException;
import com.factech.nexus.shared.error.ValidationException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Las cuentas de cobro (`RN-MV-055`): registrar (`RF-MV-035`), consultar las propias (`RF-MV-036`),
 * editar (`RF-MV-037`), dar de baja (`RF-MV-038`) y la consulta de administración (`RF-MV-039`).
 *
 * <p><b>El titular es siempre el dueño</b>, y se lee de `SP` por {@link PayoutHolderLookup} cada
 * vez: la cuenta no guarda nombre ni documento. <b>Las escrituras se serializan por persona</b> con
 * {@link PayoutAccountRepository#lockOwner}, tomado antes de leer sus cuentas.
 */
@Service
public class PayoutAccountService {

  private static final String MODULO = "MV";
  private static final String ENTIDAD = "payout_accounts";

  private final PayoutAccountRepository cuentas;
  private final PayoutInstitutionRepository entidades;
  private final PayoutHolderLookup titulares;
  private final AuthenticatedActor actor;
  private final AuditWriter auditoria;
  private final Clock reloj;

  @Autowired
  public PayoutAccountService(
      PayoutAccountRepository cuentas,
      PayoutInstitutionRepository entidades,
      PayoutHolderLookup titulares,
      AuthenticatedActor actor,
      AuditWriter auditoria) {
    this(cuentas, entidades, titulares, actor, auditoria, Clock.systemUTC());
  }

  PayoutAccountService(
      PayoutAccountRepository cuentas,
      PayoutInstitutionRepository entidades,
      PayoutHolderLookup titulares,
      AuthenticatedActor actor,
      AuditWriter auditoria,
      Clock reloj) {
    this.cuentas = cuentas;
    this.entidades = entidades;
    this.titulares = titulares;
    this.actor = actor;
    this.auditoria = auditoria;
    this.reloj = reloj;
  }

  // ---------------------------------------------------------------------------
  // `RF-MV-035` — registrar
  // ---------------------------------------------------------------------------

  @Transactional
  public PayoutAccountResponse register(PayoutRequests.RegisterAccount peticion) {
    // 1. La forma, con los errores juntos, antes de consultar nada (`EX-001`).
    UUID entidadId = peticion == null ? null : peticion.institutionId();
    String tipoEscrito = peticion == null ? null : peticion.accountType();
    Optional<String> numero =
        PayoutAccountNumber.normalize(peticion == null ? null : peticion.number());
    List<FieldError> errores = new ArrayList<>();
    if (entidadId == null) {
      errores.add(new FieldError("institutionId", "VAL-001", "La entidad es obligatoria."));
    }
    if (numero.isEmpty()) {
      errores.add(
          new FieldError(
              "number",
              "VAL-002",
              "El número es obligatorio: de 4 a 20 dígitos, con espacios o guiones si se"
                  + " quiere."));
    }
    Optional<PayoutAccountType> tipo = PayoutAccountType.parse(tipoEscrito);
    if (tipoEscrito != null && tipo.isEmpty()) {
      errores.add(
          new FieldError("accountType", "VAL-003", "El tipo de cuenta es AHORROS o CORRIENTE."));
    }
    if (!errores.isEmpty()) {
      throw new ValidationException(errores.get(0).code(), errores.get(0).message(), errores);
    }

    // 2. El titular y la entidad.
    UUID quien = actor.id();
    HolderView titular = titularConDocumento(quien, "EX-005");
    InstitutionRow entidad =
        entidades
            .find(entidadId)
            .orElseThrow(
                () ->
                    new UnprocessableEntityException(
                        "EX-002",
                        "La entidad indicada no existe.",
                        List.of(
                            new FieldError(
                                "institutionId", "EX-002", "La entidad indicada no existe."))));
    exigirUsable(entidad.active(), entidad.name());
    if (!entidad.countryId().equals(titular.countryId())) {
      String mensaje = "La entidad " + entidad.name() + " no es de su país.";
      throw new BusinessRuleException(
          "EX-003", mensaje, List.of(new FieldError("institutionId", "EX-003", mensaje)));
    }
    PayoutInstitutionKind clase = PayoutInstitutionKind.valueOf(entidad.kind());
    exigirForma(clase, tipo.orElse(null), numero.get(), "EX-004");

    // 3. Serializado por persona: «¿es la primera?» y «¿ya la tiene?».
    cuentas.lockOwner(quien);
    if (cuentas.existsLive(quien, entidad.id(), numero.get(), null)) {
      throw repetida("EX-006");
    }
    boolean principal = !cuentas.hasLive(quien) || Boolean.TRUE.equals(peticion.principal());
    OffsetDateTime ahora = OffsetDateTime.now(reloj);
    List<AccountRow> desmarcadas = List.of();
    if (principal) {
      desmarcadas = cuentas.of(quien, false).stream().filter(AccountRow::principal).toList();
      cuentas.unmarkPrincipal(quien, ahora);
    }
    UUID id = UUID.randomUUID();
    String tipoGuardado = clase.requiresAccountType() ? tipo.get().name() : null;
    cuentas.insert(id, quien, entidad.id(), tipoGuardado, numero.get(), principal, ahora);

    Map<String, Object> despues = new LinkedHashMap<>();
    despues.put("institution_id", entidad.id().toString());
    despues.put("account_type", tipoGuardado);
    despues.put("number", PayoutAccountNumber.masked(numero.get()));
    despues.put("is_principal", principal);
    auditar(id, ChangeAction.CREATE, Map.of("after", despues));
    for (AccountRow anterior : desmarcadas) {
      auditarPrincipal(anterior.id(), true, false);
    }

    return respuesta(cuentas.findLiveOwn(id, quien).orElseThrow(), titular);
  }

  // ---------------------------------------------------------------------------
  // `RF-MV-036` — mis cuentas
  // ---------------------------------------------------------------------------

  @Transactional(readOnly = true)
  public List<PayoutAccountResponse> listMine() {
    UUID quien = actor.id();
    List<AccountRow> filas = cuentas.of(quien, false);
    if (filas.isEmpty()) {
      return List.of();
    }
    HolderView titular = titulares.holderOf(quien).orElse(null);
    return filas.stream().map(f -> respuesta(f, titular)).toList();
  }

  // ---------------------------------------------------------------------------
  // `RF-MV-037` — editar
  // ---------------------------------------------------------------------------

  @Transactional
  public PayoutAccountResponse update(UUID id, PayoutRequests.UpdateAccount peticion) {
    String tipoEscrito = peticion == null ? null : peticion.accountType();
    String numeroEscrito = peticion == null ? null : peticion.number();
    Boolean principal = peticion == null ? null : peticion.principal();

    List<FieldError> errores = new ArrayList<>();
    if (tipoEscrito == null && numeroEscrito == null && principal == null) {
      errores.add(
          new FieldError(
              "body", "VAL-001", "Indique el tipo de cuenta, el número o la marca de principal."));
    }
    Optional<String> numero = PayoutAccountNumber.normalize(numeroEscrito);
    if (numeroEscrito != null && numero.isEmpty()) {
      errores.add(new FieldError("number", "VAL-002", "El número tiene de 4 a 20 dígitos."));
    }
    Optional<PayoutAccountType> tipo = PayoutAccountType.parse(tipoEscrito);
    if (tipoEscrito != null && tipo.isEmpty()) {
      errores.add(
          new FieldError("accountType", "VAL-003", "El tipo de cuenta es AHORROS o CORRIENTE."));
    }
    if (Boolean.FALSE.equals(principal)) {
      errores.add(
          new FieldError(
              "principal",
              "VAL-004",
              "La principal no se quita: para cambiarla, marque otra como principal."));
    }
    if (!errores.isEmpty()) {
      throw new ValidationException(errores.get(0).code(), errores.get(0).message(), errores);
    }

    UUID quien = actor.id();
    cuentas.lockOwner(quien);
    AccountRow actual = cuentas.findLiveOwn(id, quien).orElseThrow(() -> noExiste("EX-002"));
    exigirUsable(actual.institutionActive(), actual.institutionName());

    PayoutInstitutionKind clase = PayoutInstitutionKind.valueOf(actual.institutionKind());
    String numeroNuevo = numero.orElse(actual.number());
    String tipoNuevo = tipo.map(Enum::name).orElse(actual.accountType());
    // Sobre el RESULTADO de aplicar los cambios (`VAL-005`): un tipo enviado a una
    // billetera, o un número con la longitud del otro tipo, no pasa.
    exigirForma(
        clase,
        tipoNuevo == null ? null : PayoutAccountType.valueOf(tipoNuevo),
        numeroNuevo,
        "EX-004");
    boolean principalNueva = actual.principal() || Boolean.TRUE.equals(principal);

    if (numeroNuevo.equals(actual.number())
        && Objects.equals(tipoNuevo, actual.accountType())
        && principalNueva == actual.principal()) {
      return respuesta(actual, titulares.holderOf(quien).orElse(null)); // `FA-001`
    }
    if (!numeroNuevo.equals(actual.number())
        && cuentas.existsLive(quien, actual.institutionId(), numeroNuevo, actual.id())) {
      throw repetida("EX-005");
    }

    OffsetDateTime ahora = OffsetDateTime.now(reloj);
    List<AccountRow> desmarcadas = List.of();
    if (principalNueva && !actual.principal()) {
      desmarcadas = cuentas.of(quien, false).stream().filter(AccountRow::principal).toList();
      cuentas.unmarkPrincipal(quien, ahora);
    }
    cuentas.update(id, tipoNuevo, numeroNuevo, principalNueva, ahora);

    Map<String, Object> antes = new LinkedHashMap<>();
    Map<String, Object> despues = new LinkedHashMap<>();
    if (!numeroNuevo.equals(actual.number())) {
      antes.put("number", PayoutAccountNumber.masked(actual.number()));
      despues.put("number", PayoutAccountNumber.masked(numeroNuevo));
    }
    if (!Objects.equals(tipoNuevo, actual.accountType())) {
      antes.put("account_type", actual.accountType());
      despues.put("account_type", tipoNuevo);
    }
    if (principalNueva != actual.principal()) {
      antes.put("is_principal", actual.principal());
      despues.put("is_principal", principalNueva);
    }
    Map<String, Object> cambios = new LinkedHashMap<>();
    cambios.put("before", antes);
    cambios.put("after", despues);
    auditar(id, ChangeAction.UPDATE, cambios);
    for (AccountRow anterior : desmarcadas) {
      auditarPrincipal(anterior.id(), true, false);
    }

    return respuesta(
        cuentas.findLiveOwn(id, quien).orElseThrow(), titulares.holderOf(quien).orElse(null));
  }

  // ---------------------------------------------------------------------------
  // `RF-MV-038` — dar de baja
  // ---------------------------------------------------------------------------

  @Transactional
  public void delete(UUID id) {
    UUID quien = actor.id();
    cuentas.lockOwner(quien);
    AccountRow actual = cuentas.findLiveOwn(id, quien).orElseThrow(() -> noExiste("EX-001"));
    OffsetDateTime ahora = OffsetDateTime.now(reloj);
    cuentas.softDelete(id, ahora);

    Map<String, Object> foto = new LinkedHashMap<>();
    foto.put("institution_id", actual.institutionId().toString());
    foto.put("account_type", actual.accountType());
    foto.put("number", PayoutAccountNumber.masked(actual.number()));
    foto.put("was_principal", actual.principal());
    auditoria.recordDeletion(
        new DeletionEvent(
            MODULO,
            ENTIDAD,
            id,
            DeletionType.LOGICAL,
            "Baja de la cuenta de cobro por su dueño (RF-MV-038).",
            foto));

    // `RN-MV-055`: con cuentas vivas siempre hay una principal, y la hereda la más antigua.
    if (actual.principal()) {
      cuentas.promoteOldest(quien, ahora).ifPresent(otra -> auditarPrincipal(otra, false, true));
    }
  }

  // ---------------------------------------------------------------------------
  // `RF-MV-039` — las de una persona, para administración
  // ---------------------------------------------------------------------------

  @Transactional(readOnly = true)
  public List<PayoutAccountResponse> listOf(UUID userId, boolean includeDeleted) {
    HolderView titular =
        titulares
            .holderOf(userId)
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "EX-002", "No existe una persona con ese identificador."));
    return cuentas.of(userId, includeDeleted).stream().map(f -> respuesta(f, titular)).toList();
  }

  // ---------------------------------------------------------------------------

  /** El titular con documento, o conflicto con el código que corresponde a quien pregunta. */
  HolderView titularConDocumento(UUID quien, String codigo) {
    HolderView titular =
        titulares
            .holderOf(quien)
            .orElseThrow(() -> new IllegalStateException("La persona autenticada no existe."));
    if (!titular.hasDocument()) {
      String mensaje =
          "Su cuenta no tiene documento de identidad, y sin él no hay titular. Pida a"
              + " administración que lo complete.";
      throw new BusinessRuleException(
          codigo, mensaje, List.of(new FieldError("user", codigo, mensaje)));
    }
    return titular;
  }

  private static void exigirUsable(boolean activa, String nombre) {
    if (!activa) {
      String mensaje = "La entidad " + nombre + " está desactivada: registre otra cuenta.";
      throw new BusinessRuleException(
          "EX-003", mensaje, List.of(new FieldError("institutionId", "EX-003", mensaje)));
    }
  }

  private static void exigirForma(
      PayoutInstitutionKind clase, PayoutAccountType tipo, String numero, String codigo) {
    List<FieldError> errores = new ArrayList<>();
    if (clase.requiresAccountType() && tipo == null) {
      errores.add(
          new FieldError(
              "accountType", codigo, "Una cuenta bancaria lleva tipo: AHORROS o CORRIENTE."));
    }
    if (!clase.requiresAccountType() && tipo != null) {
      errores.add(
          new FieldError("accountType", codigo, "Una billetera móvil no lleva tipo de cuenta."));
    }
    if (numero.length() < clase.minDigits() || numero.length() > clase.maxDigits()) {
      errores.add(
          new FieldError(
              "number",
              codigo,
              (clase.requiresAccountType() ? "El número de cuenta" : "El celular")
                  + " tiene de "
                  + clase.minDigits()
                  + " a "
                  + clase.maxDigits()
                  + " dígitos."));
    }
    if (!errores.isEmpty()) {
      throw new ValidationException(codigo, errores.get(0).message(), errores);
    }
  }

  private static BusinessRuleException repetida(String codigo) {
    String mensaje = "Ya tiene registrada esa cuenta en esa entidad.";
    return new BusinessRuleException(
        codigo, mensaje, List.of(new FieldError("number", codigo, mensaje)));
  }

  private static ResourceNotFoundException noExiste(String codigo) {
    return new ResourceNotFoundException(
        codigo, "No existe una cuenta de cobro suya con ese identificador.");
  }

  private void auditar(UUID id, ChangeAction accion, Map<String, Object> cambios) {
    auditoria.recordChange(new ChangeEvent(MODULO, ENTIDAD, id, accion, cambios));
  }

  private void auditarPrincipal(UUID id, boolean antes, boolean despues) {
    Map<String, Object> cambios = new LinkedHashMap<>();
    cambios.put("before", Map.of("is_principal", antes));
    cambios.put("after", Map.of("is_principal", despues));
    auditar(id, ChangeAction.UPDATE, cambios);
  }

  static PayoutAccountResponse respuesta(AccountRow f, HolderView titular) {
    return new PayoutAccountResponse(
        f.id(),
        new PayoutAccountResponse.Institution(
            f.institutionId(),
            f.institutionCode(),
            f.institutionName(),
            f.institutionKind(),
            f.institutionActive()),
        f.accountType(),
        f.number(),
        f.principal(),
        f.deletedAt() == null && f.institutionActive(),
        titular == null
            ? null
            : new PayoutAccountResponse.Holder(
                titular.fullName(), titular.documentType(), titular.documentNumber()),
        f.createdAt(),
        f.deletedAt());
  }
}
