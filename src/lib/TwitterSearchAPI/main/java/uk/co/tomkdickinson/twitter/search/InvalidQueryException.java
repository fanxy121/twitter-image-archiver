package lib.TwitterSearchAPI.main.java.uk.co.tomkdickinson.twitter.search;

public class InvalidQueryException extends Exception{

	private static final long serialVersionUID = 4949456261975663415L;

	public InvalidQueryException(String query) {
        super("Query string '"+query+"' is invalid");
    }
}