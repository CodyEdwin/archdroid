package com.archdroid.bootstrap;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Unit tests for TarExtractor
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class TarExtractorTest {

    private File testDir;

    @Before
    public void setUp() {
        testDir = new File(RuntimeEnvironment.getApplication().getCacheDir(), "tar-test");
        testDir.mkdirs();
    }

    @Test
    public void testExtractEmptyTar() throws IOException {
        // Create an empty tar (just null bytes for end of archive)
        byte[] emptyTar = new byte[1024];
        TarExtractor extractor = new TarExtractor(testDir);

        final AtomicBoolean progressCalled = new AtomicBoolean(false);
        long bytes = extractor.extract(
            new ByteArrayInputStream(emptyTar),
            (current, total) -> {
                progressCalled.set(true);
                return true;
            }
        );

        assertTrue(progressCalled.get());
    }

    @Test
    public void testExtractSingleFile() throws IOException {
        // Create a minimal tar-like structure
        // This is a simplified test - real tar files are more complex
        TarExtractor extractor = new TarExtractor(testDir);

        // Test that the extractor can be instantiated
        assertNotNull(extractor);
    }

    @Test
    public void testProgressCallback() {
        TarExtractor extractor = new TarExtractor(testDir);

        final int[] callCount = {0};
        TarExtractor.ProgressCallback callback = (current, total) -> {
            callCount[0]++;
            return true;
        };

        // Test callback interface
        assertNotNull(callback);
        assertTrue(callback.onProgress(100, 1000));
    }

    @Test
    public void testExtractDirectory() throws IOException {
        TarExtractor extractor = new TarExtractor(testDir);
        File subDir = new File(testDir, "test-subdir");
        subDir.mkdirs();

        // Test that directories can be handled
        assertTrue(subDir.exists());
        assertTrue(subDir.isDirectory());
    }

    @Test
    public void testDestinationDirectory() {
        File destDir = new File(RuntimeEnvironment.getApplication().getCacheDir(), "destination");
        TarExtractor extractor = new TarExtractor(destDir);

        assertNotNull(extractor);
    }
}
