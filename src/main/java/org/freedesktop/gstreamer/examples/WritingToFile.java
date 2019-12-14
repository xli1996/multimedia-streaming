package org.freedesktop.gstreamer.examples;

import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import org.freedesktop.gstreamer.Bin;
import org.freedesktop.gstreamer.Bus;
import org.freedesktop.gstreamer.ElementFactory;
import org.freedesktop.gstreamer.FlowReturn;
import org.freedesktop.gstreamer.Gst;
import org.freedesktop.gstreamer.GstObject;
import org.freedesktop.gstreamer.Pipeline;
import org.freedesktop.gstreamer.Sample;
import org.freedesktop.gstreamer.elements.AppSink;
import org.freedesktop.gstreamer.lowlevel.MainLoop;

public class WritingToFile {

    /**
     * @param args the command line arguments
     */

    private static Pipeline pipe;

    public static void main(String[] args) throws Exception {

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

        String outputFileLocation = "output.x263";
        FileChannel output = new FileOutputStream(outputFileLocation).getChannel();
        AppSink sink = (AppSink)ElementFactory.make("appsink", "video-output");

        // We connect to NEW_SAMPLE and NEW_PREROLL because either can come up
        // as sources of data, although usually just one does.
        sink.set("emit-signals", true);
        // sync=false lets us run the pipeline faster than real (based on the file)
        // time
        sink.set("sync", false);
        sink.connect(new AppSink.NEW_SAMPLE() {
            @Override
            public FlowReturn newSample(AppSink elem) {
                Sample sample = elem.pullSample();
                ByteBuffer bytes = sample.getBuffer().map(false);
                try {
                    output.write(bytes);
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
                    output.write(bytes);
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
            "autovideosrc ! capsfilter caps=video/x-raw,width=640,height=480! x264enc ",
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

        output.close();
    }

}

