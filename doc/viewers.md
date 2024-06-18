
## User Flow

```mermaid
flowchart TB
  datasource -- edit --> datasource-input
  datasource-input -- schema --> schema-list
  datasource-input -- query --> query-editor
  datasource -- schema --> schema-list
  datasource -- query --> query-editor
  schema-list -- tables --> table-list
  table-list -- columns --> column-list
  table-list -- data --> datagrid
  datasource -- schema --> datomic-database-list
  datasource-input -- schema --> datomic-database-list
  datomic-database-list -- namespaces --> datomic-namespace-list
  datomic-database-list -- attributes --> datomic-attribute-list
  datomic-database-list -- query --> datomic-query-editor
  datomic-attribute-list -- data --> datomic-query-editor
  datomic-namespace-list -- attributes --> datomic-attribute-list
```
