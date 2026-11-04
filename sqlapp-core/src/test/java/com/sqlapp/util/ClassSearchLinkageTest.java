/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.util;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.net.*;
import java.util.jar.*;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ClassSearchLinkageTest {
	@TempDir Path directory;

	@ParameterizedTest
	@ValueSource(booleans = { false, true })
	void normalJarDiscoveryRetainsPackageNamesWithoutInitializingClasses(boolean recursive) throws Exception {
		var source = directory.resolve("Healthy.java");
		Files.writeString(source, "package fixture; public class Healthy { static { if (Boolean.parseBoolean(\"true\")) throw new IllegalStateException(\"must not initialize\"); } }\n");
		assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null,
				"-encoding", "UTF-8", "-d", directory.toString(), source.toString()));
		var jar = directory.resolve("healthy.jar");
		try (var output = new JarOutputStream(Files.newOutputStream(jar))) {
			output.putNextEntry(new JarEntry("fixture/Healthy.class"));
			Files.copy(directory.resolve("fixture/Healthy.class"), output);
			output.closeEntry();
		}
		try (var loader = new URLClassLoader(new URL[] { jar.toUri().toURL() }, ClassLoader.getPlatformClassLoader())) {
			var searcher = new JarClassSearcher();
			searcher.setClassLoader(loader);
			var found = searcher.search("fixture", URI.create("jar:" + jar.toUri() + "!/").toURL(), recursive);
			assertEquals(1, found.size());
			assertEquals("fixture.Healthy", found.getFirst().getName());
		}
	}

	@ParameterizedTest
	@ValueSource(strings = { "FILE", "DIRECTORY", "JAR", "RECURSIVE_JAR" })
	void incompatibleCandidateRetainsItsIdentityAndOriginalErrorInsteadOfBeingSkipped(String mode) throws Exception {
		var parent = directory.resolve("Parent.java");
		var broken = directory.resolve("Broken.java");
		Files.writeString(parent, "package fixture; public class Parent {}\n");
		Files.writeString(broken, "package fixture; public class Broken extends Parent {}\n");
		var compiler = ToolProvider.getSystemJavaCompiler();
		assertNotNull(compiler);
		assertEquals(0, compiler.run(null, null, null, "-encoding", "UTF-8", "-d", directory.toString(), parent.toString(), broken.toString()));
		Files.writeString(parent, "package fixture; public final class Parent {}\n");
		assertEquals(0, compiler.run(null, null, null, "-encoding", "UTF-8", "-d", directory.toString(), parent.toString()));
		URL root = directory.toUri().toURL();
		if (mode.contains("JAR")) {
			var jar = directory.resolve("fixture.jar");
			try (var output = new JarOutputStream(Files.newOutputStream(jar))) {
				for (var name : new String[] { "Parent", "Broken" }) {
					output.putNextEntry(new JarEntry("fixture/" + name + ".class"));
					Files.copy(directory.resolve("fixture/" + name + ".class"), output);
					output.closeEntry();
				}
			}
			root = jar.toUri().toURL();
		}
		try (var loader = new URLClassLoader(new URL[] { root }, ClassLoader.getPlatformClassLoader())) {
			AbstractClassSearcher searcher = mode.contains("JAR") ? new JarClassSearcher() : new FileClassSearcher();
			searcher.setClassLoader(loader);
			searcher.setFilter(type -> false);
			URL input = mode.contains("JAR") ? URI.create("jar:" + root + "!/").toURL()
					: directory.resolve(mode.equals("FILE") ? "fixture/Broken.class" : "fixture").toUri().toURL();
			var failure = assertThrows(ClassSearchLinkageError.class,
					() -> searcher.search("fixture", input, mode.equals("RECURSIVE_JAR")));
			assertEquals("fixture.Broken", failure.getClassName());
			assertInstanceOf(IncompatibleClassChangeError.class, failure.getCause());
			assertTrue(failure.getCause().getMessage().contains("final"));
			assertTrue(failure.getMessage().contains("fixture.Broken"));
			assertEquals("fixture.Parent", loader.loadClass("fixture.Parent").getName());
		}
	}
}
