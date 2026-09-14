package io.point3.p3api.mail.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "p3.mail")
public record MailProperties(boolean enabled, String from, String sellerInquiriesUrl) {

  public MailProperties {
    from = normalize(from);
    sellerInquiriesUrl = normalize(sellerInquiriesUrl);
  }

  public boolean ready() {
    return enabled
        && from != null
        && !from.isBlank()
        && sellerInquiriesUrl != null
        && !sellerInquiriesUrl.isBlank();
  }

  private static String normalize(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    return value.trim();
  }
}
