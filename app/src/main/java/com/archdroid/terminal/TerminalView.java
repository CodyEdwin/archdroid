package com.archdroid.terminal;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.archdroid.R;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Custom View for rendering terminal output on Android
 * Handles ANSI escape codes and user input
 */
public class TerminalView extends View {

    private static final String TAG = "TerminalView";

    private final TerminalEmulator emulator;
    private final Paint textPaint;
    private final Paint backgroundPaint;
    private final GestureDetector gestureDetector;
    private final ScaleGestureDetector scaleGestureDetector;
    private final ExecutorService executor;

    private TerminalSession session;
    private OnSessionChangeListener sessionChangeListener;

    private float textSize = 14f;
    private float lineSpacing = 2f;
    private float horizontalPadding = 4f;
    private float verticalPadding = 4f;

    private int defaultForegroundColor = Color.WHITE;
    private int defaultBackgroundColor = Color.BLACK;

    public interface OnSessionChangeListener {
        void onSessionChanged(TerminalSession session);
    }

    public TerminalView(Context context) {
        this(context, null);
    }

    public TerminalView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public TerminalView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        emulator = new TerminalEmulator(80, 24);
        textPaint = new Paint();
        textPaint.setColor(defaultForegroundColor);
        textPaint.setTextSize(textSize);
        textPaint.setTypeface(Typeface.MONOSPACE);
        textPaint.setAntiAlias(true);

        backgroundPaint = new Paint();
        backgroundPaint.setColor(defaultBackgroundColor);

        gestureDetector = new GestureDetector(context, new GestureListener());
        scaleGestureDetector = new ScaleGestureDetector(context, new ScaleListener());

        executor = Executors.newSingleThreadExecutor();

        setFocusable(true);
        setClickable(true);

        // Initialize with default terminal colors
        initializeTerminalColors();
    }

    private void initializeTerminalColors() {
        // Set up default color palette based on Android dark theme
        if (emulator != null) {
            emulator.setColorPalette(
                Color.parseColor("#000000"), // Black
                Color.parseColor("#FF5555"), // Red
                Color.parseColor("#50FA7B"), // Green
                Color.parseColor("#F1FA8C"), // Yellow
                Color.parseColor("#BD93F9"), // Blue
                Color.parseColor("#FF79C6"), // Magenta
                Color.parseColor("#8BE9FD"), // Cyan
                Color.parseColor("#F8F8F2"), // White
                Color.parseColor("#6272A4"), // Bright Black (Gray)
                Color.parseColor("#FF6E6E"), // Bright Red
                Color.parseColor("#69FF94"), // Bright Green
                Color.parseColor("#FFFFA5"), // Bright Yellow
                Color.parseColor("#D6ACFF"), // Bright Blue
                Color.parseColor("#FF92DF"), // Bright Magenta
                Color.parseColor("#A4FFFF"), // Bright Cyan
                Color.parseColor("#FFFFFF")  // Bright White
            );
        }
    }

    public void startSession(String launchScript) {
        if (session != null) {
            session.close();
        }

        session = new TerminalSession(launchScript);
        session.setOutputListener(new TerminalSession.OutputListener() {
            @Override
            public void onOutput(String data) {
                emulator.write(data);
                postInvalidate();
            }
        });

        if (sessionChangeListener != null) {
            sessionChangeListener.onSessionChanged(session);
        }

        invalidate();
    }

    public void createNewSession() {
        try {
            com.archdroid.bootstrap.BootstrapManager manager =
                new com.archdroid.bootstrap.BootstrapManager(getContext());
            String scriptPath = com.archdroid.bootstrap.BootstrapManager
                .getLaunchScriptPath(getContext());
            startSession(scriptPath);
        } catch (Exception e) {
            // Session creation failed
        }
    }

    public void write(String input) {
        if (session != null && input != null) {
            session.write(input);
        }
    }

    public void write(int keyCode) {
        if (session == null) {
            return;
        }

        String escapeSequence = getEscapeSequence(keyCode);
        if (escapeSequence != null) {
            session.write(escapeSequence);
        }
    }

    private String getEscapeSequence(int keyCode) {
        switch (keyCode) {
            case TerminalSession.KeyCodes.KEYCODE_CTRL_LEFT:
            case TerminalSession.KeyCodes.KEYCODE_CTRL_RIGHT:
                return "\u0017"; // ETX (Ctrl+C equivalent)

            case TerminalSession.KeyCodes.KEYCODE_ALT_LEFT:
            case TerminalSession.KeyCodes.KEYCODE_ALT_RIGHT:
                return "\u001b"; // ESC

            case TerminalSession.KeyCodes.KEYCODE_TAB:
                return "\u0009"; // TAB

            case TerminalSession.KeyCodes.KEYCODE_ESCAPE:
                return "\u001b"; // ESC

            case TerminalSession.KeyCodes.KEYCODE_UP:
                return "\u001b[A";

            case TerminalSession.KeyCodes.KEYCODE_DOWN:
                return "\u001b[B";

            case TerminalSession.KeyCodes.KEYCODE_LEFT:
                return "\u001b[D";

            case TerminalSession.KeyCodes.KEYCODE_RIGHT:
                return "\u001b[C";

            case TerminalSession.KeyCodes.KEYCODE_HOME:
                return "\u001b[H";

            case TerminalSession.KeyCodes.KEYCODE_END:
                return "\u001b[F";

            case TerminalSession.KeyCodes.KEYCODE_PAGE_UP:
                return "\u001b[5~";

            case TerminalSession.KeyCodes.KEYCODE_PAGE_DOWN:
                return "\u001b[6~";

            case TerminalSession.KeyCodes.KEYCODE_F1:
                return "\u001bOP";

            case TerminalSession.KeyCodes.KEYCODE_F2:
                return "\u001bOQ";

            case TerminalSession.KeyCodes.KEYCODE_F3:
                return "\u001bOR";

            case TerminalSession.KeyCodes.KEYCODE_F4:
                return "\u001bOS";

            case TerminalSession.KeyCodes.KEYCODE_F5:
                return "\u001b[15~";

            case TerminalSession.KeyCodes.KEYCODE_F6:
                return "\u001b[17~";

            case TerminalSession.KeyCodes.KEYCODE_F7:
                return "\u001b[18~";

            case TerminalSession.KeyCodes.KEYCODE_F8:
                return "\u001b[19~";

            case TerminalSession.KeyCodes.KEYCODE_F9:
                return "\u001b[20~";

            case TerminalSession.KeyCodes.KEYCODE_F10:
                return "\u001b[21~";

            case TerminalSession.KeyCodes.KEYCODE_F11:
                return "\u001b[23~";

            case TerminalSession.KeyCodes.KEYCODE_F12:
                return "\u001b[24~";

            default:
                return null;
        }
    }

    public void onResume() {
        if (session != null) {
            session.resume();
        }
    }

    public void onPause() {
        // Session continues in background
    }

    public void onDestroy() {
        if (session != null) {
            session.close();
            session = null;
        }
        executor.shutdown();
    }

    public void setOnSessionChangeListener(OnSessionChangeListener listener) {
        this.sessionChangeListener = listener;
    }

    public void setTextSize(float size) {
        this.textSize = size;
        textPaint.setTextSize(size);
        invalidate();
    }

    public float getTextSize() {
        return textSize;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);

        float cellWidth = textPaint.measureText("M");
        float cellHeight = textPaint.getTextSize() + lineSpacing;

        int cols = (int) ((w - 2 * horizontalPadding) / cellWidth);
        int rows = (int) ((h - 2 * verticalPadding) / cellHeight);

        cols = Math.max(1, cols);
        rows = Math.max(1, rows);

        emulator.resize(cols, rows);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        // Draw background
        canvas.drawRect(0, 0, getWidth(), getHeight(), backgroundPaint);

        float cellWidth = textPaint.measureText("M");
        float cellHeight = textPaint.getTextSize() + lineSpacing;

        int cols = emulator.getWidth();
        int rows = emulator.getHeight();

        // Draw each character
        for (int row = 0; row < rows; row++) {
            float y = verticalPadding + (row + 1) * cellHeight - lineSpacing;

            TerminalEmulator.TextLine line = emulator.getLine(row);
            if (line == null) continue;

            for (int col = 0; col < cols; col++) {
                char ch = line.getChar(col);
                if (ch == 0 || ch == ' ') continue;

                float x = horizontalPadding + col * cellWidth;

                // Get colors for this cell
                int foregroundColor = line.getForeColor(col);
                int backgroundColor = line.getBackColor(col);

                // Draw background for this cell
                Paint cellBgPaint = new Paint();
                cellBgPaint.setColor(backgroundColor);
                canvas.drawRect(
                    x, verticalPadding + row * cellHeight,
                    x + cellWidth, verticalPadding + (row + 1) * cellHeight,
                    cellBgPaint
                );

                // Draw character
                textPaint.setColor(foregroundColor);
                if (line.isBold(col)) {
                    textPaint.setFakeBoldText(true);
                } else {
                    textPaint.setFakeBoldText(false);
                }
                canvas.drawText(String.valueOf(ch), x, y, textPaint);
            }
        }

        // Draw cursor
        if (emulator.getCursorRow() >= 0 && emulator.getCursorRow() < rows &&
            emulator.getCursorCol() >= 0 && emulator.getCursorCol() < cols) {

            int cursorRow = emulator.getCursorRow();
            int cursorCol = emulator.getCursorCol();

            float cursorX = horizontalPadding + cursorCol * cellWidth;
            float cursorY = verticalPadding + cursorRow * cellHeight;

            Paint cursorPaint = new Paint();
            cursorPaint.setColor(Color.WHITE);
            cursorPaint.setStyle(Paint.Style.STROKE);
            cursorPaint.setStrokeWidth(2f);
            canvas.drawRect(
                cursorX, cursorY,
                cursorX + cellWidth, cursorY + cellHeight,
                cursorPaint
            );
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        scaleGestureDetector.onTouchEvent(event);
        gestureDetector.onTouchEvent(event);
        return true;
    }

    private void handleTap(float x, float y) {
        float cellWidth = textPaint.measureText("M");
        float cellHeight = textPaint.getTextSize() + lineSpacing;

        int col = (int) ((x - horizontalPadding) / cellWidth);
        int row = (int) ((y - verticalPadding) / cellHeight);

        emulator.moveCursorTo(row, col);
        invalidate();
    }

    private void handleScroll(float distanceY) {
        emulator.scroll((int) (-distanceY / 20));
        invalidate();
    }

    private class GestureListener extends GestureDetector.SimpleOnGestureListener {
        @Override
        public boolean onSingleTapUp(MotionEvent e) {
            handleTap(e.getX(), e.getY());
            requestFocus();
            return true;
        }

        @Override
        public boolean onDoubleTap(MotionEvent e) {
            requestFocus();
            return true;
        }

        @Override
        public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
            handleScroll(distanceY);
            return true;
        }

        public boolean onContextMenu(MotionEvent e) {
            // Handle long press for context menu
            return true;
        }
    }

    private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            textSize *= detector.getScaleFactor();
            textSize = Math.max(8f, Math.min(32f, textSize));
            textPaint.setTextSize(textSize);

            // Recalculate terminal size
            float cellWidth = textPaint.measureText("M");
            float cellHeight = textPaint.getTextSize() + lineSpacing;

            int cols = (int) ((getWidth() - 2 * horizontalPadding) / cellWidth);
            int rows = (int) ((getHeight() - 2 * verticalPadding) / cellHeight);

            emulator.resize(Math.max(1, cols), Math.max(1, rows));

            invalidate();
            return true;
        }
    }
}
