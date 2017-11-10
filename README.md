# betelgeuse
Opinionated akka utils and tool set for microservices.



# Extensions

## Core

* **-Dbg.info.instance=[NUM]** 1,2,3 so on
* **-Dbg.info.docker=[TRUE|FALSE]**

* **TimeoutActor**
        actor that will stop on inactive but gives a chance to declare what inactivity means

## Serialization

* **JacksonJsonSerializable** 
                            by default these are serialized by jackson serializer
* **DepricatedTypeWithMigrationInfo** 
                                old objects when deserialized with this 
                                are called to be able to upgade themselves to never 
                                version 

## Scharding

* **TimeoutShardedActor**
                    TimeoutActor for sharded actors (handles stop properly)
* **ShardedActor** 
                    Gives access to shard name and id in shard
## Persistence

* **utils.PersistenceId** 
                tostring of this creates properly formated pid for journal 
* **utils.PersistentShardedActor** 
                has properly implemented persistence ID for sharded actors
* **BetelgeuseEntityObject** 
                objects of scalike entities can use it to have proper schema 
                name and pool names
 

## Persistence - Crate

* **CrateScalikeJdbcImports**
                implicits for handling object <-> crate sql object mapping
* **CrateScalikeJdbcImports.CrateDbObject**                
* **CrateScalikeJdbcImports.CrateDbObjectMapper**
                Objects to be contained in EntityObjects that can be transformed into
                / from sql objects have to have companion object extending this and extend
                 CrateDbObject

## Persistence - Journal

* **JournalCallback** 
                events can implement it to have a call on restoration from db
* **DepricatedTypeWithMigrationInfo** 
                see serialization
                    
## Persistence - Journal - Crate

* **CrateScalikeJdbcImports.CrateDbObject** - this events will be changed to 
                database objects
                 
* **JacksonJsonSerializable** 
                if events implement this they will go to json column as string unless
                object mapping was possible
                
                
## PATTERNS: 

* **GlobalConfigFactory**
    Complicated - distribution of some config/setting something from central configuration to
    local or remote receivers, with local storage on receivers
    
* **AsyncInitActor**
    Actor that requires some initialization phase, probably not needed when using FSM
    but separates init phase from all the rest of the stuff and in persistence actors 
    (extends PersistentActor with AsyncInitActor) gives you ability to init before 
    loading stuff from db