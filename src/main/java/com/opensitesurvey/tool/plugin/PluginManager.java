package com.opensitesurvey.tool.plugin;

import com.opensitesurvey.tool.model.ScanSnapshot;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.ServiceLoader;

/**
 * Discovers and dispatches to {@link OpenSiteSurveyPlugin} implementations packaged as JARs in a
 * plugins directory (see {@code AppPaths.pluginsDir()}), via the standard {@link ServiceLoader}
 * mechanism - each plugin JAR must declare its implementation(s) in a
 * {@code META-INF/services/com.opensitesurvey.tool.plugin.OpenSiteSurveyPlugin} file, the same
 * convention any Java {@code ServiceLoader}-based plugin system uses.
 *
 * <p>One misbehaving plugin (a JAR that fails to load, a provider that fails to instantiate, an
 * {@code onLoad()}/{@code onScanSnapshot()} that throws) is isolated from every other plugin and
 * from the host app itself - failures are logged to stderr rather than aborting startup or the
 * scan loop.
 *
 * <p>Each plugin JAR's {@link URLClassLoader} is kept open for the lifetime of this manager (a
 * plugin's own code can lazily reference classes from its JAR well after {@link #load} returns,
 * e.g. from inside {@code onScanSnapshot()}) - call {@link #close()} once, when the app shuts
 * down, to release those JAR file handles.
 */
public final class PluginManager implements AutoCloseable {

    private final List<OpenSiteSurveyPlugin> plugins;
    private final List<URLClassLoader> classLoaders;

    private PluginManager(List<OpenSiteSurveyPlugin> plugins, List<URLClassLoader> classLoaders) {
        this.plugins = plugins;
        this.classLoaders = classLoaders;
    }

    /** Package-visible so tests can exercise dispatch/isolation logic without real JAR files on disk. */
    static PluginManager of(List<OpenSiteSurveyPlugin> plugins) {
        return new PluginManager(List.copyOf(plugins), List.of());
    }

    /**
     * Scans {@code pluginsDir} for {@code *.jar} files and loads every {@link OpenSiteSurveyPlugin}
     * each one declares. A missing/non-directory {@code pluginsDir} yields an empty (no-op)
     * manager rather than an error - most installs have no plugins at all.
     */
    public static PluginManager load(Path pluginsDir) {
        List<OpenSiteSurveyPlugin> loaded = new ArrayList<>();
        List<URLClassLoader> classLoaders = new ArrayList<>();
        File dir = pluginsDir.toFile();
        File[] jars = dir.isDirectory() ? dir.listFiles((f, name) -> name.endsWith(".jar")) : null;
        if (jars == null) {
            return new PluginManager(List.of(), List.of());
        }
        for (File jar : jars) {
            try {
                URL url = jar.toURI().toURL();
                URLClassLoader classLoader = new URLClassLoader(new URL[]{url}, PluginManager.class.getClassLoader());
                classLoaders.add(classLoader);
                ServiceLoader<OpenSiteSurveyPlugin> serviceLoader = ServiceLoader.load(OpenSiteSurveyPlugin.class, classLoader);
                // Iterated manually (not a for-each) so one provider failing to instantiate
                // (ServiceConfigurationError) doesn't abort the whole jar's remaining providers -
                // ServiceLoader is documented to support resuming iteration after such an error.
                Iterator<OpenSiteSurveyPlugin> iterator = serviceLoader.iterator();
                while (iterator.hasNext()) {
                    try {
                        OpenSiteSurveyPlugin plugin = iterator.next();
                        plugin.onLoad();
                        loaded.add(plugin);
                    } catch (Throwable e) {
                        System.err.println("OpenSiteSurvey: failed to load a plugin from " + jar.getName() + ": " + e);
                    }
                }
            } catch (Exception e) {
                System.err.println("OpenSiteSurvey: failed to open plugin jar " + jar.getName() + ": " + e);
            }
        }
        return new PluginManager(loaded, classLoaders);
    }

    /** Releases every plugin JAR's classloader - call once, when the app shuts down. */
    @Override
    public void close() {
        for (URLClassLoader classLoader : classLoaders) {
            try {
                classLoader.close();
            } catch (Exception e) {
                System.err.println("OpenSiteSurvey: failed to close a plugin classloader: " + e);
            }
        }
    }

    /** Notifies every loaded plugin - safe to call on any thread; each plugin's own exception is isolated from the others. */
    public void dispatchSnapshot(ScanSnapshot snapshot) {
        for (OpenSiteSurveyPlugin plugin : plugins) {
            try {
                plugin.onScanSnapshot(snapshot);
            } catch (Exception e) {
                System.err.println("OpenSiteSurvey plugin '" + plugin.name() + "' threw during onScanSnapshot(): " + e);
            }
        }
    }

    /** Shown in the "Loaded Plugins" dialog (Help menu). */
    public List<String> loadedPluginNames() {
        List<String> names = new ArrayList<>();
        for (OpenSiteSurveyPlugin plugin : plugins) {
            names.add(plugin.name());
        }
        return Collections.unmodifiableList(names);
    }
}
