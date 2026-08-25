package co.sendik;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

/**
 * Segunda linea de defensa de la arquitectura.
 *
 * <p>La primera es el grafo de Gradle: {@code domain} no declara Spring, asi que
 * no puede importarlo. Estas pruebas cubren lo que el grafo no alcanza a ver, y
 * fallan tambien si alguien agrega la dependencia al build para saltarse la
 * regla. La tercera linea es el subagente revisor
 * (docs/arquitectura/vision-tecnica.md).
 *
 * <p>Las capas son modulos de Gradle, no paquetes, asi que cada regla se aplica
 * sobre las clases de un modulo concreto filtrando por su carpeta de salida.
 *
 * <p>Sobre {@code allowEmptyShould}: en Fase 1 varios modulos todavia estan
 * vacios y una regla sin clases que evaluar fallaria por eso, no por una
 * violacion. Las reglas ya estan escritas para que el dia que entre la primera
 * clase de HU-001 ya haya alguien vigilando.
 */
class ArchitectureTest {

    private static final String[] FRAMEWORKS_PROHIBIDOS_EN_EL_DOMINIO = {
        "org.springframework..", "jakarta..", "java.sql..", "org.flywaydb..", "org.testcontainers..", "lombok.."
    };

    @Test
    void el_dominio_no_depende_de_ningun_framework() {
        ArchRule regla = noClasses()
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(FRAMEWORKS_PROHIBIDOS_EN_EL_DOMINIO)
                .because("si manana cambia la base de datos, el marco web o la pasarela,"
                        + " el dominio no se toca. Ver docs/arquitectura/vision-tecnica.md")
                .allowEmptyShould(true);

        regla.check(clasesDelModulo("domain"));
    }

    @Test
    void la_capa_de_aplicacion_solo_usa_las_transacciones_de_spring() {
        ArchRule regla = noClasses()
                .should()
                .dependOnClassesThat(resideInAPackage("org.springframework..")
                        .and(not(resideInAPackage("org.springframework.transaction.."))))
                .because("application orquesta casos de uso y abre transacciones."
                        + " Todo lo demas de Spring pertenece a infrastructure o presentation")
                .allowEmptyShould(true);

        regla.check(clasesDelModulo("application"));
    }

    @Test
    void la_presentacion_no_conoce_la_persistencia() {
        ArchRule regla = noClasses()
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("co.sendik..persistence..", "co.sendik..client..")
                .because("un controlador delega en un caso de uso; si llega a la base de datos,"
                        + " la logica quedo en el borde")
                .allowEmptyShould(true);

        regla.check(clasesDelModulo("presentation"));
    }

    @Test
    void la_infraestructura_no_conoce_los_controladores() {
        ArchRule regla = noClasses()
                .should()
                .dependOnClassesThat()
                .resideInAPackage("co.sendik..rest..")
                .because("las dependencias apuntan hacia adentro: un adaptador nunca depende del borde HTTP")
                .allowEmptyShould(true);

        regla.check(clasesDelModulo("infrastructure"));
    }

    @Test
    void un_controlador_no_habla_directamente_con_un_repositorio() {
        ArchRule regla = noClasses()
                .that()
                .haveSimpleNameEndingWith("Controller")
                .should()
                .dependOnClassesThat()
                .haveSimpleNameEndingWith("Repository")
                .because("entre el controlador y el repositorio va el caso de uso,"
                        + " que es quien abre la transaccion")
                .allowEmptyShould(true);

        regla.check(todasLasClases());
    }

    @Test
    void un_caso_de_uso_no_toca_la_persistencia() {
        ArchRule regla = noClasses()
                .that()
                .resideInAPackage("co.sendik..usecase..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("co.sendik..persistence..")
                .because("un caso de uso habla con un puerto que el mismo define, nunca con"
                        + " el adaptador que lo implementa")
                .allowEmptyShould(true);

        regla.check(todasLasClases());
    }

    @Test
    void un_caso_de_uso_se_llama_caso_de_uso() {
        ArchRule regla = classes()
                .that()
                .resideInAPackage("co.sendik..usecase..")
                .and()
                .areTopLevelClasses()
                .should()
                .haveSimpleNameEndingWith("UseCase")
                .because("un caso de uso por clase, con un unico metodo publico (backend/CLAUDE.md)")
                .allowEmptyShould(true);

        regla.check(todasLasClases());
    }

    @Test
    void no_se_inyectan_dependencias_por_campo() {
        ArchRule regla = noFields()
                .should()
                .beAnnotatedWith("org.springframework.beans.factory.annotation.Autowired")
                .because("la inyeccion va por constructor y sin anotacion: deja las dependencias"
                        + " visibles y la clase construible en una prueba")
                .allowEmptyShould(true);

        regla.check(todasLasClasesIncluidasLasPruebas());
    }

    @Test
    void no_se_usa_jackson_2_sino_jackson_3() {
        ArchRule regla = noClasses()
                .should()
                .dependOnClassesThat()
                .resideInAPackage("com.fasterxml.jackson..")
                .because("Spring Boot 4 trae Jackson 3, cuyo paquete es tools.jackson."
                        + " com.fasterxml es la version anterior y arrastra otro ObjectMapper")
                .allowEmptyShould(true);

        regla.check(todasLasClasesIncluidasLasPruebas());
    }

    @Test
    void no_se_usa_resttemplate_sino_restclient() {
        ArchRule regla = noClasses()
                .should()
                .dependOnClassesThat()
                .haveFullyQualifiedName("org.springframework.web.client.RestTemplate")
                .because("los clientes HTTP se escriben con RestClient o con una interfaz @HttpExchange")
                .allowEmptyShould(true);

        regla.check(todasLasClasesIncluidasLasPruebas());
    }

    @Test
    void no_se_usan_las_anotaciones_de_simulacion_retiradas() {
        ArchRule regla = noFields()
                .should()
                .beAnnotatedWith("org.springframework.boot.test.mock.mockito.MockBean")
                .orShould()
                .beAnnotatedWith("org.springframework.boot.test.mock.mockito.SpyBean")
                .because("en Spring Boot 4 se llaman @MockitoBean y @MockitoSpyBean")
                .allowEmptyShould(true);

        regla.check(todasLasClasesIncluidasLasPruebas());
    }

    /**
     * Los dos paquetes del dominio, no solo {@code model}.
     *
     * <p>La regla nombraba unicamente {@code ..model..} y eso la dejaba ciega ante
     * cualquier tipo de dominio que viva en otro paquete. Al agregar
     * {@code co.sendik.shared.file} —los objetos de valor de archivos e imagenes,
     * ADR-0018— un DTO de la API podia exponer una {@code FileKey} entera, es decir
     * la ruta interna del archivo dentro del almacen, y la regla no habria dicho
     * nada. Enumerar los
     * paquetes tiene ese costo: cada paquete nuevo del dominio hay que agregarlo
     * aqui, y el dia que se olvide la regla protege menos de lo que parece.
     */
    @Test
    void un_objeto_de_dominio_no_se_filtra_hacia_la_api() {
        ArchRule regla = noClasses()
                .that()
                .resideInAPackage("co.sendik..rest.dto..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("co.sendik..model..", "co.sendik.shared.file..")
                .because("la API tiene sus propios DTO aunque al principio parezcan identicos:"
                        + " asi el dominio puede cambiar sin romper el contrato publico")
                .allowEmptyShould(true);

        regla.check(todasLasClases());
    }

    /** Solo las clases de produccion del modulo indicado, filtradas por su carpeta de salida. */
    private static JavaClasses clasesDelModulo(String modulo) {
        return new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .withImportOption(location -> location.contains("/" + modulo + "/build/"))
                .importPackages("co.sendik");
    }

    private static JavaClasses todasLasClases() {
        return new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("co.sendik");
    }

    private static JavaClasses todasLasClasesIncluidasLasPruebas() {
        return new ClassFileImporter().importPackages("co.sendik");
    }
}
