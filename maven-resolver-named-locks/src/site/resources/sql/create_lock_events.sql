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

create table lock_events as
select file, timestamp, substr(thread, 2, length(thread)-2) as thread,
(case
when message like 'Acquiring%'
    then 'acquire'
when message like 'Releasing%'
    then 'release'
when message like 'Failed to acquire%'
    then 'acquire_failed'
end) as lock_event,
(case
when message like '%write%'
    then 'write'
when message like '%read%'
    then 'read'
end) as lock_mode,
(substr(
    substr(message, instr(message, '''') + 1), 1,
    instr(substr(message, instr(message, '''') + 1), '''') - 1)
) as lock_name
from lock_history;
