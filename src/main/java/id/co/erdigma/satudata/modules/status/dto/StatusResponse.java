package id.co.erdigma.satudata.modules.status.dto;

import id.co.erdigma.satudata.annotation.PrefixedId;
import id.co.erdigma.satudata.enums.IdPrefix;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import lombok.Data;

@Data
public class StatusResponse {
    private String overall;
    private String overallLabel;
    private List<Component> components;
    private List<IncidentItem> incidents;

    @Data
    public static class Component {
        private String name;
        private String state;
    }

    @Data
    public static class IncidentItem {
        @PrefixedId(IdPrefix.INCIDENT)
        private UUID id;
        private String title;
        private String tag;
        private String detail;
        private String occurredLabel;
        private LocalDateTime occurredAt;
    }
}
