# pagoPA Functions template

Java template to create an Azure Function.

## Function examples
There is an example of a Http Trigger function.

---

## Run locally with Docker
`docker build -t pagopa-functions-template .`

`cp .env.example .env`
 
and replace in `.env` with correct values, then typing :

`docker run -p 80:80 --env-file=./.env pagopa-functions-template` 


### Test

`curl http://localhost:8999/example`

## Run locally with Maven

On terminal and  typing :

`cp local.settings.json.example local.settings.json`

then replace
- `EVENTHUB_CONN_STRING` and `COSMOS_CONN_STRING` with real one connection string
- `COSMOS_DB_CONTAINER_NAME` and `COSMOS_DB_NAME` with correct value
> to doc details about AZ fn config see [here](https://stackoverflow.com/questions/62669672/azure-functions-what-is-the-purpose-of-having-host-json-and-local-settings-jso)

`mvn clean package`

`mvn azure-functions:run`

### Test
`curl http://localhost:7071/example` 

---

Configure the SonarCloud project :point_right: [guide](https://pagopa.atlassian.net/wiki/spaces/DEVOPS/pages/147193860/SonarCloud+experimental).