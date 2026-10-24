/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConnectionProfileServiceTest {
	@TempDir
	Path directory;
	private final ConnectionProfileService service = new ConnectionProfileService();

	@Test
	void roundTripsUtf8AndOnlyTheFourDocumentedFields() throws Exception {
		final var profile = new ConnectionProfileService.Profile(1, "jdbc:hsqldb:mem:例", "利用者", "");
		final Path file = directory.resolve("接続.json");
		service.write(profile, file);
		assertEquals(profile, service.read(file));
		assertTrue(Files.readString(file).contains("利用者"));
		assertFalse(Files.readString(file).toLowerCase().contains("password"));
		service.write(
				new ConnectionProfileService.Profile(1, "jdbc:hsqldb:mem:other", "SA", "org.hsqldb.jdbc.JDBCDriver"),
				file);
		assertEquals("SA", service.read(file).username());
	}

	@Test
	void acceptsConciseProfileWithOptionalUsernameAndDriverOmitted() throws Exception {
		final Path file = directory.resolve("minimal.json");
		Files.writeString(file, "{\"formatVersion\":1,\"jdbcUrl\":\"jdbc:hsqldb:mem:example\"}");
		assertEquals(new ConnectionProfileService.Profile(1, "jdbc:hsqldb:mem:example", "", ""), service.read(file));
	}

	@Test
	void rejectsEmbeddedSecretsAndEncodedFormsWithoutEchoingThem() {
		for (final String url : List.of("jdbc:postgresql://host/db?password=hidden", "jdbc:sqlserver://host;PWD=hidden",
				"jdbc:oracle:thin:scott/hidden@host:1521:db", "jdbc:mysql://user:hidden@host/db",
				"jdbc:sqlserver://host;accessToken=hidden", "jdbc:postgresql://host/db?pass%77ord=hidden",
				"jdbc:postgresql://host/db?%2570assword=hidden")) {
			final var failure = assertThrows(IllegalArgumentException.class,
					() -> new ConnectionProfileService.Profile(1, url, "user", ""));
			assertFalse(failure.getMessage().contains("hidden"));
		}
		assertDoesNotThrow(
				() -> new ConnectionProfileService.Profile(1, "jdbc:oracle:thin:@//host:1521/db", "user", ""));
	}

	@Test
	void rejectsUnknownSecretFieldsDuplicatesTrailingObjectsAndWrongTypes() throws Exception {
		final String valid = "{\"formatVersion\":1,\"jdbcUrl\":\"jdbc:test\",\"username\":\"user\",\"driverClass\":\"\"}";
		final Path file = directory.resolve("profile.json");
		for (final String json : List.of(
				valid.replace("\"driverClass\":\"\"", "\"password\":\"hidden\",\"driverClass\":\"\""),
				valid.replace("\"formatVersion\":1", "\"formatVersion\":1,\"formatVersion\":1"), valid + " {}",
				valid.replace("\"formatVersion\":1", "\"formatVersion\":2"),
				valid.replace("\"formatVersion\":1", "\"formatVersion\":\"1\""),
				valid.replace("\"username\":\"user\"", "\"username\":null"), "null", "[]", "{broken hidden")) {
			Files.writeString(file, json);
			final var failure = assertThrows(IllegalArgumentException.class, () -> service.read(file));
			assertFalse(failure.getMessage().contains("hidden"));
		}
	}

	@Test
	void boundsReadsAndWritesAndPreservesExistingDestinationOnValidationFailure() throws Exception {
		final Path file = directory.resolve("profile.json");
		Files.writeString(file, "x".repeat(65_537));
		assertThrows(IllegalArgumentException.class, () -> service.read(file));
		Files.writeString(file, "original");
		final var huge = new ConnectionProfileService.Profile(1, "jdbc:hsqldb:mem:example", "x".repeat(65_536), "");
		assertThrows(IllegalArgumentException.class, () -> service.write(huge, file));
		assertEquals("original", Files.readString(file));
		assertThrows(IllegalArgumentException.class, () -> service.write(huge, directory.resolve("profile.yaml")));
	}

	@Test
	void rejectsMalformedUtf8AndUnavailableFiles() throws Exception {
		final Path file = directory.resolve("profile.json");
		Files.write(file, new byte[] { (byte) 0xc3, (byte) 0x28 });
		assertThrows(IOException.class, () -> service.read(file));
		assertThrows(IOException.class, () -> service.read(directory.resolve("missing.json")));
	}

	@Test
	void validatesVersionUrlAndControlCharacters() {
		assertThrows(IllegalArgumentException.class,
				() -> new ConnectionProfileService.Profile(2, "jdbc:test", "", ""));
		assertThrows(IllegalArgumentException.class,
				() -> new ConnectionProfileService.Profile(1, "https://host", "", ""));
		assertThrows(IllegalArgumentException.class,
				() -> new ConnectionProfileService.Profile(1, "jdbc:te\nst", "", ""));
		assertThrows(IllegalArgumentException.class,
				() -> new ConnectionProfileService.Profile(1, "jdbc:test", "u\0ser", ""));
	}
}
