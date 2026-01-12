package com.archdroid.terminal;

import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Manages a terminal session - the connection between Android and Linux environment
 * Handles process creation, I/O streams, and lifecycle management
 */
public class TerminalSession {

    private static final String TAG = "TerminalSession";

    private final String launchScript;
    private Process process;
    private OutputStream stdin;
    private Thread outputThread;
    private final ExecutorService executor;
    private final AtomicBoolean isRunning;
    private OutputListener outputListener;

    public interface OutputListener {
        void onOutput(String data);
    }

    public TerminalSession(String launchScript) {
        this.launchScript = launchScript;
        this.executor = Executors.newSingleThreadExecutor();
        this.isRunning = new AtomicBoolean(false);
    }

    public void setOutputListener(OutputListener listener) {
        this.outputListener = listener;
    }

    public void start() {
        if (isRunning.getAndSet(true)) {
            // Already running
            return;
        }

        try {
            // Create process with shell execution
            ProcessBuilder processBuilder = new ProcessBuilder(
                "/system/bin/sh",
                "-c",
                launchScript
            );

            processBuilder.redirectErrorStream(true);

            process = processBuilder.start();
            stdin = process.getOutputStream();

            // Start output reader
            startOutputReader();

            Log.d(TAG, "Terminal session started with script: " + launchScript);

        } catch (IOException e) {
            Log.e(TAG, "Failed to start terminal session", e);
            isRunning.set(false);
        }
    }

    public void resume() {
        if (!isRunning.get()) {
            start();
        }
    }

    public void pause() {
        // Process continues in background when app is paused
    }

    public void write(String data) {
        if (stdin == null || data == null) {
            return;
        }

        try {
            byte[] bytes = data.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            stdin.write(bytes);
            stdin.flush();
        } catch (IOException e) {
            Log.e(TAG, "Failed to write to terminal", e);
        }
    }

    public void write(byte[] data) {
        if (stdin == null || data == null) {
            return;
        }

        try {
            stdin.write(data);
            stdin.flush();
        } catch (IOException e) {
            Log.e(TAG, "Failed to write to terminal", e);
        }
    }

    private void startOutputReader() {
        outputThread = new Thread(() -> {
            byte[] buffer = new byte[4096];
            InputStream inputStream = null;

            try {
                if (process != null) {
                    inputStream = process.getInputStream();
                }

                if (inputStream == null) {
                    return;
                }

                int bytesRead;
                while (isRunning.get() && (bytesRead = inputStream.read(buffer)) != -1) {
                    if (bytesRead > 0) {
                        String data = new String(buffer, 0, bytesRead,
                            java.nio.charset.StandardCharsets.UTF_8);

                        if (outputListener != null) {
                            outputListener.onOutput(data);
                        }
                    }
                }

            } catch (IOException e) {
                if (isRunning.get()) {
                    Log.e(TAG, "Output reader error", e);
                }
            } finally {
                isRunning.set(false);
                Log.d(TAG, "Output reader stopped");
            }
        }, "TerminalOutputReader");

        outputThread.start();
    }

    public void close() {
        if (!isRunning.getAndSet(false)) {
            // Already closed
            return;
        }

        // Close stdin
        if (stdin != null) {
            try {
                stdin.close();
            } catch (IOException e) {
                Log.w(TAG, "Error closing stdin", e);
            }
            stdin = null;
        }

        // Destroy process
        if (process != null) {
            process.destroy();
            try {
                boolean finished = process.waitFor(1, TimeUnit.SECONDS);
                int exitCode = finished ? process.exitValue() : -1;
                Log.d(TAG, "Process exited with code: " + exitCode);
            } catch (InterruptedException e) {
                Log.w(TAG, "Process termination interrupted", e);
            } catch (IllegalThreadStateException e) {
                Log.w(TAG, "Process was not running", e);
            }
            process = null;
        }

        // Wait for output thread
        if (outputThread != null) {
            try {
                outputThread.join(1000);
            } catch (InterruptedException e) {
                Log.w(TAG, "Output thread join interrupted", e);
            }
            outputThread = null;
        }

        // Shutdown executor
        executor.shutdown();

        Log.d(TAG, "Terminal session closed");
    }

    public boolean isRunning() {
        return isRunning.get();
    }

    public int waitFor() throws InterruptedException {
        if (process != null) {
            return process.waitFor();
        }
        return -1;
    }

    public int exitValue() {
        if (process != null) {
            return process.exitValue();
        }
        return -1;
    }

    /**
     * Key codes for special keys not available on mobile keyboards
     */
    public static class KeyCodes {
        public static final int KEYCODE_CTRL_LEFT = 1001;
        public static final int KEYCODE_CTRL_RIGHT = 1002;
        public static final int KEYCODE_ALT_LEFT = 1003;
        public static final int KEYCODE_ALT_RIGHT = 1004;
        public static final int KEYCODE_TAB = 1005;
        public static final int KEYCODE_ESCAPE = 1006;
        public static final int KEYCODE_UP = 1007;
        public static final int KEYCODE_DOWN = 1008;
        public static final int KEYCODE_LEFT = 1009;
        public static final int KEYCODE_RIGHT = 1010;
        public static final int KEYCODE_HOME = 1011;
        public static final int KEYCODE_END = 1012;
        public static final int KEYCODE_PAGE_UP = 1013;
        public static final int KEYCODE_PAGE_DOWN = 1014;
        public static final int KEYCODE_F1 = 1015;
        public static final int KEYCODE_F2 = 1016;
        public static final int KEYCODE_F3 = 1017;
        public static final int KEYCODE_F4 = 1018;
        public static final int KEYCODE_F5 = 1019;
        public static final int KEYCODE_F6 = 1020;
        public static final int KEYCODE_F7 = 1021;
        public static final int KEYCODE_F8 = 1022;
        public static final int KEYCODE_F9 = 1023;
        public static final int KEYCODE_F10 = 1024;
        public static final int KEYCODE_F11 = 1025;
        public static final int KEYCODE_F12 = 1026;

        private KeyCodes() {
            // Prevent instantiation
        }
    }
}
