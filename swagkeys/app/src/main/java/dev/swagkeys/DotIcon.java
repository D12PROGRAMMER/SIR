package dev.swagkeys;

import android.graphics.Canvas;
import android.graphics.Paint;

/** A monochrome dot-matrix glyph, drawn as a grid of filled circles. */
public final class DotIcon {
    private final boolean[][] cells;
    private final int cols;
    private final int rows;

    private DotIcon(String... pattern) {
        rows = pattern.length;
        int c = 0;
        for (String row : pattern) c = Math.max(c, row.length());
        cols = c;
        cells = new boolean[rows][cols];
        for (int r = 0; r < rows; r++) {
            for (int i = 0; i < pattern[r].length(); i++) {
                cells[r][i] = pattern[r].charAt(i) == '#';
            }
        }
    }

    public static final DotIcon SHIFT = new DotIcon(
            "...#...",
            "..#.#..",
            ".#...#.",
            "##...##",
            "..#.#..",
            "..#.#..",
            "..###..");

    public static final DotIcon SHIFT_ON = new DotIcon(
            "...#...",
            "..###..",
            ".#####.",
            "#######",
            "..###..",
            "..###..",
            "..###..");

    public static final DotIcon SHIFT_LOCK = new DotIcon(
            "...#...",
            "..###..",
            ".#####.",
            "#######",
            "..###..",
            ".......",
            "..###..");

    public static final DotIcon DELETE = new DotIcon(
            "...######",
            "..#.....#",
            ".#..#.#.#",
            "#....#..#",
            ".#..#.#.#",
            "..#.....#",
            "...######");

    public static final DotIcon ENTER = new DotIcon(
            "......#",
            "......#",
            "..#...#",
            ".#....#",
            "#######",
            ".#.....",
            "..#....");

    public static final DotIcon SEARCH = new DotIcon(
            ".###...",
            "#...#..",
            "#...#..",
            "#...#..",
            ".###...",
            "....##.",
            ".....##");

    public static final DotIcon GLOBE = new DotIcon(
            "..###..",
            ".#.#.#.",
            "#..#..#",
            "#######",
            "#..#..#",
            ".#.#.#.",
            "..###..");

    public static final DotIcon GEAR = new DotIcon(
            "...#...",
            ".#####.",
            ".##.##.",
            "###.###",
            ".##.##.",
            ".#####.",
            "...#...");

    /** Draws the icon centered at (cx, cy) so that its larger dimension spans {@code size}. */
    public void draw(Canvas canvas, float cx, float cy, float size, Paint paint) {
        float cell = size / Math.max(cols, rows);
        float radius = cell * 0.36f;
        float left = cx - cell * cols / 2f;
        float top = cy - cell * rows / 2f;
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                if (cells[r][c]) {
                    canvas.drawCircle(left + (c + 0.5f) * cell, top + (r + 0.5f) * cell, radius, paint);
                }
            }
        }
    }
}
