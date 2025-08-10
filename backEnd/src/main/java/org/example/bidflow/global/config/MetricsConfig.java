package org.example.bidflow.global.config;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.config.MeterFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

@Configuration
public class MetricsConfig {

    @Bean
    public MeterFilter httpServerRequestsUriRelabel() {
        return new MeterFilter() {
            @Override
            public Meter.Id map(Meter.Id id) {
                if (!"http.server.requests".equals(id.getName())) {
                    return id;
                }

                List<Tag> tags = new ArrayList<>(id.getTags());
                String method = null;
                String uri = null;
                for (Tag t : tags) {
                    if ("method".equals(t.getKey())) {
                        method = t.getValue();
                    } else if ("uri".equals(t.getKey())) {
                        uri = t.getValue();
                    }
                }

                String newUri = uri;
                if (uri != null) {
                    if ("UNKNOWN".equals(uri) && method != null && method.equalsIgnoreCase("OPTIONS")) {
                        newUri = "PRE_FLIGHT";
                    } else if ("/**".equals(uri)) {
                        newUri = "/static/**";
                    }
                }

                List<Tag> newTags = new ArrayList<>();
                boolean uriReplaced = false;
                for (Tag t : tags) {
                    if ("uri".equals(t.getKey())) {
                        newTags.add(Tag.of("uri", newUri != null ? newUri : t.getValue()));
                        uriReplaced = true;
                    } else {
                        newTags.add(t);
                    }
                }
                if (!uriReplaced) {
                    newTags.add(Tag.of("uri", newUri != null ? newUri : "UNKNOWN"));
                }

                // 원본 requestUri 태그 추가(한계: 여기서는 패턴 기반 uri 값만 접근 가능)
                if (uri != null) {
                    newTags.add(Tag.of("requestUri", uri));
                }

                return id.withTags(newTags);
            }
        };
    }
}


