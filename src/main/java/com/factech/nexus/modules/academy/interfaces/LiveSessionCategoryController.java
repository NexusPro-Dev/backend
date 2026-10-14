package com.factech.nexus.modules.academy.interfaces;

import com.factech.nexus.modules.academy.application.LiveSessionCategoryRequests.Delete;
import com.factech.nexus.modules.academy.application.LiveSessionCategoryRequests.Register;
import com.factech.nexus.modules.academy.application.LiveSessionCategoryRequests.Update;
import com.factech.nexus.modules.academy.application.LiveSessionCategoryResponse;
import com.factech.nexus.modules.academy.domain.service.LiveSessionCategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Las categorías de las clases en vivo (`RF-AC-063` a `RF-AC-066`, `RN-AC-034`): un catálogo
 * propio, distinto del de cursos, con nombre y orden. Una ruta por operación, un permiso por ruta
 * (`RN-SEG-014`).
 */
@RestController
@RequestMapping("/api/v1/live-session-categories")
@Tag(
    name = "Categorías de clases en vivo",
    description =
        "Consultar, registrar, corregir y retirar las categorías de las clases en vivo, como"
            + " Trading, Clases o Señales.")
public class LiveSessionCategoryController {

  private final LiveSessionCategoryService categorias;

  public LiveSessionCategoryController(LiveSessionCategoryService categorias) {
    this.categorias = categorias;
  }

  @GetMapping
  @PreAuthorize("hasAuthority('live-session-categories:list')")
  @Operation(
      summary = "Consultar las categorías de las clases en vivo",
      description =
          """
          **Las vivas**, sin paginar, por `displayOrder`, nombre e identificador: las que se
          pueden asignar al programar una clase y por las que se filtran los listados y la
          vitrina (`RN-AC-034`). Las retiradas no salen.

          Exige `live-session-categories:list`.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Las categorías."),
    @ApiResponse(responseCode = "401", description = "Sin sesión (`AUTH-001`)"),
    @ApiResponse(responseCode = "403", description = "Sin el permiso (`AUTH-002`)")
  })
  public List<LiveSessionCategoryResponse> listar() {
    return categorias.list();
  }

  @PostMapping
  @PreAuthorize("hasAuthority('live-session-categories:create')")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(
      summary = "Registrar una categoría de clases en vivo",
      description =
          """
          Con `name` —de 1 a 100 caracteres, **único entre las vivas** sin distinguir acentos ni
          mayúsculas; el de una retirada se admite— y `displayOrder`, entero mayor o igual que
          cero y no único. Se audita.

          Exige `live-session-categories:create`.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "201", description = "La categoría registrada."),
    @ApiResponse(
        responseCode = "400",
        description = "Nombre (`VAL-001`) u orden (`VAL-002`) inválidos"),
    @ApiResponse(responseCode = "401", description = "Sin sesión (`AUTH-001`)"),
    @ApiResponse(responseCode = "403", description = "Sin el permiso (`AUTH-002`)"),
    @ApiResponse(responseCode = "409", description = "Ya hay una viva con ese nombre (`EX-001`)")
  })
  public LiveSessionCategoryResponse registrar(@RequestBody(required = false) Register peticion) {
    return categorias.register(peticion);
  }

  @PatchMapping("/{id}")
  @PreAuthorize("hasAuthority('live-session-categories:update')")
  @Operation(
      summary = "Corregir una categoría de clases en vivo",
      description =
          """
          Parcial: `name` y `displayOrder`, al menos uno y **ninguno se vacía**. Solo una
          categoría viva. Las clases que la tienen muestran el nombre nuevo. Se audita lo que
          cambió; los mismos valores no auditan nada.

          Exige `live-session-categories:update`.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "La categoría corregida."),
    @ApiResponse(
        responseCode = "400",
        description =
            "Nombre (`VAL-001`) u orden (`VAL-002`) inválidos, o nada que corregir"
                + " (`VAL-003`)"),
    @ApiResponse(responseCode = "401", description = "Sin sesión (`AUTH-001`)"),
    @ApiResponse(responseCode = "403", description = "Sin el permiso (`AUTH-002`)"),
    @ApiResponse(responseCode = "404", description = "No existe o está retirada (`EX-001`)"),
    @ApiResponse(responseCode = "409", description = "Ya hay otra viva con ese nombre (`EX-002`)")
  })
  public LiveSessionCategoryResponse corregir(
      @PathVariable UUID id, @RequestBody(required = false) Update peticion) {
    return categorias.update(id, peticion);
  }

  @PostMapping("/{id}/deletion")
  @PreAuthorize("hasAuthority('live-session-categories:delete')")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(
      summary = "Retirar una categoría de clases en vivo",
      description =
          """
          Baja lógica con `reason` (`RN-AC-018`). **No toca las clases que la tienen**: la
          conservan y la siguen mostrando, pero ya no se asigna a ninguna y deja de salir en el
          listado. Libera el nombre. Se audita la eliminación.

          Exige `live-session-categories:delete`.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "204", description = "Retirada."),
    @ApiResponse(
        responseCode = "400",
        description = "Sin motivo, o motivo inválido (`VAL-002`, `VAL-003`)"),
    @ApiResponse(responseCode = "401", description = "Sin sesión (`AUTH-001`)"),
    @ApiResponse(responseCode = "403", description = "Sin el permiso (`AUTH-002`)"),
    @ApiResponse(responseCode = "404", description = "No existe (`EX-001`)"),
    @ApiResponse(responseCode = "409", description = "Ya está retirada (`EX-002`)")
  })
  public void retirar(@PathVariable UUID id, @RequestBody(required = false) Delete peticion) {
    categorias.delete(id, peticion);
  }
}
