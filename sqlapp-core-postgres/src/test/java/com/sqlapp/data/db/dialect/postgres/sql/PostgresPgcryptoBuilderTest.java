package com.sqlapp.data.db.dialect.postgres.sql;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.postgres.DialectHolder;

class PostgresPgcryptoBuilderTest {
	private final PostgresPgcryptoBuilder builder = new PostgresPgcryptoBuilder(DialectHolder.postgreSQL180);

	@Test
	void createsSha2PasswordExpressionsWithoutEmbeddingSecrets() {
		assertEquals("crypt(?, gen_salt('sha256crypt', 10000))",
				builder.crypt("?", builder.genSalt("SHA256CRYPT", 10000)));
		assertEquals("gen_salt('sha512crypt')", builder.genSalt("sha512crypt"));
		assertEquals("gen_salt('sha512crypt', 1000)", builder.genSalt("sha512crypt", 1000));
		assertEquals("gen_salt('sha512crypt', 999999999)", builder.genSalt("sha512crypt", 999999999));
		assertEquals("fips_mode()", builder.fipsMode());
	}

	@Test
	void usesExplicitIvForCfb() {
		assertEquals("encrypt_iv(?, ?, ?, 'aes-cfb')", builder.encryptCfb("?", "?", "?", "AES"));
		assertEquals("decrypt_iv(payload, key, iv, 'bf-cfb')", builder.decryptCfb("payload", "key", "iv", "bf"));
		assertThrows(IllegalArgumentException.class, () -> builder.encryptCfb("?", "?", " ", "aes"));
		assertThrows(IllegalArgumentException.class, () -> builder.decryptCfb("?", "?", "?", "aes'; SELECT 1"));
	}

	@Test
	void rejectsInvalidAlgorithmsIterationsExpressionsAndOlderVersions() {
		assertThrows(IllegalArgumentException.class, () -> builder.genSalt("sha256"));
		assertThrows(IllegalArgumentException.class, () -> builder.genSalt("sha256crypt", 999));
		assertThrows(IllegalArgumentException.class, () -> builder.genSalt("sha512crypt", 1000000000));
		assertThrows(IllegalArgumentException.class, () -> builder.crypt(null, "?"));
		assertThrows(IllegalArgumentException.class, () -> builder.crypt("?", " "));
		var old = new PostgresPgcryptoBuilder(DialectHolder.postgreSQL170);
		assertThrows(IllegalArgumentException.class, () -> old.genSalt("sha256crypt"));
		assertThrows(IllegalArgumentException.class, () -> old.crypt("?", "?"));
		assertThrows(IllegalArgumentException.class, old::fipsMode);
		assertThrows(IllegalArgumentException.class, () -> old.encryptCfb("?", "?", "?", "aes"));
		assertThrows(IllegalArgumentException.class, () -> old.decryptCfb("?", "?", "?", "aes"));
	}
}
