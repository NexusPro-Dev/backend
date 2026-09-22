package com.factech.nexus.shared.security;

import java.util.UUID;

/**
 * Declarar que los tokens de acceso ya emitidos de una persona dejan de valer (`RF-SP-028`
 * `plan.md` §7).
 *
 * <p><b>Por qué hace falta, junto a {@link SessionRevoker} y no en su lugar.</b> Los dos cortan
 * accesos distintos y ninguno cubre al otro. `SessionRevoker` revoca los <b>refresh tokens</b>, que
 * viven en la base de datos y por tanto pueden marcarse: impide que la sesión se <b>prolongue</b>.
 * Pero el <b>token de acceso</b> ya emitido es un JWT firmado que se valida sin consultar nada, y
 * sigue abriendo puertas durante los quince minutos que le quedan de vida. `security.md` §4.5 exige
 * que el retiro sea <b>inmediato</b>, y sin este puerto no lo es: quien acaba de ser desactivado
 * conserva un cuarto de hora de acceso.
 *
 * <p><b>Vive en {@code shared} por lo mismo que `SessionRevoker`</b>: quien lo necesita
 * —`RF-SP-028`, `RF-SP-029`, `RF-SP-037` y `RF-SP-038`— no puede depender de quien lo implementa.
 * El puerto solo habla de un identificador y de una intención.
 *
 * <p><b>Es un puerto y no una clase concreta por una razón que ya está escrita.</b> `RF-SP-028`
 * `plan.md` §10 declara el riesgo: con más de una instancia del backend, un registro en memoria
 * solo corta en la que atendió la petición. La corrección prevista es un canal compartido <b>detrás
 * de este mismo puerto</b>, sin tocar ningún caso de uso. Debe resolverse antes de desplegar una
 * segunda instancia (<b>D-09</b>).
 */
public interface AccessRevocationPublisher {

  /**
   * Corta los tokens de acceso de esa persona: los ya emitidos dejan de admitirse.
   *
   * <p><b>El corte se publica después del commit</b>, y quien lo garantiza es la implementación, no
   * quien la llama. Es deliberado: publicarlo antes rechazaría tokens por un cambio que todavía
   * puede revertirse, y dejar esa disciplina en manos de cada caso de uso es pedir que uno de los
   * cuatro se equivoque. Es la asimetría inversa a la de `SessionRevoker`, que sí va <b>dentro</b>
   * de la transacción porque escribe en la base y debe revertirse con ella.
   */
  void publicarCorte(UUID userId);
}
