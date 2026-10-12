package com.sqlapp.gradle.plugins

import static org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import com.sqlapp.data.db.command.generator.GenerateDataInsertCommand
import java.time.LocalDateTime

class GenerateDataTaskPropertiesTest extends AbstractTaskTest {
    @Test
    void mapsOptionalReproducibilityProperties() {
        def project = createProject(testProjectDir)
        def task = project.tasks.register('generateData', GenerateDataTask).get()
        assertFalse(task.randomSeed.present)
        assertFalse(task.baseTime.present)
        task.directory.set(testProjectDir)
        task.includes.set([])
        task.excludes.set([])
        task.randomSeed.set(42L)
        task.baseTime.set('2026-01-02T03:04:00')
        def command = new GenerateDataInsertCommand()
        task.beforeRun(command)
        assertEquals(42L, command.randomSeed)
        assertEquals(LocalDateTime.of(2026, 1, 2, 3, 4), command.baseTime)
    }
}
