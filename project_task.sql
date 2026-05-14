-- Project task module migration
-- Run in database: advisora

CREATE TABLE IF NOT EXISTS `task` (
  `id` INT NOT NULL AUTO_INCREMENT,
  `project_id` INT NOT NULL,
  `title` VARCHAR(255) NOT NULL,
  `status` ENUM('TODO','IN_PROGRESS','DONE') NOT NULL DEFAULT 'TODO',
  `weight` INT NOT NULL DEFAULT 1,
  `duration_days` INT NOT NULL DEFAULT 1,
  `last_warning_date` DATE NULL,
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_task_project` (`project_id`),
  KEY `idx_task_status` (`status`),
  CONSTRAINT `fk_task_project` FOREIGN KEY (`project_id`) REFERENCES `projects` (`idProj`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE `task`
  ADD COLUMN IF NOT EXISTS `duration_days` INT NOT NULL DEFAULT 1 AFTER `weight`,
  ADD COLUMN IF NOT EXISTS `last_warning_date` DATE NULL AFTER `duration_days`;
