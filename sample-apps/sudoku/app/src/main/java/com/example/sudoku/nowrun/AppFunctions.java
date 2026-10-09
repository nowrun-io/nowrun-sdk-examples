package com.example.sudoku.nowrun;

import com.example.sudoku.SudokuBoard;

import io.nowrun.sdk.NowrunSdkService.Describe;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * The functions the nowrun agent can call. Every public method declared here, static ones
 * included, is published, so everything else is private or package-private.
 *
 * Each function goes through the same SudokuBoard methods as the buttons, so a call and a tap
 * behave the same. The board repaints the screen and pushes the state itself.
 *
 * Sudoku has a single screen and no back stack, so there is no openScreen or goBack, and no
 * entities to search: cells are addressed by row and column.
 */
public final class AppFunctions {
    private static volatile AppFunctions instance;

    /** One instance for the process. Package-private: activities go through Nowrun.start. */
    static AppFunctions get() {
        if (instance == null) {
            synchronized (AppFunctions.class) {
                if (instance == null) {
                    StateReporter.install(SudokuBoard.shared());
                    instance = new AppFunctions();
                }
            }
        }
        return instance;
    }

    private final SudokuBoard board = SudokuBoard.shared();

    private AppFunctions() {}

    @Describe("Where the game stands: board and clues (nine rows of nine characters, '.' for an empty cell), "
            + "the selected cell, cells in conflict, how many are empty, whether it's solved, and what just "
            + "changed. Cells are named R<row>C<col>, both 1-9. Same shape as pushed state.")
    public String getState() {
        return Results.ok(StateReporter.snapshot());
    }

    @Describe("Fills one cell with a digit, or clears it with 0, like tapping the cell and a number key. "
            + "Clues can't be changed. Returns the cell and whether the digit conflicts with its row, "
            + "column or block. Use getState to see the board first.")
    public String setCell(
            @Describe("row: 1-9, counted from the top.") int row,
            @Describe("col: the column, 1-9, counted from the left.") int col,
            @Describe("value: the digit 1-9 to place, or 0 to clear the cell.") int value) {
        if (row < 1 || row > SudokuBoard.SIZE || col < 1 || col > SudokuBoard.SIZE) {
            return Results.fail("INVALID_ARGUMENTS", "row and col must each be 1-9, got row " + row + ", col " + col);
        }
        if (value < 0 || value > SudokuBoard.SIZE) {
            return Results.fail("INVALID_ARGUMENTS", "value must be 1-9, or 0 to clear, got " + value);
        }
        int index = (row - 1) * SudokuBoard.SIZE + (col - 1);
        String cell = SudokuBoard.describe(index);
        synchronized (board) {
            if (board.isGiven(index)) {
                return Results.fail("NOT_ALLOWED", cell + " is a clue holding " + board.clueAt(index) + " and can't be changed");
            }
            board.set(index, value);
            return reply(cell + (value == 0 ? " cleared" : " = " + value), board.conflicts(index));
        }
    }

    @Describe("Fills one cell with its correct digit, like the Hint button: the first empty cell, or else "
            + "the first wrong entry. Returns the cell it filled.")
    public String giveHint() {
        synchronized (board) {
            int at = board.hint();
            if (at < 0) {
                return Results.fail("NOT_ALLOWED", "every cell is already correct");
            }
            return reply("filled " + SudokuBoard.describe(at) + " = " + board.get(at), false);
        }
    }

    @Describe("Clears every digit the player entered, keeping the clues, like the Restart button. Irreversible.")
    public String restart(
            @Describe("confirm: true to clear; false returns how many entries would be cleared, changing nothing.") boolean confirm) {
        synchronized (board) {
            int entries = board.entries();
            if (!confirm) {
                return Results.fail("NEEDS_CONFIRMATION", "would clear " + entries + " entered digits");
            }
            board.restart();
            return Results.ok("cleared " + entries + " entered digits");
        }
    }

    @Describe("Starts a different puzzle, discarding the current one and everything entered, like the New "
            + "button. Irreversible.")
    public String newGame(
            @Describe("confirm: true to start a new puzzle; false returns what would be lost, changing nothing.") boolean confirm) {
        synchronized (board) {
            if (!confirm) {
                return Results.fail("NEEDS_CONFIRMATION",
                        "would discard this puzzle and " + board.entries() + " entered digits");
            }
            board.newGame();
            return Results.ok("new puzzle, " + board.blanks() + " cells to fill");
        }
    }

    @Describe("Fills in the whole solution, overwriting any wrong entries, like the Solve button. Ends the "
            + "puzzle. Use giveHint for one cell instead.")
    public String solve(
            @Describe("confirm: true to reveal the solution; false returns what would change, changing nothing.") boolean confirm) {
        synchronized (board) {
            if (!confirm) {
                return Results.fail("NEEDS_CONFIRMATION",
                        "would fill the " + board.blanks() + " empty cells and overwrite any wrong entries");
            }
            if (!board.solve()) {
                return Results.fail("APP_FUNCTION_FAILED", "this puzzle has no solution");
            }
            return Results.ok("solved");
        }
    }

    private String reply(String message, boolean conflicts) {
        try {
            return Results.ok(new JSONObject()
                    .put("message", message)
                    .put("conflicts", conflicts)
                    .put("blanks", board.blanks())
                    .put("solved", board.isSolved()));
        } catch (JSONException e) {
            return Results.ok(message);
        }
    }
}
