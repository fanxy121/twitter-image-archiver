package core;

import java.io.*;
//import java.io.FileInputStream;
//import java.io.ObjectInputStream;
//import java.io.ObjectOutputStream;
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

import com.sun.xml.internal.ws.policy.privateutil.PolicyUtils;
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

	private static final int MAX_RETRIES = 3;

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

	private List<Tweet> getTweets(String query, int queryType, String sinceId) throws InvalidQueryException {
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
		String s = getQueryTypeDirectory(queryType) + File.separator + getQueryTypeString(queryType) + ".txt";
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

	private String getTweetsFilename(String query, int queryType) {
		query = getPathableQuery(query);

		return "Tweets" + File.separator + getQueryTypeDirectory(queryType) + File.separator + query + File.separator + query + ".ser";
	}

	private String getSinceId(String query, int queryType) throws IOException {
		List<Tweet> tweets = importTweets(query, queryType);

		return (tweets.size() > 0) ? tweets.get(0).getId() : "0";
	}

	private String getPathableQuery(String query) {
		return query.replace(":", ".");
	}

	private List<Tweet> importTweets(String query, int queryType) {
		List<Tweet> tweets = new ArrayList<Tweet>();

		try (FileInputStream in = new FileInputStream(getTweetsFilename(query, queryType));
			ObjectInputStream ois = new ObjectInputStream(in)) {
			tweets.addAll((List<Tweet>) ois.readObject());
		} catch (FileNotFoundException e) {
			return tweets;
		} catch (IOException e) {
			e.printStackTrace();
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		}

		return tweets;
	}

	private void exportTweets(String query, int queryType, List<Tweet> tweets) {
		tweets.addAll(importTweets(query, queryType));

		File file = new File(getTweetsFilename(query, queryType));
		file.getParentFile().mkdirs();

		try (FileOutputStream out = new FileOutputStream(file);
			 ObjectOutputStream oos = new ObjectOutputStream(out)) {
			oos.writeObject(tweets);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private List<Tweet> mergeTweets(List<Tweet> tweets1, List<Tweet> tweets2) {
		List<Tweet> ret = new ArrayList<Tweet>();

		int i = 0;
		int j = 0;

		while (i < tweets1.size() || j < tweets2.size()) {
			if (i < tweets1.size() && j < tweets2.size()) {
				Tweet t1 = tweets1.get(i);
				Tweet t2 = tweets2.get(j);

				long t1Id = Long.parseLong(t1.getId());
				long t2Id = Long.parseLong(t2.getId());

				if (t1Id < t2Id) {
					ret.add(t1);
					i++;
				} else if (t1Id == t2Id) {
					ret.add(t1);
					i++;
					j++;
				} else {
					ret.add(t2);
					j++;
				}
			} else if (i < tweets1.size()) {
				while (i < tweets1.size()) {
					ret.add(tweets1.get(i++));
				}
			} else {
				while (j < tweets2.size()) {
					ret.add(tweets2.get(j++));
				}
			}
		}

		return ret;
	}

	// gets and saves images to disk from list of tweets
	private void saveMedia(String query, int queryType, List<Tweet> tweets) throws Exception {
		query = getPathableQuery(query);

		System.out.println("Getting media");

		for (int i = tweets.size() - 1; i >= 0; i--) {
			Tweet tweet = tweets.get(i);

			for (String urlString : tweet.getimageUrls()) {
				System.out.println("Got URL " + urlString);

				// format path
				String directory = getQueryTypeDirectory(queryType) + File.separator + query;
				String filename = urlString.substring(urlString.lastIndexOf('/') + 1);
				File file = new File(directory + File.separator + filename);

				if (!file.isFile()) {
					URL url;
					try {
						url = new URL(urlString + ":orig");
					} catch (MalformedURLException e) {
						try (FileWriter fw = new FileWriter(directory + File.separator + "url_errors.txt", true);
							 BufferedWriter bw = new BufferedWriter(fw); PrintWriter pw = new PrintWriter(bw)) {
							pw.println(filename + "," + urlString);
						}

						continue;
					}

					byte[] response = new byte[0];
					// get file
					for (int j = 0; j <= MAX_RETRIES; j++) {
						try (InputStream inputStream = new BufferedInputStream(url.openStream());
							 ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
							byte[] byteBuffer = new byte[1024];
							int n = 0;
							while (-1 != (n = inputStream.read(byteBuffer))) {
								outputStream.write(byteBuffer, 0, n);
							}
							response = outputStream.toByteArray();

							System.out.println("successfully got " + response.length + "bytes for " + url);

							break;
						} catch (Exception e) {
							if (j == MAX_RETRIES) {
								throw e;
							}

							if (!handleException(e, (int) (BACKOFF_TIME_SECONDS * Math.pow(2, j)))) {
								break;
							}
						}
					}

					file.getParentFile().mkdirs();

					// save image file
					try (FileOutputStream fos = new FileOutputStream(file)) {
						fos.write(response);
					}

					file.setLastModified(tweet.getCreatedAt().getTime());
				}
			}
		}
	}

	private void sync() throws Exception {
		for (int queryType : new int[]{SEARCH, MEDIA_TIMELINE}) {
			System.out.println("Getting queries from file");
			List<String> queries = getQueries(queryType);
			System.out.println("Got " + queries.size() + " queries from file");

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
						tweets = mergeTweets(tweets, getTweets("from:" + query + " filter:media", SEARCH, sinceId));
					}
				} catch (InvalidQueryException e) {
					e.printStackTrace();
				}

				System.out.println("Finished getting tweets for " + query);

				if (tweets.size() > 0) {
					System.out.println("Got " + tweets.size() + " tweets for: " + query);

					exportTweets(query, queryType, tweets);

					System.out.println("Getting media for: " + query);
					saveMedia(query, queryType, tweets);
					System.out.println("Finished getting media for " + query);
				}
			}
		}
	}

	public static void main(String[] args) {
		TwitterImageArchiver tia = TwitterImageArchiver.getInstance();

		try {
			tia.sync();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}