package org.sawiq.voicetranslate.engine;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * Which packages the engine needs on this computer.
 *
 * <p>With an NVIDIA card Whisper runs on CUDA, which needs NVIDIA's cuBLAS
 * and cuDNN next to it, about 1.2 GB more. Everything else runs on the
 * processor, which works everywhere but is a few times slower.</p>
 */
public enum PackageSet {
    CUDA("requirements-cuda.txt", 1_400, "large-v3-turbo"),
    CPU("requirements-cpu.txt", 150, "small");

    private static final long NVIDIA_SMI_TIMEOUT_SECONDS = 10L;
    /** CTranslate2's CUDA build needs a Maxwell card or newer. */
    private static final double MIN_COMPUTE_CAPABILITY = 5.0;

    private final String requirementsFile;
    private final int downloadMegabytes;
    private final String recommendedModel;

    PackageSet(String requirementsFile, int downloadMegabytes, String recommendedModel) {
        this.requirementsFile = requirementsFile;
        this.downloadMegabytes = downloadMegabytes;
        this.recommendedModel = recommendedModel;
    }

    public String requirementsFile() {
        return this.requirementsFile;
    }

    /** Roughly what the packages download, for telling the player before they agree. */
    public int downloadMegabytes() {
        return this.downloadMegabytes;
    }

    /** The model "auto" means here: the best one this hardware runs in real time. */
    public String recommendedModel() {
        return this.recommendedModel;
    }

    /** Asks nvidia-smi for the first card; the processor build when there is none or it cannot be asked. */
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
