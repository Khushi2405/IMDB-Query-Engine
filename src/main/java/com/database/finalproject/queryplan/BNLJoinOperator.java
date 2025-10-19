package com.database.finalproject.queryplan;

import com.database.finalproject.model.record.*;
import com.database.finalproject.buffermanager.BufferManagerImpl;
import com.database.finalproject.model.page.DataPage;

import static com.database.finalproject.constants.PageConstants.BNL_MOVIE_WORKED_ON_INDEX;
import static com.database.finalproject.constants.PageConstants.BNL_MOVIE_WORKED_ON_PEOPLE_INDEX;

import java.util.*;

public class BNLJoinOperator<L extends ParentRecord, R extends ParentRecord, O extends ParentRecord> implements Operator<O> {
    private final Operator<L> leftChild;
    private final Operator<R> rightChild;
    private final int joinAttrLeft;
    private final int joinAttrRight;
    private final int blockSize;
    private final int joinResultType;
    private final BufferManagerImpl bufferManager;

    private Map<String, List<L>> hashTable;
    private Iterator<L> outerMatchesIterator;
    private R currentRightRecord;
    private int previousBlockPages;


    public BNLJoinOperator(Operator<L> leftChild, Operator<R> rightChild, int joinAttrLeft, int joinAttrRight, int blockSize,
            BufferManagerImpl bf, int joinResultType) {
        this.leftChild = leftChild;
        this.rightChild = rightChild;
        this.joinAttrLeft = joinAttrLeft;
        this.joinAttrRight = joinAttrRight;
        this.blockSize = blockSize;
        this.joinResultType = joinResultType;
        this.bufferManager = bf;
        this.previousBlockPages = 0;
    }

    @Override
    public void open() {
        leftChild.open();
        rightChild.open();
        hashTable = new HashMap<>();
        loadNextLeftBlock();
    }

    @Override
    public O next() {
        while (true) {
            if (outerMatchesIterator != null && outerMatchesIterator.hasNext()) {
                L outerRecord = outerMatchesIterator.next();
                return joinRecords(outerRecord, currentRightRecord);
            }

            currentRightRecord = rightChild.next();
            if (currentRightRecord == null) {
                if (!loadNextLeftBlock()) {
                    return null; // All data processed
                }
                continue;
            }

            String key = currentRightRecord.getFieldByIndex(joinAttrRight);
            List<L> matchingOuter = hashTable.getOrDefault(key, new ArrayList<>());
            outerMatchesIterator = matchingOuter.iterator();
        }
    }

    @Override
    public void close() {
        leftChild.close();
        rightChild.close();
        hashTable.clear();
    }

    private boolean loadNextLeftBlock() {
        hashTable.clear();
        rightChild.close();
        rightChild.open();

        unpinLeftBlock();
        int pagesLoaded = 0;
        boolean endOfFile = false;
        DataPage<L> page = (DataPage<L>) bufferManager.createPage(joinResultType);
        while (true) {
            while (!page.isFull()) {
                L record = leftChild.next();
                if(record == null) {
                    endOfFile = true;
                    break;
                }
                page.insertRecord(record);
                String key = record.getFieldByIndex(joinAttrLeft);
                hashTable.computeIfAbsent(key, k -> new ArrayList<>()).add(record);
            }
            pagesLoaded++;

            if (pagesLoaded == blockSize || endOfFile) {
                break;
            }
            page = (DataPage<L>) bufferManager.createPage(joinResultType);
        }
        previousBlockPages = pagesLoaded;
        return !hashTable.isEmpty();
    }

    private void unpinLeftBlock() {
        for (int i = 0; i < previousBlockPages; i++) {
            bufferManager.unpinPage(i, joinResultType);
        }
        bufferManager.resetBlockPageCount(joinResultType);
    }

    private O joinRecords(L left, R right) {
        if (joinResultType == BNL_MOVIE_WORKED_ON_INDEX) {
            MovieRecord movie = (MovieRecord) left;
            MoviePersonRecord moviePersonRecord = (MoviePersonRecord) right;

            byte[] movieId = movie.movieId();
            byte[] title = movie.movieTitle();
            byte[] personId = moviePersonRecord.personId();
            return (O) new MovieWorkedOnJoinRecord(movieId, personId, title);
        } else if (joinResultType == BNL_MOVIE_WORKED_ON_PEOPLE_INDEX) {
            MovieWorkedOnJoinRecord joined = (MovieWorkedOnJoinRecord) left;
            PeopleRecord person = (PeopleRecord) right;
            byte[] movieId = joined.movieId();
            byte[] personId = joined.personId();
            byte[] title = joined.title();
            byte[] name = person.name();
            return (O) new MovieWorkedOnPeopleJoinRecord(movieId, personId, title, name);
        }

        System.err.println("Incorrect JOIN result type");
        return null;
    }
}
