# Schema viewpoints

Schema viewpoints define stable, reusable subsets of the sqlapp Schema model.
The same YAML file can select tables for HTML documentation and legacy loader
generation.

```yaml
format: sqlapp-schema-viewpoints
version: 1
viewpoints:
  - id: company-migration
    name: Company migration
    description: Tables used by the company hierarchy migration
    color: "#dbeafe"
    tables:
      - PUBLIC.COMPANY_MASTER
      - PUBLIC.EMPLOYEE_LIST

  - id: payroll
    name: Payroll
    description: Tables used by payroll processing
    tables:
      - PUBLIC.EMPLOYEE_LIST
      - PUBLIC.PAYROLL_INFO
```

`id` values are public configuration identifiers and must be unique and stable.
Table references may be `table`, `schema.table`, or
`catalog.schema.table`. Unqualified names are accepted only when they resolve
to exactly one table. Missing and ambiguous references are errors. A table may
belong to any number of viewpoints.

## HTML documentation

Configure `GenerateHtmlDocsCommand` with `viewpointsFile`. All Catalog tables
remain in the generated documentation. Each viewpoint gets its own relationship
tab, logical-name/full-column ER diagram, and description on the relationship
page. The Catalog-wide tab retains the existing compact/full and
physical/logical switches. A table detail page also
shows every viewpoint containing that table, together with the corresponding
description and ER diagram.

Set `viewpointId` to generate only one viewpoint section. The corresponding
Gradle task exposes the same properties.

## Legacy loader generation

`GenerateLegacyRdbLoaderCommand` and `GenerateLegacyRdbLoaderTask` select one
viewpoint using `viewpointsFile` and `viewpointId`. Required hierarchy
ancestors are added by default. Set the Loader-specific
`includeViewpointAncestors` property to `false` to make an omitted ancestor an
error.

The generated load plan records:

- the viewpoint ID;
- the viewpoint file path and fingerprint;
- the directly resolved table identities;
- the final data-set IDs after ancestor closure.

`LoadLegacyHierarchyCommand` verifies the recorded viewpoint-file fingerprint,
so a changed selection cannot be applied silently to an existing load plan.
