#!/bin/bash

# capture.sh
# 
# Capture sensor data from phone in batch
# Operates on all movies in a given directory
#
# Movie filenames must have extensions
# Movie filenames may not contain spaces
# This script requires mplayer

# This script assumes a file named wifibuf exists in the current directory
# and is being fed data from whatever socket the phone is writing to

MOVIEDIR=movies # directory of movies to collect data on
DATADIR=sensor-data # directory to place the data

if [ ! -d "$MOVIEDIR" ]
then
	echo "You must first create a directory named \`\`$MOVIEDIR'' to contain the raw movie files"
	exit -1
fi

if [ ! -d "$DATADIR" ]
then
	echo "You must first create a directory named \`\`$DATADIR'' to hold the sensor data for each movie in \`\`$MOVIEDIR''"
	exit -1
fi
	

for movie in $(ls "$MOVIEDIR")
do
	echo "Capturing ${movie%.*}..."
	gtruncate wifibuf --size=0
	mplayer -fs "$MOVIEDIR/$movie" &>/dev/null
	tr -C -d "0123456789,.\n" < wifibuf > "${DATADIR}/${movie%.*}"-data.txt
done
