# Twitter Image Archiver
Download images from Twitter and archive them locally

## Features
* Download all images tweeted by a user (what you see when you scroll through https://twitter.com/\<username\>/media_timeline)
* Not limited to the most recent ~3200 tweets like other tools

## Usage
* Put usernames to download images for in "Timelines/usernames.txt", one per line, no "@"s
* Images will be saved as "Timelines/\<username\>/YYMMDD-hhmmss_filename"
* Logs for each image will be saved in "Timelines/\<username\>/\<username\>.txt" as "YYMMDD-hhmmss,filename,tweet ID,tweet text"
* ID of the oldest tweet to get for a specific user is read from - and automatically updated to - "Timelines/\<username\>.txt" (tweet URLs are formatted as https://twitter.com/\<username\>/status/\<ID\>)

## Known issues
* Scrolling through /media_timeline eventually stops sometimes (after ~1100 tweets shown from one account I tried with)

## In progress
* Auto update
* Images from twitpic, imgur, etc.
* Images from Twitter's search function
* Liked images
* gifs, videos

## Credits
This project uses a modified version of Tom Dickinson's [Twitter Search API](https://github.com/tomkdickinson/TwitterSearchAPI) for Java. He has some very helpful instructional posts at his [blog](http://tomkdickinson.co.uk/).