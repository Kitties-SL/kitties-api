package es.kitti.user.observability;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.trace.ReadWriteSpan;
import io.opentelemetry.sdk.trace.ReadableSpan;
import io.opentelemetry.sdk.trace.SpanProcessor;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.regex.Pattern;

@ApplicationScoped
public class OtelSanitizingSpanProcessor implements SpanProcessor {

    // OTel HTTP semconv 1.x
    private static final AttributeKey<String> HTTP_TARGET = AttributeKey.stringKey("http.target");
    // OTel HTTP semconv 2.x
    private static final AttributeKey<String> URL_PATH = AttributeKey.stringKey("url.path");
    private static final AttributeKey<String> URL_FULL = AttributeKey.stringKey("url.full");

    // matches any path segment containing an @ (i.e. an email address)
    private static final Pattern EMAIL_SEGMENT = Pattern.compile("/[^/@\\s]*@[^/@\\s?#]*");

    @Override
    public void onStart(Context parentContext, ReadWriteSpan span) {
        sanitize(span, HTTP_TARGET);
        sanitize(span, URL_PATH);
        sanitize(span, URL_FULL);
    }

    @Override
    public boolean isStartRequired() {
        return true;
    }

    @Override
    public void onEnd(ReadableSpan span) {}

    @Override
    public boolean isEndRequired() {
        return false;
    }

    private void sanitize(ReadWriteSpan span, AttributeKey<String> key) {
        String value = span.getAttribute(key);
        if (value != null && value.contains("@")) {
            span.setAttribute(key, EMAIL_SEGMENT.matcher(value).replaceAll("/{redacted}"));
        }
    }
}
