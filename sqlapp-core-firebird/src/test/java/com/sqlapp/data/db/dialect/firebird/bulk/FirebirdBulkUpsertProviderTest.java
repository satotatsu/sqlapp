/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.firebird.bulk;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.firebird.DialectHolder;
import com.sqlapp.jdbc.bulk.BulkUpsertResolver;
class FirebirdBulkUpsertProviderTest {
	@Test void resolvesProvider(){assertInstanceOf(FirebirdBulkUpsertExecutor.class,
			BulkUpsertResolver.resolve(DialectHolder.defaultDialect50));}
}
