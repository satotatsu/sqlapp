/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.test.audit;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.sql.*;
import com.sqlapp.data.schemas.*;

/**
 * Offline evidence collection, not a server SQL-validity or parity assertion.
 */
public final class Db2PostgresCapabilityAudit {
	private Db2PostgresCapabilityAudit() {
	}

	private static final List<String> rows = new ArrayList<>();

	public static void main(String[] args) throws Exception {
		if (args.length != 1)
			throw new IllegalArgumentException("Supply the UTF-8 TSV output path");
		rows.add("dialect\tcase\tfactory\toutcome\tsql_or_error");
		for (var dialect : List.of(com.sqlapp.data.db.dialect.db2.DialectHolder.Db2_1215Dialect,
				com.sqlapp.data.db.dialect.postgres.DialectHolder.postgreSQL180)) {
			var schemaReader = dialect.getCatalogReader().getSchemaReader();
			for (String method : List.of("getTableReader", "getViewReader", "getMviewReader", "getSequenceReader",
					"getDomainReader", "getTypeReader", "getFunctionReader", "getProcedureReader", "getTriggerReader",
					"getPackageReader", "getConstantReader")) {
				Object reader = schemaReader.getClass().getMethod(method).invoke(schemaReader);
				rows.add(dialect.getClass().getSimpleName() + "\treader." + method + "\t"
						+ (reader == null ? "none" : reader.getClass().getName()) + "\t"
						+ (reader == null ? "UNSUPPORTED" : "RESOLVED")
						+ "\tDispatch only; no metadata query executed");
			}
			table(dialect, "column.default.add", value -> value.getColumns().get("V").setDefaultValue("1"));
			table(dialect, "column.default.drop", value -> value.getColumns().get("V").setDefaultValue(null), true);
			table(dialect, "column.notNull.add", value -> value.getColumns().get("V").setNotNull(true));
			table(dialect, "column.type.widen", value -> value.getColumns().get("V").setDataType(DataType.BIGINT));
			table(dialect, "column.add",
					value -> value.getColumns().add("NEW_COL", column -> column.setDataType(DataType.INT)));
			table(dialect, "column.drop", value -> value.getColumns().remove("V"));
			table(dialect, "column.comment", value -> value.getColumns().get("V").setRemarks("changed"));
			table(dialect, "table.comment", value -> value.setRemarks("changed"));
			var function = new Function("F").setSchemaName("APP");
			function.setStatement("RETURN 1");
			function.getReturning().setDataType(DataType.INT);
			diff(dialect, "function.comment", function, function.clone().setRemarks("changed"));
			var schema = new Schema("APP");
			schema.getFunctions().add(function.clone());
			var targetSchema = schema.clone();
			targetSchema.getFunctions().get("F").setRemarks("changed");
			diff(dialect, "schema.function.comment", schema, targetSchema);
			var catalog = new Catalog();
			catalog.getSchemas().add(schema);
			var targetCatalog = catalog.clone();
			targetCatalog.getSchemas().get("APP").getFunctions().get("F").setRemarks("changed");
			diff(dialect, "catalog.function.comment", catalog, targetCatalog);
			var changed = function.clone();
			changed.setStatement("RETURN 2");
			diff(dialect, "function.body", function, changed);
			var procedure = new Procedure("P").setSchemaName("APP");
			procedure.setStatement("BEGIN END");
			diff(dialect, "procedure.comment", procedure, procedure.clone().setRemarks("changed"));
			var view = new View("V").setSchemaName("APP");
			view.setStatement("SELECT 1 AS ID FROM SYSIBM.SYSDUMMY1");
			var changedView = view.clone();
			changedView.setStatement("SELECT 2 AS ID FROM SYSIBM.SYSDUMMY1");
			diff(dialect, "view.body", view, changedView);
			diff(dialect, "view.comment", view, view.clone().setRemarks("changed"));
			for (SqlType sqlType : List.of(SqlType.CREATE, SqlType.DROP, SqlType.INSERT, SqlType.UPDATE, SqlType.DELETE,
					SqlType.MERGE)) {
				var value = baseTable();
				factory(dialect, "table." + sqlType, value, sqlType);
			}
		}
		Path output = Path.of(args[0]);
		Files.createDirectories(output.toAbsolutePath().getParent());
		Files.write(output, rows, StandardCharsets.UTF_8);
		System.out.println("Offline audit wrote " + (rows.size() - 1) + " observations to " + output);
		System.out.println("GENERATED means generation completed; it does not mean the SQL executed successfully.");
	}

	private static Table baseTable() {
		var table = new Table("T").setSchemaName("APP");
		table.getColumns().add("V", column -> column.setDataType(DataType.INT));
		return table;
	}

	private static void table(Dialect dialect, String name, Consumer<Table> change) {
		table(dialect, name, change, false);
	}

	private static void table(Dialect dialect, String name, Consumer<Table> change, boolean withDefault) {
		var original = baseTable();
		if (withDefault)
			original.getColumns().get("V").setDefaultValue("1");
		var target = original.clone();
		change.accept(target);
		diff(dialect, name, original, target);
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	private static void diff(Dialect dialect, String name, DbObject original, DbObject target) {
		SqlFactory factory = null;
		try {
			factory = dialect.createSqlFactoryRegistry().getSqlFactory(target, SqlType.ALTER);
			List<SqlOperation> operations = factory.createDiffSql(original.diff(target));
			add(dialect, name, factory, "GENERATED", operations.stream()
					.map(operation -> operation.getSqlType() + ":" + operation.getSqlText()).toList().toString());
		} catch (RuntimeException failure) {
			add(dialect, name, factory, "REJECTED", failure.getClass().getSimpleName() + ": " + failure.getMessage());
		}
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	private static void factory(Dialect dialect, String name, AbstractSchemaObject value, SqlType type) {
		SqlFactory factory = null;
		try {
			factory = dialect.createSqlFactoryRegistry().getSqlFactory(value, type);
			add(dialect, name, factory, "RESOLVED", "SQL not generated in dispatch-only observation");
		} catch (RuntimeException failure) {
			add(dialect, name, factory, "REJECTED", failure.toString());
		}
	}

	private static void add(Dialect dialect, String name, SqlFactory<?> factory, String outcome, String detail) {
		rows.add(dialect.getClass().getSimpleName() + "\t" + name + "\t"
				+ (factory == null ? "none" : factory.getClass().getName()) + "\t" + outcome + "\t"
				+ detail.replace("\t", "\\t").replace("\r", "").replace("\n", "\\n"));
	}
}
