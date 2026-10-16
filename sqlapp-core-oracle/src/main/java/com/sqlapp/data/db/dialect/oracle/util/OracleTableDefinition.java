/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.oracle.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;
import com.sqlapp.data.schemas.Table;

/** Complete IOT DDL; modeled components remain supplemental metadata. */
public final class OracleTableDefinition {
	private OracleTableDefinition() { }
	public static final String DEFINITION = "oracle.table.definitionBase64";
	public static final String FINGERPRINT = "oracle.table.definitionFingerprint";
	public static String fingerprint(Table table) {
		Table copy = table.clone(); copy.getSpecifics().remove(DEFINITION); copy.getSpecifics().remove(FINGERPRINT);
		// Secondary indexes are emitted separately and are not embedded in GET_DDL(TABLE).
		copy.getIndexes().clear();
		try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(copy.asXml().getBytes(StandardCharsets.UTF_8))); }
		catch (java.security.NoSuchAlgorithmException | javax.xml.stream.XMLStreamException error) { throw new IllegalStateException(error); }
	}
	public static void set(Table table, String ddl) {
		validate(ddl); table.getSpecifics().put(DEFINITION, Base64.getEncoder().encodeToString(ddl.getBytes(StandardCharsets.UTF_8)));
		table.getSpecifics().put(FINGERPRINT, fingerprint(table));
	}
	private static void validate(String ddl) {
		if (ddl == null || !ddl.matches("(?is)^\\s*CREATE\\s+TABLE\\b.*")) throw new IllegalArgumentException("Complete CREATE TABLE DDL is required");
	}
	public static String get(Table table) {
		String encoded = table.getSpecifics().get(DEFINITION); if (encoded == null) return null;
		if (!fingerprint(table).equals(table.getSpecifics().get(FINGERPRINT))) throw new IllegalArgumentException("Complete Oracle table DDL is stale; refresh the definition: " + table.getName());
		try {
			String ddl = StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(Base64.getDecoder().decode(encoded))).toString(); validate(ddl); return ddl;
		} catch (java.nio.charset.CharacterCodingException error) { throw new IllegalArgumentException("Invalid table DDL encoding", error); }
	}
}
