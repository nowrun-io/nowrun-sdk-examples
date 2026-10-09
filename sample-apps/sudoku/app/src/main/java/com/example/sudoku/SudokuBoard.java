package com.example.sudoku;

import java.util.List;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The game itself: 81 cells, the rules, and a solver. No Android in here.
 *
 * The single source of truth for both the UI and the nowrun functions: taps and function
 * calls go through the same methods, and every change is announced to the listeners, which
 * repaint the screen and push the state.
 *
 * Every method is synchronized because the board is touched both by taps on the main thread
 * and by nowrun calls arriving on worker threads.
 *
 * Listeners run on a thread of their own, never while anyone holds the board's lock: one of them
 * pushes the state to the nowrun player, a call into another process, and a tap waiting on the
 * lock must not wait for that too.
 */
public final class SudokuBoard {

    public static final int SIZE = 9;
    public static final int CELLS = SIZE * SIZE;

    /** '.' is a blank. Each was generated and checked to have exactly one solution. */
    private static final String[] PUZZLES = {
            "7..629...2...1.....46........3...5..5......831...7..6....1...78.....62.449..8.3..",
            ".3.....98...7...6........5....2.4.3.......2..19..5....57.318...9.....6..4...2....",
            ".8...79...178...3..429.5........2........65.1..4....89..1...7.3.6..39..........5.",
            "2...8.7........2.1...1.6..8.7.5.1...8......3...3.7..4.6......19..2..5......6.7..2",
    };

    /** One board for the process, so a recreated activity and the functions share it. */
    private static final SudokuBoard SHARED = new SudokuBoard();

    public static SudokuBoard shared() {
        return SHARED;
    }

    private final Random random = new Random();
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();
    /** One thread, so listeners see changes one at a time and in order. */
    private final ExecutorService notifier = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "sudoku-listeners");
        thread.setDaemon(true);
        return thread;
    });

    /** The clues, fixed for the round. Zero means the player fills it in. */
    private final int[] given = new int[CELLS];
    private final int[] cells = new int[CELLS];

    /** The puzzle's own answer, worked out once from the clues alone. */
    private final int[] solution = new int[CELLS];
    private boolean solvable;

    private int selected = -1;
    /** What just changed, in words, or null when there's nothing to say. */
    private String lastAction;

    private SudokuBoard() {
        load(PUZZLES[random.nextInt(PUZZLES.length)]);
    }

    public void addListener(Runnable listener) {
        listeners.add(listener);
    }

    public void removeListener(Runnable listener) {
        listeners.remove(listener);
    }

    private void changed(String action) {
        lastAction = action;
        // Called with the lock held, by this class and often by the caller too, so the
        // listeners are handed off rather than run here.
        notifier.execute(() -> {
            for (Runnable listener : listeners) {
                listener.run();
            }
        });
    }

    // ---- Actions: the UI's buttons and the nowrun functions both call these ----------------

    /** Starts a fresh puzzle, discarding anything entered. */
    public synchronized void newGame() {
        load(PUZZLES[random.nextInt(PUZZLES.length)]);
        selected = -1;
        changed("New puzzle");
    }

    /** Clears everything the player entered, keeping the clues. */
    public synchronized void restart() {
        System.arraycopy(given, 0, cells, 0, CELLS);
        changed("Cleared your entries");
    }

    /** Selects a cell, or -1 for none. */
    public synchronized void select(int index) {
        selected = index;
        changed(null);
    }

    /** Writes a value, or 0 to blank it, and selects the cell. Refuses to touch a clue. */
    public synchronized boolean set(int index, int value) {
        if (index < 0 || index >= CELLS || value < 0 || value > SIZE || given[index] != 0) {
            return false;
        }
        cells[index] = value;
        selected = index;
        changed(isSolved() ? "Solved" : describe(index) + (value == 0 ? " cleared" : " = " + value));
        return true;
    }

    /**
     * Fills one cell correctly: the first blank, or failing that the first wrong entry.
     * Returns its index, or -1 when the grid is already right.
     */
    public synchronized int hint() {
        if (!solvable) {
            return -1;
        }
        int at = firstWhere(true);
        if (at < 0) {
            at = firstWhere(false);
        }
        if (at < 0) {
            return -1;
        }
        cells[at] = solution[at];
        selected = at;
        changed(isSolved() ? "Solved" : "Filled " + describe(at));
        return at;
    }

    /** Reveals the answer, replacing anything entered wrongly. False if there is none. */
    public synchronized boolean solve() {
        if (!solvable) {
            return false;
        }
        System.arraycopy(solution, 0, cells, 0, CELLS);
        changed("Solved");
        return true;
    }

    // ---- Reading the board ---------------------------------------------------------------

    public synchronized int get(int index) {
        return cells[index];
    }

    public synchronized boolean isGiven(int index) {
        return given[index] != 0;
    }

    public synchronized int clueAt(int index) {
        return given[index];
    }

    public synchronized int selected() {
        return selected;
    }

    public synchronized String lastAction() {
        return lastAction;
    }

    /** True when this cell repeats a value already in its row, column or block. */
    public synchronized boolean conflicts(int index) {
        int value = cells[index];
        if (value == 0) {
            return false;
        }
        int row = index / SIZE;
        int col = index % SIZE;
        for (int i = 0; i < SIZE; i++) {
            int inRow = row * SIZE + i;
            int inCol = i * SIZE + col;
            if (inRow != index && cells[inRow] == value) {
                return true;
            }
            if (inCol != index && cells[inCol] == value) {
                return true;
            }
        }
        int blockRow = (row / 3) * 3;
        int blockCol = (col / 3) * 3;
        for (int r = blockRow; r < blockRow + 3; r++) {
            for (int c = blockCol; c < blockCol + 3; c++) {
                int at = r * SIZE + c;
                if (at != index && cells[at] == value) {
                    return true;
                }
            }
        }
        return false;
    }

    public synchronized boolean isSolved() {
        for (int i = 0; i < CELLS; i++) {
            if (cells[i] == 0 || conflicts(i)) {
                return false;
            }
        }
        return true;
    }

    public synchronized int blanks() {
        int count = 0;
        for (int value : cells) {
            if (value == 0) {
                count++;
            }
        }
        return count;
    }

    /** How many cells hold a digit the player entered. */
    public synchronized int entries() {
        int count = 0;
        for (int i = 0; i < CELLS; i++) {
            if (given[i] == 0 && cells[i] != 0) {
                count++;
            }
        }
        return count;
    }

    /** "R3C4": row and column, both 1-based, as the status line and the functions show a cell. */
    public static String describe(int index) {
        return "R" + (index / SIZE + 1) + "C" + (index % SIZE + 1);
    }

    // ---- Internals -----------------------------------------------------------------------

    private void load(String puzzle) {
        for (int i = 0; i < CELLS; i++) {
            char c = i < puzzle.length() ? puzzle.charAt(i) : '.';
            int value = (c >= '1' && c <= '9') ? c - '0' : 0;
            given[i] = value;
            cells[i] = value;
        }

        // Solve from the clues now, so hints never depend on what the player typed.
        int[] work = given.clone();
        solvable = search(work, 0);
        if (solvable) {
            System.arraycopy(work, 0, solution, 0, CELLS);
        }
    }

    /** The first blank cell (blank == true), or the first wrong entry. */
    private int firstWhere(boolean blank) {
        for (int i = 0; i < CELLS; i++) {
            if (blank ? cells[i] == 0 : cells[i] != solution[i]) {
                return i;
            }
        }
        return -1;
    }

    /** Plain backtracking: first blank cell, try 1..9, recurse. */
    private static boolean search(int[] board, int index) {
        if (index == CELLS) {
            return true;
        }
        if (board[index] != 0) {
            return search(board, index + 1);
        }
        for (int value = 1; value <= SIZE; value++) {
            if (allowed(board, index, value)) {
                board[index] = value;
                if (search(board, index + 1)) {
                    return true;
                }
                board[index] = 0;
            }
        }
        return false;
    }

    private static boolean allowed(int[] board, int index, int value) {
        int row = index / SIZE;
        int col = index % SIZE;
        for (int i = 0; i < SIZE; i++) {
            if (board[row * SIZE + i] == value || board[i * SIZE + col] == value) {
                return false;
            }
        }
        int blockRow = (row / 3) * 3;
        int blockCol = (col / 3) * 3;
        for (int r = blockRow; r < blockRow + 3; r++) {
            for (int c = blockCol; c < blockCol + 3; c++) {
                if (board[r * SIZE + c] == value) {
                    return false;
                }
            }
        }
        return true;
    }
}
