-- phpMyAdmin SQL Dump
-- version 5.2.1
-- https://www.phpmyadmin.net/
--
-- Hôte : 127.0.0.1
-- Généré le : jeu. 19 fév. 2026 à 23:31
-- Version du serveur : 10.4.32-MariaDB
-- Version de PHP : 8.2.12

SET SQL_MODE = "NO_AUTO_VALUE_ON_ZERO";
START TRANSACTION;
SET time_zone = "+00:00";


/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!40101 SET NAMES utf8mb4 */;

--
-- Base de données : `advisora`
--

-- --------------------------------------------------------

--
-- Structure de la table `strategies`
--

DROP TABLE IF EXISTS `strategies`;
CREATE TABLE `strategies` (
  `idStrategie` int(11) NOT NULL,
  `versions` int(11) NOT NULL DEFAULT 1,
  `statusStrategie` enum('En_cours','Acceptée','Refusée') NOT NULL DEFAULT 'En_cours',
  `CreatedAtS` datetime NOT NULL DEFAULT current_timestamp(),
  `lockedAt` datetime DEFAULT NULL,
  `news` varchar(255) DEFAULT NULL,
  `idProj` int(11) DEFAULT NULL,
  `idUser` int(11) DEFAULT NULL,
  `nomStrategie` varchar(50) NOT NULL,
  `justification` varchar(500) DEFAULT NULL,
  `type` enum('MARKETING','FINANCIERE','OPERATIONNELLE','DIGITALE','RH','CROISSANCE','COMMERCIALE','JURIDIQUE') DEFAULT NULL,
  `budgetTotal` double DEFAULT NULL,
  `gainEstime` double DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

--
-- Déchargement des données de la table `strategies`
--

INSERT INTO `strategies` (`idStrategie`, `versions`, `statusStrategie`, `CreatedAtS`, `lockedAt`, `news`, `idProj`, `idUser`, `nomStrategie`, `justification`, `type`, `budgetTotal`, `gainEstime`) VALUES
(15, 1, 'En_cours', '2026-02-14 17:36:08', NULL, NULL, 3, 2, 'Stratégie de Domination par les Coûts', NULL, 'MARKETING', NULL, NULL),
(18, 1, 'Acceptée', '2026-02-14 21:02:12', NULL, NULL, 3, 2, 'Stratégie de Diversification', NULL, 'MARKETING', NULL, NULL),
(19, 2, 'Acceptée', '2026-02-14 21:11:55', NULL, NULL, 8, 2, '2', NULL, 'MARKETING', NULL, NULL),
(20, 1, 'En_cours', '2026-02-15 15:49:48', NULL, NULL, 3, 2, 'aaaaaaaa', NULL, 'MARKETING', NULL, NULL),
(21, 1, 'Acceptée', '2026-02-19 09:11:25', NULL, NULL, 3, 2, 'desxription de l\'option hhh', NULL, 'MARKETING', NULL, NULL),
(22, 1, 'En_cours', '2026-02-19 20:54:23', NULL, NULL, 8, 2, 'mamacita', NULL, 'FINANCIERE', 500, 1000);

--
-- Index pour les tables déchargées
--

--
-- Index pour la table `strategies`
--
ALTER TABLE `strategies`
  ADD PRIMARY KEY (`idStrategie`),
  ADD KEY `fk_strategies_project` (`idProj`),
  ADD KEY `fk_strategies_user` (`idUser`);

--
-- AUTO_INCREMENT pour les tables déchargées
--

--
-- AUTO_INCREMENT pour la table `strategies`
--
ALTER TABLE `strategies`
  MODIFY `idStrategie` int(11) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=23;

--
-- Contraintes pour les tables déchargées
--

--
-- Contraintes pour la table `strategies`
--
ALTER TABLE `strategies`
  ADD CONSTRAINT `fk_strategies_project` FOREIGN KEY (`idProj`) REFERENCES `projects` (`idProj`) ON DELETE CASCADE,
  ADD CONSTRAINT `fk_strategies_user` FOREIGN KEY (`idUser`) REFERENCES `user` (`idUser`) ON DELETE SET NULL;
COMMIT;

/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
