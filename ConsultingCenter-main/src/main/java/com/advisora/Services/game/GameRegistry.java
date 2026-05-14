package com.advisora.Services.game;

import com.advisora.GUI.Game.api.PlayableGame;
import com.advisora.GUI.Game.impl.chess.ChessGame;
import com.advisora.GUI.Game.impl.game2048.Game2048;
import com.advisora.GUI.Game.impl.pacman.PacmanGame;
import com.advisora.GUI.Game.impl.snake.SnakeGame;
import com.advisora.GUI.Game.impl.tictactoe.TicTacToeGame;
import com.advisora.Model.game.GameDescriptor;

import java.util.List;

public class GameRegistry {

    public List<GameDescriptor> availableGames() {
        return List.of(
                new GameDescriptor("g2048", "2048", "Fusionnez les tuiles pour atteindre 2048."),
                new GameDescriptor("chess", "Chess", "Jouez en local 2 joueurs ou contre l'ordinateur."),
                new GameDescriptor("pacman", "Pac-Man", "Mangez les points en evitant les fantomes."),
                new GameDescriptor("tictactoe", "Tic-Tac-Toe", "Jeu de grille 3x3 a deux joueurs."),
                new GameDescriptor("snake", "Snake", "Mangez la nourriture sans toucher les murs ou votre corps.")
        );
    }

    public PlayableGame build(String gameId) {
        return switch (gameId) {
            case "g2048" -> new Game2048();
            case "chess" -> new ChessGame();
            case "pacman" -> new PacmanGame();
            case "tictactoe" -> new TicTacToeGame();
            case "snake" -> new SnakeGame();
            default -> throw new IllegalArgumentException("Jeu inconnu: " + gameId);
        };
    }
}

