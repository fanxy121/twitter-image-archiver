package core;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
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
import java.util.concurrent.TimeUnit;

import twitter4j.ExtendedMediaEntity;
import twitter4j.ExtendedMediaEntity.Variant;
import twitter4j.PagableResponseList;
import twitter4j.Paging;
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
				TimeUnit.SECONDS.sleep(secondsUntilReset + 60);
			} else if (te.isCausedByNetworkIssue()) {
				TimeUnit.SECONDS.sleep(NETWORK_WAIT_SECONDS);
			} else {
				throw te;
			}
		} catch (InterruptedException e) {
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
		for (int query : new int[] {USER_TIMELINE, FAVORITES}) {
			List<String> usernames = getUsernames(query);

			for (String username : usernames) {
				long sinceId = getSinceId(username, query);

				List<Status> statuses = getStatuses(username, sinceId, query);

				if (statuses.size() > 0) {
					// TODO include date, time, etc.
					// https://www.tutorialspoint.com/java/io/objectoutputstream_writeobject.htm
					// https://www.tutorialspoint.com/java/io/objectinputstream_readobject.htm
					try (FileOutputStream out = new FileOutputStream("test.txt");
							ObjectOutputStream oos = new ObjectOutputStream(out)) {
						oos.writeObject(statuses);
					}

					syncMedia(statuses);

					setSinceId(username, statuses.get(0).getId(), query);
				}
			}
		}
	}

	private void recover() {
		// TODO
	}

	private void syncMedia(List<Status> statuses) throws IOException {
		for (Status status : statuses) {
			for (ExtendedMediaEntity media : status.getExtendedMediaEntities()) {
				// System.out.println(media.getMediaURL());

				String urlString = media.getMediaURL();

				if (media.getType().equals("video") || media.getType().equals("animated_gif")) {
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
				// TODO check this is correct username
				String username = status.getUser().getName();
				String directory = username + File.separator;
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

	public static void main(String[] args) {
		TwitterImageSync tis = TwitterImageSync.getInstance();

		try {
			tis.verifyCredentials();
		} catch (TwitterException te) {
			te.printStackTrace();
			System.out.println(te.getMessage());
		}

		try {
			tis.sync();
		} catch (TwitterException e) {
			e.printStackTrace();
		} catch (MalformedURLException e) {
		} catch (IOException e) {
		}

		/*
		 * for (Status status : statuses) { System.out.println("@" +
		 * status.getUser().getScreenName() + " - " + status.getText()); }
		 */
	}
}