/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.oracle.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HexFormat;
import com.sqlapp.data.schemas.Index;

/**
 * Complete vendor DDL with a snapshot guard against stale supplemental
 * metadata.
 */
public final class OracleIndexDefinition {
	private OracleIndexDefinition() {
	}

	public static final String DEFINITION = "oracle.index.definitionBase64";
	public static final String FINGERPRINT = "oracle.index.definitionFingerprint";
	public static final String REQUIRED = "oracle.index.definitionRequired";

	public static String fingerprint(Index index) {
		Index copy = index.clone();
		copy.getSpecifics().remove(DEFINITION);
		copy.getSpecifics().remove(FINGERPRINT);
		copy.getSpecifics().remove(REQUIRED);
		try {
			return HexFormat.of().formatHex(
					MessageDigest.getInstance("SHA-256").digest(copy.asXml().getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException | javax.xml.stream.XMLStreamException error) {
			throw new IllegalStateException(error);
		}
	}

	public static void set(Index index, String ddl) {
		if (ddl == null || !ddl.matches("(?is)^\\s*CREATE\\s+(?:(?:UNIQUE|BITMAP)\\s+)?INDEX\\b.*"))
			throw new IllegalArgumentException("Complete CREATE INDEX DDL is required: " + index.getName());
		index.getSpecifics().put(DEFINITION, Base64.getEncoder().encodeToString(ddl.getBytes(StandardCharsets.UTF_8)));
		index.getSpecifics().put(FINGERPRINT, fingerprint(index));
	}

	public static String get(Index index) {
		String encoded = index.getSpecifics().get(DEFINITION);
		if (encoded == null) {
			if (index.getSpecifics().containsKey(REQUIRED))
				throw new IllegalArgumentException(
						"Complete Oracle index DDL is unavailable; DBMS_METADATA access is required: "
								+ index.getName());
			return null;
		}
		if (!fingerprint(index).equals(index.getSpecifics().get(FINGERPRINT)))
			throw new IllegalArgumentException(
					"Complete index DDL is stale after model changes; refresh or replace its definition: "
							+ index.getName());
		try {
			String ddl = StandardCharsets.UTF_8.newDecoder()
					.decode(java.nio.ByteBuffer.wrap(Base64.getDecoder().decode(encoded))).toString();
			if (!ddl.matches("(?is)^\\s*CREATE\\s+(?:(?:UNIQUE|BITMAP)\\s+)?INDEX\\b.*"))
				throw new IllegalArgumentException("Invalid complete index DDL");
			return ddl.replaceFirst("(?is)^\\s*CREATE\\s+", "");
		} catch (java.nio.charset.CharacterCodingException error) {
			throw new IllegalArgumentException("Invalid index DDL encoding", error);
		}
	}
}
