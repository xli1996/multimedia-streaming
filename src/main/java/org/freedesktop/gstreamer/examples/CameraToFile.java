package org.freedesktop.gstreamer.examples;

import java.awt.Dimension;
import java.awt.EventQueue;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import javax.swing.JFrame;
import org.freedesktop.gstreamer.Bin;
import org.freedesktop.gstreamer.Buffer;
import org.freedesktop.gstreamer.Bus;
import org.freedesktop.gstreamer.Element;
import org.freedesktop.gstreamer.ElementFactory;
import org.freedesktop.gstreamer.FlowReturn;
import org.freedesktop.gstreamer.Gst;
import org.freedesktop.gstreamer.GstObject;
import org.freedesktop.gstreamer.Pad;
import org.freedesktop.gstreamer.PadLinkException;
import org.freedesktop.gstreamer.Pipeline;
import org.freedesktop.gstreamer.Sample;
import org.freedesktop.gstreamer.elements.AppSink;
import org.freedesktop.gstreamer.elements.AppSrc;
import org.freedesktop.gstreamer.lowlevel.MainLoop;

public class CameraToFile {

    /**
     * @param args the command line arguments
     */

    private static Pipeline pipe;

    public static void main2(String[] args) {

        Gst.init("CameraTest", args);
        EventQueue.invokeLater(new Runnable() {

            @Override
            public void run() {
                SimpleVideoComponent vc = new SimpleVideoComponent();
                Bin bin = Gst.parseBinFromDescription(
                    "autovideosrc ! videoconvert ! capsfilter caps=video/x-raw,width=640,height=480",
                    true);
                pipe = new Pipeline();
                pipe.addMany(bin, vc.getElement());
                Pipeline.linkMany(bin, vc.getElement());

                JFrame f = new JFrame("Camera Test");
                f.add(vc);
                vc.setPreferredSize(new Dimension(640, 480));
                f.pack();
                f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

                pipe.play();
                f.setVisible(true);
            }
        });
    }

    private static byte[] soundBytes = null;

    public static void main(String[] args) throws Exception {
        Gst.init();

        String outputFileLocation = args[0];
        FileChannel output =
            new FileOutputStream(outputFileLocation).getChannel();
        final MainLoop loop = new MainLoop();

        Element source = ElementFactory.make("audiotestsrc", "audio");
        Element decoder = ElementFactory.make("decodebin", "decoder");
        Element converter = ElementFactory.make("audioconvert", "converter");
        // The encoder element determines your output format.
        AppSink sink = (AppSink)ElementFactory.make("appsink", "audio-output");

        Pipeline pipe = new Pipeline();
        // We connect to EOS and ERROR on the bus for cleanup and error messages.
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

        pipe.addMany(source, decoder, converter, sink);
        source.link(decoder);
        decoder.link(sink);
        decoder.connect(new Element.PAD_ADDED() {

            @Override
            public void padAdded(Element element, Pad pad) {
                System.out.println("Dynamic pad created, linking decoder/converter");
                System.out.println("Pad name: " + pad.getName());
                System.out.println("Pad type: " + pad.getTypeName());
                Pad sinkPad = converter.getStaticPad("sink");
                try {
                    pad.link(sinkPad);
                    System.out.println("Pad linked.");
                } catch (PadLinkException ex) {
                    System.out.println("Pad link failed : " + ex.getLinkResult());
                }
            }

        });

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

