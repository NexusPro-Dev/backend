package com.factech.nexus.modules.system.teams.domain.models;

/**
 * El estado de un equipo (`RN-SP-053`): el mismo dominio cerrado que declara {@code
 * ck_teams_status} en `V33`.
 *
 * <p><b>`INACTIVO` significa «no recibe», no «no existe» ni «está vacío»</b>: un equipo suspendido
 * conserva a sus miembros, porque cerrarlos movería la atribución de toda una red —cada manager
 * arrastra a sus directores y a los agentes de estos— sin que nadie lo hubiera decidido. Quien
 * quiera vaciarlo lo hace miembro a miembro y con motivo (`RF-SP-070`).
 *
 * <p>Es distinto de la baja lógica, y por eso conviven: `INACTIVO` es «ya no organizo con él, y
 * quiero verlo con su gente»; eliminado es «no debería existir», exige vaciarlo antes (`RN-SP-054`)
 * y sale del listado. Un rol tiene los dos por la misma razón.
 */
public enum TeamStatus {
  ACTIVO,
  INACTIVO
}
