package it.gov.pagopa.nodoretodatastore;

import com.azure.data.tables.TableClient;
import com.azure.data.tables.TableServiceClient;
import com.azure.data.tables.TableServiceClientBuilder;
import com.azure.data.tables.models.TableEntity;
import com.azure.data.tables.models.TableTransactionAction;
import com.azure.data.tables.models.TableTransactionActionType;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.annotation.BindingName;
import com.microsoft.azure.functions.annotation.Cardinality;
import com.microsoft.azure.functions.annotation.EventHubTrigger;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.mongodb.MongoClient;
import com.mongodb.MongoClientURI;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import it.gov.pagopa.nodoretodatastore.util.ObjectMapperUtils;
import org.bson.Document;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

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
	private static String insertedTimestamp = "insertedTimestamp";
	private static String partitionKey = "PartitionKey";
	private static String payloadField = "payload";

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


	private void addToBatch(Logger logger, Map<String,List<TableTransactionAction>> partitionEvents, Map<String,Object> reEvent){
		if(reEvent.get(idField) == null) {
			logger.warning("event has no '" + idField + "' field");
		} else {
			TableEntity entity = new TableEntity((String) reEvent.get(partitionKey), (String)reEvent.get(idField));
			entity.setProperties(reEvent);
			if(!partitionEvents.containsKey(entity.getPartitionKey())){
				partitionEvents.put(entity.getPartitionKey(),new ArrayList<TableTransactionAction>());
			}
			partitionEvents.get(entity.getPartitionKey()).add(new TableTransactionAction(TableTransactionActionType.UPSERT_REPLACE,entity));
		}
	}

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

	private void zipPayload(Logger logger,Map<String,Object> reEvent){
		if(reEvent.get(payloadField)!=null){
			try {
				byte[] data = ((String)reEvent.get(payloadField)).getBytes(StandardCharsets.UTF_8);
				ByteArrayOutputStream baos = new ByteArrayOutputStream();
				Deflater deflater = new Deflater();
				DeflaterOutputStream dos = new DeflaterOutputStream(baos, deflater);
				dos.write(data);
				dos.close();
				reEvent.put(payloadField,baos.toByteArray());
			} catch (Exception e) {
				logger.severe(e.getMessage());
			}
		}
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
//				Map<String,List<TableTransactionAction>> partitionEvents = new HashMap<>();
				List<Document> eventsToPersistCosmos = new ArrayList<>();

				for(int index=0; index< properties.length; index++) {
					// logger.info("processing "+(index+1)+" of "+properties.length);
					final Map<String,Object> reEvent = ObjectMapperUtils.readValue(reEvents.get(index), Map.class);
					properties[index].forEach((p,v)->{
						String s = replaceDashWithUppercase(p);
						reEvent.put(s,v);
					});
					reEvent.put("timestamp", ZonedDateTime.now().toInstant().toEpochMilli());

//					String partitionKeyValue = reEvent.get(insertedTimestamp) != null ? ((String)reEvent.get(insertedTimestamp)).substring(0,13) : "NA";
					String partitionKeyValue = reEvent.get(insertedTimestamp) != null ? ((String)reEvent.get(insertedTimestamp)).substring(0,10) : "NA";
					partitionKeyValue += reEvent.get("idDominio") + reEvent.get("psp").toString();
					reEvent.put(partitionKey, partitionKeyValue);

					zipPayload(logger,reEvent);

//					addToBatch(logger,partitionEvents,reEvent);
					eventsToPersistCosmos.add(new Document(reEvent));
				}

//				partitionEvents.forEach((pe,values)->{
//					try {
//						tableClient.submitTransaction(values);
//					} catch (Throwable t){
//						logger.severe("Could not save on tableStorage,partition "+pe+", "+values.size()+" rows,error:"+ t.toString());
//					}
//				});

				try {
					long start = ZonedDateTime.now().toInstant().toEpochMilli();
					collection.insertMany(eventsToPersistCosmos);
					logger.info("written " + (ZonedDateTime.now().toInstant().toEpochMilli() - start));
				} catch (Throwable t){
					logger.severe("Could not save on cosmos "+eventsToPersistCosmos.size()+",error:"+ t.toString());
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
