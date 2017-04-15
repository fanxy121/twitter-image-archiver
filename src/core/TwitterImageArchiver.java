package core;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
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

	private TwitterImageArchiver() {
		twitterSearch = new TwitterSearchImpl();
	}

	public static TwitterImageArchiver getInstance() {
		if (instance == null) {
			instance = new TwitterImageArchiver();
		}
		return instance;
	}

	private List<Tweet> getTweets(String query, int queryType, String sinceid)
			throws InvalidQueryException {
		return twitterSearch.search(query, queryType, sinceid);
	}

	private String getUsernamesFilename(int query) {
		String dir = null;

		switch (query) {
			case MEDIA_TIMELINE:
				dir = "Timelines";
				break;
			case LIKES:
				dir = "Likes";
				break;
			default:
		}

		return dir + File.separator + "usernames.txt";
	}

	private List<String> getUsernames(int query) throws IOException {
		List<String> usernames = new ArrayList<String>();

		File file = new File(getUsernamesFilename(query));

		if (file.isFile()) {
			try (BufferedReader br = new BufferedReader(new FileReader(file))) {
				String line;
				while ((line = br.readLine()) != null) {
					String s = line.trim();

					if (s.length() > 0) {
						usernames.add(s);
					}
				}
			}
		}

		return usernames;
	}

	private String getSinceIdFilename(String username, int query) {
		String dir = null;

		switch (query) {
			case MEDIA_TIMELINE:
				dir = "Timelines";
				break;
			case LIKES:
				dir = "Likes";
				break;
			default:
		}

		return dir + File.separator + username + ".txt";
	}

	private String getSinceId(String username, int query) throws IOException {
		String sinceId = null;

		File file = new File(getSinceIdFilename(username, query));

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

	private void setSinceId(String username, String sinceId, int query) throws IOException {
		String pathString = getSinceIdFilename(username, query);

		File file = new File(pathString);

		if (!file.isFile()) {
			file.getParentFile().mkdirs();
		}

		Files.write(Paths.get(pathString), sinceId.getBytes());
	}

	private void sync() throws IOException {
		int query = MEDIA_TIMELINE;
		System.out.println("Getting usernames from file");
		List<String> usernames = getUsernames(query);
		System.out.println("Got " + usernames.size() + "usernames from file");

		for (String username : usernames) {
			System.out.println("Working with username: " + username);
			String sinceId = getSinceId(username, query);
			System.out.println("Got sinceId: " + sinceId);

			System.out.println("Getting statuses for: " + username);
			List<Tweet> tweets = new ArrayList<Tweet>();

			try {
				tweets = getTweets(username, query, sinceId);
			} catch (InvalidQueryException e) {
				e.printStackTrace();
			}

			System.out.println("Finished getting statuses for " + username);

			if (tweets.size() > 0) {
				System.out.println("Got " + tweets.size() + " statuses for: " + username);
				try (FileOutputStream out = new FileOutputStream(username + ".ser");
						ObjectOutputStream oos = new ObjectOutputStream(out)) {
					oos.writeObject(tweets);
				}

				System.out.println("Getting media for: " + username);
				saveMediaFromTweets(tweets, query);
				System.out.println("Finished getting media for " + username);

				System.out.println(
						"Setting sinceId of " + tweets.get(0).getId() + "for: " + username);
				setSinceId(username, tweets.get(0).getId(), query);
			}
		}
	}

	private void recover() {
		// TODO
	}

	private void saveMediaFromTweets(List<Tweet> tweets, int query) throws IOException {
		System.out.println("Getting media");

		for (int i = tweets.size() - 1; i >= 0; i--) {
			Tweet tweet = tweets.get(i);

			for (String urlString : tweet.getimageUrls()) {
				System.out.println("Got URL " + urlString);

				// format path
				String username = tweet.getUserScreenName();

				String directory = null;
				switch (query) {
					case MEDIA_TIMELINE:
						directory = "Timelines" + File.separator + username + File.separator;
						break;
					case LIKES:
						directory = "Likes" + File.separator + username + File.separator;
						break;
					default:
				}

				DateFormat df = new SimpleDateFormat("yyMMdd-HHmmss");
				Date d = tweet.getCreatedAt();
				String date = df.format(d);

				String originalFilename = urlString.substring(urlString.lastIndexOf('/') + 1);

				File file = new File(directory + date + "_" + originalFilename);

				String log = date + "," + originalFilename + "," + tweet.getId() + ",\""
						+ tweet.getText() + "\"";

				if (!file.isFile()) {
					URL url;
					try {
						url = new URL(urlString + ":orig");
					} catch (MalformedURLException e) {
						try (FileWriter fw = new FileWriter(directory + "url_errors.txt", true);
								BufferedWriter bw = new BufferedWriter(fw);
								PrintWriter pw = new PrintWriter(bw)) {
							pw.println(log + "," + urlString);
						}

						continue;
					}

					byte[] response = new byte[0];
					// get file
					try (InputStream inputStream = new BufferedInputStream(url.openStream());
							ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
						byte[] byteBuffer = new byte[1024];
						int n = 0;
						while (-1 != (n = inputStream.read(byteBuffer))) {
							outputStream.write(byteBuffer, 0, n);
						}
						response = outputStream.toByteArray();
					}

					file.getParentFile().mkdirs();

					// save file
					try (FileOutputStream fos = new FileOutputStream(file)) {
						fos.write(response);
					}

					// add log with format: date,originalFilename,tweetId
					try (FileWriter fw = new FileWriter(directory + username + ".txt", true);
							BufferedWriter bw = new BufferedWriter(fw);
							PrintWriter pw = new PrintWriter(bw)) {
						pw.println(log);
					}
				}
			}
		}
	}

	private static void importTweets(String filename) {
		// TODO
		try {
			ObjectInputStream ois = new ObjectInputStream(new FileInputStream(filename));
			List<Tweet> tweets = (List<Tweet>) ois.readObject();

			System.out.println("# tweets: " + Integer.toString(tweets.size()));
			System.out.println("Last twitterId: " + tweets.get(tweets.size() - 1).getId());
		} catch (IOException e) {
			e.printStackTrace();
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		}
	}

	public static void main(String[] args) {
		TwitterImageArchiver tia = TwitterImageArchiver.getInstance();

		try {
			tia.sync();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}