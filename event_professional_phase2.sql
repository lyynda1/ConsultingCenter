-- Professional Event Management - Phase 2 migration
-- Execute once on advisora database after phase 1.

ALTER TABLE bookings
    ADD COLUMN reminder24SentBk TINYINT(1) NOT NULL DEFAULT 0,
    ADD COLUMN reminder48SentBk TINYINT(1) NOT NULL DEFAULT 0;

CREATE INDEX idx_bookings_reminders ON bookings (bookingStatus, reminder48SentBk, reminder24SentBk);
