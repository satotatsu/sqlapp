# Access complex columns

Status: design agreed, implementation deferred (2026-11-22).

## Background

Access 2007+ (ACCDB only) stores multi-valued cells as Jackcess
`DataType.COMPLEX_TYPE`:

- Attachment fields (file name, file type, encoded file data, timestamp, flags)
- Multi-value lookup fields (one value column of the base type)
- Version history of append-only long text (value and modification date)

The table column holds only a `ComplexValueForeignKey`. Values are stored one to
many in a hidden flat table `f_<GUID>_<column>`, registered in
`MSysComplexColumns` and built from the `MSysComplexType_*` templates.
`Attachment.getFileData()` decodes the stored file data;
`getEncodedFileData()` returns the stored form.

Current sqlapp behavior: the schema maps the column to `BLOB`
(`MdbColumnSupport`), `MdbRowIteratorHandler` throws when reading values, and
`MdbJackcessSupport` rejects rebuilding the type. The migration assessment has no
dedicated finding, so a target `BLOB` column can be created that the data
transfer cannot fill.

## Agreed design

- Expand each complex column into a child table: parent primary key columns plus
  a sequence, a foreign key to the parent, and fixed columns per kind
  (attachment / multi-value / version history). Drop the column from the parent
  table. The Access complex value key is not carried to the target.
- Multi-value columns may later map to an array (`Column.arrayDimension`) on
  targets that support arrays, as an option on top of the child-table default.
  Access multi-values are unordered, so a deterministic order is needed.
- Child table names: `<table>_<column>` is only the initial default. Track the
  source by a stable identifier in the child table specifics, persist the
  resolved name in the migration plan or settings, and allow an explicit name.
  Renaming the source table or column must not change or break the mapping.
- Interim step (optional): map `COMPLEX_TYPE` to `OTHER` and report each complex
  column and its kind in the assessment.

## Memo: verification needed before implementation

Jackcess 5.2.0 cannot create complex columns ("Complex column creation is not
yet implemented"), so test files must be created with Microsoft Access, which is
not available in the current environment. Check with Access:

- Whether renaming the table or the complex column changes the flat table name
  `f_<GUID>_<column>` and `MSysComplexColumns.ColumnName`
- Which identifier stays stable across renames (`ComplexID`, flat table object
  id) and can be used as the source identifier
- Attachment, multi-value and append-only memo samples in ACCDB, kept as test
  resources, including file data that Access compresses and file types it does not
