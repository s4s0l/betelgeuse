# cratedbstartup
Swarm required: `docker swarm init`

```
#!/usr/bin/env bash
docker network create -d overlay crate-network
docker service create \
    --name crate \
    --network crate-network \
    --mode global \
    -e CRATE_HEAP_SIZE=1024m \
    --endpoint-mode vip \
    --update-parallelism 1 \
    --update-delay 60s \
    --publish 4200:4200 \
    --publish 4300:4300 \
    --publish 5432:5432 \
    --mount type=volume,source=crate-test-db,target=/data \
  crate:latest \
    crate \
    -Cpath.repo=/data/repos \
    -Cdiscovery.zen.ping.unicast.hosts=crate \
    -Cgateway.expected_nodes=1 \
    -Cdiscovery.zen.minimum_master_nodes=1 \
    -Cgateway.recover_after_nodes=1 \
    -Cnetwork.host=_site_ \
    -Clicense.enterprise=false
```

# copyright idea template

```
Copyright© $today.year the original author or authors.
  
Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```

And the regexpr:

`Copyright© ([0-9]{4}) the original author or authors.`


## workaroud for idea changing gradlew files every refresh:
`git update-index --assume-unchanged gradle/wrapper/gradle-wrapper.properties gradlew.bat gradle/wrapper/gradle-wrapper.jar`

to restore it:
`git update-index --no-assume-unchanged gradle/wrapper/gradle-wrapper.properties gradlew.bat gradle/wrapper/gradle-wrapper.jar`


## aspect j in tests from idea
vm parameters for scala test:
`-javaagent:build/agent/aspectjweaver-1.8.10.jar`

but befor it run `./gradlew exportAgent` it will download and extract aspectj to above location