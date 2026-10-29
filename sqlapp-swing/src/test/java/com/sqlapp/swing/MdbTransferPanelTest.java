/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MdbTransferPanelTest {
	@TempDir
	Path directory;

	@Test
	void selectionBuildsSourceWithoutConnectingAndInvalidSelectionRevokesIt() throws Exception {
		var source = Files.writeString(directory.resolve("日本語 database.MDB"), "fixture");
		SwingUtilities.invokeAndWait(() -> {
			var calls = new AtomicInteger();
			var panel = new MdbTransferPanel((action, request, from, to) -> {
				calls.incrementAndGet();
				throw new AssertionError("Selection must not connect");
			}, null);
			panel.file.setText(source.toString());
			assertEquals(MdbSourceFile.jdbcUrl(source), panel.transfer.source.jdbcUrl.getText());
			assertEquals(MdbSourceFile.DRIVER, panel.transfer.source.driverClass.getText());
			assertEquals(panel.transfer.source.jdbcUrl.getText(), panel.csv.source.jdbcUrl.getText());
			assertFalse(panel.csv.source.jdbcUrl.isVisible());
			assertFalse(SwingUtilities.isDescendingFrom(panel.csv.sourceSchema, panel));
			panel.modes.setSelectedComponent(panel.csv);
			assertTrue(panel.selection.getText().contains("then export"));
			panel.csv.step.setSelectedIndex(1);
			assertFalse(panel.file.getParent().isVisible());
			panel.modes.setSelectedComponent(panel.transfer);
			assertTrue(panel.file.getParent().isVisible());
			assertFalse(panel.transfer.source.jdbcUrl.isVisible());
			assertFalse(panel.transfer.source.buildUrl.isVisible());
			assertFalse(SwingUtilities.isDescendingFrom(panel.transfer.sourceSchema, panel));
			assertNotNull(panel.file.getTransferHandler());
			assertTrue(panel.selection.getText().contains("No connection"));
			panel.transfer.source.jdbcUrl.setEnabled(false);
			assertFalse(panel.file.isEnabled());
			assertFalse(panel.browse.isEnabled());
			panel.transfer.source.jdbcUrl.setEnabled(true);
			assertTrue(panel.file.isEnabled());
			panel.file.setText(directory.resolve("missing.mdb").toString());
			assertEquals("", panel.transfer.source.jdbcUrl.getText());
			assertEquals("", panel.csv.source.jdbcUrl.getText());
			assertTrue(panel.selection.getText().contains("missing or unreadable"));
			assertFalse(panel.transfer.execute.isEnabled());
			panel.transfer.create.doClick(0);
			assertTrue(panel.transfer.status.getText().contains("source database file"));
			assertEquals(0, calls.get());
		});
	}

	@Test
	void validatesExtensionsMissingFilesAndUrlOptionInjectionBeforeOpening() throws Exception {
		var valid = Files.writeString(directory.resolve("source.accdb"), "fixture");
		var spec = new PlanningJdbc.Spec(MdbSourceFile.jdbcUrl(valid), "", MdbSourceFile.DRIVER);
		MdbSourceFile.validate(spec);
		assertThrows(IllegalArgumentException.class, () -> MdbSourceFile.jdbcUrl(directory.resolve("source.txt")));
		var unsafe = Files.writeString(directory.resolve("source;newDatabaseVersion=V2000.mdb"), "fixture");
		assertThrows(IllegalArgumentException.class, () -> MdbSourceFile.jdbcUrl(unsafe));
		Files.delete(valid);
		assertThrows(IllegalArgumentException.class, () -> MdbSourceFile.validate(spec));
		assertFalse(Files.exists(valid));
	}
}
