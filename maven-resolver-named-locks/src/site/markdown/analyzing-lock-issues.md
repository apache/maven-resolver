# Analyzing Lock Issues

<!--
Licensed to the Apache Software Foundation (ASF) under one
or more contributor license agreements.  See the NOTICE file
distributed with this work for additional information
regarding copyright ownership.  The ASF licenses this file
to you under the Apache License, Version 2.0 (the
"License"); you may not use this file except in compliance
with the License.  You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing,
software distributed under the License is distributed on an
"AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
KIND, either express or implied.  See the License for the
specific language governing permissions and limitations
under the License.
-->

This guide will show you how to collect and analyze lock issues to report with us.

## Prerequisites

Make sure that the following applies:

- Bash or Bash-compatible shell installed
- BSD or GNU userland installed
- SQLite installed
- At least Maven 3.9.0

Prepare your environment:

- Set Maven home to `MAVEN_HOME`
- Select a lock factory, a name mapper, a parallel job count (processes), a build count and set
  `LOCK_FACTORY`, `NAME_MAPPER`, `JOB_COUNT`, `BUILD_COUNT`
- Set your root working directory to `ROOT`
- Set your project name with `PROJECT_NAME`
- Obtain your project source into `$PROJECT_NAME-1` to `$PROJECT_NAME-$JOB_COUNT`
- Decide whether each job and build will have its own distinct local repository or a shared one
  and set it to `LOCAL_REPO`
- Download [`schema.sql`](./sql/schema.sql), [`create_lock_events.sql`](./sql/create_lock_events.sql),
  [`create_lock_workflows.sql`](./sql/create_lock_workflows.sql) and set `SCHEMA_FILE`,
  `CREATE_LOCK_EVENTS_FILE`, `CREATE_LOCK_WORKFLOWS_FILE` to their respective locations

TIP: See [overview](./index.html) of possible values for `LOCK_FACTORY` and `NAME_MAPPER`.

## Running Builds

Open `$JOB_COUNT` terminals and change into the `$ROOT/$PROJECT_NAME-1` to `$ROOT/$PROJECT_NAME-$JOB_COUNT`
directories. Now start each job in a build loop:
```
$ for build in $(eval echo {1..$BUILD_COUNT}); do \
    # Only if dedicated
    rm -rf $LOCAL_REPO; \
    $MAVEN_HOME/bin/mvn clean compile -T1C -B -DskipTests -Dmaven.repo.local=$LOCAL_REPO \
    -Dorg.slf4j.simpleLogger.showThreadName=true -Dorg.slf4j.simpleLogger.showDateTime=true \
    -Dorg.slf4j.simpleLogger.log.org.eclipse.aether=trace -l $LOCK_FACTORY-$NAME_MAPPER-$build.log \
    # Adjust to your needs
    -Daether.connector.basic.threads=8 -Daether.metadataResolver.threads=8 \
    # If downloads take longer consider to increase
    -Daether.syncContext.named.time=120 \
    -Daether.syncContext.named.factory=$LOCK_FACTORY -Daether.syncContext.named.nameMapper=$NAME_MAPPER; \
  done
```

Wait for all jobs to complete.

TIP: If you don't need parallel (multiprocess) builds, you can set `JOB_COUNT` to 1.

## Analyzing Builds

We now need to extract, transform and load the data (ETL) into a SQLite database.

Change back to the `$ROOT` directory and collect all log files with build failures:
```
$ BUILD_FAILURE_LIST=$(mktemp)
$ grep -l -r "BUILD FAILURE" $PROJECT_NAME-* > $BUILD_FAILURE_LIST
```
Run extraction and transformation:
```
$ while read log_file; do \
    grep -e Acquiring -e Releasing -e "Failed to acquire" $log_file | \
      sed -e 's# \[#;[#g' -e 's#\] #];#' -e "s#^#$log_file;#" | \
      sed 's#\[TRACE\];##'; \
  done < $BUILD_FAILURE_LIST > $LOCK_FACTORY-$NAME_MAPPER-locks.txt
```
Load and process the data in SQLite:
```
$ SCRIPT=$(mktemp)
$ cat > $SCRIPT <<EOT
.read $SCHEMA_FILE
.separator ;
.import $LOCK_FACTORY-$NAME_MAPPER-locks.txt lock_history
.read $CREATE_LOCK_EVENTS_FILE
.read $CREATE_LOCK_WORKFLOWS_FILE
.save $LOCK_FACTORY-$NAME_MAPPER-locks.db
EOT
$ sqlite3 -batch < $SCRIPT
$ rm -f $SCRIPT
```

## Uploading Data

Compress the SQLite database as well as the log files:
```
$ tar -caf $LOCK_FACTORY-$NAME_MAPPER-locks.db.xz $LOCK_FACTORY-$NAME_MAPPER-locks.db
$ tar -caf $LOCK_FACTORY-$NAME_MAPPER-logfiles.tar.xz -T $BUILD_FAILURE_LIST
$ rm -f $BUILD_FAILURE_LIST
```
and upload them to the JIRA issue.
