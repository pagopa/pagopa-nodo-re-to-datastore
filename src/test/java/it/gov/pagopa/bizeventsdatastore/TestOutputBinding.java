package it.gov.pagopa.bizeventsdatastore;

import com.microsoft.azure.functions.OutputBinding;
import it.gov.pagopa.bizeventsdatastore.entity.ReEvent;

import java.util.List;

public class TestOutputBinding implements OutputBinding<List<ReEvent>> {
    List<ReEvent> value;
    @Override
    public List<ReEvent> getValue() {
        return value;
    }

    @Override
    public void setValue(List<ReEvent> o) {
        value = o;
    }
}
