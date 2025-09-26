package org.example.bidflow.global.annotation;

import org.example.bidflow.domain.user.entity.Role;

import java.lang.annotation.*;

@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface HasRole {
    Role value(); // 권한을 파라미터로 받음
}