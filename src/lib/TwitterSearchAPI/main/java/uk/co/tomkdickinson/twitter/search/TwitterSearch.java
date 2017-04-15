package lib.TwitterSearchAPI.main.java.uk.co.tomkdickinson.twitter.search;

import com.google.gson.Gson;
import org.apache.http.client.utils.URIBuilder;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public abstract class TwitterSearch {

	public TwitterSearch() {

	}

	public abstract void saveTweets(List<Tweet> tweets);

	public final static long RATE_DELAY = 250;

	public final static int SEARCH = 0;
	public final static int MEDIA_TIMELINE = 1;
	public final static int LIKES = 2;

	public List<Tweet> search(final String query, final int queryType, final String sinceId)
			throws InvalidQueryException {
		TwitterResponse response;
		URL url = constructURL(query, queryType, null);
		boolean continueSearch = true;
		String minTweet = null;

		List<Tweet> allTweets = new ArrayList<Tweet>();

		while ((response = executeSearch(url)) != null && continueSearch
				&& !response.getTweets().isEmpty()) {
			if (minTweet == null) {
				minTweet = response.getTweets().get(0).getId();
			}
			List<Tweet> tweets = response.getTweets();
			saveTweets(tweets);

			// oldest tweet retrieved
			String maxTweet = response.getTweets().get(response.getTweets().size() - 1).getId();

			if (sinceId != null) {
				if (maxTweet.compareTo(sinceId) > 0) {
					allTweets.addAll(tweets);
				} else {
					for (Tweet t : tweets) {
						if (t.getId().compareTo(sinceId) > 0) {
							allTweets.add(t);
						} else {
							continueSearch = false;
							break;
						}
					}
				}
			} else {
				allTweets.addAll(tweets);
			}

			if (continueSearch && !minTweet.equals(maxTweet)) {
				try {
					Thread.sleep(RATE_DELAY);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				String maxPosition = null;
				switch (queryType) {
					case SEARCH:
						maxPosition = "TWEET-" + maxTweet + "-" + minTweet;
						break;
					case MEDIA_TIMELINE:
						maxPosition = maxTweet;
						break;
					default:
				}
				url = constructURL(query, queryType, maxPosition);
			} else {
				continueSearch = false;
			}
		}

		return allTweets;
	}

	public static TwitterResponse executeSearch(final URL url) {
		BufferedReader reader = null;
		try {
			reader = new BufferedReader(
					new InputStreamReader(url.openConnection().getInputStream()));
			Gson gson = new Gson();
			return gson.fromJson(reader, TwitterResponse.class);
		} catch (IOException e) {
			// If we get an IOException, sleep for 5 seconds and retry.
			System.err.println("Could not connect to Twitter. Retrying in 5 seconds.");
			try {
				Thread.sleep(5000);
				return executeSearch(url);
			} catch (InterruptedException e2) {
				e.printStackTrace();
			}
		} finally {
			try {
				if (reader != null) {
					reader.close();
				}
			} catch (NullPointerException | IOException e) {
				e.printStackTrace();
			}
		}
		return null;
	}

	public final static String TYPE_PARAM = "f";
	public final static String QUERY_PARAM = "q";
	public final static String SCROLL_CURSOR_PARAM = "max_position";
	public final static String TWITTER_SEARCH_URL = "https://twitter.com/i/search/timeline";

	public final static String AVAILABLE_FEATURES_PARAM = "include_available_features";
	public final static String ENTITIES_PARAM = "include_entities";
	public final static String TWITTER_PROFILE_URL = "https://twitter.com/i/profiles/show/";
	public final static String MEDIA_TIMELINE_PATH = "/media_timeline";
	public final static String RESET_ERROR_PARAM = "reset_error_state";

	public static URL constructURL(final String arg, final int queryType, final String maxPosition)
			throws InvalidQueryException {
		switch (queryType) {
			case SEARCH:
				return constructSearchURL(arg, maxPosition);
			case MEDIA_TIMELINE:
				return constructMediaTimelineURL(arg, maxPosition);
			default:
				throw new InvalidQueryException(Integer.toString(queryType));
		}
	}

	public static URL constructSearchURL(final String query, final String maxPosition)
			throws InvalidQueryException {
		if (query == null || query.isEmpty()) {
			throw new InvalidQueryException(query);
		}
		try {
			URIBuilder uriBuilder;
			uriBuilder = new URIBuilder(TWITTER_SEARCH_URL);
			uriBuilder.addParameter(QUERY_PARAM, query);
			uriBuilder.addParameter(TYPE_PARAM, "tweets");
			if (maxPosition != null) {
				uriBuilder.addParameter(SCROLL_CURSOR_PARAM, maxPosition);
			}
			return uriBuilder.build().toURL();
		} catch (MalformedURLException | URISyntaxException e) {
			e.printStackTrace();
			throw new InvalidQueryException(query);
		}
	}

	public static URL constructMediaTimelineURL(final String username, final String maxPosition)
			throws InvalidQueryException {
		if (username == null || username.isEmpty()) {
			throw new InvalidQueryException(username);
		}
		try {
			URIBuilder uriBuilder;
			uriBuilder = new URIBuilder(TWITTER_PROFILE_URL + username + MEDIA_TIMELINE_PATH);
			uriBuilder.addParameter(AVAILABLE_FEATURES_PARAM, "1");
			uriBuilder.addParameter(ENTITIES_PARAM, "1");
			uriBuilder.addParameter(RESET_ERROR_PARAM, "false");
			if (maxPosition != null) {
				uriBuilder.addParameter(SCROLL_CURSOR_PARAM, maxPosition);
			}
			return uriBuilder.build().toURL();
		} catch (MalformedURLException | URISyntaxException e) {
			e.printStackTrace();
			throw new InvalidQueryException(username);
		}
	}
}