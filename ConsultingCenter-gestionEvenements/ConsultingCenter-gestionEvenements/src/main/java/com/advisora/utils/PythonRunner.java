package com.advisora.utils;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class PythonRunner {

    private PythonRunner() {}

    public static final class Result {
        public final int exitCode;
        public final String out;
        public final List<String> cmd;
        public Result(int exitCode, String out, List<String> cmd) {
            this.exitCode = exitCode;
            this.out = out;
            this.cmd = cmd;
        }
        public boolean ok() { return exitCode == 0; }
    }

    public static Result run(String pythonExe, String scriptPath, String... args) {
        File scriptFile = resolveScriptFile(scriptPath);

        List<String> cmd = new ArrayList<>();
        cmd.add(pythonExe);
        cmd.add(scriptFile.getPath());
        cmd.addAll(Arrays.asList(args));

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);

        // Run from project root when script sits in ./Python or ./python
        pb.directory(resolveWorkingDir(scriptFile));

        String out = "";
        int code = 999;

        try {
            Process p = pb.start();

            StringBuilder sb = new StringBuilder();
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) sb.append(line).append("\n");
            }

            code = p.waitFor();
            out = sb.toString().trim();

        } catch (Exception ex) {
            // Do NOT throw; return error as output instead
            out = "FAIL python_runner_exception " + ex.getClass().getSimpleName() + ": " + ex.getMessage();
            code = 1;
        }

        return new Result(code, out, cmd);
    }

    private static File resolveWorkingDir(File scriptFile) {
        File cwd = new File(System.getProperty("user.dir"));
        if (scriptFile == null) return cwd;

        File parent = scriptFile.getParentFile();
        if (parent != null && "python".equalsIgnoreCase(parent.getName())) {
            File root = parent.getParentFile();
            if (root != null && root.exists()) return root;
        }
        return cwd;
    }

    private static File resolveScriptFile(String scriptPath) {
        File direct = new File(scriptPath);
        if (direct.isAbsolute()) return direct;

        String p = scriptPath == null ? "" : scriptPath.trim();
        if (p.isEmpty()) return direct;

        List<String> variants = new ArrayList<>();
        variants.add(p);
        variants.add(p.replace("python\\", "Python\\").replace("python/", "Python/"));
        variants.add(p.replace("Python\\", "python\\").replace("Python/", "python/"));

        File base = new File(System.getProperty("user.dir"));
        int guard = 0;
        while (base != null && guard < 30) {
            for (String v : variants) {
                File candidate = new File(base, v);
                if (candidate.exists() && candidate.isFile()) return candidate;
            }
            base = base.getParentFile();
            guard++;
        }

        File homeGuess = searchInUserHomeByName(Paths.get(p).getFileName().toString());
        if (homeGuess != null) return homeGuess;

        return direct;
    }

    private static File searchInUserHomeByName(String fileName) {
        if (fileName == null || fileName.isBlank()) return null;
        String home = System.getProperty("user.home");
        if (home == null || home.isBlank()) return null;

        Path start = Paths.get(home);
        try (var stream = Files.find(start, 12,
                (path, attrs) -> attrs.isRegularFile() && path.getFileName().toString().equalsIgnoreCase(fileName))) {
            Optional<Path> hit = stream.findFirst();
            return hit.map(Path::toFile).orElse(null);
        } catch (Exception ignored) {
            return null;
        }
    }
}
