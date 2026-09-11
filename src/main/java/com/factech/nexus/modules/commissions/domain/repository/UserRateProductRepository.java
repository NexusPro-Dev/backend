package com.factech.nexus.modules.commissions.domain.repository;

import com.factech.nexus.modules.commissions.domain.models.UserRateProduct;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * La asociación entre una tasa personalizada y los productos sobre los que rige (`RN-CM-014`).
 *
 * <p><b>Gemelo de {@code ProductCommissionRateRepository}</b>, con una diferencia que importa: aquí
 * vive también {@link #haySolape}, porque `RN-CM-006` <b>dejó el motor el 11-09-2026</b>. El {@code
 * EXCLUDE} cabía mientras persona, vigencia y producto estaban en la misma fila; con la asociación
 * aparte la regla cruza dos tablas, y ningún índice hace eso.
 */
public interface UserRateProductRepository {

  void save(UserRateProduct asociacion);

  boolean existe(UUID rateId, UUID productId);

  /** Borrado <b>físico</b>: una asociación es configuración vigente, no un hecho del pasado. */
  void borrar(UUID rateId, UUID productId);

  /** `RN-CM-015`: si queda alguna, la tasa no se retira. */
  boolean tieneAsociaciones(UUID rateId);

  /** Los productos donde rige, para revalidar `RN-CM-006` y `RN-CM-019` al corregir. */
  List<UUID> productosDe(UUID rateId);

  /**
   * Los mismos productos, <b>resueltos</b>, para armar la respuesta en una sola sentencia.
   *
   * <p>Con {@link #productosDe} y una llamada al catálogo por cada uno saldría un N+1 que no
   * rompería ninguna prueba: la respuesta sería correcta y solo lenta.
   */
  List<ProductoAsociado> asociadosDe(UUID rateId);

  /** Un producto asociado, con lo que la respuesta publica de él. */
  record ProductoAsociado(UUID id, String code, String name) {}

  /**
   * `RN-CM-006` fuera del motor: ¿hay otra tasa viva de esa persona, asociada a ese producto, que
   * cubra alguno de esos días?
   *
   * <p><b>Se consulta ANTES de escribir y bajo bloqueo</b>, al revés que {@code
   * UserCommissionRateRepository#findOverlapping}, que se consulta DESPUÉS de que el motor haya
   * rechazado. La diferencia es exactamente lo que se perdió al retirar el {@code EXCLUDE}: aquí no
   * hay nadie detrás que atrape el caso, de modo que el bloqueo es la única defensa.
   *
   * @param excluida la tasa que se está asociando o corrigiendo, que no compite consigo misma
   */
  boolean haySolape(
      UUID userId, UUID productId, UUID excluida, LocalDate validFrom, LocalDate validTo);
}
