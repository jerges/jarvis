package karate;

import com.intuit.karate.junit5.Karate;
import org.junit.jupiter.api.Tag;

class KarateRunner {

    /** Ejecuta todos los tests (API + UI). */
    @Karate.Test
    Karate all() {
        return Karate.run().relativeTo(getClass());
    }

    /** Solo tests de API. */
    @Karate.Test
    @Tag("api")
    Karate api() {
        return Karate.run("classpath:api").relativeTo(getClass());
    }

    /** Solo tests de UI (requiere Chrome + app arriba). */
    @Karate.Test
    @Tag("ui")
    Karate ui() {
        return Karate.run("classpath:web").relativeTo(getClass());
    }
}
