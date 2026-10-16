/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-oracle.
 */
package com.sqlapp.data.db.dialect.oracle.metadata;

import static com.sqlapp.util.CommonUtils.list;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.oracle.sql.Oracle23aiCreateDomainFactory;
import com.sqlapp.data.db.dialect.oracle.sql.OracleAnnotationUtils;
import com.sqlapp.data.db.metadata.DomainReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.Domain;
import com.sqlapp.data.schemas.DomainColumn;
import com.sqlapp.data.schemas.DomainCheckConstraint;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;

/**
 * Oracle Database 23ai scalar and multi-column data use case domain reader.
 */
public class Oracle23aiDomainReader extends DomainReader {
	protected static final String EXPECTED_COLUMNS = "oracle.domain.expectedColumns";

	protected Oracle23aiDomainReader(final Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<Domain> doGetAll(final Connection connection, final ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		final List<Domain> result = list();
		final Map<List<String>, Domain> domains = new LinkedHashMap<>();
		final SqlNode node = getDomainSqlNode();
		try {
			execute(connection, node, context, new ResultSetNextHandler() {
				@Override
				public void handleResultSetNext(final ExResultSet rs) throws SQLException {
					List<String> key = List.of(getString(rs, "OWNER"), getString(rs, "DOMAIN_NAME"));
					Domain domain = domains.get(key);
					if (domain == null) {
						domain = createDomain(rs);
						domains.put(key, domain);
						result.add(domain);
					}
					String expression = getString(rs, "SEARCH_CONDITION");
					if (expression != null) {
						DomainCheckConstraint check = new DomainCheckConstraint(getString(rs, "CONSTRAINT_NAME"), expression);
						check.setDeferrability(OracleMetadataUtils.getDeferrability(getString(rs, "DEFERRABLE"), getString(rs, "DEFERRED")));
						check.setEnable(!"DISABLED".equals(getString(rs, "CONSTRAINT_STATUS")));
						String validated = getString(rs, "VALIDATED");
						if (validated != null) check.getSpecifics().put(Oracle23aiCreateDomainFactory.VALIDATED, validated);
						String rely = getString(rs, "RELY");
						if (rely != null) check.getSpecifics().put(Oracle23aiCreateDomainFactory.RELY, rely);
						domain.getChecks().add(check);
					}
				}
			});
		} catch (final RuntimeException e) {
			if (!Oracle23aiIndexReader.isVectorIndexDetailUnavailable(e)) {
				throw e;
			}
			logger.warn("Oracle data use case domain metadata is unavailable "
					+ "or not permitted; domains are skipped. " + e.getMessage());
		}
		loadColumns(connection, context, result);
		loadAnnotations(connection, context, result);
		return result;
	}

	protected SqlNode getDomainSqlNode() {
		return getSqlNodeCache().getString("useCaseDomains.sql");
	}

	protected void loadColumns(Connection connection, ParametersContext original, List<Domain> domains) {
		Map<List<String>, Domain> targets = new LinkedHashMap<>();
		for (Domain domain : domains) {
			if (domain.getSpecifics().containsKey(EXPECTED_COLUMNS)) {
				targets.put(List.of(domain.getSchemaName(), domain.getName()), domain);
			}
		}
		if (targets.isEmpty()) return;
		ParametersContext context = new ParametersContext();
		context.put("schemaName", targets.keySet().stream().map(k -> k.get(0)).distinct().toList());
		context.put("domainName", targets.keySet().stream().map(k -> k.get(1)).distinct().toList());
		execute(connection, getSqlNodeCache().getString("domainColumns.sql"), context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(ExResultSet rs) throws SQLException {
				Domain domain = targets.get(List.of(getString(rs, "OWNER"), getString(rs, "DOMAIN_NAME")));
				if (domain == null) return;
				if (rs.getInt("DATA_TYPE_ID") != 1) {
					throw new IllegalStateException("Alternative domain column types are not supported: " + domain.getName());
				}
				DomainColumn column = new DomainColumn(getString(rs, "COLUMN_NAME"));
				Long precision = getLong(rs, "DATA_PRECISION");
				Long length = getLong(rs, "CHAR_LENGTH");
				Integer scale = getInteger(rs, "DATA_SCALE");
				String type = getString(rs, "DATA_TYPE");
				getDialect().setDbType(type, length != null && length > 0 ? length : precision, scale, column);
				if ("NUMBER".equalsIgnoreCase(type)) column.setLength(precision).setScale(scale);
				column.setNullable(!"N".equals(getString(rs, "NULLABLE")));
				column.setDefaultValue(getString(rs, "DATA_DEFAULT"));
				column.setCollation(getString(rs, "COLLATION"));
				if ("YES".equals(getString(rs, "DEFAULT_ON_NULL"))) column.getSpecifics().put(Oracle23aiCreateDomainFactory.DEFAULT_ON_NULL, true);
				if (rs.getBoolean("EXACT")) column.getSpecifics().put(Oracle23aiCreateDomainFactory.STRICT, true);
				domain.getColumns().add(column);
			}
		});
		for (Domain domain : targets.values()) {
			int expected = Integer.parseInt(domain.getSpecifics().get(EXPECTED_COLUMNS));
			if (domain.getColumns().size() != expected) {
				throw new IllegalStateException("Incomplete multi-column domain metadata: " + domain.getSchemaName() + "." + domain.getName());
			}
			domain.getSpecifics().remove(EXPECTED_COLUMNS);
		}
	}

	private void loadAnnotations(final Connection connection, final ParametersContext context,
			final List<Domain> domains) {
		if (domains.isEmpty()) {
			return;
		}
		final SqlNode node = getAnnotationSqlNode();
		try {
			execute(connection, node, context, new ResultSetNextHandler() {
				@Override
				public void handleResultSetNext(final ExResultSet rs) throws SQLException {
					final Domain domain = findDomain(domains, getString(rs, "ANNOTATION_OWNER"),
							getString(rs, "OBJECT_NAME"));
					if (domain != null) {
						setDomainAnnotation(domain, rs);
					}
				}
			});
		} catch (final RuntimeException e) {
			if (!Oracle23aiIndexReader.isVectorIndexDetailUnavailable(e)) {
				throw e;
			}
			logger.warn("Oracle domain annotation metadata is unavailable or "
					+ "not permitted; annotations are skipped. " + e.getMessage());
		}
	}

	protected void setDomainAnnotation(Domain domain, ExResultSet rs) throws SQLException {
		String columnName = getString(rs, "COLUMN_NAME");
		com.sqlapp.data.schemas.AbstractDbObject<?> target;
		if (columnName == null || domain.getColumns().isEmpty()) {
			target = domain;
		} else {
			target = domain.getColumns().stream().filter(column -> columnName.equals(column.getName())).findFirst().orElse(null);
		}
		if (target == null) throw new IllegalStateException("Unknown domain annotation column: " + domain.getSchemaName() + "." + domain.getName() + "." + columnName);
		OracleAnnotationUtils.setAnnotation(target, getString(rs, "ANNOTATION_NAME"), getString(rs, "ANNOTATION_VALUE"));
	}

	protected SqlNode getAnnotationSqlNode() {
		return getSqlNodeCache().getString("domainAnnotations.sql");
	}

	private Domain findDomain(final List<Domain> domains, final String owner, final String name) {
		for (Domain domain : domains) {
			if (java.util.Objects.equals(name, domain.getName())
					&& java.util.Objects.equals(owner, domain.getSchemaName())) {
				return domain;
			}
		}
		return null;
	}

	protected Domain createDomain(final ExResultSet rs) throws SQLException {
		final Domain domain = new Domain(getString(rs, "DOMAIN_NAME"));
		domain.setSchemaName(getString(rs, "OWNER"));
		if (rs.getInt("DOMAIN_COLUMN_COUNT") > 1) {
			domain.getSpecifics().put(EXPECTED_COLUMNS, rs.getInt("DOMAIN_COLUMN_COUNT"));
			putSpecific(domain, Oracle23aiCreateDomainFactory.DISPLAY, getString(rs, "DATA_DISPLAY"));
			putSpecific(domain, Oracle23aiCreateDomainFactory.ORDER, getString(rs, "DATA_ORDER"));
			return domain;
		}
		final String productDataType = getString(rs, "DATA_TYPE");
		final Long charLength = getLong(rs, "CHAR_LENGTH");
		final Long precision = getLong(rs, "DATA_PRECISION");
		final Integer scale = getInteger(rs, "DATA_SCALE");
		final Long size = charLength != null && charLength > 0 ? charLength : precision;
		getDialect().setDbType(productDataType, size, scale, domain);
		if ("NUMBER".equalsIgnoreCase(productDataType)) {
			// Absent precision/scale denotes Oracle's unconstrained NUMBER, not NUMBER(0,0).
			domain.setLength(precision);
			domain.setScale(scale);
		}
		domain.setOctetLength(getLong(rs, "DATA_LENGTH"));
		domain.setNullable(!"N".equalsIgnoreCase(getString(rs, "NULLABLE")));
		domain.setDefaultValue(getString(rs, "DATA_DEFAULT"));
		domain.setCollation(getString(rs, "COLLATION"));
		if ("YES".equalsIgnoreCase(getString(rs, "DEFAULT_ON_NULL"))) {
			domain.getSpecifics().put(Oracle23aiCreateDomainFactory.DEFAULT_ON_NULL, true);
		}
		if (rs.getBoolean("EXACT")) {
			domain.getSpecifics().put(Oracle23aiCreateDomainFactory.STRICT, true);
		}
		putSpecific(domain, Oracle23aiCreateDomainFactory.DISPLAY, getString(rs, "DATA_DISPLAY"));
		putSpecific(domain, Oracle23aiCreateDomainFactory.ORDER, getString(rs, "DATA_ORDER"));
		return domain;
	}

	private void putSpecific(final Domain domain, final String key, final String value) {
		if (value != null && !value.isBlank()) {
			domain.getSpecifics().put(key, value);
		}
	}
}
