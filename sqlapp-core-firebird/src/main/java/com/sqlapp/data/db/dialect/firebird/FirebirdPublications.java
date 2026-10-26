/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.firebird;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.util.JsonConverter;

/**
 * Stores publication metadata; explicit restore plans affect the whole target
 * database.
 */
public final class FirebirdPublications {
	public static final String SPECIFICS_KEY = "firebird.publication.v1";

	public enum Activation {
		DISABLED, SOURCE_STATE
	}

	public record Manifest(int format, FirebirdPublication publication) {
	}

	private FirebirdPublications() {
	}

	public static Optional<FirebirdPublication> get(Schema schema) {
		String json = schema.getSpecifics().get(SPECIFICS_KEY);
		if (json == null)
			return Optional.empty();
		var manifest = (Manifest) new JsonConverter().getObjectMapper().readerFor(Manifest.class)
				.with(tools.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
				.with(tools.jackson.databind.DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES)
				.with(tools.jackson.databind.DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES).readValue(json);
		if (manifest == null || manifest.format() != 1 || manifest.publication() == null)
			throw new IllegalArgumentException("Invalid Firebird publication manifest");
		return Optional.of(manifest.publication());
	}

	public static void set(Schema schema, FirebirdPublication publication) {
		schema.getSpecifics().put(SPECIFICS_KEY,
				new JsonConverter().toJsonString(new Manifest(1, Objects.requireNonNull(publication, "publication"))));
	}

	/**
	 * Call after creating all target tables. Leaves replication disabled. Does not
	 * execute SQL.
	 */
	public static List<String> restoreSql(Schema schema, Dialect dialect) {
		return restoreSql(schema, dialect, Activation.DISABLED);
	}

	/**
	 * Replaces the database-wide policy; schema must describe the complete target
	 * user-table set.
	 */
	public static List<String> restoreSql(Schema schema, Dialect dialect, Activation activation) {
		Objects.requireNonNull(activation, "activation");
		var source = get(schema);
		if (source.isEmpty())
			return List.of();
		if (!(dialect instanceof Firebird40))
			throw new UnsupportedOperationException("Publication restore requires Firebird 4.0 or later");
		var publication = source.get();
		var eligible = new HashSet<String>();
		for (Table table : schema.getTables()) {
			if (table.getTableType() == null && table.getSpecifics().get("firebird.external.file") == null)
				eligible.add(table.getName());
		}
		for (String name : publication.tables())
			if (!eligible.contains(name))
				throw new IllegalArgumentException("Publication table is missing or not a persistent internal table: "
						+ name + ". Supply the complete target Schema before restoring publication policy.");
		var sql = new ArrayList<String>();
		sql.add("ALTER DATABASE DISABLE PUBLICATION");
		sql.add("ALTER DATABASE EXCLUDE ALL FROM PUBLICATION");
		if (publication.autoEnable()) {
			sql.add("ALTER DATABASE INCLUDE ALL TO PUBLICATION");
			var included = new HashSet<>(publication.tables());
			eligible.stream().filter(name -> !included.contains(name)).sorted()
					.forEach(name -> sql.add("ALTER TABLE " + quote(name) + " DISABLE PUBLICATION"));
		} else {
			for (String name : publication.tables())
				sql.add("ALTER TABLE " + quote(name) + " ENABLE PUBLICATION");
		}
		if (activation == Activation.SOURCE_STATE && publication.active())
			sql.add("ALTER DATABASE ENABLE PUBLICATION");
		return List.copyOf(sql);
	}

	private static String quote(String name) {
		return "\"" + name.replace("\"", "\"\"") + "\"";
	}
}
