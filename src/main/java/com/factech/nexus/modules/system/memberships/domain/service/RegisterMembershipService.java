package com.factech.nexus.modules.system.memberships.domain.service;

import com.factech.nexus.modules.system.memberships.application.MembershipResponse;
import com.factech.nexus.modules.system.memberships.application.RegisterMembershipCommand;
import com.factech.nexus.modules.system.memberships.domain.models.ChainLink;
import com.factech.nexus.modules.system.memberships.domain.models.LevelShift;
import com.factech.nexus.modules.system.memberships.domain.models.Membership;
import com.factech.nexus.modules.system.memberships.domain.models.MembershipChain;
import com.factech.nexus.modules.system.memberships.domain.models.MembershipInsertion;
import com.factech.nexus.modules.system.memberships.domain.repository.MembershipRepository;
import com.factech.nexus.shared.audit.AuditEnums.ChangeAction;
import com.factech.nexus.shared.audit.AuditEvents.ChangeEvent;
import com.factech.nexus.shared.audit.AuditWriter;
import com.factech.nexus.shared.error.BusinessRuleException;
import com.factech.nexus.shared.error.FieldError;
import com.factech.nexus.shared.persistence.UuidV7Generator;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Alta de una membresía y su inserción en la cadena (`RF-SP-016`).
 *
 * <p><b>El orden de verificación es el contrato</b> (`plan.md` §4), y el primer paso no es una
 * verificación sino el <b>bloqueo</b>:
 *
 * <ol>
 *   <li>Bloqueo de la cadena. Va antes y no después: verificar sobre una cadena que otra
 *       transacción está reordenando produce decisiones tomadas sobre un estado que ya no existe.
 *   <li>Unicidad de código y nombre (`EX-001` → {@code 409}).
 *   <li>Existencia de la hija indicada (`EX-002` → {@code 422}), que resuelve el dominio sobre la
 *       cadena ya cargada: no hace falta una consulta más.
 *   <li>Cálculo de la posición y de los niveles, en {@link MembershipChain}.
 * </ol>
 *
 * <p><b>La atomicidad no es un detalle, es el requerimiento.</b> Una transacción parcialmente
 * aplicada dejaría la cadena bifurcada o con niveles duplicados —un orden lineal que ya no es
 * lineal— y `RN-SP-008` impide corregirlo por la API. Las restricciones diferidas garantizan que
 * una transacción incoherente no pueda confirmarse.
 */
@Service
public class RegisterMembershipService {

  private static final String MODULO = "SP";
  private static final String ENTIDAD = "memberships";

  private final MembershipRepository membresias;
  private final AuditWriter auditoria;
  private final UuidV7Generator ids;
  private final Clock reloj;

  /**
   * Constructor de producción. La anotación es obligatoria porque la clase declara dos
   * constructores —el segundo existe para que la prueba pueda fijar el reloj— y Spring solo infiere
   * cuando hay uno.
   */
  @Autowired
  public RegisterMembershipService(
      MembershipRepository membresias, AuditWriter auditoria, UuidV7Generator ids) {
    this(membresias, auditoria, ids, Clock.systemUTC());
  }

  RegisterMembershipService(
      MembershipRepository membresias, AuditWriter auditoria, UuidV7Generator ids, Clock reloj) {
    this.membresias = membresias;
    this.auditoria = auditoria;
    this.ids = ids;
    this.reloj = reloj;
  }

  @Transactional
  public MembershipResponse register(RegisterMembershipCommand comando) {
    List<ChainLink> vigente = membresias.loadChainForUpdate();

    verificarUnicidad(comando.code(), comando.name(), comando.color());

    MembershipInsertion posicion =
        MembershipChain.of(vigente).insertAbove(comando.childMembershipId());

    // El desplazamiento va ANTES de insertar: el UPDATE masivo alcanza a todo
    // lo que esté en el nivel de la nueva o por debajo, y con la fila nueva ya
    // escrita se desplazaría a sí misma.
    if (!posicion.sinReordenamiento()) {
      membresias.shiftLevelsFrom(posicion.level());
    }

    Membership nueva =
        membresias.save(
            Membership.create(
                ids.next(),
                comando.code(),
                comando.name(),
                comando.description(),
                comando.color(),
                posicion,
                OffsetDateTime.now(reloj)));

    if (posicion.reencadenada() != null) {
      membresias.reparent(posicion.reencadenada(), nueva.getId());
    }

    auditar(nueva, posicion);

    return MembershipResponse.from(nueva, posicion.childId());
  }

  /**
   * `EX-001`. La verificación previa existe <b>para poder dar un mensaje preciso</b> —cuál de los
   * dos está duplicado—; la garantía la dan {@code uq_memberships_code} y {@code
   * uq_memberships_name}, y su violación la traduce el adaptador. La restricción decide; esto solo
   * redacta.
   */
  private void verificarUnicidad(String code, String name, String color) {
    if (membresias.existsCode(code)) {
      throw new BusinessRuleException(
          "EX-001",
          "Ya existe una membresía con ese código.",
          List.of(new FieldError("code", "EX-001", "Ya existe una membresía con ese código.")));
    }
    if (membresias.existsName(name)) {
      throw new BusinessRuleException(
          "EX-001",
          "Ya existe una membresía con ese nombre.",
          List.of(new FieldError("name", "EX-001", "Ya existe una membresía con ese nombre.")));
    }
    // El color se compara ya normalizado, igual que lo hará `uq_memberships_color`:
    // el dominio lo pasa a mayúsculas al escribir, y comparar aquí el texto en
    // crudo dejaría pasar `1e88e5` contra un `1E88E5` existente para que el
    // índice lo rechazara después como fallo de integridad.
    if (color != null && membresias.existsColor(color.trim().toUpperCase(java.util.Locale.ROOT))) {
      throw new BusinessRuleException(
          "EX-001",
          "Ya existe una membresía con ese color.",
          List.of(new FieldError("color", "EX-001", "Ya existe una membresía con ese color.")));
    }
  }

  /**
   * Un evento por la membresía creada y <b>uno por cada membresía que el reordenamiento tocó</b>
   * (`CA-SP-118`), todos en la misma transacción y bajo el mismo identificador de correlación —que
   * el escritor toma del contexto de la petición—.
   *
   * <p><b>Por qué no un único evento agregado.</b> Sería tentador, porque una sola sentencia cambió
   * <i>n</i> filas; pero la línea de tiempo de una membresía se consulta por su {@code entity_id},
   * y un evento agregado no aparecería en ninguna de ellas. El coste es acotado: la cadena tiene
   * unos pocos elementos.
   *
   * <p><b>El alta no emite evento de seguridad</b>, y no es una omisión: el catálogo de
   * `security.md` §8.1 es cerrado y no incluye las membresías. Son un nivel de acceso a
   * <i>contenido</i>, no un privilegio sobre el sistema, y no intervienen en la resolución de
   * permisos. Es la asimetría deliberada con `RF-SP-001`, donde crear un rol sí amplía la
   * superficie de privilegios.
   */
  private void auditar(Membership nueva, MembershipInsertion posicion) {
    Map<String, Object> estadoInicial = new HashMap<>();
    estadoInicial.put("code", nueva.getCode());
    estadoInicial.put("name", nueva.getName());
    estadoInicial.put("description", nueva.getDescription());
    estadoInicial.put("color", nueva.getColor());
    estadoInicial.put(
        "parent_membership_id",
        nueva.getParentMembershipId() == null ? null : nueva.getParentMembershipId().toString());
    estadoInicial.put("level", (int) nueva.getLevel());

    auditoria.recordChange(
        new ChangeEvent(MODULO, ENTIDAD, nueva.getId(), ChangeAction.CREATE, estadoInicial));

    for (LevelShift desplazada : posicion.desplazadas()) {
      Map<String, Object> diff = new HashMap<>();
      diff.put("level", diferencia(desplazada.antes(), desplazada.despues()));

      // La hija reencadenada cambia además de superior, y ese cambio va en el
      // mismo evento: es una sola modificación de una sola fila.
      if (desplazada.id().equals(posicion.reencadenada())) {
        diff.put(
            "parent_membership_id",
            diferencia(
                posicion.superiorAnteriorDeLaReencadenada() == null
                    ? null
                    : posicion.superiorAnteriorDeLaReencadenada().toString(),
                nueva.getId().toString()));
      }

      auditoria.recordChange(
          new ChangeEvent(MODULO, ENTIDAD, desplazada.id(), ChangeAction.UPDATE, diff));
    }
  }

  /**
   * Forma {@code before}/{@code after} que exige `architecture.md` §6.6.2 para un {@code UPDATE}.
   */
  private static Map<String, Object> diferencia(Object antes, Object despues) {
    Map<String, Object> cambio = new HashMap<>();
    cambio.put("before", antes);
    cambio.put("after", despues);
    return cambio;
  }
}
