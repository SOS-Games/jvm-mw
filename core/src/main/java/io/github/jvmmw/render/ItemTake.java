/*
 * Ported to Java from OpenMW (https://openmw.org)
 * Original work Copyright (C) OpenMW Contributors
 * Java translation Copyright (C) 2026 JVM-MW contributors
 */
package io.github.jvmmw.render;

import com.badlogic.gdx.math.Intersector;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.BoundingBox;
import com.badlogic.gdx.math.collision.Ray;

import io.github.jvmmw.esm.EsmObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * World-item {@code ActionTake} without inventory. Unparents the instance
 * like {@code Objects::removeObject}.
 */
public final class ItemTake {
    public static final class Hit {
        public final Placed item;
        public final float dist;

        Hit(Placed item, float dist) {
            this.item = item;
            this.dist = dist;
        }
    }

    public static final class Placed {
        public final SceneNode node;
        public final String refId;
        public final String rec;
        public final boolean book;
        CellLight light;

        Placed(SceneNode node, String refId, String rec, boolean book) {
            this.node = node;
            this.refId = refId;
            this.rec = rec;
            this.book = book;
        }
    }

    public final List<Placed> items = new ArrayList<>();
    private final Ray ray = new Ray();
    private final BoundingBox box = new BoundingBox();
    private final Vector3 hit = new Vector3();

    public void clear() {
        items.clear();
    }

    public Placed add(SceneNode node, String refId, EsmObject obj) {
        Placed placed = new Placed(node, refId, obj.rec, "BOOK".equals(obj.rec));
        items.add(placed);
        return placed;
    }

    public void bindLight(SceneNode node, CellLight light) {
        for (Placed item : items) {
            if (item.node == node) {
                item.light = light;
                return;
            }
        }
    }

    public int takeCount() {
        int n = 0;
        for (Placed item : items) {
            if (!item.book) {
                n++;
            }
        }
        return n;
    }

    public Hit nearest(Vector3 origin, Vector3 direction) {
        ray.set(origin, direction);
        Hit best = null;
        for (Placed item : items) {
            box.inf();
            item.node.collectAabb(box);
            if (!box.isValid() || !Intersector.intersectRayBounds(ray, box, hit)) {
                continue;
            }
            float dist = origin.dst(hit);
            if (dist <= DoorSwing.MAX_ACTIVATE && (best == null || dist < best.dist)) {
                best = new Hit(item, dist);
            }
        }
        return best;
    }

    public String activate(Hit picked, CellLighting lighting) {
        Placed item = picked.item;
        if (item.book) {
            return "book " + item.refId;
        }
        item.node.removeFromParent();
        if (item.light != null) {
            lighting.lights.remove(item.light);
        }
        Iterator<Placed> it = items.iterator();
        while (it.hasNext()) {
            if (it.next() == item) {
                it.remove();
                break;
            }
        }
        return "take " + item.refId;
    }
}
