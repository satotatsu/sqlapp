/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.virtica.bulk;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.virtica.DialectHolder;
import com.sqlapp.jdbc.bulk.BulkUpsertResolver;
class VirticaBulkUpsertProviderTest {
	@Test void resolvesProvider(){assertInstanceOf(VirticaBulkUpsertExecutor.class,
			BulkUpsertResolver.resolve(DialectHolder.defaultDialect12_0_4));}
}
