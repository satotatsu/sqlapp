/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import javax.swing.SwingUtilities;

import com.sqlapp.data.db.command.driver.JdbcDriverCatalog;

/**
 * Explains a JDBC URL that no bundled, downloaded or added driver accepts, and
 * offers once per URL type to open the driver download. Nothing is downloaded
 * without the user's choice.
 */
final class MissingJdbcDriver {
	private static volatile Consumer<JdbcDriverCatalog.Entry> guide;
	private static final Set<String> offered = ConcurrentHashMap.newKeySet();

	private MissingJdbcDriver() {
	}

	/** Thrown before connecting when no driver accepts the URL. */
	static final class Missing extends IllegalArgumentException {
		private static final long serialVersionUID = 1L;
		private final transient JdbcDriverCatalog.Entry entry;

		Missing(final String message, final JdbcDriverCatalog.Entry entry) {
			super(message);
			this.entry = entry;
		}

		/** The catalog driver to download, or null. */
		JdbcDriverCatalog.Entry entry() {
			return entry;
		}
	}

	/** Shows the download offer on the event thread; null removes it. */
	static void install(final Consumer<JdbcDriverCatalog.Entry> offer) {
		guide = offer;
		offered.clear();
	}

	/**
	 * Checks a URL that has no explicit driver class and no added driver.
	 *
	 * @throws Missing when no registered driver accepts it
	 */
	static void check(final String jdbcUrl) {
		try {
			DriverManager.getDriver(jdbcUrl);
			return;
		} catch (final SQLException absent) {
			/* Explained below. */
		}
		final var entry = JdbcDriverCatalog.forUrl(jdbcUrl).orElse(null);
		if (entry == null)
			throw new Missing("No JDBC driver accepts this URL. In Tools › JDBC drivers, download the driver by its"
					+ " Maven coordinate or load its JAR files.", null);
		final var offer = guide;
		if (offer != null && offered.add(entry.urlPrefix()))
			SwingUtilities.invokeLater(() -> offer.accept(entry));
		throw new Missing("No JDBC driver for " + entry.urlPrefix() + " URLs. Download " + entry.name() + " ("
				+ entry.groupId() + ":" + entry.artifactId() + ") in Tools › JDBC drivers.", entry);
	}
}
