/**
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 * 
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 */

package org.jxmapviewer.painter;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Stroke;
import java.awt.geom.Point2D;
import org.jxmapviewer.JXMapViewer;
import org.jxmapviewer.viewer.GeoPosition;
import org.jxmapviewer.viewer.Tile;
import org.jxmapviewer.viewer.TileFactory;
import org.jxmapviewer.viewer.TileFactoryInfo;
import org.jxmapviewer.viewer.TileListener;

/**
 * Painter for a tile based layer.
 *
 * <p>
 * Bound properties:</p>
 * <dl>
 *  <dt>tilefactory</dt>
 *  <dd>Tilefactory used for drawing this layer</dd>
 *  <dt>opacity</dt>
 *  <dd>Opacity of the rendered tiles.</dd>
 *  <dt>drawTileBorders</dt>
 *  <dd>If enabled borders are painted around the files and tilecoordinates + 
 *      zoom are displayed. Useful as a debugging tool.</dd>
 * </dl>
 */
public class TilePainter extends AbstractPainter<JXMapViewer> {

    private TileFactory tilefactory;
    private boolean drawTileBorders;
    private float opacity;

    public TilePainter(TileFactory tf, float opacity) {
        super(false);
        this.tilefactory = tf;
        this.opacity = opacity;
        tf.addTileListener(new TileListener() {

            @Override
            public void tileLoaded(Tile tile) {
                TilePainter.this.setDirty(true);
            }
        });
    }

    public void setOpacity(float opacity) {
        float oldValue = this.opacity;
        if (opacity == oldValue) {
            return;
        }
        this.opacity = opacity;
        firePropertyChange("opacity", oldValue, this.opacity);
    }

    public float getOpacity() {
        return opacity;
    }

    public TileFactory getTilefactory() {
        return tilefactory;
    }

    public void setTilefactory(TileFactory tilefactory) {
        TileFactory oldValue = this.tilefactory;
        if (tilefactory == oldValue) {
            return;
        }
        this.tilefactory = tilefactory;
        firePropertyChange("tilefactory", oldValue, this.tilefactory);
        setDirty(true);
    }

    public boolean isDrawTileBorders() {
        return drawTileBorders;
    }

    public void setDrawTileBorders(boolean drawTileBorders) {
        boolean oldValue = this.drawTileBorders;
        if (oldValue == drawTileBorders) {
            return;
        }
        this.drawTileBorders = drawTileBorders;
        firePropertyChange("drawTileBorders", oldValue, this.tilefactory);
        setDirty(true);
    }

    @Override
    protected void doPaint(Graphics2D g, JXMapViewer object, int width, int height) {
        g = (Graphics2D) g.create();
        if(opacity < 1) {
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, opacity));
        }

        GeoPosition upperLeftGeo = object.convertPointToGeoPosition(new Point2D.Double(0, 0));
        GeoPosition lowerRightGeo = object.convertPointToGeoPosition(new Point2D.Double(width, height));

        Rectangle viewportBounds = object.getViewportBounds();

        double targetDegreeWidth
                = Math.abs(viewportBounds.width)
                / Math.abs(lowerRightGeo.getLongitude()
                        - upperLeftGeo.getLongitude());

        TileFactoryInfo info = tilefactory.getInfo();

        int zoom = info.getMinimumZoomLevel();
        double diff = targetDegreeWidth
                - info.getLongitudeDegreeWidthInPixels(zoom);

        for (int i = zoom + 1; i <= info.getMaximumZoomLevel(); i++) {
            double diffCurrent = targetDegreeWidth
                    - info.getLongitudeDegreeWidthInPixels(i);

            if (diffCurrent > diff && diffCurrent > 0) {
                // Situation got worse -- and diff is already closest matching zoom factor
                break;
            } else if (diffCurrent < diff && diffCurrent < 0) {
                // Got best zoom
                zoom = i;
                diff = diffCurrent;
                break;
            }
            zoom = i;
            diff = diffCurrent;
        }

        // ------------------------
        double scale = targetDegreeWidth
                / info.getLongitudeDegreeWidthInPixels(zoom);

        g.scale(scale, scale);

        int size = tilefactory.getTileSize(zoom);

        Point2D offset = tilefactory.geoToPixel(upperLeftGeo, zoom);
        Point2D pGeo2 = tilefactory.geoToPixel(lowerRightGeo, zoom);

        int pX = (int) offset.getX();
        int pY = (int) offset.getY();
        int pWidth = (int) (pGeo2.getX() - offset.getX());
        int pHeight = (int) (pGeo2.getY() - offset.getY());

        // calculate the "visible" viewport area in tiles
        int numWide = (int) Math.ceil((((double)pWidth) / size) / scale) + 1;
        int numHigh = (int) Math.ceil((((double)pHeight) / size) / scale) + 1;

        int tpx = (int) Math.floor(((double) pX) / info.getTileSize(zoom));
        int tpy = (int) Math.floor(((double) pY) / info.getTileSize(zoom));

        for (int x = 0; x < numWide; x++) {
            for (int y = 0; y < numHigh; y++) {
                int itpx = x + tpx;// topLeftTile.getX();
                int itpy = y + tpy;// topLeftTile.getY();
                Tile tile = tilefactory.getTile(itpx, itpy, zoom);
                int ox = ((itpx * size) - pX);
                int oy = ((itpy * size) - pY);

                Rectangle paintRect = new Rectangle(ox, oy, size, size);
                
                if(! g.getClipBounds().intersects(paintRect)) {
                    continue;
                }
                
                if (tile.isLoaded()) {
                    g.drawImage(tile.getImage(), ox, oy, null);
                }
                if (isDrawTileBorders()) {
                    Stroke s2 = new BasicStroke(1.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
                    g.setStroke(s2);

                    g.setColor(Color.black);
                    g.drawRect(ox, oy, size, size);
                    g.drawRect(ox + size / 2 - 5, oy + size / 2 - 5, 10, 10);
                    g.setColor(Color.white);
                    g.drawRect(ox + 1, oy + 1, size, size);

                    String text = itpx + ", " + itpy + ", " + zoom;
                    g.setColor(Color.BLACK);
                    g.drawString(text, ox + 10, oy + 30);
                    g.drawString(text, ox + 10 + 2, oy + 30 + 2);
                    g.setColor(Color.WHITE);
                    g.drawString(text, ox + 10 + 1, oy + 30 + 1);
                }
            }
        }
    }
}
