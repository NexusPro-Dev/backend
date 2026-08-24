package com.factech.nexus.modules.system.memberships.domain.models;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.factech.nexus.shared.error.UnprocessableEntityException;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * El invariante lineal de la cadena (`RF-SP-016`, `RN-SP-006`, `RN-SP-007`).
 *
 * <p><b>Sin Spring y sin base de datos</b>, que es exactamente la razón de que {@link
 * MembershipChain} sea un objeto de dominio y no lógica dentro del servicio: los seis casos de
 * `spec.md` §9 y §13 se comprueban con listas en memoria (Art. VI.3).
 */
class MembershipChainTest {

  private static final UUID ORO = UUID.fromString("01a02a33-4c00-7101-9c4f-5e7ad2000001");
  private static final UUID PLATA = UUID.fromString("01a02a33-4c00-7102-9c4f-5e7ad2000002");
  private static final UUID BRONCE = UUID.fromString("01a02a33-4c00-7103-9c4f-5e7ad2000003");

  /** ORO(1) → PLATA(2) → BRONCE(3). */
  private static MembershipChain cadenaDeTres() {
    return MembershipChain.of(
        List.of(
            new ChainLink(ORO, null, 1),
            new ChainLink(PLATA, ORO, 2),
            new ChainLink(BRONCE, PLATA, 3)));
  }

  @Test
  @DisplayName("CA-SP-111 — FA-001: la primera membresía es la superior, nivel 1 y sin vecinos")
  void primeraMembresia() {
    MembershipInsertion posicion = MembershipChain.of(List.of()).insertAbove(null);

    assertThat(posicion.level()).isEqualTo(1);
    assertThat(posicion.parentId()).isNull();
    assertThat(posicion.childId()).isNull();
    assertThat(posicion.sinReordenamiento()).isTrue();
  }

  @Test
  @DisplayName("CA-SP-113 — FA-002: sin hija indicada va al extremo inferior y no toca nada")
  void extremoInferior() {
    MembershipInsertion posicion = cadenaDeTres().insertAbove(null);

    assertThat(posicion.level()).isEqualTo(4);
    assertThat(posicion.parentId()).isEqualTo(BRONCE);
    assertThat(posicion.childId()).isNull();
    // Es la propiedad que fija la numeración de `level`: con la numeración
    // inversa, el alta más común renumeraría la cadena entera.
    assertThat(posicion.desplazadas()).isEmpty();
    assertThat(posicion.reencadenada()).isNull();
  }

  @Test
  @DisplayName(
      "CA-SP-112 y CA-SP-115 — inserción en medio: ocupa el nivel de la hija y baja el resto")
  void insercionEnMedio() {
    MembershipInsertion posicion = cadenaDeTres().insertAbove(PLATA);

    assertThat(posicion.level()).isEqualTo(2);
    assertThat(posicion.parentId()).isEqualTo(ORO);
    assertThat(posicion.childId()).isEqualTo(PLATA);

    // PLATA(2)→3 y BRONCE(3)→4. ORO no se toca.
    assertThat(posicion.desplazadas())
        .containsExactly(new LevelShift(PLATA, 2, 3), new LevelShift(BRONCE, 3, 4));

    assertThat(posicion.reencadenada()).isEqualTo(PLATA);
    assertThat(posicion.superiorAnteriorDeLaReencadenada()).isEqualTo(ORO);
  }

  @Test
  @DisplayName("insertar por encima de la superior convierte a la nueva en la cima")
  void porEncimaDeLaCima() {
    // Primer caso límite de `spec.md` §13: debe admitirse.
    MembershipInsertion posicion = cadenaDeTres().insertAbove(ORO);

    assertThat(posicion.level()).isEqualTo(1);
    assertThat(posicion.parentId()).isNull();
    assertThat(posicion.childId()).isEqualTo(ORO);
    assertThat(posicion.desplazadas()).hasSize(3);
    assertThat(posicion.superiorAnteriorDeLaReencadenada()).isNull();
  }

  @Test
  @DisplayName("CA-SP-114 — tras la inserción cada membresía sigue teniendo como mucho una hija")
  void sinBifurcacion() {
    // La nueva toma como hija a PLATA, y PLATA deja de colgar de ORO. Nadie más
    // queda apuntando a ORO: la única que lo hacía era PLATA.
    MembershipInsertion posicion = cadenaDeTres().insertAbove(PLATA);

    assertThat(posicion.childId()).isEqualTo(PLATA);
    assertThat(posicion.reencadenada()).isEqualTo(PLATA);
    assertThat(posicion.parentId()).isEqualTo(ORO);
  }

  @Test
  @DisplayName("CA-SP-117 — una hija que no está en la cadena produce EX-002")
  void hijaInexistente() {
    assertThatThrownBy(() -> cadenaDeTres().insertAbove(UUID.randomUUID()))
        .isInstanceOf(UnprocessableEntityException.class)
        .extracting(fallo -> ((UnprocessableEntityException) fallo).errorCode())
        .isEqualTo("EX-002");
  }

  @Test
  @DisplayName("indicar hija sobre una cadena vacía también es EX-002")
  void hijaSobreCadenaVacia() {
    assertThatThrownBy(() -> MembershipChain.of(List.of()).insertAbove(UUID.randomUUID()))
        .isInstanceOf(UnprocessableEntityException.class);
  }

  @Test
  @DisplayName("cadena de una sola membresía: por encima y por debajo son las dos únicas opciones")
  void cadenaDeUnaSola() {
    MembershipChain sola = MembershipChain.of(List.of(new ChainLink(ORO, null, 1)));

    MembershipInsertion arriba = sola.insertAbove(ORO);
    assertThat(arriba.level()).isEqualTo(1);
    assertThat(arriba.parentId()).isNull();
    assertThat(arriba.desplazadas()).containsExactly(new LevelShift(ORO, 1, 2));

    MembershipInsertion abajo = sola.insertAbove(null);
    assertThat(abajo.level()).isEqualTo(2);
    assertThat(abajo.parentId()).isEqualTo(ORO);
    assertThat(abajo.desplazadas()).isEmpty();
  }

  @Test
  @DisplayName("la cadena se ordena por nivel aunque llegue desordenada")
  void seOrdenaSola() {
    // Toda la aritmética da por hecho que el primero es la cima. Confiar en el
    // ORDER BY de quien lee sería confiar en que nadie lo olvide.
    MembershipChain desordenada =
        MembershipChain.of(
            List.of(
                new ChainLink(BRONCE, PLATA, 3),
                new ChainLink(ORO, null, 1),
                new ChainLink(PLATA, ORO, 2)));

    assertThat(desordenada.eslabones())
        .extracting(ChainLink::id)
        .containsExactly(ORO, PLATA, BRONCE);
    assertThat(desordenada.insertAbove(null).parentId()).isEqualTo(BRONCE);
  }
}
