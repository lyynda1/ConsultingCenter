package projet;

import com.advisora.Model.projet.Project;
import com.advisora.Model.projet.ProjectBadgeScore;
import com.advisora.Model.projet.Task;
import com.advisora.Services.projet.ProjectBadgeService;
import com.advisora.enums.ProjectStatus;
import com.advisora.enums.TaskStatus;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class test_project_badge {
    private static final ZoneId ZONE = ZoneId.of("UTC");
    private static final Instant FIXED_NOW = Instant.parse("2026-02-21T12:00:00Z");

    @Test
    void temporal_score_aligned_advance_and_late_cases() {
        ProjectBadgeService service = new ProjectBadgeService(Clock.fixed(FIXED_NOW, ZONE));

        Project aligned = baseProject(1, LocalDateTime.of(2026, 2, 11, 10, 0), 11.0, ProjectStatus.ACCEPTED);
        Project advance = baseProject(2, LocalDateTime.of(2026, 2, 11, 10, 0), 85.0, ProjectStatus.ACCEPTED);
        Project late = baseProject(3, LocalDateTime.of(2026, 2, 11, 10, 0), 5.0, ProjectStatus.ACCEPTED);

        double alignedScore = service.compute(aligned, List.of(), List.of(), false).getTemporalScore();
        double advanceScore = service.compute(advance, List.of(), List.of(), false).getTemporalScore();
        double lateScore = service.compute(late, List.of(), List.of(), false).getTemporalScore();

        assertTrue(alignedScore >= 95.0 && alignedScore <= 100.0);
        assertEquals(100.0, advanceScore, 0.0001);
        assertTrue(lateScore < alignedScore);
    }

    @Test
    void reliability_score_is_zero_with_refusal_history() {
        ProjectBadgeService service = new ProjectBadgeService(Clock.fixed(FIXED_NOW, ZONE));
        Project project = baseProject(10, LocalDateTime.of(2026, 2, 1, 10, 0), 60.0, ProjectStatus.ACCEPTED);

        ProjectBadgeScore withoutRefusal = service.compute(project, List.of(), List.of(), false);
        ProjectBadgeScore withRefusal = service.compute(project, List.of(), List.of(), true);

        assertEquals(100.0, withoutRefusal.getReliabilityScore(), 0.0001);
        assertEquals(0.0, withRefusal.getReliabilityScore(), 0.0001);
    }

    @Test
    void regularity_prefers_distributed_weekly_activity() {
        ProjectBadgeService service = new ProjectBadgeService(Clock.fixed(FIXED_NOW, ZONE));
        Project project = baseProject(20, LocalDateTime.of(2026, 1, 20, 10, 0), 55.0, ProjectStatus.ACCEPTED);

        List<Task> distributed = List.of(
                task(1, 20, LocalDateTime.of(2026, 1, 21, 9, 0), TaskStatus.DONE, 2),
                task(2, 20, LocalDateTime.of(2026, 1, 28, 9, 0), TaskStatus.DONE, 2),
                task(3, 20, LocalDateTime.of(2026, 2, 4, 9, 0), TaskStatus.TODO, 2),
                task(4, 20, LocalDateTime.of(2026, 2, 11, 9, 0), TaskStatus.IN_PROGRESS, 2)
        );
        List<Task> concentrated = List.of(
                task(5, 20, LocalDateTime.of(2026, 2, 10, 9, 0), TaskStatus.DONE, 2),
                task(6, 20, LocalDateTime.of(2026, 2, 10, 10, 0), TaskStatus.TODO, 2),
                task(7, 20, LocalDateTime.of(2026, 2, 10, 11, 0), TaskStatus.IN_PROGRESS, 2),
                task(8, 20, LocalDateTime.of(2026, 2, 10, 12, 0), TaskStatus.TODO, 2)
        );

        double distributedScore = service.compute(project, distributed, List.of(), false).getRegularityScore();
        double concentratedScore = service.compute(project, concentrated, List.of(), false).getRegularityScore();

        assertTrue(distributedScore > concentratedScore);
    }

    @Test
    void stability_penalizes_more_late_fluctuations_and_duration() {
        ProjectBadgeService service = new ProjectBadgeService(Clock.fixed(FIXED_NOW, ZONE));
        Project project = baseProject(30, LocalDateTime.of(2026, 1, 1, 10, 0), 40.0, ProjectStatus.ACCEPTED);

        List<Task> stable = List.of(
                task(10, 30, LocalDateTime.of(2026, 1, 1, 11, 0), TaskStatus.DONE, 100)
        );

        List<Task> unstable = List.of(
                task(11, 30, LocalDateTime.of(2026, 1, 1, 11, 0), TaskStatus.DONE, 100),
                task(12, 30, LocalDateTime.of(2026, 1, 8, 11, 0), TaskStatus.TODO, 300),
                task(13, 30, LocalDateTime.of(2026, 1, 15, 11, 0), TaskStatus.DONE, 900),
                task(14, 30, LocalDateTime.of(2026, 1, 22, 11, 0), TaskStatus.TODO, 3000)
        );

        double stableScore = service.compute(project, stable, List.of(), false).getStabilityScore();
        double unstableScore = service.compute(project, unstable, List.of(), false).getStabilityScore();

        assertTrue(stableScore > unstableScore);
    }

    @Test
    void badge_mapping_respects_thresholds() throws Exception {
        ProjectBadgeService service = new ProjectBadgeService(Clock.fixed(FIXED_NOW, ZONE));
        Method badgeFor = ProjectBadgeService.class.getDeclaredMethod("badgeFor", double.class, boolean.class);
        badgeFor.setAccessible(true);

        assertEquals("BRONZE", badgeFor.invoke(service, 39.9, false));
        assertEquals("ARGENT", badgeFor.invoke(service, 40.0, false));
        assertEquals("OR", badgeFor.invoke(service, 65.0, false));
        assertEquals("PLATINE", badgeFor.invoke(service, 85.0, false));
    }

    @Test
    void platinum_lock_applies_when_refusal_history_exists() throws Exception {
        ProjectBadgeService service = new ProjectBadgeService(Clock.fixed(FIXED_NOW, ZONE));
        Method badgeFor = ProjectBadgeService.class.getDeclaredMethod("badgeFor", double.class, boolean.class);
        badgeFor.setAccessible(true);

        assertEquals("OR", badgeFor.invoke(service, 95.0, true));
        assertEquals("PLATINE", badgeFor.invoke(service, 95.0, false));
    }

    @Test
    void empty_data_uses_prudent_minimal_behavior() {
        ProjectBadgeService service = new ProjectBadgeService(Clock.fixed(FIXED_NOW, ZONE));
        Project project = baseProject(40, LocalDateTime.of(2026, 2, 1, 10, 0), 20.0, ProjectStatus.ACCEPTED);

        ProjectBadgeScore score = service.compute(project, List.of(), List.of(), false);

        assertEquals(0.0, score.getRegularityScore(), 0.0001);
        assertEquals(50.0, score.getStabilityScore(), 0.0001);
        assertEquals(100.0, score.getReliabilityScore(), 0.0001);
    }

    private static Project baseProject(int id, LocalDateTime createdAt, double avancement, ProjectStatus status) {
        Project p = new Project();
        p.setIdProj(id);
        p.setCreatedAtProj(Timestamp.valueOf(createdAt));
        p.setAvancementProj(avancement);
        p.setStateProj(status);
        p.setTitleProj("P" + id);
        p.setIdClient(1);
        return p;
    }

    private static Task task(int id, int projectId, LocalDateTime createdAt, TaskStatus status, int weight) {
        Task t = new Task();
        t.setId(id);
        t.setProjectId(projectId);
        t.setCreatedAt(Timestamp.valueOf(createdAt));
        t.setStatus(status);
        t.setWeight(weight);
        return t;
    }
}
