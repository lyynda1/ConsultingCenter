package com.advisora.Services.projet;

import com.advisora.Model.projet.Task;
import com.advisora.enums.TaskStatus;
import org.junit.jupiter.api.Test;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaskServiceTest {
    private static final ZoneId ZONE = ZoneId.of("UTC");
    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-02-25T10:00:00Z"), ZONE);

    @Test
    void computes_last_quarter_start_and_due_date() {
        LocalDate created = LocalDate.of(2026, 2, 1);
        assertEquals(LocalDate.of(2026, 2, 7), TaskService.computeLastQuarterStart(created, 8));
        assertEquals(LocalDate.of(2026, 2, 9), TaskService.computeDueDate(created, 8));
    }

    @Test
    void near_finish_is_true_for_non_done_and_not_notified_today() {
        TaskService service = new TaskService(FIXED_CLOCK);
        Task t = new Task();
        t.setStatus(TaskStatus.IN_PROGRESS);
        t.setDurationDays(8);
        t.setCreatedAt(Timestamp.valueOf("2026-02-16 10:00:00"));
        t.setLastWarningDate(Date.valueOf(LocalDate.of(2026, 2, 24)));

        boolean should = service.shouldNotifyNearFinish(LocalDate.of(2026, 2, 25), t);
        assertTrue(should);
    }

    @Test
    void near_finish_is_false_when_done_or_already_notified_today() {
        TaskService service = new TaskService(FIXED_CLOCK);

        Task done = new Task();
        done.setStatus(TaskStatus.DONE);
        done.setDurationDays(8);
        done.setCreatedAt(Timestamp.valueOf("2026-02-16 10:00:00"));
        assertFalse(service.shouldNotifyNearFinish(LocalDate.of(2026, 2, 25), done));

        Task alreadyNotified = new Task();
        alreadyNotified.setStatus(TaskStatus.TODO);
        alreadyNotified.setDurationDays(8);
        alreadyNotified.setCreatedAt(Timestamp.valueOf("2026-02-16 10:00:00"));
        alreadyNotified.setLastWarningDate(Date.valueOf(LocalDate.of(2026, 2, 25)));
        assertFalse(service.shouldNotifyNearFinish(LocalDate.of(2026, 2, 25), alreadyNotified));
    }
}
