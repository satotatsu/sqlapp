# sqlapp-elk-svg

Generate SVG ER diagrams from the shared Schema model:

```java
TableSvgCreator creator = new TableSvgCreator(SVGDrawMode.NORMAL, NameMode.LOGICAL);
TableSvgCreator.SVGResult result = creator.generateSchemaSvg(schemas);
String svg = result.getImage();
```

Use `generateSvg(tables)` for a table selection without schema backgrounds.
`NameMode.LOGICAL` uses display names, falling back to physical names, for schema,
table and column labels. `SVGDrawMode.SIMPLE` reduces column details.

Names, column details and URLs are supplied as plain, unescaped strings; the
renderer escapes XML text and attributes. This also applies to custom column
builders and `setUrlFunction` results: embedded markup is rendered as text.
Numeric SVG output is independent of the default formatting locale.

`setColumnFilterEnabled(false)` disables the column filter for rendering, table
height and relationship attachment positions. Schema canvas bounds include empty
schema backgrounds and respect `setPadding`; canvas dimensions may consequently
differ from older output. Public method signatures and configuration formats are
unchanged. Font metrics and ELK layout still affect coordinates and dimensions.
