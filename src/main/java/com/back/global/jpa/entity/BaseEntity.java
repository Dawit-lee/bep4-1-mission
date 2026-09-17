package com.back.global.jpa.entity;

import com.back.global.global.GlobalConfig;
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
    protected void publishEvent(Object event) {
        GlobalConfig.getEventPublisher().publish(event);
    }
}
