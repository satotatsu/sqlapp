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
        assertTrue(task.countRowsBeforeInsert.get())
        assertFalse(task.preview.get())
        assertEquals(10, task.previewLimit.get())
        task.preview.set(true)
        task.previewLimit.set(3)
        task.countRowsBeforeInsert.set(false)
        assertFalse(task.randomSeed.present)
        assertFalse(task.baseTime.present)
        task.directory.set(testProjectDir)
        task.includes.set([])
        task.excludes.set([])
        task.randomSeed.set(42L)
        task.baseTime.set('2026-01-02T03:04:00')
        def command = new GenerateDataInsertCommand()
        task.beforeRun(command)
        assertFalse(command.countRowsBeforeInsert)
        assertTrue(command.preview)
        assertEquals(3, command.previewLimit)
        assertEquals(42L, command.randomSeed)
        assertEquals(LocalDateTime.of(2026, 1, 2, 3, 4), command.baseTime)
    }
    @Test
    void previewExampleRegistersTaskWithExistingConfiguration() {
        settingsFile.text = "rootProject.name = 'generator-preview-example'"
        buildFile.text = """
plugins { id 'com.sqlapp.db' }
tasks.register('generateData', com.sqlapp.gradle.plugins.GenerateDataTask) {
    directory.set(layout.projectDirectory)
    includeSchemas.add('PUBLIC')
}
tasks.register('verifyPreviewExample') {
    doLast {
        def previewTask = tasks.named('previewData').get()
        assert previewTask.preview.get()
        assert previewTask.previewLimit.get() == 10
        assert previewTask.includeSchemas.get() == ['PUBLIC']
        assert previewTask.directory.get().asFile == projectDir
        println 'preview example configured'
    }
}
"""
        def initScript = new File('../docs/examples/generator-preview/preview.init.gradle').canonicalFile
        assertTrue(initScript.isFile())
        def result = org.gradle.testkit.runner.GradleRunner.create()
            .withProjectDir(testProjectDir).withPluginClasspath()
            .withArguments('-I', initScript.absolutePath, 'verifyPreviewExample', '--stacktrace').build()
        assertTrue(result.output.contains('preview example configured'))
    }

}
