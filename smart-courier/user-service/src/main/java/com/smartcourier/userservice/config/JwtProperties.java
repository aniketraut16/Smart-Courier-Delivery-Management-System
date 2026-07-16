package com.smartcourier.userservice.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {

    /** Base64-encoded 256-bit HMAC-SHA256 signing secret. Matches yaml key: jwt.secret-key */
    private String secretKey;

    /** Token lifetime in milliseconds. Matches yaml key: jwt.expiration-ms */
    private long expirationMs;
}
