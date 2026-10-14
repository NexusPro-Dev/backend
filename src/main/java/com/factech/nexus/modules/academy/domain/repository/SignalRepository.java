package com.factech.nexus.modules.academy.domain.repository;

import com.factech.nexus.modules.academy.domain.models.Signal;
import com.factech.nexus.modules.academy.domain.models.SubscriptionType;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Las señales de trading (`RF-AC-067` a `RF-AC-072`, `RN-AC-035`). */
public interface SignalRepository {

  /** Una membresía tal como la muestra una señal. */
  record MembershipRef(UUID id, String code, String name) {}

  /**
   * El filtro de administración: {@code visible} nulo, todas; verdadero, las que se muestran ahora;
   * falso, las que ya no. Las retiradas solo con {@code includeDeleted}.
   */
  record SignalFilter(
      SubscriptionType subscriptionType,
      Boolean visible,
      boolean includeDeleted,
      OffsetDateTime ahora) {}

  /** Guarda la señal y sus membresías. */
  void insert(Signal senal, Collection<UUID> membresias);

  Optional<Signal> findByIdForUpdate(UUID id);

  List<Signal> search(SignalFilter filtro, int offset, int size);

  long count(SignalFilter filtro);

  /**
   * Las vigentes que abre esa membresía (`RN-AC-035`): sin retirar, con {@code visible_until}
   * posterior a {@code ahora}; FREE con cualquier membresía, PREMIUM con una por encima de BECA y
   * PERSONALIZED con una de las suyas. Sin membresía, ninguna.
   */
  List<Signal> available(OffsetDateTime ahora, UUID membresia);

  /** Las membresías de cada señal, por nivel. */
  Map<UUID, List<MembershipRef>> membershipsOf(Collection<UUID> senales);

  /** Las que existen entre esos identificadores. */
  Set<UUID> existingMemberships(Collection<UUID> ids);

  /** Las que existen entre esos códigos, por código en mayúsculas. */
  Map<String, UUID> membershipsByCode(Collection<String> codigos);
}
