package projet;

import com.advisora.Model.Project;
import com.advisora.Services.ProjectService;
import com.advisora.enums.ProjectStatus;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class test_projet {

    // Verify default constructor business rule:
    // every new Project starts with PENDING state.
    @Test
    void project_default_state_is_pending() {
        Project p = new Project();
        assertEquals(ProjectStatus.PENDING, p.getStateProj());
    }

    // Verify enum mapping robustness:
    // - accepts lowercase DB value
    // - falls back to PENDING for unknown/null values
    @Test
    void test_project_status_enum_mapping() {
        assertEquals(ProjectStatus.PENDING, ProjectStatus.fromDb("pending"));       
        assertEquals(ProjectStatus.ACCEPTED, ProjectStatus.fromDb("accepted"));
        assertEquals(ProjectStatus.REFUSED, ProjectStatus.fromDb("REFUSED"));
        assertEquals(ProjectStatus.PENDING, ProjectStatus.fromDb("unknown"));
        assertEquals(ProjectStatus.PENDING, ProjectStatus.fromDb(null));
    }
    // Validation guard: null project must be rejected.
    @Test
    void validate_create_rejects_null_project() {
        assertThrows(IllegalArgumentException.class, () -> invokeValidate(null, true));
    }

    // Validation guard: blank title is not allowed.
    @Test
    void validate_create_rejects_blank_title() {
        Project p = validProject();
        p.setTitleProj("   ");
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> invokeValidate(p, true));
        assertEquals("Title required", ex.getMessage());
    }

    // Validation guard: budget cannot be negative.
    @Test
    void validate_create_rejects_negative_budget() {
        Project p = validProject();
        p.setBudgetProj(-1);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> invokeValidate(p, true));
        assertEquals("Budget >= 0", ex.getMessage());
    }

    // Validation guard: progress must stay between 0 and 100.
    @Test
    void validate_create_rejects_invalid_avancement() {
        Project p = validProject();
        p.setAvancementProj(120);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> invokeValidate(p, true));
        assertEquals("Avancement between 0 and 100", ex.getMessage());
    }

    // Validation guard: client id is mandatory and must be > 0.
    @Test
    void validate_create_rejects_invalid_client_id() {
        Project p = validProject();
        p.setIdClient(0);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> invokeValidate(p, true));
        assertEquals("idClient invalide", ex.getMessage());
    }

    // Validation guard for update mode:
    // existing project id must be present (> 0).
    @Test
    void validate_update_requires_project_id() {
        Project p = validProject();
        p.setIdProj(0);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> invokeValidate(p, false));
        assertEquals("idProj invalide", ex.getMessage());
    }

    // Happy path:
    // a correct project must pass validation in both create and update mode.
    @Test
    void validate_accepts_valid_create_and_update() throws Exception {
        Project p = validProject();
        invokeValidate(p, true);
        p.setIdProj(15);
        invokeValidate(p, false);
    }

    // Boundary checks:
    // avancement exactly 0 and 100 must be accepted.
    @Test
    void validate_accepts_avancement_boundaries() throws Exception {
        Project p = validProject();
        p.setAvancementProj(0);
        invokeValidate(p, true);

        p.setAvancementProj(100);
        invokeValidate(p, true);
    }

    // Enum mapping should accept whitespace and mixed case.
    @Test
    void project_status_mapping_accepts_whitespace_and_case() {
        assertEquals(ProjectStatus.ACCEPTED, ProjectStatus.fromDb(" accepted "));
        assertEquals(ProjectStatus.ARCHIVED, ProjectStatus.fromDb("ArChIvEd"));
    }

    // Test fixture factory:
    // returns a project object that satisfies service validation rules.
    private static Project validProject() {
        Project p = new Project();
        p.setTitleProj("Migration ERP");
        p.setDescriptionProj("Projet test");
        p.setBudgetProj(1000.0);
        p.setTypeProj("IT");
        p.setAvancementProj(25);
        p.setIdClient(2);
        return p;
    }

    // Reflection helper:
    // calls private method ProjectService.validate(Project, boolean)
    // so we can unit-test validation without hitting the database.
    private static void invokeValidate(Project p, boolean create) throws Exception {
        ProjectService service = new ProjectService();
        Method validate = ProjectService.class.getDeclaredMethod("validate", Project.class, boolean.class);
        validate.setAccessible(true);
        try {
            validate.invoke(service, p, create);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof Exception ex) {
                throw ex;
            }
            throw e;
        }
    }

}
