package core;

import java.io.BufferedInputStream;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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

	// TODO userid vs. username + folder names
	// TODO singleton + other design patterns
	// TODO http://twitter4j.org/javadoc/twitter4j/RateLimitStatus.html

	Twitter twitter;
	User user;

	long friendsCursor = -1;

	int apiTryCount = 0;
	static final int MAX_API_TRIES = 3;
	TwitterException twitterException;

	static final String SYNC_FILE = "sync.txt";

	static final int USER_TIMELINE = 0;
	static final int FAVORITES = 1;

	public TwitterImageSync() {
		// verify this doesn't throw exceptions
		twitter = new TwitterFactory().getInstance();
	}

	private void verifyCredentials() throws TwitterException {
		// default credentials
		user = twitter.verifyCredentials();
	}

	/*
	 * two cases where we throw higher due to inability to handle: 1. api failed
	 * too many times 2. unknown api error
	 */
	private void handleTwitterException(TwitterException te) throws TwitterException {
		if (twitterException == null) {
			twitterException = te;
		}

		if (!twitterException.equals(te)) {
			apiTryCount = 0;
			twitterException = te;
		}

		if (++apiTryCount >= MAX_API_TRIES) {
			throw te;
		}

		if (twitterException.exceededRateLimitation()) {
			int secondsUntilReset = te.getRateLimitStatus().getSecondsUntilReset();
			// wait secondsUntilReset;
		} else if (te.isCausedByNetworkIssue()) {
			// TODO pause until network is restored
		} else {
			throw te;
		}
	}

	private List<User> getFriends(String username) throws TwitterException {
		List<User> friends = new ArrayList<User>();

		long cursor = -1;
		PagableResponseList<User> users;

		while (cursor != 0) {
			// TODO lambda
			while (true) {
				try {
					users = twitter.getFriendsList(username, cursor, 200);
					break;
				} catch (TwitterException te) {
					handleTwitterException(te);
				}
			}

			friends.addAll(users);

			cursor = users.getNextCursor();
		}

		return friends;
	}

	private List<Status> getStatuses(String username, long sinceId, int query) throws TwitterException {
		List<Status> statuses = new ArrayList<Status>();

		Paging paging = new Paging(1, 200);

		if (sinceId >= 0) {
			paging.setSinceId(sinceId);
		}

		while (true) {
			List<Status> statusPage = new ArrayList<Status>();

			while (true) {
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

	// TODO modularize twitterexception handling inside method
	private boolean sync() throws TwitterException {
        // user timelines
        List<User> users = getUsers();
       
        for (User user: users) {
            String username = user.getName();
 
            long sinceIdUser = getSinceId(username);
 
            List<Status> userStatuses = getStatuses(username, sinceIdUser, USER_TIMELINE);
 
            if (userStatuses.size() > 0) {
                // TODO while loop
                try {
                    syncMedia(userStatuses);
 
                    setSinceId(username, userStatuses.get(0).getId());
                }
            }
        }
 
        // favorites
        long sinceIdFavorites = getSinceIdFavorites();
 
        List<Status> favorites = getStatuses(username, sinceIdFavorites, FAVORITES);
 
        if (favorites.size() > 0) {
            // TODO while loop
            try {
                syncMedia(userStatuses);
 
                setSinceId(username, userStatuses.get(0).getId());
            }
        }
    }

	private void syncMedia(List<Status> statuses) {
		Set<String> usernames = new HashSet<String>();

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
				// TODO if (retweet) { create subdirectory for user }
				String username = status.getUser().getName(); // double check
																// this is
																// actually
																// username
				usernames.add(username);

				DateFormat df = new SimpleDateFormat("yyMMdd-HHmmss");
				Date d = status.getCreatedAt();
				String date = df.format(d);

				String filename = date + "_" + urlString.substring(urlString.lastIndexOf('/') + 1);

				File file = new File(username + File.separator + filename);

				if (!file.exists()) {
					try {
						URL url = new URL(urlString);
						
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

						// write log
						// http://stackoverflow.com/questions/1625234/how-to-append-text-to-an-existing-file-in-java
						try (FileWriter fw = new FileWriter(username + File.separator + username + ".txt", true);
								BufferedWriter bw = new BufferedWriter(fw);
								PrintWriter pw = new PrintWriter(bw)) {
							pw.println(filename + '_' + Long.toString(status.getId()));
						}
					} catch (MalformedURLException e1) {
						// TODO Auto-generated catch block
						e1.printStackTrace();
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					} 
				}
			}
		}
	}

	// TODO retweet dupe

	public static void main(String[] args) {
		TwitterImageSync tis = new TwitterImageSync();

		try {
			tis.verifyCredentials();
		} catch (TwitterException te) {
			te.printStackTrace();
			System.out.println(te.getMessage());
		}

		try {
			tis.sync();
		} catch (TwitterException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		/*
		 * for (Status status : statuses) { System.out.println("@" +
		 * status.getUser().getScreenName() + " - " + status.getText()); }
		 */
	}
}