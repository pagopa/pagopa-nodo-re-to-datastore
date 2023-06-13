package it.gov.pagopa.nodoretodatastore;

import com.fasterxml.jackson.core.JsonProcessingException;
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
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Azure Functions with Azure Queue trigger.
 */
public class NodoReEventToDataStore {
    /**
     * This function will be invoked when an Event Hub trigger occurs
     */

	private static MongoClient mongoClient = null;

	private static MongoClient getMongoClient(){
		if(mongoClient==null){
			mongoClient = new MongoClient(new MongoClientURI(System.getenv("COSMOS_CONN_STRING")));
		}
		return mongoClient;
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
//            @CosmosDBOutput(
//    	            name = "NodoReEventDatastore",
//    	            databaseName = "nodo_re",
//    	            collectionName = "events",
//    	            createIfNotExists = false,
//                    connectionStringSetting = "COSMOS_CONN_STRING")
//                    @NonNull OutputBinding<List<ReEvent>> documentdb,
            final ExecutionContext context) {

		MongoDatabase database = getMongoClient().getDatabase(System.getenv("COSMOS_DB_NAME"));
		MongoCollection<Document> collection = database.getCollection(System.getenv("COSMOS_DB_COLLECTION_NAME"));

        Logger logger = context.getLogger();

        String message = String.format("NodoReEventToDataStore function called at %s with events list size %s and properties size %s", LocalDateTime.now(), reEvents.size(), properties.length);
        logger.info(message);
        
        // persist the item
        try {
        	if (reEvents.size() == properties.length) {
				List<Document> reEventsWithProperties = IntStream.of(reEvents.size()).mapToObj(i -> {
					int index = i-1;
					logger.info("processing "+index+" of "+properties.length);
					Map<String,Object> reEvent = null;
					try {
						reEvent = ObjectMapperUtils.readValue(reEvents.get(index), Map.class);
					} catch (JsonProcessingException e) {
						throw new RuntimeException(e);
					}

					String msg = String.format("NodoReEventToDataStore function called at %s with event id %s rx",
							LocalDateTime.now(), reEvent.get("unique_id"));
					logger.info(msg);
					reEvent.put("timestamp",ZonedDateTime.now().toInstant().toEpochMilli());
					reEvent.putAll(properties[index]);
					return new Document(reEvent);
				}).collect(Collectors.toList());
				collection.insertMany(reEventsWithProperties);

//    	        documentdb.setValue(reEventsWithProperties);
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
