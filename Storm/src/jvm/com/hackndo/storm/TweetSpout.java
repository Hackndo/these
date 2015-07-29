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

import twitter4j.conf.ConfigurationBuilder;
import twitter4j.TwitterStream;
import twitter4j.TwitterStreamFactory;
import twitter4j.Status;
import twitter4j.StatusDeletionNotice;
import twitter4j.StatusListener;
import twitter4j.StallWarning;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * A spout that uses Twitter streaming API for continuously
 * getting tweets
 */
public class TweetSpout extends BaseRichSpout 
{

  String custkey, custsecret;              
  String accesstoken, accesssecret;
  SpoutOutputCollector collector;
  TwitterStream twitterStream;
  LinkedBlockingQueue<String> queue = null;

  private class TweetListener implements StatusListener {
    @Override
    public void onStatus(Status status) 
    {
      queue.offer(status.getText());
    }

    @Override
    public void onDeletionNotice(StatusDeletionNotice sdn) 
    {}

    @Override
    public void onTrackLimitationNotice(int i) 
    {}

    @Override
    public void onScrubGeo(long l, long l1) 
    {}

    @Override
    public void onStallWarning(StallWarning warning) 
    {}

    @Override
    public void onException(Exception e) 
    {
      e.printStackTrace();
    }
  }

  /**
   * Constructor for tweet spout that accepts the credentials
   */
  public TweetSpout(
      String                key, 
      String                secret, 
      String                token, 
      String                tokensecret) 
  {
    custkey = key;
    custsecret = secret;
    accesstoken = token;
    accesssecret = tokensecret;
  }
  
  @Override
  public void open(
      Map                     map,
      TopologyContext         topologyContext,
      SpoutOutputCollector    spoutOutputCollector)
  {
    queue = new LinkedBlockingQueue<String>(1000);
    collector = spoutOutputCollector;

    ConfigurationBuilder config = 
        new ConfigurationBuilder()
               .setOAuthConsumerKey(custkey)
               .setOAuthConsumerSecret(custsecret)
               .setOAuthAccessToken(accesstoken)
               .setOAuthAccessTokenSecret(accesssecret);

    TwitterStreamFactory fact = 
        new TwitterStreamFactory(config.build());

    twitterStream = fact.getInstance();
    twitterStream.addListener(new TweetListener());
    twitterStream.sample();
  }

  @Override
  public void nextTuple() 
  {

    String ret = queue.poll();

    if (ret==null) 
    {
      Utils.sleep(50);
      return;
    }

    collector.emit(new Values(ret));
  }

  @Override
  public void close() 
  {
    twitterStream.shutdown();
  }

  @Override
  public Map<String, Object> getComponentConfiguration() 
  {

    Config ret = new Config();
    ret.setMaxTaskParallelism(1);
    return ret;
  }    

  @Override
  public void declareOutputFields(
      OutputFieldsDeclarer outputFieldsDeclarer)
  {
    outputFieldsDeclarer.declare(new Fields("tweet"));
  }
}
