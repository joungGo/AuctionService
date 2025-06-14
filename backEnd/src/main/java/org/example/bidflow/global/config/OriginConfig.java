package org.example.bidflow.global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import lombok.Getter;
import lombok.Setter;
import java.util.List;

@Configuration
@ConfigurationProperties(prefix = "origin.ip")
@Getter
@Setter
public class OriginConfig {
    private List<String> frontend;
} 