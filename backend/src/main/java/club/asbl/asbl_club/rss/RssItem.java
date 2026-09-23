package club.asbl.asbl_club.rss;

import java.time.Instant;

record RssItem(String title, String link, String description, Instant pubDate) {
}
