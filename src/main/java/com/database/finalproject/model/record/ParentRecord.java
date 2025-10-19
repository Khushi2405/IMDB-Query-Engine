package com.database.finalproject.model.record;

public interface ParentRecord {
    /**
     * Returns a string representation of the record.
     * Typically used for displaying or logging the record's contents.
     *
     * @return a string describing the record
     */
    String toString();

    /**
     * Retrieves the value of a specific field within the record based on its index.
     * The index is zero-based unless otherwise specified by the implementing class.
     *
     * @param index the position of the field to retrieve
     * @return the value of the field as a String
     */
    String getFieldByIndex(int index);
}
