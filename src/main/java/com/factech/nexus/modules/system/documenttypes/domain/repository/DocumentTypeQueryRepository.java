package com.factech.nexus.modules.system.documenttypes.domain.repository;

import com.factech.nexus.modules.system.documenttypes.application.DocumentTypeItem;
import java.util.List;

/**
 * Lectura del catálogo de tipos de documento (`RF-SP-051`).
 *
 * <p><b>No declara ningún método de escritura, y esa ausencia es `RN-SP-036` en el código.</b> El
 * catálogo se puebla por migración: no hay alta, ni edición, ni eliminación, ni cambio de estado.
 * Un puerto con un {@code save} que nadie invoca acabaría invocándose.
 *
 * <p>Tampoco hay un agregado {@code DocumentType}: es un catálogo que nadie escribe, y un agregado
 * con un constructor que ningún caso de uso llama es código muerto que además sugiere que existe un
 * alta.
 */
public interface DocumentTypeQueryRepository {

  /** El catálogo completo, ordenado por nombre. Sin paginar: son unos pocos elementos. */
  List<DocumentTypeItem> findAll(boolean incluirInactivos);
}
