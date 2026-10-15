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
import com.sqlapp.data.schemas.DomainCheckConstraint;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;

/**
 * Oracle Database 23ai single-column data use case domain reader.
 */
public class Oracle23aiDomainReader extends DomainReader {

	protected Oracle23aiDomainReader(final Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<Domain> doGetAll(final Connection connection, final ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		final List<Domain> result = list();
		final Map<List<String>, Domain> domains = new LinkedHashMap<>();
		final SqlNode node = getSqlNodeCache().getString("useCaseDomains.sql");
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
		loadAnnotations(connection, context, result);
		return result;
	}

	private void loadAnnotations(final Connection connection, final ParametersContext context,
			final List<Domain> domains) {
		if (domains.isEmpty()) {
			return;
		}
		final SqlNode node = getSqlNodeCache().getString("domainAnnotations.sql");
		try {
			execute(connection, node, context, new ResultSetNextHandler() {
				@Override
				public void handleResultSetNext(final ExResultSet rs) throws SQLException {
					final Domain domain = findDomain(domains, getString(rs, "ANNOTATION_OWNER"),
							getString(rs, "OBJECT_NAME"));
					if (domain != null) {
						OracleAnnotationUtils.setAnnotation(domain, getString(rs, "ANNOTATION_NAME"),
								getString(rs, "ANNOTATION_VALUE"));
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

	private Domain findDomain(final List<Domain> domains, final String owner, final String name) {
		for (Domain domain : domains) {
			if (java.util.Objects.equals(name, domain.getName())
					&& java.util.Objects.equals(owner, domain.getSchemaName())) {
				return domain;
			}
		}
		return null;
	}

	private Domain createDomain(final ExResultSet rs) throws SQLException {
		final Domain domain = new Domain(getString(rs, "DOMAIN_NAME"));
		domain.setSchemaName(getString(rs, "OWNER"));
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
