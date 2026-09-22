package com.factech.nexus.modules.system.documenttypes.domain.service;

import com.factech.nexus.modules.system.documenttypes.application.DocumentTypeCatalogResponse;
import com.factech.nexus.modules.system.documenttypes.application.ListDocumentTypesRequest;
import com.factech.nexus.modules.system.documenttypes.domain.repository.DocumentTypeQueryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * El catálogo de tipos de documento (`RF-SP-051`).
 *
 * <p><b>Sin ninguna regla que aplicar</b>, y eso es lo que hay que entender de este servicio: la
 * única regla que gobierna este catálogo —que solo contenga documentos de mayor de edad— vive en el
 * <b>contenido</b> de la tabla y en {@code fk_users_document_type}, no aquí. No hay nada que
 * comprobar porque no hay nada que pueda estar mal.
 *
 * <p><b>Los inactivos se AÑADEN, no sustituyen.</b> Pedirlos devuelve activos e inactivos juntos, y
 * es el criterio de `RF-SP-021` y `RF-SP-019`: un filtro que reemplazara el conjunto obligaría a
 * dos llamadas para ver el catálogo entero.
 */
@Service
public class ListDocumentTypesService {

  private final DocumentTypeQueryRepository catalogo;

  public ListDocumentTypesService(DocumentTypeQueryRepository catalogo) {
    this.catalogo = catalogo;
  }

  @Transactional(readOnly = true)
  public DocumentTypeCatalogResponse list(ListDocumentTypesRequest filtros) {
    ListDocumentTypesRequest efectivos =
        filtros == null ? new ListDocumentTypesRequest(null) : filtros;
    return new DocumentTypeCatalogResponse(catalogo.findAll(efectivos.incluirInactivos()));
  }
}
