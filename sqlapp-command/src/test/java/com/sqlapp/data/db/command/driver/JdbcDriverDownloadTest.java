/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.driver;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Driver download against local {@code file:} repositories; no network.
 */
class JdbcDriverDownloadTest {
	@TempDir
	Path directory;

	private Path repository;

	private Path repo(final String name) throws IOException {
		repository = Files.createDirectories(directory.resolve(name));
		return repository;
	}

	private void publish(final String path, final byte[] content, final String algorithm) throws Exception {
		final Path file = repository.resolve(path);
		Files.createDirectories(file.getParent());
		Files.write(file, content);
		if (algorithm != null) {
			final String extension = switch (algorithm) {
			case "SHA-256" -> ".sha256";
			case "SHA-512" -> ".sha512";
			default -> ".sha1";
			};
			Files.writeString(file.resolveSibling(file.getFileName() + extension),
					HexFormat.of().formatHex(MessageDigest.getInstance(algorithm).digest(content)) + "  "
							+ file.getFileName());
		}
	}

	private void pom(final String groupId, final String artifactId, final String version, final String body)
			throws Exception {
		final var coordinate = new MavenCoordinate(groupId, artifactId, version).pom();
		publish(coordinate.path(),
				("<?xml version=\"1.0\"?>\n<project xmlns=\"http://maven.apache.org/POM/4.0.0\">"
						+ "<modelVersion>4.0.0</modelVersion>" + body + "</project>").getBytes(StandardCharsets.UTF_8),
				"SHA-1");
	}

	private void jar(final String groupId, final String artifactId, final String version) throws Exception {
		publish(new MavenCoordinate(groupId, artifactId, version).path(),
				(groupId + ":" + artifactId + ":" + version).getBytes(StandardCharsets.UTF_8), "SHA-256");
	}

	private static String dep(final String groupId, final String artifactId, final String version, final String extra) {
		return "<dependency><groupId>" + groupId + "</groupId><artifactId>" + artifactId + "</artifactId>"
				+ (version == null ? "" : "<version>" + version + "</version>") + extra + "</dependency>";
	}

	/**
	 * A driver with a parent, a BOM, properties, scopes, optional and exclusions.
	 */
	private void driverRepository() throws Exception {
		repo("central");
		pom("org.example", "parent", "1", "<groupId>org.example</groupId><artifactId>parent</artifactId>"
				+ "<version>1</version><packaging>pom</packaging><properties><util.version>2.0</util.version></properties>"
				+ "<dependencyManagement><dependencies>"
				+ dep("org.example", "bom", "1", "<type>pom</type><scope>import</scope>")
				+ "</dependencies></dependencyManagement>");
		pom("org.example", "bom", "1",
				"<groupId>org.example</groupId><artifactId>bom</artifactId><version>1</version>"
						+ "<packaging>pom</packaging><dependencyManagement><dependencies>"
						+ dep("org.example", "logging", "3.1", "") + "</dependencies></dependencyManagement>");
		pom("org.example", "driver", "1.5",
				"<parent><groupId>org.example</groupId><artifactId>parent</artifactId>"
						+ "<version>1</version></parent><artifactId>driver</artifactId><dependencies>"
						+ dep("org.example", "util", "${util.version}",
								"<exclusions><exclusion><groupId>org.excluded</groupId>"
										+ "<artifactId>*</artifactId></exclusion></exclusions>")
						+ dep("org.example", "logging", null, "<scope>runtime</scope>")
						+ dep("org.example", "metrics", "1", "<optional>true</optional>")
						+ dep("org.junit", "junit", "5", "<scope>test</scope>")
						+ dep("org.example", "api", "1", "<scope>provided</scope>") + "</dependencies>");
		jar("org.example", "driver", "1.5");
		pom("org.example", "util", "2.0",
				"<groupId>org.example</groupId><artifactId>util</artifactId><version>2.0</version>" + "<dependencies>"
						+ dep("org.excluded", "heavy", "1", "") + dep("org.example", "logging", "1.0", "")
						+ dep("org.example", "core", "${project.version}", "") + "</dependencies>");
		jar("org.example", "util", "2.0");
		pom("org.example", "core", "2.0",
				"<groupId>org.example</groupId><artifactId>core</artifactId><version>2.0</version>");
		jar("org.example", "core", "2.0");
		pom("org.example", "logging", "3.1",
				"<groupId>org.example</groupId><artifactId>logging</artifactId><version>3.1</version>");
		jar("org.example", "logging", "3.1");
	}

	private MavenRepositoryClient client(final Path... repositories) {
		final var uris = new ArrayList<URI>();
		for (final Path path : repositories)
			uris.add(path.toUri());
		return new MavenRepositoryClient(uris);
	}

	@Test
	void resolvesRuntimeDependenciesLikeMaven() throws Exception {
		driverRepository();
		final var lines = new ArrayList<String>();
		final var resolved = new MavenDependencyResolver(client(repository))
				.resolve(MavenCoordinate.parse("org.example:driver:1.5"), lines::add);
		assertEquals(
				List.of("org.example:driver:1.5", "org.example:util:2.0", "org.example:logging:3.1",
						"org.example:core:2.0"),
				resolved.stream().map(MavenCoordinate::toString).toList(),
				"parent properties, BOM version, nearest wins, no test/provided/optional, exclusions applied");
		assertTrue(lines.stream().anyMatch(line -> line.contains("org.example:bom:1")));
	}

	@Test
	void storeInstallsVerifiedJarsAndRemembersThemForTheNextStart() throws Exception {
		driverRepository();
		final var store = new JdbcDriverStore(directory.resolve("home"));
		assertEquals(List.of(MavenRepositoryClient.MAVEN_CENTRAL), store.repositories());
		store.setRepositories(List.of(repository.toUri()));
		final var installed = store.install(MavenCoordinate.parse("org.example:driver:1.5"), "org.example.Driver",
				store.client(), null);
		assertEquals(4, installed.files().size());
		for (final Path file : installed.files()) {
			assertTrue(Files.isRegularFile(file));
			assertTrue(file.startsWith(store.home().resolve("repository")));
		}
		final var reloaded = new JdbcDriverStore(directory.resolve("home"));
		assertEquals(List.of(installed), reloaded.installed());
		assertEquals(List.of(repository.toUri()), reloaded.repositories());

		// Another version of the same module replaces the entry.
		pom("org.example", "driver", "1.6",
				"<groupId>org.example</groupId><artifactId>driver</artifactId><version>1.6</version>");
		jar("org.example", "driver", "1.6");
		final var newer = reloaded.install(MavenCoordinate.parse("org.example:driver:1.6"), null, reloaded.client(),
				null);
		assertEquals(List.of(newer), reloaded.installed());
		assertNull(newer.driverClass(), "service discovery");
		assertTrue(reloaded.remove(newer.coordinate()).isEmpty());
		assertFalse(Files.exists(newer.files().getFirst()));
		assertEquals(List.of(), reloaded.installed());
	}

	@Test
	void rejectsTamperedOrUnverifiableFiles() throws Exception {
		driverRepository();
		Files.writeString(repository.resolve(new MavenCoordinate("org.example", "core", "2.0").path()), "tampered");
		final var mismatch = assertThrows(IOException.class, () -> new JdbcDriverStore(directory.resolve("home"))
				.install(MavenCoordinate.parse("org.example:driver:1.5"), null, client(repository), null));
		assertTrue(mismatch.getMessage().startsWith("Checksum mismatch"), mismatch.getMessage());
		publish("org/example/plain/1/plain-1.jar", new byte[] { 1 }, null);
		final var missing = assertThrows(IOException.class,
				() -> client(repository).download("org/example/plain/1/plain-1.jar", 10));
		assertTrue(missing.getMessage().startsWith("No .sha256"), missing.getMessage());
		assertTrue(new JdbcDriverStore(directory.resolve("home")).installed().isEmpty(), "nothing recorded");
	}

	@Test
	void triesRepositoriesInOrderAndListsVersionsNewestFirst() throws Exception {
		final Path empty = repo("internal");
		driverRepository();
		publish("org/example/driver/maven-metadata.xml",
				("<metadata><groupId>org.example</groupId><artifactId>driver</artifactId><versioning>"
						+ "<release>1.5</release><versions><version>1.0</version><version>1.5</version></versions>"
						+ "</versioning></metadata>").getBytes(StandardCharsets.UTF_8),
				null);
		final var client = client(empty, repository);
		final var versions = client.versions("org.example", "driver");
		assertEquals(List.of("1.5", "1.0"), versions.versions());
		assertEquals("1.5", versions.release());
		final var download = client.download(new MavenCoordinate("org.example", "core", "2.0").path(), 100);
		assertTrue(Path.of(download.source()).startsWith(repository), download.source().toString());
		assertNull(client.download("org/example/none/1/none-1.jar", 100));
	}

	@Test
	void rejectsUnsafeCoordinatesAndRepositoryUrls() {
		for (final String bad : new String[] { "../x:y:1", "a:b/../c:1", "a", "a:b:1:c:d", "a..b:c:1", "a:b:1 0" })
			assertThrows(IllegalArgumentException.class, () -> MavenCoordinate.parse(bad), bad);
		assertEquals("a.b:c:1:linux", MavenCoordinate.parse(" a.b:c:1:linux ").toString());
		for (final String bad : new String[] { "ftp://repo/", "https://user:secret@repo/", "repo/maven2",
				"https://repo/?token=x" })
			assertThrows(IllegalArgumentException.class, () -> MavenRepositoryClient.normalize(URI.create(bad)), bad);
		final var secret = assertThrows(IllegalArgumentException.class,
				() -> MavenRepositoryClient.normalize(URI.create("https://user:secret@repo/")));
		assertFalse(secret.getMessage().contains("secret"));
		assertEquals(URI.create("https://repo.example/maven2/"),
				MavenRepositoryClient.normalize(URI.create("https://repo.example/maven2")));
	}

	@Test
	void catalogFindsDriversByUrl() {
		assertEquals("Oracle", JdbcDriverCatalog.forUrl("jdbc:oracle:thin:@//h:1521/x").orElseThrow().name());
		assertTrue(JdbcDriverCatalog.forUrl("jdbc:postgresql://h/db").isEmpty(), "bundled drivers are not listed");
	}

	@Test
	void reportsBrokenRegistryInsteadOfIgnoringIt() throws Exception {
		final Path home = Files.createDirectories(directory.resolve("home"));
		Files.writeString(home.resolve(JdbcDriverStore.DRIVERS),
				"drivers:\n  - coordinate: a:b:1\n    files: [../../outside.jar]\n");
		final var failure = assertThrows(IllegalArgumentException.class, () -> new JdbcDriverStore(home).installed());
		assertTrue(failure.getMessage().contains("outside"), failure.getMessage());
	}
}
