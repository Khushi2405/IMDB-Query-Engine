package com.database.finalproject.buffermanager;

import com.database.finalproject.model.*;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Arrays;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicInteger;

import com.database.finalproject.model.page.*;
import com.database.finalproject.model.record.MoviePersonRecord;
import com.database.finalproject.model.record.MovieRecord;
import com.database.finalproject.model.record.PeopleRecord;
import com.database.finalproject.model.record.WorkedOnRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import static com.database.finalproject.constants.PageConstants.*;

@Component
public class BufferManagerImpl extends BufferManager {

    DLLNode headBufferPool, tailBufferPool;
    Map<Pair<Integer, Integer>, DLLNode> pageHash;
    DatabaseCatalog catalog;
    RandomAccessFile movieDataRaf;
    RandomAccessFile peopleDataRaf;
    RandomAccessFile workedOnDataRaf;
    RandomAccessFile moviePersonDataRaf;
    RandomAccessFile movieIdIndexRaf;
    RandomAccessFile movieTitleIndexRaf;
    int movieWorksOnBlockPageCount;
    int movieWorksOnPeopleBlockPageCount;
    AtomicInteger ioCounter;
    public static Logger logger = LoggerFactory.getLogger(BufferManagerImpl.class);

    public BufferManagerImpl(@Value("${buffer.size:10}") int bufferSize) {
        super(bufferSize);
        catalog = new DatabaseCatalog("src/main/resources/static/database_catalog.txt");
        this.pageHash = new HashMap<>();
        tailBufferPool = null;
        headBufferPool = null;
        this.movieWorksOnPeopleBlockPageCount = 0;
        this.movieWorksOnBlockPageCount = 0;
        this.ioCounter = new AtomicInteger();

        try {
            movieDataRaf = new RandomAccessFile(catalog.getCatalog(MOVIES_DATA_PAGE_INDEX).get(DATABASE_CATALOGUE_KEY_FILENAME), "rw");
//            System.out.println(dataRaf.length()/PAGE_SIZE);
        } catch (IOException e) {
            System.out.println("Error in RAF, file cannot be created");
            throw new RuntimeException(e);
        }
        try {
            peopleDataRaf = new RandomAccessFile(catalog.getCatalog(PEOPLE_DATA_PAGE_INDEX).get(DATABASE_CATALOGUE_KEY_FILENAME), "rw");
//            System.out.println(dataRaf.length()/PAGE_SIZE);
        } catch (IOException e) {
            System.out.println("Error in RAF, file cannot be created");
            throw new RuntimeException(e);
        }
        try {
            moviePersonDataRaf = new RandomAccessFile(catalog.getCatalog(MOVIE_PERSON_DATA_PAGE_INDEX).get(DATABASE_CATALOGUE_KEY_FILENAME), "rw");
//            System.out.println(dataRaf.length()/PAGE_SIZE);
        } catch (IOException e) {
            System.out.println("Error in RAF, file cannot be created");
            throw new RuntimeException(e);
        }
        try {
            workedOnDataRaf = new RandomAccessFile(catalog.getCatalog(WORKED_ON_DATA_PAGE_INDEX).get(DATABASE_CATALOGUE_KEY_FILENAME), "rw");
//            System.out.println(dataRaf.length()/PAGE_SIZE);
        } catch (IOException e) {
            System.out.println("Error in RAF, file cannot be created");
            throw new RuntimeException(e);
        }
        try {
            movieIdIndexRaf = new RandomAccessFile(catalog.getCatalog(MOVIE_ID_INDEX_PAGE_INDEX).get(DATABASE_CATALOGUE_KEY_FILENAME), "rw");
        } catch (IOException e) {
            System.out.println("Error in RAF, file cannot be created");
            throw new RuntimeException(e);
        }
        try {
            movieTitleIndexRaf = new RandomAccessFile(catalog.getCatalog(MOVIE_TITLE_INDEX_INDEX).get(DATABASE_CATALOGUE_KEY_FILENAME), "rw");
        } catch (IOException e) {
            System.out.println("Error in RAF, file cannot be created");
            throw new RuntimeException(e);
        }
    }

    @Override
    public Page getPage(int pageId, int ...index) {
        // Logic to fetch a page from buffer
        int catalogIndex = getCatalogIndex(index);
        ioCounter.incrementAndGet();
        Pair<Integer, Integer> pair = new Pair(pageId, catalogIndex);
        // if page id already in buffer then move it to front and increment the pin counter
        if (pageHash.containsKey(pair)) {
            DLLNode currNode = pageHash.get(pair);
            bringPageFront(currNode);
            currNode.pinCount++;
            return currNode.page;
        } else {
            if (pageHash.size() > bufferSize - 1) {
                // implement LRU
                if (!removeLRUNode()) {
                    // buffer manager is full throw error
                    logger.error("Buffer manager is full cannot fetch new pages");
                    return null;
                }
            }
            try {
                Page page = readPage(pageId, catalogIndex);

                // if page is null then page with the page id not found return null
                if (page == null) {
//                    logger.error("Page not found: {}", pageId);
                    return null;
                }
                DLLNode currNode = new DLLNode(page, catalogIndex);
                addNewPage(pair, currNode);
                return page;
            } catch (IOException e) {
                logger.error("Cannot read file");
                return null;
            }

        }
    }

    @Override
    public Page createPage(int ...index) {
        // Logic to create a new page

        int catalogIndex = getCatalogIndex(index);
        ioCounter.incrementAndGet();
        if (pageHash.size() > bufferSize - 1) {
            if (!removeLRUNode()) {
                // buffer manager is full throw error
                logger.error("Buffer manager is full cannot create new pages");
                return null;
            }
        }
        int pageCount = 0;
        if (catalogIndex >= 0)
            pageCount = Integer.parseInt(catalog.getCatalog(catalogIndex).get("totalPages"));
        Page page;
        if (catalogIndex == MOVIES_DATA_PAGE_INDEX) {
            page = new MovieDataPage(pageCount++);
        } else if (catalogIndex == MOVIE_ID_INDEX_PAGE_INDEX) {
            page = new IndexPage(pageCount++, MOVIE_ID_INDEX_PAGE_INDEX);
        } else if (catalogIndex == MOVIE_TITLE_INDEX_INDEX) {
            page = new IndexPage(pageCount++, MOVIE_TITLE_INDEX_INDEX);
        } else if (catalogIndex == WORKED_ON_DATA_PAGE_INDEX) {
            page = new WorkedOnDataPage(pageCount++);
        } else if (catalogIndex == PEOPLE_DATA_PAGE_INDEX) {
            page = new PeopleDataPage(pageCount++);
        } else if (catalogIndex == BNL_MOVIE_WORKED_ON_INDEX) {
            page = new MovieDataPage(movieWorksOnBlockPageCount++);
        } else if (catalogIndex == BNL_MOVIE_WORKED_ON_PEOPLE_INDEX) {
            page = new MoviesWorkedOnJoinPage(movieWorksOnPeopleBlockPageCount++);
        } else if (catalogIndex == MOVIE_PERSON_DATA_PAGE_INDEX) {
            page = new MoviePersonDataPage(pageCount++);
        } else {
            logger.error("Incorrect index for page");
            return null;
        }
        if (catalogIndex >= 0) {
            catalog.setCatalog(catalogIndex, DATABASE_CATALOGUE_KEY_TOTAL_PAGES, String.valueOf(pageCount));
        }
        DLLNode currNode = new DLLNode(page, catalogIndex);
        addNewPage(new Pair<>(page.getPid(), catalogIndex), currNode);
        if (catalogIndex >= 0)
            markDirty(page.getPid(), catalogIndex);
        return page;
    }

    @Override
    public void markDirty(int pageId, int ...index) {
        // Mark page as dirty

        int catalogIndex = getCatalogIndex(index);

        Pair<Integer, Integer> pair = new Pair<>(pageId, catalogIndex);
        if (pageHash.containsKey(pair)) {
            pageHash.get(pair).isDirty = true;
            return;
        }
//        logger.error("Page not found: {}", pageId);
    }

    @Override
    public void unpinPage(int pageId, int ...index) {
        // Unpin page
        int catalogIndex = getCatalogIndex(index);
        Pair<Integer, Integer> pair = new Pair<>(pageId, catalogIndex);
        if (pageHash.containsKey(pair)) {
            pageHash.get(pair).pinCount--;
            return;
        }
//        logger.error("Page not found: {}", pageId);
    }

    @Override
    public void writeToBinaryFile(Page page, int ...index) {
        int catalogIndex = getCatalogIndex(index);
        if (catalogIndex < 0) {
            return;
        }
        RandomAccessFile raf;
        if (catalogIndex == MOVIES_DATA_PAGE_INDEX) raf = movieDataRaf;
        else if (catalogIndex == PEOPLE_DATA_PAGE_INDEX) raf = peopleDataRaf;
        else if (catalogIndex == WORKED_ON_DATA_PAGE_INDEX) raf = workedOnDataRaf;
        else if (catalogIndex == MOVIE_PERSON_DATA_PAGE_INDEX) raf = moviePersonDataRaf;
        else if (catalogIndex == MOVIE_ID_INDEX_PAGE_INDEX) raf = movieIdIndexRaf;
        else if (catalogIndex == MOVIE_TITLE_INDEX_INDEX)raf = movieTitleIndexRaf;
        else{
            logger.error("Invalid index");
            return;
        }

        try {
            long offset = (long) (page.getPid()) * PAGE_SIZE;
            raf.seek(offset);

            byte[] pageData = page.getByteArray();

            raf.write(pageData);
            raf.getFD().sync();

            // System.out.println("Updated page " + page.getPid() + " successfully!");
        } catch (IOException e) {
            // System.out.println("Reached the exception");
            throw new RuntimeException(e);
        }
    }

    @Override
    public void force() {
        while(headBufferPool != null){
            if(headBufferPool.isDirty) {
                writeToBinaryFile(headBufferPool.page, headBufferPool.index);
            }
            headBufferPool = headBufferPool.next;
        }
        pageHash.clear();
        headBufferPool = null;
        tailBufferPool = null;
    }

    @Override
    public String getRootPageId(int ...index){
        int catalogIndex = getCatalogIndex(index);
        return catalog.getCatalog(catalogIndex).get(DATABASE_CATALOGUE_KEY_ROOT_PAGE);
    }

    @Override
    public void setRootPageId(int rootPageId, int... index) {
        int catalogIndex = getCatalogIndex(index);
        catalog.setCatalog(catalogIndex, DATABASE_CATALOGUE_KEY_ROOT_PAGE, String.valueOf(rootPageId));
    }

    @Override
    public String getFilePath(int index) {
        int catalogIndex = getCatalogIndex(index);
        return catalog.getCatalog(catalogIndex).get(DATABASE_CATALOGUE_KEY_FILENAME);
    }

    @Override
    public int getTotalPages(int index) {
        int catalogIndex = getCatalogIndex(index);
        return Integer.parseInt(catalog.getCatalog(catalogIndex).get(DATABASE_CATALOGUE_KEY_TOTAL_PAGES));
    }

    public void resetBlockPageCount(int index) {
        if (index == BNL_MOVIE_WORKED_ON_INDEX) {
            movieWorksOnBlockPageCount = 0;
        }
        if (index == BNL_MOVIE_WORKED_ON_PEOPLE_INDEX) {
            movieWorksOnPeopleBlockPageCount = 0;
        }
    }

    private void bringPageFront(DLLNode currNode) {
        // if node is already at front don't do anything
        if (currNode == headBufferPool)
            return;

        // if node is at tail just point the prev node to null
        else if (currNode == tailBufferPool) {
            DLLNode prev = currNode.prev;
            prev.next = null;
            tailBufferPool = prev;
        }

        // if node in middle of list point to prev and next nodes to each other
        else {
            DLLNode prev = currNode.prev;
            DLLNode next = currNode.next;
            prev.next = next;
            next.prev = prev;
        }

        // now add the curr node to starting of list and change the head pointer
        currNode.prev = null;
        currNode.next = headBufferPool;
        headBufferPool.prev = currNode;
        headBufferPool = currNode;
    }

    private Page readPage(int pageId, int... index) throws IOException {
        int catalogIndex = getCatalogIndex(index);
        // TODO: read mode
        RandomAccessFile raf;
        if (catalogIndex == MOVIES_DATA_PAGE_INDEX)
            raf = movieDataRaf;
        else if (catalogIndex == WORKED_ON_DATA_PAGE_INDEX)
            raf = workedOnDataRaf;
        else if (catalogIndex == PEOPLE_DATA_PAGE_INDEX)
            raf = peopleDataRaf;
        else if (catalogIndex == MOVIE_PERSON_DATA_PAGE_INDEX)
            raf = moviePersonDataRaf;
        else if (catalogIndex == MOVIE_ID_INDEX_PAGE_INDEX)
            raf = movieIdIndexRaf;
        else if (catalogIndex == MOVIE_TITLE_INDEX_INDEX)
            raf = movieTitleIndexRaf;
        else{
            logger.error("Invalid index");
            return null;
        }

        Page page = null;
        try {
            long offset = (long) (pageId) * PAGE_SIZE;
            if (raf.length() <= offset) {
                return null;
            }
            raf.seek(offset);
            byte[] pageData = new byte[PAGE_SIZE]; // Buffer to hold 4096 bytes (one page)
            raf.readFully(pageData);

            page = switch (catalogIndex) {
                case MOVIES_DATA_PAGE_INDEX,
                        PEOPLE_DATA_PAGE_INDEX,
                        WORKED_ON_DATA_PAGE_INDEX,
                        MOVIE_PERSON_DATA_PAGE_INDEX -> readDataPage(pageId, pageData, catalogIndex);
                case MOVIE_ID_INDEX_PAGE_INDEX,
                        MOVIE_TITLE_INDEX_INDEX -> readIndexPage(pageId, pageData, catalogIndex);
                default -> {
                    logger.error("Invalid index for reading page");
                    yield null;
                }
            };
            // System.out.println(page.getRow(0));
            // System.out.println(page.getRow(PAGE_ROW_LIMIT - 1));
        } catch (IOException e) {
            logger.error("Cannot read file");
            return null;
        }
        return page;
    }

    // read data page
    private Page readDataPage(int pageId, byte[] pageData, int catalogIndex) {
        Page page = new MovieDataPage(pageId);
        int nextRow = binaryToDecimal(pageData[PAGE_SIZE - 1]);
        switch (catalogIndex) {
            case MOVIES_DATA_PAGE_INDEX -> {
                page = new MovieDataPage(pageId);
                for (int i = 0; i < nextRow; i++) {
                    int offset = i * 39;
                    byte[] movieId = Arrays.copyOfRange(pageData, offset, offset + 9);
                    byte[] movieTitle = Arrays.copyOfRange(pageData, offset + 9, offset + 39);
                    ((MovieDataPage) page).insertRecord(new MovieRecord(movieId, movieTitle));
                }
            }

            case WORKED_ON_DATA_PAGE_INDEX -> {
                page = new WorkedOnDataPage(pageId);
                for (int i = 0; i < nextRow; i++) {
                    int offset = i * 39;
                    byte[] movieId = Arrays.copyOfRange(pageData, offset, offset + 9);
                    byte[] personId = Arrays.copyOfRange(pageData, offset + 9, offset + 19);
                    byte[] category = Arrays.copyOfRange(pageData, offset + 19, offset + 39);
                    ((WorkedOnDataPage) page).insertRecord(new WorkedOnRecord(movieId, personId, category));
                }
            }

            case PEOPLE_DATA_PAGE_INDEX -> {
                page = new PeopleDataPage(pageId);
                for (int i = 0; i < nextRow; i++) {
                    int offset = i * 115;
                    byte[] personId = Arrays.copyOfRange(pageData, offset, offset + 10);
                    byte[] name = Arrays.copyOfRange(pageData, offset + 10, offset + 115);
                    ((PeopleDataPage) page).insertRecord(new PeopleRecord(personId, name));
                }
            }

            case MOVIE_PERSON_DATA_PAGE_INDEX -> {
                page = new MoviePersonDataPage(pageId);
                for (int i = 0; i < nextRow; i++) {
                    int offset = i * MOVIE_PERSON_ROW_SIZE;
                    byte[] movieId = Arrays.copyOfRange(pageData, offset, offset + 9);
                    byte[] personId = Arrays.copyOfRange(pageData, offset + 9, offset + 19);
                    ((MoviePersonDataPage) page).insertRecord(new MoviePersonRecord(movieId, personId));
                }
            }

            default -> {
                logger.error("Invalid data page catalog index: {}", catalogIndex);
                return null;
            }
        }

        return page;
    }

    // read index page
    private Page readIndexPage(int pageId, byte[] pageData, int catalogIndex) {
        Page page;
        if (catalogIndex == MOVIE_ID_INDEX_PAGE_INDEX) {
            page = new IndexPage(pageId, MOVIE_ID_INDEX_PAGE_INDEX);
            ((IndexPage) page).setByteArray(pageData);
        } else {
            page = new IndexPage(pageId, MOVIE_TITLE_INDEX_INDEX);
            ((IndexPage) page).setByteArray(pageData);
        }
        return page;
    }

    private boolean removeLRUNode() {
        DLLNode unpinnedNode = tailBufferPool;
        if(unpinnedNode == headBufferPool){
            headBufferPool = null;
            tailBufferPool = null;
            pageHash.clear();
            return true;
        }
        while (unpinnedNode != null && unpinnedNode.pinCount != 0) {
            unpinnedNode = unpinnedNode.prev;
        }
        if (unpinnedNode == null) {
            // no such page with pincount 0
            // throw error
            return false;
        } else {
            if (unpinnedNode.isDirty) {
                writeToBinaryFile(unpinnedNode.page, unpinnedNode.index);
            }
            pageHash.remove(new Pair<>(unpinnedNode.page.getPid(), unpinnedNode.index));
            DLLNode prev = unpinnedNode.prev;
            DLLNode next = unpinnedNode.next;
            if (unpinnedNode == tailBufferPool) {
                prev.next = null;
                tailBufferPool = prev;
            } else if (unpinnedNode == headBufferPool) {
                headBufferPool = next;
            } else {
                prev.next = next;
                next.prev = prev;
            }
        }
        return true;
    }

    private void addNewPage(Pair<Integer, Integer> pair, DLLNode currNode) {
        pageHash.put(pair, currNode);
        if (headBufferPool == tailBufferPool && tailBufferPool == null) {
            headBufferPool = currNode;
            tailBufferPool = currNode;
        } else {
            currNode.prev = null;
            currNode.next = headBufferPool;
            headBufferPool.prev = currNode;
            headBufferPool = currNode;

        }
        currNode.pinCount++;
    }

    private int getCatalogIndex(int ...index){
        int catalogIndex;
        if(index.length == 0) return -1;
        else catalogIndex = index[0];
        return catalogIndex;
    }

    public void printList(){
        DLLNode node = headBufferPool;
        while(node != null){
            System.out.print(node.page.getPid() + " ");
            node = node.next;
        }
        System.out.println();
    }

    public AtomicInteger getIoCounter(){
        return ioCounter;
    }

}
