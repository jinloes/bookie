package com.bookie.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.bookie.BookieApplication;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

class ModuleDocumentationTest {

  private static final Path OUTPUT = Path.of("build", "spring-modulith-docs");

  @Test
  void writesModuleDiagramsAndCanvasesUnderTheBuildDirectory() throws Exception {
    new Documenter(ApplicationModules.of(BookieApplication.class), OUTPUT.toString())
        .writeDocumentation();

    try (Stream<Path> files = Files.list(OUTPUT)) {
      assertThat(files.map(path -> path.getFileName().toString()))
          .contains("components.puml", "module-intake.puml", "module-intake.adoc");
    }
  }
}
