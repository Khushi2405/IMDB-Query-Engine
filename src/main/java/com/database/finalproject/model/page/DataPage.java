package com.database.finalproject.model.page;

import com.database.finalproject.model.record.ParentRecord;

public abstract class DataPage<T extends ParentRecord> implements Page {
    /**
     * Fetches a record from the page by its record ID.
     * @param recordId The ID of the record to retrieve.
     * @return The MovieRecord object containing the data of a row.
     */
    public abstract T getRecord(int recordId);

    /**
     * Inserts a new movieRecord into the page.
     * @param parentRecord The ParentRecord object containing the data to insert.
     * @return The parentRecord ID of the inserted parentRecord, or -1 if the page is full
     */
    public abstract int insertRecord(T parentRecord);

    /**
     * Check if the page is full.
     * @return true if the page is full, false otherwise
     */
    public abstract boolean isFull();


}
