package com.archdroid.bootstrap;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/**
 * Unit tests for BootstrapManager
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class BootstrapManagerTest {

    private BootstrapManager bootstrapManager;

    @Before
    public void setUp() {
        bootstrapManager = new BootstrapManager(RuntimeEnvironment.getApplication());
    }

    @Test
    public void testGetLaunchScriptPath() {
        String path = BootstrapManager.getLaunchScriptPath(RuntimeEnvironment.getApplication());
        assertNotNull(path);
        assertTrue(path.endsWith("start-arch.sh"));
        assertTrue(path.contains("files"));
    }

    @Test
    public void testIsArchLinuxInstalledInitiallyFalse() {
        // Fresh install should return false
        boolean installed = bootstrapManager.isArchLinuxInstalled();
        assertFalse(installed);
    }

    @Test
    public void testBootstrapListener() {
        // Test that we can set a listener without error
        BootstrapManager.BootstrapListener listener = new BootstrapManager.BootstrapListener() {
            @Override
            public void onBootstrapStarted(String message) {}

            @Override
            public void onProgressUpdate(long current, long total) {}

            @Override
            public void onBootstrapComplete() {}

            @Override
            public void onBootstrapFailed(String error) {}
        };

        bootstrapManager.setBootstrapListener(listener);
        assertNotNull(bootstrapManager);
    }

    @Test
    public void testCancelBootstrap() {
        // Should not throw exception
        bootstrapManager.cancelBootstrap();
    }

    @Test
    public void testResetEnvironment() {
        // Should not throw exception
        bootstrapManager.resetEnvironment();
    }
}
