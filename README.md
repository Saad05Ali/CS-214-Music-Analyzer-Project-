# CS-214 Music Analyzer Project

A Java program that analyzes music ratings from CSV files.

## How to Run

javac Cs214Project_PA6.java
java Cs214Project input.csv output.csv [flag]

## Flags

| Flag | Description |
|------|-------------|
| (none) | PA1 — song stats (mean, std dev per song) |
| -a | PA2 — user rating matrix with NaN for unrated songs |
| -u | PA3/PA6 — song-to-song similarity using Euclidean distance |
| -p | PA4 — predict missing ratings using nearest neighbor |
| -r | PA5 — song recommendations using K-Means clustering |
| -s K song1 song2 ... | PA6 — playlist generated from cluster proximity |

## Flag Details

No flag: For every song, calculates how many people rated it, the average rating, and the standard deviation. Output is one row per song.

-a: Builds a grid with every user as a row and every song as a column. If a user did not rate a song it puts NaN. Only includes users who gave at least 2 different rating values.

-u: Compares every pair of songs and outputs how similar they are. Lower number means more similar.

-p: For every user and song combo where a rating is missing, predicts what that user would rate it by borrowing from the most similar user who did rate it.

-r: Groups songs into clusters using K-Means, then recommends songs based on those clusters.

-s: You give it K (number of clusters) and a list of songs you like. It runs K-Means, scores every other song by 1/distance, and outputs the top 20 recommendations.

## Input Format

CSV file with 3 columns: song, username, rating (1-5)

Example:
Song A,alice,5
Song A,bob,3
Song B,alice,2
Song B,bob,4
Song C,alice,4
Song C,bob,1

## Requirements

Java 8 or higher. No external libraries needed.
