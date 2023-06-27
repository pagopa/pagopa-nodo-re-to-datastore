package it.gov.pagopa.nodoretodatastore;

import com.azure.data.tables.TableClient;
import com.azure.data.tables.TableServiceClient;
import com.azure.data.tables.TableServiceClientBuilder;
import com.azure.data.tables.models.TableEntity;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.annotation.BindingName;
import com.microsoft.azure.functions.annotation.Cardinality;
import com.microsoft.azure.functions.annotation.EventHubTrigger;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.mongodb.MongoClient;
import com.mongodb.MongoClientURI;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import it.gov.pagopa.nodoretodatastore.exception.AppException;
import it.gov.pagopa.nodoretodatastore.util.ObjectMapperUtils;
import org.bson.Document;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Azure Functions with Azure Queue trigger.
 */
public class NodoReEventToDataStore {
    /**
     * This function will be invoked when an Event Hub trigger occurs
     */

	private Pattern replaceDashPattern = Pattern.compile("-([a-zA-Z])");
	private static String idField = "uniqueId";
	private static String tableName = System.getenv("TABLE_STORAGE_TABLE_NAME");
	private static String partitionKey = "insertedTimestamp";

	private static MongoClient mongoClient = null;

	private static TableServiceClient tableServiceClient = null;

	private static MongoClient getMongoClient(){
		if(mongoClient==null){
			mongoClient = new MongoClient(new MongoClientURI(System.getenv("COSMOS_CONN_STRING")));
		}
		return mongoClient;
	}

	private static TableServiceClient getTableServiceClient(){
		if(tableServiceClient==null){
			tableServiceClient = new TableServiceClientBuilder().connectionString(System.getenv("TABLE_STORAGE_CONN_STRING"))
					.buildClient();
			tableServiceClient.createTableIfNotExists(tableName);
		}
		return tableServiceClient;
	}


	private void toTableStorage(Logger logger,TableClient tableClient,Map<String,Object> reEvent){
		if(reEvent.get(partitionKey) == null){
			logger.warning("event has no '"+partitionKey+"' field");
		}else if(reEvent.get(idField) == null) {
			logger.warning("event has no '"+idField+"' field");
		}else{
			TableEntity entity = new TableEntity((String)reEvent.get(partitionKey), (String)reEvent.get(idField));
			reEvent.keySet().forEach(d->{
				entity.addProperty(d,reEvent.get(d));
			});
			tableClient.createEntity(entity);
		}
	}

	private String replaceDashWithUppercase(String input) {

		Matcher matcher = replaceDashPattern.matcher(input);
		StringBuffer sb = new StringBuffer();

		while (matcher.find()) {
			matcher.appendReplacement(sb, matcher.group(1).toUpperCase());
		}
		matcher.appendTail(sb);

		return sb.toString();
	}

    @FunctionName("EventHubNodoReEventProcessor")
    public void processNodoReEvent (
            @EventHubTrigger(
                    name = "NodoReEvent",
                    eventHubName = "", // blank because the value is included in the connection string
                    connection = "EVENTHUB_CONN_STRING",
                    cardinality = Cardinality.MANY)
    		List<String> reEvents,
    		@BindingName(value = "PropertiesArray") Map<String, Object>[] properties,
            final ExecutionContext context) {

		MongoDatabase database = getMongoClient().getDatabase(System.getenv("COSMOS_DB_NAME"));
		MongoCollection<Document> collection = database.getCollection(System.getenv("COSMOS_DB_COLLECTION_NAME"));

        Logger logger = context.getLogger();

		TableClient tableClient = getTableServiceClient().getTableClient(tableName);
		String msg = String.format("Persisting %d events",reEvents.size());
		logger.info(msg);
        try {
        	if (reEvents.size() == properties.length) {

				List<Document> reEventsWithProperties = new ArrayList<>();
				for(int index=0;index< properties.length;index++){
					logger.info("processing "+index+" of "+properties.length);
					final Map<String,Object> reEvent = ObjectMapperUtils.readValue(reEvents.get(index), Map.class);

					reEvent.putAll(properties[index]);

					final Map<String,Object> reEventClean = new HashMap<>();

					reEventClean.put("timestamp",ZonedDateTime.now().toInstant().toEpochMilli());

					reEvent.keySet().forEach(k->{
						String newkey = replaceDashWithUppercase(k);
						reEventClean.put(newkey,reEvent.get(k));
					});

					reEventsWithProperties.add(new Document(reEventClean));
					toTableStorage(logger,tableClient,reEventClean);
				}
				collection.insertMany(reEventsWithProperties);

            } else {
            	throw new AppException("Error during processing - "
            			+ "The size of the events to be processed and their associated properties does not match [reEvents.size="+reEvents.size()+"; properties.length="+properties.length+"]");
            }
        	
        } catch (NullPointerException e) {
            logger.severe("NullPointerException exception on cosmos nodo-re-events msg ingestion at "+ LocalDateTime.now()+ " : " + e.getMessage());
        } catch (Exception e) {
			e.printStackTrace();
            logger.severe("Generic exception on cosmos nodo-re-events msg ingestion at "+ LocalDateTime.now()+ " : " + e.getMessage());
        }

    }
}
