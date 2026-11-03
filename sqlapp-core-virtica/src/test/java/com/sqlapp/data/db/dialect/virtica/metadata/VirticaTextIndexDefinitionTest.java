package com.sqlapp.data.db.dialect.virtica.metadata;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.schemas.*;

class VirticaTextIndexDefinitionTest {
	private Index index(String name) {
		Index index = new Index(name);
		index.setIndexType(IndexType.FullText);
		index.setSchemaName("source");
		index.setTableName("data");
		index.getSpecifics().put("INDEX_SCHEMA_NAME", "index schema");
		return index;
	}

	private String ddl(String name) {
		return "CREATE TEXT INDEX \"index schema\".\"" + name.replace("\"", "\"\"")
				+ "\" ON source.data(id,body,payload) TOKENIZER v_txtindex.StringTokenizer(Integer, Long Varchar, Varchar) STEMMER NONE;";
	}

	@Test
	void extractsOnlyMatchingCreateWithQuotedIdentityAndKeepsTokenizers() {
		Index first = index("a.\"b");
		Index second = index("second");
		new VirticaDdlExporter().applyTextIndexDefinitions(
				"CREATE TABLE source.data(id INT);" + ddl(first.getName()) + ddl(second.getName()),
				List.of(first, second));
		assertTrue(String.join("\n", first.getDefinition()).contains("Integer, Long Varchar, Varchar"));
		assertFalse(String.join("\n", first.getDefinition()).contains("CREATE TABLE"));
		assertTrue(String.join("\n", second.getDefinition()).contains("STEMMER NONE"));
	}

	@Test
	void missingDuplicateMismatchedAndContextScriptsLeaveDefinitionsUnchanged() {
		Index first = index("one");
		Index second = index("two");
		first.setDefinition("old one");
		second.setDefinition("old two");
		for (String script : List.of(ddl("one"), ddl("one") + ddl("one") + ddl("two"),
				"SET search_path TO source;" + ddl("one") + ddl("two"),
				ddl("one").replace("source.data", "other.data") + ddl("two"))) {
			assertThrows(IllegalArgumentException.class,
					() -> new VirticaDdlExporter().applyTextIndexDefinitions(script, List.of(first, second)));
			assertEquals(List.of("old one"), first.getDefinition());
			assertEquals(List.of("old two"), second.getDefinition());
		}
	}
}
