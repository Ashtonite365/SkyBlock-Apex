// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.init;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import dev.ashtonite365.apex.util.compatloader.CompatSwitch;

public class AutoDiscoveryPlugin {
	private static final String DEFAULT_PACKAGE = "dev.ashtonite365.apex.mixins";
	private static final String CLASS_SUFFIX = ".class";
	private static final List<AutoDiscoveryPlugin> mixinPlugins = new ArrayList<>(2);

	private String mixinPackage;

	private List<String> mixins;

	public static List<String> getDefaultAllMixinClassesFQNs() {
		var discovery = new AutoDiscoveryPlugin();
		discovery.setMixinPackage(DEFAULT_PACKAGE);
		return discovery.getMixins().stream().map(relative -> DEFAULT_PACKAGE + "." + relative).toList();
	}

	public static List<AutoDiscoveryPlugin> getMixinPlugins() { return AutoDiscoveryPlugin.mixinPlugins; }

	private static void log(String message) {
		System.out.println("[apex mixin discovery] " + message);
	}

	private static URL url(String spec) {
		try {
			return new URL(spec);
		} catch (MalformedURLException malformed) {
			throw new RuntimeException(malformed);
		}
	}

	public void setMixinPackage(String dottedPackage) {
		this.mixinPackage = dottedPackage;
		AutoDiscoveryPlugin.mixinPlugins.add(this);
	}

	public String getMixinPackage() { return this.mixinPackage; }

	public String getMixinBaseDir() { return this.mixinPackage.replace('.', '/'); }

	public URL getBaseUrlForClassUrl(final URL location) {
		String spec = location.toString();
		if ("jar".equals(location.getProtocol())) {
			return url(spec.substring("jar:".length()).split("!")[0]);
		}
		if (!spec.endsWith(CLASS_SUFFIX)) return location;
		String ownFile = getClass().getCanonicalName().replace(".", "/") + CLASS_SUFFIX;
		return url(spec.replace("\\", "/").replace(ownFile, ""));
	}

	public void tryAddMixinClass(final String entryName) {
		if (!entryName.endsWith(CLASS_SUFFIX)) return;
		String dotted = entryName.substring(0, entryName.length() - CLASS_SUFFIX.length()).replace('\\', '/').replace('/', '.');
		String packagePrefix = getMixinPackage() + ".";
		boolean inPackage = dotted.startsWith(packagePrefix) && !dotted.endsWith(".");
		if (inPackage && CompatSwitch.Companion.mayLoad(dotted)) {
			mixins.add(dotted.substring(packagePrefix.length()));
		}
	}

	public List<String> getMixins() {
		if (this.mixins == null) discover();
		return this.mixins;
	}

	private void discover() {
		try {
			log("discovering mixins");
			this.mixins = new ArrayList<>();
			URL ownLocation = getClass().getProtectionDomain().getCodeSource().getLocation();
			log("own classes at " + ownLocation);
			scanRoot(ownLocation);
			String extraRoots = System.getProperty("apex.classroots");
			if (extraRoots != null && !extraRoots.isBlank()) {
				log("extra class roots " + extraRoots);
				for (String root : extraRoots.split(File.pathSeparator)) {
					if (!root.isBlank()) scanRoot(new File(root).toURI().toURL());
				}
			}
		} catch (Exception failure) {
			failure.printStackTrace();
			System.exit(1);
		}
	}

	private void scanRoot(URL location) {
		Path root;
		try {
			root = Paths.get(getBaseUrlForClassUrl(location).toURI());
		} catch (URISyntaxException badUri) {
			throw new RuntimeException(badUri);
		}
		log("class root " + root);
		if (!Files.exists(root)) {
			log("class root missing, skipped: " + root);
			return;
		}
		try {
			if (Files.isDirectory(root)) eachDirectoryEntry(root, this::tryAddMixinClass);
			else eachJarEntry(root, this::tryAddMixinClass);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		log("mixins so far: " + mixins);
	}

	private void eachDirectoryEntry(Path root, Consumer<String> sink) throws IOException {
		Path packageDir = root.resolve(getMixinBaseDir());
		if (!Files.exists(packageDir)) return;
		try (Stream<Path> tree = Files.walk(packageDir)) {
			tree.forEach(path -> sink.accept(root.relativize(path).toString()));
		}
	}

	private static void eachJarEntry(Path jar, Consumer<String> sink) throws IOException {
		try (ZipInputStream entries = new ZipInputStream(Files.newInputStream(jar))) {
			for (ZipEntry entry = entries.getNextEntry(); entry != null; entry = entries.getNextEntry()) {
				sink.accept(entry.getName());
				entries.closeEntry();
			}
		}
	}
}
