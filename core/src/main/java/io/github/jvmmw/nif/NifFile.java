/*
 * Ported to Java from OpenMW (https://openmw.org)
 * Original work Copyright (C) OpenMW Contributors
 * Java translation Copyright (C) 2026 JVM-MW contributors
 */
package io.github.jvmmw.nif;

import java.util.ArrayList;
import java.util.List;

/** Morrowind NIF 4.0.0.2 reader. Maps to {@code Nif::Reader::parse}. */
public final class NifFile {
    public final List<NifRecord> records = new ArrayList<>();
    public final List<Integer> roots = new ArrayList<>();
    public int version;

    public static NifFile parse(byte[] data, String filename) {
        NifStream nif = new NifStream(data);
        String head = nif.getVersionString();
        if (!head.startsWith("NetImmerse File Format") && !head.startsWith("Gamebryo File Format")) {
            throw new IllegalArgumentException("Invalid NIF header: " + head + " in " + filename);
        }
        nif.version = nif.getI32();
        if (nif.version != NifStream.VER_MW && nif.version != NifStream.VER_4_0_0_0) {
            throw new IllegalArgumentException("Unsupported NIF version 0x" + Integer.toHexString(nif.version)
                + " in " + filename);
        }
        int recordsCount = nif.getI32();
        NifFile file = new NifFile();
        file.version = nif.version;
        for (int i = 0; i < recordsCount; i++) {
            String rec = nif.getSizedString();
            if (rec.isEmpty()) {
                throw new IllegalArgumentException("Blank record type at " + i + " in " + filename);
            }
            NifRecord r = create(rec);
            r.recordName = rec;
            r.recordIndex = i;
            readRecord(nif, r);
            file.records.add(r);
        }
        int rootsCount = nif.getI32();
        for (int i = 0; i < rootsCount; i++) {
            file.roots.add(nif.getI32());
        }
        return file;
    }

    public NifRecord get(int index) {
        if (index < 0 || index >= records.size()) {
            return null;
        }
        return records.get(index);
    }

    private static NifRecord create(String rec) {
        return switch (rec) {
            case "NiNode", "AvoidNode", "NiBSAnimationNode", "NiBSParticleNode", "NiCollisionSwitch",
                 "NiBillboardNode", "NiSortAdjustNode" -> new NiNode();
            case "RootCollisionNode" -> {
                NiNode n = new NiNode();
                n.rootCollision = true;
                n.skipMeshes = true;
                yield n;
            }
            case "NiTriShape" -> new NiTriBasedGeom();
            case "NiTriStrips" -> {
                NiTriBasedGeom g = new NiTriBasedGeom();
                g.strips = true;
                yield g;
            }
            case "NiTriShapeData" -> new NiTriShapeData();
            case "NiTriStripsData" -> {
                NiTriShapeData d = new NiTriShapeData();
                d.strips = true;
                yield d;
            }
            case "NiTexturingProperty" -> new NiTexturingProperty();
            case "NiSourceTexture" -> new NiSourceTexture();
            case "NiMaterialProperty" -> new NiMaterialProperty();
            case "NiAlphaProperty" -> new NiAlphaProperty();
            case "NiVertexColorProperty", "NiZBufferProperty", "NiSpecularProperty", "NiWireframeProperty",
                 "NiDitherProperty", "NiFogProperty", "NiShadeProperty", "NiStencilProperty" -> new PropertyStub(rec);
            case "NiStringExtraData", "NiExtraData", "NiTextKeyExtraData", "NiVertWeightsExtraData",
                 "NiBinaryExtraData", "NiIntegerExtraData", "NiBooleanExtraData", "NiFloatExtraData",
                 "NiStringsExtraData" -> new ExtraStub(rec);
            case "NiAlphaAccumulator", "NiClusterAccumulator" -> new AccumulatorStub();
            default -> throw new IllegalArgumentException("Unknown record type " + rec);
        };
    }

    private static void readRecord(NifStream nif, NifRecord r) {
        if (r instanceof NiNode node) {
            readAvObject(nif, node);
            readIndexList(nif, node.children);
            readIndexList(nif, node.effects);
            if (node.recordIndex == 0 && !"bip01".equalsIgnoreCase(node.name)) {
                node.transform.identity();
            }
            if (node.rootCollision) {
                node.skipMeshes = true;
            }
            if ("NiSortAdjustNode".equals(node.recordName)) {
                nif.getI32(); // sorting mode
                nif.getI32(); // sub-sorter ptr (MW version <= 20.0.0.3)
            }
            return;
        }
        if (r instanceof NiTriBasedGeom geom) {
            readAvObject(nif, geom);
            geom.data = nif.getI32();
            geom.skin = nif.getI32();
            return;
        }
        if (r instanceof NiTriShapeData data) {
            readGeometryData(nif, data);
            return;
        }
        if (r instanceof NiTexturingProperty tex) {
            readObjectNet(nif, tex);
            tex.flags = nif.getU16();
            tex.applyMode = nif.getI32();
            int n = nif.getI32();
            for (int i = 0; i < n; i++) {
                tex.textures.add(readTextureSlot(nif));
                if (i == 5 && tex.textures.get(5).enabled) {
                    nif.skip(4 * 2 + 4 * 4); // env luma + bump matrix
                }
            }
            return;
        }
        if (r instanceof NiSourceTexture src) {
            readObjectNet(nif, src);
            src.external = nif.getI8() != 0;
            boolean hasData = false;
            if (!src.external) {
                hasData = nif.getI8() != 0;
            }
            if (src.external) {
                src.file = nif.getSizedString();
            }
            if (hasData) {
                nif.getI32(); // pixel data ptr
            }
            nif.getI32(); // pixel layout
            nif.getI32(); // mip
            nif.getI32(); // alpha
            nif.getI8(); // isStatic
            return;
        }
        if (r instanceof NiMaterialProperty mat) {
            readObjectNet(nif, mat);
            mat.flags = nif.getU16();
            nif.getVec3(mat.ambient, 0);
            nif.getVec3(mat.diffuse, 0);
            nif.getVec3(mat.specular, 0);
            nif.getVec3(mat.emissive, 0);
            mat.glossiness = nif.getF32();
            mat.alpha = nif.getF32();
            return;
        }
        if (r instanceof NiAlphaProperty alpha) {
            readObjectNet(nif, alpha);
            alpha.flags = nif.getU16();
            alpha.threshold = nif.getI8() & 0xFF;
            return;
        }
        if (r instanceof PropertyStub stub) {
            readPropertyStub(nif, stub);
            return;
        }
        if (r instanceof ExtraStub extra) {
            readExtraStub(nif, extra);
            return;
        }
        if (r instanceof AccumulatorStub) {
            return;
        }
        throw new IllegalStateException("Unhandled " + r.recordName);
    }

    private static void readObjectNet(NifStream nif, NifRecord r) {
        r.name = nif.getSizedString();
        r.extra = nif.getI32();
        r.controller = nif.getI32();
    }

    private static void readAvObject(NifStream nif, NiAvObject av) {
        readObjectNet(nif, av);
        av.flags = nif.getU16();
        av.transform.read(nif);
        nif.getVec3(av.velocity, 0);
        readIndexList(nif, av.properties);
        if (nif.getBool()) {
            readBoundingVolume(nif);
        }
        if (av.hidden()) {
            av.skipMeshes = true;
        }
    }

    private static void readIndexList(NifStream nif, List<Integer> list) {
        int n = nif.getI32();
        for (int i = 0; i < n; i++) {
            list.add(nif.getI32());
        }
    }

    private static void readBoundingVolume(NifStream nif) {
        int type = nif.getI32();
        switch (type) {
            case 0xFFFFFFFF -> {
            }
            case 0 -> nif.skip(16); // sphere
            case 1 -> nif.skip(12 + 36 + 12); // box
            case 2 -> nif.skip(12 + 12 + 8); // capsule
            case 3 -> {
                nif.getF32(); // radius
                nif.skip(12 + 12 + 12); // center + 2 axes (no extents on < 4.2.1.0)
            }
            case 4 -> {
                int n = nif.getI32();
                for (int i = 0; i < n; i++) {
                    readBoundingVolume(nif);
                }
            }
            case 5 -> nif.skip(16); // plane only on MW
            default -> throw new IllegalArgumentException("Unhandled BoundingVolume type " + type);
        }
    }

    private static void readGeometryData(NifStream nif, NiTriShapeData data) {
        data.numVertices = nif.getU16();
        if (nif.getBool()) {
            data.vertices = new float[data.numVertices * 3];
            for (int i = 0; i < data.vertices.length; i++) {
                data.vertices[i] = nif.getF32();
            }
        }
        if (nif.getBool()) {
            data.normals = new float[data.numVertices * 3];
            for (int i = 0; i < data.normals.length; i++) {
                data.normals[i] = nif.getF32();
            }
        }
        nif.skip(16); // bounding sphere
        if (nif.getBool()) {
            data.colors = new float[data.numVertices * 4];
            for (int i = 0; i < data.colors.length; i++) {
                data.colors[i] = nif.getF32();
            }
        }
        int numUVs = nif.getU16();
        if (!nif.getBool()) {
            numUVs = 0;
        }
        if (numUVs > 0) {
            data.uvs = new float[data.numVertices * 2];
            for (int i = 0; i < data.uvs.length; i++) {
                data.uvs[i] = nif.getF32();
            }
            for (int set = 1; set < numUVs; set++) {
                nif.skip(data.numVertices * 8);
            }
        }
        int numTriangles = nif.getU16();
        if (data.strips) {
            int numStrips = nif.getU16();
            int[] lengths = new int[numStrips];
            for (int i = 0; i < numStrips; i++) {
                lengths[i] = nif.getU16();
            }
            for (int i = 0; i < numStrips; i++) {
                short[] strip = new short[lengths[i]];
                for (int j = 0; j < strip.length; j++) {
                    strip[j] = nif.getI16();
                }
                data.stripList.add(strip);
            }
            data.triangles = stripsToTriangles(data.stripList);
        } else {
            int numIndices = nif.getI32();
            data.triangles = new short[numIndices];
            for (int i = 0; i < numIndices; i++) {
                data.triangles[i] = nif.getI16();
            }
            int groups = nif.getU16();
            for (int g = 0; g < groups; g++) {
                int count = nif.getU16();
                nif.skip(count * 2);
            }
        }
        if (numTriangles != 0 && !data.strips && data.triangles.length / 3 != numTriangles) {
            // Morrowind stores both; trust index buffer length.
        }
    }

    private static short[] stripsToTriangles(List<short[]> strips) {
        List<Short> out = new ArrayList<>();
        for (short[] strip : strips) {
            if (strip.length < 3) {
                continue;
            }
            short b = strip[0];
            short c = strip[1];
            for (int i = 2; i < strip.length; i++) {
                short a = b;
                b = c;
                c = strip[i];
                if (a == b || b == c || a == c) {
                    continue;
                }
                if ((i % 2) == 0) {
                    out.add(a);
                    out.add(b);
                    out.add(c);
                } else {
                    out.add(a);
                    out.add(c);
                    out.add(b);
                }
            }
        }
        short[] tri = new short[out.size()];
        for (int i = 0; i < out.size(); i++) {
            tri[i] = out.get(i);
        }
        return tri;
    }

    private static NiTexturingProperty.TextureSlot readTextureSlot(NifStream nif) {
        NiTexturingProperty.TextureSlot slot = new NiTexturingProperty.TextureSlot();
        slot.enabled = nif.getBool();
        if (!slot.enabled) {
            return slot;
        }
        slot.source = nif.getI32();
        slot.clamp = nif.getI32();
        slot.filter = nif.getI32();
        slot.uvSet = nif.getI32();
        nif.skip(4); // PS2
        nif.skip(2); // unknown <= 4.1.0.12
        return slot;
    }

    private static void readPropertyStub(NifStream nif, PropertyStub stub) {
        readObjectNet(nif, stub);
        switch (stub.kind) {
            case "NiVertexColorProperty" -> {
                nif.getU16();
                nif.getI32();
                nif.getI32();
            }
            case "NiZBufferProperty" -> {
                nif.getU16();
                // 4.0.0.2 < 4.1.0.12 so no extra test function uint32
            }
            case "NiSpecularProperty", "NiWireframeProperty", "NiDitherProperty", "NiShadeProperty" -> nif.getU16();
            case "NiFogProperty" -> {
                nif.getU16();
                nif.getF32();
                nif.skip(12);
            }
            case "NiStencilProperty" -> {
                nif.getU16();
                nif.getI8();
                nif.getI32();
                nif.getI32();
                nif.getI32();
                nif.getI32();
                nif.getI32();
                nif.getI32();
                nif.getI32();
            }
            default -> throw new IllegalArgumentException("Property stub " + stub.kind);
        }
    }

    private static void readExtraStub(NifStream nif, ExtraStub extra) {
        extra.name = "";
        extra.extra = nif.getI32();
        int recordSize = nif.getI32();
        extra.controller = -1;
        switch (extra.kind) {
            case "NiStringExtraData" -> nif.getSizedString();
            case "NiExtraData" -> nif.skip(recordSize);
            case "NiVertWeightsExtraData" -> nif.skip(nif.getU16() * 4);
            case "NiTextKeyExtraData" -> {
                int n = nif.getI32();
                for (int i = 0; i < n; i++) {
                    nif.getF32();
                    nif.getSizedString();
                }
            }
            case "NiBinaryExtraData" -> {
                int n = nif.getI32();
                nif.skip(n);
            }
            case "NiIntegerExtraData" -> nif.getI32();
            case "NiBooleanExtraData" -> nif.getBool();
            case "NiFloatExtraData" -> nif.getF32();
            case "NiStringsExtraData" -> {
                int n = nif.getI32();
                for (int i = 0; i < n; i++) {
                    nif.getSizedString();
                }
            }
            default -> throw new IllegalArgumentException("Extra stub " + extra.kind);
        }
    }

    public String debugSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("records=").append(records.size()).append(" roots=").append(roots).append('\n');
        for (NifRecord r : records) {
            sb.append(r.recordIndex).append(' ').append(r.recordName).append(" name=")
                .append(r.name).append('\n');
        }
        return sb.toString();
    }

    static final class PropertyStub extends NifRecord {
        final String kind;

        PropertyStub(String kind) {
            this.kind = kind;
        }
    }

    static final class ExtraStub extends NifRecord {
        final String kind;

        ExtraStub(String kind) {
            this.kind = kind;
        }
    }

    static final class AccumulatorStub extends NifRecord {
    }
}
