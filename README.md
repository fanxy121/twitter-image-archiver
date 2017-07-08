# Twitter Image Archiver
If you've ever tried manually downloading many images from Twitter, you've probably wanted this tool

## Features
* Timelines: Download all (non-retweeted) images tweeted by a user - this is what you see when you scroll through https://twitter.com/(username)/media_timeline, combined with the search results of "from:(username) filter:media" sorted by "Latest"
* Searches: Download all search results for a search query (what you see when you scroll through the search results on Twitter sorted by "Latest")
* No restrictions that other tools have (e.g. ~3200 most recent tweets, tweets from the last ~7 days, etc.)
* Timestamps saved into image file metadata; all other Tweet data also saved locally

## Usage
* "Timelines" and "Searches" are referred to as "query types"
  * For timelines, "queries" refers to the usernames of the account timelines you'd like to download images for
  * For searches, "queries" refers to the text you enter into the search bar on Twitter
* Put queries in "(query type)/(query_type).txt", one per line (no "@"s for usernames)
  * **This is the only thing you actually need to do**
* Images are saved in "(query type)/(query)/"
* Tweet data is saved in "Tweets/(query type)/(query)/(query).ser"
* For example, if you want to archive all images tweeted by @realDonaldTrump:
  * Create a directory named "Timelines" in the same directory as this program, (if it doesn't exist already)
  * In the directory "Timelines", create a file named "Timelines.txt", (if it doesn't exist already)
  * Open the file "Timelines.txt" and enter "realDonaldTrump" (no quotes), then save the file
  * Run the program
  * Images will be saved in "Timelines/realDonaldTrump/"

## In progress
* Auto update
* Images from URLs in Tweet text (twitpic, imgur, etc.)
* Images liked/retweeted by a user
* gifs, videos

## Credits
This project uses a modified version of Tom Dickinson's [Twitter Search API](https://github.com/tomkdickinson/TwitterSearchAPI) for Java. He has some very helpful instructional posts at his [blog](http://tomkdickinson.co.uk/).