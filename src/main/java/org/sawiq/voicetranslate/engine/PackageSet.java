package org.sawiq.voicetranslate.engine;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * Which packages the engine needs on this computer.
 *
 * <p>The processor set is what everyone gets: Parakeet and the Piper voices
 * run on any processor in real time. NVIDIA's cuBLAS and cuDNN, about
 * 1.2 GB more, only let Whisper run on an NVIDIA card, and are added when
 * the player asks for that.</p>
 */
public enum PackageSet {
    CUDA("requirements-cuda.txt", 1_430),
    CPU("requirements-cpu.txt", 180);

    private static final long NVIDIA_SMI_TIMEOUT_SECONDS = 10L;
    /** CTranslate2's CUDA build needs a Maxwell card or newer. */
    private static final double MIN_COMPUTE_CAPABILITY = 5.0;

    private final String requirementsFile;
    private final int downloadMegabytes;

    PackageSet(String requirementsFile, int downloadMegabytes) {
        this.requirementsFile = requirementsFile;
        this.downloadMegabytes = downloadMegabytes;
    }

    public String requirementsFile() {
        return this.requirementsFile;
    }

    /** Roughly what the packages download, for telling the player before they agree. */
    public int downloadMegabytes() {
        return this.downloadMegabytes;
    }

    /** Asks nvidia-smi for the first card: CUDA when it can run CUDA, the processor set otherwise. */
    public static PackageSet detect() {
        if (EnginePaths.isMac()) {
            return CPU;
        }
        try {
            Process process = new ProcessBuilder("nvidia-smi", "--query-gpu=compute_cap", "--format=csv,noheader")
                    .redirectErrorStream(true)
                    .start();
            String firstLine;
            try (BufferedReader output = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                firstLine = output.readLine();
            }
            if (!process.waitFor(NVIDIA_SMI_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return CPU;
            }
            return process.exitValue() == 0 && isSupportedCard(firstLine) ? CUDA : CPU;
        } catch (IOException exception) {
            return CPU;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return CPU;
        }
    }

    static boolean isSupportedCard(String computeCapability) {
        if (computeCapability == null) {
            return false;
        }
        try {
            return Double.parseDouble(computeCapability.strip()) >= MIN_COMPUTE_CAPABILITY;
        } catch (NumberFormatException exception) {
            return false;
        }
    }
}
