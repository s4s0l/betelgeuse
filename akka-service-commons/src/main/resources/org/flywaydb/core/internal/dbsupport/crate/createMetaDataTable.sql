--
-- Copyright 2010-2017 Boxfuse GmbH
--
-- Licensed under the Apache License, Version 2.0 (the "License");
-- you may not use this file except in compliance with the License.
-- You may obtain a copy of the License at
--
--         http://www.apache.org/licenses/LICENSE-2.0
--
-- Unless required by applicable law or agreed to in writing, software
-- distributed under the License is distributed on an "AS IS" BASIS,
-- WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
-- See the License for the specific language governing permissions and
-- limitations under the License.
--

CREATE TABLE "${schema}"."${table}" (
    "installed_rank" INT NOT NULL,
    "version" string,
    "description" string NOT NULL,
    "type" string NOT NULL,
    "script" string NOT NULL,
    "checksum" INTEGER,
    "installed_by" string NOT NULL,
    "installed_on" AS CURRENT_TIMESTAMP,
    "execution_time" INTEGER NOT NULL,
    "success" BOOLEAN NOT NULL,
    primary key (installed_rank)
);
