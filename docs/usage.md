---
title: Usage
layout: doc
---
# Usage

## Before you begin

RDF4J repositories represent just configured connectors to the particular RDF storage. The repositories are always created and persisted within the actual context. RDF4J Console repository configuration is persisted under the actual user home directory. Repositories created through RDF4J Workbench exist within the actually connected RDF4J Server context only.

Halyard Datasets with all the RDF data are persisted within the HBase tables. Corresponding Halyard Dataset can be optionally created together with repository creation.

Multiple repositories configured in various RDF4J Servers or in multiple RDF4J Consoles can share one common Halyard Dataset and so pint to the same HBase table.

**Deletion of the repository** from one particular RDF4J Server or RDF4J Console does not delete Halyard Dataset and so it does not affect the data and other users. However **clearing** the repository or **deletion of the statements** has global effect for all users.

## Create Repository

### HBase Repository Settings

* **Repository ID** - is mandatory and may correspond to the HBase table name
* **Repository title** - is optional
* **HBase Table Name** - may be left empty when the table name corresponds to the Rpository ID
* **Create HBase Table if missing** - table presplit bits are ignored in case the table is not created
* **HBase Table presplit bits** - keep the default 0 unless you expect a very big dataset
* **Use Halyard Push Evaluation Strategy** - may be set to false to fallback to the default RDF4J Evaluation Strategy implementation
* **Query Evaluation Timeout** - may be adjusted or set to 0, however it creates a risk of resources exhaustion

### With RDF4J Console

```
> create hbase
Please specify values for the following variables:
Repository ID: testRepo
Repository title:
HBase Table Name:
Create HBase Table if missing (true|false) [true]:
HBase Table presplit bits [0]:
Use Halyard Push Evaluation Strategy (true|false) [true]:
Query Evaluation Timeout [180]:
Repository created
```

### With RDF4J Workbench

![RDF4J Workbench - New Repository](img/new_repo.png)

## Connect to Existing Repository

### From RDF4J Console

```
> open testRepo
Opened repository 'testRepo'
```

### From RDF4J Workbench

Just select the repository from the list of repositories.

Newly created repository is automatically connected in RDF4J Workbench.


## Load RDF Data

### With Halyard Bulk Load

```
> ./bulkload /my_hdfs_path/my_triples.owl /my_hdfs_temp_path testRepo
impl.YarnClientImpl: Submitted application application_1458475483810_40875
mapreduce.Job: The url to track the job: http://my_app_master/proxy/application_1458475483810_40875/
mapreduce.Job:  map 0% reduce 0%
mapreduce.Job:  map 100% reduce 0%
mapreduce.Job:  map 100% reduce 100%
mapreduce.Job: Job job_1458475483810_40875 completed successfully
INFO: Bulk Load Completed..
```
Note: Before Bulk Load of a very large datasets into a new HBase table it is wise to use Halyard PreSplit. Halyard PreSplit calculates HBase table region splits and creates HBase table for optimal following Bulk Load process. 

### With Halyard Hive Load

```
> ./hiveload -Dhalyard.rdf.mime.type='application/n-triples' -Dhalyard.hive.data.column.index=3 my_hive_table /my_hdfs_temp_path testRepo
impl.YarnClientImpl: Submitted application application_1514793734614_41673
mapreduce.Job: The url to track the job: http://my_app_master/proxy/application_1514793734614_41673/
mapreduce.Job:  map 0% reduce 0%
mapreduce.Job:  map 100% reduce 0%
mapreduce.Job:  map 100% reduce 100%
mapreduce.Job: Job job_1514793734614_41673 completed successfully
INFO: Hive Load Completed..
```
Note: skipping a lot of debugging information from the Map Reduce execution

### With RDF4J Console

```
testRepo> load /home/user/my_triples.owl
Loading data...
Data has been added to the repository (2622 ms)
```

### With RDF4J Workbench

![RDF4J Workbench - Add RDF](img/add_rdf.png)

### With RDF4J Server SPARQL Endpoint REST APIs

```
PUT /rdf4j-server/repositories/testRepo/statements HTTP/1.1
Content-Type: application/rdf+xml;charset=UTF-8

[RDF/XML ENCODED RDF DATA]
```

## SPARQL Federated Queries Across Datasets

Each Halyard dataset represents isolated graph space (a standalone triplestore). To SPARQL query across multiple datasets it is possible to:

1. Merge all the required datasets into one, as described later in this document.
2. Or use SPARQL SERVICE to construct federated queries across multiple datasets.

Halyard resolves another directly accessible HBase tables (datasets) as federation services. Halyard service URL for each dataset is constructed from Halyard prefix `http://merck.github.io/Halyard/ns#` and table name.

For example we have datasets `dataset1` and `dataset2`. While querying dataset `dataset1` we can use following SPARQL query to access also data from dataset `dataset2`:

```
PREFIX halyard: <http://merck.github.io/Halyard/ns#>

SELECT *
  WHERE {
    SERVICE halyard:dataset2 {
      ?s ?p ?o.
    }
  }
```

The above query can be used any of the above described ways or tools (Console, Workbench, REST API, Halyard Update, Export or Parallel Export). No other federated service types are recognised.

## SPARQL Update

### With Halyard Update

```
./update -s testRepo -q 'insert {?s ?p ?o} where {?s ?p ?o}'
```

### With RDF4J Console

```
testRepo> sparql
enter multi-line SPARQL query (terminate with line containing single '.')
insert {?s ?p ?o} where {?s ?p ?o}
.
Executing update...
Update executed in 800 ms
```

### With RDF4J Workbench

![RDF4J Workbench - SPARQL Update](img/update.png)

### With RDF4J Server SPARQL Endpoint REST APIs

```
POST /rdf4j-server/repositories/testRepo/statements HTTP/1.1
Content-Type: application/x-www-form-urlencoded

update=INSERT%20{?s%20?p%20?o}%20WHERE%20{?s%20?p%20?o}
```

## SPARQL Query and Export Data

### With Halyard Export

```
> ./export -s testRepo -q 'select * where {?s ?p ?o}' -t file:///my_path/my_export.csv
INFO: Query execution started
INFO: Export finished
```
Note: additional debugging information may appear in the output of the export execution

### With Halyard Parallel Export

```
> ./pexport -Dmapreduce.job.maps=10 -s testRepo -q 'PREFIX halyard: <http://merck.github.io/Halyard/ns#> select * where {?s ?p ?o . FILTER (halyard:parallelSplitBy (?s))}' -t hdfs:///my_path/my_export{0}.csv
impl.YarnClientImpl: Submitted application application_1572718538572_94727
mapreduce.Job: The url to track the job: http://my_app_master/proxy/application_1572718538572_94727/
mapreduce.Job:  map 0% reduce 0%
mapreduce.Job:  map 100% reduce 0%
mapreduce.Job: Job job_1572718538572_94727 completed successfully
INFO: Parallel Export Completed..
```

### With RDF4J Console

```
testRepo> sparql
enter multi-line SPARQL query (terminate with line containing single '.')
select * where {?s ?p ?o} limit 10
.
Evaluating SPARQL query...
+------------------------+------------------------+------------------------+
| s                      | p                      | o                      |
+------------------------+------------------------+------------------------+
| :complexion            | rdfs:label             | "cor da pele"@pt       |
| :complexion            | rdfs:label             | "complexion"@en        |
| :complexion            | rdfs:subPropertyOf     | dul:hasQuality         |
| :complexion            | prov:wasDerivedFrom    | <http://mappings.dbpedia.org/index.php/OntologyProperty:complexion>|
| :complexion            | rdfs:domain            | :Person                |
| :complexion            | rdf:type               | owl:ObjectProperty     |
| :complexion            | rdf:type               | rdf:Property           |
| :Document              | rdfs:comment           | "Any document"@en      |
| :Document              | rdfs:label             | "\u30C9\u30AD\u30E5\u30E1\u30F3\u30C8"@ja|
| :Document              | rdfs:label             | "document"@en          |
+------------------------+------------------------+------------------------+
10 result(s) (51 ms)
```

### With RDF4J Workbench

![RDF4J Workbench - SPARQL Query](img/query.png)

![RDF4J Workbench - Query Result](img/result.png)


### With RDF4J Server SPARQL Endpoint REST APIs

```
GET /rdf4j-server/repositories/testRepo?query=select+*+where+%7B%3Fs+%3Fp+%3Fo%7D HTTP/1.1
Accept: application/sparql-results+xml, */*;q=0.5
```

## Delete Statements

### With RDF4J Update

```
./update -s testRepo -q 'delete {?s ?p ?o} where {?s ?p ?o}'
```

### With RDF4J Workbench

![RDF4J Workbench - Remove Statements](img/remove.png)

### With RDF4J Server SPARQL Endpoint REST APIs

```
DELETE /rdf4j-server/repositories/testRepo/statements?subj=&pred=&obj= HTTP/1.1

```

## Clear Repository

### With RDF4J Console

```
testRepo> clear
Clearing repository...
```

### With RDF4J Workbench

![RDF4J Workbench - Clear All Statements](img/clear.png)


### With RDF4J Server SPARQL Endpoint REST APIs

```
DELETE /rdf4j-server/repositories/testRepo/statements HTTP/1.1

```

## HBase Shell Dataset Operations

### Snapshot Halyard Dataset
```
> hbase shell
HBase Shell; enter 'help<RETURN>' for list of supported commands.
Type "exit<RETURN>" to leave the HBase Shell
Version 1.1.2.2.4.2.0-258

hbase(main):001:0> snapshot 'testRepo', 'testRepo_my_snapshot'
0 row(s) in 36.3380 seconds
```

### Clone Halyard Dataset from Snapshot
```
> hbase shell
HBase Shell; enter 'help<RETURN>' for list of supported commands.
Type "exit<RETURN>" to leave the HBase Shell
Version 1.1.2.2.4.2.0-258

hbase(main):001:0> clone_snapshot 'testRepo_my_snapshot', 'testRepo2'
0 row(s) in 31.1590 seconds
```

### Export Halyard Dataset Snapshot
```
> hbase org.apache.hadoop.hbase.snapshot.ExportSnapshot -snapshot testRepo_my_snapshot -copy-to /my_hdfs_export_path
2016-04-28 09:01:07,019 INFO  [main] snapshot.ExportSnapshot: Loading Snapshot hfile list
2016-04-28 09:01:07,427 INFO  [main] snapshot.ExportSnapshot: Copy Snapshot Manifest
2016-04-28 09:01:11,704 INFO  [main] impl.YarnClientImpl: Submitted application application_1458475483810_41563
2016-04-28 09:01:11,826 INFO  [main] mapreduce.Job: The url to track the job: http://my_app_master/proxy/application_1458475483810_41563/
2016-04-28 09:01:19,956 INFO  [main] mapreduce.Job:  map 0% reduce 0%
2016-04-28 09:01:29,031 INFO  [main] mapreduce.Job:  map 100% reduce 0%
2016-04-28 09:01:29,039 INFO  [main] mapreduce.Job: Job job_1458475483810_41563 completed successfully
2016-04-28 09:01:29,158 INFO  [main] snapshot.ExportSnapshot: Finalize the Snapshot Export
2016-04-28 09:01:29,164 INFO  [main] snapshot.ExportSnapshot: Verify snapshot integrity
2016-04-28 09:01:29,193 INFO  [main] snapshot.ExportSnapshot: Export Completed: testRepo_my_snapshot
```
Note: skipping a lot of debugging information from the Map Reduce excution

### Bulk Merge of Multiple Datasets

1. Snapshot and Export all Halyard Datasets you want to merge <br>
  (see the above described processes)
2. Merge the exported files <br>
  Exported HBase files are organised under the target folder in the following structure:
  `/archive/data/<table_namespace>/<table_name>/<region_id>/<column_family>/<region_files>`
  We need to merge the region files under each column family from all exports into a single structure. <br>
  As Halyard Dataset currently contains the only `e` column family, it can be achieved for example by following commands: <br>
  `> hdfs dfs -mkdir -p /my_hdfs_merged_path/e` <br>
  `> hdfs dfs -mv /my_hdfs_export_path/archive/data/*/*/*/e/* /my_hdfs_merged_path/e`
3. Create a new Halyard Dataset <br>
  (see the above described process)
4. Load the merged files <br>
  `> hbase org.apache.hadoop.hbase.mapreduce.LoadIncrementalHFiles /my_hdfs_merged_path new_dataset_table_name`

### Boost Query Performance by Making the Dataset Read-only
```
> hbase shell
HBase Shell; enter 'help<RETURN>' for list of supported commands.
Type "exit<RETURN>" to leave the HBase Shell
Version 1.1.2.2.4.2.0-258

hbase(main):001:0> alter 'testRepo', READONLY => 'true'
Updating all regions with the new schema...
0/3 regions updated.
3/3 regions updated.
Done.
0 row(s) in 2.2210 seconds
```

### Disable/Enable Unused Dataset to Save HBase Resources
```
> hbase shell
HBase Shell; enter 'help<RETURN>' for list of supported commands.
Type "exit<RETURN>" to leave the HBase Shell
Version 1.1.2.2.4.2.0-258

hbase(main):001:0> disable 'testRepo'
0 row(s) in 1.3040 seconds
```

```
> hbase shell
HBase Shell; enter 'help<RETURN>' for list of supported commands.
Type "exit<RETURN>" to leave the HBase Shell
Version 1.1.2.2.4.2.0-258

hbase(main):001:0> enable 'testRepo'
0 row(s) in 1.2130 seconds
```

### Delete Dataset

```
> hbase shell
HBase Shell; enter 'help<RETURN>' for list of supported commands.
Type "exit<RETURN>" to leave the HBase Shell
Version 1.1.2.2.4.2.0-258

hbase(main):001:0> disable 'testRepo'
0 row(s) in 1.2750 seconds

hbase(main):002:0> drop 'testRepo'
0 row(s) in 0.2070 seconds
```
