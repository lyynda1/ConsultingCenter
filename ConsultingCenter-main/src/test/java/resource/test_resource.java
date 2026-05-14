package resource;

import com.advisora.Model.ressource.Ressource;
import com.advisora.Services.ressource.RessourceService;
import com.advisora.enums.RessourceStatut;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class test_resource {

    @Test
    void status_from_db_mapping_is_correct() {
        assertEquals(RessourceStatut.AVAILABLE, RessourceStatut.fromDb("available"));
        assertEquals(RessourceStatut.RESERVED, RessourceStatut.fromDb("reserve"));
        assertEquals(RessourceStatut.UNAVAILABLE, RessourceStatut.fromDb("indisponible"));
        assertEquals(RessourceStatut.UNAVAILABLE, RessourceStatut.fromDb("unknown"));
        assertEquals(RessourceStatut.UNAVAILABLE, RessourceStatut.fromDb(null));
    }

    @Test
    void status_from_db_mapping_handles_spaces_and_case() {
        assertEquals(RessourceStatut.AVAILABLE, RessourceStatut.fromDb("  DISPONIBLE  "));
        assertEquals(RessourceStatut.RESERVED, RessourceStatut.fromDb("ReSeRvEd"));
        assertEquals(RessourceStatut.UNAVAILABLE, RessourceStatut.fromDb("  unavailable "));
    }

    @Test
    void validate_rejects_null_resource() {
        assertThrows(IllegalArgumentException.class, () -> invokeValidate(null));
    }

    @Test
    void validate_rejects_blank_name() {
        Ressource r = validResource();
        r.setNomRs("   ");
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> invokeValidate(r));
        assertEquals("Nom ressource obligatoire.", ex.getMessage());
    }

    @Test
    void validate_rejects_negative_price() {
        Ressource r = validResource();
        r.setPrixRs(-0.01);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> invokeValidate(r));
        assertEquals("Prix >= 0 obligatoire.", ex.getMessage());
    }

    @Test
    void validate_rejects_negative_quantity() {
        Ressource r = validResource();
        r.setQuantiteRs(-1);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> invokeValidate(r));
        assertEquals("Quantite >= 0 obligatoire.", ex.getMessage());
    }

    @Test
    void validate_rejects_invalid_supplier_id() {
        Ressource r = validResource();
        r.setIdFr(0);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> invokeValidate(r));
        assertEquals("Fournisseur obligatoire.", ex.getMessage());
    }

    @Test
    void validate_sets_default_status_available_when_quantity_positive() throws Exception {
        Ressource r = validResource();
        r.setAvailabilityStatusRs(null);
        r.setQuantiteRs(2);

        invokeValidate(r);

        assertEquals(RessourceStatut.AVAILABLE, r.getAvailabilityStatusRs());
    }

    @Test
    void validate_sets_default_status_unavailable_when_quantity_zero() throws Exception {
        Ressource r = validResource();
        r.setAvailabilityStatusRs(null);
        r.setQuantiteRs(0);

        invokeValidate(r);

        assertEquals(RessourceStatut.UNAVAILABLE, r.getAvailabilityStatusRs());
    }

    @Test
    void validate_accepts_valid_resource() throws Exception {
        Ressource r = validResource();
        invokeValidate(r);
        assertEquals(RessourceStatut.AVAILABLE, r.getAvailabilityStatusRs());
    }

    private static Ressource validResource() {
        Ressource r = new Ressource();
        r.setNomRs("Laptop Pro");
        r.setPrixRs(2500.0);
        r.setQuantiteRs(10);
        r.setAvailabilityStatusRs(RessourceStatut.AVAILABLE);
        r.setIdFr(1);
        return r;
    }

    private static void invokeValidate(Ressource r) throws Exception {
        RessourceService service = new RessourceService();
        Method validate = RessourceService.class.getDeclaredMethod("validate", Ressource.class);
        validate.setAccessible(true);
        try {
            validate.invoke(service, r);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof Exception ex) {
                throw ex;
            }
            throw e;
        }
    }
}
