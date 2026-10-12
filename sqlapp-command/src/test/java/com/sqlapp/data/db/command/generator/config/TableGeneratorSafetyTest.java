package com.sqlapp.data.db.command.generator.config;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.Map;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.command.generator.config.strategy.ValueSelectStrategy;
import com.sqlapp.data.schemas.Table;

class TableGeneratorSafetyTest {
	private TableGeneratorConfig config() {
		var t = new TableGeneratorConfig();
		t.setName("CHILD");
		t.setSchemaName("PUBLIC");
		t.setRandomSeed(42L);
		for (String name : List.of("A", "B")) {
			var c = new ColumnGeneratorConfig();
			c.setName(name);
			c.setLookupGroup("parent");
			c.setMinValue("-1");
			t.addColumn(c);
		}
		return t;
	}

	@Test
	void selectsOneQueryRowPerGeneratedRow() throws Exception {
		var t = config();
		var q = new QueryGeneratorConfig();
		q.setGenerationGroup("parent");
		q.setSelectSql("select A, B from PARENT");
		q.setSelectionStrategy(ValueSelectStrategy.RANDOM);
		q.setValues(List.of(Map.of("A", 1, "B", 10), Map.of("A", 2, "B", 20)));
		t.addQueryDefinition(q);
		t.check();
		q.copyData(q);
		t.validateLoadedData();
		t.calculateInitialObjectValues();
		for (int i = 0; i < 100; i++) {
			var row = t.generateValue(i, i);
			assertEquals(((Number) row.get("A")).intValue() * 10, row.get("B"));
		}
	}

	private TableGeneratorConfig fileConfig() {
		var t = config();
		var f = new FileGeneratorConfig();
		f.setLookupGroup("parent");
		f.setDataSourceExpression("[['A':1, 'B':10], ['A':2, 'B':20]]");
		f.setSelectionStrategy(ValueSelectStrategy.RANDOM);
		t.addFileDefinition(f);
		t.check();
		f.loadData();
		t.validateLoadedData();
		t.calculateInitialObjectValues();
		return t;
	}

	@Test
	void fileRowsStayTogetherAndSeedIsIndependentOfOtherGroups() {
		var a = fileConfig();
		var b = fileConfig();
		b.createSelectionRandom("unrelated").nextLong();
		for (int i = 0; i < 100; i++) {
			var row = a.generateValue(i, i);
			var other = b.generateValue(i, i);
			assertEquals(row.get("A"), other.get("A"));
			assertEquals(((Number) row.get("A")).intValue() * 10, row.get("B"));
		}
	}

	@Test
	void rejectsUnknownAmbiguousEmptyAndInvalidLookups() {
		var t = config();
		assertThrows(IllegalArgumentException.class, t::check);
		var f = new FileGeneratorConfig();
		f.setLookupGroup("parent");
		f.setDataSourceExpression("[]");
		t.addFileDefinition(f);
		f.setLimit(0);
		assertThrows(IllegalArgumentException.class, t::check);
		f.setLimit(1);
		f.setOffset(-1);
		assertThrows(IllegalArgumentException.class, t::check);
		f.setOffset(0);
		t.check();
		f.loadData();
		assertThrows(IllegalArgumentException.class, t::validateLoadedData);
		var q = new QueryGeneratorConfig();
		q.setGenerationGroup("parent");
		q.setSelectSql("select 1");
		t.addQueryDefinition(q);
		assertThrows(IllegalArgumentException.class, t::check);
		assertThrows(IllegalArgumentException.class, () -> ValueSelectStrategy.parse("RANODM"));
		assertEquals(ValueSelectStrategy.NEXT_VALUE, ValueSelectStrategy.parse(null));
	}

	@Test
	void rejectsUnknownColumnsAndInvertedBounds() {
		var t = new TableGeneratorConfig();
		var c = new ColumnGeneratorConfig();
		c.setName("MISSING");
		c.setMinValue("10");
		c.setMaxValue("1");
		t.addColumn(c);
		assertThrows(IllegalArgumentException.class, () -> t.initializeTableColumnData(new Table("T")));
		assertThrows(IllegalArgumentException.class, t::calculateInitialObjectValues);
		c.setMaxValue("9223372036854775807");
		assertDoesNotThrow(t::calculateInitialObjectValues);
	}

	@Test
	void fixedTimeIsAvailableInInitialAndNextValueExpressions() {
		var t = new TableGeneratorConfig();
		t.setBaseTime(LocalDateTime.of(2026, 1, 2, 3, 4));
		var c = new ColumnGeneratorConfig();
		c.setName("CREATED");
		c.setMinValue("_baseTime");
		c.setNextValue("_baseTime.plusDays(_index)");
		t.addColumn(c);
		t.calculateInitialObjectValues();
		assertEquals(t.getBaseTime(), t.generateValue(0, 0).get("CREATED"));
		assertEquals(t.getBaseTime().plusDays(1), t.generateValue(1, 1).get("CREATED"));
	}
}
