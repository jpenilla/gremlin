package xyz.jpenilla.gremlin.runtime.util;

import org.jspecify.annotations.NullMarked;

@NullMarked
public record HashResult(byte[] bytes) {
    public String asHexString() {
        return asHexString(this.bytes);
    }

    private static String asHexString(final byte[] bytes) {
        final StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (final byte b : bytes) {
            sb.append("%02x".formatted(b & 0xFF));
        }
        return sb.toString();
    }
}
