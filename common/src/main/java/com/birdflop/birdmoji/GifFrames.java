package com.birdflop.birdmoji;

import org.w3c.dom.Node;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageInputStream;
import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Decodes GIFs into fully composited frames, since ImageIO only returns each frame's raw patch.
 */
final class GifFrames {

    record Frame(BufferedImage image, int delayCentiseconds) {}

    private GifFrames() {}

    static List<Frame> read(byte[] data) throws IOException {
        ImageReader reader = ImageIO.getImageReadersByFormatName("gif").next();
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(data))) {
            reader.setInput(input, false);
            int count = reader.getNumImages(true);
            if (count == 0) throw new IOException("GIF has no frames");

            int width = reader.getWidth(0);
            int height = reader.getHeight(0);
            IIOMetadata streamMetadata = reader.getStreamMetadata();
            if (streamMetadata != null) {
                Node screen = child(streamMetadata.getAsTree("javax_imageio_gif_stream_1.0"), "LogicalScreenDescriptor");
                if (screen != null) {
                    width = Math.max(width, intAttribute(screen, "logicalScreenWidth", width));
                    height = Math.max(height, intAttribute(screen, "logicalScreenHeight", height));
                }
            }

            BufferedImage canvas = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            List<Frame> frames = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                BufferedImage patch = reader.read(i);
                Node metadata = reader.getImageMetadata(i).getAsTree("javax_imageio_gif_image_1.0");
                Node descriptor = child(metadata, "ImageDescriptor");
                Node control = child(metadata, "GraphicControlExtension");
                int left = descriptor == null ? 0 : intAttribute(descriptor, "imageLeftPosition", 0);
                int top = descriptor == null ? 0 : intAttribute(descriptor, "imageTopPosition", 0);
                String disposal = control == null ? "none" : ((IIOMetadataNode) control).getAttribute("disposalMethod");
                int delay = control == null ? 10 : intAttribute(control, "delayTime", 10);

                BufferedImage previous = disposal.equals("restoreToPrevious") ? copy(canvas) : null;
                Graphics2D graphics = canvas.createGraphics();
                graphics.drawImage(patch, left, top, null);
                frames.add(new Frame(copy(canvas), delay));

                if (disposal.equals("restoreToBackgroundColor")) {
                    graphics.setComposite(AlphaComposite.Clear);
                    graphics.fillRect(left, top, patch.getWidth(), patch.getHeight());
                } else if (previous != null) {
                    canvas = previous;
                }
                graphics.dispose();
            }
            return frames;
        } finally {
            reader.dispose();
        }
    }

    private static BufferedImage copy(BufferedImage image) {
        BufferedImage copy = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = copy.createGraphics();
        graphics.drawImage(image, 0, 0, null);
        graphics.dispose();
        return copy;
    }

    private static Node child(Node node, String name) {
        for (Node child = node.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNodeName().equals(name)) return child;
        }
        return null;
    }

    private static int intAttribute(Node node, String name, int fallback) {
        try {
            return Integer.parseInt(((IIOMetadataNode) node).getAttribute(name));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
