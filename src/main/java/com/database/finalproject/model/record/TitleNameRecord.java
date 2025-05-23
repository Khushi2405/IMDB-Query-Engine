package com.database.finalproject.model.record;

import static com.database.finalproject.constants.PageConstants.*;

public record TitleNameRecord(byte[] title, byte[] name) implements ParentRecord {
    public TitleNameRecord {
        title = truncateOrPadByteArray(title, MOVIE_TITLE_SIZE);
        name = truncateOrPadByteArray(name, PERSON_NAME_SIZE);
    }


    @Override
    public String toString() {
        return "MoviePersonRecord{" +
                "title=" + new String(removeTrailingBytes(title)).trim() +
                ", name=" + new String(removeTrailingBytes(name)).trim() +
                '}';
    }

    @Override
    public String getFieldByIndex(int index) {
        return switch (index) {
            case 0 -> new String(removeTrailingBytes(title)).trim();
            case 1 -> new String(removeTrailingBytes(name)).trim();
            default -> throw new IllegalArgumentException("Invalid field index for TitleNameRecord: " + index);
        };
    }
}
