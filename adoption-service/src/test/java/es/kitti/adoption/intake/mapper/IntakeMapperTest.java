package es.kitti.adoption.intake.mapper;

import es.kitti.adoption.intake.dto.IntakeApproveRequest;
import es.kitti.adoption.intake.entity.IntakeRequest;
import es.kitti.adoption.intake.entity.IntakeStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class IntakeMapperTest {

    private final IntakeMapper mapper = new IntakeMapper();
    private IntakeRequest intake;

    @BeforeEach
    void setUp() {
        intake = new IntakeRequest();
        intake.id = 1L;
        intake.userId = 100L;
        intake.targetOrganizationId = 200L;
        intake.catName = "Mishi";
        intake.catAge = 3;
        intake.region = "Santa Cruz de Tenerife";
        intake.city = "La Orotava";
        intake.vaccinated = true;
        intake.description = "shy but friendly";
        intake.status = IntakeStatus.Pending;
        intake.createdAt = LocalDateTime.now();
    }

    @Test
    void toCatCreateInternal_allOverridesNull_usesIntakeValues() {
        var approve = new IntakeApproveRequest("Female", "ES", null, null, null, null, null, null);

        var result = mapper.toCatCreateInternal(intake, approve);

        assertEquals("Mishi", result.name());
        assertEquals(3, result.age());
        assertEquals("Female", result.sex());
        assertEquals("shy but friendly", result.description());
        assertEquals("La Orotava", result.city());
        assertEquals("Santa Cruz de Tenerife", result.region());
        assertEquals("ES", result.country());
        assertEquals(200L, result.organizationId());
        assertNull(result.neutered());
        assertNull(result.latitude());
        assertNull(result.longitude());
    }

    @Test
    void toCatCreateInternal_overridesPresent_overrideIntakeValues() {
        var approve = new IntakeApproveRequest(
                "Male", "PT",
                "Pelusa", 5,
                "Lisboa", "Lisboa",
                "renamed description",
                true);

        var result = mapper.toCatCreateInternal(intake, approve);

        assertEquals("Pelusa", result.name());
        assertEquals(5, result.age());
        assertEquals("Male", result.sex());
        assertEquals("renamed description", result.description());
        assertEquals("Lisboa", result.city());
        assertEquals("Lisboa", result.region());
        assertEquals("PT", result.country());
        assertEquals(Boolean.TRUE, result.neutered());
        assertEquals(200L, result.organizationId(), "organizationId must come from intake, not from approve body");
    }

    @Test
    void toCatCreateInternal_organizationIdAlwaysFromIntake() {
        var approve = new IntakeApproveRequest("Female", "ES", null, null, null, null, null, null);
        intake.targetOrganizationId = 999L;

        var result = mapper.toCatCreateInternal(intake, approve);

        assertEquals(999L, result.organizationId());
    }
}
