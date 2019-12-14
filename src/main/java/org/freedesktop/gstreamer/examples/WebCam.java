package org.freedesktop.gstreamer.examples;

import java.awt.Dimension;
import javax.swing.JFrame;
import org.freedesktop.gstreamer.Bin;
import org.freedesktop.gstreamer.Bus;
import org.freedesktop.gstreamer.Gst;
import org.freedesktop.gstreamer.GstObject;
import org.freedesktop.gstreamer.Pipeline;
import org.freedesktop.gstreamer.elements.AppSink;
import org.freedesktop.gstreamer.lowlevel.MainLoop;

public class WebCam {

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

        SimpleVideoComponent vc = new SimpleVideoComponent();
        AppSink sink = (AppSink)vc.getElement(); //(AppSink)ElementFactory.make("appsink", "video-output");
        JFrame window = new JFrame("Video Player");
        window.add(vc);
        vc.setPreferredSize(new Dimension(800, 600));
        window.pack();
        window.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        window.setVisible(true);


        /*
        Bin bin = Gst.parseBinFromDescription(
            "testaudiosrc ! videoconvert ! capsfilter caps=video/x-raw,width=640,height=480",
            true);
         */

        Bin bin = Gst.parseBinFromDescription(
            "autovideosrc ! videoconvert",
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

