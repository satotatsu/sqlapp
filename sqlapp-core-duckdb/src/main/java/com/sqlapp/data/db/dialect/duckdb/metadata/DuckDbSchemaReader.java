/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-duckdb.
 */
package com.sqlapp.data.db.dialect.duckdb.metadata;

import static com.sqlapp.util.CommonUtils.list;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.ConstantReader;
import com.sqlapp.data.db.metadata.DbLinkReader;
import com.sqlapp.data.db.metadata.DimensionReader;
import com.sqlapp.data.db.metadata.DomainReader;
import com.sqlapp.data.db.metadata.EventReader;
import com.sqlapp.data.db.metadata.ExternalTableReader;
import com.sqlapp.data.db.metadata.FunctionReader;
import com.sqlapp.data.db.metadata.MaskReader;
import com.sqlapp.data.db.metadata.MviewLogReader;
import com.sqlapp.data.db.metadata.MviewReader;
import com.sqlapp.data.db.metadata.OperatorClassReader;
import com.sqlapp.data.db.metadata.OperatorReader;
import com.sqlapp.data.db.metadata.PackageBodyReader;
import com.sqlapp.data.db.metadata.PackageReader;
import com.sqlapp.data.db.metadata.ProcedureReader;
import com.sqlapp.data.db.metadata.RuleReader;
import com.sqlapp.data.db.metadata.SchemaReader;
import com.sqlapp.data.db.metadata.SequenceReader;
import com.sqlapp.data.db.metadata.SynonymReader;
import com.sqlapp.data.db.metadata.TableLinkReader;
import com.sqlapp.data.db.metadata.TableReader;
import com.sqlapp.data.db.metadata.TriggerReader;
import com.sqlapp.data.db.metadata.TypeBodyReader;
import com.sqlapp.data.db.metadata.TypeReader;
import com.sqlapp.data.db.metadata.ViewReader;
import com.sqlapp.data.db.metadata.XmlSchemaReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;

/**
 * Reads DuckDB schemas. {@code information_schema} and {@code pg_catalog} are
 * excluded.
 */
public class DuckDbSchemaReader extends SchemaReader {

	protected DuckDbSchemaReader(final Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<Schema> doGetAll(final Connection connection, final ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		final SqlNode node = getSqlNodeCache().getString("schemas.sql");
		final List<Schema> result = list();
		execute(connection, node, context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(final ExResultSet rs) throws SQLException {
				final Schema schema = new Schema(getString(rs, SCHEMA_NAME));
				schema.setCatalogName(getString(rs, CATALOG_NAME));
				schema.setRemarks(getString(rs, REMARKS));
				result.add(schema);
			}
		});
		return result;
	}

	@Override
	protected TableReader newTableReader() {
		return new DuckDbTableReader(getDialect());
	}

	@Override
	protected ViewReader newViewReader() {
		return new DuckDbViewReader(getDialect());
	}

	@Override
	protected MviewReader newMviewReader() {
		return null;
	}

	@Override
	protected MviewLogReader newMviewLogReader() {
		return null;
	}

	@Override
	protected SequenceReader newSequenceReader() {
		return new DuckDbSequenceReader(getDialect());
	}

	@Override
	protected DbLinkReader newDbLinkReader() {
		return null;
	}

	@Override
	protected DomainReader newDomainReader() {
		return new DuckDbEnumReader(getDialect());
	}

	@Override
	protected TypeReader newTypeReader() {
		return null;
	}

	@Override
	protected TypeBodyReader newTypeBodyReader() {
		return null;
	}

	@Override
	protected SynonymReader newSynonymReader() {
		return null;
	}

	@Override
	protected TableLinkReader newTableLinkReader() {
		return null;
	}

	@Override
	protected RuleReader newRuleReader() {
		return null;
	}

	@Override
	protected FunctionReader newFunctionReader() {
		return new DuckDbFunctionReader(getDialect());
	}

	@Override
	protected ProcedureReader newProcedureReader() {
		return null;
	}

	@Override
	protected PackageReader newPackageReader() {
		return null;
	}

	@Override
	protected PackageBodyReader newPackageBodyReader() {
		return null;
	}

	@Override
	protected ConstantReader newConstantReader() {
		return null;
	}

	@Override
	protected TriggerReader newTriggerReader() {
		return null;
	}

	@Override
	protected XmlSchemaReader newXmlSchemaReader() {
		return null;
	}

	@Override
	protected OperatorReader newOperatorReader() {
		return null;
	}

	@Override
	protected OperatorClassReader newOperatorClassReader() {
		return null;
	}

	@Override
	protected ExternalTableReader newExternalTableReader() {
		return null;
	}

	@Override
	protected DimensionReader newDimensionReader() {
		return null;
	}

	@Override
	protected EventReader newEventReader() {
		return null;
	}

	@Override
	protected MaskReader newMaskReader() {
		return null;
	}
}
