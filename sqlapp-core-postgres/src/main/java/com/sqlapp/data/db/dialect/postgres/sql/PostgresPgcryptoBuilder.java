/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.postgres.sql;

import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.postgres.DialectHolder;

/**
 * PostgreSQL 18 pgcrypto expressions. Expression arguments are trusted SQL,
 * including JDBC placeholders; never concatenate passwords or keys into them.
 * Execution requires pgcrypto and an OpenSSL-enabled server.
 */
public class PostgresPgcryptoBuilder {
	private final Dialect dialect;

	public PostgresPgcryptoBuilder(Dialect dialect) {
		this.dialect = Objects.requireNonNull(dialect, "dialect");
	}

	/** Uses the server's default iteration count. */
	public String genSalt(String algorithm) {
		return "gen_salt('" + algorithm(algorithm) + "')";
	}

	public String genSalt(String algorithm, int iterations) {
		String normalized = algorithm(algorithm);
		if (iterations < 1000 || iterations > 999999999) {
			throw new IllegalArgumentException("SHA-2 crypt iterations must be between 1000 and 999999999.");
		}
		return "gen_salt('" + normalized + "', " + iterations + ")";
	}

	/** Hashes a password using a caller-selected salt or stored hash. */
	public String crypt(String passwordExpression, String saltExpression) {
		checkVersion();
		return "crypt(" + expression(passwordExpression) + ", " + expression(saltExpression) + ")";
	}

	public String fipsMode() {
		checkVersion();
		return "fips_mode()";
	}

	/** Raw CFB encryption provides no integrity checking. Supply an explicit IV. */
	public String encryptCfb(String dataExpression, String keyExpression, String ivExpression, String algorithm) {
		return cfb("encrypt_iv", dataExpression, keyExpression, ivExpression, algorithm);
	}

	public String decryptCfb(String dataExpression, String keyExpression, String ivExpression, String algorithm) {
		return cfb("decrypt_iv", dataExpression, keyExpression, ivExpression, algorithm);
	}

	private String cfb(String function, String data, String key, String iv, String algorithm) {
		checkVersion();
		String normalized = expression(algorithm).toLowerCase(Locale.ROOT);
		if (!Set.of("aes", "bf").contains(normalized)) {
			throw new IllegalArgumentException("CFB algorithm must be aes or bf.");
		}
		return function + "(" + expression(data) + ", " + expression(key) + ", " + expression(iv)
				+ ", '" + normalized + "-cfb')";
	}

	private String algorithm(String algorithm) {
		checkVersion();
		String normalized = expression(algorithm).toLowerCase(Locale.ROOT);
		if (!Set.of("sha256crypt", "sha512crypt").contains(normalized)) {
			throw new IllegalArgumentException("Algorithm must be sha256crypt or sha512crypt.");
		}
		return normalized;
	}

	private String expression(String value) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException("SQL expression or algorithm must not be blank.");
		}
		return value;
	}

	private void checkVersion() {
		if (dialect.compareTo(DialectHolder.postgreSQL180) < 0) {
			throw new IllegalArgumentException("These pgcrypto operations require PostgreSQL 18 or later.");
		}
	}
}
