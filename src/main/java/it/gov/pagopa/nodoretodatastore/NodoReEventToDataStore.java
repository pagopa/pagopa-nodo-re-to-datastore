package it.gov.pagopa.nodoretodatastore;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.OutputBinding;
import com.microsoft.azure.functions.annotation.*;
import it.gov.pagopa.nodoretodatastore.entity.ReEvent;
import it.gov.pagopa.nodoretodatastore.exception.AppException;
import lombok.NonNull;

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
    @FunctionName("EventHubNodoReEventProcessor")
    public void processNodoReEvent (
            @EventHubTrigger(
                    name = "NodoReEvent",
                    eventHubName = "", // blank because the value is included in the connection string
                    connection = "EVENTHUB_CONN_STRING",
                    cardinality = Cardinality.MANY)
    		List<ReEvent> reEvents,
    		@BindingName(value = "PropertiesArray") Map<String, Object>[] properties,
            @CosmosDBOutput(
    	            name = "NodoReEventDatastore",
    	            databaseName = "db",
    	            collectionName = "nodo-re-events",
    	            createIfNotExists = false,
                    connectionStringSetting = "COSMOS_CONN_STRING")
                    @NonNull OutputBinding<List<ReEvent>> documentdb,
            final ExecutionContext context) {

        Logger logger = context.getLogger();

        String message = String.format("NodoReEventToDataStore function called at %s with events list size %s and properties size %s", LocalDateTime.now(), reEvents.size(), properties.length);
        logger.info(message);
        
        // persist the item
        try {
        	if (reEvents.size() == properties.length) {
				List<ReEvent> reEventsWithProperties = IntStream.of(reEvents.size()).mapToObj(i -> {
					int index = i-1;
					ReEvent reEvent = reEvents.get(index);
					String msg = String.format("NodoReEventToDataStore function called at %s with event id %s rx",
							LocalDateTime.now(), reEvent.getUniqueId());
					logger.info(msg);
					reEvent.setTimestamp(ZonedDateTime.now().toInstant().toEpochMilli());
					reEvent.setProperties(properties[index]);
					return reEvent;
				}).collect(Collectors.toList());
    	        documentdb.setValue(reEventsWithProperties);
            } else {
            	throw new AppException("Error during processing - "
            			+ "The size of the events to be processed and their associated properties does not match [reEvents.size="+reEvents.size()+"; properties.length="+properties.length+"]");
            }
        	
        } catch (NullPointerException e) {
            logger.severe("NullPointerException exception on cosmos nodo-re-events msg ingestion at "+ LocalDateTime.now()+ " : " + e.getMessage());
        } catch (Exception e) {
            logger.severe("Generic exception on cosmos nodo-re-events msg ingestion at "+ LocalDateTime.now()+ " : " + e.getMessage());
        }

    }
}
