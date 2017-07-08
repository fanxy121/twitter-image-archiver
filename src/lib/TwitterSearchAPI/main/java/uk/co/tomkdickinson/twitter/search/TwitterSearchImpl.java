package lib.TwitterSearchAPI.main.java.uk.co.tomkdickinson.twitter.search;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class TwitterSearchImpl extends TwitterSearch {

	private final AtomicInteger counter = new AtomicInteger();

	@Override
	public void saveTweets(List<Tweet> tweets) {
		/*if (tweets != null) {
			for (Tweet tweet : tweets) {
				System.out.println(counter.getAndIncrement() + 1 + "[" + tweet.getCreatedAt()
						+ "] - " + tweet.getText());
			}
		}*/
	}

	/*
	 * public static void main(String[] args) throws InvalidQueryException {
	 * TwitterSearch twitterSearch = new TwitterSearchImpl();
	 * 
	 * List<Tweet> tweets = twitterSearch.search(args[0],
	 * TwitterSearch.MEDIA_TIMELINE, null);
	 * 
	 * try (FileOutputStream out = new
	 * FileOutputStream("tweets_wait_ar_good.ser"); ObjectOutputStream oos = new
	 * ObjectOutputStream(out)) { oos.writeObject(tweets); } catch
	 * (FileNotFoundException e) { // TODO Auto-generated catch block
	 * e.printStackTrace(); } catch (IOException e) { // TODO Auto-generated
	 * catch block e.printStackTrace(); }
	 * 
	 * int i = 1; for (Tweet t : tweets) { List<String> urls = t.getImageURLs();
	 * 
	 * for (String s : urls) { System.out.println(i++ + " - " + s); } }
	 * 
	 * URL url = TwitterSearch.constructURL("from:wait_ar filter:media", null);
	 * 
	 * System.out.println(url); }
	 */
}