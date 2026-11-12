package com.nafahat.key;

import java.util.ArrayList;
import java.util.List;

/** A keyboard page: rows of keys described as data. */
public final class Layout {
    public static final class Row {
        public final Key[] keys;
        /** Left padding in units (e.g. 0.5 for the shifted second Arabic row). */
        public final float offset;
        /** Nominal row height in dp (key + vertical gap). */
        public final float heightDp;

        public Row(float offset, float heightDp, Key... keys) {
            this.offset = offset;
            this.heightDp = heightDp;
            this.keys = keys;
        }
    }

    public final String id;
    public final float units;
    public final Row[] rows;
    public final boolean arabic;
    /** Letters page (shift / suggestions apply); false for symbols, numbers, diacritics. */
    public final boolean letters;

    public Layout(String id, float units, boolean arabic, boolean letters, Row... rows) {
        this.id = id;
        this.units = units;
        this.arabic = arabic;
        this.letters = letters;
        this.rows = rows;
    }

    public float totalHeightDp() {
        float h = 0;
        for (Row r : rows) h += r.heightDp;
        return h;
    }

    public List<Key> allKeys() {
        List<Key> out = new ArrayList<>();
        for (Row r : rows) for (Key k : r.keys) out.add(k);
        return out;
    }
}
