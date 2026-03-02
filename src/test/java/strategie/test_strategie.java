package strategie;

import com.advisora.Model.projet.Project;
import com.advisora.Model.strategie.Strategie;
import com.advisora.Services.strategie.ServiceStrategie;
import com.advisora.enums.StrategyStatut;
import com.advisora.enums.TypeStrategie;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class test_strategie {

    // Verify default constructor business rule:
    // a new strategy starts with version=1 and status=EN_COURS.
    @Test
    void strategie_default_values_are_initialized() {
        Strategie s = new Strategie();
        assertEquals(1, s.getVersion());
        assertEquals(StrategyStatut.EN_COURS, s.getStatut());
    }

    // Verify DB value mapping robustness for strategy status.
    @Test
    void strategy_status_enum_mapping_is_robust() {
        assertEquals(StrategyStatut.EN_COURS, StrategyStatut.fromDb("En_cours"));
        assertEquals(StrategyStatut.ACCEPTEE, StrategyStatut.fromDb("Acceptee"));
        assertEquals(StrategyStatut.ACCEPTEE, StrategyStatut.fromDb("ACCEPTEE"));
        assertEquals(StrategyStatut.REFUSEE, StrategyStatut.fromDb("Refusee"));
        assertEquals(StrategyStatut.EN_COURS, StrategyStatut.fromDb("unknown"));
        assertEquals(StrategyStatut.EN_COURS, StrategyStatut.fromDb(null));
    }

    // Mapping should also tolerate spaces and separators.
    @Test
    void strategy_status_mapping_handles_spaces_and_dash() {
        assertEquals(StrategyStatut.EN_COURS, StrategyStatut.fromDb("  en-cours  "));
        assertEquals(StrategyStatut.ACCEPTEE, StrategyStatut.fromDb(" acceptee "));
        assertEquals(StrategyStatut.REFUSEE, StrategyStatut.fromDb(" REFUSEE "));
    }

    // Validation guard: null strategy must be rejected.
    @Test
    void validate_rejects_null_strategie() {
        assertThrows(IllegalArgumentException.class, () -> invokeValidate(null, true));
    }

    // Validation guard: strategy name is mandatory.
    @Test
    void validate_rejects_blank_name() {
        Strategie s = validStrategie();
        s.setNomStrategie("   ");
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> invokeValidate(s, true));
        assertEquals("Nom strategie obligatoire.", ex.getMessage());
    }

    // Validation guard: project association is mandatory.
    @Test
    void validate_rejects_missing_project() {
        Strategie s = validStrategie();
        s.setProjet(null);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> invokeValidate(s, true));
        assertEquals("Projet obligatoire.", ex.getMessage());
    }

    // Validation guard: project id must be valid (>0).
    @Test
    void validate_rejects_invalid_project_id() {
        Strategie s = validStrategie();
        Project p = new Project();
        p.setIdProj(0);
        s.setProjet(p);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> invokeValidate(s, true));
        assertEquals("Projet obligatoire.", ex.getMessage());
    }

    // Validation guard in update mode: strategy id is required.
    @Test
    void validate_update_requires_id() {
        Strategie s = validStrategie();
        s.setId(0);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> invokeValidate(s, false));
        assertEquals("idStrategie invalide.", ex.getMessage());
    }

    // Defaulting behavior:
    // if createdAt is missing, service sets a safe default timestamp.
    @Test
    void validate_sets_createdAt_default_when_missing() throws Exception {
        Strategie s = validStrategie();
        s.setCreatedAt(null);

        invokeValidate(s, true);

        assertNotNull(s.getCreatedAt());
    }

    // Validation guard:
    // null status must be rejected by current validator rules.
    @Test
    void validate_rejects_null_status() {
        Strategie s = validStrategie();
        s.setStatut(null);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> invokeValidate(s, true));
        assertEquals("Statut strategie obligatoire.", ex.getMessage());
    }

    // Happy path:
    // a valid strategy passes create and update validation.
    @Test
    void validate_accepts_valid_create_and_update() throws Exception {
        Strategie s = validStrategie();
        invokeValidate(s, true);
        s.setId(7);
        invokeValidate(s, false);
    }

    // Test fixture factory:
    // returns a strategy object that satisfies service validation rules.
    private static Strategie validStrategie() {
        Project p = new Project();
        p.setIdProj(1);
        p.setTitleProj("Projet test");

        Strategie s = new Strategie();
        s.setNomStrategie("Strategie croissance");
        s.setVersion(1);
        s.setStatut(StrategyStatut.EN_COURS);
        s.setTypeStrategie(TypeStrategie.AUTRE);
        s.setDureeTerme(180);
        s.setProjet(p);
        s.setIdUser(2);
        return s;
    }

    // Reflection helper:
    // calls private method ServiceStrategie.validate(Strategie, boolean)
    // so we can unit-test business rules without DB access.
    private static void invokeValidate(Strategie s, boolean create) throws Exception {
        ServiceStrategie service = new ServiceStrategie();
        Method validate = ServiceStrategie.class.getDeclaredMethod("validate", Strategie.class, boolean.class);
        validate.setAccessible(true);
        try {
            validate.invoke(service, s, create);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof Exception ex) {
                throw ex;
            }
            throw e;
        }
    }
}
