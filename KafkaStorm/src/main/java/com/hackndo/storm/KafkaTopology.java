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


/**
 * This topology demonstrates how to count distinct words from
 * a stream of words.
 *
 * This is an example for Udacity Real Time Analytics Course - ud381
 *
 */
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


    public static class ParseLogBolt extends BaseBasicBolt {
        @Override
        public void declareOutputFields(OutputFieldsDeclarer outputFieldsDeclarer) {
            outputFieldsDeclarer.declare(new Fields("id", "date"));
        }

        @Override
        public void execute(Tuple tuple, BasicOutputCollector collector) {
            String pattern = "\\[(.*)\\]";

            // Create a Pattern object
            Pattern r = Pattern.compile(pattern);

            // Now create matcher object.
            Matcher m = r.matcher(tuple.toString());
            if (m.find()) {
                JSONObject jsonObject = null;
                try {
                    jsonObject = (JSONObject) new JSONParser().parse(m.group(1));
                } catch (ParseException e) {
                    LOG.error("Parsing error. Invalid JSON");
                    return;
                }
                Long ts = (Long) jsonObject.get("server_timestamp");
                String visitor_id = (String) jsonObject.get("visitor_id");
                collector.emit(new Values(visitor_id, ts));
            }
        }

    }

    public static class ReportBolt extends BaseRichBolt {
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
            String id = tuple.getStringByField("id");
            // access the first column 'word'
            Long date = tuple.getLongByField("date");
            // access the second column 'count'
            LOG.info("[Storm] Add line to redis");
            redis.set(Long.toString(date), id);
            redis.zadd("storm", date, id);
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
        // create the topology
        TopologyBuilder builder = new TopologyBuilder();
        builder.setSpout("kafka-spout", new KafkaSpout(kafkaConfig), 10);
        builder.setBolt("bolt", new ParseLogBolt()).shuffleGrouping("kafka-spout");
        // attach the report bolt using global grouping - parallelism of 1
        builder.setBolt("report-bolt", new ReportBolt(), 1).globalGrouping("bolt");

        return builder.createTopology();
    }

    public static void main(String[] args) throws Exception
    {
        String kafkaZk = "localhost:2181";

        KafkaTopology kafkaTopology = new KafkaTopology(kafkaZk);
        Config config = new Config();
        StormTopology topology = kafkaTopology.buildTopology();
        // create the default config object

        // set the config in debugging mode
        config.setDebug(true);

        if (args != null && args.length > 0) {

            // run it in a live cluster

            // set the number of workers for running all spout and bolt tasks
            config.setNumWorkers(2);

            // create the topology and submit with config
            StormSubmitter.submitTopology(args[0], config, topology);

        } else {

            // run it in a simulated local cluster

            // set the number of threads to run - similar to setting number of workers in live cluster
            config.setMaxTaskParallelism(3);

            // create the local cluster instance
            LocalCluster cluster = new LocalCluster();

            // submit the topology to the local cluster
            cluster.submitTopology("kafka", config, topology);

            // let the topology run for 30 seconds. note topologies never terminate!
            Thread.sleep(300000);

            // we are done, so shutdown the local cluster
            cluster.shutdown();
        }
    }
}
