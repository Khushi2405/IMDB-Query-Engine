package com.database.finalproject.buffermanager;

import com.database.finalproject.model.page.Page;

/**
 * Abstract class representing a Buffer Manager responsible for managing
 * in-memory pages from one or more database files.
 *
 * The Buffer Manager uses an LRU (Least Recently Used) replacement strategy
 * to decide which pages to evict when the buffer is full.
 *
 * It supports multiple files, each identified by an index, allowing operations
 * such as page retrieval, creation, dirty marking, unpinning, and forced flushing.
 */
public abstract class BufferManager {
    /** Maximum number of pages the buffer can hold. */
    final int bufferSize;

    /**
     * Constructs a BufferManager with a given buffer size.
     *
     * @param bufferSize maximum number of pages the buffer can store at once
     */
    public BufferManager(int bufferSize) {
        this.bufferSize = bufferSize;
    }

    /**
     * Retrieves a page from the buffer.
     * If the page is not already in memory, it is loaded from disk.
     * Uses the LRU policy to manage buffer replacement.
     *
     * @param pageId ID of the page to retrieve
     * @param index identifies which binary file the page belongs to
     * @return the requested {@link Page} object
     */
    public abstract Page getPage(int pageId, int ...index);

    /**
     * Creates a new blank page in the buffer and assigns it the next sequential page ID.
     * The new page may later be written to disk when flushed.
     *
     * @param index identifies which file the page will belong to
     * @return the newly created {@link Page} object
     */
    public abstract Page createPage(int ...index);

    /**
     * Marks a page as dirty, indicating it has been modified in memory
     * and must be written back to disk before eviction or during a flush.
     * This does not trigger an immediate write.
     *
     * @param pageId ID of the modified page
     * @param index identifies which file the page belongs to
     */
    public abstract void markDirty(int pageId, int ...index);

    /**
     * Unpins a page, decreasing its pin count.
     * Once the pin count reaches zero, the page becomes eligible for replacement.
     *
     * @param pageId ID of the page to unpin
     * @param index identifies which file the page belongs to
     */
    public abstract void unpinPage(int pageId, int ...index);

    /**
     * Writes a specific page to its corresponding binary file on disk.
     * Overwrites the existing data at that page’s position.
     *
     * @param page the {@link Page} to write
     * @param index identifies which file to write the page to
     */
    public abstract void writeToBinaryFile(Page page, int ...index);

    /**
     * Forces the buffer manager to flush all pages to disk,
     * regardless of their pin count.
     * Only dirty pages are written back; clean pages are ignored.
     * After this operation, the buffer is emptied.
     */
    public abstract void force();

    /**
     * Retrieves the root page ID of a given file (e.g., for B+ tree structures).
     * The root page ID is stored in the file’s metadata.
     *
     * @param index identifies which file’s root page ID to retrieve
     * @return the root page ID as a string
     */
    public abstract String getRootPageId(int ...index);

    /**
     * Updates the root page ID in the metadata of the specified file.
     *
     * @param rootPageId new root page ID to set
     * @param index identifies which file’s root page ID to update
     */
    public abstract void setRootPageId(int rootPageId, int ...index);

    /**
     * Returns the physical file path associated with a given file index.
     *
     * @param index index representing a specific file
     * @return absolute file path as a string
     */
    public abstract String getFilePath(int index);

    /**
     * Returns the total number of pages currently stored
     * in the specified file on disk.
     *
     * @param index identifies which file to query
     * @return total count of pages currently existing in the file
     */
    public abstract int getTotalPages(int index);
}
