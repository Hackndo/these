package com.hackndo.storm;

import backtype.storm.Config;
import backtype.storm.LocalCluster;
import backtype.storm.StormSubmitter;
import backtype.storm.spout.SpoutOutputCollector;
import backtype.storm.task.OutputCollector;
import backtype.storm.task.TopologyContext;
import backtype.storm.testing.TestWordSpout;
import backtype.storm.topology.OutputFieldsDeclarer;
import backtype.storm.topology.TopologyBuilder;
import backtype.storm.topology.base.BaseRichSpout;
import backtype.storm.topology.base.BaseRichBolt;
import backtype.storm.tuple.Fields;
import backtype.storm.tuple.Tuple;
import backtype.storm.tuple.Values;
import backtype.storm.utils.Utils;

class TweetTopology
{
  public static void main(String[] args) throws Exception
  {

    TopologyBuilder builder = new TopologyBuilder();

    TweetSpout tweetSpout = new TweetSpout(
            "xxxxxxxx- custom_key    -xxxxxxxx-",
            "xxxxxxxx- custom_secret -xxxxxxxx-",
            "xxxxxxxx- access_token  -xxxxxxxx-",
            "xxxxxxxx- access_secret -xxxxxxxx-"
    );

    builder.setSpout("tweet-spout", tweetSpout, 1);

    builder.setBolt("python-split-sentence", new SplitSentence(), 10).shuffleGrouping("tweet-spout");

    builder.setBolt("count-bolt", new CountBolt(), 15).fieldsGrouping("python-split-sentence", new Fields("word"));
    builder.setBolt("report-bolt", new ReportBolt(), 1).globalGrouping("count-bolt");
    Config conf = new Config();
    conf.setDebug(true);

    if (args != null && args.length > 0) {
      conf.setNumWorkers(3);
      StormSubmitter.submitTopology(args[0], conf, builder.createTopology());

    } else {

      conf.setMaxTaskParallelism(3);
      LocalCluster cluster = new LocalCluster();
      cluster.submitTopology("tweet-word-count", conf, builder.createTopology());
      Utils.sleep(1000*30000);
      cluster.killTopology("tweet-word-count");
      cluster.shutdown();
    }
  }
}
