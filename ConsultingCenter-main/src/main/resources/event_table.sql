-- SQL script to create the event table
-- Run this in your MySQL database (advisora)

CREATE TABLE IF NOT EXISTS event (
    id INT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    startDate DATETIME NOT NULL,
    endDate DATETIME NOT NULL,
    capacite INT NOT NULL,
    localisation VARCHAR(255) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- Optional: Add some sample data for testing
INSERT INTO event (title, description, startDate, endDate, capacite, localisation) VALUES
('Investment Workshop', 'Learn about investment strategies', '2026-03-15 10:00:00', '2026-03-15 17:00:00', 50, 'Conference Room A'),
('Business Planning Seminar', 'Strategic planning for startups', '2026-03-20 09:00:00', '2026-03-20 16:00:00', 30, 'Main Hall'),
('Networking Event', 'Connect with industry professionals', '2026-04-01 18:00:00', '2026-04-01 21:00:00', 100, 'Downtown Center');
