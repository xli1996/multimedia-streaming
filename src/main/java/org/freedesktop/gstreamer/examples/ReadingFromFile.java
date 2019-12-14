package org.freedesktop.gstreamer.examples;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import org.freedesktop.gstreamer.Buffer;
import org.freedesktop.gstreamer.Bus;
import org.freedesktop.gstreamer.Element;
import org.freedesktop.gstreamer.ElementFactory;
import org.freedesktop.gstreamer.Gst;
import org.freedesktop.gstreamer.GstObject;
import org.freedesktop.gstreamer.Pad;
import org.freedesktop.gstreamer.PadLinkException;
import org.freedesktop.gstreamer.Pipeline;
import org.freedesktop.gstreamer.elements.AppSrc;
import org.freedesktop.gstreamer.lowlevel.MainLoop;

public class ReadingFromFile {

    /**
     * @param args the command line arguments
     */

    private static Pipeline pipe;
    private static byte[] videoBytes = null;

    public static void main(String[] args) throws Exception {

        String inputFileLocation = "/tmp/output.avi";
        File videoFile = new File(inputFileLocation);
        FileInputStream inStream = null;

        if (videoFile.exists()){
            try {
                System.out.println("Read media file.");
                long fileSize = videoFile.length();
                videoBytes = new byte[(int)fileSize];
                inStream = new FileInputStream(videoFile);
                int byteCount = inStream.read(videoBytes);
                System.out.println("Number of bytes read: " + byteCount);
            } catch (IOException e) {
                e.printStackTrace();
                return;
            }
        }

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

            private final ByteBuffer bb = ByteBuffer.wrap(videoBytes);

            @Override
            public void needData(AppSrc elem, int size) {
                if (bb.hasRemaining()) {
                    System.out.println("needData: size = " + size);
                    byte[] tempBuffer;
                    Buffer buf;
                    int copyLength = (bb.remaining() >= size) ? size : bb.remaining();
                    tempBuffer = new byte[copyLength];
                    buf = new Buffer(copyLength);
                    bb.get(tempBuffer);
                    System.out.println("Temp Buffer remaining bytes: " + bb.remaining());
                    buf.map(true).put(ByteBuffer.wrap(tempBuffer));
                    elem.pushBuffer(buf);
                } else {
                    elem.endOfStream();
                }
            }
        });


//        Bin bin = Gst.parseBinFromDescription(
//            "decodebin ! videoconvert ! autovideosink",
//            true);

        Element decoder = ElementFactory.make("decodebin", "decoder");
        Element converter = ElementFactory.make("videoconvert", "converter");
        // The encoder element determines your output format.
        Element display = ElementFactory.make("autovideosink", "video-output");

        pipe.addMany(source, decoder, converter, display);
        //Pipeline.linkMany(source, decoder, converter, display);
        source.link(decoder);
        decoder.link(converter);
        converter.link(display);

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
    }

}
