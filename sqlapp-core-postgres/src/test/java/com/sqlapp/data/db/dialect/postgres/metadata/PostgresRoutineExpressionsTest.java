package com.sqlapp.data.db.dialect.postgres.metadata;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;

class PostgresRoutineExpressionsTest {
	@Test
	void splitsNestedQuotedArrayAndDollarExpressions() {
		assertEquals(List.of("concat('a,b', 'it''s')", "ARRAY[1,2]", "$$a,b$$", "numeric(12,2)", "\"a,b\""),
				PostgresRoutineExpressions
						.split("concat('a,b', 'it''s'), ARRAY[1,2], $$a,b$$, numeric(12,2), \"a,b\""));
		assertEquals(List.of(), PostgresRoutineExpressions.split(null));
	}

	@Test
	void rejectsMalformedExpressions() {
		for (String text : List.of("'abc", "(1,2", "1,,2", "$$abc", "1)"))
			assertThrows(IllegalArgumentException.class, () -> PostgresRoutineExpressions.split(text));
	}
}
