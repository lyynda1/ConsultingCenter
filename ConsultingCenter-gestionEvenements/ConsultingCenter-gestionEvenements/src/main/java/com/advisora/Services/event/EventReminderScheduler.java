package com.advisora.Services.event;

import com.advisora.Model.event.EventBooking;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class EventReminderScheduler {
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final EventBookingService bookingService = new EventBookingService();
    private final EventNotificationService notificationService = new EventNotificationService();

    private volatile boolean started = false;

    public synchronized void start() {
        if (started) {
            return;
        }
        started = true;
        scheduler.scheduleAtFixedRate(this::safeRun, 20, 30, TimeUnit.MINUTES);
        safeRun();
    }

    public synchronized void stop() {
        scheduler.shutdownNow();
        started = false;
    }

    public int processDueRemindersNow() {
        int total = 0;
        total += processReminderWindow(48);
        total += processReminderWindow(24);
        return total;
    }

    private int processReminderWindow(int hoursBefore) {
        List<EventBooking> due = bookingService.findBookingsForReminder(hoursBefore);
        int sent = 0;
        for (EventBooking booking : due) {
            try {
                notificationService.sendEventReminder(booking, hoursBefore);
                bookingService.markReminderSent(booking.getIdBk(), hoursBefore);
                sent++;
            } catch (Exception ex) {
                System.err.println("[EVENT-REMINDER] reminder skipped for booking " + booking.getIdBk() + ": " + ex.getMessage());
            }
        }
        return sent;
    }

    private void safeRun() {
        try {
            int sent = processDueRemindersNow();
            if (sent > 0) {
                System.out.println("[EVENT-REMINDER] reminders sent: " + sent);
            }
        } catch (Exception ex) {
            System.err.println("[EVENT-REMINDER] scheduler run failed: " + ex.getMessage());
        }
    }
}
