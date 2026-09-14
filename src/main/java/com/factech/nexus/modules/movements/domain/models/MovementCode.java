package com.factech.nexus.modules.movements.domain.models;

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.random.RandomGenerator;

/**
 * El código del comprobante (`RN-MV-016`, `requirements/mv.md` §7.2.1).
 *
 * <p>{@code <prefijo>-<AAAAMMDD>-<seis aleatorios>} — por ejemplo {@code VTA-20260904-K7M2QX}.
 *
 * <h2>No sustituye al identificador, y por eso puede ser corto</h2>
 *
 * <p>La venta la identifica su {@code uuid}. Este código existe para <b>dictarse por teléfono y
 * teclearse</b>: es lo que va impreso en el papel que se le entrega al cliente. Que sea legible es
 * su único requisito de forma, y que sea único, el único de fondo.
 *
 * <h2>Los seis caracteres son ALEATORIOS y no correlativos</h2>
 *
 * <p>Una serie sin huecos no sobrevive ni a una transacción revertida —una {@code SEQUENCE} de
 * PostgreSQL los deja por diseño— ni a una carga histórica, y prometerla obligaría a renumerar lo
 * ya emitido el día que se rompiera. Lo que sí se promete es la unicidad, y la garantiza {@code
 * uq_movements_code} y no este generador: <b>treinta y dos elevado a seis</b> por tipo y día hace
 * la colisión improbable y <b>no imposible</b>, y sin el índice produciría dos comprobantes iguales
 * sin que nada avisara.
 *
 * <h2>El alfabeto es el de Crockford: 32 símbolos SIN {@code I}, {@code L}, {@code O} ni {@code U}
 * </h2>
 *
 * <p>Las tres primeras se caen porque este código se dicta y se teclea: {@code O} contra {@code 0}
 * y {@code I}/{@code L} contra {@code 1} son <b>los</b> errores que se cometen. La {@code U} se
 * descarta por un motivo distinto: completa palabras que nadie quiere leer en un comprobante.
 *
 * <h2>El día se corta en {@code America/Bogota}, y sale de {@code occurred_at}</h2>
 *
 * <p>Las dos cosas importan y por separado. <b>La zona</b>: una venta de las 23:30 en Bogotá es
 * 04:30 UTC del día siguiente, y con el corte en UTC llevaría impresa una fecha que no es la del
 * día en que se vendió. <b>El origen</b>: es {@code occurred_at} y no {@code created_at}, de modo
 * que la venta del sábado registrada el lunes lleva el sábado — que es justo para lo que existe esa
 * columna (`requirements/mv.md` §7.1).
 */
public final class MovementCode {

  /**
   * El alfabeto de Crockford, exactamente. No lleva {@code I}, {@code L}, {@code O} ni {@code U}, y
   * está escrito en orden para que la ausencia sea visible al leerlo.
   */
  static final String ALFABETO = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";

  /** `RN-MV-016`: seis, ni cinco ni siete. */
  static final int LONGITUD = 6;

  /**
   * La zona en la que este sistema corta los días (`architecture.md` §15.1.1). No es una constante
   * de este módulo: es la del proyecto, y aquí se referencia para que el día del comprobante sea el
   * mismo que el de cualquier otro corte diario.
   */
  public static final ZoneId ZONA = ZoneId.of("America/Bogota");

  private static final DateTimeFormatter DIA = DateTimeFormatter.ofPattern("yyyyMMdd");

  /**
   * {@code SecureRandom} y no {@code Random}. No porque el código sea un secreto —no lo es, y no
   * concede nada—, sino porque un generador sembrado por el reloj hace que dos procesos arrancados
   * en el mismo milisegundo emitan la misma secuencia, y eso es una colisión sistemática en lugar
   * de la improbable que este diseño acepta.
   */
  private static final RandomGenerator AZAR = new SecureRandom();

  private MovementCode() {}

  /** El código de una venta de ese tipo, ocurrida en ese instante. */
  public static String generar(String prefijo, OffsetDateTime ocurrioEn) {
    return generar(prefijo, ocurrioEn, AZAR);
  }

  /**
   * La misma composición, con el azar inyectado.
   *
   * <p>Existe para que la prueba del <b>reintento acotado</b> pueda forzar la colisión: sin poder
   * fijar el generador, «tres intentos y falla» solo se podría comprobar esperando a que el azar
   * repita, que es exactamente lo que no ocurre.
   */
  public static String generar(String prefijo, OffsetDateTime ocurrioEn, RandomGenerator azar) {
    StringBuilder sufijo = new StringBuilder(LONGITUD);
    for (int i = 0; i < LONGITUD; i++) {
      sufijo.append(ALFABETO.charAt(azar.nextInt(ALFABETO.length())));
    }
    return prefijo + "-" + ocurrioEn.atZoneSameInstant(ZONA).format(DIA) + "-" + sufijo;
  }
}
