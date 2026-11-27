/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.sybase.sql;

import com.sqlapp.data.db.sql.AbstractCreateNamedObjectFactory;
import com.sqlapp.data.db.dialect.sybase.metadata.SybaseSourceText;
import com.sqlapp.data.schemas.AbstractNamedObject;
import com.sqlapp.data.schemas.Trigger;
import com.sqlapp.util.AbstractSqlBuilder;

/**
 * Recreates complete native DDL without requiring duplicate argument models.
 */
public class SybaseCreateDefinitionFactory
		extends AbstractCreateNamedObjectFactory<AbstractNamedObject<?>, AbstractSqlBuilder<?>> {
	@Override
	protected void addCreateObject(AbstractNamedObject<?> object, AbstractSqlBuilder<?> builder) {
		String text = String.join("\n", object.getDefinition());
		if (text.isBlank() && object instanceof Trigger trigger)
			text = String.join("\n", trigger.getStatement());
		final String kind = object instanceof Trigger ? "TRIGGER"
				: object instanceof com.sqlapp.data.schemas.Procedure ? "(?:PROCEDURE|PROC)" : "FUNCTION";
		if (!java.util.regex.Pattern.compile("(?is)^\\s*CREATE(?:\\s+OR\\s+REPLACE)?\\s+" + kind + "\\b")
				.matcher(SybaseSourceText.mask(text)).find())
			throw new IllegalArgumentException("Complete SAP ASE CREATE definition is required: " + object.getName());
		builder._add(text);
	}
}
