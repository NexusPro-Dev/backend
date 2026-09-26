package com.factech.nexus.shared.video;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** `RN-AC-005` desde el 25-09-2026: las siete formas de YouTube y Vimeo, y nada más. */
class VideoLinkTest {

  @Test
  @DisplayName("reconoce las cuatro formas de YouTube con su identificador de once caracteres")
  void youtube() {
    for (String enlace :
        new String[] {
          "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
          "https://youtube.com/watch?feature=share&v=dQw4w9WgXcQ&t=10",
          "https://m.youtube.com/watch?v=dQw4w9WgXcQ",
          "https://youtu.be/dQw4w9WgXcQ?si=abc",
          "https://www.youtube.com/embed/dQw4w9WgXcQ",
          "https://www.youtube.com/shorts/dQw4w9WgXcQ"
        }) {
      assertThat(VideoLink.reconocer(enlace))
          .as(enlace)
          .contains(new VideoLink(VideoProvider.YOUTUBE, "dQw4w9WgXcQ", null));
    }
  }

  @Test
  @DisplayName("reconoce las tres formas de Vimeo, con el código del no listado")
  void vimeo() {
    assertThat(VideoLink.reconocer("https://vimeo.com/76979871"))
        .contains(new VideoLink(VideoProvider.VIMEO, "76979871", null));
    assertThat(VideoLink.reconocer("https://vimeo.com/76979871/ab12cd34ef"))
        .contains(new VideoLink(VideoProvider.VIMEO, "76979871", "ab12cd34ef"));
    assertThat(VideoLink.reconocer("https://player.vimeo.com/video/76979871"))
        .contains(new VideoLink(VideoProvider.VIMEO, "76979871", null));
    assertThat(VideoLink.reconocer("https://player.vimeo.com/video/76979871?h=ab12cd34ef&badge=0"))
        .contains(new VideoLink(VideoProvider.VIMEO, "76979871", "ab12cd34ef"));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "https://v.io/1",
        "https://evil.com/?u=https://youtu.be/dQw4w9WgXcQ",
        "https://youtube.com.evil.com/watch?v=dQw4w9WgXcQ",
        "https://www.youtube.com/watch?v=corto",
        "https://www.youtube.com/channel/UCxyz",
        "https://vimeo.com/canal",
        "https://vimeo.com/123 456",
        "ftp://vimeo.com/123",
        "vimeo.com/123",
        ""
      })
  @DisplayName("rechaza otros dominios, formas que no son de un video y lo que no es un enlace")
  void rechaza(String enlace) {
    assertThat(VideoLink.reconocer(enlace)).isEmpty();
    assertThat(enlace.isEmpty() || !enlace.matches(VideoLink.PATRON)).isTrue();
  }

  @Test
  @DisplayName("rechaza un enlace de más de 500 caracteres aunque sea de YouTube")
  void largo() {
    String enlace = "https://youtu.be/dQw4w9WgXcQ?x=" + "a".repeat(500);
    assertThat(VideoLink.reconocer(enlace)).isEmpty();
  }
}
