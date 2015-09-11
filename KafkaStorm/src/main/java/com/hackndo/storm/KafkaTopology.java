package com.hackndo.storm;

import backtype.storm.Config;
import backtype.storm.LocalCluster;
import backtype.storm.StormSubmitter;
import backtype.storm.generated.StormTopology;
import backtype.storm.spout.SchemeAsMultiScheme;
import backtype.storm.topology.BasicOutputCollector;
import backtype.storm.topology.OutputFieldsDeclarer;
import backtype.storm.topology.TopologyBuilder;
import backtype.storm.topology.base.BaseBasicBolt;
import backtype.storm.topology.base.BaseRichBolt;
import backtype.storm.tuple.Fields;
import backtype.storm.tuple.Tuple;
import backtype.storm.tuple.Values;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import backtype.storm.task.TopologyContext;
import backtype.storm.task.OutputCollector;

import java.util.Map;

import com.lambdaworks.redis.RedisClient;
import com.lambdaworks.redis.RedisConnection;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import storm.kafka.*;


public class KafkaTopology {
    public static final Logger LOG = LoggerFactory.getLogger(KafkaTopology.class);
    private BrokerHosts brokerHosts;

    /**
     * Constructor - does nothing
     */

    private KafkaTopology() {}

    public KafkaTopology(String kafkaZookeeper) {
        brokerHosts = new ZkHosts(kafkaZookeeper);
    }

    public static class ParseToJsonBolt extends BaseBasicBolt {
        @Override
        public void declareOutputFields(OutputFieldsDeclarer outputFieldsDeclarer) {
            outputFieldsDeclarer.declare(new Fields("json"));
        }

        @Override
        public void execute(Tuple tuple, BasicOutputCollector basicOutputCollector) {
            String pattern = "\\[(.*)\\]";
            // Create a Pattern object
            Pattern r = Pattern.compile(pattern);

            // Now create matcher object.
            Matcher m = r.matcher(tuple.toString());
            if (m.find()) {
                basicOutputCollector.emit(new Values(m.group(1)));
            }
        }
    }

    public static class ExtractInformationBolt extends BaseBasicBolt {

        public static Map<String, List<String>> splitQuery(String url) throws UnsupportedEncodingException {
            final Map<String, List<String>> query_pairs = new LinkedHashMap<String, List<String>>();
            final String[] pairs = url.split("&");
            for (String pair : pairs) {
                final int idx = pair.indexOf("=");
                final String key = idx > 0 ? URLDecoder.decode(pair.substring(0, idx), "UTF-8") : pair;
                if (!query_pairs.containsKey(key)) {
                    query_pairs.put(key, new LinkedList<String>());
                }
                final String value = idx > 0 && pair.length() > idx + 1 ? URLDecoder.decode(pair.substring(idx + 1), "UTF-8") : null;
                query_pairs.get(key).add(value);
            }
            return query_pairs;
        }

        @Override
        public void declareOutputFields(OutputFieldsDeclarer outputFieldsDeclarer) {
            outputFieldsDeclarer.declare(new Fields("tuple", "user_id", "timestamp", "currentState"));
        }

        @Override
        public void execute(Tuple tuple, BasicOutputCollector basicOutputCollector) {

            JSONObject jsonObject;
            try {
                jsonObject = (JSONObject) new JSONParser().parse(tuple.getStringByField("json"));
            } catch (ParseException e) {
                LOG.error("Parsing error. Invalid JSON");
                return;
            }
            Long ts = (Long) jsonObject.get("server_timestamp");
            String visitor_id = (String) jsonObject.get("visitor_id");
            String sp = (String) jsonObject.get("session_params");
            Map<String, List<String>> sp_parameters;
            try {
                sp_parameters = splitQuery(sp);
                LOG.debug("[COUNT USERS] " + sp_parameters.toString());
            } catch (UnsupportedEncodingException e) {
                LOG.error("Unsupported Encoding");
                return;
            }
            String currentState = "NA";
            if (sp_parameters.containsKey("currentState")) {
                currentState = sp_parameters.get("currentState").get(0);
            }
            basicOutputCollector.emit(new Values(tuple, visitor_id, ts, currentState));
        }
    }

    public static class ReportUserCountBolt extends BaseRichBolt {
        // place holder to keep the connection to redis
        transient RedisConnection<String,String> redis;

        @Override
        public void prepare(
                Map                     map,
                TopologyContext         topologyContext,
                OutputCollector         outputCollector)
        {
            // instantiate a redis connection
            RedisClient client = new RedisClient("localhost",6379);

            // initiate the actual connection
            redis = client.connect();
        }

        @Override
        public void execute(Tuple tuple)
        {
            String id = tuple.getStringByField("user_id");
            // access the first column 'word'
            Long date = tuple.getLongByField("timestamp");
            // access the second column 'count'
            LOG.info("[Storm] Add line to redis");
            redis.zadd("users", date, id);
            LOG.info("[Storm] Line added !");
        }

        public void declareOutputFields(OutputFieldsDeclarer declarer)
        {
            // nothing to add - since it is the final bolt
        }
    }

    public static class ReportUserCountPerComponentBolt extends BaseRichBolt {
        // place holder to keep the connection to redis
        transient RedisConnection<String,String> redis;

        @Override
        public void prepare(
                Map                     map,
                TopologyContext         topologyContext,
                OutputCollector         outputCollector)
        {
            // instantiate a redis connection
            RedisClient client = new RedisClient("localhost",6379);

            // initiate the actual connection
            redis = client.connect();
        }

        @Override
        public void execute(Tuple tuple)
        {
            String id = tuple.getStringByField("user_id");
            // access the first column 'word'
            Long date = tuple.getLongByField("timestamp");
            String component = tuple.getStringByField("currentState");
            LOG.info("[Storm] Add line to redis");

            redis.zadd("users_per_component", date, "{'user_id':'" + id + "', 'component':'" + component +  "'}");
            LOG.info("[Storm] Line added !");
        }

        public void declareOutputFields(OutputFieldsDeclarer declarer)
        {
            // nothing to add - since it is the final bolt
        }
    }

    public static class ReportLastUserComponentBolt extends BaseRichBolt {
        // place holder to keep the connection to redis
        transient RedisConnection<String,String> redis;

        @Override
        public void prepare(
                Map                     map,
                TopologyContext         topologyContext,
                OutputCollector         outputCollector)
        {
            // instantiate a redis connection
            RedisClient client = new RedisClient("localhost",6379);

            // initiate the actual connection
            redis = client.connect();
        }

        @Override
        public void execute(Tuple tuple)
        {
            String id = tuple.getStringByField("user_id");
            // access the first column 'word'
            Long date = tuple.getLongByField("timestamp");
            String component = tuple.getStringByField("currentState");
            LOG.info("[Storm] Add line to redis");
            redis.zadd("user_last_component", date, "{'user_id':'" + id + "',  'component':'" + component + "'}");

            //redis.zadd("users_per_component", date, "{'user_id':'" + id + "', 'component':'" + component +  "'}");
            LOG.info("[Storm] Line added !");
        }

        public void declareOutputFields(OutputFieldsDeclarer declarer)
        {
            // nothing to add - since it is the final bolt
        }
    }

    public StormTopology buildTopology() {

        SpoutConfig kafkaConfig = new SpoutConfig(brokerHosts, "dataiku-topic", "", "storm");
        kafkaConfig.scheme = new SchemeAsMultiScheme(new StringScheme());

        TopologyBuilder builder = new TopologyBuilder();
        builder.setSpout("kafka-spout", new KafkaSpout(kafkaConfig), 10);

        builder.setBolt("parse-to-json-bolt", new ParseToJsonBolt()).shuffleGrouping("kafka-spout");
        builder.setBolt("extract-information-bolt", new ExtractInformationBolt()).shuffleGrouping("parse-to-json-bolt");
        builder.setBolt("report-bolt", new ReportUserCountBolt(), 1).globalGrouping("extract-information-bolt");
        builder.setBolt("report-per-component-bolt", new ReportUserCountPerComponentBolt(), 1).globalGrouping("extract-information-bolt");
        builder.setBolt("report-last-user-component-bolt", new ReportLastUserComponentBolt(), 1).globalGrouping("extract-information-bolt");

        return builder.createTopology();
    }

    public static void main(String[] args) throws Exception
    {
        String kafkaZk = "localhost:2181";

        KafkaTopology kafkaTopology = new KafkaTopology(kafkaZk);
        Config config = new Config();
        StormTopology topology = kafkaTopology.buildTopology();
        config.setDebug(true);

        if (args != null && args.length > 0) {
            config.setNumWorkers(2);
            StormSubmitter.submitTopology(args[0], config, topology);

        } else {

            config.setMaxTaskParallelism(3);
            LocalCluster cluster = new LocalCluster();
            cluster.submitTopology("kafka", config, topology);
            Thread.sleep(60 * 1000 * 60 * 24);
            cluster.shutdown();
        }
    }
}
