/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-command.
 */
package com.sqlapp.data.db.command.host.cobol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.command.host.cobol.CobolItem.Category;
import com.sqlapp.data.db.command.host.cobol.CobolItem.Usage;
import com.sqlapp.exceptions.CommandException;

class CobolCopybookParserTest {

	static final String CUSTOMER = """
			000100* CUSTOMER MASTER RECORD
			000200 01  CUSTOMER-REC.
			000300     05  CUST-ID             PIC X(8).
			000400*    CUSTOMER NAME (KANJI)
			000500     05  CUST-NAME           PIC N(10).
			000600     05  CUST-STATUS         PIC X.
			000700         88  ACTIVE          VALUE 'A'.
			000800         88  CLOSED          VALUE 'C' 'X'.
			000900     05  BALANCE             PIC S9(7)V99 COMP-3.
			001000     05  CREDIT-LIMIT        PIC 9(7) COMP.
			001100     05  FILLER              PIC X(2).
			001200     05  BIRTH-DATE.
			001300         10  BIRTH-YY        PIC 9(4).
			001400         10  BIRTH-MM        PIC 99.
			001500         10  BIRTH-DD        PIC 99.
			001600     05  BIRTH-DATE-N REDEFINES BIRTH-DATE
			001700                             PIC 9(8).
			001800     05  POINTS              PIC S9(5) SIGN LEADING SEPARATE.
			001900     05  ORDER-COUNT         PIC S9(4) COMP-5.
			002000     05  ORDERS OCCURS 0 TO 5 TIMES
			002100                DEPENDING ON ORDER-COUNT.
			002200         10  ORDER-NO        PIC X(6).
			002300         10  ORDER-AMT       PIC S9(5)V99 COMP-3.
			""";

	@Test
	void computesTheStorageLayout() {
		CobolCopybook copybook = new CobolCopybookParser().parse(CUSTOMER);
		CobolItem record = copybook.record("CUSTOMER-REC");
		assertEquals(106, record.getTotalLength());
		assertLayout(record, "CUST-ID", 0, 8, Category.ALPHANUMERIC, Usage.DISPLAY);
		assertLayout(record, "CUST-NAME", 8, 20, Category.NATIONAL, Usage.NATIONAL);
		assertLayout(record, "CUST-STATUS", 28, 1, Category.ALPHANUMERIC, Usage.DISPLAY);
		assertLayout(record, "BALANCE", 29, 5, Category.NUMERIC, Usage.PACKED_DECIMAL);
		assertLayout(record, "CREDIT-LIMIT", 34, 4, Category.NUMERIC, Usage.BINARY);
		assertLayout(record, "BIRTH-DATE", 40, 8, Category.GROUP, null);
		assertLayout(record, "BIRTH-MM", 44, 2, Category.NUMERIC, Usage.DISPLAY);
		assertLayout(record, "BIRTH-DATE-N", 40, 8, Category.NUMERIC, Usage.DISPLAY);
		assertLayout(record, "POINTS", 48, 6, Category.NUMERIC, Usage.DISPLAY);
		assertLayout(record, "ORDER-COUNT", 54, 2, Category.NUMERIC, Usage.NATIVE_BINARY);
		assertLayout(record, "ORDERS", 56, 10, Category.GROUP, null);
		assertLayout(record, "ORDER-AMT", 62, 4, Category.NUMERIC, Usage.PACKED_DECIMAL);

		CobolItem balance = record.find("BALANCE");
		assertEquals(9, balance.getDigits());
		assertEquals(2, balance.getScale());
		assertTrue(balance.isSigned());
		assertEquals("PIC S9(7)V99 COMP-3", balance.getDeclaration());
		CobolItem points = record.find("POINTS");
		assertTrue(points.isSignLeading() && points.isSignSeparate());
		CobolItem orders = record.find("ORDERS");
		assertEquals(0, orders.getOccursMinimum());
		assertEquals(5, orders.getOccursMaximum());
		assertEquals(record.find("ORDER-COUNT"), orders.getDependingOn());
		assertEquals(record.find("BIRTH-DATE"), record.find("BIRTH-DATE-N").getRedefines());
		assertEquals(
				List.of(new CobolItem.Condition("ACTIVE", List.of("'A'")),
						new CobolItem.Condition("CLOSED", List.of("'C'", "'X'"))),
				record.find("CUST-STATUS").getConditions());
		assertEquals("CUSTOMER MASTER RECORD", record.getRemarks());
		assertEquals("CUSTOMER NAME (KANJI)", record.find("CUST-NAME").getRemarks());
		assertTrue(record.getChildren().get(5).isFiller());
	}

	@Test
	void parsesFreeFormatReplacingAndCopybooksWithoutLevel01() {
		CobolCopybook copybook = new CobolCopybookParser().setSourceFormat(CobolCopybookParser.SourceFormat.FREE)
				.setReplacing(Map.of(":PFX:", "CUST")).setDefaultRecordName("CUST-FRAGMENT").parse("""
						*> fragment included under the program's own 01
						05 :PFX:-CODE PIC X(4). *> code
						05 :PFX:-RATE PIC SVP(2)9(3) COMP-3.
						05 :PFX:-UNITS PIC 9(3)P(3).
						05 :PFX:-WEIGHT COMP-2.
						05 :PFX:-DATE PIC 99/99/99.
						05 :PFX:-AMOUNT PIC ZZ,ZZ9.99-.
						""");
		CobolItem record = copybook.records().getFirst();
		assertEquals("CUST-FRAGMENT", record.getName());
		assertEquals("fragment included under the program's own 01 code", record.find("CUST-CODE").getRemarks());
		assertNull(record.find("CUST-RATE").getRemarks());
		CobolItem rate = record.find("CUST-RATE");
		assertEquals(3, rate.getDigits());
		assertEquals(5, rate.getScale());
		assertEquals(2, rate.getLength());
		CobolItem units = record.find("CUST-UNITS");
		assertEquals(-3, units.getScale());
		assertEquals(3, units.getLength());
		assertEquals(Category.FLOATING_POINT, record.find("CUST-WEIGHT").getCategory());
		assertEquals(8, record.find("CUST-WEIGHT").getLength());
		assertEquals(Category.NUMERIC_EDITED, record.find("CUST-DATE").getCategory());
		assertEquals(8, record.find("CUST-DATE").getLength());
		assertEquals(Category.NUMERIC_EDITED, record.find("CUST-AMOUNT").getCategory());
		assertEquals(10, record.find("CUST-AMOUNT").getLength());
		assertEquals(4 + 2 + 3 + 8 + 8 + 10, record.getLength());
	}

	@Test
	void continuesLiteralsAndInheritsGroupUsage() {
		CobolCopybook copybook = new CobolCopybookParser().parse("""
				       01  REC.
				           05  TITLE PIC X(70) VALUE 'ABCDEFGHIJKLMNOPQRSTUVWXYZABCDEFGHIJKLMNOPQRS
				      -    'TUVWXYZ'.
				           05  AMOUNTS COMP-3.
				               10  AMOUNT-1 PIC S9(3).
				               10  AMOUNT-2 PIC S9(4).
				           05  SIGNED-GROUP SIGN TRAILING SEPARATE.
				               10  ZONED PIC S9(3).
				               10  PACKED PIC S9(3) COMP-3.
				               10  TEXT-1 PIC X(2).
				""");
		CobolItem record = copybook.record("REC");
		assertEquals(Usage.PACKED_DECIMAL, record.find("AMOUNT-1").getUsage());
		assertEquals(2, record.find("AMOUNT-1").getLength());
		assertEquals(3, record.find("AMOUNT-2").getLength());
		assertEquals(4, record.find("ZONED").getLength());
		assertTrue(record.find("ZONED").isSignSeparate());
		assertEquals(2, record.find("PACKED").getLength());
		assertEquals(70 + 5 + 4 + 2 + 2, record.getLength());
		assertEquals("PIC X(2)", record.find("TEXT-1").getDeclaration());
		assertNull(record.find("TITLE").getRemarks());
	}

	@Test
	void rejectsLayoutsThatAreNotComputed() {
		for (String source : List.of("01 R. 05 A PIC X. 05 B PIC S9(4) COMP SYNC.",
				"01 R. 05 N PIC 9. 05 T OCCURS 1 TO 3 DEPENDING ON N. 10 V PIC X. 05 AFTER PIC X.",
				"01 R. 05 A PIC X(2). 05 B REDEFINES A PIC X(3).",
				"01 R. 05 A PIC X(2). 05 B PIC X. 05 C REDEFINES A PIC X.", "01 R. 05 A PIC 9(19) COMP.",
				"01 R. 05 A PIC X COMP-3.", "01 R. 05 A PIC X SIGN LEADING.", "01 R. 05 A PIC 9(2)",
				"01 R. 05 A USAGE POINTER.", "01 R. 05 A PIC 9 OCCURS 2 TO 1 DEPENDING ON A.")) {
			assertThrows(CommandException.class, () -> new CobolCopybookParser()
					.setSourceFormat(CobolCopybookParser.SourceFormat.FREE).parse(source), source);
		}
	}

	private static void assertLayout(CobolItem record, String name, int offset, int length, Category category,
			Usage usage) {
		CobolItem item = record.find(name);
		assertEquals(offset, item.getOffset(), name + " offset");
		assertEquals(length, item.getLength(), name + " length");
		assertEquals(category, item.getCategory(), name + " category");
		if (usage != null) {
			assertEquals(usage, item.getUsage(), name + " usage");
		}
	}
}
