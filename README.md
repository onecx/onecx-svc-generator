# OneCX SVC Generator

Generator of OneCX-like Quarkus services, based on a custom template engine and OpenAPI generation.
Java version 25, Quarkus 3.2, OpenAPI Generator 7.0.1.

## 1. What it does

- creates a OneCX-like Quarkus service layout,
- generates a permission-aware OpenAPI contract,
- generates controllers + mappers + domain layer,
- relies on Maven/OpenAPI generation for REST interfaces and DTOs.

## 2. structure
## Project Structure

```text
onecx-svc-generator/
├─ .github/
│  └─ workflows/
│     └─ release.yml
├─ examples/
│  └─ model.yaml
├─ src/
│  ├─ main/
│  │  ├─ java/
│  │  │  └─ org/tkit/onecx/onecxsvcgen/
│  │  │     ├─ Main.java
│  │  │     ├─ commands/
│  │  │     │  ├─ AddEntityCommand.java
│  │  │     │  ├─ BatchModelCommand.java
│  │  │     │  └─ CreateSvcCommand.java
│  │  │     ├─ model/
│  │  │     │  ├─ ApiDef.java
│  │  │     │  ├─ EntityDef.java
│  │  │     │  ├─ FieldDef.java
│  │  │     │  └─ RelationDef.java
│  │  │     └─ service/
│  │  │        ├─ BuildService.java
│  │  │        ├─ GitHubActionsService.java
│  │  │        ├─ LiquibaseChangelogService.java
│  │  │        ├─ ModelParserService.java
│  │  │        ├─ NamingService.java
│  │  │        ├─ OpenApiService.java
│  │  │        └─ TemplateService.java
│  │  └─ resources/
│  │     ├─ application.properties
│  │     └─ templates/
│  │        ├─ entity/
│  │        │  ├─ Controller.java.tpl
│  │        │  ├─ DAO.java.tpl
│  │        │  ├─ Entity.java.tpl
│  │        │  ├─ ExternalController.java.tpl
│  │        │  ├─ ExternalExceptionMapper.java.tpl
│  │        │  ├─ ExternalMapper.java.tpl
│  │        │  ├─ InternalExceptionMapper.java.tpl
│  │        │  ├─ Liquibase-changelog.xml.tpl
│  │        │  ├─ Liquibase-changeset.xml.tpl
│  │        │  ├─ Mapper.java.tpl
│  │        │  ├─ NonRootDAO.java.tpl
│  │        │  └─ Service.java.tpl
│  │        ├─ github/
│  │        │  ├─ dependabot.yml.tpl
│  │        │  └─ workflows/
│  │        │     ├─ build.yml.tpl
│  │        │     ├─ build-branch.yml.tpl
│  │        │     ├─ build-pr.yml.tpl
│  │        │     ├─ build-pr-merge.yml.tpl
│  │        │     ├─ build-release.yml.tpl
│  │        │     ├─ create-fix-branch.yml.tpl
│  │        │     ├─ create-new-build.yml.tpl
│  │        │     ├─ create-release.yml.tpl
│  │        │     ├─ documentation.yml.tpl
│  │        │     ├─ security.yml.tpl
│  │        │     └─ sonar-pr.yml.tpl
│  │        ├─ svc-project/
│  │        │  ├─ Chart.yaml.tpl
│  │        │  ├─ Dockerfile.jvm.tpl
│  │        │  ├─ Dockerfile.native.tpl
│  │        │  ├─ application.properties.tpl
│  │        │  ├─ gitignore.tpl
│  │        │  ├─ openapi-skeleton.yaml.tpl
│  │        │  ├─ pom.xml.tpl
│  │        │  └─ values.yaml.tpl
│  │        └─ test/
│  │           ├─ AbstractTest.java.tpl
│  │           ├─ ControllerIT.java.tpl
│  │           ├─ ControllerTest.java.tpl
│  │           ├─ ExternalControllerIT.java.tpl
│  │           └─ ExternalControllerTest.java.tpl
├─ .gitignore
├─ LICENSE
├─ pom.xml
└─ README.md
```


## 3. Local workflow

### 3.1. Build the generator
```bash
cd cd ../onecx-svc-generator/generator
mvn clean package -Dquarkus.package.type=uber-jar
```

### 3.2. Generate a new service
```bash
cd ../
java -jar onecx-svc-generator/generator/target/onecx-svc-generator-1.0.0-runner.jar create-svc   \
  --name onecx-demo-svc   \
  --group org.tkit.onecx   \
  --package org.tkit.onecx.demo
  --parent-version 3.2.0
```
#### with autobuild - recommended for development, as it compiles the generated code after each change:
```bash 
cd ../

java -jar onecx-svc-generator/generator/target/onecx-svc-generator-1.0.0-runner.jar create-svc \
  --name onecx-demo-svc \
  --group org.tkit.onecx \
  --package org.tkit.onecx.demo \
  --build true
```

#### with autobuild and stable specific parent version - recommended for development, and this parent version is last with java 21:
```bash 
cd ../

java -jar onecx-svc-generator/generator/target/onecx-svc-generator-1.0.0-runner.jar create-svc \
  --name onecx-demo-svc \
  --group org.tkit.onecx \
  --package org.tkit.onecx.demo \
  --build true \
  --parent-version 2.5.0
```

### 3.3. Add a root entity (creates API + controller + mapper + domain layer)
```bash
cd ../
java -jar onecx-svc-generator/generator/target/onecx-svc-generator-1.0.0-runner.jar add-entity \
  --project /home/Maciej/projects/onecx/onecx-demo-svc \
  --package org.tkit.onecx.demo \
  --entity Product \
  --fields name:String,price:BigDecimal \
  --root true \
  --build true
```

### 3.4. Add a child entity/component (updates existing API schema, no standalone CRUD)
```bash
cd ../
java -jar onecx-svc-generator/generator/target/onecx-svc-generator-1.0.0-runner.jar add-entity \
  --project /home/Maciej/projects/onecx/onecx-demo-svc \
  --package org.tkit.onecx.demo \
  --entity ProductItem \
  --fields quantity:Integer,position:Integer \
  --root false \
  --api-parent Product \
  --api-field items \
  --api-parent-collection true \
  --build true
```

### 3.5. Add entities in batch from a model definition file
```bash
cd ../
java -jar onecx-svc-generator/generator/target/onecx-svc-generator-1.0.0-runner.jar batch-model \
  --project /home/Maciej/projects/onecx/onecx-demo-svc \
  --package org.tkit.onecx.demo \
  --model /home/Maciej/projects/onecx/onecx-svc-generator/generator/examples/model.yaml \
  --build true
``` 

#### with Liquibase diff generation for existing entities - generates changelog with missing tables/columns based on the model definition:
```bash
cd ../
java -jar onecx-svc-generator/generator/target/onecx-svc-generator-1.0.0-runner.jar batch-model \
  --project /home/Maciej/projects/onecx/onecx-demo-svc \
  --package org.tkit.onecx.demo \
  --model /home/Maciej/projects/onecx/onecx-svc-generator/generator/examples/model.yaml \
  --liquibase-diff true \
  --build true
``` 

### 3.6. Build the generated service
```bash
cd ../onecx-demo-svc
mvn clean package
```

The first build generates REST interfaces and DTOs from OpenAPI using `openapi-generator-maven-plugin`.
The hand-written controllers and mappers already reference those classes and compile after generation.

### 3.7. Run the generated and built service
```bash
cd ../onecx-demo-svc
mvn quarkus:dev
```
### 3.8. Welcome Quarkus page
http://localhost:8080/q/dev-ui/welcome

### 3.9. Test locally example model endpoints with curl or Postman:

### a. internal api:

#### - create product with category
curl -X POST \
  http://localhost:8080/internal/products \
  -H 'Content-Type: application/json' \
  -H 'Accept: application/json' \
  -d '{
    "name": "Laptop",
    "price": 2999.99,
    "category": {
      "name": "Hardware"
    }
  }'

#### - get product by id
curl -X GET \
  http://localhost:8080/internal/products/p1a2b3c4-e222-4f66-bbbb-987654321000 \
  -H 'Accept: application/json'

#### - search products by name
curl -X POST \
  'http://localhost:8080/internal/products/search?limit=20&offset=0' \
  -H 'Content-Type: application/json' \
  -H 'Accept: application/json' \
  -d '{
    "name": "Laptop"
  }'

#### - delete product by id
curl -X DELETE \
  http://localhost:8080/internal/products/p1a2b3c4-e222-4f66-bbbb-987654321000 \
  -H 'Accept: application/json'


### b. external API:

#### - get product by id
curl --request GET \
  --url http://localhost:8080/v1/products/123e4567-e89b-12d3-a456-426614174000 \
  --header 'Accept: application/json'

#### - search products with pagination
curl --request POST \
--url 'http://localhost:8080/v1/products/search?limit=20&offset=0' \
--header 'Accept: application/json'

#### - search products by name only
curl --request POST \
  --url 'http://localhost:8080/v1/products/search?limit=20&offset=0' \
  --header 'Accept: application/json' \
  --header 'Content-Type: application/json' \
  --data '{
    "name": "prod"
  }'

#### - search products by name and price
curl --request POST \
--url 'http://localhost:8080/v1/products/search?limit=20&offset=0' \
--header 'Accept: application/json' \
--header 'Content-Type: application/json' \
--data '{
"name": "test",
"price": 100.00
}'


### 3.10. Do **not** commit the built JAR to the repository root.

## 4. Recommended flow:

1. build the generator JAR locally,
2. publish it as a GitHub Release asset,
3. run it via the JBang launcher from the repo catalog.

## 5. After a release is published:
```bash
jbang onecx-svc-generator@maciejkryger/onecx-svc-generator create-svc \
  --name onecx-demo-svc \
  --group org.tkit.onecx \
  --package org.tkit.onecx.demo
```
## 6. Appeared issues 

### during CreateSvcCommand

[INFO] --- quarkus:3.27.1:build (default) @ onecx-demo2-svc ---
java.lang.RuntimeException: create-svc failed
at org.tkit.onecx.onecxsvcgen.commands.CreateSvcCommand.run(CreateSvcCommand.java:118)
at picocli.CommandLine.executeUserObject(CommandLine.java:2045)
at picocli.CommandLine.access$1500(CommandLine.java:148)
at picocli.CommandLine$RunLast.executeUserObjectOfLastSubcommandWithSameParent(CommandLine.java:2469)
at picocli.CommandLine$RunLast.handle(CommandLine.java:2461)
at picocli.CommandLine$RunLast.handle(CommandLine.java:2423)
at picocli.CommandLine$AbstractParseResultHandler.execute(CommandLine.java:2277)
at picocli.CommandLine$RunLast.execute(CommandLine.java:2425)
at io.quarkus.picocli.runtime.PicocliRunner$EventExecutionStrategy.execute(PicocliRunner.java:26)
at picocli.CommandLine.execute(CommandLine.java:2174)
at io.quarkus.picocli.runtime.PicocliRunner.run(PicocliRunner.java:40)
at io.quarkus.runtime.ApplicationLifecycleManager.run(ApplicationLifecycleManager.java:149)
at io.quarkus.runtime.Quarkus.run(Quarkus.java:79)
at io.quarkus.runtime.Quarkus.run(Quarkus.java:50)
at io.quarkus.runner.GeneratedMain.main(Unknown Source)
Caused by: java.lang.RuntimeException: Failed to run Maven build in: /home/Maciej/projects/onecx/onecx-demo2-svc
at org.tkit.onecx.onecxsvcgen.service.BuildService.runMaven(BuildService.java:56)
at org.tkit.onecx.onecxsvcgen.service.BuildService.runMavenBuild(BuildService.java:15)
at org.tkit.onecx.onecxsvcgen.service.BuildService_ClientProxy.runMavenBuild(Unknown Source)
at org.tkit.onecx.onecxsvcgen.commands.CreateSvcCommand.run(CreateSvcCommand.java:115)
... 14 more
Caused by: java.lang.RuntimeException: Maven build failed with exit code: 137
at org.tkit.onecx.onecxsvcgen.service.BuildService.runMaven(BuildService.java:53)
... 17 more
2026-04-10 13:18:13,732 INFO  [io.quarkus] (main) onecx-svc-generator stopped in 0.026s

reason: build was killed by out of memory/memory limit in WSL, tested on machine with 32GM RAM, running WSL, Docker with running containers and remote screen sharing during Teams meeting,
after meeting it started to work, I built it again manually by command:
```bash
mvn clean package -DskipTests -e
```
