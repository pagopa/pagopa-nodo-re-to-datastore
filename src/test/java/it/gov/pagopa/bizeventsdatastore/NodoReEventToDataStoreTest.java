package it.gov.pagopa.bizeventsdatastore;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.OutputBinding;
import it.gov.pagopa.bizeventsdatastore.entity.ReEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NodoReEventToDataStoreTest {

    @Spy
    NodoReEventToDataStore function;

    @Mock
    ExecutionContext context;

    @Spy TestOutputBinding testOutputBinding = new TestOutputBinding();

    @Test
    void runOk() {
        // test precondition
        Logger logger = Logger.getLogger("NodoReEventToDataStore-test-logger");
        when(context.getLogger()).thenReturn(logger);
        
        List<ReEvent> reEvtMsg = new ArrayList<>();
        ReEvent reEvent = new ReEvent();
        reEvent.setUniqueId("unique1");
        reEvtMsg.add(reEvent);
        
        Map<String, Object>[] properties = new HashMap[1];
        properties[0] = new HashMap<>();

        // test execution
        function.processNodoReEvent(reEvtMsg, properties, testOutputBinding, context);

        // test assertion -> this line means the call was successful
        verify(testOutputBinding,times(1)).setValue(any());
        assertTrue(true);
        assertTrue(testOutputBinding.getValue().get(0).getUniqueId().equals("unique1"));
    }
    
    @Test
    void runKo_differentSize() {
        // test precondition
        Logger logger = Logger.getLogger("NodoReEventToDataStore-test-logger");
        when(context.getLogger()).thenReturn(logger);
        
        List<ReEvent> bizEvtMsg = new ArrayList<>();
        bizEvtMsg.add (new ReEvent());
        
        Map<String, Object>[] properties = new HashMap[0];
        // test execution
        function.processNodoReEvent(bizEvtMsg, properties, testOutputBinding, context);

        // test assertion -> this line means the call was successful
        assertTrue(true);
    }
}
