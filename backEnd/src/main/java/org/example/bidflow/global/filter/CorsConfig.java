package org.example.bidflow.global.filter;

import lombok.RequiredArgsConstructor;
import org.example.bidflow.global.config.OriginConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

@Configuration
@RequiredArgsConstructor
public class CorsConfig {
    private final OriginConfig originConfig;

    @Bean
    public CorsFilter corsFilter() {
        CorsConfiguration config = new CorsConfiguration();
        originConfig.getFrontend().forEach(config::addAllowedOrigin);
        config.addAllowedOriginPattern("*");
        config.addAllowedHeader("*");
        config.addAllowedMethod("*");
        config.setAllowCredentials(true);

        // WebSocket 전용 CORS 설정 - 쿠키 전달을 위해 allowCredentials true로 설정
        CorsConfiguration wsConfig = new CorsConfiguration();
        originConfig.getFrontend().forEach(wsConfig::addAllowedOrigin);
        wsConfig.addAllowedOriginPattern("*");
        wsConfig.addAllowedHeader("*");
        wsConfig.addAllowedMethod("*");
        wsConfig.setAllowCredentials(true); // WebSocket에서도 쿠키 허용

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        source.registerCorsConfiguration("/ws/**", wsConfig);
        return new CorsFilter(source);
    }
}
