---
title: Get started
description: Run sqlapp through Gradle without an external database and choose the right plugin release.
---
# Get started

## Try the current implementation

Use Java 25 and the checked-in Gradle Wrapper from a checkout of this repository:

```shell
./gradlew --init-script docs/examples/offline-html/demo.init.gradle demoSqlapp
```

On Windows PowerShell:

```powershell
.\gradlew.bat --init-script docs/examples/offline-html/demo.init.gradle demoSqlapp
```

Open `build/docs/demo/index.html`. You get HTML and ER documentation, before/after
schema references and change SQL, plus actual HSQLDB migration and verification
reports. Fictional fixtures and in-memory databases make the example reproducible
without external credentials. The first build downloads dependencies.

You can also [browse these generated artifacts now](demos.md).

## Add the Gradle plugin to your workflow

The [Gradle setup guide](docs/gradle-plugin/getting-started.md) explains dialect and
JDBC dependencies, environment-based connection settings, metadata-only XML export
and HTML generation. The [task reference](docs/gradle-plugin/task-reference.md)
records inputs, outputs and database effects.

::: warning Source documentation and published versions
These guides describe the current source checkout. The source version `0.80.0`
has not been verified as a published plugin. The
[Plugin Portal](https://plugins.gradle.org/plugin/com.sqlapp.db) lists `0.30.0`;
do not assume it contains the current task API. Replace version placeholders
only with releases that actually provide the documented features. The repository
demo above runs the checked-out implementation directly.
:::

For embedded use, continue with the [Java API](docs/getting-started/java-api.md)
and [artifact selection](docs/artifacts.md). Review the
[database compatibility evidence](docs/compatibility.md) before accessing a database.
