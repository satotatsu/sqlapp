/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.informix.metadata;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.ArrayList;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.TypeReader;
import com.sqlapp.data.db.metadata.TypeColumnReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.Type;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;

/** Bulk named ROW type definitions, including the fields introduced by a subtype. */
public class InformixRowTypeReader extends TypeReader {
	private record Attribute(String name, int code, int length, int reference, String typeName, String owner) {}
	private java.util.Map<Integer,List<Attribute>> complexFields = java.util.Map.of();
	private final java.util.Map<Integer,Integer> complexCodes = new LinkedHashMap<>();
	java.util.Map<Integer,String> readComplexDeclarations(final Connection connection, final java.util.Set<Integer> ids) {
		final var context = new ParametersContext();
		context.put("complexTypesOnly", true);
		final var options = new com.sqlapp.data.db.metadata.ReaderOptions();
		options.setReadDefinition(false); options.setReadStatement(false); setReaderOptions(options);
		doGetAll(connection, context, null);
		final var result = new LinkedHashMap<Integer,String>();
		for (int id : ids) {
			final var code = complexCodes.get(id);
			if (code == null) throw new IllegalStateException("Missing Informix column complex type: " + id);
			result.put(id,declaration(new Attribute(null,code & ~256,0,id,null,null),complexFields,new java.util.HashSet<>()));
		}
		return result;
	}
	public InformixRowTypeReader(final Dialect dialect) { super(dialect); }
	@Override protected TypeColumnReader newColumnFactory() { return null; }
	@Override protected List<Type> doGetAll(final Connection connection, final ParametersContext context, final ProductVersionInfo version) {
		final boolean readText = getReaderOptions().isReadDefinition() || getReaderOptions().isReadStatement();
		final var types = new LinkedHashMap<Integer, Type>();
		final var fields = new LinkedHashMap<Integer, List<Attribute>>();
		final var parents = new LinkedHashMap<Integer, String>();
		final var modes = new LinkedHashMap<Integer, String>();
		final var bases = new LinkedHashMap<Integer, Attribute>();
		complexCodes.clear();
		execute(connection, getSqlNodeCache().getString("rowTypes.sql"), context, new ResultSetNextHandler() {
			@Override public void handleResultSetNext(final ExResultSet rs) throws SQLException {
				final int id = rs.getInt("type_id");
				if (!types.containsKey(id)) {
					final var type = new Type(getString(rs, TYPE_NAME)).setSchemaName(getString(rs, SCHEMA_NAME)).setCatalogName(getString(rs, CATALOG_NAME));
					types.put(id, type);
					fields.put(id, new ArrayList<>());
					final String mode = getString(rs, "type_mode");
					modes.put(id, mode == null ? "R" : mode);
					if ("C".equals(mode)) complexCodes.put(id,rs.getInt("base_type"));
					if (readText && "D".equals(mode)) {
						if (rs.getInt("source_id") != 0 && getString(rs, "source_name") == null)
							throw new IllegalStateException("Missing Informix DISTINCT source identity: " + type.getName());
						final boolean lvarchar = "lvarchar".equals(getString(rs, "source_name")) && "informix".equals(getString(rs, "source_owner"));
						bases.put(id, new Attribute(null, rs.getInt("base_type") & ~2048, rs.getInt("base_length"), 0,
								lvarchar ? null : getString(rs, "source_name"), getString(rs, "source_owner")));
					}
					if (getString(rs, "parent_name") != null) parents.put(id, identifier(getString(rs, "parent_owner")) + "." + identifier(getString(rs, "parent_name")));
				}
				if (!readText && !"C".equals(modes.get(id)) || getString(rs, "inherited_field") != null) return;
				final String field = getString(rs, "field_name");
				if (field != null || "C".equals(modes.get(id))) fields.get(id).add(new Attribute(field,
						rs.getInt("field_type"), rs.getInt("field_length"), rs.getInt("field_type_id"),
						getString(rs, "field_type_name"), getString(rs, "field_type_owner")));
			}
		});
		for (var entry : types.entrySet()) {
			if (!readText) continue;
			final var type = entry.getValue();
			if ("C".equals(modes.get(entry.getKey()))) continue;
			if ("D".equals(modes.get(entry.getKey()))) {
				final var base = bases.get(entry.getKey());
				type.setDefinition("CREATE DISTINCT TYPE " + identifier(type.getSchemaName()) + "." + identifier(type.getName())
						+ " AS " + (base.typeName() != null ? identifier(base.owner()) + "." + identifier(base.typeName())
								: declaration(base, fields, new java.util.HashSet<>())));
				continue;
			}
			final boolean inheritedOnly = fields.get(entry.getKey()).isEmpty();
			if (inheritedOnly && !parents.containsKey(entry.getKey()))
				throw new UnsupportedOperationException("Informix root ROW type has no independently readable fields: " + type.getName());
			type.setDefinition("CREATE ROW TYPE " + identifier(type.getSchemaName()) + "." + identifier(type.getName())
					+ (inheritedOnly ? "" : "(" + rowFields(fields.get(entry.getKey()), fields, new java.util.HashSet<>()) + ")")
					+ (parents.containsKey(entry.getKey()) ? " UNDER " + parents.get(entry.getKey()) : ""));
		}
		complexFields = fields;
		return types.entrySet().stream().filter(e -> !"C".equals(modes.get(e.getKey()))).map(java.util.Map.Entry::getValue).toList();
	}

	private String rowFields(final List<Attribute> attributes, final java.util.Map<Integer,List<Attribute>> fields, final java.util.Set<Integer> path) {
		return attributes.stream().map(a -> identifier(a.name()) + " " + declaration(a, fields, path)).collect(java.util.stream.Collectors.joining(","));
	}
	private String declaration(final Attribute attribute, final java.util.Map<Integer,List<Attribute>> fields, final java.util.Set<Integer> path) {
		final int raw = attribute.code();
		if ((raw & 2048) != 0) {
			return identifier(attribute.owner()) + "." + identifier(attribute.typeName()) + ((raw & 256) != 0 ? " NOT NULL" : "");
		}
		final int type = raw & 255;
		final int length = attribute.length() & 65535;
		final String result;
		if (java.util.Set.of(19,20,21,22).contains(type) && attribute.typeName() == null) {
			final int reference = attribute.reference();
			final var members = fields.get(reference);
			if (members == null || members.isEmpty()) throw new IllegalStateException("Missing Informix complex type members: " + reference);
			if (!path.add(reference)) throw new IllegalStateException("Cyclic Informix anonymous type: " + reference);
			try {
				if (type == 22) result = "ROW(" + rowFields(members, fields, path) + ")";
				else {
					if (members.size() != 1) throw new IllegalStateException("Informix collection requires exactly one element type: " + reference);
					result = (type == 19 ? "SET" : type == 20 ? "MULTISET" : "LIST") + "(" + declaration(members.get(0), fields, path) + ")";
				}
			} finally { path.remove(reference); }
		} else result = switch (type) {
		case 0 -> "CHAR(" + length + ")";
		case 1 -> "SMALLINT";
		case 2 -> "INTEGER";
		case 3 -> "FLOAT";
		case 4 -> "SMALLFLOAT";
		case 5, 8 -> (type == 5 ? "DECIMAL(" : "MONEY(") + (length >>> 8) + ((length & 255) == 255 ? "" : "," + (length & 255)) + ")";
		case 7 -> "DATE";
		case 10 -> datetime(length);
		case 11 -> "BYTE";
		case 12 -> "TEXT";
		case 13, 16 -> (type == 13 ? "VARCHAR(" : "NVARCHAR(") + (length & 255) + (length >>> 8 == 0 ? "" : "," + (length >>> 8)) + ")";
		case 14 -> com.sqlapp.data.db.dialect.informix.util.InformixTemporalTypes.interval(length);
		case 15 -> "NCHAR(" + length + ")";
		case 17 -> "INT8";
		case 40 -> "LVARCHAR(" + length + ")";
		case 45 -> "BOOLEAN";
		case 52 -> "BIGINT";
		case 22, 41 -> {
			final String name = attribute.typeName();
			if (name == null) throw new IllegalStateException("Missing Informix ROW field type identity");
			if (java.util.Set.of("blob", "clob", "boolean", "bigint").contains(name.toLowerCase(java.util.Locale.ROOT))) yield name.toUpperCase(java.util.Locale.ROOT);
			yield identifier(attribute.owner()) + "." + identifier(name);
		}
		default -> throw new UnsupportedOperationException("Unsupported Informix ROW field catalog type: " + type);
		};
		return result + ((raw & 256) != 0 ? " NOT NULL" : "");
	}
	private String datetime(final int length) {
		final var units = List.of("YEAR", "MONTH", "DAY", "HOUR", "MINUTE", "SECOND", "FRACTION");
		final int first = (length >>> 4) & 15, last = length & 15;
		if (first > 12 || (first & 1) != 0 || last > 15 || last < 11 && (last & 1) != 0)
			throw new IllegalStateException("Invalid Informix ROW DATETIME qualifier: " + length);
		return com.sqlapp.data.db.dialect.informix.util.InformixTemporalTypes.normalize("DATETIME " + units.get(first / 2) + " TO " + (last >= 11 ? "FRACTION(" + (last - 10) + ")" : units.get(last / 2)));
	}
	private String identifier(final String name) {
		if (name == null || name.isBlank()) throw new IllegalStateException("Missing Informix ROW type identifier");
		return "\"" + name.replace("\"", "\"\"") + "\"";
	}
}
