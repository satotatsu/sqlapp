/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-postgres.
 */
package com.sqlapp.data.db.dialect.postgres.metadata;

import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.NotNullConstraint;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.util.CommonUtils;

final class Postgres180ColumnMetadata {
	static final String CONSTRAINT_NAME = "postgres.notNullConstraintName";
	static final String NO_INHERIT = "postgres.notNullNoInherit";
	static final String VALIDATED = "postgres.notNullValidated";
	private Postgres180ColumnMetadata() {
	}

	static void applyNamedNotNull(Column column, String constraintName) {
		applyNamedNotNull(column, constraintName, false, true);
	}

	static void applyNamedNotNull(Column column, String constraintName,
			boolean noInherit, boolean validated) {
		if (!CommonUtils.isEmpty(constraintName)) {
			column.getSpecifics().put(CONSTRAINT_NAME, constraintName);
			column.getSpecifics().put(NO_INHERIT,
					Boolean.toString(noInherit));
			column.getSpecifics().put(VALIDATED,
					Boolean.toString(validated));
		}
	}

	static void moveNamedNotNullConstraints(Table table) {
		for (Column column : table.getColumns()) {
			String name = column.getSpecifics().remove(CONSTRAINT_NAME);
			if (CommonUtils.isEmpty(name)) {
				continue;
			}
			boolean noInherit = Boolean.parseBoolean(
					column.getSpecifics().remove(NO_INHERIT));
			boolean validated = Boolean.parseBoolean(
					column.getSpecifics().remove(VALIDATED));
			NotNullConstraint constraint = new NotNullConstraint(name, column)
					.setNoInherit(noInherit).setValidated(validated);
			table.getConstraints().add(constraint);
		}
	}
}
