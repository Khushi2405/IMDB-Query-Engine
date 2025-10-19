package com.database.finalproject.model.page;

import com.database.finalproject.model.record.MovieRecord;
import java.util.Arrays;
import static com.database.finalproject.constants.PageConstants.*;

public class MovieDataPage extends DataPage<MovieRecord> {


    private byte[] pageArray = new byte[PAGE_SIZE];
    private final int pageId;

    public MovieDataPage(int pageId) {
        this.pageId = pageId;
    }

    @Override
    public byte[] getByteArray() {
        return pageArray;
    }


    public MovieRecord getRecord(int rowId) {
        int totalRows = binaryToDecimal(pageArray[PAGE_SIZE - 1]);
        if (rowId >= totalRows || rowId < 0) {
//            System.out.println("MovieRecord out of bounds");
            return null;
        }

        int offset = rowId * 39;
        byte[] movieId = Arrays.copyOfRange(pageArray, offset, offset + 9);
        byte[] movieTitle = Arrays.copyOfRange(pageArray, offset + 9, offset + 39);
        return new MovieRecord(movieId, movieTitle);
    }


    public int insertRecord(MovieRecord movieRecord) {
        int nextRow = binaryToDecimal(pageArray[PAGE_SIZE - 1]);
        if (nextRow == MOVIE_PAGE_ROW_LIMIT) {
            System.out.println("No space available to store more rows.");
            return -1; // Not enough space
        }
        int offset = nextRow * 39;
        System.arraycopy(movieRecord.movieId(), 0, pageArray, offset, 9);
        System.arraycopy(movieRecord.movieTitle(), 0, pageArray, offset + 9, 30);
        pageArray[PAGE_SIZE - 1] = decimalToBinary(nextRow + 1);
        return nextRow;

    }


    public boolean isFull() {
        return binaryToDecimal(pageArray[PAGE_SIZE - 1]) == MOVIE_PAGE_ROW_LIMIT;
    }

    @Override
    public int getPid() {
        return this.pageId;
    }


}

