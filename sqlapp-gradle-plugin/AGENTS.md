# sqlapp-gradle-plugin development instructions

These instructions supplement the repository root `AGENTS.md`.

## Task design

- Treat task names and properties as public user-facing APIs.
- Do not rename or remove them without an explicit compatibility plan.
- Expose command properties using the appropriate Gradle lazy type, such as
  `Property<T>`, `RegularFileProperty`, `DirectoryProperty` or
  `ListProperty<T>`.
- Declare accurate `@Input`, `@InputFile`, `@OutputDirectory`, optionality and
  path sensitivity annotations.
- Set sensible conventions for optional/default command behavior.
- Map properties in `beforeRun`; do not duplicate business logic.
- Consider supported Gradle versions when using APIs.

## Testing

- Test plugin task registration, conventions and property mapping.
- Use Gradle TestKit when task execution or plugin behavior requires it.
- Run the corresponding command tests as well as focused plugin tests.

## Documentation

- When adding or changing a Gradle task, update the task reference in this repository.
- For user-facing changes, add or update an executable example in `sqlapp-gradle-example`.
- Ensure that documented property names, types, conventions, required conditions, inputs, and outputs match the implementation.
- Before modifying `sqlapp-gradle-example`, confirm that the repository is within the authorized task scope. Otherwise, propose the required documentation changes without modifying it.