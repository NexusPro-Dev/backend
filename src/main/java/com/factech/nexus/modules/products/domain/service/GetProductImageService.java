package com.factech.nexus.modules.products.domain.service;

import com.factech.nexus.modules.products.domain.models.ProductImage;
import com.factech.nexus.modules.products.domain.repository.ProductImageRepository;
import com.factech.nexus.shared.error.ResourceNotFoundException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * `RF-PM-016` — la imagen de una portada, por su identificador y sin token.
 *
 * <p><b>No mira el producto</b> (`requirements/pm.md` §5.2.9): ni estado, ni alcance, ni retiro.
 * Una sentencia, sobre {@code product_images} y nada más — es la única lectura del sistema que
 * carga {@code content}. El {@code 404} es el mismo para un identificador que nunca existió, uno
 * reemplazado y uno quitado: para quien pregunta son lo mismo, y distinguirlos diría que hubo una
 * portada.
 */
@Service
public class GetProductImageService {

  private final ProductImageRepository imagenes;

  public GetProductImageService(ProductImageRepository imagenes) {
    this.imagenes = imagenes;
  }

  @Transactional(readOnly = true)
  public ProductImage get(UUID id) {
    return imagenes
        .findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("EX-001", "La imagen no existe."));
  }
}
