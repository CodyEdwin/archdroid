package com.archdroid.bootstrap;

import android.content.Context;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * BootstrapManager handles the installation of Arch Linux ARM64 on the Android device
 * Manages download, extraction, and configuration of the root filesystem
 */
public class BootstrapManager {

    private static final String TAG = "BootstrapManager";
    private static final String ARCH_DOWNLOAD_URL =
        "https://os.archlinuxarm.org/os/ArchLinuxARM-aarch64-latest.tar.gz";
    private static final String ROOTFS_DIR = "arch";
    private static final int BUFFER_SIZE = 8192;
    private static final int CONNECT_TIMEOUT = 30000;
    private static final int READ_TIMEOUT = 60000;

    private final Context context;
    private BootstrapListener listener;
    private boolean isCancelled = false;

    public interface BootstrapListener {
        void onBootstrapStarted(String message);
        void onProgressUpdate(long current, long total);
        void onBootstrapComplete();
        void onBootstrapFailed(String error);
    }

    public BootstrapManager(Context context) {
        this.context = context.getApplicationContext();
    }

    public void setBootstrapListener(BootstrapListener listener) {
        this.listener = listener;
    }

    /**
     * Check if Arch Linux is already installed
     */
    public boolean isArchLinuxInstalled() {
        String rootfsPath = getRootfsPath();
        File rootfsDir = new File(rootfsPath);

        if (!rootfsDir.exists()) {
            return false;
        }

        // Check for essential directories
        String[] essentialDirs = {
            "bin", "etc", "home", "lib", "lib64", "mnt",
            "opt", "root", "sbin", "srv", "usr", "var"
        };

        for (String dir : essentialDirs) {
            File directory = new File(rootfsDir, dir);
            if (!directory.exists() || !directory.isDirectory()) {
                return false;
            }
        }

        return true;
    }

    /**
     * Start the bootstrap process
     */
    public void startBootstrap() {
        isCancelled = false;
        Thread bootstrapThread = new Thread(() -> {
            try {
                notifyStarted("Checking storage space...");

                // Get directories
                File downloadsDir = getDownloadsDir();
                File archiveFile = new File(downloadsDir, "archlinuxarm.tar.gz");
                String rootfsPath = getRootfsPath();
                File rootfsDir = new File(rootfsPath);

                // Clean up previous installation
                if (rootfsDir.exists()) {
                    deleteRecursive(rootfsDir);
                }
                rootfsDir.mkdirs();

                // Download rootfs
                notifyStarted("Downloading Arch Linux ARM64 root filesystem...");
                downloadFile(ARCH_DOWNLOAD_URL, archiveFile);

                if (isCancelled) {
                    archiveFile.delete();
                    return;
                }

                // Extract rootfs
                notifyStarted("Extracting filesystem (this may take a while)...");
                extractArchive(archiveFile, rootfsDir);

                if (isCancelled) {
                    deleteRecursive(rootfsDir);
                    archiveFile.delete();
                    return;
                }

                // Clean up archive
                archiveFile.delete();

                // Configure system
                notifyStarted("Configuring system...");
                configureSystem();

                notifyComplete();

                Log.d(TAG, "Bootstrap completed successfully");

            } catch (Exception e) {
                Log.e(TAG, "Bootstrap failed", e);
                notifyFailed("Error: " + e.getMessage());
            }
        }, "BootstrapThread");

        bootstrapThread.start();
    }

    /**
     * Cancel ongoing bootstrap operation
     */
    public void cancelBootstrap() {
        isCancelled = true;
    }

    /**
     * Reset the environment - delete all installed files
     */
    public void resetEnvironment() {
        String rootfsPath = getRootfsPath();
        File rootfsDir = new File(rootfsPath);
        deleteRecursive(rootfsDir);

        File homePath = new File(context.getFilesDir(), "home");
        deleteRecursive(homePath);

        File binPath = new File(context.getFilesDir(), "bin");
        deleteRecursive(binPath);
    }

    private void downloadFile(String urlString, File outputFile) throws IOException {
        URL url = new URL(urlString);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(CONNECT_TIMEOUT);
        connection.setReadTimeout(READ_TIMEOUT);
        connection.setRequestMethod("GET");
        connection.connect();

        int responseCode = connection.getResponseCode();
        if (responseCode != HttpURLConnection.HTTP_OK) {
            connection.disconnect();
            throw new IOException("Download failed with HTTP code: " + responseCode);
        }

        long totalSize = connection.getContentLengthLong();
        long downloadedSize = 0;

        try (BufferedInputStream input = new BufferedInputStream(connection.getInputStream());
             BufferedOutputStream output = new BufferedOutputStream(
                 new FileOutputStream(outputFile))) {

            byte[] buffer = new byte[BUFFER_SIZE];
            int bytesRead;

            while ((bytesRead = input.read(buffer)) != -1) {
                if (isCancelled) {
                    output.flush();
                    return;
                }

                output.write(buffer, 0, bytesRead);
                downloadedSize += bytesRead;

                if (totalSize > 0) {
                    notifyProgress(downloadedSize, totalSize);
                }
            }
            output.flush();
        }

        connection.disconnect();
    }

    private void extractArchive(File archiveFile, File destDir) throws IOException {
        try (FileInputStream fileIn = new FileInputStream(archiveFile)) {
            TarExtractor extractor = new TarExtractor(destDir);
            extractor.extract(fileIn, (current, total) -> {
                if (isCancelled) {
                    return false;
                }
                if (total > 0) {
                    notifyProgress(current, total);
                }
                return true;
            });
        }
    }

    private void configureSystem() throws IOException {
        String rootfsPath = getRootfsPath();

        // Configure DNS
        File resolvConf = new File(rootfsPath + "/etc/resolv.conf");
        resolvConf.getParentFile().mkdirs();
        String dnsConfig =
            "# Generated by ArchDroid\n" +
            "nameserver 8.8.8.8\n" +
            "nameserver 8.8.4.4\n" +
            "nameserver 1.1.1.1\n";
        writeStringToFile(dnsConfig, resolvConf);

        // Configure pacman mirrorlist
        File mirrorlist = new File(rootfsPath + "/etc/pacman.d/archlinuxarm-mirrorlist");
        String mirrorlistContent =
            "# Arch Linux ARM mirrors\n" +
            "# Generated by ArchDroid\n\n" +
            "Server = http://mirror.archlinuxarm.org/$arch/$repo\n" +
            "Server = https://archlinuxarm.org/$arch/$repo\n";
        writeStringToFile(mirrorlistContent, mirrorlist);

        // Create launch script
        createLaunchScript();
    }

    private void createLaunchScript() throws IOException {
        String scriptPath = getLaunchScriptPath(context);
        File scriptFile = new File(scriptPath);
        scriptFile.getParentFile().mkdirs();

        String scriptContent =
            "#!/data/data/com.archdroid/files/bin/bash\n" +
            "# ArchDroid Launch Script\n\n" +
            "# Define paths\n" +
            "ROOTFS=/data/data/com.archdroid/files/arch\n" +
            "PROOT=/data/data/com.archdroid/files/bin/proot\n" +
            "HOMEDIR=/data/data/com.archdroid/files/home\n\n" +
            "# Set environment\n" +
            "export HOME=/root\n" +
            "export TERM=xterm-256color\n" +
            "export PATH=/usr/local/sbin:/usr/local/bin:/usr/bin:/usr/sbin:/sbin:/bin:/opt/bin\n" +
            "export TMPDIR=/tmp\n" +
            "export SHELL=/bin/bash\n" +
            "export USER=root\n" +
            "export LOGNAME=root\n\n" +
            "# Configure locale\n" +
            "export LANG=C.UTF-8\n" +
            "export LC_ALL=C.UTF-8\n\n" +
            "# Ensure DNS resolution works\n" +
            "if [ ! -f \"$ROOTFS/etc/resolv.conf\" ]; then\n" +
            "    cat > \"$ROOTFS/etc/resolv.conf\" << 'EOF'\n" +
            "# Generated by ArchDroid\n" +
            "nameserver 8.8.8.8\n" +
            "nameserver 8.8.4.4\n" +
            "nameserver 1.1.1.1\n" +
            "EOF\n" +
            "fi\n\n" +
            "# Create necessary directories\n" +
            "mkdir -p \"$HOMEDIR\"\n" +
            "mkdir -p /tmp\n" +
            "mkdir -p /var/log\n\n" +
            "# Execute PRoot with Arch Linux environment\n" +
            "exec $PROOT \\\n" +
            "    --link2symlink \\\n" +
            "    -0 \\\n" +
            "    -r $ROOTFS \\\n" +
            "    -b /dev \\\n" +
            "    -b /proc \\\n" +
            "    -b /sys \\\n" +
            "    -b /data/data/com.archdroid/files/home:/root \\\n" +
            "    -b /storage:/storage \\\n" +
            "    -b /sdcard:/sdcard \\\n" +
            "    -b /cache:/cache \\\n" +
            "    -w /root \\\n" +
            "    /bin/bash --login +h\n";

        writeStringToFile(scriptContent, scriptFile);

        // Make script executable
        try {
            Process chmod = Runtime.getRuntime().exec("chmod 755 " + scriptPath);
            chmod.waitFor();
        } catch (Exception e) {
            Log.w(TAG, "Failed to set script permissions", e);
        }
    }

    private void writeStringToFile(String content, File file) throws IOException {
        try (OutputStream out = new FileOutputStream(file)) {
            out.write(content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
    }

    private void deleteRecursive(File file) {
        if (file != null && file.exists()) {
            if (file.isDirectory()) {
                File[] children = file.listFiles();
                if (children != null) {
                    for (File child : children) {
                        deleteRecursive(child);
                    }
                }
            }
            file.delete();
        }
    }

    private String getRootfsPath() {
        return context.getFilesDir() + "/" + ROOTFS_DIR;
    }

    private File getDownloadsDir() {
        File dir = new File(context.getCacheDir(), "downloads");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    private void notifyStarted(String message) {
        if (listener != null) {
            listener.onBootstrapStarted(message);
        }
    }

    private void notifyProgress(long current, long total) {
        if (listener != null) {
            listener.onProgressUpdate(current, total);
        }
    }

    private void notifyComplete() {
        if (listener != null) {
            listener.onBootstrapComplete();
        }
    }

    private void notifyFailed(String error) {
        if (listener != null) {
            listener.onBootstrapFailed(error);
        }
    }

    /**
     * Get the launch script path
     */
    public static String getLaunchScriptPath(Context context) {
        return context.getFilesDir() + "/bin/start-arch.sh";
    }
}
