package it.gov.pagopa.nodoretodatastore;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.OutputBinding;
import com.microsoft.azure.functions.annotation.BindingName;
import com.microsoft.azure.functions.annotation.Cardinality;
import com.microsoft.azure.functions.annotation.CosmosDBOutput;
import com.microsoft.azure.functions.annotation.EventHubTrigger;
import com.microsoft.azure.functions.annotation.FunctionName;
import it.gov.pagopa.nodoretodatastore.util.ObjectMapperUtils;
import lombok.NonNull;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Azure Functions with Azure Event Hub trigger.
 */
public class NodoReEventToDataStore {
    /**
     * This function will be invoked when an Event Hub trigger occurs
     */

	private Pattern replaceDashPattern = Pattern.compile("-([a-zA-Z])");
	private static String NA = "NA";
	private static String uniqueIdField = "uniqueId";
	private static String idDominioField = "idDominio";
	private static String pspField = "psp";
	private static String insertedTimestampField = "insertedTimestamp";
	private static String insertedDateField = "insertedDate";
	private static String partitionKeyField = "PartitionKey";
	private static String payloadField = "payload";

	private String replaceDashWithUppercase(String input) {
		if(!input.contains("-")){
			return input;
		}
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
			@CosmosDBOutput(
					name = "NodoReEventToDataStore",
					databaseName = "nodo_re",
					containerName = "events",
					createIfNotExists = false,
					connection = "COSMOS_CONN_STRING")
			@NonNull OutputBinding<List<Object>> documentdb,
            final ExecutionContext context) {

		Logger logger = context.getLogger();

		String msg = String.format("Persisting %d events", reEvents.size());
		logger.info(msg);
        try {
        	if (reEvents.size() == properties.length) {
				List<Object> eventsToPersistCosmos = new ArrayList<>();

				for(int index=0; index< properties.length; index++) {
					final Map<String,Object> reEvent = ObjectMapperUtils.readValue(reEvents.get(index), Map.class);
					properties[index].forEach((p,v) -> {
						String s = replaceDashWithUppercase(p);
						reEvent.put(s, v);
					});

					reEvent.put("id", reEvent.get(uniqueIdField));

					String insertedDateValue = reEvent.get(insertedTimestampField) != null ? ((String)reEvent.get(insertedTimestampField)).substring(0, 10) : NA;
					reEvent.put(insertedDateField, insertedDateValue);

					String idDominio = reEvent.get(idDominioField) != null ? reEvent.get(idDominioField).toString() : NA;
					String idPsp = reEvent.get(pspField) != null ? reEvent.get(pspField).toString() : NA;
					String partitionKeyValue = insertedDateValue + "-" + idDominio + "-" + idPsp;
					reEvent.put(partitionKeyField, partitionKeyValue);

					reEvent.put(payloadField, null);

					eventsToPersistCosmos.add(reEvent);
				}

				try {
					documentdb.setValue(eventsToPersistCosmos);
				} catch (Throwable t){
					logger.severe("Could not save on cosmos "+eventsToPersistCosmos.size()+", error:"+ t.toString());
				}

				logger.info("Done processing events");
            } else {
				logger.severe("Error processing events, lengths do not match ["+reEvents.size()+","+properties.length+"]");
            }
        } catch (NullPointerException e) {
            logger.severe("NullPointerException exception on cosmos nodo-re-events msg ingestion at "+ LocalDateTime.now()+ " : " + e.getMessage());
        } catch (Throwable e) {
            logger.severe("Generic exception on cosmos nodo-re-events msg ingestion at "+ LocalDateTime.now()+ " : " + e.getMessage());
        }
    }
}
