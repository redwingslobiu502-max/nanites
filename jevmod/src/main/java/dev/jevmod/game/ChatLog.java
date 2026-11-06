package dev.jevmod.game;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * The last few things the owner typed, newest first. Chat is an input to the state, and the AI is told that the
 * newest message wins, so the order and the age matter more than the wording.
 */
public final class ChatLog {
    public record Line(String text, long tick) {
    }

    private static final int KEEP = 16;

    private final Deque<Line> lines = new ArrayDeque<>();

    void add(String text, long tick) {
        lines.addFirst(new Line(text, tick));
        while (lines.size() > KEEP) {
            lines.removeLast();
        }
    }

    /** Newest first, at most {@code max} entries. */
    public List<Line> newest(int max) {
        List<Line> out = new ArrayList<>();
        for (Line l : lines) {
            if (out.size() >= Math.max(0, max)) {
                break;
            }
            out.add(l);
        }
        return out;
    }
}
