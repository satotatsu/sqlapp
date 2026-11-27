/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.spanner.sql;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.spanner.DialectHolder;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.*;

class SpannerRowDeletionPolicyTest {
    @Test
    void requiresExactCasingForAQuotedReservedColumnName() {
        final var table = new Table("Events");
        table.getColumns().add(new Column("Order").setDataType(DataType.TIMESTAMP));
        final var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
        policy(table, "OLDER_THAN(`Order`, INTERVAL 1 DAY)");
        assertTrue(registry.createSql(table, SqlType.CREATE).getFirst().getSqlText()
                .contains("OLDER_THAN(`Order`, INTERVAL 1 DAY)"));
        policy(table, "OLDER_THAN(`order`, INTERVAL 1 DAY)");
        assertThrows(IllegalArgumentException.class, () -> registry.createSql(table, SqlType.CREATE));
    }

    private Table table() {
        final var table = new Table("Events");
        table.getColumns().add(new Column("Created At").setDataType(DataType.TIMESTAMP));
        return table;
    }
    private void policy(Table table, String expression) {
        table.getSpecifics().put(SpannerCreateTableFactory.ROW_DELETION_POLICY_EXPRESSION, expression);
    }
    @Test
    void createsAndMigratesPolicyIncludingZeroDays() {
        final var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
        final var original = table();
        final var added = original.clone();
        policy(added, "OLDER_THAN(`Created At`, INTERVAL 0 DAY)");
        final String create = registry.createSql(added, SqlType.CREATE).get(0).getSqlText();
        assertTrue(create.contains(", ROW DELETION POLICY (OLDER_THAN(`Created At`, INTERVAL 0 DAY))"), create);
        assertTrue(registry.createSql(original.diff(added), SqlType.ALTER).get(0).getSqlText().contains("ADD ROW DELETION POLICY"));
        final var replaced = added.clone();
        policy(replaced, "OLDER_THAN(`Created At`, INTERVAL 7 DAY)");
        assertTrue(registry.createSql(added.diff(replaced), SqlType.ALTER).get(0).getSqlText().contains("REPLACE ROW DELETION POLICY"));
        assertTrue(registry.createSql(replaced.diff(original), SqlType.ALTER).get(0).getSqlText().contains("DROP ROW DELETION POLICY"));
        assertFalse(registry.createSql(original, SqlType.CREATE).get(0).getSqlText().contains("ROW DELETION"));
    }
    @Test
    void rejectsInvalidExpressionsAndNonTimestampColumns() {
        for (String expression : new String[] {"", "OLDER_THAN(`Created At`, INTERVAL -1 DAY)", "OLDER_THAN(`Created At`, INTERVAL 1 HOUR)", "OLDER_THAN(Missing, INTERVAL 1 DAY)", "OLDER_THAN(`Created At`, INTERVAL 1 DAY); DROP TABLE Events"}) {
            final var table = table();
            policy(table, expression);
            assertThrows(IllegalArgumentException.class, () -> DialectHolder.defaultDialect.createSqlFactoryRegistry().createSql(table, SqlType.CREATE));
        }
        final var table = table();
        table.getColumns().get(0).setDataType(DataType.BIGINT);
        policy(table, "OLDER_THAN(`Created At`, INTERVAL 1 DAY)");
        assertThrows(IllegalArgumentException.class, () -> DialectHolder.defaultDialect.createSqlFactoryRegistry().createSql(table, SqlType.CREATE));
    }
    @Test
    void ordersPolicyAdditionAndRemovalAroundTimestampColumnChanges() {
        final var original = table();
        final var changed = original.clone();
        changed.getColumns().add(new Column("ExpiresAt").setDataType(DataType.TIMESTAMP));
        policy(changed, "OLDER_THAN(ExpiresAt, INTERVAL 1 DAY)");
        final var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
        final var adds = registry.createSql(original.diff(changed), SqlType.ALTER);
        assertEquals(2, adds.size());
        assertTrue(adds.get(0).getSqlText().contains("ADD COLUMN"));
        assertTrue(adds.get(1).getSqlText().contains("ADD ROW DELETION POLICY"));
        final var drops = registry.createSql(changed.diff(original), SqlType.ALTER);
        assertEquals(2, drops.size());
        assertTrue(drops.get(0).getSqlText().contains("DROP ROW DELETION POLICY"));
        assertTrue(drops.get(1).getSqlText().contains("DROP COLUMN"));
    }

    @Test
    void replacesTtlAfterAddingTheNewColumnWhileTheOldColumnRemains() {
        final var original = table();
        policy(original, "OLDER_THAN(`Created At`, INTERVAL 1 DAY)");
        final var changed = original.clone();
        changed.getColumns().add(new Column("ExpiresAt").setDataType(DataType.TIMESTAMP));
        policy(changed, "OLDER_THAN(ExpiresAt, INTERVAL 7 DAY)");
        final var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
        final var operations = registry.createSql(original.diff(changed), SqlType.ALTER);
        assertEquals(2, operations.size());
        assertTrue(operations.getFirst().getSqlText().contains("ADD COLUMN"));
        assertTrue(operations.getLast().getSqlText().contains("REPLACE ROW DELETION POLICY"));
    }

    @Test
    void rejectsRemovingTheOldReferenceBeforeReplacingTtl() {
        final var original = table();
        policy(original, "OLDER_THAN(`Created At`, INTERVAL 1 DAY)");
        final var changed = original.clone();
        changed.getColumns().remove("Created At");
        final var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
        assertThrows(IllegalArgumentException.class, () -> registry.createSql(original.diff(changed), SqlType.ALTER));
        changed.getColumns().add(new Column("ExpiresAt").setDataType(DataType.TIMESTAMP));
        policy(changed, "OLDER_THAN(ExpiresAt, INTERVAL 7 DAY)");
        assertThrows(IllegalArgumentException.class,
                () -> registry.createSql(original.diff(changed), SqlType.ALTER));
    }
}
