import java.io.*;
import java.nio.file.*;
import java.util.*;

class Cluster {
    double[] centroid;
    List<String> songs;

    Cluster(double[] centroid) {
        this.centroid = centroid;
        this.songs = new ArrayList<>();
    }
}

class SongStats {
    int count;
    double mean;
    double m2;

    void add(int rating) {
        count++;
        double delta = rating - mean;
        mean += delta / count;
        double delta2 = rating - mean;
        m2 += delta * delta2;
    }

    double stddev() {
        if (count == 0) return 0.0;
        return Math.sqrt(m2 / count);
    }
}

class UserProfile {
    String name;
    Map<String, Integer> rawRatings;
    Map<String, Double> normalizedRatings;
    double mean;
    double stddev;
    boolean cooperative;

    UserProfile(String name) {
        this.name = name;
        this.rawRatings = new HashMap<>();
        this.normalizedRatings = new HashMap<>();
        this.mean = 0.0;
        this.stddev = 0.0;
        this.cooperative = false;
    }

    void addRating(String song, int rating) {
        rawRatings.put(song, rating);
    }

    void finish() {
        if (rawRatings.isEmpty()) {
            cooperative = false;
            return;
        }

        Set<Integer> distinct = new HashSet<>(rawRatings.values());
        double sum = 0.0;
        for (int rating : rawRatings.values()) {
            sum += rating;
        }
        mean = sum / rawRatings.size();

        double variance = 0.0;
        for (int rating : rawRatings.values()) {
            double diff = rating - mean;
            variance += diff * diff;
        }
        stddev = Math.sqrt(variance / rawRatings.size());

        cooperative = distinct.size() >= 2 && stddev > 0.0;

        if (cooperative) {
            for (String song : rawRatings.keySet()) {
                int rating = rawRatings.get(song);
                normalizedRatings.put(song, (rating - mean) / stddev);
            }
        }
    }
}

public class Cs214Project {

    public static void main(String[] args) {
        try {
            int mode;

            if (args.length == 2) {
                mode = 1;
            } else if (args.length >= 3) {
                if (args[2].equals("-a")) {
                    mode = 2;
                } else if (args[2].equals("-u")) {
                    mode = 3;
                } else if (args[2].equals("-p")) {
                    mode = 4;
                } else if (args[2].equals("-r")) {
                    mode = 5;
                } else if (args[2].equals("-s")) {
                    mode = 6;
                } else {
                    System.err.println("Error: unsupported argument " + args[2]);
                    return;
                }
            } else {
                System.err.println("Error: incorrect number of arguments");
                return;
            }

            if (!args[0].toLowerCase().endsWith(".csv") || !args[1].toLowerCase().endsWith(".csv")) {
                System.err.println("Error: input and output paths must have `.csv` extension");
                return;
            }

            Path in = Paths.get(args[0]);
            Path out = Paths.get(args[1]);

            if (!Files.exists(in)) {
                System.err.println("Error: file " + in + " does not exist");
                return;
            }

            try (BufferedReader chk = Files.newBufferedReader(in)) {
                if (chk.readLine() == null) {
                    System.err.println("Error: file " + in + " is empty");
                    return;
                }
            }

            Path parent = out.toAbsolutePath().getParent();
            if (parent != null && !Files.exists(parent)) {
                System.err.println("Error: directory " + parent + " does not exist");
                return;
            }

            List<String[]> rows = readRows(in);

            if (mode == 1) {
                writePA1(rows, out);
            } else if (mode == 2) {
                writePA2(rows, out);
            } else if (mode == 3) {
                writeUserSimilarity(rows, out);
            } else if (mode == 4) {
                writePredictions(rows, out);
            } else if (mode == 5) {
                writeRecommendations(rows, out, args);
            } else if (mode == 6) {
                writeClusterProximityPlaylist(rows, out, args);
            }

        } catch (Exception e) {
            String m = e.getMessage();
            if (m == null) System.err.println("Error: unknown error");
            else if (m.startsWith("Error:")) System.err.println(m);
            else System.err.println("Error: " + m);
        }
    }

    static List<String[]> readRows(Path input) throws IOException {
        List<String[]> rows = new ArrayList<>();

        try (BufferedReader br = Files.newBufferedReader(input)) {
            String line;
            int lineNum = 0;

            while ((line = br.readLine()) != null) {
                lineNum++;
                if (line.trim().isEmpty()) continue;

                String[] parts = line.split(",", -1);
                if (parts.length != 3) {
                    throw new IllegalArgumentException("Error: line " + lineNum + " does not have 3 fields");
                }

                String song = parts[0].trim();
                String user = parts[1].trim();
                String ratingStr = parts[2].trim();

                if (song.isEmpty() || user.isEmpty()) {
                    throw new IllegalArgumentException("Error: empty song or user on line " + lineNum);
                }

                if (!ratingStr.matches("-?\\d+")) {
                    throw new IllegalArgumentException("Error: rating is not an integer on line " + lineNum);
                }

                int rating = Integer.parseInt(ratingStr);
                if (rating < 1 || rating > 5) {
                    throw new IllegalArgumentException("Error: rating out of range on line " + lineNum);
                }

                rows.add(new String[] { song, user, Integer.toString(rating) });
            }
        }

        if (rows.isEmpty()) {
            throw new IllegalArgumentException("Error: file " + input + " is empty");
        }

        return rows;
    }

    static void writePA1(List<String[]> rows, Path out) throws IOException {
        Map<String, SongStats> map = new HashMap<>();

        for (String[] r : rows) {
            String song = r[0];
            int rating = Integer.parseInt(r[2]);
            map.computeIfAbsent(song, k -> new SongStats()).add(rating);
        }

        List<String> songs = new ArrayList<>(map.keySet());
        Collections.sort(songs);

        try (BufferedWriter bw = Files.newBufferedWriter(out)) {
            bw.write("song,number of ratings,mean,standard deviation\n");
            for (String s : songs) {
                SongStats st = map.get(s);
                bw.write(s + "," + st.count + "," + st.mean + "," + st.stddev() + "\n");
            }
        }
    }

    static void writePA2(List<String[]> rows, Path out) throws IOException {
        Map<String, Map<String, Integer>> userSongRatings = new HashMap<>();
        Map<String, Set<Integer>> userDistinctRatings = new HashMap<>();
        Set<String> allSongs = new TreeSet<>();

        for (String[] r : rows) {
            String song = r[0];
            String user = r[1];
            int rating = Integer.parseInt(r[2]);

            allSongs.add(song);

            userSongRatings
                .computeIfAbsent(user, k -> new HashMap<>())
                .put(song, rating);

            userDistinctRatings
                .computeIfAbsent(user, k -> new HashSet<>())
                .add(rating);
        }

        List<String> validUsers = new ArrayList<>();
        for (String user : userSongRatings.keySet()) {
            if (userDistinctRatings.get(user).size() >= 2) {
                validUsers.add(user);
            }
        }

        Collections.sort(validUsers);

        try (BufferedWriter bw = Files.newBufferedWriter(out)) {
            bw.write("username,song,rating\n");
            for (String user : validUsers) {
                Map<String, Integer> ratings = userSongRatings.get(user);
                for (String song : allSongs) {
                    Integer rating = ratings.get(song);
                    bw.write(user + "," + song + "," + (rating == null ? "NaN" : rating) + "\n");
                }
            }
        }
    }

    static Map<String, UserProfile> buildProfiles(List<String[]> rows) {
        Map<String, UserProfile> profiles = new HashMap<>();

        for (String[] r : rows) {
            String song = r[0];
            String user = r[1];
            int rating = Integer.parseInt(r[2]);

            profiles.computeIfAbsent(user, k -> new UserProfile(user)).addRating(song, rating);
        }

        for (UserProfile profile : profiles.values()) {
            profile.finish();
        }

        return profiles;
    }

    static double similarity(UserProfile a, UserProfile b) {
        if (!a.cooperative || !b.cooperative) {
            return Double.NaN;
        }

        double sum = 0.0;
        int common = 0;

        for (String song : a.normalizedRatings.keySet()) {
            if (b.normalizedRatings.containsKey(song)) {
                double diff = a.normalizedRatings.get(song) - b.normalizedRatings.get(song);
                sum += diff * diff;
                common++;
            }
        }

        if (common == 0) {
            return Double.NaN;
        }

        return Math.sqrt(sum);
    }

    static int countCooperativeUsers(Map<String, UserProfile> profiles) {
        int count = 0;
        for (UserProfile p : profiles.values()) {
            if (p.cooperative) {
                count++;
            }
        }
        return count;
    }

    static void writeUserSimilarity(List<String[]> rows, Path out) throws IOException {
        Map<String, UserProfile> profiles = buildProfiles(rows);

        if (countCooperativeUsers(profiles) < 2) {
            System.err.println("Error: at least two cooperative users are required for user similarity");
            return;
        }

        Set<String> allSongs = new TreeSet<>();
        for (String[] r : rows) allSongs.add(r[0]);
        List<String> songs = new ArrayList<>(allSongs);

        try (BufferedWriter bw = Files.newBufferedWriter(out)) {
            bw.write("name1,name2,similarity\n");
            for (int i = 0; i < songs.size(); i++) {
                for (int j = i + 1; j < songs.size(); j++) {
                    String s1 = songs.get(i);
                    String s2 = songs.get(j);
                    double sim = songSimilarity(s1, s2, profiles);
                    bw.write(s1 + "," + s2 + "," + (Double.isNaN(sim) ? "NaN" : sim) + "\n");
                }
            }
        }
    }

    static double songSimilarity(String song1, String song2, Map<String, UserProfile> profiles) {
        double sum = 0.0;
        int common = 0;
        for (UserProfile profile : profiles.values()) {
            if (!profile.cooperative) continue;
            Double r1 = profile.normalizedRatings.get(song1);
            Double r2 = profile.normalizedRatings.get(song2);
            if (r1 != null && r2 != null) {
                double diff = r1 - r2;
                sum += diff * diff;
                common++;
            }
        }
        return common == 0 ? Double.NaN : Math.sqrt(sum);
    }

    static void writePredictions(List<String[]> rows, Path out) throws IOException {
        Map<String, UserProfile> profiles = buildProfiles(rows);

        if (countCooperativeUsers(profiles) < 2) {
            System.err.println("Error: at least two cooperative users are required for prediction");
            return;
        }

        Set<String> allSongs = new TreeSet<>();
        Set<String> allUsers = new TreeSet<>();

        for (String[] r : rows) {
            allSongs.add(r[0]);
            allUsers.add(r[1]);
        }

        List<String> missing = new ArrayList<>();
        for (String song : allSongs) {
            for (String user : allUsers) {
                UserProfile profile = profiles.get(user);
                if (!profile.rawRatings.containsKey(song)) {
                    missing.add(song + "\t" + user);
                }
            }
        }

        if (missing.isEmpty()) {
            System.err.println("Error: no predictions to be made");
            return;
        }

        Map<String, List<String>> sortedNeighbors = new HashMap<>();

        for (String user : allUsers) {
            List<String> others = new ArrayList<>();
            for (String other : allUsers) {
                if (!user.equals(other)) {
                    double sim = similarity(profiles.get(user), profiles.get(other));
                    if (!Double.isNaN(sim)) {
                        others.add(other);
                    }
                }
            }

            Collections.sort(others, new Comparator<String>() {
                public int compare(String a, String b) {
                    double sa = similarity(profiles.get(user), profiles.get(a));
                    double sb = similarity(profiles.get(user), profiles.get(b));
                    if (sa < sb) return -1;
                    if (sa > sb) return 1;
                    return a.compareTo(b);
                }
            });

            sortedNeighbors.put(user, others);
        }

        try (BufferedWriter bw = Files.newBufferedWriter(out)) {
            bw.write("song,user,predicted rating\n");

            Collections.sort(missing);

            for (String entry : missing) {
                String[] parts = entry.split("\t", -1);
                String song = parts[0];
                String user = parts[1];
                UserProfile target = profiles.get(user);

                String prediction = "NaN";

                if (target.cooperative) {
                    List<String> neighbors = sortedNeighbors.get(user);

                    for (String otherName : neighbors) {
                        UserProfile other = profiles.get(otherName);
                        if (other.normalizedRatings.containsKey(song)) {
                            double borrowed = other.normalizedRatings.get(song);
                            long rounded = Math.round(borrowed * target.stddev + target.mean);

                            if (rounded < 1) rounded = 1;
                            if (rounded > 5) rounded = 5;

                            prediction = Long.toString(rounded);
                            break;
                        }
                    }
                }

                bw.write(song + "," + user + "," + prediction + "\n");
            }
        }
    }

    static double distance(double[] a, double[] b) {
        double sum = 0.0;
        for (int i = 0; i < a.length; i++) {
            double d = a[i] - b[i];
            sum += d * d;
        }
        return Math.sqrt(sum);
    }

    static Map<String, List<String>> buildSortedNeighbors(Map<String, UserProfile> profiles, Set<String> users) {
        Map<String, List<String>> neighbors = new HashMap<>();

        for (String user : users) {
            List<String> others = new ArrayList<>();

            for (String other : users) {
                if (!user.equals(other)) {
                    double sim = similarity(profiles.get(user), profiles.get(other));
                    if (!Double.isNaN(sim)) {
                        others.add(other);
                    }
                }
            }

            Collections.sort(others, new Comparator<String>() {
                public int compare(String a, String b) {
                    double simA = similarity(profiles.get(user), profiles.get(a));
                    double simB = similarity(profiles.get(user), profiles.get(b));
                    if (simA < simB) return -1;
                    if (simA > simB) return 1;
                    return a.compareTo(b);
                }
            });

            neighbors.put(user, others);
        }

        return neighbors;
    }

    static Map<String, Map<String, Integer>> buildOriginalRatingsMatrix(List<String[]> rows) {
        Map<String, Map<String, Integer>> ratings = new HashMap<>();

        for (String[] r : rows) {
            String song = r[0];
            String user = r[1];
            int rating = Integer.parseInt(r[2]);

            ratings.computeIfAbsent(song, k -> new HashMap<>()).put(user, rating);
        }

        return ratings;
    }

    static Map<String, Integer> buildSongCount(List<String[]> rows) {
        Map<String, Integer> count = new HashMap<>();
        for (String[] r : rows) {
            String song = r[0];
            count.put(song, count.getOrDefault(song, 0) + 1);
        }
        return count;
    }

    static Map<String, Integer> buildUserCount(List<String[]> rows) {
        Map<String, Integer> count = new HashMap<>();
        for (String[] r : rows) {
            String user = r[1];
            count.put(user, count.getOrDefault(user, 0) + 1);
        }
        return count;
    }

    static Map<String, Double> buildSongMean(List<String[]> rows) {
        Map<String, Double> sum = new HashMap<>();
        Map<String, Integer> count = new HashMap<>();

        for (String[] r : rows) {
            String song = r[0];
            int rating = Integer.parseInt(r[2]);
            sum.put(song, sum.getOrDefault(song, 0.0) + rating);
            count.put(song, count.getOrDefault(song, 0) + 1);
        }

        Map<String, Double> mean = new HashMap<>();
        for (String song : sum.keySet()) {
            mean.put(song, sum.get(song) / count.get(song));
        }
        return mean;
    }

    static Map<String, Double> buildUserMean(List<String[]> rows) {
        Map<String, Double> sum = new HashMap<>();
        Map<String, Integer> count = new HashMap<>();

        for (String[] r : rows) {
            String user = r[1];
            int rating = Integer.parseInt(r[2]);
            sum.put(user, sum.getOrDefault(user, 0.0) + rating);
            count.put(user, count.getOrDefault(user, 0) + 1);
        }

        Map<String, Double> mean = new HashMap<>();
        for (String user : sum.keySet()) {
            mean.put(user, sum.get(user) / count.get(user));
        }
        return mean;
    }

    static String predictSingleRating(String song, String user, Map<String, UserProfile> profiles,
                                      Map<String, List<String>> sortedNeighbors) {
        UserProfile target = profiles.get(user);
        if (target == null || !target.cooperative) {
            return "NaN";
        }

        List<String> neighbors = sortedNeighbors.get(user);
        if (neighbors == null) {
            return "NaN";
        }

        for (String otherName : neighbors) {
            UserProfile other = profiles.get(otherName);
            if (other != null && other.normalizedRatings.containsKey(song)) {
                double borrowed = other.normalizedRatings.get(song);
                long rounded = Math.round(borrowed * target.stddev + target.mean);

                if (rounded < 1) rounded = 1;
                if (rounded > 5) rounded = 5;

                return Long.toString(rounded);
            }
        }

        return "NaN";
    }

    static Map<String, Map<String, Integer>> buildFullRatingsForRecommendations(List<String[]> rows) {
        Map<String, UserProfile> profiles = buildProfiles(rows);
        Map<String, Map<String, Integer>> original = buildOriginalRatingsMatrix(rows);

        Set<String> songs = new TreeSet<>();
        Set<String> users = new TreeSet<>();
        for (String[] r : rows) {
            songs.add(r[0]);
            users.add(r[1]);
        }

        Map<String, List<String>> neighbors = buildSortedNeighbors(profiles, users);

        Map<String, Double> songMean = buildSongMean(rows);
        Map<String, Integer> songCount = buildSongCount(rows);
        Map<String, Double> userMean = buildUserMean(rows);
        Map<String, Integer> userCount = buildUserCount(rows);

        Map<String, Map<String, Integer>> full = new HashMap<>();
        for (String song : songs) {
            full.put(song, new HashMap<>());
            Map<String, Integer> existing = original.get(song);
            if (existing != null) {
                full.get(song).putAll(existing);
            }
        }

        for (String song : songs) {
            for (String user : users) {
                if (!full.get(song).containsKey(user)) {
                    String prediction = predictSingleRating(song, user, profiles, neighbors);

                    if (!prediction.equals("NaN")) {
                        full.get(song).put(user, Integer.parseInt(prediction));
                    } else {
                        double sm = songMean.get(song);
                        int sc = songCount.get(song);
                        double um = userMean.get(user);
                        int uc = userCount.get(user);
                        long weighted = Math.round((sm * sc + um * uc) / (sc + uc));

                        if (weighted < 1) weighted = 1;
                        if (weighted > 5) weighted = 5;

                        full.get(song).put(user, (int) weighted);
                    }
                }
            }
        }

        return full;
    }

    static void removeFlatSongs(Map<String, Map<String, Integer>> fullRatings) {
        List<String> toRemove = new ArrayList<>();

        for (String song : fullRatings.keySet()) {
            Set<Integer> distinct = new HashSet<>(fullRatings.get(song).values());
            if (distinct.size() <= 1) {
                toRemove.add(song);
            }
        }

        for (String song : toRemove) {
            fullRatings.remove(song);
        }
    }

    static Map<String, double[]> normalizeSongs(Map<String, Map<String, Integer>> fullRatings, List<String> users) {
        Map<String, double[]> normalized = new HashMap<>();

        for (String song : fullRatings.keySet()) {
            Map<String, Integer> ratings = fullRatings.get(song);

            double mean = 0.0;
            for (String user : users) {
                mean += ratings.get(user);
            }
            mean /= users.size();

            double variance = 0.0;
            for (String user : users) {
                double diff = ratings.get(user) - mean;
                variance += diff * diff;
            }
            variance /= users.size();

            double stddev = Math.sqrt(variance);
            if (stddev == 0.0) {
                continue;
            }

            double[] vector = new double[users.size()];
            for (int i = 0; i < users.size(); i++) {
                vector[i] = (ratings.get(users.get(i)) - mean) / stddev;
            }

            normalized.put(song, vector);
        }

        return normalized;
    }

    static List<Cluster> runKMeans(Map<String, double[]> normalizedSongs, List<String> selections, int iterations) {
        List<Cluster> clusters = new ArrayList<>();

        for (String song : selections) {
            double[] seed = normalizedSongs.get(song);
            clusters.add(new Cluster(Arrays.copyOf(seed, seed.length)));
        }

        for (int iter = 0; iter < iterations; iter++) {
            for (Cluster cluster : clusters) {
                cluster.songs.clear();
            }

            List<String> songNames = new ArrayList<>(normalizedSongs.keySet());
            Collections.sort(songNames);

            for (String song : songNames) {
                double[] vector = normalizedSongs.get(song);

                int bestIndex = 0;
                double bestDistance = distance(vector, clusters.get(0).centroid);

                for (int i = 1; i < clusters.size(); i++) {
                    double d = distance(vector, clusters.get(i).centroid);
                    if (d < bestDistance) {
                        bestDistance = d;
                        bestIndex = i;
                    }
                }

                clusters.get(bestIndex).songs.add(song);
            }

            for (Cluster cluster : clusters) {
                if (cluster.songs.isEmpty()) {
                    continue;
                }

                int dimensions = cluster.centroid.length;
                double[] nextCentroid = new double[dimensions];

                for (String song : cluster.songs) {
                    double[] vec = normalizedSongs.get(song);
                    for (int i = 0; i < dimensions; i++) {
                        nextCentroid[i] += vec[i];
                    }
                }

                for (int i = 0; i < dimensions; i++) {
                    nextCentroid[i] /= cluster.songs.size();
                }

                cluster.centroid = nextCentroid;
            }
        }

        return clusters;
    }

    static void writeRecommendations(List<String[]> rows, Path out, String[] args) throws IOException {
        if (args.length < 4) {
            System.err.println("Error: must select at least one song for recommendations");
            return;
        }

        List<String> selections = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (int i = 3; i < args.length; i++) {
            if (!seen.add(args[i])) {
                System.err.println("Error: duplicate song selections");
                return;
            }
            selections.add(args[i]);
        }

        Map<String, Map<String, Integer>> fullRatings = buildFullRatingsForRecommendations(rows);
        removeFlatSongs(fullRatings);

        List<String> users = new ArrayList<>();
        Set<String> userSet = new TreeSet<>();
        for (String[] r : rows) {
            userSet.add(r[1]);
        }
        users.addAll(userSet);

        if (fullRatings.size() < selections.size() + 1) {
            System.err.println("Error: fewer than k + 1 songs remain after preprocessing");
            return;
        }

        for (String selection : selections) {
            if (!fullRatings.containsKey(selection)) {
                System.err.println("Error: selected song not found in usable input data");
                return;
            }
        }

        Map<String, double[]> normalizedSongs = normalizeSongs(fullRatings, users);

        for (String selection : selections) {
            if (!normalizedSongs.containsKey(selection)) {
                System.err.println("Error: selected song not found in usable input data");
                return;
            }
        }

        List<Cluster> clusters = runKMeans(normalizedSongs, selections, 10);

        List<String[]> outputRows = new ArrayList<>();
        int realRecommendations = 0;

        for (int i = 0; i < selections.size(); i++) {
            String choice = selections.get(i);
            Cluster cluster = clusters.get(i);

            List<String> recs = new ArrayList<>();
            for (String song : cluster.songs) {
                if (!song.equals(choice) && !seen.contains(song)) {
                    recs.add(song);
                }
            }

            Collections.sort(recs);

            for (String rec : recs) {
                outputRows.add(new String[] { choice, rec });
                realRecommendations++;
            }
        }

        if (realRecommendations == 0) {
            System.err.println("Error: all resulting clusters are empty or only include user selections");
            return;
        }

        Collections.sort(outputRows, new Comparator<String[]>() {
            public int compare(String[] a, String[] b) {
                int c = a[0].compareTo(b[0]);
                if (c != 0) return c;
                return a[1].compareTo(b[1]);
            }
        });

        try (BufferedWriter bw = Files.newBufferedWriter(out)) {
            bw.write("user choice,recommendation\n");
            for (String[] row : outputRows) {
                bw.write(row[0] + "," + row[1] + "\n");
            }
        }
    }

    static class PlaylistCandidate {
        String song;
        double score;

        PlaylistCandidate(String song, double score) {
            this.song = song;
            this.score = score;
        }
    }

    static void writeClusterProximityPlaylist(List<String[]> rows, Path out, String[] args) throws IOException {
        if (args.length < 5) {
            System.err.println("Error: -s requires at least 5 arguments");
            return;
        }

        int k;
        try {
            k = Integer.parseInt(args[3]);
        } catch (NumberFormatException e) {
            System.err.println("Error: K must be an integer");
            return;
        }

        if (k < 1) {
            System.err.println("Error: K must be at least 1");
            return;
        }

        List<String> likedSongs = new ArrayList<>();
        Set<String> likedSet = new HashSet<>();

        for (int i = 4; i < args.length; i++) {
            if (!likedSet.add(args[i])) {
                System.err.println("Error: duplicate song titles in input");
                return;
            }
            likedSongs.add(args[i]);
        }

        if (likedSongs.size() < k) {
            System.err.println("Error: fewer user-provided songs than K");
            return;
        }

        Map<String, Map<String, Integer>> fullRatings = buildFullRatingsForRecommendations(rows);
        removeFlatSongs(fullRatings);

        if (fullRatings.size() < k + 1) {
            System.err.println("Error: fewer than K+1 songs remain after preprocessing");
            return;
        }

        for (String song : likedSongs) {
            if (!fullRatings.containsKey(song)) {
                System.err.println("Error: user-provided song not found in usable input data");
                return;
            }
        }

        List<String> users = new ArrayList<>();
        Set<String> userSet = new TreeSet<>();
        for (String[] r : rows) {
            userSet.add(r[1]);
        }
        users.addAll(userSet);

        Map<String, double[]> normalizedSongs = normalizeSongs(fullRatings, users);

        for (String song : likedSongs) {
            if (!normalizedSongs.containsKey(song)) {
                System.err.println("Error: user-provided song not found in usable input data");
                return;
            }
        }

        List<String> centroids = likedSongs.subList(0, k);
        List<Cluster> clusters = runKMeans(normalizedSongs, centroids, 10);

        Map<String, Integer> songToCluster = new HashMap<>();
        for (int i = 0; i < clusters.size(); i++) {
            for (String s : clusters.get(i).songs) {
                songToCluster.put(s, i);
            }
        }

        Map<String, Double> bestScore = new HashMap<>();

        for (String liked : likedSongs) {
            Integer clusterIdx = songToCluster.get(liked);
            if (clusterIdx == null) continue;

            Cluster cluster = clusters.get(clusterIdx);
            double[] likedVec = normalizedSongs.get(liked);

            for (String candidate : cluster.songs) {
                if (likedSet.contains(candidate)) continue;

                double[] candidateVec = normalizedSongs.get(candidate);
                double dist = distance(likedVec, candidateVec);

                double score = (dist == 0.0) ? Double.MAX_VALUE : 1.0 / dist;

                if (!bestScore.containsKey(candidate) || score > bestScore.get(candidate)) {
                    bestScore.put(candidate, score);
                }
            }
        }

        List<String> candidates = new ArrayList<>(bestScore.keySet());
        candidates.sort((a, b) -> {
            double diff = bestScore.get(b) - bestScore.get(a);
            if (diff < 0) return -1;
            if (diff > 0) return 1;
            return a.compareTo(b);
        });

        try (BufferedWriter bw = Files.newBufferedWriter(out)) {
            int limit = Math.min(20, candidates.size());
            for (int i = 0; i < limit; i++) {
                bw.write(candidates.get(i) + "\n");
            }
        }
    }
}
