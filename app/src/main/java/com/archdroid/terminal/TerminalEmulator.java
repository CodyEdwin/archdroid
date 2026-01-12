package com.archdroid.terminal;

import android.graphics.Color;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Terminal emulator that processes ANSI escape sequences
 * Supports xterm-256color and basic ANSI codes
 */
public class TerminalEmulator {

    private static final String TAG = "TerminalEmulator";

    public static class Cell {
        public char character;
        public int foregroundColor;
        public int backgroundColor;
        public boolean bold;
        public boolean underline;
        public boolean reverse;
        public boolean italic;

        public Cell() {
            this(' ', Color.WHITE, Color.BLACK, false, false, false, false);
        }

        public Cell(char character, int foregroundColor, int backgroundColor,
                    boolean bold, boolean underline, boolean reverse, boolean italic) {
            this.character = character;
            this.foregroundColor = foregroundColor;
            this.backgroundColor = backgroundColor;
            this.bold = bold;
            this.underline = underline;
            this.reverse = reverse;
            this.italic = italic;
        }

        public void reset() {
            this.character = ' ';
            this.foregroundColor = Color.WHITE;
            this.backgroundColor = Color.BLACK;
            this.bold = false;
            this.underline = false;
            this.reverse = false;
            this.italic = false;
        }
    }

    public static class TextLine {
        private final List<Cell> cells;

        public TextLine() {
            cells = new ArrayList<>();
        }

        public TextLine(int size) {
            cells = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                cells.add(new Cell());
            }
        }

        public char getChar(int index) {
            if (index >= 0 && index < cells.size()) {
                return cells.get(index).character;
            }
            return ' ';
        }

        public int getForeColor(int index) {
            if (index >= 0 && index < cells.size()) {
                return cells.get(index).foregroundColor;
            }
            return Color.WHITE;
        }

        public int getBackColor(int index) {
            if (index >= 0 && index < cells.size()) {
                return cells.get(index).backgroundColor;
            }
            return Color.BLACK;
        }

        public boolean isBold(int index) {
            if (index >= 0 && index < cells.size()) {
                return cells.get(index).bold;
            }
            return false;
        }

        public void setChar(int index, char ch) {
            ensureCapacity(index);
            cells.get(index).character = ch;
        }

        public void setCell(int index, Cell cell) {
            ensureCapacity(index);
            cells.set(index, cell);
        }

        public void ensureCapacity(int index) {
            while (cells.size() <= index) {
                cells.add(new Cell());
            }
        }

        public int size() {
            return cells.size();
        }

        public void clear() {
            for (Cell cell : cells) {
                cell.reset();
            }
        }
    }

    private volatile int width = 80;
    private volatile int height = 24;

    private final List<TextLine> screen;
    private final List<TextLine> scrollbackBuffer;
    private final int maxScrollbackLines = 1000;

    private volatile int cursorRow = 0;
    private volatile int cursorCol = 0;

    private int[] colorPalette;
    private int defaultForeground = Color.WHITE;
    private int defaultBackground = Color.BLACK;

    private int currentForeground;
    private int currentBackground;
    private boolean currentBold = false;
    private boolean currentUnderline = false;
    private boolean currentReverse = false;
    private boolean currentItalic = false;

    private int savedCursorRow = 0;
    private int savedCursorCol = 0;

    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    private static final Pattern ANSI_PATTERN = Pattern.compile(
        "\u001B\\[([\\d;]*)([a-zA-Z])"
    );

    public TerminalEmulator(int initialWidth, int initialHeight) {
        screen = new ArrayList<>();
        scrollbackBuffer = new ArrayList<>();
        colorPalette = new int[16];

        setDefaultColorPalette();
        resize(initialWidth, initialHeight);
    }

    private void setDefaultColorPalette() {
        // Standard 16-color palette (Dracula theme inspired)
        colorPalette[0] = Color.parseColor("#000000");  // Black
        colorPalette[1] = Color.parseColor("#FF5555");  // Red
        colorPalette[2] = Color.parseColor("#50FA7B");  // Green
        colorPalette[3] = Color.parseColor("#F1FA8C");  // Yellow
        colorPalette[4] = Color.parseColor("#BD93F9");  // Blue
        colorPalette[5] = Color.parseColor("#FF79C6");  // Magenta
        colorPalette[6] = Color.parseColor("#8BE9FD");  // Cyan
        colorPalette[7] = Color.parseColor("#F8F8F2");  // White
        colorPalette[8] = Color.parseColor("#6272A4");  // Bright Black (Gray)
        colorPalette[9] = Color.parseColor("#FF6E6E");  // Bright Red
        colorPalette[10] = Color.parseColor("#69FF94"); // Bright Green
        colorPalette[11] = Color.parseColor("#FFFFA5"); // Bright Yellow
        colorPalette[12] = Color.parseColor("#D6ACFF"); // Bright Blue
        colorPalette[13] = Color.parseColor("#FF92DF"); // Bright Magenta
        colorPalette[14] = Color.parseColor("#A4FFFF"); // Bright Cyan
        colorPalette[15] = Color.parseColor("#FFFFFF"); // Bright White

        currentForeground = defaultForeground;
        currentBackground = defaultBackground;
    }

    public void setColorPalette(int... colors) {
        if (colors != null && colors.length >= 16) {
            for (int i = 0; i < 16 && i < colors.length; i++) {
                colorPalette[i] = colors[i];
            }
        }
    }

    public void resize(int newWidth, int newHeight) {
        lock.writeLock().lock();
        try {
            width = Math.max(1, newWidth);
            height = Math.max(1, newHeight);

            // Adjust screen size
            while (screen.size() < height) {
                screen.add(new TextLine(width));
            }

            // Trim or expand existing lines
            for (TextLine line : screen) {
                if (line.size() < width) {
                    line.ensureCapacity(width - 1);
                }
            }

            // Adjust cursor position
            cursorRow = Math.min(cursorRow, height - 1);
            cursorCol = Math.min(cursorCol, width - 1);

        } finally {
            lock.writeLock().unlock();
        }
    }

    public void write(String data) {
        lock.readLock().lock();
        try {
            int i = 0;
            while (i < data.length()) {
                char ch = data.charAt(i);

                if (ch == '\u001B' && i + 1 < data.length() && data.charAt(i + 1) == '[') {
                    // ANSI escape sequence
                    i = processEscapeSequence(data, i + 2);
                } else {
                    // Regular character
                    processCharacter(ch);
                    i++;
                }
            }
        } finally {
            lock.readLock().unlock();
        }
    }

    private int processEscapeSequence(String data, int startIndex) {
        int i = startIndex;
        List<Integer> params = new ArrayList<>();
        int currentParam = 0;
        boolean hasDigits = false;

        // Parse parameters
        while (i < data.length()) {
            char ch = data.charAt(i);
            if (Character.isDigit(ch)) {
                currentParam = currentParam * 10 + (ch - '0');
                hasDigits = true;
            } else if (ch == ';') {
                if (hasDigits) {
                    params.add(currentParam);
                }
                currentParam = 0;
                hasDigits = false;
            } else {
                break;
            }
            i++;
        }

        if (hasDigits) {
            params.add(currentParam);
        }

        // Get command character
        char command = i < data.length() ? data.charAt(i) : 'm';
        i++;

        // Execute command
        executeCommand(command, params);

        return i;
    }

    private void executeCommand(char command, List<Integer> params) {
        int getParam = (index, defaultVal) -> {
            if (index < params.size()) {
                return params.get(index);
            }
            return defaultVal;
        };

        switch (command) {
            case 'A': // Cursor up
                cursorRow = Math.max(0, cursorRow - getParam.apply(0, 1));
                break;

            case 'B': // Cursor down
                cursorRow = Math.min(height - 1, cursorRow + getParam.apply(0, 1));
                break;

            case 'C': // Cursor forward (right)
                cursorCol = Math.min(width - 1, cursorCol + getParam.apply(0, 1));
                break;

            case 'D': // Cursor backward (left)
                cursorCol = Math.max(0, cursorCol - getParam.apply(0, 1));
                break;

            case 'E': // Cursor to beginning of line, N lines down
                cursorRow = Math.min(height - 1, cursorRow + getParam.apply(0, 1));
                cursorCol = 0;
                break;

            case 'F': // Cursor to beginning of line, N lines up
                cursorRow = Math.max(0, cursorRow - getParam.apply(0, 1));
                cursorCol = 0;
                break;

            case 'G': // Cursor horizontal absolute
                cursorCol = Math.max(0, Math.min(width - 1, getParam.apply(0, 1) - 1));
                break;

            case 'H': // Cursor position
            case 'f':
                cursorRow = Math.max(0, Math.min(height - 1, getParam.apply(0, 1) - 1));
                cursorCol = Math.max(0, Math.min(width - 1, getParam.apply(1, 1) - 1));
                break;

            case 'J': // Erase display
                int param = getParam.apply(0, 0);
                if (param == 0) {
                    clearToEndOfScreen();
                } else if (param == 1) {
                    clearToBeginningOfScreen();
                } else if (param == 2) {
                    clearScreen();
                }
                break;

            case 'K': // Erase in line
                param = getParam.apply(0, 0);
                if (param == 0) {
                    clearToEndOfLine();
                } else if (param == 1) {
                    clearToBeginningOfLine();
                } else if (param == 2) {
                    clearLine();
                }
                break;

            case 'm': // SGR - graphics rendition
                if (params.isEmpty()) {
                    resetAttributes();
                } else {
                    processSgrParameters(params);
                }
                break;

            case 's': // Save cursor position
                savedCursorRow = cursorRow;
                savedCursorCol = cursorCol;
                break;

            case 'u': // Restore cursor position
                cursorRow = Math.max(0, Math.min(height - 1, savedCursorRow));
                cursorCol = Math.max(0, Math.min(width - 1, savedCursorCol));
                break;

            case 'n': // Device status report (ignore)
                break;

            case 'l': // Insert line (ignore in this implementation)
                break;

            case 'M': // Delete line (ignore in this implementation)
                break;

            default:
                // Unknown command, ignore
                break;
        }
    }

    private void processSgrParameters(List<Integer> params) {
        for (int i = 0; i < params.size(); i++) {
            int param = params.get(i);

            switch (param) {
                case 0:
                    resetAttributes();
                    break;
                case 1:
                    currentBold = true;
                    break;
                case 3:
                    currentItalic = true;
                    break;
                case 4:
                    currentUnderline = true;
                    break;
                case 7:
                    currentReverse = true;
                    break;
                case 22:
                    currentBold = false;
                    break;
                case 23:
                    currentItalic = false;
                    break;
                case 24:
                    currentUnderline = false;
                    break;
                case 27:
                    currentReverse = false;
                    break;

                // Foreground colors (30-37, 90-97)
                case 30:
                    currentForeground = colorPalette[0];
                    break;
                case 31:
                    currentForeground = colorPalette[1];
                    break;
                case 32:
                    currentForeground = colorPalette[2];
                    break;
                case 33:
                    currentForeground = colorPalette[3];
                    break;
                case 34:
                    currentForeground = colorPalette[4];
                    break;
                case 35:
                    currentForeground = colorPalette[5];
                    break;
                case 36:
                    currentForeground = colorPalette[6];
                    break;
                case 37:
                    currentForeground = colorPalette[7];
                    break;

                // 256 color foreground (38;5;n)
                case 38:
                    if (i + 2 < params.size() && params.get(i + 1) == 5) {
                        currentForeground = get256Color(params.get(i + 2));
                        i += 2;
                    }
                    break;

                case 39:
                    currentForeground = defaultForeground;
                    break;

                // Background colors (40-47, 100-107)
                case 40:
                    currentBackground = colorPalette[0];
                    break;
                case 41:
                    currentBackground = colorPalette[1];
                    break;
                case 42:
                    currentBackground = colorPalette[2];
                    break;
                case 43:
                    currentBackground = colorPalette[3];
                    break;
                case 44:
                    currentBackground = colorPalette[4];
                    break;
                case 45:
                    currentBackground = colorPalette[5];
                    break;
                case 46:
                    currentBackground = colorPalette[6];
                    break;
                case 47:
                    currentBackground = colorPalette[7];
                    break;

                // 256 color background (48;5;n)
                case 48:
                    if (i + 2 < params.size() && params.get(i + 1) == 5) {
                        currentBackground = get256Color(params.get(i + 2));
                        i += 2;
                    }
                    break;

                case 49:
                    currentBackground = defaultBackground;
                    break;

                // Bright foreground colors (90-97)
                case 90:
                    currentForeground = colorPalette[8];
                    break;
                case 91:
                    currentForeground = colorPalette[9];
                    break;
                case 92:
                    currentForeground = colorPalette[10];
                    break;
                case 93:
                    currentForeground = colorPalette[11];
                    break;
                case 94:
                    currentForeground = colorPalette[12];
                    break;
                case 95:
                    currentForeground = colorPalette[13];
                    break;
                case 96:
                    currentForeground = colorPalette[14];
                    break;
                case 97:
                    currentForeground = colorPalette[15];
                    break;

                // Bright background colors (100-107)
                case 100:
                    currentBackground = colorPalette[8];
                    break;
                case 101:
                    currentBackground = colorPalette[9];
                    break;
                case 102:
                    currentBackground = colorPalette[10];
                    break;
                case 103:
                    currentBackground = colorPalette[11];
                    break;
                case 104:
                    currentBackground = colorPalette[12];
                    break;
                case 105:
                    currentBackground = colorPalette[13];
                    break;
                case 106:
                    currentBackground = colorPalette[14];
                    break;
                case 107:
                    currentBackground = colorPalette[15];
                    break;

                default:
                    // Unknown parameter, ignore
                    break;
            }
        }
    }

    private int get256Color(int index) {
        if (index < 16) {
            return colorPalette[index];
        } else if (index < 232) {
            // 216 colors (6x6x6 cube)
            index -= 16;
            int r = (index / 36) * 51;
            int g = ((index % 36) / 6) * 51;
            int b = (index % 6) * 51;
            return Color.rgb(r, g, b);
        } else {
            // 24 grayscale colors
            int gray = ((index - 232) * 10) + 8;
            return Color.rgb(gray, gray, gray);
        }
    }

    private void resetAttributes() {
        currentForeground = defaultForeground;
        currentBackground = defaultBackground;
        currentBold = false;
        currentUnderline = false;
        currentReverse = false;
        currentItalic = false;
    }

    private void processCharacter(char ch) {
        lock.writeLock().lock();
        try {
            switch (ch) {
                case '\n':
                    cursorRow++;
                    if (cursorRow >= height) {
                        scrollUp();
                        cursorRow = height - 1;
                    }
                    break;

                case '\r':
                    cursorCol = 0;
                    break;

                case '\t':
                    cursorCol = ((cursorCol / 8) + 1) * 8;
                    if (cursorCol >= width) {
                        cursorCol = width - 1;
                    }
                    break;

                case '\b':
                    if (cursorCol > 0) {
                        cursorCol--;
                    }
                    break;

                default:
                    setCharAtCursor(ch);
                    cursorCol++;
                    if (cursorCol >= width) {
                        cursorCol = 0;
                        cursorRow++;
                        if (cursorRow >= height) {
                            scrollUp();
                            cursorRow = height - 1;
                        }
                    }
                    break;
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    private void setCharAtCursor(char ch) {
        if (cursorRow >= 0 && cursorRow < height && cursorCol >= 0) {
            TextLine line = screen.get(cursorRow);
            line.ensureCapacity(cursorCol);

            Cell cell = new Cell(
                ch,
                currentForeground,
                currentBackground,
                currentBold,
                currentUnderline,
                currentReverse,
                currentItalic
            );
            line.setCell(cursorCol, cell);
        }
    }

    private void scrollUp() {
        if (!screen.isEmpty()) {
            TextLine removedLine = screen.remove(0);
            scrollbackBuffer.add(removedLine);

            // Limit scrollback size
            while (scrollbackBuffer.size() > maxScrollbackLines) {
                scrollbackBuffer.remove(0);
            }

            screen.add(new TextLine(width));
        }
    }

    public void scroll(int lines) {
        lock.writeLock().lock();
        try {
            if (lines < 0) {
                // Scroll down (show more scrollback)
                int scrollAmount = -lines;
                for (int i = 0; i < scrollAmount; i++) {
                    if (!scrollbackBuffer.isEmpty()) {
                        TextLine line = scrollbackBuffer.remove(scrollbackBuffer.size() - 1);
                        screen.add(0, line);
                        if (screen.size() > height) {
                            TextLine removed = screen.remove(screen.size() - 1);
                            scrollbackBuffer.add(0, removed);
                        }
                    }
                }
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void moveCursorTo(int row, int col) {
        cursorRow = Math.max(0, Math.min(height - 1, row));
        cursorCol = Math.max(0, Math.min(width - 1, col));
    }

    public TextLine getLine(int row) {
        lock.readLock().lock();
        try {
            if (row >= 0 && row < screen.size()) {
                return screen.get(row);
            }
            return new TextLine(width);
        } finally {
            lock.readLock().unlock();
        }
    }

    public void clearScreen() {
        lock.writeLock().lock();
        try {
            screen.clear();
            for (int i = 0; i < height; i++) {
                screen.add(new TextLine(width));
            }
            cursorRow = 0;
            cursorCol = 0;
        } finally {
            lock.writeLock().unlock();
        }
    }

    private void clearToEndOfScreen() {
        lock.writeLock().lock();
        try {
            // Clear from cursor to end of current line
            clearToEndOfLine();

            // Clear remaining lines
            for (int i = cursorRow + 1; i < height; i++) {
                screen.set(i, new TextLine(width));
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    private void clearToBeginningOfScreen() {
        lock.writeLock().lock();
        try {
            // Clear from beginning to cursor on current line
            clearToBeginningOfLine();

            // Clear previous lines
            for (int i = 0; i < cursorRow; i++) {
                screen.set(i, new TextLine(width));
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    private void clearToEndOfLine() {
        lock.writeLock().lock();
        try {
            if (cursorRow >= 0 && cursorRow < height) {
                TextLine line = screen.get(cursorRow);
                line.ensureCapacity(width - 1);
                for (int i = cursorCol; i < width; i++) {
                    line.setChar(i, ' ');
                }
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    private void clearToBeginningOfLine() {
        lock.writeLock().lock();
        try {
            if (cursorRow >= 0 && cursorRow < height) {
                TextLine line = screen.get(cursorRow);
                for (int i = 0; i <= cursorCol; i++) {
                    line.setChar(i, ' ');
                }
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    private void clearLine() {
        lock.writeLock().lock();
        try {
            if (cursorRow >= 0 && cursorRow < height) {
                screen.set(cursorRow, new TextLine(width));
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    // Getters
    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public int getCursorRow() {
        return cursorRow;
    }

    public int getCursorCol() {
        return cursorCol;
    }
}
