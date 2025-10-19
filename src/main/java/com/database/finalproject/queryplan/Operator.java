package com.database.finalproject.queryplan;

import com.database.finalproject.model.record.ParentRecord;

/**
 * Represents an operator in a query execution plan.
 *
 * <p>This interface defines the standard iterator-style methods
 * used by all query operators (e.g., Scan, Join, Filter, Project, etc.).
 *
 * <p>The typical execution workflow is:
 * <ol>
 *   <li>{@link #open()} — initialize the operator and allocate required resources.</li>
 *   <li>{@link #next()} — fetch the next available record in the result set, one at a time.</li>
 *   <li>{@link #close()} — release resources and cleanup after execution.</li>
 * </ol>
 *
 * @param <T> the type of record produced by this operator, extending {@link ParentRecord}
 */
public interface Operator<T extends ParentRecord> {
    /**
     * Opens the operator for execution.
     * <p>This method is called once before the first call to {@link #next()}.
     * It typically initializes internal data structures, buffer pages, or child operators.</p>
     */
    void open();

    /**
     * Returns the next record from this operator’s output.
     * <p>Each call retrieves the next available {@link ParentRecord} until no more results remain,
     * at which point this method should return {@code null}.</p>
     *
     * @return the next record, or {@code null} if no more records are available
     */
    T next();

    /**
     * Closes the operator and releases all associated resources.
     * <p>After this call, the operator should not be used again unless reopened.</p>
     */
    void close();
}
