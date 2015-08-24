package com.dataiku.wt1.storage;

import com.dataiku.wt1.ProcessingQueue;
import com.dataiku.wt1.TrackedRequest;
import com.dataiku.wt1.Utils;
import org.joda.time.DateTimeZone;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.Set;

/**
 * Created by romain on 21/08/15.
 */
public class JsonFormatWriter {
    /** Compute the filename to use for a new output file */
    public static String newFileName(String instance) {
        long now = System.currentTimeMillis();
        SimpleDateFormat sdf1 = new SimpleDateFormat("yyyy/MM/dd/HH/mm-ss");
        String name = "tracker/" + sdf1.format(new Date(now)) + (instance == null ? "" : "-" + instance) + ".log.gz";
        return name;
    }

    private static DateTimeFormatter isoFormatter = ISODateTimeFormat.dateHourMinuteSecondMillis().withZone(DateTimeZone.UTC);

    private static String escape(String in) {
        return in == null ? "null" : in.replace("\"", "\\\"");
    }

    private static String addKeyValue(String key, String value) {
        return "\"" + escape(key) + "\": \"" + escape(value) + "\"";
    }

    private static String addKeyValue(String key, Long value) {
        return "\"" + escape(key) + "\": " + value;
    }

    public JsonFormatWriter() {
        this.thirdPartyCookies =  ProcessingQueue.getInstance().isThirdPartyCookies();
    }

    private boolean thirdPartyCookies;



    /**
     * Write the line of log for one request, with optional terminating newline
     */
    public String makeLogLine(TrackedRequest req) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append(addKeyValue("server_timestamp", req.serverTS));
        sb.append(",");
        sb.append(addKeyValue("server_ts", isoFormatter.print(req.serverTS)));
        sb.append(",");
        sb.append(addKeyValue("client_ts", isoFormatter.print(req.clientTS)));
        sb.append(",");
        sb.append(addKeyValue("client_addr", req.origAddress));
        sb.append(",");
        sb.append(addKeyValue("visitor_id", req.visitorId));
        sb.append(",");
        sb.append(addKeyValue("session_id", req.sessionId));
        sb.append(",");
        sb.append(addKeyValue("location", escape(req.page)));
        sb.append(",");
        sb.append(addKeyValue("referer", escape(req.referer)));
        sb.append(",");
        sb.append(addKeyValue("user-agent", escape(req.ua)));
        sb.append(",");
        sb.append(addKeyValue("type", escape(req.type)));
        sb.append(",");
        sb.append(addKeyValue("visitor_params", escape(req.visitorParams)));
        sb.append(",");
        sb.append(addKeyValue("session_params", escape(req.sessionParams)));
        sb.append(",");
        sb.append(addKeyValue("event_params", escape(req.eventParams)));
        sb.append(",");
        sb.append(addKeyValue("br_width", Integer.toString(req.browserWidth)));
        sb.append(",");
        sb.append(addKeyValue("br_height", Integer.toString(req.browserHeight)));
        sb.append(",");
        sb.append(addKeyValue("sc_width", Integer.toString(req.screenWidth)));
        sb.append(",");
        sb.append(addKeyValue("sc_height", Integer.toString(req.screenHeight)));
        sb.append(",");
        sb.append(addKeyValue("br_lang",req.browserLanguage ));
        sb.append(",");
        sb.append(addKeyValue("tz_off", req.tzOffset));
        if (thirdPartyCookies) {
            sb.append(",");
            sb.append(addKeyValue("global_visitor_id", req.globalVisitorId));
        }

        sb.append("}");

        return sb.toString();
    }
}
