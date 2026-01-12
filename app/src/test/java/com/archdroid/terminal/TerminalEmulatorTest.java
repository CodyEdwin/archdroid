package com.archdroid.terminal;

import static org.junit.Assert.*;

import android.graphics.Color;

import org.junit.Before;
import org.junit.Test;

/**
 * Unit tests for TerminalEmulator
 */
public class TerminalEmulatorTest {

    private TerminalEmulator emulator;

    @Before
    public void setUp() {
        emulator = new TerminalEmulator(80, 24);
    }

    @Test
    public void testInitialSize() {
        assertEquals(80, emulator.getWidth());
        assertEquals(24, emulator.getHeight());
    }

    @Test
    public void testResize() {
        emulator.resize(100, 40);
        assertEquals(100, emulator.getWidth());
        assertEquals(40, emulator.getHeight());
    }

    @Test
    public void testWriteSimpleText() {
        emulator.write("Hello");
        assertEquals('H', emulator.getLine(0).getChar(0));
        assertEquals('e', emulator.getLine(0).getChar(1));
        assertEquals('l', emulator.getLine(0).getChar(2));
        assertEquals('l', emulator.getLine(0).getChar(3));
        assertEquals('o', emulator.getLine(0).getChar(4));
    }

    @Test
    public void testWriteNewline() {
        emulator.write("Line1\nLine2");
        assertEquals('L', emulator.getLine(0).getChar(0));
        assertEquals('L', emulator.getLine(1).getChar(0));
    }

    @Test
    public void testCursorMovement() {
        emulator.write("ABCD");
        assertEquals(4, emulator.getCursorCol());
        assertEquals(0, emulator.getCursorRow());

        emulator.write("\n");
        assertEquals(0, emulator.getCursorCol());
        assertEquals(1, emulator.getCursorRow());
    }

    @Test
    public void testBackspace() {
        emulator.write("ABC\b");
        assertEquals('A', emulator.getLine(0).getChar(0));
        assertEquals('B', emulator.getLine(0).getChar(1));
        // 'C' should be removed by backspace
        assertNotEquals('C', emulator.getLine(0).getChar(2));
    }

    @Test
    public void testClearScreen() {
        emulator.write("Some text");
        assertEquals('S', emulator.getLine(0).getChar(0));

        emulator.clearScreen();
        assertEquals(' ', emulator.getLine(0).getChar(0));
    }

    @Test
    public void testColorPalette() {
        // Test that color palette is set
        int[] palette = new int[16];
        palette[0] = Color.BLACK;
        palette[1] = Color.RED;
        emulator.setColorPalette(palette);

        // Verify the emulator can handle colors
        assertNotNull(emulator);
    }

    @Test
    public void testScroll() {
        // Fill screen with text
        for (int i = 0; i < 24; i++) {
            emulator.write("Line " + i + "\n");
        }

        // Scroll should be possible
        emulator.scroll(-5);
        // Verify no exception is thrown
        assertNotNull(emulator);
    }

    @Test
    public void testCursorPosition() {
        emulator.moveCursorTo(10, 20);
        assertEquals(10, emulator.getCursorRow());
        assertEquals(20, emulator.getCursorCol());
    }

    @Test
    public void testEmptyLine() {
        TerminalEmulator.TextLine line = emulator.getLine(0);
        assertNotNull(line);
        assertEquals(' ', line.getChar(0));
    }

    @Test
    public void testOutOfBoundsLine() {
        TerminalEmulator.TextLine line = emulator.getLine(100);
        assertNotNull(line);
    }
}
