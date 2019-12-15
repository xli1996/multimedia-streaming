package org.freedesktop.gstreamer.examples;

import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.freedesktop.gstreamer.Bin;
import org.freedesktop.gstreamer.Buffer;
import org.freedesktop.gstreamer.Bus;
import org.freedesktop.gstreamer.ElementFactory;
import org.freedesktop.gstreamer.Gst;
import org.freedesktop.gstreamer.GstObject;
import org.freedesktop.gstreamer.Pipeline;
import org.freedesktop.gstreamer.elements.AppSrc;
import org.freedesktop.gstreamer.kafka.KafkaConsumerWrapper;
import org.freedesktop.gstreamer.lowlevel.MainLoop;

public class ReadFromKafka {

  /**
   * @param args the command line arguments
   */

  private static Pipeline pipe;
  private static byte[] videoBytes = null;
  private static String topic = "live-stream";

  public static void main(String[] args) throws Exception {
    final KafkaConsumerWrapper kafkaConsumer = new KafkaConsumerWrapper();

    kafkaConsumer
        .initialize("ec2-34-217-2-237.us-west-2.compute.amazonaws.com:9093", topic, "hackday");
    kafkaConsumer.seekToEnd(topic, 0);
//    kafkaConsumer.seek(topic, 0, 0);
    // get the output stream from the socket.
    Gst.init();

    final ConcurrentLinkedQueue<ConsumerRecord<String, byte[]>>
        consumerRecords = new ConcurrentLinkedQueue();

    Runnable kafkaPolling = () -> {
      ConsumerRecords<String, byte[]> records;
      records = kafkaConsumer.receive();
      if (records.count() > 0) {
        records.forEach(record -> {
          consumerRecords.add(record);
        });
        synchronized (consumerRecords) {
          consumerRecords.notify();
        }
      }
    };

    ScheduledExecutorService ses = Executors.newScheduledThreadPool(1);
    ScheduledFuture<?> scheduledFuture = ses
        .scheduleAtFixedRate(kafkaPolling, 0, 30, TimeUnit.MILLISECONDS);

    final MainLoop loop = new MainLoop();
    pipe = new Pipeline();

    Bus bus = pipe.getBus();
    bus.connect(new Bus.EOS() {

      @Override
      public void endOfStream(GstObject source) {
        System.out.println("Reached end of stream");
        loop.quit();
      }

    });

    bus.connect(new Bus.ERROR() {

      @Override
      public void errorMessage(GstObject source, int code, String message) {
        System.out.println("Error detected");
        System.out.println("Error source: " + source.getName());
        System.out.println("Error code: " + code);
        System.out.println("Message: " + message);
        loop.quit();
      }
    });

    AppSrc source = (AppSrc) ElementFactory.make("appsrc", "app-source");
    source.set("emit-signals", true);
//    source.set("blocksize", 1048576);
    source.set("blocksize", 4096);
    source.connect(new AppSrc.NEED_DATA() {

      final int[] offsetWithinRecord = new int[]{0};

      @Override
      public void needData(AppSrc elem, int size) {
        //no new record yet, wait the the g
        if (consumerRecords.isEmpty()) {
          synchronized (consumerRecords) {
            try {
              consumerRecords.wait();
            } catch (InterruptedException e) {
              e.printStackTrace();
            }
          }
        }

        int copySize = 0;
        int currentSize = 0;
        Iterator<ConsumerRecord<String, byte[]>> currentRecord = consumerRecords.iterator();
        while (currentRecord.hasNext()) {
          ConsumerRecord<String, byte[]> record = currentRecord.next();
          currentSize += record.value().length;
          if (currentSize > size) {
            copySize = size;
            break;
          }
          copySize = currentSize;
        }

        byte[] tempBuffer = new byte[copySize];

        int bufferOffset = 0;
        while (!consumerRecords.isEmpty()) {
          ConsumerRecord<String, byte[]> record = consumerRecords.peek();
          int recordLength = record.value().length;
          int futureOffset = bufferOffset + recordLength;
          int numCopy = futureOffset > copySize ? copySize - bufferOffset : recordLength;
          numCopy = Math.min(numCopy, record.value().length - offsetWithinRecord[0]);
          System.arraycopy(record.value(), offsetWithinRecord[0], tempBuffer, bufferOffset,
              numCopy);
          if (record.value().length <= offsetWithinRecord[0] + numCopy) {
            //we have copied everything from the record
            //go to the next one
            consumerRecords.poll();
            offsetWithinRecord[0] = 0;
          } else {
            offsetWithinRecord[0] += numCopy;
          }

          bufferOffset += numCopy;
          if (bufferOffset >= tempBuffer.length) {
            //the buffer is full do not copy more
            break;
          }
        }

        Buffer buf;
        buf = new Buffer(copySize);
        buf.map(true).put(ByteBuffer.wrap(tempBuffer));
        elem.pushBuffer(buf);
      }
    });

    Bin bin = Gst.parseBinFromDescription(
        "h264parse ! avdec_h264 ! videoconvert ! autovideosink",
        true);

    pipe.addMany(source, bin);
    Pipeline.linkMany(source, bin);

    System.out.println("Playing...");
    pipe.play();
    System.out.println("Running...");
    loop.run();
    System.out.println("Returned, stopping playback");
    pipe.stop();
    Gst.deinit();
    Gst.quit();

    scheduledFuture.cancel(true);
  }
}
