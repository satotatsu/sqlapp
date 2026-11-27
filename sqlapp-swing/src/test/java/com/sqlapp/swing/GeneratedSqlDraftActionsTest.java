/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GeneratedSqlDraftActionsTest {
	@TempDir
	Path directory;

	private static class Fixture {
		final AtomicBoolean busy = new AtomicBoolean();
		final AtomicReference<String> status = new AtomicReference<>();
		final AtomicReference<Throwable> failure = new AtomicReference<>();
		final CountDownLatch done = new CountDownLatch(1);
		GeneratedSqlDraftActions actions;

		Fixture(GeneratedSqlDraftActions.Saver writer) throws Exception {
			SwingUtilities.invokeAndWait(() -> actions = new GeneratedSqlDraftActions(
					new ReadOnlyCodePanel(
							CodeEditors.create(org.fife.ui.rsyntaxtextarea.SyntaxConstants.SYNTAX_STYLE_SQL, false)),
					busy::get, value -> {
						busy.set(value);
						if (!value)
							done.countDown();
					}, status::set, failure::set, writer));
		}

		void await() throws Exception {
			assertTrue(done.await(5, TimeUnit.SECONDS));
		}
	}

	@Test
	void savesExactUtf8SnapshotAndEnablesEditorHandoffWithoutExecutingAnything() throws Exception {
		var fixture = new Fixture((file, text, inputs) -> new ReviewFileWriter().write(file, text, inputs));
		var destination = directory.resolve("draft.sql");
		var opened = new AtomicReference<Path>();
		String sql = "-- 変更SQL\nCREATE TABLE ITEMS(ID INTEGER)\n;\n";
		SwingUtilities.invokeAndWait(() -> {
			fixture.actions.setEditor(path -> {
				opened.set(path);
				return true;
			});
			fixture.actions.show(sql, List.of());
			fixture.actions.saveTo(destination);
			assertTrue(fixture.busy.get());
			assertFalse(fixture.actions.save.isEnabled());
		});
		fixture.await();
		assertEquals(sql, Files.readString(destination));
		assertNull(fixture.failure.get());
		SwingUtilities.invokeAndWait(() -> {
			assertTrue(fixture.actions.edit.isEnabled());
			fixture.actions.edit.doClick(0);
			assertEquals(destination.toAbsolutePath(), opened.get());
			fixture.actions.clear();
			assertFalse(fixture.actions.edit.isEnabled());
			assertFalse(fixture.actions.save.isEnabled());
		});
	}

	@Test
	void changedInputsDuringSaveDoNotPublishStaleEditorHandoffOrDuplicateSave() throws Exception {
		var entered = new CountDownLatch(1);
		var release = new CountDownLatch(1);
		var writes = new AtomicInteger();
		var fixture = new Fixture((file, text, inputs) -> {
			writes.incrementAndGet();
			entered.countDown();
			assertTrue(release.await(5, TimeUnit.SECONDS));
			new ReviewFileWriter().write(file, text, inputs);
		});
		var file = directory.resolve("previous.sql");
		SwingUtilities.invokeAndWait(() -> {
			fixture.actions.setEditor(path -> true);
			fixture.actions.show("OLD SQL", List.of());
			fixture.actions.saveTo(file);
		});
		assertTrue(entered.await(5, TimeUnit.SECONDS));
		SwingUtilities.invokeAndWait(() -> {
			fixture.actions.saveTo(directory.resolve("duplicate.sql"));
			fixture.actions.show("NEW SQL", List.of());
		});
		release.countDown();
		fixture.await();
		assertEquals(1, writes.get());
		assertEquals("OLD SQL", Files.readString(file));
		assertFalse(Files.exists(directory.resolve("duplicate.sql")));
		SwingUtilities.invokeAndWait(() -> {
			assertFalse(fixture.actions.edit.isEnabled());
			assertTrue(fixture.actions.save.isEnabled());
		});
		assertTrue(fixture.status.get().contains("previous SQL snapshot"));
	}

	@Test
	void inputAliasesCannotBeOverwrittenAndFailureKeepsDraftAvailableForRetry() throws Exception {
		var input = Files.writeString(directory.resolve("source.xml"), "protected schema");
		var alias = Files.createLink(directory.resolve("alias.sql"), input);
		var fixture = new Fixture((file, text, inputs) -> new ReviewFileWriter().write(file, text, inputs));
		SwingUtilities.invokeAndWait(() -> {
			fixture.actions.show("SQL", List.of(input));
			fixture.actions.saveTo(alias);
		});
		fixture.await();
		assertInstanceOf(IllegalArgumentException.class, fixture.failure.get());
		assertEquals("protected schema", Files.readString(input));
		SwingUtilities.invokeAndWait(() -> assertTrue(fixture.actions.save.isEnabled()));
	}

	@Test
	void invalidExtensionDoesNotStartAWrite() throws Exception {
		var writes = new AtomicInteger();
		var fixture = new Fixture((file, text, inputs) -> writes.incrementAndGet());
		SwingUtilities.invokeAndWait(() -> {
			fixture.actions.show("SQL", List.of());
			fixture.actions.saveTo(directory.resolve("draft.txt"));
		});
		assertFalse(fixture.busy.get());
		assertEquals(0, writes.get());
		assertTrue(fixture.status.get().contains(".sql"));
	}
}
