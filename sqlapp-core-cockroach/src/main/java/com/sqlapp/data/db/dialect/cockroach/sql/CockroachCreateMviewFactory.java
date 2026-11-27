/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.cockroach.sql;

import java.util.List;
import com.sqlapp.data.db.dialect.postgres.util.PostgresSqlBuilder;
import com.sqlapp.data.db.dialect.postgres.sql.Postgres93CreateMviewFactory;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.schemas.Mview;

public class CockroachCreateMviewFactory extends Postgres93CreateMviewFactory {
	@Override
	protected void addOtherDefinitions(Mview view, List<SqlOperation> operations) {
		if (view.getOwnerName() == null && view.getSpecifics().get("COCKROACH_OWNER") != null) {
			var compatible = (Mview) view.clone();
			compatible.setOwnerName(view.getSpecifics().get("COCKROACH_OWNER"));
			super.addOtherDefinitions(compatible, operations);
		} else {
			super.addOtherDefinitions(view, operations);
		}
	}

	@Override
	protected void addCreateObject(Mview view, PostgresSqlBuilder builder) {
		if (view.getRemarks() != null)
			throw new UnsupportedOperationException("CockroachDB 24.3 materialized view comments cannot be recreated");
		if (view.getTableSpaceName() != null)
			throw new UnsupportedOperationException("CockroachDB materialized views do not support TABLESPACE");
		if (view.getColumns().stream().anyMatch(column -> column.getRemarks() != null))
			throw new UnsupportedOperationException(
					"CockroachDB 24.3 materialized view column comments cannot be recreated");
		super.addCreateObject(view, builder);
	}
}
