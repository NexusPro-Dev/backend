package com.factech.nexus.modules.system.auth.domain.repository;

import com.factech.nexus.modules.system.auth.domain.models.MfaChallenge;
import java.util.Optional;

/** Los desafíos del inicio de sesión en dos pasos (`RF-SP-072`). */
public interface MfaChallengeRepository {

  void guardar(MfaChallenge desafio);

  /** Por su resumen y con la fila bloqueada: es la primera lectura del segundo paso. */
  Optional<MfaChallenge> buscarPorHashParaActualizar(String challengeHash);
}
