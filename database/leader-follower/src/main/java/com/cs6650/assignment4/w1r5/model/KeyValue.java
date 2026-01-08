package com.cs6650.assignment4.w1r5.model;

/**
 * Represents a key-value pair with a version number.
 * The version is used to determine which value is most recent
 * when reading from multiple nodes (R=5).
 */
public class KeyValue {

    private String key;
    private String value;
    private long version;

    /**
     * Default constructor (needed for JSON deserialization)
     */
    public KeyValue() {
    }

    /**
     * Full constructor
     */
    public KeyValue(String key, String value, long version) {
        this.key = key;
        this.value = value;
        this.version = version;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public long getVersion() {
        return version;
    }

    public void setVersion(long version) {
        this.version = version;
    }

    /**
     * Compare versions to see which is newer
     * @param other Another KeyValue to compare with
     * @return true if this KeyValue is newer (higher version)
     */
    public boolean isNewerThan(KeyValue other) {
        if (other == null) {
            return true;
        }
        return this.version > other.version;
    }

    /**
     * String representation for debugging
     */
    @Override
    public String toString() {
        return "KeyValue{" +
            "key='" + key + '\'' +
            ", value='" + value + '\'' +
            ", version=" + version +
            '}';
    }

    /**
     * Check equality (useful for testing)
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        KeyValue keyValue = (KeyValue) o;

        if (version != keyValue.version) return false;
        if (!key.equals(keyValue.key)) return false;
        return value.equals(keyValue.value);
    }

    /**
     * Generate hash code (needed when using equals)
     */
    @Override
    public int hashCode() {
        int result = key.hashCode();
        result = 31 * result + value.hashCode();
        result = 31 * result + (int) (version ^ (version >>> 32));
        return result;
    }
}