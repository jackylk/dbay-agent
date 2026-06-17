# dbay-agent

DBay Agent contains the data intelligence layer that runs on top of DBay Lakebase:

- DataAgent
- Sources / Connectors
- Knowledge Base
- Memory Base
- Datalake / Ray / Notebook / Pipeline

This repo depends on `dbay.cloud` Lakebase APIs. It must not read Lakebase metadata tables directly.
DBay Agent owns its Knowledge, Memory, Datalake and Pipeline metadata in a
dedicated DBay Agent RDS instance.
