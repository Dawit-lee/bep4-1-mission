package com.back.jpa.entity;

import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
@Getter
// 모든 엔티티들의 조상
public class BaseEntity {
    public String getModelTypeCode() {
        return this.getClass().getSimpleName();
    }
}
