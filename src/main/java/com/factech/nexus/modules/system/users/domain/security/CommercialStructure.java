package com.factech.nexus.modules.system.users.domain.security;

import com.factech.nexus.modules.system.users.domain.repository.AssignableRole;
import com.factech.nexus.modules.system.users.domain.repository.RoleCatalog;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * `RN-SP-019` y `RN-SP-020`: quién reporta a quién en la fuerza comercial.
 *
 * <p>Responde tres preguntas, y la tercera es la que `RF-SP-024` no podía tener:
 *
 * <ol>
 *   <li>Cuál es el <b>rol vendedor de mayor rango</b> de un conjunto de roles.
 *   <li>Qué rol debe portar el superior de quien lleva ese rol, o si es la <b>cúspide</b> y no
 *       reporta a nadie.
 *   <li>Si una operación <b>cambia</b> ese rango, que es lo único que distingue un <b>ascenso</b>
 *       de una asignación lateral.
 * </ol>
 *
 * <p>La tercera es la razón de que este componente exista separado del alta: en un alta no hay
 * estado anterior con el que comparar, de modo que toda concesión de un rol vendedor es «la
 * primera». Al asignar roles a alguien que ya los tiene, en cambio, la diferencia importa: <b>quien
 * pasa de agente a director deja de poder estar a cargo de un director</b>, y su superior anterior
 * puede haber dejado de ser admisible sin que nadie tocara esa fila.
 *
 * <p>El rol de mayor rango es aquel que <b>no desciende de ningún otro</b> de los roles vendedores
 * de la misma persona. Se mira así y no «el primero de la lista» justamente por el ascenso.
 *
 * <p><b>Hueco declarado:</b> si alguien portara dos roles vendedores en ramas distintas —ninguno
 * ancestro del otro— habría dos candidatos y las reglas no dicen cuál manda. Se toma el primero por
 * código para que el resultado sea determinista y reproducible. El catálogo aprobado es una cadena
 * lineal, de modo que hoy el caso no puede darse; queda anotado en `tasks.md` de `RF-SP-024`.
 */
@Component
public class CommercialStructure {

  /**
   * Tope de saltos al recorrer la jerarquía hacia arriba.
   *
   * <p>La cadena es acíclica por `RN-SEG-006` y por {@code ck_roles_parent_not_self}, de modo que
   * este límite no debería alcanzarse nunca. Existe porque un recorrido de punteros sin tope
   * convierte un defecto de datos en un <b>hilo colgado</b>, y un rechazo es preferible a un
   * servidor que deja de responder.
   */
  private static final int SALTOS_MAXIMOS = 32;

  private final RoleCatalog roles;

  public CommercialStructure(RoleCatalog roles) {
    this.roles = roles;
  }

  /** El rol vendedor de mayor rango del conjunto, o vacío si no hay ninguno vendedor. */
  public Optional<AssignableRole> rolDeMayorRango(Collection<AssignableRole> conjunto) {
    List<AssignableRole> vendedores =
        conjunto.stream().filter(AssignableRole::esVendedor).sorted(porCodigo()).toList();

    if (vendedores.size() <= 1) {
      return vendedores.stream().findFirst();
    }
    Set<UUID> deLaPersona =
        new LinkedHashSet<>(vendedores.stream().map(AssignableRole::id).toList());

    return vendedores.stream().filter(rol -> !desciendeDeAlguno(rol, deLaPersona)).findFirst();
  }

  /**
   * El rol que debe portar el superior de quien lleva {@code rol}.
   *
   * <p>Es su <b>rol padre inmediato</b>, y no un ancestro cualquiera: eso es lo que hace que la
   * cadena de personas herede la aciclicidad de la cadena de roles <b>sin necesitar una regla
   * anti-ciclos propia</b>. Si el padre no existe o no es vendedor, quien lleva este rol es la
   * cúspide comercial y no reporta a nadie.
   *
   * @return el rol exigido al superior, o vacío si {@code rol} es la cúspide
   */
  public Optional<AssignableRole> rolExigidoAlSuperior(AssignableRole rol) {
    return roles.findById(rol.parentRoleId()).filter(AssignableRole::esVendedor);
  }

  /** Atajo legible: la cúspide es el vendedor cuyo padre ya no es vendedor. */
  public boolean esCuspide(AssignableRole rol) {
    return rolExigidoAlSuperior(rol).isEmpty();
  }

  /**
   * ¿Cambia el rango comercial al pasar de un conjunto de roles a otro?
   *
   * <p>Devuelve verdadero tanto cuando aparece el primer rol vendedor como cuando el de mayor rango
   * pasa a ser otro. Devuelve <b>falso</b> ante una asignación lateral —añadir `AGENTE` a quien ya
   * es `DIRECTOR` no lo degrada— y ante una que no toca la fuerza comercial.
   */
  public boolean cambiaElRango(
      Collection<AssignableRole> antes, Collection<AssignableRole> despues) {
    Optional<UUID> rangoAntes = rolDeMayorRango(antes).map(AssignableRole::id);
    Optional<UUID> rangoDespues = rolDeMayorRango(despues).map(AssignableRole::id);
    return !rangoAntes.equals(rangoDespues);
  }

  /** Recorre el árbol hacia arriba con tope: un dato corrupto no debe colgar un hilo. */
  private boolean desciendeDeAlguno(AssignableRole rol, Set<UUID> candidatos) {
    UUID padre = rol.parentRoleId();
    for (int salto = 0; salto < SALTOS_MAXIMOS && padre != null; salto++) {
      if (candidatos.contains(padre)) {
        return true;
      }
      Optional<AssignableRole> siguiente = roles.findById(padre);
      if (siguiente.isEmpty()) {
        return false;
      }
      padre = siguiente.get().parentRoleId();
    }
    return false;
  }

  private static Comparator<AssignableRole> porCodigo() {
    return Comparator.comparing(AssignableRole::code);
  }
}
