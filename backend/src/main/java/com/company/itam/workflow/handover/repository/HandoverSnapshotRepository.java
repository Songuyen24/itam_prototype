package com.company.itam.workflow.handover.repository;

import com.company.itam.workflow.handover.dto.HandoverResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public class HandoverSnapshotRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    public HandoverSnapshotRepository(JdbcTemplate jdbc, ObjectMapper json) { this.jdbc=jdbc; this.json=json; }
    public void save(HandoverResponse snapshot) {
        jdbc.update("INSERT INTO handover_snapshots(transaction_id,snapshot) VALUES (?,?::jsonb)", snapshot.transactionId(), encode(snapshot));
    }
    public Optional<HandoverResponse> find(Long id) {
        return jdbc.query("SELECT snapshot::text FROM handover_snapshots WHERE transaction_id=?", (rs,n)->decode(rs.getString(1)),id).stream().findFirst();
    }
    public String encode(Object value) {
        try { return json.writeValueAsString(value); }
        catch (JsonProcessingException e) { throw new IllegalStateException("Cannot encode handover snapshot",e); }
    }
    private HandoverResponse decode(String value) {
        try { return json.readValue(value,HandoverResponse.class); }
        catch (JsonProcessingException e) { throw new IllegalStateException("Cannot decode handover snapshot",e); }
    }
}
