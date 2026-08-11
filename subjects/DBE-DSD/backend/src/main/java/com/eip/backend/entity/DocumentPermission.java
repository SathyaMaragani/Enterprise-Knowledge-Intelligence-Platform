package com.eip.backend.entity;
import jakarta.persistence.*;
import java.time.ZonedDateTime;

@Entity
@Table(name = "document_permissions")
public class DocumentPermission {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id", nullable = false)
    private Document document;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    
    @Column(name = "permission_type", nullable = false, length = 50)
    private String permissionType;
    
    @Column(name = "granted_at", nullable = false, updatable = false)
    private ZonedDateTime grantedAt = ZonedDateTime.now();

    // Getters and setters
    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public Document getDocument() { return document; }
    public void setDocument(Document document) { this.document = document; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public String getPermissionType() { return permissionType; }
    public void setPermissionType(String permissionType) { this.permissionType = permissionType; }
    public ZonedDateTime getGrantedAt() { return grantedAt; }
    public void setGrantedAt(ZonedDateTime grantedAt) { this.grantedAt = grantedAt; }
}