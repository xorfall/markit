package com.markit.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Enforces the Clean/Hexagonal dependency rule (ADR-0001, NFR-MAINT-001): the {@code domain} layer
 * of any bounded context must stay free of framework and infrastructure code. Rules pass vacuously
 * until domain packages exist, then guard every future slice.
 */
@AnalyzeClasses(packages = "com.markit", importOptions = ImportOption.DoNotIncludeTests.class)
class LayeredArchitectureTest {

  // allowEmptyShould: these guard future slices; until a `domain` package exists they match no
  // classes and must pass vacuously rather than fail on empty.
  @ArchTest
  static final ArchRule domain_is_free_of_frameworks =
      noClasses()
          .that().resideInAPackage("..domain..")
          .should().dependOnClassesThat().resideInAnyPackage(
              "org.springframework..",
              "jakarta.persistence..",
              "org.hibernate..",
              "org.springframework.data..")
          .allowEmptyShould(true);

  @ArchTest
  static final ArchRule domain_does_not_depend_on_infrastructure =
      noClasses()
          .that().resideInAPackage("..domain..")
          .should().dependOnClassesThat().resideInAPackage("..infrastructure..")
          .allowEmptyShould(true);

  @ArchTest
  static final ArchRule domain_does_not_depend_on_presentation =
      noClasses()
          .that().resideInAPackage("..domain..")
          .should().dependOnClassesThat().resideInAPackage("..presentation..")
          .allowEmptyShould(true);
}
