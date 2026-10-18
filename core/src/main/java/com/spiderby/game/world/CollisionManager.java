package com.spiderby.game.world;

import com.badlogic.gdx.maps.MapLayer;
import com.badlogic.gdx.maps.MapObject;
import com.badlogic.gdx.maps.objects.PolygonMapObject;
import com.badlogic.gdx.maps.objects.PolylineMapObject;
import com.badlogic.gdx.maps.objects.RectangleMapObject;
import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.math.Intersector;
import com.badlogic.gdx.math.Polygon;
import com.badlogic.gdx.math.Polyline;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Array;

/**
 * Loads ALL THREE Tiled collision types: rectangles, polygons, and polylines.
 * TMX coordinates are already flipped to the correct LibGDX coordinate system.
 */
public class CollisionManager {

    // A thin border around drawn polylines so the player's feet cannot cross them.
    private static final float LINE_PADDING = 3f;

    private final Array<Rectangle> collisionRectangles = new Array<>();
    private final Array<Rectangle> waterRectangles = new Array<>();
    private final Array<Polygon> collisionPolygons = new Array<>();
    private final Array<Polygon> waterPolygons = new Array<>();
    private final Array<Polyline> collisionLines = new Array<>();
    private final Array<Polyline> waterLines = new Array<>();

    private final Rectangle feet = new Rectangle();
    private final Rectangle paddedFeet = new Rectangle();

    public CollisionManager(TiledMap tiledMap) {
        loadLayer(tiledMap, "Collision", collisionRectangles, collisionPolygons, collisionLines);
        loadLayer(tiledMap, "Water", waterRectangles, waterPolygons, waterLines);
    }

    private void loadLayer(TiledMap tiledMap, String name,
                           Array<Rectangle> rectangles,
                           Array<Polygon> polygons,
                           Array<Polyline> lines) {
        MapLayer layer = tiledMap.getLayers().get(name);
        if (layer == null) return;  // Water may not exist in early prototypes.

        for (MapObject object : layer.getObjects()) {
            if (!object.isVisible()) continue;

            if (object instanceof RectangleMapObject) {
                Rectangle r = ((RectangleMapObject) object).getRectangle();
                // Ignore stray point-sized/zero-area objects from Tiled.
                if (r.width > 1f && r.height > 1f) rectangles.add(new Rectangle(r));
            } else if (object instanceof PolygonMapObject) {
                polygons.add(((PolygonMapObject) object).getPolygon());
            } else if (object instanceof PolylineMapObject) {
                lines.add(((PolylineMapObject) object).getPolyline());
            }
        }
    }

    private void setFeet(float x, float y, float width, float height) {
        // Only the player's feet should hit walls, not their head and shoulders.
        float fw = width * 0.30f;
        float fh = height * 0.10f;
        feet.set(x + (width - fw) / 2f,
                 y + height * 0.04f,
                 fw, fh);
    }

    public boolean isColliding(float x, float y, float width, float height) {
        setFeet(x, y, width, height);
        return hits(collisionRectangles, collisionPolygons, collisionLines)
            || hits(waterRectangles, waterPolygons, waterLines);
    }

    private boolean hits(Array<Rectangle> rects,
                         Array<Polygon> polygons,
                         Array<Polyline> lines) {
        for (Rectangle r : rects) {
            if (feet.overlaps(r)) return true;
        }
        for (Polygon polygon : polygons) {
            if (touchesPolygon(polygon)) return true;
        }
        // Polyline means a WALL/SHORE BORDER, not a filled region.
        paddedFeet.set(feet.x - LINE_PADDING, feet.y - LINE_PADDING,
                       feet.width + 2f * LINE_PADDING,
                       feet.height + 2f * LINE_PADDING);
        for (Polyline line : lines) {
            float[] points = line.getTransformedVertices();
            for (int i = 0; i + 3 < points.length; i += 2) {
                if (Intersector.intersectSegmentRectangle(
                        points[i], points[i + 1],
                        points[i + 2], points[i + 3], paddedFeet)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean touchesPolygon(Polygon polygon) {
        float[] points = polygon.getTransformedVertices();
        if (points.length < 6) return false;

        // A corner of the feet rectangle is inside the polygon.
        float left = feet.x;
        float right = feet.x + feet.width;
        float bottom = feet.y;
        float top = feet.y + feet.height;
        if (polygon.contains(left, bottom) || polygon.contains(right, bottom)
                || polygon.contains(left, top) || polygon.contains(right, top)) return true;

        // A polygon vertex is inside the feet rectangle.
        for (int i = 0; i < points.length; i += 2) {
            if (feet.contains(points[i], points[i + 1])) return true;
        }

        // Edges can cross even when no corner lies inside the other shape.
        for (int i = 0; i < points.length; i += 2) {
            int next = (i + 2) % points.length;
            if (Intersector.intersectSegmentRectangle(
                    points[i], points[i + 1],
                    points[next], points[next + 1], feet)) return true;
        }
        return false;
    }

    /** Makes the current player's feet visible in F3 debugging. */
    public Rectangle getPlayerHitbox(float x, float y, float width, float height) {
        setFeet(x, y, width, height);
        return feet;
    }

    public Array<Rectangle> getCollisionRectangles() { return collisionRectangles; }
    public Array<Rectangle> getWaterRectangles() { return waterRectangles; }
    public Array<Polygon> getCollisionPolygons() { return collisionPolygons; }
    public Array<Polygon> getWaterPolygons() { return waterPolygons; }
    public Array<Polyline> getCollisionLines() { return collisionLines; }
    public Array<Polyline> getWaterLines() { return waterLines; }
}
