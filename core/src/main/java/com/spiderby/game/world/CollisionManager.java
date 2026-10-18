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
 * Loads collisions directly from the TMX.
 *
 * Tree canopies are NOT hard-coded here. If a canopy has no collision object
 * in barangay.tmx, the player can walk underneath it.
 */
public class CollisionManager {

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

    private void loadLayer(
            TiledMap tiledMap,
            String name,
            Array<Rectangle> rectangles,
            Array<Polygon> polygons,
            Array<Polyline> lines) {

        MapLayer layer = tiledMap.getLayers().get(name);
        if (layer == null) {
            return;
        }

        for (MapObject object : layer.getObjects()) {
            if (!object.isVisible()) {
                continue;
            }

            if (object instanceof RectangleMapObject) {
                Rectangle rectangle = ((RectangleMapObject) object).getRectangle();

                // Ignore accidental point-sized Tiled objects.
                if (rectangle.width > 1f && rectangle.height > 1f) {
                    rectangles.add(new Rectangle(rectangle));
                }
            } else if (object instanceof PolygonMapObject) {
                polygons.add(((PolygonMapObject) object).getPolygon());
            } else if (object instanceof PolylineMapObject) {
                lines.add(((PolylineMapObject) object).getPolyline());
            }
        }
    }

    /** Small hitbox at the character's feet only. */
    private void setFeet(float x, float y, float width, float height) {
        float feetWidth = width * 0.30f;
        float feetHeight = height * 0.10f;

        feet.set(
                x + (width - feetWidth) / 2f,
                y + height * 0.04f,
                feetWidth,
                feetHeight
        );
    }

    public boolean isColliding(float x, float y, float width, float height) {
        setFeet(x, y, width, height);

        return hits(collisionRectangles, collisionPolygons, collisionLines)
                || hits(waterRectangles, waterPolygons, waterLines);
    }

    private boolean hits(
            Array<Rectangle> rectangles,
            Array<Polygon> polygons,
            Array<Polyline> lines) {

        for (Rectangle rectangle : rectangles) {
            if (feet.overlaps(rectangle)) {
                return true;
            }
        }

        for (Polygon polygon : polygons) {
            if (touchesPolygon(polygon)) {
                return true;
            }
        }

        paddedFeet.set(
                feet.x - LINE_PADDING,
                feet.y - LINE_PADDING,
                feet.width + LINE_PADDING * 2f,
                feet.height + LINE_PADDING * 2f
        );

        for (Polyline line : lines) {
            float[] points = line.getTransformedVertices();

            for (int i = 0; i + 3 < points.length; i += 2) {
                if (Intersector.intersectSegmentRectangle(
                        points[i], points[i + 1],
                        points[i + 2], points[i + 3],
                        paddedFeet)) {
                    return true;
                }
            }
        }

        return false;
    }

    private boolean touchesPolygon(Polygon polygon) {
        float[] points = polygon.getTransformedVertices();
        if (points.length < 6) {
            return false;
        }

        float left = feet.x;
        float right = feet.x + feet.width;
        float bottom = feet.y;
        float top = feet.y + feet.height;

        // Player-feet corner inside polygon.
        if (polygon.contains(left, bottom)
                || polygon.contains(right, bottom)
                || polygon.contains(left, top)
                || polygon.contains(right, top)) {
            return true;
        }

        // Polygon point inside the feet rectangle.
        for (int i = 0; i < points.length; i += 2) {
            if (feet.contains(points[i], points[i + 1])) {
                return true;
            }
        }

        // Polygon edge crossing the feet rectangle.
        for (int i = 0; i < points.length; i += 2) {
            int next = (i + 2) % points.length;

            if (Intersector.intersectSegmentRectangle(
                    points[i], points[i + 1],
                    points[next], points[next + 1],
                    feet)) {
                return true;
            }
        }

        return false;
    }

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
