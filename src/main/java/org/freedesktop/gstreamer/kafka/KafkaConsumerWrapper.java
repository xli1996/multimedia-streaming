package org.freedesktop.gstreamer.kafka;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KafkaConsumerWrapper {
  private static final Logger logger = LoggerFactory.getLogger(KafkaProducerWrapper.class);

  private KafkaConsumer<String, byte[]> kafkaConsumer = null;

  public void initialize(String kafkaBootstrapServers, String topics, String zookeeperGroupId) {
    Properties consumerProperties = new Properties();
    consumerProperties.put("bootstrap.servers", kafkaBootstrapServers);
    consumerProperties.put("group.id", zookeeperGroupId);
    consumerProperties.put("zookeeper.session.timeout.ms", "6000");
    consumerProperties.put("zookeeper.sync.time.ms","2000");
    consumerProperties.put("auto.commit.enable", "true");  //manually commit the offset or not
    consumerProperties.put("auto.commit.interval.ms", "100");
    consumerProperties.put("max.poll.interval.ms", 10000000);
    consumerProperties.put("consumer.timeout.ms", "-1");
    consumerProperties.put("max.poll.records", "500");
    consumerProperties.put("value.deserializer", "org.apache.kafka.common.serialization.ByteArrayDeserializer");
    consumerProperties.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");

    kafkaConsumer = new KafkaConsumer<>(consumerProperties);
//    kafkaConsumer.subscribe(Arrays.asList(topics));
    kafkaConsumer.assign(Arrays.asList(new TopicPartition(topics, 0)));

  }

  public ConsumerRecords<String, byte[]> receive() {
    return kafkaConsumer.poll(100);
  }

  public void seekToEnd(TopicPartition topicPartition) {
    logger.debug("Seeking to the end of topic partition {}.", topicPartition);
    kafkaConsumer.seekToEnd(Collections.singleton(topicPartition));
  }

  public void seekToEnd(String topic, int partition) {
    logger.debug("Seeking to the end of topic {} partition {}.", topic, partition);
    seekToEnd(new TopicPartition(topic, partition));
  }

  public void seek(TopicPartition topicPartition, long offset) {
    logger.debug("Seeking topic partition {} to offset {}", topicPartition, offset);
    kafkaConsumer.seek(topicPartition, offset);
  }

  public void seek(String topic, int partition, long offset) {
    logger.debug("Seeking topic {} partition {} to offset " + offset, topic, partition);
    seek(new TopicPartition(topic, partition), offset);
  }

  public void commitOffset(String topic, int partition, long offset) {
    Map<TopicPartition, OffsetAndMetadata> commitMessage = new HashMap<>();

    System.out.println("Committing offset for topic " + topic + " Parition " + partition + " offset " + offset);
    commitMessage.put(new TopicPartition(topic, partition),
        new OffsetAndMetadata(offset));

    kafkaConsumer.commitSync(commitMessage);

    logger.debug("Offset committed to Kafka.");
  }
}
