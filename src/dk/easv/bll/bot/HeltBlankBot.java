package dk.easv.bll.bot;

import dk.easv.bll.field.IField;
import dk.easv.bll.game.GameState;
import dk.easv.bll.game.IGameState;
import dk.easv.bll.move.IMove;

import java.util.List;
import java.util.Objects;
import java.util.Random;

public class HeltBlankBot implements IBot {
    //Time per move (milliseconds)
    final int moveTimeMs = 1000;
    //Name of the bot shown in UI
    private String BOT_NAME = getClass().getSimpleName();

    /*  Creates a simulator based on the current game state.
        The simulator is used for AI calculations so the bot can test
        possible moves without modifying the real game board.
        We copy the board, macroboard and game counters into a new
        GameSimulator instance. */
    private GameSimulator createSimulator(IGameState state) {
        GameSimulator simulator = new GameSimulator(new GameState());
        simulator.setGameOver(GameOverState.Active);
        // Determine which player should move next
        simulator.setCurrentPlayer(state.getMoveNumber() % 2);
        // Copy round and move counters
        simulator.getCurrentState().setRoundNumber(state.getRoundNumber());
        simulator.getCurrentState().setMoveNumber(state.getMoveNumber());
        // Copy board and macroboard state
        simulator.getCurrentState().getField().setBoard(state.getField().getBoard());
        simulator.getCurrentState().getField().setMacroboard(state.getField().getMacroboard());
        return simulator;
    }

    // Main method called by the game engine when the bot must make a move
    @Override
    public IMove doMove(IGameState state) {
        return calculateWinningMove(state, moveTimeMs);
    }

    /*Calculates the best move using a Minimax search.
        The bot:
        1. Gets all available moves
        2. Simulates each move
        3. Evaluates the result using Minimax
        4. Chooses the move with the best score */
    private IMove calculateWinningMove(IGameState state, int maxTimeMs) {
        //List all possible moves that the bot can play this turn
        List<IMove> moves = state.getField().getAvailableMoves();
        //Variable to store the best move found during evaluation
        IMove bestMove = null;
        //Initial value for comparing moves: we want the highest score
        //Integer.MIN_VALUE ensures that any realistic move will be better
        int bestValue = Integer.MIN_VALUE;
        //Test this move in a simulated version of the game
        //Loop through all possible legal moves
        for (IMove move : moves) {
            //Create a copy of the current game state for simulation
            GameSimulator sim = createSimulator(state);
            //Set the current player in the simulator (0 or 1)
            sim.setCurrentPlayer(state.getMoveNumber() % 2);
            //Apply the move in the simulated game state
            sim.updateGame(move);
            //Evaluate the move and keep it if it is better than the previous one
            //Evaluate the simulated move using Minimax algorithm
            int moveValue = minimax(sim, 2, false); // depth 2, opponent plays as minimizer
            //If this move has a better score than the previous best move
            if (moveValue > bestValue) {
                //Update the best score found so far
                bestValue = moveValue;
                //Store this move as the current best move
                bestMove = move;
            }
        }
        // fallback safety check in case no best move was found (Should normally not happen)
        if (bestMove == null) {
            Random rand = new Random();
            bestMove = moves.get(rand.nextInt(moves.size()));
        }
        return bestMove;
    }

        /* Minimax algorithm.
           This recursive function simulates future moves and evaluates them.
           Maximizing player = the bot
           Minimizing player = the opponent
           The algorithm explores possible game states up to a certain depth
           and returns a score representing how good the position is. */
    private int minimax(GameSimulator simulator, int depth, boolean isMaximizingPlayer) {
        //Stop conditions: if the game is finished or we reached maximum search depth
        if (simulator.getGameOver() != GameOverState.Active || depth == 0) {
            //return a heuristic (look, count, return) evaluation of the current board state
            return evaluateBoard(simulator.getCurrentState());
        }
        //Get all possible legal moves from the current board state
        List<IMove> moves = simulator.getCurrentState().getField().getAvailableMoves();
        //Initialize the best value for comparison:
        // - If we are the maximizing ((Minimax)bot) player, start with the smallest possible value
        // - If we are the minimizing player (opponent), start with the largest possible player
        int bestValue = isMaximizingPlayer ? Integer.MIN_VALUE : Integer.MAX_VALUE;

        for (IMove move : moves) {
            GameSimulator child = createSimulator(simulator.getCurrentState());
            child.setCurrentPlayer(simulator.getCurrentPlayer());
            child.updateGame(move);

            int val = minimax(child, depth - 1, !isMaximizingPlayer);
            if (isMaximizingPlayer)
                bestValue = Math.max(bestValue, val);
            else
                bestValue = Math.min(bestValue, val);
        }
        return bestValue;
    }

        /* Heuristic evaluation function.
        The bot evaluates the board by looking at the macroboard.
        +10 points for each microboard controlled by the bot
        -10 points for each microboard controlled by the opponent
        A higher score means a better position for the bot.  */
    private int evaluateBoard(IGameState state) {
        int score = 0;
        String[][] macro = state.getField().getMacroboard();

        for (int i = 0; i < macro.length; i++) {
            for (int j = 0; j < macro[i].length; j++) {
                if (macro[i][j].equals("0")) score += 10; // bot control
                else if (macro[i][j].equals("1")) score -= 10; // opponent control
            }
        }
        return score;
    }

    /* The code below is a simulator for simulation of gameplay. This is needed for AI.
        It is put here to make the Bot independent of the GameManager and its subclasses/enums
        Now this class is only dependent on a few interfaces: IMove, IField, and IGameState
        You could say it is self-contained. The drawback is that if the game rules change, the simulator must be
        changed accordingly, making the code redundant. */

    @Override
    public String getBotName() {
        return BOT_NAME;
    }

    public enum GameOverState {
        Active,
        Win,
        Tie
    }

    public class Move implements IMove {
        int x = 0;
        int y = 0;

        public Move(int x, int y) {
            this.x = x;
            this.y = y;
        }

        @Override
        public int getX() {
            return x;
        }

        @Override
        public int getY() {
            return y;
        }

        @Override
        public String toString() {
            return "(" + x + "," + y + ")";
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Move move = (Move) o;
            return x == move.x && y == move.y;
        }

        @Override
        public int hashCode() {
            return Objects.hash(x, y);
        }
    }

      /*GameSimulator
        A simplified game engine used only for AI simulations.
        It allows the bot to play moves and explore possible futures
        without affecting the real game running in the UI.*/
    class GameSimulator {
        private final IGameState currentState;
        private int currentPlayer = 0; //player0 == 0 && player1 == 1
          // Tracks if the simulated game has ended
        private volatile GameOverState gameOver = GameOverState.Active;

        public void setGameOver(GameOverState state) {
            gameOver = state;
        }

        public GameOverState getGameOver() {
            return gameOver;
        }

        public void setCurrentPlayer(int player) {
            currentPlayer = player;
        }

        public IGameState getCurrentState() {
            return currentState;
        }

        public GameSimulator(IGameState currentState) {
            this.currentState = currentState;
        }

          /*  Applies a move to the simulated board.
              Steps:
              1. Check if the move is legal
              2. Update the board
              3. Switch player turn         */
        public Boolean updateGame(IMove move) {
            if (!verifyMoveLegality(move))
                return false;

            updateBoard(move);
            // Switch player (0 -> 1 or 1 -> 0)
            currentPlayer = (currentPlayer + 1) % 2;

            return true;
        }

          /*  Verifies if a move follows the game rules.
              The move must:
              - Be inside the active microboard
              - Be inside board boundaries
              - Be placed on an empty field    */
        private Boolean verifyMoveLegality(IMove move) {
            IField field = currentState.getField();
            boolean isValid = field.isInActiveMicroboard(move.getX(), move.getY());

            if (isValid && (move.getX() < 0 || 9 <= move.getX())) isValid = false;
            if (isValid && (move.getY() < 0 || 9 <= move.getY())) isValid = false;

            if (isValid && !field.getBoard()[move.getX()][move.getY()].equals(IField.EMPTY_FIELD))
                isValid = false;

            return isValid;
        }

          //Updates the board after a move is played.
        private void updateBoard(IMove move) {
            // Get the current game board
            String[][] board = currentState.getField().getBoard();
            // Place the current player's mark ("0" or "1") at the move position
            board[move.getX()][move.getY()] = currentPlayer + "";
            // Increase the total move counter
            currentState.setMoveNumber(currentState.getMoveNumber() + 1);
            // Every two moves means a full round has been completed
            if (currentState.getMoveNumber() % 2 == 0) {
                currentState.setRoundNumber(currentState.getRoundNumber() + 1);
            }
            // After placing the move we check if someone won a microboard
            checkAndUpdateIfWin(move);
            // Update which microboard will be active for the next player
            updateMacroboard(move);

        }

          // Checks if the last move caused a win or tie
          // and updates both the microboard and macroboard
        private void checkAndUpdateIfWin(IMove move) {
            // Macroboard represents which player has won each 3x3 microboard
            String[][] macroBoard = currentState.getField().getMacroboard();
            // Determine which microboard the move was placed in
            int macroX = move.getX() / 3;
            int macroY = move.getY() / 3;
            // Only check boards that are still playable
            if (macroBoard[macroX][macroY].equals(IField.EMPTY_FIELD) ||
                    macroBoard[macroX][macroY].equals(IField.AVAILABLE_FIELD)) {
                // Get the full board
                String[][] board = getCurrentState().getField().getBoard();
                // Check if the player won the small 3x3 board
                if (isWin(board, move, "" + currentPlayer))
                    macroBoard[macroX][macroY] = currentPlayer + "";
                // Otherwise check if the small board is full (tie)
                else if (isTie(board, move))
                    macroBoard[macroX][macroY] = "TIE";
                //Check macro win
                if (isWin(macroBoard, new Move(macroX, macroY), "" + currentPlayer))
                    gameOver = GameOverState.Win;
                    // If the macroboard is full without a winner it becomes a tie
                else if (isTie(macroBoard, new Move(macroX, macroY)))
                    gameOver = GameOverState.Tie;
            }
        }

          // Checks if a 3x3 board is full without a winner
          // If every cell is filled the board is considered a tie
        private boolean isTie(String[][] board, IMove move) {
            // Find the position of the microboard
            int localX = move.getX() % 3;
            int localY = move.getY() % 3;
            // Find the starting coordinate of the 3x3 board
            int startX = move.getX() - (localX);
            int startY = move.getY() - (localY);
            // Check every field in the 3x3 board
            for (int i = startX; i < startX + 3; i++) {
                for (int k = startY; k < startY + 3; k++) {
                    // If we find an empty or available field
                    // the board is not a tie yet
                    if (board[i][k].equals(IField.AVAILABLE_FIELD) ||
                            board[i][k].equals(IField.EMPTY_FIELD))
                        return false;
                }
            }
            // No empty fields were found → it is a tie
            return true;
        }

          // Checks if the current player has won a 3x3 board
          // A win can happen horizontally, vertically or diagonally
        public boolean isWin(String[][] board, IMove move, String currentPlayer) {
            // Find local position inside the 3x3 microboard
            int localX = move.getX() % 3;
            int localY = move.getY() % 3;
            // Find top-left coordinate of the microboard
            int startX = move.getX() - (localX);
            int startY = move.getY() - (localY);
            //check col
            for (int i = startY; i < startY + 3; i++) {
                // If any field is not the player's mark, stop checking
                if (!board[move.getX()][i].equals(currentPlayer))
                    break;
                // If we reach the last position, it means 3 in a column
                if (i == startY + 3 - 1)
                    return true;
            }

            //check row
            for (int i = startX; i < startX + 3; i++) {
                if (!board[i][move.getY()].equals(currentPlayer))
                    break;
                if (i == startX + 3 - 1)
                    return true;
            }

            //check diagonal
            if (localX == localY) {
                //we're on a diagonal
                int y = startY;
                for (int i = startX; i < startX + 3; i++) {
                    if (!board[i][y++].equals(currentPlayer))
                        break;
                    if (i == startX + 3 - 1)
                        return true;
                }
            }

            //check anti diagonal
            //The anti-diagonal runs from top-right to bottom-left in a matrix.
            //In a 3×3 grid, the anti-diagonal positions are: (0,2), (1,1), (2,0)
            if (localX + localY == 3 - 1) {
                int less = 0;
                for (int i = startX; i < startX + 3; i++) {
                    if (!board[i][(startY + 2) - less++].equals(currentPlayer))
                        break;
                    if (i == startX + 3 - 1)
                        return true;
                }
            }
            // No win found
            return false;
        }

          // Updates which microboard is active for the next move
        private void updateMacroboard(IMove move) {
            String[][] macroBoard = currentState.getField().getMacroboard();
            // First reset all previously available fields
            for (int i = 0; i < macroBoard.length; i++)
                for (int k = 0; k < macroBoard[i].length; k++) {
                    if (macroBoard[i][k].equals(IField.AVAILABLE_FIELD))
                        macroBoard[i][k] = IField.EMPTY_FIELD;
                }
            // Determine which microboard the opponent must play in
            int xTrans = move.getX() % 3;
            int yTrans = move.getY() % 3;
            // If that microboard is still playable → activate it
            if (macroBoard[xTrans][yTrans].equals(IField.EMPTY_FIELD))
                macroBoard[xTrans][yTrans] = IField.AVAILABLE_FIELD;
            else {
                // Field is already won, set all fields not won to avail.
                for (int i = 0; i < macroBoard.length; i++)
                    for (int k = 0; k < macroBoard[i].length; k++) {
                        if (macroBoard[i][k].equals(IField.EMPTY_FIELD))
                            macroBoard[i][k] = IField.AVAILABLE_FIELD;
                    }
            }
        }

        public int getCurrentPlayer() {
            return currentPlayer;
        }
    }

}