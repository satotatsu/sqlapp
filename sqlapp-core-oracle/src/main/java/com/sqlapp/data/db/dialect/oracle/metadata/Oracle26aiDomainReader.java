/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.oracle.metadata;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.oracle.sql.OracleDomainDependencies;
import com.sqlapp.util.JsonUtils;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.Domain;
import com.sqlapp.data.schemas.DomainColumn;
import com.sqlapp.data.db.dialect.oracle.sql.OracleDomainAssociationUtils;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;

/** Oracle 26ai domains, including complete definitions for advanced forms. */
public class Oracle26aiDomainReader extends Oracle23aiDomainReader {
	private static final String DEFINITION_REQUIRED = "oracle.domain.definitionRequired";

	@Override
	protected List<Domain> doGetAll(Connection connection, ParametersContext context, ProductVersionInfo version) {
		List<Domain> result = super.doGetAll(connection, context, version);
		Map<List<String>, Domain> targets = new LinkedHashMap<>();
		for (Domain domain : result) targets.put(List.of(domain.getSchemaName(), domain.getName()), domain);
		ParametersContext definitionContext = new ParametersContext();
		definitionContext.putAll(context);
		var required = result.stream().filter(d -> d.getSpecifics().get(DEFINITION_REQUIRED) != null).toList();
		definitionContext.put("definitionOwner", required.stream().map(Domain::getSchemaName).distinct().toList());
		definitionContext.put("definitionName", required.stream().map(Domain::getName).distinct().toList());
		execute(connection, getSqlNodeCache().getString("domainDefinitions26ai.sql"), definitionContext, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(ExResultSet rs) throws SQLException {
				String owner = getString(rs, "OWNER");
				String name = getString(rs, "DOMAIN_NAME");
				String ddl = getString(rs, "DOMAIN_DEFINITION");
				if (ddl == null || ddl.isBlank()) throw new IllegalStateException("Oracle domain DDL unavailable: " + owner + "." + name);
				Domain domain = targets.get(List.of(owner, name));
				if (domain == null) {
					domain = new Domain(name).setSchemaName(owner);
					targets.put(List.of(owner, name), domain);
					result.add(domain);
				}
				domain.setDefinition(ddl);
				domain.getSpecifics().remove(DEFINITION_REQUIRED);
				String kind = getString(rs, "DOMAIN_KIND");
				if (kind != null) domain.getSpecifics().put("oracle.domain.kind", kind);
			}
		});
		for (Domain domain : result) {
			if (domain.getSpecifics().get(DEFINITION_REQUIRED) != null) {
				throw new IllegalStateException("Oracle domain requires a complete definition: " + domain.getSchemaName() + "." + domain.getName());
			}
		}
		if (!result.isEmpty()) {
			Map<List<String>, List<List<String>>> dependencies = new LinkedHashMap<>();
			execute(connection, getSqlNodeCache().getString("domainDependencies26ai.sql"), context, new ResultSetNextHandler() {
				@Override public void handleResultSetNext(ExResultSet rs) throws SQLException {
					var key = List.of(getString(rs, "OWNER"), getString(rs, "DOMAIN_NAME"));
					if (!targets.containsKey(key)) return;
					dependencies.computeIfAbsent(key, ignored -> new ArrayList<>()).add(
							List.of(getString(rs, "REFERENCED_OWNER"), getString(rs, "REFERENCED_NAME")));
				}
			});
			dependencies.forEach((key, refs) -> targets.get(key).getSpecifics().put(
					OracleDomainDependencies.DEPENDENCIES, JsonUtils.toJsonString(refs)));
		}
		loadFlexibleColumns(connection, context, result, targets);
		return OracleDomainDependencies.sort(result);
	}

	private void loadFlexibleColumns(Connection connection, ParametersContext context, List<Domain> domains,
			Map<List<String>, Domain> targets) {
		var flexible = domains.stream().filter(OracleDomainAssociationUtils::isFlexible).toList();
		if (flexible.isEmpty()) return;
		execute(connection, getSqlNodeCache().getString("flexibleDomainColumns26ai.sql"), context, new ResultSetNextHandler() {
			@Override public void handleResultSetNext(ExResultSet rs) throws SQLException {
				Domain domain = targets.get(List.of(getString(rs, "OWNER"), getString(rs, "DOMAIN_NAME")));
				if (domain == null || !OracleDomainAssociationUtils.isFlexible(domain)) return;
				DomainColumn column = new DomainColumn(getString(rs, "COLUMN_NAME"));
				column.getSpecifics().put(OracleDomainAssociationUtils.DISCRIMINANT, rs.getBoolean("DISCRIMINANT"));
				domain.getColumns().add(column);
			}
		});
		for (Domain domain : flexible) {
			long discriminants = domain.getColumns().stream().filter(OracleDomainAssociationUtils::isDiscriminant).count();
			if (discriminants == 0 || discriminants == domain.getColumns().size()) {
				throw new IllegalStateException("Incomplete flexible domain components: " + domain.getSchemaName() + "." + domain.getName());
			}
		}
	}
	protected Oracle26aiDomainReader(Dialect dialect) { super(dialect); }
	@Override protected SqlNode getDomainSqlNode() {
		return getSqlNodeCache().getString("useCaseDomains26ai.sql");
	}
	@Override protected SqlNode getAnnotationSqlNode() {
		return getSqlNodeCache().getString("domainAnnotations26ai.sql");
	}

	@Override
	protected void setDomainAnnotation(Domain domain, ExResultSet rs) throws SQLException {
		super.setDomainAnnotation(domain, rs);
		if (domain.getColumns().isEmpty() && getString(rs, "COLUMN_NAME") == null) {
			domain.getSpecifics().put(DEFINITION_REQUIRED, true);
		}
	}

	@Override
	protected Domain createDomain(ExResultSet rs) throws SQLException {
		Domain domain = super.createDomain(rs);
		if (rs.getInt("ALTERNATIVE_TYPE_COUNT") > 0) {
			domain.getSpecifics().put(DEFINITION_REQUIRED, true);
		}
		return domain;
	}
}
