package dev.nanites.core;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.TimeUnit;
import java.util.zip.*;

/** Optional trusted infrastructure process; never exposed as an LLM tool. No mod execution/classloading. */
public final class VineflowerRunner {
    public record Config(Path javaExecutable, Path decompilerJar, String version, Path approvedMods, Path workRoot, Duration timeout) {
        public Config {
            Objects.requireNonNull(javaExecutable); Objects.requireNonNull(decompilerJar); Ledger.id(version);
            Objects.requireNonNull(approvedMods); Objects.requireNonNull(workRoot); Objects.requireNonNull(timeout);
            if (timeout.isNegative() || timeout.isZero() || timeout.compareTo(Duration.ofSeconds(60)) > 0) throw new IllegalArgumentException("Timeout quota");
        }
    }
    private final Config config;
    private final Path mods, work, java, decompiler;
    private final String fingerprint;
    public VineflowerRunner(Config config) throws IOException {
        this.config = config; mods = SafeData.root(config.approvedMods()); work = SafeData.root(config.workRoot());
        java = approvedFile(config.javaExecutable()); decompiler = approvedFile(config.decompilerJar());
        if (!Set.of("java", "java.exe").contains(java.getFileName().toString()) || !decompiler.getFileName().toString().endsWith(".jar")) throw new IOException("Explicit Java and Vineflower JAR required");
        fingerprint = config.version() + "." + SafeData.hashFile(decompiler, 64L * 1024 * 1024);
        Ledger.id(fingerprint);
    }
    private static Path approvedFile(Path p) throws IOException {
        Path real = p.toRealPath(); if (!real.equals(p.toAbsolutePath().normalize()) || !Files.isRegularFile(real, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Configured file must be regular and canonical"); return real;
    }
    public String decompilerFingerprint() { return fingerprint; }
    public IntegrationContracts.CacheKey decompile(String relativeJar, String classEntry, String loader, String mc, String loaderVersion, String schema, SourceCache cache) throws IOException, InterruptedException {
        if (!relativeJar.endsWith(".jar") || !classEntry.matches("[a-zA-Z0-9_$/]+\\.class") || classEntry.length() > 240 || classEntry.contains("..") || classEntry.startsWith("/")) throw new IOException("Single class selection required");
        Path jar = SafeData.child(mods, relativeJar); String jarHash = SafeData.hashFile(jar, 256L * 1024 * 1024);
        var key = new IntegrationContracts.CacheKey(jarHash, loader, mc, loaderVersion, fingerprint, schema);
        Path job = Files.createTempDirectory(work, "vf-"); Process process = null; Thread drain = null;
        try {
            byte[] clazz;
            try (ZipFile zip = new ZipFile(jar.toFile())) {
                if (zip.size() > 16384) throw new IOException("Archive entry quota"); ZipEntry e = zip.getEntry(classEntry);
                if (e == null || e.isDirectory() || e.getSize() < 0 || e.getSize() > 1_048_576 || e.getCompressedSize() <= 0 || e.getSize() / e.getCompressedSize() > 100) throw new IOException("Class size/ratio quota");
                try (InputStream in = zip.getInputStream(e)) { clazz = in.readNBytes(1_048_577); if (clazz.length > 1_048_576) throw new IOException("Class quota"); }
            }
            if (clazz.length < 4 || clazz[0] != (byte)0xca || clazz[1] != (byte)0xfe || clazz[2] != (byte)0xba || clazz[3] != (byte)0xbe) throw new IOException("Class magic invalid");
            String basename = classEntry.substring(classEntry.lastIndexOf('/') + 1);
            Path input = SafeData.child(job, basename); Files.write(input, clazz, StandardOpenOption.CREATE_NEW);
            Path output = SafeData.child(job, "output"); Files.createDirectory(output);
            ProcessBuilder pb = new ProcessBuilder(java.toString(), "-Xmx256m", "-jar", decompiler.toString(), "--folder", input.toString(), output.toString());
            pb.directory(job.toFile()); pb.redirectErrorStream(true);
            for (String env : List.of("JAVA_TOOL_OPTIONS", "JDK_JAVA_OPTIONS", "_JAVA_OPTIONS", "CLASSPATH")) pb.environment().remove(env);
            // Recheck explicitly configured trusted tool before launch; a changed tool invalidates configuration.
            if (!fingerprint.equals(config.version() + "." + SafeData.hashFile(decompiler, 64L * 1024 * 1024))) throw new IOException("Decompiler changed");
            process = pb.start(); final Process running = process; AtomicBoolean excess = new AtomicBoolean();
            drain = new Thread(() -> {
                try (InputStream in = running.getInputStream()) {
                    byte[] buf = new byte[4096]; int n, count = 0;
                    while ((n = in.read(buf)) >= 0) { count += n; if (count > 262144) { excess.set(true); running.destroyForcibly(); break; } }
                } catch (IOException ignored) { /* Process shutdown closes stream. */ }
            }, "nanites-vineflower-output"); drain.setDaemon(true); drain.start();
            long deadline = System.nanoTime() + config.timeout().toNanos();
            while (!process.waitFor(50, TimeUnit.MILLISECONDS)) {
                if (System.nanoTime() > deadline) throw new IOException("Vineflower timeout");
                checkOutput(output);
            }
            drain.join(1000); checkOutput(output);
            if (process.exitValue() != 0 || excess.get()) throw new IOException("Vineflower failed/output quota");
            List<Path> candidates;
            try (var walk = Files.walk(output, 16)) { candidates = walk.filter(p -> p.getFileName().toString().equals(basename.replace(".class", ".java"))).toList(); }
            if (candidates.size() != 1) throw new IOException("Expected one selected source output");
            Path selected = candidates.get(0); SafeData.child(output, output.relativize(selected).toString().replace('\\', '/'));
            cache.put(key, classEntry.replace(".class", ".java"), new String(SafeData.bytes(selected, SourceCache.MAX_ENTRY_BYTES), StandardCharsets.UTF_8));
            return key;
        } finally {
            if (process != null && process.isAlive()) { process.descendants().forEach(ProcessHandle::destroyForcibly); process.destroyForcibly(); process.waitFor(2, TimeUnit.SECONDS); }
            if (drain != null) drain.join(1000);
            try (var walk = Files.walk(job)) {
                for (Path p : walk.sorted(Comparator.reverseOrder()).toList()) {
                    if (Files.isSymbolicLink(p)) Files.delete(p); else { SafeData.child(work, work.relativize(p).toString().replace('\\', '/')); Files.deleteIfExists(p); }
                }
            }
        }
    }
    private static void checkOutput(Path output) throws IOException {
        long bytes = 0; int count = 0;
        try (var walk = Files.walk(output, 16)) {
            for (Path p : (Iterable<Path>)walk::iterator) {
                if (++count > 128 || Files.isSymbolicLink(p)) throw new IOException("Vineflower output tree quota/link");
                if (Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS)) bytes = Math.addExact(bytes, Files.size(p));
                if (bytes > 2_097_152) throw new IOException("Vineflower output disk quota");
            }
        }
    }
}
