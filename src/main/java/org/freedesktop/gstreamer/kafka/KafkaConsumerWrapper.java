package org.freedesktop.gstreamer.kafka;

import java.time.Duration;
import java.util.Arrays;
import java.util.Properties;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
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
    consumerProperties.put("auto.commit.enable", "false");
    consumerProperties.put("auto.commit.interval.ms", "1000");
    consumerProperties.put("consumer.timeout.ms", "-1");
    consumerProperties.put("max.poll.records", "1");
    consumerProperties.put("value.deserializer", "org.apache.kafka.common.serialization.ByteArrayDeserializer");
    consumerProperties.put("key.deserializer", "org.apache.kafka.common.serialization.ByteArrayDeserializer");

    kafkaConsumer = new KafkaConsumer<>(consumerProperties);
    kafkaConsumer.subscribe(Arrays.asList(topics));
  }

  public ConsumerRecords<String, byte[]> receive() {
    return kafkaConsumer.poll(Duration.ofMillis(100));
  }
}
