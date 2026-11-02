/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.virtica.sql;

import java.util.List;
import java.util.Map;
import com.sqlapp.data.db.dialect.virtica.util.VirticaSqlBuilder;
import com.sqlapp.data.db.sql.*;
import com.sqlapp.data.schemas.*;

/**
 * Explicit rename-only ALTER path; physical design replacement is never
 * inferred.
 */
public class VirticaAlterProjectionFactory extends AbstractAlterNamedObjectFactory<VirticaSqlBuilder> {
	@Override
	public List<SqlOperation> createSql(AbstractNamedObject<?> object) {
		throw new IllegalArgumentException("Vertica projection ALTER requires an original/target difference");
	}

	@Override
	protected List<SqlOperation> doCreateDiffSql(DbObjectDifference difference, Map<String, Difference<?>> changes) {
		if (changes.isEmpty())
			return List.of();
		if (changes.size() != 1 || !changes.containsKey(SchemaProperties.NAME.getLabel()))
			throw new UnsupportedOperationException(
					"Vertica projection ALTER supports name changes only; explicit physical redesign is required for: "
							+ changes.keySet());
		Projection original = (Projection) difference.getOriginal();
		Projection target = (Projection) difference.getTarget();
		if (target.getName() == null || target.getName().isBlank())
			throw new IllegalArgumentException("Vertica projection RENAME requires a target name");
		var builder = createSqlBuilder();
		builder.alter().space()._add("PROJECTION ").name(original, getOptions().isDecorateSchemaName()).space()
				._add("RENAME TO ").name(target.getName());
		var result = new java.util.ArrayList<SqlOperation>();
		addSql(result, createOperation(builder.toString(), SqlType.ALTER, original, target));
		return result;
	}
}
