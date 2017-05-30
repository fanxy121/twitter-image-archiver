package core;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
//import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectOutputStream;
//import java.io.ObjectInputStream;
//import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

import lib.TwitterSearchAPI.main.java.uk.co.tomkdickinson.twitter.search.InvalidQueryException;
import lib.TwitterSearchAPI.main.java.uk.co.tomkdickinson.twitter.search.Tweet;
import lib.TwitterSearchAPI.main.java.uk.co.tomkdickinson.twitter.search.TwitterSearch;
import lib.TwitterSearchAPI.main.java.uk.co.tomkdickinson.twitter.search.TwitterSearchImpl;

public class TwitterImageArchiver {

	private static TwitterImageArchiver instance = null;

	private static TwitterSearch twitterSearch = null;

	private static final int SEARCH = TwitterSearch.SEARCH;
	private static final int MEDIA_TIMELINE = TwitterSearch.MEDIA_TIMELINE;
	private static final int LIKES = TwitterSearch.LIKES;

	private static final int MAX_ATTEMPTS = 3;

	private static final int BACKOFF_TIME_SECONDS = 60;

	private TwitterImageArchiver() {
		twitterSearch = new TwitterSearchImpl();
	}

	public static TwitterImageArchiver getInstance() {
		if (instance == null) {
			instance = new TwitterImageArchiver();
		}
		return instance;
	}

	// returns true if we can try again, false if exception can't be helped
	private boolean handleException(Exception e, int backoffSeconds) throws Exception {
		try {
			if (e instanceof FileNotFoundException) {
				return false;
			} else if (e instanceof IOException) {
				System.out.println(
						"Handling IOException, sleeping for " + backoffSeconds + "seconds");
				TimeUnit.SECONDS.sleep(backoffSeconds);
			}
		} catch (InterruptedException ie) {
			System.out.println("Interrupted during sleep");
			throw e;
		}

		return true;
	}

	private List<Tweet> getTweets(String query, int queryType, String sinceId)
			throws InvalidQueryException {
		return twitterSearch.search(query, queryType, sinceId);
	}

	private String getQueryTypeString(int queryType) {
		String queryTypeString = null;

		switch (queryType) {
			case SEARCH:
				queryTypeString = "Searches";
				break;
			case MEDIA_TIMELINE:
				queryTypeString = "Timelines";
				break;
			case LIKES:
				queryTypeString = "Likes";
				break;
			default:
		}

		return queryTypeString;
	}

	private String getQueryTypeDirectory(int queryType) {
		return getQueryTypeString(queryType);
	}

	private String getQueryFilename(int queryType) {
		String s = getQueryTypeDirectory(queryType) + File.separator + getQueryTypeString(queryType)
				+ "_queries.txt";
		System.out.println("query file: " + s);
		return s;
	}

	private List<String> getQueries(int queryType) throws IOException {
		List<String> queries = new ArrayList<String>();

		File file = new File(getQueryFilename(queryType));

		if (file.isFile()) {
			try (BufferedReader br = new BufferedReader(new FileReader(file))) {
				String line;
				while ((line = br.readLine()) != null) {
					String s = line.trim();

					if (s.length() > 0) {
						queries.add(s);
					}
				}
			}
		}

		return queries;
	}

	private String getSinceIdFilename(String query, int queryType) {
		query = getPathableQuery(query);

		return getQueryTypeDirectory(queryType) + File.separator + query + File.separator + query
				+ "_sinceId.txt";
	}

	private String getSinceId(String query, int queryType) throws IOException {
		query = getPathableQuery(query);

		String sinceId = null;

		File file = new File(getSinceIdFilename(query, queryType));

		if (file.isFile()) {
			try (BufferedReader br = new BufferedReader(new FileReader(file))) {
				String line;
				while ((line = br.readLine()) != null) {
					String s = line.trim();

					if (s.length() > 0) {
						sinceId = s;
						break;
					}
				}
			}
		}

		return sinceId;
	}

	private void setSinceId(String query, String sinceId, int queryType) throws IOException {
		query = getPathableQuery(query);

		String pathString = getSinceIdFilename(query, queryType);

		File file = new File(pathString);

		if (!file.isFile()) {
			file.getParentFile().mkdirs();
		}

		Files.write(Paths.get(pathString), sinceId.getBytes());
	}

	private String getPathableQuery(String query) {
		return query.replace(":", ".");
	}

	private void sync() throws Exception {
		for (int queryType : new int[] {SEARCH, MEDIA_TIMELINE}) {
			System.out.println("Getting queries from file");
			List<String> queries = getQueries(queryType);
			System.out.println("Got " + queries.size() + " query(s) from file");

			for (String query : queries) {
				System.out.println("Working with query: " + query);
				String sinceId = getSinceId(query, queryType);
				System.out.println("Got sinceId: " + sinceId);

				System.out.println("Getting tweets for: " + query);
				List<Tweet> tweets = new ArrayList<Tweet>();

				try {
					tweets.addAll(getTweets(query, queryType, sinceId));
					if (queryType == MEDIA_TIMELINE) {
						System.out.println("Getting search tweets for: " + query);
						tweets.addAll(
								getTweets("from:" + query + " filter:media", SEARCH, sinceId));
					}
				} catch (InvalidQueryException e) {
					e.printStackTrace();
				}

				System.out.println("Finished getting tweets for " + query);

				if (tweets.size() > 0) {
					System.out.println("Got " + tweets.size() + " tweets for: " + query);

					try (FileOutputStream out = new FileOutputStream(query + ".ser");
							ObjectOutputStream oos = new ObjectOutputStream(out)) {
						oos.writeObject(tweets);
					}

					System.out.println("Getting media for: " + query);
					saveMedia(query, queryType, tweets);
					System.out.println("Finished getting media for " + query);

					System.out.println(
							"Setting sinceId of " + tweets.get(0).getId() + "for: " + query);
					setSinceId(query, tweets.get(0).getId(), queryType);
				}
			}
		}
	}

	/*
	 * private void recover() { // TODO }
	 */

	private void saveMedia(String query, int queryType, List<Tweet> tweets) throws Exception {
		query = getPathableQuery(query);

		System.out.println("Getting media");

		for (int i = tweets.size() - 1; i >= 0; i--) {
			Tweet tweet = tweets.get(i);

			for (String urlString : tweet.getimageUrls()) {
				System.out.println("Got URL " + urlString);

				// format path
				String directory = getQueryTypeDirectory(queryType) + File.separator + query;

				DateFormat df = new SimpleDateFormat("yyMMdd-HHmmss");
				Date d = tweet.getCreatedAt();
				String date = df.format(d);

				String originalFilename = urlString.substring(urlString.lastIndexOf('/') + 1);

				File file = new File(directory + File.separator + date + "_" + originalFilename);

				String log = date + "," + originalFilename + "," + tweet.getId() + ",\""
						+ tweet.getText() + "\"";

				if (!file.isFile()) {
					URL url;
					try {
						url = new URL(urlString + ":orig");
					} catch (MalformedURLException e) {
						try (FileWriter fw = new FileWriter(
								directory + File.separator + "url_errors.txt", true);
								BufferedWriter bw = new BufferedWriter(fw);
								PrintWriter pw = new PrintWriter(bw)) {
							pw.println(log + "," + urlString);
						}

						continue;
					}

					byte[] response = new byte[0];
					// get file
					for (int j = 1; j <= MAX_ATTEMPTS; j++) {
						try (InputStream inputStream = new BufferedInputStream(url.openStream());
								ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
							byte[] byteBuffer = new byte[1024];
							int n = 0;
							while (-1 != (n = inputStream.read(byteBuffer))) {
								outputStream.write(byteBuffer, 0, n);
							}
							response = outputStream.toByteArray();

							System.out.println(
									"successfully got " + response.length + "bytes for " + url);

							break;
						} catch (Exception e) {
							if (j == MAX_ATTEMPTS) {
								throw e;
							}

							if (!handleException(e,
									(int) (BACKOFF_TIME_SECONDS * Math.pow(2, j)))) {
								break;
							}
						}
					}

					file.getParentFile().mkdirs();

					// save file
					try (FileOutputStream fos = new FileOutputStream(file)) {
						fos.write(response);
					}

					// add log with format: date,originalFilename,tweetId
					try (FileWriter fw = new FileWriter(
							directory + File.separator + query + "_logs.txt", true);
							BufferedWriter bw = new BufferedWriter(fw);
							PrintWriter pw = new PrintWriter(bw)) {
						pw.println(log);
					}
				}
			}
		}
	}

	/*
	 * private static void importTweets(String filename) { // TODO try {
	 * ObjectInputStream ois = new ObjectInputStream(new
	 * FileInputStream(filename)); List<Tweet> tweets = (List<Tweet>)
	 * ois.readObject();
	 * 
	 * System.out.println("# tweets: " + Integer.toString(tweets.size()));
	 * System.out.println("Last twitterId: " + tweets.get(tweets.size() -
	 * 1).getId()); } catch (IOException e) { e.printStackTrace(); } catch
	 * (ClassNotFoundException e) { e.printStackTrace(); } }
	 */

	public static void main(String[] args) {
		TwitterImageArchiver tia = TwitterImageArchiver.getInstance();

		try {
			tia.sync();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}