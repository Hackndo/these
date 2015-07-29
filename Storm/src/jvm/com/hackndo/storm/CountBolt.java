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

import java.util.HashMap;
import java.util.Map;

/**
 * A bolt that counts the words that it receives
 */
public class CountBolt extends BaseRichBolt
{
  private OutputCollector collector;
  private Map<String, Integer> countMap;

  @Override
  public void prepare(
      Map                     map,
      TopologyContext         topologyContext,
      OutputCollector         outputCollector)
  {
    collector = outputCollector;
    countMap = new HashMap<String, Integer>();
  }

  @Override
  public void execute(Tuple tuple)
  {
    String word = tuple.getString(0);
    if (countMap.get(word) == null) {
      countMap.put(word, 1);
    } else {
      Integer val = countMap.get(word);
      countMap.put(word, ++val);
    }

    collector.emit(new Values(word, countMap.get(word)));
  }

  @Override
  public void declareOutputFields(OutputFieldsDeclarer outputFieldsDeclarer)
  {
    outputFieldsDeclarer.declare(new Fields("word","count"));
  }
}
