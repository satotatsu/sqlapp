/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.cockroach;

import java.math.BigInteger;
import java.util.Objects;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.datatype.DbDataType;
import com.sqlapp.data.db.datatype.NumericType;
import com.sqlapp.data.db.dialect.cockroach.util.CockroachPlacement;
import com.sqlapp.data.db.dialect.postgres.Postgres110;
import com.sqlapp.data.db.dialect.postgres.sql.Postgres110SqlFactoryRegistry;
import com.sqlapp.data.db.metadata.CatalogReader;
import com.sqlapp.data.db.sql.*;
import com.sqlapp.data.schemas.*;
import com.sqlapp.util.AbstractSqlBuilder;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.data.db.dialect.cockroach.metadata.CockroachCatalogReader;
import com.sqlapp.data.db.dialect.cockroach.sql.*;

/**
 * CockroachDB 24.3+; PostgreSQL wire compatibility does not imply catalog
 * compatibility.
 */
public class CockroachDB extends Postgres110 {
	private static final long serialVersionUID = 1L;

	public CockroachDB() {
		super(() -> null);
	}

	@Override
	public String getProductName() {
		return "CockroachDB";
	}

	@Override
	public String getSimpleName() {
		return "cockroach";
	}

	@Override
	public CatalogReader getCatalogReader() {
		return new CockroachCatalogReader(this);
	}

	/**
	 * PostgreSQL types that CockroachDB does not implement (verified with
	 * {@code CREATE TABLE} on CockroachDB 24.3 and 25.4). Their PostgreSQL names
	 * still resolve, but columns are created and bound as {@code TEXT}.
	 */
	private static final DataType[] TEXT_SURROGATE_TYPES = { DataType.SQLXML, DataType.CIDR, DataType.MACADDR,
			DataType.POINT, DataType.CIRCLE, DataType.BOX, DataType.LINE, DataType.LSEG, DataType.PATH,
			DataType.POLYGON, DataType.RANGE, DataType.MULTIRANGE };

	@Override
	protected void registerDataType() {
		super.registerDataType();
		// PostgreSQL 11 does not register MULTIRANGE; register it so that it can
		// be redirected to TEXT below.
		getDbDataTypes().addMultirange("MULTIRANGE", type -> type.setCreateFormat("MULTIRANGE"));
		final DbDataType<?> text = getDbDataTypes().getDbType(DataType.VARCHAR, CommonUtils.LEN_1GB);
		for (final DataType dataType : TEXT_SURROGATE_TYPES) {
			getDbDataTypes().getDbTypeStrict(dataType).setDeprecated(text);
		}
		// MONEY is a fixed two-decimal 64-bit amount (up to 92233720368547758.07).
		getDbDataTypes().getDbTypeStrict(DataType.MONEY)
				.setDeprecated(new NumericType().setDefaultPrecision(19).setDefaultScale(2));
		// CockroachDB has no abstime: keep second precision like PostgreSQL 12+.
		getDbDataTypes().getDbType(DataType.SMALLDATETIME).setCreateFormat("TIMESTAMP(0) WITH TIME ZONE");
	}

	/**
	 * Generates an explicit restart for an ordinary sequence in the current
	 * database. The requested value is the next value, and must be safe for the
	 * caller's data. Bounds present in the model are checked before generation.
	 */
	public SqlOperation createSequenceRestartSql(Sequence sequence, long nextValue) {
		var sql = sequenceAlter(sequence);
		BigInteger value = BigInteger.valueOf(nextValue);
		if ((sequence.getMinValue() != null && value.compareTo(sequence.getMinValue()) < 0)
				|| (sequence.getMaxValue() != null && value.compareTo(sequence.getMaxValue()) > 0))
			throw new IllegalArgumentException("CockroachDB sequence restart value is outside the modeled bounds");
		sql._add(" RESTART WITH ")._add(nextValue);
		return new SqlOperation(sql.toString(), SqlType.ALTER, sequence);
	}

	/** Generates a change to an existing named role in the current database. */
	public SqlOperation createSequenceOwnerChangeSql(Sequence sequence, String roleName) {
		if (roleName == null || roleName.isBlank() || roleName.indexOf('\0') >= 0)
			throw new IllegalArgumentException("CockroachDB sequence owner requires a nonempty role name without NUL");
		var sql = sequenceAlter(sequence)._add(" OWNER TO ")._add(CockroachPlacement.quote(roleName));
		return new SqlOperation(sql.toString(), SqlType.ALTER, sequence);
	}

	/** Generates a materialized view ownership change to an existing named role. */
	public SqlOperation createMaterializedViewOwnerChangeSql(Mview view, String roleName) {
		Objects.requireNonNull(view, "view");
		var target = (Mview) view.clone();
		target.setOwnerName(roleName);
		target.setDialect(this);
		var operation = createSqlFactoryRegistry().createSql(target, SqlType.ALTER).getFirst();
		return new SqlOperation(operation.getSqlText(), SqlType.ALTER, view);
	}

	private AbstractSqlBuilder<?> sequenceAlter(Sequence sequence) {
		Objects.requireNonNull(sequence, "sequence");
		if (sequence.getName() == null || sequence.getName().isBlank() || sequence.getSchemaName() == null
				|| sequence.getSchemaName().isBlank())
			throw new IllegalArgumentException("CockroachDB sequence ALTER requires a sequence name and schema");
		return createSqlBuilder().alter().sequence().space().name(sequence, true);
	}

	@Override
	public SqlFactoryRegistry createSqlFactoryRegistry() {
		return new Postgres110SqlFactoryRegistry(this) {
			@Override
			protected void initializeAllStateSqls() {
				super.initializeAllStateSqls();
				for (var type : java.util.List.<Class<?>>of(Schema.class, Index.class, Function.class, Sequence.class,
						Type.class, View.class, Mview.class))
					registerSqlFactory(type, State.Modified, SqlType.ALTER);
			}

			@Override
			protected void initializeAllSqls() {
				super.initializeAllSqls();
				registerSqlFactory(Catalog.class, SqlType.CREATE, CockroachCreateCatalogFactory.class);
				registerSqlFactory(Schema.class, SqlType.CREATE, CockroachCreateSchemaFactory.class);
				registerSqlFactory(Schema.class, SqlType.ALTER, CockroachAlterSchemaFactory.class);
				registerSqlFactory(View.class, SqlType.ALTER, CockroachAlterViewFactory.class);
				registerSqlFactory(Mview.class, SqlType.CREATE, CockroachCreateMviewFactory.class);
				registerSqlFactory(Mview.class, SqlType.DROP, CockroachDropMviewFactory.class);
				registerSqlFactory(Mview.class, SqlType.ALTER, CockroachAlterMviewFactory.class);
				registerSqlFactory(Mview.class, SqlType.REFRESH, CockroachRefreshMviewFactory.class);
				for (var type : java.util.List.<Class<?>>of(UniqueConstraint.class, CheckConstraint.class,
						ForeignKeyConstraint.class))
					registerSqlFactory(type, SqlType.DROP, CockroachDropConstraintFactory.class);

				registerSqlFactory(Table.class, SqlType.ALTER, CockroachAlterTableFactory.class);
				registerSqlFactory(Table.class, SqlType.DROP, CockroachDropTableFactory.class);
				registerSqlFactory(Index.class, SqlType.DROP, CockroachDropIndexFactory.class);
				registerSqlFactory(Index.class, SqlType.ALTER, CockroachAlterIndexFactory.class);
				registerSqlFactory(Function.class, SqlType.ALTER, CockroachAlterFunctionFactory.class);
				registerSqlFactory(Sequence.class, SqlType.ALTER, CockroachAlterSequenceFactory.class);
				registerSqlFactory(Type.class, SqlType.ALTER, CockroachAlterTypeFactory.class);

				registerSqlFactory(Index.class, SqlType.CREATE, CockroachCreateIndexFactory.class);
				registerSqlFactory(Table.class, SqlType.CREATE, CockroachCreateTableFactory.class);
				registerSqlFactory(Sequence.class, SqlType.CREATE, CockroachCreateSequenceFactory.class);
				registerSqlFactory(Table.class, SqlType.MERGE, CockroachMergeFactory.class);
			}
		};
	}
}
