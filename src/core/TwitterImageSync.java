package core;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import twitter4j.ExtendedMediaEntity;
import twitter4j.ExtendedMediaEntity.Variant;
import twitter4j.IDs;
import twitter4j.MediaEntity;
import twitter4j.Paging;
import twitter4j.Status;
import twitter4j.Twitter;
import twitter4j.TwitterException;
import twitter4j.TwitterFactory;
import twitter4j.User;

public class TwitterImageSync {

	// todo: userid vs. username + folder names
	// todo http://twitter4j.org/javadoc/twitter4j/RateLimitStatus.html

	Twitter twitter;
	User user;

	public TwitterImageSync() {
		try {
			// gets Twitter instance with default credentials
			twitter = new TwitterFactory().getInstance();
			System.out.println();
			user = twitter.verifyCredentials();
		} catch (TwitterException te) {
			te.printStackTrace();
			System.out.println(te.getMessage());
			System.exit(-1);
		}
	}

	private List<Long> getFriends() {
		List<Long> friends = new ArrayList<Long>();

		long cursor = -1;

		try {
			while (cursor != 0) {
				IDs ids = twitter.getFriendsIDs(cursor);

				for (long l : ids.getIDs()) {
					friends.add(l);
				}

				cursor = ids.getNextCursor();
			}
		} catch (TwitterException te) {
		}

		return friends;
	}

	private List<Status> getStatuses(String username, long since_id, long max_id) {
		List<Status> statuses = new ArrayList<Status>();

		Paging paging = new Paging(1, 200);

		if (since_id >= 0) {
			paging.setSinceId(since_id);
		}

		paging.setMaxId((max_id >= 0) ? max_id : Long.MAX_VALUE);

		while (true) {
			try {
				List<Status> statusPage = (!username.equals("")) ? twitter.getUserTimeline(username, paging)
						: twitter.getHomeTimeline(paging);

				if (statusPage.size() == 0) {
					break;
				}

				statuses.addAll(statusPage);

				paging.setMaxId(statuses.get(statuses.size() - 1).getId() - 1);

				System.out.println("Last tweet ID: " + Long.toString(paging.getMaxId()));
			} catch (TwitterException e) {
				e.printStackTrace();
				break;
			}
		}

		return statuses;
	}

	/*
	 * private long initFollowing() { long since_id = Long.MAX_VALUE;
	 * 
	 * List<List<Status>> statuses = new ArrayList<List<Status>>();
	 * 
	 * // todo: check user on filter, user already done, etc. for (following
	 * users) { List<Status> statuses = getStatuses(username, -1, -1);
	 * 
	 * // mediaDownload(statuses);
	 * 
	 * // problem: last tweet by following is 2013, 2017 -> hometimeline checks
	 * back to 2013 // solution: since_id should be based on time(?) init on
	 * user began, not "most recent tweet by user" }
	 * 
	 * for (List<Status> list : statuses) { if (list.size() > 0) { since_id =
	 * Math.min(since_id, list.get(0).getId()); } }
	 * 
	 * if (since_id == Long.MAX_VALUE) { since_id = 0; }
	 * 
	 * return since_id; }
	 */

	private void getMedia(List<Status> statuses) {
		for (Status status : statuses) {
			for (ExtendedMediaEntity media : status.getExtendedMediaEntities()) {
				System.out.println(media.getMediaURL());

				try {
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
					// todo: if (retweet) { create subdirectory for user }
					String username = status.getUser().getScreenName();

					DateFormat df = new SimpleDateFormat("yyMMdd-HHmmss");
					Date d = status.getCreatedAt();
					String date = df.format(d);

					String filename = date + "_" + urlString.substring(urlString.lastIndexOf('/') + 1);

					File file = new File(username + File.separator + filename);

					if (!file.exists()) {
						// get file
						URL url = new URL(urlString);
						InputStream in = new BufferedInputStream(url.openStream());
						ByteArrayOutputStream out = new ByteArrayOutputStream();
						byte[] buf = new byte[1024];
						int n = 0;
						while (-1 != (n = in.read(buf))) {
							out.write(buf, 0, n);
						}
						out.close();
						in.close();
						byte[] response = out.toByteArray();

						// save file
						file.getParentFile().mkdirs();

						try (FileOutputStream fos = new FileOutputStream(file)) {
							fos.write(response);
							fos.close();

						} catch (IOException e) {
							e.printStackTrace();
						}
					}
				} catch (Exception e) {
					e.printStackTrace();
					return;
				}
			}
		}
	}

	/*private boolean log() {
		FileOutputStream out = null;

		try {
			out = new FileOutputStream("output.txt");

			out.write(scanTime);
			out.write(newestImageTime);
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			if (out != null) {
				out.close();
			}
		}
	}*/

	// long since_id = initFollowing();

	// List<Status> newStatuses = getStatuses("", since_id, -1);

	// todo retweet dupe

	public static void main(String[] args) {
		TwitterImageSync tis = new TwitterImageSync();

		/*
		 * boolean init = false;
		 * 
		 * if (!init) { tis.initFollowing(); }
		 */

		// startup -> getFriends() compare to txt filenames
		// if (!file.exists()) { getStatuses(); }
		// else { getStatuses(); since_id = Math.max(scanTime - 1 day,
		// newestImageTime); }

		List<Status> statuses = tis.getStatuses(args[0], -1, -1);

		tis.getMedia(statuses);

		/*
		 * for (Status status : statuses) { System.out.println("@" +
		 * status.getUser().getScreenName() + " - " + status.getText()); }
		 */

	}

}
