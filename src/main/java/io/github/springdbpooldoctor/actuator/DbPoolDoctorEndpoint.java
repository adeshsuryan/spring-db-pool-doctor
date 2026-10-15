package io.github.springdbpooldoctor.actuator;

import io.github.springdbpooldoctor.core.PoolMonitorService;
import java.util.Map;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;

/**
 * Optional Actuator endpoint at /actuator/dbPoolDoctor. Disabled by default;
 * the host application must secure it before exposing it on a management port.
 */
@Endpoint(id = "dbPoolDoctor")
public class DbPoolDoctorEndpoint {

    private final PoolMonitorService monitorService;

    public DbPoolDoctorEndpoint(PoolMonitorService monitorService) {
        this.monitorService = monitorService;
    }

    @ReadOperation
    public Map<String, Object> report() {
        return monitorService.reportAsMap();
    }
}
