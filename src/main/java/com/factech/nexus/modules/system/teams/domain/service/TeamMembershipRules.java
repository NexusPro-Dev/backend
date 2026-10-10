package com.factech.nexus.modules.system.teams.domain.service;

import com.factech.nexus.modules.system.users.domain.repository.AssignableRole;
import com.factech.nexus.modules.system.users.domain.security.CommercialStructure;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * `RN-SP-051` en una clase: <b>a un equipo solo pertenece un director</b> (enmendada el 09-10-2026;
 * hasta entonces, la cúspide).
 *
 * <p><b>Sin Spring y sin base de datos</b> (Art. VI.3): recibe a las personas con sus roles ya
 * resueltos y devuelve quiénes pueden entrar y quiénes no. Eso es lo que permite probar la regla
 * con dobles —director sí; manager, agente, cliente y persona sin rol, no— sin levantar un contexto
 * ni sembrar una fila.
 *
 * <p><b>No reimplementa «quién es director»: la pregunta.</b> {@link CommercialStructure} ya la
 * responde por la <b>forma de la jerarquía</b> —el rol justo debajo de la cúspide, que es el
 * vendedor cuyo rol padre ya no es vendedor (`RN-SP-011`)—. Si mañana se renombra el rol o nace un
 * rango por encima de `MANAGER`, cambia `parent_role_id` y la regla lo sigue sin tocar código.
 * Deducirlo aquí del código `DIRECTOR` ataría la regla al catálogo de hoy.
 *
 * <p><b>Evalúa a TODOS antes de decidir</b>, y por eso devuelve dos listas en lugar de fallar en el
 * primero: la operación es toda o nada (`spec.md` §14.4) y el `422` informa de cuántos no pueden
 * entrar y de cuáles, en vez de obligar al administrador a descubrirlos de uno en uno.
 */
public class TeamMembershipRules {

  private final CommercialStructure estructura;

  public TeamMembershipRules(CommercialStructure estructura) {
    this.estructura = estructura;
  }

  /**
   * Quiénes pueden entrar y quiénes no.
   *
   * @param rolesPorPersona los roles de cada persona; una persona sin entrada es una persona sin
   *     ningún rol, y no es director
   */
  public Veredicto evaluar(
      Collection<UUID> solicitados, Map<UUID, List<AssignableRole>> rolesPorPersona) {
    List<UUID> admitidos = new ArrayList<>();
    List<UUID> rechazados = new ArrayList<>();

    for (UUID persona : solicitados) {
      if (esDirector(rolesPorPersona.getOrDefault(persona, List.of()))) {
        admitidos.add(persona);
      } else {
        rechazados.add(persona);
      }
    }
    return new Veredicto(List.copyOf(admitidos), List.copyOf(rechazados));
  }

  /**
   * <b>Se mira el rol de MAYOR RANGO, no «alguno» de los que porta</b> (`RN-SP-011`). Un manager
   * que además portara `DIRECTOR` seguiría siendo manager, y preguntar por el primero que
   * apareciera lo dejaría entrar o no según el orden de una consulta.
   */
  private boolean esDirector(List<AssignableRole> roles) {
    return estructura.rolDeMayorRango(roles).filter(estructura::esDirector).isPresent();
  }

  /**
   * El resultado de evaluar la lista entera.
   *
   * @param admitidos los que tienen el rango de director
   * @param rechazados los que no, en el mismo orden en que se pidieron, para que el mensaje del
   *     `422` sea estable entre dos peticiones iguales
   */
  public record Veredicto(List<UUID> admitidos, List<UUID> rechazados) {

    public boolean todosAdmitidos() {
      return rechazados.isEmpty();
    }
  }
}
