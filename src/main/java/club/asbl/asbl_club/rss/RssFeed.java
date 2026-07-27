package club.asbl.asbl_club.rss;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

final class RssFeed {

    private static final DateTimeFormatter RFC_822 =
            DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss Z", Locale.ENGLISH);

    private RssFeed() {
    }

    static String render(String title, String link, String description, List<RssItem> items) {
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<rss version=\"2.0\">\n  <channel>\n");
        xml.append("    <title>").append(escape(title)).append("</title>\n");
        xml.append("    <link>").append(escape(link)).append("</link>\n");
        xml.append("    <description>").append(escape(description)).append("</description>\n");
        for (RssItem item : items) {
            xml.append("    <item>\n");
            xml.append("      <title>").append(escape(item.title())).append("</title>\n");
            xml.append("      <link>").append(escape(item.link())).append("</link>\n");
            xml.append("      <guid>").append(escape(item.link())).append("</guid>\n");
            if (item.description() != null) {
                xml.append("      <description>").append(escape(item.description())).append("</description>\n");
            }
            if (item.pubDate() != null) {
                xml.append("      <pubDate>")
                        .append(RFC_822.format(item.pubDate().atOffset(ZoneOffset.UTC)))
                        .append("</pubDate>\n");
            }
            xml.append("    </item>\n");
        }
        xml.append("  </channel>\n</rss>\n");
        return xml.toString();
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
