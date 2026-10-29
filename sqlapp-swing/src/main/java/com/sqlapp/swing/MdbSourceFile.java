/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/** File selection validation only; never opens a database. */
final class MdbSourceFile {
	static final String PREFIX = "jdbc:ucanaccess://";
	static final String DRIVER = "net.ucanaccess.jdbc.UcanaccessDriver";

	private MdbSourceFile() {
	}

	static String jdbcUrl(Path file) {
		final var path = file.toAbsolutePath().normalize();
		final String name = path.getFileName() == null ? "" : path.getFileName().toString().toLowerCase(Locale.ROOT);
		if (!name.endsWith(".mdb") && !name.endsWith(".accdb"))
			throw new IllegalArgumentException("Choose an existing Access .mdb or .accdb file.");
		if (!Files.isRegularFile(path) || !Files.isReadable(path))
			throw new IllegalArgumentException("Access source file is missing or unreadable: " + path);
		if (path.toString().contains(";") || path.toString().chars().anyMatch(Character::isISOControl))
			throw new IllegalArgumentException(
					"Access file paths cannot contain semicolons or control characters. Rename or copy the file before selecting it.");
		return PREFIX + path.toString().replace('\\', '/');
	}

	static void validate(PlanningJdbc.Spec source) {
		if (source == null || !source.jdbcUrl().startsWith(PREFIX))
			throw new IllegalArgumentException("Choose an Access source file before continuing.");
		final String expected = jdbcUrl(Path.of(source.jdbcUrl().substring(PREFIX.length())));
		if (!expected.equals(source.jdbcUrl()) || !DRIVER.equals(source.driverClass()))
			throw new IllegalArgumentException("Access source settings changed. Select the file again.");
	}
}
