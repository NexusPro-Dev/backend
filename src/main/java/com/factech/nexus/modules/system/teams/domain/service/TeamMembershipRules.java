package com.factech.nexus.modules.system.teams.domain.service;

import com.factech.nexus.modules.system.users.domain.repository.AssignableRole;
import com.factech.nexus.modules.system.users.domain.security.CommercialStructure;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * `RN-SP-051` en una clase: <b>a un equipo solo pertenece la cúspide</b>.
 *
 * <p><b>Sin Spring y sin base de datos</b> (Art. VI.3): recibe a las personas con sus roles ya
 * resueltos y devuelve quiénes pueden entrar y quiénes no. Eso es lo que permite probar la regla
 * con dobles —manager sí; director, agente, cliente y persona sin rol, no— sin levantar un contexto
 * ni sembrar una fila.
 *
 * <p><b>No reimplementa «quién es la cúspide»: la pregunta.</b> {@link CommercialStructure} ya la
 * responde por la <b>forma de la jerarquía</b> —el vendedor cuyo rol padre ya no es vendedor
 * (`RN-SP-011`)— y es la misma pieza con la que `AssignSupervisorService` comprueba `RN-SP-020`. Si
 * mañana nace un rango por encima de `MANAGER`, cambia `parent_role_id` y las dos operaciones lo
 * siguen sin tocar código. Deducirlo aquí del código `MANAGER` ataría la regla al catálogo de hoy.
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
   *     ningún rol, y no es de la cúspide
   */
  public Veredicto evaluar(
      Collection<UUID> solicitados, Map<UUID, List<AssignableRole>> rolesPorPersona) {
    List<UUID> admitidos = new ArrayList<>();
    List<UUID> rechazados = new ArrayList<>();

    for (UUID persona : solicitados) {
      if (esCuspide(rolesPorPersona.getOrDefault(persona, List.of()))) {
        admitidos.add(persona);
      } else {
        rechazados.add(persona);
      }
    }
    return new Veredicto(List.copyOf(admitidos), List.copyOf(rechazados));
  }

  /**
   * <b>Se mira el rol de MAYOR RANGO, no «alguno» de los que porta</b> (`RN-SP-011`). Un director
   * que además portara `AGENTE` seguiría siendo director, y preguntar por el primero que apareciera
   * lo dejaría entrar o no según el orden de una consulta.
   */
  private boolean esCuspide(List<AssignableRole> roles) {
    return estructura.rolDeMayorRango(roles).filter(estructura::esCuspide).isPresent();
  }

  /**
   * El resultado de evaluar la lista entera.
   *
   * @param admitidos los que portan el rol comercial de mayor rango
   * @param rechazados los que no, en el mismo orden en que se pidieron, para que el mensaje del
   *     `422` sea estable entre dos peticiones iguales
   */
  public record Veredicto(List<UUID> admitidos, List<UUID> rechazados) {

    public boolean todosAdmitidos() {
      return rechazados.isEmpty();
    }
  }
}
