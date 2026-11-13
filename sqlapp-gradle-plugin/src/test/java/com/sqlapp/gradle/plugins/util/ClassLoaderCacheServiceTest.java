/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.gradle.plugins.util;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.sqlapp.data.db.dialect.DialectResolver;

class ClassLoaderCacheServiceTest {

	private static final String RESOURCE = "sqlapp-class-loader-cache-test.txt";

	@TempDir
	Path tempDir;

	private static ClassLoaderCacheService newService() {
		final Project project = ProjectBuilder.builder().build();
		return ClassLoaderCacheService.register(project.getGradle()).get();
	}

	private static File writeJar(final Path path, final String content) throws IOException {
		try (OutputStream out = Files.newOutputStream(path); JarOutputStream jar = new JarOutputStream(out)) {
			jar.putNextEntry(new JarEntry(RESOURCE));
			jar.write(content.getBytes(StandardCharsets.UTF_8));
			jar.closeEntry();
		}
		return path.toFile();
	}

	@Test
	void sharesClassLoaderForSameClasspath() throws IOException {
		final ClassLoaderCacheService service = newService();
		try {
			final File jar = writeJar(tempDir.resolve("a.jar"), "a");
			assertSame(service.getClassLoader(List.of(jar)), service.getClassLoader(List.of(jar)));
		} finally {
			service.close();
		}
	}

	@Test
	void changedJarGetsNewClassLoader() throws IOException {
		final ClassLoaderCacheService service = newService();
		try {
			final File jar = writeJar(tempDir.resolve("a.jar"), "a");
			final ClassLoader before = service.getClassLoader(List.of(jar));
			writeJar(tempDir.resolve("a.jar"), "changed content");
			assertNotSame(before, service.getClassLoader(List.of(jar)));
		} finally {
			service.close();
		}
	}

	@Test
	void keepsClasspathOrderAndLeavesSqlappCoreToPlugin() throws IOException {
		final File second = writeJar(tempDir.resolve("second.jar"), "2");
		final File core = writeJar(tempDir.resolve("sqlapp-core-0.80.0.jar"), "core");
		final File first = writeJar(tempDir.resolve("first.jar"), "1");
		try (URLClassLoader loader = ClassLoaderCacheService.createClassLoader(List.of(second, core, first))) {
			assertArrayEquals(new URL[] { second.toURI().toURL(), first.toURI().toURL() }, loader.getURLs());
		}
	}

	@Test
	void closeClosesClassLoadersAndReleasesDialectResolver() throws IOException {
		final ClassLoaderCacheService service = newService();
		final File jar = writeJar(tempDir.resolve("a.jar"), "a");
		final ClassLoader loader = service.getClassLoader(List.of(jar));
		assertNotNull(loader.getResource(RESOURCE));
		final DialectResolver resolver = DialectResolver.getInstance(loader);
		service.close();
		try {
			assertNull(loader.getResource(RESOURCE));
			assertNotSame(resolver, DialectResolver.getInstance(loader));
			assertThrows(IllegalStateException.class, () -> service.getClassLoader(List.of(jar)));
		} finally {
			DialectResolver.release(loader);
		}
	}
}
