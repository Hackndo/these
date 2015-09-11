package com.dataiku.wt1.storage;

import com.dataiku.wt1.*;
import com.dataiku.wt1.ProcessingQueue.Stats;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.log4j.Logger;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.*;


public class KafkaSourceProcessor implements TrackingRequestProcessor {

	private CSVFormatWriter csvWriter;
	private JsonFormatWriter jsonFormatWriter;
	private KafkaProducer<String, String> producer;
	private Map<String, Object> kafkaConfig;
	private long connectionDate;
	private int flushInterval;
	private List<String> events;
	private int maxBufferSize = 1000;
	private int reconnectDelay = 60;

	private String kafkaTopic;

	private int writtenSize;
	private boolean shutdown = false;
	private long flushDate;


    public static final String FLUSH_INTERVAL_PARAM = "flushInterval";
	public static final String MAX_BUFFER_SIZE_PARAM = "maxBufferSize";
    public static final String RECONNECT_DELAY_PARAM = "reconnectDelay";
    public static final String TIMESTAMP_HEADER_PARAM = "timestampHeader";
    public static final String HOST_HEADER_PARAM = "hostHeader";

	@Override
	public void init(Map<String, String> params) throws IOException {

		csvWriter = new CSVFormatWriter(
				Utils.parseCSVToSet(params.get(ConfigConstants.INLINED_VISITOR_PARAMS)),
				Utils.parseCSVToSet(params.get(ConfigConstants.INLINED_SESSION_PARAMS)),
				Utils.parseCSVToSet(params.get(ConfigConstants.INLINED_EVENT_PARAMS)));
		jsonFormatWriter = new JsonFormatWriter();

        String flushIntervalParam = params.get(FLUSH_INTERVAL_PARAM);
        if (flushIntervalParam != null) {
        	try {
        		flushInterval = Integer.parseInt(flushIntervalParam);
        	} catch (NumberFormatException e) {
        		logger.error("Invalid value for configuration parameter " + FLUSH_INTERVAL_PARAM);
        		throw e;
        	}
        }
        String maxBufferSizeParam = params.get(MAX_BUFFER_SIZE_PARAM);
        if (maxBufferSizeParam != null) {
        	try {
        		maxBufferSize = Integer.parseInt(maxBufferSizeParam);
        	} catch (NumberFormatException e) {
        		logger.error("Invalid value for configuration parameter " + MAX_BUFFER_SIZE_PARAM);
        		throw e;
        	}
        }
        String reconnectDelayParam = params.get(RECONNECT_DELAY_PARAM);
        if (reconnectDelayParam != null) {
        	try {
        		reconnectDelay = Integer.parseInt(reconnectDelayParam);
        	} catch (NumberFormatException e) {
        		logger.error("Invalid value for configuration parameter " + RECONNECT_DELAY_PARAM);
        		throw e;
        	}
        }

		this.kafkaConfig = new HashMap<String, Object>();

		for (String k : params.keySet()) {
			if (k.startsWith("kafka.")) {
				String c = k.replace("kafka.", "");
				if ("topic".equals(c)) {
					this.kafkaTopic = params.get(k);
					continue;
				}
				this.kafkaConfig.put(c, params.get(k));
			}
		}


		events = new ArrayList<String>();
		this.producer = new org.apache.kafka.clients.producer.KafkaProducer<String, String>(this.kafkaConfig);
	}

	private synchronized void flushBuffer(boolean reinit) throws IOException {
		if (shutdown) {
    		// processor has already been shutdown
    		return;
    	}
		if (producer == null) {
			if (System.currentTimeMillis() > connectionDate + reconnectDelay * 1000) {
				logger.info("Reconnecting Flume source");
				connectionDate = System.currentTimeMillis();
				this.producer = new org.apache.kafka.clients.producer.KafkaProducer<String, String>(kafkaConfig);
			} else {
				throw new IOException("kafka connection is dead");
			}
		}


		logger.info("Sending event batch, size=" + events.size());
		for (String event : events) {
			producer.send(new ProducerRecord<String, String>(this.kafkaTopic, "msg",event));
		}


		Stats stats = ProcessingQueue.getInstance().getStats();
		synchronized (stats) {
			stats.createdFiles++;
			stats.savedEvents += events.size();
			stats.savedEventsGZippedSize += writtenSize;
			stats.savedEventsInputSize += writtenSize;
		}
		events.clear();
		writtenSize = 0;
		flushDate = System.currentTimeMillis();
		if (! reinit) {
			if (producer != null) {
				producer.close();
			}
			shutdown = true;
		}
	}

	@Override
	public void process(TrackedRequest req) throws IOException {
        String line = jsonFormatWriter.makeLogLine(req);
		logger.info("[WT1] Received line - " + line);

        byte[] data = line.getBytes("utf8");

		if (events.size() >= maxBufferSize) {
			flushBuffer(true);
		}
		logger.info("[WT1] Send to topic " + this.kafkaTopic + " to Kafka");
		this.producer.send(new ProducerRecord<String, String>(this.kafkaTopic, req.visitorId, new String(data)));
		events.add(new String(data));
        // Event event = EventBuilder.withBody(data);

        writtenSize += data.length;
        if (flushInterval > 0 && System.currentTimeMillis() > flushDate + flushInterval * 1000) {
        	try {
        		flushBuffer(true);
        	} catch (IOException e) {
        		// Catch error since event has been buffered
        	}
        }
	}

	@Override
	public void service(HttpServletRequest req, HttpServletResponse resp)
			throws IOException, ServletException {
        throw new ServletException("No HTTP service for KafkaSource");
	}

	@Override
	public void shutdown() throws IOException {
		flushBuffer(false);
	}

	@Override
	public void flush() throws IOException {
		flushBuffer(true);
	}

	private static final Logger logger = Logger.getLogger("wt1.processor.kafka");
}
