package com.eip.backend.entity.mongodb;
import org.springframework.data.mongodb.core.mapping.Field;
import java.util.Date;
public class Version {
    private Integer number;
    @Field("change_summary") private String changeSummary;
    @Field("created_at") private Date createdAt;
    // Getters and Setters
    public Integer getNumber() { return number; }
    public void setNumber(Integer number) { this.number = number; }
    public String getChangeSummary() { return changeSummary; }
    public void setChangeSummary(String changeSummary) { this.changeSummary = changeSummary; }
    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }
}
