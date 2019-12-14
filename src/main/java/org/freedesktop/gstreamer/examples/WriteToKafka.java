package org.freedesktop.gstreamer.examples;

import java.nio.ByteBuffer;
import org.freedesktop.gstreamer.Bin;
import org.freedesktop.gstreamer.Bus;
import org.freedesktop.gstreamer.ElementFactory;
import org.freedesktop.gstreamer.FlowReturn;
import org.freedesktop.gstreamer.Gst;
import org.freedesktop.gstreamer.GstObject;
import org.freedesktop.gstreamer.Pipeline;
import org.freedesktop.gstreamer.Sample;
import org.freedesktop.gstreamer.elements.AppSink;
import org.freedesktop.gstreamer.kafka.KafkaProducerWrapper;
import org.freedesktop.gstreamer.lowlevel.MainLoop;

public class WriteToKafka {

    /**
     * @param args the command line arguments
     */

    private static Pipeline pipe;
    private static KafkaProducerWrapper kafkaProducer = new KafkaProducerWrapper();

    public static void main(String[] args) throws Exception {

        kafkaProducer.initialize("ec2-34-217-2-237.us-west-2.compute.amazonaws.com:9093");
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

        AppSink sink = (AppSink)ElementFactory.make("appsink", "video-output");

        // We connect to NEW_SAMPLE and NEW_PREROLL because either can come up
        // as sources of data, although usually just one does.
        sink.set("emit-signals", true);
        // sync=false lets us run the pipeline faster than real (based on the file)
        // time
        sink.set("sync", false);
        sink.set("max-buffers", 2000);
        sink.connect(new AppSink.NEW_SAMPLE() {
            @Override
            public FlowReturn newSample(AppSink elem) {
                Sample sample = elem.pullSample();
                ByteBuffer bytes = sample.getBuffer().map(false);
                try {
                    //output.write(bytes);
                    while(bytes.hasRemaining()) {
                        int copy = Math.min(bytes.remaining(), 4096);
                        byte[] buf = new byte[copy];
                        bytes.get(buf);
                        kafkaProducer.send("live-stream", buf);
                        System.out.println("Writing " + copy + " to cluster");
                    }
                } catch (Exception e) {
                    System.err.println(e);
                }
                sample.dispose();
                return FlowReturn.OK;
            }
        });

        sink.connect(new AppSink.NEW_PREROLL() {

            @Override
            public FlowReturn newPreroll(AppSink elem) {
                Sample sample = elem.pullPreroll();
                ByteBuffer bytes = sample.getBuffer().map(false);
                try {
                    while(bytes.hasRemaining()) {
                        int copy = Math.min(bytes.remaining(), 2000);
                        byte[] buf = new byte[copy];
                        bytes.get(buf);
                        kafkaProducer.send("live-stream", buf);
                        System.out.println("Writing " + copy + " to cluster");
                    }
                } catch (Exception e) {
                    System.err.println(e);
                }
                sample.dispose();
                return FlowReturn.OK;
            }
        });


        /*
        Bin bin = Gst.parseBinFromDescription(
            "testaudiosrc ! videoconvert ! capsfilter caps=video/x-raw,width=640,height=480",
            true);
         */

        Bin bin = Gst.parseBinFromDescription(
            "autovideosrc ! capsfilter caps=video/x-raw,width=640,height=480 ! x264enc ",
            true);

        pipe.addMany(bin, sink);
        Pipeline.linkMany(bin, sink);

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

