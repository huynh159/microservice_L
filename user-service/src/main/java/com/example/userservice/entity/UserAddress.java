package com.example.userservice.entity;

import jakarta.persistence.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "user_addresses", indexes = {
        // Đánh index cho user_id để tăng tốc độ truy vấn danh sách địa chỉ của 1 user
        @Index(name = "idx_user_addresses_user_id", columnList = "user_id")
})
@EntityListeners(AuditingEntityListener.class)
public class UserAddress {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID) // Tự động sinh chuỗi UUID ngẫu nhiên
    private UUID id;

    // Quan hệ ManyToOne với User
    // FetchType.LAZY: Chỉ khi nào gọi address.getUser() thì Hibernate mới thực sự
    // query bảng users (tránh thừa thãi)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "recipient_name", nullable = false, length = 100)
    private String recipientName; // Tên người nhận hàng

    @Column(name = "phone", nullable = false, length = 20)
    private String phone;

    @Column(name = "street_address", nullable = false, length = 255)
    private String streetAddress; // Số nhà, tên đường

    @Column(name = "city", nullable = false, length = 100)
    private String city; // Thành phố / Tỉnh

    @Column(name = "is_default", nullable = false)
    private boolean isDefault = false; // Địa chỉ mặc định

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    // Constructor rỗng (Bắt buộc phải có trong chuẩn JPA)
    public UserAddress() {
    }

    // Constructor tiện ích
    public UserAddress(User user, String recipientName, String phone, String streetAddress, String city,
            boolean isDefault) {
        this.user = user;
        this.recipientName = recipientName;
        this.phone = phone;
        this.streetAddress = streetAddress;
        this.city = city;
        this.isDefault = isDefault;
    }

    // --- GETTER / SETTER ---
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public String getRecipientName() {
        return recipientName;
    }

    public void setRecipientName(String recipientName) {
        this.recipientName = recipientName;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getStreetAddress() {
        return streetAddress;
    }

    public void setStreetAddress(String streetAddress) {
        this.streetAddress = streetAddress;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public boolean isDefault() {
        return isDefault;
    }

    public void setDefault(boolean aDefault) {
        isDefault = aDefault;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    // --- equals & hashCode dựa trên ID ---
    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof UserAddress that))
            return false;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}