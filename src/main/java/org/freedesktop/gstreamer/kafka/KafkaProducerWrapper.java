package org.freedesktop.gstreamer.kafka;

import java.util.Properties;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class KafkaProducerWrapper {
  private static final Logger logger = LoggerFactory.getLogger(KafkaProducerWrapper.class);

  private KafkaProducer<String, byte[]> producer =  null;

  public void initialize(String kafkaBootstrapServers) {
    /*
     * Defining producer properties.
     */
    Properties producerProperties = new Properties();
    producerProperties.put("bootstrap.servers", kafkaBootstrapServers);
    producerProperties.put("acks", "all");
    producerProperties.put("retries", 0);
    producerProperties.put("batch.size", 16384);
    producerProperties.put("linger.ms", 1);
    producerProperties.put("buffer.memory", 33554432);
    producerProperties.put("key.serializer", "org.apache.kafka.common.serialization.ByteArraySerializer");
    producerProperties.put("value.serializer", "org.apache.kafka.common.serialization.ByteArraySerializer");

        /*
        Creating a Kafka Producer object with the configuration above.
         */
    producer = new KafkaProducer<>(producerProperties);
  }

  public void send(String topic, byte[] payload) {
    logger.debug("Sending Kafka message: {}", payload);
    producer.send(new ProducerRecord<>(topic, payload));
  }
}
