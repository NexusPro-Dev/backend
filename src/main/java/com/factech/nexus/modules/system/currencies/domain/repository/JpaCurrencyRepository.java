package com.factech.nexus.modules.system.currencies.domain.repository;

import com.factech.nexus.modules.system.currencies.domain.models.Currency;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/** Adaptador de escritura (`RF-SP-023`). */
@Repository
public class JpaCurrencyRepository implements CurrencyRepository {

  private final EntityManager em;

  public JpaCurrencyRepository(EntityManager em) {
    this.em = em;
  }

  /**
   * {@code PESSIMISTIC_WRITE} se traduce a {@code SELECT … FOR UPDATE}.
   *
   * <p>Se bloquea <b>una fila</b> y no la tabla: los cambios de estado de monedas distintas no se
   * estorban entre sí, y el listado de `RF-SP-019` no se bloquea en absoluto porque {@code FOR
   * UPDATE} no afecta a los lectores.
   */
  @Override
  public Optional<Currency> findByIdForUpdate(UUID id) {
    return Optional.ofNullable(em.find(Currency.class, id, LockModeType.PESSIMISTIC_WRITE));
  }
}
