/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.List;
import java.util.jar.*;
import javax.tools.ToolProvider;

/**
 * A driver absent from the application classpath, with a separate required JAR.
 */
final class JdbcDriverJarFixture {
	final Path driver;
	final Path dependency;
	final Path second;
	final Path secondShared;
	final Path explicit;
	final Path initialized;

	JdbcDriverJarFixture(final Path root) throws Exception {
		final Path source = Files.createDirectories(root.resolve("fixture-source/fixture"));
		final Path classes = Files.createDirectories(root.resolve("fixture-classes"));
		initialized = root.resolve("driver-initializations.txt");
		Files.writeString(source.resolve("Prefix.java"),
				"package fixture; public class Prefix { public static String value() { return \"jdbc:gui-added:\"; } }");
		Files.writeString(source.resolve("ChildDriver.java"),
				"""
						package fixture;
						public class ChildDriver extends org.hsqldb.jdbc.JDBCDriver {
						 static {
						  try { java.nio.file.Files.writeString(java.nio.file.Path.of("%s"), "initialized\\n",
						    java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND); }
						  catch(java.io.IOException e) { throw new ExceptionInInitializerError(e); }
						 }
						 private final String prefix = Prefix.value();
						 public ChildDriver() {
						  if (javax.swing.SwingUtilities.isEventDispatchThread()) throw new IllegalStateException("Driver initialized on EDT");
						  if (Thread.currentThread().getContextClassLoader() != getClass().getClassLoader()) throw new IllegalStateException("Missing driver context loader");
						 }
						 @Override public boolean acceptsURL(String url) { return url != null && url.startsWith(prefix); }
						 @Override public java.sql.Connection connect(String url, java.util.Properties props) throws java.sql.SQLException {
						  if (!acceptsURL(url)) return null;
						  if (Thread.currentThread().getContextClassLoader() != getClass().getClassLoader()) throw new java.sql.SQLException("Missing connection context loader");
						  return super.connect("jdbc:hsqldb:mem:" + url.substring(prefix.length()), props);
						 }
						}
						"""
						.formatted(initialized.toString().replace("\\", "\\\\").replace("\"", "\\\"")));
		Files.writeString(source.resolve("SecondDriver.java"),
				"package fixture; public class SecondDriver extends ChildDriver { }");
		final String hsql = Path.of(
				Class.forName("org.hsqldb.jdbc.JDBCDriver").getProtectionDomain().getCodeSource().getLocation().toURI())
				.toString();
		assertEquals(0,
				ToolProvider.getSystemJavaCompiler().run(null, null, null, "-encoding", "UTF-8", "-proc:none",
						"-classpath", hsql, "-d", classes.toString(), source.resolve("Prefix.java").toString(),
						source.resolve("ChildDriver.java").toString(), source.resolve("SecondDriver.java").toString()));
		driver = jar(root.resolve("driver.jar"), classes, List.of("fixture/ChildDriver.class"), "fixture.ChildDriver");
		dependency = jar(root.resolve("dependency.jar"), classes, List.of("fixture/Prefix.class"), null);
		second = jar(root.resolve("second.jar"), classes,
				List.of("fixture/SecondDriver.class", "fixture/ChildDriver.class", "fixture/Prefix.class"),
				"fixture.SecondDriver");
		secondShared = jar(root.resolve("second-shared.jar"), classes,
				List.of("fixture/SecondDriver.class", "fixture/ChildDriver.class"), "fixture.SecondDriver");
		explicit = jar(root.resolve("explicit.jar"), classes,
				List.of("fixture/ChildDriver.class", "fixture/Prefix.class"), null);
	}

	private static Path jar(final Path file, final Path classes, final List<String> names, final String service)
			throws Exception {
		try (var output = new JarOutputStream(Files.newOutputStream(file))) {
			for (final String name : names) {
				output.putNextEntry(new JarEntry(name));
				Files.copy(classes.resolve(name), output);
				output.closeEntry();
			}
			if (service != null) {
				output.putNextEntry(new JarEntry("META-INF/services/java.sql.Driver"));
				output.write(service.getBytes(java.nio.charset.StandardCharsets.UTF_8));
				output.closeEntry();
			}
		}
		return file;
	}
}
