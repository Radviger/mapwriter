package mapwriter.region;

import mapwriter.forge.MapWriterForge;

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

/*
 * Anvil region file reader/writer implementation. This code is very similar to
 * RegionFile and RegionFileChunkBuffer from Minecraft. Not sure if it would
 * have been better just to use the Minecraft code.
 */

public class RegionFile {

    // basically an in memory byte array that writes its contents
    // to a file when it is closed.
    private class RegionFileChunkBuffer extends ByteArrayOutputStream {
        private final int x;
        private final int z;
        private final RegionFile regionFile;

        public RegionFileChunkBuffer(RegionFile regionFile, int x, int z) {

            super(8096);
            this.regionFile = regionFile;
            this.x = x;
            this.z = z;
        }

        @Override
        public void close() {

            this.regionFile.writeCompressedChunk(this.x, this.z, this.buf, this.count);
        }
    }

    private class Section {
        final int startSector;
        final int length;

        Section(int sectorAndSize) {

            this(sectorAndSize >> 8 & 0xffffff, sectorAndSize & 0xff);
        }

        Section(int startSector, int length) {

            this.startSector = startSector;
            this.length = length;
        }

        int getSectorAndSize() {

            return this.startSector << 8 | this.length & 0xff;
        }
    }

    private final File file;

    private int lengthInSectors = 0;
    private RandomAccessFile fin = null;
    private final Section[] chunkSectionsArray = new Section[4096];

    private final int[] timestampArray = new int[4096];

    private List<Boolean> filledSectorArray = null;

    public RegionFile(File file) {

        this.file = file;
    }

    public void close() {

        if (this.fin != null) {
            try {
                this.fin.close();
            } catch (final IOException e) {
            }
        }
    }

    public boolean exists() {

        return this.file.isFile();
    }

    public DataInputStream getChunkDataInputStream(int x, int z) {

        DataInputStream dis = null;
        if (this.fin != null) {
            final Section section = this.getChunkSection(x, z);
            if (section != null && section.length > 0) {
                final int offset = section.startSector * 4096;
                try {
                    // read length of following data (includes version byte) and
                    // compressed data version byte
                    this.fin.seek(offset);
                    final int length = this.fin.readInt();
                    final byte version = this.fin.readByte();
                    // version 1 = gzip compressed, version 2 = zlib/inflater
                    // compressed
                    if (length > 1 && length + 4 < section.length * 4096 && version == 2) {
                        // read the compressed data
                        final byte[] compressedChunkData = new byte[length - 1];
                        this.fin.read(compressedChunkData);
                        // create a buffered inflater stream on the compressed
                        // data
                        dis = new DataInputStream(new BufferedInputStream(new InflaterInputStream(new ByteArrayInputStream(compressedChunkData))));
                    } else {
                        MapWriterForge.LOGGER.error("data length ({}) or version ({}) invalid for chunk ({}, {})", length, version, x, z);
                    }
                } catch (final Exception e) {
                    MapWriterForge.LOGGER.error("exception while reading chunk ({}, {}): {}", x, z, e);
                    dis = null;
                }
            }
        }
        return dis;
    }

    public DataOutputStream getChunkDataOutputStream(int x, int z) {

        return new DataOutputStream(new DeflaterOutputStream(new RegionFileChunkBuffer(this, x, z)));
    }

    public boolean isOpen() {

        return this.fin != null;
    }

    public boolean open() {

        final File dir = this.file.getParentFile();
        if (dir.exists()) {
            if (!dir.isDirectory()) {
                MapWriterForge.LOGGER.error("path {} exists and is not a directory", dir);
                return true;
            }
        } else {
            if (!dir.mkdirs()) {
                MapWriterForge.LOGGER.error("could not create directory {}", dir);
                return true;
            }
        }
        try {
            this.fin = new RandomAccessFile(this.file, "rw");

            // seek to start
            this.fin.seek(0);

            this.lengthInSectors = (int) ((this.fin.length() + 4095L) / 4096L);

            this.filledSectorArray = new ArrayList<>();

            Arrays.fill(this.chunkSectionsArray, null);
            Arrays.fill(this.timestampArray, 0);

            if (this.lengthInSectors < 3) {
                // no chunk data
                // fill chunk and timestamp tables with 0's
                for (int i = 0; i < 2048; i++) {
                    this.fin.writeInt(0);
                }
            } else {
                // add a section for each chunk
                for (int i = 0; i < 1024; i++) {
                    final Section section = new Section(this.fin.readInt());
                    if (section.length > 0) {
                        // make sure chunk does not overlap another
                        if (!this.checkSectionOverlaps(section)) {
                            this.chunkSectionsArray[i] = section;
                            this.setFilledSectorArray(section, true);
                        } else {
                            MapWriterForge.LOGGER.error("chunk {} overlaps another chunk, file may be corrupt", i);
                        }
                    }
                }
                for (int i = 0; i < 1024; i++) {
                    this.timestampArray[i] = this.fin.readInt();
                }
            }

            // this.printInfo();

        } catch (final Exception e) {
            this.fin = null;
            MapWriterForge.LOGGER.error("exception when opening region file '{}': {}", this.file, e);

        }

        return this.fin == null;
    }

    public void printInfo() {

        int freeCount = 0;
        int filledCount = 0;
        // start at 2 to skip headers
        for (int i = 2; i < this.filledSectorArray.size(); i++) {
            if (this.filledSectorArray.get(i)) {
                filledCount++;
            } else {
                freeCount++;
            }
        }
        MapWriterForge.LOGGER.info("Region File {}: filled sectors = {}, free sectors = {}", this, filledCount, freeCount);

        String s = "";
        int i;
        for (i = 0; i < this.filledSectorArray.size(); i++) {
            if ((i & 31) == 0) {
                s = String.format("%04x:", i);
            }
            s += this.filledSectorArray.get(i) ? '1' : '0';
            if ((i & 31) == 31) {
                MapWriterForge.LOGGER.info("{}", s);
            }
        }
        if ((i & 31) != 31) {
            MapWriterForge.LOGGER.info("{}", s);
        }
    }

    @Override
    public String toString() {

        return String.format("{}", this.file);
    }

    private boolean checkSectionOverlaps(Section section) {

        // get end sector, limiting to length of the filled sector array as all
        // sectors past the
        // end of the file are assumed free.
        final int endSector = Math.min(section.startSector + section.length, this.filledSectorArray.size());
        boolean overlaps = false;
        for (int i = section.startSector; i < endSector; i++) {
            if (this.filledSectorArray.get(i)) {
                overlaps = true;
            }
        }
        return overlaps;
    }

    private Section getChunkSection(int x, int z) {

        return this.chunkSectionsArray[(z & 31) << 5 | x & 31];
    }

    private Section getFreeSection(int requiredLength) {

        int start = 0;
        int length = 0;
        int closestStart = 0;
        int closestLength = Integer.MAX_VALUE;
        // start at 2 to skip headers
        int i;
        for (i = 2; i < this.filledSectorArray.size(); i++) {
            if (this.filledSectorArray.get(i)) {
                // sector filled
                // if the length of the empty block we found is greater than or
                // equal to the required length, and is closer to the required
                // length than the previous found length, then set this as the
                // new closest length.
                // the idea is to use an empty block of exactly the required
                // length, rather than one that is larger.
                if (length >= requiredLength && length < closestLength) {
                    closestLength = length;
                    closestStart = start;
                    // if we find an empty block of exactly the correct length
                    // then exit the loop.
                    if (closestLength == requiredLength) {
                        break;
                    }
                }
                length = 0;
            } else {
                // sector empty
                if (length == 0) {
                    start = i;
                }
                length++;
            }
        }

        if (closestStart <= 0) {
            // append to end of file
            closestStart = i;
        }

        return new Section(closestStart, requiredLength);
    }

    // set the corresponding bits in filledSectorArray to 'filled'
    // for 'count' sectors, starting at 'firstSector'.
    private void setFilledSectorArray(Section section, boolean filled) {

        final int endSector = section.startSector + section.length;
        final int sectorsToAppend = endSector + 1 - this.filledSectorArray.size();
        for (int i = 0; i < sectorsToAppend; i++) {
            this.filledSectorArray.add(Boolean.valueOf(false));
        }
        for (int i = section.startSector; i < endSector; i++) {
            if (filled && this.filledSectorArray.get(i)) {
                MapWriterForge.LOGGER.error("sector {} already filled, possible chunk overlap", i);
            }
            this.filledSectorArray.set(i, Boolean.valueOf(filled));
        }
    }

    private void updateChunkSection(int x, int z, Section newSection) throws IOException {

        final int chunkIndex = (z & 31) << 5 | x & 31;
        this.fin.seek(chunkIndex * 4);
        if (newSection != null && newSection.length > 0) {
            this.fin.writeInt(newSection.getSectorAndSize());
        } else {
            this.fin.writeInt(0);
        }

        this.chunkSectionsArray[chunkIndex] = newSection;
    }

    /*
     * private int padToSectorSize() throws IOException { // pad with 0 so that the file length
     * is a multiple of 4096 bytes long paddedLength = (this.length + 4095L) & (-4096L);
     * this.fin.seek(this.length); for (long i = this.length; i < paddedLength; i++) {
     * this.fin.writeByte(0); } this.length = paddedLength; return (int) (paddedLength / 4096);
     * }
     */

    private void writeChunkDataToSection(Section section, byte[] compressedChunkData, int length) throws IOException {

        this.fin.seek(section.startSector * 4096L);
        // write version and length
        this.fin.writeInt(length + 1);
        this.fin.writeByte(2);
        // write compressed data
        this.fin.write(compressedChunkData, 0, length);

        final int endSector = section.startSector + section.length;
        if (endSector + 1 > this.lengthInSectors) {
            this.lengthInSectors = endSector + 1;
        }
    }

    private boolean writeCompressedChunk(int x, int z, byte[] compressedChunkData, int length) {
        // if larger than the existing chunk data or chunk does not exist then
        // need to find the
        // first possible file position to write to. This will either be a
        // contiguous strip of
        // free sectors longer than the length of the chunk data, or the end of
        // the file (append).

        if (length <= 0) {
            MapWriterForge.LOGGER.warn("not writing chunk ({}, {}) with length {}", x, z, length);
            return true;
        }

        // free the section this chunk currently occupies
        final Section currentSection = this.getChunkSection(x, z);
        if (currentSection != null) {
            this.setFilledSectorArray(currentSection, false);
        }

        final int requiredSectors = (length + 5 + 4095) / 4096;
        Section newSection;

        if (currentSection != null && requiredSectors <= currentSection.length) {
            // if the chunk still fits in it's current location don't move
            // RegionManager.logInfo("chunk ({}, {}) fits in current location
            // {}",
            // x, z, currentSection.startSector);
            newSection = new Section(currentSection.startSector, requiredSectors);
        } else {
            // otherwise find a free section large enough to hold the chunk data
            newSection = this.getFreeSection(requiredSectors);
        }

        // set the new section to filled
        this.setFilledSectorArray(newSection, true);

        boolean error = true;
        try {
            // RegionManager.logInfo("writing {} bytes to sector {} for chunk
            // ({}, {})",
            // length, newSection.startSector, x, z);
            this.writeChunkDataToSection(newSection, compressedChunkData, length);
            // update the header
            this.updateChunkSection(x, z, newSection);
            error = false;
        } catch (final IOException e) {
            MapWriterForge.LOGGER.error("could not write chunk ({}, {}) to region file: {}", x, z, e);
        }

        return error;
    }
}
