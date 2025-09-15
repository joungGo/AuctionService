package org.example.bidflow.global.filter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import lombok.extern.slf4j.Slf4j;
import org.example.bidflow.global.config.RateLimitingConfig;
import org.example.bidflow.global.service.RateLimitingService.RateLimitResult;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;

/**
 * Rate Limiting 정보를 성공 응답에 추가하기 위한 Response Wrapper
 */
@Slf4j
public class RateLimitResponseWrapper extends HttpServletResponseWrapper {

    private final ByteArrayOutputStream capture;
    private ServletOutputStream output;
    private PrintWriter writer;
    private final ObjectMapper objectMapper;
    private final RateLimitResult rateLimitResult;
    private final String requestUri;

    public RateLimitResponseWrapper(HttpServletResponse response, ObjectMapper objectMapper, 
                                  RateLimitResult rateLimitResult, String requestUri) {
        super(response);
        this.capture = new ByteArrayOutputStream();
        this.objectMapper = objectMapper;
        this.rateLimitResult = rateLimitResult;
        this.requestUri = requestUri;
    }

    @Override
    public ServletOutputStream getOutputStream() throws IOException {
        if (writer != null) {
            throw new IllegalStateException("getWriter() has already been called on this response.");
        }

        if (output == null) {
            output = new ServletOutputStream() {
                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setWriteListener(WriteListener listener) {
                    // Not implemented
                }

                @Override
                public void write(int b) throws IOException {
                    capture.write(b);
                }

                @Override
                public void flush() throws IOException {
                    capture.flush();
                }

                @Override
                public void close() throws IOException {
                    capture.close();
                }
            };
        }

        return output;
    }

    @Override
    public PrintWriter getWriter() throws IOException {
        if (output != null) {
            throw new IllegalStateException("getOutputStream() has already been called on this response.");
        }

        if (writer == null) {
            writer = new PrintWriter(new StringWriter() {
                @Override
                public void write(String str) {
                    try {
                        capture.write(str.getBytes(StandardCharsets.UTF_8));
                    } catch (IOException e) {
                        log.error("Error writing to capture buffer", e);
                    }
                }
            });
        }
        return writer;
    }

    @Override
    public void flushBuffer() throws IOException {
        super.flushBuffer();
        if (writer != null) {
            writer.flush();
        } else if (output != null) {
            output.flush();
        }
    }

    /**
     * 캡처된 응답 내용을 실제 응답에 쓰고 Rate Limit 정보를 추가
     */
    public void copyBodyToResponse() throws IOException {
        if (capture.size() > 0) {
            String contentType = getContentType();
            String capturedContent = capture.toString(StandardCharsets.UTF_8);

            // JSON 응답인 경우에만 Rate Limit 정보 추가
            if (contentType != null && contentType.contains("application/json") && 
                getStatus() == 200 && rateLimitResult != null) {
                
                try {
                    // 기존 JSON에 Rate Limit 정보 추가
                    JsonNode rootNode = objectMapper.readTree(capturedContent);
                    if (rootNode.isObject()) {
                        ObjectNode objectNode = (ObjectNode) rootNode;
                        
                        // Rate Limit 정보 추가
                        ObjectNode rateLimitInfo = objectMapper.createObjectNode();
                        
                        // 남은 토큰 수 정보
                        ObjectNode remainingTokens = objectMapper.createObjectNode();
                        if (rateLimitResult.getSecondRemainingTokens() >= 0) {
                            remainingTokens.put("second", rateLimitResult.getSecondRemainingTokens());
                        }
                        if (rateLimitResult.getMinuteRemainingTokens() >= 0) {
                            remainingTokens.put("minute", rateLimitResult.getMinuteRemainingTokens());
                        }
                        if (rateLimitResult.getHourRemainingTokens() >= 0) {
                            remainingTokens.put("hour", rateLimitResult.getHourRemainingTokens());
                        }
                        rateLimitInfo.set("remainingTokens", remainingTokens);
                        
                        // 제한 토큰 수 정보
                        ObjectNode limits = objectMapper.createObjectNode();
                        if (rateLimitResult.getAppliedLimit() != null) {
                            RateLimitingConfig.ApiLimit limit = rateLimitResult.getAppliedLimit();
                            limits.put("second", limit.getRequestsPerSecond());
                            limits.put("minute", limit.getRequestsPerMinute());
                            limits.put("hour", limit.getRequestsPerHour());
                        } else {
                            // 기본 IP 제한 정보
                            limits.put("second", 10);
                            limits.put("minute", 100);
                            limits.put("hour", 1000);
                        }
                        rateLimitInfo.set("limits", limits);
                        
                        // 추가 정보
                        rateLimitInfo.put("endpoint", requestUri);
                        rateLimitInfo.put("appliedRuleType", rateLimitResult.getLimitType() != null ? 
                                         rateLimitResult.getLimitType() : "IP");
                        
                        objectNode.set("rateLimitInfo", rateLimitInfo);
                        
                        // 수정된 JSON을 응답에 쓰기
                        String modifiedContent = objectMapper.writeValueAsString(objectNode);
                        byte[] modifiedBytes = modifiedContent.getBytes(StandardCharsets.UTF_8);
                        
                        getResponse().setContentLength(modifiedBytes.length);
                        getResponse().getOutputStream().write(modifiedBytes);
                        return;
                    }
                } catch (Exception e) {
                    log.warn("[Rate Limiting] JSON 응답 수정 실패, 원본 응답 사용: {}", e.getMessage());
                }
            }
            
            // JSON이 아니거나 수정에 실패한 경우 원본 응답 사용
            getResponse().getOutputStream().write(capture.toByteArray());
        }
    }

    public byte[] getCaptureAsBytes() {
        return capture.toByteArray();
    }
}
