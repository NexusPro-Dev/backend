package com.factech.nexus.modules.system.memberships.domain.models;

import com.factech.nexus.shared.error.FieldError;
import com.factech.nexus.shared.error.UnprocessableEntityException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * El invariante lineal de la cadena de membresías (`RF-SP-016` · `T-…`, `RN-SP-006`, `RN-SP-007`).
 *
 * <p><b>Es un objeto de dominio y no lógica dentro del servicio</b>, y la razón es que se pueda
 * probar. Dónde queda la nueva membresía y qué niveles cambian es la única regla de negocio real de
 * este requerimiento; metida en el caso de uso quedaría inseparable de la transacción y de los
 * puertos, y comprobar «insertar por encima de la superior» exigiría levantar PostgreSQL. Aquí los
 * seis casos de `spec.md` §9 y §13 se prueban con listas en memoria.
 *
 * <p><b>La cadena no es un árbol.</b> Cada membresía tiene una sola hija: es una lista ordenada.
 * Por eso «insertar» y no «añadir» — la operación reasigna la superior de la hija indicada, y que
 * esa hija ya tuviera otra superior es el caso normal, no un error (`spec.md` §13).
 *
 * <p><b>{@code level} es la distancia hasta la cima</b>: la superior tiene {@code 1} y el número
 * crece hacia abajo. Cuidado con el vocabulario de `RN-SP-006` —«una de <i>mayor nivel</i>»—, donde
 * <i>mayor</i> es jerárquico y no numérico: la superior de una membresía tiene siempre un {@code
 * level} menor.
 */
public final class MembershipChain {

  private final List<ChainLink> eslabones;

  private MembershipChain(List<ChainLink> eslabones) {
    this.eslabones = eslabones;
  }

  /**
   * Cadena vigente, ordenada de la cima hacia abajo.
   *
   * <p>Se ordena aquí y no se confía en el orden de llegada: quien lee de la base de datos puede
   * olvidarse del {@code ORDER BY}, y toda la aritmética de esta clase da por hecho que el primero
   * es la cima.
   */
  public static MembershipChain of(List<ChainLink> vigentes) {
    List<ChainLink> ordenados = new ArrayList<>(vigentes);
    ordenados.sort(Comparator.comparingInt(ChainLink::level));
    return new MembershipChain(List.copyOf(ordenados));
  }

  /** ¿La cadena está vacía? Es `FA-001`: la primera membresía del sistema. */
  public boolean vacia() {
    return eslabones.isEmpty();
  }

  public List<ChainLink> eslabones() {
    return eslabones;
  }

  /**
   * Calcula dónde entra una membresía nueva por encima de la hija indicada.
   *
   * <p>Tres formas, y ninguna es un caso especial escondido: son las tres que la especificación
   * describe.
   *
   * <ul>
   *   <li><b>Cadena vacía</b> (`FA-001`): la nueva es la superior, {@code level = 1}, sin vecinos.
   *   <li><b>Sin hija indicada</b> (`FA-002`): va al extremo inferior. Su superior es la actual
   *       última y <b>no toca ninguna otra fila</b>. Es el alta más común, y es la que fija la
   *       numeración de {@code level}: con la numeración inversa habría que renumerar la cadena
   *       entera precisamente aquí.
   *   <li><b>Con hija indicada</b>: la nueva ocupa el nivel de esa hija, hereda su superior, y todo
   *       lo que estaba en ese nivel o por debajo baja uno.
   * </ul>
   *
   * @param hijaIndicada membresía que quedará por debajo de la nueva; {@code null} para el extremo
   *     inferior
   * @throws UnprocessableEntityException `EX-002` si se indica una hija que no está en la cadena
   */
  public MembershipInsertion insertAbove(UUID hijaIndicada) {
    if (vacia()) {
      if (hijaIndicada != null) {
        throw hijaInexistente(hijaIndicada);
      }
      return new MembershipInsertion(1, null, null, List.of(), null, null);
    }

    if (hijaIndicada == null) {
      ChainLink ultima = eslabones.get(eslabones.size() - 1);
      return new MembershipInsertion(ultima.level() + 1, ultima.id(), null, List.of(), null, null);
    }

    ChainLink hija = buscar(hijaIndicada).orElseThrow(() -> hijaInexistente(hijaIndicada));
    int nivel = hija.level();

    // Todo lo que está en el nivel de la hija o por debajo baja uno. La hija
    // entra en este conjunto: también baja.
    List<LevelShift> desplazadas =
        eslabones.stream()
            .filter(eslabon -> eslabon.level() >= nivel)
            .map(eslabon -> new LevelShift(eslabon.id(), eslabon.level(), eslabon.level() + 1))
            .toList();

    // La superior de la hija pasa a ser la nueva membresía. Si la hija era la
    // cima, `parentId` es nulo y la nueva se convierte en la cima — que es el
    // primer caso límite de `spec.md` §13 y debe admitirse.
    return new MembershipInsertion(
        nivel, hija.parentId(), hija.id(), desplazadas, hija.id(), hija.parentId());
  }

  private Optional<ChainLink> buscar(UUID id) {
    return eslabones.stream().filter(eslabon -> eslabon.id().equals(id)).findFirst();
  }

  /**
   * `EX-002`, y es un {@code 422} y no un {@code 404}: el recurso de la ruta es la colección {@code
   * /api/v1/memberships}, que existe. Lo que no resuelve es una referencia del cuerpo.
   */
  private static UnprocessableEntityException hijaInexistente(UUID id) {
    return new UnprocessableEntityException(
        "EX-002",
        "La membresía indicada no existe.",
        List.of(new FieldError("childMembershipId", "EX-002", "La membresía indicada no existe.")));
  }
}
