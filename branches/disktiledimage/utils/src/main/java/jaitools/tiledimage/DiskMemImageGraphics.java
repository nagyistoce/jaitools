/*
 * Copyright 2009 Michael Bedward
 *
 * This file is part of jai-tools.
 *
 * jai-tools is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * jai-tools is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with jai-tools.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package jaitools.tiledimage;

import java.awt.Color;
import java.awt.Composite;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.Image;
import java.awt.Paint;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.RenderingHints.Key;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.BufferedImageOp;
import java.awt.image.ColorModel;
import java.awt.image.ImageObserver;
import java.awt.image.Raster;
import java.awt.image.RenderedImage;
import java.awt.image.SampleModel;
import java.awt.image.WritableRaster;
import java.awt.image.renderable.RenderableImage;
import java.lang.reflect.Method;
import java.text.AttributedCharacterIterator;
import java.util.Hashtable;
import java.util.Map;
import javax.media.jai.PlanarImage;

/**
 * A Graphics class for drawing into a <code>DiskMemImage</code>.
 * As with JAI's <code>TiledImageGraphics</code> class, java.awt
 * routines do the real work and the purpose of this class is to
 * serve the image data in a form that those routines can handle.
 *
 * @todo It would have been a lot easier to be able to use JAI's
 * TiledImageGraphics class directly but, for some reason, they've
 * made it package private.
 *
 * @author Michael Bedward
 * @since 1.0
 * @version $Id$
 */
public class DiskMemImageGraphics extends Graphics2D {

    private DiskMemImage targetImage;
    private ColorModel   colorModel;
    private Hashtable<String, Object> properties;
    private RenderingHints renderingHints;

    public static enum PaintMode {
        PAINT,
        XOR;
    };
    
    /*
     * java.awt.Graphics parameters
     */
    private Point origin;
    private Shape clip;
    private Color color;
    private Font  font;
    private PaintMode paintMode;
    private Color XORColor;

    /*
     * java.awt.Graphics2D parameters
     */
    private Color background;
    private Composite composite;
    private Paint paint;
    private Stroke stroke;
    private AffineTransform transform;

    public static enum OpType {

        CLEAR_RECT("clearRect", int.class, int.class, int.class, int.class),

        COPY_AREA("copyArea", int.class, int.class, int.class, int.class, int.class, int.class),
        
        DRAW_ARC("drawArc", int.class, int.class, int.class, int.class, int.class, int.class),

        DRAW_BUFFERED_IMAGE("drawImage", BufferedImage.class, BufferedImageOp.class, int.class, int.class),

        DRAW_IMAGE("drawImage", Image.class, AffineTransform.class, ImageObserver.class),

        DRAW_LINE("drawLine", int.class, int.class, int.class, int.class),

        DRAW_OVAL("drawOval", int.class, int.class, int.class, int.class),

        DRAW_ROUND_RECT("drawRoundRect", int.class, int.class, int.class, int.class, int.class, int.class),

        DRAW_SHAPE("draw", Shape.class),

        DRAW_STRING_XY("drawString", String.class, float.class, float.class),

        DRAW_STRING_ITER_XY("drawString", AttributedCharacterIterator.class, float.class, float.class),

        FILL("fill", Shape.class),

        FILL_ARC("fillArc", int.class, int.class, int.class, int.class, int.class, int.class),

        FILL_OVAL("fillOval", int.class, int.class, int.class, int.class),

        FILL_RECT("fillRect", int.class, int.class, int.class, int.class),

        FILL_ROUND_RECT("fillRoundRect", int.class, int.class, int.class, int.class, int.class, int.class);


        private String methodName;
        private Class<?>[] paramTypes;

        private OpType(String methodName, Class<?> ...types) {
            this.methodName = methodName;
            this.paramTypes = new Class<?>[types.length];
            for (int i = 0; i < types.length; i++) {
                this.paramTypes[i] = types[i];
            }
        }

        public String getMethodName() {
            return methodName;
        }

        public String getFullMethodName() {
            StringBuffer sb = new StringBuffer();
            sb.append(methodName);
            sb.append("(");
            if (paramTypes.length > 0) {
                sb.append(paramTypes[0].getSimpleName());
                for (int i = 1; i < paramTypes.length; i++) {
                    sb.append(", ");
                    sb.append(paramTypes[i].getSimpleName());
                }
            }
            sb.append(")");
            return sb.toString();
        }

        public int getNumParams() {
            return paramTypes.length;
        }

        public Class<?>[] getParamTypes() {
            return paramTypes;
        }
    }

    /**
     * Constructor: create an instance of this class for the given target image
     *
     * @param targetImage the image to be drawn into
     */
    DiskMemImageGraphics(DiskMemImage targetImage) {
        this.targetImage = targetImage;
        setColorModel();
        setProperties();
        setGraphicsParams();
    }

    @Override
    public void draw(Shape s) {
        doDraw(OpType.DRAW_SHAPE, s.getBounds2D(), s);
    }

    @Override
    public boolean drawImage(Image img, AffineTransform xform, ImageObserver obs) {
        Rectangle bounds = new Rectangle(0, 0, img.getWidth(obs), img.getHeight(obs));
        Rectangle2D xformBounds = xform.createTransformedShape(bounds).getBounds2D();

        return doDraw(OpType.DRAW_IMAGE, xformBounds, img, xform, obs);
    }

    @Override
    public void drawImage(BufferedImage img, BufferedImageOp op, int x, int y) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public void drawRenderedImage(RenderedImage img, AffineTransform xform) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public void drawRenderableImage(RenderableImage img, AffineTransform xform) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public void drawString(String str, int x, int y) {
        drawString( str, (float)x, (float)y );
    }

    @Override
    public void drawString(String str, float x, float y) {
        Rectangle2D bounds = getFontMetrics().getStringBounds(str, this);
        bounds.setRect(x,
                       y - bounds.getHeight() + 1,
                       bounds.getWidth(),
                       bounds.getHeight() );

        doDraw(OpType.DRAW_STRING_XY, bounds, str, x, y);
    }

    @Override
    public void drawString(AttributedCharacterIterator iter, int x, int y) {
        drawString( iter, (float)x, (float)y );
    }

    @Override
    public void drawString(AttributedCharacterIterator iter, float x, float y) {
        Rectangle2D bounds = getFontMetrics().getStringBounds(
                iter, iter.getBeginIndex(), iter.getEndIndex(), this);

        bounds.setRect(x,
                       y - bounds.getHeight() + 1,
                       bounds.getWidth(),
                       bounds.getHeight() );

        doDraw(OpType.DRAW_STRING_ITER_XY, bounds, iter, x, y);
    }

    @Override
    public void drawGlyphVector(GlyphVector g, float x, float y) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public void fill(Shape s) {
        doDraw(OpType.FILL, s.getBounds2D(), s);
    }

    @Override
    public boolean hit(Rectangle rect, Shape s, boolean onStroke) {
        Graphics2D gr = getProxy();
        copyGraphicsParams(gr);
        boolean rtnVal = gr.hit(rect, s, onStroke);
        gr.dispose();
        return rtnVal;
    }

    @Override
    public GraphicsConfiguration getDeviceConfiguration() {
        Graphics2D gr = getProxy();
        copyGraphicsParams(gr);
        GraphicsConfiguration gc = gr.getDeviceConfiguration();
        gr.dispose();
        return gc;
    }

    @Override
    public void setComposite(Composite comp) {
        composite = comp;
    }

    @Override
    public void setPaint(Paint p) {
        paint = p;
    }

    @Override
    public void setStroke(Stroke s) {
        stroke = s;
    }

    @Override
    public void setRenderingHint(Key hintKey, Object hintValue) {
        renderingHints.clear();
        renderingHints.put(hintKey, hintValue);
    }

    @Override
    public Object getRenderingHint(Key hintKey) {
        return renderingHints.get(hintKey);
    }

    @Override
    public void setRenderingHints(Map<?, ?> hints) {
        renderingHints.putAll(hints);
    }

    @Override
    public void addRenderingHints(Map<?, ?> hints) {
        renderingHints.putAll(hints);
    }

    @Override
    public RenderingHints getRenderingHints() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public void translate(int x, int y) {
        origin.setLocation(x, y);
        transform.translate( (double)x, (double)y );
    }

    @Override
    public void translate(double tx, double ty) {
        transform.translate(tx, ty);
    }

    @Override
    public void rotate(double theta) {
        transform.rotate(theta);
    }

    @Override
    public void rotate(double theta, double x, double y) {
        transform.rotate(theta, x, y);
    }

    @Override
    public void scale(double sx, double sy) {
        transform.scale(sx, sy);
    }

    @Override
    public void shear(double shx, double shy) {
        transform.shear(shx, shy);
    }

    @Override
    public void transform(AffineTransform Tx) {
        transform.concatenate(Tx);
    }

    @Override
    public void setTransform(AffineTransform Tx) {
        transform = Tx;
    }

    @Override
    public AffineTransform getTransform() {
        return transform;
    }

    @Override
    public Paint getPaint() {
        return paint;
    }

    @Override
    public Composite getComposite() {
        return composite;
    }

    @Override
    public void setBackground(Color color) {
        background = color;
    }

    @Override
    public Color getBackground() {
        return background;
    }

    @Override
    public Stroke getStroke() {
        return stroke;
    }

    @Override
    public void clip(Shape s) {
        if(clip == null) {
            clip = s;
        } else {
            Area clipArea = (clip instanceof Area ? (Area)clip : new Area(clip));
            clipArea.intersect(s instanceof Area ? (Area)s : new Area(s));
            clip = clipArea;
        }
    }

    @Override
    public FontRenderContext getFontRenderContext() {
        Graphics2D gr = getProxy();
        copyGraphicsParams(gr);
        FontRenderContext frc = gr.getFontRenderContext();
        gr.dispose();
        return frc;
    }

    /**
     * Returns a copy of this object with its current settings
     * @return a new instance of this class
     */
    @Override
    public Graphics create() {
        DiskMemImageGraphics gr = new DiskMemImageGraphics(targetImage);
        copyGraphicsParams(gr);
        return gr;
    }

    @Override
    public Color getColor() {
        return color;
    }

    @Override
    public void setColor(Color c) {
        color = c;
    }

    @Override
    public void setPaintMode() {
        paintMode = PaintMode.PAINT;
    }

    @Override
    public void setXORMode(Color color) {
        paintMode = PaintMode.XOR;
        XORColor = color;
    }

    @Override
    public Font getFont() {
        return font;
    }

    @Override
    public void setFont(Font font) {
        this.font = font;
    }

    @Override
    public FontMetrics getFontMetrics(Font f) {
        Graphics2D gr = getProxy();
        copyGraphicsParams(gr);
        FontMetrics fm = gr.getFontMetrics(f);
        gr.dispose();
        return fm;
    }

    @Override
    public Rectangle getClipBounds() {
        return clip.getBounds();
    }

    @Override
    public void clipRect(int x, int y, int width, int height) {
        clip(new Rectangle(x, y, width, height));
    }

    @Override
    public void setClip(int x, int y, int width, int height) {
        setClip(new Rectangle(x, y, width, height));
    }

    @Override
    public Shape getClip() {
        return clip;
    }

    @Override
    public void setClip(Shape clip) {
        this.clip = clip;
    }

    @Override
    public void copyArea(int x, int y, int width, int height, int dx, int dy) {
        doDraw(OpType.COPY_AREA,
               new Rectangle(x + dx, y + dy, width, height),
               x, y, width, height, dx, dy);
    }

    @Override
    public void drawLine(int x1, int y1, int x2, int y2) {
        Rectangle2D bounds = new Rectangle();
        bounds.setFrameFromDiagonal(x1, y1, x2, y2);
        doDraw(OpType.DRAW_LINE, bounds, x1, y1, x2, y2);
    }

    @Override
    public void fillRect(int x, int y, int width, int height) {
        doDraw(OpType.FILL_RECT, new Rectangle(x, y, width, height), x, y, width, height);
    }

    @Override
    public void clearRect(int x, int y, int width, int height) {
        doDraw(OpType.CLEAR_RECT, new Rectangle(x, y, width, height), x, y, width, height);
    }

    @Override
    public void drawRoundRect(int x, int y, int width, int height, int arcWidth, int arcHeight) {
        Rectangle2D bounds = new Rectangle(
                x - arcWidth, y - arcHeight,
                width + 2 * arcWidth, height + 2 * arcHeight);

        doDraw(OpType.DRAW_ROUND_RECT, bounds, x, y, width, height, arcWidth, arcHeight);
    }

    @Override
    public void fillRoundRect(int x, int y, int width, int height, int arcWidth, int arcHeight) {
        Rectangle2D bounds = new Rectangle(
                x - arcWidth, y - arcHeight,
                width + 2 * arcWidth, height + 2 * arcHeight);

        doDraw(OpType.FILL_ROUND_RECT, bounds, x, y, width, height, arcWidth, arcHeight);
    }

    @Override
    public void drawOval(int x, int y, int width, int height) {
        Rectangle2D bounds = new Rectangle(x, y, width, height);
        doDraw(OpType.DRAW_OVAL, bounds, x, y, width, height);
    }

    @Override
    public void fillOval(int x, int y, int width, int height) {
        Rectangle2D bounds = new Rectangle(x, y, width, height);
        doDraw(OpType.FILL_OVAL, bounds, x, y, width, height);
    }

    @Override
    public void drawArc(int x, int y, int width, int height, int startAngle, int arcAngle) {
        Rectangle2D bounds = new Rectangle(x, y, width, height);
        doDraw(OpType.DRAW_ARC, bounds, x, y, width, height, startAngle, arcAngle);
    }

    @Override
    public void fillArc(int x, int y, int width, int height, int startAngle, int arcAngle) {
        Rectangle2D bounds = new Rectangle(x, y, width, height);
        doDraw(OpType.FILL_ARC, bounds, x, y, width, height, startAngle, arcAngle);
    }

    @Override
    public void drawPolyline(int[] xPoints, int[] yPoints, int nPoints) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public void drawPolygon(int[] xPoints, int[] yPoints, int nPoints) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public void fillPolygon(int[] xPoints, int[] yPoints, int nPoints) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public boolean drawImage(Image img, int x, int y, ImageObserver observer) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public boolean drawImage(Image img, int x, int y, int width, int height, ImageObserver observer) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public boolean drawImage(Image img, int x, int y, Color bgcolor, ImageObserver observer) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public boolean drawImage(Image img, int x, int y, int width, int height, Color bgcolor, ImageObserver observer) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public boolean drawImage(Image img, int dx1, int dy1, int dx2, int dy2, int sx1, int sy1, int sx2, int sy2, ImageObserver observer) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public boolean drawImage(Image img, int dx1, int dy1, int dx2, int dy2, int sx1, int sy1, int sx2, int sy2, Color bgcolor, ImageObserver observer) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public void dispose() {
        /*
         * No need to do anything here
         */
    }

    /**
     * Perform the graphics operation by partitioning the work across the image's
     * tiles and using Graphics2D routines to draw into each tile.
     *
     * @param opType the type of operation
     * @param bounds bounds of the element to be drawn
     * @param args a variable length list of arguments for the operation
     */
    boolean doDraw(OpType opType, Rectangle2D bounds, Object ...args) {
        Method method = null;
        boolean rtnVal = false;

        try {
            method = Graphics2D.class.getMethod(opType.getMethodName(), opType.getParamTypes());

        } catch (NoSuchMethodException nsmEx) {
            // programmer error :-(
            throw new RuntimeException("No such method: " + opType.getFullMethodName());
        }

        int minTileX = Math.max(targetImage.XToTileX((int)bounds.getMinX()),
                                targetImage.getMinTileX());

        int maxTileX = Math.min(targetImage.XToTileX((int)(bounds.getMaxX() + 0.5)),
                                targetImage.getMaxTileX());

        int minTileY = Math.max(targetImage.YToTileY((int)bounds.getMinY()),
                                targetImage.getMinTileY());

        int maxTileY = Math.min(targetImage.YToTileY((int)(bounds.getMaxY() + 0.5)),
                                targetImage.getMaxTileY());

        for (int tileY = minTileY; tileY <= maxTileY; tileY++) {
            int minY = targetImage.tileYToY(tileY);

            for (int tileX = minTileX; tileX <= maxTileX; tileX++) {
                int minX = targetImage.tileXToX(tileX);

                WritableRaster tile = targetImage.getWritableTile(tileX, tileY);

                // create a live-copy of the tile with the upper-left corner
                // translated to 0,0
                WritableRaster copy = tile.createWritableTranslatedChild(0, 0);

                BufferedImage bufImg = new BufferedImage(
                        colorModel,
                        copy,
                        colorModel.isAlphaPremultiplied(),
                        properties);

                Graphics2D gr = bufImg.createGraphics();
                copyGraphicsParams(gr);

                try {
                    Point2D p2d = gr.getTransform().transform(new Point2D.Double(0, 0), null);
                    Point p = new Point((int)p2d.getX() - minX, (int)p2d.getY() - minY);
                    p2d = gr.getTransform().inverseTransform(p, null);
                    gr.translate(p2d.getX(), p2d.getY());

                } catch(NoninvertibleTransformException nte) {
                    // TODO replace this with decent error handling
                    throw new RuntimeException(nte);
                }

                try {
                    Object oRtnVal = method.invoke(gr, args);
                    if(oRtnVal != null && oRtnVal.getClass() == boolean.class) {
                        rtnVal = ((Boolean)oRtnVal).booleanValue();
                    }

                } catch(Exception ex) {
                    // TODO replace this with decent error handling
                    throw new RuntimeException(ex);
                }

                gr.dispose();
                targetImage.releaseWritableTile(tileX, tileY);
            }
        }

        return rtnVal;
    }

    /**
     * Helper method for the constructor. Attempts to get
     * or construct a <code>ColorModel</code> for the target
     * image.
     *
     * @throws UnsupportedOperationException if a compatible <code>ColorModel</code> is not found
     */
    void setColorModel() {
        assert(targetImage != null);

        colorModel = targetImage.getColorModel();

        if (colorModel == null) {
            SampleModel sm = targetImage.getSampleModel();
            colorModel = PlanarImage.createColorModel(sm);

            if (colorModel == null) {
                // try simple default
                if (ColorModel.getRGBdefault().isCompatibleSampleModel(sm)) {
                    colorModel = ColorModel.getRGBdefault();

                } else {
                    // admit defeat
                    throw new UnsupportedOperationException(
                            "Failed to get or construct a ColorModel for the image");
                }
            }
        }
    }

    /**
     * Helper method for the constructor. Retrieves any properties 
     * set for the target image.
     */
    void setProperties() {
        assert(targetImage != null);

        properties = new Hashtable<String, Object>();
        String[] propertyNames = targetImage.getPropertyNames();
        if (propertyNames != null) {
            for (String name : propertyNames) {
                properties.put(name, targetImage.getProperty(name));
            }
        }

        // TODO: set rendering hints
    }

    /**
     * Helper method for the constructor. Creates a Graphics2D instance
     * based on the target image's first tile and uses its state to set
     * the graphics params of this object
     */
    void setGraphicsParams() {
        assert(targetImage != null);
        assert(colorModel != null);
        assert(properties != null);

        Graphics2D gr = getProxy();

        origin = new Point(0, 0);
        clip = targetImage.getBounds();
        color = gr.getColor();
        font = gr.getFont();

        paintMode = PaintMode.PAINT;
        XORColor = null;

        background = gr.getBackground();
        composite = gr.getComposite();
        paint = null;
        stroke = gr.getStroke();
        transform = gr.getTransform();

        gr.dispose();
    }

    /**
     * Copy the current graphics params into the given <code>Graphics2D</code>
     * object
     *
     * @param gr a Graphics2D object
     */
    void copyGraphicsParams(Graphics2D gr) {
        gr.translate(origin.x, origin.y);
        gr.setClip(clip);
        gr.setColor(getColor());

        if(paintMode == PaintMode.PAINT) {
            gr.setPaintMode();
        } else if (XORColor != null) {
            gr.setXORMode(XORColor);
        }

        gr.setFont(font);

        // java.awt.Graphics2D state
        gr.setBackground(background);
        gr.setComposite(composite);
        if(paint != null) {
            gr.setPaint(paint);
        }
        if (renderingHints != null) {
            gr.setRenderingHints(renderingHints);
        }
        gr.setStroke(stroke);
        gr.setTransform(transform);
    }

    /**
     * Helper method for other methods that need an instantiated
     * Graphics2D object that is 'representative' of the target image.
     * 
     * @return a new Graphics2D instance
     */
    private Graphics2D getProxy() {
        Raster tile = targetImage.getTile(targetImage.getMinTileX(), targetImage.getMinTileY());
        WritableRaster tiny = tile.createCompatibleWritableRaster(1, 1);

        BufferedImage img = new BufferedImage(
                colorModel, tiny, colorModel.isAlphaPremultiplied(), properties);

        return img.createGraphics();
    }
}