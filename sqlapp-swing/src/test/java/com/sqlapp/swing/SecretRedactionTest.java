/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class SecretRedactionTest {
	private static final PlanningJdbc.Spec SPEC = new PlanningJdbc.Spec("jdbc:hsqldb:mem:sample", "sa", null);

	@Test
	void longSecretsAreReplacedAnywhere() {
		assertEquals("login failed for [redacted] at [JDBC URL]", SecretRedaction
				.redact("login failed for verysecret at jdbc:hsqldb:mem:sample", SPEC, "verysecret".toCharArray()));
		assertEquals("token=x[redacted]y",
				SecretRedaction.redact("token=xverysecrety", SPEC, "verysecret".toCharArray()));
	}

	@Test
	void shortSecretsDoNotCorruptUnrelatedWords() {
		final String text = "Database rejected password=a; table data unavailable";
		assertEquals("Database rejected password=[redacted]; table data unavailable",
				SecretRedaction.redact(text, SPEC, "a".toCharArray()));
		assertEquals("Access denied for 'sa' using '[redacted]'",
				SecretRedaction.redact("Access denied for 'sa' using 'pw1'", SPEC, "pw1".toCharArray()));
	}

	@Test
	void urlEncodedSecretsAreRemoved() {
		final char[] secret = "p@ss word&1".toCharArray();
		final String redacted = SecretRedaction
				.redact("url=jdbc:x?password=p%40ss+word%261 alt=p%40ss%20word%261 raw=p@ss word&1", SPEC, secret);
		assertFalse(redacted.contains("p%40ss"), redacted);
		assertFalse(redacted.contains("p@ss"), redacted);
		assertEquals(3, redacted.split("\\[redacted]", -1).length - 1, redacted);
	}

	@Test
	void urlIsReplacedBeforeSecretsAndNullsAreTolerated() {
		final var spec = new PlanningJdbc.Spec("jdbc:x://host/db?password=verysecret", null, null);
		assertEquals("cannot open [JDBC URL]", SecretRedaction
				.redact("cannot open jdbc:x://host/db?password=verysecret", spec, "verysecret".toCharArray()));
		assertNull(SecretRedaction.redact(null, spec, "verysecret".toCharArray()));
		assertEquals("plain", SecretRedaction.redact("plain", (PlanningJdbc.Spec[]) null, (char[]) null));
		assertEquals("plain", SecretRedaction.redact("plain", new PlanningJdbc.Spec[] { null }, new char[0]));
	}
}
