package com.factech.nexus.modules.commissions.domain.repository;

import com.factech.nexus.modules.commissions.domain.models.RateSource;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/** Puerto de resolución de la comisión efectiva (`RF-CM-005`). */
public interface CommissionResolutionRepository {

  /**
   * Qué porcentaje le corresponde a esa persona por ese producto en esa fecha (`RN-CM-004`).
   *
   * <p><b>La precedencia se resuelve en la sentencia</b>, no leyendo dos veces y eligiendo en Java:
   * con dos lecturas el orden viviría en el flujo de control y una refactorización podría alterarlo
   * sin que nada falle — devolvería un porcentaje <b>plausible</b>.
   *
   * <p><b>{@code roleId} admite el nulo, y es deliberado.</b> Quien no porta rol vendedor puede
   * seguir teniendo una tasa personalizada viva: al quitarle el rol a esas tasas (01-09-2026),
   * <b>dejaron de morir con el rol de su titular</b> (`cm.md` §5.3). El nulo apaga la rama del rol
   * y deja la personalizada respondiendo, que es exactamente lo que el modelo declara.
   *
   * @param roleId el rol vendedor de la persona, o {@code null} si no porta ninguno
   */
  Optional<ResolvedRate> resolve(UUID roleId, UUID productId, UUID userId, LocalDate fecha);

  /**
   * La tasa que ganó, y de cuál de las dos piezas salió.
   *
   * <p>La vigencia llega <b>nula</b> cuando la fuente es el rol, y no es un dato que falte: las
   * tasas de rol no tienen vigencia.
   */
  record ResolvedRate(
      RateSource source,
      UUID rateId,
      BigDecimal percentage,
      LocalDate validFrom,
      LocalDate validTo) {}
}
