package com.database.finalproject.controller;

import com.database.finalproject.btree.BTreeImpl;
import com.database.finalproject.buffermanager.BufferManagerImpl;
import com.database.finalproject.model.Comparator;
import com.database.finalproject.model.ProjectionType;
import com.database.finalproject.model.SelectionPredicate;
import com.database.finalproject.model.page.MovieDataPage;
import com.database.finalproject.model.record.*;
import com.database.finalproject.model.Rid;
import com.database.finalproject.queryplan.*;
import com.database.finalproject.repository.Utilities;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static com.database.finalproject.constants.PageConstants.*;

public class UserController {

    int bufferSize;
    BufferManagerImpl bf;
    BTreeImpl movieIdBtree;
    BTreeImpl movieTitleBtree;

    /**
     * to create any index file, uncomment the code below for that file(make sure to change the database_catalog.txt to initial value)
     * the initial value for database_catalog.tx is as below, change only for the files you are creating the index for
     * src/main/resources/static/movie_data_binary_heap.bin,0,0
     * src/main/resources/static/movie_id_index.bin,0,-1
     * src/main/resources/static/movie_title_index.bin,0,-1
     * src/main/resources/static/worked_on_data_binary_heap.bin,0,0
     * src/main/resources/static/people_data_binary_heap.bin,0,0
     * src/main/resources/static/projected_worked_on_data_binary_heap.bin,0,0
     * **/
    public UserController(int bufferSize) {
        this.bf = new BufferManagerImpl(bufferSize);
        this.bufferSize = bufferSize;
        this.movieIdBtree = new BTreeImpl(bf, MOVIE_ID_INDEX_PAGE_INDEX);
        this.movieTitleBtree = new BTreeImpl(bf, MOVIE_TITLE_INDEX_INDEX);
//        Utilities.loadMoviesDataset(bf, MOVIE_DATABASE_FILE);
//        Utilities.loadWorkedOnDataset(bf, WORKED_ON_DATABASE_FILE);
//        Utilities.loadPeopleDataset(bf, PEOPLE_DATABASE_FILE);
//        Utilities.createMovieIdIndex(bf, movieIdBtree);
//        Utilities.createMovieTitleIndex(bf, movieTitleBtree);

    }

    public UserController(int bufferSize, BTreeImpl movieIdBtree, BTreeImpl movieTitleBtree){
        this.bufferSize = bufferSize;
        this.bf = new BufferManagerImpl(bufferSize);
        this.movieIdBtree = new BTreeImpl(bf, MOVIE_ID_INDEX_PAGE_INDEX);
        this.movieTitleBtree = new BTreeImpl(bf, MOVIE_TITLE_INDEX_INDEX);
        Utilities.loadMoviesDataset(bf, MOVIE_DATABASE_FILE);
        Utilities.loadWorkedOnDataset(bf, WORKED_ON_DATABASE_FILE);
        Utilities.loadPeopleDataset(bf, PEOPLE_DATABASE_FILE);
        Utilities.createMovieIdIndex(bf, movieIdBtree);
        Utilities.createMovieTitleIndex(bf, movieTitleBtree);
    }

    public MovieDataPage createMoviePage(){
        return (MovieDataPage) bf.createPage();
    }

    public MovieDataPage getMoviePage(int pageId){
        return (MovieDataPage) bf.getPage(pageId);
    }

    public void makeDirty(int pageId){
        bf.markDirty(pageId);
    }

    public void unpinPage(int pageId){
        bf.unpinPage(pageId);
    }

    public void force(){
        bf.force();
    }

    public List<MovieRecord> searchMovieId(String key){
        Iterator<Rid> res = movieIdBtree.search(key);
        return fetchRows(res);
    }

    public List<MovieRecord> searchMovieTitle(String key){
        Iterator<Rid> res = movieTitleBtree.search(key);
        return fetchRows(res);
    }

    public List<MovieRecord> rangeSearchMovieId(String startKey, String endKey){
        Iterator<Rid> res = movieIdBtree.rangeSearch(startKey, endKey);
        return fetchRows(res);
    }

    public List<MovieRecord> rangeSearchMovieTitle(String startKey, String endKey){
        Iterator<Rid> res = movieTitleBtree.rangeSearch(startKey, endKey);
        return fetchRows(res);
    }

    private List<MovieRecord> fetchRows(Iterator<Rid> res) {
        List<MovieRecord> ans = new ArrayList<>();
        List<String> list = new ArrayList<>();
        while (res.hasNext()) {
            Rid r = res.next();
            int pageId = r.getPageId();
            int slotId = r.getSlotId();
            MovieDataPage page = (MovieDataPage) bf.getPage(pageId, 0);
            MovieRecord movieRecord = page.getRecord(slotId);
            ans.add(movieRecord);
            list.add(movieRecord.getFieldByIndex(0) + " , " + movieRecord.getFieldByIndex(1));
            bf.unpinPage(pageId);
        }
        printResults(list);
        return ans;
    }

    public Object runQuery(String startRange, String endRange) {
        // Scan Movies
		ScanOperator<MovieRecord> movieScan = new ScanOperator<>(bf, MOVIES_DATA_PAGE_INDEX);

		// Select Movies title BETWEEN startRange and endRange
		List<SelectionPredicate> moviePredicates = List.of(
				new SelectionPredicate(1, startRange, Comparator.GREATER_THAN_OR_EQUALS),
				new SelectionPredicate(1, endRange, Comparator.LESS_THAN_OR_EQUALS)
		);
		SelectionOperator<MovieRecord> movieSelection = new SelectionOperator<>(movieScan, moviePredicates);

		// Scan WorkedOn
		ScanOperator<WorkedOnRecord> workedOnScan = new ScanOperator<>(bf, WORKED_ON_DATA_PAGE_INDEX);

		// Select WorkedOn where category = director
		List<SelectionPredicate> workedOnPredicates = List.of(
				new SelectionPredicate(2, "director", Comparator.EQUALS)
		);
		SelectionOperator<WorkedOnRecord> workedOnSelection = new SelectionOperator<>(workedOnScan, workedOnPredicates);

		// Projection on WorkedOn to (movieId, personId)
		ProjectionOperator<WorkedOnRecord, MoviePersonRecord> workedOnProjection =
				new ProjectionOperator<>(workedOnSelection, ProjectionType.PROJECTION_ON_WORKED_ON);

		// Materialize WorkedOn
		MaterializeOperator<MoviePersonRecord> workedOnMaterialized =
				new MaterializeOperator<>(workedOnProjection, bf, MOVIE_PERSON_DATA_PAGE_INDEX);

		// First BNL Join: Movies ⋈ WorkedOn
		int blockSize = (bufferSize - 4) / 2; // assume C = 4 for safety
//        System.out.println(bufferSize + " " + blockSize);
		BNLJoinOperator<MovieRecord, MoviePersonRecord, MovieWorkedOnJoinRecord> firstJoin =
				new BNLJoinOperator<>(
						movieSelection,
						workedOnMaterialized,
						0, 0,
						blockSize,
						bf,
						BNL_MOVIE_WORKED_ON_INDEX
				);

		// Scan People
		ScanOperator<PeopleRecord> peopleScan = new ScanOperator<>(bf, PEOPLE_DATA_PAGE_INDEX);

		// Second BNL Join: (Movies ⋈ WorkedOn) ⋈ People
		BNLJoinOperator<MovieWorkedOnJoinRecord, PeopleRecord, MovieWorkedOnPeopleJoinRecord> secondJoin =
				new BNLJoinOperator<>(
						firstJoin,
						peopleScan,
						1, 0, // personId from left join, personId from People
						blockSize,
						bf,
						BNL_MOVIE_WORKED_ON_PEOPLE_INDEX
				);

		// Final Projection: (title, name)
		ProjectionOperator<MovieWorkedOnPeopleJoinRecord, TitleNameRecord> finalProjection =
				new ProjectionOperator<>(secondJoin, ProjectionType.PROJECTION_ON_FINAL_JOIN);

		// Open the pipeline
		finalProjection.open();

		// Prepare Excel file
		Workbook workbook = new XSSFWorkbook();
		Sheet sheet = workbook.createSheet("QueryResults");
		int rowNum = 0;
		Row headerRow = sheet.createRow(rowNum++);
		headerRow.createCell(0).setCellValue("Title");
		headerRow.createCell(1).setCellValue("Name");

		TitleNameRecord output;
        List<String> list = new ArrayList<>();
		while ((output = finalProjection.next()) != null) {
			String title = new String(removeTrailingBytes(output.title())).trim();
			String name = new String(removeTrailingBytes(output.name())).trim();
            list.add(new String(title + " , " + name));

			// Print to console uncomment if want to print on command line
//			System.out.println(title + "," + name);

			// Write to Excel comment if only want to print on command line
			Row row = sheet.createRow(rowNum++);
			row.createCell(0).setCellValue(title);
			row.createCell(1).setCellValue(name);
		}

		finalProjection.close();

		// Write Excel file
		try (FileOutputStream fileOut = new FileOutputStream("src/main/resources/static/query_output_"+ startRange + "_" + endRange + ".xlsx")) {
			workbook.write(fileOut);
			workbook.close();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}

//        System.out.println("Total records in Movies Table : " + movieScan.getTotalRecords());
//        System.out.println("Total records in Worked On Table : " + workedOnScan.getTotalRecords());
//        System.out.println("Total records in People Table : " + peopleScan.getTotalRecords());
//        System.out.println("Total records matched with Worked On Table : " + workedOnSelection.getTotalMatched());
//        System.out.println("Total records matched with Movies Table : " + movieSelection.getTotalMatched());
//        System.out.println("Actual total I/Os : " + bf.getIoCounter());
//
//
//		System.out.println("Query completed. Results saved to src/main/resources/static/query_output_"+ startRange + "_" + endRange + ".xlsx");
        Map<String, Object> result = new HashMap<>();
        result.put("iocount", bf.getIoCounter());
        result.put("movieSelection", movieSelection.getTotalMatched());
        printResults(list);
        return result;
    }

    private void printResults(List<String> list) {
        for(String s : list){
            System.out.println(s);
        }

        System.out.println("\n\n\nTotal Records - " + list.size());
    }
}
