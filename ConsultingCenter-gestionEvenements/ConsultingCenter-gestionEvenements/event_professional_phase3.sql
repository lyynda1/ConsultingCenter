-- Professional Event Management - Phase 3 migration
-- Execute once on advisora database after phases 1 and 2.

ALTER TABLE bookings
    ADD COLUMN qrTokenBk VARCHAR(512) NULL,
    ADD COLUMN qrImagePathBk VARCHAR(500) NULL;

CREATE UNIQUE INDEX idx_bookings_qr_token ON bookings (qrTokenBk);
