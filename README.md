# Twitter Image Archiver
I like saving images from Twitter but there are too many accounts I follow, who tweet too many images, for me to do manually, so I made this

## Features
* Timelines: Download all non-retweeted images tweeted by a username (what you see when you scroll through https://twitter.com/(username)/media_timeline, combined with the search results of "from:(username) filter:media" on Twitter sorted by "Latest")
* Searches: Download all search results for a search query (what you see when you scroll through the search results on Twitter sorted by "Latest")
* No restrictions that other tools have (e.g. ~3200 most recent tweets, tweets from the last ~7 days, etc.)

## Usage
* "Timelines" and "Searches" are referred to as "query types"
  * For timelines, "queries" refers to the usernames you'd like to download images for
  * For searches, "queries" refers to what you type into the search bar on Twitter
* Put queries in "(query type)/(query_type)_queries.txt", one per line (no "@"s for usernames)
  * **This is the only thing you actually need to do**
  * For example, if you want to archive the images tweeted by @realDonaldTrump:
    * Create a directory named "Timelines" in the same directory as this program, (if it doesn't exist already)
    * In the directory "Timelines", create a file named "Timelines_queries.txt", (if it doesn't exist already)
    * Open the file "Timelines_queries.txt" and enter "realDonaldTrump" (no quotes), then save the file
    * Run the program
* Images will be saved as "(query type)/(query)/YYMMDD-hhmmss_filename"
* Logs for each image will be saved in "(query type)/(query)/(query)_logs.txt" as "YYMMDD-hhmmss,filename,tweet ID,tweet text"
* ID of the oldest tweet to get for a specific user is read from and automatically updated to "(query type)/(query)/(query)_sinceId.txt" (for reference, tweet URLs are formatted as https://twitter.com/(username)/status/(ID))

## In progress
* Auto update
* Images from twitpic, imgur, etc.
* Images liked/retweeted by a user
* gifs, videos

## Credits
This project uses a modified version of Tom Dickinson's [Twitter Search API](https://github.com/tomkdickinson/TwitterSearchAPI) for Java. He has some very helpful instructional posts at his [blog](http://tomkdickinson.co.uk/).