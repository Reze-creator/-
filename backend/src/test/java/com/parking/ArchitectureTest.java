package com.parking;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

class ArchitectureTest {
  private static final JavaClasses CLASSES =
      new ClassFileImporter()
          .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
          .importPackages("com.parking");

  @Test
  void controllersDoNotDependOnMappersOrEntities() {
    noClasses()
        .that()
        .resideInAPackage("..controller..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage("..mapper..", "..entity..")
        .check(CLASSES);
  }

  @Test
  void servicesDoNotDependOnControllers() {
    noClasses()
        .that()
        .resideInAPackage("..service..")
        .should()
        .dependOnClassesThat()
        .resideInAPackage("..controller..")
        .check(CLASSES);
  }

  @Test
  void serviceImplementationsDoNotDependOnEachOther() {
    noClasses()
        .that()
        .haveSimpleNameEndingWith("ServiceImpl")
        .should()
        .dependOnClassesThat()
        .haveSimpleNameEndingWith("ServiceImpl")
        .check(CLASSES);
  }
}
