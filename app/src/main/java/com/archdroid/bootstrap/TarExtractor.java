package com.archdroid.bootstrap;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * TarExtractor extracts tar archives, preserving file permissions and structure
 * Used for extracting the Arch Linux root filesystem
 */
public class TarExtractor {

    private static final String TAG = "TarExtractor";
    private static final int BLOCK_SIZE = 512;

    private final File destDir;

    public interface ProgressCallback {
        /**
         * Called during extraction to report progress
         * @param current Current number of bytes processed
         * @param total Total bytes to process (-1 if unknown)
         * @return true to continue extraction, false to cancel
         */
        boolean onProgress(long current, long total);
    }

    public TarExtractor(File destDir) {
        this.destDir = destDir;
    }

    /**
     * Extract a tar archive from an input stream
     * @param inputStream The input stream containing tar data
     * @param callback Progress callback
     * @return Total bytes extracted
     * @throws IOException If an I/O error occurs
     */
    public long extract(InputStream inputStream, ProgressCallback callback) throws IOException {
        long totalBytes = 0;

        BufferedInputStream bufferedStream = new BufferedInputStream(inputStream);
        DataInputStream dataStream = new DataInputStream(bufferedStream);

        try {
            while (true) {
                // Read header block
                byte[] header = new byte[BLOCK_SIZE];
                int bytesRead = dataStream.read(header);

                if (bytesRead < BLOCK_SIZE) {
                    // End of archive
                    break;
                }

                // Parse header
                String fileName = extractString(header, 0, 100);
                long fileSize = extractOctalLong(header, 124, 12);

                // Check for empty filename (end of archive)
                if (fileName.isEmpty()) {
                    break;
                }

                // Handle directories
                String normalizedName = fileName.replaceFirst("^\\./", "");
                File targetFile = new File(destDir, normalizedName);

                if (normalizedName.endsWith("/") || normalizedName.endsWith("/.")) {
                    // Create directory
                    targetFile.mkdirs();
                } else {
                    // Create parent directories if needed
                    File parentDir = targetFile.getParentFile();
                    if (parentDir != null && !parentDir.exists()) {
                        parentDir.mkdirs();
                    }

                    // Extract file
                    extractFile(dataStream, targetFile, fileSize);
                    totalBytes += fileSize;
                }

                // Set file permissions
                setFilePermissions(targetFile, header);

                // Calculate padding
                long paddedSize = ((fileSize + BLOCK_SIZE - 1) / BLOCK_SIZE) * BLOCK_SIZE;
                totalBytes += BLOCK_SIZE; // Header block

                // Report progress
                if (callback != null) {
                    boolean shouldContinue = callback.onProgress(totalBytes, -1);
                    if (!shouldContinue) {
                        return -1;
                    }
                }

                // Skip padding
                if (paddedSize > fileSize) {
                    dataStream.skipBytes((int) (paddedSize - fileSize));
                }
            }

            // Final progress update
            if (callback != null) {
                callback.onProgress(totalBytes, totalBytes);
            }

        } finally {
            dataStream.close();
        }

        return totalBytes;
    }

    /**
     * Extract a single file from the tar stream
     */
    private void extractFile(DataInputStream dataStream, File targetFile, long size)
            throws IOException {

        try (FileOutputStream fileOut = new FileOutputStream(targetFile)) {
            long remaining = size;
            byte[] buffer = new byte[8192];

            while (remaining > 0) {
                int toRead = (int) Math.min(remaining, buffer.length);
                int bytesRead = dataStream.read(buffer, 0, toRead);

                if (bytesRead < 0) {
                    // Unexpected end of stream
                    break;
                }

                fileOut.write(buffer, 0, bytesRead);
                remaining -= bytesRead;
            }
        }
    }

    /**
     * Extract a null-terminated string from a byte array
     */
    private String extractString(byte[] data, int offset, int length) {
        if (data == null || offset < 0 || length <= 0) {
            return "";
        }

        int endIndex = offset;
        int maxIndex = Math.min(offset + length, data.length);

        // Find null terminator
        while (endIndex < maxIndex && data[endIndex] != 0) {
            endIndex++;
        }

        int strLength = endIndex - offset;
        if (strLength <= 0) {
            return "";
        }

        byte[] strBytes = new byte[strLength];
        System.arraycopy(data, offset, strBytes, 0, strLength);

        return new String(strBytes, java.nio.charset.StandardCharsets.US_ASCII);
    }

    /**
     * Extract an octal number from a byte array
     */
    private long extractOctalLong(byte[] data, int offset, int length) {
        String str = extractString(data, offset, length).trim();

        if (str.isEmpty()) {
            return 0L;
        }

        try {
            return Long.parseLong(str, 8);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    /**
     * Set file permissions from tar header
     */
    private void setFilePermissions(File file, byte[] header) {
        if (file == null || !file.exists() || header == null) {
            return;
        }

        // Extract file mode (permissions)
        int mode = (int) extractOctalLong(header, 100, 8);

        // Only set permissions if we have a valid mode
        if (mode > 0) {
            try {
                // Use chmod to set permissions
                String chmodCmd = String.format("chmod %o %s", mode,
                    file.getAbsolutePath().replace(" ", "\\ "));
                Process process = Runtime.getRuntime().exec(new String[]{
                    "/system/bin/sh", "-c", chmodCmd
                });
                process.waitFor();
            } catch (Exception e) {
                // Permission setting failed, continue anyway
            }
        }

        // Set file timestamp from tar header
        long modTime = extractOctalLong(header, 136, 12);
        if (modTime > 0) {
            try {
                file.setLastModified(modTime * 1000);
            } catch (Exception e) {
                // Timestamp setting failed, ignore
            }
        }
    }
}
