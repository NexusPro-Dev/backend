package com.factech.nexus.modules.system.teams.domain.service;

import com.factech.nexus.modules.system.users.domain.repository.AssignableRole;
import com.factech.nexus.modules.system.users.domain.security.CommercialStructure;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * `RN-SP-051` en una clase: <b>a un equipo pertenece cualquier vendedor menos el último eslabón</b>
 * (enmendada el 15-10-2026; del 09-10 a esa fecha, solo directores; antes, la cúspide).
 *
 * <p><b>Sin Spring y sin base de datos</b> (Art. VI.3): recibe a las personas con sus roles ya
 * resueltos y devuelve quiénes pueden entrar y quiénes no. Eso es lo que permite probar la regla
 * con dobles —manager y director sí; agente, cliente y persona sin rol, no— sin levantar un
 * contexto ni sembrar una fila.
 *
 * <p><b>No reimplementa la pregunta.</b> {@link CommercialStructure#encabezaOficina} la responde
 * por la <b>forma de la jerarquía</b>: si nace un rango nuevo, entra sin tocar código.
 *
 * <p><b>Evalúa a TODOS antes de decidir</b>, y por eso devuelve dos listas en lugar de fallar en el
 * primero: la operación es toda o nada (`spec.md` §14.4) y el `422` informa de cuántos no pueden
 * entrar y de cuáles, en vez de obligar al administrador a descubrirlos de uno en uno.
 */
public class TeamMembershipRules {

  private final CommercialStructure estructura;
  private final Set<UUID> ultimoEslabon;

  /**
   * @param ultimoEslabon los roles vendedores de los que no cuelga ningún otro ({@code
   *     LastLinkRoles}), hoy {@code AGENTE}
   */
  public TeamMembershipRules(CommercialStructure estructura, Set<UUID> ultimoEslabon) {
    this.estructura = estructura;
    this.ultimoEslabon = ultimoEslabon;
  }

  /**
   * Quiénes pueden entrar y quiénes no.
   *
   * <p><b>Se mira el rol de MAYOR RANGO, no «alguno» de los que porta</b> (`RN-SP-011`): un
   * director que además portara `AGENTE` sigue siendo director.
   *
   * @param rolesPorPersona los roles de cada persona; una persona sin entrada es una persona sin
   *     ningún rol, y no entra
   */
  public Veredicto evaluar(
      Collection<UUID> solicitados, Map<UUID, List<AssignableRole>> rolesPorPersona) {
    List<UUID> admitidos = new ArrayList<>();
    List<UUID> rechazados = new ArrayList<>();

    for (UUID persona : solicitados) {
      if (estructura.encabezaOficina(
          rolesPorPersona.getOrDefault(persona, List.of()), ultimoEslabon)) {
        admitidos.add(persona);
      } else {
        rechazados.add(persona);
      }
    }
    return new Veredicto(List.copyOf(admitidos), List.copyOf(rechazados));
  }

  /**
   * El resultado de evaluar la lista entera.
   *
   * @param admitidos los que pueden encabezar una oficina
   * @param rechazados los que no, en el mismo orden en que se pidieron, para que el mensaje del
   *     `422` sea estable entre dos peticiones iguales
   */
  public record Veredicto(List<UUID> admitidos, List<UUID> rechazados) {

    public boolean todosAdmitidos() {
      return rechazados.isEmpty();
    }
  }
}
