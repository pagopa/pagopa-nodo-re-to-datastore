package it.gov.pagopa.nodoretodatastore.entity;

import lombok.*;

import java.time.LocalDateTime;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReEvent {
    private String uniqueId;
    private String dataOraEvento;
    private String version;
    private LocalDateTime insertedTimestamp;
    private String componente;
    private String categoriaEvento;
    private String sottoTipoEvento;
    private String idDominio;
    private String iuv;
    private String ccp;
    private String psp;
    private String tipoVersamento;
    private String tipoEvento;
    private String fruitore;
    private String erogatore;
    private String stazione;
    private String canale;
    private String parametriSpecificiInterfaccia;
    private String esito;
    private String sessionId;
    private String status;
    private byte[] payload;
    private String info;
    private String businessProcess;
    private String fruitoreDescr;
    private String erogatoreDescr;
    private String pspDescr;
    private String noticeNumber;
    private String creditorReferenceId;
    private String paymentToken;
    private String sessionIdOriginal;

    private Long timestamp;
    private Map<String, Object> properties;
}
