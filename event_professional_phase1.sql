-- Professional Event Management - Phase 1 migration
-- Execute once on the advisora database before launching the upgraded event module.

ALTER TABLE events
    ADD COLUMN ticketPrice DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    ADD COLUMN currencyCode VARCHAR(8) NOT NULL DEFAULT 'TND',
    ADD COLUMN minReservationThreshold DECIMAL(5,2) NULL,
    ADD COLUMN thresholdDeadline DATETIME NULL,
    ADD COLUMN statusEv VARCHAR(32) NOT NULL DEFAULT 'PUBLISHED',
    ADD COLUMN categoryEv VARCHAR(64) NULL,
    ADD COLUMN imageUrlEv VARCHAR(500) NULL;

ALTER TABLE bookings
    ADD COLUMN bookingStatus VARCHAR(32) NOT NULL DEFAULT 'CONFIRMED',
    ADD COLUMN paymentReference VARCHAR(255) NULL,
    ADD COLUMN refundAmountBk DECIMAL(10,2) NULL,
    ADD COLUMN refundDateBk DATETIME NULL,
    ADD COLUMN cancelReasonBk VARCHAR(255) NULL,
    ADD COLUMN notificationSentBk TINYINT(1) NOT NULL DEFAULT 0;

UPDATE events
SET statusEv = 'PUBLISHED'
WHERE statusEv IS NULL OR statusEv = '';

UPDATE bookings
SET bookingStatus = 'CONFIRMED'
WHERE bookingStatus IS NULL OR bookingStatus = '';

CREATE INDEX idx_events_status_threshold ON events (statusEv, thresholdDeadline);
CREATE INDEX idx_bookings_status_event ON bookings (idEv, bookingStatus);
CREATE INDEX idx_bookings_payment_reference ON bookings (paymentReference);
