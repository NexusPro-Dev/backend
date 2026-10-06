package com.factech.nexus.modules.system.auth.domain.repository;

import com.factech.nexus.modules.system.auth.domain.models.MfaFactor;
import com.factech.nexus.modules.system.auth.domain.models.RecoveryCode;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Los factores de cada persona y sus códigos de recuperación (`RF-SP-071` a `RF-SP-076`).
 *
 * <p>Las lecturas «para actualizar» toman la fila con {@code FOR UPDATE}: son las que deciden algo
 * —confirmar, aceptar un código, retirar— y dos peticiones concurrentes tienen que ordenarse en la
 * fila y no en el caso de uso (`071` · `plan.md` §7).
 */
public interface MfaFactorRepository {

  Optional<MfaFactor> activoParaActualizar(UUID userId);

  Optional<MfaFactor> pendienteParaActualizar(UUID userId);

  Optional<MfaFactor> activo(UUID userId);

  /** Inserta y vacía el contexto, para que el orden de las escrituras sea el del caso de uso. */
  void guardar(MfaFactor factor);

  /** Vacía el contexto: un cambio de estado tiene que llegar a la base antes del siguiente. */
  void sincronizar();

  void guardarCodigos(List<RecoveryCode> codigos);

  /** Los códigos que todavía sirven de un factor: ni usados ni anulados. */
  List<RecoveryCode> codigosVigentesParaActualizar(UUID factorId);

  /** Anula los vigentes de un factor (`RF-SP-074`). Devuelve cuántos. */
  int anularCodigosVigentes(UUID factorId, OffsetDateTime ahora);

  /** El nombre de usuario, para que la app muestre la entrada reconocible. */
  String nombreDeUsuario(UUID userId);
}
