package com.back.boundedContext.member.domain;

import com.back.global.jpa.entity.BaseIdAndTime;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import lombok.NoArgsConstructor;
import lombok.Getter;
import org.hibernate.annotations.ColumnDefault;

@Entity
@NoArgsConstructor
@Getter
public class Member extends BaseIdAndTime {

    @Column(unique = true)
    private String username;
    private String password;
    private String nickname;
    @ColumnDefault("0")
    private int activityScore;

    public Member(String username, String password, String nickname) {
        this.username = username;
        this.password = password;
        this.nickname = nickname;
    }
    public int increaseActivityScore(int amount) {
        return this.activityScore += amount;
    }
}
