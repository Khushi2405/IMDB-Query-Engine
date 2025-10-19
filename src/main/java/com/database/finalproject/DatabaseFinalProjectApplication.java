package com.database.finalproject;

import com.database.finalproject.controller.UserController;
import com.database.finalproject.repository.Utilities;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.database.finalproject.constants.PageConstants.removeTrailingBytes;
import static com.database.finalproject.constants.PageConstants.truncateOrPadByteArray;


@SpringBootApplication
public class DatabaseFinalProjectApplication {

	public static void main(String[] args) {
		SpringApplication.run(DatabaseFinalProjectApplication.class, args);

		Scanner scanner = new Scanner(System.in);

		System.out.println("Welcome to IMDB Query Engine CLI!");
		boolean running = true;

		System.out.println("Enter buffer size/RAM you want to allot for this session (default is 10000)");
		int bufferSize = 0;
		try{
			bufferSize = Integer.parseInt(scanner.nextLine().trim());
		}
		catch(Exception e){
			bufferSize = 10000;
		}
		UserController controller = new UserController(bufferSize);

		while(running){
			System.out.println("\nAvailable commands:");
			System.out.println("1. run_query <startMovieRange> <endMovieRange>");
			System.out.println("2. search_movieId <movieId>");
			System.out.println("3. search_movieId_range <startMovieIdRange> <endMovieIdRange>");
			System.out.println("4. search_movieTitle <movieTitle>");
			System.out.println("5. search_movieTitle_range <startMovieTitleRange> <endMovieTitleRange>");
			System.out.println("6. exit");

			String input = scanner.nextLine().trim();
			if (input.isEmpty()) continue;

			Matcher matcher = Pattern.compile("\"([^\"]*)\"|(\\S+)").matcher(input);
			List<String> tokens = new ArrayList<>();
			while (matcher.find()) {
				if (matcher.group(1) != null)
					tokens.add(matcher.group(1)); // remove quotes
				else
					tokens.add(matcher.group(2));
			}

			String[] parts = tokens.toArray(new String[0]);
			String command = parts[0];
			try {
				switch (command) {
					case "run_query":
						if (parts.length == 3) {
							String startRange = parts[1];
							String endRange = parts[2];
							controller.runQuery(startRange, endRange);
						} else {
							System.out.println("Usage: run_query <startMovieRange> <endMovieRange>");
						}
						break;

					case "search_movieId":
						if (parts.length == 2) {
							controller.searchMovieId(parts[1]);
						} else {
							System.out.println("Usage: search_movieId <movieId>");
						}
						break;

					case "search_movieId_range":
						if (parts.length == 3) {
							controller.rangeSearchMovieId(parts[1], parts[2]);
						} else {
							System.out.println("Usage: search_movieId_range <startMovieIdRange> <endMovieIdRange>");
						}
						break;

					case "search_movieTitle":
						if (parts.length == 2) {
							controller.searchMovieTitle(parts[1]);
						} else {
							System.out.println("Usage: search_movieTitle <movieTitle>");
						}
						break;

					case "search_movieTitle_range":
						if (parts.length == 3) {
							controller.rangeSearchMovieTitle(parts[1], parts[2]);
						} else {
							System.out.println("Usage: search_movieTitle_range <startMovieTitleRange> <endMovieTitleRange>");
						}
						break;

					case "exit":
						running = false;
						System.out.println("Exiting IMDB Query Engine. Goodbye!");
						break;

					default:
						System.out.println("Unknown command. Please try again.");
				}
			} catch (Exception e) {
				System.out.println("Error: " + e.getMessage());
			}
		}

		scanner.close();

	}



}
