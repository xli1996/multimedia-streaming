package org.freedesktop.gstreamer.examples;

import java.nio.ByteBuffer;
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
    private static KafkaConsumerWrapper kafkaConsumer = new KafkaConsumerWrapper();
    private static String topic = "live-stream";

    public static void main(String[] args) throws Exception {
        kafkaConsumer.initialize("ec2-34-217-2-237.us-west-2.compute.amazonaws.com:9093", topic, "hackday");
        // get the output stream from the socket.
        Gst.init();

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



        AppSrc source = (AppSrc)ElementFactory.make("appsrc", "app-source");
        source.set("emit-signals", true);
        source.connect(new AppSrc.NEED_DATA() {

            @Override
            public void needData(AppSrc elem, int size) {
                ConsumerRecords<String, byte[]> records;
                while(true) {
                    records = kafkaConsumer.receive();
                    if (records.count() != 0)
                        break;
                }

                int copySize = 0;
                int currentSize = 0;
                for (ConsumerRecord<String, byte[]> record : records) {
                    currentSize += record.value().length;
                    if (currentSize > size)
                        break;
                    ;
                    copySize = currentSize;
                }

                byte[] tempBuffer = new byte[copySize];

                currentSize = 0;
                int preCopySize = 0;
                long offset = -1;
                int partition = 0;
                for (ConsumerRecord<String, byte[]> record : records) {
                    currentSize += record.value().length;
                    if (currentSize > size)
                        break;
                    System.arraycopy(record.value(), 0, tempBuffer, preCopySize,
                        record.value().length);
                    preCopySize = currentSize;
                    offset = record.offset();
                    partition = record.partition();
                }

                if (records.count() > 0)
                    kafkaConsumer.commitOffset(topic, partition, offset + 1);


                Buffer buf;
                buf = new Buffer(copySize);
                buf.map(true).put(ByteBuffer.wrap(tempBuffer));
                elem.pushBuffer(buf);
            }
        });


        Bin bin = Gst.parseBinFromDescription(
            "h264parse ! avdec_h264 ! videoconvert ! autovideosink",
            true);
//        Element fakesink = ElementFactory.make("fakesink", "fakesink");

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
    }

}
