package com.example.sudoku.nowrun;

import com.example.sudoku.SudokuBoard;

import io.nowrun.sdk.NowrunSdkService;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * The app's state as the agent sees it. getState returns snapshot(), and push() sends the
 * same object, so pulled and pushed state never disagree.
 */
final class StateReporter {
    private StateReporter() {}

    private static boolean installed;

    /** Pushes the state on every change to the board. */
    static synchronized void install(SudokuBoard board) {
        if (installed) return;
        installed = true;
        board.addListener(StateReporter::push);
    }

    static void push() {
        NowrunSdkService.notifyStateChange(snapshot().toString());
    }

    static JSONObject snapshot() {
        SudokuBoard board = SudokuBoard.shared();
        synchronized (board) {
            JSONArray rows = new JSONArray();
            JSONArray clues = new JSONArray();
            JSONArray conflicts = new JSONArray();
            for (int row = 0; row < SudokuBoard.SIZE; row++) {
                StringBuilder values = new StringBuilder();
                StringBuilder givens = new StringBuilder();
                for (int col = 0; col < SudokuBoard.SIZE; col++) {
                    int index = row * SudokuBoard.SIZE + col;
                    values.append(digit(board.get(index)));
                    givens.append(digit(board.clueAt(index)));
                    if (board.conflicts(index)) {
                        conflicts.put(SudokuBoard.describe(index));
                    }
                }
                rows.put(values.toString());
                clues.put(givens.toString());
            }
            int selected = board.selected();
            try {
                return new JSONObject()
                        .put("screen", "board")
                        .put("board", rows)
                        .put("clues", clues)
                        .put("selected", selected < 0 ? JSONObject.NULL : SudokuBoard.describe(selected))
                        .put("conflicts", conflicts)
                        .put("blanks", board.blanks())
                        .put("solved", board.isSolved())
                        .put("lastAction", board.lastAction() == null ? JSONObject.NULL : board.lastAction());
            } catch (JSONException impossible) {
                return new JSONObject();
            }
        }
    }

    private static char digit(int value) {
        return value == 0 ? '.' : (char) ('0' + value);
    }
}
