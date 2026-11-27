/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import java.net.IDN;
import java.net.InetAddress;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Pure URL construction for ordinary single-server connections; never connects.
 */
final class JdbcUrlBuilder {
	enum Database {
		POSTGRESQL("PostgreSQL", "jdbc:postgresql:", 5432), MYSQL("MySQL", "jdbc:mysql:", 3306),
		SQL_SERVER("SQL Server", "jdbc:sqlserver:", 1433),
		ORACLE_SERVICE("Oracle (service name)", "jdbc:oracle:thin:", 1521);

		final String label;
		final String prefix;
		final int port;

		Database(String label, String prefix, int port) {
			this.label = label;
			this.prefix = prefix;
			this.port = port;
		}

		@Override
		public String toString() {
			return label;
		}
	}

	static String build(Database type, String host, String port, String database) {
		if (type == null)
			throw new IllegalArgumentException("Choose a database type.");
		final String server = host(host);
		final String number = port == null ? "" : port.trim();
		if (!number.matches("[0-9]{1,5}") || Integer.parseInt(number) < 1 || Integer.parseInt(number) > 65535)
			throw new IllegalArgumentException("Enter a port from 1 to 65535.");
		if (database == null || database.isBlank())
			throw new IllegalArgumentException(
					type == Database.ORACLE_SERVICE ? "Enter the Oracle service name." : "Enter the database name.");
		if (database.codePoints().anyMatch(Character::isISOControl))
			throw new IllegalArgumentException("The database or service name cannot contain control characters.");
		if (!StandardCharsets.UTF_8.newEncoder().canEncode(database))
			throw new IllegalArgumentException("The database or service name contains invalid Unicode characters.");
		final String address = server + ":" + Integer.parseInt(number);
		return switch (type) {
		case POSTGRESQL, MYSQL -> type.prefix + "//" + address + "/"
				+ URLEncoder.encode(database, StandardCharsets.UTF_8).replace("+", "%20").replace("*", "%2A");
		case SQL_SERVER -> {
			if (database.contains("{") || database.contains("}"))
				throw new IllegalArgumentException(
						"For database names containing braces, enter the JDBC URL directly.");
			final String target = server.startsWith("[")
					? ";serverName=" + server.substring(1, server.length() - 1) + ";portNumber="
							+ Integer.parseInt(number)
					: address;
			yield type.prefix + "//" + target + ";databaseName={" + database + "}";
		}
		case ORACLE_SERVICE -> {
			if (!database.matches("[A-Za-z0-9_.$-]+"))
				throw new IllegalArgumentException(
						"Use a simple Oracle service name, or enter a custom JDBC URL directly.");
			yield type.prefix + "@//" + address + "/" + database;
		}
		};
	}

	private static String host(String value) {
		String host = value == null ? "" : value.trim();
		if (host.isEmpty())
			throw new IllegalArgumentException("Enter the server host name or IP address.");
		try {
			if (host.startsWith("[") && host.endsWith("]"))
				host = host.substring(1, host.length() - 1);
			if (host.contains(":")) {
				if (host.contains("%"))
					throw new IllegalArgumentException();
				InetAddress.ofLiteral(host); // Literal parsing only; never resolve a DNS name.
				return "[" + host + "]";
			}
			return IDN.toASCII(host, IDN.USE_STD3_ASCII_RULES);
		} catch (IllegalArgumentException failure) {
			throw new IllegalArgumentException(
					"Enter only a host name or IP address, without a URL, port, credentials or properties.");
		}
	}
}
