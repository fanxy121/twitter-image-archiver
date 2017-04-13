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
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;

import twitter4j.ExtendedMediaEntity;
import twitter4j.ExtendedMediaEntity.Variant;
import twitter4j.PagableResponseList;
import twitter4j.Paging;
import twitter4j.Query;
import twitter4j.QueryResult;
import twitter4j.Status;
import twitter4j.Twitter;
import twitter4j.TwitterException;
import twitter4j.TwitterFactory;
import twitter4j.User;

public class TwitterImageSync {
	private static TwitterImageSync instance = null;

	private Twitter twitter;
	private User user;

	private static final int MAX_API_ATTEMPTS = 3;
	private static final int NETWORK_WAIT_SECONDS = 60;

	private static final int USER_TIMELINE = 0;
	private static final int FAVORITES = 1;

	// TODO JUnit

	private TwitterImageSync() {
		twitter = new TwitterFactory().getInstance();
	}

	public static TwitterImageSync getInstance() {
		if (instance == null) {
			instance = new TwitterImageSync();
		}
		return instance;
	}

	// TODO check if this is necessary
	private void verifyCredentials() throws TwitterException {
		// default credentials
		user = twitter.verifyCredentials();
	}

	private void handleTwitterException(TwitterException te) throws TwitterException {
		try {
			if (te.exceededRateLimitation()) {
				int secondsUntilReset = te.getRetryAfter();
				System.out.println(
						"Handling exceeded rate, sleeping for " + Integer.toString(secondsUntilReset + 60) + "seconds");
				TimeUnit.SECONDS.sleep(secondsUntilReset + 60);
			} else if (te.isCausedByNetworkIssue()) {
				System.out.println("Network issue, sleeping for " + Integer.toString(NETWORK_WAIT_SECONDS));
				TimeUnit.SECONDS.sleep(NETWORK_WAIT_SECONDS);
			} else {
				throw te;
			}
		} catch (InterruptedException e) {
			System.out.println("Interrupted during sleep");
			throw te;
		}
	}

	private List<User> getFriends(String username) throws TwitterException {
		List<User> friends = new ArrayList<User>();
		PagableResponseList<User> friendsPage = null;

		long cursor = -1;

		while (cursor != 0) {
			for (int i = 1; i <= MAX_API_ATTEMPTS; i++) {
				try {
					friendsPage = twitter.getFriendsList(username, cursor, 200);
					break;
				} catch (TwitterException te) {
					if (i == MAX_API_ATTEMPTS) {
						throw te;
					}
					handleTwitterException(te);
				}
			}

			friends.addAll(friendsPage);

			cursor = friendsPage.getNextCursor();
		}

		return friends;
	}

	private List<Status> getStatuses(String username, long sinceId, int query) throws TwitterException {
		List<Status> statuses = new ArrayList<Status>();
		List<Status> statusPage = new ArrayList<Status>();

		Paging paging = new Paging(1, 200);

		if (sinceId >= 0) {
			paging.setSinceId(sinceId);
		}

		while (true) {
			for (int i = 1; i <= MAX_API_ATTEMPTS; i++) {
				try {
					switch (query) {
						case USER_TIMELINE:
							statusPage = twitter.getUserTimeline(username, paging);
							break;
						case FAVORITES:
							statusPage = twitter.getFavorites(username, paging);
							break;
						default:
					}
					break;
				} catch (TwitterException te) {
					if (i == MAX_API_ATTEMPTS) {
						throw te;
					}
					handleTwitterException(te);
				}
			}

			if (statusPage.size() == 0) {
				break;
			}

			statuses.addAll(statusPage);

			paging.setMaxId(statuses.get(statuses.size() - 1).getId() - 1);

			// System.out.println("Last tweet ID: " +
			// Long.toString(paging.getMaxId()));
		}

		return statuses;
	}

	private String getUsernamesFilename(int query) {
		String dir = null;

		switch (query) {
			case USER_TIMELINE:
				dir = "Timelines";
				break;
			case FAVORITES:
				dir = "Favorites";
				break;
			default:
		}

		return dir + File.separator + "usernames.txt";
	}

	private List<String> getUsernames(int query) throws IOException {
		List<String> usernames = new ArrayList<String>();

		File file = new File(getUsernamesFilename(query));

		if (file.isFile()) {
			// http://stackoverflow.com/questions/5868369/how-to-read-a-large-text-file-line-by-line-using-java
			try (BufferedReader br = new BufferedReader(new FileReader(file))) {
				String line;
				while ((line = br.readLine()) != null) {
					String s = line.trim();

					if (s.length() > 0) {
						System.out.println("Got username: " + s);
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
			case USER_TIMELINE:
				dir = "Timelines";
				break;
			case FAVORITES:
				dir = "Favorites";
				break;
			default:
		}

		return dir + File.separator + username + ".txt";
	}

	private long getSinceId(String username, int query) throws IOException {
		long sinceId = -1;

		File file = new File(getSinceIdFilename(username, query));

		if (file.isFile()) {
			try (BufferedReader br = new BufferedReader(new FileReader(file))) {
				String line;
				while ((line = br.readLine()) != null) {
					String s = line.trim();

					if (s.length() > 0) {
						sinceId = Long.parseLong(s);
						break;
					}
				}
			}
		}

		return sinceId;
	}

	private void setSinceId(String username, long sinceId, int query) throws IOException {
		String pathString = getSinceIdFilename(username, query);

		File file = new File(pathString);

		if (!file.isFile()) {
			file.getParentFile().mkdirs();
		}

		Files.write(Paths.get(pathString), Long.toString(sinceId).getBytes());
	}

	private void sync() throws TwitterException, IOException {
		// for (int query : new int[] {USER_TIMELINE, FAVORITES}) {
		int query = USER_TIMELINE;
		System.out.println("Getting usernames from file");
		List<String> usernames = getUsernames(query);
		System.out.println("Successfully got usernames from file");

		for (String username : usernames) {
			System.out.println("Working with username: " + username);
			long sinceId = getSinceId(username, query);
			System.out.println("Got sinceId: " + Long.toString(sinceId));

			System.out.println("Getting statuses for: " + username);
			List<Status> statuses = getStatuses(username, sinceId, query);
			System.out.println("Finished getting statuses for " + username);

			// TODO statuses.size(), Got URL -> got status #, twitterId, etc.
			// TODO spaces in filename: Timelines\‚í‚½‚¨\‚ê‚¨‚¦‚ñ \
			// TIA_‚½73a\170402-053231_C8YwhvGUQAAS7Ue.jpg (The system cannot
			// find the path specified)

			if (statuses.size() > 0) {
				System.out.println("Got >0 statuses for: " + username);
				// TODO include date, time, etc.
				// https://www.tutorialspoint.com/java/io/objectoutputstream_writeobject.htm
				// https://www.tutorialspoint.com/java/io/objectinputstream_readobject.htm
				try (FileOutputStream out = new FileOutputStream("test.txt");
						ObjectOutputStream oos = new ObjectOutputStream(out)) {
					oos.writeObject(statuses);
				}

				System.out.println("Getting media for: " + username);
				syncMedia(statuses, query);
				System.out.println("Finished getting media for " + username);

				System.out.println("Setting sinceId of " + Long.toString(statuses.get(0).getId()) + "for: " + username);
				setSinceId(username, statuses.get(0).getId(), query);
			}
		}
		// }
	}

	private void recover() {
		// TODO
	}

	private void syncMedia(List<Status> statuses, int query) throws IOException {
		System.out.println("Getting media");

		for (Status status : statuses) {
			for (ExtendedMediaEntity media : status.getExtendedMediaEntities()) {
				String urlString = media.getMediaURL();
				System.out.println("Got URL " + urlString);

				if (media.getType().equals("video") || media.getType().equals("animated_gif")) {
					System.out.println("URL is video or gif");
					Variant[] variants = media.getVideoVariants();

					int bitrate = -1;
					Variant bestVariant = null;

					for (Variant variant : variants) {
						if (variant.getContentType().equals("video/mp4") && variant.getBitrate() > bitrate) {
							bitrate = variant.getBitrate();
							bestVariant = variant;
						}
					}

					if (bestVariant != null) {
						urlString = bestVariant.getUrl();
					}
				}

				// format path
				// TODO check this is correct username // it's not, it's screen
				// name
				String username = status.getUser().getName();

				// TODO array
				String directory = "";
				if (query == USER_TIMELINE) {
					directory += "Timelines" + File.separator + username + File.separator;
				} else if (query == FAVORITES) {
					directory += "Favorites" + File.separator + username + File.separator;
				}

				if (status.isRetweet()) {
					String retweetUsername = status.getRetweetedStatus().getUser().getName();

					if (!retweetUsername.equals(username)) {
						directory += retweetUsername + File.separator;
					}
				}

				DateFormat df = new SimpleDateFormat("yyMMdd-HHmmss");
				Date d = status.getCreatedAt();
				String date = df.format(d);

				String originalFilename = urlString.substring(urlString.lastIndexOf('/') + 1);

				File file = new File(directory + date + "_" + originalFilename);

				String log = date + "," + originalFilename + "," + Long.toString(status.getId());

				if (!file.isFile()) {
					URL url;
					try {
						url = new URL(urlString);
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
					// http://stackoverflow.com/questions/1625234/how-to-append-text-to-an-existing-file-in-java
					try (FileWriter fw = new FileWriter(directory + username + ".txt", true);
							BufferedWriter bw = new BufferedWriter(fw);
							PrintWriter pw = new PrintWriter(bw)) {
						pw.println(log);
					}
				}
			}
		}
	}
	// TODO orig

	private static void importStatuses() {
		try {
			// create an ObjectInputStream for the file we created before
			ObjectInputStream ois = new ObjectInputStream(new FileInputStream("test.txt"));

			// read and print an object and cast it as string
			// byte[] read = (byte[]) ois.readObject();
			List<Status> statuses = (List<Status>) ois.readObject();
			System.out.println("# statuses: " + Integer.toString(statuses.size()));
			System.out.println("Last twitterId: " + Long.toString(statuses.get(statuses.size() - 1).getId()));
		} catch (IOException e) {
			e.printStackTrace();
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		}
	}

	private List<Status> testSearch(String queryText) throws TwitterException {
		Query query = new Query(queryText);
		ArrayList<Status> tweets = new ArrayList<Status>();
		while (query != null) {
			try {
				QueryResult result = twitter.search(query);
				tweets.addAll(result.getTweets());
				System.out.println("Gathered " + tweets.size() + " tweets");
				

				query = result.nextQuery();

			}

			catch (TwitterException te) {
				System.out.println("Couldn't connect: " + te);
			}
			

		}

		return tweets;

		/*
		 * Query query = new Query(queryText); //QueryResult result;
		 * 
		 * int numberOfTweets = 512; long lastID = Long.MAX_VALUE;
		 * ArrayList<Status> tweets = new ArrayList<Status>(); while
		 * (tweets.size () < numberOfTweets) { if (numberOfTweets -
		 * tweets.size() > 100) query.setCount(100); else
		 * query.setCount(numberOfTweets - tweets.size()); try { QueryResult
		 * result = twitter.search(query); tweets.addAll(result.getTweets());
		 * System.out.println("Gathered " + tweets.size() + " tweets"); for
		 * (Status t: tweets) if(t.getId() < lastID) lastID = t.getId(); }
		 * 
		 * catch (TwitterException te) { handleTwitterException(te); };
		 * query.setMaxId(lastID-1); }
		 */

		// result = twitter.search(query);
		// List<Status> statuses = result.getTweets();

	}
	
	private List<String> searchStatuses(String username) throws IOException {
		List<String> statuses = new ArrayList<String>();
		
		//String filename = "Statuses" + File.separator + username + ".txt";
		String filename = "foo.txt";
		
		File file = new File(filename);

		if (file.isFile()) {
			// http://stackoverflow.com/questions/5868369/how-to-read-a-large-text-file-line-by-line-using-java
			try (BufferedReader br = new BufferedReader(new FileReader(file))) {
				String line;
				while ((line = br.readLine()) != null) {
					String s = line.trim();

					if (s.length() > 0) {
						System.out.println("Got tweet ID from file: " + s);
						statuses.add(s);
					}
				}
			}
		}
		
		return statuses;
	}
	
	private List<Status> idsToStatuses(List<String> ids) throws TwitterException {
		List<Status> statuses = new ArrayList<Status>();
		
		Iterator<String> itr = ids.iterator();
		
		int count = 0;
		while (itr.hasNext()) {
			count++;
			String id = itr.next();
			
			Status status = null;
			
			for (int i = 1; i <= MAX_API_ATTEMPTS; i++) {
				try {
					status = twitter.showStatus(Long.parseLong(id));
					
					System.out.println("Got showStatus from id " + Long.parseLong(id) + " - #" + Integer.toString(count) + " / " + ids.size());
					
					break;
				} catch (TwitterException te) {
					if (i == MAX_API_ATTEMPTS) {
						throw te;
					}
					handleTwitterException(te);
				}
			}
			
			if (status != null) {
				statuses.add(status);
			}
		}
		
		return statuses;
	}

	public static void main(String[] args) {

		TwitterImageSync tis = TwitterImageSync.getInstance();

		try {
			tis.verifyCredentials();
		} catch (TwitterException te) {
			te.printStackTrace();
			System.out.println(te.getMessage());
		}

		try {
			// tis.sync();
			
			List<String> ids = tis.searchStatuses("");
			
			
			List<Status> statuses = tis.idsToStatuses(ids);
			
			
			
			try (FileOutputStream out = new FileOutputStream("statuses_from_ids.txt");
					ObjectOutputStream oos = new ObjectOutputStream(out)) {
				oos.writeObject(statuses);
			}
			
			for (Status status : statuses) {
				System.out.println("finished, id: " + Long.toString(status.getId()));
			}

			/*List<Status> searchStatuses = tis.testSearch("from:wait_ar filter:media");

			try (FileOutputStream out = new FileOutputStream("testSearch.txt");
					ObjectOutputStream oos = new ObjectOutputStream(out)) {
				oos.writeObject(searchStatuses);
			}

			System.out.println("# statuses: " + searchStatuses.size());

			System.out
					.println("last status id: " + Long.toString(searchStatuses.get(searchStatuses.size() - 1).getId()));*/
		} catch (TwitterException e) {
			e.printStackTrace();
			/*
			 * } catch (MalformedURLException e) { e.printStackTrace();
			 */
		} catch (IOException e) {
			e.printStackTrace();
		}

		/*
		 * for (Status status : statuses) { System.out.println("@" +
		 * status.getUser().getScreenName() + " - " + status.getText()); }
		 */
	}
}