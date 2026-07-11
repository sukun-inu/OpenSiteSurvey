package com.opensitesurvey.tool.plugin;

import com.opensitesurvey.tool.model.ScanSnapshot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PluginManagerTest {

    private static final String MARKER_PROPERTY = "opensitesurvey.test.pluginMarkerFile";

    private record RecordingPlugin(String name, List<ScanSnapshot> received) implements OpenSiteSurveyPlugin {
        @Override
        public void onScanSnapshot(ScanSnapshot snapshot) {
            received.add(snapshot);
        }
    }

    private record ThrowingPlugin(String name) implements OpenSiteSurveyPlugin {
        @Override
        public void onScanSnapshot(ScanSnapshot snapshot) {
            throw new RuntimeException("boom");
        }
    }

    @Test
    void dispatchNotifiesEveryLoadedPlugin() {
        List<ScanSnapshot> receivedA = new ArrayList<>();
        List<ScanSnapshot> receivedB = new ArrayList<>();
        PluginManager manager = PluginManager.of(List.of(
                new RecordingPlugin("A", receivedA), new RecordingPlugin("B", receivedB)));
        ScanSnapshot snapshot = new ScanSnapshot(Instant.now(), List.of());
        manager.dispatchSnapshot(snapshot);
        assertEquals(List.of(snapshot), receivedA);
        assertEquals(List.of(snapshot), receivedB);
    }

    @Test
    void onePluginThrowingDoesNotPreventOthersFromBeingNotified() {
        List<ScanSnapshot> received = new ArrayList<>();
        PluginManager manager = PluginManager.of(List.of(
                new ThrowingPlugin("Bad"), new RecordingPlugin("Good", received)));
        manager.dispatchSnapshot(new ScanSnapshot(Instant.now(), List.of()));
        assertEquals(1, received.size());
    }

    @Test
    void loadedPluginNamesReflectsEachPluginsOwnName() {
        PluginManager manager = PluginManager.of(List.of(
                new RecordingPlugin("Alpha", new ArrayList<>()), new RecordingPlugin("Beta", new ArrayList<>())));
        assertEquals(List.of("Alpha", "Beta"), manager.loadedPluginNames());
    }

    @Test
    void loadWithNonExistentDirectoryYieldsNoPlugins(@TempDir Path tempDir) {
        PluginManager manager = PluginManager.load(tempDir.resolve("does-not-exist"));
        assertTrue(manager.loadedPluginNames().isEmpty());
        manager.dispatchSnapshot(new ScanSnapshot(Instant.now(), List.of())); // must not throw
    }

    @Test
    void loadWithEmptyDirectoryYieldsNoPlugins(@TempDir Path tempDir) {
        PluginManager manager = PluginManager.load(tempDir);
        assertTrue(manager.loadedPluginNames().isEmpty());
    }

    /**
     * Compiles a tiny fixture plugin at test time and packages it as a real JAR with a
     * META-INF/services entry, so PluginManager's actual ServiceLoader/URLClassLoader-based
     * discovery path is exercised end-to-end rather than just its dispatch logic (already covered
     * above via {@link PluginManager#of}). A system property carries a marker-file path across
     * the plugin's own classloader boundary, since static fields on a class loaded by the child
     * URLClassLoader wouldn't be visible to this test's own copy of that class.
     */
    @Test
    void loadDiscoversAndDispatchesToARealPluginJar(@TempDir Path tempDir) throws Exception {
        Path markerFile = tempDir.resolve("marker.txt");
        System.setProperty(MARKER_PROPERTY, markerFile.toString());
        try {
            Path pluginsDir = tempDir.resolve("plugins");
            Files.createDirectories(pluginsDir);
            buildFixturePluginJar(pluginsDir.resolve("fixture.jar"));

            // try-with-resources: closes the JAR's classloader before @TempDir tries to delete
            // the directory (an open URLClassLoader holds the jar file open on Windows).
            try (PluginManager manager = PluginManager.load(pluginsDir)) {
                assertEquals(List.of("Fixture Plugin"), manager.loadedPluginNames());

                manager.dispatchSnapshot(new ScanSnapshot(Instant.now(), List.of()));

                List<String> markerLines = Files.readAllLines(markerFile);
                assertEquals(List.of("loaded", "snapshot"), markerLines);
            }
        } finally {
            System.clearProperty(MARKER_PROPERTY);
        }
    }

    private void buildFixturePluginJar(Path jarPath) throws IOException {
        Path sourceDir = Files.createTempDirectory("plugin-fixture-src");
        Path sourceFile = sourceDir.resolve("FixturePlugin.java");
        Files.writeString(sourceFile, FIXTURE_SOURCE);

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Path classesDir = Files.createTempDirectory("plugin-fixture-classes");
        String classpath = System.getProperty("java.class.path");
        int result = compiler.run(null, null, null,
                "-classpath", classpath, "-d", classesDir.toString(), sourceFile.toString());
        if (result != 0) {
            throw new IOException("Failed to compile fixture plugin (javac exit code " + result + ")");
        }

        try (JarOutputStream jar = new JarOutputStream(Files.newOutputStream(jarPath))) {
            addJarEntry(jar, "test/fixture/FixturePlugin.class",
                    Files.readAllBytes(classesDir.resolve("test").resolve("fixture").resolve("FixturePlugin.class")));
            addJarEntry(jar, "META-INF/services/com.opensitesurvey.tool.plugin.OpenSiteSurveyPlugin",
                    "test.fixture.FixturePlugin\n".getBytes(StandardCharsets.UTF_8));
        }
    }

    private void addJarEntry(JarOutputStream jar, String entryName, byte[] content) throws IOException {
        jar.putNextEntry(new JarEntry(entryName));
        jar.write(content);
        jar.closeEntry();
    }

    private static final String FIXTURE_SOURCE = """
            package test.fixture;

            import com.opensitesurvey.tool.model.ScanSnapshot;
            import com.opensitesurvey.tool.plugin.OpenSiteSurveyPlugin;
            import java.nio.file.Files;
            import java.nio.file.Paths;
            import java.nio.file.StandardOpenOption;

            public class FixturePlugin implements OpenSiteSurveyPlugin {
                @Override
                public String name() {
                    return "Fixture Plugin";
                }

                @Override
                public void onLoad() {
                    mark("loaded");
                }

                @Override
                public void onScanSnapshot(ScanSnapshot snapshot) {
                    mark("snapshot");
                }

                private void mark(String event) {
                    try {
                        String path = System.getProperty("opensitesurvey.test.pluginMarkerFile");
                        if (path != null) {
                            Files.writeString(Paths.get(path), event + "\\n",
                                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                        }
                    } catch (Exception e) {
                        // best-effort marker only
                    }
                }
            }
            """;
}
