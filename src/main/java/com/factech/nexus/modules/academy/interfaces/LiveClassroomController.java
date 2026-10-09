package com.factech.nexus.modules.academy.interfaces;

import com.factech.nexus.modules.academy.application.LiveSessionRequests.AvailableFilter;
import com.factech.nexus.modules.academy.application.LiveSessionResponses.Available;
import com.factech.nexus.modules.academy.application.LiveSessionResponses.Join;
import com.factech.nexus.modules.academy.domain.service.LiveClassroomService;
import com.factech.nexus.modules.academy.domain.service.LiveClassroomService.Entrada;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Las clases en vivo para el alumno (`RF-AC-053`, `RF-AC-054`): verlas y entrar. <b>El actor sale
 * del token</b>: no hay por dónde entrar por otra persona.
 */
@RestController
@RequestMapping("/api/v1/live-sessions/available")
@Tag(name = "Aula en vivo", description = "Las clases en vivo que vienen, y entrar a ellas.")
public class LiveClassroomController {

  private final LiveClassroomService aula;

  public LiveClassroomController(LiveClassroomService aula) {
    this.aula = aula;
  }

  @GetMapping
  @PreAuthorize("hasAuthority('live-sessions:learn')")
  @Operation(
      summary = "Consultar las clases en vivo como alumno",
      description =
          """
          **Las clases programadas que no han terminado**, por inicio, sin paginar. Cada una
          dice **`accessible`** —se le abre porque su membresía o uno de sus servicios
          vigentes está en la lista, o porque la clase no declara ninguno—, **`registered`**
          si ya pidió entrar, `inProgress` si ya empezó, y **las membresías y servicios que la
          abren**, que es la invitación. `onlyAccessible=true` deja solo las suyas; `courseId`
          acota a un curso. **Nada de Zoom**: el enlace se pide al entrar.

          Exige `live-sessions:learn`.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Las clases."),
    @ApiResponse(responseCode = "400", description = "Filtro mal formado (`VAL-001`)"),
    @ApiResponse(responseCode = "403", description = "Sin el permiso (`AUTH-002`)")
  })
  public List<Available> clases(
      @org.springdoc.core.annotations.ParameterObject @ModelAttribute AvailableFilter filtros) {
    return aula.available(filtros);
  }

  @PostMapping("/{id}/registration")
  @PreAuthorize("hasAuthority('live-sessions:join')")
  @Operation(
      summary = "Entrar a una clase en vivo",
      description =
          """
          **Entrar es registrarse.** Si la clase está programada, no terminó y **se le abre** a
          quien pide, la plataforma lo registra en Zoom con su nombre y su correo y devuelve
          **su enlace personal** (`201`). **La segunda vez devuelve el mismo enlace sin llamar a
          Zoom** (`200`). El acceso se comprueba en cada petición. El enlace sirve a un
          dispositivo a la vez y **no se debe compartir**.

          Una clase cancelada, terminada o inexistente responde `404`; una que no se le abre,
          `403` con `EX-002` y los miembros `memberships` y `products`: lo que la abre.

          Exige `live-sessions:join`.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "201", description = "Registrado: el enlace personal."),
    @ApiResponse(responseCode = "200", description = "Ya estaba registrado: el mismo enlace."),
    @ApiResponse(
        responseCode = "403",
        description = "Sin el permiso (`AUTH-002`), o la clase no se le abre (`EX-002`)"),
    @ApiResponse(
        responseCode = "404",
        description = "No existe, está cancelada o terminó (`EX-001`)"),
    @ApiResponse(responseCode = "503", description = "Zoom no disponible (`EX-003`)")
  })
  public ResponseEntity<Join> entrar(@PathVariable UUID id) {
    Entrada entrada = aula.join(id);
    return ResponseEntity.status(entrada.nuevo() ? HttpStatus.CREATED : HttpStatus.OK)
        .body(entrada.join());
  }
}
