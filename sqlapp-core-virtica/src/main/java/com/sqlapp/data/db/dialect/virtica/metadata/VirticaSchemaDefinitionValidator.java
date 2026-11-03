package com.sqlapp.data.db.dialect.virtica.metadata;

import java.util.List;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.schemas.Schema;

/**
 * Preflights an authoritative schema script before any SQL operations are
 * returned.
 */
public final class VirticaSchemaDefinitionValidator {
	private VirticaSchemaDefinitionValidator() {
	}

	public static List<String> statements(Dialect dialect, Schema schema) {
		var statements = dialect.createSqlSplitter().parse(String.join("\n", schema.getDefinition())).stream()
				.filter(s -> !s.getTextType().isComment() && !s.getText().isBlank()).map(s -> s.getText().strip())
				.toList();
		boolean flex = false;
		boolean projections = false;
		for (String sql : statements) {
			var cursor = new VirticaProjectionDdlMapper.Cursor(sql);
			if (!cursor.keyword("CREATE"))
				continue;
			if (cursor.keyword("SCHEDULE") || cursor.keyword("TRIGGER"))
				throw new IllegalArgumentException(
						"Vertica scheduled schema replay requires explicit scheduler quiescence, independent enable-state restoration and procedure-reference resolution; automatic replay is unsupported");
			cursor = new VirticaProjectionDdlMapper.Cursor(sql);
			cursor.expect("CREATE");
			flex |= cursor.keyword("FLEX") || cursor.keyword("FLEXIBLE");
			cursor = new VirticaProjectionDdlMapper.Cursor(sql);
			cursor.expect("CREATE");
			projections |= cursor.keyword("PROJECTION");
		}
		if (flex && projections)
			throw new IllegalArgumentException(
					"Vertica schema export contains Flex tables and projection CREATEs: implicit projection collisions and AHM history require an explicit restore design; automatic replay is unsupported");
		return statements;
	}
}
