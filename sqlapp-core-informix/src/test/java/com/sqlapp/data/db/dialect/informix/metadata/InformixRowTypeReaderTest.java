/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.informix.metadata;

import static org.junit.jupiter.api.Assertions.*;
import java.io.StringReader;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.informix.DialectHolder;
import com.sqlapp.data.db.metadata.ReaderOptions;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.SchemaUtils;
import com.sqlapp.jdbc.sql.JdbcQueryHandler;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;

class InformixRowTypeReaderTest {
	private static class Reader extends InformixRowTypeReader {
		final List<Map<String,Object>> rows = new ArrayList<>(); int queries;
		Reader() { super(DialectHolder.defaultDialect); setReaderOptions(new ReaderOptions()); }
		@Override protected JdbcQueryHandler execute(Connection c, SqlNode n, ParametersContext p, ResultSetNextHandler handler) {
			queries++;
			try { for (var row : rows) handler.handleResultSetNext(InformixMetadataReaderTest.row(row)); }
			catch (SQLException e) { throw new RuntimeException(e); }
			return null;
		}
	}
	private Map<String,Object> field(int id, String name, String field, int type, int length) {
		final Map<String,Object> row = new HashMap<>();
		row.put("type_id", id); row.put("type_name", name); row.put("catalog_name", "db"); row.put("schema_name", "owner");
		row.put("field_name", field); row.put("field_type", type); row.put("field_length", length);
		return row;
	}
	@Test void readsManyRowTypesAndOnlyNewSubtypeFieldsInOneQueryThroughXml() throws Exception {
		final var reader = new Reader();
		for (int i=0; i<20; i++) reader.rows.add(field(i,"type"+i,"quoted\"field",2,4));
		final var inherited = field(20,"child_t","id",2,4);
		inherited.put("parent_name", "type0"); inherited.put("parent_owner", "owner"); inherited.put("inherited_field", "1"); reader.rows.add(inherited);
		final var own = new HashMap<>(inherited); own.remove("inherited_field"); own.put("field_name", "value"); own.put("field_type",5); own.put("field_length",2306); reader.rows.add(own);
		final var types = reader.doGetAll(null,new ParametersContext(),null);
		assertEquals(1, reader.queries); assertEquals(21, types.size());
		final com.sqlapp.data.schemas.Type restored = SchemaUtils.readXml(new StringReader(types.get(20).asXml()));
		assertEquals("CREATE ROW TYPE \"owner\".\"child_t\"(\"value\" DECIMAL(9,2)) UNDER \"owner\".\"type0\"", String.join("\n", restored.getDefinition()));
		assertTrue(String.join("\n",types.get(0).getDefinition()).contains("\"quoted\"\"field\" INTEGER"));
	}
	@Test void preservesDistinctFieldIdentityInsteadOfFlatteningItsBaseType() {
		final var reader = new Reader();
		final var value = field(1,"row_t","value",2050,4);
		value.put("field_type_name","money_id"); value.put("field_type_owner","custom");
		reader.rows.add(value);
		assertTrue(String.join("\n",reader.doGetAll(null,new ParametersContext(),null).get(0).getDefinition()).contains("\"custom\".\"money_id\""));
		value.remove("field_type_name");
		assertThrows(IllegalStateException.class, () -> reader.doGetAll(null,new ParametersContext(),null));
	}
	@Test void honorsTextOptionsAndRejectsUnreadableFields() {
		final var reader = new Reader(); reader.rows.add(field(1,"unknown","field",19,4));
		assertThrows(IllegalStateException.class, () -> reader.doGetAll(null,new ParametersContext(),null));
		reader.getReaderOptions().setReadStatement(false); reader.getReaderOptions().setReadDefinition(false);
		assertTrue(reader.doGetAll(null,new ParametersContext(),null).get(0).getDefinition().isEmpty());
	}
	@Test void resolvesNestedCollectionsAndAnonymousRowsInOneQuery() {
		final var reader = new Reader();
		final var root = field(1,"root_t","values",21,0);
		root.put("field_type_id",2); reader.rows.add(root);
		final var list = field(2,null,null,278,0);
		list.put("type_mode","C"); list.put("field_type_id",3); reader.rows.add(list);
		final var nested = field(3,null,"value",2,4);
		nested.put("type_mode","C"); reader.rows.add(nested);
		final var types = reader.doGetAll(null,new ParametersContext(),null);
		assertEquals(1,types.size()); assertEquals(1,reader.queries);
		assertTrue(String.join("\n",types.get(0).getDefinition()).contains("LIST(ROW(\"value\" INTEGER) NOT NULL)"));
		nested.put("field_type",21); nested.put("field_type_id",2);
		assertThrows(IllegalStateException.class, () -> reader.doGetAll(null,new ParametersContext(),null));
	}
	@Test void createsDistinctDefinitionsIncludingNamedSources() {
		final var reader = new Reader();
		final var base = field(1,"money_t",null,0,0);
		base.put("type_mode","D"); base.put("base_type",2053); base.put("base_length",2306); reader.rows.add(base);
		final var child = field(2,"money_child",null,0,0);
		child.put("type_mode","D"); child.put("source_id",1); child.put("source_name","money_t"); child.put("source_owner","owner"); reader.rows.add(child);
		final var types = reader.doGetAll(null,new ParametersContext(),null);
		assertEquals("CREATE DISTINCT TYPE \"owner\".\"money_t\" AS DECIMAL(9,2)", String.join("\n",types.get(0).getDefinition()));
		assertTrue(String.join("\n",types.get(1).getDefinition()).endsWith("AS \"owner\".\"money_t\""));
		child.remove("source_name");
		assertThrows(IllegalStateException.class, () -> reader.doGetAll(null,new ParametersContext(),null));
		reader.getReaderOptions().setReadDefinition(false); reader.getReaderOptions().setReadStatement(false);
		final var headers = reader.doGetAll(null,new ParametersContext(),null);
		assertEquals(2,headers.size()); assertTrue(headers.stream().allMatch(t -> t.getDefinition().isEmpty()));
	}
	@Test void recreatesInheritedOnlySubtypesButRejectsEmptyRootTypes() {
		final var reader = new Reader();
		final var inherited = field(1,"child_t","id",2,4);
		inherited.put("parent_name","base_t"); inherited.put("parent_owner","owner"); inherited.put("inherited_field","1");
		reader.rows.add(inherited);
		assertEquals("CREATE ROW TYPE \"owner\".\"child_t\" UNDER \"owner\".\"base_t\"",
				String.join("\n",reader.doGetAll(null,new ParametersContext(),null).get(0).getDefinition()));
		assertEquals(1,reader.queries);
		reader.rows.clear(); reader.rows.add(field(2,"empty_root",null,0,0));
		assertThrows(UnsupportedOperationException.class,() -> reader.doGetAll(null,new ParametersContext(),null));
	}
}
