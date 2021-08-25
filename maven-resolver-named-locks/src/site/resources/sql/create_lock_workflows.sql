/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

create table lock_workflows as
select lock_held, file, timestamp as start_timestamp, "timestamp:1" as end_timestamp, thread, (lock_event || ' => ' || "lock_event:1") as lock_workflow, lock_mode, lock_name
from (
    select
        row_number() over (
            partition by a.file, a.timestamp, a.thread, a.lock_event, a.lock_mode, a.lock_name
           order by r.timestamp asc
        ) as rn,
        (r.timestamp - a.timestamp) as lock_held, a.*, r.*
    from lock_events a, lock_events r
    where r.file = a.file
        and r.thread = a.thread
        and r.timestamp >= a.timestamp
        and a.lock_event = 'acquire' and r.lock_event in ('release', 'acquire_failed')
        and a.lock_mode = r.lock_mode
        and a.lock_name = r.lock_name
)
where rn = 1
order by timestamp;
