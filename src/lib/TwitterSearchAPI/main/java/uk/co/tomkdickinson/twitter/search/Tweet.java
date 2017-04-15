package lib.TwitterSearchAPI.main.java.uk.co.tomkdickinson.twitter.search;

import java.io.Serializable;
import java.util.Date;
import java.util.List;

public class Tweet implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = -6421334289082766487L;
	private String id;
	private String text;
	private String userId;
	private String userName;
	private String userScreenName;
	private Date createdAt;
	private int retweets;
	private int favourites;
	private List<String> imageUrls;

	public Tweet() {
	}

	public Tweet(String id, String text, String userId, String userName, String userScreenName,
			Date createdAt, int retweets, int favourites, List<String> imageUrls) {
		this.id = id;
		this.text = text;
		this.userId = userId;
		this.userName = userName;
		this.userScreenName = userScreenName;
		this.createdAt = createdAt;
		this.retweets = retweets;
		this.favourites = favourites;
		this.imageUrls = imageUrls;
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getText() {
		return text;
	}

	public void setText(String text) {
		this.text = text;
	}

	public String getUserId() {
		return userId;
	}

	public void setUserId(String userId) {
		this.userId = userId;
	}

	public String getUserName() {
		return userName;
	}

	public void setUserName(String userName) {
		this.userName = userName;
	}

	public String getUserScreenName() {
		return userScreenName;
	}

	public void setUserScreenName(String userScreenName) {
		this.userScreenName = userScreenName;
	}

	public Date getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(Date createdAt) {
		this.createdAt = createdAt;
	}

	public int getRetweets() {
		return retweets;
	}

	public void setRetweets(int retweets) {
		this.retweets = retweets;
	}

	public int getFavourites() {
		return favourites;
	}

	public void setFavourites(int favourites) {
		this.favourites = favourites;
	}

	public List<String> getimageUrls() {
		return imageUrls;
	}

	public void setimageUrls(List<String> imageUrls) {
		this.imageUrls = imageUrls;
	}
}