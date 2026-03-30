package com.warehouse.database.model;

/**
 * A key-value pair with a version number.
 * The version is used to determine which value is most recent
 * when reading from multiple nodes (R=5).
 */
public class KeyValue {

    private String key;
    private String value;
    private long version;

    public KeyValue() {}

    public KeyValue(String key, String value, long version) {
        this.key = key;
        this.value = value;
        this.version = version;
    }

    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }

    public String getValue() { return value; }
    public void setValue(String value) { this.value = value; }

    public long getVersion() { return version; }
    public void setVersion(long version) { this.version = version; }

    /** Returns true if this KeyValue has a higher version than the other. */
    public boolean isNewerThan(KeyValue other) {
        if (other == null) return true;
        return this.version > other.version;
    }

    @Override
    public String toString() {
        return "KeyValue{key='" + key + "', value='" + value + "', version=" + version + '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        KeyValue kv = (KeyValue) o;
        return version == kv.version && key.equals(kv.key) && value.equals(kv.value);
    }

    @Override
    public int hashCode() {
        int result = key.hashCode();
        result = 31 * result + value.hashCode();
        result = 31 * result + (int) (version ^ (version >>> 32));
        return result;
    }
}
