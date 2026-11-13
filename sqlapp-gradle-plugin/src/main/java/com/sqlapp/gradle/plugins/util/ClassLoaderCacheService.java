/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.gradle.plugins.util;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.gradle.api.invocation.Gradle;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.provider.Provider;
import org.gradle.api.services.BuildService;
import org.gradle.api.services.BuildServiceParameters;

import com.sqlapp.data.db.dialect.DialectResolver;

/**
 * Build-scoped cache of the class loaders that run commands with a project's
 * runtime classpath.
 * <p>
 * Tasks of one build share a class loader per classpath. Gradle closes this
 * service when the build finishes, which closes the class loaders so that the
 * daemon releases their jar files (Windows file locks) and a later build picks
 * up rebuilt jars.
 */
public abstract class ClassLoaderCacheService
		implements BuildService<BuildServiceParameters.None>, AutoCloseable {

	public static final String NAME = "sqlappClassLoaderCache";

	private static final Logger LOGGER = Logging.getLogger(ClassLoaderCacheService.class);

	private final Map<String, URLClassLoader> classLoaders = new ConcurrentHashMap<>();

	private volatile boolean closed;

	/**
	 * Registers the service for the build, or returns the registered one.
	 *
	 * @param gradle the build
	 * @return the service provider
	 */
	public static Provider<ClassLoaderCacheService> register(final Gradle gradle) {
		return gradle.getSharedServices().registerIfAbsent(NAME, ClassLoaderCacheService.class, spec -> {
		});
	}

	/**
	 * Returns the class loader for the classpath, creating it on first use in
	 * this build. A file whose size or modification time changed gets a new
	 * class loader.
	 *
	 * @param files runtime classpath in classpath order
	 * @return the class loader
	 */
	public ClassLoader getClassLoader(final Collection<File> files) {
		if (closed) {
			throw new IllegalStateException("The sqlapp class loader cache has already been closed.");
		}
		return classLoaders.computeIfAbsent(key(files), k -> createClassLoader(files));
	}

	static String key(final Collection<File> files) {
		return files.stream().map(f -> f.getAbsolutePath() + "|" + f.lastModified() + "|" + f.length())
				.collect(Collectors.joining(File.pathSeparator));
	}

	/**
	 * Creates a class loader for the classpath, keeping classpath order. The
	 * sqlapp-core and sqlapp-command jars are left to the plugin class loader.
	 *
	 * @param files runtime classpath in classpath order
	 * @return a new class loader; the caller must close it
	 */
	public static URLClassLoader createClassLoader(final Collection<File> files) {
		final URL[] urls = files.stream().filter(f -> !JarNameUtils.isTarget(f)).map(f -> {
			try {
				return f.toURI().toURL();
			} catch (final MalformedURLException e) {
				throw new UncheckedIOException(e);
			}
		}).toArray(URL[]::new);
		return new URLClassLoader(urls, ClassLoaderCacheService.class.getClassLoader());
	}

	@Override
	public void close() {
		closed = true;
		for (final URLClassLoader classLoader : classLoaders.values()) {
			// DialectResolver lives in the plugin class loader and outlives the build.
			DialectResolver.release(classLoader);
			try {
				classLoader.close();
			} catch (final IOException e) {
				LOGGER.warn("Failed to close a sqlapp task class loader: {}", e.toString());
			}
		}
		classLoaders.clear();
	}
}
