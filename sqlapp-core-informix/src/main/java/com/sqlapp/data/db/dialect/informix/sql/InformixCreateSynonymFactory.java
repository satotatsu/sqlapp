/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.informix.sql;

import static com.sqlapp.data.db.dialect.informix.metadata.InformixSynonymReader.*;
import com.sqlapp.data.db.sql.AbstractCreateSynonymFactory;
import com.sqlapp.data.schemas.Synonym;
import com.sqlapp.util.AbstractSqlBuilder;

/**
 * Generates Informix synonym visibility and database@server:owner.object
 * targets.
 */
public class InformixCreateSynonymFactory extends AbstractCreateSynonymFactory<AbstractSqlBuilder<?>> {
	@Override
	protected void addCreateObject(final Synonym synonym, final AbstractSqlBuilder<?> builder) {
		final String type = synonym.getSpecifics().get(INFORMIX_SYNONYM_TYPE);
		final String ansi = synonym.getSpecifics().get(INFORMIX_ANSI_DATABASE);
		if (!"P".equals(type) && !"S".equals(type))
			throw new IllegalArgumentException(
					"Informix synonym requires INFORMIX_SYNONYM_TYPE=P (private) or S (public): " + synonym.getName());
		if (!"true".equals(ansi) && !"false".equals(ansi))
			throw new IllegalArgumentException(
					"Informix synonym requires INFORMIX_ANSI_DATABASE=true or false for the target database: "
							+ synonym.getName());
		if ("true".equals(ansi) && "S".equals(type))
			throw new IllegalArgumentException(
					"ANSI Informix databases do not support public synonyms: " + synonym.getName());
		require(synonym.getName(), "synonym name");
		require(synonym.getObjectName(), "target object");
		final String database = synonym.getSpecifics().get(INFORMIX_DATABASE_NAME);
		final String server = synonym.getSpecifics().get(INFORMIX_SERVER_NAME);
		if (server != null && database == null)
			throw new IllegalArgumentException("Informix synonym server requires a database name");
		if (synonym.getDbLinkName() != null)
			throw new IllegalArgumentException(
					"Informix synonyms use INFORMIX_DATABASE_NAME and INFORMIX_SERVER_NAME instead of dbLinkName");
		builder.create();
		if ("false".equals(ansi))
			builder.space()._add("P".equals(type) ? "PRIVATE" : "PUBLIC");
		builder.space()._add("SYNONYM").space();
		final String name = (getOptions().isDecorateSchemaName() && synonym.getSchemaName() != null
				? identifier(synonym.getSchemaName()) + "."
				: "") + identifier(synonym.getName());
		builder._add(name).space()._add("FOR").space();
		final StringBuilder target = new StringBuilder();
		if (database != null) {
			target.append(identifier(database));
			if (server != null)
				target.append('@').append(identifier(server));
			target.append(':');
		}
		if (synonym.getObjectSchemaName() != null)
			target.append(identifier(synonym.getObjectSchemaName())).append('.');
		builder._add(target.append(identifier(synonym.getObjectName())).toString());
	}

	private String identifier(final String name) {
		require(name, "identifier");
		return "\"" + name.replace("\"", "\"\"") + "\"";
	}

	private void require(final String value, final String label) {
		if (value == null || value.isBlank())
			throw new IllegalArgumentException("Informix synonym requires " + label);
	}
}
