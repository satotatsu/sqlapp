package com.sqlapp.data.db.dialect.oracle.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import com.sqlapp.data.schemas.Event;

/**
 * Detects edits to a captured Scheduler model; this is not a script
 * authenticity signature.
 */
public final class OracleSchedulerDefinition {
	private OracleSchedulerDefinition() {
	}

	public static final String FINGERPRINT = "oracle.scheduler.definitionFingerprint";

	public static String fingerprint(Event event) {
		Event copy = event.clone();
		var retained = new LinkedHashMap<>(copy.getSpecifics());
		retained.keySet().removeIf(key -> key.equalsIgnoreCase(FINGERPRINT));
		copy.getSpecifics().clear();
		copy.getSpecifics().putAll(retained);
		try {
			return HexFormat.of().formatHex(
					MessageDigest.getInstance("SHA-256").digest(copy.asXml().getBytes(StandardCharsets.UTF_8)));
		} catch (java.security.NoSuchAlgorithmException | javax.xml.stream.XMLStreamException error) {
			throw new IllegalStateException("Cannot fingerprint Oracle Scheduler metadata", error);
		}
	}
}
